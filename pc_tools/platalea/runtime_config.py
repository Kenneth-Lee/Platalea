from __future__ import annotations

import json
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .bulletin_roles import RoleDefinition, RolesConfig, load_roles_config

RUNTIME_CONFIG_SCHEMA_VERSION = 1


@dataclass(frozen=True)
class RuntimeConfigChangeEvent:
    revision: int
    changed_domains: tuple[str, ...]
    changed_paths: tuple[str, ...]
    updated_at: int


@dataclass(frozen=True)
class RuntimeConfigPatchResult:
    revision: int
    updated_at: int
    changed_domains: tuple[str, ...]
    changed_paths: tuple[str, ...]
    roles: RolesConfig


@dataclass(frozen=True)
class RuntimeConfigConflictError(RuntimeError):
    server_revision: int
    server_snapshot: dict[str, Any]
    changed_paths: tuple[str, ...]

    def __str__(self) -> str:
        return f"revision_conflict: server_revision={self.server_revision}"


class RuntimeConfigRepository:
    def __init__(self, *, runtime_file: Path, base_roles: RolesConfig) -> None:
        self._runtime_file = runtime_file
        self._lock = threading.RLock()
        self._cond = threading.Condition(self._lock)
        self._changes: list[RuntimeConfigChangeEvent] = []
        self._revision = 0
        self._updated_at = int(time.time() * 1000)
        self._roles_raw = _roles_to_raw(base_roles)
        self._roles = base_roles
        self._load_runtime_overlay_if_exists()

    @property
    def runtime_file(self) -> Path:
        return self._runtime_file

    def current_roles(self) -> RolesConfig:
        with self._lock:
            return self._roles

    def snapshot_public(self) -> dict[str, Any]:
        with self._lock:
            return self._snapshot_public_locked()

    def wait_for_changes(self, *, since: int, timeout_ms: int) -> RuntimeConfigChangeEvent | None:
        timeout_sec = max(0.0, min(timeout_ms, 60_000) / 1000.0)
        with self._cond:
            event = self._aggregate_changes_since_locked(since)
            if event is not None:
                return event
            if timeout_sec <= 0.0:
                return None
            self._cond.wait(timeout=timeout_sec)
            return self._aggregate_changes_since_locked(since)

    def patch(
        self,
        *,
        expected_revision: int,
        payload: dict[str, Any],
    ) -> RuntimeConfigPatchResult:
        with self._cond:
            if expected_revision != self._revision:
                raise RuntimeConfigConflictError(
                    server_revision=self._revision,
                    server_snapshot=self._snapshot_public_locked(),
                    changed_paths=self._changed_paths_since_locked(expected_revision),
                )

            changed_paths = _extract_patch_paths(payload)
            if not changed_paths:
                raise ValueError("请求体缺少可更新字段")

            next_roles_raw = json.loads(json.dumps(self._roles_raw, ensure_ascii=False))
            _apply_roles_patch(next_roles_raw, payload)
            next_roles = load_roles_config({"roles": next_roles_raw})

            now = int(time.time() * 1000)
            self._roles_raw = next_roles_raw
            self._roles = next_roles
            self._revision += 1
            self._updated_at = now

            self._persist_locked()

            changed_domains = tuple(sorted({path.split(".", 1)[0] for path in changed_paths}))
            event = RuntimeConfigChangeEvent(
                revision=self._revision,
                changed_domains=changed_domains,
                changed_paths=changed_paths,
                updated_at=self._updated_at,
            )
            self._changes.append(event)
            if len(self._changes) > 200:
                self._changes = self._changes[-200:]
            self._cond.notify_all()

            return RuntimeConfigPatchResult(
                revision=self._revision,
                updated_at=self._updated_at,
                changed_domains=changed_domains,
                changed_paths=changed_paths,
                roles=self._roles,
            )

    def _load_runtime_overlay_if_exists(self) -> None:
        if not self._runtime_file.exists():
            return
        raw = json.loads(self._runtime_file.read_text(encoding="utf-8"))
        if not isinstance(raw, dict):
            raise ValueError("runtime_config.json 根节点必须是对象")
        revision = int(raw.get("revision", 0))
        updated_at = int(raw.get("updated_at", self._updated_at))
        domains = raw.get("domains")
        if not isinstance(domains, dict):
            raise ValueError("runtime_config.json 缺少 domains")
        auth = domains.get("auth")
        if not isinstance(auth, dict):
            raise ValueError("runtime_config.json 缺少 domains.auth")
        roles = auth.get("roles")
        if not isinstance(roles, dict):
            raise ValueError("runtime_config.json 缺少 domains.auth.roles")
        loaded_roles = load_roles_config({"roles": roles})
        self._roles = loaded_roles
        self._roles_raw = _roles_to_raw(loaded_roles)
        self._revision = max(0, revision)
        self._updated_at = updated_at

    def _persist_locked(self) -> None:
        self._runtime_file.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "schema_version": RUNTIME_CONFIG_SCHEMA_VERSION,
            "revision": self._revision,
            "updated_at": self._updated_at,
            "domains": {
                "auth": {
                    "roles": self._roles_raw,
                }
            },
        }
        self._runtime_file.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    def _snapshot_public_locked(self) -> dict[str, Any]:
        return {
            "ok": True,
            "revision": self._revision,
            "updated_at": self._updated_at,
            "domains": {
                "auth": {
                    "roles": {
                        role_id: {
                            "label": role.label,
                            "can_create_boards": role.can_create_boards,
                            "can_manage_boards": role.can_manage_boards,
                            "allowed_agent_models": list(role.allowed_agent_models)
                            if role.allowed_agent_models is not None
                            else None,
                            "password_set": bool(role.password),
                        }
                        for role_id, role in sorted(self._roles.roles.items())
                    }
                }
            },
        }

    def _aggregate_changes_since_locked(self, since: int) -> RuntimeConfigChangeEvent | None:
        if since < 0:
            since = 0
        recent = [event for event in self._changes if event.revision > since]
        if not recent:
            if since < self._revision:
                # 进程重启后历史可能丢失，返回一个保守的全域变更提示，
                # 让客户端主动刷新完整快照。
                return RuntimeConfigChangeEvent(
                    revision=self._revision,
                    changed_domains=("auth",),
                    changed_paths=("auth",),
                    updated_at=self._updated_at,
                )
            return None
        paths: set[str] = set()
        domains: set[str] = set()
        for event in recent:
            paths.update(event.changed_paths)
            domains.update(event.changed_domains)
        latest = recent[-1]
        return RuntimeConfigChangeEvent(
            revision=latest.revision,
            changed_domains=tuple(sorted(domains)),
            changed_paths=tuple(sorted(paths)),
            updated_at=latest.updated_at,
        )

    def _changed_paths_since_locked(self, since: int) -> tuple[str, ...]:
        event = self._aggregate_changes_since_locked(since)
        if event is None:
            return ()
        return event.changed_paths


