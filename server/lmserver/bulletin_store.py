from __future__ import annotations

import json
import shutil
import threading
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .bulletin_ai_internal import (
    MESSAGE_KIND_AI_STATUS,
    MESSAGE_KIND_MESSAGE,
    format_ai_status_content,
    is_ai_status_message,
    is_conversation_message,
)
from .bulletin_attachment_store import BulletinAttachmentStore
from .bulletin_boardpack import (
    BoardpackError,
    BoardpackImportOptions,
    export_boardpack_from_board_dir,
    import_boardpack_bytes,
)
from .bulletin_roles import board_role_ids_from_meta, normalize_stored_role_ids

DEFAULT_BOARD_ID = "default"
DEFAULT_BOARD_NAME = "默认留言板"


@dataclass(frozen=True)
class BulletinBoardInfo:
    id: str
    name: str
    revision: int
    message_count: int
    role_ids: tuple[str, ...] | None = None


@dataclass
class BulletinMessage:
    id: str
    seq: int
    author_label: str
    content: str
    created_at: int
    updated_at: int
    deleted: bool = False
    author_device: str | None = None
    attachments: list[dict[str, Any]] | None = None
    message_kind: str = MESSAGE_KIND_MESSAGE

    def to_json(self) -> dict[str, Any]:
        payload = {
            "id": self.id,
            "seq": self.seq,
            "author_label": self.author_label,
            "content": self.content,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "deleted": self.deleted,
        }
        if self.message_kind != MESSAGE_KIND_MESSAGE:
            payload["message_kind"] = self.message_kind
        if self.author_device:
            payload["author_device"] = self.author_device
        if self.attachments:
            payload["attachments"] = self.attachments
        return payload

    @classmethod
    def from_json(cls, obj: dict[str, Any]) -> BulletinMessage:
        attachments = obj.get("attachments")
        content = str(obj.get("content", ""))
        message_kind = str(obj.get("message_kind", MESSAGE_KIND_MESSAGE)).strip() or MESSAGE_KIND_MESSAGE
        if message_kind == MESSAGE_KIND_MESSAGE and content.strip().lower().startswith("/ai status"):
            message_kind = MESSAGE_KIND_AI_STATUS
        return cls(
            id=str(obj["id"]),
            seq=int(obj.get("seq", 0)),
            author_label=str(obj.get("author_label", "")),
            content=content,
            created_at=int(obj.get("created_at", 0)),
            updated_at=int(obj.get("updated_at", 0)),
            deleted=bool(obj.get("deleted", False)),
            author_device=str(obj.get("author_device", "")).strip() or None,
            attachments=attachments if isinstance(attachments, list) else None,
            message_kind=message_kind,
        )


@dataclass(frozen=True)
class BulletinBoardSnapshot:
    board_id: str
    board_name: str
    revision: int
    messages: list[BulletinMessage]
    can_manage: bool = False

    def to_json(self) -> dict[str, Any]:
        return {
            "ok": True,
            "board_id": self.board_id,
            "board_name": self.board_name,
            "revision": self.revision,
            "can_manage": self.can_manage,
            "messages": [message.to_json() for message in self.messages],
        }


