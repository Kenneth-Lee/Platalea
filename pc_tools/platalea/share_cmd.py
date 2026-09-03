from __future__ import annotations

import argparse
import ftplib
import os
import shlex
import sys
import time
from pathlib import Path

from prompt_toolkit import PromptSession
from prompt_toolkit.history import InMemoryHistory

CSI_CLEAR_LINE = "\r\x1b[2K"


def _build_share_cmd_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="platalea share-cmd",
        description="FTP-like local share command client",
    )
    parser.add_argument("action", choices=["ls", "get", "put", "rm", "mkdir", "pwd"], help="operation")
    parser.add_argument("host", help="target host")
    parser.add_argument("path1", nargs="?", default="", help="remote path or local path")
    parser.add_argument("path2", nargs="?", default="", help="secondary path for get/put")
    parser.add_argument("--port", type=int, default=2121, help="target FTP port")
    parser.add_argument("--user", default="lm", help="username")
    parser.add_argument("--password", default="", help="password")
    parser.add_argument("--timeout", type=float, default=10.0, help="socket timeout in seconds")
    parser.add_argument("--active", action="store_true", help="use active mode instead of passive mode")
    parser.add_argument("--json", action="store_true", help="json output where applicable")
    return parser


def _connect(host: str, port: int, user: str, password: str, timeout: float, passive: bool) -> ftplib.FTP:
    ftp = ftplib.FTP()
    ftp.connect(host=host, port=port, timeout=timeout)
    ftp.login(user=user, passwd=password)
    ftp.set_pasv(passive)
    return ftp


def _print_json_line(payload: dict[str, object]) -> None:
    import json

    print(json.dumps(payload, ensure_ascii=False))


def _prompt_overwrite(target: str) -> bool:
    while True:
        ans = input(f"target exists, overwrite? {target} [y/N]: ").strip().lower()
        if ans in {"y", "yes"}:
            return True
        if ans in {"", "n", "no"}:
            return False


def _progress_writer(label: str, total: int | None):
    state = {
        "written": 0,
        "last_ts": 0.0,
    }

    def _write(chunk: bytes, out_fh) -> None:
        out_fh.write(chunk)
        state["written"] += len(chunk)
        now = time.time()
        if now - state["last_ts"] >= 0.2:
            if total and total > 0:
                pct = min(100.0, state["written"] * 100.0 / total)
                print(f"{CSI_CLEAR_LINE}{label}: {pct:6.2f}% ({state['written']}/{total} bytes)", end="", flush=True)
            else:
                print(f"{CSI_CLEAR_LINE}{label}: {state['written']} bytes", end="", flush=True)
            state["last_ts"] = now

    def _finish() -> None:
        if total and total > 0:
            pct = min(100.0, state["written"] * 100.0 / total)
            print(f"{CSI_CLEAR_LINE}{label}: {pct:6.2f}% ({state['written']}/{total} bytes)")
        else:
            print(f"{CSI_CLEAR_LINE}{label}: {state['written']} bytes")

    return _write, _finish


def _progress_reader(label: str, total: int, sink_callback):
    state = {
        "written": 0,
        "last_ts": 0.0,
    }

    def _read(chunk: bytes) -> None:
        sink_callback(chunk)
        state["written"] += len(chunk)
        now = time.time()
        if now - state["last_ts"] >= 0.2:
            pct = min(100.0, state["written"] * 100.0 / total) if total > 0 else 0.0
            if total > 0:
                print(f"{CSI_CLEAR_LINE}{label}: {pct:6.2f}% ({state['written']}/{total} bytes)", end="", flush=True)
            else:
                print(f"{CSI_CLEAR_LINE}{label}: {state['written']} bytes", end="", flush=True)
            state["last_ts"] = now

    def _finish() -> None:
        if total > 0:
            pct = min(100.0, state["written"] * 100.0 / total)
            print(f"{CSI_CLEAR_LINE}{label}: {pct:6.2f}% ({state['written']}/{total} bytes)")
        else:
            print(f"{CSI_CLEAR_LINE}{label}: {state['written']} bytes")

    return _read, _finish


