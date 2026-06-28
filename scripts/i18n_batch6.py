#!/usr/bin/env python3
"""Batch 6: family/ module error messages, work logs, and related UI strings."""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES_ZH = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"

FAMILY_FILES = sorted((ROOT / "app/src/main/java/com/kenny/localmanager/family").glob("*.kt"))
ATTACHMENT_PICK = ROOT / "app/src/main/java/com/kenny/localmanager/ui/AttachmentPickDialog.kt"

# context variable name per file (for getString replacement)
FILE_CONTEXT = {
    "FamilyNetworkManager.kt": "appContext",
    "BulletinBoardStore.kt": "appContext",
    "FamilyTlsManager.kt": "context",
    "AttachmentPickDialog.kt": "context",
}
DEFAULT_CONTEXT = "context"

# Manual key overrides (Chinese template -> resource key). Template uses ${var} syntax.
KEY_OVERRIDES: dict[str, str] = {
    "默认留言板": "family_board_default_name",
    "访客": "family_board_guest_label",
    "留言板附件": "family_attachment_folder_name",
    "留言板导出": "family_board_export_folder_name",
    "加载失败": "common_load_failed",
    "远程不可修改或删除，请在本机应用中操作": "family_http_remote_modify_forbidden",
}

# Manual English overrides
EN_OVERRIDES: dict[str, str] = {
    "family_board_default_name": "Default board",
    "family_board_guest_label": "Guest",
    "family_attachment_folder_name": "Bulletin attachments",
    "family_board_export_folder_name": "Bulletin exports",
    "common_load_failed": "Load failed",
    "family_http_remote_modify_forbidden": "Remote clients cannot modify or delete; use the app on the host device.",
}

# Simple zh -> en phrase map for auto-translation
PHRASE_EN = [
    ("无法", "Unable to "),
    ("不能", "Cannot "),
    ("未", "Not "),
    ("没有", "No "),
    ("不存在", "does not exist"),
    ("失败", " failed"),
    ("已", "Already "),
    ("正在", " "),
    ("请", "Please "),
    ("留言板", "bulletin board "),
    ("留言", "message "),
    ("附件", "attachment "),
    ("目录", "directory "),
    ("文件", "file "),
    ("导出", "export "),
    ("导入", "import "),
    ("转发", "forward "),
    ("下载", "download "),
    ("上传", "upload "),
    ("初始化", "initialize "),
    ("读取", "read "),
    ("写入", "write "),
    ("创建", "create "),
    ("删除", "delete "),
    ("重命名", "rename "),
    ("完成", "complete "),
    ("停止", "stop "),
    ("启动", "start "),
    ("服务", "service "),
    ("端口", "port "),
    ("密码", "password "),
    ("证书", "certificate "),
    ("指纹", "fingerprint "),
    ("设备", "device "),
    ("章节", "chapter "),
    ("消息", "message "),
    ("接口", "endpoint "),
    ("分块", "chunk "),
    ("归档包", "archive pack "),
    ("根目录", "root directory "),
    ("访客", "guest"),
    ("宿主", "host "),
    ("远程", "remote "),
    ("本机", "local "),
    ("局域网", "LAN "),
    ("手工刷新", "Manual refresh "),
    ("工作日志", "work log"),
    ("（暂无留言）", "(No messages yet)"),
    ("附件：", "Attachments:"),
]


def slugify(text: str) -> str:
    t = re.sub(r"\$\{[^}]+\}", "", text)
    t = re.sub(r"[^\w\u4e00-\u9fff]+", "_", t)
    t = t.strip("_").lower()
    if not t or re.search(r"[\u4e00-\u9fff]", t):
        h = abs(hash(text)) % 100000
        return f"family_msg_{h:05d}"
    return "family_" + t[:48].strip("_")


def auto_en(zh_fmt: str) -> str:
    if zh_fmt in KEY_OVERRIDES and KEY_OVERRIDES[zh_fmt] in EN_OVERRIDES:
        return EN_OVERRIDES[KEY_OVERRIDES[zh_fmt]]
    # preserve ${} placeholders, translate static Chinese parts
    parts = re.split(r"(\$\{[^}]+\})", zh_fmt)
    out = []
    for p in parts:
        if p.startswith("${"):
            out.append(p)
            continue
        s = p
        for zh, en in PHRASE_EN:
            s = s.replace(zh, en)
        out.append(s)
    result = "".join(out).strip()
    # cleanup common patterns
    result = re.sub(r"\s+", " ", result)
    if not result or re.search(r"[\u4e00-\u9fff]", result):
        # fallback: keep structure, mark untranslated parts
        result = zh_fmt  # will be improved manually if needed
        for zh, en in [
            ("无法", "Unable to "),
            ("失败", " failed"),
            ("不存在", " not found"),
            ("不能为空", " cannot be empty"),
            ("无效", " invalid"),
            ("错误", " error"),
            ("取消", " cancelled"),
            ("空", " empty"),
        ]:
            result = result.replace(zh, en)
    return result


