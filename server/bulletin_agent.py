#!/usr/bin/env python3
from __future__ import annotations

import json
import logging
import re
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from bulletin_store import BulletinBoardStore, BulletinMessage

LOGGER = logging.getLogger("local_manager.bulletin_agent")

DEFAULT_SYSTEM_PROMPT = (
    "你是家庭网络留言板上的 AI 助手。用户通过 @你的模型名 向你提问。"
    "请根据提供的留言板对话记录回答，保持简洁；信息不足时请说明。"
)

AGENT_DEVICE_PREFIX = "agent:"


@dataclass(frozen=True)
class AgentConfig:
    enabled: bool
    board_id: str
    model_name: str
    ollama_base_url: str
    poll_interval_seconds: float

    @property
    def author_label(self) -> str:
        return f"AI-{self.model_name}"

    @property
    def author_device(self) -> str:
        return f"{AGENT_DEVICE_PREFIX}{self.model_name}"


def load_agent_config(raw: dict[str, Any] | None) -> AgentConfig | None:
    if not raw or not isinstance(raw, dict):
        return None
    if not raw.get("enabled"):
        return None
    model_name = str(raw.get("model_name", "")).strip()
    if not model_name:
        raise ValueError("agent.enabled 为 true 时必须设置 agent.model_name")
    board_id = str(raw.get("board_id", "default")).strip() or "default"
    base_url = str(raw.get("ollama_base_url", "http://127.0.0.1:11434")).strip()
    base_url = base_url.rstrip("/")
    poll = float(raw.get("poll_interval_seconds", 3))
    poll = max(1.0, min(poll, 60.0))
    return AgentConfig(
        enabled=True,
        board_id=board_id,
        model_name=model_name,
        ollama_base_url=base_url,
        poll_interval_seconds=poll,
    )


