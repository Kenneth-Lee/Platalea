from __future__ import annotations

import argparse
import sys

from .paths import service_control_paths
from .service_control.broker_client import BrokerClientError, request_broker


def run_power_status(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea power status",
        description="查询本机 power broker 状态",
    )
    ap.parse_args(argv)

    try:
        control = service_control_paths()
        resp = request_broker(state_root=control.state_root, op="status")
    except BrokerClientError as exc:
        print(f"查询失败: {exc}", file=sys.stderr)
        return 1
    if not resp.get("ok", False):
        print(str(resp.get("message", "broker status failed")), file=sys.stderr)
        return 1
    print(f"broker_pid: {resp.get('pid', '?')}")
    print(f"owner_uid: {resp.get('owner_uid', '?')}")
    return 0


def run_power_shutdown(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea power shutdown",
        description="请求系统关机（通过本机特权 broker）",
    )
    ap.add_argument(
        "--yes",
        action="store_true",
        help="确认执行关机请求（未提供该参数将只给出提示）",
    )
    args = ap.parse_args(argv)
    if not args.yes:
        print("为避免误操作，请加 --yes 确认发送关机请求。", file=sys.stderr)
        return 2

    try:
        control = service_control_paths()
        resp = request_broker(state_root=control.state_root, op="shutdown")
    except BrokerClientError as exc:
        print(f"关机请求失败: {exc}", file=sys.stderr)
        return 1

    if resp.get("ok", False):
        print(f"服务器已受理关机请求: {resp.get('message', '关机请求已提交')}")
        return 0
    print(
        f"服务器拒绝关机请求: {resp.get('error', 'shutdown_failed')} - {resp.get('message', '关机请求被拒绝')}",
        file=sys.stderr,
    )
    return 1
