"""Tests for platalea.service_cmd."""
from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from platalea.service_cmd import build_install_plan
from platalea.service_control.models import ActiveOwner, build_control_state, detect_active_owner
from platalea.service_control.state import save_control_state
import platalea.service_cmd as service_cmd


class ServiceInstallPlanTest(unittest.TestCase):
    def test_build_install_plan_uses_current_owner_and_default_program(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cfg = root / "config.json"
            cfg.write_text("{}\n", encoding="utf-8")

            import platalea.service_cmd as sc
            import platalea.paths as paths

            old_service_paths = paths.service_control_paths
            old_sc_service_paths = sc.service_control_paths
            try:
                paths.service_control_paths = lambda: paths.ServiceControlPaths(
                    state_root=root / "service_control",
                    system_units_dir=root / "service_control" / "system_units",
                    state_file=root / "service_control" / "state.json",
                    logs_dir=root / "service_control" / "logs",
                )
                sc.service_control_paths = paths.service_control_paths
                owner = ActiveOwner(uid=501, username="kenny", home=str(root / "home"))
                plan = build_install_plan(owner=owner, config=cfg)
                self.assertEqual(plan.owner.username, "kenny")
                self.assertFalse(plan.replaced_previous_owner)
                self.assertIn("-m", plan.user_server_spec.program_arguments)
                self.assertIn("platalea", plan.user_server_spec.program_arguments)
                self.assertIn("start", plan.user_server_spec.program_arguments)
                self.assertEqual(plan.user_server_spec.owner.username, "kenny")
                self.assertTrue(plan.user_server_spec.working_directory.endswith("pc_tools"))
                self.assertIn("PYTHONPATH", plan.user_server_spec.environment or {})
                self.assertEqual(plan.user_server_spec.environment.get("PLATALEA_ALLOW_SERVICE_BOOTSTRAP"), "1")
                self.assertEqual(plan.user_server_spec.environment.get("PLATALEA_POWER_SHUTDOWN"), "1")
                self.assertTrue(plan.user_server_spec.stdout_path.startswith(str(root / "home")))
            finally:
                paths.service_control_paths = old_service_paths
                sc.service_control_paths = old_sc_service_paths

    def test_detect_active_owner_prefers_sudo_user(self) -> None:
        mock_pw = type("Pw", (), {"pw_dir": "/Users/kenny", "pw_name": "kenny"})
        with mock.patch("os.geteuid", return_value=0), mock.patch.dict(
            os.environ,
            {
                "SUDO_UID": "501",
                "SUDO_USER": "kenny",
            },
            clear=False,
        ), mock.patch("pwd.getpwuid", return_value=mock_pw):
            owner = detect_active_owner()
            self.assertEqual(owner.uid, 501)
            self.assertEqual(owner.username, "kenny")
            self.assertEqual(owner.home, "/Users/kenny")

    def test_detect_active_owner_works_without_pwd_module(self) -> None:
        with mock.patch("platalea.service_control.models.pwd", None), mock.patch(
            "os.geteuid", return_value=0
        ), mock.patch.dict(
            os.environ,
            {
                "SUDO_UID": "1001",
                "SUDO_USER": "tester",
                "SUDO_HOME": "/tmp/tester",
            },
            clear=False,
        ):
            owner = detect_active_owner()
            self.assertEqual(owner.uid, 1001)
            self.assertEqual(owner.username, "tester")
            self.assertEqual(owner.home, "/tmp/tester")

    def test_detect_active_owner_works_without_getuid(self) -> None:
        with mock.patch.object(os, "geteuid", None), mock.patch.object(os, "getuid", None), mock.patch(
            "getpass.getuser", return_value="plainuser"
        ), mock.patch("pathlib.Path.home", return_value=Path("/tmp/plainuser")):
            owner = detect_active_owner()
            self.assertEqual(owner.uid, -1)
            self.assertEqual(owner.username, "plainuser")
            self.assertEqual(owner.home, "/tmp/plainuser")

    def test_build_install_plan_marks_replaced_previous_owner(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cfg = root / "config.json"
            cfg.write_text("{}\n", encoding="utf-8")

            import platalea.service_cmd as sc
            import platalea.paths as paths

            old_service_paths = paths.service_control_paths
            old_sc_service_paths = sc.service_control_paths
            try:
                service_paths = paths.ServiceControlPaths(
                    state_root=root / "service_control",
                    system_units_dir=root / "service_control" / "system_units",
                    state_file=root / "service_control" / "state.json",
                    logs_dir=root / "service_control" / "logs",
                )
                paths.service_control_paths = lambda: service_paths
                sc.service_control_paths = paths.service_control_paths
                previous = ActiveOwner(uid=502, username="alice", home=str(root / "alice"))
                save_control_state(
                    service_paths.state_file,
                    build_control_state(
                        owner=previous,
                        privileged_label="com.localmanager.platalea.privileged",
                        user_server_label="com.localmanager.platalea.bootstrap",
                    ),
                )
                owner = ActiveOwner(uid=501, username="kenny", home=str(root / "kenny"))
                plan = build_install_plan(owner=owner, config=cfg)
                self.assertTrue(plan.replaced_previous_owner)
                self.assertIsNotNone(plan.previous_owner)
                self.assertEqual(plan.previous_owner.username, "alice")
                raw = json.loads(service_paths.state_file.read_text(encoding="utf-8"))
                self.assertEqual(raw["active_owner"]["username"], "alice")
            finally:
                paths.service_control_paths = old_service_paths
                sc.service_control_paths = old_sc_service_paths


class ServicePlatformBackendTest(unittest.TestCase):
    def test_detect_platform_backend_supports_linux_windows_macos(self) -> None:
        with mock.patch("platform.system", return_value="Darwin"):
            self.assertEqual(service_cmd.detect_platform_backend(), "macos")
        with mock.patch("platform.system", return_value="Linux"):
            self.assertEqual(service_cmd.detect_platform_backend(), "linux")
        with mock.patch("platform.system", return_value="Windows"):
            self.assertEqual(service_cmd.detect_platform_backend(), "windows")

    def test_select_adapter_by_backend(self) -> None:
        with mock.patch.object(service_cmd, "detect_platform_backend", return_value="macos"):
            adapter = service_cmd._select_adapter()
            self.assertEqual(adapter.__class__.__name__, "MacOSLaunchdAdapter")
        with mock.patch.object(service_cmd, "detect_platform_backend", return_value="linux"):
            adapter = service_cmd._select_adapter()
            self.assertEqual(adapter.__class__.__name__, "LinuxSystemdAdapter")
        with mock.patch.object(service_cmd, "detect_platform_backend", return_value="windows"):
            adapter = service_cmd._select_adapter()
            self.assertEqual(adapter.__class__.__name__, "WindowsServiceAdapter")


class ServiceWindowsCredentialTest(unittest.TestCase):
    def test_resolve_windows_install_credentials_from_env(self) -> None:
        with mock.patch.dict(os.environ, {"PLATALEA_TEST_WIN_PASS": "abc123"}, clear=False):
            user, password = service_cmd._resolve_windows_install_credentials(
                default_user="kenny",
                override_user="",
                password_env="PLATALEA_TEST_WIN_PASS",
            )
            self.assertEqual(user, "kenny")
            self.assertEqual(password, "abc123")

    def test_resolve_windows_install_credentials_from_prompt(self) -> None:
        with mock.patch("getpass.getpass", return_value="secret-pass"):
            user, password = service_cmd._resolve_windows_install_credentials(
                default_user="kenny",
                override_user="domain\\kenny",
                password_env="",
            )
            self.assertEqual(user, "domain\\kenny")
            self.assertEqual(password, "secret-pass")

    def test_resolve_windows_install_credentials_empty_env_raises(self) -> None:
        with mock.patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(service_cmd.ServiceControlError):
                service_cmd._resolve_windows_install_credentials(
                    default_user="kenny",
                    override_user="",
                    password_env="PLATALEA_TEST_WIN_PASS",
                )


if __name__ == "__main__":
    unittest.main()