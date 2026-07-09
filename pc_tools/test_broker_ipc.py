"""Integration tests for broker IPC client/server."""
from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

from platalea.service_control.broker_client import request_broker
from platalea.service_control.models import ActiveOwner, build_control_state
from platalea.service_control.state import save_control_state


class BrokerIpcTest(unittest.TestCase):
    def test_status_and_shutdown_dry_run(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            state_root = Path(tmp)
            owner = ActiveOwner(uid=os.getuid(), username="tester", home=str(state_root))
            save_control_state(
                state_root / "state.json",
                build_control_state(
                    owner=owner,
                    privileged_label="com.localmanager.platalea.privileged",
                    user_server_label="com.localmanager.platalea.server",
                ),
            )

            env = dict(os.environ)
            env["PYTHONPATH"] = str(Path(__file__).resolve().parent)
            proc = subprocess.Popen(
                [
                    sys.executable,
                    "-m",
                    "platalea.service_control.broker_server",
                    "--state-dir",
                    str(state_root),
                    "--dry-run",
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=env,
            )
            try:
                sock = state_root / "broker.sock"
                deadline = time.time() + 5.0
                while time.time() < deadline and not sock.exists():
                    time.sleep(0.05)
                self.assertTrue(sock.exists(), "broker socket not created")

                status = request_broker(state_root=state_root, op="status")
                self.assertTrue(status.get("ok", False))
                self.assertEqual(status.get("owner_uid"), os.getuid())

                shutdown = request_broker(state_root=state_root, op="shutdown")
                self.assertTrue(shutdown.get("ok", False))
                self.assertIn("dry-run", str(shutdown.get("message", "")))
            finally:
                proc.terminate()
                try:
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    proc.kill()


if __name__ == "__main__":
    unittest.main()
