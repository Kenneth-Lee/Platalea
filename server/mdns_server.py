#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
import logging
import socket
import ssl
import sys
import threading
import time
import uuid
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

from zeroconf import IPVersion, ServiceBrowser, ServiceInfo, ServiceListener, Zeroconf
from zeroconf import ZeroconfServiceTypes


LOGGER = logging.getLogger("local_manager.mdns_server")
DEFAULT_SERVICE_TYPE = "_localmanager._tcp.local."
DEFAULT_DISCOVERY_TIMEOUT_MS = 2000
DEFAULT_TYPE_SCAN_INTERVAL = 15.0
TLS_DIR = Path(__file__).resolve().parent / "tls"
DEFAULT_TLS_CA_CERT = TLS_DIR / "ca_cert.pem"
DEFAULT_TLS_SERVER_CERT = TLS_DIR / "pc_server_cert.pem"
DEFAULT_TLS_SERVER_KEY = TLS_DIR / "pc_server_key.pem"
TLS_FINGERPRINT_ATTR = "tls_fp_sha256"


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="注册 LocalManager mDNS 服务，并持续打印局域网内可解析的 mDNS 服务。",
    )
    parser.add_argument(
        "--bind-host",
        default="0.0.0.0",
        help="内置 HTTP 服务监听地址，默认 0.0.0.0",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=8765,
        help="内置 HTTP 服务监听端口，默认 8765",
    )
    parser.add_argument(
        "--service-type",
        default=DEFAULT_SERVICE_TYPE,
        help="mDNS 服务类型，必须以 .local. 结尾，默认 _localmanager._tcp.local.",
    )
    parser.add_argument(
        "--service-name",
        default=socket.gethostname(),
        help="mDNS 实例名，默认取当前主机名",
    )
    parser.add_argument(
        "--hostname",
        default=socket.gethostname(),
        help="注册到 mDNS 的主机名，不带 .local. 后缀时会自动补齐",
    )
    parser.add_argument(
        "--type-scan-interval",
        type=float,
        default=DEFAULT_TYPE_SCAN_INTERVAL,
        help="重新扫描局域网服务类型的间隔秒数，默认 15",
    )
    parser.add_argument(
        "--discovery-timeout-ms",
        type=int,
        default=DEFAULT_DISCOVERY_TIMEOUT_MS,
        help="解析单个服务详情时的超时毫秒数，默认 2000",
    )
    parser.add_argument(
        "--run-seconds",
        type=float,
        default=0.0,
        help="运行多少秒后自动退出；0 表示持续运行直到 Ctrl+C",
    )
    parser.add_argument(
        "--log-level",
        default="INFO",
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="日志级别，默认 INFO",
    )
    parser.add_argument(
        "--cert-file",
        default=str(DEFAULT_TLS_SERVER_CERT),
        help="HTTPS 服务端证书文件，默认 server/tls/pc_server_cert.pem",
    )
    parser.add_argument(
        "--key-file",
        default=str(DEFAULT_TLS_SERVER_KEY),
        help="HTTPS 服务端私钥文件，默认 server/tls/pc_server_key.pem",
    )
    return parser


def normalize_service_type(raw_service_type: str) -> str:
    service_type = raw_service_type.strip()
    if not service_type:
        raise ValueError("服务类型不能为空")
    if not service_type.endswith(".local."):
        raise ValueError(
            f"服务类型必须以 .local. 结尾，当前值为 {service_type!r}。"
        )
    if not service_type.endswith("._tcp.local.") and not service_type.endswith("._udp.local."):
        raise ValueError(
            f"服务类型必须是 _service._tcp.local. 或 _service._udp.local. 形式，当前值为 {service_type!r}。"
        )
    return service_type


def normalize_hostname(hostname: str) -> str:
    normalized = hostname.strip().rstrip(".")
    if not normalized:
        raise ValueError("主机名不能为空")
    if normalized.endswith(".local"):
        return f"{normalized}."
    if "." in normalized and not normalized.endswith(".local"):
        raise ValueError(
            f"当前原型要求主机名使用本地域名，收到 {hostname!r}。请传入裸主机名或 *.local。"
        )
    return f"{normalized}.local."


def is_usable_ip(address_text: str) -> bool:
    ip_value = ipaddress.ip_address(address_text)
    return not (
        ip_value.is_loopback
        or ip_value.is_unspecified
        or ip_value.is_multicast
    )


