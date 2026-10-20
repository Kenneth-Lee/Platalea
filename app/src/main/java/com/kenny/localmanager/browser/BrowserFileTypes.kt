package com.kenny.localmanager.browser

import com.kenny.localmanager.ui.openRecentItemByType
import com.kenny.localmanager.file.isEpubFile
import com.kenny.localmanager.file.isLlmFile
import com.kenny.localmanager.file.isTxtFile
import com.kenny.localmanager.ui.RecentTabRoute
/**
 * 浏览器域文件类型谓词（批次 B2）：文件名 → 类型的单一知识来源。
 *
 * 点击行为表（BrowserScreen.openItemByDefault）、上下文菜单的独特项条件、
 * 最近记录的打开路由（RecentTabRoute.openRecentItemByType）三处共用这套谓词，
 * 避免同一后缀知识散装手写。新增阅读/操作格式时只在此处加谓词 + 各表加行。
 */
internal fun isCompressedMarkdown(name: String): Boolean =
    name.endsWith(".md.zip", ignoreCase = true) || name.endsWith(".rst.zip", ignoreCase = true)

/** 判断文件名是否为压缩 HTML 文件（.html.zip）。 */
internal fun isCompressedHtml(name: String): Boolean =
    name.endsWith(".html.zip", ignoreCase = true)

/** 判断文件名是否为压缩 LLM 包（.llm.zip）。 */
internal fun isCompressedLlmZip(name: String): Boolean =
    name.endsWith(".llm.zip", ignoreCase = true)

/** 判断文件名是否为 .pic.zip 图片压缩包。 */
internal fun isPicZip(name: String): Boolean =
    name.endsWith(".pic.zip", ignoreCase = true)

/** 判断文件名是否为 .vid.zip 视频压缩包。 */
internal fun isVidZip(name: String): Boolean =
    name.endsWith(".vid.zip", ignoreCase = true)

/** 判断文件名是否为 EPUB 文件。 */
internal fun isEpubFile(name: String): Boolean =
    name.endsWith(".epub", ignoreCase = true)

/** 判断文件名是否为 TXT 文件。 */
internal fun isTxtFile(name: String): Boolean =
    name.endsWith(".txt", ignoreCase = true)

/** 判断文件名是否为 LLM 对话文件。 */
internal fun isLlmFile(name: String): Boolean =
    name.endsWith(".llm", ignoreCase = true)

/** 判断文件名是否为 PDF 文件。 */
internal fun isPdfFile(name: String): Boolean =
    name.endsWith(".pdf", ignoreCase = true)

/** 判断文件名是否为 GPG 加密文件（.gpg）。 */
internal fun isGpgEncryptedFile(name: String): Boolean =
    name.endsWith(".gpg", ignoreCase = true)

/** 判断文件名是否为密码文件（.pass）。 */
internal fun isPassFile(name: String): Boolean =
    name.endsWith(".pass", ignoreCase = true)

/** 判断文件名是否为普通 Markdown / reST 文档（未压缩）。 */
internal fun isMarkdownFile(name: String): Boolean =
    name.endsWith(".md", ignoreCase = true) || name.endsWith(".rst", ignoreCase = true)

/** 判断文件名是否为 JSON 文件。 */
internal fun isJsonFile(name: String): Boolean =
    name.endsWith(".json", ignoreCase = true)

/** 判断文件名是否可作为配置导入文件（明文 .json 或 GPG 加密的 .json.gpg）。 */
internal fun isConfigImportFile(name: String): Boolean =
    isJsonFile(name) || name.endsWith(".json.gpg", ignoreCase = true)

private val sevenZSplitPartRegex = Regex("(?i)^(.*\\.7z)\\.(\\d{3})$")

/** 判断文件名是否为可解压压缩包（zip / rar / 7z / tar 及常见压缩格式）。 */
internal fun isExtractableArchive(name: String): Boolean =
    (name.endsWith(".zip", ignoreCase = true) && !isPicZip(name) && !isVidZip(name)) ||
        name.endsWith(".rar", ignoreCase = true) ||
        name.endsWith(".7z", ignoreCase = true) ||
        sevenZSplitPartRegex.matches(name) ||
        name.endsWith(".tar", ignoreCase = true) ||
        name.endsWith(".tar.gz", ignoreCase = true) ||
        name.endsWith(".tgz", ignoreCase = true) ||
        name.endsWith(".tar.bz2", ignoreCase = true) ||
        name.endsWith(".tbz2", ignoreCase = true) ||
        name.endsWith(".tar.xz", ignoreCase = true) ||
        name.endsWith(".txz", ignoreCase = true) ||
        (name.endsWith(".gz", ignoreCase = true) &&
            !name.endsWith(".tar.gz", ignoreCase = true) &&
            !name.endsWith(".tgz", ignoreCase = true)) ||
        (name.endsWith(".bz2", ignoreCase = true) &&
            !name.endsWith(".tar.bz2", ignoreCase = true) &&
            !name.endsWith(".tbz2", ignoreCase = true)) ||
        (name.endsWith(".xz", ignoreCase = true) &&
            !name.endsWith(".tar.xz", ignoreCase = true) &&
            !name.endsWith(".txz", ignoreCase = true))
