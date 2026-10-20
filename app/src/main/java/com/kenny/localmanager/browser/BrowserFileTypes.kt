package com.kenny.localmanager.browser

import com.kenny.localmanager.file.isPicZip
import com.kenny.localmanager.file.isVidZip

/**
 * 浏览器域文件类型谓词（批次 B2）：文件名 → 类型的单一知识来源。
 * 压缩包/文档谓词已随打开意图声明迁往 file 包（CompressedFileTypes.kt、
 * DocumentFileTypes.kt，拆解设计第一/二批——多方共用故为域间工具）。
 * 此处保留浏览器菜单专属判断（配置导入候选等，第三批随菜单动作再评估）。
 */

/** 判断文件名是否为 JSON 文件。 */
internal fun isJsonFile(name: String): Boolean =
    name.endsWith(".json", ignoreCase = true)

/** 判断文件名是否可作为配置导入文件（明文 .json 或 GPG 加密的 .json.gpg）。 */
internal fun isConfigImportFile(name: String): Boolean =
    isJsonFile(name) || name.endsWith(".json.gpg", ignoreCase = true)
