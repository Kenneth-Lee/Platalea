#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import threading
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any
from urllib.parse import urlparse

from bulletin_store import BulletinBoardStore, BulletinMessage

if TYPE_CHECKING:
    from bulletin_agent_status import AgentStatusReporter

DEFAULT_MAX_ATTACHMENT_READ_BYTES = 100_000
DEFAULT_MAX_WEB_FETCH_BYTES = 200_000
DEFAULT_WEB_FETCH_TIMEOUT_SECONDS = 30.0
DEFAULT_MAX_TOOL_ROUNDS = 10


@dataclass(frozen=True)
class AgentToolsConfig:
    enabled: bool = True
    web_fetch: bool = True
    attachments: bool = True
    max_attachment_read_bytes: int = DEFAULT_MAX_ATTACHMENT_READ_BYTES
    max_web_fetch_bytes: int = DEFAULT_MAX_WEB_FETCH_BYTES
    web_fetch_timeout_seconds: float = DEFAULT_WEB_FETCH_TIMEOUT_SECONDS
    max_tool_rounds: int = DEFAULT_MAX_TOOL_ROUNDS
    status_heartbeat_seconds: float = 15.0


def load_agent_tools_config(raw: dict[str, Any] | None) -> AgentToolsConfig:
    if not raw or not isinstance(raw, dict):
        return AgentToolsConfig()
    if raw.get("enabled") is False:
        return AgentToolsConfig(enabled=False)
    max_read = int(raw.get("max_attachment_read_bytes", DEFAULT_MAX_ATTACHMENT_READ_BYTES))
    max_web = int(raw.get("max_web_fetch_bytes", DEFAULT_MAX_WEB_FETCH_BYTES))
    timeout = float(raw.get("web_fetch_timeout_seconds", DEFAULT_WEB_FETCH_TIMEOUT_SECONDS))
    max_rounds = int(raw.get("max_tool_rounds", DEFAULT_MAX_TOOL_ROUNDS))
    heartbeat = float(raw.get("status_heartbeat_seconds", 15.0))
    return AgentToolsConfig(
        enabled=True,
        web_fetch=bool(raw.get("web_fetch", True)),
        attachments=bool(raw.get("attachments", True)),
        max_attachment_read_bytes=max(1024, max_read),
        max_web_fetch_bytes=max(1024, max_web),
        web_fetch_timeout_seconds=max(1.0, timeout),
        max_tool_rounds=max(1, max_rounds),
        status_heartbeat_seconds=max(3.0, heartbeat),
    )


def build_tool_definitions(config: AgentToolsConfig) -> list[dict[str, Any]]:
    if not config.enabled:
        return []
    tools: list[dict[str, Any]] = []
    if config.attachments:
        tools.append(
            {
                "type": "function",
                "function": {
                    "name": "list_attachment_files",
                    "description": (
                        "列出留言板附件中的文件。"
                        "不传 attachment_id 时返回板上全部附件及其文件清单；"
                        "传入 attachment_id 时只返回该附件内的文件。"
                    ),
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "attachment_id": {
                                "type": "string",
                                "description": "附件 ID；省略则列出板上全部附件",
                            }
                        },
                    },
                },
            }
        )
        tools.append(
            {
                "type": "function",
                "function": {
                    "name": "read_attachment_file",
                    "description": (
                        "读取留言板附件中某个文件的文本内容（如 .txt/.md/.json/.py 等）。"
                        "单文件附件可省略 file_path；目录附件必须指定 file_path（相对路径）。"
                    ),
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "attachment_id": {
                                "type": "string",
                                "description": "附件 ID",
                            },
                            "file_path": {
                                "type": "string",
                                "description": "目录附件内的相对路径；单文件附件可省略",
                            },
                        },
                        "required": ["attachment_id"],
                    },
                },
            }
        )
    if config.web_fetch:
        tools.append(
            {
                "type": "function",
                "function": {
                    "name": "web_fetch",
                    "description": (
                        "通过 HTTP/HTTPS 获取网页或 API 的文本内容。"
                        "用于查询公开网络信息；响应过大会被截断。"
                    ),
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "url": {
                                "type": "string",
                                "description": "http 或 https URL",
                            }
                        },
                        "required": ["url"],
                    },
                },
            }
        )
    return tools


