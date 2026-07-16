from __future__ import annotations

import hashlib
import ipaddress
import logging
import re
import socket
import ssl
import sys
import time
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

from zeroconf import ServiceInfo

LOGGER = logging.getLogger("local_manager.family")

RFC1918_IPV4_NETWORKS = (
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("172.16.0.0/12"),
    ipaddress.ip_network("192.168.0.0/16"),
)
LINK_LOCAL_IPV4_NETWORK = ipaddress.ip_network("169.254.0.0/16")
ULA_IPV6_NETWORK = ipaddress.ip_network("fc00::/7")

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
    # 仅保留局域网可达地址，避免把文档地址/保留地址（如 2001::1、253.x.x.x）写入 mDNS。
    if ip_value.is_loopback or ip_value.is_unspecified or ip_value.is_multicast:
        return False
    if ip_value.version == 4:
        ipv4 = ip_value
        return any(ipv4 in network for network in RFC1918_IPV4_NETWORKS) or ipv4 in LINK_LOCAL_IPV4_NETWORK
    ipv6 = ip_value
    return ipv6 in ULA_IPV6_NETWORK or ipv6.is_link_local


def collect_local_addresses() -> list[ipaddress._BaseAddress]:
    candidates: dict[str, ipaddress._BaseAddress] = {}

    # 方法1: 通过 UDP 探测获取本地出口地址（不依赖 DNS）
    probe_targets: list[tuple[int, str]] = [
        (socket.AF_INET, "192.0.2.1"),     # RFC 5737 测试地址
        (socket.AF_INET6, "2001:db8::1"),   # RFC 3849 文档地址
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

    # 方法2: 通过网络接口补充地址（即使方法1成功也继续，避免多网卡场景漏地址）
    try:
        import subprocess
        result = subprocess.run(
            ["ifconfig"],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
        if result.returncode == 0:
            import re
            # 匹配 inet 地址（IPv4）
            for match in re.finditer(r"inet\s+([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)", result.stdout):
                addr = match.group(1)
                if is_usable_ip(addr):
                    candidates[addr] = ipaddress.ip_address(addr)
            # 匹配 inet6 地址（IPv6）
            for match in re.finditer(r"inet6\s+([0-9a-fA-F:]+)", result.stdout):
                addr = match.group(1)
                if is_usable_ip(addr):
                    candidates[addr] = ipaddress.ip_address(addr)
    except Exception as exc:
        LOGGER.debug("ifconfig 获取地址失败: %s", exc)

    # 方法3: 尝试解析主机名补充地址（但过滤掉公网地址）
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

    addresses = sorted(
        candidates.values(),
        key=lambda value: (value.version, value.compressed),
    )
    if not addresses:
        raise RuntimeError(
            "没有找到可用于 mDNS 广播的非回环地址。"
            "请确认当前机器已连接到局域网，并且网络栈已分配可用 IPv4 或 IPv6 地址。"
        )
    return prioritize_broadcast_addresses(addresses)


def _default_route_interface() -> str | None:
    """返回默认路由出口接口名（如 en1/eth0）；无法确定时返回 None。"""
    import subprocess

    if sys.platform == "darwin":
        try:
            result = subprocess.run(
                ["route", "-n", "get", "default"],
                capture_output=True,
                text=True,
                timeout=3,
                check=False,
            )
        except (OSError, subprocess.SubprocessError):
            return None
        if result.returncode == 0:
            match = re.search(r"interface:\s*(\S+)", result.stdout)
            if match:
                return match.group(1)
        return None

    # Linux
    try:
        result = subprocess.run(
            ["ip", "route", "show", "default"],
            capture_output=True,
            text=True,
            timeout=3,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if result.returncode == 0:
        match = re.search(r"\bdev\s+(\S+)", result.stdout)
        if match:
            return match.group(1)
    return None


def _interface_ipv4_addresses(iface: str) -> list[str]:
    """返回指定接口上的 IPv4 地址列表。"""
    import subprocess

    addresses: list[str] = []
    if sys.platform == "darwin":
        try:
            result = subprocess.run(
                ["ifconfig", iface],
                capture_output=True,
                text=True,
                timeout=3,
                check=False,
            )
        except (OSError, subprocess.SubprocessError):
            return addresses
        if result.returncode == 0:
            for match in re.finditer(r"inet\s+(\d{1,3}(?:\.\d{1,3}){3})", result.stdout):
                addresses.append(match.group(1))
    else:
        try:
            result = subprocess.run(
                ["ip", "-o", "-4", "addr", "show", iface],
                capture_output=True,
                text=True,
                timeout=3,
                check=False,
            )
        except (OSError, subprocess.SubprocessError):
            return addresses
        if result.returncode == 0:
            for line in result.stdout.splitlines():
                parts = line.split()
                # 形如: "3: en1    inet 192.168.1.160/24 ..."
                if "inet" in parts:
                    idx = parts.index("inet")
                    if idx + 1 < len(parts):
                        addresses.append(parts[idx + 1].split("/")[0])
    return addresses


def _default_route_ready() -> bool:
    """默认路由出口接口是否已获得可用 IPv4。

    家庭网络可达性由默认路由决定。开机时 ZeroTier/VPN 等虚拟接口常先于物理网卡就绪，
    若仅凭“存在任意 LAN IPv4”就注册 mDNS，会把广播地址锚定在虚拟接口上。这里要求
    默认路由接口也拿到地址；无法判定默认路由时不阻塞（返回 True）。
    """
    iface = _default_route_interface()
    if not iface:
        return True
    addresses = _interface_ipv4_addresses(iface)
    if not addresses:
        return False
    return any(
        is_usable_ip(addr) and not ipaddress.ip_address(addr).is_link_local
        for addr in addresses
    )


def collect_mdns_broadcast_addresses(
    timeout_seconds: float = 30.0,
    poll_interval_seconds: float = 1.0,
) -> list[ipaddress._BaseAddress]:
    deadline = time.monotonic() + timeout_seconds
    last_error: Exception | None = None
    last_addresses: list[ipaddress._BaseAddress] = []

    while True:
        try:
            addresses = collect_local_addresses()
        except Exception as exc:
            last_error = exc
            addresses = []
        else:
            last_addresses = addresses
            # Android 端连接诊断与地址回退策略都依赖 IPv4；若尚无局域网 IPv4，继续等待。
            has_lan_ipv4 = any(address.version == 4 and not address.is_link_local for address in addresses)
            # 仅当默认路由出口接口（家庭网关所在的真实物理网卡）也已获得 IPv4 时才视为就绪。
            # 否则开机时 ZeroTier/VPN 等虚拟网卡会先 up 并被当成 LAN 地址，导致 mDNS 过早注册在
            # 虚拟接口上，家庭局域网内的其他设备既收不到 multicast 应答、解析到的虚拟地址也连不上。
            if has_lan_ipv4 and _default_route_ready():
                return prioritize_broadcast_addresses(addresses)
            if has_lan_ipv4:
                LOGGER.info(
                    "已检测到局域网地址，但默认路由接口尚未获得 IPv4（等待 DHCP/物理网卡就绪），%s 秒后重试...",
                    poll_interval_seconds,
                )

        if time.monotonic() >= deadline:
            break
        LOGGER.info(
            "尚未获取到可用于 mDNS 广播的 LAN IPv4 地址，%s 秒后重试... 当前地址=%s",
            poll_interval_seconds,
            ", ".join(address.compressed for address in last_addresses) or "<none>",
        )
        time.sleep(poll_interval_seconds)

    # 超时兜底：宁可带着现有地址先注册（后台 watcher 会在物理网卡就绪后重新绑定），
    # 也不要让 daemon 崩溃——当前 bootstrap 设计下崩溃后不会被 launchd 重新拉起。
    if last_addresses:
        LOGGER.warning(
            "等待默认路由接口就绪超时（%ss），先用现有地址注册 mDNS: %s；"
            "后台会在物理网卡就绪后自动重新绑定。",
            timeout_seconds,
            ", ".join(address.compressed for address in last_addresses),
        )
        return prioritize_broadcast_addresses(last_addresses)
    if last_error is not None:
        raise RuntimeError(
            "启动 mDNS 前未能获取到可广播的局域网 IPv4 地址。"
            "请确认网络已连通后再启动服务。"
        ) from last_error
    raise RuntimeError(
        "启动 mDNS 前未能获取到可广播的局域网 IPv4 地址。"
        f"最后一次扫描到的地址: {', '.join(address.compressed for address in last_addresses) or '<none>'}"
    )


def current_lan_ipv4_addresses() -> list[str]:
    """返回当前可用于 mDNS 的局域网 IPv4（压缩字符串）；无法获取时返回空列表，不抛异常。

    供后台 watcher 比对地址集合是否变化（WiFi 晚到、DHCP 换 IP 等）。
    """
    try:
        addresses = collect_local_addresses()
    except Exception:
        return []
    return [
        address.compressed
        for address in addresses
        if address.version == 4 and not address.is_link_local
    ]


def prioritize_broadcast_addresses(addresses: list[ipaddress._BaseAddress]) -> list[ipaddress._BaseAddress]:
    preferred = [address for address in addresses if not address.is_link_local]
    return preferred if preferred else addresses


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
    supports_power_shutdown: bool = False,
    platform: str = "python",
    host_name: str | None = None,
    addresses: list[ipaddress._BaseAddress] | None = None,
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
    if supports_power_shutdown:
        props["power_shutdown"] = "1"
    if host_name:
        props["host_name"] = host_name
    if addresses:
        ipv4_candidates = [address.compressed for address in addresses if address.version == 4]
        if ipv4_candidates:
            props["ipv4_list"] = ",".join(ipv4_candidates[:8])
    return props


def _display_host_name(service_name: str) -> str:
    prefix = "LocalManager-"
    if service_name.startswith(prefix):
        stripped = service_name.removeprefix(prefix).strip()
        return stripped or service_name
    return service_name


def build_service_info(
    service_type: str,
    service_name: str,
    hostname: str,
    port: int,
    instance_id: str,
    tls_fingerprint: str,
    *,
    auth_required: bool,
    supports_power_shutdown: bool = False,
    platform: str = "python",
) -> ServiceInfo:
    addresses = collect_mdns_broadcast_addresses()
    LOGGER.info(
        "用于 mDNS 广播的本地地址: %s",
        ", ".join(address.compressed for address in addresses),
    )
    full_name = f"{service_name}.{service_type}"
    properties = build_service_properties(
        instance_id,
        tls_fingerprint,
        auth_required=auth_required,
        supports_power_shutdown=supports_power_shutdown,
        platform=platform,
        host_name=_display_host_name(service_name),
        addresses=addresses,
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


def text_response(
    handler: BaseHTTPRequestHandler,
    status: int,
    text: str,
    content_type: str = "text/plain; charset=utf-8",
) -> None:
    encoded = text.encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", content_type)
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
