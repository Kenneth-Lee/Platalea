"""Tests for Linux/Windows service control adapters rendering and status parsing."""
from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from platalea.service_control.models import ActiveOwner, PrivilegedUnitSpec, UserServerUnitSpec
from platalea.service_control.platform.linux_systemd import (
    BOOTSTRAP_LABEL as LINUX_BOOTSTRAP_LABEL,
    PRIVILEGED_LABEL as LINUX_PRIVILEGED_LABEL,
    LinuxSystemdAdapter,
    render_bootstrap_unit,
    render_privileged_unit,
)
from platalea.service_control.platform.windows_sc import (
    BOOTSTRAP_LABEL as WINDOWS_BOOTSTRAP_LABEL,
    PRIVILEGED_LABEL as WINDOWS_PRIVILEGED_LABEL,
    WindowsServiceAdapter,
)


class LinuxSystemdRenderTest(unittest.TestCase):
    def test_render_privileged_unit_contains_expected_fields(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            text = render_privileged_unit(
                PrivilegedUnitSpec(
                    label=LINUX_PRIVILEGED_LABEL,
                    python_executable="/usr/bin/python3",
                    broker_module="platalea.service_control.broker_server",
                    state_dir=tmp,
                    working_directory=tmp,
                    environment={"PYTHONPATH": tmp},
                )
            )
            self.assertIn("[Service]", text)
            self.assertIn("ExecStart=/usr/bin/python3 -m", text)
            self.assertIn("platalea.service_control.broker_server", text)
            self.assertIn("WantedBy=multi-user.target", text)

    def test_render_bootstrap_unit_contains_user_and_start(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            owner = ActiveOwner(uid=1000, username="kenny", home=tmp)
            text = render_bootstrap_unit(
                UserServerUnitSpec(
                    label=LINUX_BOOTSTRAP_LABEL,
                    owner=owner,
                    program_arguments=["/usr/bin/python3", "-m", "platalea", "start"],
                    working_directory=tmp,
                    stdout_path=str(Path(tmp) / "stdout.log"),
                    stderr_path=str(Path(tmp) / "stderr.log"),
                    environment={"PYTHONPATH": tmp},
                )
            )
            self.assertIn("User=kenny", text)
            self.assertIn("ExecStart=/usr/bin/python3 -m platalea start", text)
            self.assertIn("Type=oneshot", text)


class LinuxSystemdAdapterTest(unittest.TestCase):
    def test_query_status_sets_linux_platform(self) -> None:
        calls: list[list[str]] = []

        def fake_runner(cmd, check, capture_output, text):
            calls.append(cmd)

            class P:
                returncode = 0
                stdout = "ok"
                stderr = ""

            return P()

        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(os.environ, {"PLATALEA_SYSTEMD_DIR": tmp}):
            adapter = LinuxSystemdAdapter(command_runner=fake_runner)
            status = adapter.query_status()
            self.assertEqual(status.platform, "linux")
            self.assertTrue(
                any(cmd[:3] == ["systemctl", "is-enabled", f"{LINUX_PRIVILEGED_LABEL}.service"] for cmd in calls)
            )
            self.assertTrue(
                any(cmd[:3] == ["systemctl", "is-active", f"{LINUX_BOOTSTRAP_LABEL}.service"] for cmd in calls)
            )


class WindowsServiceAdapterTest(unittest.TestCase):
    def test_install_user_server_requires_credentials(self) -> None:
        calls: list[list[str]] = []

        def fake_runner(cmd, check, capture_output, text):
            calls.append(cmd)

            class P:
                returncode = 0
                stdout = "ok"
                stderr = ""

            return P()

        with tempfile.TemporaryDirectory() as tmp, mock.patch(
            "platalea.service_control.platform.windows_sc.service_control_paths",
            return_value=type("Paths", (), {"state_root": Path(tmp)})(),
        ), mock.patch(
            "platalea.service_control.platform.windows_sc._is_admin",
            return_value=True,
        ):
            adapter = WindowsServiceAdapter(command_runner=fake_runner)
            owner = ActiveOwner(uid=1000, username="kenny", home=tmp)
            spec = UserServerUnitSpec(
                label=WINDOWS_BOOTSTRAP_LABEL,
                owner=owner,
                program_arguments=["python", "-m", "platalea", "start"],
                working_directory=tmp,
                stdout_path=str(Path(tmp) / "stdout.log"),
                stderr_path=str(Path(tmp) / "stderr.log"),
                environment={"PYTHONPATH": tmp},
            )
            with self.assertRaises(RuntimeError):
                adapter.install_user_server_unit(spec)
            self.assertEqual(len(calls), 0)

    def test_install_user_server_uses_runas_service(self) -> None:
        calls: list[list[str]] = []

        def fake_runner(cmd, check, capture_output, text):
            calls.append(cmd)

            class P:
                returncode = 0
                stdout = "ok"
                stderr = ""

            return P()

        with tempfile.TemporaryDirectory() as tmp, mock.patch(
            "platalea.service_control.platform.windows_sc.service_control_paths",
            return_value=type("Paths", (), {"state_root": Path(tmp)})(),
        ), mock.patch(
            "platalea.service_control.platform.windows_sc._is_admin",
            return_value=True,
        ), mock.patch(
            "platalea.service_control.platform.windows_sc._verify_windows_logon",
        ):
            adapter = WindowsServiceAdapter(
                service_user="kenny",
                service_password="secret-pass",
                command_runner=fake_runner,
            )
            owner = ActiveOwner(uid=1000, username="kenny", home=tmp)
            spec = UserServerUnitSpec(
                label=WINDOWS_BOOTSTRAP_LABEL,
                owner=owner,
                program_arguments=["python", "-m", "platalea", "start"],
                working_directory=tmp,
                stdout_path=str(Path(tmp) / "stdout.log"),
                stderr_path=str(Path(tmp) / "stderr.log"),
                environment={"PYTHONPATH": tmp},
            )
            adapter.install_user_server_unit(spec)
            # 应调用 schtasks /create 创建登录触发任务
            create_calls = [c for c in calls if len(c) > 1 and c[1] == "/create"]
            self.assertTrue(any(c[3] == WINDOWS_BOOTSTRAP_LABEL for c in create_calls))
            # ONLOGON 触发器 + 指定用户身份 + 密码
            self.assertTrue(any("/sc" in c and "ONLOGON" in c for c in create_calls))
            self.assertTrue(any("/ru" in c and "kenny" in c and ".\\kenny" not in c for c in create_calls))
            self.assertTrue(any("/rp" in c and "secret-pass" in c for c in create_calls))
            self.assertTrue(any("/rl" in c and "HIGHEST" in c for c in create_calls))

    def test_install_user_server_rejects_bad_password(self) -> None:
        calls: list[list[str]] = []

        def fake_runner(cmd, check, capture_output, text):
            calls.append(cmd)

            class P:
                returncode = 0
                stdout = "ok"
                stderr = ""

            return P()

        with tempfile.TemporaryDirectory() as tmp, mock.patch(
            "platalea.service_control.platform.windows_sc.service_control_paths",
            return_value=type("Paths", (), {"state_root": Path(tmp)})(),
        ), mock.patch(
            "platalea.service_control.platform.windows_sc._is_admin",
            return_value=True,
        ), mock.patch(
            "platalea.service_control.platform.windows_sc._verify_windows_logon",
            side_effect=RuntimeError("Windows 账户密码校验失败"),
        ):
            adapter = WindowsServiceAdapter(
                service_user="kenny",
                service_password="wrong-pass",
                command_runner=fake_runner,
            )
            owner = ActiveOwner(uid=1000, username="kenny", home=tmp)
            spec = UserServerUnitSpec(
                label=WINDOWS_BOOTSTRAP_LABEL,
                owner=owner,
                program_arguments=["python", "-m", "platalea", "start"],
                working_directory=tmp,
                stdout_path=str(Path(tmp) / "stdout.log"),
                stderr_path=str(Path(tmp) / "stderr.log"),
                environment={},
            )
            with self.assertRaises(RuntimeError):
                adapter.install_user_server_unit(spec)
            # 密码校验失败时不应调用 schtasks /create
            self.assertFalse(any(len(cmd) > 1 and cmd[1] == "/create" for cmd in calls))

    def test_query_status_sets_windows_platform(self) -> None:
        calls: list[list[str]] = []

        def fake_runner(cmd, check, capture_output, text):
            calls.append(cmd)

            class P:
                returncode = 0
                stdout = "ok"
                stderr = ""

            return P()

        with tempfile.TemporaryDirectory() as tmp, mock.patch(
            "platalea.service_control.platform.windows_sc.service_control_paths",
            return_value=type("Paths", (), {"state_root": Path(tmp)})(),
        ):
            adapter = WindowsServiceAdapter(command_runner=fake_runner)
            status = adapter.query_status()
            self.assertEqual(status.platform, "windows")
            self.assertEqual(status.privileged_unit, WINDOWS_PRIVILEGED_LABEL)
            self.assertEqual(status.user_server_unit, WINDOWS_BOOTSTRAP_LABEL)
            self.assertTrue(any(cmd[:2] == ["schtasks", "/query"] and WINDOWS_PRIVILEGED_LABEL in cmd for cmd in calls))
            self.assertTrue(any(cmd[:2] == ["schtasks", "/query"] and WINDOWS_BOOTSTRAP_LABEL in cmd for cmd in calls))


if __name__ == "__main__":
    unittest.main()
