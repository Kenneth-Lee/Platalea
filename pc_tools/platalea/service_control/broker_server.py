from __future__ import annotations

import argparse
import json
import os
import platform
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

from .broker_client import WINDOWS_BROKER_HOST, WINDOWS_BROKER_PORT
from .state import load_control_state


def _socket_path(state_dir: Path) -> Path:
    return state_dir / "broker.sock"


def _audit_log_path(state_dir: Path) -> Path:
    return state_dir / "audit.log"


def _load_owner_uid(state_dir: Path) -> int:
    state = load_control_state(state_dir / "state.json")
    if state is None:
        return -1
    return int(state.active_owner.uid)


def _load_broker_token(state_dir: Path) -> str:
    state = load_control_state(state_dir / "state.json")
    if state is None:
        return ""
    return state.broker_token


def _peer_uid(conn: socket.socket) -> int:
    if hasattr(socket, "SO_PEERCRED"):
        try:
            raw = conn.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, struct.calcsize("3i"))
            _pid, uid, _gid = struct.unpack("3i", raw)
            return int(uid)
        except OSError:
            pass
    if hasattr(socket, "getpeereid"):
        try:
            uid, _gid = socket.getpeereid(conn)  # type: ignore[attr-defined]
            return int(uid)
        except OSError:
            pass
    return -1


def _write_audit(state_dir: Path, item: dict[str, Any]) -> None:
    path = _audit_log_path(state_dir)
    path.parent.mkdir(parents=True, exist_ok=True)
    line = json.dumps(item, ensure_ascii=False)
    with path.open("a", encoding="utf-8") as fh:
        fh.write(line + "\n")


def _response(conn: socket.socket, body: dict[str, Any]) -> None:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    conn.sendall(data)


def _is_windows() -> bool:
    return platform.system().lower() == "windows"


def _handle_shutdown(*, dry_run: bool) -> tuple[bool, str]:
    if dry_run:
        return True, "dry-run: skip shutdown"
    if _is_windows():
        # Windows：shutdown.exe（LocalSystem 服务拥有 SeShutdownPrivilege）。
        # /s 关机 /t 0 立即 /f 强制关闭应用。
        cmd = ["shutdown", "/s", "/t", "0", "/f"]
    else:
        cmd = ["/sbin/shutdown", "-h", "now"]
    proc = subprocess.run(cmd, check=False, capture_output=True, text=True)
    if proc.returncode == 0:
        return True, "shutdown command accepted"
    detail = (proc.stderr or proc.stdout or "").strip()
    if not detail:
        detail = f"exit {proc.returncode}"
    return False, detail


def _serve_connection(
    conn: socket.socket,
    *,
    state_dir: Path,
    dry_run: bool,
) -> None:
    owner_uid = _load_owner_uid(state_dir)
    expected_token = _load_broker_token(state_dir)

    raw = conn.recv(65536).decode("utf-8", errors="replace").strip()
    if not raw:
        _response(conn, {"ok": False, "error": "bad_request", "message": "empty request"})
        return
    try:
        request = json.loads(raw)
    except json.JSONDecodeError:
        _response(conn, {"ok": False, "error": "bad_request", "message": "invalid json"})
        return
    if not isinstance(request, dict):
        _response(conn, {"ok": False, "error": "bad_request", "message": "request must be object"})
        return

    supplied_token = str(request.get("token", "")).strip()
    uid = _peer_uid(conn)
    if expected_token and supplied_token != expected_token:
        _response(conn, {"ok": False, "error": "forbidden", "message": "token 无效"})
        _write_audit(
            state_dir,
            {
                "ts": int(time.time()),
                "uid": uid,
                "action": "reject",
                "reason": "bad_token",
            },
        )
        return

    op = str(request.get("op", "")).strip().lower()
    if op == "status":
        _response(
            conn,
            {
                "ok": True,
                "op": "status",
                "owner_uid": owner_uid,
                "pid": os.getpid(),
            },
        )
        return

    if op == "shutdown":
        ok, message = _handle_shutdown(dry_run=dry_run)
        _write_audit(
            state_dir,
            {
                "ts": int(time.time()),
                "uid": uid,
                "action": "shutdown",
                "ok": ok,
                "message": message,
            },
        )
        if ok:
            _response(conn, {"ok": True, "op": "shutdown", "message": message})
        else:
            _response(conn, {"ok": False, "error": "shutdown_failed", "message": message})
        return

    _response(conn, {"ok": False, "error": "unsupported_op", "message": f"unsupported op: {op}"})


def _create_listen_socket(state_dir: Path) -> tuple[socket.socket, Path | None]:
    """创建监听 socket（平台隔离）。

    Windows：TCP localhost（AF_UNIX 在 Windows Python 不可用），认证依赖 broker_token。
    Unix：AF_UNIX domain socket + 文件权限/属主，认证依赖 peer uid + 文件权限。
    返回 (server_socket, socket_path_or_None)。
    """
    if _is_windows():
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((WINDOWS_BROKER_HOST, WINDOWS_BROKER_PORT))
        server.listen(16)
        return server, None
    sock_path = _socket_path(state_dir)
    if sock_path.exists():
        sock_path.unlink()
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(str(sock_path))
    os.chmod(sock_path, 0o660)
    owner_uid = _load_owner_uid(state_dir)
    if owner_uid >= 0:
        try:
            # owner user can connect; root keeps ownership.
            import pwd

            pw = pwd.getpwuid(owner_uid)
            os.chown(sock_path, 0, pw.pw_gid)
        except Exception:
            pass
    server.listen(16)
    return server, sock_path


def run_broker(*, state_dir: Path, dry_run: bool) -> int:
    state_dir.mkdir(parents=True, exist_ok=True)
    server, _sock_path = _create_listen_socket(state_dir)
    try:
        while True:
            try:
                conn, _ = server.accept()
            except KeyboardInterrupt:
                break
            try:
                with conn:
                    _serve_connection(conn, state_dir=state_dir, dry_run=dry_run)
            except Exception as exc:
                try:
                    _response(conn, {"ok": False, "error": "internal_error", "message": str(exc)})
                except Exception:
                    pass
    finally:
        try:
            server.close()
        except Exception:
            pass
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="python -m platalea.service_control.broker_server",
        description="platalea 特权 broker（Unix socket）",
    )
    ap.add_argument("--state-dir", default="", help="控制面状态目录")
    ap.add_argument(
        "--dry-run",
        action="store_true",
        help="测试模式：收到 shutdown 请求时不实际关机",
    )
    args = ap.parse_args(argv)
    state_dir = Path(args.state_dir).expanduser().resolve() if args.state_dir else Path.cwd()
    try:
        return run_broker(state_dir=state_dir, dry_run=bool(args.dry_run))
    except Exception as exc:
        print(f"broker 异常退出: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())