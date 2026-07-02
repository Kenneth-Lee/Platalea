"""Unified platalea CLI: PC local services, board API, and file utilities."""
from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

from . import __version__
from .board_client import BOARD_COMMANDS, build_parser as build_board_parser, run_board_client
from .bulletin_server import load_config, run_server
from .daemon import (
    already_running_message,
    ensure_server_running,
    load_server_probe,
    server_status,
    start_background,
    stop_server,
    wait_until_ready,
)
from .obfuscate import run_deobfuscate, run_obfuscate
from .paths import (
    app_dir,
    config_path,
    ensure_app_layout,
    ensure_config,
    log_file,
)
from .tls_setup import ensure_tls_materials

CLI_NAME = "platalea"

SERVE_COMMANDS = frozenset({
    "start",
    "stop",
    "status",
    "init-config",
    "init-tls",
})

OBFUSCATE_COMMANDS = frozenset({
    "obfuscate",
    "deobfuscate",
})


def _config_flag(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--config",
        default="",
        help=f"配置文件路径（默认 {config_path()}）",
    )


def _resolved_config(explicit: str) -> Path:
    return ensure_config(explicit or None)


def _cmd_start(args: argparse.Namespace) -> int:
    cfg_path = _resolved_config(args.config)
    try:
        ensure_tls_materials(cfg_path)
    except Exception as exc:
        print(f"TLS setup failed: {exc}", file=sys.stderr)
        return 1
    message = already_running_message(cfg_path)
    if message:
        print(message)
        return 0
    if start_background(cfg_path) != 0:
        return 1
    probe_host, port, ca, password = load_server_probe(cfg_path)
    if wait_until_ready(probe_host, port, ca_cert=ca, password=password):
        print(f"Server ready on port {port}. Log: {log_file()}")
        return 0
    print(
        f"Server started but not ready within 30s on {probe_host}:{port}. "
        f"Check {log_file()} for details.",
        file=sys.stderr,
    )
    return 1


def _cmd_stop(args: argparse.Namespace) -> int:
    cfg_path = _resolved_config(args.config) if getattr(args, "config", "") else ensure_config(None)
    return stop_server(cfg_path)