def collect_local_addresses() -> list[ipaddress._BaseAddress]:
    candidates: dict[str, ipaddress._BaseAddress] = {}
    hostnames = {socket.gethostname(), socket.getfqdn()}

    for hostname in hostnames:
        if not hostname:
            continue
        try:
            infos = socket.getaddrinfo(
                hostname,
                None,
                family=socket.AF_UNSPEC,
                type=socket.SOCK_STREAM,
            )
        except OSError as exc:
            LOGGER.debug("解析本机地址失败 hostname=%s exc=%s", hostname, exc)
            continue

        for info in infos:
            sockaddr = info[4]
            address_text = sockaddr[0]
            try:
                if is_usable_ip(address_text):
                    candidates[address_text] = ipaddress.ip_address(address_text)
            except ValueError:
                LOGGER.debug("忽略无法识别的地址 %s", address_text)

    probe_targets: list[tuple[int, str]] = [
        (socket.AF_INET, "192.0.2.1"),
        (socket.AF_INET6, "2001:db8::1"),
    ]
    for family, target in probe_targets:
        try:
            with socket.socket(family, socket.SOCK_DGRAM) as sock:
                sock.connect((target, 9))
                address_text = sock.getsockname()[0]
                if is_usable_ip(address_text):
                    candidates[address_text] = ipaddress.ip_address(address_text)
        except OSError as exc:
            LOGGER.debug("UDP 探测本地出口地址失败 family=%s target=%s exc=%s", family, target, exc)

    addresses = sorted(
        candidates.values(),
        key=lambda value: (value.version, value.compressed),
    )
    if not addresses:
        raise RuntimeError(
            "没有找到可用于 mDNS 广播的非回环地址。"
            "请确认当前机器已连接到局域网，并且网络栈已分配可用 IPv4 或 IPv6 地址。"
        )
    return addresses


def service_properties(instance_id: str, tls_fingerprint: str) -> dict[str, str]:
    return {
        "app": "LocalManager",
        "proto": "https",
        "version": "0.1",
        "instance_id": instance_id,
        "tls": "1",
        TLS_FINGERPRINT_ATTR: tls_fingerprint,
    }


class JsonStatusHandler(BaseHTTPRequestHandler):
    server_version = "LocalManagerMDNSServer/0.1"

    def do_GET(self) -> None:
        payload = {
            "service": "LocalManager mDNS prototype",
            "path": self.path,
            "timestamp": time.time(),
            "server_address": self.server.server_address,
        }
        encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_POST(self) -> None:
        if self.path != "/message":
            self._write_json(
                HTTPStatus.NOT_FOUND,
                {"ok": False, "error": "not_found", "path": self.path},
            )
            return

        raw_body = self._read_request_body()
        try:
            payload = json.loads(raw_body.decode("utf-8"))
        except Exception as exc:
            self._write_json(
                HTTPStatus.BAD_REQUEST,
                {"ok": False, "error": "invalid_json", "message": str(exc)},
            )
            return

        message_id = str(payload.get("message_id", "")).strip() or str(uuid.uuid4())
        content = str(payload.get("content", "")).strip()
        sender_name = str(payload.get("sender_name", "")).strip() or self.client_address[0]
        sender_instance_id = str(payload.get("sender_instance_id", "")).strip()
        sender_platform = str(payload.get("sender_platform", "")).strip()
        if not content:
            self._write_json(
                HTTPStatus.BAD_REQUEST,
                {"ok": False, "error": "empty_content", "message": "消息内容不能为空"},
            )
            return

        LOGGER.info(
            "收到消息 sender=%s platform=%s instance_id=%s remote=%s content=%s",
            sender_name,
            sender_platform or "unknown",
            sender_instance_id or "-",
            self.client_address[0],
            content,
        )
        self._write_json(
            HTTPStatus.OK,
            {
                "ok": True,
                "acknowledged": True,
                "message_id": message_id,
                "received": True,
                "sender_name": sender_name,
                "message_length": len(content),
            },
        )

    def _read_request_body(self) -> bytes:
        content_length = self.headers.get("Content-Length", "0")
        try:
            body_size = max(0, int(content_length))
        except ValueError:
            body_size = 0
        return self.rfile.read(body_size)

    def _write_json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, fmt: str, *args: Any) -> None:
        LOGGER.debug("HTTP %s - %s", self.address_string(), fmt % args)


class ReusableThreadingHTTPServer(ThreadingHTTPServer):
    allow_reuse_address = True
    daemon_threads = True


class ReusableThreadingHTTPSServer(ReusableThreadingHTTPServer):
    def __init__(
        self,
        server_address: tuple[str, int],
        request_handler_class: type[BaseHTTPRequestHandler],
        ssl_context: ssl.SSLContext,
    ) -> None:
        super().__init__(server_address, request_handler_class)
        self.socket = ssl_context.wrap_socket(self.socket, server_side=True)


