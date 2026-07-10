from __future__ import annotations

import ipaddress
import unittest

from platalea.family_common import build_service_properties, prioritize_broadcast_addresses


class FamilyCommonAddressSelectionTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
