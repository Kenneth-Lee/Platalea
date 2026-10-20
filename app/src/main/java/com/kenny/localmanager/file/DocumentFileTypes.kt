package com.kenny.localmanager.file

/**
 * 文档/操作类文件名谓词（文件浏览功能拆解设计 第二批）：随打开意图
 * 声明从 browser/BrowserFileTypes.kt 迁来——查看器、加密、解压各域与
 * 浏览器、player 域多方共用，落 file 包作域间纯函数工具。
 * isEpubFile / isTxtFile / isLlmFile 原即定义于 file 包（FileOperations.kt）。
 */

/** 判断文件名是否为 PDF 文件。 */
fun isPdfFile(name: String): Boolean =
    name.endsWith(".pdf", ignoreCase = true)

/** 判断文件名是否为 GPG 加密文件（.gpg）。 */
fun isGpgEncryptedFile(name: String): Boolean =
    name.endsWith(".gpg", ignoreCase = true)

/** 判断文件名是否为密码文件（.pass）。 */
fun isPassFile(name: String): Boolean =
    name.endsWith(".pass", ignoreCase = true)

/** 判断文件名是否为普通 Markdown / reST 文档（未压缩）。 */
fun isMarkdownFile(name: String): Boolean =
    name.endsWith(".md", ignoreCase = true) || name.endsWith(".rst", ignoreCase = true)

private val sevenZSplitPartRegex = Regex("(?i)^(.*\\.7z)\\.(\\d{3})$")

/** 判断文件名是否为可解压压缩包（zip / rar / 7z / tar 及常见压缩格式）。 */
fun isExtractableArchive(name: String): Boolean =
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
