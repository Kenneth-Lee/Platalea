from __future__ import annotations

import argparse
import json
import sys
import time
from dataclasses import asdict, dataclass

from zeroconf import IPVersion, ServiceBrowser, ServiceInfo, ServiceListener, Zeroconf

from .family_common import FAMILY_SERVICE_TYPE, TLS_FINGERPRINT_ATTR, normalize_service_type


@dataclass(frozen=True)
class DiscoveredService:
    name: str
    service_type: str
    display_name: str
    host_name: str
    addresses: list[str]
    port: int
    requires_password: bool
    supports_power_shutdown: bool
    platform: str
    instance_id: str
    tls_fingerprint: str


def _decode_properties(info: ServiceInfo) -> dict[str, str]:
    result: dict[str, str] = {}
    for key, value in (info.properties or {}).items():
        key_text = key.decode("utf-8", errors="replace") if isinstance(key, bytes) else str(key)
        if isinstance(value, bytes):
            value_text = value.decode("utf-8", errors="replace")
        elif value is None:
            value_text = ""
        else:
            value_text = str(value)
        result[key_text] = value_text
    return result


def _service_display_name(service_name: str, properties: dict[str, str]) -> str:
    host_name = properties.get("host_name", "").strip()
    if host_name:
        return host_name
    prefix = "LocalManager-"
    if service_name.startswith(prefix):
        stripped = service_name.removeprefix(prefix).strip()
        if stripped:
            return stripped
    return service_name


def _build_record(info: ServiceInfo) -> DiscoveredService:
    properties = _decode_properties(info)
    service_name = info.name.removesuffix(f".{info.type}").strip()
    addresses = list(dict.fromkeys(info.parsed_addresses(IPVersion.All)))
    return DiscoveredService(
        name=service_name,
        service_type=info.type,
        display_name=_service_display_name(service_name, properties),
        host_name=str(info.server or "").rstrip("."),
        addresses=addresses,
        port=int(info.port or 0),
        requires_password=properties.get("auth", "").strip() == "1",
        supports_power_shutdown=properties.get("power_shutdown", "").strip() == "1",
        platform=properties.get("platform", "").strip(),
        instance_id=properties.get("instance_id", "").strip(),
        tls_fingerprint=properties.get(TLS_FINGERPRINT_ATTR, "").strip(),
    )


class _Listener(ServiceListener):
    def __init__(self, zc: Zeroconf, service_type: str) -> None:
        self._zc = zc
        self._service_type = service_type
        self.records: dict[str, DiscoveredService] = {}

    def _refresh(self, name: str) -> None:
        info = self._zc.get_service_info(self._service_type, name, timeout=1500)
        if info is None:
            return
        record = _build_record(info)
        key = record.instance_id or record.name or record.display_name
        self.records[key] = record

    def add_service(self, zc: Zeroconf, service_type: str, name: str) -> None:
        self._refresh(name)

    def update_service(self, zc: Zeroconf, service_type: str, name: str) -> None:
        self._refresh(name)

    def remove_service(self, zc: Zeroconf, service_type: str, name: str) -> None:
        stale = [key for key, value in self.records.items() if value.name == name.removesuffix(f".{service_type}")]
        for key in stale:
            self.records.pop(key, None)


def discover_family_services(
    *,
    timeout_seconds: float = 5.0,
    service_type: str = FAMILY_SERVICE_TYPE,
    zeroconf_factory=Zeroconf,
    browser_factory=ServiceBrowser,
) -> list[DiscoveredService]:
    try:
        zc = zeroconf_factory(ip_version=IPVersion.All)
    except TypeError:
        # Older zeroconf builds (seen on some macOS Python envs) may not accept ip_version.
        zc = zeroconf_factory()
    listener = _Listener(zc, service_type)
    try:
        browser = browser_factory(zc, service_type, listener=listener)
    except TypeError:
        # Compatibility fallback for zeroconf variants that use handlers instead of listener.
        browser = browser_factory(zc, service_type, handlers=listener)
    try:
        time.sleep(max(0.0, timeout_seconds))
        return sorted(
            listener.records.values(),
            key=lambda item: (item.display_name.lower(), item.host_name.lower(), item.port),
        )
    finally:
        cancel = getattr(browser, "cancel", None)
        if callable(cancel):
            cancel()
        zc.close()


def _print_human(records: list[DiscoveredService], service_type: str, timeout_seconds: float) -> None:
    if not records:
        print(f"未发现 {service_type} 服务")
        print("排查建议:")
        print(f"  1) 增加等待时间后重试：platalea discover --timeout {max(8, int(timeout_seconds) + 3)}")
        print("  2) 确认服务端已运行并在广播 mDNS（platalea status / platalea start）")
        print("  3) macOS 下确认同一网段且未被防火墙拦截 Bonjour/mDNS（UDP 5353）")
        return
    for idx, item in enumerate(records, start=1):
        addresses = ", ".join(item.addresses) if item.addresses else "<unknown>"
        tags = []
        if item.requires_password:
            tags.append("auth")
        if item.supports_power_shutdown:
            tags.append("power-shutdown")
        if item.tls_fingerprint:
            tags.append("tls")
        tag_text = f" [{' '.join(tags)}]" if tags else ""
        print(f"[{idx}] {item.display_name}{tag_text}")
        print(f"  service_name: {item.name}")
        print(f"  host_name: {item.host_name or '<unknown>'}")
        print(f"  addresses: {addresses}")
        print(f"  port: {item.port}")
        print(f"  auth_required: {'yes' if item.requires_password else 'no'}")
        print(f"  power_shutdown: {'yes' if item.supports_power_shutdown else 'no'}")
        if item.platform:
            print(f"  platform: {item.platform}")
        if item.instance_id:
            print(f"  instance_id: {item.instance_id}")


def run_discover(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="platalea discover",
        description="发现当前局域网内提供家庭网络服务的设备。",
    )
    parser.add_argument("--service-type", default=FAMILY_SERVICE_TYPE, help=f"要发现的服务类型（默认 {FAMILY_SERVICE_TYPE}）")
    parser.add_argument("--timeout", type=float, default=5.0, help="等待发现的秒数（默认 5）")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出发现结果")
    args = parser.parse_args(argv)

    if args.timeout < 0:
        print("错误：--timeout 不能为负数", file=sys.stderr)
        return 2

    try:
        service_type = normalize_service_type(args.service_type)
    except ValueError as exc:
        print(f"发现失败: {exc}", file=sys.stderr)
        return 1

    records = discover_family_services(timeout_seconds=args.timeout, service_type=service_type)
    if args.json:
        print(json.dumps([asdict(record) for record in records], ensure_ascii=False, indent=2))
    else:
        _print_human(records, service_type, args.timeout)
    return 0