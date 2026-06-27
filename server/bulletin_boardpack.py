from __future__ import annotations

import io
import json
import shutil
import time
import uuid
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from bulletin_ai_internal import is_conversation_message
from bulletin_roles import normalize_stored_role_ids

BOARDPACK_FORMAT = "localmanager.boardpack"
BOARDPACK_VERSION = 1
MANIFEST_NAME = "manifest.json"
BOARD_PREFIX = "board/"


class BoardpackError(Exception):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass(frozen=True)
class BoardpackImportOptions:
    name: str | None = None
    role_ids: tuple[str, ...] | None = None


@dataclass(frozen=True)
class BoardpackStats:
    message_count: int
    attachment_count: int
    total_bytes: int


def export_boardpack_from_board_dir(
    board_dir: Path,
    *,
    source_device: str,
) -> bytes:
    meta_file = board_dir / "meta.json"
    if not meta_file.exists():
        raise BoardpackError("board_not_found", f"留言板目录无效：{board_dir}")
    try:
        meta = json.loads(meta_file.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise BoardpackError("invalid_board", f"meta.json 不是合法 JSON：{exc}") from exc
    if not isinstance(meta, dict):
        raise BoardpackError("invalid_board", "meta.json 必须是对象")

    board_id = str(meta.get("id", board_dir.name))
    board_name = str(meta.get("name", board_id))
    stats = _collect_stats(board_dir)

    manifest = {
        "format": BOARDPACK_FORMAT,
        "version": BOARDPACK_VERSION,
        "exported_at": int(time.time() * 1000),
        "source": {
            "device": source_device,
            "board_id": board_id,
            "board_name": board_name,
        },
        "stats": {
            "message_count": stats.message_count,
            "attachment_count": stats.attachment_count,
            "total_bytes": stats.total_bytes,
        },
    }

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(
            MANIFEST_NAME,
            json.dumps(manifest, ensure_ascii=False, indent=2),
        )
        for path in sorted(board_dir.rglob("*")):
            if not path.is_file():
                continue
            arcname = BOARD_PREFIX + path.relative_to(board_dir).as_posix()
            archive.write(path, arcname=arcname)
    return buffer.getvalue()


def import_boardpack_bytes(
    boards_root: Path,
    data: bytes,
    *,
    options: BoardpackImportOptions | None = None,
    max_import_bytes: int | None = None,
) -> dict[str, Any]:
    if max_import_bytes is not None and len(data) > max_import_bytes:
        raise BoardpackError(
            "import_too_large",
            f"导入包过大：{len(data)} 字节，上限 {max_import_bytes}",
        )

    boards_root.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        manifest = _read_manifest(archive)
        _validate_manifest(manifest)
        board_entries = [
            name
            for name in archive.namelist()
            if name.startswith(BOARD_PREFIX) and not name.endswith("/")
        ]
        if not any(name == f"{BOARD_PREFIX}meta.json" for name in board_entries):
            raise BoardpackError("invalid_boardpack", "包内缺少 board/meta.json")

        new_board_id = str(uuid.uuid4())
        board_dir = boards_root / new_board_id
        board_dir.mkdir(parents=True, exist_ok=True)

        for entry in board_entries:
            relative = entry.removeprefix(BOARD_PREFIX)
            if not relative or ".." in Path(relative).parts:
                raise BoardpackError("invalid_boardpack", f"包内路径非法：{entry}")
            target = board_dir / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(entry) as source, target.open("wb") as dest:
                shutil.copyfileobj(source, dest)

    meta_file = board_dir / "meta.json"
    try:
        meta = json.loads(meta_file.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        shutil.rmtree(board_dir)
        raise BoardpackError("invalid_board", f"board/meta.json 不是合法 JSON：{exc}") from exc
    if not isinstance(meta, dict):
        shutil.rmtree(board_dir)
        raise BoardpackError("invalid_board", "board/meta.json 必须是对象")

    opts = options or BoardpackImportOptions()
    if opts.name is not None:
        trimmed = opts.name.strip()
        if not trimmed:
            shutil.rmtree(board_dir)
            raise BoardpackError("invalid_board", "覆盖名称不能为空")
        meta["name"] = trimmed
    if opts.role_ids is not None:
        meta["role_ids"] = list(normalize_stored_role_ids(list(opts.role_ids)))

    meta["id"] = new_board_id
    meta["revision"] = 0
    meta["imported_at"] = int(time.time() * 1000)
    meta_file.write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")

    active_count = _count_active_messages(board_dir / "messages.json")
    role_ids_raw = meta.get("role_ids")
    stored_role_ids: tuple[str, ...] | None
    if "role_ids" not in meta:
        stored_role_ids = None
    elif isinstance(role_ids_raw, list):
        stored_role_ids = normalize_stored_role_ids(role_ids_raw)
    else:
        stored_role_ids = ()

    return {
        "id": new_board_id,
        "name": str(meta.get("name", new_board_id)),
        "revision": 0,
        "message_count": active_count,
        "role_ids": list(stored_role_ids) if stored_role_ids is not None else None,
    }


def _read_manifest(archive: zipfile.ZipFile) -> dict[str, Any]:
    try:
        raw = archive.read(MANIFEST_NAME).decode("utf-8")
        payload = json.loads(raw)
    except KeyError as exc:
        raise BoardpackError("invalid_boardpack", "包内缺少 manifest.json") from exc
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise BoardpackError("invalid_boardpack", f"manifest.json 无效：{exc}") from exc
    if not isinstance(payload, dict):
        raise BoardpackError("invalid_boardpack", "manifest.json 必须是对象")
    return payload


def _validate_manifest(manifest: dict[str, Any]) -> None:
    if manifest.get("format") != BOARDPACK_FORMAT:
        raise BoardpackError(
            "unsupported_boardpack",
            f"不支持的包格式：{manifest.get('format')!r}",
        )
    version = manifest.get("version")
    if version != BOARDPACK_VERSION:
        raise BoardpackError(
            "unsupported_boardpack",
            f"不支持的包版本：{version!r}，当前仅支持 v{BOARDPACK_VERSION}",
        )


def _collect_stats(board_dir: Path) -> BoardpackStats:
    messages_file = board_dir / "messages.json"
    message_count = _count_active_messages(messages_file)
    attachment_count = 0
    total_bytes = 0
    attachments_root = board_dir / "attachments"
    if attachments_root.is_dir():
        for att_dir in attachments_root.iterdir():
            if not att_dir.is_dir():
                continue
            meta_file = att_dir / "attachment.json"
            if not meta_file.exists():
                continue
            try:
                meta = json.loads(meta_file.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                continue
            if meta.get("status") != "ready":
                continue
            attachment_count += 1
            total_bytes += _attachment_bytes(att_dir, meta)
    total_bytes += messages_file.stat().st_size if messages_file.exists() else 0
    meta_file = board_dir / "meta.json"
    total_bytes += meta_file.stat().st_size if meta_file.exists() else 0
    return BoardpackStats(message_count, attachment_count, total_bytes)


def _attachment_bytes(att_dir: Path, meta: dict[str, Any]) -> int:
    kind = str(meta.get("kind", "file"))
    if kind == "directory":
        files_dir = att_dir / "files"
        total = 0
        if files_dir.is_dir():
            for file_dir in files_dir.iterdir():
                blob = file_dir / "blob"
                if blob.is_file():
                    total += blob.stat().st_size
        return total
    blob = att_dir / "blob"
    return blob.stat().st_size if blob.is_file() else int(meta.get("size", 0))


def _count_active_messages(messages_file: Path) -> int:
    if not messages_file.exists():
        return 0
    try:
        raw = json.loads(messages_file.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return 0
    if not isinstance(raw, list):
        return 0
    count = 0
    for item in raw:
        if not isinstance(item, dict):
            continue
        if item.get("deleted"):
            continue
        content = str(item.get("content", ""))
        message_kind = str(item.get("message_kind", "message"))
        if message_kind == "ai_status" or content.strip().lower().startswith("/ai status"):
            continue
        count += 1
    return count


def parse_import_options_payload(payload: dict[str, Any]) -> BoardpackImportOptions:
    name = payload.get("name")
    name_value = str(name).strip() if name is not None else None
    if name is not None and not name_value:
        raise BoardpackError("invalid_board", "覆盖名称不能为空")
    role_ids: tuple[str, ...] | None = None
    if "role_ids" in payload:
        raw = payload.get("role_ids")
        if raw is None:
            role_ids = ()
        elif isinstance(raw, list):
            role_ids = normalize_stored_role_ids(raw)
        else:
            raise BoardpackError("invalid_board", "role_ids 必须是数组")
    return BoardpackImportOptions(name=name_value, role_ids=role_ids)
