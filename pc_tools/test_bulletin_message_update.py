"""Tests for message update and attachment replace."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from platalea.bulletin_attachment_store import BulletinAttachmentStore
from platalea.bulletin_store import BulletinBoardStore
from platalea.board_attachment_client import resolve_directory_file_id


class BulletinMessageUpdateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.store = BulletinBoardStore(self.root)
        self.board_id = "default"
        self.att_store: BulletinAttachmentStore = self.store.attachments

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def _ready_attachment(self, name: str, content: bytes) -> dict:
        init = self.att_store.init_file_upload(self.board_id, name, len(content))
        self.att_store.write_file_chunk(self.board_id, init.attachment_id, 0, content)
        self.att_store.complete_upload(self.board_id, init.attachment_id)
        return {"id": init.attachment_id, "kind": "file", "name": name, "size": len(content)}

    def test_update_content_keeps_attachments(self) -> None:
        att = self._ready_attachment("a.txt", b"data")
        posted = self.store.append_message(
            self.board_id, "author", "hello", "pc", [att]
        )
        self.assertIsNotNone(posted)
        updated = self.store.update_message(
            self.board_id, posted.id, content="updated"
        )
        self.assertIsNotNone(updated)
        self.assertEqual(updated.content, "updated")
        self.assertEqual(len(updated.attachments or []), 1)
        self.assertEqual(updated.attachments[0]["id"], att["id"])

    def test_replace_attachments_deletes_old(self) -> None:
        old = self._ready_attachment("old.txt", b"old")
        posted = self.store.append_message(
            self.board_id, "author", "hello", "pc", [old]
        )
        self.assertIsNotNone(posted)
        old_id = old["id"]
        new = self._ready_attachment("new.txt", b"new")
        updated = self.store.update_message(
            self.board_id, posted.id, attachments=[new]
        )
        self.assertIsNotNone(updated)
        self.assertEqual(updated.content, "hello")
        self.assertEqual(updated.attachments[0]["id"], new["id"])
        self.assertFalse(self.att_store.is_attachment_ready(self.board_id, old_id))

    def test_resolve_directory_file_id(self) -> None:
        meta = {
            "files": [
                {"file_id": "f1", "path": "docs/readme.md"},
                {"file_id": "f2", "path": "skip.bin"},
            ]
        }
        self.assertEqual(resolve_directory_file_id(meta, "docs/readme.md"), "f1")
        with self.assertRaises(ValueError):
            resolve_directory_file_id(meta, "missing.txt")


if __name__ == "__main__":
    unittest.main()