def _remote_exists(ftp: ftplib.FTP, remote_path: str) -> bool:
    parent, name = _split_remote_parent_name(remote_path)
    try:
        entries = ftp.nlst(parent or ".")
    except Exception:
        return False
    for entry in entries:
        candidate = entry.rstrip("/")
        if candidate == remote_path.rstrip("/"):
            return True
        if os.path.basename(candidate) == name:
            return True
    return False


def _split_remote_parent_name(remote_path: str) -> tuple[str, str]:
    p = remote_path.strip()
    if not p:
        return ".", ""
    if "/" not in p:
        return ".", p
    parent = p.rsplit("/", 1)[0] or "/"
    name = p.rsplit("/", 1)[1]
    return parent, name


def _join_remote(base_dir: str, child: str) -> str:
    base = base_dir.rstrip("/")
    child_clean = child.lstrip("/")
    if not base or base == "/":
        return f"/{child_clean}" if child_clean else "/"
    return f"{base}/{child_clean}" if child_clean else base


def _normalize_remote_path(path: str, cwd: str) -> tuple[str, bool]:
    raw = (path or "").strip()
    if not raw:
        raw = "."
    if raw.startswith("/"):
        full = raw
    else:
        full = _join_remote(cwd, raw)
    parts: list[str] = []
    escaped_root = False
    for part in full.split("/"):
        if part == "" or part == ".":
            continue
        if part == "..":
            if parts:
                parts.pop()
            else:
                escaped_root = True
            continue
        parts.append(part)
    return "/" + "/".join(parts), escaped_root


def _ensure_remote_dirs(ftp: ftplib.FTP, remote_path: str) -> None:
    parent, _name = _split_remote_parent_name(remote_path)
    if parent in {"", "."}:
        return
    absolute = parent.startswith("/")
    cwd = ftp.pwd()
    try:
        if absolute:
            ftp.cwd("/")
        parts = [part for part in parent.split("/") if part and part != "."]
        for part in parts:
            try:
                ftp.cwd(part)
            except Exception:
                ftp.mkd(part)
                ftp.cwd(part)
    finally:
        ftp.cwd(cwd)


def _list_dir(ftp: ftplib.FTP, target: str) -> list[str]:
    return ftp.nlst(target)


def _stream_list_dir(ftp: ftplib.FTP, target: str) -> int:
    count = 0

    def _print_line(line: str) -> None:
        nonlocal count
        print(line, flush=True)
        count += 1

    ftp.retrlines(f"LIST {target}", _print_line)
    return count


def _remote_size(ftp: ftplib.FTP, remote_path: str) -> int | None:
    try:
        size = ftp.size(remote_path)
        return int(size) if size is not None else None
    except Exception:
        return None


def _is_probably_dir(ftp: ftplib.FTP, remote_path: str) -> bool:
    cwd = ftp.pwd()
    try:
        ftp.cwd(remote_path)
        return True
    except Exception:
        return False
    finally:
        try:
            ftp.cwd(cwd)
        except Exception:
            pass


def _safe_local_rel_path(path: Path, root: Path) -> str:
    rel = path.relative_to(root)
    return str(rel).replace("\\", "/")


def _resolve_local_path(local_cwd: Path, raw: str) -> Path:
    candidate = Path(raw).expanduser()
    if candidate.is_absolute():
        return candidate.resolve()
    return (local_cwd / candidate).resolve()


def _stream_list_local(target: Path) -> int:
    if not target.exists():
        raise FileNotFoundError(f"local path not found: {target}")
    if target.is_file():
        print(target.name, flush=True)
        return 1
    count = 0
    for child in sorted(target.iterdir(), key=lambda p: p.name.lower()):
        suffix = "/" if child.is_dir() else ""
        print(f"{child.name}{suffix}", flush=True)
        count += 1
    return count


