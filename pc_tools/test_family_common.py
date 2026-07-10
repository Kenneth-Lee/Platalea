from __future__ import annotations

import ipaddress
import unittest

from platalea.family_common import prioritize_broadcast_addresses


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


if __name__ == "__main__":
    unittest.main()
