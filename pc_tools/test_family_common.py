from __future__ import annotations

import ipaddress
import unittest
from unittest import mock

from platalea.family_common import (
    _default_route_ready,
    build_service_properties,
    collect_mdns_broadcast_addresses,
    is_usable_ip,
    prioritize_broadcast_addresses,
)


class FamilyCommonAddressSelectionTest(unittest.TestCase):
    def test_is_usable_ip_filters_non_lan_special_ranges(self) -> None:
        self.assertTrue(is_usable_ip("192.168.8.10"))
        self.assertTrue(is_usable_ip("10.0.0.5"))
        self.assertTrue(is_usable_ip("fc00::1"))
        self.assertFalse(is_usable_ip("253.157.14.165"))
        self.assertFalse(is_usable_ip("2001::1"))

    def test_prefer_non_link_local_when_available(self) -> None:
        addresses = [
            ipaddress.ip_address("169.254.1.134"),
            ipaddress.ip_address("192.168.8.10"),
            ipaddress.ip_address("10.0.0.5"),
        ]

        prioritized = prioritize_broadcast_addresses(addresses)

        self.assertEqual(
            [address.compressed for address in prioritized],
            ["192.168.8.10", "10.0.0.5"],
        )
        self.assertNotIn("169.254.1.134", [address.compressed for address in prioritized])

    def test_keep_link_local_when_it_is_the_only_choice(self) -> None:
        addresses = [
            ipaddress.ip_address("169.254.1.134"),
            ipaddress.ip_address("fe80::1"),
        ]

        prioritized = prioritize_broadcast_addresses(addresses)

        self.assertEqual([address.compressed for address in prioritized], ["169.254.1.134", "fe80::1"])

    def test_build_service_properties_emits_ipv4_candidates(self) -> None:
        props = build_service_properties(
            "instance",
            "fingerprint",
            auth_required=True,
            supports_power_shutdown=True,
            addresses=[
                ipaddress.ip_address("192.168.8.20"),
                ipaddress.ip_address("10.8.62.18"),
                ipaddress.ip_address("fe80::1"),
            ],
        )

        self.assertEqual(props.get("ipv4_list"), "192.168.8.20,10.8.62.18")


class DefaultRouteReadinessTest(unittest.TestCase):
    def test_ready_when_default_route_cannot_be_determined(self) -> None:
        with mock.patch("platalea.family_common._default_route_interface", return_value=None):
            self.assertTrue(_default_route_ready())

    def test_not_ready_when_default_iface_has_no_ipv4(self) -> None:
        with mock.patch("platalea.family_common._default_route_interface", return_value="en1"), \
             mock.patch("platalea.family_common._interface_ipv4_addresses", return_value=[]):
            self.assertFalse(_default_route_ready())

    def test_not_ready_when_default_iface_only_has_link_local(self) -> None:
        with mock.patch("platalea.family_common._default_route_interface", return_value="en1"), \
             mock.patch("platalea.family_common._interface_ipv4_addresses", return_value=["169.254.1.1"]):
            self.assertFalse(_default_route_ready())

    def test_ready_when_default_iface_has_real_ipv4(self) -> None:
        with mock.patch("platalea.family_common._default_route_interface", return_value="en1"), \
             mock.patch("platalea.family_common._interface_ipv4_addresses", return_value=["192.168.1.160"]):
            self.assertTrue(_default_route_ready())


class CollectMdnsBroadcastAddressesTest(unittest.TestCase):
    def test_falls_back_when_only_virtual_iface_ready_at_boot(self) -> None:
        # 开机时序：ZeroTier 虚拟地址已就绪，但默认路由物理网卡 en1 尚未拿到 IP。
        # 超时后不能崩（bootstrap 不会重拉 daemon），应返回现有地址作为兜底，
        # 由后台 watcher 在物理网卡就绪后重新绑定。
        zt = ipaddress.ip_address("10.8.62.18")
        with mock.patch("platalea.family_common.collect_local_addresses", return_value=[zt]), \
             mock.patch("platalea.family_common._default_route_interface", return_value="en1"), \
             mock.patch("platalea.family_common._interface_ipv4_addresses", return_value=[]):
            result = collect_mdns_broadcast_addresses(
                timeout_seconds=0.2, poll_interval_seconds=0.05
            )
        self.assertEqual([address.compressed for address in result], ["10.8.62.18"])

    def test_raises_when_no_address_at_all(self) -> None:
        # 完全没有任何地址时仍应报错（真正的网络未就绪）。
        with mock.patch("platalea.family_common.collect_local_addresses", side_effect=RuntimeError("no addr")), \
             mock.patch("platalea.family_common._default_route_interface", return_value="en1"), \
             mock.patch("platalea.family_common._interface_ipv4_addresses", return_value=[]):
            with self.assertRaises(RuntimeError):
                collect_mdns_broadcast_addresses(
                    timeout_seconds=0.2, poll_interval_seconds=0.05
                )

    def test_returns_addresses_once_default_route_ready(self) -> None:
        zt = ipaddress.ip_address("10.8.62.18")
        lan = ipaddress.ip_address("192.168.1.160")
        with mock.patch("platalea.family_common.collect_local_addresses", return_value=[zt, lan]), \
             mock.patch("platalea.family_common._default_route_interface", return_value="en1"), \
             mock.patch("platalea.family_common._interface_ipv4_addresses", return_value=["192.168.1.160"]):
            result = collect_mdns_broadcast_addresses(
                timeout_seconds=1.0, poll_interval_seconds=0.05
            )
        compressed = [address.compressed for address in result]
        self.assertIn("10.8.62.18", compressed)
        self.assertIn("192.168.1.160", compressed)


if __name__ == "__main__":
    unittest.main()
