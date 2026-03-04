package com.kenny.localmanager.ui

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.getDirectoryToOpen
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.gpg.GpgHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_MARKDOWN_BYTES = 512 * 1024

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

private class LinkCallbackHolder { var onLink: (String) -> Unit = {} }

/** 判断是否像外链（无 scheme 的域名，如 www.baidu.com）。 */
private fun looksLikeExternalUrl(url: String): Boolean {
    val s = url.trim()
    if (s.isEmpty()) return false
    val scheme = Uri.parse(s).scheme
    if (!scheme.isNullOrEmpty()) return false
    return s.startsWith("www.") || (s.contains(".") && !s.contains("/"))
}

/** 判断是否像主机名（用于 content URI 的 lastPathSegment，如 www.baidu.com）。 */
private fun looksLikeHostname(segment: String): Boolean {
    if (segment.isBlank() || segment.contains("/")) return false
    if (segment.startsWith("www.")) return true
    val idx = segment.indexOf('.')
    if (idx <= 0) return false
    return segment.indexOf('.', idx + 1) > 0 // 至少两个点，避免把 test1.md 当主机名
}

/** 当前文件所在目录 URI。若为 tree URI 则返回同树的父目录 tree URI，否则用 getDirectoryToOpen。 */
private fun getParentDirectoryUri(context: android.content.Context, currentUri: String): Uri? {
    val uri = Uri.parse(currentUri)
    if (currentUri.contains("/tree/")) {
        return try {
            val docId = DocumentsContract.getDocumentId(uri) ?: return null
            val lastSlash = docId.lastIndexOf('/')
            val parentId = if (lastSlash > 0) docId.substring(0, lastSlash) else docId
            val authority = uri.authority ?: return null
            DocumentsContract.buildTreeDocumentUri(authority, parentId)
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

/** 根据当前文件 URI 解析资源 URL，返回同目录下文件的 WebResourceResponse，否则返回 null。 */
private fun resolveResource(
    context: android.content.Context,
    currentUri: String,
    resourceUrl: String
): WebResourceResponse? {
    val segment = Uri.parse(resourceUrl).lastPathSegment ?: resourceUrl.substringAfterLast('/').ifBlank { return null }
    val parentUri = getParentDirectoryUri(context, currentUri) ?: run {
        Log.d(MD_DEBUG, "[图片] 无法取父目录 currentUri=$currentUri resourceUrl=$resourceUrl")
        return null
    }
    val childUri = findChildByName(context, parentUri, segment) ?: run {
        Log.d(MD_DEBUG, "[图片] 父目录下未找到 name=$segment resourceUrl=$resourceUrl")
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
    private val currentUriHolder: ResourceResolverHolder
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
                    raw.readBytesUpTo(MAX_MARKDOWN_BYTES)
                }
                val decoded = bytes.decodeToString()
                val trimmed = decoded.dropLastWhile { it == '\uFFFD' }
                val parser = Parser.builder().build()
                val document = parser.parse(trimmed)
                val renderer = HtmlRenderer.builder().build()
                htmlContent = renderer.render(document)
                Log.d(MD_DEBUG, "[加载] 成功 currentUri=$currentUri 长度=${trimmed.length}")
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
        val isMd = name.endsWith(".md", ignoreCase = true)
        Log.d(MD_DEBUG, "[内链] doc.exists=${doc?.exists()} name=$name isMd=$isMd")
        if (isMd) {
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
        AlertDialog(
            onDismissRequest = { pendingExternalUrl = null },
            title = { Text("打开链接") },
            text = { Text("将用浏览器打开该链接。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingExternalUrl?.let { url ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(Intent.createChooser(intent, "用浏览器打开"))
                        } catch (_: Exception) {}
                    }
                    pendingExternalUrl = null
                }) { Text("打开") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalUrl = null }) { Text("不打开") }
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
                    val baseUrl = currentUri.substringBeforeLast("/") + "/"
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
                        <style>
                            body { background: $bgHex; color: $fgHex; font-size: 16px; padding: 16px; line-height: 1.6; font-family: sans-serif; }
                            h1 { font-size: 1.5em; margin: 0.8em 0 0.4em; }
                            h2 { font-size: 1.3em; margin: 0.8em 0 0.4em; }
                            h3 { font-size: 1.15em; margin: 0.6em 0 0.3em; }
                            pre, code { background: rgba(128,128,128,0.2); padding: 0.2em 0.4em; border-radius: 4px; font-family: monospace; }
                            pre { display: block; padding: 12px; overflow-x: auto; }
                            pre code { padding: 0; background: none; }
                            blockquote { border-left: 4px solid rgba(128,128,128,0.5); margin: 0.5em 0; padding-left: 1em; color: rgba(128,128,128,0.95); }
                            a { color: #2196F3; }
                            ul, ol { margin: 0.5em 0; padding-left: 1.5em; }
                            table { border-collapse: collapse; width: 100%; }
                            th, td { border: 1px solid rgba(128,128,128,0.4); padding: 6px 10px; text-align: left; }
                            th { background: rgba(128,128,128,0.15); }
                        </style>
                    </head>
                    <body>${htmlContent}</body>
                    </html>
                    """.trimIndent()
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(Color.TRANSPARENT)
                                webViewClient = LinkInterceptClient(linkHolder, context, currentUriHolder)
                                settings.domStorageEnabled = false
                                settings.javaScriptEnabled = false
                                loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webView ->
                            currentUriHolder.currentUri = currentUri
                            webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
                        }
                    )
                }
            }
        }
    }
}
