"""Tests for macOS launchd service control rendering."""
from __future__ import annotations

import os
import plistlib
import tempfile
import unittest
from pathlib import Path

from unittest import mock

from platalea.service_control.models import ActiveOwner, PrivilegedUnitSpec, UserServerUnitSpec
from platalea.service_control.platform.macos_launchd import (
    MacOSLaunchdAdapter,
    PRIVILEGED_LABEL,
    USER_SERVER_LABEL,
    launchd_layout,
    render_privileged_plist,
    render_user_server_plist,
)


class MacOSLaunchdRenderTest(unittest.TestCase):
    def test_render_privileged_plist_contains_expected_fields(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            payload = plistlib.loads(
                render_privileged_plist(
                    PrivilegedUnitSpec(
                        label=PRIVILEGED_LABEL,
                        python_executable="/usr/bin/python3",
                        broker_module="platalea.service_control.broker_server",
                        state_dir=tmp,
                        working_directory=tmp,
                        environment={"PYTHONPATH": tmp},
                    )
                )
            )
            self.assertEqual(payload["Label"], PRIVILEGED_LABEL)
            self.assertIn("platalea.service_control.broker_server", payload["ProgramArguments"])
            self.assertEqual(payload["ProgramArguments"][0], "/usr/bin/python3")
            self.assertEqual(payload["WorkingDirectory"], tmp)
            self.assertEqual(payload["EnvironmentVariables"]["PYTHONPATH"], tmp)
            self.assertEqual(payload["RunAtLoad"], True)
            self.assertEqual(payload["KeepAlive"], True)

    def test_render_user_server_plist_contains_owner_and_serve_daemon(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            owner = ActiveOwner(uid=501, username="kenny", home=str(root))
            payload = plistlib.loads(
                render_user_server_plist(
                    UserServerUnitSpec(
                        label=USER_SERVER_LABEL,
                        owner=owner,
                        program_arguments=[
                            "/usr/bin/python3",
                            "-m",
                            "platalea",
                            "_serve-daemon",
                            "--config",
                            str(root / "config.json"),
                        ],
                        working_directory=str(root),
                        stdout_path=str(root / "stdout.log"),
                        stderr_path=str(root / "stderr.log"),
                        environment={"PYTHONPATH": str(root)},
                    )
                )
            )
            self.assertEqual(payload["Label"], USER_SERVER_LABEL)
            self.assertEqual(payload["UserName"], "kenny")
            self.assertIn("_serve-daemon", payload["ProgramArguments"])
            self.assertEqual(payload["WorkingDirectory"], str(root))
            self.assertEqual(payload["EnvironmentVariables"]["PYTHONPATH"], str(root))


class MacOSLaunchdAdapterTest(unittest.TestCase):
    def test_install_calls_launchctl_sequence(self) -> None:
        calls: list[list[str]] = []

        def fake_runner(cmd, check, capture_output, text):
            calls.append(cmd)
            class P:
                returncode = 0
                stdout = ""
                stderr = ""
            return P()

        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(os.environ, {"PLATALEA_LAUNCHD_DIR": tmp}):
            adapter = MacOSLaunchdAdapter(command_runner=fake_runner)
            spec = PrivilegedUnitSpec(
                label=PRIVILEGED_LABEL,
                python_executable="/usr/bin/python3",
                broker_module="platalea.service_control.broker_server",
                state_dir=tmp,
                working_directory=tmp,
            )
            adapter.install_privileged_unit(spec)
            plist = str(launchd_layout().privileged_plist)
            self.assertTrue(any(cmd[:3] == ["/bin/launchctl", "bootstrap", "system"] and cmd[3] == plist for cmd in calls))
            self.assertTrue(any(cmd[:3] == ["/bin/launchctl", "kickstart", "-k"] for cmd in calls))

    def test_query_status_uses_launchctl_print(self) -> None:
        calls: list[list[str]] = []

        def fake_runner(cmd, check, capture_output, text):
            calls.append(cmd)
            class P:
                returncode = 0
                stdout = "ok"
                stderr = ""
            return P()

        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(os.environ, {"PLATALEA_LAUNCHD_DIR": tmp}):
            adapter = MacOSLaunchdAdapter(command_runner=fake_runner)
            status = adapter.query_status()
            self.assertEqual(status.platform, "macos")
            self.assertTrue(any(cmd[:3] == ["/bin/launchctl", "print", "system/com.localmanager.platalea.privileged"] for cmd in calls))
            self.assertTrue(any(cmd[:3] == ["/bin/launchctl", "print", "system/com.localmanager.platalea.server"] for cmd in calls))


if __name__ == "__main__":
    unittest.main()