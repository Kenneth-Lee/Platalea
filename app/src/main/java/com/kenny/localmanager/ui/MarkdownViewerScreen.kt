package com.kenny.localmanager.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.getDirectoryToOpen
import com.kenny.localmanager.file.listHtmlZipContentFiles
import com.kenny.localmanager.file.listMdZipContentFiles
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.gpg.GpgHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

private const val MAX_MARKDOWN_BYTES = 512 * 1024
private const val MAX_RST_BYTES = 512 * 1024

/** 简易 reStructuredText → HTML，覆盖常用语法（标题、粗/斜体、代码、链接、列表、代码块）。 */
private fun rstToHtml(rst: String): String {
    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
    val lines = rst.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val out = StringBuilder()
    var i = 0
    fun peekUnderlineChar(line: String): Char? =
        line.takeIf { it.isNotEmpty() && it.all { c -> c == it[0] && (c == '=' || c == '-' || c == '`' || c == '.' || c == '\'' || c == '"' || c == '~' || c == '^' || c == '_' || c == '*' || c == '#' || c == '+') } }?.get(0)
    while (i < lines.size) {
        val line = lines[i]
        val next = lines.getOrNull(i + 1)
        val under = next?.let { peekUnderlineChar(it) }
        if (under != null && next.isNotBlank() && line.isNotBlank() && next.length >= line.length) {
            val level = when (under) { '=', '#' -> 1; '-', '^' -> 2; '~', '"', '\'' -> 3; else -> 2 }
            val tag = "h${level.coerceIn(1, 3)}"
            out.append("<").append(tag).append(">").append(escape(line.trim())).append("</").append(tag).append(">")
            i += 2
            continue
        }
        if (line.trim().startsWith(".. figure::")) {
            val pathMatch = Regex("""\.\.\s+figure\s*::\s*(?:"([^"]*)"|'([^']*)'|(\S+))""").find(line.trim())
            val path = pathMatch?.groupValues?.let { g -> g[1].ifBlank { g[2].ifBlank { g[3] } } }?.trim()?.takeIf { it.isNotBlank() }
            if (path != null) {
                i++
                var alt = ""
                var width = ""
                var height = ""
                while (i < lines.size && lines[i].trimStart().startsWith(":")) {
                    val opt = lines[i].trimStart()
                    when {
                        opt.startsWith(":alt:") -> alt = opt.removePrefix(":alt:").trim().replace("\"", "&quot;")
                        opt.startsWith(":width:") -> width = opt.removePrefix(":width:").trim().takeIf { it.isNotBlank() }?.let { "width:$it" } ?: ""
                        opt.startsWith(":height:") -> height = opt.removePrefix(":height:").trim().takeIf { it.isNotBlank() }?.let { "height:$it" } ?: ""
                    }
                    i++
                }
                if (i < lines.size && lines[i].isBlank()) i++
                val caption = mutableListOf<String>()
                while (i < lines.size && (lines[i].isEmpty() || lines[i].startsWith(" ") || lines[i].startsWith("\t"))) {
                    if (lines[i].isNotBlank()) caption.add(rstInlineToHtml(escape(lines[i].trimStart())))
                    i++
                }
                val style = listOfNotNull(width, height).filter { it.isNotBlank() }.joinToString("; ")
                out.append("<figure>")
                out.append("<img src=\"").append(escape(path)).append("\" alt=\"").append(alt).append("\"")
                if (style.isNotBlank()) out.append(" style=\"").append(style).append("\"")
                out.append(">")
                if (caption.isNotEmpty()) out.append("<figcaption>").append(caption.joinToString(" ")).append("</figcaption>")
                out.append("</figure>")
            } else {
                i++
            }
            continue
        }
        if (line.trim().startsWith(".. math::")) {
            val restOfLine = line.trim().removePrefix(".. math::").trim()
            val mathLines = mutableListOf<String>()
            if (restOfLine.isNotBlank()) mathLines.add(escape(restOfLine))
            i++
            while (i < lines.size && (lines[i].isEmpty() || lines[i].startsWith(" ") || lines[i].startsWith("\t"))) {
                if (lines[i].isNotBlank()) mathLines.add(escape(lines[i].trimStart()))
                i++
            }
            if (mathLines.isNotEmpty()) {
                var latex = mathLines.joinToString(" \\\\ ").replace("&amp;", "&")
                if (mathLines.size > 1 || latex.contains("&=") || latex.contains("&")) {
                    latex = "\\begin{aligned}$latex\\end{aligned}"
                }
                out.append("""<div class="katex-display" data-latex="${escapeHtmlAttr(latex)}"></div>""")
            }
            continue
        }
        if (line.trim().startsWith(".. ") && (line.trim().length <= 3 || line.trim()[3].isWhitespace() || line.trim()[3] == ':')) {
            i++
            continue
        }
        if (line.trim().matches(Regex("^[-*•]\\s.+"))) {
            out.append("<ul>")
            while (i < lines.size && lines[i].trim().matches(Regex("^[-*•]\\s.+"))) {
                out.append("<li>").append(rstInlineToHtml(escape(lines[i].trim().drop(1).trim()))).append("</li>")
                i++
            }
            out.append("</ul>")
            continue
        }
        if (line.trim().matches(Regex("^\\d+\\.\\s.+"))) {
            out.append("<ol>")
            while (i < lines.size && lines[i].trim().matches(Regex("^\\d+\\.\\s.+"))) {
                out.append("<li>").append(rstInlineToHtml(escape(lines[i].trim().replaceFirst(Regex("^\\d+\\.\\s"), "")))).append("</li>")
                i++
            }
            out.append("</ol>")
            continue
        }
        if (line.trim() == ".." || (line.trim().startsWith("::") && line.trim().length == 2)) {
            i++
            val codeLines = mutableListOf<String>()
            while (i < lines.size && (lines[i].isEmpty() || lines[i].startsWith(" ") || lines[i].startsWith("\t"))) {
                if (lines[i].isNotBlank()) codeLines.add(escape(lines[i].trimStart()))
                i++
            }
            if (codeLines.isNotEmpty()) {
                out.append("<pre><code>").append(codeLines.joinToString("\n")).append("</code></pre>")
            }
            continue
        }
        if (line.isBlank()) {
            out.append("<p></p>")
            i++
            continue
        }
        if (line.startsWith(" ") || line.startsWith("\t")) {
            val codeLines = mutableListOf<String>()
            while (i < lines.size && (lines[i].isEmpty() || lines[i].startsWith(" ") || lines[i].startsWith("\t"))) {
                if (lines[i].isNotBlank()) codeLines.add(escape(lines[i].trimStart()))
                i++
            }
            if (codeLines.isNotEmpty()) out.append("<pre><code>").append(codeLines.joinToString("\n")).append("</code></pre>")
            continue
        }
        // RST：仅空行分段；连续非空行合并为一段，用空格连接
        val paraLines = mutableListOf(line)
        while (i + 1 < lines.size && lines[i + 1].isNotBlank() && !rstLineStartsBlock(lines[i + 1], lines.getOrNull(i + 2)) { peekUnderlineChar(it) }) {
            i++
            paraLines.add(lines[i])
        }
        val paraText = paraLines.joinToString(" ")
        out.append("<p>").append(rstInlineToHtml(escape(paraText))).append("</p>")
        i++
    }
    return out.toString()
}

