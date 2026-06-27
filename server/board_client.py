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

from family_common import PASSWORD_HEADER

TLS_DIR = Path(__file__).resolve().parent / "tls"
DEFAULT_CA = TLS_DIR / "ca_cert.pem"
DEFAULT_CONFIG = Path(__file__).resolve().parent / "config.json"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="LocalManager 留言板 API 命令行客户端。",
        epilog=(
            "示例（本机服务已启动时）：\n"
            "  python3 server/board_client.py list-boards\n"
            "  python3 server/board_client.py get-messages default\n"
            "  python3 server/board_client.py post default \"@qwen2.5 你好\"\n"
            "  python3 server/board_client.py create-board \"厨房留言\"\n"
            "  python3 server/board_client.py delete-board kitchen\n"
            "  python3 server/board_client.py get-agent\n"
            "\n"
            "可从 config.json 读取端口与密码：\n"
            "  python3 server/board_client.py --config server/config.json get-messages default"
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
        default="",
        help=f"读取 server/config.json 中的 port 与密码（默认尝试 {DEFAULT_CONFIG}）",
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

    delete_board = sub.add_parser("delete-board", help="删除留言板（需 host 密码）")
    delete_board.add_argument("board_id", help="留言板 ID")

    post = sub.add_parser("post", help="在指定留言板发布消息")
    post.add_argument("board_id", help="留言板 ID")
    post.add_argument("content", help="留言内容")
    post.add_argument("--author", default="pc-cli", help="显示名称")

    put = sub.add_parser("put", help="修改留言（需 host 密码）")
    put.add_argument("board_id", help="留言板 ID")
    put.add_argument("message_id", help="消息 ID")
    put.add_argument("content", help="新内容")

    delete = sub.add_parser("delete", help="删除留言（需 host 密码）")
    delete.add_argument("board_id", help="留言板 ID")
    delete.add_argument("message_id", help="消息 ID")

    return parser


def load_config_defaults(config_path: str) -> dict[str, Any]:
    path = Path(config_path).expanduser().resolve() if config_path else DEFAULT_CONFIG
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


def format_timestamp(ms: int) -> str:
    if ms <= 0:
        return "?"
    dt = datetime.fromtimestamp(ms / 1000, tz=timezone.utc).astimezone()
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def print_human(command: str, status: int, body: dict[str, Any], board_id: str) -> None:
    if status >= 400 or not body.get("ok", True):
        message = body.get("message") or body.get("error") or "请求失败"
        print(f"错误 ({status}): {message}", file=sys.stderr)
        return

    if command == "list-boards":
        boards = body.get("boards") or []
        if not boards:
            print("（暂无留言板）")
            return
        print(f"共 {len(boards)} 个留言板：")
        for board in boards:
            print(
                f"  • {board.get('id', '?')}  "
                f"「{board.get('name', '')}」  "
                f"消息 {board.get('message_count', 0)}  "
                f"rev {board.get('revision', 0)}"
            )
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
        print(f"已创建留言板: {board.get('id')} 「{board.get('name')}」")
        return

    if command == "delete-board":
        print(f"已删除留言板: {board_id}")
        return

    if command == "post":
        message = body.get("message") or {}
        print(f"已发送到 {board_id}: id={message.get('id')}  author={message.get('author_label')}")
        return

    if command in {"put", "delete"}:
        print(f"操作成功 (board={board_id})")
        return

    print(json.dumps(body, ensure_ascii=False, indent=2))


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    host, port, guest_password, host_password = resolve_connection_args(args)

    board_id = getattr(args, "board_id", "")

    if args.command == "list-boards":
        path, method, body, password = "/boards", "GET", None, guest_password
    elif args.command == "get-messages":
        path, method, body, password = f"/boards/{board_id}/messages", "GET", None, guest_password
    elif args.command == "get-agent":
        path, method, body, password = "/agent", "GET", None, guest_password
    elif args.command == "create-board":
        path, method, body, password = "/boards", "POST", {"name": args.name}, host_password
    elif args.command == "delete-board":
        path, method, body, password = f"/boards/{board_id}", "DELETE", None, host_password
    elif args.command == "post":
        path = f"/boards/{board_id}/messages"
        method = "POST"
        body = {"content": args.content, "author_label": args.author}
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


if __name__ == "__main__":
    sys.exit(main())
