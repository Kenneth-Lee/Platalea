from __future__ import annotations

import tempfile
from pathlib import Path

from lmserver.board_attachment_client import (
    collect_directory_entries,
    meta_to_attachment_ref,
)


def test_meta_to_attachment_ref_file() -> None:
    ref = meta_to_attachment_ref(
        {
            "id": "abc",
            "kind": "file",
            "name": "note.txt",
            "size": 12,
            "mime": "text/plain",
        }
    )
    assert ref == {
        "id": "abc",
        "kind": "file",
        "name": "note.txt",
        "size": 12,
        "mime": "text/plain",
    }


def test_meta_to_attachment_ref_directory() -> None:
    ref = meta_to_attachment_ref(
        {
            "id": "dir1",
            "kind": "directory",
            "name": "bundle",
            "file_count": 2,
            "total_size": 100,
        }
    )
    assert ref["kind"] == "directory"
    assert ref["file_count"] == 2
    assert ref["total_size"] == 100


def test_collect_directory_entries() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "a.txt").write_text("hello", encoding="utf-8")
        sub = root / "sub"
        sub.mkdir()
        (sub / "b.txt").write_text("world", encoding="utf-8")
        entries = collect_directory_entries(root)
        paths = {item["path"] for item in entries}
        assert paths == {"a.txt", "sub/b.txt"}
        assert all(item["size"] > 0 for item in entries)
