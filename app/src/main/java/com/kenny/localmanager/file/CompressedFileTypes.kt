package com.kenny.localmanager.file

/**
 * 压缩包文件名谓词（文件浏览功能拆解设计 §3）：压缩包阅读的文件名
 * 知识随首个声明者迁往所属域时，因最近记录路由（ui）、归档查看器
 * （archivereader）、浏览器（browser）三方共用，落在 file 包作域间
 * 纯函数工具。
 */

/** 判断文件名是否为压缩 Markdown/ReST 文件（.md.zip / .rst.zip）。 */
fun isCompressedMarkdown(name: String): Boolean =
    name.endsWith(".md.zip", ignoreCase = true) || name.endsWith(".rst.zip", ignoreCase = true)

/** 判断文件名是否为压缩 HTML 文件（.html.zip）。 */
fun isCompressedHtml(name: String): Boolean =
    name.endsWith(".html.zip", ignoreCase = true)

/** 判断文件名是否为压缩 LLM 包（.llm.zip）。 */
fun isCompressedLlmZip(name: String): Boolean =
    name.endsWith(".llm.zip", ignoreCase = true)

/** 判断文件名是否为 .pic.zip 图片压缩包。 */
fun isPicZip(name: String): Boolean =
    name.endsWith(".pic.zip", ignoreCase = true)

/** 判断文件名是否为 .vid.zip 视频压缩包。 */
fun isVidZip(name: String): Boolean =
    name.endsWith(".vid.zip", ignoreCase = true)
