#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import http.client
import json
import ssl
import sys
import time
import uuid
from pathlib import Path

TLS_DIR = Path(__file__).resolve().parent / "tls"
DEFAULT_TLS_CA_CERT = TLS_DIR / "ca_cert.pem"


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="向 LocalManager 调试服务发送一条测试消息。",
    )
    parser.add_argument(
        "host",
        help="目标主机，例如 192.168.3.74 或 kllt03.local",
    )
    parser.add_argument(
        "message",
        help="要发送的消息内容",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=8765,
        help="目标端口，默认 8765",
    )
    parser.add_argument(
        "--sender-name",
        default="pc-debug",
        help="发送方名称，默认 pc-debug",
    )
    parser.add_argument(
        "--sender-instance-id",
        default="",
        help="发送方实例 ID；默认自动生成一个临时 UUID",
    )
    parser.add_argument(
        "--sender-platform",
        default="python",
        help="发送方平台标识，默认 python",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=5.0,
        help="HTTPS 超时秒数，默认 5",
    )
    parser.add_argument(
        "--scheme",
        choices=["https", "http"],
        default="https",
        help="传输协议，默认 https",
    )
    parser.add_argument(
        "--ca-cert",
        default=str(DEFAULT_TLS_CA_CERT),
        help="私有 CA 根证书路径，默认 pc_tools/tls/ca_cert.pem",
    )
    parser.add_argument(
        "--tls-fingerprint",
        default="",
        help="可选，对端 TLS 证书的 SHA-256 指纹；传入后会额外做证书固定校验",
    )
    return parser


def normalize_fingerprint(value: str) -> str:
    return value.strip().lower().replace(":", "")


def verify_peer_fingerprint(peer_cert_der: bytes, expected_fingerprint: str) -> None:
    actual = hashlib.sha256(peer_cert_der).hexdigest()
    expected = normalize_fingerprint(expected_fingerprint)
    if expected and actual != expected:
        raise ssl.SSLError(
            f"TLS 指纹不匹配，expected={expected} actual={actual}"
        )


def send_message(
    host: str,
    port: int,
    sender_name: str,
    sender_instance_id: str,
    sender_platform: str,
    message: str,
    timeout: float,
    scheme: str,
    ca_cert: str,
    tls_fingerprint: str,
) -> dict[str, object]:
    payload = {
        "sender_name": sender_name,
        "sender_instance_id": sender_instance_id or str(uuid.uuid4()),
        "sender_platform": sender_platform,
        "content": message,
        "timestamp": int(time.time() * 1000),
    }
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {
        "Content-Type": "application/json; charset=utf-8",
        "Accept": "application/json",
    }
    if scheme == "https":
        ssl_context = ssl.create_default_context(cafile=ca_cert)
        ssl_context.check_hostname = False
        connection: http.client.HTTPConnection = http.client.HTTPSConnection(
            host=host,
            port=port,
            timeout=timeout,
            context=ssl_context,
        )
    else:
        connection = http.client.HTTPConnection(host=host, port=port, timeout=timeout)

    try:
        connection.connect()
        if scheme == "https":
            peer_cert = connection.sock.getpeercert(binary_form=True)
            verify_peer_fingerprint(peer_cert, tls_fingerprint)
        connection.request("POST", "/message", body=body, headers=headers)
        response = connection.getresponse()
        raw = response.read().decode("utf-8", errors="replace")
        return {
            "status": response.status,
            "body": json.loads(raw) if raw.strip() else {},
        }
    finally:
        connection.close()


def main() -> int:
    parser = build_argument_parser()
    args = parser.parse_args()

    if args.port <= 0 or args.port > 65535:
        parser.error("端口必须在 1 到 65535 之间")

    message = args.message.strip()
    if not message:
        parser.error("消息内容不能为空")

    try:
        result = send_message(
            host=args.host,
            port=args.port,
            sender_name=args.sender_name.strip() or "pc-debug",
            sender_instance_id=args.sender_instance_id.strip(),
            sender_platform=args.sender_platform.strip() or "python",
            message=message,
            timeout=max(0.1, args.timeout),
            scheme=args.scheme,
            ca_cert=args.ca_cert,
            tls_fingerprint=args.tls_fingerprint,
        )
    except ssl.SSLError as exc:
        print(f"TLS 连接失败: {exc}", file=sys.stderr)
        return 1
    except OSError as exc:
        print(f"请求失败: {exc}", file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"发送失败: {exc}", file=sys.stderr)
        return 1

    if int(result.get("status", 0)) >= 400:
        print(json.dumps(result, ensure_ascii=False, indent=2), file=sys.stderr)
        return 1

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())