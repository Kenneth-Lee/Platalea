#!/usr/bin/env python3
from __future__ import annotations

import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from platalea.bulletin_boardpack import (
    BOARDPACK_FORMAT,
    BOARDPACK_VERSION,
    BoardpackError,
    export_boardpack_from_board_dir,
    import_boardpack_bytes,
)
from platalea.bulletin_store import BulletinBoardStore


class BulletinBoardpackTests(unittest.TestCase):
    def setUp(self) -> None:
        self._tmpdir = tempfile.TemporaryDirectory()
        self.root = Path(self._tmpdir.name)
        self.store = BulletinBoardStore(self.root)

    def tearDown(self) -> None:
        self._tmpdir.cleanup()

    def test_round_trip_preserves_messages(self) -> None:
        board = self.store.create_board("旅行记录", role_ids=("guest",))
        assert board is not None
        self.store.append_message(board.id, "我", "第一天到拉萨")
        pack = self.store.export_boardpack(board.id)
        assert pack is not None

        imported = self.store.import_boardpack(pack, role_ids=())
        self.assertNotEqual(imported.id, board.id)
        self.assertEqual(imported.name, "旅行记录")
        self.assertEqual(imported.role_ids, ())
        snapshot = self.store.snapshot(imported.id)
        assert snapshot is not None
        self.assertEqual(len(snapshot.messages), 1)
        self.assertEqual(snapshot.messages[0].content, "第一天到拉萨")

    def test_import_rejects_broken_format(self) -> None:
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            archive.writestr(
                "manifest.json",
                json.dumps({"format": "broken", "version": 1}),
            )
            archive.writestr("board/meta.json", json.dumps({"id": "x", "name": "x"}))
        with self.assertRaises(ValueError):
            self.store.import_boardpack(buffer.getvalue())

    def test_export_manifest_contents(self) -> None:
        board = self.store.create_board("清单板")
        assert board is not None
        pack = export_boardpack_from_board_dir(
            self.root / board.id,
            source_device="test",
        )
        with zipfile.ZipFile(io.BytesIO(pack)) as archive:
            manifest = json.loads(archive.read("manifest.json"))
        self.assertEqual(manifest["format"], BOARDPACK_FORMAT)
        self.assertEqual(manifest["version"], BOARDPACK_VERSION)
        self.assertEqual(manifest["source"]["board_id"], board.id)

    def test_import_rejects_missing_manifest(self) -> None:
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            archive.writestr("board/meta.json", "{}")
        with self.assertRaises(BoardpackError):
            import_boardpack_bytes(self.root, buffer.getvalue())


if __name__ == "__main__":
    unittest.main()