private fun rstLineStartsBlock(line: String, next: String?, peekUnderlineChar: (String) -> Char?): Boolean {
    val t = line.trim()
    if (t.startsWith(".. figure::") || t.startsWith(".. math::")) return true
    if (t.startsWith(".. ") && (t.length <= 3 || t.getOrNull(3)?.isWhitespace() == true || t.getOrNull(3) == ':')) return true
    if (t == ".." || (t.startsWith("::") && t.length == 2)) return true
    if (t.matches(Regex("^[-*•]\\s.+"))) return true
    if (t.matches(Regex("^\\d+\\.\\s.+"))) return true
    if (line.startsWith(" ") || line.startsWith("\t")) return true
    if (next != null && next.isNotBlank() && peekUnderlineChar(next) != null && next.length >= t.length) return true
    return false
}

private fun escapeHtmlAttr(s: String): String = s
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun rstInlineToHtml(escaped: String): String {
    var s = escaped
    // 先处理 :math:，用 data-latex 供前端 KaTeX 显式渲染，避免 delimiter 解析问题
    s = Regex(""":math:`([^`]+?)`""").replace(s) {
        val latex = it.groupValues[1].replace("&amp;", "&")
        """<span class="katex-inline" data-latex="${escapeHtmlAttr(latex)}"></span>"""
    }
    s = Regex("""\*\*(.+?)\*\*""").replace(s) { "<strong>${it.groupValues[1]}</strong>" }
    s = Regex("""\*(.+?)\*""").replace(s) { "<em>${it.groupValues[1]}</em>" }
    s = Regex("""``([^`]+?)``""").replace(s) { "<code>${it.groupValues[1]}</code>" }
    s = Regex("""`([^`]+?) &lt;(.+?)&gt;`_""").replace(s) { """<a href="${it.groupValues[2]}">${it.groupValues[1]}</a>""" }
    s = Regex("""`([^`]+?)`_""").replace(s) { "<a href=\"#${it.groupValues[1].replace(" ", "-")}\">${it.groupValues[1]}</a>" }
    return s
}

private fun InputStream.readBytesUpTo(maxLen: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (total < maxLen) {
        val n = read(buf)
        if (n == -1) break
        out.write(buf, 0, minOf(n, maxLen - total))
        total += n
    }
    return out.toByteArray()
}

private const val MD_DEBUG = "MdViewer"
private const val MDZIP_DEBUG = "MdZipViewer"

private class LinkCallbackHolder { var onLink: (String) -> Unit = {} }

/** 判断是否像外链（无 scheme 的域名，如 www.baidu.com）。 */
private fun looksLikeExternalUrl(url: String): Boolean {
    val s = url.trim()
    if (s.isEmpty()) return false
    val scheme = Uri.parse(s).scheme
    if (!scheme.isNullOrEmpty()) return false
    return s.startsWith("www.") || (s.contains(".") && !s.contains("/"))
}

/** 从任意 URL 中提取可能的主机名段（如 file:///www.bing.com -> www.bing.com），用于判断是否当外链处理。 */
private fun hostnameSegmentFromUrl(url: String): String? {
    val parsed = Uri.parse(url)
    val seg = parsed.path?.trimStart('/')?.split('/')?.lastOrNull() ?: return null
    return if (looksLikeHostname(seg)) seg else null
}

/** 判断是否像主机名（用于 content URI 的 lastPathSegment，如 www.baidu.com）。 */
private fun looksLikeHostname(segment: String): Boolean {
    if (segment.isBlank() || segment.contains("/")) return false
    if (segment.startsWith("www.")) return true
    val idx = segment.indexOf('.')
    if (idx <= 0) return false
    return segment.indexOf('.', idx + 1) > 0 // 至少两个点，避免把 test1.md 当主机名
}

/** 当前文件所在目录 URI。使用 buildDocumentUriUsingTree 保持在已授权的树中。 */
private fun getParentDirectoryUri(context: android.content.Context, currentUri: String): Uri? {
    val uri = Uri.parse(currentUri)
    if (currentUri.contains("/tree/")) {
        return try {
            val docId = DocumentsContract.getDocumentId(uri) ?: return null
            val lastSlash = docId.lastIndexOf('/')
            val parentId = if (lastSlash > 0) docId.substring(0, lastSlash) else {
                DocumentsContract.getTreeDocumentId(uri)
            }
            DocumentsContract.buildDocumentUriUsingTree(uri, parentId)
        } catch (_: Exception) {
            Log.d(MD_DEBUG, "[父目录] tree 解析异常 currentUri=$currentUri")
            null
        }
    }
    return getDirectoryToOpen(context, uri)
}

/** 将链接 URL 解析为当前文件同目录下的实际 content URI；无法解析时返回 null。 */
private fun resolveRelativeToCurrent(context: android.content.Context, currentUri: String, clickedUrl: String): String? {
    val segment = Uri.parse(clickedUrl).lastPathSegment ?: clickedUrl.substringAfterLast('/').ifBlank { clickedUrl }
    if (segment.isBlank()) return null
    val parentUri = getParentDirectoryUri(context, currentUri) ?: run {
        Log.d(MD_DEBUG, "[内链] 无法取父目录 currentUri=$currentUri")
        return null
    }
    val childUri = findChildByName(context, parentUri, segment) ?: run {
        Log.d(MD_DEBUG, "[内链] 父目录下未找到 name=$segment parentUri=$parentUri")
        return null
    }
    Log.d(MD_DEBUG, "[内链] 解析成功 clickedUrl=$clickedUrl -> $childUri")
    return childUri.toString()
}

/** 根据当前文件所在目录解析相对路径（可含子目录，如 _static/决策树.png），返回该文档的 Uri 或 null。 */
private fun resolveResourcePath(
    context: android.content.Context,
    currentUri: String,
    path: String
): Uri? {
    val segments = path.trim().split("/").filter { it.isNotBlank() }.map { Uri.decode(it) }.ifEmpty { return null }
    var parentUri = getParentDirectoryUri(context, currentUri) ?: return null
    for (j in 0 until segments.size - 1) {
        parentUri = findChildByName(context, parentUri, segments[j]) ?: return null
    }
    return findChildByName(context, parentUri, segments.last())
}

/** 从资源请求 URL 中提取相对于当前文档所在目录的路径（WebView 可能请求 base + relativePath 的完整 URL）。 */
private fun extractRelativePathFromRequest(currentUri: String, resourceUrl: String): String? {
    val basePrefix = currentUri.substringBeforeLast("/").let { if (it.isEmpty()) return null else "$it/" }
    if (resourceUrl.startsWith(basePrefix)) {
        val rel = resourceUrl.substring(basePrefix.length)
        if (rel.isNotBlank()) return Uri.decode(rel)
    }
    return null
}

