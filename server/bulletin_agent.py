#!/usr/bin/env python3
from __future__ import annotations

import json
import logging
import queue
import re
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from bulletin_agent_tools import (
    AgentToolExecutor,
    AgentToolsConfig,
    build_tool_definitions,
    load_agent_tools_config,
    parse_tool_arguments,
    tools_system_prompt_suffix,
)
from bulletin_mention import AGENT_DEVICE_PREFIX
from bulletin_store import BulletinBoardStore, BulletinMessage

LOGGER = logging.getLogger("local_manager.bulletin_agent")

DEFAULT_SYSTEM_PROMPT = (
    "你是家庭网络留言板上的 AI 助手。用户通过 @你的模型名 向你提问。"
    "请根据提供的留言板对话记录回答，保持简洁；信息不足时请说明。"
)


@dataclass(frozen=True)
class AgentConfig:
    enabled: bool
    model_names: tuple[str, ...]
    ollama_base_url: str
    board_ids: frozenset[str] | None
    tools: AgentToolsConfig

    @staticmethod
    def author_label_for(model_name: str) -> str:
        return f"AI-{model_name}"

    @staticmethod
    def author_device_for(model_name: str) -> str:
        return f"{AGENT_DEVICE_PREFIX}{model_name}"

    def applies_to_board(self, board_id: str) -> bool:
        if self.board_ids is None:
            return True
        return board_id in self.board_ids

    def models_for_board(self, board_id: str) -> list[str]:
        if not self.applies_to_board(board_id):
            return []
        return list(self.model_names)

    def to_public_json(self) -> dict[str, Any]:
        return {
            "ok": True,
            "enabled": True,
            "models": list(self.model_names),
            "board_ids": sorted(self.board_ids) if self.board_ids is not None else None,
            "tools": {
                "enabled": self.tools.enabled,
                "attachments": self.tools.attachments,
                "web_fetch": self.tools.web_fetch,
            },
        }


def load_agent_config(raw: dict[str, Any] | None) -> AgentConfig | None:
    if not raw or not isinstance(raw, dict):
        return None
    if not raw.get("enabled"):
        return None
    model_names = _parse_model_names(raw)
    base_url = str(raw.get("ollama_base_url", "http://127.0.0.1:11434")).strip().rstrip("/")
    board_ids = _parse_board_ids(raw)
    tools = load_agent_tools_config(raw.get("tools") if isinstance(raw.get("tools"), dict) else None)
    return AgentConfig(
        enabled=True,
        model_names=model_names,
        ollama_base_url=base_url,
        board_ids=board_ids,
        tools=tools,
    )


def _parse_model_names(raw: dict[str, Any]) -> tuple[str, ...]:
    if "models" in raw:
        value = raw.get("models")
        if not isinstance(value, list):
            raise ValueError("agent.models 必须是字符串数组")
        names = tuple(str(item).strip() for item in value if str(item).strip())
        if not names:
            raise ValueError("agent.models 不能为空")
        return names
    legacy = str(raw.get("model_name", "")).strip()
    if not legacy:
        raise ValueError("agent.enabled 为 true 时必须设置 agent.models 或 agent.model_name")
    return (legacy,)


