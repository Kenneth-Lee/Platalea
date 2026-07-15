from __future__ import annotations

import os
import platform
import shlex
import subprocess
from dataclasses import dataclass
from pathlib import Path

from ...paths import service_control_paths
from ..models import PrivilegedUnitSpec, SupervisorStatus, UserServerUnitSpec
from .base import PowerAction

PRIVILEGED_LABEL = "com.localmanager.platalea.privileged"
BOOTSTRAP_LABEL = "com.localmanager.platalea.bootstrap"


@dataclass(frozen=True)
class SystemdLayout:
    privileged_unit: Path
    bootstrap_unit: Path


def systemd_units_dir() -> Path:
    override = os.environ.get("PLATALEA_SYSTEMD_DIR", "").strip()
    if override:
        return Path(override).expanduser().resolve()
    if platform.system().lower() == "linux":
        return Path("/etc/systemd/system")
    return service_control_paths().system_units_dir


def systemd_layout() -> SystemdLayout:
    units_dir = systemd_units_dir()
    return SystemdLayout(
        privileged_unit=units_dir / f"{PRIVILEGED_LABEL}.service",
        bootstrap_unit=units_dir / f"{BOOTSTRAP_LABEL}.service",
    )


def _render_environment(env: dict[str, str] | None) -> str:
    if not env:
        return ""
    lines: list[str] = []
    for key, value in sorted(env.items()):
        lines.append(f'Environment="{key}={value}"')
    return "\n".join(lines)


def render_privileged_unit(spec: PrivilegedUnitSpec) -> str:
    env_block = _render_environment(spec.environment)
    env_line = f"{env_block}\n" if env_block else ""
    exec_cmd = " ".join(
        [
            shlex.quote(spec.python_executable),
            "-m",
            shlex.quote(spec.broker_module),
            "--state-dir",
            shlex.quote(spec.state_dir),
        ]
    )
    return (
        "[Unit]\n"
        "Description=LocalManager Platalea privileged broker\n"
        "After=network.target\n\n"
        "[Service]\n"
        "Type=simple\n"
        f"WorkingDirectory={spec.working_directory}\n"
        f"{env_line}"
        f"ExecStart={exec_cmd}\n"
        "Restart=always\n"
        "RestartSec=2\n\n"
        "[Install]\n"
        "WantedBy=multi-user.target\n"
    )


def render_bootstrap_unit(spec: UserServerUnitSpec) -> str:
    env_block = _render_environment(spec.environment)
    env_line = f"{env_block}\n" if env_block else ""
    exec_cmd = " ".join(shlex.quote(arg) for arg in spec.program_arguments)
    return (
        "[Unit]\n"
        "Description=LocalManager Platalea bootstrap user server\n"
        "After=network-online.target\n"
        "Wants=network-online.target\n\n"
        "[Service]\n"
        "Type=oneshot\n"
        f"User={spec.owner.username}\n"
        f"WorkingDirectory={spec.working_directory}\n"
        f"{env_line}"
        f"ExecStart={exec_cmd}\n"
        "RemainAfterExit=no\n"
        "Restart=on-failure\n"
        "RestartSec=10\n\n"
        "[Install]\n"
        "WantedBy=multi-user.target\n"
    )


class LinuxSystemdAdapter:
    def __init__(self, *, command_runner=subprocess.run) -> None:
        self._command_runner = command_runner

    def _run_systemctl(self, *args: str, allow_failure: bool = False) -> subprocess.CompletedProcess:
        proc = self._command_runner(
            ["systemctl", *args],
            check=False,
            capture_output=True,
            text=True,
        )
        if proc.returncode != 0 and not allow_failure:
            detail = (proc.stderr or proc.stdout or "").strip() or "unknown systemctl error"
            raise RuntimeError(f"systemctl {' '.join(args)} 失败: {detail}")
        return proc

    def install_privileged_unit(self, spec: PrivilegedUnitSpec) -> None:
        layout = systemd_layout()
        layout.privileged_unit.parent.mkdir(parents=True, exist_ok=True)
        layout.privileged_unit.write_text(render_privileged_unit(spec), encoding="utf-8")
        self._run_systemctl("daemon-reload")
        self._run_systemctl("enable", "--now", f"{spec.label}.service")

    def install_user_server_unit(self, spec: UserServerUnitSpec) -> None:
        layout = systemd_layout()
        layout.bootstrap_unit.parent.mkdir(parents=True, exist_ok=True)
        layout.bootstrap_unit.write_text(render_bootstrap_unit(spec), encoding="utf-8")
        self._run_systemctl("daemon-reload")
        self._run_systemctl("enable", "--now", f"{spec.label}.service")

    def uninstall_all_units(self, *, ignore_missing: bool = True) -> None:
        layout = systemd_layout()
        units = (
            (layout.privileged_unit, f"{PRIVILEGED_LABEL}.service"),
            (layout.bootstrap_unit, f"{BOOTSTRAP_LABEL}.service"),
        )
        for unit_path, unit_name in units:
            self._run_systemctl("disable", "--now", unit_name, allow_failure=True)
            if unit_path.exists():
                unit_path.unlink()
            elif not ignore_missing:
                raise FileNotFoundError(f"systemd unit 不存在: {unit_path}")
        self._run_systemctl("daemon-reload", allow_failure=True)

    def query_status(self) -> SupervisorStatus:
        layout = systemd_layout()
        priv_enabled = self._run_systemctl("is-enabled", f"{PRIVILEGED_LABEL}.service", allow_failure=True).returncode == 0
        boot_enabled = self._run_systemctl("is-enabled", f"{BOOTSTRAP_LABEL}.service", allow_failure=True).returncode == 0
        priv_active = self._run_systemctl("is-active", f"{PRIVILEGED_LABEL}.service", allow_failure=True).returncode == 0
        boot_active = self._run_systemctl("is-active", f"{BOOTSTRAP_LABEL}.service", allow_failure=True).returncode == 0
        installed = (
            layout.privileged_unit.exists()
            or layout.bootstrap_unit.exists()
            or priv_enabled
            or boot_enabled
            or priv_active
            or boot_active
        )
        details = [
            f"privileged_unit_file={layout.privileged_unit}",
            f"bootstrap_unit_file={layout.bootstrap_unit}",
            f"privileged_enabled={'yes' if priv_enabled else 'no'}",
            f"bootstrap_enabled={'yes' if boot_enabled else 'no'}",
            f"privileged_active={'yes' if priv_active else 'no'}",
            f"bootstrap_active={'yes' if boot_active else 'no'}",
        ]
        return SupervisorStatus(
            platform="linux",
            privileged_unit=PRIVILEGED_LABEL,
            user_server_unit=BOOTSTRAP_LABEL,
            active_owner="",
            installed=installed,
            details=details,
        )

    def run_power_action(self, action: PowerAction) -> None:
        raise NotImplementedError(f"linux power action 尚未接入 supervisor adapter: {action.name}")
