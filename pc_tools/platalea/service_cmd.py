from __future__ import annotations

import argparse
import os
import platform
import sys
from pathlib import Path

from .daemon import stop_server
from .paths import config_path, service_control_paths
from .service_control.broker_client import broker_socket_path
from .service_control.models import (
    ActiveOwner,
    InstallPlan,
    PrivilegedUnitSpec,
    SupervisorStatus,
    UserServerUnitSpec,
    build_control_state,
    detect_active_owner,
)
from .service_control.platform.macos_launchd import (
    MacOSLaunchdAdapter,
    PRIVILEGED_LABEL,
    USER_SERVER_LABEL,
)
from .service_control.state import load_control_state, save_control_state


class ServiceControlError(RuntimeError):
    pass


def detect_platform_backend() -> str:
    current = platform.system().lower()
    if current == "darwin":
        return "macos"
    raise ServiceControlError(f"当前平台暂不支持 service control: {current}")


def _select_adapter() -> MacOSLaunchdAdapter:
    backend = detect_platform_backend()
    if backend == "macos":
        return MacOSLaunchdAdapter()
    raise ServiceControlError(f"未知平台后端: {backend}")


def build_install_plan(*, owner: ActiveOwner | None = None, config: Path | None = None) -> InstallPlan:
    active_owner = owner or detect_active_owner()
    control_paths = service_control_paths()
    previous = load_control_state(control_paths.state_file)
    previous_owner = previous.active_owner if previous is not None else None
    replaced = previous_owner is not None and previous_owner.uid != active_owner.uid
    cfg = config or config_path()
    source_root = Path(__file__).resolve().parent.parent
    user_logs_dir = Path(active_owner.home) / ".localmanager" / "service_control_logs"
    stdout_path = str(user_logs_dir / "user_server.stdout.log")
    stderr_path = str(user_logs_dir / "user_server.stderr.log")
    program_arguments = [
        sys.executable,
        "-m",
        "platalea",
        "_serve-daemon",
        "--config",
        str(cfg),
    ]
    user_spec = UserServerUnitSpec(
        label=USER_SERVER_LABEL,
        owner=active_owner,
        program_arguments=program_arguments,
        working_directory=str(source_root),
        stdout_path=stdout_path,
        stderr_path=stderr_path,
        environment={
            "PYTHONPATH": str(source_root),
        },
    )
    privileged_spec = PrivilegedUnitSpec(
        label=PRIVILEGED_LABEL,
        python_executable=sys.executable,
        broker_module="platalea.service_control.broker_server",
        state_dir=str(control_paths.state_root),
        working_directory=str(source_root),
        environment={
            "PYTHONPATH": str(source_root),
        },
    )
    state = build_control_state(
        owner=active_owner,
        privileged_label=PRIVILEGED_LABEL,
        user_server_label=USER_SERVER_LABEL,
        broker_token=None,
        revision=(previous.service_revision + 1) if previous is not None else 1,
    )
    return InstallPlan(
        owner=active_owner,
        replaced_previous_owner=replaced,
        previous_owner=previous_owner,
        user_server_spec=user_spec,
        privileged_spec=privileged_spec,
        control_state=state,
    )


def run_service_install(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea service install",
        description="安装 platalea 系统控制面与开机自启服务（owner=当前用户）",
    )
    ap.add_argument(
        "--config",
        default="",
        help=f"业务服务配置文件路径（默认 {config_path()}）",
    )
    args = ap.parse_args(argv)
    try:
        adapter = _select_adapter()
        cfg = config_path(args.config or None)
        # Avoid launchd restart loop when an old manual/background server already holds the port.
        stop_rc = stop_server(cfg)
        if stop_rc != 0:
            raise ServiceControlError(
                "检测到现有服务占用端口且无法自动停止，请先执行 `platalea stop` 后重试安装。"
            )

        plan = build_install_plan(config=cfg)
        control_paths = service_control_paths()
        control_paths.logs_dir.mkdir(parents=True, exist_ok=True)
        user_logs_dir = Path(plan.user_server_spec.stdout_path).parent
        user_logs_dir.mkdir(parents=True, exist_ok=True)
        if os.geteuid() == 0:
            os.chown(user_logs_dir, plan.owner.uid, plan.owner.uid)
        # Persist owner metadata before launchd starts the privileged broker so it can
        # create its Unix socket with the correct access group.
        save_control_state(control_paths.state_file, plan.control_state)
        adapter.install_privileged_unit(plan.privileged_spec)
        adapter.install_user_server_unit(plan.user_server_spec)
        if plan.replaced_previous_owner and plan.previous_owner is not None:
            print(
                "已覆盖旧 owner: "
                f"{plan.previous_owner.username} -> {plan.owner.username}"
            )
        else:
            print(f"已安装 service control，owner={plan.owner.username}")
        print(f"状态文件: {control_paths.state_file}")
        return 0
    except (ServiceControlError, OSError, ValueError) as exc:
        print(f"安装失败: {exc}", file=sys.stderr)
        return 1


def _format_status(status: SupervisorStatus, owner: str, state_path: Path) -> None:
    print(f"平台后端: {status.platform}")
    print(f"已安装: {'是' if status.installed else '否'}")
    print(f"owner: {owner or '(unknown)'}")
    print(f"privileged unit: {status.privileged_unit}")
    print(f"user server unit: {status.user_server_unit}")
    print(f"状态文件: {state_path}")
    for item in status.details:
        print(f"详情: {item}")


def run_service_status(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea service status",
        description="查看 platalea 系统控制面状态",
    )
    ap.parse_args(argv)
    try:
        adapter = _select_adapter()
        control_paths = service_control_paths()
        state = load_control_state(control_paths.state_file)
        status = adapter.query_status()
        owner = state.active_owner.username if state is not None else ""
        _format_status(status, owner, control_paths.state_file)
        return 0
    except (ServiceControlError, OSError, ValueError) as exc:
        print(f"状态查询失败: {exc}", file=sys.stderr)
        return 1


def run_service_uninstall(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea service uninstall",
        description="卸载 platalea 系统控制面与托管服务",
    )
    ap.add_argument(
        "--keep-state",
        action="store_true",
        help="仅卸载托管单元，保留控制面状态文件",
    )
    args = ap.parse_args(argv)
    try:
        adapter = _select_adapter()
        control_paths = service_control_paths()
        adapter.uninstall_all_units(ignore_missing=True)
        sock = broker_socket_path(control_paths.state_root)
        if sock.exists():
            sock.unlink()
        audit = control_paths.state_root / "audit.log"
        if audit.exists() and not args.keep_state:
            audit.unlink()
        if control_paths.state_file.exists() and not args.keep_state:
            control_paths.state_file.unlink()
        print("已卸载 service control")
        if args.keep_state:
            print(f"保留状态文件: {control_paths.state_file}")
        return 0
    except (ServiceControlError, OSError) as exc:
        print(f"卸载失败: {exc}", file=sys.stderr)
        return 1