def _put_single_file(ftp: ftplib.FTP, local_file: Path, remote_path: str) -> None:
    if _remote_exists(ftp, remote_path) and not _prompt_overwrite(remote_path):
        print("cancelled")
        return
    _ensure_remote_dirs(ftp, remote_path)
    total = local_file.stat().st_size
    with local_file.open("rb") as fh:
        reader, finish = _progress_reader("put", total, lambda data: data)

        def _cb(chunk: bytes) -> None:
            reader(chunk)

        ftp.storbinary(f"STOR {remote_path}", fh, callback=_cb)
        finish()
    print(f"put ok: {local_file} -> {remote_path}")


def _put_directory_recursive(ftp: ftplib.FTP, local_dir: Path, remote_base: str) -> None:
    dir_paths = sorted([p for p in local_dir.rglob("*") if p.is_dir()])
    for d in dir_paths:
        rel = _safe_local_rel_path(d, local_dir)
        remote_dir = _join_remote(remote_base, rel)
        _ensure_remote_dirs(ftp, _join_remote(remote_dir, "dummy.tmp"))
    file_paths = sorted([p for p in local_dir.rglob("*") if p.is_file()])
    for f in file_paths:
        rel = _safe_local_rel_path(f, local_dir)
        remote_file = _join_remote(remote_base, rel)
        _put_single_file(ftp, f, remote_file)


def _get_single_file(ftp: ftplib.FTP, remote_path: str, local_file: Path) -> None:
    if local_file.exists() and not _prompt_overwrite(str(local_file)):
        print("cancelled")
        return
    local_file.parent.mkdir(parents=True, exist_ok=True)
    total = _remote_size(ftp, remote_path)
    with local_file.open("wb") as fh:
        writer, finish = _progress_writer("get", total)

        def _sink(chunk: bytes) -> None:
            writer(chunk, fh)

        ftp.retrbinary(f"RETR {remote_path}", _sink)
        finish()
    print(f"get ok: {remote_path} -> {local_file}")


def _list_remote_children(ftp: ftplib.FTP, remote_dir: str) -> list[str]:
    cwd = ftp.pwd()
    try:
        ftp.cwd(remote_dir)
        names = ftp.nlst()
        result: list[str] = []
        for n in names:
            base = n.rstrip("/")
            if "/" in base:
                base = base.rsplit("/", 1)[1]
            if base in {"", ".", ".."}:
                continue
            result.append(base)
        return sorted(set(result))
    finally:
        ftp.cwd(cwd)


def _get_directory_recursive(ftp: ftplib.FTP, remote_dir: str, local_base: Path) -> None:
    local_base.mkdir(parents=True, exist_ok=True)
    for child in _list_remote_children(ftp, remote_dir):
        remote_child = _join_remote(remote_dir, child)
        local_child = local_base / child
        if _is_probably_dir(ftp, remote_child):
            _get_directory_recursive(ftp, remote_child, local_child)
        else:
            _get_single_file(ftp, remote_child, local_child)


