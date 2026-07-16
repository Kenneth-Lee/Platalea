from __future__ import annotations

import ctypes
from ctypes import wintypes
import subprocess
from dataclasses import dataclass
from pathlib import Path

from ...paths import service_control_paths
from ..models import PrivilegedUnitSpec, SupervisorStatus, UserServerUnitSpec
from .base import PowerAction

PRIVILEGED_LABEL = "com.localmanager.platalea.privileged"
BOOTSTRAP_LABEL = "com.localmanager.platalea.bootstrap"

_NOT_ADMIN_MESSAGE = (
    "安装 Windows 计划任务需要管理员权限。\n"
    "请右键点击命令提示符或 PowerShell，选择“以管理员身份运行”。"
)


@dataclass(frozen=True)
class WindowsLayout:
    broker_script: Path
    bootstrap_script: Path


def windows_layout() -> WindowsLayout:
    root = service_control_paths().state_root
    return WindowsLayout(
        broker_script=root / "broker_start.cmd",
        bootstrap_script=root / "bootstrap_start.cmd",
    )


def _quote_cmd(value: str) -> str:
    return '"' + value.replace('"', '""') + '"'


def _normalize_windows_user(user: str) -> str:
    r"""规范化 Windows 用户名，去掉 .\ 前缀。

    schtasks 内部用 LookupAccountName 解析 /ru 账户名，该 API **不认 .\user 格式**
    （报 ERROR_NONE_MAPPED / 1332）。裸用户名（如 nekin）能正确解析为本地账户，
    LogonUser 校验密码时也以 domain="." 默认本地，两者均可用裸名。
    """
    clean = user.strip()
    if not clean:
        return ""
    # 去掉 .\ 前缀（schtasks 无法解析）
    if clean.startswith(".\\"):
        clean = clean[2:]
    return clean


def _is_admin() -> bool:
    """检查当前进程是否以管理员权限运行"""
    try:
        return ctypes.windll.shell32.IsUserAnAdmin() != 0
    except (AttributeError, OSError):
        return False


def _verify_windows_logon(user: str, password: str) -> None:
    """用 Windows LogonUser API 主动校验账户密码，错误则 raise RuntimeError。

    计划任务 /create /ru 用户 /rp 密码 时 schtasks 也会校验密码，但提前用 LogonUser
    校验能给出更清晰的错误信息，且在写脚本之前就挡住错误密码。
    """
    if "\\" in user:
        domain, username = user.split("\\", 1)
    elif "@" in user:
        username, domain = user.split("@", 1)
    else:
        domain, username = ".", user

    advapi32 = ctypes.WinDLL("advapi32", use_last_error=True)
    logon_user = advapi32.LogonUserW
    logon_user.restype = wintypes.BOOL
    logon_user.argtypes = [
        wintypes.LPCWSTR, wintypes.LPCWSTR, wintypes.LPCWSTR,
        wintypes.DWORD, wintypes.DWORD, ctypes.POINTER(wintypes.HANDLE),
    ]
    token = wintypes.HANDLE()
    LOGON32_LOGON_NETWORK = 2
    LOGON32_PROVIDER_DEFAULT = 0
    ok = logon_user(
        username, domain, password,
        LOGON32_LOGON_NETWORK, LOGON32_PROVIDER_DEFAULT,
        ctypes.byref(token),
    )
    if ok:
        ctypes.WinDLL("kernel32").CloseHandle(token)
        return
    err = ctypes.get_last_error()
    # 1326 = ERROR_LOGON_FAILURE（用户名或密码错误）
    raise RuntimeError(
        f"Windows 账户密码校验失败: {domain}\\{username}（错误码 {err}）。"
        "请确认账户名和密码正确后重试。"
    )


def _assert_no_space(path: str, what: str) -> None:
    """计划任务 /tr 路径不能含空格（schtasks 命令行解析限制）。"""
    if " " in path:
        raise RuntimeError(
            f"Windows {what} 路径不能包含空格: {path}\n"
            "请把状态目录放到不含空格的路径下。"
        )


