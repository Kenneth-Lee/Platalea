"""Bulletin board attachment upload helpers for lmserver CLI."""
from __future__ import annotations

import mimetypes
import sys
from pathlib import Path
from typing import Any, Callable

from .board_client import request_api, request_api_bytes


def collect_directory_entries(root: Path) -> list[dict[str, Any]]:
    root = root.resolve()
    if not root.is_dir():
        raise ValueError(f"不是目录: {root}")
    entries: list[dict[str, Any]] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(root).as_posix()
        if not rel:
            continue
        entries.append({"path": rel, "size": path.stat().st_size})
    return entries


def meta_to_attachment_ref(meta: dict[str, Any]) -> dict[str, Any]:
    kind = str(meta.get("kind", "file"))
    ref: dict[str, Any] = {
        "id": str(meta.get("id", "")),
        "kind": kind,
        "name": str(meta.get("name", "")),
    }
    if kind == "directory":
        ref["file_count"] = int(meta.get("file_count", 0))
        ref["total_size"] = int(meta.get("total_size", meta.get("size", 0)))
    else:
        ref["size"] = int(meta.get("size", 0))
    if meta.get("sha256"):
        ref["sha256"] = str(meta["sha256"])
    if meta.get("mime"):
        ref["mime"] = str(meta["mime"])
    return ref


def _fail(status: int, body: dict[str, Any], context: str) -> None:
    message = body.get("message") or body.get("error") or "请求失败"
    raise RuntimeError(f"{context} ({status}): {message}")


def _upload_stream_in_chunks(
    host: str,
    port: int,
    *,
    board_id: str,
    attachment_id: str,
    file_id: str | None,
    chunk_size: int,
    total_size: int,
    open_stream: Callable[[], Any],
    password: str,
    ca_cert: str,
    tls_fingerprint: str,
    label: str,
    on_progress: Callable[[int, int], None] | None = None,
) -> None:
    stream = open_stream()
    if stream is None:
        raise RuntimeError(f"无法读取: {label}")
    uploaded = 0
    chunk_index = 0
    try:
        while uploaded < total_size:
            to_read = min(chunk_size, total_size - uploaded)
            chunk = stream.read(to_read)
            if not chunk:
                break
            if file_id is None:
                path = f"/boards/{board_id}/attachments/{attachment_id}/chunks/{chunk_index}"
            else:
                path = (
                    f"/boards/{board_id}/attachments/{attachment_id}"
                    f"/files/{file_id}/chunks/{chunk_index}"
                )
            status, _payload = request_api_bytes(
                host,
                port,
                "PUT",
                path,
                password=password,
                body=chunk,
                accept="application/json",
                content_type="application/octet-stream",
                ca_cert=ca_cert,
                tls_fingerprint=tls_fingerprint,
                timeout=300,
            )
            if status >= 400:
                raise RuntimeError(f"附件分块上传失败: {label} chunk={chunk_index} status={status}")
            chunk_index += 1
            uploaded += len(chunk)
            if on_progress is not None:
                on_progress(uploaded, total_size)
    finally:
        stream.close()
    if uploaded < total_size:
        raise RuntimeError(f"附件上传不完整: {label} ({uploaded}/{total_size} 字节)")


def _fetch_attachment_meta(
    host: str,
    port: int,
    *,
    board_id: str,
    attachment_id: str,
    password: str,
    ca_cert: str,
    tls_fingerprint: str,
) -> dict[str, Any]:
    status, body = request_api(
        host,
        port,
        "GET",
        f"/boards/{board_id}/attachments/{attachment_id}",
        password=password,
        body=None,
        ca_cert=ca_cert,
        tls_fingerprint=tls_fingerprint,
    )
    if status >= 400 or not isinstance(body, dict):
        _fail(status, body if isinstance(body, dict) else {}, "读取附件元数据失败")
    return body


def upload_file_path(
    host: str,
    port: int,
    *,
    board_id: str,
    path: Path,
    password: str,
    ca_cert: str,
    tls_fingerprint: str,
    uploader_device: str = "pc-cli",
    on_progress: Callable[[str, int, int], None] | None = None,
) -> dict[str, Any]:
    path = path.expanduser().resolve()
    if not path.is_file():
        raise FileNotFoundError(f"文件不存在: {path}")
    size = path.stat().st_size
    mime, _encoding = mimetypes.guess_type(str(path))
    init_body: dict[str, Any] = {
        "kind": "file",
        "name": path.name,
        "size": size,
        "uploader_device": uploader_device,
    }
    if mime:
        init_body["mime"] = mime
    status, init = request_api(
        host,
        port,
        "POST",
        f"/boards/{board_id}/attachments/init",
        password=password,
        body=init_body,
        ca_cert=ca_cert,
        tls_fingerprint=tls_fingerprint,
    )
    if status >= 400 or not init.get("ok"):
        _fail(status, init, f"初始化附件上传失败: {path.name}")
    attachment_id = str(init["attachment_id"])
    chunk_size = int(init.get("chunk_size", 262144))

    def report(uploaded: int, total: int) -> None:
        if on_progress is not None:
            on_progress(path.name, uploaded, total)

    _upload_stream_in_chunks(
        host,
        port,
        board_id=board_id,
        attachment_id=attachment_id,
        file_id=None,
        chunk_size=chunk_size,
        total_size=size,
        open_stream=lambda: path.open("rb"),
        password=password,
        ca_cert=ca_cert,
        tls_fingerprint=tls_fingerprint,
        label=path.name,
        on_progress=report,
    )
    status, complete = request_api(
        host,
        port,
        "POST",
        f"/boards/{board_id}/attachments/{attachment_id}/complete",
        password=password,
        body={},
        ca_cert=ca_cert,
        tls_fingerprint=tls_fingerprint,
    )
    if status >= 400 or not complete.get("ok"):
        _fail(status, complete, f"完成附件上传失败: {path.name}")
    meta = _fetch_attachment_meta(
        host,
        port,
        board_id=board_id,
        attachment_id=attachment_id,
        password=password,
        ca_cert=ca_cert,
        tls_fingerprint=tls_fingerprint,
    )
    return meta_to_attachment_ref(meta)


