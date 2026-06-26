from __future__ import annotations

import hashlib
import ipaddress
import logging
import socket
import ssl
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

from zeroconf import ServiceInfo

LOGGER = logging.getLogger("local_manager.family")

FAMILY_SERVICE_TYPE = "_localmanager._tcp.local."
FAMILY_VERSION = "0.2"
FAMILY_TLS_PROTOCOL = "https"
TLS_FINGERPRINT_ATTR = "tls_fp_sha256"
PASSWORD_HEADER = "X-Network-Service-Password"

TLS_DIR = Path(__file__).resolve().parent / "tls"
DEFAULT_TLS_CA_CERT = TLS_DIR / "ca_cert.pem"
DEFAULT_TLS_SERVER_CERT = TLS_DIR / "pc_server_cert.pem"
DEFAULT_TLS_SERVER_KEY = TLS_DIR / "pc_server_key.pem"


def normalize_service_type(raw_service_type: str) -> str:
    service_type = raw_service_type.strip()
    if not service_type:
        raise ValueError("服务类型不能为空")
    if not service_type.endswith(".local."):
        raise ValueError(f"服务类型必须以 .local. 结尾，当前值为 {service_type!r}。")
    if not service_type.endswith("._tcp.local.") and not service_type.endswith("._udp.local."):
        raise ValueError(
            f"服务类型必须是 _service._tcp.local. 形式，当前值为 {service_type!r}。"
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
            f"当前要求主机名使用本地域名，收到 {hostname!r}。请传入裸主机名或 *.local。"
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


def pem_cert_fingerprint_sha256(cert_path: str | Path) -> str:
    text = Path(cert_path).read_text(encoding="utf-8")
    begin = "-----BEGIN CERTIFICATE-----"
    end = "-----END CERTIFICATE-----"
    start_index = text.find(begin)
    end_index = text.find(end, start_index)
    if start_index < 0 or end_index < 0:
        raise ValueError(f"证书文件中没有 PEM 证书块: {cert_path}")
    pem_block = text[start_index : end_index + len(end)]
    der_bytes = ssl.PEM_cert_to_DER_cert(pem_block)
    return hashlib.sha256(der_bytes).hexdigest()


def build_service_properties(
    instance_id: str,
    tls_fingerprint: str,
    *,
    auth_required: bool,
    platform: str = "python",
) -> dict[str, str]:
    props = {
        "app": "LocalManager",
        "proto": FAMILY_TLS_PROTOCOL,
        "version": FAMILY_VERSION,
        "instance_id": instance_id,
        "platform": platform,
        "tls": "1",
        TLS_FINGERPRINT_ATTR: tls_fingerprint,
    }
    if auth_required:
        props["auth"] = "1"
    return props


def build_service_info(
    service_type: str,
    service_name: str,
    hostname: str,
    port: int,
    instance_id: str,
    tls_fingerprint: str,
    *,
    auth_required: bool,
    platform: str = "python",
) -> ServiceInfo:
    addresses = collect_local_addresses()
    LOGGER.info(
        "用于 mDNS 广播的本地地址: %s",
        ", ".join(address.compressed for address in addresses),
    )
    full_name = f"{service_name}.{service_type}"
    properties = build_service_properties(
        instance_id,
        tls_fingerprint,
        auth_required=auth_required,
        platform=platform,
    )
    return ServiceInfo(
        type_=service_type,
        name=full_name,
        addresses=[address.packed for address in addresses],
        port=port,
        properties=properties,
        server=hostname,
    )


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


def start_https_server(
    bind_host: str,
    port: int,
    cert_file: str | Path,
    key_file: str | Path,
    handler_class: type[BaseHTTPRequestHandler],
) -> tuple[ReusableThreadingHTTPSServer, threading.Thread, str]:
    ssl_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ssl_context.load_cert_chain(certfile=str(cert_file), keyfile=str(key_file))
    https_server = ReusableThreadingHTTPSServer(
        (bind_host, port),
        handler_class,
        ssl_context,
    )
    thread = threading.Thread(
        target=https_server.serve_forever,
        name="family-https-server",
        daemon=True,
    )
    thread.start()
    fingerprint = pem_cert_fingerprint_sha256(cert_file)
    LOGGER.info(
        "HTTPS 服务已监听 %s:%s cert=%s fingerprint=%s",
        bind_host,
        port,
        cert_file,
        fingerprint,
    )
    return https_server, thread, fingerprint


def binary_response(
    handler: BaseHTTPRequestHandler,
    status: int,
    data: bytes,
    content_type: str = "application/octet-stream",
    extra_headers: dict[str, str] | None = None,
) -> None:
    handler.send_response(status)
    handler.send_header("Content-Type", content_type)
    handler.send_header("Content-Length", str(len(data)))
    handler.send_header("Accept-Ranges", "bytes")
    for key, value in (extra_headers or {}).items():
        handler.send_header(key, value)
    handler.end_headers()
    handler.wfile.write(data)


def json_response(handler: BaseHTTPRequestHandler, status: int, payload: dict[str, Any]) -> None:
    import json

    encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(encoded)))
    handler.end_headers()
    handler.wfile.write(encoded)


def read_request_body(handler: BaseHTTPRequestHandler) -> bytes:
    content_length = handler.headers.get("Content-Length", "0")
    try:
        body_size = max(0, int(content_length))
    except ValueError:
        body_size = 0
    return handler.rfile.read(body_size)


def load_or_create_instance_id(path: Path) -> str:
    import uuid

    if path.exists():
        stored = path.read_text(encoding="utf-8").strip()
        if stored:
            return stored
    instance_id = str(uuid.uuid4())
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(instance_id + "\n", encoding="utf-8")
    return instance_id