class AgentToolExecutor:
    def __init__(
        self,
        store: BulletinBoardStore,
        board_id: str,
        messages: list[BulletinMessage],
        config: AgentToolsConfig,
        *,
        status_reporter: AgentStatusReporter | None = None,
        cancel_event: threading.Event | None = None,
    ) -> None:
        self._store = store
        self._board_id = board_id
        self._messages = messages
        self._config = config
        self._status_reporter = status_reporter
        self._cancel_event = cancel_event

    def _check_cancelled(self) -> None:
        if self._cancel_event is not None and self._cancel_event.is_set():
            raise RuntimeError("任务已被用户停止")

    def execute(self, name: str, arguments: dict[str, Any]) -> str:
        self._check_cancelled()
        if self._status_reporter is not None:
            self._status_reporter.update(self._tool_status_label(name, arguments))
        try:
            if name == "list_attachment_files":
                return self._list_attachment_files(arguments)
            if name == "read_attachment_file":
                return self._read_attachment_file(arguments)
            if name == "web_fetch":
                return self._web_fetch(arguments)
            return json.dumps({"ok": False, "error": f"未知工具: {name}"}, ensure_ascii=False)
        except Exception as exc:
            return json.dumps({"ok": False, "error": str(exc)}, ensure_ascii=False)

    @staticmethod
    def _tool_status_label(name: str, arguments: dict[str, Any]) -> str:
        if name == "web_fetch":
            return f"正在访问 {arguments.get('url', '网页')}…"
        if name == "list_attachment_files":
            attachment_id = str(arguments.get("attachment_id", "")).strip()
            if attachment_id:
                return f"正在列出附件 {attachment_id} 的文件…"
            return "正在列出留言板附件…"
        if name == "read_attachment_file":
            path = str(arguments.get("file_path", "")).strip()
            suffix = f" / {path}" if path else ""
            return f"正在读取附件 {arguments.get('attachment_id', '')}{suffix}…"
        return f"正在调用 {name}…"

    def _list_attachment_files(self, arguments: dict[str, Any]) -> str:
        if not self._config.attachments:
            return json.dumps({"ok": False, "error": "附件工具未启用"}, ensure_ascii=False)
        attachment_id = str(arguments.get("attachment_id", "")).strip()
        if attachment_id:
            payload = _describe_attachment_files(
                self._store, self._board_id, attachment_id, self._messages
            )
            if payload is None:
                return json.dumps(
                    {"ok": False, "error": f"附件不存在或未就绪: {attachment_id}"},
                    ensure_ascii=False,
                )
            return json.dumps({"ok": True, "attachments": [payload]}, ensure_ascii=False)

        attachments = collect_board_attachments(self._messages)
        if not attachments:
            return json.dumps({"ok": True, "attachments": [], "message": "留言板上暂无附件"}, ensure_ascii=False)

        described: list[dict[str, Any]] = []
        for item in attachments:
            detail = _describe_attachment_files(
                self._store, self._board_id, item["attachment_id"], self._messages
            )
            if detail is not None:
                described.append(detail)
        return json.dumps({"ok": True, "attachments": described}, ensure_ascii=False)

    def _read_attachment_file(self, arguments: dict[str, Any]) -> str:
        if not self._config.attachments:
            return json.dumps({"ok": False, "error": "附件工具未启用"}, ensure_ascii=False)
        attachment_id = str(arguments.get("attachment_id", "")).strip()
        if not attachment_id:
            return json.dumps({"ok": False, "error": "attachment_id 不能为空"}, ensure_ascii=False)
        file_path = str(arguments.get("file_path", "")).strip() or None
        result = read_attachment_text(
            self._store,
            self._board_id,
            attachment_id,
            file_path=file_path,
            max_bytes=self._config.max_attachment_read_bytes,
        )
        return json.dumps(result, ensure_ascii=False)

    def _web_fetch(self, arguments: dict[str, Any]) -> str:
        if not self._config.web_fetch:
            return json.dumps({"ok": False, "error": "网络工具未启用"}, ensure_ascii=False)
        url = str(arguments.get("url", "")).strip()
        if not url:
            return json.dumps({"ok": False, "error": "url 不能为空"}, ensure_ascii=False)
        result = fetch_url_text(
            url,
            max_bytes=self._config.max_web_fetch_bytes,
            timeout_seconds=self._config.web_fetch_timeout_seconds,
        )
        return json.dumps(result, ensure_ascii=False)