def run_share_cmd(argv: list[str] | None = None) -> int:
    parser = _build_share_cmd_parser()
    args = parser.parse_args(argv)

    try:
        ftp = _connect(
            host=args.host,
            port=args.port,
            user=args.user,
            password=args.password,
            timeout=args.timeout,
            passive=not args.active,
        )
    except Exception as exc:
        print(f"share-cmd connect failed: {exc}", file=sys.stderr)
        return 1

    try:
        action = args.action
        p1 = args.path1.strip()
        p2 = args.path2.strip()

        if action == "ls":
            target = p1 or "."
            if args.json:
                entries = _list_dir(ftp, target)
                _print_json_line({"ok": True, "action": "ls", "host": args.host, "path": target, "entries": entries})
            else:
                _stream_list_dir(ftp, target)
            return 0

        if action == "pwd":
            cwd = ftp.pwd()
            if args.json:
                _print_json_line({"ok": True, "action": "pwd", "cwd": cwd})
            else:
                print(cwd)
            return 0

        if action == "mkdir":
            if not p1:
                parser.error("mkdir requires remote path")
            created = ftp.mkd(p1)
            if args.json:
                _print_json_line({"ok": True, "action": "mkdir", "path": p1, "result": created})
            else:
                print(f"mkdir ok: {created}")
            return 0

        if action == "rm":
            if not p1:
                parser.error("rm requires remote path")
            ftp.delete(p1)
            if args.json:
                _print_json_line({"ok": True, "action": "rm", "path": p1})
            else:
                print(f"rm ok: {p1}")
            return 0

        if action == "get":
            if not p1:
                parser.error("get requires remote path")
            local_path = p2 or os.path.basename(p1) or "download.bin"
            local_file = Path(local_path).expanduser()
            if _is_probably_dir(ftp, p1):
                _get_directory_recursive(ftp, p1, local_file)
            else:
                _get_single_file(ftp, p1, local_file)
            if args.json:
                _print_json_line(
                    {
                        "ok": True,
                        "action": "get",
                        "remote": p1,
                        "local": str(local_file),
                        "size": local_file.stat().st_size,
                    }
                )
            else:
                print(f"get ok: {p1} -> {local_file}")
            return 0

        if action == "put":
            if not p1:
                parser.error("put requires local file path")
            local_file = Path(p1).expanduser()
            if not local_file.exists() or not local_file.is_file():
                if local_file.is_dir():
                    remote_base = p2 or local_file.name
                    _put_directory_recursive(ftp, local_file, remote_base)
                    return 0
                print(f"local file not found: {local_file}", file=sys.stderr)
                return 2
            remote_path = p2 or local_file.name
            if _is_probably_dir(ftp, remote_path):
                remote_path = remote_path.rstrip("/") + "/" + local_file.name
            _put_single_file(ftp, local_file, remote_path)
            if args.json:
                _print_json_line(
                    {
                        "ok": True,
                        "action": "put",
                        "local": str(local_file),
                        "remote": remote_path,
                        "size": local_file.stat().st_size,
                    }
                )
            else:
                print(f"put ok: {local_file} -> {remote_path}")
            return 0

        parser.error(f"unsupported action: {action}")
        return 2
    except Exception as exc:
        print(f"share-cmd failed: {exc}", file=sys.stderr)
        return 1
    finally:
        try:
            ftp.quit()
        except Exception:
            pass


def _print_client_help() -> None:
    print("commands: ls [path], cd <path>, pwd, get <remote> [local], put <local> [remote], rm <remote>, mkdir <remote>")
    print("local commands: lls [path], lcd [path], lpwd")
    print("other: help, quit")


