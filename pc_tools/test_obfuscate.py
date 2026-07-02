"""Tests for platalea.obfuscate."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from platalea.obfuscate import EXTENSION, HEADER_SIZE, deobfuscate_file_inplace, obfuscate_file_inplace, transform_header_inplace


class ObfuscateTest(unittest.TestCase):
    def test_round_trip_single_file_inplace(self) -> None:
        password = b"test-pass"
        original = b"hello world" + b"x" * 5000
        with tempfile.TemporaryDirectory() as tmp:
            inp = Path(tmp) / "sample.bin"
            inp.write_bytes(original)

            self.assertTrue(obfuscate_file_inplace(inp, password))
            obf = Path(tmp) / f"sample.bin{EXTENSION}"
            self.assertTrue(obf.is_file())
            self.assertFalse(inp.exists())

            self.assertTrue(deobfuscate_file_inplace(obf, password))
            restored = Path(tmp) / "sample.bin"
            self.assertTrue(restored.is_file())
            self.assertEqual(restored.read_bytes(), original)

    def test_large_file_tail_untouched(self) -> None:
        password = b"p"
        tail = b"TAIL" * 500_000
        original = b"A" * HEADER_SIZE + tail
        with tempfile.TemporaryDirectory() as tmp:
            inp = Path(tmp) / "big.bin"
            inp.write_bytes(original)
            obfuscate_file_inplace(inp, password)
            obf = Path(tmp) / f"big.bin{EXTENSION}"
            self.assertEqual(obf.stat().st_size, len(original))
            with obf.open("rb") as handle:
                handle.seek(HEADER_SIZE)
                self.assertEqual(handle.read(), tail)
            deobfuscate_file_inplace(obf, password)
            restored = Path(tmp) / "big.bin"
            self.assertEqual(restored.read_bytes(), original)

    def test_transform_header_only_touches_prefix(self) -> None:
        password = b"x"
        body = b"BODY" * 100_000
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "file.bin"
            path.write_bytes(b"H" * HEADER_SIZE + body)
            before_tail = body[:64]
            transform_header_inplace(path, password)
            with path.open("rb") as handle:
                handle.seek(HEADER_SIZE)
                self.assertEqual(handle.read(64), before_tail)


if __name__ == "__main__":
    unittest.main()
