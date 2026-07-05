"""Import mobile-exported configuration JSON into PC ~/.localmanager layout."""
from __future__ import annotations

import argparse
import base64
import binascii
import json
import shutil
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .paths import app_dir, config_path, ensure_app_layout, gnupg_dir, imported_dir

# Keys aligned with app/src/main/java/.../ConfigExport.kt
KEY_CONFIG_VERSION = "config_version"
KEY_FAMILY_NETWORK_USER_NAME = "family_network_user_name"
KEY_FAMILY_NETWORK_HOST_NAME = "family_network_host_name"
KEY_GIT_REPO_URL = "git_repo_url"
KEY_GIT_USER_NAME = "git_user_name"
KEY_GIT_USER_EMAIL = "git_user_email"
KEY_GIT_HTTPS_PASSWORD = "git_https_password"
KEY_GIT_CONFIG_APPLIED = "git_config_applied"
KEY_GPG_PUBLIC_KEYS_BASE64 = "gpg_public_keys_base64"
KEY_GPG_SECRET_KEYS_BASE64 = "gpg_secret_keys_base64"
KEY_PLAYER_PLAYLISTS = "player_playlists"
KEY_PLAYER_LIST_BOOKMARKS = "player_list_bookmarks"
KEY_PLAYER_PLAYLIST_RESUMES = "player_playlist_resumes"
KEY_RECENT_OPEN_ITEMS = "recent_open_items"

CATEGORY_GPG = "gpg"
CATEGORY_GIT = "git"
CATEGORY_MUSIC = "music"
CATEGORY_RECENT = "recent"
CATEGORY_EPUB = "epub"
CATEGORY_OTHER = "other"

ALL_CATEGORIES = frozenset({
    CATEGORY_GPG,
    CATEGORY_GIT,
    CATEGORY_MUSIC,
    CATEGORY_RECENT,
    CATEGORY_EPUB,
    CATEGORY_OTHER,
})

GIT_KEYS = (
    KEY_GIT_REPO_URL,
    KEY_GIT_USER_NAME,
    KEY_GIT_USER_EMAIL,
    KEY_GIT_HTTPS_PASSWORD,
    KEY_GIT_CONFIG_APPLIED,
)
MUSIC_KEYS = (
    KEY_PLAYER_PLAYLISTS,
    KEY_PLAYER_LIST_BOOKMARKS,
    KEY_PLAYER_PLAYLIST_RESUMES,
    "player_list_bookmark",
)
EPUB_KEYS = (
    "epub_dict_area_expanded",
    "epub_dict_lookup_words",
    "epub_tts_engine_package",
    "epub_tts_voice_name",
    "epub_tts_speed_percent",
    "epub_tts_auto_next_chapter",
)
OTHER_KEYS = (
    "filter_visible",
    "hide_dot_files",
    "viewer_preview_bytes",
    "startup_decrypt_key",
    "ftp_port",
    "ftp_password",
    "ftp_timeout_minutes",
    "local_network_service_enabled",
    KEY_FAMILY_NETWORK_USER_NAME,
    KEY_FAMILY_NETWORK_HOST_NAME,
    "external_open_by_extension",
    "root_bookmarks",
)


class ConfigImportError(RuntimeError):
    pass


@dataclass
class ImportReport:
    actions: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    categories: set[str] = field(default_factory=set)


def load_mobile_config(path: Path) -> dict[str, Any]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise ConfigImportError(f"无法读取配置文件: {path}: {exc}") from exc
    except json.JSONDecodeError as exc:
        raise ConfigImportError(f"JSON 解析失败: {exc}") from exc
    if not isinstance(raw, dict):
        raise ConfigImportError("配置文件根节点必须是 JSON 对象")
    return raw


def detect_categories(obj: dict[str, Any]) -> set[str]:
    found: set[str] = set()
    if any(obj.get(key) not in (None, "") for key in GIT_KEYS):
        found.add(CATEGORY_GIT)
    if any(key in obj for key in MUSIC_KEYS):
        found.add(CATEGORY_MUSIC)
    if KEY_RECENT_OPEN_ITEMS in obj:
        found.add(CATEGORY_RECENT)
    if any(key in obj for key in EPUB_KEYS):
        found.add(CATEGORY_EPUB)
    if KEY_GPG_PUBLIC_KEYS_BASE64 in obj or KEY_GPG_SECRET_KEYS_BASE64 in obj:
        found.add(CATEGORY_GPG)
    if any(key in obj for key in OTHER_KEYS):
        found.add(CATEGORY_OTHER)
    return found


