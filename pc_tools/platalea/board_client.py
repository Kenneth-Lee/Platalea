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
DEFAULT_BOARD_FALLBACK = "default"

BOARD_COMMAND_ORDER = (
    "list-boards",
    "get-agent",
    "get-messages",
    "post",
    "create-board",
    "delete-board",
    "modify",
    "delete",
    "download-attachment",
    "export-boardpack",
    "import-boardpack",
)

BOARD_COMMAND_META: dict[str, dict[str, str]] = {
    "list-boards": {
        "usage": "list-boards",
        "summary": "列出所有留言板",
    },
    "get-agent": {
        "usage": "get-agent",
        "summary": "查看 AI Agent 配置（可用模型列表）",
    },
    "get-messages": {
        "usage": "get-messages",
        "summary": "读取留言板消息（--board 指定板，默认同上）",
    },
    "post": {
        "usage": "post [CONTENT] [--attach PATH] [--author NAME]",
        "summary": "发布消息（正文与 --attach 至少一项；仅附件可省略 CONTENT）",
    },
    "create-board": {
        "usage": "create-board NAME",
        "summary": "创建留言板（需 admin 密码）",
    },
    "delete-board": {
        "usage": "delete-board",
        "summary": "删除留言板（需 admin 密码，--board 指定板）",
    },
    "modify": {
        "usage": "modify MSG_ID [CONTENT] [--attach PATH]",
        "summary": "修改留言（需 admin 密码；无 --attach 仅改正文，有 --attach 则替换附件）",
    },
    "delete": {
        "usage": "delete MSG_ID",
        "summary": "删除留言（需 admin 密码）",
    },
    "download-attachment": {
        "usage": "download-attachment ATTACHMENT_ID [-o PATH] [--file REL]",
        "summary": "下载留言板附件（目录附件可用 --file 指定内部文件）",
    },
    "export-boardpack": {
        "usage": "export-boardpack OUT",
        "summary": "导出留言板归档包到文件",
    },
    "import-boardpack": {
        "usage": "import-boardpack FILE",
        "summary": "从归档包导入留言板（需 admin 密码）",
    },
}

BOARD_COMMANDS = frozenset(BOARD_COMMAND_ORDER)


def render_board_commands_help() -> str:
    lines = [
        "留言板 API（--board 指定留言板，默认读 config.json 的 default_board）:",
    ]
    for name in BOARD_COMMAND_ORDER:
        meta = BOARD_COMMAND_META[name]
        usage = meta["usage"]
        summary = meta["summary"]
        if len(usage) <= 28:
            lines.append(f"  {usage:<30}{summary}")
        else:
            lines.append(f"  {usage}")
            lines.append(f"{'':<24}{summary}")
    return "\n".join(lines)