class AgentStateStore:
    def __init__(self, path: Path) -> None:
        self._path = path
        self._lock = threading.Lock()

    def load_processed_ids(self) -> set[str]:
        with self._lock:
            if not self._path.exists():
                return set()
            try:
                payload = json.loads(self._path.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                LOGGER.warning("Agent 状态文件损坏，将重新建立: %s", self._path)
                return set()
            ids = payload.get("processed_message_ids")
            if not isinstance(ids, list):
                return set()
            return {str(item) for item in ids if str(item).strip()}

    def save_processed_ids(self, processed_ids: set[str]) -> None:
        with self._lock:
            self._path.parent.mkdir(parents=True, exist_ok=True)
            payload = {
                "processed_message_ids": sorted(processed_ids),
                "updated_at": int(time.time() * 1000),
            }
            self._path.write_text(
                json.dumps(payload, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )


class BulletinBoardAgent:
    def __init__(self, store: BulletinBoardStore, config: AgentConfig, state_dir: Path) -> None:
        self._store = store
        self._config = config
        self._state = AgentStateStore(state_dir / f"{config.board_id}_state.json")
        self._processed_ids = self._state.load_processed_ids()
        self._busy = False
        self._stop = threading.Event()

    def run_forever(self) -> None:
        LOGGER.info(
            "AI Agent 已启动: board=%s model=@%s ollama=%s",
            self._config.board_id,
            self._config.model_name,
            self._config.ollama_base_url,
        )
        while not self._stop.is_set():
            try:
                self._poll_once()
            except Exception:
                LOGGER.exception("Agent 轮询异常")
            self._stop.wait(self._config.poll_interval_seconds)

    def stop(self) -> None:
        self._stop.set()

    def _poll_once(self) -> None:
        if self._busy:
            return
        snapshot = self._store.snapshot(self._config.board_id)
        if snapshot is None:
            LOGGER.warning("留言板不存在: %s", self._config.board_id)
            return

        pending: list[BulletinMessage] = []
        for message in snapshot.messages:
            if message.id in self._processed_ids:
                continue
            if message.deleted:
                self._mark_processed(message.id)
                continue
            if self._is_agent_message(message):
                self._mark_processed(message.id)
                continue
            pending.append(message)

        for message in pending:
            self._mark_processed(message.id)
            question = extract_mention_question(message.content, self._config.model_name)
            if question is None:
                continue
            self._handle_mention(message, question, snapshot.messages)

    def _handle_mention(
        self,
        trigger: BulletinMessage,
        question: str,
        all_messages: list[Any],
    ) -> None:
        self._busy = True
        try:
            LOGGER.info(
                "收到 @%s 来自 %s (message=%s): %s",
                self._config.model_name,
                trigger.author_label,
                trigger.id,
                question[:120],
            )
            board_context = format_board_context(all_messages, self._config.model_name)
            reply = call_ollama_chat(
                base_url=self._config.ollama_base_url,
                model=self._config.model_name,
                system_prompt=DEFAULT_SYSTEM_PROMPT,
                board_context=board_context,
                trigger_author=trigger.author_label,
                question=question,
            )
            posted = self._store.append_message(
                self._config.board_id,
                self._config.author_label,
                reply,
                author_device=self._config.author_device,
            )
            if posted is None:
                raise RuntimeError("Agent 回复写入留言板失败")
            self._mark_processed(posted.id)
            LOGGER.info("Agent 已回复 message=%s", posted.id)
        except Exception as exc:
            LOGGER.exception("Agent 处理 @%s 失败", self._config.model_name)
            error_text = f"AI 处理失败：{exc}"
            posted = self._store.append_message(
                self._config.board_id,
                self._config.author_label,
                error_text,
                author_device=self._config.author_device,
            )
            if posted is not None:
                self._mark_processed(posted.id)
        finally:
            self._busy = False

    def _is_agent_message(self, message: BulletinMessage) -> bool:
        device = (message.author_device or "").strip()
        if device.startswith(AGENT_DEVICE_PREFIX):
            return True
        label = message.author_label.strip()
        return label == self._config.author_label

    def _mark_processed(self, message_id: str) -> None:
        if message_id in self._processed_ids:
            return
        self._processed_ids.add(message_id)
        self._state.save_processed_ids(self._processed_ids)


def extract_mention_question(content: str, model_name: str) -> str | None:
    if not content.strip() or not model_name.strip():
        return None
    pattern = rf"@{re.escape(model_name)}\b"
    match = re.search(pattern, content, flags=re.IGNORECASE)
    if not match:
        return None
    question = content[match.end() :].strip()
    if not question:
        return "请根据留言板上的对话给出简要回应。"
    return question


def format_board_context(messages: list[Any], model_name: str) -> str:
    lines: list[str] = []
    for message in messages:
        if getattr(message, "deleted", False):
            continue
        author = getattr(message, "author_label", "") or "访客"
        device = getattr(message, "author_device", None) or ""
        if device.startswith(AGENT_DEVICE_PREFIX) or author == f"AI-{model_name}":
            role = "助手"
        else:
            role = author
        content = getattr(message, "content", "").strip()
        if not content:
            continue
        lines.append(f"[{role}] {content}")
    if not lines:
        return "（留言板暂无文本消息）"
    return "\n".join(lines)


def call_ollama_chat(
    *,
    base_url: str,
    model: str,
    system_prompt: str,
    board_context: str,
    trigger_author: str,
    question: str,
    timeout_seconds: float = 300,
) -> str:
    user_content = (
        "以下是留言板上的对话记录：\n"
        f"{board_context}\n\n"
        f"用户 {trigger_author} 向你提问：\n"
        f"{question}"
    )
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_content},
        ],
        "stream": False,
    }
    url = f"{base_url.rstrip('/')}/api/chat"
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Ollama HTTP {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"无法连接 Ollama ({base_url}): {exc.reason}") from exc

    try:
        parsed = json.loads(body)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Ollama 返回非 JSON: {body[:200]}") from exc

    message = parsed.get("message")
    if not isinstance(message, dict):
        raise RuntimeError(f"Ollama 响应缺少 message 字段: {body[:200]}")
    content = str(message.get("content", "")).strip()
    if not content:
        raise RuntimeError("Ollama 返回空内容")
    return content


def start_agent_thread(
    store: BulletinBoardStore,
    config: AgentConfig,
    state_dir: Path,
) -> tuple[BulletinBoardAgent, threading.Thread]:
    agent = BulletinBoardAgent(store, config, state_dir)
    thread = threading.Thread(
        target=agent.run_forever,
        name="bulletin-agent",
        daemon=True,
    )
    thread.start()
    return agent, thread
