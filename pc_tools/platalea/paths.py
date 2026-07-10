"""Cross-platform paths under the user's home directory (~/.localmanager)."""
from __future__ import annotations

import json
import os
import platform
import shutil
from pathlib import Path

from dataclasses import dataclass

APP_DIR_NAME = ".localmanager"
CONFIG_NAME = "config.json"
PID_NAME = "server.pid"
LOG_NAME = "server.log"
INSTANCE_ID_NAME = "instance_id"
BOARDS_DIR_NAME = "boards"
TLS_DIR_NAME = "tls"
GNUPG_DIR_NAME = "gnupg"
IMPORTED_DIR_NAME = "imported"
SERVICE_CONTROL_DIR_NAME = "service_control"

PACKAGE_DIR = Path(__file__).resolve().parent
PC_TOOLS_DIR = PACKAGE_DIR.parent
REPO_ROOT = PC_TOOLS_DIR.parent
EXAMPLE_CONFIG = PC_TOOLS_DIR / "config.example.json"


def app_dir() -> Path:
    return Path.home() / APP_DIR_NAME


def config_path(explicit: str | Path | None = None) -> Path:
    if explicit:
        return Path(explicit).expanduser().resolve()
    return app_dir() / CONFIG_NAME


def boards_dir(config_file: Path | None = None) -> Path:
    cfg = config_file or config_path()
    if cfg.exists():
        try:
            raw = json.loads(cfg.read_text(encoding="utf-8"))
            if isinstance(raw, dict) and raw.get("board_root"):
                root = Path(str(raw["board_root"])).expanduser()
                if not root.is_absolute():
                    root = cfg.parent / root
                return root.resolve()
        except (OSError, json.JSONDecodeError):
            pass
    return app_dir() / BOARDS_DIR_NAME


def tls_dir(config_file: Path | None = None) -> Path:
    cfg = config_file or config_path()
    if cfg.exists():
        try:
            raw = json.loads(cfg.read_text(encoding="utf-8"))
            if isinstance(raw, dict) and raw.get("cert_file"):
                cert = Path(str(raw["cert_file"])).expanduser()
                if not cert.is_absolute():
                    cert = cfg.parent / cert
                return cert.parent.resolve()
        except (OSError, json.JSONDecodeError):
            pass
    return app_dir() / TLS_DIR_NAME


def default_ca_cert(config_file: Path | None = None) -> Path:
    return tls_dir(config_file) / "ca_cert.pem"


def pid_file() -> Path:
    return app_dir() / PID_NAME


def log_file() -> Path:
    return app_dir() / LOG_NAME


def gnupg_dir() -> Path:
    return app_dir() / GNUPG_DIR_NAME


def imported_dir() -> Path:
    return app_dir() / IMPORTED_DIR_NAME


@dataclass(frozen=True)
class ServiceControlPaths:
    state_root: Path
    system_units_dir: Path
    state_file: Path
    logs_dir: Path


def service_control_paths() -> ServiceControlPaths:
    override = os.environ.get("PLATALEA_SERVICE_CONTROL_ROOT", "").strip()
    if override:
        root = Path(override).expanduser().resolve()
    else:
        current = platform.system().lower()
        if current == "darwin":
            root = Path("/Library/LocalManager") / SERVICE_CONTROL_DIR_NAME
        else:
            root = app_dir() / SERVICE_CONTROL_DIR_NAME
    return ServiceControlPaths(
        state_root=root,
        system_units_dir=root / "system_units",
        state_file=root / "state.json",
        logs_dir=root / "logs",
    )


def ensure_app_layout() -> Path:
    root = app_dir()
    root.mkdir(parents=True, exist_ok=True)
    (root / BOARDS_DIR_NAME).mkdir(parents=True, exist_ok=True)
    (root / TLS_DIR_NAME).mkdir(parents=True, exist_ok=True)
    (root / GNUPG_DIR_NAME).mkdir(parents=True, exist_ok=True)
    (root / IMPORTED_DIR_NAME).mkdir(parents=True, exist_ok=True)
    (root / SERVICE_CONTROL_DIR_NAME).mkdir(parents=True, exist_ok=True)
    return root


def default_config_dict() -> dict:
    return {
        "listen_host": "0.0.0.0",
        "port": 8765,
        "service_name": "",
        "hostname": "",
        "service_type": "_localmanager._tcp.local.",
        "board_root": BOARDS_DIR_NAME,
        "guest_password": "guest",
        "host_password": "host",
        "supports_power_shutdown": True,
        "default_board": "default",
        "max_import_bytes": 524288000,
        "cert_file": f"{TLS_DIR_NAME}/pc_server_cert.pem",
        "key_file": f"{TLS_DIR_NAME}/pc_server_key.pem",
        "instance_id_file": INSTANCE_ID_NAME,
        "log_level": "INFO",
        "agent": {
            "enabled": False,
            "board_ids": None,
            "models": ["qwen2.5", "gpt-oss:latest"],
            "ollama_base_url": "http://127.0.0.1:11434",
            "max_board_context_chars": 12000,
            "tools": {
                "enabled": True,
                "attachments": True,
                "web_fetch": True,
                "max_attachment_read_bytes": 100000,
                "max_web_fetch_bytes": 200000,
                "web_fetch_timeout_seconds": 30,
                "max_tool_rounds": 10,
                "status_heartbeat_seconds": 15,
            },
        },
    }


def ensure_config(explicit: str | Path | None = None) -> Path:
    path = config_path(explicit)
    if path.exists():
        return path
    ensure_app_layout()
    if EXAMPLE_CONFIG.exists():
        shutil.copy(EXAMPLE_CONFIG, path)
        raw = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(raw, dict):
            raw.pop("roles_example", None)
            raw["board_root"] = BOARDS_DIR_NAME
            raw["cert_file"] = f"{TLS_DIR_NAME}/pc_server_cert.pem"
            raw["key_file"] = f"{TLS_DIR_NAME}/pc_server_key.pem"
            raw["instance_id_file"] = INSTANCE_ID_NAME
            path.write_text(json.dumps(raw, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    else:
        path.write_text(
            json.dumps(default_config_dict(), ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return path


def dev_repo_tls_dir() -> Path:
    return PC_TOOLS_DIR / "tls"
