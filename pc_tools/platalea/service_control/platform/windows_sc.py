from __future__ import annotations

import os
import subprocess
from dataclasses import dataclass
from pathlib import Path

from ...paths import service_control_paths
from ..models import PrivilegedUnitSpec, SupervisorStatus, UserServerUnitSpec
from .base import PowerAction

PRIVILEGED_LABEL = "com.localmanager.platalea.privileged"
BOOTSTRAP_LABEL = "com.localmanager.platalea.bootstrap"


@dataclass(frozen=True)
class WindowsLayout:
    bootstrap_script: Path


def windows_layout() -> WindowsLayout:
    root = service_control_paths().state_root
    return WindowsLayout(bootstrap_script=root / "bootstrap_start.cmd")


def _quote_cmd(value: str) -> str:
    return '"' + value.replace('"', '""') + '"'


def _normalize_windows_user(user: str) -> str:
    clean = user.strip()
    if not clean:
        return ""
    if "\\" in clean or "@" in clean:
        return clean
    return f".\\{clean}"


def _render_bootstrap_cmd(spec: UserServerUnitSpec) -> str:
    lines: list[str] = ["@echo off"]
    lines.append(f"cd /d {_quote_cmd(spec.working_directory)}")
    if spec.environment:
        for key, value in sorted(spec.environment.items()):
            lines.append(f"set \"{key}={value}\"")
    cmd = " ".join(_quote_cmd(arg) for arg in spec.program_arguments)
    lines.append(f"{cmd} 1>>{_quote_cmd(spec.stdout_path)} 2>>{_quote_cmd(spec.stderr_path)}")
    return "\r\n".join(lines) + "\r\n"


class WindowsServiceAdapter:
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
        cmd = f'"{spec.python_executable}" -m {spec.broker_module} --state-dir "{spec.state_dir}"'
        self._run(["sc.exe", "stop", spec.label], allow_failure=True)
        self._run(["sc.exe", "delete", spec.label], allow_failure=True)
        self._run([
            "sc.exe",
            "create",
            spec.label,
            f"binPath= {cmd}",
            "start= auto",
            "obj= LocalSystem",
        ])
        self._run(["sc.exe", "description", spec.label, "LocalManager Platalea privileged broker"])
        self._run(["sc.exe", "start", spec.label], allow_failure=True)

    def install_user_server_unit(self, spec: UserServerUnitSpec) -> None:
        layout = windows_layout()
        layout.bootstrap_script.parent.mkdir(parents=True, exist_ok=True)
        layout.bootstrap_script.write_text(_render_bootstrap_cmd(spec), encoding="utf-8")
        if not self._service_user or not self._service_password:
            raise RuntimeError("windows 安装用户服务需要提供账户与密码")

        self._run(["sc.exe", "stop", spec.label], allow_failure=True)
        self._run(["sc.exe", "delete", spec.label], allow_failure=True)
        bin_cmd = f'cmd.exe /d /c "{layout.bootstrap_script}"'
        self._run([
            "sc.exe",
            "create",
            spec.label,
            f"binPath= {bin_cmd}",
            "start= auto",
            f"obj= {self._service_user}",
            f"password= {self._service_password}",
        ], redacted_command=f"sc.exe create {spec.label} binPath= {bin_cmd} start= auto obj= {self._service_user} password= ******")
        self._run(["sc.exe", "description", spec.label, "LocalManager Platalea bootstrap user server"])
        self._run(["sc.exe", "start", spec.label], allow_failure=True)

    def uninstall_all_units(self, *, ignore_missing: bool = True) -> None:
        layout = windows_layout()
        self._run(["sc.exe", "stop", PRIVILEGED_LABEL], allow_failure=True)
        self._run(["sc.exe", "delete", PRIVILEGED_LABEL], allow_failure=True)
        bootstrap_del = self._run(["sc.exe", "stop", BOOTSTRAP_LABEL], allow_failure=True)
        self._run(["sc.exe", "delete", BOOTSTRAP_LABEL], allow_failure=True)
        if bootstrap_del.returncode != 0 and not ignore_missing:
            detail = (bootstrap_del.stderr or bootstrap_del.stdout or "").strip()
            raise FileNotFoundError(f"bootstrap 服务不存在: {detail}")
        if layout.bootstrap_script.exists():
            layout.bootstrap_script.unlink()
        elif not ignore_missing:
            raise FileNotFoundError(f"bootstrap 脚本不存在: {layout.bootstrap_script}")

    def query_status(self) -> SupervisorStatus:
        svc = self._run(["sc.exe", "query", PRIVILEGED_LABEL], allow_failure=True)
        bootstrap_svc = self._run(["sc.exe", "query", BOOTSTRAP_LABEL], allow_failure=True)
        layout = windows_layout()
        installed = svc.returncode == 0 or bootstrap_svc.returncode == 0 or layout.bootstrap_script.exists()
        details = [
            f"broker_service_exists={'yes' if svc.returncode == 0 else 'no'}",
            f"bootstrap_service_exists={'yes' if bootstrap_svc.returncode == 0 else 'no'}",
            f"bootstrap_script={layout.bootstrap_script}",
            "bootstrap_mode=runas_user_service",
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
