from __future__ import annotations

import os
import platform
import plistlib
import subprocess
from dataclasses import dataclass
from pathlib import Path

from ...paths import service_control_paths
from ..models import PrivilegedUnitSpec, SupervisorStatus, UserServerUnitSpec
from .base import PowerAction

PRIVILEGED_LABEL = "com.localmanager.platalea.privileged"
USER_SERVER_LABEL = "com.localmanager.platalea.server"


@dataclass(frozen=True)
class LaunchdLayout:
    privileged_plist: Path
    user_server_plist: Path


def launchd_units_dir() -> Path:
    override = os.environ.get("PLATALEA_LAUNCHD_DIR", "").strip()
    if override:
        return Path(override).expanduser().resolve()
    if platform.system().lower() == "darwin":
        return Path("/Library/LaunchDaemons")
    return service_control_paths().system_units_dir


def launchd_layout() -> LaunchdLayout:
    units_dir = launchd_units_dir()
    return LaunchdLayout(
        privileged_plist=units_dir / f"{PRIVILEGED_LABEL}.plist",
        user_server_plist=units_dir / f"{USER_SERVER_LABEL}.plist",
    )


def render_privileged_plist(spec: PrivilegedUnitSpec) -> bytes:
    payload = {
        "Label": spec.label,
        "ProgramArguments": [
            "/usr/bin/python3",
            "-m",
            spec.broker_module,
            "--state-dir",
            spec.state_dir,
        ],
        "RunAtLoad": True,
        "KeepAlive": True,
        "StandardOutPath": f"{spec.state_dir}/privileged.stdout.log",
        "StandardErrorPath": f"{spec.state_dir}/privileged.stderr.log",
    }
    return plistlib.dumps(payload, fmt=plistlib.FMT_XML)


def render_user_server_plist(spec: UserServerUnitSpec) -> bytes:
    payload = {
        "Label": spec.label,
        "UserName": spec.owner.username,
        "ProgramArguments": spec.program_arguments,
        "WorkingDirectory": spec.working_directory,
        "RunAtLoad": True,
        "KeepAlive": True,
        "StandardOutPath": spec.stdout_path,
        "StandardErrorPath": spec.stderr_path,
    }
    return plistlib.dumps(payload, fmt=plistlib.FMT_XML)


class MacOSLaunchdAdapter:
    def __init__(self, *, command_runner=subprocess.run) -> None:
        self._command_runner = command_runner

    def _run_launchctl(self, *args: str, allow_failure: bool = False) -> subprocess.CompletedProcess:
        proc = self._command_runner(
            ["/bin/launchctl", *args],
            check=False,
            capture_output=True,
            text=True,
        )
        if proc.returncode != 0 and not allow_failure:
            stderr = (proc.stderr or "").strip()
            stdout = (proc.stdout or "").strip()
            detail = stderr or stdout or "unknown launchctl error"
            raise RuntimeError(f"launchctl {' '.join(args)} 失败: {detail}")
        return proc

    def _load_unit(self, plist: Path, label: str) -> None:
        target = f"system/{label}"
        self._run_launchctl("bootout", target, allow_failure=True)
        self._run_launchctl("bootstrap", "system", str(plist))
        self._run_launchctl("enable", target, allow_failure=True)
        self._run_launchctl("kickstart", "-k", target)

    def _unload_unit(self, plist: Path, label: str) -> None:
        target = f"system/{label}"
        self._run_launchctl("bootout", target, allow_failure=True)
        self._run_launchctl("bootout", "system", str(plist), allow_failure=True)

    def install_privileged_unit(self, spec: PrivilegedUnitSpec) -> None:
        layout = launchd_layout()
        layout.privileged_plist.parent.mkdir(parents=True, exist_ok=True)
        layout.privileged_plist.write_bytes(render_privileged_plist(spec))
        self._load_unit(layout.privileged_plist, spec.label)

    def install_user_server_unit(self, spec: UserServerUnitSpec) -> None:
        layout = launchd_layout()
        layout.user_server_plist.parent.mkdir(parents=True, exist_ok=True)
        layout.user_server_plist.write_bytes(render_user_server_plist(spec))
        self._load_unit(layout.user_server_plist, spec.label)

    def uninstall_all_units(self, *, ignore_missing: bool = True) -> None:
        layout = launchd_layout()
        units = (
            (layout.privileged_plist, PRIVILEGED_LABEL),
            (layout.user_server_plist, USER_SERVER_LABEL),
        )
        for path, label in units:
            self._unload_unit(path, label)
            if path.exists():
                path.unlink()
            elif not ignore_missing:
                raise FileNotFoundError(f"launchd plist 不存在: {path}")

    def _is_loaded(self, label: str) -> bool:
        proc = self._run_launchctl("print", f"system/{label}", allow_failure=True)
        return proc.returncode == 0

    def query_status(self) -> SupervisorStatus:
        layout = launchd_layout()
        privileged_loaded = self._is_loaded(PRIVILEGED_LABEL)
        user_loaded = self._is_loaded(USER_SERVER_LABEL)
        installed = (
            layout.privileged_plist.exists()
            or layout.user_server_plist.exists()
            or privileged_loaded
            or user_loaded
        )
        details = []
        details.append(f"privileged_plist={layout.privileged_plist}")
        details.append(f"user_server_plist={layout.user_server_plist}")
        details.append(f"privileged_loaded={'yes' if privileged_loaded else 'no'}")
        details.append(f"user_server_loaded={'yes' if user_loaded else 'no'}")
        return SupervisorStatus(
            platform="macos",
            privileged_unit=PRIVILEGED_LABEL,
            user_server_unit=USER_SERVER_LABEL,
            active_owner="",
            installed=installed,
            details=details,
        )

    def run_power_action(self, action: PowerAction) -> None:
        raise NotImplementedError(
            f"macOS power action 尚未接入特权 broker: {action.name}"
        )