def upload_directory_path(
    host: str,
    port: int,
    *,
    board_id: str,
    path: Path,
    password: str,
    ca_cert: str,
    tls_fingerprint: str,
    uploader_device: str = "pc-cli",
    on_progress: Callable[[str, int, int], None] | None = None,
) -> dict[str, Any]:
    path = path.expanduser().resolve()
    if not path.is_dir():
        raise NotADirectoryError(f"目录不存在: {path}")
    entries = collect_directory_entries(path)
    if not entries:
        raise ValueError(f"目录为空: {path}")
    status, init = request_api(
        host,
        port,
        "POST",
        f"/boards/{board_id}/attachments/init",
        password=password,
        body={
            "kind": "directory",
            "name": path.name,
            "entries": entries,
            "uploader_device": uploader_device,
        },
        ca_cert=ca_cert,
        tls_fingerprint=tls_fingerprint,
    )
    if status >= 400 or not init.get("ok"):
        _fail(status, init, f"初始化目录附件上传失败: {path.name}")
    attachment_id = str(init["attachment_id"])
    chunk_size = int(init.get("chunk_size", 262144))
    files = init.get("files") or []
    uploaded_in_dir = 0
    total_in_dir = sum(int(item.get("size", 0)) for item in files)

    for slot in files:
        rel_path = str(slot.get("path", ""))
        file_id = str(slot.get("file_id", ""))
        file_size = int(slot.get("size", 0))
        local_file = path / rel_path
        if not local_file.is_file():
            raise FileNotFoundError(f"目录附件缺少文件: {local_file}")
        file_offset = uploaded_in_dir

        def report(uploaded: int, total: int, *, _name=rel_path, _offset=file_offset) -> None:
            if on_progress is not None:
                on_progress(_name, _offset + uploaded, total_in_dir)

        _upload_stream_in_chunks(
            host,
            port,
            board_id=board_id,
            attachment_id=attachment_id,
            file_id=file_id,
            chunk_size=chunk_size,
            total_size=file_size,
            open_stream=lambda lf=local_file: lf.open("rb"),
            password=password,
            ca_cert=ca_cert,
            tls_fingerprint=tls_fingerprint,
            label=str(local_file),
            on_progress=report,
        )
        uploaded_in_dir += file_size

    status, complete = request_api(
        host,
        port,
        "POST",
        f"/boards/{board_id}/attachments/{attachment_id}/complete",
        password=password,
        body={},
        ca_cert=ca_cert,
        tls_fingerprint=tls_fingerprint,
    )
    if status >= 400 or not complete.get("ok"):
        _fail(status, complete, f"完成目录附件上传失败: {path.name}")
    meta = _fetch_attachment_meta(
        host,
        port,
        board_id=board_id,
        attachment_id=attachment_id,
        password=password,
        ca_cert=ca_cert,
        tls_fingerprint=tls_fingerprint,
    )
    return meta_to_attachment_ref(meta)


def upload_paths(
    host: str,
    port: int,
    *,
    board_id: str,
    paths: list[Path],
    password: str,
    ca_cert: str,
    tls_fingerprint: str,
    uploader_device: str = "pc-cli",
    verbose: bool = True,
) -> list[dict[str, Any]]:
    refs: list[dict[str, Any]] = []

    def on_progress(name: str, uploaded: int, total: int) -> None:
        if not verbose:
            return
        pct = (uploaded * 100 // total) if total > 0 else 100
        print(f"\r上传 {name}: {pct}% ({uploaded}/{total})", end="", file=sys.stderr, flush=True)

    for raw in paths:
        path = raw.expanduser().resolve()
        if path.is_dir():
            if verbose:
                print(f"上传目录: {path}", file=sys.stderr)
            ref = upload_directory_path(
                host,
                port,
                board_id=board_id,
                path=path,
                password=password,
                ca_cert=ca_cert,
                tls_fingerprint=tls_fingerprint,
                uploader_device=uploader_device,
                on_progress=on_progress,
            )
        elif path.is_file():
            if verbose:
                print(f"上传文件: {path}", file=sys.stderr)
            ref = upload_file_path(
                host,
                port,
                board_id=board_id,
                path=path,
                password=password,
                ca_cert=ca_cert,
                tls_fingerprint=tls_fingerprint,
                uploader_device=uploader_device,
                on_progress=on_progress,
            )
        else:
            raise FileNotFoundError(f"路径不存在: {path}")
        if verbose:
            print(file=sys.stderr)
        refs.append(ref)
    return refs
