#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import json
import logging
import socket
import sys
import time
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs

from zeroconf import Zeroconf

from bulletin_attachment_store import DirectoryEntry
from bulletin_agent import AgentConfig, BulletinBoardAgent, load_agent_config, start_agent
from bulletin_mention import collect_participants, enrich_board_payload
from bulletin_store import BulletinBoardStore, BulletinBoardSnapshot, DEFAULT_BOARD_ID
from bulletin_boardpack import BoardpackError, BoardpackImportOptions, parse_import_options_payload
from bulletin_roles import (
    AuthContext,
    RolesConfig,
    auth_session_fields,
    can_access_board,
    load_roles_config,
    normalize_stored_role_ids,
)
from family_common import (
    FAMILY_SERVICE_TYPE,
    FAMILY_VERSION,
    PASSWORD_HEADER,
    binary_response,
    build_service_info,
    json_response,
    text_response,
    load_or_create_instance_id,
    normalize_hostname,
    normalize_service_type,
    read_request_body,
    start_https_server,
)

LOGGER = logging.getLogger("local_manager.bulletin_server")


@dataclass(frozen=True)
class ServerConfig:
    listen_host: str
    port: int
    service_name: str
    hostname: str
    service_type: str
    board_root: Path
    roles: RolesConfig
    cert_file: Path
    key_file: Path
    instance_id_file: Path
    log_level: str
    max_import_bytes: int | None

    @property
    def auth_required(self) -> bool:
        return self.roles.auth_required


def resolve_path(base_dir: Path, value: str) -> Path:
    path = Path(value).expanduser()
    if not path.is_absolute():
        path = base_dir / path
    return path.resolve()


def _parse_optional_positive_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    parsed = int(value)
    if parsed <= 0:
        return None
    return parsed