def contains_gpg_keys(obj: dict[str, Any]) -> bool:
    return KEY_GPG_PUBLIC_KEYS_BASE64 in obj or KEY_GPG_SECRET_KEYS_BASE64 in obj


def _backup_if_exists(path: Path) -> None:
    if not path.is_file():
        return
    stamp = time.strftime("%Y%m%d-%H%M%S")
    backup = path.with_name(path.name + f".bak.{stamp}")
    shutil.copy2(path, backup)


def _write_key_file(target: Path, b64_value: str, label: str) -> None:
    try:
        data = base64.b64decode(b64_value, validate=True)
    except (ValueError, binascii.Error) as exc:
        raise ConfigImportError(f"{label} Base64 解码失败") from exc
    if not data:
        raise ConfigImportError(f"{label} 为空")
    _backup_if_exists(target)
    target.write_bytes(data)


def _import_gpg(obj: dict[str, Any], report: ImportReport) -> None:
    key_dir = gnupg_dir()
    key_dir.mkdir(parents=True, exist_ok=True)
    if KEY_GPG_PUBLIC_KEYS_BASE64 in obj:
        pub = key_dir / "pubring.gpg"
        _write_key_file(pub, str(obj[KEY_GPG_PUBLIC_KEYS_BASE64]), "公钥")
        report.actions.append(f"已写入公钥: {pub}")
    if KEY_GPG_SECRET_KEYS_BASE64 in obj:
        sec = key_dir / "secring.gpg"
        _write_key_file(sec, str(obj[KEY_GPG_SECRET_KEYS_BASE64]), "私钥")
        report.actions.append(f"已写入私钥: {sec}")


def _extract_subset(obj: dict[str, Any], keys: tuple[str, ...]) -> dict[str, Any]:
    return {key: obj[key] for key in keys if key in obj}


