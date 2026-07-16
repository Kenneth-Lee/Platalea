from __future__ import annotations

import json
import platform
import socket
from pathlib import Path
from typing import Any

from .state import load_control_token

# Windows broker 走 TCP localhost（AF_UNIX 在 Windows 不可用）。
# 与 broker_server.py 保持一致。
WINDOWS_BROKER_HOST = "127.0.0.1"
WINDOWS_BROKER_PORT = 51871


def _is_windows() -> bool:
    return platform.system().lower() == "windows"


class BrokerClientError(RuntimeError):
    pass


def broker_socket_path(state_root: Path) -> Path:
    return state_root / "broker.sock"


def request_broker(
    *,
    state_root: Path,
    op: str,
    payload: dict[str, Any] | None = None,
    timeout_seconds: float = 3.0,
) -> dict[str, Any]:
    token = load_control_token(state_root / "state.json")
    if not token:
        raise BrokerClientError(f"控制面 token 不存在: {state_root / 'state.json'}")

    request_obj: dict[str, Any] = {"op": op}
    if payload:
        request_obj["payload"] = payload
    request_obj["token"] = token
    req = json.dumps(request_obj, ensure_ascii=False).encode("utf-8") + b"\n"

    # 平台隔离的连接方式：Windows 用 TCP localhost，Unix 用 AF_UNIX socket。
    if _is_windows():
        client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        connect_target: Any = (WINDOWS_BROKER_HOST, WINDOWS_BROKER_PORT)
    else:
        sock_path = broker_socket_path(state_root)
        if not sock_path.exists():
            raise BrokerClientError(f"broker socket 不存在: {sock_path}")
        client = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        connect_target = str(sock_path)

    try:
        client.settimeout(timeout_seconds)
        client.connect(connect_target)
        client.sendall(req)
        client.shutdown(socket.SHUT_WR)

        chunks: list[bytes] = []
        while True:
            try:
                part = client.recv(8192)
            except ConnectionResetError:
                if chunks:
                    break
                raise
            if not part:
                break
            chunks.append(part)
    except OSError as exc:
        raise BrokerClientError(f"broker 请求失败: {exc}") from exc
    finally:
        client.close()

    raw = b"".join(chunks).decode("utf-8", errors="replace").strip()
    if not raw:
        raise BrokerClientError("broker 响应为空")
    try:
        obj = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise BrokerClientError(f"broker 响应不是 JSON: {raw}") from exc
    if not isinstance(obj, dict):
        raise BrokerClientError("broker 响应格式无效")
    return obj