def decode_properties(properties: dict[bytes, bytes | None]) -> dict[str, str]:
    decoded: dict[str, str] = {}
    for key, value in properties.items():
        decoded_key = key.decode("utf-8", errors="replace")
        if value is None:
            decoded[decoded_key] = ""
            continue
        decoded[decoded_key] = value.decode("utf-8", errors="replace")
    return decoded


def extract_addresses(info: ServiceInfo) -> list[str]:
    if hasattr(info, "parsed_scoped_addresses"):
        return [str(value) for value in info.parsed_scoped_addresses()]
    if hasattr(info, "parsed_addresses"):
        return [str(value) for value in info.parsed_addresses()]
    return []


class DiscoveryRegistry:
    def __init__(self, zeroconf_client: Zeroconf, discovery_timeout_ms: int) -> None:
        self._zeroconf = zeroconf_client
        self._discovery_timeout_ms = discovery_timeout_ms
        self._listener = DiscoveryListener(self)
        self._browsers: dict[str, ServiceBrowser] = {}
        self._service_snapshots: dict[tuple[str, str], str] = {}
        self._lock = threading.Lock()

    def refresh_service_types(self) -> None:
        try:
            service_types = sorted(
                ZeroconfServiceTypes.find(
                    self._zeroconf,
                    timeout=self._discovery_timeout_ms / 1000.0,
                )
            )
        except Exception as exc:
            LOGGER.warning("扫描 mDNS 服务类型失败: %s", exc)
            return

        if not service_types:
            LOGGER.info("本轮未发现新的 mDNS 服务类型。")
            return

        for service_type in service_types:
            with self._lock:
                if service_type in self._browsers:
                    continue
                LOGGER.info("开始监听服务类型 %s", service_type)
                self._browsers[service_type] = ServiceBrowser(
                    self._zeroconf,
                    service_type,
                    self._listener,
                )

    def upsert_service(self, service_type: str, service_name: str) -> None:
        try:
            info = self._zeroconf.get_service_info(
                service_type,
                service_name,
                timeout=self._discovery_timeout_ms,
            )
        except Exception as exc:
            LOGGER.warning(
                "解析服务详情失败 type=%s name=%s error=%s",
                service_type,
                service_name,
                exc,
            )
            return

        if info is None:
            LOGGER.warning(
                "服务详情为空，可能设备已离线或网络阻塞了 mDNS 响应 type=%s name=%s",
                service_type,
                service_name,
            )
            return

        details = {
            "name": service_name,
            "type": service_type,
            "server": info.server,
            "port": info.port,
            "priority": info.priority,
            "weight": info.weight,
            "addresses": extract_addresses(info),
            "properties": decode_properties(info.properties),
        }
        snapshot = json.dumps(details, ensure_ascii=False, sort_keys=True)
        service_key = (service_type, service_name)

        with self._lock:
            previous = self._service_snapshots.get(service_key)
            self._service_snapshots[service_key] = snapshot

        if previous == snapshot:
            LOGGER.debug("服务无变化: %s %s", service_type, service_name)
            return

        change_kind = "更新" if previous is not None else "发现"
        LOGGER.info("%s服务: %s", change_kind, json.dumps(details, ensure_ascii=False))

    def remove_service(self, service_type: str, service_name: str) -> None:
        service_key = (service_type, service_name)
        with self._lock:
            existed = self._service_snapshots.pop(service_key, None)
        if existed is not None:
            LOGGER.info("服务离线: type=%s name=%s", service_type, service_name)
        else:
            LOGGER.debug("收到离线通知但本地没有缓存: type=%s name=%s", service_type, service_name)


class DiscoveryListener(ServiceListener):
    def __init__(self, registry: DiscoveryRegistry) -> None:
        self._registry = registry

    def add_service(self, zeroconf_client: Zeroconf, service_type: str, name: str) -> None:
        del zeroconf_client
        self._registry.upsert_service(service_type, name)

    def update_service(self, zeroconf_client: Zeroconf, service_type: str, name: str) -> None:
        del zeroconf_client
        self._registry.upsert_service(service_type, name)

    def remove_service(self, zeroconf_client: Zeroconf, service_type: str, name: str) -> None:
        del zeroconf_client
        self._registry.remove_service(service_type, name)


