#!/usr/bin/env python3
from __future__ import annotations

import argparse
import http.client
import json
import ssl
import sys
from pathlib import Path

from family_common import PASSWORD_HEADER, pem_cert_fingerprint_sha256

TLS_DIR = Path(__file__).resolve().parent / "tls"
DEFAULT_CA = TLS_DIR / "ca_cert.pem"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="LocalManager 留言板 API 命令行客户端。")
    parser.add_argument("host", help="目标主机 IP 或 .local 主机名")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--password", default="", help="接入密码（普通或宿主）")
    parser.add_argument("--board-id", default="default")
    parser.add_argument("--ca-cert", default=str(DEFAULT_CA))
    parser.add_argument(
        "--tls-fingerprint",
        default="",
        help="服务端 TLS SHA-256 指纹；不传则仅校验 CA",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("list-boards", help="列出留言板")
    sub.add_parser("get-messages", help="读取留言板消息")

    post = sub.add_parser("post", help="发布留言")
    post.add_argument("content", help="留言内容")
    post.add_argument("--author", default="pc-cli")

    put = sub.add_parser("put", help="修改留言（需宿主密码）")
    put.add_argument("message_id")
    put.add_argument("content")

    delete = sub.add_parser("delete", help="删除留言（需宿主密码）")
    delete.add_argument("message_id")

    return parser


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
    headers = {
        "Accept": "application/json",
    }
    if password:
        headers[PASSWORD_HEADER] = password
    if payload is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"

    ssl_context = ssl.create_default_context(cafile=ca_cert)
    ssl_context.check_hostname = False
    connection = http.client.HTTPSConnection(host, port, context=ssl_context, timeout=8)
    try:
        connection.connect()
        if tls_fingerprint:
            peer = connection.sock.getpeercert(binary_form=True)
            actual = pem_cert_fingerprint_sha256_from_der(peer)
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


def pem_cert_fingerprint_sha256_from_der(der_bytes: bytes) -> str:
    import hashlib

    return hashlib.sha256(der_bytes).hexdigest()


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    board_id = args.board_id

    if args.command == "list-boards":
        path = "/boards"
        method = "GET"
        body = None
    elif args.command == "get-messages":
        path = f"/boards/{board_id}/messages"
        method = "GET"
        body = None
    elif args.command == "post":
        path = f"/boards/{board_id}/messages"
        method = "POST"
        body = {"content": args.content, "author_label": args.author}
    elif args.command == "put":
        path = f"/boards/{board_id}/messages/{args.message_id}"
        method = "PUT"
        body = {"content": args.content}
    elif args.command == "delete":
        path = f"/boards/{board_id}/messages/{args.message_id}"
        method = "DELETE"
        body = None
    else:
        parser.error(f"未知命令: {args.command}")
        return 2

    try:
        status, payload = request_api(
            args.host,
            args.port,
            method,
            path,
            password=args.password,
            body=body,
            ca_cert=args.ca_cert,
            tls_fingerprint=args.tls_fingerprint,
        )
    except Exception as exc:
        print(f"请求失败: {exc}", file=sys.stderr)
        return 1

    print(json.dumps({"status": status, "body": payload}, ensure_ascii=False, indent=2))
    return 0 if status < 400 else 1


if __name__ == "__main__":
    sys.exit(main())