def load_config(config_path: Path) -> tuple[ServerConfig, AgentConfig | None]:
    if not config_path.exists():
        raise FileNotFoundError(
            f"配置文件不存在: {config_path}\n"
            f"请复制 server/config.example.json 为 {config_path.name} 后修改。"
        )
    raw = json.loads(config_path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError("配置文件必须是 JSON 对象")

    base_dir = config_path.parent.resolve()
    hostname = str(raw.get("hostname", "")).strip() or socket.gethostname()
    service_name = str(raw.get("service_name", "")).strip() or f"LocalManager-{hostname}"

    guest_password = str(raw.get("guest_password", "")).strip() or None
    host_password = str(raw.get("host_password", "")).strip() or None
    roles = load_roles_config(raw, guest_password=guest_password, host_password=host_password)

    server_config = ServerConfig(
        listen_host=str(raw.get("listen_host", "0.0.0.0")).strip() or "0.0.0.0",
        port=int(raw.get("port", 8765)),
        service_name=service_name,
        hostname=hostname,
        service_type=str(raw.get("service_type", FAMILY_SERVICE_TYPE)).strip() or FAMILY_SERVICE_TYPE,
        board_root=resolve_path(base_dir, str(raw.get("board_root", "./boards"))),
        roles=roles,
        cert_file=resolve_path(base_dir, str(raw.get("cert_file", "tls/pc_server_cert.pem"))),
        key_file=resolve_path(base_dir, str(raw.get("key_file", "tls/pc_server_key.pem"))),
        instance_id_file=resolve_path(
            base_dir,
            str(raw.get("instance_id_file", "./instance_id")),
        ),
        log_level=str(raw.get("log_level", "INFO")).strip().upper() or "INFO",
        max_import_bytes=_parse_optional_positive_int(raw.get("max_import_bytes")),
    )
    agent_raw = raw.get("agent")
    agent_config = load_agent_config(agent_raw if isinstance(agent_raw, dict) else None)
    return server_config, agent_config


class AuthService:
    def __init__(self, roles: RolesConfig) -> None:
        self._roles = roles

    def resolve(self, headers: dict[str, str]) -> AuthContext | None:
        return self._roles.resolve(headers)


class BulletinBoardHttpHandler(BaseHTTPRequestHandler):
    server_version = "LocalManagerBulletinServer/0.2"

    @property
    def store(self) -> BulletinBoardStore:
        return self.server.store  # type: ignore[attr-defined]

    @property
    def auth_service(self) -> AuthService:
        return self.server.auth_service  # type: ignore[attr-defined]

    def do_GET(self) -> None:
        self._dispatch("GET")

    def do_POST(self) -> None:
        self._dispatch("POST")

    def do_PUT(self) -> None:
        self._dispatch("PUT")

    def do_DELETE(self) -> None:
        self._dispatch("DELETE")

    def _dispatch(self, method: str) -> None:
        normalized_path = self.path.split("?", 1)[0].rstrip("/") or "/"
        query_string = self.path.split("?", 1)[1] if "?" in self.path else ""
        if normalized_path.startswith("/boards"):
            auth = self.auth_service.resolve(self._header_map())
            if auth is None:
                json_response(
                    self,
                    HTTPStatus.UNAUTHORIZED,
                    {
                        "ok": False,
                        "error": "unauthorized",
                        "message": "需要正确的网络服务密码",
                    },
                )
                return
            body_bytes = read_request_body(self) if method in {"POST", "PUT"} else b""
            response = handle_board_request(
                self.store,
                method,
                normalized_path,
                body_bytes,
                auth,
                self._header_map(),
                agent=getattr(self.server, "agent", None),
                max_import_bytes=getattr(self.server, "max_import_bytes", None),
                query_string=query_string,
            )
            if response.get("binary") is not None:
                binary_response(
                    self,
                    int(response["status"]),
                    response["binary"],
                    content_type=response.get("content_type", "application/octet-stream"),
                    extra_headers=response.get("extra_headers"),
                )
            elif response.get("body_text") is not None:
                text_response(
                    self,
                    int(response["status"]),
                    str(response["body_text"]),
                    content_type=response.get("content_type", "text/plain; charset=utf-8"),
                )
            else:
                json_response(self, response["status"], response["body"])
            return

        if method == "GET" and normalized_path == "/agent":
            auth = self.auth_service.resolve(self._header_map())
            if auth is None and self.auth_service._roles.auth_required:
                json_response(
                    self,
                    HTTPStatus.UNAUTHORIZED,
                    {
                        "ok": False,
                        "error": "unauthorized",
                        "message": "需要正确的网络服务密码",
                    },
                )
                return
            agent = getattr(self.server, "agent", None)
            if agent is None:
                json_response(
                    self,
                    HTTPStatus.OK,
                    {"ok": True, "enabled": False, "models": [], "board_ids": None, "commands": []},
                )
            else:
                json_response(self, HTTPStatus.OK, agent._config.to_public_json())
            return

        if method == "GET" and normalized_path in {"/", ""}:
            boards = self.store.list_boards()
            json_response(
                self,
                HTTPStatus.OK,
                {
                    "service": "LocalManager Bulletin Board",
                    "version": FAMILY_VERSION,
                    "board_count": len(boards),
                },
            )
            return

        json_response(
            self,
            HTTPStatus.NOT_FOUND,
            {"ok": False, "error": "not_found", "message": "接口不存在"},
        )

    def _header_map(self) -> dict[str, str]:
        return {key: value for key, value in self.headers.items()}

    def log_message(self, fmt: str, *args: Any) -> None:
        LOGGER.debug("HTTP %s - %s", self.address_string(), fmt % args)


def requires_manage_auth(method: str, path: str) -> bool:
    if method == "POST" and path in {"/boards", "/boards/import"}:
        return True
    if method == "PUT" and path.startswith("/boards/") and "/messages" not in path and "/attachments" not in path:
        return True
    if (
        method == "DELETE"
        and path.startswith("/boards/")
        and "/messages" not in path
        and "/attachments" not in path
    ):
        return True
    if method == "DELETE" and "/attachments/" in path:
        return True
    if method in {"PUT", "DELETE"} and "/messages" in path:
        return True
    return False


def _forbidden(message: str = "当前角色无权执行此操作") -> dict[str, Any]:
    return {
        "status": HTTPStatus.FORBIDDEN,
        "body": {"ok": False, "error": "forbidden", "message": message},
    }


def _board_access_denied() -> dict[str, Any]:
    return {
        "status": HTTPStatus.NOT_FOUND,
        "body": {
            "ok": False,
            "error": "board_not_found",
            "message": "留言板不存在或当前角色无权访问",
        },
    }


def _board_to_json(board, auth: AuthContext) -> dict[str, Any]:
    item: dict[str, Any] = {
        "id": board.id,
        "name": board.name,
        "revision": board.revision,
        "message_count": board.message_count,
    }
    if auth.can_manage:
        item["role_ids"] = list(board.role_ids) if board.role_ids is not None else None
    return item


def _parse_role_ids_payload(payload: dict[str, Any]) -> tuple[str, ...] | None:
    if "role_ids" not in payload:
        return None
    raw = payload.get("role_ids")
    if raw is None:
        return ()
    if not isinstance(raw, list):
        raise ValueError("role_ids 必须是数组")
    return normalize_stored_role_ids(raw)


def _require_board_access(
    store: BulletinBoardStore,
    board_id: str,
    auth: AuthContext,
) -> dict[str, Any] | None:
    board_info = store.get_board_info(board_id)
    if board_info is None or not can_access_board(auth, board_info.role_ids):
        return _board_access_denied()
    return None


def handle_board_request(
    store: BulletinBoardStore,
    method: str,
    path: str,
    body_bytes: bytes,
    auth: AuthContext,
    headers: dict[str, str],
    agent: BulletinBoardAgent | None = None,
    max_import_bytes: int | None = None,
    query_string: str = "",
) -> dict[str, Any]:
    if requires_manage_auth(method, path) and not auth.can_manage:
        return _forbidden("修改或删除需要管理员权限")

    if method == "GET" and path == "/boards":
        boards = store.list_boards()
        visible = [board for board in boards if can_access_board(auth, board.role_ids)]
        body: dict[str, Any] = {
            "ok": True,
            "boards": [_board_to_json(board, auth) for board in visible],
            **auth_session_fields(auth),
        }
        return {"status": HTTPStatus.OK, "body": body}

    if method == "POST" and path == "/boards":
        if not auth.can_create_boards:
            return _forbidden("创建留言板需要管理员权限")
        payload = _parse_json(body_bytes.decode("utf-8"))
        if payload is None:
            return _bad_request("invalid_json", "请求体不是合法 JSON")
        name = str(payload.get("name", "")).strip()
        if not name:
            return _bad_request("invalid_board", "留言板名称不能为空")
        try:
            role_ids = _parse_role_ids_payload(payload)
        except ValueError as exc:
            return _bad_request("invalid_board", str(exc))
        if role_ids is None:
            role_ids = ()
        board = store.create_board(name, role_ids=role_ids)
        if board is None:
            return _bad_request("invalid_board", "创建留言板失败")
        return {
            "status": HTTPStatus.OK,
            "body": {
                "ok": True,
                "board": _board_to_json(board, auth),
            },
        }

    if method == "POST" and path == "/boards/import":
        if not auth.can_create_boards:
            return _forbidden("导入留言板需要管理员权限")
        if not body_bytes:
            return _bad_request("invalid_boardpack", "请求体为空")
        try:
            options = _parse_boardpack_import_options(query_string, headers)
        except BoardpackError as exc:
            return _bad_request(exc.code, exc.message)
        try:
            board = store.import_boardpack(
                body_bytes,
                name=options.name,
                role_ids=options.role_ids,
                max_import_bytes=max_import_bytes,
            )
        except ValueError as exc:
            return _boardpack_error_response(str(exc))
        return {
            "status": HTTPStatus.OK,
            "body": {"ok": True, "board": _board_to_json(board, auth)},
        }

    if (
        method == "PUT"
        and path.startswith("/boards/")
        and "/messages" not in path
        and "/attachments" not in path
    ):
        board_id = path.removeprefix("/boards/")
        if not board_id or "/" in board_id:
            return _not_found()
        board_info = store.get_board_info(board_id)
        if board_info is None:
            return _board_access_denied()
        payload = _parse_json(body_bytes.decode("utf-8"))
        if payload is None:
            return _bad_request("invalid_json", "请求体不是合法 JSON")
        name = payload.get("name")
        try:
            role_ids = _parse_role_ids_payload(payload)
        except ValueError as exc:
            return _bad_request("invalid_board", str(exc))
        updated = store.update_board(
            board_id,
            name=str(name).strip() if name is not None else None,
            role_ids=role_ids,
        )
        if updated is None:
            return _bad_request("invalid_board", "更新留言板失败")
        return {
            "status": HTTPStatus.OK,
            "body": {"ok": True, "board": _board_to_json(updated, auth)},
        }

    if (
        method == "DELETE"
        and path.startswith("/boards/")
        and "/messages" not in path
        and "/attachments" not in path
    ):
        board_id = path.removeprefix("/boards/")
        if not board_id or "/" in board_id:
            return _not_found()
        board_info = store.get_board_info(board_id)
        if board_info is None or not can_access_board(auth, board_info.role_ids):
            return _board_access_denied()
        if not store.delete_board(board_id):
            return _bad_request(
                "board_delete_failed",
                "删除失败：留言板不存在，或这是最后一个留言板",
            )
        return {"status": HTTPStatus.OK, "body": {"ok": True}}

    if method == "GET" and path.startswith("/boards/") and path.endswith("/export.md"):
        board_id = path.removeprefix("/boards/").removesuffix("/export.md")
        board_info = store.get_board_info(board_id)
        if board_info is None or not can_access_board(auth, board_info.role_ids):
            return _board_access_denied()
        markdown = store.export_markdown(board_id)
        if markdown is None:
            return {
                "status": HTTPStatus.NOT_FOUND,
                "body": {
                    "ok": False,
                    "error": "board_not_found",
                    "message": f"留言板不存在：{board_id}",
                },
            }
        return {
            "status": HTTPStatus.OK,
            "body_text": markdown,
            "content_type": "text/markdown; charset=utf-8",
        }

    if method == "GET" and path.startswith("/boards/") and path.endswith("/export.boardpack"):
        board_id = path.removeprefix("/boards/").removesuffix("/export.boardpack")
        board_info = store.get_board_info(board_id)
        if board_info is None or not can_access_board(auth, board_info.role_ids):
            return _board_access_denied()
        pack = store.export_boardpack(board_id)
        if pack is None:
            return {
                "status": HTTPStatus.NOT_FOUND,
                "body": {
                    "ok": False,
                    "error": "board_not_found",
                    "message": f"留言板不存在：{board_id}",
                },
            }
        return {
            "status": HTTPStatus.OK,
            "binary": pack,
            "content_type": "application/vnd.localmanager.boardpack+zip",
            "extra_headers": {
                "Content-Disposition": f'attachment; filename="{_boardpack_filename(board_info.name)}"',
            },
        }

    if method == "GET" and path.startswith("/boards/") and path.endswith("/messages"):
        board_id = path.removeprefix("/boards/").removesuffix("/messages")
        board_info = store.get_board_info(board_id)
        if board_info is None or not can_access_board(auth, board_info.role_ids):
            return _board_access_denied()
        snapshot = store.snapshot(board_id)
        if snapshot is None:
            return _board_access_denied()
        agents = agent._config.models_for_board(board_id) if agent is not None else []
        commands = agent._config.commands_for_board(board_id) if agent is not None else []
        participants = collect_participants(snapshot.messages)
        body = enrich_board_payload(
            BulletinBoardSnapshot(
                board_id=snapshot.board_id,
                board_name=snapshot.board_name,
                revision=snapshot.revision,
                messages=snapshot.messages,
                can_manage=auth.can_manage,
            ).to_json(),
            agents=agents,
            participants=participants,
            commands=commands,
        )
        body.update(auth_session_fields(auth))
        return {"status": HTTPStatus.OK, "body": body}

    if method == "POST" and path.startswith("/boards/") and path.endswith("/messages"):
        board_id = path.removeprefix("/boards/").removesuffix("/messages")
        board_info = store.get_board_info(board_id)
        if board_info is None or not can_access_board(auth, board_info.role_ids):
            return _board_access_denied()
        body_text = body_bytes.decode("utf-8")
        payload = _parse_json(body_text)
        if payload is None:
            return _bad_request("invalid_json", "请求体不是合法 JSON")
        content = str(payload.get("content", ""))
        author_label = str(payload.get("author_label", "访客"))
        author_device = str(payload.get("author_device", "")).strip() or None
        if agent is not None:
            from bulletin_ai_internal import parse_ai_control_command

            control = parse_ai_control_command(content)
            if control is not None:
                command, detail = control
                agent.notify_control_command(board_id, command, detail, author_label)
                snapshot = store.snapshot(board_id)
                return {
                    "status": HTTPStatus.OK,
                    "body": {
                        "ok": True,
                        "internal": True,
                        "command": command,
                        "revision": snapshot.revision if snapshot else 0,
                    },
                }
        attachments = payload.get("attachments")
        attachment_list = attachments if isinstance(attachments, list) else None
        message = store.append_message(
            board_id, author_label, content, author_device, attachment_list
        )
        if message is None:
            detail = (
                "消息无效或附件未就绪"
                if attachment_list
                else "消息内容不能为空或留言板不存在"
            )
            return _bad_request("invalid_message", detail)
        if agent is not None:
            agent.notify_new_message(board_id, message.id)
        snapshot = store.snapshot(board_id)
        return {
            "status": HTTPStatus.OK,
            "body": {
                "ok": True,
                "message": message.to_json(),
                "revision": snapshot.revision if snapshot else 0,
            },
        }

    if method == "POST" and path.endswith("/attachments/init"):
        board_id = path.removeprefix("/boards/").removesuffix("/attachments/init")
        denied = _require_board_access(store, board_id, auth)
        if denied is not None:
            return denied
        return _init_attachment(store, board_id, body_bytes.decode("utf-8"))

    if method == "PUT" and "/attachments/" in path and "/chunks/" in path:
        board_id = path.removeprefix("/boards/").split("/attachments/", 1)[0]
        denied = _require_board_access(store, board_id, auth)
        if denied is not None:
            return denied
        return _put_attachment_chunk(store, path, body_bytes)

    if method == "POST" and path.endswith("/complete") and "/attachments/" in path:
        match = re.match(r"/boards/([^/]+)/attachments/([^/]+)/complete", path)
        if not match:
            return _not_found()
        board_id, attachment_id = match.groups()
        denied = _require_board_access(store, board_id, auth)
        if denied is not None:
            return denied
        return _complete_attachment(store, board_id, attachment_id)

    if method == "GET" and path.endswith("/blob") and "/attachments/" in path:
        board_id = path.removeprefix("/boards/").split("/attachments/", 1)[0]
        denied = _require_board_access(store, board_id, auth)
        if denied is not None:
            return denied
        return _get_attachment_blob(store, path, headers.get("Range") or headers.get("range"))

    if (
        method == "GET"
        and "/attachments/" in path
        and not path.endswith("/blob")
        and not path.endswith("/complete")
    ):
        parts = path.removeprefix("/boards/").split("/attachments/", 1)
        if len(parts) != 2:
            return _not_found()
        board_id, attachment_id = parts
        denied = _require_board_access(store, board_id, auth)
        if denied is not None:
            return denied
        meta = store.attachments.get_attachment_meta(board_id, attachment_id)
        if meta is None:
            return {
                "status": HTTPStatus.NOT_FOUND,
                "body": {
                    "ok": False,
                    "error": "attachment_not_found",
                    "message": f"附件不存在：{attachment_id}",
                },
            }
        return {"status": HTTPStatus.OK, "body": {"ok": True, **meta}}

    if method == "DELETE" and "/attachments/" in path:
        parts = path.removeprefix("/boards/").split("/attachments/", 1)
        if len(parts) != 2:
            return _not_found()
        board_id, attachment_id = parts
        denied = _require_board_access(store, board_id, auth)
        if denied is not None:
            return denied
        if not store.attachments.delete_attachment(board_id, attachment_id):
            return {
                "status": HTTPStatus.NOT_FOUND,
                "body": {
                    "ok": False,
                    "error": "attachment_not_found",
                    "message": "附件不存在",
                },
            }
        return {"status": HTTPStatus.OK, "body": {"ok": True}}

    if method == "PUT" and path.startswith("/boards/") and "/messages/" in path:
        parts = path.removeprefix("/boards/").split("/messages/", 1)
        if len(parts) != 2:
            return _not_found()
        board_id, message_id = parts
        denied = _require_board_access(store, board_id, auth)
        if denied is not None:
            return denied
        payload = _parse_json(body_bytes.decode("utf-8"))
        if payload is None:
            return _bad_request("invalid_json", "请求体不是合法 JSON")
        content = str(payload.get("content", ""))
        message = store.update_message(board_id, message_id, content)
        if message is None:
            return {
                "status": HTTPStatus.NOT_FOUND,
                "body": {
                    "ok": False,
                    "error": "message_not_found",
                    "message": "消息不存在或内容为空",
                },
            }
        snapshot = store.snapshot(board_id)
        return {
            "status": HTTPStatus.OK,
            "body": {
                "ok": True,
                "message": message.to_json(),
                "revision": snapshot.revision if snapshot else 0,
            },
        }

    if method == "DELETE" and path.startswith("/boards/") and "/messages/" in path:
        parts = path.removeprefix("/boards/").split("/messages/", 1)
        if len(parts) != 2:
            return _not_found()
        board_id, message_id = parts
        denied = _require_board_access(store, board_id, auth)
        if denied is not None:
            return denied
        if not store.delete_message(board_id, message_id):
            return {
                "status": HTTPStatus.NOT_FOUND,
                "body": {
                    "ok": False,
                    "error": "message_not_found",
                    "message": "消息不存在",
                },
            }
        snapshot = store.snapshot(board_id)
        return {
            "status": HTTPStatus.OK,
            "body": {
                "ok": True,
                "revision": snapshot.revision if snapshot else 0,
            },
        }

    return _not_found()


def _boardpack_filename(board_name: str) -> str:
    safe = re.sub(r'[\\/:*?"<>|]+', "_", board_name.strip()).strip("._") or "board"
    return f"{safe}.boardpack"


def _parse_boardpack_import_options(
    query_string: str,
    headers: dict[str, str],
) -> BoardpackImportOptions:
    name: str | None = None
    role_ids: tuple[str, ...] | None = None
    if query_string.strip():
        params = parse_qs(query_string, keep_blank_values=True)
        if params.get("name"):
            name = str(params["name"][0]).strip() or None
        if "role_ids" in params:
            raw = str(params.get("role_ids", [""])[0]).strip()
            role_ids = (
                normalize_stored_role_ids(
                    [part.strip() for part in raw.split(",") if part.strip()]
                )
                if raw
                else ()
            )
    options = BoardpackImportOptions(name=name, role_ids=role_ids)
    header_raw = headers.get("X-Boardpack-Import-Options", "").strip()
    if not header_raw:
        return options
    payload = _parse_json(header_raw)
    if payload is None:
        raise BoardpackError("invalid_board", "X-Boardpack-Import-Options 不是合法 JSON")
    return parse_import_options_payload(payload)


def _boardpack_error_response(message: str) -> dict[str, Any]:
    if ":" in message:
        code, detail = message.split(":", 1)
        return _bad_request(code.strip(), detail.strip())
    return _bad_request("invalid_boardpack", message)


def _parse_json(body_text: str) -> dict[str, Any] | None:
    if not body_text.strip():
        return {}
    try:
        payload = json.loads(body_text)
    except json.JSONDecodeError:
        return None
    if not isinstance(payload, dict):
        return None
    return payload


def _bad_request(code: str, message: str) -> dict[str, Any]:
    return {
        "status": HTTPStatus.BAD_REQUEST,
        "body": {"ok": False, "error": code, "message": message},
    }


def _not_found() -> dict[str, Any]:
    return {
        "status": HTTPStatus.NOT_FOUND,
        "body": {"ok": False, "error": "not_found", "message": "接口不存在"},
    }


def _init_attachment(store: BulletinBoardStore, board_id: str, body_text: str) -> dict[str, Any]:
    payload = _parse_json(body_text)
    if payload is None:
        return _bad_request("invalid_json", "请求体不是合法 JSON")
    kind = str(payload.get("kind", "file"))
    uploader_device = str(payload.get("uploader_device", "")).strip() or None
    if kind == "file":
        name = str(payload.get("name", ""))
        size = int(payload.get("size", -1))
        if not name.strip() or size < 0:
            return _bad_request("invalid_attachment", "单文件附件需要 name 与 size")
        result = store.attachments.init_file_upload(
            board_id,
            name,
            size,
            sha256=str(payload.get("sha256", "")).strip() or None,
            mime=str(payload.get("mime", "")).strip() or None,
            uploader_device=uploader_device,
        )
    elif kind == "directory":
        name = str(payload.get("name", ""))
        entries_raw = payload.get("entries")
        if not name.strip() or not isinstance(entries_raw, list):
            return _bad_request("invalid_attachment", "目录附件需要 name 与 entries")
        entries = [
            DirectoryEntry(
                path=str(item.get("path", "")),
                size=int(item.get("size", -1)),
                sha256=str(item.get("sha256", "")).strip() or None,
            )
            for item in entries_raw
            if isinstance(item, dict) and str(item.get("path", "")).strip()
        ]
        if not entries:
            return _bad_request("invalid_attachment", "目录附件 entries 无效")
        result = store.attachments.init_directory_upload(board_id, name, entries, uploader_device)
    else:
        return _bad_request("invalid_attachment", "kind 无效")
    if result is None:
        return _bad_request("invalid_attachment", "初始化附件失败")
    body: dict[str, Any] = {
        "ok": True,
        "attachment_id": result.attachment_id,
        "chunk_size": result.chunk_size,
        "status": "uploading",
    }
    if result.directory_files:
        body["files"] = [
            {"file_id": slot.file_id, "path": slot.path, "size": slot.size}
            for slot in result.directory_files
        ]
    return {"status": HTTPStatus.OK, "body": body}


def _put_attachment_chunk(
    store: BulletinBoardStore, path: str, body_bytes: bytes
) -> dict[str, Any]:
    file_match = re.match(
        r"/boards/([^/]+)/attachments/([^/]+)/files/([^/]+)/chunks/(\d+)", path
    )
    if file_match:
        board_id, attachment_id, file_id, chunk_index_text = file_match.groups()
        chunk_index = int(chunk_index_text)
        result = store.attachments.write_directory_file_chunk(
            board_id, attachment_id, file_id, chunk_index, body_bytes
        )
        if result is None:
            return _bad_request("invalid_attachment", "目录附件分块写入失败")
        return {
            "status": HTTPStatus.OK,
            "body": {"ok": True, **result},
        }
    file_only = re.match(r"/boards/([^/]+)/attachments/([^/]+)/chunks/(\d+)", path)
    if not file_only:
        return _not_found()
    board_id, attachment_id, chunk_index_text = file_only.groups()
    result = store.attachments.write_file_chunk(
        board_id, attachment_id, int(chunk_index_text), body_bytes
    )
    if result is None:
        return _bad_request("invalid_attachment", "附件分块写入失败")
    return {"status": HTTPStatus.OK, "body": {"ok": True, **result}}


def _complete_attachment(
    store: BulletinBoardStore, board_id: str, attachment_id: str
) -> dict[str, Any]:
    meta = store.attachments.complete_upload(board_id, attachment_id)
    if meta is None:
        return _bad_request("incomplete_upload", "附件未完成上传或校验失败")
    return {
        "status": HTTPStatus.OK,
        "body": {"ok": True, "attachment_id": attachment_id, "status": "ready"},
    }


def _get_attachment_blob(
    store: BulletinBoardStore, path: str, range_header: str | None
) -> dict[str, Any]:
    dir_match = re.match(
        r"/boards/([^/]+)/attachments/([^/]+)/files/([^/]+)/blob", path
    )
    if dir_match:
        board_id, attachment_id, file_id = dir_match.groups()
        result = store.attachments.read_directory_file_blob(
            board_id, attachment_id, file_id, range_header
        )
    else:
        file_match = re.match(r"/boards/([^/]+)/attachments/([^/]+)/blob", path)
        if not file_match:
            return _not_found()
        board_id, attachment_id = file_match.groups()
        result = store.attachments.read_file_blob(board_id, attachment_id, range_header)
    if result is None:
        return {
            "status": HTTPStatus.NOT_FOUND,
            "body": {
                "ok": False,
                "error": "attachment_not_found",
                "message": "附件不存在或未就绪",
            },
        }
    extra_headers: dict[str, str] = {}
    if result.content_range:
        extra_headers["Content-Range"] = result.content_range
    return {
        "status": result.status,
        "binary": result.data,
        "content_type": "application/octet-stream",
        "extra_headers": extra_headers,
    }


def build_argument_parser() -> argparse.ArgumentParser:
    default_config = Path(__file__).resolve().parent / "config.json"
    parser = argparse.ArgumentParser(
        description="LocalManager PC 端留言板 HTTPS 服务（mDNS 可发现，兼容 Android 客户端）。",
    )
    parser.add_argument(
        "--config",
        default=str(default_config),
        help=f"配置文件路径，默认 {default_config}",
    )
    return parser


def run_server(config: ServerConfig, agent_config: AgentConfig | None = None) -> int:
    if config.port <= 0 or config.port > 65535:
        raise ValueError(f"端口无效: {config.port}")
    if not config.cert_file.exists():
        raise FileNotFoundError(
            f"TLS 证书不存在: {config.cert_file}\n"
            "请先运行 server/generate_tls_materials.sh 生成证书。"
        )
    if not config.key_file.exists():
        raise FileNotFoundError(f"TLS 私钥不存在: {config.key_file}")

    store = BulletinBoardStore(config.board_root)
    auth_service = AuthService(config.roles)
    instance_id = load_or_create_instance_id(config.instance_id_file)
    service_type = normalize_service_type(config.service_type)
    hostname = normalize_hostname(config.hostname)

    https_server, _thread, tls_fingerprint = start_https_server(
        config.listen_host,
        config.port,
        config.cert_file,
        config.key_file,
        BulletinBoardHttpHandler,
    )
    https_server.store = store  # type: ignore[attr-defined]
    https_server.auth_service = auth_service  # type: ignore[attr-defined]
    https_server.max_import_bytes = config.max_import_bytes  # type: ignore[attr-defined]

    zeroconf_client: Zeroconf | None = None
    service_info = build_service_info(
        service_type=service_type,
        service_name=config.service_name,
        hostname=hostname,
        port=config.port,
        instance_id=instance_id,
        tls_fingerprint=tls_fingerprint,
        auth_required=config.auth_required,
        platform="python",
    )

    LOGGER.info("留言板数据目录: %s", config.board_root)
    LOGGER.info(
        "密码策略: roles=%s auth_required=%s",
        ",".join(sorted(config.roles.roles.keys())),
        config.auth_required,
    )
    LOGGER.info("默认留言板 ID: %s", DEFAULT_BOARD_ID)

    agent_handle: BulletinBoardAgent | None = None
    if agent_config is not None:
        agent_state_dir = config.board_root / ".agent"
        agent_handle = start_agent(store, agent_config, agent_state_dir)
        https_server.agent = agent_handle  # type: ignore[attr-defined]
        LOGGER.info(
            "AI Agent 已随服务启动: %s",
            ", ".join(f"@{name}" for name in agent_config.model_names),
        )

    try:
        zeroconf_client = Zeroconf()
        zeroconf_client.register_service(service_info)
        LOGGER.info(
            "mDNS 服务已注册: name=%s type=%s hostname=%s port=%s instance_id=%s fingerprint=%s",
            service_info.name,
            service_type,
            hostname,
            config.port,
            instance_id,
            tls_fingerprint,
        )
        LOGGER.info("服务就绪。Android 客户端可在同一局域网中发现并连接本机。")
        while _thread.is_alive():
            time.sleep(0.5)
        return 0
    except KeyboardInterrupt:
        LOGGER.info("收到 Ctrl+C，准备退出。")
        return 0
    finally:
        if agent_handle is not None:
            agent_handle.stop()
        if zeroconf_client is not None:
            try:
                zeroconf_client.unregister_service(service_info)
                LOGGER.info("mDNS 服务已注销: %s", service_info.name)
            except Exception as exc:
                LOGGER.warning("注销 mDNS 服务失败: %s", exc)
            zeroconf_client.close()
        https_server.shutdown()
        https_server.server_close()
        LOGGER.info("HTTPS 服务已关闭。")


def main() -> int:
    parser = build_argument_parser()
    args = parser.parse_args()
    try:
        config, agent_config = load_config(Path(args.config).resolve())
    except Exception as exc:
        print(f"加载配置失败: {exc}", file=sys.stderr)
        return 1

    logging.basicConfig(
        level=getattr(logging, config.log_level, logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    try:
        return run_server(config, agent_config)
    except Exception as exc:
        LOGGER.exception("留言板服务启动失败: %s", exc)
        return 1


if __name__ == "__main__":
    sys.exit(main())