class BulletinBoardStore:
    def __init__(self, root_dir: str | Path) -> None:
        self._root_dir = Path(root_dir)
        self._lock = threading.RLock()
        self._root_dir.mkdir(parents=True, exist_ok=True)
        self.attachments = BulletinAttachmentStore(self._root_dir)
        self.ensure_default_board()

    @property
    def root_dir(self) -> Path:
        return self._root_dir

    def ensure_default_board(self) -> None:
        with self._lock:
            board_dir = self._board_dir(DEFAULT_BOARD_ID)
            board_dir.mkdir(parents=True, exist_ok=True)
            meta_file = self._meta_file(DEFAULT_BOARD_ID)
            if not meta_file.exists():
                meta_file.write_text(
                    json.dumps(
                        {
                            "id": DEFAULT_BOARD_ID,
                            "name": DEFAULT_BOARD_NAME,
                            "revision": 0,
                        },
                        ensure_ascii=False,
                    ),
                    encoding="utf-8",
                )
            messages_file = self._messages_file(DEFAULT_BOARD_ID)
            if not messages_file.exists():
                messages_file.write_text("[]", encoding="utf-8")

    def list_boards(self) -> list[BulletinBoardInfo]:
        with self._lock:
            boards: list[BulletinBoardInfo] = []
            if not self._root_dir.exists():
                return boards
            for board_dir in self._root_dir.iterdir():
                if not board_dir.is_dir():
                    continue
                meta = self._read_meta(board_dir.name)
                if meta is None:
                    continue
                active_count = sum(
                    1
                    for message in self._read_messages(board_dir.name)
                    if is_conversation_message(message)
                )
                boards.append(
                    BulletinBoardInfo(
                        id=str(meta["id"]),
                        name=str(meta.get("name", board_dir.name)),
                        revision=int(meta.get("revision", 0)),
                        message_count=active_count,
                        role_ids=board_role_ids_from_meta(meta),
                    )
                )
            return sorted(boards, key=lambda item: item.id)

    def create_board(
        self,
        name: str,
        role_ids: tuple[str, ...] | None = (),
    ) -> BulletinBoardInfo | None:
        trimmed = name.strip()
        if not trimmed:
            return None
        with self._lock:
            board_id = str(uuid.uuid4())
            board_dir = self._board_dir(board_id)
            board_dir.mkdir(parents=True, exist_ok=True)
            now = int(time.time() * 1000)
            stored_role_ids = normalize_stored_role_ids(list(role_ids or ()))
            meta = {
                "id": board_id,
                "name": trimmed,
                "revision": 0,
                "created_at": now,
                "role_ids": list(stored_role_ids),
            }
            self._write_meta(board_id, meta)
            self._write_messages(board_id, [])
            return BulletinBoardInfo(
                id=board_id,
                name=trimmed,
                revision=0,
                message_count=0,
                role_ids=stored_role_ids,
            )

    def update_board(
        self,
        board_id: str,
        *,
        name: str | None = None,
        role_ids: tuple[str, ...] | None = None,
    ) -> BulletinBoardInfo | None:
        with self._lock:
            meta = self._read_meta(board_id)
            if meta is None:
                return None
            trimmed_name = name.strip() if name is not None else None
            if trimmed_name is not None and not trimmed_name:
                return None
            if trimmed_name is not None:
                meta["name"] = trimmed_name
            if role_ids is not None:
                meta["role_ids"] = list(normalize_stored_role_ids(list(role_ids)))
            self._write_meta(board_id, meta)
            active_count = sum(
                1
                for message in self._read_messages(board_id)
                if is_conversation_message(message)
            )
            return BulletinBoardInfo(
                id=board_id,
                name=str(meta.get("name", board_id)),
                revision=int(meta.get("revision", 0)),
                message_count=active_count,
                role_ids=board_role_ids_from_meta(meta),
            )

    def get_board_info(self, board_id: str) -> BulletinBoardInfo | None:
        with self._lock:
            meta = self._read_meta(board_id)
            if meta is None:
                return None
            active_count = sum(
                1
                for message in self._read_messages(board_id)
                if is_conversation_message(message)
            )
            return BulletinBoardInfo(
                id=str(meta["id"]),
                name=str(meta.get("name", board_id)),
                revision=int(meta.get("revision", 0)),
                message_count=active_count,
                role_ids=board_role_ids_from_meta(meta),
            )

    def export_boardpack(self, board_id: str, *, source_device: str = "python") -> bytes | None:
        with self._lock:
            if self._read_meta(board_id) is None:
                return None
            return export_boardpack_from_board_dir(
                self._board_dir(board_id),
                source_device=source_device,
            )

    def import_boardpack(
        self,
        data: bytes,
        *,
        name: str | None = None,
        role_ids: tuple[str, ...] | None = None,
        source_device: str = "python",
        max_import_bytes: int | None = None,
    ) -> BulletinBoardInfo:
        with self._lock:
            options = BoardpackImportOptions(name=name, role_ids=role_ids)
            try:
                imported = import_boardpack_bytes(
                    self._root_dir,
                    data,
                    options=options,
                    max_import_bytes=max_import_bytes,
                )
            except BoardpackError as exc:
                raise ValueError(f"{exc.code}: {exc.message}") from exc
            role_ids_value = imported.get("role_ids")
            parsed_role_ids: tuple[str, ...] | None
            if role_ids_value is None:
                parsed_role_ids = None
            else:
                parsed_role_ids = tuple(str(item) for item in role_ids_value)
            return BulletinBoardInfo(
                id=str(imported["id"]),
                name=str(imported["name"]),
                revision=int(imported.get("revision", 0)),
                message_count=int(imported.get("message_count", 0)),
                role_ids=parsed_role_ids,
            )

    def delete_board(self, board_id: str) -> bool:
        with self._lock:
            if self._read_meta(board_id) is None:
                return False
            board_count = sum(
                1
                for board_dir in self._root_dir.iterdir()
                if board_dir.is_dir() and self._read_meta(board_dir.name) is not None
            )
            if board_count <= 1:
                return False
            shutil.rmtree(self._board_dir(board_id))
            return True

    def export_markdown(self, board_id: str) -> str | None:
        snapshot = self.snapshot(board_id)
        if snapshot is None:
            return None
        return _snapshot_to_markdown(snapshot)

    def snapshot(self, board_id: str) -> BulletinBoardSnapshot | None:
        with self._lock:
            meta = self._read_meta(board_id)
            if meta is None:
                return None
            messages = [
                message
                for message in self._read_messages(board_id)
                if not message.deleted
            ]
            messages.sort(key=lambda item: item.seq)
            return BulletinBoardSnapshot(
                board_id=board_id,
                board_name=str(meta.get("name", board_id)),
                revision=int(meta.get("revision", 0)),
                messages=messages,
            )

    def append_message(
        self,
        board_id: str,
        author_label: str,
        content: str,
        author_device: str | None = None,
        attachments: list[dict[str, Any]] | None = None,
        message_kind: str = MESSAGE_KIND_MESSAGE,
    ) -> BulletinMessage | None:
        trimmed = content.strip()
        attachment_list = attachments or []
        if not trimmed and not attachment_list:
            return None
        with self._lock:
            for item in attachment_list:
                attachment_id = str(item.get("id", ""))
                if not attachment_id or not self.attachments.is_attachment_ready(board_id, attachment_id):
                    return None
            meta = self._read_meta(board_id)
            if meta is None:
                return None
            messages = self._read_messages(board_id)
            next_seq = max((message.seq for message in messages), default=0) + 1
            now = int(time.time() * 1000)
            message = BulletinMessage(
                id=str(uuid.uuid4()),
                seq=next_seq,
                author_label=author_label.strip() or "访客",
                content=trimmed,
                created_at=now,
                updated_at=now,
                author_device=author_device.strip() if author_device else None,
                attachments=attachment_list or None,
                message_kind=message_kind,
            )
            messages.append(message)
            self._write_messages(board_id, messages)
            meta["revision"] = int(meta.get("revision", 0)) + 1
            self._write_meta(board_id, meta)
            return message

    def append_ai_status(
        self,
        board_id: str,
        author_label: str,
        detail: str,
        author_device: str | None = None,
    ) -> BulletinMessage | None:
        return self.append_message(
            board_id,
            author_label,
            format_ai_status_content(detail),
            author_device=author_device,
            message_kind=MESSAGE_KIND_AI_STATUS,
        )

    def delete_ai_status_messages(
        self,
        board_id: str,
        *,
        author_device: str | None = None,
    ) -> int:
        with self._lock:
            meta = self._read_meta(board_id)
            if meta is None:
                return 0
            messages = self._read_messages(board_id)
            removed = 0
            kept: list[BulletinMessage] = []
            for message in messages:
                if not is_ai_status_message(message):
                    kept.append(message)
                    continue
                if author_device and (message.author_device or "") != author_device:
                    kept.append(message)
                    continue
                removed += 1
            if removed == 0:
                return 0
            self._write_messages(board_id, kept)
            meta["revision"] = int(meta.get("revision", 0)) + 1
            self._write_meta(board_id, meta)
            return removed

    def update_message(
        self,
        board_id: str,
        message_id: str,
        content: str,
    ) -> BulletinMessage | None:
        trimmed = content.strip()
        if not trimmed:
            return None
        with self._lock:
            meta = self._read_meta(board_id)
            if meta is None:
                return None
            messages = self._read_messages(board_id)
            index = next(
                (
                    idx
                    for idx, message in enumerate(messages)
                    if message.id == message_id and not message.deleted
                ),
                -1,
            )
            if index < 0:
                return None
            now = int(time.time() * 1000)
            updated = BulletinMessage(
                id=messages[index].id,
                seq=messages[index].seq,
                author_label=messages[index].author_label,
                content=trimmed,
                created_at=messages[index].created_at,
                updated_at=now,
                deleted=False,
                author_device=messages[index].author_device,
                attachments=messages[index].attachments,
                message_kind=messages[index].message_kind,
            )
            messages[index] = updated
            self._write_messages(board_id, messages)
            meta["revision"] = int(meta.get("revision", 0)) + 1
            self._write_meta(board_id, meta)
            return updated

    def delete_message(self, board_id: str, message_id: str) -> bool:
        with self._lock:
            meta = self._read_meta(board_id)
            if meta is None:
                return False
            messages = self._read_messages(board_id)
            index = next(
                (
                    idx
                    for idx, message in enumerate(messages)
                    if message.id == message_id and not message.deleted
                ),
                -1,
            )
            if index < 0:
                return False
            target = messages[index]
            for attachment in target.attachments or []:
                attachment_id = str(attachment.get("id", "")).strip()
                if attachment_id:
                    self.attachments.delete_attachment(board_id, attachment_id)
            now = int(time.time() * 1000)
            messages[index] = BulletinMessage(
                id=target.id,
                seq=target.seq,
                author_label=target.author_label,
                content=target.content,
                created_at=target.created_at,
                updated_at=now,
                deleted=True,
                author_device=target.author_device,
                attachments=target.attachments,
            )
            self._write_messages(board_id, messages)
            meta["revision"] = int(meta.get("revision", 0)) + 1
            self._write_meta(board_id, meta)
            return True

    def _board_dir(self, board_id: str) -> Path:
        return self._root_dir / board_id

    def _meta_file(self, board_id: str) -> Path:
        return self._board_dir(board_id) / "meta.json"

    def _messages_file(self, board_id: str) -> Path:
        return self._board_dir(board_id) / "messages.json"

    def _read_meta(self, board_id: str) -> dict[str, Any] | None:
        meta_file = self._meta_file(board_id)
        if not meta_file.exists():
            return None
        try:
            return json.loads(meta_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return None

    def _write_meta(self, board_id: str, meta: dict[str, Any]) -> None:
        self._meta_file(board_id).write_text(
            json.dumps(meta, ensure_ascii=False),
            encoding="utf-8",
        )

    def _read_messages(self, board_id: str) -> list[BulletinMessage]:
        messages_file = self._messages_file(board_id)
        if not messages_file.exists():
            return []
        try:
            raw = json.loads(messages_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return []
        if not isinstance(raw, list):
            return []
        result: list[BulletinMessage] = []
        for item in raw:
            if isinstance(item, dict):
                result.append(BulletinMessage.from_json(item))
        return result

    def _write_messages(self, board_id: str, messages: list[BulletinMessage]) -> None:
        payload = [message.to_json() for message in messages]
        self._messages_file(board_id).write_text(
            json.dumps(payload, ensure_ascii=False),
            encoding="utf-8",
        )


def _format_byte_count(bytes_value: int) -> str:
    if bytes_value <= 0:
        return "0 B"
    if bytes_value < 1024:
        return f"{bytes_value} B"
    if bytes_value < 1024 * 1024:
        return f"{bytes_value / 1024:.1f} KB"
    if bytes_value < 1024 * 1024 * 1024:
        return f"{bytes_value / (1024 * 1024):.1f} MB"
    return f"{bytes_value / (1024 * 1024 * 1024):.2f} GB"


def _format_attachment_line(attachment: dict[str, Any]) -> str:
    kind = str(attachment.get("kind", "file"))
    name = str(attachment.get("name", ""))
    if kind == "directory":
        size = int(attachment.get("total_size", attachment.get("size", 0)))
        kind_label = "目录"
    else:
        size = int(attachment.get("size", 0))
        kind_label = "文件"
    return f"{name}（{kind_label}，{_format_byte_count(size)}）"


def _snapshot_to_markdown(snapshot: BulletinBoardSnapshot) -> str:
    from datetime import datetime

    exported_at = datetime.now().strftime("%Y-%m-%d %H:%M")
    lines = [
        f"# {snapshot.board_name}",
        "",
        f"> 留言板 ID：`{snapshot.board_id}`",
        f"> 导出时间：{exported_at}",
        f"> 消息数：{len(snapshot.messages)}",
        "",
    ]
    if not snapshot.messages:
        lines.append("（暂无留言）")
        return "\n".join(lines) + "\n"
    for message in snapshot.messages:
        if not is_conversation_message(message):
            continue
        time_label = datetime.fromtimestamp(message.updated_at / 1000).strftime(
            "%Y-%m-%d %H:%M"
        )
        lines.extend(["---", "", f"**{message.author_label}** · {time_label}"])
        if message.author_device:
            lines.extend(["", f"> 设备：{message.author_device}"])
        lines.append("")
        if message.content.strip():
            lines.append(message.content)
            lines.append("")
        attachments = message.attachments or []
        if attachments:
            lines.append("附件：")
            for attachment in attachments:
                lines.append(f"- {_format_attachment_line(attachment)}")
            lines.append("")
    return "\n".join(lines).rstrip() + "\n"
