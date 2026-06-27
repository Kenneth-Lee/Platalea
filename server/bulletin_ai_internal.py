#!/usr/bin/env python3
"""AI Agent 内部命令与 ai_status 消息约定。"""
from __future__ import annotations

from typing import Any

MESSAGE_KIND_MESSAGE = "message"
MESSAGE_KIND_AI_STATUS = "ai_status"

AI_STATUS_PREFIX = "/ai status "

DEFAULT_AGENT_COMMANDS: tuple[str, ...] = (
    "/ai status",
    "/ai stop",
)


def agent_commands_for_board(agent_applies: bool) -> list[str]:
    if not agent_applies:
        return []
    return list(DEFAULT_AGENT_COMMANDS)


def format_ai_status_content(detail: str) -> str:
    text = detail.strip()
    if text.lower().startswith("/ai status"):
        return text
    return f"{AI_STATUS_PREFIX}{text}"


def is_ai_status_message(message: Any) -> bool:
    kind = getattr(message, "message_kind", None) or _dict_get(message, "message_kind")
    if kind == MESSAGE_KIND_AI_STATUS:
        return True
    content = getattr(message, "content", None) or _dict_get(message, "content") or ""
    return str(content).strip().lower().startswith("/ai status")


def is_conversation_message(message: Any) -> bool:
    if getattr(message, "deleted", False) or _dict_get(message, "deleted"):
        return False
    return not is_ai_status_message(message)


def parse_ai_control_command(content: str) -> tuple[str, str] | None:
    """解析用户内部命令。返回 (command, detail)，如 ('stop', '') 或 ('status', '')。"""
    text = content.strip()
    if not text:
        return None
    lower = text.lower()
    if lower in {"/stopai", "/ai stop", "/ai stopai"}:
        return ("stop", "")
    if lower == "/ai status":
        return ("status", "")
    if lower.startswith("/ai status "):
        return ("status", text[len("/ai status ") :].strip())
    return None


def _dict_get(value: Any, key: str) -> Any:
    if isinstance(value, dict):
        return value.get(key)
    return None
