#!/usr/bin/env python3
"""LocalManager 留言板 API 命令行客户端（本地调试 PC 服务或远程设备）。"""
from __future__ import annotations

import argparse
import hashlib
import http.client
import json
import ssl
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .family_common import PASSWORD_HEADER
from .paths import config_path as default_config_path, default_ca_cert, app_dir

DEFAULT_CA = str(default_ca_cert())
DEFAULT_CONFIG = default_config_path()

BOARD_COMMANDS = frozenset({
    "list-boards",
    "get-agent",
    "get-messages",
    "post",
    "post-attachment",
    "upload-attachment",
    "create-board",
    "delete-board",
    "put",
    "delete",
    "export-boardpack",
    "import-boardpack",
})


def build_parser(*, prog: str = "lmserver") -> argparse.ArgumentParser:
    home = app_dir()
    parser = argparse.ArgumentParser(
        prog=prog,
        description="LocalManager 留言板 HTTPS API 客户端。",
        epilog=(
            "示例（本机，默认读取 ~/.localmanager/config.json）：\n"
            "  lmserver list-boards\n"
            "  lmserver get-messages default\n"
            "  lmserver post default \"@qwen2.5 你好\"\n"
            "  lmserver post default \"说明\" --attach ./report.pdf\n"
            "  lmserver upload-attachment default ./photo.jpg\n"
            "  lmserver post-attachment default ./notes.md ./data.zip --content \"资料\"\n"
            "  lmserver create-board \"厨房留言\"\n"
            "  lmserver delete-board kitchen\n"
            "  lmserver get-agent\n"
            "\n"
            "连接局域网内其它设备（不会自动启动本机服务）：\n"
            "  lmserver --host 192.168.1.10 --password guest list-boards\n"
            "\n"
            f"配置与 TLS 默认目录: {home}\n"
            "连接 127.0.0.1 时若服务未运行，lmserver 会自动后台启动本机服务。"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--host",
        default="127.0.0.1",
        help="目标主机，默认 127.0.0.1（本机 PC 服务）",
    )
    parser.add_argument("--port", type=int, default=None, help="HTTPS 端口，默认 8765")
    parser.add_argument(
        "--password",
        default="",
        help="接入密码（guest 或 host）；未指定时从 --config 读取 guest_password",
    )
    parser.add_argument(
        "--host-password",
        default="",
        help="宿主密码；create/delete 等操作优先使用此密码",
    )
    parser.add_argument(
        "--config",
        default=str(DEFAULT_CONFIG),
        help=f"读取配置中的 port 与密码（默认 {DEFAULT_CONFIG}）",
    )
    parser.add_argument("--ca-cert", default=str(DEFAULT_CA), help="TLS CA 证书路径")
    parser.add_argument(
        "--tls-fingerprint",
        default="",
        help="服务端 TLS SHA-256 指纹；不传则仅校验 CA",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="输出原始 JSON（默认为人可读格式）",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("list-boards", help="列出所有留言板")
    sub.add_parser("get-agent", help="查看 AI Agent 配置（可用模型列表）")

    get_messages = sub.add_parser("get-messages", help="读取指定留言板的消息")
    get_messages.add_argument("board_id", help="留言板 ID（先用 list-boards 查看）")

    create = sub.add_parser("create-board", help="创建留言板（需 host 密码）")
    create.add_argument("name", help="留言板名称")
    create.add_argument(
        "--role-ids",
        default="guest",
        help="授权可见的非 admin 角色，逗号分隔（默认 guest；传空串表示仅 admin 可见）",
    )

    delete_board = sub.add_parser("delete-board", help="删除留言板（需 host 密码）")
    delete_board.add_argument("board_id", help="留言板 ID")

    post = sub.add_parser("post", help="在指定留言板发布消息")
    post.add_argument("board_id", help="留言板 ID")
    post.add_argument("content", help="留言内容")
    post.add_argument("--author", default="pc-cli", help="显示名称")
    post.add_argument(
        "--attach",
        action="append",
        default=[],
        metavar="PATH",
        help="上传附件并随消息发送（可重复指定多个路径）",
    )

    upload_attachment = sub.add_parser("upload-attachment", help="上传附件（不发布消息）")
    upload_attachment.add_argument("board_id", help="留言板 ID")
    upload_attachment.add_argument("paths", nargs="+", help="文件或目录路径（可多个）")

    post_attachment = sub.add_parser("post-attachment", help="上传附件并发布消息")
    post_attachment.add_argument("board_id", help="留言板 ID")
    post_attachment.add_argument("paths", nargs="+", help="文件或目录路径（可多个）")
    post_attachment.add_argument(
        "--content",
        default="",
        help="随附件一起发送的文字（省略时自动生成占位说明）",
    )
    post_attachment.add_argument("--author", default="pc-cli", help="显示名称")

    put = sub.add_parser("put", help="修改留言（需 host 密码）")
    put.add_argument("board_id", help="留言板 ID")
    put.add_argument("message_id", help="消息 ID")
    put.add_argument("content", help="新内容")

    delete = sub.add_parser("delete", help="删除留言（需 host 密码）")
    delete.add_argument("board_id", help="留言板 ID")
    delete.add_argument("message_id", help="消息 ID")

    export_pack = sub.add_parser("export-boardpack", help="导出留言板归档包到文件")
    export_pack.add_argument("board_id", help="留言板 ID")
    export_pack.add_argument("output", help="输出 .boardpack 路径")

    import_pack = sub.add_parser("import-boardpack", help="从归档包导入留言板（需 host 密码）")
    import_pack.add_argument("input", help="输入 .boardpack 路径")
    import_pack.add_argument("--name", default="", help="覆盖显示名（可选）")
    import_pack.add_argument(
        "--role-ids",
        default="",
        help="覆盖 role_ids，逗号分隔（可选，不含 admin）",
    )

    return parser


def load_config_defaults(config_path_arg: str) -> dict[str, Any]:
    path = Path(config_path_arg).expanduser().resolve() if config_path_arg else DEFAULT_CONFIG
    if not path.exists():
        return {}
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"读取配置失败 {path}: {exc}", file=sys.stderr)
        return {}
    if not isinstance(raw, dict):
        return {}
    return raw


def resolve_connection_args(args: argparse.Namespace) -> tuple[str, int, str, str]:
    config = load_config_defaults(args.config)
    host = args.host or "127.0.0.1"
    port = args.port if args.port is not None else int(config.get("port", 8765))
    guest_password = args.password or str(config.get("guest_password", ""))
    host_password = args.host_password or str(config.get("host_password", "")) or guest_password
    return host, port, guest_password, host_password


def request_api(
    host: str,
    port: int,
    method: str,
    path: str,
    *,
    password: str,
    body: dict | None,
    ca_cert: str,
    tls_fingerprint: str,
) -> tuple[int, dict]:
    payload = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers: dict[str, str] = {"Accept": "application/json"}
    if password:
        headers[PASSWORD_HEADER] = password
    if payload is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"

    ssl_context = ssl.create_default_context(cafile=ca_cert)
    ssl_context.check_hostname = False
    connection = http.client.HTTPSConnection(host, port, context=ssl_context, timeout=15)
    try:
        connection.connect()
        if tls_fingerprint:
            peer = connection.sock.getpeercert(binary_form=True)
            actual = hashlib.sha256(peer).hexdigest()
            expected = tls_fingerprint.strip().lower().replace(":", "")
            if actual != expected:
                raise ssl.SSLError(f"TLS 指纹不匹配 expected={expected} actual={actual}")
        connection.request(method, path, body=payload, headers=headers)
        response = connection.getresponse()
        raw = response.read().decode("utf-8", errors="replace")
        parsed = json.loads(raw) if raw.strip() else {}
        return response.status, parsed
    finally:
        connection.close()


def request_api_bytes(
    host: str,
    port: int,
    method: str,
    path: str,
    *,
    password: str,
    body: bytes | None,
    accept: str,
    content_type: str | None,
    ca_cert: str,
    tls_fingerprint: str,
    timeout: int = 120,
) -> tuple[int, bytes]:
    headers: dict[str, str] = {"Accept": accept}
    if password:
        headers[PASSWORD_HEADER] = password
    if content_type:
        headers["Content-Type"] = content_type

    ssl_context = ssl.create_default_context(cafile=ca_cert)
    ssl_context.check_hostname = False
    connection = http.client.HTTPSConnection(host, port, context=ssl_context, timeout=timeout)
    try:
        connection.connect()
        if tls_fingerprint:
            peer = connection.sock.getpeercert(binary_form=True)
            actual = hashlib.sha256(peer).hexdigest()
            expected = tls_fingerprint.strip().lower().replace(":", "")
            if actual != expected:
                raise ssl.SSLError(f"TLS 指纹不匹配 expected={expected} actual={actual}")
        connection.request(method, path, body=body, headers=headers)
        response = connection.getresponse()
        return response.status, response.read()
    finally:
        connection.close()


def format_timestamp(ms: int) -> str:
    if ms <= 0:
        return "?"
    dt = datetime.fromtimestamp(ms / 1000, tz=timezone.utc).astimezone()
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def print_human(command: str, status: int, body: dict[str, Any], board_id: str) -> None:
    if status >= 400 or not body.get("ok", True):
        error = body.get("error")
        message = body.get("message") or error or "请求失败"
        if error == "board_name_duplicate":
            print(f"错误 ({status}): 留言板名称已存在", file=sys.stderr)
            if message and message != error:
                print(f"  {message}", file=sys.stderr)
            return
        print(f"错误 ({status}): {message}", file=sys.stderr)
        return

    if command == "list-boards":
        boards = body.get("boards") or []
        role_id = body.get("role_id")
        role_label = body.get("role_label")
        role_class = body.get("role_class")
        if role_id or role_label or role_class:
            label = role_label or role_id or "?"
            kind = "管理员" if role_class == "admin" else ("用户" if role_class == "user" else role_class or "")
            manage = "可管理" if body.get("can_manage") else "只读"
            suffix = f" [{kind}]" if kind else ""
            print(f"当前角色: {label}{suffix}（{manage}）")
        if not boards:
            print("（暂无留言板）")
            return
        print(f"共 {len(boards)} 个留言板：")
        for board in boards:
            line = (
                f"  • {board.get('id', '?')}  "
                f"「{board.get('name', '')}」  "
                f"消息 {board.get('message_count', 0)}  "
                f"rev {board.get('revision', 0)}"
            )
            role_ids = board.get("role_ids")
            if "role_ids" in board:
                if role_ids is None:
                    line += "  roles=(legacy)"
                else:
                    line += f"  roles={role_ids}"
            print(line)
        return

    if command == "get-agent":
        if not body.get("enabled"):
            print("AI Agent：未启用")
            return
        models = body.get("models") or []
        commands = body.get("commands") or []
        board_ids = body.get("board_ids")
        scope = "全部留言板" if board_ids is None else ", ".join(board_ids)
        print("AI Agent：已启用")
        print(f"  生效范围: {scope}")
        print(f"  可 @ 模型: {', '.join(models) if models else '（无）'}")
        print(f"  可用 / 命令: {', '.join(commands) if commands else '（无）'}")
        return

    if command == "get-messages":
        name = body.get("board_name") or board_id
        messages = body.get("messages") or []
        agents = body.get("agents") or []
        participants = body.get("participants") or []
        commands = body.get("commands") or []
        print(f"留言板: {board_id} 「{name}」  共 {len(messages)} 条")
        if agents:
            print(f"可 @ 模型: {', '.join(agents)}")
        if commands:
            print(f"可用 / 命令: {', '.join(commands)}")
        if participants:
            print(f"参与者: {', '.join(participants)}")
        print()
        for message in messages:
            if message.get("deleted"):
                continue
            author = message.get("author_label") or "访客"
            ts = format_timestamp(int(message.get("created_at") or 0))
            content = (message.get("content") or "").replace("\n", " ↵ ")
            msg_id = message.get("id", "")
            print(f"[{ts}] {author}")
            print(f"  {content}")
            print(f"  id={msg_id}")
            print()
        return

    if command == "create-board":
        board = body.get("board") or {}
        board_id = board.get("id", "?")
        board_name = board.get("name", "")
        role_ids = board.get("role_ids")
        print(f"已创建留言板: {board_id} 「{board_name}」")
        if role_ids is None:
            print("  可见角色: (legacy，所有非 admin 角色可见)")
        elif not role_ids:
            print("  可见角色: (无，仅 host/admin 可见)")
            print("  提示: guest 执行 list-boards 看不到此板；可用 --role-ids guest 创建，或以 host 密码 list-boards")
        else:
            print(f"  可见角色: {', '.join(role_ids)}")
        return

    if command == "import-boardpack":
        board = body.get("board") or {}
        print(f"已导入留言板: {board.get('id')} 「{board.get('name')}」")
        return

    if command == "delete-board":
        print(f"已删除留言板: {board_id}")
        return

    if command == "post":
        message = body.get("message") or {}
        print(f"已发送到 {board_id}: id={message.get('id')}  author={message.get('author_label')}")
        attachments = message.get("attachments") or []
        if attachments:
            print(f"  附件 {len(attachments)} 个:")
            for item in attachments:
                kind = item.get("kind", "file")
                name = item.get("name", "?")
                print(f"    - {name} ({kind}, id={item.get('id', '?')})")
        return

    if command == "upload-attachment":
        attachments = body.get("attachments") or []
        print(f"已上传 {len(attachments)} 个附件到 {board_id}:")
        for item in attachments:
            kind = item.get("kind", "file")
            name = item.get("name", "?")
            print(f"  - {name} ({kind}, id={item.get('id', '?')})")
        return

    if command == "post-attachment":
        message = body.get("message") or {}
        print(f"已发送附件消息到 {board_id}: id={message.get('id')}  author={message.get('author_label')}")
        attachments = message.get("attachments") or []
        for item in attachments:
            print(f"  - {item.get('name', '?')} (id={item.get('id', '?')})")
        return

    if command in {"put", "delete"}:
        print(f"操作成功 (board={board_id})")
        return

    print(json.dumps(body, ensure_ascii=False, indent=2))


def _upload_attachment_refs(args: argparse.Namespace, board_id: str, paths: list[str]) -> list[dict[str, Any]]:
    from .board_attachment_client import upload_paths

    host, port, guest_password, _host_password = resolve_connection_args(args)
    if not paths:
        raise ValueError("至少指定一个文件或目录路径")
    return upload_paths(
        host,
        port,
        board_id=board_id,
        paths=[Path(item) for item in paths],
        password=guest_password,
        ca_cert=args.ca_cert,
        tls_fingerprint=args.tls_fingerprint,
        verbose=not args.json,
    )


def _post_message_with_attachments(
    args: argparse.Namespace,
    board_id: str,
    content: str,
    attachment_refs: list[dict[str, Any]],
) -> tuple[int, dict[str, Any]]:
    host, port, guest_password, _host_password = resolve_connection_args(args)
    body: dict[str, Any] = {
        "content": content,
        "author_label": getattr(args, "author", "pc-cli"),
    }
    if attachment_refs:
        body["attachments"] = attachment_refs
    return request_api(
        host,
        port,
        "POST",
        f"/boards/{board_id}/messages",
        password=guest_password,
        body=body,
        ca_cert=args.ca_cert,
        tls_fingerprint=args.tls_fingerprint,
    )


def _default_attachment_message(count: int) -> str:
    return f"[{count} 个附件]"


def _dispatch_board_client(args: argparse.Namespace, parser: argparse.ArgumentParser) -> int:
    host, port, guest_password, host_password = resolve_connection_args(args)

    board_id = getattr(args, "board_id", "")

    if args.command == "upload-attachment":
        try:
            refs = _upload_attachment_refs(args, board_id, args.paths)
        except Exception as exc:
            print(f"上传失败: {exc}", file=sys.stderr)
            return 1
        if args.json:
            print(json.dumps({"ok": True, "attachments": refs}, ensure_ascii=False, indent=2))
        else:
            print_human("upload-attachment", 200, {"ok": True, "attachments": refs}, board_id)
        return 0

    if args.command == "post-attachment":
        try:
            refs = _upload_attachment_refs(args, board_id, args.paths)
        except Exception as exc:
            print(f"上传失败: {exc}", file=sys.stderr)
            return 1
        content = args.content.strip() or _default_attachment_message(len(refs))
        try:
            status, payload = _post_message_with_attachments(args, board_id, content, refs)
        except Exception as exc:
            print(f"发帖失败: {exc}", file=sys.stderr)
            return 1
        if args.json:
            print(json.dumps({"status": status, "body": payload}, ensure_ascii=False, indent=2))
        else:
            print_human("post-attachment", status, payload, board_id)
        return 0 if status < 400 else 1

    if args.command == "list-boards":
        path, method, body, password = "/boards", "GET", None, guest_password
    elif args.command == "get-messages":
        path, method, body, password = f"/boards/{board_id}/messages", "GET", None, guest_password
    elif args.command == "get-agent":
        path, method, body, password = "/agent", "GET", None, guest_password
    elif args.command == "create-board":
        body: dict[str, Any] = {"name": args.name}
        role_ids_raw = args.role_ids.strip()
        if role_ids_raw:
            body["role_ids"] = [item.strip() for item in role_ids_raw.split(",") if item.strip()]
        else:
            body["role_ids"] = []
        path, method, body, password = "/boards", "POST", body, host_password
    elif args.command == "delete-board":
        path, method, body, password = f"/boards/{board_id}", "DELETE", None, host_password
    elif args.command == "post":
        attach_paths = [item for item in (args.attach or []) if str(item).strip()]
        attachment_refs: list[dict[str, Any]] = []
        if attach_paths:
            try:
                attachment_refs = _upload_attachment_refs(args, board_id, attach_paths)
            except Exception as exc:
                print(f"上传附件失败: {exc}", file=sys.stderr)
                return 1
        content = args.content.strip()
        if not content and attachment_refs:
            content = _default_attachment_message(len(attachment_refs))
        path = f"/boards/{board_id}/messages"
        method = "POST"
        body = {"content": content, "author_label": args.author}
        if attachment_refs:
            body["attachments"] = attachment_refs
        password = guest_password
    elif args.command == "put":
        path = f"/boards/{board_id}/messages/{args.message_id}"
        method = "PUT"
        body = {"content": args.content}
        password = host_password
    elif args.command == "delete":
        path = f"/boards/{board_id}/messages/{args.message_id}"
        method = "DELETE"
        body = None
        password = host_password
    elif args.command == "export-boardpack":
        path = f"/boards/{board_id}/export.boardpack"
        method = "GET"
        body = None
        password = guest_password
        try:
            status, payload = request_api_bytes(
                host,
                port,
                method,
                path,
                password=password,
                body=None,
                accept="application/zip, application/octet-stream, */*",
                content_type=None,
                ca_cert=args.ca_cert,
                tls_fingerprint=args.tls_fingerprint,
            )
        except Exception as exc:
            print(f"请求失败 ({host}:{port}): {exc}", file=sys.stderr)
            return 1
        if status >= 400:
            detail = payload.decode("utf-8", errors="replace")
            print(f"错误 ({status}): {detail}", file=sys.stderr)
            return 1
        output = Path(args.output).expanduser()
        output.write_bytes(payload)
        print(f"已导出 boardpack: {output} ({len(payload)} 字节)")
        return 0
    elif args.command == "import-boardpack":
        pack_path = Path(args.input).expanduser()
        if not pack_path.exists():
            print(f"文件不存在: {pack_path}", file=sys.stderr)
            return 1
        query_parts: list[str] = []
        if args.name.strip():
            from urllib.parse import quote

            query_parts.append(f"name={quote(args.name.strip())}")
        if args.role_ids.strip():
            from urllib.parse import quote

            query_parts.append(f"role_ids={quote(args.role_ids.strip())}")
        path = "/boards/import"
        if query_parts:
            path = f"{path}?{'&'.join(query_parts)}"
        method = "POST"
        body_bytes = pack_path.read_bytes()
        password = host_password
        try:
            status, payload = request_api_bytes(
                host,
                port,
                method,
                path,
                password=password,
                body=body_bytes,
                accept="application/json",
                content_type="application/vnd.localmanager.boardpack+zip",
                ca_cert=args.ca_cert,
                tls_fingerprint=args.tls_fingerprint,
            )
        except Exception as exc:
            print(f"请求失败 ({host}:{port}): {exc}", file=sys.stderr)
            return 1
        try:
            parsed = json.loads(payload.decode("utf-8"))
        except json.JSONDecodeError:
            print(f"错误 ({status}): 响应不是 JSON", file=sys.stderr)
            return 1
        if args.json:
            print(json.dumps({"status": status, "body": parsed}, ensure_ascii=False, indent=2))
        else:
            print_human("import-boardpack", status, parsed, "")
        return 0 if status < 400 else 1
    else:
        parser.error(f"未知命令: {args.command}")
        return 2

    try:
        status, payload = request_api(
            host,
            port,
            method,
            path,
            password=password,
            body=body,
            ca_cert=args.ca_cert,
            tls_fingerprint=args.tls_fingerprint,
        )
    except Exception as exc:
        print(f"请求失败 ({host}:{port}): {exc}", file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps({"status": status, "body": payload}, ensure_ascii=False, indent=2))
    else:
        print_human(args.command, status, payload, board_id)

    return 0 if status < 400 else 1


def run_board_client(argv: list[str] | None = None) -> int:
    """Run board_client with the given argv (defaults to sys.argv[1:])."""
    parser = build_parser()
    args = parser.parse_args(argv)
    return _dispatch_board_client(args, parser)


def main() -> int:
    return run_board_client()


if __name__ == "__main__":
    sys.exit(main())
