"""Unified platalea CLI: PC local services, board API, and file utilities."""
from __future__ import annotations

import argparse
import json
import logging
import sys
from collections.abc import Callable
from pathlib import Path

from . import __version__
from .board_client import (
    BOARD_COMMANDS,
    build_parser as build_board_parser,
    render_board_commands_help,
    run_board_client,
)
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
from .config_import import run_import_config
from .gpg_cmd import (
    run_list_keys,
    run_pass_decrypt,
    run_pass_encrypt,
    run_quick_decrypt,
    run_quick_encrypt,
)
from .power_cmd import run_power_shutdown
from .service_cmd import run_service_install, run_service_status, run_service_uninstall
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
})

GROUP_COMMANDS = frozenset({"gpg", "file", "config", "service", "power", "help"})

GPG_SUBCOMMANDS: dict[str, Callable[[list[str] | None], int]] = {
    "list-keys": run_list_keys,
    "pass-encrypt": run_pass_encrypt,
    "pass-decrypt": run_pass_decrypt,
    "quick-encrypt": run_quick_encrypt,
    "quick-decrypt": run_quick_decrypt,
}

FILE_SUBCOMMANDS: dict[str, Callable[[list[str] | None], int]] = {
    "obfuscate": run_obfuscate,
    "deobfuscate": run_deobfuscate,
}

CONFIG_SUBCOMMANDS: dict[str, Callable[[list[str] | None], int]] = {}

SERVICE_SUBCOMMANDS: dict[str, Callable[[list[str] | None], int]] = {
    "install": run_service_install,
    "uninstall": run_service_uninstall,
    "status": run_service_status,
}

POWER_SUBCOMMANDS: dict[str, Callable[[list[str] | None], int]] = {
    "shutdown": run_power_shutdown,
}


def run_config_init(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog=f"{CLI_NAME} config init",
        description=f"创建默认配置（{app_dir()}）",
    )
    parser.add_argument("--config", default="", help="自定义配置文件路径")
    parser.add_argument("--force", action="store_true", help="覆盖已有配置文件")
    args = parser.parse_args(argv)
    return _cmd_init_config(args)


def run_config_init_tls(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog=f"{CLI_NAME} config init-tls",
        description="安装 TLS 证书（通常 start 会自动安装）",
    )
    _config_flag(parser)
    parser.add_argument("--force", action="store_true", help="覆盖已有 TLS 文件")
    args = parser.parse_args(argv)
    return _cmd_init_tls(args)


CONFIG_SUBCOMMANDS.update({
    "init": run_config_init,
    "init-tls": run_config_init_tls,
    "import": run_import_config,
})

HELP_TOPICS = frozenset({"serve", "board", "gpg", "file", "config", "service", "power"})


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


def _data_dir_blurb(home: Path, cfg: Path) -> str:
    return f"""配置与数据目录: {home}
  config.json    服务配置（默认 {cfg}）
  boards/        留言板数据
  tls/           TLS 证书（首次 {CLI_NAME} start 时自动安装）
  server.log     后台模式日志"""


def _serve_help() -> str:
    return f"""服务管理:
  start [--config PATH]
                        启动 HTTPS + mDNS 守护进程（日志写入 server.log）
  stop [--config PATH]
                        停止本机服务（按 pid 文件或监听端口）
  status [--config PATH]
                        查看运行状态"""


def _board_help() -> str:
        return f"""{render_board_commands_help()}

API 全局选项（写在子命令之前）:
  --host HOST           目标主机，默认 127.0.0.1
  --port PORT           HTTPS 端口，未指定时从 --config 读取
  --board BOARD         留言板 ID（默认 default_board）
  --password PASSWORD   guest 密码，未指定时从 config 读取
  --host-password PASSWORD
                        host 密码（create/delete 等管理操作）
  --config PATH         配置文件
  --ca-cert PATH        CA 证书
  --tls-fingerprint HEX 可选，固定服务端 TLS SHA-256 指纹
  --json                输出原始 JSON"""


def _gpg_help() -> str:
    return f"""GPG（需本机安装 gpg，密钥默认 ~/.localmanager/gnupg/）:
    gpg list-keys [--public-only|--secret-only] [--json]
                                                检查当前可用公钥/私钥、指纹和 UID（用于确认 -r 收件人）
  gpg pass-encrypt INPUT -r RECIPIENT [-o OUT.pass] [--pubring PATH]
                        公钥加密为 .pass（ASCII armor，兼容 Android 密码保护）
  gpg pass-decrypt INPUT -p KEYPASS [-o OUT] [--secret-keyring PATH]
                        解密 .pass 文件
  gpg quick-encrypt TEXT -r RECIPIENT [--pubring PATH]
                        快密加密，输出 Base64（兼容 Android 快密 Tab）
  gpg quick-decrypt TEXT -p KEYPASS [--secret-keyring PATH]
                        快密解密（Base64 或 armor 密文 → stdout）"""


def _file_help() -> str:
    return f"""本地文件工具（与 Android 快速混淆 .qx 格式兼容，原地修改）:
  file obfuscate INPUT -p PASSWORD [-q]
                        混淆文件或目录（原文件变为 .qx）
  file deobfuscate INPUT -p PASSWORD [-q]
                        反混淆 .qx 文件或目录（恢复原名）"""


def _config_help() -> str:
    return f"""配置（~/.localmanager/）:
  config init [--force] [--config PATH]
                        创建默认 config.json
  config init-tls [--force] [--config PATH]
                        手动安装/覆盖 TLS 证书（通常 start 会自动安装）
  config import FILE [--list] [--categories gpg,git,...] [--skip-keys]
                        从 Android 导出 JSON 导入公钥/私钥等到 PC"""