/** 根据当前文件 URI 解析资源 URL，返回同目录或相对路径下文件的 WebResourceResponse，否则返回 null。 */
private fun resolveResource(
    context: android.content.Context,
    currentUri: String,
    resourceUrl: String
): WebResourceResponse? {
    val parentUri = getParentDirectoryUri(context, currentUri) ?: run {
        Log.d(MD_DEBUG, "[图片] 无法取父目录 currentUri=$currentUri resourceUrl=$resourceUrl")
        return null
    }
    val relativePath = extractRelativePathFromRequest(currentUri, resourceUrl)
    val path = if (relativePath != null) relativePath else (Uri.parse(resourceUrl).path ?: resourceUrl)
    val segment = path.substringAfterLast('/').let { Uri.decode(it) }.ifBlank { return null }
    val childUri = if (path.contains("/")) {
        resolveResourcePath(context, currentUri, path)
    } else {
        findChildByName(context, parentUri, segment)
    } ?: run {
        Log.d(MD_DEBUG, "[图片] 未找到 path=$path resourceUrl=$resourceUrl")
        return null
    }
    return try {
        val mime = context.contentResolver.getType(childUri) ?: "application/octet-stream"
        val stream = context.contentResolver.openInputStream(childUri) ?: run {
            Log.d(MD_DEBUG, "[图片] openInputStream 失败 uri=$childUri")
            return null
        }
        Log.d(MD_DEBUG, "[图片] 加载成功 segment=$segment mime=$mime")
        WebResourceResponse(mime, "UTF-8", stream)
    } catch (e: Exception) {
        Log.d(MD_DEBUG, "[图片] 异常 resourceUrl=$resourceUrl", e)
        null
    }
}

private class ResourceResolverHolder(var currentUri: String)

/** WebViewClient：链接点击回调；资源请求用同目录文件响应（图片等）。 */
private class LinkInterceptClient(
    private val linkHolder: LinkCallbackHolder,
    private val context: android.content.Context,
    private val currentUriHolder: ResourceResolverHolder,
    private val onPageFinished: ((WebView) -> Unit)? = null
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        linkHolder.onLink(url)
        return true
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        if (request.isForMainFrame == true) return null
        Log.d(MD_DEBUG, "[资源] 请求 resourceUrl=$url currentUri=${currentUriHolder.currentUri}")
        val resp = resolveResource(context, currentUriHolder.currentUri, url)
        Log.d(MD_DEBUG, "[资源] 结果 resourceUrl=$url 返回=${resp != null}")
        return resp
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { onPageFinished?.invoke(it) }
    }
}