def collect_board_attachments(messages: list[BulletinMessage]) -> list[dict[str, Any]]:
    seen: dict[str, dict[str, Any]] = {}
    for message in messages:
        if message.deleted:
            continue
        for attachment in message.attachments or []:
            attachment_id = str(attachment.get("id", "")).strip()
            if not attachment_id or attachment_id in seen:
                continue
            seen[attachment_id] = {
                "attachment_id": attachment_id,
                "name": str(attachment.get("name", "")),
                "kind": str(attachment.get("kind", "file")),
                "message_id": message.id,
                "author_label": message.author_label,
            }
    return list(seen.values())


def _describe_attachment_files(
    store: BulletinBoardStore,
    board_id: str,
    attachment_id: str,
    messages: list[BulletinMessage],
) -> dict[str, Any] | None:
    if not store.attachments.is_attachment_ready(board_id, attachment_id):
        return None
    meta = store.attachments.get_attachment_meta(board_id, attachment_id)
    if meta is None:
        return None
    ref = next(
        (
            item
            for item in collect_board_attachments(messages)
            if item["attachment_id"] == attachment_id
        ),
        None,
    )
    kind = str(meta.get("kind", "file"))
    payload: dict[str, Any] = {
        "attachment_id": attachment_id,
        "name": str(meta.get("name") or (ref or {}).get("name", "")),
        "kind": kind,
        "message_id": (ref or {}).get("message_id"),
        "author_label": (ref or {}).get("author_label"),
    }
    if kind == "file":
        payload["files"] = [
            {
                "path": payload["name"],
                "size": int(meta.get("size", 0)),
            }
        ]
    elif kind == "directory":
        payload["files"] = [
            {
                "path": str(entry.get("path", "")),
                "size": int(entry.get("size", 0)),
                "file_id": str(entry.get("file_id", "")),
            }
            for entry in meta.get("files") or []
        ]
        payload["file_count"] = len(payload["files"])
    else:
        payload["files"] = []
    return payload


def read_attachment_text(
    store: BulletinBoardStore,
    board_id: str,
    attachment_id: str,
    *,
    file_path: str | None,
    max_bytes: int,
) -> dict[str, Any]:
    if not store.attachments.is_attachment_ready(board_id, attachment_id):
        return {"ok": False, "error": f"附件不存在或未就绪: {attachment_id}"}
    meta = store.attachments.get_attachment_meta(board_id, attachment_id)
    if meta is None:
        return {"ok": False, "error": f"附件不存在: {attachment_id}"}

    kind = str(meta.get("kind", "file"))
    if kind == "file":
        name = str(meta.get("name", "file"))
        if file_path and file_path not in {name, "."}:
            return {
                "ok": False,
                "error": f"单文件附件 {attachment_id} 无需 file_path，或请使用文件名 {name!r}",
            }
        blob = store.attachments.read_file_blob(board_id, attachment_id, None)
        if blob is None:
            return {"ok": False, "error": "读取附件失败"}
        return _decode_blob_result(name, blob.data, blob.total_size, max_bytes)

    if kind != "directory":
        return {"ok": False, "error": f"不支持的附件类型: {kind}"}

    files = meta.get("files") or []
    if not file_path:
        paths = [str(entry.get("path", "")) for entry in files]
        return {
            "ok": False,
            "error": "目录附件必须指定 file_path",
            "available_paths": paths,
        }

    normalized = _normalize_relative_path(file_path)
    match = next(
        (
            entry
            for entry in files
            if _normalize_relative_path(str(entry.get("path", ""))) == normalized
        ),
        None,
    )
    if match is None:
        paths = [str(entry.get("path", "")) for entry in files]
        return {
            "ok": False,
            "error": f"文件不存在: {file_path}",
            "available_paths": paths,
        }
    file_id = str(match.get("file_id", ""))
    blob = store.attachments.read_directory_file_blob(board_id, attachment_id, file_id, None)
    if blob is None:
        return {"ok": False, "error": "读取附件文件失败"}
    return _decode_blob_result(str(match.get("path", file_path)), blob.data, blob.total_size, max_bytes)


