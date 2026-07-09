from __future__ import annotations

import argparse


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="python -m platalea.service_control.broker_server",
        description="platalea 特权 broker 占位入口（Phase 1 骨架）",
    )
    ap.add_argument("--state-dir", default="", help="控制面状态目录")
    args = ap.parse_args(argv)
    print(
        "broker_server 骨架已就位，当前不启动常驻 IPC 服务。"
        f" state_dir={args.state_dir}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())