/** 独立 Markdown 渲染查看器：支持内链（同应用内打开、可退回）、外链（仅提示用浏览器打开）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownViewerScreen(
    initialFileUri: String,
    initialFileName: String,
    isEncrypted: Boolean,
    onBack: () -> Unit,
    onOpenFile: (uri: String, name: String, encrypted: Boolean) -> Unit
) {
    val context = LocalContext.current
    var backStack by remember { mutableStateOf(listOf<Triple<String, String, Boolean>>()) }
    var currentUri by remember { mutableStateOf(initialFileUri) }
    var currentName by remember { mutableStateOf(initialFileName) }
    var currentEncrypted by remember { mutableStateOf(isEncrypted) }

    var htmlContent by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    var pendingInternalUri by remember { mutableStateOf<String?>(null) }
    val linkHolder = remember { LinkCallbackHolder() }
    val currentUriHolder = remember { ResourceResolverHolder(currentUri) }
    currentUriHolder.currentUri = currentUri
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var scalePercent by remember { mutableStateOf(100) } // 50..200，页面 body.style.zoom

    val katexInline = remember(context) {
        try {
            val css = context.assets.open("katex/katex.min.css").use { it.bufferedReader().readText() }
                .replace("</style>", "<\\/style>")
            val katexJs = context.assets.open("katex/katex.min.js").use { it.bufferedReader().readText() }
                .replace("</script>", "<\\/script>")
            val autoRender = context.assets.open("katex/auto-render.min.js").use { it.bufferedReader().readText() }
                .replace("</script>", "<\\/script>")
            Triple(css, katexJs, autoRender)
        } catch (_: Exception) {
            Triple("", "", "")
        }
    }
    val (katexCss, katexJs, autoRenderJs) = katexInline

    val mermaidJs = remember(context) {
        try {
            context.assets.open("mermaid/mermaid.min.js").use { it.bufferedReader().readText() }
                .replace("</script>", "<\\/script>")
        } catch (_: Exception) { "" }
    }

    LaunchedEffect(currentUri, currentEncrypted) {
        loading = true
        loadError = null
        htmlContent = null
        Log.d(MD_DEBUG, "[加载] 开始 currentUri=$currentUri")
        val uri = Uri.parse(currentUri)
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStreamSafe(uri)
            if (stream == null) {
                Log.d(MD_DEBUG, "[加载] 失败 openInputStreamSafe 返回 null uri=$currentUri")
                loadError = "无法打开文件"
                return@withContext
            }
            stream.use { raw ->
                val bytes = if (currentEncrypted) {
                    GpgHelper.decryptStream(raw) ?: run {
                        loadError = "解密失败或需要密码"
                        Log.d(MD_DEBUG, "[加载] 解密失败")
                        return@withContext
                    }
                } else {
                    raw.readBytesUpTo(maxOf(MAX_MARKDOWN_BYTES, MAX_RST_BYTES))
                }
                val decoded = bytes.decodeToString()
                val trimmed = decoded.dropLastWhile { it == '\uFFFD' }
                val isRst = currentName.endsWith(".rst", ignoreCase = true)
                htmlContent = if (isRst) {
                    rstToHtml(trimmed)
                } else {
                    val extensions = listOf(TablesExtension.create())
                    val parser = Parser.builder().extensions(extensions).build()
                    val document = parser.parse(trimmed)
                    val renderer = HtmlRenderer.builder().extensions(extensions).build()
                    renderer.render(document)
                }
                Log.d(MD_DEBUG, "[加载] 成功 currentUri=$currentUri 长度=${trimmed.length} isRst=$isRst")
            }
        }
        loading = false
    }

    LaunchedEffect(pendingInternalUri) {
        val target = pendingInternalUri ?: return@LaunchedEffect
        pendingInternalUri = null
        Log.d(MD_DEBUG, "[内链] 处理 target=$target currentUri=$currentUri")
        val resolvedUri = resolveRelativeToCurrent(context, currentUri, target)
        val resolved = resolvedUri ?: target
        Log.d(MD_DEBUG, "[内链] resolvedUri=$resolvedUri 使用 resolved=$resolved")
        val targetUri = Uri.parse(resolved)
        val doc = DocumentFile.fromSingleUri(context, targetUri)
        val name = doc?.name ?: targetUri.lastPathSegment ?: "文件"
        val isRenderable = name.endsWith(".md", ignoreCase = true) || name.endsWith(".rst", ignoreCase = true)
        Log.d(MD_DEBUG, "[内链] doc.exists=${doc?.exists()} name=$name isRenderable=$isRenderable")
        if (isRenderable) {
            backStack = backStack + Triple(currentUri, currentName, currentEncrypted)
            currentUri = resolved
            currentName = name
            currentEncrypted = false
        } else {
            onOpenFile(resolved, name, false)
        }
    }

    linkHolder.onLink = { url ->
        val parsed = Uri.parse(url)
        val scheme = parsed.scheme?.lowercase() ?: ""
        val mainHandler = Handler(Looper.getMainLooper())
        val finalUrl = when {
            scheme == "http" || scheme == "https" || scheme == "mailto" -> url
            scheme == "content" -> {
                val segment = parsed.lastPathSegment ?: ""
                if (looksLikeHostname(segment)) {
                    Log.d(MD_DEBUG, "[链接] content URI 识别为外链 segment=$segment")
                    "https://$segment"
                } else null
            }
            scheme == "file" -> {
                hostnameSegmentFromUrl(url)?.let { seg ->
                    Log.d(MD_DEBUG, "[链接] file URI 识别为外链 segment=$seg")
                    "https://$seg"
                }
            }
            looksLikeExternalUrl(url) -> {
                Log.d(MD_DEBUG, "[链接] 识别为外链(无 scheme) url=$url")
                if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            }
            else -> null
        }
        if (finalUrl != null) {
            Log.d(MD_DEBUG, "[链接] 外链 -> 弹窗 url=$finalUrl")
            mainHandler.post { pendingExternalUrl = finalUrl }
        } else {
            Log.d(MD_DEBUG, "[链接] 内链 url=$url")
            mainHandler.post { pendingInternalUri = url }
        }
    }

    BackHandler {
        if (backStack.isNotEmpty()) {
            val last = backStack.last()
            backStack = backStack.dropLast(1)
            currentUri = last.first
            currentName = last.second
            currentEncrypted = last.third
        } else {
            onBack()
        }
    }

    if (pendingExternalUrl != null) {
        val urlToShow = pendingExternalUrl!!
        AlertDialog(
            onDismissRequest = { pendingExternalUrl = null },
            title = { Text("链接") },
            text = {
                Column {
                    Text(urlToShow, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text("可选择复制链接或用浏览器打开。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clip?.setPrimaryClip(ClipData.newPlainText("链接", urlToShow))
                        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                    pendingExternalUrl = null
                }) { Text("复制链接") }
                TextButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToShow))
                        context.startActivity(Intent.createChooser(intent, "用浏览器打开"))
                    } catch (_: Exception) {}
                    pendingExternalUrl = null
                }) { Text("用浏览器打开") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalUrl = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (backStack.isNotEmpty()) {
                            val last = backStack.last()
                            backStack = backStack.dropLast(1)
                            currentUri = last.first
                            currentName = last.second
                            currentEncrypted = last.third
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (htmlContent != null) {
                        IconButton(
                            onClick = {
                                scalePercent = maxOf(50, scalePercent - 25)
                                webViewRef.value?.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                            }
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "缩小")
                        }
                        IconButton(
                            onClick = {
                                scalePercent = minOf(200, scalePercent + 25)
                                webViewRef.value?.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                            }
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "放大")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loadError!!, color = MaterialTheme.colorScheme.error)
                }
                htmlContent != null -> {
                    val dirPrefix = currentUri.substringBeforeLast("/")
                    val baseUrl = if (dirPrefix.isNotEmpty()) "$dirPrefix/" else "file:///"
                    val bg = MaterialTheme.colorScheme.surface
                    val fg = MaterialTheme.colorScheme.onSurface
                    val bgHex = "#%02x%02x%02x".format(
                        (bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt()
                    )
                    val fgHex = "#%02x%02x%02x".format(
                        (fg.red * 255).toInt(), (fg.green * 255).toInt(), (fg.blue * 255).toInt()
                    )
                    val fullHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <base href="$baseUrl">
                        <style>$katexCss</style>
                        <style>
                            body { background: $bgHex; color: $fgHex; font-size: 16px; padding: 16px; line-height: 1.6; font-family: sans-serif; }
                            h1 { font-size: 1.5em; margin: 0.8em 0 0.4em; }
                            h2 { font-size: 1.3em; margin: 0.8em 0 0.4em; }
                            h3 { font-size: 1.15em; margin: 0.6em 0 0.3em; }
                            pre, code { background: rgba(128,128,128,0.2); padding: 0.2em 0.4em; border-radius: 4px; font-family: monospace; }
                            pre { display: block; padding: 12px; overflow-x: auto; }
                            pre code { padding: 0; background: none; }
                            .katex { font-size: 1.1em; }
                            blockquote { border-left: 4px solid rgba(128,128,128,0.5); margin: 0.5em 0; padding-left: 1em; color: rgba(128,128,128,0.95); }
                            figure { margin: 1em 0; }
                            figure img { max-width: 100%; height: auto; display: block; }
                            figcaption { font-size: 0.9em; color: rgba(128,128,128,0.9); margin-top: 0.4em; }
                            a { color: #2196F3; }
                            ul, ol { margin: 0.5em 0; padding-left: 1.5em; }
                            table { border-collapse: collapse; width: 100%; }
                            th, td { border: 1px solid rgba(128,128,128,0.4); padding: 6px 10px; text-align: left; }
                            th { background: rgba(128,128,128,0.15); }
                        </style>
                    </head>
                    <body>${htmlContent}
                    <script>$katexJs</script>
                    <script>$autoRenderJs</script>
                    <script>$mermaidJs</script>
                    <script>
                    document.addEventListener("DOMContentLoaded", function() {
                        if (typeof katex !== "undefined") {
                            document.querySelectorAll(".katex-inline").forEach(function(el) {
                                var latex = el.getAttribute("data-latex");
                                if (latex) try { el.innerHTML = katex.renderToString(latex, { displayMode: false, throwOnError: false }); } catch(e) {}
                            });
                            document.querySelectorAll(".katex-display").forEach(function(el) {
                                var latex = el.getAttribute("data-latex");
                                if (latex) try { el.innerHTML = katex.renderToString(latex, { displayMode: true, throwOnError: false }); } catch(e) {}
                            });
                        }
                        if (typeof renderMathInElement === "function") {
                            renderMathInElement(document.body, {
                                delimiters: [
                                    { left: "$$", right: "$$", display: true },
                                    { left: "$", right: "$", display: false },
                                    { left: "\\\\(", right: "\\\\)", display: false },
                                    { left: "\\\\[", right: "\\\\]", display: true }
                                ],
                                throwOnError: false
                            });
                        }
                        if (typeof mermaid !== "undefined") {
                            mermaid.initialize({ startOnLoad: false, theme: 'default' });
                            document.querySelectorAll("pre > code.language-mermaid").forEach(function(codeEl) {
                                var pre = codeEl.parentElement;
                                var div = document.createElement("div");
                                div.className = "mermaid";
                                div.textContent = codeEl.textContent;
                                pre.parentNode.replaceChild(div, pre);
                            });
                            mermaid.run();
                        }
                    });
                    </script>
                    </body>
                    </html>
                    """.trimIndent()
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(Color.TRANSPARENT)
                                webViewClient = LinkInterceptClient(
                                    linkHolder, context, currentUriHolder
                                ) { w -> w.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null) }
                                settings.domStorageEnabled = false
                                settings.javaScriptEnabled = true
                                webViewRef.value = this
                                loadDataWithBaseURL(baseUrl, fullHtml, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webView ->
                            currentUriHolder.currentUri = currentUri
                            webViewRef.value = webView
                            webView.loadDataWithBaseURL(baseUrl, fullHtml, "text/html", "UTF-8", null)
                        }
                    )
                }
            }
        }
    }
}

/** 密码保护文件查看器：从内存中的解密字节直接渲染 md/rst，不产生本地文件。退出时清除 WebView 缓存。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassContentViewerScreen(
    innerFileName: String,
    decryptedBytes: ByteArray,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var scalePercent by remember { mutableStateOf(100) }

    val katexInline = remember(context) {
        try {
            val css = context.assets.open("katex/katex.min.css").use { it.bufferedReader().readText() }
                .replace("</style>", "<\\/style>")
            val katexJs = context.assets.open("katex/katex.min.js").use { it.bufferedReader().readText() }
                .replace("</script>", "<\\/script>")
            val autoRender = context.assets.open("katex/auto-render.min.js").use { it.bufferedReader().readText() }
                .replace("</script>", "<\\/script>")
            Triple(css, katexJs, autoRender)
        } catch (_: Exception) {
            Triple("", "", "")
        }
    }
    val (katexCss, katexJs, autoRenderJs) = katexInline

    val mermaidJs = remember(context) {
        try {
            context.assets.open("mermaid/mermaid.min.js").use { it.bufferedReader().readText() }
                .replace("</script>", "<\\/script>")
        } catch (_: Exception) { "" }
    }

    val htmlContent = remember(decryptedBytes, innerFileName) {
        val decoded = decryptedBytes.decodeToString()
        val trimmed = decoded.dropLastWhile { it == '\uFFFD' }
        val isRst = innerFileName.endsWith(".rst", ignoreCase = true)
        if (isRst) rstToHtml(trimmed)
        else {
        val extensions = listOf(TablesExtension.create())
            val parser = org.commonmark.parser.Parser.builder().extensions(extensions).build()
            val document = parser.parse(trimmed)
            val renderer = org.commonmark.renderer.html.HtmlRenderer.builder().extensions(extensions).build()
            renderer.render(document)
        }
    }

    val doBack = {
        webViewRef.value?.clearCache(true)
        onBack()
    }

    BackHandler { doBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(innerFileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { doBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scalePercent = maxOf(50, scalePercent - 25)
                            webViewRef.value?.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                        }
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "缩小")
                    }
                    IconButton(
                        onClick = {
                            scalePercent = minOf(200, scalePercent + 25)
                            webViewRef.value?.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                        }
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "放大")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val bg = MaterialTheme.colorScheme.surface
            val fg = MaterialTheme.colorScheme.onSurface
            val bgHex = "#%02x%02x%02x".format(
                (bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt()
            )
            val fgHex = "#%02x%02x%02x".format(
                (fg.red * 255).toInt(), (fg.green * 255).toInt(), (fg.blue * 255).toInt()
            )
            val fullHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>$katexCss</style>
                <style>
                    body { background: $bgHex; color: $fgHex; font-size: 16px; padding: 16px; line-height: 1.6; font-family: sans-serif; }
                    h1 { font-size: 1.5em; margin: 0.8em 0 0.4em; }
                    h2 { font-size: 1.3em; margin: 0.8em 0 0.4em; }
                    h3 { font-size: 1.15em; margin: 0.6em 0 0.3em; }
                    pre, code { background: rgba(128,128,128,0.2); padding: 0.2em 0.4em; border-radius: 4px; font-family: monospace; }
                    pre { display: block; padding: 12px; overflow-x: auto; }
                    pre code { padding: 0; background: none; }
                    .katex { font-size: 1.1em; }
                    blockquote { border-left: 4px solid rgba(128,128,128,0.5); margin: 0.5em 0; padding-left: 1em; color: rgba(128,128,128,0.95); }
                    a { color: #2196F3; }
                    ul, ol { margin: 0.5em 0; padding-left: 1.5em; }
                    table { border-collapse: collapse; width: 100%; }
                    th, td { border: 1px solid rgba(128,128,128,0.4); padding: 6px 10px; text-align: left; }
                    th { background: rgba(128,128,128,0.15); }
                </style>
            </head>
            <body>$htmlContent
            <script>$katexJs</script>
            <script>$autoRenderJs</script>
            <script>$mermaidJs</script>
            <script>
            document.addEventListener("DOMContentLoaded", function() {
                if (typeof katex !== "undefined") {
                    document.querySelectorAll(".katex-inline").forEach(function(el) {
                        var latex = el.getAttribute("data-latex");
                        if (latex) try { el.innerHTML = katex.renderToString(latex, { displayMode: false, throwOnError: false }); } catch(e) {}
                    });
                    document.querySelectorAll(".katex-display").forEach(function(el) {
                        var latex = el.getAttribute("data-latex");
                        if (latex) try { el.innerHTML = katex.renderToString(latex, { displayMode: true, throwOnError: false }); } catch(e) {}
                    });
                }
                if (typeof renderMathInElement === "function") {
                    renderMathInElement(document.body, {
                        delimiters: [
                            { left: "$$", right: "$$", display: true },
                            { left: "$", right: "$", display: false },
                            { left: "\\(", right: "\\)", display: false },
                            { left: "\\[", right: "\\]", display: true }
                        ],
                        throwOnError: false
                    });
                }
                if (typeof mermaid !== "undefined") {
                    mermaid.initialize({ startOnLoad: false, theme: 'default' });
                    document.querySelectorAll("pre > code.language-mermaid").forEach(function(codeEl) {
                        var pre = codeEl.parentElement;
                        var div = document.createElement("div");
                        div.className = "mermaid";
                        div.textContent = codeEl.textContent;
                        pre.parentNode.replaceChild(div, pre);
                    });
                    mermaid.run();
                }
            });
            </script>
            </body>
            </html>
            """.trimIndent()
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        settings.domStorageEnabled = false
                        settings.javaScriptEnabled = true
                        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        webViewRef.value = this
                        loadDataWithBaseURL("about:blank", fullHtml, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { webView ->
                    webViewRef.value = webView
                }
            )
        }
    }
}

/** .md.zip 压缩 Markdown 查看器：从本地缓存目录中读取 md/rst 并渲染，支持本地图片等资源。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MdZipViewerScreen(
    initialTargetFile: File?,  // null 表示未找到可渲染文件
    contentDir: File,
    zipFileName: String,
    onBack: () -> Unit,
    logDebug: ((String) -> Unit)? = null
) {
    // 无可渲染文件时显示目录内容
    if (initialTargetFile == null) {
        MdZipNoTargetScreen(
            contentDir = contentDir,
            zipFileName = zipFileName,
            onBack = onBack,
            logDebug = logDebug
        )
        return
    }

    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var scalePercent by remember { mutableStateOf(100) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    val linkHolder = remember { LinkCallbackHolder() }

    var backStack by remember { mutableStateOf(listOf<File>()) }
    var currentFile by remember { mutableStateOf(initialTargetFile) }
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pendingInternalFile by remember { mutableStateOf<File?>(null) }

    val katexInline = remember(context) {
        try {
            val css = context.assets.open("katex/katex.min.css").use { it.bufferedReader().readText() }
                .replace("</style>", "<\\/style>")
            val katexJs = context.assets.open("katex/katex.min.js").use { it.bufferedReader().readText() }
                .replace("</script>", "<\\/script>")
            val autoRender = context.assets.open("katex/auto-render.min.js").use { it.bufferedReader().readText() }
                .replace("</script>", "<\\/script>")
            Triple(css, katexJs, autoRender)
        } catch (_: Exception) {
            Triple("", "", "")
        }
    }
    val (katexCss, katexJs, autoRenderJs) = katexInline

    val mermaidJs = remember(context) {
        try {
            context.assets.open("mermaid/mermaid.min.js").use { it.bufferedReader().readText() }
                .replace("</script>", "<\\/script>")
        } catch (_: Exception) { "" }
    }

    Log.d(MDZIP_DEBUG, "打开 zipFileName=$zipFileName contentDir=${contentDir.absolutePath} initialTargetFile=${initialTargetFile.absolutePath}")
    logDebug?.invoke("[MDZIP] 打开 zipFileName=$zipFileName")
    logDebug?.invoke("[MDZIP] contentDir=${contentDir.absolutePath}")
    logDebug?.invoke("[MDZIP] initialTargetFile=${initialTargetFile.absolutePath}")

    LaunchedEffect(currentFile) {
        Log.d(MDZIP_DEBUG, "加载文件 path=${currentFile.absolutePath} exists=${currentFile.exists()} parentDir=${currentFile.parentFile?.absolutePath}")
        logDebug?.invoke("[MDZIP] 加载文件 path=${currentFile.absolutePath}")
        logDebug?.invoke("[MDZIP]   exists=${currentFile.exists()}")
        logDebug?.invoke("[MDZIP]   parentDir=${currentFile.parentFile?.absolutePath}")
        loading = true
        loadError = null
        htmlContent = null
        withContext(Dispatchers.IO) {
            try {
                if (!currentFile.exists()) {
                    loadError = "文件不存在: ${currentFile.absolutePath}"
                    Log.e(MDZIP_DEBUG, "文件不存在: ${currentFile.absolutePath}")
                    logDebug?.invoke("[MDZIP] 文件不存在!")
                    return@withContext
                }
                val bytes = currentFile.readBytes().let {
                    if (it.size > MAX_MARKDOWN_BYTES) it.copyOf(MAX_MARKDOWN_BYTES) else it
                }
                val decoded = bytes.decodeToString()
                val trimmed = decoded.dropLastWhile { it == '\uFFFD' }
                Log.d(MDZIP_DEBUG, "文件内容长度=${trimmed.length}")
                logDebug?.invoke("[MDZIP] 文件内容长度=${trimmed.length}")
                logDebug?.invoke("[MDZIP] 文件内容前200字符:")
                logDebug?.invoke(trimmed.take(200))
                val isRst = currentFile.name.endsWith(".rst", ignoreCase = true)
                htmlContent = if (isRst) {
                    rstToHtml(trimmed)
                } else {
                    val extensions = listOf(TablesExtension.create())
                    val parser = Parser.builder().extensions(extensions).build()
                    val document = parser.parse(trimmed)
                    val renderer = HtmlRenderer.builder().extensions(extensions).build()
                    renderer.render(document)
                }
                Log.d(MDZIP_DEBUG, "HTML渲染完成 长度=${htmlContent?.length}")
                logDebug?.invoke("[MDZIP] HTML渲染完成 长度=${htmlContent?.length}")
            } catch (e: Exception) {
                loadError = "加载失败: ${e.message}"
                Log.e(MDZIP_DEBUG, "加载异常: ${e.javaClass.name}: ${e.message}", e)
                logDebug?.invoke("[MDZIP] 加载异常: ${e.javaClass.name}: ${e.message}")
            }
        }
        loading = false
    }

    LaunchedEffect(pendingInternalFile) {
        val target = pendingInternalFile ?: return@LaunchedEffect
        pendingInternalFile = null
        Log.d(MDZIP_DEBUG, "内链跳转目标 path=${target.absolutePath} exists=${target.exists()} isFile=${target.isFile}")
        logDebug?.invoke("[MDZIP] 内链跳转目标 path=${target.absolutePath}")
        logDebug?.invoke("[MDZIP]   exists=${target.exists()}")
        logDebug?.invoke("[MDZIP]   isFile=${target.isFile}")
        if (target.exists()) {
            val name = target.name
            val isRenderable = name.endsWith(".md", ignoreCase = true) || name.endsWith(".rst", ignoreCase = true)
            Log.d(MDZIP_DEBUG, "  name=$name isRenderable=$isRenderable")
            logDebug?.invoke("[MDZIP]   name=$name isRenderable=$isRenderable")
            if (isRenderable) {
                backStack = backStack + currentFile
                currentFile = target
                Log.d(MDZIP_DEBUG, "  跳转成功 -> ${target.absolutePath}")
                logDebug?.invoke("[MDZIP]   跳转成功 -> ${target.absolutePath}")
            } else {
                Log.d(MDZIP_DEBUG, "  不是 md/rst，忽略")
                logDebug?.invoke("[MDZIP]   不是 md/rst，忽略")
            }
        } else {
            Log.e(MDZIP_DEBUG, "  文件不存在，跳转失败!")
            logDebug?.invoke("[MDZIP]   文件不存在，跳转失败!")
        }
    }

    linkHolder.onLink = { url ->
        val parsed = Uri.parse(url)
        val scheme = parsed.scheme?.lowercase() ?: ""
        val mainHandler = Handler(Looper.getMainLooper())
        Log.d(MDZIP_DEBUG, "链接点击 url=$url scheme=$scheme")
        logDebug?.invoke("[MDZIP] 链接点击 url=$url")
        logDebug?.invoke("[MDZIP]   scheme=$scheme")
        val finalUrl = when {
            scheme == "http" || scheme == "https" || scheme == "mailto" -> url
            scheme == "file" -> hostnameSegmentFromUrl(url)?.let { seg -> "https://$seg" }
            looksLikeExternalUrl(url) -> {
                if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            }
            else -> null
        }
        if (finalUrl != null) {
            Log.d(MDZIP_DEBUG, "  识别为外链 finalUrl=$finalUrl")
            logDebug?.invoke("[MDZIP]   识别为外链 finalUrl=$finalUrl")
            mainHandler.post { pendingExternalUrl = finalUrl }
        } else {
            val decoded = Uri.decode(if (scheme == "file") parsed.path ?: url else url)
            Log.d(MDZIP_DEBUG, "  parsed.path=${parsed.path} decoded=$decoded")
            logDebug?.invoke("[MDZIP]   parsed.path=${parsed.path}")
            logDebug?.invoke("[MDZIP]   decoded=$decoded")
            logDebug?.invoke("[MDZIP]   currentFile.parentFile=${currentFile.parentFile?.absolutePath}")
            val resolved = if (decoded.startsWith("/")) {
                Log.d(MDZIP_DEBUG, "  绝对路径，直接使用")
                logDebug?.invoke("[MDZIP]   绝对路径，直接使用")
                File(decoded).canonicalFile
            } else {
                Log.d(MDZIP_DEBUG, "  相对路径，拼接父目录 ${currentFile.parentFile?.absolutePath}")
                logDebug?.invoke("[MDZIP]   相对路径，拼接父目录")
                currentFile.parentFile?.let { File(it, decoded).canonicalFile }
            }
            val contentDirCanonical = contentDir.canonicalPath
            Log.d(MDZIP_DEBUG, "  resolved=${resolved?.absolutePath} contentDirCanonical=$contentDirCanonical")
            logDebug?.invoke("[MDZIP]   resolved=${resolved?.absolutePath}")
            logDebug?.invoke("[MDZIP]   contentDirCanonical=$contentDirCanonical")
            val startsWithCheck = resolved?.absolutePath?.startsWith(contentDirCanonical)
            Log.d(MDZIP_DEBUG, "  startsWith=$startsWithCheck")
            logDebug?.invoke("[MDZIP]   startsWith=$startsWithCheck")
            if (resolved != null && startsWithCheck == true) {
                Log.d(MDZIP_DEBUG, "  通过安全检查，设置 pendingInternalFile")
                logDebug?.invoke("[MDZIP]   通过安全检查，设置 pendingInternalFile")
                mainHandler.post { pendingInternalFile = resolved }
            } else {
                Log.w(MDZIP_DEBUG, "  未通过安全检查或 resolved 为 null，忽略")
                logDebug?.invoke("[MDZIP]   未通过安全检查或 resolved 为 null，忽略")
            }
        }
    }

    val doBack: () -> Unit = {
        if (backStack.isNotEmpty()) {
            currentFile = backStack.last()
            backStack = backStack.dropLast(1)
        } else {
            onBack()
        }
    }

    BackHandler { doBack() }

    if (pendingExternalUrl != null) {
        val urlToShow = pendingExternalUrl!!
        AlertDialog(
            onDismissRequest = { pendingExternalUrl = null },
            title = { Text("链接") },
            text = {
                Column {
                    Text(urlToShow, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text("可选择复制链接或用浏览器打开。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clip?.setPrimaryClip(ClipData.newPlainText("链接", urlToShow))
                        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                    pendingExternalUrl = null
                }) { Text("复制链接") }
                TextButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToShow))
                        context.startActivity(Intent.createChooser(intent, "用浏览器打开"))
                    } catch (_: Exception) {}
                    pendingExternalUrl = null
                }) { Text("用浏览器打开") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalUrl = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zipFileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { doBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (htmlContent != null) {
                        IconButton(
                            onClick = {
                                scalePercent = maxOf(50, scalePercent - 25)
                                webViewRef.value?.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                            }
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "缩小")
                        }
                        IconButton(
                            onClick = {
                                scalePercent = minOf(200, scalePercent + 25)
                                webViewRef.value?.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                            }
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "放大")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loadError!!, color = MaterialTheme.colorScheme.error)
                }
                htmlContent != null -> {
                    val baseUrl = "file://${currentFile.parentFile?.absolutePath}/"
                    val bg = MaterialTheme.colorScheme.surface
                    val fg = MaterialTheme.colorScheme.onSurface
                    val bgHex = "#%02x%02x%02x".format(
                        (bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt()
                    )
                    val fgHex = "#%02x%02x%02x".format(
                        (fg.red * 255).toInt(), (fg.green * 255).toInt(), (fg.blue * 255).toInt()
                    )
                    val fullHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>$katexCss</style>
                        <style>
                            body { background: $bgHex; color: $fgHex; font-size: 16px; padding: 16px; line-height: 1.6; font-family: sans-serif; }
                            h1 { font-size: 1.5em; margin: 0.8em 0 0.4em; }
                            h2 { font-size: 1.3em; margin: 0.8em 0 0.4em; }
                            h3 { font-size: 1.15em; margin: 0.6em 0 0.3em; }
                            pre, code { background: rgba(128,128,128,0.2); padding: 0.2em 0.4em; border-radius: 4px; font-family: monospace; }
                            pre { display: block; padding: 12px; overflow-x: auto; }
                            pre code { padding: 0; background: none; }
                            .katex { font-size: 1.1em; }
                            blockquote { border-left: 4px solid rgba(128,128,128,0.5); margin: 0.5em 0; padding-left: 1em; color: rgba(128,128,128,0.95); }
                            figure { margin: 1em 0; }
                            figure img { max-width: 100%; height: auto; display: block; }
                            figcaption { font-size: 0.9em; color: rgba(128,128,128,0.9); margin-top: 0.4em; }
                            a { color: #2196F3; }
                            ul, ol { margin: 0.5em 0; padding-left: 1.5em; }
                            table { border-collapse: collapse; width: 100%; }
                            th, td { border: 1px solid rgba(128,128,128,0.4); padding: 6px 10px; text-align: left; }
                            th { background: rgba(128,128,128,0.15); }
                        </style>
                    </head>
                    <body>${htmlContent}
                    <script>$katexJs</script>
                    <script>$autoRenderJs</script>
                    <script>$mermaidJs</script>
                    <script>
                    document.addEventListener("DOMContentLoaded", function() {
                        if (typeof katex !== "undefined") {
                            document.querySelectorAll(".katex-inline").forEach(function(el) {
                                var latex = el.getAttribute("data-latex");
                                if (latex) try { el.innerHTML = katex.renderToString(latex, { displayMode: false, throwOnError: false }); } catch(e) {}
                            });
                            document.querySelectorAll(".katex-display").forEach(function(el) {
                                var latex = el.getAttribute("data-latex");
                                if (latex) try { el.innerHTML = katex.renderToString(latex, { displayMode: true, throwOnError: false }); } catch(e) {}
                            });
                        }
                        if (typeof renderMathInElement === "function") {
                            renderMathInElement(document.body, {
                                delimiters: [
                                    { left: "$$", right: "$$", display: true },
                                    { left: "$", right: "$", display: false },
                                    { left: "\\\\(", right: "\\\\)", display: false },
                                    { left: "\\\\[", right: "\\\\]", display: true }
                                ],
                                throwOnError: false
                            });
                        }
                        if (typeof mermaid !== "undefined") {
                            mermaid.initialize({ startOnLoad: false, theme: 'default' });
                            document.querySelectorAll("pre > code.language-mermaid").forEach(function(codeEl) {
                                var pre = codeEl.parentElement;
                                var div = document.createElement("div");
                                div.className = "mermaid";
                                div.textContent = codeEl.textContent;
                                pre.parentNode.replaceChild(div, pre);
                            });
                            mermaid.run();
                        }
                    });
                    </script>
                    </body>
                    </html>
                    """.trimIndent()
                    val currentFileForClient = currentFile
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(Color.TRANSPARENT)
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = false
                                settings.allowFileAccess = true
                                webViewClient = MdZipWebViewClient(linkHolder, contentDir, currentFileForClient) { w ->
                                    w.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                                }
                                webViewRef.value = this
                                loadDataWithBaseURL(baseUrl, fullHtml, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webView ->
                            webViewRef.value = webView
                            webView.loadDataWithBaseURL(baseUrl, fullHtml, "text/html", "UTF-8", null)
                        }
                    )
                }
            }
        }
    }
}

/** WebViewClient：拦截链接点击；从本地解压目录加载图片等资源。 */
private class MdZipWebViewClient(
    private val linkHolder: LinkCallbackHolder,
    private val contentDir: File,
    private val currentFile: File,
    private val onPageFinished: ((WebView) -> Unit)? = null
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        linkHolder.onLink(url)
        return true
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return null
        if (request.isForMainFrame) return null
        if (url.scheme == "file") {
            val path = url.path ?: return null
            val file = File(path)
            if (file.exists() && file.isFile && file.absolutePath.startsWith(contentDir.absolutePath)) {
                return try {
                    val mime = when {
                        file.name.endsWith(".png", true) -> "image/png"
                        file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true) -> "image/jpeg"
                        file.name.endsWith(".gif", true) -> "image/gif"
                        file.name.endsWith(".svg", true) -> "image/svg+xml"
                        file.name.endsWith(".webp", true) -> "image/webp"
                        file.name.endsWith(".css", true) -> "text/css"
                        file.name.endsWith(".js", true) -> "application/javascript"
                        else -> "application/octet-stream"
                    }
                    WebResourceResponse(mime, "UTF-8", FileInputStream(file))
                } catch (_: Exception) { null }
            }
        }
        return null
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { onPageFinished?.invoke(it) }
    }
}