def _roles_to_raw(roles: RolesConfig) -> dict[str, dict[str, Any]]:
    payload: dict[str, dict[str, Any]] = {}
    for role_id, role in roles.roles.items():
        payload[role_id] = _role_to_raw(role)
    return payload


def _role_to_raw(role: RoleDefinition) -> dict[str, Any]:
    return {
        "password": role.password or "",
        "label": role.label or "",
        "can_create_boards": role.can_create_boards,
        "can_manage_boards": role.can_manage_boards,
        "allowed_agent_models": list(role.allowed_agent_models)
        if role.allowed_agent_models is not None
        else None,
    }


def _extract_patch_paths(payload: dict[str, Any]) -> tuple[str, ...]:
    auth = payload.get("auth")
    if not isinstance(auth, dict):
        return ()
    roles = auth.get("roles")
    if not isinstance(roles, dict) or not roles:
        return ()
    paths = [f"auth.roles.{str(role_id).strip()}" for role_id in roles.keys() if str(role_id).strip()]
    return tuple(sorted(set(paths)))


def _apply_roles_patch(next_roles_raw: dict[str, dict[str, Any]], payload: dict[str, Any]) -> None:
    auth = payload.get("auth")
    if not isinstance(auth, dict):
        return
    roles_patch = auth.get("roles")
    if not isinstance(roles_patch, dict):
        return

    for raw_role_id, patch_value in roles_patch.items():
        role_id = str(raw_role_id).strip()
        if not role_id:
            raise ValueError("role_id 不能为空")
        if patch_value is None:
            if role_id == "admin":
                raise ValueError("不能删除 admin 角色")
            next_roles_raw.pop(role_id, None)
            continue
        if not isinstance(patch_value, dict):
            raise ValueError(f"auth.roles.{role_id} 必须是对象或 null")

        existing = dict(next_roles_raw.get(role_id, {}))
        for field in ("label", "password", "can_create_boards", "can_manage_boards", "allowed_agent_models"):
            if field not in patch_value:
                continue
            value = patch_value[field]
            if field in {"label", "password"}:
                existing[field] = str(value).strip()
            elif field == "allowed_agent_models":
                if value is None:
                    existing[field] = None
                elif isinstance(value, list):
                    normalized: list[str] = []
                    for item in value:
                        name = str(item).strip()
                        if not name:
                            continue
                        if name not in normalized:
                            normalized.append(name)
                    existing[field] = normalized
                elif isinstance(value, str):
                    name = value.strip()
                    existing[field] = [name] if name else []
                else:
                    raise ValueError(f"auth.roles.{role_id}.allowed_agent_models 必须是字符串数组、字符串或 null")
            else:
                existing[field] = bool(value)

        if "password" not in existing:
            existing["password"] = ""
        if "label" not in existing:
            existing["label"] = ""
        if "can_create_boards" not in existing:
            existing["can_create_boards"] = role_id == "admin"
        if "can_manage_boards" not in existing:
            existing["can_manage_boards"] = role_id == "admin"
        if "allowed_agent_models" not in existing:
            existing["allowed_agent_models"] = None

        next_roles_raw[role_id] = existing
