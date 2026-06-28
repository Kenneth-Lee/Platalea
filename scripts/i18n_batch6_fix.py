#!/usr/bin/env python3
"""Fix batch6 broken strings and Kotlin after auto-replace."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

XML_FIXES = {
    "family_msg_24247": "不支持的包格式：%1$s",
    "family_msg_86457": "不支持的包版本：%1$d，当前仅支持 v%2$d",
    "family_msg_44400": "无法解析本机 TLS 私钥，已尝试算法 %1$s：%2$s",
    "family_msg_83175": "解析到服务：%1$s -> %2$s:%3$d",
    "family_self_suffix": "（本机）",
    "family_msg_66267": "端口 %1$d 监听失败：%2$s；改用系统分配端口。",
    "family_msg_48632": "> 导出时间：%1$s",
    "family_msg_33585": "> 设备：%1$s",
    "family_msg_97534": "> 消息数：%1$d",
    "family_msg_00942": "下载附件分块为空：offset=%1$d",
    "family_msg_14374": "下载未完成：%1$d / %2$d 字节",
    "family_msg_18940": "无法创建目录：%1$s",
    "family_msg_05412": "无法创建子目录：%1$s",
    "family_msg_53162": "无法创建文件：%1$s",
    "family_msg_34495": "附件不存在：%1$s",
    "family_msg_71757": "留言板不存在：%1$s",
    "family_msg_43889": "留言板名称已存在：%1$s",
    "family_msg_76614": "留言板名称已存在：%1$s",
    "family_msg_21357": "留言板名称已存在：%1$s",
    "family_msg_81577": "无法生成不冲突的文件名：%1$s",
    "family_msg_45381": "写入附件分块失败：index=%1$d",
    "family_msg_50876": "写入目录附件分块失败：%1$s#%2$d",
    "family_msg_03789": "完成附件上传失败：%1$s",
    "family_msg_91420": "上传未完成：已传 %1$d / %2$d 字节",
    "family_msg_07788": "目标 URL 不是 HTTPS：%1$s",
    "family_msg_47294": "%1$s (导入)",
    "family_msg_17812": "已导出到 %1$s",
    "family_msg_60034": "boardpack 已导出到 %1$s",
    "family_msg_83171": "留言板已删除：%1$s",
    "family_msg_72725": "留言已删除：%1$s",
    "family_msg_02548": "留言已更新：%1$s",
    "family_msg_19687": "同步留言板失败：%1$s",
    "family_msg_17804": "主机名已更新，重新广播 mDNS：%1$s",
    "family_msg_94230": "开始发现服务：%1$s",
    "family_msg_01436": "已停止发现服务：%1$s",
    "family_msg_81223": "发现候选服务：%1$s",
    "family_msg_74498": "本机留言板服务已加入发现列表：%1$s（%2$s:%3$d）",
    "family_msg_97773": "已转发 %1$d 条消息到 %2$s/%3$s",
    "family_msg_55407": "本地 HTTPS 留言板已监听端口 %1$d，证书指纹=%2$s。",
    "family_msg_38541": "mDNS 服务已注册：%1$s %2$s 端口 %3$d",
    "family_msg_61384": "mDNS 服务注册失败，错误码=%1$d，service=%2$s",
    "family_msg_11672": "mDNS 服务注销失败，错误码=%1$d，service=%1$s",
    "family_msg_57086": "解析服务失败，name=%1$s，错误码=%2$d",
    "family_msg_41010": "启动服务发现失败，类型=%1$s，错误码=%2$d",
    "family_msg_28896": "停止服务发现失败，类型=%1$s，错误码=%2$d",
    "family_msg_09808": "包内路径非法：%1$s",
}

EN_FIXES = {
    "family_self_suffix": " (local)",
    "family_msg_24247": "Unsupported pack format: %1$s",
    "family_msg_86457": "Unsupported pack version: %1$d; only v%2$d is supported",
    "family_msg_44400": "Unable to parse local TLS private key; tried algorithms %1$s: %2$s",
    "family_msg_83175": "Resolved service: %1$s -> %2$s:%3$d",
    "family_msg_66267": "Failed to listen on port %1$d: %2$s; using an ephemeral port instead.",
}


def patch_xml(path: Path, fixes: dict):
    content = path.read_text(encoding="utf-8")
    for key, val in fixes.items():
        pat = rf'<string name="{key}">[^<]*</string>'
        rep = f'<string name="{key}">{val.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")}</string>'
        if re.search(pat, content):
            content = re.sub(pat, rep, content)
        elif key == "family_self_suffix":
            content = content.replace("</resources>", f'    <string name="{key}">{val}</string>\n</resources>')
    path.write_text(content, encoding="utf-8")


if __name__ == "__main__":
    patch_xml(ROOT / "app/src/main/res/values/strings.xml", XML_FIXES)
    patch_xml(ROOT / "app/src/main/res/values-en/strings.xml", {**XML_FIXES, **EN_FIXES})
    print("Patched strings.xml")
