"""Tests for platalea.power_cmd."""
from __future__ import annotations

import io
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

import platalea.power_cmd as pc


class PowerCmdTest(unittest.TestCase):
    def test_shutdown_requires_yes_flag(self) -> None:
        buf = io.StringIO()
        with redirect_stderr(buf):
            rc = pc.run_power_shutdown([])
        self.assertEqual(rc, 2)
        self.assertIn("--yes", buf.getvalue())

    def test_shutdown_ok(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            old_paths = pc.service_control_paths
            old_req = pc.request_broker
            try:
                class P:
                    state_root = __import__("pathlib").Path(tmp)

                pc.service_control_paths = lambda: P()
                pc.request_broker = lambda **_kwargs: {"ok": True, "message": "submitted"}

                out = io.StringIO()
                with redirect_stdout(out):
                    rc = pc.run_power_shutdown(["--yes"])
                self.assertEqual(rc, 0)
                self.assertIn("submitted", out.getvalue())
            finally:
                pc.service_control_paths = old_paths
                pc.request_broker = old_req

    def test_power_status_ok(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            old_paths = pc.service_control_paths
            old_req = pc.request_broker
            try:
                class P:
                    state_root = __import__("pathlib").Path(tmp)

                pc.service_control_paths = lambda: P()
                pc.request_broker = lambda **_kwargs: {"ok": True, "pid": 123, "owner_uid": 501}
                out = io.StringIO()
                with redirect_stdout(out):
                    rc = pc.run_power_status([])
                self.assertEqual(rc, 0)
                text = out.getvalue()
                self.assertIn("broker_pid: 123", text)
                self.assertIn("owner_uid: 501", text)
            finally:
                pc.service_control_paths = old_paths
                pc.request_broker = old_req


if __name__ == "__main__":
    unittest.main()