def _write_json_subset(
    obj: dict[str, Any],
    keys: tuple[str, ...],
    target: Path,
    report: ImportReport,
    label: str,
) -> None:
    subset = _extract_subset(obj, keys)
    if not subset:
        return
    _backup_if_exists(target)
    target.write_text(json.dumps(subset, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report.actions.append(f"已写入{label}: {target}")


def _patch_server_config(obj: dict[str, Any], report: ImportReport, cfg_file: Path) -> None:
    mobile_meta: dict[str, Any] = {}

    if KEY_FAMILY_NETWORK_USER_NAME in obj:
        value = str(obj[KEY_FAMILY_NETWORK_USER_NAME]).strip()
        if value:
            mobile_meta[KEY_FAMILY_NETWORK_USER_NAME] = value
    if KEY_FAMILY_NETWORK_HOST_NAME in obj:
        value = str(obj[KEY_FAMILY_NETWORK_HOST_NAME]).strip()
        if value:
            mobile_meta[KEY_FAMILY_NETWORK_HOST_NAME] = value

    if not mobile_meta:
        return

    if cfg_file.is_file():
        try:
            cfg = json.loads(cfg_file.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise ConfigImportError(f"现有 config.json 无法解析: {exc}") from exc
        if not isinstance(cfg, dict):
            raise ConfigImportError("现有 config.json 根节点必须是对象")
    else:
        cfg = {}

    imported_block = cfg.get("imported_from_mobile")
    if not isinstance(imported_block, dict):
        imported_block = {}
    imported_block.update(mobile_meta)
    cfg["imported_from_mobile"] = imported_block
    _backup_if_exists(cfg_file)
    cfg_file.write_text(json.dumps(cfg, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report.actions.append(f"已更新 PC 配置: {cfg_file} (imported_from_mobile)")


def import_mobile_config(
    input_path: Path,
    *,
    categories: set[str] | None = None,
    import_keys: bool = True,
    cfg_file: Path | None = None,
) -> ImportReport:
    obj = load_mobile_config(input_path)
    detected = detect_categories(obj)
    selected = detected if categories is None else (categories & detected)
    report = ImportReport(categories=selected)
    if categories is not None:
        unknown = categories - ALL_CATEGORIES
        if unknown:
            raise ConfigImportError(f"未知分类: {', '.join(sorted(unknown))}")
        for name in sorted(categories - detected):
            report.warnings.append(f"导出文件中未包含分类: {name}")

    ensure_app_layout()
    cfg_file = cfg_file or config_path()

    if CATEGORY_GPG in selected:
        if import_keys:
            _import_gpg(obj, report)
        else:
            report.warnings.append("已跳过 GPG 密钥（--skip-keys）")

    imp_root = imported_dir()
    imp_root.mkdir(parents=True, exist_ok=True)

    if CATEGORY_GIT in selected:
        _write_json_subset(obj, GIT_KEYS, imp_root / "git.json", report, "Git 配置")

    if CATEGORY_MUSIC in selected:
        _write_json_subset(obj, MUSIC_KEYS, imp_root / "player.json", report, "播放列表配置")
        report.warnings.append(
            "播放列表 URI 来自 Android，PC 端仅存档；曲目路径需在 PC 上重新建立。"
        )

    if CATEGORY_RECENT in selected:
        _write_json_subset(obj, (KEY_RECENT_OPEN_ITEMS,), imp_root / "recent.json", report, "最近打开")

    if CATEGORY_EPUB in selected:
        _write_json_subset(obj, EPUB_KEYS, imp_root / "epub.json", report, "EPUB 配置")

    if CATEGORY_OTHER in selected:
        _write_json_subset(obj, OTHER_KEYS, imp_root / "app_other.json", report, "其它应用配置")
        _patch_server_config(obj, report, cfg_file)

    snapshot = {
        KEY_CONFIG_VERSION: obj.get(KEY_CONFIG_VERSION),
        "imported_at": int(time.time()),
        "source_file": str(input_path.resolve()),
        "categories": sorted(selected),
        "keys_imported": import_keys and CATEGORY_GPG in selected,
    }
    for key in sorted(obj.keys()):
        if key in (KEY_GPG_PUBLIC_KEYS_BASE64, KEY_GPG_SECRET_KEYS_BASE64):
            continue
        if key in snapshot:
            continue
        cat = None
        if key in GIT_KEYS:
            cat = CATEGORY_GIT
        elif key in MUSIC_KEYS:
            cat = CATEGORY_MUSIC
        elif key in EPUB_KEYS:
            cat = CATEGORY_EPUB
        elif key in OTHER_KEYS:
            cat = CATEGORY_OTHER
        elif key == KEY_RECENT_OPEN_ITEMS:
            cat = CATEGORY_RECENT
        if cat is None or cat not in selected:
            continue
        snapshot[key] = obj[key]

    manifest = imp_root / "mobile_import_manifest.json"
    _backup_if_exists(manifest)
    manifest.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report.actions.append(f"已写入导入清单: {manifest}")

    if not report.actions and not report.warnings:
        report.warnings.append("没有可导入的内容（请检查分类选项与导出文件）")
    return report


def _parse_categories(text: str | None) -> set[str] | None:
    if text is None:
        return None
    parts = {part.strip().lower() for part in text.split(",") if part.strip()}
    if not parts:
        return None
    return parts


def run_import_config(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="platalea import-config",
        description="导入 Android 导出的 local_manager 配置 JSON 到 PC (~/.localmanager/)",
    )
    ap.add_argument("input", type=Path, help="手机导出的 .json 配置文件")
    ap.add_argument(
        "--categories",
        help=f"导入分类，逗号分隔：{','.join(sorted(ALL_CATEGORIES))}；默认导入文件中检测到的全部分类",
    )
    ap.add_argument(
        "--skip-keys",
        action="store_true",
        help="不导入 GPG 公钥/私钥（即使导出文件中包含）",
    )
    ap.add_argument(
        "--list",
        action="store_true",
        help="仅列出导出文件中包含的分类，不写入",
    )
    ap.add_argument(
        "--config",
        default="",
        help=f"PC 留言板 config.json 路径（默认 {config_path()}）",
    )
    args = ap.parse_args(argv)

    input_path = args.input.expanduser().resolve()
    if not input_path.is_file():
        print(f"错误：文件不存在: {input_path}", file=sys.stderr)
        return 1

    try:
        obj = load_mobile_config(input_path)
        detected = detect_categories(obj)
        if args.list:
            print(f"配置文件: {input_path}")
            print(f"config_version: {obj.get(KEY_CONFIG_VERSION, '?')}")
            print(f"检测到的分类: {', '.join(sorted(detected)) or '(无)'}")
            if contains_gpg_keys(obj):
                print("包含 GPG 密钥: 是")
            else:
                print("包含 GPG 密钥: 否")
            return 0

        categories = _parse_categories(args.categories)
        if categories is not None:
            categories = categories & ALL_CATEGORIES
            if not categories:
                raise ConfigImportError("--categories 未指定有效分类")

        report = import_mobile_config(
            input_path,
            categories=categories,
            import_keys=not args.skip_keys,
            cfg_file=config_path(args.config or None),
        )
    except ConfigImportError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    for line in report.actions:
        print(line)
    for line in report.warnings:
        print(f"注意: {line}", file=sys.stderr)
    print(f"数据目录: {app_dir()}")
    return 0 if report.actions else 1
