from __future__ import annotations

import getpass
import os
import platform
import pwd
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class ActiveOwner:
    uid: int
    username: str
    home: str


@dataclass(frozen=True)
class ControlState:
    schema_version: int
    active_owner: ActiveOwner
    installed_at: int
    platform: str
    service_revision: int
    managed_units: dict[str, str]

    def to_json_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_json_dict(cls, raw: dict[str, Any]) -> "ControlState":
        owner_raw = raw.get("active_owner") or {}
        owner = ActiveOwner(
            uid=int(owner_raw.get("uid", -1)),
            username=str(owner_raw.get("username", "")).strip(),
            home=str(owner_raw.get("home", "")).strip(),
        )
        return cls(
            schema_version=int(raw.get("schema_version", 1)),
            active_owner=owner,
            installed_at=int(raw.get("installed_at", 0)),
            platform=str(raw.get("platform", "")).strip(),
            service_revision=int(raw.get("service_revision", 1)),
            managed_units=dict(raw.get("managed_units") or {}),
        )


@dataclass(frozen=True)
class UserServerUnitSpec:
    label: str
    owner: ActiveOwner
    program_arguments: list[str]
    working_directory: str
    stdout_path: str
    stderr_path: str


@dataclass(frozen=True)
class PrivilegedUnitSpec:
    label: str
    broker_module: str
    state_dir: str


@dataclass(frozen=True)
class SupervisorStatus:
    platform: str
    privileged_unit: str
    user_server_unit: str
    active_owner: str
    installed: bool
    details: list[str]


@dataclass(frozen=True)
class InstallPlan:
    owner: ActiveOwner
    replaced_previous_owner: bool
    previous_owner: ActiveOwner | None
    user_server_spec: UserServerUnitSpec
    privileged_spec: PrivilegedUnitSpec
    control_state: ControlState


def detect_active_owner() -> ActiveOwner:
    if os.geteuid() == 0:
        sudo_uid = os.environ.get("SUDO_UID", "").strip()
        sudo_user = os.environ.get("SUDO_USER", "").strip()
        if sudo_uid and sudo_user:
            uid = int(sudo_uid)
            try:
                ent = pwd.getpwuid(uid)
                home = ent.pw_dir
                username = ent.pw_name
            except KeyError:
                home = os.environ.get("SUDO_HOME", "").strip() or str(Path.home().resolve())
                username = sudo_user
            return ActiveOwner(uid=uid, username=username, home=home)

    username = getpass.getuser().strip()
    home = str(Path.home().resolve())
    return ActiveOwner(uid=os.getuid(), username=username, home=home)


def build_control_state(
    *,
    owner: ActiveOwner,
    privileged_label: str,
    user_server_label: str,
    revision: int = 1,
) -> ControlState:
    return ControlState(
        schema_version=1,
        active_owner=owner,
        installed_at=int(time.time()),
        platform=platform.system().lower(),
        service_revision=revision,
        managed_units={
            "privileged": privileged_label,
            "user_server": user_server_label,
        },
    )