def _render_broker_cmd(spec: PrivilegedUnitSpec) -> str:
    """生成 broker 启动脚本（开机时以 SYSTEM 运行，常驻监听关机请求）。"""
    lines: list[str] = ["@echo off"]
    lines.append(f"cd /d {_quote_cmd(spec.working_directory)}")
    if spec.environment:
        for key, value in sorted(spec.environment.items()):
            lines.append(f"set \"{key}={value}\"")
    cmd = " ".join([
        _quote_cmd(spec.python_executable),
        "-m",
        _quote_cmd(spec.broker_module),
        "--state-dir",
        _quote_cmd(spec.state_dir),
    ])
    log_dir = Path(spec.state_dir)
    stdout_log = _quote_cmd(str(log_dir / "broker.stdout.log"))
    stderr_log = _quote_cmd(str(log_dir / "broker.stderr.log"))
    lines.append(f"{cmd} 1>>{stdout_log} 2>>{stderr_log}")
    return "\r\n".join(lines) + "\r\n"


def _render_bootstrap_cmd(spec: UserServerUnitSpec) -> str:
    """生成 bootstrap 启动脚本（用户登录时运行，拉起 platalea server）。"""
    lines: list[str] = ["@echo off"]
    lines.append(f"cd /d {_quote_cmd(spec.working_directory)}")
    if spec.environment:
        for key, value in sorted(spec.environment.items()):
            lines.append(f"set \"{key}={value}\"")
    cmd = " ".join(_quote_cmd(arg) for arg in spec.program_arguments)
    lines.append(f"{cmd} 1>>{_quote_cmd(spec.stdout_path)} 2>>{_quote_cmd(spec.stderr_path)}")
    return "\r\n".join(lines) + "\r\n"