def kotlin_template_to_android(zh: str) -> tuple[str, list[str]]:
    """Convert Kotlin "text ${a} more ${b}" to Android format string and arg names."""
    args: list[str] = []
    fmt_parts: list[str] = []
    i = 0
    while i < len(zh):
        if zh[i:i+2] == "${":
            j = zh.find("}", i + 2)
            if j < 0:
                fmt_parts.append(zh[i:])
                break
            expr = zh[i + 2:j]
            args.append(expr)
            fmt_parts.append(f"%{len(args)}$s")
            i = j + 1
        else:
            j = zh.find("${", i)
            if j < 0:
                fmt_parts.append(zh[i:])
                break
            fmt_parts.append(zh[i:j])
            i = j
    return "".join(fmt_parts), args


def extract_strings(path: Path) -> list[tuple[int, str]]:
    text = path.read_text(encoding="utf-8")
    hits = []
    for line_no, line in enumerate(text.splitlines(), 1):
        if "Log." in line or line.strip().startswith("//"):
            continue
        for m in re.finditer(r'"([^"\\]*(?:\\.[^"\\]*)*)"', line):
            s = m.group(1)
            if re.search(r"[\u4e00-\u9fff]", s):
                hits.append((line_no, s))
    return hits


def collect_all() -> dict[str, str]:
    keys: dict[str, str] = {}
    for path in FAMILY_FILES + [ATTACHMENT_PICK]:
        for _, s in extract_strings(path):
            if s in keys:
                continue
            keys[s] = KEY_OVERRIDES.get(s) or slugify(s)
    # dedupe keys
    used = {}
    for s, k in list(keys.items()):
        if k in used and used[k] != s:
            k = f"{k}_{abs(hash(s)) % 1000:03d}"
        used[k] = s
        keys[s] = k
    return keys


def build_resources(string_keys: dict[str, str]) -> tuple[dict[str, str], dict[str, str]]:
    zh, en = {}, {}
    for s, key in string_keys.items():
        fmt, _ = kotlin_template_to_android(s)
        zh[key] = fmt.replace("\\'", "'").replace('\\"', '"')
        en[key] = EN_OVERRIDES.get(key) or auto_en(s)
        en_fmt, _ = kotlin_template_to_android(en[key] if "${" in en[key] else en[key])
        if "${" not in en[key]:
            en[key] = en_fmt
    return zh, en


def xml_escape(s: str) -> str:
    return (
        s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "\\'")
        .replace('"', "&quot;")
        if False
        else s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")
    )


def add_to_xml(path: Path, entries: dict[str, str]):
    content = path.read_text(encoding="utf-8")
    adds = []
    for k, v in sorted(entries.items()):
        if f'name="{k}"' in content:
            continue
        adds.append(f'    <string name="{k}">{xml_escape(v)}</string>')
    if adds:
        content = content.replace("</resources>", "\n".join(adds) + "\n</resources>")
        path.write_text(content, encoding="utf-8")
        print(f"Added {len(adds)} to {path.name}")


def make_getstring(s: str, ctx: str, key: str) -> str:
    _, args = kotlin_template_to_android(s)
    if not args:
        return f'{ctx}.getString(R.string.{key})'
    arg_str = ", ".join(args)
    return f'{ctx}.getString(R.string.{key}, {arg_str})'


