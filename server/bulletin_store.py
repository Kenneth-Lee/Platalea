from __future__ import annotations

import json
import threading
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any


from bulletin_attachment_store import BulletinAttachmentStore

DEFAULT_BOARD_ID = "default"
DEFAULT_BOARD_NAME = "默认留言板"


@dataclass(frozen=True)
class BulletinBoardInfo:
    id: str
    name: str
    revision: int
    message_count: int


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
        if self.author_device:
            payload["author_device"] = self.author_device
        if self.attachments:
            payload["attachments"] = self.attachments
        return payload

    @classmethod
    def from_json(cls, obj: dict[str, Any]) -> BulletinMessage:
        attachments = obj.get("attachments")
        return cls(
            id=str(obj["id"]),
            seq=int(obj.get("seq", 0)),
            author_label=str(obj.get("author_label", "")),
            content=str(obj.get("content", "")),
            created_at=int(obj.get("created_at", 0)),
            updated_at=int(obj.get("updated_at", 0)),
            deleted=bool(obj.get("deleted", False)),
            author_device=str(obj.get("author_device", "")).strip() or None,
            attachments=attachments if isinstance(attachments, list) else None,
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
                    1 for message in self._read_messages(board_dir.name) if not message.deleted
                )
                boards.append(
                    BulletinBoardInfo(
                        id=str(meta["id"]),
                        name=str(meta.get("name", board_dir.name)),
                        revision=int(meta.get("revision", 0)),
                        message_count=active_count,
                    )
                )
            return sorted(boards, key=lambda item: item.id)

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
            )
            messages.append(message)
            self._write_messages(board_id, messages)
            meta["revision"] = int(meta.get("revision", 0)) + 1
            self._write_meta(board_id, meta)
            return message

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
