package com.kenny.localmanager.ui

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 链接/URL 分类等纯逻辑，便于单元测试。
 * 内部使用 JDK 的 URI/URLDecoder，在 JVM 单测下可运行；对外与 Android Uri 行为一致。
 */
object MdLinkUtils {

    /** 判断是否像外链（无 scheme 的域名，如 www.baidu.com）。路径、含 # 的锚点、常见文档扩展名（如 .md）不算外链。 */
    fun looksLikeExternalUrl(url: String): Boolean {
        val s = url.trim()
        if (s.isEmpty()) return false
        val scheme = try { URI.create(s).scheme } catch (_: Exception) { null }
        if (!scheme.isNullOrEmpty()) return false
        if (s.contains("/") || s.contains("#")) return false
        if (s.startsWith("www.")) return true
        if (!s.contains(".")) return false
        if (looksLikeDocFilename(s)) return false
        return true
    }

    private val docExtensions = setOf(".md", ".rst", ".txt", ".html", ".htm", ".pdf", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg")
    private fun looksLikeDocFilename(s: String): Boolean = docExtensions.any { s.lowercase().endsWith(it) }

    /** 从任意 URL 中提取可能的主机名段（如 file:///www.bing.com -> www.bing.com），用于判断是否当外链处理。 */
    fun hostnameSegmentFromUrl(url: String): String? {
        val path = try { URI.create(url).path } catch (_: Exception) { null } ?: return null
        val seg = path.trimStart('/').split('/').lastOrNull() ?: return null
        return if (looksLikeHostname(seg)) seg else null
    }

    /** 判断是否像主机名（用于 content URI 的 lastPathSegment，如 www.baidu.com）。至少两个点，避免把 test1.md 当主机名。 */
    fun looksLikeHostname(segment: String): Boolean {
        if (segment.isBlank() || segment.contains("/")) return false
        if (segment.startsWith("www.")) return true
        val idx = segment.indexOf('.')
        if (idx <= 0) return false
        return segment.indexOf('.', idx + 1) > 0
    }

    /**
     * 从资源请求 URL 中提取相对于当前文档所在目录的路径。
     * WebView 可能请求 base + relativePath 的完整 URL。
     */
    fun extractRelativePathFromRequest(currentUri: String, resourceUrl: String): String? {
        val basePrefix = currentUri.substringBeforeLast("/").let { if (it.isEmpty()) return null else "$it/" }
        if (resourceUrl.startsWith(basePrefix)) {
            val rel = resourceUrl.substring(basePrefix.length)
            if (rel.isNotBlank()) return decodeUriComponent(rel)
        }
        return null
    }

    /** 与 Android Uri.decode 行为一致，在 JVM 上用 URLDecoder 实现便于单测。 */
    private fun decodeUriComponent(s: String): String {
        return try {
            URLDecoder.decode(s, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            s
        }
    }
}