/** 无可渲染文件时显示目录内容的界面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MdZipNoTargetScreen(
    contentDir: File,
    zipFileName: String,
    onBack: () -> Unit,
    logDebug: ((String) -> Unit)? = null
) {
    val isRstZip = zipFileName.endsWith(".rst.zip", ignoreCase = true)
    val fileList = remember(contentDir) {
        listMdZipContentFiles(contentDir)
    }

    logDebug?.invoke("[MDZIP] 无可渲染文件，显示目录内容")
    logDebug?.invoke("[MDZIP] contentDir=${contentDir.absolutePath}")
    logDebug?.invoke("[MDZIP] 文件数量=${fileList.size}")
    logDebug?.invoke("[MDZIP] contentDir.exists=${contentDir.exists()}, listFiles=${contentDir.listFiles()?.map { it.name }}")

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zipFileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "未找到可打开的文件",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            val ext = if (isRstZip) ".rst" else ".md"
            Text(
                text = "压缩包中未找到 index${ext}、README${ext} 文件，也没有其他 ${ext} 文件可生成索引。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "压缩包内容：",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            if (fileList.isEmpty()) {
                Text(
                    text = "（空目录）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(fileList) { filePath ->
                        Text(
                            text = filePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (filePath.endsWith("/")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---- .html.zip 查看器 ----

private const val HTMLZIP_DEBUG = "HtmlZipViewer"

/** .html.zip 查看器：解压到缓存后用 WebView 渲染 index.html，支持目录内跳转与 cache 资源。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlZipViewerScreen(
    initialIndexFile: File?,
    contentDir: File,
    zipFileName: String,
    onBack: () -> Unit,
    logDebug: ((String) -> Unit)? = null
) {
    if (initialIndexFile == null) {
        HtmlZipNoIndexScreen(
            contentDir = contentDir,
            zipFileName = zipFileName,
            onBack = onBack,
            logDebug = logDebug
        )
        return
    }

    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }

    logDebug?.invoke("[HTMLZIP] 打开 zipFileName=$zipFileName indexFile=${initialIndexFile.absolutePath}")

    BackHandler {
        val w = webViewRef.value
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }

    if (pendingExternalUrl != null) {
        val urlToShow = pendingExternalUrl!!
        AlertDialog(
            onDismissRequest = { pendingExternalUrl = null },
            title = { Text("链接") },
            text = {
                Column {
                    Text(urlToShow, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text("可选择复制链接或用浏览器打开。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clip?.setPrimaryClip(ClipData.newPlainText("链接", urlToShow))
                        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                    pendingExternalUrl = null
                }) { Text("复制链接") }
                TextButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToShow))
                        context.startActivity(Intent.createChooser(intent, "用浏览器打开"))
                    } catch (_: Exception) {}
                    pendingExternalUrl = null
                }) { Text("用浏览器打开") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalUrl = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zipFileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        val w = webViewRef.value
                        if (w != null && w.canGoBack()) w.goBack() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        webViewClient = HtmlZipWebViewClient(contentDir) { url ->
                            pendingExternalUrl = url
                        }
                        webViewRef.value = this
                        loadUrl("file://${initialIndexFile.absolutePath}")
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { webView ->
                    webViewRef.value = webView
                }
            )
        }
    }
}

/** WebViewClient：允许 file 协议下 contentDir 内跳转，外链弹窗；子资源从 cache 提供。 */
private class HtmlZipWebViewClient(
    private val contentDir: File,
    private val onExternalUrl: (String) -> Unit
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (!request.isForMainFrame) return false
        val scheme = request.url?.scheme?.lowercase() ?: ""
        when {
            scheme == "http" || scheme == "https" || scheme == "mailto" -> {
                onExternalUrl(url)
                return true
            }
            scheme == "file" -> {
                val path = request.url?.path ?: return false
                val file = File(path)
                if (file.exists() && file.absolutePath.startsWith(contentDir.absolutePath)) {
                    return false
                }
            }
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return null
        if (request.isForMainFrame) return null
        if (url.scheme == "file") {
            val path = url.path ?: return null
            val file = File(path)
            if (file.exists() && file.isFile && file.absolutePath.startsWith(contentDir.absolutePath)) {
                return try {
                    val mime = when {
                        file.name.endsWith(".png", true) -> "image/png"
                        file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true) -> "image/jpeg"
                        file.name.endsWith(".gif", true) -> "image/gif"
                        file.name.endsWith(".svg", true) -> "image/svg+xml"
                        file.name.endsWith(".webp", true) -> "image/webp"
                        file.name.endsWith(".css", true) -> "text/css"
                        file.name.endsWith(".js", true) -> "application/javascript"
                        file.name.endsWith(".woff", true) -> "font/woff"
                        file.name.endsWith(".woff2", true) -> "font/woff2"
                        file.name.endsWith(".ttf", true) -> "font/ttf"
                        else -> "application/octet-stream"
                    }
                    WebResourceResponse(mime, "UTF-8", FileInputStream(file))
                } catch (_: Exception) { null }
            }
        }
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HtmlZipNoIndexScreen(
    contentDir: File,
    zipFileName: String,
    onBack: () -> Unit,
    logDebug: ((String) -> Unit)? = null
) {
    val fileList = remember(contentDir) { listHtmlZipContentFiles(contentDir) }
    logDebug?.invoke("[HTMLZIP] 未找到 index.html，显示目录内容，数量=${fileList.size}")
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zipFileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "未找到 index.html",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "压缩包中未找到 index.html、README.html 或其它 .html 文件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "压缩包内容：",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            if (fileList.isEmpty()) {
                Text(
                    text = "（空目录）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(fileList) { filePath ->
                        Text(
                            text = filePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (filePath.endsWith("/")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
