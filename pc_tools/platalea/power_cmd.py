from __future__ import annotations

import argparse
import sys


def run_power_shutdown(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea power shutdown",
        description="请求系统关机（需后续特权 broker 链路支持）",
    )
    ap.parse_args(argv)
    print(
        "power shutdown 尚未启用：当前版本只完成 service control 安装/卸载/状态骨架，"
        "后续会接入本机特权 broker。",
        file=sys.stderr,
    )
    return 1
