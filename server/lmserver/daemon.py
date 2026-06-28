"""Background server process management."""
from __future__ import annotations

import http.client
import json
import os
import signal
import ssl
import subprocess
import sys
import time
from pathlib import Path

from .paths import default_ca_cert, log_file, pid_file


def load_server_probe(
    config_path: Path,
    *,
    guest_password: str = "",
) -> tuple[str, int, Path, str]:
    """Return (probe_host, port, ca_cert, password) for health checks."""
    config_path = config_path.resolve()
    raw = json.loads(config_path.read_text(encoding="utf-8"))
    host = str(raw.get("listen_host", "127.0.0.1")).strip() or "127.0.0.1"
    probe_host = "127.0.0.1" if host in {"0.0.0.0", "::"} else host
    port = int(raw.get("port", 8765))
    ca_cert = default_ca_cert(config_path)
    password = guest_password or str(raw.get("guest_password", ""))
    return probe_host, port, ca_cert, password


def already_running_message(config_path: Path, *, guest_password: str = "") -> str | None:
    """If a server is already up, return a user-facing message; otherwise None."""
    probe_host, port, ca_cert, password = load_server_probe(
        config_path,
        guest_password=guest_password,
    )
    responding = is_server_responding(probe_host, port, ca_cert=ca_cert, password=password)
    pid = _read_pid()
    pid_alive = pid is not None and is_process_alive(pid)
    if pid_alive:
        if responding:
            return f"Server already running (pid {pid}, port {port})."
        return f"Server already running (pid {pid}, port {port}, starting up)."
    if responding:
        return f"Server already running (port {port})."
    return None


def _read_pid() -> int | None:
    path = pid_file()
    if not path.exists():
        return None
    try:
        pid = int(path.read_text(encoding="utf-8").strip())
    except (OSError, ValueError):
        return None
    return pid if pid > 0 else None


def _write_pid(pid: int) -> None:
    path = pid_file()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(str(pid), encoding="utf-8")


def _clear_pid() -> None:
    path = pid_file()
    if path.exists():
        try:
            path.unlink()
        except OSError:
            pass


def is_process_alive(pid: int) -> bool:
    if pid <= 0:
        return False
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    else:
        return True


def is_server_responding(
    host: str,
    port: int,
    *,
    ca_cert: Path,
    password: str = "",
    timeout: float = 2.0,
) -> bool:
    if not ca_cert.exists():
        return False
    ssl_context = ssl.create_default_context(cafile=str(ca_cert))
    ssl_context.check_hostname = False
    try:
        connection = http.client.HTTPSConnection(host, port, context=ssl_context, timeout=timeout)
        connection.connect()
        headers = {"Accept": "application/json"}
        if password:
            from .family_common import PASSWORD_HEADER

            headers[PASSWORD_HEADER] = password
        connection.request("GET", "/boards", headers=headers)
        response = connection.getresponse()
        response.read()
        return response.status < 500
    except Exception:
        return False
    finally:
        try:
            connection.close()
        except Exception:
            pass


def server_status(host: str, port: int, *, ca_cert: Path, password: str = "") -> str:
    pid = _read_pid()
    if pid is not None and is_process_alive(pid):
        if is_server_responding(host, port, ca_cert=ca_cert, password=password):
            return f"running (pid {pid}, port {port})"
        return f"starting or unhealthy (pid {pid}, port {port})"
    if is_server_responding(host, port, ca_cert=ca_cert, password=password):
        return f"running (port {port}, no pid file)"
    _clear_pid()
    return "stopped"


def stop_server() -> int:
    pid = _read_pid()
    if pid is None:
        print("Server is not running (no pid file).")
        return 0
    if not is_process_alive(pid):
        _clear_pid()
        print("Server is not running (stale pid file removed).")
        return 0
    try:
        os.kill(pid, signal.SIGTERM)
    except ProcessLookupError:
        _clear_pid()
        print("Server is not running (stale pid file removed).")
        return 0
    for _ in range(30):
        if not is_process_alive(pid):
            _clear_pid()
            print(f"Server stopped (pid {pid}).")
            return 0
        time.sleep(0.2)
    print(f"Server did not stop within timeout (pid {pid}).", file=sys.stderr)
    return 1


def start_background(config_path: Path) -> int:
    message = already_running_message(config_path)
    if message:
        print(message)
        return 0

    config_path = config_path.resolve()
    log_path = log_file()
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_fd = open(log_path, "a", encoding="utf-8")

    cmd = [
        sys.executable,
        "-m",
        "lmserver",
        "_serve-daemon",
        "--config",
        str(config_path),
    ]
    creationflags = 0
    if sys.platform == "win32":
        creationflags = subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS  # type: ignore[attr-defined]

    proc = subprocess.Popen(
        cmd,
        stdin=subprocess.DEVNULL,
        stdout=log_fd,
        stderr=subprocess.STDOUT,
        start_new_session=sys.platform != "win32",
        creationflags=creationflags,
        close_fds=True,
    )
    log_fd.close()
    _write_pid(proc.pid)
    print(f"Server started in background (pid {proc.pid}). Log: {log_path}")
    return 0


def wait_until_ready(
    host: str,
    port: int,
    *,
    ca_cert: Path,
    password: str = "",
    timeout: float = 30.0,
) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if is_server_responding(host, port, ca_cert=ca_cert, password=password):
            return True
        time.sleep(0.25)
    return False


def ensure_server_running(config_path: Path, *, guest_password: str = "") -> None:
    config_path = config_path.resolve()
    probe_host, port, ca_cert, password = load_server_probe(
        config_path,
        guest_password=guest_password,
    )

    if is_server_responding(probe_host, port, ca_cert=ca_cert, password=password):
        return

    message = already_running_message(config_path, guest_password=guest_password)
    if message and "starting up" in message:
        if wait_until_ready(probe_host, port, ca_cert=ca_cert, password=password):
            return
        raise RuntimeError(
            f"Server did not become ready on {probe_host}:{port} within 30s. "
            f"Check {log_file()} for details."
        )

    print("Local server is not running; starting it in the background...", file=sys.stderr)
    try:
        from .tls_setup import ensure_tls_materials

        ensure_tls_materials(config_path)
    except Exception as exc:
        raise RuntimeError(f"TLS setup failed: {exc}") from exc
    start_background(config_path)
    if not wait_until_ready(probe_host, port, ca_cert=ca_cert, password=password):
        raise RuntimeError(
            f"Server did not become ready on {probe_host}:{port} within 30s. "
            f"Check {log_file()} for details."
        )