def _decode_blob_result(path: str, data: bytes, total_size: int, max_bytes: int) -> dict[str, Any]:
    if _looks_binary(data):
        return {
            "ok": False,
            "error": f"文件 {path!r} 似乎是二进制，无法作为文本读取",
            "size": total_size,
        }
    truncated = len(data) > max_bytes
    if truncated:
        data = data[:max_bytes]
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        text = data.decode("utf-8", errors="replace")
    result: dict[str, Any] = {
        "ok": True,
        "path": path,
        "size": total_size,
        "content": text,
    }
    if truncated:
        result["truncated"] = True
        result["max_bytes"] = max_bytes
    return result


def _looks_binary(data: bytes) -> bool:
    if not data:
        return False
    sample = data[:4096]
    if b"\x00" in sample:
        return True
    text_chars = sum(1 for byte in sample if byte in b"\n\r\t" or 32 <= byte <= 126 or byte >= 128)
    return text_chars / len(sample) < 0.85


def _normalize_relative_path(path: str) -> str:
    cleaned = path.strip().replace("\\", "/")
    while cleaned.startswith("./"):
        cleaned = cleaned[2:]
    return cleaned.lstrip("/")


def fetch_url_text(url: str, *, max_bytes: int, timeout_seconds: float) -> dict[str, Any]:
    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"}:
        return {"ok": False, "error": "仅支持 http/https URL"}
    if not parsed.netloc:
        return {"ok": False, "error": "URL 无效"}

    request = urllib.request.Request(
        url,
        headers={"User-Agent": "LocalManager-BulletinAgent/1.0"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            content_type = response.headers.get("Content-Type", "")
            raw = response.read(max_bytes + 1)
    except urllib.error.HTTPError as exc:
        detail = exc.read(min(max_bytes, 8192)).decode("utf-8", errors="replace")
        return {
            "ok": False,
            "error": f"HTTP {exc.code}",
            "url": url,
            "body_preview": detail[:2000],
        }
    except urllib.error.URLError as exc:
        return {"ok": False, "error": f"网络请求失败: {exc.reason}", "url": url}

    truncated = len(raw) > max_bytes
    if truncated:
        raw = raw[:max_bytes]

    if "html" in content_type.lower():
        text = _html_to_text(raw.decode("utf-8", errors="replace"))
    else:
        if _looks_binary(raw):
            return {
                "ok": False,
                "error": "响应似乎是二进制内容",
                "url": url,
                "content_type": content_type,
            }
        text = raw.decode("utf-8", errors="replace")

    result: dict[str, Any] = {
        "ok": True,
        "url": url,
        "content_type": content_type,
        "content": text,
    }
    if truncated:
        result["truncated"] = True
        result["max_bytes"] = max_bytes
    return result


def _html_to_text(html: str) -> str:
    text = re.sub(r"(?is)<(script|style).*?>.*?</\1>", " ", html)
    text = re.sub(r"(?i)<br\s*/?>", "\n", text)
    text = re.sub(r"(?i)</p\s*>", "\n\n", text)
    text = re.sub(r"(?s)<[^>]+>", " ", text)
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"[ \t]{2,}", " ", text)
    return text.strip()


def parse_tool_arguments(raw: Any) -> dict[str, Any]:
    if isinstance(raw, dict):
        return raw
    if isinstance(raw, str):
        text = raw.strip()
        if not text:
            return {}
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as exc:
            raise ValueError(f"工具参数不是合法 JSON: {text[:200]}") from exc
        if not isinstance(parsed, dict):
            raise ValueError("工具参数必须是 JSON 对象")
        return parsed
    return {}


def tools_system_prompt_suffix(config: AgentToolsConfig) -> str:
    if not config.enabled:
        return ""
    parts: list[str] = ["你可以使用工具完成任务："]
    if config.attachments:
        parts.append(
            "- list_attachment_files：列出留言板附件及其中文件；"
            "read_attachment_file：读取附件内文本文件内容。"
        )
    if config.web_fetch:
        parts.append("- web_fetch：获取 http/https 网页或 API 的文本内容。")
    parts.append("需要查看附件或网页信息时，请先调用工具再回答。")
    return "\n".join(parts)