def apply_file(path: Path, string_keys: dict[str, str]):
    ctx = FILE_CONTEXT.get(path.name, DEFAULT_CONTEXT)
    text = path.read_text(encoding="utf-8")
    original = text

    # BulletinBoardHttpHandler: add context field
    if path.name == "BulletinBoardHttpHandler.kt":
        if "private val context: Context" not in text:
            text = text.replace(
                "package com.kenny.localmanager.family\n",
                "package com.kenny.localmanager.family\n\nimport android.content.Context\nimport com.kenny.localmanager.R\n",
            )
            text = text.replace(
                "class BulletinBoardHttpHandler(\n    private val store: BulletinBoardStore\n)",
                "class BulletinBoardHttpHandler(\n    private val context: Context,\n    private val store: BulletinBoardStore\n)",
            )

    # BulletinBoardStore: add appContext
    if path.name == "BulletinBoardStore.kt":
        if "private val appContext" not in text:
            text = text.replace(
                "import android.content.Context\n",
                "import android.content.Context\nimport com.kenny.localmanager.R\n",
            )
            text = text.replace(
                "class BulletinBoardStore(context: Context) {\n    private val rootDir",
                "class BulletinBoardStore(context: Context) {\n    private val appContext = context.applicationContext\n    private val rootDir",
            )
            text = text.replace(
                'put("name", BulletinBoardDefaults.DEFAULT_BOARD_NAME)',
                'put("name", appContext.getString(R.string.family_board_default_name))',
            )
            text = text.replace(
                'authorLabel = authorLabel.trim().ifEmpty { "访客" }',
                'authorLabel = authorLabel.trim().ifEmpty { appContext.getString(R.string.family_board_guest_label) }',
            )
        ctx = "appContext"

    # FamilyNetworkManager: fix HttpHandler ctor
    if path.name == "FamilyNetworkManager.kt":
        text = text.replace(
            "import com.kenny.localmanager.util.getLocalIpAddress\n",
            "import com.kenny.localmanager.R\nimport com.kenny.localmanager.util.getLocalIpAddress\n",
        )
        text = text.replace(
            "private val boardHttpHandler = BulletinBoardHttpHandler(boardStore)",
            "private val boardHttpHandler = BulletinBoardHttpHandler(appContext, boardStore)",
        )

    # BulletinBoardModels: remove hardcoded default name constant usage
    if path.name == "BulletinBoardModels.kt":
        text = text.replace(
            'const val DEFAULT_BOARD_NAME = "默认留言板"',
            'const val DEFAULT_BOARD_NAME = "default" // localized via family_board_default_name',
        )

    # BulletinAttachmentDownloadPaths
    if path.name == "BulletinAttachmentDownloadPaths.kt":
        if "import com.kenny.localmanager.R" not in text:
            text = text.replace(
                "package com.kenny.localmanager.family\n",
                "package com.kenny.localmanager.family\n\nimport android.content.Context\nimport com.kenny.localmanager.R\n",
            )

    # AttachmentPickDialog
    if path.name == "AttachmentPickDialog.kt":
        if "import com.kenny.localmanager.R" not in text:
            pass  # already has R

    # Sort strings longest first to avoid partial replacements
    for s in sorted(string_keys.keys(), key=len, reverse=True):
        key = string_keys[s]
        gs = make_getstring(s, ctx, key)
        # skip if already replaced
        old = f'"{s}"'
        if old not in text:
            continue
        text = text.replace(old, gs)

    if text != original:
        path.write_text(text, encoding="utf-8")
        print(f"Updated {path.name}")


def patch_forwarder_context():
    path = ROOT / "app/src/main/java/com/kenny/localmanager/family/BulletinMessageForwarder.kt"
    text = path.read_text(encoding="utf-8")
    if "import android.content.Context" in text:
        return
    text = text.replace(
        "package com.kenny.localmanager.family\n",
        "package com.kenny.localmanager.family\n\nimport android.content.Context\nimport com.kenny.localmanager.R\n",
    )
    text = text.replace(
        "fun relayAttachments(\n        sourceSession:",
        "fun relayAttachments(\n        context: Context,\n        sourceSession:",
    )
    path.write_text(text, encoding="utf-8")
    print("Patched BulletinMessageForwarder.kt context param")


def patch_forwarder_callers():
    path = ROOT / "app/src/main/java/com/kenny/localmanager/family/FamilyNetworkManager.kt"
    text = path.read_text(encoding="utf-8")
    text = text.replace(
        "BulletinMessageForwarder.relayAttachments(\n                        sourceSession =",
        "BulletinMessageForwarder.relayAttachments(\n                        context = appContext,\n                        sourceSession =",
    )
    path.write_text(text, encoding="utf-8")


def patch_download_paths():
    path = ROOT / "app/src/main/java/com/kenny/localmanager/family/BulletinAttachmentDownloadPaths.kt"
    text = path.read_text(encoding="utf-8")
    text = text.replace(
        "fun defaultDownloadDir(",
        "fun defaultDownloadDir(context: Context, ",
    )
    text = text.replace(
        "fun uniqueFileName(",
        "fun uniqueFileName(context: Context, ",
    )
    # fix internal usages - read file
    path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    keys = collect_all()
    zh, en = build_resources(keys)
    add_to_xml(RES_ZH, zh)
    add_to_xml(RES_EN, en)
    patch_forwarder_context()
    for p in FAMILY_FILES + [ATTACHMENT_PICK]:
        apply_file(p, keys)
    patch_forwarder_callers()
    print(f"Processed {len(keys)} unique strings")