def _parse_board_ids(raw: dict[str, Any]) -> frozenset[str] | None:
    if "board_ids" in raw:
        value = raw.get("board_ids")
        if value is None:
            return None
        if not isinstance(value, list):
            raise ValueError("agent.board_ids 必须是字符串数组或 null")
        ids = {str(item).strip() for item in value if str(item).strip()}
        return frozenset(ids) if ids else None
    legacy = str(raw.get("board_id", "")).strip()
    if legacy:
        return frozenset({legacy})
    return None


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
        self._state_dir = state_dir
        self._board_states: dict[str, AgentStateStore] = {}
        self._processed_by_board: dict[str, set[str]] = {}
        self._queue: queue.Queue[tuple[str, str] | None] = queue.Queue()
        self._stop = threading.Event()
        self._worker = threading.Thread(target=self._worker_loop, name="bulletin-agent", daemon=True)

    def start(self) -> None:
        scope = (
            "全部留言板"
            if self._config.board_ids is None
            else ", ".join(sorted(self._config.board_ids))
        )
        model_list = ", ".join(f"@{name}" for name in self._config.model_names)
        tools_note = "tools=on" if self._config.tools.enabled else "tools=off"
        LOGGER.info(
            "AI Agent 已启动: scope=%s models=%s %s ollama=%s",
            scope,
            model_list,
            tools_note,
            self._config.ollama_base_url,
        )
        self._worker.start()
        self._scan_all_boards_once()

    def stop(self) -> None:
        self._stop.set()
        self._queue.put(None)
        if self._worker.is_alive():
            self._worker.join(timeout=2)

    def notify_new_message(self, board_id: str, message_id: str) -> None:
        if not self._config.applies_to_board(board_id):
            return
        self._queue.put((board_id, message_id))

    def _worker_loop(self) -> None:
        while not self._stop.is_set():
            try:
                item = self._queue.get(timeout=0.5)
            except queue.Empty:
                continue
            if item is None:
                break
            board_id, message_id = item
            try:
                self._process_message(board_id, message_id)
            except Exception:
                LOGGER.exception("Agent 处理消息失败 board=%s message=%s", board_id, message_id)

    def _scan_all_boards_once(self) -> None:
        for board in self._store.list_boards():
            if not self._config.applies_to_board(board.id):
                continue
            snapshot = self._store.snapshot(board.id)
            if snapshot is None:
                continue
            for message in snapshot.messages:
                if message.id in self._get_processed(board.id):
                    continue
                if message.deleted or self._is_agent_message(message):
                    self._mark_processed(board.id, message.id)
                    continue
                if extract_mention_for_models(message.content, self._config.model_names) is not None:
                    self.notify_new_message(board.id, message.id)

    def _process_message(self, board_id: str, message_id: str) -> None:
        if message_id in self._get_processed(board_id):
            return
        snapshot = self._store.snapshot(board_id)
        if snapshot is None:
            return
        message = next((item for item in snapshot.messages if item.id == message_id), None)
        if message is None or message.deleted:
            self._mark_processed(board_id, message_id)
            return
        if self._is_agent_message(message):
            self._mark_processed(board_id, message_id)
            return
        self._mark_processed(board_id, message_id)
        mention = extract_mention_for_models(message.content, self._config.model_names)
        if mention is None:
            return
        model_name, question = mention
        self._handle_mention(board_id, message, model_name, question, snapshot.messages)

    def _handle_mention(
        self,
        board_id: str,
        trigger: BulletinMessage,
        model_name: str,
        question: str,
        all_messages: list[BulletinMessage],
    ) -> None:
        author_label = AgentConfig.author_label_for(model_name)
        author_device = AgentConfig.author_device_for(model_name)
        try:
            LOGGER.info(
                "收到 @%s 来自 %s (board=%s message=%s): %s",
                model_name,
                trigger.author_label,
                board_id,
                trigger.id,
                question[:120],
            )
            board_context = format_board_context(all_messages, self._config.model_names)
            system_prompt = build_system_prompt(self._config.tools)
            reply = run_ollama_agent_chat(
                base_url=self._config.ollama_base_url,
                model=model_name,
                system_prompt=system_prompt,
                board_context=board_context,
                trigger_author=trigger.author_label,
                question=question,
                store=self._store,
                board_id=board_id,
                messages=all_messages,
                tools_config=self._config.tools,
            )
            posted = self._store.append_message(
                board_id,
                author_label,
                reply,
                author_device=author_device,
            )
            if posted is None:
                raise RuntimeError("Agent 回复写入留言板失败")
            self._mark_processed(board_id, posted.id)
            LOGGER.info("Agent 已回复 board=%s message=%s model=%s", board_id, posted.id, model_name)
        except Exception as exc:
            LOGGER.exception("Agent 处理 @%s 失败", model_name)
            error_text = f"AI 处理失败：{exc}"
            posted = self._store.append_message(
                board_id,
                author_label,
                error_text,
                author_device=author_device,
            )
            if posted is not None:
                self._mark_processed(board_id, posted.id)

    def _state_for_board(self, board_id: str) -> AgentStateStore:
        cached = self._board_states.get(board_id)
        if cached is not None:
            return cached
        store = AgentStateStore(self._state_dir / f"{board_id}_state.json")
        self._board_states[board_id] = store
        self._processed_by_board[board_id] = store.load_processed_ids()
        return store

    def _get_processed(self, board_id: str) -> set[str]:
        self._state_for_board(board_id)
        return self._processed_by_board.setdefault(board_id, set())

    def _mark_processed(self, board_id: str, message_id: str) -> None:
        processed = self._get_processed(board_id)
        if message_id in processed:
            return
        processed.add(message_id)
        self._state_for_board(board_id).save_processed_ids(processed)

    def _is_agent_message(self, message: BulletinMessage) -> bool:
        device = (message.author_device or "").strip()
        if device.startswith(AGENT_DEVICE_PREFIX):
            return True
        author = message.author_label.strip()
        return any(author == AgentConfig.author_label_for(name) for name in self._config.model_names)


def build_system_prompt(tools_config: AgentToolsConfig) -> str:
    prompt = DEFAULT_SYSTEM_PROMPT
    suffix = tools_system_prompt_suffix(tools_config)
    if suffix:
        prompt = f"{prompt}\n\n{suffix}"
    return prompt


