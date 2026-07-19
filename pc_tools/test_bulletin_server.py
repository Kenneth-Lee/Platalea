from __future__ import annotations

import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from platalea.bulletin_server import ServerConfig, load_config, run_server


class BulletinServerShutdownFlagTest(unittest.TestCase):
    def test_load_config_defaults_power_shutdown_on(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cfg = root / "config.json"
            cfg.write_text(
                json.dumps(
                    {
                        "roles": {
                            "admin": {"password": "host"},
                            "guest": {"password": "guest"},
                        },
                    },
                    ensure_ascii=False,
                ) + "\n",
                encoding="utf-8",
            )
            with mock.patch.dict(os.environ, {"PLATALEA_POWER_SHUTDOWN": ""}, clear=False):
                config, _agent = load_config(cfg)
            self.assertTrue(config.supports_power_shutdown)

    def test_load_config_uses_config_flag_without_env(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cfg = root / "config.json"
            cfg.write_text(
                json.dumps(
                    {
                        "roles": {
                            "admin": {"password": "host"},
                            "guest": {"password": "guest"},
                        },
                        "supports_power_shutdown": True,
                    },
                    ensure_ascii=False,
                ) + "\n",
                encoding="utf-8",
            )
            with mock.patch.dict(os.environ, {"PLATALEA_POWER_SHUTDOWN": ""}, clear=False):
                config, _agent = load_config(cfg)
            self.assertTrue(config.supports_power_shutdown)

    def test_load_config_env_remains_fallback(self) -> None:
        """当配置项缺失时，默认开启远程关机（不再依赖环境变量）。"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cfg = root / "config.json"
            cfg.write_text(
                json.dumps(
                    {
                        "roles": {
                            "admin": {"password": "host"},
                            "guest": {"password": "guest"},
                        },
                    },
                    ensure_ascii=False,
                ) + "\n",
                encoding="utf-8",
            )
            # 即使环境变量未设置，也应该默认开启
            with mock.patch.dict(os.environ, {"PLATALEA_POWER_SHUTDOWN": ""}, clear=False):
                config, _agent = load_config(cfg)
            self.assertTrue(config.supports_power_shutdown)

    def test_load_config_allows_explicit_disable(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cfg = root / "config.json"
            cfg.write_text(
                json.dumps(
                    {
                        "roles": {
                            "admin": {"password": "host"},
                            "guest": {"password": "guest"},
                        },
                        "supports_power_shutdown": False,
                    },
                    ensure_ascii=False,
                ) + "\n",
                encoding="utf-8",
            )
            with mock.patch.dict(os.environ, {"PLATALEA_POWER_SHUTDOWN": "1"}, clear=False):
                config, _agent = load_config(cfg)
            self.assertFalse(config.supports_power_shutdown)

    def test_run_server_attaches_power_shutdown_flag_to_https_server(self) -> None:
        class FakeHttpsServer:
            def __init__(self) -> None:
                self.store = None
                self.auth_service = None
                self.max_import_bytes = None
                self.supports_power_shutdown = None

            def shutdown(self) -> None:
                self.shutdown_called = True

            def server_close(self) -> None:
                self.server_close_called = True

        class FakeThread:
            def join(self) -> None:
                self.join_called = True

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cert_file = root / "cert.pem"
            key_file = root / "key.pem"
            cert_file.write_text("-----BEGIN CERTIFICATE-----\n-----END CERTIFICATE-----\n", encoding="utf-8")
            key_file.write_text("-----BEGIN PRIVATE KEY-----\n-----END PRIVATE KEY-----\n", encoding="utf-8")
            fake_server = FakeHttpsServer()
            fake_thread = FakeThread()
            fake_config = ServerConfig(
                listen_host="0.0.0.0",
                port=8765,
                service_name="LocalManager-test",
                hostname="test.local",
                service_type="_localmanager._tcp.",
                board_root=root / "boards",
                roles=mock.Mock(auth_required=False, roles={}),
                cert_file=cert_file,
                key_file=key_file,
                instance_id_file=root / "instance_id",
                log_level="INFO",
                max_import_bytes=None,
                supports_power_shutdown=True,
            )

            with mock.patch("platalea.bulletin_server.start_https_server", return_value=(fake_server, fake_thread, "fingerprint")), \
                mock.patch("platalea.bulletin_server.write_server_pid"), \
                mock.patch("platalea.bulletin_server.BulletinBoardStore"), \
                mock.patch("platalea.bulletin_server.AuthService") as auth_service_cls, \
                mock.patch("platalea.bulletin_server.load_or_create_instance_id", return_value="instance"), \
                mock.patch("platalea.bulletin_server.normalize_service_type", return_value="_localmanager._tcp."), \
                mock.patch("platalea.bulletin_server.normalize_hostname", return_value="test.local"), \
                mock.patch("platalea.bulletin_server.build_service_info"), \
                mock.patch("platalea.bulletin_server.Zeroconf") as zeroconf_cls:
                auth_service_cls.return_value = mock.Mock()
                zeroconf = mock.Mock()
                zeroconf_cls.return_value = zeroconf
                zeroconf.register_service.side_effect = KeyboardInterrupt()

                result = run_server(fake_config, agent_config=None)

        self.assertEqual(result, 0)
        self.assertTrue(fake_server.supports_power_shutdown)
        self.assertTrue(getattr(fake_server, "shutdown_called", False))
        self.assertTrue(getattr(fake_server, "server_close_called", False))


if __name__ == "__main__":
    unittest.main()