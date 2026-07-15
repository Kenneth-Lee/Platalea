from __future__ import annotations

import argparse
import getpass
import os
import platform
import sys
from pathlib import Path

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
    BOOTSTRAP_LABEL,
    MacOSLaunchdAdapter,
    PRIVILEGED_LABEL,
)
from .service_control.platform.linux_systemd import LinuxSystemdAdapter
from .service_control.platform.windows_sc import WindowsServiceAdapter
from .service_control.state import load_control_state, save_control_state


class ServiceControlError(RuntimeError):
    pass


def detect_platform_backend() -> str:
    current = platform.system().lower()
    if current == "darwin":
        return "macos"
    if current == "linux":
        return "linux"
    if current == "windows":
        return "windows"
    raise ServiceControlError(f"当前平台暂不支持 service control: {current}")


def _select_adapter(
    *,
    windows_service_user: str | None = None,
    windows_service_password: str | None = None,
):
    backend = detect_platform_backend()
    if backend == "macos":
        return MacOSLaunchdAdapter()
    if backend == "linux":
        return LinuxSystemdAdapter()
    if backend == "windows":
        return WindowsServiceAdapter(
            service_user=windows_service_user,
            service_password=windows_service_password,
        )
    raise ServiceControlError(f"未知平台后端: {backend}")


def _resolve_windows_install_credentials(
    *,
    default_user: str,
    override_user: str,
    password_env: str,
) -> tuple[str, str]:
    service_user = (override_user or "").strip() or default_user
    if not service_user:
        raise ServiceControlError("Windows 安装失败：无法确定服务运行用户")

    if password_env:
        password = os.environ.get(password_env, "").strip()
        if not password:
            raise ServiceControlError(f"Windows 安装失败：环境变量 {password_env} 为空")
        return service_user, password

    prompt = f"请输入 Windows 服务账户 {service_user} 的密码: "
    password = getpass.getpass(prompt).strip()
    if not password:
        raise ServiceControlError("Windows 安装失败：服务账户密码不能为空")
    return service_user, password


def _resolve_install_config_path(*, owner: ActiveOwner, config_arg: str) -> Path:
    if config_arg.strip():
        return config_path(config_arg)
    return (Path(owner.home) / ".localmanager" / "config.json").expanduser().resolve()


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
        "start",
        "--config",
        str(cfg),
    ]
    user_spec = UserServerUnitSpec(
        label=BOOTSTRAP_LABEL,
        owner=active_owner,
        program_arguments=program_arguments,
        working_directory=str(source_root),
        stdout_path=stdout_path,
        stderr_path=stderr_path,
        environment={
            "PYTHONPATH": str(source_root),
            "PLATALEA_ALLOW_SERVICE_BOOTSTRAP": "1",
            "PLATALEA_POWER_SHUTDOWN": "1",
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
        user_server_label=BOOTSTRAP_LABEL,
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
    ap.add_argument(
        "--windows-user",
        default="",
        help="仅 Windows：指定 bootstrap 服务运行账户（默认当前 owner）",
    )
    ap.add_argument(
        "--windows-password-env",
        default="",
        help="仅 Windows：从该环境变量读取服务账户密码（避免交互输入）",
    )
    args = ap.parse_args(argv)
    try:
        backend = detect_platform_backend()
        owner = detect_active_owner()
        cfg = _resolve_install_config_path(owner=owner, config_arg=args.config)
        plan = build_install_plan(owner=owner, config=cfg)
        if backend == "windows":
            win_user, win_password = _resolve_windows_install_credentials(
                default_user=plan.owner.username,
                override_user=args.windows_user,
                password_env=args.windows_password_env,
            )
            adapter = _select_adapter(
                windows_service_user=win_user,
                windows_service_password=win_password,
            )
        else:
            adapter = _select_adapter()
        control_paths = service_control_paths()
        control_paths.logs_dir.mkdir(parents=True, exist_ok=True)
        user_logs_dir = Path(plan.user_server_spec.stdout_path).parent
        user_logs_dir.mkdir(parents=True, exist_ok=True)
        geteuid = getattr(os, "geteuid", None)
        if callable(geteuid) and geteuid() == 0:
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
            print(f"已安装 service control（独立 broker + 开机一次性 bootstrap），owner={plan.owner.username}")
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
    print(f"bootstrap unit: {status.user_server_unit}")
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
        description="卸载 platalea 独立系统服务（broker + bootstrap）",
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