def extract_mention_question(content: str, model_name: str) -> str | None:
    if not content.strip() or not model_name.strip():
        return None
    needle = f"@{model_name}"
    match = re.search(re.escape(needle), content, flags=re.IGNORECASE)
    if not match:
        return None
    question = content[match.end() :].strip()
    if not question:
        return "请根据留言板上的对话给出简要回应。"
    return question


def extract_mention_for_models(
    content: str,
    model_names: tuple[str, ...] | list[str],
) -> tuple[str, str] | None:
    for model_name in sorted(model_names, key=len, reverse=True):
        question = extract_mention_question(content, model_name)
        if question is not None:
            return model_name, question
    return None


def format_board_context(messages: list[BulletinMessage], model_names: tuple[str, ...] | list[str]) -> str:
    agent_labels = {AgentConfig.author_label_for(name) for name in model_names}
    lines: list[str] = []
    for message in messages:
        if message.deleted:
            continue
        author = message.author_label or "访客"
        device = message.author_device or ""
        if device.startswith(AGENT_DEVICE_PREFIX) or author in agent_labels:
            role = "助手"
        else:
            role = author
        content = message.content.strip()
        attachment_note = _format_attachment_note(message)
        if not content and not attachment_note:
            continue
        if content:
            lines.append(f"[{role}] {content}")
        if attachment_note:
            lines.append(f"[{role}] {attachment_note}")
    if not lines:
        return "（留言板暂无文本消息）"
    return "\n".join(lines)


def _format_attachment_note(message: BulletinMessage) -> str:
    attachments = message.attachments or []
    if not attachments:
        return ""
    parts: list[str] = []
    for attachment in attachments:
        attachment_id = str(attachment.get("id", "")).strip()
        name = str(attachment.get("name", "")).strip()
        kind = str(attachment.get("kind", "file"))
        if not attachment_id:
            continue
        parts.append(f"附件 id={attachment_id} name={name!r} kind={kind}")
    if not parts:
        return ""
    return "（附带 " + "；".join(parts) + "）"


def run_ollama_agent_chat(
    *,
    base_url: str,
    model: str,
    system_prompt: str,
    board_context: str,
    trigger_author: str,
    question: str,
    store: BulletinBoardStore,
    board_id: str,
    messages: list[BulletinMessage],
    tools_config: AgentToolsConfig,
    timeout_seconds: float = 300,
) -> str:
    user_content = (
        "以下是留言板上的对话记录：\n"
        f"{board_context}\n\n"
        f"用户 {trigger_author} 向你提问：\n"
        f"{question}"
    )
    chat_messages: list[dict[str, Any]] = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_content},
    ]
    tools = build_tool_definitions(tools_config)
    executor = AgentToolExecutor(store, board_id, messages, tools_config)

    for round_index in range(tools_config.max_tool_rounds):
        payload: dict[str, Any] = {
            "model": model,
            "messages": chat_messages,
            "stream": False,
        }
        if tools:
            payload["tools"] = tools

        response_message = _ollama_chat_request(
            base_url=base_url,
            payload=payload,
            timeout_seconds=timeout_seconds,
        )
        tool_calls = response_message.get("tool_calls")
        if not tool_calls:
            content = str(response_message.get("content", "")).strip()
            if content:
                return content
            raise RuntimeError("Ollama 返回空内容")

        chat_messages.append(response_message)
        for tool_call in tool_calls:
            function = tool_call.get("function") if isinstance(tool_call, dict) else None
            if not isinstance(function, dict):
                continue
            name = str(function.get("name", "")).strip()
            try:
                arguments = parse_tool_arguments(function.get("arguments"))
            except ValueError as exc:
                tool_result = json.dumps({"ok": False, "error": str(exc)}, ensure_ascii=False)
            else:
                LOGGER.info(
                    "Agent 工具调用 board=%s round=%s tool=%s args=%s",
                    board_id,
                    round_index + 1,
                    name,
                    json.dumps(arguments, ensure_ascii=False)[:200],
                )
                tool_result = executor.execute(name, arguments)
            chat_messages.append(
                {
                    "role": "tool",
                    "content": tool_result,
                    "name": name,
                }
            )

    raise RuntimeError(f"工具调用超过最大轮数 ({tools_config.max_tool_rounds})")


def _ollama_chat_request(
    *,
    base_url: str,
    payload: dict[str, Any],
    timeout_seconds: float,
) -> dict[str, Any]:
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
    return message


def start_agent(
    store: BulletinBoardStore,
    config: AgentConfig,
    state_dir: Path,
) -> BulletinBoardAgent:
    agent = BulletinBoardAgent(store, config, state_dir)
    agent.start()
    return agent