def _run_client_command(ftp: ftplib.FTP, line: str, client_state: dict[str, object]) -> bool:
    parts = shlex.split(line)
    if not parts:
        return True
    cmd = parts[0].lower()
    args = parts[1:]
    local_cwd = client_state["local_cwd"]
    if not isinstance(local_cwd, Path):
        local_cwd = Path.cwd().resolve()
        client_state["local_cwd"] = local_cwd

    if cmd in {"quit", "exit"}:
        return False

    if cmd == "help":
        _print_client_help()
        return True

    if cmd == "lpwd":
        print(str(local_cwd))
        return True

    if cmd == "lcd":
        if not args:
            print(str(local_cwd))
            return True
        target = _resolve_local_path(local_cwd, args[0])
        if not target.exists() or not target.is_dir():
            print(f"lcd failed: not a directory: {target}", file=sys.stderr)
            return True
        client_state["local_cwd"] = target
        print(str(target))
        return True

    if cmd == "lls":
        target = _resolve_local_path(local_cwd, args[0]) if args else local_cwd
        try:
            _stream_list_local(target)
        except Exception as exc:
            print(f"lls failed: {exc}", file=sys.stderr)
        return True

    if cmd == "ls":
        target = args[0] if args else "."
        _stream_list_dir(ftp, target)
        return True

    if cmd == "pwd":
        print(ftp.pwd())
        return True

    if cmd == "cd":
        target, escaped_root = _normalize_remote_path(args[0] if args else "/", ftp.pwd())
        if escaped_root:
            print("cd failed: already at root", file=sys.stderr)
            return True
        ftp.cwd(target)
        print(ftp.pwd())
        return True

    if cmd == "mkdir":
        if not args:
            print("mkdir requires remote path", file=sys.stderr)
            return True
        print(f"mkdir ok: {ftp.mkd(args[0])}")
        return True

    if cmd == "rm":
        if not args:
            print("rm requires remote path", file=sys.stderr)
            return True
        ftp.delete(args[0])
        print(f"rm ok: {args[0]}")
        return True

    if cmd == "get":
        if not args:
            print("get requires remote path", file=sys.stderr)
            return True
        remote = args[0]
        local = args[1] if len(args) > 1 else (os.path.basename(remote) or "download.bin")
        local_path = _resolve_local_path(local_cwd, local)
        if local_path.exists() and local_path.is_dir() and not _is_probably_dir(ftp, remote):
            local_path = local_path / (os.path.basename(remote.rstrip("/")) or "download.bin")
        if _is_probably_dir(ftp, remote):
            _get_directory_recursive(ftp, remote, local_path)
        else:
            _get_single_file(ftp, remote, local_path)
        return True

    if cmd == "put":
        if not args:
            print("put requires local path", file=sys.stderr)
            return True
        local = _resolve_local_path(local_cwd, args[0])
        if not local.exists():
            print(f"local file not found: {local}", file=sys.stderr)
            return True
        remote = args[1] if len(args) > 1 else local.name
        if local.is_dir():
            _put_directory_recursive(ftp, local, remote)
            return True
        if _is_probably_dir(ftp, remote):
            remote = remote.rstrip("/") + "/" + local.name
        _put_single_file(ftp, local, remote)
        return True

    print(f"unknown command: {cmd}", file=sys.stderr)
    _print_client_help()
    return True


def run_share_client(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="platalea share-client",
        description="Interactive FTP-like local share client",
    )
    parser.add_argument("host", help="target host")
    parser.add_argument("--port", type=int, default=2121, help="target FTP port")
    parser.add_argument("--user", default="lm", help="username")
    parser.add_argument("--password", default="", help="password")
    parser.add_argument("--timeout", type=float, default=10.0, help="socket timeout in seconds")
    parser.add_argument("--active", action="store_true", help="use active mode instead of passive mode")
    args = parser.parse_args(argv)

    try:
        ftp = _connect(
            host=args.host,
            port=args.port,
            user=args.user,
            password=args.password,
            timeout=args.timeout,
            passive=not args.active,
        )
    except Exception as exc:
        print(f"share-client connect failed: {exc}", file=sys.stderr)
        return 1

    print(f"connected to {args.host}:{args.port} as {args.user}")
    _print_client_help()
    session = PromptSession(history=InMemoryHistory())
    client_state: dict[str, object] = {
        "local_cwd": Path.cwd().resolve(),
    }
    try:
        while True:
            try:
                line = session.prompt("share> ").strip()
            except EOFError:
                break
            except KeyboardInterrupt:
                print()
                continue
            try:
                keep_going = _run_client_command(ftp, line, client_state)
            except Exception as exc:
                print(f"command failed: {exc}", file=sys.stderr)
                continue
            if not keep_going:
                break
        return 0
    finally:
        try:
            ftp.quit()
        except Exception:
            pass