def build_parser(*, prog: str = "platalea") -> argparse.ArgumentParser:
    home = app_dir()
    parser = argparse.ArgumentParser(
        prog=prog,
        description="LocalManager 留言板 HTTPS API 客户端。",
        epilog=(
            f"示例（本机，默认读取 ~/.localmanager/config.json）：\n"
            f"  {prog} list-boards\n"
            f"  {prog} get-agent\n"
            f"  {prog} get-messages\n"
            f"  {prog} get-messages --board kitchen\n"
            f"  {prog} post \"@qwen2.5 你好\"\n"
            f"  {prog} post \"说明\" --attach ./report.pdf\n"
            f"  {prog} post --attach ./photo.jpg\n"
            f"  {prog} modify <message_id> \"更新后的正文\"\n"
            f"  {prog} delete <message_id>\n"
            f"  {prog} download-attachment <attachment_id> -o ./downloads/\n"
            f"  {prog} create-board \"厨房留言\"\n"
            f"  {prog} delete-board --board kitchen\n"
            f"  {prog} export-boardpack ./kitchen.boardpack --board kitchen\n"
            f"  {prog} import-boardpack ./kitchen.boardpack --name \"厨房留言(导入)\"\n"
            "\n"
            "连接局域网内其它设备（不会自动启动本机服务）：\n"
            f"  {prog} --host 192.168.1.10 --password guest list-boards\n"
            "\n"
            f"默认留言板：config.json 的 default_board（未设置时为 {DEFAULT_BOARD_FALLBACK}）。\n"
            f"配置与 TLS 默认目录: {home}\n"
            f"连接 127.0.0.1 时若服务未运行，{prog} 会自动后台启动本机服务。"
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
        help="接入密码；未指定时从 --config 读取 roles.admin.password",
    )
    parser.add_argument(
        "--board",
        default="",
        help=f"留言板 ID（默认读 config.json 的 default_board，否则 {DEFAULT_BOARD_FALLBACK}）",
    )
    parser.add_argument(
        "--config",
        default=str(DEFAULT_CONFIG),
        help=f"读取配置中的 port、密码与 default_board（默认 {DEFAULT_CONFIG}）",
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

    sub.add_parser("list-boards", help=BOARD_COMMAND_META["list-boards"]["summary"])
    sub.add_parser("get-agent", help=BOARD_COMMAND_META["get-agent"]["summary"])

    sub.add_parser("get-messages", help=BOARD_COMMAND_META["get-messages"]["summary"])

    create = sub.add_parser("create-board", help=BOARD_COMMAND_META["create-board"]["summary"])
    create.add_argument("name", help="留言板名称")
    create.add_argument(
        "--role-ids",
        default="guest",
        help="授权可见的非 admin 角色，逗号分隔（默认 guest；传空串表示仅 admin 可见）",
    )

    sub.add_parser("delete-board", help=BOARD_COMMAND_META["delete-board"]["summary"])

    post = sub.add_parser("post", help=BOARD_COMMAND_META["post"]["summary"])
    post.add_argument(
        "content",
        nargs="?",
        default="",
        help="留言内容（可省略，仅发附件时用 --attach）",
    )
    post.add_argument("--author", default="pc-cli", help="显示名称")
    post.add_argument(
        "--attach",
        action="append",
        default=[],
        metavar="PATH",
        help="上传附件并随消息发送（可重复指定多个路径）",
    )

    modify = sub.add_parser(
        "modify",
        help=BOARD_COMMAND_META["modify"]["summary"],
    )
    modify.add_argument("message_id", help="消息 ID")
    modify.add_argument(
        "content",
        nargs="?",
        default=None,
        help="新正文（仅替换附件时可省略）",
    )
    modify.add_argument(
        "--attach",
        action="append",
        default=[],
        metavar="PATH",
        help="上传新附件并替换消息上的原有附件（可重复）",
    )

    delete = sub.add_parser("delete", help=BOARD_COMMAND_META["delete"]["summary"])
    delete.add_argument("message_id", help="消息 ID")

    download_att = sub.add_parser("download-attachment", help=BOARD_COMMAND_META["download-attachment"]["summary"])
    download_att.add_argument("attachment_id", help="附件 ID（get-messages 输出中可见）")
    download_att.add_argument(
        "-o",
        "--output",
        default="",
        help="输出路径（单文件）或目录（目录附件整包解压；默认当前目录下附件名）",
    )
    download_att.add_argument(
        "--file",
        default="",
        metavar="REL_PATH",
        help="目录附件内的相对路径（仅下载其中一个文件）",
    )

    export_pack = sub.add_parser("export-boardpack", help=BOARD_COMMAND_META["export-boardpack"]["summary"])
    export_pack.add_argument("output", help="输出 .boardpack 路径")

    import_pack = sub.add_parser("import-boardpack", help=BOARD_COMMAND_META["import-boardpack"]["summary"])
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


def resolve_board_id(args: argparse.Namespace) -> str:
    explicit = str(getattr(args, "board", "") or "").strip()
    if explicit:
        return explicit
    config = load_config_defaults(args.config)
    default = str(config.get("default_board", DEFAULT_BOARD_FALLBACK)).strip()
    if not default:
        raise ValueError(
            "未指定 --board，且 config.json 中 default_board 为空。"
            "请 platalea list-boards 查看 ID 后使用 --board，或在 config.json 设置 default_board。"
        )
    return default


def resolve_board_id_for_request(
    args: argparse.Namespace,
    *,
    host: str,
    port: int,
    access_password: str,
) -> str:
    """Resolve --board to an actual board ID.

    Accepts exact board ID and board name. For the common legacy case where user passes
    --board default but multiple boards exist, prefer the board whose *name* is "default"
    (case-insensitive), then fallback to ID match.
    Accepts exact board ID and board name.
    """
    requested = resolve_board_id(args)
    if not requested:
        return requested

    # Local host with no explicit --board keeps lightweight behavior.
    if not str(getattr(args, "board", "") or "").strip():
        return requested

    try:
        status, payload = request_api(
            host,
            port,
            "GET",
            "/boards",
            password=access_password,
            body=None,
            ca_cert=args.ca_cert,
            tls_fingerprint=args.tls_fingerprint,
        )
    except Exception:
        return requested

    if status >= 400 or not isinstance(payload, dict) or not payload.get("ok", False):
        return requested

    boards = payload.get("boards") or []
    if not isinstance(boards, list) or not boards:
        return requested

    requested_lower = requested.lower()

    if requested_lower == DEFAULT_BOARD_FALLBACK:
        by_name_default = [
            b for b in boards
            if str(b.get("name", "")).strip().lower() == DEFAULT_BOARD_FALLBACK
        ]
        if len(by_name_default) == 1:
            board_id = str(by_name_default[0].get("id", "")).strip()
            if board_id:
                return board_id
        if len(by_name_default) > 1:
            with_messages = [
                b for b in by_name_default if int(b.get("message_count", 0) or 0) > 0
            ]
            candidate = with_messages[0] if with_messages else by_name_default[0]
            board_id = str(candidate.get("id", "")).strip()
            if board_id:
                return board_id

    by_id = [b for b in boards if str(b.get("id", "")).strip() == requested]
    if len(by_id) == 1:
        return requested

    by_name = [
        b for b in boards
        if str(b.get("name", "")).strip().lower() == requested_lower
    ]
    if len(by_name) == 1:
        board_id = str(by_name[0].get("id", "")).strip()
        if board_id:
            return board_id
    if len(by_name) > 1:
        with_messages = [b for b in by_name if int(b.get("message_count", 0) or 0) > 0]
        candidate = with_messages[0] if with_messages else by_name[0]
        board_id = str(candidate.get("id", "")).strip()
        if board_id:
            return board_id

    return requested


def resolve_connection_args(args: argparse.Namespace) -> tuple[str, int, str]:
    config = load_config_defaults(args.config)
    host = args.host or "127.0.0.1"
    port = args.port if args.port is not None else int(config.get("port", 8765))
    roles = config.get("roles") if isinstance(config, dict) else None
    admin_password = ""
    if isinstance(roles, dict):
        admin = roles.get("admin")
        if isinstance(admin, dict):
            admin_password = str(admin.get("password", ""))
    password = args.password or admin_password
    return host, port, password


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
        if not messages:
            diag = body.get("_diag_visible_boards") or []
            if diag:
                print("提示: 当前板为空。你当前密码可见的留言板消息数:")
                for item in diag:
                    board_name = item.get("name", "")
                    board_count = item.get("message_count", 0)
                    board_id_item = item.get("id", "?")
                    print(f"  - {board_id_item} 「{board_name}」: {board_count} 条")
                print("可用 --board <id> 指定目标板。")
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
            attachments = message.get("attachments") or []
            print(f"[{ts}] {author}")
            print(f"  {content}")
            print(f"  id={msg_id}")
            if attachments:
                print("  附件:")
                for item in attachments:
                    kind = item.get("kind", "file")
                    name = item.get("name", "?")
                    att_id = item.get("id", "?")
                    print(f"    - {name} ({kind}, attachment_id={att_id})")
            print()
        return

    if command == "create-board":
        board = body.get("board") or {}
        created_id = board.get("id", "?")
        board_name = board.get("name", "")
        role_ids = board.get("role_ids")
        print(f"已创建留言板: {created_id} 「{board_name}」")
        if role_ids is None:
            print("  可见角色: (仅 admin)")
        elif not role_ids:
            print("  可见角色: (仅 admin)")
            print("  提示: 可用 --role-ids guest 创建可见板，或显式指定其它角色")
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

    if command in {"modify", "delete"}:
        print(f"操作成功 (board={board_id})")
        return

    if command == "download-attachment":
        print(f"已下载附件到: {body.get('output', '?')}")
        return

    print(json.dumps(body, ensure_ascii=False, indent=2))


def _upload_attachment_refs(args: argparse.Namespace, board_id: str, paths: list[str]) -> list[dict[str, Any]]:
    from .board_attachment_client import upload_paths

    host, port, access_password = resolve_connection_args(args)
    if not paths:
        raise ValueError("至少指定一个文件或目录路径")
    return upload_paths(
        host,
        port,
        board_id=board_id,
        paths=[Path(item) for item in paths],
        password=access_password,
        ca_cert=args.ca_cert,
        tls_fingerprint=args.tls_fingerprint,
        verbose=not args.json,
    )


def _default_attachment_message(count: int) -> str:
    return f"[{count} 个附件]"


def _dispatch_modify(args: argparse.Namespace, board_id: str, access_password: str) -> tuple[str, str, dict[str, Any] | None, str] | int:
    attach_paths = [item for item in (args.attach or []) if str(item).strip()]
    content = args.content
    has_content = content is not None and str(content).strip()
    if not attach_paths and not has_content:
        print("错误：未指定 --attach 时必须提供新正文", file=sys.stderr)
        return 1
    attachment_refs: list[dict[str, Any]] = []
    if attach_paths:
        try:
            attachment_refs = _upload_attachment_refs(args, board_id, attach_paths)
        except Exception as exc:
            print(f"上传附件失败: {exc}", file=sys.stderr)
            return 1
    body: dict[str, Any] = {}
    if has_content:
        body["content"] = str(content).strip()
    if attach_paths:
        body["attachments"] = attachment_refs
    return (
        f"/boards/{board_id}/messages/{args.message_id}",
        "PUT",
        body,
        access_password,
    )


def _dispatch_board_client(args: argparse.Namespace, parser: argparse.ArgumentParser) -> int:
    host, port, access_password = resolve_connection_args(args)

    board_id = ""
    board_scoped = {
        "get-messages",
        "post",
        "modify",
        "delete",
        "download-attachment",
        "export-boardpack",
        "delete-board",
    }
    if args.command in board_scoped:
        try:
            board_id = resolve_board_id_for_request(
                args,
                host=host,
                port=port,
                access_password=access_password,
            )
        except ValueError as exc:
            print(str(exc), file=sys.stderr)
            return 1

    if args.command == "list-boards":
        path, method, body, request_password = "/boards", "GET", None, access_password
    elif args.command == "get-messages":
        path, method, body, request_password = f"/boards/{board_id}/messages", "GET", None, access_password
    elif args.command == "get-agent":
        path, method, body, request_password = "/agent", "GET", None, access_password
    elif args.command == "create-board":
        body: dict[str, Any] = {"name": args.name}
        role_ids_raw = args.role_ids.strip()
        if role_ids_raw:
            body["role_ids"] = [item.strip() for item in role_ids_raw.split(",") if item.strip()]
        else:
            body["role_ids"] = []
        path, method, body, request_password = "/boards", "POST", body, access_password
    elif args.command == "delete-board":
        path, method, body, request_password = f"/boards/{board_id}", "DELETE", None, access_password
    elif args.command == "post":
        attach_paths = [item for item in (args.attach or []) if str(item).strip()]
        content = (args.content or "").strip()
        if not content and not attach_paths:
            print("错误：消息正文与 --attach 不能同时为空", file=sys.stderr)
            return 1
        attachment_refs: list[dict[str, Any]] = []
        if attach_paths:
            try:
                attachment_refs = _upload_attachment_refs(args, board_id, attach_paths)
            except Exception as exc:
                print(f"上传附件失败: {exc}", file=sys.stderr)
                return 1
        if not content and attachment_refs:
            content = _default_attachment_message(len(attachment_refs))
        path = f"/boards/{board_id}/messages"
        method = "POST"
        body = {"content": content, "author_label": args.author}
        if attachment_refs:
            body["attachments"] = attachment_refs
        request_password = access_password
    elif args.command == "modify":
        result = _dispatch_modify(args, board_id, access_password)
        if isinstance(result, int):
            return result
        path, method, body, request_password = result
    elif args.command == "delete":
        path = f"/boards/{board_id}/messages/{args.message_id}"
        method = "DELETE"
        body = None
        request_password = access_password
    elif args.command == "download-attachment":
        from .board_attachment_client import download_attachment_to_path

        output_raw = (args.output or "").strip()
        if output_raw:
            output = Path(output_raw)
        else:
            output = Path.cwd()
        try:
            saved = download_attachment_to_path(
                host,
                port,
                board_id=board_id,
                attachment_id=args.attachment_id,
                output=output,
                password=access_password,
                ca_cert=args.ca_cert,
                tls_fingerprint=args.tls_fingerprint,
                file_path=args.file or "",
            )
        except Exception as exc:
            print(f"下载失败: {exc}", file=sys.stderr)
            return 1
        if args.json:
            print(json.dumps({"ok": True, "output": str(saved)}, ensure_ascii=False, indent=2))
        else:
            print_human("download-attachment", 200, {"ok": True, "output": str(saved)}, board_id)
        return 0
    elif args.command == "export-boardpack":
        path = f"/boards/{board_id}/export.boardpack"
        method = "GET"
        body = None
        request_password = access_password
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
        request_password = access_password
        try:
            status, payload = request_api_bytes(
                host,
                port,
                method,
                path,
                password=request_password,
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
            password=request_password,
            body=body,
            ca_cert=args.ca_cert,
            tls_fingerprint=args.tls_fingerprint,
        )
    except Exception as exc:
        print(f"请求失败 ({host}:{port}): {exc}", file=sys.stderr)
        return 1

    # Diagnose the common "board looks empty" confusion by showing all visible boards.
    if (
        args.command == "get-messages"
        and status < 400
        and isinstance(payload, dict)
        and payload.get("ok", True)
        and not (payload.get("messages") or [])
    ):
        try:
            list_status, list_payload = request_api(
                host,
                port,
                "GET",
                "/boards",
                password=access_password,
                body=None,
                ca_cert=args.ca_cert,
                tls_fingerprint=args.tls_fingerprint,
            )
            if list_status < 400 and isinstance(list_payload, dict) and list_payload.get("ok", False):
                payload["_diag_visible_boards"] = list_payload.get("boards") or []
        except Exception:
            # Best-effort diagnostics only; keep original output path.
            pass

    if args.json:
        print(json.dumps({"status": status, "body": payload}, ensure_ascii=False, indent=2))
    else:
        print_human(args.command, status, payload, board_id)

    return 0 if status < 400 else 1


def run_board_client(argv: list[str] | None = None, *, prog: str = "platalea") -> int:
    """Run board_client with the given argv (defaults to sys.argv[1:])."""
    parser = build_parser(prog=prog)
    args = parser.parse_args(list(argv if argv is not None else sys.argv[1:]))
    return _dispatch_board_client(args, parser)


def main() -> int:
    return run_board_client()


if __name__ == "__main__":
    sys.exit(main())
