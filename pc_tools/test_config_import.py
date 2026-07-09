"""Tests for platalea.config_import."""
from __future__ import annotations

import base64
import json
import tempfile
import unittest
from pathlib import Path

from platalea.config_import import (
    CATEGORY_GPG,
    CATEGORY_GIT,
    CATEGORY_OTHER,
    DEFAULT_PC_IMPORT_CATEGORIES,
    detect_categories,
    import_mobile_config,
    run_import_config,
)


class ConfigImportTest(unittest.TestCase):
    def test_detect_categories(self) -> None:
        obj = {
            "gpg_public_keys_base64": "YQ==",
            "git_repo_url": "https://example.com/repo.git",
            "family_network_user_name": "kenny",
        }
        cats = detect_categories(obj)
        self.assertIn(CATEGORY_GPG, cats)
        self.assertIn(CATEGORY_GIT, cats)
        self.assertIn(CATEGORY_OTHER, cats)

    def test_import_gpg_and_other(self) -> None:
        pub_bytes = b"-----BEGIN PGP PUBLIC KEY BLOCK-----\ntest\n"
        sec_bytes = b"-----BEGIN PGP PRIVATE KEY BLOCK-----\ntest\n"
        obj = {
            "config_version": 2,
            "gpg_public_keys_base64": base64.b64encode(pub_bytes).decode("ascii"),
            "gpg_secret_keys_base64": base64.b64encode(sec_bytes).decode("ascii"),
            "family_network_user_name": "kenny",
            "family_network_host_name": "pc-home",
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cfg = root / "config.json"
            cfg.write_text("{}", encoding="utf-8")
            imp = root / "imported"
            gpg = root / "gnupg"

            input_file = root / "mobile.json"
            input_file.write_text(json.dumps(obj), encoding="utf-8")

            import platalea.config_import as ci
            import platalea.paths as paths

            old_app = paths.app_dir
            old_cfg = paths.config_path
            old_gnupg = paths.gnupg_dir
            old_imp = paths.imported_dir
            old_ensure = paths.ensure_app_layout
            try:
                paths.app_dir = lambda: root
                paths.config_path = lambda explicit=None: cfg if explicit is None else Path(explicit)
                paths.gnupg_dir = lambda: gpg
                paths.imported_dir = lambda: imp
                paths.ensure_app_layout = lambda: root
                ci.app_dir = lambda: root
                ci.config_path = paths.config_path
                ci.gnupg_dir = paths.gnupg_dir
                ci.imported_dir = paths.imported_dir
                ci.ensure_app_layout = paths.ensure_app_layout

                report = import_mobile_config(input_file, categories={CATEGORY_GPG, CATEGORY_OTHER})
                self.assertEqual((gpg / "pubring.gpg").read_bytes(), pub_bytes)
                self.assertEqual((gpg / "secring.gpg").read_bytes(), sec_bytes)
                cfg_data = json.loads(cfg.read_text(encoding="utf-8"))
                self.assertEqual(cfg_data["imported_from_mobile"]["family_network_user_name"], "kenny")
                self.assertTrue(any("公钥" in action for action in report.actions))
            finally:
                paths.app_dir = old_app
                paths.config_path = old_cfg
                paths.gnupg_dir = old_gnupg
                paths.imported_dir = old_imp
                paths.ensure_app_layout = old_ensure

    def test_default_import_only_pc_useful_categories(self) -> None:
        pub_bytes = b"pub"
        obj = {
            "config_version": 2,
            "gpg_public_keys_base64": base64.b64encode(pub_bytes).decode("ascii"),
            "git_repo_url": "https://example.com/a.git",
            "recent_open_items": "[]",
            "family_network_user_name": "kenny",
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            cfg = root / "config.json"
            cfg.write_text("{}", encoding="utf-8")
            imp = root / "imported"
            gpg = root / "gnupg"
            input_file = root / "mobile.json"
            input_file.write_text(json.dumps(obj), encoding="utf-8")

            import platalea.config_import as ci
            import platalea.paths as paths

            old_app = paths.app_dir
            old_cfg = paths.config_path
            old_gnupg = paths.gnupg_dir
            old_imp = paths.imported_dir
            old_ensure = paths.ensure_app_layout
            try:
                paths.app_dir = lambda: root
                paths.config_path = lambda explicit=None: cfg if explicit is None else Path(explicit)
                paths.gnupg_dir = lambda: gpg
                paths.imported_dir = lambda: imp
                paths.ensure_app_layout = lambda: root
                ci.app_dir = lambda: root
                ci.config_path = paths.config_path
                ci.gnupg_dir = paths.gnupg_dir
                ci.imported_dir = paths.imported_dir
                ci.ensure_app_layout = paths.ensure_app_layout

                report = import_mobile_config(input_file)
                self.assertEqual(report.categories, DEFAULT_PC_IMPORT_CATEGORIES)
                self.assertTrue((gpg / "pubring.gpg").is_file())
                self.assertTrue((imp / "git.json").is_file())
                self.assertFalse((imp / "recent.json").exists())
                self.assertFalse((imp / "app_other.json").exists())
            finally:
                paths.app_dir = old_app
                paths.config_path = old_cfg
                paths.gnupg_dir = old_gnupg
                paths.imported_dir = old_imp
                paths.ensure_app_layout = old_ensure

    def test_list_mode(self) -> None:
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as fh:
            json.dump({"gpg_public_keys_base64": "YQ=="}, fh)
            path = fh.name
        self.assertEqual(run_import_config([path, "--list"]), 0)


if __name__ == "__main__":
    unittest.main()
