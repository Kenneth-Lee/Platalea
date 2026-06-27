#!/usr/bin/env python3
from __future__ import annotations

import threading
from dataclasses import dataclass, field

from bulletin_ai_internal import format_ai_status_content
from bulletin_store import BulletinBoardStore


@dataclass
class AgentRunHandle:
    board_id: str
    model_name: str
    author_label: str
    author_device: str
    cancel_event: threading.Event = field(default_factory=threading.Event)
    status_message_id: str | None = None
    last_detail: str = ""


class AgentStatusReporter:
    def __init__(
        self,
        store: BulletinBoardStore,
        handle: AgentRunHandle,
        *,
        heartbeat_seconds: float = 15.0,
    ) -> None:
        self._store = store
        self._handle = handle
        self._heartbeat_seconds = max(3.0, heartbeat_seconds)
        self._lock = threading.Lock()
        self._timer: threading.Timer | None = None
        self._closed = False

    def update(self, detail: str) -> None:
        with self._lock:
            if self._closed:
                return
            self._handle.last_detail = detail.strip() or "正在处理…"
            content = format_ai_status_content(self._handle.last_detail)
            if self._handle.status_message_id:
                updated = self._store.update_message(
                    self._handle.board_id,
                    self._handle.status_message_id,
                    content,
                )
                if updated is None:
                    self._handle.status_message_id = None
            if not self._handle.status_message_id:
                posted = self._store.append_ai_status(
                    self._handle.board_id,
                    self._handle.author_label,
                    self._handle.last_detail,
                    author_device=self._handle.author_device,
                )
                if posted is not None:
                    self._handle.status_message_id = posted.id
            self._schedule_heartbeat()

    def clear(self) -> None:
        with self._lock:
            self._closed = True
            if self._timer is not None:
                self._timer.cancel()
                self._timer = None
            if self._handle.status_message_id:
                self._store.delete_ai_status_messages(
                    self._handle.board_id,
                    author_device=self._handle.author_device,
                )
                self._handle.status_message_id = None

    def _schedule_heartbeat(self) -> None:
        if self._timer is not None:
            self._timer.cancel()
        self._timer = threading.Timer(self._heartbeat_seconds, self._heartbeat)
        self._timer.daemon = True
        self._timer.start()

    def _heartbeat(self) -> None:
        with self._lock:
            if self._closed:
                return
            detail = self._handle.last_detail or "仍在处理…"
        self.update(detail)
