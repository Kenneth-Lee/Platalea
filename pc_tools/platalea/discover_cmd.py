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
        self._pending_names: set[str] = set()

    def _refresh(self, name: str, *, timeout_ms: int = 1500) -> bool:
        info = self._zc.get_service_info(self._service_type, name, timeout=timeout_ms)
        if info is None:
            self._pending_names.add(name)
            return False
        record = _build_record(info)
        # Use the DNS-SD full service name as the map key. Different devices may
        # share instance_id when config is copied, but mDNS instance names remain distinct.
        self.records[name] = record
        self._pending_names.discard(name)
        return True

    def resolve_pending(self, *, attempts: int = 2, timeout_ms: int = 2500) -> None:
        for _ in range(max(0, attempts)):
            if not self._pending_names:
                return
            for service_name in list(self._pending_names):
                self._refresh(service_name, timeout_ms=timeout_ms)
            if self._pending_names:
                time.sleep(0.15)

    def add_service(self, zc: Zeroconf, service_type: str, name: str) -> None:
        self._pending_names.add(name)
        self._refresh(name)

    def update_service(self, zc: Zeroconf, service_type: str, name: str) -> None:
        self._pending_names.add(name)
        self._refresh(name)

    def remove_service(self, zc: Zeroconf, service_type: str, name: str) -> None:
        self._pending_names.discard(name)
        self.records.pop(name, None)


def discover_family_services(
    *,
    timeout_seconds: float = 5.0,
    service_type: str = FAMILY_SERVICE_TYPE,
    ip_version: IPVersion = IPVersion.All,
    zeroconf_factory=Zeroconf,
    browser_factory=ServiceBrowser,
) -> list[DiscoveredService]:
    try:
        zc = zeroconf_factory(ip_version=ip_version)
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
        # Retry unresolved services to handle mDNS callback/resolve races seen on some macOS environments.
        listener.resolve_pending(attempts=3, timeout_ms=2500)
        return sorted(
            listener.records.values(),
            key=lambda item: (item.display_name.lower(), item.host_name.lower(), item.port),
        )
    finally:
        cancel = getattr(browser, "cancel", None)
        if callable(cancel):
            cancel()
        zc.close()


def discover_family_services_multi_stack(
    *,
    timeout_seconds: float,
    service_type: str,
    debug: bool = False,
    zeroconf_factory=Zeroconf,
    browser_factory=ServiceBrowser,
) -> list[DiscoveredService]:
    merged: dict[str, DiscoveredService] = {}
    stack_plan = [IPVersion.All, IPVersion.V4Only]
    for stack in stack_plan:
        records = discover_family_services(
            timeout_seconds=timeout_seconds,
            service_type=service_type,
            ip_version=stack,
            zeroconf_factory=zeroconf_factory,
            browser_factory=browser_factory,
        )
        if debug:
            print(f"[debug] stack={stack.name} hits={len(records)}", file=sys.stderr)
        for item in records:
            # Keep records distinct per service identity to avoid cross-device collapsing.
            key = f"{item.name}|{item.host_name}|{item.port}"
            merged[key] = item
    return sorted(
        merged.values(),
        key=lambda item: (item.display_name.lower(), item.host_name.lower(), item.port),
    )


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
    parser.add_argument("--debug", action="store_true", help="输出发现阶段调试信息（stderr）")
    args = parser.parse_args(argv)

    if args.timeout < 0:
        print("错误：--timeout 不能为负数", file=sys.stderr)
        return 2

    try:
        service_type = normalize_service_type(args.service_type)
    except ValueError as exc:
        print(f"发现失败: {exc}", file=sys.stderr)
        return 1

    records = discover_family_services_multi_stack(
        timeout_seconds=args.timeout,
        service_type=service_type,
        debug=args.debug,
    )
    if args.json:
        print(json.dumps([asdict(record) for record in records], ensure_ascii=False, indent=2))
    else:
        _print_human(records, service_type, args.timeout)
    return 0