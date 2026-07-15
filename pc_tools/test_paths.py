"""Tests for platalea.paths service control root selection."""
from __future__ import annotations

import os
import unittest
from pathlib import Path
from unittest import mock

import platalea.paths as paths


class ServiceControlPathsTest(unittest.TestCase):
    def test_linux_root_with_sudo_home_uses_owner_root(self) -> None:
        with mock.patch("platform.system", return_value="Linux"), mock.patch(
            "os.geteuid", return_value=0
        ), mock.patch.dict(
            os.environ,
            {
                "SUDO_HOME": "/home/kenny",
                "SUDO_USER": "kenny",
                "SUDO_UID": "1000",
            },
            clear=False,
        ):
            result = paths.service_control_paths()
            self.assertEqual(result.state_root, Path("/home/kenny/.localmanager/service_control"))

    def test_linux_root_with_sudo_uid_uses_pwd_home(self) -> None:
        fake_pw = type("Pw", (), {"pw_dir": "/home/frompwd"})
        with mock.patch("platform.system", return_value="Linux"), mock.patch(
            "os.geteuid", return_value=0
        ), mock.patch.dict(
            os.environ,
            {
                "SUDO_USER": "kenny",
                "SUDO_UID": "1000",
            },
            clear=False,
        ), mock.patch("platalea.paths.pwd.getpwuid", return_value=fake_pw):
            result = paths.service_control_paths()
            self.assertEqual(result.state_root, Path("/home/frompwd/.localmanager/service_control"))

    def test_linux_non_root_uses_current_app_dir(self) -> None:
        with mock.patch("platform.system", return_value="Linux"), mock.patch(
            "os.geteuid", return_value=1000
        ), mock.patch.object(paths, "app_dir", return_value=Path("/home/alice/.localmanager")):
            result = paths.service_control_paths()
            self.assertEqual(result.state_root, Path("/home/alice/.localmanager/service_control"))

    def test_override_has_highest_priority(self) -> None:
        with mock.patch.dict(
            os.environ,
            {
                "PLATALEA_SERVICE_CONTROL_ROOT": "/tmp/custom_sc",
                "SUDO_HOME": "/home/kenny",
            },
            clear=False,
        ):
            result = paths.service_control_paths()
            self.assertEqual(result.state_root, Path("/tmp/custom_sc"))


if __name__ == "__main__":
    unittest.main()
