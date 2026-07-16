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
    BOOTSTRAP_LABEL,
    LEGACY_USER_SERVER_LABEL,
    MacOSLaunchdAdapter,
    PRIVILEGED_LABEL,
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

    def test_render_user_server_plist_contains_owner_and_bootstrap_start(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            owner = ActiveOwner(uid=501, username="kenny", home=str(root))
            payload = plistlib.loads(
                render_user_server_plist(
                    UserServerUnitSpec(
                        label=BOOTSTRAP_LABEL,
                        owner=owner,
                        program_arguments=[
                            "/usr/bin/python3",
                            "-m",
                            "platalea",
                            "start",
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
            self.assertEqual(payload["Label"], BOOTSTRAP_LABEL)
            self.assertEqual(payload["UserName"], "kenny")
            self.assertIn("start", payload["ProgramArguments"])
            self.assertEqual(payload["WorkingDirectory"], str(root))
            self.assertEqual(payload["EnvironmentVariables"]["PYTHONPATH"], str(root))
            self.assertEqual(payload["KeepAlive"], {"SuccessfulExit": False})


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

    def test_install_user_server_cleans_up_legacy_server_unit(self) -> None:
        calls: list[list[str]] = []

        def fake_runner(cmd, check, capture_output, text):
            calls.append(cmd)

            class P:
                returncode = 0
                stdout = ""
                stderr = ""
            return P()

        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(os.environ, {"PLATALEA_LAUNCHD_DIR": tmp}):
            units_dir = Path(tmp)
            legacy_plist = units_dir / f"{LEGACY_USER_SERVER_LABEL}.plist"
            legacy_plist.write_bytes(b"<plist/>")
            self.assertTrue(legacy_plist.exists())

            adapter = MacOSLaunchdAdapter(command_runner=fake_runner)
            owner = ActiveOwner(uid=501, username="kenny", home=tmp)
            spec = UserServerUnitSpec(
                label=BOOTSTRAP_LABEL,
                owner=owner,
                program_arguments=["/usr/bin/python3", "-m", "platalea", "start"],
                working_directory=tmp,
                stdout_path=str(units_dir / "stdout.log"),
                stderr_path=str(units_dir / "stderr.log"),
                environment={},
            )
            adapter.install_user_server_unit(spec)

            # 旧标签必须被 bootout 并删除文件，否则开机会双开守护进程。
            self.assertTrue(
                any(
                    cmd[:2] == ["/bin/launchctl", "bootout"]
                    and f"system/{LEGACY_USER_SERVER_LABEL}" in cmd
                    for cmd in calls
                )
            )
            self.assertFalse(legacy_plist.exists())

    def test_uninstall_cleans_up_legacy_server_unit(self) -> None:
        calls: list[list[str]] = []

        def fake_runner(cmd, check, capture_output, text):
            calls.append(cmd)

            class P:
                returncode = 0
                stdout = ""
                stderr = ""
            return P()

        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(os.environ, {"PLATALEA_LAUNCHD_DIR": tmp}):
            units_dir = Path(tmp)
            legacy_plist = units_dir / f"{LEGACY_USER_SERVER_LABEL}.plist"
            legacy_plist.write_bytes(b"<plist/>")

            adapter = MacOSLaunchdAdapter(command_runner=fake_runner)
            adapter.uninstall_all_units(ignore_missing=True)

            self.assertFalse(legacy_plist.exists())

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
            self.assertTrue(any(cmd[:3] == ["/bin/launchctl", "print", "system/com.localmanager.platalea.bootstrap"] for cmd in calls))


if __name__ == "__main__":
    unittest.main()