def pem_cert_fingerprint_sha256(cert_path: str) -> str:
    text = Path(cert_path).read_text(encoding="utf-8")
    begin = "-----BEGIN CERTIFICATE-----"
    end = "-----END CERTIFICATE-----"
    start_index = text.find(begin)
    end_index = text.find(end, start_index)
    if start_index < 0 or end_index < 0:
        raise ValueError(f"证书文件中没有 PEM 证书块: {cert_path}")
    pem_block = text[start_index:end_index + len(end)]
    der_bytes = ssl.PEM_cert_to_DER_cert(pem_block)
    return hashlib.sha256(der_bytes).hexdigest()


def start_https_server(
    bind_host: str,
    port: int,
    cert_file: str,
    key_file: str,
) -> tuple[ReusableThreadingHTTPSServer, threading.Thread, str]:
    ssl_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ssl_context.load_cert_chain(certfile=cert_file, keyfile=key_file)
    https_server = ReusableThreadingHTTPSServer((bind_host, port), JsonStatusHandler, ssl_context)
    thread = threading.Thread(target=https_server.serve_forever, name="https-server", daemon=True)
    thread.start()
    fingerprint = pem_cert_fingerprint_sha256(cert_file)
    LOGGER.info("HTTPS 服务已监听 %s:%s cert=%s fingerprint=%s", bind_host, port, cert_file, fingerprint)
    return https_server, thread, fingerprint


def build_service_info(
    service_type: str,
    service_name: str,
    hostname: str,
    port: int,
    instance_id: str,
    tls_fingerprint: str,
) -> ServiceInfo:
    addresses = collect_local_addresses()
    LOGGER.info(
        "用于 mDNS 广播的本地地址: %s",
        ", ".join(address.compressed for address in addresses),
    )
    full_name = f"{service_name}.{service_type}"
    properties = service_properties(instance_id, tls_fingerprint)
    return ServiceInfo(
        type_=service_type,
        name=full_name,
        addresses=[address.packed for address in addresses],
        port=port,
        properties=properties,
        server=hostname,
    )


def run_server(args: argparse.Namespace) -> int:
    service_type = normalize_service_type(args.service_type)
    hostname = normalize_hostname(args.hostname)
    instance_id = str(uuid.uuid4())

    https_server, _thread, tls_fingerprint = start_https_server(
        args.bind_host,
        args.port,
        args.cert_file,
        args.key_file,
    )
    zeroconf_client: Zeroconf | None = None
    service_info: ServiceInfo | None = None
    registry: DiscoveryRegistry | None = None

    try:
        zeroconf_client = Zeroconf(ip_version=IPVersion.All)
        service_info = build_service_info(
            service_type=service_type,
            service_name=args.service_name,
            hostname=hostname,
            port=args.port,
            instance_id=instance_id,
            tls_fingerprint=tls_fingerprint,
        )
        zeroconf_client.register_service(service_info)
        LOGGER.info(
            "mDNS 服务已注册: name=%s type=%s hostname=%s port=%s instance_id=%s",
            service_info.name,
            service_type,
            hostname,
            args.port,
            instance_id,
        )

        registry = DiscoveryRegistry(zeroconf_client, args.discovery_timeout_ms)
        registry.refresh_service_types()

        deadline = time.monotonic() + args.run_seconds if args.run_seconds > 0 else None
        next_type_scan = time.monotonic() + args.type_scan_interval

        while True:
            now = time.monotonic()
            if deadline is not None and now >= deadline:
                LOGGER.info("达到运行时长限制，准备退出。")
                return 0
            if now >= next_type_scan:
                registry.refresh_service_types()
                next_type_scan = now + args.type_scan_interval
            time.sleep(0.5)
    except KeyboardInterrupt:
        LOGGER.info("收到 Ctrl+C，准备退出。")
        return 0
    finally:
        if zeroconf_client is not None and service_info is not None:
            try:
                zeroconf_client.unregister_service(service_info)
                LOGGER.info("mDNS 服务已注销: %s", service_info.name)
            except Exception as exc:
                LOGGER.warning("注销 mDNS 服务失败: %s", exc)
        if zeroconf_client is not None:
            zeroconf_client.close()
        https_server.shutdown()
        https_server.server_close()
        LOGGER.info("HTTPS 服务已关闭。")


def main() -> int:
    parser = build_argument_parser()
    args = parser.parse_args()
    logging.basicConfig(
        level=getattr(logging, args.log_level),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    try:
        return run_server(args)
    except Exception as exc:
        LOGGER.exception("mDNS 原型启动失败: %s", exc)
        return 1


if __name__ == "__main__":
    sys.exit(main())