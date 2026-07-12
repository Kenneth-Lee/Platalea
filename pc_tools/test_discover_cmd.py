from __future__ import annotations

import io
import json
import unittest
from contextlib import redirect_stdout
from unittest import mock

from platalea.discover_cmd import discover_family_services, run_discover


class _FakeInfo:
    name = "LocalManager-klmm._localmanager._tcp.local."
    type = "_localmanager._tcp.local."
    server = "klmm.local."
    port = 8765
    properties = {
        b"host_name": b"klmm",
        b"ipv4_list": b"192.168.3.170",
        b"auth": b"1",
        b"power_shutdown": b"1",
        b"platform": b"python",
        b"instance_id": b"instance-1",
        b"tls_fp_sha256": b"abc123",
    }

    def parsed_addresses(self, _version):
        return ["192.168.3.170", "fe80::1"]


class _FakeZeroconf:
    def __init__(self, *args, **kwargs) -> None:
        self.closed = False

    def get_service_info(self, service_type: str, name: str, timeout: int = 1500):
        if service_type == "_localmanager._tcp.local." and name == "LocalManager-klmm._localmanager._tcp.local.":
            return _FakeInfo()
        return None

    def close(self) -> None:
        self.closed = True


class _FakeBrowser:
    def __init__(self, zc, service_type, listener=None, handlers=None, **_kwargs) -> None:
        active_listener = listener or handlers
        if active_listener is None:
            raise AssertionError("listener is required")
        active_listener.add_service(zc, service_type, "LocalManager-klmm._localmanager._tcp.local.")

    def cancel(self) -> None:
        pass


class DiscoverCmdTest(unittest.TestCase):
    def test_discover_family_services_collects_records(self) -> None:
        records = discover_family_services(
            timeout_seconds=0,
            zeroconf_factory=_FakeZeroconf,
            browser_factory=_FakeBrowser,
        )
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0].display_name, "klmm")
        self.assertEqual(records[0].addresses, ["192.168.3.170", "fe80::1"])
        self.assertTrue(records[0].requires_password)
        self.assertTrue(records[0].supports_power_shutdown)

    def test_run_discover_json(self) -> None:
        output = io.StringIO()
        with redirect_stdout(output):
            with mock.patch(
                "platalea.discover_cmd.discover_family_services",
                return_value=discover_family_services(
                    timeout_seconds=0,
                    zeroconf_factory=_FakeZeroconf,
                    browser_factory=_FakeBrowser,
                ),
            ):
                exit_code = run_discover(["--json", "--timeout", "0"])

        self.assertEqual(exit_code, 0)
        parsed = json.loads(output.getvalue())
        self.assertEqual(parsed[0]["display_name"], "klmm")

    def test_discover_family_services_supports_legacy_browser_signature(self) -> None:
        class _LegacyBrowser:
            def __init__(self, zc, service_type, handlers=None, **_kwargs) -> None:
                if handlers is None:
                    raise TypeError("missing handlers")
                handlers.add_service(zc, service_type, "LocalManager-klmm._localmanager._tcp.local.")

            def cancel(self) -> None:
                pass

        records = discover_family_services(
            timeout_seconds=0,
            zeroconf_factory=_FakeZeroconf,
            browser_factory=_LegacyBrowser,
        )
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0].display_name, "klmm")

    def test_discover_family_services_retries_pending_resolution(self) -> None:
        class _DelayedZeroconf(_FakeZeroconf):
            def __init__(self, *args, **kwargs) -> None:
                super().__init__(*args, **kwargs)
                self._calls = 0

            def get_service_info(self, service_type: str, name: str, timeout: int = 1500):
                self._calls += 1
                if self._calls == 1:
                    return None
                return super().get_service_info(service_type, name, timeout=timeout)

        records = discover_family_services(
            timeout_seconds=0,
            zeroconf_factory=_DelayedZeroconf,
            browser_factory=_FakeBrowser,
        )
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0].display_name, "klmm")


if __name__ == "__main__":
    unittest.main()