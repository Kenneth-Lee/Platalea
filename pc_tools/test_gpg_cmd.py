"""Tests for platalea.gpg_cmd."""
from __future__ import annotations

import base64
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from platalea.gpg_cmd import (
    GpgError,
    decode_quick_ciphertext,
    run_pass_decrypt,
    run_pass_encrypt,
    run_quick_decrypt,
    run_quick_encrypt,
)


class DecodeQuickCipherTest(unittest.TestCase):
    def test_base64(self) -> None:
        raw = b"binary ciphertext"
        text = base64.b64encode(raw).decode("ascii")
        self.assertEqual(decode_quick_ciphertext(text), raw)

    def test_base64_with_whitespace(self) -> None:
        raw = b"abc"
        text = "Y\nWJj\n"
        self.assertEqual(decode_quick_ciphertext(text), raw)

    def test_armor(self) -> None:
        armor = "-----BEGIN PGP MESSAGE-----\n\nfoo\n-----END PGP MESSAGE-----"
        self.assertEqual(decode_quick_ciphertext(armor), armor.encode("utf-8"))

    def test_invalid(self) -> None:
        with self.assertRaises(GpgError):
            decode_quick_ciphertext("not-valid!!!")


@unittest.skipUnless(shutil.which("gpg"), "gpg not installed")
class GpgIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls._tmp = tempfile.TemporaryDirectory(prefix="platalea-gpg-test-")
        cls.home = Path(cls._tmp.name)
        subprocess.run(
            [
                "gpg",
                "--homedir",
                str(cls.home),
                "--batch",
                "--passphrase",
                "test-pass",
                "--quick-generate-key",
                "Platalea Test <platalea-test@local>",
                "default",
                "default",
                "never",
            ],
            check=True,
            capture_output=True,
        )
        cls.recipient = "platalea-test@local"

    @classmethod
    def tearDownClass(cls) -> None:
        cls._tmp.cleanup()

    def test_pass_round_trip(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plain = root / "note.md"
            plain.write_text("# secret\n", encoding="utf-8")
            pass_file = root / "note.md.pass"
            self.assertEqual(
                run_pass_encrypt(
                    [
                        str(plain),
                        "-r",
                        self.recipient,
                        "-o",
                        str(pass_file),
                        "--homedir",
                        str(self.home),
                    ]
                ),
                0,
            )
            self.assertTrue(pass_file.read_text(encoding="utf-8").startswith("-----BEGIN PGP MESSAGE-----"))
            out = root / "note.out.md"
            self.assertEqual(
                run_pass_decrypt(
                    [
                        str(pass_file),
                        "-p",
                        "test-pass",
                        "-o",
                        str(out),
                        "--homedir",
                        str(self.home),
                    ]
                ),
                0,
            )
            self.assertEqual(out.read_text(encoding="utf-8"), plain.read_text(encoding="utf-8"))

    def test_quick_round_trip(self) -> None:
        proc = subprocess.run(
            [
                "platalea",
                "quick-encrypt",
                "hello 快密",
                "-r",
                self.recipient,
                "--homedir",
                str(self.home),
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        cipher_b64 = proc.stdout.strip()
        proc2 = subprocess.run(
            [
                "platalea",
                "quick-decrypt",
                cipher_b64,
                "-p",
                "test-pass",
                "--homedir",
                str(self.home),
            ],
            check=True,
            capture_output=True,
        )
        self.assertEqual(proc2.stdout.decode("utf-8"), "hello 快密")


if __name__ == "__main__":
    unittest.main()