class WindowsServiceAdapter:
    """Windows 计划任务（schtasks）后端。

    broker（关机代理）：ONSTART + SYSTEM，开机常驻，监听 TCP localhost 关机请求。
    bootstrap（拉起 server）：ONLOGON + 当前用户，登录时以用户身份启动 platalea server。

    不使用 sc.exe 服务，因为 Windows 服务进程必须主动实现 SCM 协议（报告运行状态），
    普通 Python 脚本无法满足（会触发 1053 错误）；且普通用户账户缺少"作为服务登录"
    权限（SeServiceLogonRight）。计划任务原生支持后台进程与用户登录触发，无需这些。
    """

    def __init__(
        self,
        *,
        service_user: str | None = None,
        service_password: str | None = None,
        command_runner=subprocess.run,
    ) -> None:
        self._command_runner = command_runner
        self._service_user = _normalize_windows_user(service_user or "")
        self._service_password = service_password or ""

    def _run(
        self,
        cmd: list[str],
        *,
        allow_failure: bool = False,
        redacted_command: str | None = None,
    ) -> subprocess.CompletedProcess:
        proc = self._command_runner(cmd, check=False, capture_output=True, text=True)
        if proc.returncode != 0 and not allow_failure:
            detail = (proc.stderr or proc.stdout or "").strip() or "unknown error"
            shown = redacted_command or " ".join(cmd)
            raise RuntimeError(f"{shown} 失败: {detail}")
        return proc

    def install_privileged_unit(self, spec: PrivilegedUnitSpec) -> None:
        if not _is_admin():
            raise RuntimeError(_NOT_ADMIN_MESSAGE)
        layout = windows_layout()
        layout.broker_script.parent.mkdir(parents=True, exist_ok=True)
        layout.broker_script.write_text(_render_broker_cmd(spec), encoding="utf-8")
        _assert_no_space(str(layout.broker_script), "broker 脚本")
        # 删除旧任务（幂等）
        self._run(["schtasks", "/delete", "/tn", spec.label, "/f"], allow_failure=True)
        # 创建开机自启任务，以 SYSTEM 运行（SYSTEM 拥有关机权限 SeShutdownPrivilege）
        self._run([
            "schtasks", "/create",
            "/tn", spec.label,
            "/tr", str(layout.broker_script),
            "/sc", "ONSTART",
            "/ru", "SYSTEM",
            "/rl", "HIGHEST",
            "/f",
        ])
        # 立即启动 broker（让关机功能当场可用，无需重启）
        self._run(["schtasks", "/run", "/tn", spec.label], allow_failure=True)

    def install_user_server_unit(self, spec: UserServerUnitSpec) -> None:
        if not _is_admin():
            raise RuntimeError(_NOT_ADMIN_MESSAGE)
        if not self._service_user or not self._service_password:
            raise RuntimeError("windows 安装用户服务需要提供账户与密码")
        # 创建任务前主动校验密码（避免错误密码静默"成功"）
        _verify_windows_logon(self._service_user, self._service_password)
        layout = windows_layout()
        layout.bootstrap_script.parent.mkdir(parents=True, exist_ok=True)
        layout.bootstrap_script.write_text(_render_bootstrap_cmd(spec), encoding="utf-8")
        _assert_no_space(str(layout.bootstrap_script), "bootstrap 脚本")
        self._run(["schtasks", "/delete", "/tn", spec.label, "/f"], allow_failure=True)
        redacted_display = (
            f"schtasks /create /tn {spec.label} /tr {layout.bootstrap_script} "
            f"/sc ONLOGON /ru {self._service_user} /rp ****** /rl HIGHEST /f"
        )
        # 创建登录时触发的任务，以指定用户身份运行（在用户会话里访问其配置/证书）
        self._run([
            "schtasks", "/create",
            "/tn", spec.label,
            "/tr", str(layout.bootstrap_script),
            "/sc", "ONLOGON",
            "/ru", self._service_user,
            "/rp", self._service_password,
            "/rl", "HIGHEST",
            "/f",
        ], redacted_command=redacted_display)
        # 立即运行一次（拉起 platalea server）
        self._run(["schtasks", "/run", "/tn", spec.label], allow_failure=True)

    def uninstall_all_units(self, *, ignore_missing: bool = True) -> None:
        if not _is_admin():
            raise RuntimeError(_NOT_ADMIN_MESSAGE)
        layout = windows_layout()
        priv_del = self._run(
            ["schtasks", "/delete", "/tn", PRIVILEGED_LABEL, "/f"], allow_failure=True
        )
        boot_del = self._run(
            ["schtasks", "/delete", "/tn", BOOTSTRAP_LABEL, "/f"], allow_failure=True
        )
        # 兼容清理：移除旧版 sc.exe 服务残留（早期版本用 sc.exe 创建服务）
        for label in (PRIVILEGED_LABEL, BOOTSTRAP_LABEL):
            self._run(["sc.exe", "stop", label], allow_failure=True)
            self._run(["sc.exe", "delete", label], allow_failure=True)
        if not ignore_missing and priv_del.returncode != 0 and boot_del.returncode != 0:
            raise FileNotFoundError("已安装的计划任务均不存在")
        for script in (layout.broker_script, layout.bootstrap_script):
            if script.exists():
                script.unlink()
            elif not ignore_missing:
                raise FileNotFoundError(f"启动脚本不存在: {script}")

    def query_status(self) -> SupervisorStatus:
        priv = self._run(["schtasks", "/query", "/tn", PRIVILEGED_LABEL], allow_failure=True)
        boot = self._run(["schtasks", "/query", "/tn", BOOTSTRAP_LABEL], allow_failure=True)
        layout = windows_layout()
        installed = (
            priv.returncode == 0
            or boot.returncode == 0
            or layout.broker_script.exists()
            or layout.bootstrap_script.exists()
        )
        details = [
            f"broker_task_exists={'yes' if priv.returncode == 0 else 'no'}",
            f"bootstrap_task_exists={'yes' if boot.returncode == 0 else 'no'}",
            f"broker_script={layout.broker_script}",
            f"bootstrap_script={layout.bootstrap_script}",
            "backend=schtasks",
        ]
        return SupervisorStatus(
            platform="windows",
            privileged_unit=PRIVILEGED_LABEL,
            user_server_unit=BOOTSTRAP_LABEL,
            active_owner="",
            installed=installed,
            details=details,
        )

    def run_power_action(self, action: PowerAction) -> None:
        raise NotImplementedError(f"windows power action 尚未接入 supervisor adapter: {action.name}")