def _cmd_serve_daemon(args: argparse.Namespace) -> int:
    cfg_path = Path(args.config).expanduser().resolve()
    try:
        ensure_tls_materials(cfg_path)
    except Exception as exc:
        print(f"TLS setup failed: {exc}", file=sys.stderr)
        return 1
    try:
        config, agent_config = load_config(cfg_path)
    except Exception as exc:
        print(f"加载配置失败: {exc}", file=sys.stderr)
        return 1
    logging.basicConfig(
        level=getattr(logging, config.log_level, logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    try:
        return run_server(config, agent_config)
    except Exception as exc:
        logging.getLogger("local_manager.bulletin_server").exception("留言板服务异常退出: %s", exc)
        return 1


def _cmd_status(args: argparse.Namespace) -> int:
    cfg_path = _resolved_config(args.config)
    probe_host, port, ca, password = load_server_probe(cfg_path)
    print(server_status(probe_host, port, ca_cert=ca, password=password))
    print(f"Config: {cfg_path}")
    print(f"Data dir: {app_dir()}")
    return 0


def _cmd_init_config(args: argparse.Namespace) -> int:
    path = config_path(args.config or None)
    if path.exists() and not args.force:
        print(f"Config already exists: {path}")
        return 0
    ensure_app_layout()
    created = ensure_config(path if args.config else None)
    print(f"Config written: {created}")
    print(f"Data directory: {app_dir()}")
    return 0


def _cmd_init_tls(args: argparse.Namespace) -> int:
    ensure_app_layout()
    cfg_path = _resolved_config(args.config)
    try:
        tls_home = ensure_tls_materials(cfg_path, force=args.force)
    except Exception as exc:
        print(f"TLS setup failed: {exc}", file=sys.stderr)
        return 1
    print(f"TLS materials ready in {tls_home}")
    return 0


def _run_board_with_auto_start(argv: list[str]) -> int:
    board_parser = build_board_parser(prog=CLI_NAME)
    args, _unknown = board_parser.parse_known_args(argv)
    host = (args.host or "127.0.0.1").strip().lower()
    local_hosts = {"127.0.0.1", "localhost", "::1"}
    if host in local_hosts:
        cfg_path = _resolved_config(args.config)
        raw = json.loads(cfg_path.read_text(encoding="utf-8"))
        guest_password = args.password or str(raw.get("guest_password", ""))
        try:
            ensure_server_running(cfg_path, guest_password=guest_password)
        except RuntimeError as exc:
            print(str(exc), file=sys.stderr)
            return 1
    return run_board_client(argv, prog=CLI_NAME)


def _print_unified_help() -> None:
    home = app_dir()
    cfg = config_path()
    ca = home / "tls" / "ca_cert.pem"
    print(
        f"""usage: {CLI_NAME} [--version] <command> ...

LocalManager PC 端本地工具：家庭留言板 HTTPS 服务、API 与文件工具。

配置与数据目录: {home}
  config.json    服务配置（默认 {cfg}）
  boards/        留言板数据
  tls/           TLS 证书（首次 {CLI_NAME} start 时自动安装）
  server.log     后台模式日志

服务管理:
  init-config [--force] [--config PATH]
                        创建默认配置文件
  init-tls [--force] [--config PATH]
                        手动安装/覆盖 TLS 证书（通常不必单独运行）
  start [--config PATH]
                        启动 HTTPS + mDNS 守护进程（日志写入 server.log）
  stop [--config PATH]
                        停止本机服务（按 pid 文件或监听端口）
  status [--config PATH]
                        查看运行状态

留言板 API（连接本机 127.0.0.1 时，若服务未运行会自动后台启动）:
  list-boards           列出所有留言板
  get-agent             查看 AI Agent 配置
  get-messages BOARD    读取指定留言板消息
  post BOARD CONTENT [--author NAME] [--attach PATH]
                        发布消息（--attach 可重复，上传附件并随消息发送）
  upload-attachment BOARD PATH [PATH...]
                        上传附件（不发布消息）
  post-attachment BOARD PATH [PATH...] [--content TEXT] [--author NAME]
                        上传附件并发布消息
  create-board NAME     创建留言板（需 host 密码）
  delete-board BOARD    删除留言板（需 host 密码）
  put BOARD MSG_ID CONTENT
                        修改留言（需 host 密码）
  delete BOARD MSG_ID   删除留言（需 host 密码）
  export-boardpack BOARD OUTPUT.boardpack
                        导出留言板归档包
  import-boardpack INPUT.boardpack [--name NAME] [--role-ids IDS]
                        导入留言板（需 host 密码）

文件工具（与 Android 快速混淆 .qx 格式兼容，原地修改）:
  obfuscate INPUT -p PASSWORD [-q]
                        混淆文件或目录（原文件变为 .qx）
  deobfuscate INPUT -p PASSWORD [-q]
                        反混淆 .qx 文件或目录（恢复原名）

API 全局选项（写在子命令之前）:
  --host HOST           目标主机，默认 127.0.0.1
  --port PORT           HTTPS 端口，未指定时从 --config 读取
  --password PASSWORD   guest 密码，未指定时从 config 读取
  --host-password PASSWORD
                        host 密码（create/delete 等管理操作）
  --config PATH         配置文件（默认 {cfg}）
  --ca-cert PATH        CA 证书（默认 {ca}）
  --tls-fingerprint HEX 可选，固定服务端 TLS SHA-256 指纹
  --json                输出原始 JSON

示例:
  {CLI_NAME} init-config
  {CLI_NAME} start
  {CLI_NAME} stop
  {CLI_NAME} status
  {CLI_NAME} list-boards
  {CLI_NAME} get-messages default
  {CLI_NAME} post default "Hello from PC"
  {CLI_NAME} obfuscate secret.txt -p mypass
  {CLI_NAME} deobfuscate secret.txt.qx -p mypass
  {CLI_NAME} --host 192.168.1.10 --password guest list-boards

子命令详细说明: {CLI_NAME} <command> -h

兼容: 旧命令 lmserver 仍可用（已弃用）。
"""
    )


def build_top_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog=CLI_NAME,
        description="LocalManager PC 端本地工具。",
        add_help=False,
    )
    parser.add_argument("-h", "--help", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--version", action="version", version=f"{CLI_NAME} {__version__}")
    sub = parser.add_subparsers(dest="command")

    start = sub.add_parser("start", help="启动 HTTPS + mDNS 守护进程", add_help=False)
    start.add_argument("-h", "--help", action="help", help=argparse.SUPPRESS)
    _config_flag(start)

    stop = sub.add_parser("stop", help="停止本机服务", add_help=False)
    stop.add_argument("-h", "--help", action="help", help=argparse.SUPPRESS)
    _config_flag(stop)
    stop.set_defaults(_handler=_cmd_stop)

    status = sub.add_parser("status", help="查看服务运行状态", add_help=False)
    status.add_argument("-h", "--help", action="help", help=argparse.SUPPRESS)
    _config_flag(status)
    status.set_defaults(_handler=_cmd_status)

    init_cfg = sub.add_parser(
        "init-config",
        help=f"创建默认配置（{app_dir()}）",
        add_help=False,
    )
    init_cfg.add_argument("-h", "--help", action="help", help=argparse.SUPPRESS)
    init_cfg.add_argument("--config", default="", help="自定义配置文件路径")
    init_cfg.add_argument("--force", action="store_true", help="覆盖已有配置文件")
    init_cfg.set_defaults(_handler=_cmd_init_config)

    init_tls = sub.add_parser("init-tls", help="安装 TLS 证书（通常 start 会自动安装）", add_help=False)
    init_tls.add_argument("-h", "--help", action="help", help=argparse.SUPPRESS)
    _config_flag(init_tls)
    init_tls.add_argument("--force", action="store_true", help="覆盖已有 TLS 文件")
    init_tls.set_defaults(_handler=_cmd_init_tls)

    return parser


def _parse_serve_daemon_argv(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog=f"{CLI_NAME} _serve-daemon")
    _config_flag(parser)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    if not argv:
        _print_unified_help()
        return 0

    if argv[0] in {"--version", "-V"}:
        print(f"{CLI_NAME} {__version__}")
        return 0

    if argv[0] in {"-h", "--help"} and len(argv) == 1:
        _print_unified_help()
        return 0

    head = argv[0]
    if head == "_serve-daemon":
        return _cmd_serve_daemon(_parse_serve_daemon_argv(argv[1:]))

    if head in SERVE_COMMANDS:
        parser = build_top_parser()
        args = parser.parse_args(argv)
        if head == "start":
            return _cmd_start(args)
        handler = getattr(args, "_handler", None)
        if handler is None:
            parser.error(f"Missing handler for command: {head}")
        return handler(args)

    if head in OBFUSCATE_COMMANDS:
        if len(argv) >= 2 and argv[1] in {"-h", "--help"}:
            if head == "obfuscate":
                run_obfuscate(["--help"])
            else:
                run_deobfuscate(["--help"])
            return 0
        if head == "obfuscate":
            return run_obfuscate(argv[1:])
        return run_deobfuscate(argv[1:])

    if head in BOARD_COMMANDS:
        if len(argv) >= 2 and argv[1] in {"-h", "--help"}:
            build_board_parser(prog=CLI_NAME).print_help()
            return 0
        return _run_board_with_auto_start(argv)

    if head.startswith("-"):
        if "-h" in argv or "--help" in argv:
            build_board_parser(prog=CLI_NAME).print_help()
            return 0
        return _run_board_with_auto_start(argv)

    print(f"未知命令: {head}", file=sys.stderr)
    print(
        f"运行 {CLI_NAME} -h 查看全部命令，或 {CLI_NAME} <command> -h 查看单项说明。",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
