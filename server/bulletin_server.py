#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import logging
import socket
import sys
import time
from dataclasses import dataclass
from enum import Enum
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler
from pathlib import Path
from typing import Any

from zeroconf import Zeroconf

from bulletin_store import BulletinBoardStore, DEFAULT_BOARD_ID
from family_common import (
    FAMILY_SERVICE_TYPE,
    FAMILY_VERSION,
    PASSWORD_HEADER,
    build_service_info,
    json_response,
    load_or_create_instance_id,
    normalize_hostname,
    normalize_service_type,
    read_request_body,
    start_https_server,
)

LOGGER = logging.getLogger("local_manager.bulletin_server")


class AuthLevel(str, Enum):
    OPEN = "open"
    GUEST = "guest"
    HOST = "host"


@dataclass(frozen=True)
class ServerConfig:
    listen_host: str
    port: int
    service_name: str
    hostname: str
    service_type: str
    board_root: Path
    guest_password: str | None
    host_password: str | None
    cert_file: Path
    key_file: Path
    instance_id_file: Path
    log_level: str

    @property
    def auth_required(self) -> bool:
        return bool(self.guest_password or self.host_password)


def resolve_path(base_dir: Path, value: str) -> Path:
    path = Path(value).expanduser()
    if not path.is_absolute():
        path = base_dir / path
    return path.resolve()


def load_config(config_path: Path) -> ServerConfig:
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

    return ServerConfig(
        listen_host=str(raw.get("listen_host", "0.0.0.0")).strip() or "0.0.0.0",
        port=int(raw.get("port", 8765)),
        service_name=service_name,
        hostname=hostname,
        service_type=str(raw.get("service_type", FAMILY_SERVICE_TYPE)).strip() or FAMILY_SERVICE_TYPE,
        board_root=resolve_path(base_dir, str(raw.get("board_root", "./boards"))),
        guest_password=guest_password,
        host_password=host_password,
        cert_file=resolve_path(base_dir, str(raw.get("cert_file", "tls/pc_server_cert.pem"))),
        key_file=resolve_path(base_dir, str(raw.get("key_file", "tls/pc_server_key.pem"))),
        instance_id_file=resolve_path(
            base_dir,
            str(raw.get("instance_id_file", "./instance_id")),
        ),
        log_level=str(raw.get("log_level", "INFO")).strip().upper() or "INFO",
    )


class AuthService:
    def __init__(self, config: ServerConfig) -> None:
        self._config = config

    def resolve(self, headers: dict[str, str]) -> AuthLevel | None:
        guest = self._config.guest_password
        host = self._config.host_password
        if not guest and not host:
            return AuthLevel.OPEN

        provided = headers.get(PASSWORD_HEADER, "").strip()
        if not provided:
            return None
        if host and provided == host:
            return AuthLevel.HOST
        if guest and provided == guest:
            return AuthLevel.GUEST
        return None


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
        if normalized_path.startswith("/boards"):
            auth_level = self.auth_service.resolve(self._header_map())
            if auth_level is None:
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
            if method in {"PUT", "DELETE"} and auth_level not in {AuthLevel.HOST, AuthLevel.OPEN}:
                json_response(
                    self,
                    HTTPStatus.FORBIDDEN,
                    {
                        "ok": False,
                        "error": "forbidden",
                        "message": "修改或删除留言需要宿主密码",
                    },
                )
                return
            body = read_request_body(self).decode("utf-8") if method in {"POST", "PUT"} else ""
            response = handle_board_request(self.store, method, normalized_path, body)
            json_response(self, response["status"], response["body"])
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


def handle_board_request(
    store: BulletinBoardStore,
    method: str,
    path: str,
    body_text: str,
) -> dict[str, Any]:
    if method == "GET" and path == "/boards":
        boards = store.list_boards()
        return {
            "status": HTTPStatus.OK,
            "body": {
                "ok": True,
                "boards": [
                    {
                        "id": board.id,
                        "name": board.name,
                        "revision": board.revision,
                        "message_count": board.message_count,
                    }
                    for board in boards
                ],
            },
        }

    if method == "GET" and path.startswith("/boards/") and path.endswith("/messages"):
        board_id = path.removeprefix("/boards/").removesuffix("/messages")
        snapshot = store.snapshot(board_id)
        if snapshot is None:
            return {
                "status": HTTPStatus.NOT_FOUND,
                "body": {
                    "ok": False,
                    "error": "board_not_found",
                    "message": f"留言板不存在：{board_id}",
                },
            }
        return {"status": HTTPStatus.OK, "body": snapshot.to_json()}

    if method == "POST" and path.startswith("/boards/") and path.endswith("/messages"):
        board_id = path.removeprefix("/boards/").removesuffix("/messages")
        payload = _parse_json(body_text)
        if payload is None:
            return _bad_request("invalid_json", "请求体不是合法 JSON")
        content = str(payload.get("content", ""))
        author_label = str(payload.get("author_label", "访客"))
        message = store.append_message(board_id, author_label, content)
        if message is None:
            return _bad_request("invalid_message", "消息内容不能为空或留言板不存在")
        snapshot = store.snapshot(board_id)
        return {
            "status": HTTPStatus.OK,
            "body": {
                "ok": True,
                "message": message.to_json(),
                "revision": snapshot.revision if snapshot else 0,
            },
        }

    if method == "PUT" and path.startswith("/boards/") and "/messages/" in path:
        parts = path.removeprefix("/boards/").split("/messages/", 1)
        if len(parts) != 2:
            return _not_found()
        board_id, message_id = parts
        payload = _parse_json(body_text)
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


def run_server(config: ServerConfig) -> int:
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
    auth_service = AuthService(config)
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
        "密码策略: guest=%s host=%s auth_required=%s",
        "已设置" if config.guest_password else "未设置",
        "已设置" if config.host_password else "未设置",
        config.auth_required,
    )
    LOGGER.info("默认留言板 ID: %s", DEFAULT_BOARD_ID)

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
        config = load_config(Path(args.config).resolve())
    except Exception as exc:
        print(f"加载配置失败: {exc}", file=sys.stderr)
        return 1

    logging.basicConfig(
        level=getattr(logging, config.log_level, logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    try:
        return run_server(config)
    except Exception as exc:
        LOGGER.exception("留言板服务启动失败: %s", exc)
        return 1


if __name__ == "__main__":
    sys.exit(main())
