"""Tests for platalea.obfuscate."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from platalea.obfuscate import EXTENSION, HEADER_SIZE, process_file


class ObfuscateTest(unittest.TestCase):
    def test_round_trip_single_file(self) -> None:
        password = b"test-pass"
        original = b"hello world" + b"x" * 5000
        with tempfile.TemporaryDirectory() as tmp:
            inp = Path(tmp) / "sample.bin"
            obf = Path(tmp) / f"sample.bin{EXTENSION}"
            deob = Path(tmp) / "sample.out.bin"
            inp.write_bytes(original)

            self.assertTrue(process_file(inp, password, obf, False))
            self.assertTrue(process_file(obf, password, deob, False))
            self.assertEqual(deob.read_bytes(), original)

    def test_header_only_xor(self) -> None:
        password = b"p"
        tail = b"TAIL" * 2000
        original = b"A" * HEADER_SIZE + tail
        with tempfile.TemporaryDirectory() as tmp:
            inp = Path(tmp) / "big.bin"
            mid = Path(tmp) / "big.bin.qx"
            out = Path(tmp) / "restored.bin"
            inp.write_bytes(original)
            process_file(inp, password, mid, False)
            process_file(mid, password, out, False)
            restored = out.read_bytes()
            self.assertEqual(restored[HEADER_SIZE:], tail)
            self.assertEqual(restored, original)


if __name__ == "__main__":
    unittest.main()
