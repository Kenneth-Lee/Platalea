from __future__ import annotations

import json
import socket
from pathlib import Path
from typing import Any


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
    sock_path = broker_socket_path(state_root)
    if not sock_path.exists():
        raise BrokerClientError(f"broker socket 不存在: {sock_path}")

    request_obj: dict[str, Any] = {"op": op}
    if payload:
        request_obj["payload"] = payload
    req = json.dumps(request_obj, ensure_ascii=False).encode("utf-8") + b"\n"

    try:
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
            client.settimeout(timeout_seconds)
            client.connect(str(sock_path))
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