def _service_help() -> str:
        return """系统服务控制（Phase 1 骨架）:
    service install [--config PATH]
                                                以当前用户为 owner 安装系统控制面与开机自启
    service uninstall [--keep-state]
                                                卸载系统控制面与托管服务
    service status
                                                查看控制面安装状态与 owner"""


def _power_help() -> str:
        return """系统电源控制（预留）:
    power shutdown
                                                请求系统关机（后续接入特权 broker）"""


def _examples_help() -> str:
    return f"""示例:
  {CLI_NAME} config init
  {CLI_NAME} start
    {CLI_NAME} service status
    {CLI_NAME} list-boards
    {CLI_NAME} get-agent
  {CLI_NAME} get-messages
    {CLI_NAME} post "Hello from PC" --author kenny
  {CLI_NAME} post --attach ./report.pdf --board kitchen
    {CLI_NAME} modify <message_id> "更新后的正文"
    {CLI_NAME} delete <message_id>
    {CLI_NAME} download-attachment <attachment_id> -o ./downloads/
    {CLI_NAME} export-boardpack ./kitchen.boardpack --board kitchen
    {CLI_NAME} import-boardpack ./kitchen.boardpack --name "厨房留言(导入)"
  {CLI_NAME} file obfuscate secret.txt -p mypass
  {CLI_NAME} gpg pass-encrypt notes.md -r 0xABCD1234
  {CLI_NAME} gpg quick-decrypt "$CIPHER" -p keypass
  {CLI_NAME} config import ~/Downloads/local_manager_config.json
  {CLI_NAME} help board
  {CLI_NAME} --host 192.168.1.10 --password guest list-boards

分组帮助: {CLI_NAME} help [serve|board|gpg|file|config|service|power]
子命令详细说明: {CLI_NAME} <command> -h  或  {CLI_NAME} gpg pass-encrypt -h"""


def _print_unified_help() -> None:
    home = app_dir()
    cfg = config_path()
    print(
        f"""usage: {CLI_NAME} [--version] <command> ...

LocalManager PC 端本地工具：家庭留言板 HTTPS 服务、API 与本地文件工具。

{_data_dir_blurb(home, cfg)}

{_serve_help()}

{_board_help()}

{_file_help()}

{_gpg_help()}

{_config_help()}

{_service_help()}

{_power_help()}

{_examples_help()}
"""
    )


def _print_topic_help(topic: str | None) -> int:
    if topic is None or topic in {"-h", "--help", "all"}:
        _print_unified_help()
        return 0
    if topic not in HELP_TOPICS:
        print(f"未知帮助主题: {topic}", file=sys.stderr)
        print(f"可用主题: {', '.join(sorted(HELP_TOPICS))}", file=sys.stderr)
        return 2
    printers = {
        "serve": _serve_help,
        "board": _board_help,
        "gpg": _gpg_help,
        "file": _file_help,
        "config": _config_help,
        "service": _service_help,
        "power": _power_help,
    }
    print(printers[topic]())
    print()
    print(f"完整帮助: {CLI_NAME} help")
    return 0


def _print_group_usage(group: str) -> None:
    if group == "gpg":
        print(_gpg_help())
    elif group == "file":
        print(_file_help())
    elif group == "config":
        print(_config_help())
    elif group == "service":
        print(_service_help())
    elif group == "power":
        print(_power_help())
    else:
        _print_unified_help()


def _run_group_command(
    group: str,
    subcommands: dict[str, Callable[[list[str] | None], int]],
    argv: list[str],
) -> int:
    if not argv or argv[0] in {"-h", "--help"}:
        _print_group_usage(group)
        return 0
    sub = argv[0]
    if sub in {"-h", "--help"} and len(argv) >= 2 and argv[1] in subcommands:
        subcommands[argv[1]](["--help"])
        return 0
    if sub not in subcommands:
        names = ", ".join(sorted(subcommands))
        print(f"未知 {group} 子命令: {sub}", file=sys.stderr)
        print(f"可用: {names}", file=sys.stderr)
        print(f"运行 {CLI_NAME} help {group} 查看说明。", file=sys.stderr)
        return 2
    if len(argv) >= 2 and argv[1] in {"-h", "--help"}:
        subcommands[sub](["--help"])
        return 0
    return subcommands[sub](argv[1:])


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

    if head == "help":
        topic = argv[1] if len(argv) > 1 else None
        return _print_topic_help(topic)

    if head in SERVE_COMMANDS:
        parser = build_top_parser()
        args = parser.parse_args(argv)
        if head == "start":
            return _cmd_start(args)
        handler = getattr(args, "_handler", None)
        if handler is None:
            parser.error(f"Missing handler for command: {head}")
        return handler(args)

    if head == "gpg":
        return _run_group_command("gpg", GPG_SUBCOMMANDS, argv[1:])

    if head == "file":
        return _run_group_command("file", FILE_SUBCOMMANDS, argv[1:])

    if head == "config":
        return _run_group_command("config", CONFIG_SUBCOMMANDS, argv[1:])

    if head == "service":
        return _run_group_command("service", SERVICE_SUBCOMMANDS, argv[1:])

    if head == "power":
        return _run_group_command("power", POWER_SUBCOMMANDS, argv[1:])

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
        f"运行 {CLI_NAME} help 查看全部命令，或 {CLI_NAME} help [serve|board|gpg|file|config|service|power]。",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
