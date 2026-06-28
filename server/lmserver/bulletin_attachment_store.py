from __future__ import annotations

import hashlib
import json
import shutil
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any

CHUNK_SIZE = 262144


@dataclass(frozen=True)
class DirectoryEntry:
    path: str
    size: int
    sha256: str | None = None


@dataclass(frozen=True)
class DirectoryFileSlot:
    file_id: str
    path: str
    size: int
    sha256: str | None = None


@dataclass(frozen=True)
class InitUploadResult:
    attachment_id: str
    chunk_size: int
    directory_files: list[DirectoryFileSlot]


@dataclass(frozen=True)
class BlobReadResult:
    status: int
    data: bytes
    total_size: int
    content_range: str | None = None


class BulletinAttachmentStore:
    def __init__(self, boards_root: str | Path) -> None:
        self._boards_root = Path(boards_root)

    def init_file_upload(
        self,
        board_id: str,
        name: str,
        size: int,
        sha256: str | None = None,
        mime: str | None = None,
        uploader_device: str | None = None,
    ) -> InitUploadResult | None:
        if self._read_meta(board_id) is None or size < 0 or not name.strip():
            return None
        attachment_id = str(uuid.uuid4())
        now = int(time.time() * 1000)
        att_dir = self._attachment_dir(board_id, attachment_id)
        att_dir.mkdir(parents=True, exist_ok=True)
        (att_dir / "blob").write_bytes(b"")
        meta = {
            "id": attachment_id,
            "board_id": board_id,
            "kind": "file",
            "name": name.strip(),
            "status": "uploading",
            "size": size,
            "chunk_size": CHUNK_SIZE,
            "uploaded_chunks": [],
            "created_at": now,
            "uploader_device": uploader_device or "",
        }
        if sha256:
            meta["sha256"] = sha256
        if mime:
            meta["mime"] = mime
        self._write_attachment_meta(board_id, attachment_id, meta)
        return InitUploadResult(attachment_id, CHUNK_SIZE, [])

    def init_directory_upload(
        self,
        board_id: str,
        name: str,
        entries: list[DirectoryEntry],
        uploader_device: str | None = None,
    ) -> InitUploadResult | None:
        if self._read_meta(board_id) is None or not name.strip() or not entries:
            return None
        attachment_id = str(uuid.uuid4())
        now = int(time.time() * 1000)
        att_dir = self._attachment_dir(board_id, attachment_id)
        files_dir = att_dir / "files"
        files_dir.mkdir(parents=True, exist_ok=True)
        slots: list[DirectoryFileSlot] = []
        manifest_entries: list[dict[str, Any]] = []
        for entry in entries:
            file_id = str(uuid.uuid4())
            file_dir = files_dir / file_id
            file_dir.mkdir(parents=True, exist_ok=True)
            (file_dir / "blob").write_bytes(b"")
            file_meta = {
                "file_id": file_id,
                "path": entry.path,
                "size": entry.size,
                "uploaded_chunks": [],
            }
            if entry.sha256:
                file_meta["sha256"] = entry.sha256
            (file_dir / "meta.json").write_text(
                json.dumps(file_meta, ensure_ascii=False),
                encoding="utf-8",
            )
            slots.append(DirectoryFileSlot(file_id, entry.path, entry.size, entry.sha256))
            manifest_entries.append(
                {
                    "file_id": file_id,
                    "path": entry.path,
                    "size": entry.size,
                    **({"sha256": entry.sha256} if entry.sha256 else {}),
                }
            )
        (files_dir / "manifest.json").write_text(
            json.dumps({"version": 1, "entries": manifest_entries}, ensure_ascii=False),
            encoding="utf-8",
        )
        total_size = sum(entry.size for entry in entries)
        meta = {
            "id": attachment_id,
            "board_id": board_id,
            "kind": "directory",
            "name": name.strip(),
            "status": "uploading",
            "file_count": len(slots),
            "total_size": total_size,
            "chunk_size": CHUNK_SIZE,
            "created_at": now,
            "uploader_device": uploader_device or "",
        }
        self._write_attachment_meta(board_id, attachment_id, meta)
        return InitUploadResult(attachment_id, CHUNK_SIZE, slots)

    def write_file_chunk(
        self, board_id: str, attachment_id: str, chunk_index: int, data: bytes
    ) -> dict[str, Any] | None:
        if chunk_index < 0 or not data:
            return None
        meta = self._read_attachment_meta(board_id, attachment_id)
        if meta is None or meta.get("status") != "uploading" or meta.get("kind") != "file":
            return None
        size = int(meta.get("size", 0))
        chunk_size = int(meta.get("chunk_size", CHUNK_SIZE))
        offset = chunk_index * chunk_size
        if offset >= size:
            return None
        blob = self._attachment_dir(board_id, attachment_id) / "blob"
        with blob.open("r+b") as handle:
            handle.seek(offset)
            handle.write(data)
        chunks = set(meta.get("uploaded_chunks") or [])
        chunks.add(chunk_index)
        meta["uploaded_chunks"] = sorted(chunks)
        self._write_attachment_meta(board_id, attachment_id, meta)
        return {"chunk_index": chunk_index, "received": len(data)}

    def write_directory_file_chunk(
        self,
        board_id: str,
        attachment_id: str,
        file_id: str,
        chunk_index: int,
        data: bytes,
    ) -> dict[str, Any] | None:
        if chunk_index < 0 or not data:
            return None
        attachment_meta = self._read_attachment_meta(board_id, attachment_id)
        if (
            attachment_meta is None
            or attachment_meta.get("status") != "uploading"
            or attachment_meta.get("kind") != "directory"
        ):
            return None
        file_dir = self._attachment_dir(board_id, attachment_id) / "files" / file_id
        file_meta = self._read_file_meta(file_dir)
        if file_meta is None:
            return None
        size = int(file_meta.get("size", 0))
        chunk_size = int(attachment_meta.get("chunk_size", CHUNK_SIZE))
        offset = chunk_index * chunk_size
        if offset >= size:
            return None
        blob = file_dir / "blob"
        with blob.open("r+b") as handle:
            handle.seek(offset)
            handle.write(data)
        chunks = set(file_meta.get("uploaded_chunks") or [])
        chunks.add(chunk_index)
        file_meta["uploaded_chunks"] = sorted(chunks)
        (file_dir / "meta.json").write_text(
            json.dumps(file_meta, ensure_ascii=False),
            encoding="utf-8",
        )
        return {"chunk_index": chunk_index, "received": len(data)}

    def complete_upload(self, board_id: str, attachment_id: str) -> dict[str, Any] | None:
        meta = self._read_attachment_meta(board_id, attachment_id)
        if meta is None or meta.get("status") != "uploading":
            return None
        kind = meta.get("kind", "file")
        chunk_size = int(meta.get("chunk_size", CHUNK_SIZE))
        if kind == "file":
            size = int(meta.get("size", 0))
            if not self._chunks_complete(set(meta.get("uploaded_chunks") or []), size, chunk_size):
                return None
            blob = self._attachment_dir(board_id, attachment_id) / "blob"
            expected = meta.get("sha256")
            if expected and self._sha256_hex(blob) != str(expected).lower():
                return None
        elif kind == "directory":
            files_dir = self._attachment_dir(board_id, attachment_id) / "files"
            manifest = self._read_manifest(files_dir)
            if manifest is None:
                return None
            for entry in manifest:
                file_id = str(entry["file_id"])
                file_dir = files_dir / file_id
                file_meta = self._read_file_meta(file_dir)
                if file_meta is None:
                    return None
                file_size = int(file_meta.get("size", 0))
                if not self._chunks_complete(
                    set(file_meta.get("uploaded_chunks") or []), file_size, chunk_size
                ):
                    return None
                expected = file_meta.get("sha256")
                if expected and self._sha256_hex(file_dir / "blob") != str(expected).lower():
                    return None
        else:
            return None
        meta["status"] = "ready"
        meta["completed_at"] = int(time.time() * 1000)
        self._write_attachment_meta(board_id, attachment_id, meta)
        return meta

    def get_attachment_meta(self, board_id: str, attachment_id: str) -> dict[str, Any] | None:
        meta = self._read_attachment_meta(board_id, attachment_id)
        if meta is None:
            return None
        if meta.get("kind") == "directory":
            files_dir = self._attachment_dir(board_id, attachment_id) / "files"
            manifest = self._read_manifest(files_dir) or []
            files: list[dict[str, Any]] = []
            for entry in manifest:
                file_id = str(entry["file_id"])
                file_dir = files_dir / file_id
                file_meta = self._read_file_meta(file_dir) or {}
                files.append(
                    {
                        "file_id": file_id,
                        "path": entry.get("path"),
                        "size": entry.get("size"),
                        "uploaded_chunks": file_meta.get("uploaded_chunks", []),
                    }
                )
            meta = dict(meta)
            meta["files"] = files
        return meta

    def read_file_blob(
        self, board_id: str, attachment_id: str, range_header: str | None
    ) -> BlobReadResult | None:
        meta = self._read_attachment_meta(board_id, attachment_id)
        if meta is None or meta.get("status") != "ready" or meta.get("kind") != "file":
            return None
        blob = self._attachment_dir(board_id, attachment_id) / "blob"
        return self._read_blob_with_range(blob, int(meta.get("size", 0)), range_header)

    def read_directory_file_blob(
        self, board_id: str, attachment_id: str, file_id: str, range_header: str | None
    ) -> BlobReadResult | None:
        meta = self._read_attachment_meta(board_id, attachment_id)
        if meta is None or meta.get("status") != "ready" or meta.get("kind") != "directory":
            return None
        file_dir = self._attachment_dir(board_id, attachment_id) / "files" / file_id
        file_meta = self._read_file_meta(file_dir)
        if file_meta is None:
            return None
        return self._read_blob_with_range(
            file_dir / "blob", int(file_meta.get("size", 0)), range_header
        )

    def delete_attachment(self, board_id: str, attachment_id: str) -> bool:
        att_dir = self._attachment_dir(board_id, attachment_id)
        if not att_dir.exists():
            return False
        shutil.rmtree(att_dir)
        return True

    def is_attachment_ready(self, board_id: str, attachment_id: str) -> bool:
        meta = self._read_attachment_meta(board_id, attachment_id)
        return meta is not None and meta.get("status") == "ready"

    def _attachment_dir(self, board_id: str, attachment_id: str) -> Path:
        return self._boards_root / board_id / "attachments" / attachment_id

    def _read_meta(self, board_id: str) -> dict[str, Any] | None:
        meta_file = self._boards_root / board_id / "meta.json"
        if not meta_file.exists():
            return None
        try:
            return json.loads(meta_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return None

    def _read_attachment_meta(self, board_id: str, attachment_id: str) -> dict[str, Any] | None:
        meta_file = self._attachment_dir(board_id, attachment_id) / "attachment.json"
        if not meta_file.exists():
            return None
        try:
            return json.loads(meta_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return None

    def _write_attachment_meta(
        self, board_id: str, attachment_id: str, meta: dict[str, Any]
    ) -> None:
        meta_file = self._attachment_dir(board_id, attachment_id) / "attachment.json"
        meta_file.write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")

    def _read_file_meta(self, file_dir: Path) -> dict[str, Any] | None:
        meta_file = file_dir / "meta.json"
        if not meta_file.exists():
            return None
        try:
            return json.loads(meta_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return None

    def _read_manifest(self, files_dir: Path) -> list[dict[str, Any]] | None:
        manifest_file = files_dir / "manifest.json"
        if not manifest_file.exists():
            return None
        try:
            payload = json.loads(manifest_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return None
        entries = payload.get("entries")
        if not isinstance(entries, list):
            return None
        return [entry for entry in entries if isinstance(entry, dict)]

    @staticmethod
    def _chunks_complete(chunks: set[int], size: int, chunk_size: int) -> bool:
        if size == 0:
            return True
        last_index = (size - 1) // chunk_size
        return all(index in chunks for index in range(last_index + 1))

    @staticmethod
    def _sha256_hex(path: Path) -> str:
        digest = hashlib.sha256()
        with path.open("rb") as handle:
            while True:
                block = handle.read(8192)
                if not block:
                    break
                digest.update(block)
        return digest.hexdigest()

    @staticmethod
    def _read_blob_with_range(
        blob: Path, total_size: int, range_header: str | None
    ) -> BlobReadResult:
        effective_size = total_size if total_size > 0 else blob.stat().st_size
        if not range_header:
            return BlobReadResult(200, blob.read_bytes(), effective_size, None)
        import re

        match = re.match(r"bytes=(\d+)-(\d*)", range_header.strip())
        if not match:
            return BlobReadResult(200, blob.read_bytes(), effective_size, None)
        start = int(match.group(1))
        end_text = match.group(2)
        end = int(end_text) if end_text else effective_size - 1
        end = min(end, effective_size - 1)
        if start > end or start >= effective_size:
            return BlobReadResult(416, b"", effective_size, None)
        with blob.open("rb") as handle:
            handle.seek(start)
            data = handle.read(end - start + 1)
        content_range = f"bytes {start}-{end}/{effective_size}"
        return BlobReadResult(206, data, effective_size, content_range)
