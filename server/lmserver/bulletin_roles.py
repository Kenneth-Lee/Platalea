from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .family_common import PASSWORD_HEADER

ADMIN_ROLE_ID = "admin"
LEGACY_GUEST_ROLE_ID = "guest"


@dataclass(frozen=True)
class RoleDefinition:
    role_id: str
    password: str | None
    label: str | None
    can_create_boards: bool
    can_manage_boards: bool

    @property
    def is_admin(self) -> bool:
        return self.role_id == ADMIN_ROLE_ID


@dataclass(frozen=True)
class AuthContext:
    role_id: str
    role_label: str | None
    is_admin: bool
    can_create_boards: bool
    can_manage_boards: bool

    @property
    def can_manage(self) -> bool:
        return self.is_admin or self.can_manage_boards


@dataclass(frozen=True)
class RolesConfig:
    roles: dict[str, RoleDefinition]

    @property
    def auth_required(self) -> bool:
        return any(role.password for role in self.roles.values())

    def resolve(self, headers: dict[str, str]) -> AuthContext | None:
        if not self.auth_required:
            admin = self.roles.get(ADMIN_ROLE_ID)
            if admin is None:
                return AuthContext(
                    role_id=ADMIN_ROLE_ID,
                    role_label=None,
                    is_admin=True,
                    can_create_boards=True,
                    can_manage_boards=True,
                )
            return _auth_from_role(admin)

        provided = headers.get(PASSWORD_HEADER, "").strip()
        if not provided:
            return None
        for role in self.roles.values():
            if role.password and provided == role.password:
                return _auth_from_role(role)
        return None


def _auth_from_role(role: RoleDefinition) -> AuthContext:
    return AuthContext(
        role_id=role.role_id,
        role_label=role.label,
        is_admin=role.is_admin,
        can_create_boards=role.can_create_boards,
        can_manage_boards=role.can_manage_boards,
    )


def load_roles_config(
    raw: dict[str, Any],
    *,
    guest_password: str | None,
    host_password: str | None,
) -> RolesConfig:
    roles_raw = raw.get("roles")
    if isinstance(roles_raw, dict) and roles_raw:
        return _parse_roles_config(roles_raw)
    return _legacy_roles_from_passwords(guest_password, host_password)


def _parse_roles_config(roles_raw: dict[str, Any]) -> RolesConfig:
    roles: dict[str, RoleDefinition] = {}
    for role_id, value in roles_raw.items():
        rid = str(role_id).strip()
        if not rid:
            continue
        if not isinstance(value, dict):
            raise ValueError(f"roles.{rid} 必须是对象")
        password = str(value.get("password", "")).strip() or None
        label = str(value.get("label", "")).strip() or None
        is_admin = rid == ADMIN_ROLE_ID
        can_create = bool(value.get("can_create_boards", is_admin))
        can_manage = bool(value.get("can_manage_boards", is_admin))
        roles[rid] = RoleDefinition(
            role_id=rid,
            password=password,
            label=label,
            can_create_boards=can_create,
            can_manage_boards=can_manage,
        )
    if ADMIN_ROLE_ID not in roles:
        raise ValueError(f"roles 必须包含 admin 角色（{ADMIN_ROLE_ID!r}）")
    _ensure_unique_passwords(roles)
    return RolesConfig(roles=roles)


def _legacy_roles_from_passwords(
    guest_password: str | None,
    host_password: str | None,
) -> RolesConfig:
    roles: dict[str, RoleDefinition] = {}
    if host_password:
        roles[ADMIN_ROLE_ID] = RoleDefinition(
            role_id=ADMIN_ROLE_ID,
            password=host_password,
            label="管理员",
            can_create_boards=True,
            can_manage_boards=True,
        )
    if guest_password:
        roles[LEGACY_GUEST_ROLE_ID] = RoleDefinition(
            role_id=LEGACY_GUEST_ROLE_ID,
            password=guest_password,
            label="访客",
            can_create_boards=False,
            can_manage_boards=False,
        )
    if not roles:
        roles[ADMIN_ROLE_ID] = RoleDefinition(
            role_id=ADMIN_ROLE_ID,
            password=None,
            label=None,
            can_create_boards=True,
            can_manage_boards=True,
        )
    _ensure_unique_passwords(roles)
    return RolesConfig(roles=roles)


def _ensure_unique_passwords(roles: dict[str, RoleDefinition]) -> None:
    seen: dict[str, str] = {}
    for role in roles.values():
        if not role.password:
            continue
        if role.password in seen:
            other = seen[role.password]
            raise ValueError(
                f"角色 {role.role_id!r} 与 {other!r} 使用了相同密码，请为每个角色设置独立密码"
            )
        seen[role.password] = role.role_id


def normalize_stored_role_ids(raw: Any) -> tuple[str, ...]:
    if not isinstance(raw, list):
        return ()
    result: list[str] = []
    for item in raw:
        role_id = str(item).strip()
        if not role_id or role_id == ADMIN_ROLE_ID:
            continue
        if role_id not in result:
            result.append(role_id)
    return tuple(result)


def board_role_ids_from_meta(meta: dict[str, Any]) -> tuple[str, ...] | None:
    if "role_ids" not in meta:
        return None
    return normalize_stored_role_ids(meta.get("role_ids"))


def can_access_board(auth: AuthContext, board_role_ids: tuple[str, ...] | None) -> bool:
    if auth.is_admin:
        return True
    if board_role_ids is None:
        return True
    return auth.role_id in board_role_ids


def auth_session_fields(auth: AuthContext) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "role_id": auth.role_id,
        "can_manage": auth.can_manage,
    }
    if auth.role_label:
        payload["role_label"] = auth.role_label
    return payload
