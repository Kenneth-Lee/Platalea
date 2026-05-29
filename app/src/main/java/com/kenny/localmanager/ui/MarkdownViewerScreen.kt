package com.kenny.localmanager.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.MotionEvent
import android.view.WindowManager
import android.view.ViewConfiguration
import android.view.GestureDetector
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.core.text.HtmlCompat
import com.kenny.localmanager.R
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.getDirectoryToOpen
import com.kenny.localmanager.file.listHtmlZipContentFiles
import com.kenny.localmanager.file.listMdZipContentFiles
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.epub.EpubBookmark
import com.kenny.localmanager.epub.EpubBookmarkManager
import com.kenny.localmanager.epub.EpubReadingProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.BottomAppBar
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import com.kenny.localmanager.file.EpubExtractResult
import com.kenny.localmanager.file.getEpubChapterFile
import com.kenny.localmanager.dict.listImportedStarDicts
import com.kenny.localmanager.dict.loadImportedStarDict
import com.kenny.localmanager.dict.lookupExactWord
import com.kenny.localmanager.dict.readStarDictExplanation
import com.kenny.localmanager.dict.StarDictLoaded
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val MAX_MARKDOWN_BYTES = 512 * 1024
private const val MAX_RST_BYTES = 512 * 1024
private const val STANDALONE_MD_CACHE_LIMIT = 6
private const val EPUB_BOOKMARK_QUOTE_MAX_LENGTH = 220
private const val EPUB_BOOKMARK_TARGET_SELECTOR = "p, li, blockquote, pre, h1, h2, h3, h4, h5, h6, td, th, figcaption, div"

@Composable
private fun DraggableNextReadButton(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    initialXPercent: Int,
    initialYPercent: Int,
    onPositionChangePercent: (xPercent: Int, yPercent: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val buttonSize = 58.dp
    val margin = 16.dp
    val buttonSizePx = with(density) { buttonSize.roundToPx().toFloat() }
    val marginPx = with(density) { margin.roundToPx().toFloat() }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var offsetX by remember { mutableStateOf<Float?>(null) }
    var offsetY by remember { mutableStateOf<Float?>(null) }
    var hasUserDragged by remember { mutableStateOf(false) }
    val backgroundColor = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    fun maxOffsetX(): Float = (containerSize.width - buttonSizePx - marginPx).coerceAtLeast(marginPx)
    fun maxOffsetY(): Float = (containerSize.height - buttonSizePx - marginPx).coerceAtLeast(marginPx)

    fun ratioToOffset(percent: Int, maxOffset: Float): Float {
        if (maxOffset <= marginPx) return marginPx
        val p = percent.coerceIn(0, 100) / 100f
        return (marginPx + (maxOffset - marginPx) * p).coerceIn(marginPx, maxOffset)
    }

    fun offsetToRatio(offset: Float, maxOffset: Float): Int {
        if (maxOffset <= marginPx) return 100
        val ratio = ((offset - marginPx) / (maxOffset - marginPx)).coerceIn(0f, 1f)
        return (ratio * 100f).roundToInt()
    }

    LaunchedEffect(initialXPercent, initialYPercent, containerSize, hasUserDragged) {
        if (containerSize == IntSize.Zero || hasUserDragged) return@LaunchedEffect
        val boundedMaxX = maxOffsetX()
        val boundedMaxY = maxOffsetY()
        offsetX = ratioToOffset(initialXPercent, boundedMaxX)
        offsetY = ratioToOffset(initialYPercent, boundedMaxY)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerSize = coordinates.size
                val boundedMaxX = maxOffsetX()
                val boundedMaxY = maxOffsetY()
                offsetX = offsetX?.coerceIn(marginPx, boundedMaxX)
                    ?: ratioToOffset(initialXPercent, boundedMaxX)
                offsetY = offsetY?.coerceIn(marginPx, boundedMaxY)
                    ?: ratioToOffset(initialYPercent, boundedMaxY)
            }
    ) {
        val currentOffsetX = offsetX
        val currentOffsetY = offsetY
        if (currentOffsetX != null && currentOffsetY != null) {
            Surface(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .offset { IntOffset(currentOffsetX.roundToInt(), currentOffsetY.roundToInt()) }
                    .size(buttonSize)
                    .pointerInput(enabled, containerSize) {
                        detectDragGestures(
                            onDragEnd = {
                                val boundedMaxX = maxOffsetX()
                                val boundedMaxY = maxOffsetY()
                                val finalX = (offsetX ?: boundedMaxX).coerceIn(marginPx, boundedMaxX)
                                val finalY = (offsetY ?: boundedMaxY).coerceIn(marginPx, boundedMaxY)
                                hasUserDragged = true
                                onPositionChangePercent(
                                    offsetToRatio(finalX, boundedMaxX),
                                    offsetToRatio(finalY, boundedMaxY)
                                )
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (containerSize == IntSize.Zero) return@detectDragGestures
                            val boundedMaxX = maxOffsetX()
                            val boundedMaxY = maxOffsetY()
                            offsetX = ((offsetX ?: boundedMaxX) + dragAmount.x).coerceIn(marginPx, boundedMaxX)
                            offsetY = ((offsetY ?: boundedMaxY) + dragAmount.y).coerceIn(marginPx, boundedMaxY)
                        }
                    },
                shape = CircleShape,
                color = backgroundColor,
                contentColor = contentColor,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/** 词典查询结果 */
data class DictLookupResult(
    val word: String,
    val definition: String?,
    val error: String?
)

/**
 * 从 WebView 中提取指定位置的单词
 * @param webView WebView 实例
 * @param x 点击的 x 坐标
 * @param y 点击的 y 坐标
 * @param callback 回调函数，返回提取的单词（可能为空）
 */

class MarkdownViewerSessionCache(
    private val maxEntries: Int = STANDALONE_MD_CACHE_LIMIT
) {
    private val signatureMap: MutableMap<String, String> = mutableMapOf()
    val htmlCache: MutableMap<String, String> = mutableMapOf()
    val webViewMap: MutableMap<String, WebView> = mutableMapOf()
    val pageReadyMap: MutableMap<String, Boolean> = mutableMapOf()
    private val accessOrder = mutableListOf<String>()

    fun getHtml(key: String, signature: String): String? {
        if (signatureMap[key] != signature) {
            invalidate(key)
            return null
        }
        return htmlCache[key]?.also { touch(key) }
    }

    fun putHtml(key: String, html: String, signature: String) {
        signatureMap[key] = signature
        htmlCache[key] = html
        touch(key)
    }

    fun getWebView(key: String): WebView? = webViewMap[key]?.also { touch(key) }

    fun putWebView(key: String, webView: WebView) {
        webViewMap[key] = webView
        touch(key)
    }

    fun setPageReady(key: String, ready: Boolean) {
        pageReadyMap[key] = ready
        touch(key)
    }

    fun clear() {
        accessOrder.toList().forEach { evict(it) }
        accessOrder.clear()
    }

    fun invalidate(key: String) {
        accessOrder.remove(key)
        evict(key)
    }

    fun invalidateByUri(uri: String) {
        accessOrder.toList()
            .filter { it.startsWith("$uri:") }
            .forEach { invalidate(it) }
    }

    private fun touch(key: String) {
        accessOrder.remove(key)
        accessOrder.add(key)
        trimIfNeeded()
    }

    private fun trimIfNeeded() {
        while (accessOrder.size > maxEntries) {
            evict(accessOrder.removeAt(0))
        }
    }

    private fun evict(key: String) {
        signatureMap.remove(key)
        htmlCache.remove(key)
        pageReadyMap.remove(key)
        webViewMap.remove(key)?.let { webView ->
            runCatching { (webView.parent as? android.view.ViewGroup)?.removeView(webView) }
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            }
        }
    }
}

private fun computeMarkdownCacheSignature(context: Context, uriString: String): String {
    val uri = Uri.parse(uriString)
    return when (uri.scheme?.lowercase()) {
        "file" -> {
            val file = File(uri.path ?: "")
            "${file.lastModified()}:${file.length()}"
        }
        else -> {
            val doc = DocumentFile.fromSingleUri(context, uri)
                ?: DocumentFile.fromTreeUri(context, uri)
            val lastModified = doc?.lastModified() ?: -1L
            val length = doc?.length() ?: -1L
            "$lastModified:$length"
        }
    }
}

private fun computeMarkdownCacheSignature(file: File): String =
    "${file.lastModified()}:${file.length()}"

/** 将 Markdown 转为 HTML：支持表格、删除线(~~ 与 ~~~)、任务列表、脚注、标题锚点。脚注定义需写为 [^1]: 内容。 */
private fun markdownToHtml(md: String): String {
    val preprocessed = preprocessMarkdown(md)
    val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
        FootnotesExtension.create(),
        HeadingAnchorExtension.create()
    )
    val parser = Parser.builder().extensions(extensions).build()
    val renderer = HtmlRenderer.builder().extensions(extensions).build()
    return renderer.render(parser.parse(preprocessed))
}

private fun preprocessMarkdown(md: String): String {
    val normalizedFootnotes = normalizeMarkdownFootnoteLabels(md.replace("~~~", "~~"))
    return applyMarkdownSupSubSyntax(normalizedFootnotes)
}

private fun normalizeMarkdownFootnoteLabels(md: String): String {
    val numericLabels = LinkedHashSet<String>()
    Regex("""(?m)^\[\^(\d+)]:""").findAll(md).forEach { numericLabels += it.groupValues[1] }
    Regex("""\[\^(\d+)]""").findAll(md).forEach { numericLabels += it.groupValues[1] }
    if (numericLabels.isEmpty()) return md

    var prefix = "__lm_numfn_"
    while (md.contains("[^$prefix") || md.contains(prefix)) {
        prefix = "_$prefix"
    }

    var normalized = md
    numericLabels.forEach { label ->
        val mapped = "$prefix$label"
        normalized = normalized.replace(Regex("""\[\^${Regex.escape(label)}]:(?=\s)"""), "[^$mapped]:")
        normalized = normalized.replace(Regex("""\[\^${Regex.escape(label)}]"""), "[^$mapped]")
    }
    return normalized
}

private fun applyMarkdownSupSubSyntax(md: String): String {
    val lines = md.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val out = StringBuilder()
    var inFence = false

    lines.forEachIndexed { index, line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            out.append(line)
        } else if (inFence || trimmed.startsWith("    ") || line.startsWith("\t")) {
            out.append(line)
        } else {
            out.append(transformMarkdownSupSubInline(line))
        }
        if (index < lines.lastIndex) out.append('\n')
    }
    return out.toString()
}

private fun transformMarkdownSupSubInline(line: String): String {
    val out = StringBuilder()
    var index = 0
    var inCode = false

    while (index < line.length) {
        val ch = line[index]
        if (ch == '`') {
            inCode = !inCode
            out.append(ch)
            index++
            continue
        }
        if (!inCode && ch == '^') {
            val closing = line.indexOf('^', index + 1)
            if (closing > index + 1) {
                val content = line.substring(index + 1, closing)
                if (isValidMarkdownSupSubContent(content)) {
                    out.append("<sup>").append(content).append("</sup>")
                    index = closing + 1
                    continue
                }
            }
        }
        if (!inCode && ch == '~' && line.getOrNull(index - 1) != '~' && line.getOrNull(index + 1) != '~') {
            val closing = line.indexOf('~', index + 1)
            if (closing > index + 1 && line.getOrNull(closing - 1) != '~' && line.getOrNull(closing + 1) != '~') {
                val content = line.substring(index + 1, closing)
                if (isValidMarkdownSupSubContent(content)) {
                    out.append("<sub>").append(content).append("</sub>")
                    index = closing + 1
                    continue
                }
            }
        }
        out.append(ch)
        index++
    }

    return out.toString()
}

private fun isValidMarkdownSupSubContent(content: String): Boolean {
    if (content.isBlank()) return false
    if (content.first().isWhitespace() || content.last().isWhitespace()) return false
    if ('<' in content || '>' in content) return false
    return true
}

private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private fun normalizeCodeLanguage(raw: String?): String? {
    val lang = raw?.trim()?.lowercase()?.ifBlank { null } ?: return null
    return when (lang) {
        "c" -> "c"
        "c++", "cpp", "cxx", "cc", "hpp", "hxx" -> "cpp"
        "python", "py" -> "python"
        "java" -> "java"
        "kotlin", "kt", "kts" -> "kotlin"
        "bash", "sh", "shell", "zsh" -> "bash"
        else -> lang.replace(Regex("[^a-z0-9_+\\-]"), "")
    }
}

private fun buildCodeBlockHtml(codeLines: List<String>, language: String? = null): String {
    val langClass = normalizeCodeLanguage(language)?.takeIf { it.isNotBlank() }?.let { " class=\"language-$it\"" } ?: ""
    return "<pre><code$langClass>${codeLines.joinToString("\n")}</code></pre>"
}

private fun syntaxHighlightStyle(): String = """
        pre { background: rgba(128,128,128,0.15); }
        pre code.syntax-highlighted { background: none; }
        .hl-keyword { color: #b03a2e; font-weight: 600; }
        .hl-type { color: #7d3c98; }
        .hl-string { color: #117a65; }
        .hl-comment { color: #6c757d; font-style: italic; }
        .hl-number { color: #b9770e; }
        .hl-function { color: #1f618d; }
""".trimIndent()

private fun syntaxHighlightScript(): String = """
        (function() {
            function esc(s) {
                return s
                    .replace(/&/g, "&amp;")
                    .replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;");
            }

            function aliasLang(lang) {
                if (!lang) return null;
                var l = String(lang).toLowerCase();
                if (l === "c") return "c";
                if (["cpp", "c++", "cxx", "cc", "hpp", "hxx"].indexOf(l) >= 0) return "cpp";
                if (["python", "py"].indexOf(l) >= 0) return "python";
                if (l === "java") return "java";
                if (["kotlin", "kt", "kts"].indexOf(l) >= 0) return "kotlin";
                if (["bash", "sh", "shell", "zsh"].indexOf(l) >= 0) return "bash";
                return null;
            }

            function detectLang(el) {
                var classes = el.className ? el.className.split(/\s+/) : [];
                for (var i = 0; i < classes.length; i++) {
                    if (classes[i].indexOf("language-") === 0) {
                        return aliasLang(classes[i].substring("language-".length));
                    }
                }
                var dataLang = el.getAttribute("data-lang");
                return aliasLang(dataLang);
            }

            function cfgFor(lang) {
                var cLikeKeywords = {
                    "if":1,"else":1,"for":1,"while":1,"do":1,"switch":1,"case":1,"default":1,
                    "break":1,"continue":1,"return":1,"goto":1,"sizeof":1,"typedef":1,"const":1,
                    "static":1,"extern":1,"inline":1,"volatile":1,"enum":1,"struct":1,"class":1,
                    "public":1,"private":1,"protected":1,"new":1,"delete":1,"try":1,"catch":1,
                    "throw":1,"throws":1,"finally":1,"import":1,"package":1,"namespace":1,"using":1,
                    "template":1,"this":1,"super":1,"override":1,"virtual":1,"final":1,"sealed":1
                };
                var cLikeTypes = {
                    "void":1,"int":1,"long":1,"short":1,"char":1,"float":1,"double":1,"bool":1,
                    "auto":1,"unsigned":1,"signed":1,"size_t":1,"string":1,"String":1,"Object":1,
                    "var":1,"val":1,"fun":1,"Unit":1,"Any":1,"Nothing":1,"List":1,"Map":1,"Set":1
                };
                var pythonKeywords = {
                    "def":1,"class":1,"if":1,"elif":1,"else":1,"for":1,"while":1,"break":1,
                    "continue":1,"return":1,"import":1,"from":1,"as":1,"try":1,"except":1,
                    "finally":1,"raise":1,"with":1,"lambda":1,"yield":1,"pass":1,"global":1,
                    "nonlocal":1,"assert":1,"del":1,"in":1,"is":1,"not":1,"and":1,"or":1
                };
                var bashKeywords = {
                    "if":1,"then":1,"else":1,"fi":1,"for":1,"in":1,"do":1,"done":1,"while":1,
                    "case":1,"esac":1,"function":1,"local":1,"export":1,"unset":1,"return":1,
                    "break":1,"continue":1,"source":1
                };

                if (lang === "python") {
                    return { lineComment: "#", blockStart: null, blockEnd: null, keywords: pythonKeywords, types: {} };
                }
                if (lang === "bash") {
                    return { lineComment: "#", blockStart: null, blockEnd: null, keywords: bashKeywords, types: {} };
                }
                return { lineComment: "//", blockStart: "/*", blockEnd: "*/", keywords: cLikeKeywords, types: cLikeTypes };
            }

            function highlightCode(code, lang) {
                var cfg = cfgFor(lang);
                var out = "";
                var i = 0;

                function span(cls, txt) {
                    return '<span class="' + cls + '">' + esc(txt) + '</span>';
                }

                while (i < code.length) {
                    var ch = code[i];

                    if (cfg.blockStart && code.substring(i, i + cfg.blockStart.length) === cfg.blockStart) {
                        var endIdx = code.indexOf(cfg.blockEnd, i + cfg.blockStart.length);
                        if (endIdx < 0) endIdx = code.length - cfg.blockEnd.length;
                        var block = code.substring(i, endIdx + cfg.blockEnd.length);
                        out += span("hl-comment", block);
                        i = endIdx + cfg.blockEnd.length;
                        continue;
                    }

                    if (cfg.lineComment && code.substring(i, i + cfg.lineComment.length) === cfg.lineComment) {
                        var lineEnd = code.indexOf("\n", i);
                        if (lineEnd < 0) lineEnd = code.length;
                        var lineText = code.substring(i, lineEnd);
                        out += span("hl-comment", lineText);
                        i = lineEnd;
                        continue;
                    }

                    if (ch === '"' || ch === "'" || ch === "`") {
                        var q = ch;
                        var j = i + 1;
                        while (j < code.length) {
                            if (code[j] === "\\") {
                                j += 2;
                                continue;
                            }
                            if (code[j] === q) {
                                j++;
                                break;
                            }
                            j++;
                        }
                        out += span("hl-string", code.substring(i, j));
                        i = j;
                        continue;
                    }

                    if ((ch >= "0" && ch <= "9") || (ch === "." && i + 1 < code.length && code[i + 1] >= "0" && code[i + 1] <= "9")) {
                        var n = i + 1;
                        while (n < code.length && /[0-9a-fA-FxX._]/.test(code[n])) n++;
                        out += span("hl-number", code.substring(i, n));
                        i = n;
                        continue;
                    }

                    if (/[A-Za-z_]/.test(ch)) {
                        var k = i + 1;
                        while (k < code.length && /[A-Za-z0-9_]/.test(code[k])) k++;
                        var word = code.substring(i, k);
                        if (cfg.keywords[word]) {
                            out += span("hl-keyword", word);
                        } else if (cfg.types[word]) {
                            out += span("hl-type", word);
                        } else {
                            var nextPos = k;
                            while (nextPos < code.length && /\s/.test(code[nextPos])) nextPos++;
                            if (nextPos < code.length && code[nextPos] === "(") out += span("hl-function", word);
                            else out += esc(word);
                        }
                        i = k;
                        continue;
                    }

                    out += esc(ch);
                    i++;
                }

                return out;
            }

            window.applySyntaxHighlight = function(root) {
                var scope = root || document;
                var blocks = scope.querySelectorAll("pre > code");
                blocks.forEach(function(codeEl) {
                    var lang = detectLang(codeEl);
                    if (!lang) return;
                    if (lang === "mermaid") return;
                    var raw = codeEl.textContent || "";
                    codeEl.innerHTML = highlightCode(raw, lang);
                    codeEl.classList.add("syntax-highlighted");
                });
            };
        })();
""".trimIndent()

private data class RstFootnoteDefinition(
    val label: String,
    val html: String
)

private data class RstTableParseResult(
    val html: String,
    val nextIndex: Int
)

private fun countIndent(line: String): Int {
    var count = 0
    for (ch in line) {
        when (ch) {
            ' ' -> count += 1
            '\t' -> count += 4
            else -> return count
        }
    }
    return count
}

private fun trimIndentColumns(line: String, columns: Int): String {
    var remaining = columns
    var index = 0
    while (index < line.length && remaining > 0) {
        when (line[index]) {
            ' ' -> remaining -= 1
            '\t' -> remaining -= 4
            else -> break
        }
        index++
    }
    return line.substring(index)
}

private fun collectIndentedBlock(lines: List<String>, startIndex: Int): Pair<List<String>, Int> {
    var index = startIndex
    val raw = mutableListOf<String>()
    var minIndent = Int.MAX_VALUE
    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            raw += ""
            index++
            continue
        }
        val indent = countIndent(line)
        if (indent <= 0) break
        minIndent = minOf(minIndent, indent)
        raw += line
        index++
    }
    if (raw.isEmpty()) return emptyList<String>() to startIndex
    if (minIndent == Int.MAX_VALUE) minIndent = 0
    return raw.map { if (it.isBlank()) "" else trimIndentColumns(it, minIndent) } to index
}

private fun parseDirectiveOptions(lines: List<String>, startIndex: Int): Pair<Map<String, String>, Int> {
    var index = startIndex
    val options = linkedMapOf<String, String>()
    while (index < lines.size) {
        val trimmed = lines[index].trimStart()
        if (!trimmed.startsWith(":")) break
        val end = trimmed.indexOf(':', startIndex = 1)
        if (end <= 1) break
        val key = trimmed.substring(1, end).trim().lowercase()
        val value = trimmed.substring(end + 1).trim()
        options[key] = value
        index++
    }
    return options to index
}

private fun parseRstDirectivePath(trimmedLine: String): String? {
    val pathMatch = Regex("""\.\.\s+\w+(?:-\w+)?\s*::\s*(?:"([^"]*)"|'([^']*)'|(\S+))""").find(trimmedLine)
    return pathMatch?.groupValues?.let { groups ->
        groups[1].ifBlank { groups[2].ifBlank { groups[3] } }.trim().takeIf { it.isNotBlank() }
    }
}

private fun buildRstImageStyle(options: Map<String, String>): String {
    val styles = mutableListOf<String>()
    options["width"]?.takeIf { it.isNotBlank() }?.let { styles += "width:$it" }
    options["height"]?.takeIf { it.isNotBlank() }?.let { styles += "height:$it" }
    options["scale"]?.toIntOrNull()?.takeIf { it > 0 }?.let { styles += "max-width:${it}%" }
    when (options["align"]?.lowercase()) {
        "center" -> styles += listOf("display:block", "margin-left:auto", "margin-right:auto")
        "right" -> styles += listOf("display:block", "margin-left:auto")
        "left" -> styles += listOf("display:block", "margin-right:auto")
    }
    return styles.joinToString("; ")
}

private fun renderRstInlineText(text: String): String = rstInlineToHtml(escapeHtml(text.trim()))

private fun sanitizeRstFootnoteId(label: String): String =
    label.removePrefix("#").replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifBlank { "fn" }

private fun extractRstFootnotes(lines: List<String>): Pair<List<String>, List<RstFootnoteDefinition>> {
    val bodyLines = mutableListOf<String>()
    val footnotes = mutableListOf<RstFootnoteDefinition>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val match = Regex("""^\.\.\s+\[([^\]]+)]\s*(.*)$""").find(line.trim())
        if (match != null) {
            val label = match.groupValues[1].trim()
            val parts = mutableListOf<String>()
            val first = match.groupValues[2].trim()
            if (first.isNotBlank()) parts += first
            index++
            val (block, nextIndex) = collectIndentedBlock(lines, index)
            block.filter { it.isNotBlank() }.forEach { parts += it.trim() }
            index = nextIndex
            footnotes += RstFootnoteDefinition(label = label, html = renderRstInlineText(parts.joinToString(" ")))
            continue
        }
        bodyLines += line
        index++
    }
    return bodyLines to footnotes
}

private fun renderRstFootnotes(footnotes: List<RstFootnoteDefinition>): String {
    if (footnotes.isEmpty()) return ""
    return buildString {
        append("<section class=\"footnotes\"><hr><ol>")
        footnotes.forEach { footnote ->
            val id = sanitizeRstFootnoteId(footnote.label)
            append("<li id=\"fn-").append(id).append("\">")
            append(footnote.html)
            append(" <a href=\"#fnref-").append(id).append("\" class=\"footnote-backref\">↩</a></li>")
        }
        append("</ol></section>")
    }
}

private fun consumeRstCommentBlock(lines: List<String>, startIndex: Int): Int {
    var index = startIndex + 1
    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index++
            continue
        }
        if (countIndent(line) <= 0) break
        index++
    }
    return index
}

private fun isRstGridBorder(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("+") && trimmed.endsWith("+") && trimmed.all { it == '+' || it == '-' || it == '=' }
}

private fun parseRstGridTable(lines: List<String>, startIndex: Int): RstTableParseResult? {
    if (!isRstGridBorder(lines[startIndex])) return null
    var index = startIndex
    val rows = mutableListOf<List<String>>()
    val headerRows = mutableSetOf<Int>()
    while (index < lines.size && isRstGridBorder(lines[index])) {
        val border = lines[index].trim()
        val cuts = border.mapIndexedNotNull { pos, ch -> pos.takeIf { ch == '+' } }
        if (cuts.size < 2) return null
        index++
        val contentLines = mutableListOf<String>()
        while (index < lines.size && !isRstGridBorder(lines[index])) {
            contentLines += lines[index]
            index++
        }
        if (contentLines.isEmpty()) continue
        val cells = MutableList(cuts.size - 1) { StringBuilder() }
        contentLines.forEach { rowLine ->
            for (cellIndex in 0 until cuts.lastIndex) {
                val from = (cuts[cellIndex] + 1).coerceAtMost(rowLine.length)
                val to = cuts[cellIndex + 1].coerceAtMost(rowLine.length)
                val text = if (from <= to) rowLine.substring(from, to).trim() else ""
                if (text.isNotEmpty()) {
                    if (cells[cellIndex].isNotEmpty()) cells[cellIndex].append(' ')
                    cells[cellIndex].append(text)
                }
            }
        }
        rows += cells.map { renderRstInlineText(it.toString()) }
        if (index < lines.size && lines[index].contains('=')) headerRows += rows.lastIndex
    }
    if (rows.isEmpty()) return null
    return RstTableParseResult(renderRstTableHtml(rows, headerRows), index)
}

private fun isRstSimpleTableBorder(line: String): Boolean =
    line.trim().matches(Regex("""^=+(?:\t| {2,})=+(?:.*=+)*$""")) || line.trim().matches(Regex("""^=+(?:\s+=+)+$"""))

private fun splitSimpleTableSegments(line: String, ranges: List<IntRange>): List<String> =
    ranges.map { range ->
        if (range.first >= line.length) "" else line.substring(range.first, minOf(line.length, range.last + 1)).trim()
    }

private fun parseRstSimpleTable(lines: List<String>, startIndex: Int): RstTableParseResult? {
    val top = lines[startIndex].trimEnd()
    if (!isRstSimpleTableBorder(top)) return null
    val ranges = Regex("=+").findAll(top).map { it.range }.toList()
    if (ranges.isEmpty()) return null
    var index = startIndex + 1
    val headerLines = mutableListOf<String>()
    while (index < lines.size && !isRstSimpleTableBorder(lines[index])) {
        headerLines += lines[index]
        index++
    }
    if (index >= lines.size || !isRstSimpleTableBorder(lines[index])) return null
    val headerRow = splitSimpleTableSegments(headerLines.joinToString(" "), ranges).map { renderRstInlineText(it) }
    index++
    val rows = mutableListOf<List<String>>()
    while (index < lines.size && !isRstSimpleTableBorder(lines[index])) {
        if (lines[index].isBlank()) {
            index++
            continue
        }
        rows += splitSimpleTableSegments(lines[index], ranges).map { renderRstInlineText(it) }
        index++
    }
    if (index >= lines.size || !isRstSimpleTableBorder(lines[index])) return null
    index++
    return RstTableParseResult(renderRstTableHtml(listOf(headerRow) + rows, setOf(0)), index)
}

private fun renderRstTableHtml(rows: List<List<String>>, headerRows: Set<Int> = emptySet(), caption: String? = null): String {
    if (rows.isEmpty()) return ""
    val maxColumns = rows.maxOf { it.size }
    return buildString {
        append("<table>")
        caption?.takeIf { it.isNotBlank() }?.let { append("<caption>").append(renderRstInlineText(it)).append("</caption>") }
        if (headerRows.isNotEmpty()) {
            append("<thead>")
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex !in headerRows) return@forEachIndexed
                append("<tr>")
                repeat(maxColumns) { col -> append("<th>").append(row.getOrElse(col) { "" }).append("</th>") }
                append("</tr>")
            }
            append("</thead>")
        }
        append("<tbody>")
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex in headerRows) return@forEachIndexed
            append("<tr>")
            repeat(maxColumns) { col -> append("<td>").append(row.getOrElse(col) { "" }).append("</td>") }
            append("</tr>")
        }
        append("</tbody></table>")
    }
}

private fun parseRstListTable(lines: List<String>, startIndex: Int): RstTableParseResult? {
    val title = lines[startIndex].trim().substringAfter("::", "").trim().ifBlank { null }
    var index = startIndex + 1
    val (options, afterOptions) = parseDirectiveOptions(lines, index)
    index = afterOptions
    if (index < lines.size && lines[index].isBlank()) index++
    val (block, nextIndex) = collectIndentedBlock(lines, index)
    if (block.isEmpty()) return null
    val rows = mutableListOf<MutableList<String>>()
    var currentRow: MutableList<String>? = null
    var currentCellIndex = -1
    block.forEach { rawLine ->
        val trimmed = rawLine.trimStart()
        when {
            trimmed.startsWith("* -") -> {
                currentRow = mutableListOf(trimmed.removePrefix("* -").trim())
                rows += currentRow!!
                currentCellIndex = 0
            }
            trimmed.startsWith("-") && currentRow != null -> {
                currentRow!!.add(trimmed.removePrefix("-").trim())
                currentCellIndex = currentRow!!.lastIndex
            }
            trimmed.isNotBlank() && currentRow != null && currentCellIndex >= 0 -> {
                val existing = currentRow!![currentCellIndex]
                currentRow!![currentCellIndex] = if (existing.isBlank()) trimmed else "$existing $trimmed"
            }
        }
    }
    if (rows.isEmpty()) return null
    val headerRows = options["header-rows"]?.toIntOrNull()?.coerceAtLeast(0)?.let { count ->
        (0 until minOf(count, rows.size)).toSet()
    } ?: emptySet()
    val renderedRows = rows.map { row -> row.map { renderRstInlineText(it) } }
    return RstTableParseResult(renderRstTableHtml(renderedRows, headerRows, title), nextIndex)
}

/** RST 中独立一行的“引用段/字面块”标记，不参与显示；仅用于引入后续缩进块。支持 "::"、":: " 等。 */
private fun isRstStandaloneLiteralMarker(trimmed: String): Boolean =
    trimmed == ".." || trimmed.matches(Regex("^::\\s*$"))

/** 简易 reStructuredText → HTML，覆盖标题、图片、表格、list-table、代码高亮、脚注等常用语法。
 * 块类型判定顺序（避免改一处破坏另一处）：
 * 1) 标题(下划线) 2) 网格/简单表格 3) list-table 4) figure/image 5) math 6) code-block 7) 元数据/注释
 * 8) 无序/有序列表 9) 独立 "::"/".." 引用段标记 10) 空行 11) 以空格/制表开头的缩进块 12) 普通段落(含段末::引用段)
 */
private fun rstToHtml(rst: String, showMeta: Boolean = false): String {
    val normalizedLines = rst.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val (lines, footnotes) = extractRstFootnotes(normalizedLines)
    val out = StringBuilder()
    var i = 0
    // RST 标题级别映射：按首次出现顺序确定级别
    fun collectUnderlineLevels(lines: List<String>): Map<Char, Int> {
        val levelMap = mutableMapOf<Char, Int>()
        var currentLevel = 1
        for (line in lines) {
            val trimmed = line.trim()
            val underline = if (trimmed.isNotEmpty() && trimmed.all { ch ->
                ch in charArrayOf('=', '-', '`', '.', '\'', '"', '~', '^', '_', '*', '#', '+')
            }) trimmed[0] else null
            if (underline != null && !levelMap.containsKey(underline)) {
                levelMap[underline] = currentLevel++
            }
        }
        return levelMap
    }
    val underlineLevels = collectUnderlineLevels(lines)
    fun getLevelFromUnderline(c: Char): Int = underlineLevels.getOrDefault(c, 2)

    // 标题编号计数器
    var h1Number = 0
    var h2Number = 0
    var h3Number = 0
    var h4Number = 0
    var h5Number = 0
    var h6Number = 0

    fun peekUnderlineChar(line: String): Char? =
        line.takeIf {
            it.isNotEmpty() && it.all { ch -> ch == it[0] && ch in charArrayOf('=', '-', '`', '.', '\'', '"', '~', '^', '_', '*', '#', '+') }
        }?.get(0)
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        val next = lines.getOrNull(i + 1)
        val underline = next?.let { peekUnderlineChar(it) }
        if (underline != null && next.isNotBlank() && trimmed.isNotBlank() && next.length >= trimmed.length) {
            val level = getLevelFromUnderline(underline)
            val tag = "h${level.coerceIn(1, 6)}"
            // 添加标题编号
            val titleText = when (level) {
                1 -> {
                    h1Number++
                    h2Number = 0
                    h3Number = 0
                    h4Number = 0
                    h5Number = 0
                    h6Number = 0
                    "$h1Number. $trimmed"
                }
                2 -> {
                    h2Number++
                    h3Number = 0
                    h4Number = 0
                    h5Number = 0
                    h6Number = 0
                    "$h1Number.$h2Number. $trimmed"
                }
                3 -> {
                    h3Number++
                    h4Number = 0
                    h5Number = 0
                    h6Number = 0
                    "$h1Number.$h2Number.$h3Number. $trimmed"
                }
                4 -> {
                    h4Number++
                    h5Number = 0
                    h6Number = 0
                    "$h1Number.$h2Number.$h3Number.$h4Number. $trimmed"
                }
                5 -> {
                    h5Number++
                    h6Number = 0
                    "$h1Number.$h2Number.$h3Number.$h4Number.$h5Number. $trimmed"
                }
                6 -> {
                    h6Number++
                    "$h1Number.$h2Number.$h3Number.$h4Number.$h5Number.$h6Number. $trimmed"
                }
                else -> "$level. $trimmed"
            }
            out.append("<").append(tag).append(">")
                .append(renderRstInlineText(titleText))
                .append("</").append(tag).append(">")
            i += 2
            continue
        }
        val gridTable = parseRstGridTable(lines, i)
        if (gridTable != null) {
            out.append(gridTable.html)
            i = gridTable.nextIndex
            continue
        }
        val simpleTable = parseRstSimpleTable(lines, i)
        if (simpleTable != null) {
            out.append(simpleTable.html)
            i = simpleTable.nextIndex
            continue
        }
        if (trimmed.startsWith(".. list-table::")) {
            val parsed = parseRstListTable(lines, i)
            if (parsed != null) {
                out.append(parsed.html)
                i = parsed.nextIndex
            } else {
                i = consumeRstCommentBlock(lines, i)
            }
            continue
        }
        if (trimmed.startsWith(".. figure::") || trimmed.startsWith(".. image::")) {
            val path = parseRstDirectivePath(trimmed)
            var index = i + 1
            val (options, afterOptions) = parseDirectiveOptions(lines, index)
            index = afterOptions
            if (index < lines.size && lines[index].isBlank()) index++
            val captionLines = if (trimmed.startsWith(".. figure::")) {
                val (block, nextIndex) = collectIndentedBlock(lines, index)
                index = nextIndex
                block.filter { it.isNotBlank() }
            } else emptyList()
            if (path != null) {
                val style = buildRstImageStyle(options)
                val alt = escapeHtmlAttr(options["alt"].orEmpty())
                if (trimmed.startsWith(".. figure::")) out.append("<figure>")
                out.append("<img src=\"").append(escapeHtml(path)).append("\" alt=\"").append(alt).append("\"")
                if (style.isNotBlank()) out.append(" style=\"").append(escapeHtmlAttr(style)).append("\"")
                out.append(">")
                if (captionLines.isNotEmpty()) {
                    out.append("<figcaption>")
                        .append(captionLines.joinToString(" ") { renderRstInlineText(it) })
                        .append("</figcaption>")
                }
                if (trimmed.startsWith(".. figure::")) out.append("</figure>")
            }
            i = index
            continue
        }
        if (trimmed.startsWith(".. math::")) {
            val restOfLine = trimmed.removePrefix(".. math::").trim()
            val mathLines = mutableListOf<String>()
            if (restOfLine.isNotBlank()) mathLines += escapeHtml(restOfLine)
            i++
            val (block, nextIndex) = collectIndentedBlock(lines, i)
            block.filter { it.isNotBlank() }.forEach { mathLines += escapeHtml(it.trim()) }
            i = nextIndex
            if (mathLines.isNotEmpty()) {
                var latex = mathLines.joinToString(" \\\\ ").replace("&amp;", "&")
                if (mathLines.size > 1 || latex.contains("&=") || latex.contains('&')) {
                    latex = "\\begin{aligned}$latex\\end{aligned}"
                }
                out.append("""<div class="katex-display" data-latex="${escapeHtmlAttr(latex)}"></div>""")
            }
            continue
        }
        if (trimmed.startsWith(".. code-block::") || trimmed.startsWith(".. sourcecode::")) {
            val language = normalizeCodeLanguage(trimmed.substringAfter("::", "").trim())
            i++
            val (_, afterOptions) = parseDirectiveOptions(lines, i)
            i = afterOptions
            if (i < lines.size && lines[i].isBlank()) i++
            val (block, nextIndex) = collectIndentedBlock(lines, i)
            i = nextIndex
            val codeLines = block.filter { it.isNotBlank() }.map { escapeHtml(it) }
            if (codeLines.isNotEmpty()) out.append(buildCodeBlockHtml(codeLines, language))
            continue
        }
        // 文档元数据标记，如 :Authors:、:Version:、:Date:、:dtag:`xxx` 等，通常不显示
        if (!showMeta && trimmed.matches(Regex("^:\\w+:.*"))) {
            i = consumeRstCommentBlock(lines, i)
            continue
        }
        if (trimmed.startsWith(".. ") && !trimmed.startsWith(".. [")) {
            i = consumeRstCommentBlock(lines, i)
            continue
        }
        // 处理无序列表（- * •）
        if (trimmed.matches(Regex("^[-*•]\\s.+"))) {
            out.append("<ul>")
            while (i < lines.size && lines[i].isNotBlank()) {
                val currentLine = lines[i]
                val currentTrimmed = currentLine.trim()
                // 检查是否是列表项：要么匹配列表项正则，要么缩进大于0且前一行是列表项或其续行
                val isListItem = currentTrimmed.matches(Regex("^[-*•]\\s.+"))
                val isIndentedContinuation = countIndent(currentLine) > 0 && i > 0 && (lines[i - 1].trim().matches(Regex("^[-*•]\\s.+")) || countIndent(lines[i - 1]) > 0)
                if (isListItem) {
                    val content = currentTrimmed.drop(1).trim()
                    out.append("<li>").append(renderRstInlineText(content))
                } else if (isIndentedContinuation) {
                    // 列表项内的缩进行，作为列表项内容的一部分（与前一行合并）
                    val content = currentLine.trim()
                    out.append(" ").append(renderRstInlineText(content))
                } else {
                    break
                }
                i++
            }
            out.append("</li></ul>")
            continue
        }
        // 处理有序列表（1. 2. 等）
        if (trimmed.matches(Regex("^\\d+\\.\\s.+"))) {
            out.append("<ol>")
            while (i < lines.size && lines[i].isNotBlank()) {
                val currentLine = lines[i]
                val currentTrimmed = currentLine.trim()
                // 检查是否是列表项：要么匹配列表项正则，要么缩进大于0且前一行是列表项或其续行
                val isListItem = currentTrimmed.matches(Regex("^\\d+\\.\\s.+"))
                val isIndentedContinuation = countIndent(currentLine) > 0 && i > 0 && (lines[i - 1].trim().matches(Regex("^\\d+\\.\\s.+")) || countIndent(lines[i - 1]) > 0)
                if (isListItem) {
                    val content = currentTrimmed.replaceFirst(Regex("^\\d+\\.\\s"), "")
                    out.append("<li>").append(renderRstInlineText(content))
                } else if (isIndentedContinuation) {
                    // 列表项内的缩进行，作为列表项内容的一部分（与前一行合并）
                    val content = currentLine.trim()
                    out.append(" ").append(renderRstInlineText(content))
                } else {
                    break
                }
                i++
            }
            out.append("</li></ol>")
            continue
        }
        // 独立一行的 "::" / ".."：仅作引用段标记，不输出；其后（可含空行）的缩进块为字面内容
        if (isRstStandaloneLiteralMarker(trimmed)) {
            i++
            val (block, nextIndex) = collectIndentedBlock(lines, i)
            i = nextIndex
            val codeLines = block.filter { it.isNotBlank() }.map { escapeHtml(it) }
            if (codeLines.isNotEmpty()) out.append(buildCodeBlockHtml(codeLines))
            continue
        }
        if (line.isBlank()) {
            i++
            continue
        }
        // 以空格/制表开头的缩进块（在独立 "::" 之后判断，避免 "  ::" 被当成块首行输出）
        if (line.startsWith(" ") || line.startsWith("\t")) {
            val (block, nextIndex) = collectIndentedBlock(lines, i)
            i = nextIndex
            val codeLines = block.filter { it.isNotBlank() }.map { escapeHtml(it) }
            if (codeLines.isNotEmpty()) out.append(buildCodeBlockHtml(codeLines))
            continue
        }
        val paraLines = mutableListOf(line)
        while (i + 1 < lines.size && lines[i + 1].isNotBlank() && !rstLineStartsBlock(lines[i + 1], lines.getOrNull(i + 2)) { peekUnderlineChar(it) }) {
            i++
            paraLines += lines[i]
        }
        val hasLiteralBlockAfter = paraLines.last().trimEnd().endsWith("::") && hasIndentedBlockAfter(lines, i + 1)
        if (hasLiteralBlockAfter) {
            paraLines[paraLines.lastIndex] = paraLines.last().replace(Regex("::\\s*$"), "")
        }
        // 合并段落行：包含中文不加空格，纯英文/数字/符号加空格
        val paraText = if (paraLines.any { it.any { ch -> ch in '\u4e00'..'\u9fff' } }) {
            paraLines.joinToString("")
        } else {
            paraLines.joinToString(" ")
        }
        val paraTrimmed = paraText.trim()
        if (paraTrimmed.isNotEmpty()) out.append("<p>").append(renderRstInlineText(paraText)).append("</p>")
        i++
        if (hasLiteralBlockAfter) {
            val (block, nextIndex) = collectIndentedBlock(lines, i)
            i = nextIndex
            val codeLines = block.filter { it.isNotBlank() }.map { escapeHtml(it) }
            if (codeLines.isNotEmpty()) out.append(buildCodeBlockHtml(codeLines))
        }
    }
    out.append(renderRstFootnotes(footnotes))
    return out.toString()
}

private fun rstLineStartsBlock(line: String, next: String?, peekUnderlineChar: (String) -> Char?): Boolean {
    val trimmed = line.trim()
    if (trimmed.startsWith(".. figure::") || trimmed.startsWith(".. image::") || trimmed.startsWith(".. math::")) return true
    if (trimmed.startsWith(".. list-table::")) return true
    if (trimmed.startsWith(".. code-block::") || trimmed.startsWith(".. sourcecode::")) return true
    if (trimmed.startsWith(".. ")) return true
    if (isRstStandaloneLiteralMarker(trimmed)) return true
    if (isRstGridBorder(line) || isRstSimpleTableBorder(line)) return true
    if (trimmed.matches(Regex("^[-*•]\\s.+"))) return true
    if (trimmed.matches(Regex("^\\d+\\.\\s.+"))) return true
    if (line.startsWith(" ") || line.startsWith("\t")) return true
    if (next != null && next.isNotBlank() && peekUnderlineChar(next) != null && next.length >= trimmed.length) return true
    return false
}

/** 检查从 startIndex 开始是否有缩进块（跳过空行） */
private fun hasIndentedBlockAfter(lines: List<String>, startIndex: Int): Boolean {
    var index = startIndex
    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index++
            continue
        }
        return countIndent(line) > 0
    }
    return false
}

private fun escapeHtmlAttr(s: String): String = s
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

/** 提取 RST 元数据 */
private fun extractRstMeta(rst: String): List<Pair<String, String>> {
    val normalizedLines = rst.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val meta = mutableListOf<Pair<String, String>>()
    var i = 0
    while (i < normalizedLines.size) {
        val line = normalizedLines[i]
        val trimmed = line.trim()
        if (trimmed.matches(Regex("^:\\w+:.*"))) {
            val (key, value) = if (trimmed.contains("`")) {
                val parts = trimmed.split("`")
                if (parts.size >= 2) {
                    parts[0].removeSuffix(":").trim() to parts[1].trim()
                } else {
                    trimmed to ""
                }
            } else {
                val colonIndex = trimmed.indexOf(':')
                if (colonIndex > 0) {
                    trimmed.substring(0, colonIndex).trim() to trimmed.substring(colonIndex + 1).trim()
                } else {
                    trimmed to ""
                }
            }
            meta.add(key to value)
        }
        i++
    }
    return meta
}

private fun rstInlineToHtml(escaped: String): String {
    var s = escaped
    // 处理反斜杠转义：\ 后跟空格表示普通空格（RST 标准）
    s = s.replace("\\ ", " ")
    // 处理 Sphinx 角色标记
    // :index:`xxx` 显示 xxx 内容
    s = Regex(":index:`([^`]+?)`").replace(s) { it.groupValues[1] }
    // :ref:`xxx` 显示 xxx 内容
    s = Regex(":ref:`([^`]+?)`").replace(s) { it.groupValues[1] }
    // 其他自定义 role（如 :dtag:`xxx`）完全移除
    s = Regex(":\\w+:`[^`]+?`").replace(s) { "" }
    // 先处理 :math:，用 data-latex 供前端 KaTeX 显式渲染，避免 delimiter 解析问题
    s = Regex(""":math:`([^`]+?)`""").replace(s) {
        val latex = it.groupValues[1].replace("&amp;", "&")
        """<span class="katex-inline" data-latex="${escapeHtmlAttr(latex)}"></span>"""
    }
    s = Regex("""\[(#[^\]]+)]_""").replace(s) {
        val rawLabel = it.groupValues[1]
        val id = sanitizeRstFootnoteId(rawLabel)
        val display = escapeHtml(rawLabel.removePrefix("#"))
        """<sup class="footnote-ref"><a href="#fn-$id" id="fnref-$id">[$display]</a></sup>"""
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
private const val MD_VIEWER_BASE_URL = "https://local-md.invalid/"
private const val MAX_LOCAL_HTML_BYTES = 2 * 1024 * 1024

data class HtmlViewerLocation(
    val initialUrl: String,
    val title: String,
    val localFileUri: String? = null
)

private class LinkCallbackHolder { var onLink: (String) -> Unit = {} }

private fun isMdViewerLocalUrl(url: String): Boolean {
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    return parsed.scheme.equals("https", ignoreCase = true) && parsed.authority == "local-md.invalid"
}

private fun extractMdViewerRelativePath(url: String): String? {
    if (!isMdViewerLocalUrl(url)) return null
    val encodedPath = Uri.parse(url).encodedPath ?: return null
    val trimmed = encodedPath.trim('/')
    return trimmed.takeIf { it.isNotBlank() }?.let(Uri::decode)
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
    val relativePath = MdLinkUtils.extractRelativePathFromRequest(currentUri, clickedUrl)
        ?: extractMdViewerRelativePath(clickedUrl)
        ?: Uri.parse(clickedUrl).encodedPath?.trim('/')?.takeIf { it.isNotBlank() }?.let(Uri::decode)
        ?: Uri.parse(clickedUrl).lastPathSegment
        ?: clickedUrl.substringAfterLast('/').ifBlank { clickedUrl }
    if (relativePath.isBlank()) return null
    val parentUri = getParentDirectoryUri(context, currentUri) ?: run {
        Log.d(MD_DEBUG, "[内链] 无法取父目录 currentUri=$currentUri")
        return null
    }
    val childUri = if (relativePath.contains('/')) {
        resolveResourcePath(context, currentUri, relativePath)
    } else {
        findChildByName(context, parentUri, relativePath)
    } ?: run {
        Log.d(MD_DEBUG, "[内链] 父目录下未找到 path=$relativePath parentUri=$parentUri")
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
    val relativePath = extractMdViewerRelativePath(resourceUrl)
        ?: MdLinkUtils.extractRelativePathFromRequest(currentUri, resourceUrl)
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
        val mime = guessWebResourceMime(path, context.contentResolver.getType(childUri))
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

private fun guessWebResourceMime(path: String, fallback: String? = null): String {
    val normalized = path.substringBefore('#').substringBefore('?').lowercase(Locale.getDefault())
    return when {
        normalized.endsWith(".html") || normalized.endsWith(".htm") -> "text/html"
        normalized.endsWith(".css") -> "text/css"
        normalized.endsWith(".js") || normalized.endsWith(".mjs") -> "application/javascript"
        normalized.endsWith(".json") -> "application/json"
        normalized.endsWith(".svg") -> "image/svg+xml"
        normalized.endsWith(".png") -> "image/png"
        normalized.endsWith(".jpg") || normalized.endsWith(".jpeg") -> "image/jpeg"
        normalized.endsWith(".gif") -> "image/gif"
        normalized.endsWith(".webp") -> "image/webp"
        normalized.endsWith(".woff") -> "font/woff"
        normalized.endsWith(".woff2") -> "font/woff2"
        normalized.endsWith(".ttf") -> "font/ttf"
        normalized.endsWith(".otf") -> "font/otf"
        normalized.endsWith(".txt") -> "text/plain"
        else -> fallback ?: "application/octet-stream"
    }
}

private class ResourceResolverHolder(var currentUri: String)

/** 供页面 JS 调用：点击脚注时用气泡（Toast）显示脚注内容，不跳转。 */
private class FootnoteToastHandler(private val context: android.content.Context) {
    @android.webkit.JavascriptInterface
    fun showFootnote(text: String?) {
        val t = text?.trim() ?: return
        if (t.isEmpty()) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, t, Toast.LENGTH_LONG).show()
        }
    }
}

private class PageReadyJsBridge(
    private val onReady: () -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onReady() {
        Handler(Looper.getMainLooper()).post {
            onReady()
        }
    }
}

/** 同页锚点：用 JS 滚动到指定 id，解决 loadDataWithBaseURL 下 WebView 不自动滚动的现象。 */
private fun scrollToAnchor(view: WebView?, fragment: String) {
    if (fragment.isBlank() || view == null) return
    val quotedId = try { org.json.JSONObject.quote(fragment) } catch (_: Exception) { "\"$fragment\"" }
    val js = "(function(){ var el = document.getElementById($quotedId); if(el) el.scrollIntoView({behavior:'smooth',block:'start'}); })();"
    view.post { view.evaluateJavascript(js, null) }
}

/** 脚注链接：若被 WebView 拦截则在此用 JS 取内容并 Toast；否则由页面内脚本调用 FootnoteToastHandler。 */
private fun handleFootnoteClick(view: WebView?, url: String, context: android.content.Context): Boolean {
    val frag = url.substringAfter('#', "").takeIf { it.isNotBlank() } ?: return false
    if (!frag.startsWith("fn") && !frag.startsWith("fnref")) return false
    val defId = frag.replace(Regex("^fnref"), "fn")
    val js = "(function(){ var el = document.getElementById(\"${defId.replace("\"", "\\\"")}\"); return el ? (el.innerText || el.textContent || '').trim() : ''; })();"
    view?.evaluateJavascript(js) { result ->
        var text = result ?: ""
        if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\""))
            text = text.drop(1).dropLast(1).replace("\\n", "\n").replace("\\\"", "\"")
        if (text.isNotBlank()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        }
    }
    return true
}

/** WebViewClient：链接点击回调；资源请求用同目录文件响应（图片等）。 */
private class LinkInterceptClient(
    private val linkHolder: LinkCallbackHolder,
    private val context: android.content.Context,
    private val currentUriHolder: ResourceResolverHolder,
    private val onPageFinished: ((WebView) -> Unit)? = null
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (handleFootnoteClick(view, url, context)) return true
        val frag = url.substringAfter('#', "")
        val isSamePageAnchor = frag.isNotBlank() && !frag.startsWith("fn") && !frag.startsWith("fnref") &&
            (url.trimStart().startsWith("#") || (isMdViewerLocalUrl(url) && extractMdViewerRelativePath(url).isNullOrBlank()))
        if (isSamePageAnchor) {
            scrollToAnchor(view, frag)
            return true
        }
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

/** 构建单文件 MD 查看器用的完整 HTML，用于多 WebView 时按 uri 分别构建。 */
private fun buildMdViewerFullHtml(
    htmlContent: String,
    baseUrl: String,
    katexCss: String,
    katexJs: String,
    autoRenderJs: String,
    mermaidJs: String,
    syntaxHighlightCss: String,
    syntaxHighlightJs: String,
    bgHex: String,
    fgHex: String
): String = """
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
th, td { border: 1px solid rgba(128,128,128,0.4); padding: 6px 10px; }
th { background: rgba(128,128,128,0.15); }
del, s { text-decoration: line-through; }
.task-list-item { list-style: none; margin-left: -1.5em; display: flex; align-items: flex-start; gap: 6px; }
.task-list-item input { margin: 0; flex-shrink: 0; vertical-align: middle; }
.task-list-item > p { margin: 0; flex: 1; }
sup a { text-decoration: none; color: #2196F3; }
.footnote-tooltip { position: fixed; max-width: 320px; padding: 10px 12px; background: rgba(30,30,30,0.95); color: #e0e0e0; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.3); z-index: 99999; font-size: 14px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
$syntaxHighlightCss
</style>
</head>
<body>$htmlContent
<script>$katexJs</script>
<script>$autoRenderJs</script>
<script>$mermaidJs</script>
<script>$syntaxHighlightJs</script>
<script>
document.addEventListener("DOMContentLoaded", function() {
if (typeof applySyntaxHighlight === "function") { applySyntaxHighlight(document); }
function showFootnoteTooltip(linkEl, text) {
  var old = document.getElementById("fn-tooltip"); if (old) old.remove();
  var rect = linkEl.getBoundingClientRect();
  var tip = document.createElement("div"); tip.id = "fn-tooltip"; tip.className = "footnote-tooltip"; tip.textContent = text;
  tip.style.maxWidth = Math.min(320, window.innerWidth - 24) + "px";
  tip.style.left = Math.max(8, Math.min(rect.left, window.innerWidth - 336)) + "px";
  tip.style.top = (rect.top - 8) + "px"; tip.style.transform = "translateY(-100%)";
  document.body.appendChild(tip);
  setTimeout(function(){ document.addEventListener("click", function close(){ tip.remove(); document.removeEventListener("click", close); }); }, 0);
}
document.querySelectorAll('a[href^="#fn"], a.footnote-ref, sup a.footnote-ref').forEach(function(a) {
a.addEventListener("click", function(e) { e.preventDefault(); e.stopPropagation(); var id = (a.getAttribute("href") || "").slice(1).replace(/^fnref/, "fn"); var def = document.getElementById(id); if (def) { var text = (def.innerText || def.textContent || "").trim(); if (text) showFootnoteTooltip(a, text); } });
});
if (typeof katex !== "undefined") {
document.querySelectorAll(".katex-inline").forEach(function(el) { var latex = el.getAttribute("data-latex"); if (latex) try { el.innerHTML = katex.renderToString(latex, { displayMode: false, throwOnError: false }); } catch(e) {} });
document.querySelectorAll(".katex-display").forEach(function(el) { var latex = el.getAttribute("data-latex"); if (latex) try { el.innerHTML = katex.renderToString(latex, { displayMode: true, throwOnError: false }); } catch(e) {} });
}
if (typeof renderMathInElement === "function") {
renderMathInElement(document.body, { delimiters: [{ left: "$$", right: "$$", display: true }, { left: "$", right: "$", display: false }, { left: "\\\\(", right: "\\\\)", display: false }, { left: "\\\\[", right: "\\\\]", display: true }], throwOnError: false });
}
if (typeof mermaid !== "undefined") {
mermaid.initialize({ startOnLoad: false, theme: 'default' });
document.querySelectorAll("pre > code.language-mermaid").forEach(function(codeEl) { var pre = codeEl.parentElement; var div = document.createElement("div"); div.className = "mermaid"; div.textContent = codeEl.textContent; pre.parentNode.replaceChild(div, pre); }); mermaid.run();
}
if (window.androidPageReady && typeof window.androidPageReady.onReady === "function") {
window.androidPageReady.onReady();
}
});
</script>
</body>
</html>
""".trimIndent()

private data class RegexFindUiState(
    val count: Int = 0,
    val currentIndex: Int = -1,
    val error: String? = null
) {
    val hasMatches: Boolean get() = count > 0
}

private data class EpubFullTextSearchResult(
    val chapterIndex: Int,
    val chapterTitle: String,
    val matchCount: Int,
    val snippet: String
)

private fun htmlToSearchableText(html: String): String {
    val stripped = html
        .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), " ")
        .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), " ")
    return HtmlCompat.fromHtml(stripped, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun readTextForEpubSearch(file: File): String {
    return runCatching { file.readText() }
        .getOrElse {
            runCatching { file.readBytes().decodeToString() }
                .getOrElse { error -> throw IllegalStateException("读取章节文本失败：${file.absolutePath} ${error.message}", error) }
        }
}

private fun countLiteralMatches(text: String, query: String): Int {
    if (text.isEmpty() || query.isEmpty()) return 0
    var count = 0
    var searchFrom = 0
    while (true) {
        val found = text.indexOf(query, startIndex = searchFrom, ignoreCase = true)
        if (found < 0) break
        count += 1
        searchFrom = found + query.length
    }
    return count
}

private fun buildLiteralMatchSnippet(text: String, query: String, radius: Int = 36): String {
    val found = text.indexOf(query, ignoreCase = true)
    if (found < 0) return text.take(radius * 2).trim()
    val start = maxOf(0, found - radius)
    val end = minOf(text.length, found + query.length + radius)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < text.length) "..." else ""
    return prefix + text.substring(start, end).trim() + suffix
}

private suspend fun searchEpubAcrossChapters(
    extractResult: EpubExtractResult,
    query: String
): List<EpubFullTextSearchResult> = withContext(Dispatchers.IO) {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return@withContext emptyList()
    extractResult.chapters.mapIndexedNotNull { index, chapter ->
        val chapterFile = getEpubChapterFile(extractResult, chapter)
            ?: return@mapIndexedNotNull null
        if (!chapterFile.exists() || !chapterFile.isFile) {
            throw IllegalStateException("章节文件不存在：index=$index href=${chapter.href} file=${chapterFile.absolutePath}")
        }
        val plainText = htmlToSearchableText(readTextForEpubSearch(chapterFile))
        val matchCount = countLiteralMatches(plainText, normalizedQuery)
        if (matchCount <= 0) {
            null
        } else {
            EpubFullTextSearchResult(
                chapterIndex = index,
                chapterTitle = chapter.title ?: chapter.href.substringBeforeLast('.'),
                matchCount = matchCount,
                snippet = buildLiteralMatchSnippet(plainText, normalizedQuery)
            )
        }
    }
}

private val REGEX_FIND_BOOTSTRAP_JS = """
(function() {
    if (window.__lmRegexFind) {
        return true;
    }

    var styleId = 'lm-regex-find-style';
    var skipTags = { SCRIPT: true, STYLE: true, NOSCRIPT: true, TEXTAREA: true, INPUT: true, SELECT: true, OPTION: true };

    function ensureStyle() {
        if (document.getElementById(styleId)) {
            return;
        }
        var style = document.createElement('style');
        style.id = styleId;
        style.textContent = 'span.lm-regex-find-match{background:rgba(255,214,10,0.55);color:inherit;box-shadow:0 0 0 1px rgba(255,193,7,0.25);}' +
            'span.lm-regex-find-match.lm-regex-find-current{background:#ff8f00;color:#111;box-shadow:0 0 0 2px rgba(255,143,0,0.35);}';
        (document.head || document.documentElement).appendChild(style);
    }

    function resetState() {
        window.__lmRegexFindMatches = [];
        window.__lmRegexFindIndex = -1;
    }

    function result(extra) {
        var matches = window.__lmRegexFindMatches || [];
        var payload = {
            ok: true,
            count: matches.length,
            index: window.__lmRegexFindIndex == null ? -1 : window.__lmRegexFindIndex,
            error: null
        };
        if (extra) {
            for (var key in extra) {
                if (Object.prototype.hasOwnProperty.call(extra, key)) {
                    payload[key] = extra[key];
                }
            }
        }
        return payload;
    }

    function shouldSkip(node) {
        if (!node || !node.parentElement) {
            return true;
        }
        if (!node.nodeValue || !node.nodeValue.trim()) {
            return true;
        }
        var parent = node.parentElement;
        if (parent.closest && parent.closest('span.lm-regex-find-match')) {
            return true;
        }
        return !!skipTags[parent.tagName];
    }

    function clear() {
        var markers = document.querySelectorAll('span.lm-regex-find-match');
        for (var i = 0; i < markers.length; i++) {
            var marker = markers[i];
            var parent = marker.parentNode;
            if (!parent) {
                continue;
            }
            parent.replaceChild(document.createTextNode(marker.textContent || ''), marker);
            parent.normalize();
        }
        resetState();
        return result();
    }

    function buildRegex(pattern) {
        var source = String(pattern || '');
        var flags = 'gm';
        var slashForm = source.match(/^\/((?:\\.|[^\/])+)\/([dgimsuy]*)$/);
        if (slashForm) {
            source = slashForm[1];
            flags = slashForm[2] || '';
        } else {
            var inlineFlags = source.match(/^\(\?([dgimsuy]+)\)/);
            if (inlineFlags) {
                flags = inlineFlags[1] || '';
                source = source.slice(inlineFlags[0].length);
            }
        }
        if (flags.indexOf('g') < 0) {
            flags += 'g';
        }
        if (flags.indexOf('m') < 0) {
            flags += 'm';
        }
        return new RegExp(source, flags);
    }

    function activate(index) {
        var matches = window.__lmRegexFindMatches || [];
        if (!matches.length) {
            window.__lmRegexFindIndex = -1;
            return result();
        }
        var normalizedIndex = index % matches.length;
        if (normalizedIndex < 0) {
            normalizedIndex += matches.length;
        }
        window.__lmRegexFindIndex = normalizedIndex;
        for (var i = 0; i < matches.length; i++) {
            var group = matches[i];
            if (!group || !group.length) {
                continue;
            }
            for (var j = 0; j < group.length; j++) {
                group[j].classList.toggle('lm-regex-find-current', i === normalizedIndex);
            }
        }
        var currentGroup = matches[normalizedIndex];
        var current = currentGroup && currentGroup.length ? currentGroup[0] : null;
        if (current && current.scrollIntoView) {
            var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
            var rect = current.getBoundingClientRect ? current.getBoundingClientRect() : null;
            if (viewportHeight > 0 && rect) {
                var targetAnchor = viewportHeight * 0.28;
                var currentCenter = rect.top + (rect.height / 2);
                var delta = currentCenter - targetAnchor;
                window.scrollBy({ top: delta, behavior: 'smooth' });
            } else {
                current.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
            }
        }
        return result();
    }

    function search(pattern) {
        clear();
        ensureStyle();
        if (!pattern) {
            return result();
        }
        var regex;
        try {
            regex = buildRegex(pattern);
        } catch (error) {
            return result({ ok: false, error: String((error && error.message) || error || '无效正则') });
        }

        var root = document.body || document.documentElement;
        if (!root) {
            return result({ ok: false, error: '页面尚未就绪' });
        }

        var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
        var nodes = [];
        var fullText = '';
        var totalOffset = 0;
        var currentNode;
        while ((currentNode = walker.nextNode())) {
            if (!shouldSkip(currentNode)) {
                var textValue = currentNode.nodeValue || '';
                nodes.push({
                    node: currentNode,
                    text: textValue,
                    start: totalOffset,
                    end: totalOffset + textValue.length,
                    segments: []
                });
                fullText += textValue;
                totalOffset += textValue.length;
            }
        }

        var matchRanges = [];
        var match;
        regex.lastIndex = 0;
        while ((match = regex.exec(fullText)) !== null) {
            var matchedText = match[0];
            if (!matchedText) {
                regex.lastIndex += 1;
                continue;
            }
            matchRanges.push({
                id: matchRanges.length,
                start: match.index,
                end: match.index + matchedText.length
            });
        }

        var nodeCursor = 0;
        for (var rangeIndex = 0; rangeIndex < matchRanges.length; rangeIndex++) {
            var range = matchRanges[rangeIndex];
            while (nodeCursor < nodes.length && nodes[nodeCursor].end <= range.start) {
                nodeCursor += 1;
            }
            var scanIndex = nodeCursor;
            while (scanIndex < nodes.length && nodes[scanIndex].start < range.end) {
                var nodeInfo = nodes[scanIndex];
                var segmentStart = Math.max(0, range.start - nodeInfo.start);
                var segmentEnd = Math.min(nodeInfo.text.length, range.end - nodeInfo.start);
                if (segmentStart < segmentEnd) {
                    nodeInfo.segments.push({
                        start: segmentStart,
                        end: segmentEnd,
                        matchId: range.id
                    });
                }
                scanIndex += 1;
            }
        }

        var matches = new Array(matchRanges.length);
        for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
            var nodeInfo = nodes[nodeIndex];
            if (!nodeInfo.segments.length) {
                continue;
            }
            nodeInfo.segments.sort(function(a, b) {
                if (a.start !== b.start) {
                    return a.start - b.start;
                }
                return a.end - b.end;
            });
            var fragment = document.createDocumentFragment();
            var lastIndex = 0;
            for (var segmentIndex = 0; segmentIndex < nodeInfo.segments.length; segmentIndex++) {
                var segment = nodeInfo.segments[segmentIndex];
                if (segment.start > lastIndex) {
                    fragment.appendChild(document.createTextNode(nodeInfo.text.slice(lastIndex, segment.start)));
                }
                var marker = document.createElement('span');
                marker.className = 'lm-regex-find-match';
                marker.setAttribute('data-lm-regex-find-id', String(segment.matchId));
                marker.textContent = nodeInfo.text.slice(segment.start, segment.end);
                fragment.appendChild(marker);
                if (!matches[segment.matchId]) {
                    matches[segment.matchId] = [];
                }
                matches[segment.matchId].push(marker);
                lastIndex = segment.end;
            }
            if (lastIndex < nodeInfo.text.length) {
                fragment.appendChild(document.createTextNode(nodeInfo.text.slice(lastIndex)));
            }
            if (nodeInfo.node.parentNode) {
                nodeInfo.node.parentNode.replaceChild(fragment, nodeInfo.node);
            }
        }

        matches = matches.filter(function(group) { return !!(group && group.length); });

        window.__lmRegexFindMatches = matches;
        window.__lmRegexFindIndex = matches.length > 0 ? 0 : -1;
        return activate(window.__lmRegexFindIndex);
    }

    function move(delta) {
        var matches = window.__lmRegexFindMatches || [];
        if (!matches.length) {
            return result();
        }
        var startIndex = window.__lmRegexFindIndex == null ? 0 : window.__lmRegexFindIndex;
        return activate(startIndex + delta);
    }

    resetState();
    ensureStyle();
    window.__lmRegexFind = {
        clear: clear,
        search: search,
        next: function() { return move(1); },
        prev: function() { return move(-1); }
    };
    return true;
})();
""".trimIndent()

private fun decodeJsStringResult(rawResult: String?): String? {
    if (rawResult.isNullOrBlank() || rawResult == "null") return null
    return try {
        when (val decoded = org.json.JSONTokener(rawResult).nextValue()) {
            is String -> decoded
            null -> null
            else -> decoded.toString()
        }
    } catch (_: Exception) {
        rawResult.trim().removeSurrounding("\"")
    }
}

private fun parseRegexFindUiState(rawResult: String?): RegexFindUiState {
    val decoded = decodeJsStringResult(rawResult) ?: return RegexFindUiState(error = "查找结果解析失败")
    return try {
        val json = org.json.JSONObject(decoded)
        val errorValue = json.opt("error")
        RegexFindUiState(
            count = json.optInt("count", 0),
            currentIndex = json.optInt("index", -1),
            error = when (errorValue) {
                null, org.json.JSONObject.NULL -> null
                is String -> errorValue.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                else -> errorValue.toString().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            },
        )
    } catch (_: Exception) {
        RegexFindUiState(error = "查找结果解析失败")
    }
}

private fun installRegexFindBridge(view: WebView?, onInstalled: (() -> Unit)? = null) {
    if (view == null) {
        onInstalled?.invoke()
        return
    }
    view.post {
        view.evaluateJavascript(REGEX_FIND_BOOTSTRAP_JS) {
            onInstalled?.invoke()
        }
    }
}

private fun runRegexFindCommand(
    view: WebView?,
    command: String,
    onResult: (RegexFindUiState) -> Unit
) {
    if (view == null) {
        onResult(RegexFindUiState(error = "页面尚未就绪"))
        return
    }
    installRegexFindBridge(view) {
        view.post {
            view.evaluateJavascript(
                """(function(){
                    try {
                        return JSON.stringify($command);
                    } catch (error) {
                        return JSON.stringify({ ok: false, count: 0, index: -1, error: String((error && error.message) || error || '查找失败') });
                    }
                })();""".trimIndent()
            ) { rawResult ->
                onResult(parseRegexFindUiState(rawResult))
            }
        }
    }
}

private fun searchInWebViewByRegex(
    view: WebView?,
    pattern: String,
    onResult: (RegexFindUiState) -> Unit
) {
    val quotedPattern = try {
        org.json.JSONObject.quote(pattern)
    } catch (_: Exception) {
        "\"${pattern.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }
    runRegexFindCommand(view, "window.__lmRegexFind.search($quotedPattern)", onResult)
}

private fun moveRegexFindMatch(
    view: WebView?,
    forward: Boolean,
    onResult: (RegexFindUiState) -> Unit
) {
    runRegexFindCommand(view, if (forward) "window.__lmRegexFind.next()" else "window.__lmRegexFind.prev()", onResult)
}

private fun clearRegexFind(
    view: WebView?,
    onResult: (RegexFindUiState) -> Unit
) {
    runRegexFindCommand(view, "window.__lmRegexFind.clear()", onResult)
}

@Composable
private fun WebViewRegexFindAction(
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.Default.Search, contentDescription = "查找")
    }
}

@Composable
private fun WebViewRegexFindDialog(
    query: String,
    result: RegexFindUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("正则，例如 /error|warn/i") },
                            singleLine = true
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭查找")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            result.error != null -> result.error
                            result.hasMatches -> "第 ${result.currentIndex + 1} / ${result.count} 处，定位时会自动避开底部查找栏。"
                            query.isNotBlank() -> "未找到匹配"
                            else -> "支持 /pattern/flags 与 (?i)pattern。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            result.error != null -> MaterialTheme.colorScheme.error
                            result.hasMatches -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onSearch) { Text("查找") }
                        TextButton(onClick = onPrevious, enabled = result.hasMatches) { Text("上一个") }
                        TextButton(onClick = onNext, enabled = result.hasMatches) { Text("下一个") }
                        TextButton(onClick = onClear, enabled = result.hasMatches || query.isNotBlank()) { Text("清除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubFullTextSearchDialog(
    query: String,
    searching: Boolean,
    error: String?,
    results: List<EpubFullTextSearchResult>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSelectResult: (EpubFullTextSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .heightIn(max = 620.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("全文查找", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭全文查找")
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入要搜索的文字") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onSearch, enabled = !searching) {
                        Text(if (searching) "搜索中..." else "搜索")
                    }
                    TextButton(
                        onClick = onClear,
                        enabled = query.isNotBlank() || results.isNotEmpty() || error != null
                    ) {
                        Text("清除")
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    error != null -> {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    searching -> {
                        Text("正在扫描全部章节，请稍候...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    query.isBlank() -> {
                        Text("输入文字后可在全部章节中查找。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    results.isEmpty() -> {
                        Text("未找到匹配章节。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Text(
                            "找到 ${results.sumOf { it.matchCount }} 处命中，分布在 ${results.size} 个章节。",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (results.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(results) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                onClick = { onSelectResult(item) }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "第 ${item.chapterIndex + 1} 章 · ${item.chapterTitle}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "命中 ${item.matchCount} 次",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        item.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 独立 Markdown 渲染查看器：支持内链（同应用内打开、可退回）、外链（仅提示用浏览器打开）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownViewerScreen(
    initialFileUri: String,
    initialFileName: String,
    isEncrypted: Boolean,
    sessionCache: MarkdownViewerSessionCache,
    onBack: () -> Unit,
    onOpenFile: (uri: String, name: String, encrypted: Boolean) -> Unit
) {
    KeepScreenOnEffect()
    val context = LocalContext.current
    var backStack by remember { mutableStateOf(listOf<Triple<String, String, Boolean>>()) }
    var currentUri by remember { mutableStateOf(initialFileUri) }
    var currentName by remember { mutableStateOf(initialFileName) }
    var currentEncrypted by remember { mutableStateOf(isEncrypted) }

    var htmlContent by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMessage by remember { mutableStateOf("加载中…") }
    var pageLoading by remember { mutableStateOf(false) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    var pendingInternalUri by remember { mutableStateOf<String?>(null) }
    val linkHolder = remember { LinkCallbackHolder() }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var scalePercent by remember { mutableStateOf(100) } // 50..200，页面 body.style.zoom
    var showFindDialog by remember { mutableStateOf(false) }
    var regexQuery by remember { mutableStateOf("") }
    var regexFindUiState by remember { mutableStateOf(RegexFindUiState()) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val ttsController = rememberStandaloneWebViewTtsController(
        preferredLocale = remember { Locale.getDefault() },
        contentToken = "$currentUri:$currentEncrypted",
        documentTitle = currentName,
        webViewProvider = { webViewRef.value },
        scrollRatioProvider = { currentWebViewScrollRatio(webViewRef.value) }
    )

    // 词典查询状态
    var dictLookupResult by remember { mutableStateOf<DictLookupResult?>(null) }
    var dictLoaded by remember { mutableStateOf<StarDictLoaded?>(null) }

    // 加载词典（只加载一次）
    LaunchedEffect(Unit) {
        val dicts = listImportedStarDicts(context)
        val firstDict = dicts.firstOrNull()
        if (firstDict != null) {
            dictLoaded = loadImportedStarDict(context, firstDict.id)
        }
    }

    // 词典查询函数
    fun lookupWord(word: String) {
        val loaded = dictLoaded
        if (loaded == null) {
            dictLookupResult = DictLookupResult(word, null, "没有可用的词典，请先导入词典")
            return
        }
        val found = lookupExactWord(loaded, word)
        if (found == null) {
            dictLookupResult = DictLookupResult(word, null, "词典中未找到 \"$word\"")
            return
        }
        val definition = readStarDictExplanation(context, loaded.summary.id, loaded, found)
        dictLookupResult = DictLookupResult(word, definition, null)
    }

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
    val syntaxHighlightCss = remember { syntaxHighlightStyle() }
    val syntaxHighlightJs = remember { syntaxHighlightScript() }

    LaunchedEffect(currentUri, currentEncrypted) {
        regexFindUiState = RegexFindUiState()
        val cacheKey = "$currentUri:$currentEncrypted"
        val cacheSignature = withContext(Dispatchers.IO) {
            computeMarkdownCacheSignature(context, currentUri)
        }
        sessionCache.getHtml(cacheKey, cacheSignature)?.let { cached ->
            htmlContent = cached
            loading = false
            pageLoading = sessionCache.pageReadyMap[cacheKey] != true
            loadingMessage = if (pageLoading) "正在恢复页面脚本与样式…" else "正在复用已缓存页面…"
            Log.d(MD_DEBUG, "[加载] 使用缓存 currentUri=$currentUri")
            return@LaunchedEffect
        }
        loading = true
        pageLoading = false
        loadError = null
        htmlContent = null
        loadingMessage = if (currentEncrypted) "正在读取并解密文件…" else "正在读取文件内容…"
        Log.d(MD_DEBUG, "[加载] 开始 currentUri=$currentUri")
        val uri = Uri.parse(currentUri)
        val decoded = withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStreamSafe(uri)
            if (stream == null) {
                Log.d(MD_DEBUG, "[加载] 失败 openInputStreamSafe 返回 null uri=$currentUri")
                loadError = "无法打开文件"
                return@withContext null
            }
            stream.use { raw ->
                val bytes = if (currentEncrypted) {
                    GpgHelper.decryptStream(raw) ?: run {
                        loadError = "解密失败或需要密码"
                        Log.d(MD_DEBUG, "[加载] 解密失败")
                        return@withContext null
                    }
                } else {
                    raw.readBytesUpTo(maxOf(MAX_MARKDOWN_BYTES, MAX_RST_BYTES))
                }
                bytes.decodeToString().dropLastWhile { it == '\uFFFD' }
            }
        }
        val trimmed = decoded ?: run {
            loading = false
            pageLoading = false
            return@LaunchedEffect
        }
        val isRst = currentName.endsWith(".rst", ignoreCase = true)
        loadingMessage = if (isRst) "正在解析 RST 结构…" else "正在解析 Markdown 结构…"
        val renderedHtml = withContext(Dispatchers.IO) {
            if (isRst) rstToHtml(trimmed) else markdownToHtml(trimmed)
        }
        htmlContent = renderedHtml
        sessionCache.putHtml(cacheKey, renderedHtml, cacheSignature)
        pageLoading = sessionCache.pageReadyMap[cacheKey] != true
        loadingMessage = if (pageLoading) "正在初始化页面样式与脚本…" else "已完成，正在显示页面…"
        Log.d(MD_DEBUG, "[加载] 成功 currentUri=$currentUri 长度=${trimmed.length} isRst=$isRst")
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
            isMdViewerLocalUrl(url) -> null
            scheme == "http" || scheme == "https" || scheme == "mailto" -> url
            scheme == "content" -> {
                val segment = parsed.lastPathSegment ?: ""
                if (MdLinkUtils.looksLikeHostname(segment)) {
                    Log.d(MD_DEBUG, "[链接] content URI 识别为外链 segment=$segment")
                    "https://$segment"
                } else null
            }
            scheme == "file" -> {
                MdLinkUtils.hostnameSegmentFromUrl(url)?.let { seg ->
                    Log.d(MD_DEBUG, "[链接] file URI 识别为外链 segment=$seg")
                    "https://$seg"
                }
            }
            MdLinkUtils.looksLikeExternalUrl(url) -> {
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

    if (showFindDialog) {
        WebViewRegexFindDialog(
            query = regexQuery,
            result = regexFindUiState,
            onQueryChange = {
                regexQuery = it
                if (it.isBlank()) regexFindUiState = RegexFindUiState()
            },
            onSearch = {
                if (regexQuery.isBlank()) {
                    clearRegexFind(webViewRef.value) { regexFindUiState = it }
                } else {
                    searchInWebViewByRegex(webViewRef.value, regexQuery) { regexFindUiState = it }
                }
            },
            onPrevious = { moveRegexFindMatch(webViewRef.value, forward = false) { regexFindUiState = it } },
            onNext = { moveRegexFindMatch(webViewRef.value, forward = true) { regexFindUiState = it } },
            onClear = {
                clearRegexFind(webViewRef.value) {
                    regexFindUiState = it
                }
            },
            onDismiss = { showFindDialog = false }
        )
    }

    OfflineTtsEngineVoiceDialogs(
        showEngineDialog = ttsController.showTtsEngineDialog,
        onDismissEngineDialog = { ttsController.showTtsEngineDialog = false },
        showVoiceDialog = ttsController.showTtsVoiceDialog,
        onDismissVoiceDialog = { ttsController.showTtsVoiceDialog = false },
        ttsEngineLoading = ttsController.ttsEngineLoading,
        availableTtsEngines = ttsController.availableTtsEngines,
        selectedTtsEnginePackage = ttsController.selectedTtsEnginePackage,
        selectedTtsVoiceName = ttsController.selectedTtsVoiceName,
        effectiveTtsEngine = ttsController.effectiveTtsEngine,
        onRefreshEngines = { ttsController.requestRefreshTtsEngines() },
        onSelectEngine = { ttsController.selectEngine(it) },
        onSelectDefaultVoice = { ttsController.selectDefaultVoice() },
        onSelectVoice = { ttsController.selectVoice(it) }
    )

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
                        WebViewRegexFindAction(enabled = webViewRef.value != null) {
                            showFindDialog = true
                        }
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
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.epub_more_actions))
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) context.getString(R.string.epub_tts_stop)
                                            else context.getString(R.string.webview_tts_play)
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) {
                                            ttsController.stop()
                                        } else {
                                            ttsController.start()
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.epub_tts_engine)) },
                                    onClick = {
                                        showMoreMenu = false
                                        ttsController.showTtsEngineDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.epub_tts_voice)) },
                                    onClick = {
                                        showMoreMenu = false
                                        ttsController.showTtsVoiceDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                            }
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
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loadError!!, color = MaterialTheme.colorScheme.error)
                }
                htmlContent != null -> {
                    val bg = MaterialTheme.colorScheme.surface
                    val fg = MaterialTheme.colorScheme.onSurface
                    val bgHex = "#%02x%02x%02x".format(
                        (bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt()
                    )
                    val fgHex = "#%02x%02x%02x".format(
                        (fg.red * 255).toInt(), (fg.green * 255).toInt(), (fg.blue * 255).toInt()
                    )
                    val pages = (backStack + Triple(currentUri, currentName, currentEncrypted))
                        .filter { (u, _, e) -> (sessionCache.htmlCache["$u:$e"] != null || u == currentUri) && (u != currentUri || htmlContent != null) }
                    for (page in pages) {
                        val (uri, _, encrypted) = page
                        val cacheKey = "$uri:$encrypted"
                        val content = sessionCache.htmlCache[cacheKey] ?: if (uri == currentUri) htmlContent!! else continue
                        val isCurrent = uri == currentUri
                        androidx.compose.runtime.key(uri) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (isCurrent) Modifier else Modifier.size(0.dp))
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        val existing = sessionCache.getWebView(cacheKey)
                                        if (existing != null) {
                                            (existing.parent as? android.view.ViewGroup)?.removeView(existing)
                                            existing
                                        } else {
                                            val baseUrl = MD_VIEWER_BASE_URL
                                            val fullHtml = buildMdViewerFullHtml(content, baseUrl, katexCss, katexJs, autoRenderJs, mermaidJs, syntaxHighlightCss, syntaxHighlightJs, bgHex, fgHex)
                                            WebView(ctx).apply {
                                                setBackgroundColor(Color.TRANSPARENT)
                                                addJavascriptInterface(FootnoteToastHandler(context), "androidFootnote")
                                                addJavascriptInterface(PageReadyJsBridge {
                                                    sessionCache.setPageReady(cacheKey, true)
                                                    regexFindUiState = RegexFindUiState()
                                                    installRegexFindBridge(this)
                                                    evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                                                    if (isCurrent) {
                                                        pageLoading = false
                                                        loadingMessage = "页面已准备完成"
                                                    }
                                                }, "androidPageReady")
                                                webViewClient = LinkInterceptClient(
                                                    linkHolder, context, ResourceResolverHolder(uri)
                                                ) { w ->
                                                    sessionCache.setPageReady(cacheKey, true)
                                                    regexFindUiState = RegexFindUiState()
                                                    installRegexFindBridge(w)
                                                    w.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                                                    if (isCurrent) {
                                                        pageLoading = false
                                                        loadingMessage = "页面已准备完成"
                                                    }
                                                }
                                                settings.domStorageEnabled = false
                                                settings.javaScriptEnabled = true
                                                sessionCache.setPageReady(cacheKey, false)
                                                loadDataWithBaseURL(baseUrl, fullHtml, "text/html", "UTF-8", null)
                                                sessionCache.putWebView(cacheKey, this)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { webView ->
                                        if (isCurrent) {
                                            webViewRef.value = webView
                                            pageLoading = sessionCache.pageReadyMap[cacheKey] != true
                                            webView.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    if (loading || pageLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Card {
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                    Text(
                                        if (loading) "正在准备文档" else "正在准备页面",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        loadingMessage,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text("正在准备文档", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(loadingMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOnEffect(enabled: Boolean = true) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity, enabled) {
        if (enabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (enabled) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    var showFindDialog by remember { mutableStateOf(false) }
    var regexQuery by remember { mutableStateOf("") }
    var regexFindUiState by remember { mutableStateOf(RegexFindUiState()) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val ttsController = rememberStandaloneWebViewTtsController(
        preferredLocale = remember { Locale.getDefault() },
        contentToken = "pass:$innerFileName:${decryptedBytes.size}",
        documentTitle = innerFileName,
        webViewProvider = { webViewRef.value },
        scrollRatioProvider = { currentWebViewScrollRatio(webViewRef.value) }
    )

    // 词典查询状态
    var dictLookupResult by remember { mutableStateOf<DictLookupResult?>(null) }
    var dictLoaded by remember { mutableStateOf<StarDictLoaded?>(null) }

    // 加载词典（只加载一次）
    LaunchedEffect(Unit) {
        val dicts = listImportedStarDicts(context)
        val firstDict = dicts.firstOrNull()
        if (firstDict != null) {
            dictLoaded = loadImportedStarDict(context, firstDict.id)
        }
    }

    // 词典查询函数
    fun lookupWord(word: String) {
        val loaded = dictLoaded
        if (loaded == null) {
            dictLookupResult = DictLookupResult(word, null, "没有可用的词典，请先导入词典")
            return
        }
        val found = lookupExactWord(loaded, word)
        if (found == null) {
            dictLookupResult = DictLookupResult(word, null, "词典中未找到 \"$word\"")
            return
        }
        val definition = readStarDictExplanation(context, loaded.summary.id, loaded, found)
        dictLookupResult = DictLookupResult(word, definition, null)
    }

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
    val syntaxHighlightCss = remember { syntaxHighlightStyle() }
    val syntaxHighlightJs = remember { syntaxHighlightScript() }

    val htmlContent = remember(decryptedBytes, innerFileName) {
        val decoded = decryptedBytes.decodeToString()
        val trimmed = decoded.dropLastWhile { it == '\uFFFD' }
        val isRst = innerFileName.endsWith(".rst", ignoreCase = true)
        val isTxt = innerFileName.endsWith(".txt", ignoreCase = true)
        when {
            isRst -> rstToHtml(trimmed)
            isTxt -> "<pre style=\"white-space:pre-wrap;word-break:break-word;font-family:monospace;\">${escapeHtml(trimmed)}</pre>"
            else -> markdownToHtml(trimmed)
        }
    }

    val doBack = {
        webViewRef.value?.clearCache(true)
        onBack()
    }

    BackHandler { doBack() }

    if (showFindDialog) {
        WebViewRegexFindDialog(
            query = regexQuery,
            result = regexFindUiState,
            onQueryChange = {
                regexQuery = it
                if (it.isBlank()) regexFindUiState = RegexFindUiState()
            },
            onSearch = {
                if (regexQuery.isBlank()) {
                    clearRegexFind(webViewRef.value) { regexFindUiState = it }
                } else {
                    searchInWebViewByRegex(webViewRef.value, regexQuery) { regexFindUiState = it }
                }
            },
            onPrevious = { moveRegexFindMatch(webViewRef.value, forward = false) { regexFindUiState = it } },
            onNext = { moveRegexFindMatch(webViewRef.value, forward = true) { regexFindUiState = it } },
            onClear = { clearRegexFind(webViewRef.value) { regexFindUiState = it } },
            onDismiss = { showFindDialog = false }
        )
    }

    OfflineTtsEngineVoiceDialogs(
        showEngineDialog = ttsController.showTtsEngineDialog,
        onDismissEngineDialog = { ttsController.showTtsEngineDialog = false },
        showVoiceDialog = ttsController.showTtsVoiceDialog,
        onDismissVoiceDialog = { ttsController.showTtsVoiceDialog = false },
        ttsEngineLoading = ttsController.ttsEngineLoading,
        availableTtsEngines = ttsController.availableTtsEngines,
        selectedTtsEnginePackage = ttsController.selectedTtsEnginePackage,
        selectedTtsVoiceName = ttsController.selectedTtsVoiceName,
        effectiveTtsEngine = ttsController.effectiveTtsEngine,
        onRefreshEngines = { ttsController.requestRefreshTtsEngines() },
        onSelectEngine = { ttsController.selectEngine(it) },
        onSelectDefaultVoice = { ttsController.selectDefaultVoice() },
        onSelectVoice = { ttsController.selectVoice(it) }
    )

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
                    WebViewRegexFindAction(enabled = webViewRef.value != null) {
                        showFindDialog = true
                    }
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
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.epub_more_actions))
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) context.getString(R.string.epub_tts_stop)
                                        else context.getString(R.string.webview_tts_play)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) {
                                        ttsController.stop()
                                    } else {
                                        ttsController.start()
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_tts_engine)) },
                                onClick = {
                                    showMoreMenu = false
                                    ttsController.showTtsEngineDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_tts_voice)) },
                                onClick = {
                                    showMoreMenu = false
                                    ttsController.showTtsVoiceDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
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
                    th, td { border: 1px solid rgba(128,128,128,0.4); padding: 6px 10px; }
                    th { background: rgba(128,128,128,0.15); }
                    del, s { text-decoration: line-through; }
                    .task-list-item { list-style: none; margin-left: -1.5em; display: flex; align-items: flex-start; gap: 6px; }
                    .task-list-item input { margin: 0; flex-shrink: 0; vertical-align: middle; }
                    .task-list-item > p { margin: 0; flex: 1; }
                    sup a { text-decoration: none; color: #2196F3; }
                    .footnote-tooltip { position: fixed; max-width: 320px; padding: 10px 12px; background: rgba(30,30,30,0.95); color: #e0e0e0; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.3); z-index: 99999; font-size: 14px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
                    $syntaxHighlightCss
                </style>
            </head>
            <body>$htmlContent
            <script>$katexJs</script>
            <script>$autoRenderJs</script>
            <script>$mermaidJs</script>
            <script>$syntaxHighlightJs</script>
            <script>
            document.addEventListener("DOMContentLoaded", function() {
                if (typeof applySyntaxHighlight === "function") { applySyntaxHighlight(document); }
                function showFootnoteTooltip(linkEl, text) {
                    var old = document.getElementById("fn-tooltip"); if (old) old.remove();
                    var rect = linkEl.getBoundingClientRect();
                    var tip = document.createElement("div"); tip.id = "fn-tooltip"; tip.className = "footnote-tooltip"; tip.textContent = text;
                    tip.style.maxWidth = Math.min(320, window.innerWidth - 24) + "px";
                    tip.style.left = Math.max(8, Math.min(rect.left, window.innerWidth - 336)) + "px";
                    tip.style.top = (rect.top - 8) + "px"; tip.style.transform = "translateY(-100%)";
                    document.body.appendChild(tip);
                    setTimeout(function(){ document.addEventListener("click", function close(){ tip.remove(); document.removeEventListener("click", close); }); }, 0);
                }
                document.querySelectorAll('a[href^="#fn"], a.footnote-ref, sup a.footnote-ref').forEach(function(a) {
                    a.addEventListener("click", function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        var id = (a.getAttribute("href") || "").slice(1).replace(/^fnref/, "fn");
                        var def = document.getElementById(id);
                        if (def) { var text = (def.innerText || def.textContent || "").trim(); if (text) showFootnoteTooltip(a, text); }
                    });
                });
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
                        addJavascriptInterface(FootnoteToastHandler(context), "androidFootnote")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.let {
                                    regexFindUiState = RegexFindUiState()
                                    installRegexFindBridge(it)
                                    it.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                                }
                            }
                        }
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
    sessionCache: MarkdownViewerSessionCache,
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
    var loadingMessage by remember { mutableStateOf("加载中…") }
    var pageLoading by remember { mutableStateOf(false) }
    var showPageLoadingHint by remember { mutableStateOf(false) }
    var pendingInternalFile by remember { mutableStateOf<File?>(null) }
    var showFindDialog by remember { mutableStateOf(false) }
    var regexQuery by remember { mutableStateOf("") }
    var regexFindUiState by remember { mutableStateOf(RegexFindUiState()) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val ttsController = rememberStandaloneWebViewTtsController(
        preferredLocale = remember { Locale.getDefault() },
        contentToken = "mdzip:${currentFile.absolutePath}",
        documentTitle = currentFile.name,
        webViewProvider = { webViewRef.value },
        scrollRatioProvider = { currentWebViewScrollRatio(webViewRef.value) }
    )

    // 词典查询状态
    var dictLookupResult by remember { mutableStateOf<DictLookupResult?>(null) }
    var dictLoaded by remember { mutableStateOf<StarDictLoaded?>(null) }

    // 加载词典（只加载一次）
    LaunchedEffect(Unit) {
        val dicts = listImportedStarDicts(context)
        val firstDict = dicts.firstOrNull()
        if (firstDict != null) {
            dictLoaded = loadImportedStarDict(context, firstDict.id)
        }
    }

    // 词典查询函数
    fun lookupWord(word: String) {
        val loaded = dictLoaded
        if (loaded == null) {
            dictLookupResult = DictLookupResult(word, null, "没有可用的词典，请先导入词典")
            return
        }
        val found = lookupExactWord(loaded, word)
        if (found == null) {
            dictLookupResult = DictLookupResult(word, null, "词典中未找到 \"$word\"")
            return
        }
        val definition = readStarDictExplanation(context, loaded.summary.id, loaded, found)
        dictLookupResult = DictLookupResult(word, definition, null)
    }

    fun resetCurrentPageState(message: String = "正在准备文档…") {
        webViewRef.value = null
        htmlContent = null
        loadError = null
        loading = true
        loadingMessage = message
        pageLoading = false
        showPageLoadingHint = false
        regexFindUiState = RegexFindUiState()
    }

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
    val syntaxHighlightCss = remember { syntaxHighlightStyle() }
    val syntaxHighlightJs = remember { syntaxHighlightScript() }

    Log.d(MDZIP_DEBUG, "打开 zipFileName=$zipFileName contentDir=${contentDir.absolutePath} initialTargetFile=${initialTargetFile.absolutePath}")
    logDebug?.invoke("[MDZIP] 打开 zipFileName=$zipFileName")
    logDebug?.invoke("[MDZIP] contentDir=${contentDir.absolutePath}")
    logDebug?.invoke("[MDZIP] initialTargetFile=${initialTargetFile.absolutePath}")

    LaunchedEffect(pageLoading, currentFile.absolutePath) {
        if (!pageLoading) {
            showPageLoadingHint = false
            return@LaunchedEffect
        }
        showPageLoadingHint = false
        kotlinx.coroutines.delay(180)
        if (pageLoading) {
            showPageLoadingHint = true
        }
    }

    LaunchedEffect(currentFile) {
        regexFindUiState = RegexFindUiState()
        val cacheKey = "mdzip:${currentFile.absolutePath}"
        val cacheSignature = computeMarkdownCacheSignature(currentFile)
        sessionCache.getHtml(cacheKey, cacheSignature)?.let { cached ->
            htmlContent = cached
            loading = false
            pageLoading = true
            loadingMessage = "正在初始化页面样式与脚本…"
            Log.d(MDZIP_DEBUG, "[MDZIP] 使用缓存 path=$cacheKey")
            logDebug?.invoke("[MDZIP] 使用缓存 path=$cacheKey")
            return@LaunchedEffect
        }
        Log.d(MDZIP_DEBUG, "加载文件 path=${currentFile.absolutePath} exists=${currentFile.exists()} parentDir=${currentFile.parentFile?.absolutePath}")
        logDebug?.invoke("[MDZIP] 加载文件 path=${currentFile.absolutePath}")
        logDebug?.invoke("[MDZIP]   exists=${currentFile.exists()}")
        logDebug?.invoke("[MDZIP]   parentDir=${currentFile.parentFile?.absolutePath}")
        loading = true
        pageLoading = false
        loadError = null
        htmlContent = null
        loadingMessage = "正在读取压缩包中的文档…"
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
                loadingMessage = if (isRst) "正在解析 RST 结构…" else "正在解析 Markdown 结构…"
                val renderedHtml = if (isRst) {
                    rstToHtml(trimmed)
                } else {
                    markdownToHtml(trimmed)
                }
                htmlContent = renderedHtml
                sessionCache.putHtml(cacheKey, renderedHtml, cacheSignature)
                pageLoading = true
                loadingMessage = "正在初始化页面样式与脚本…"
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
                resetCurrentPageState()
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
            scheme == "file" -> MdLinkUtils.hostnameSegmentFromUrl(url)?.let { seg -> "https://$seg" }
            MdLinkUtils.looksLikeExternalUrl(url) -> {
                if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            }
            else -> null
        }
        if (finalUrl != null) {
            Log.d(MDZIP_DEBUG, "  识别为外链 finalUrl=$finalUrl")
            logDebug?.invoke("[MDZIP]   识别为外链 finalUrl=$finalUrl")
            mainHandler.post { pendingExternalUrl = finalUrl }
        } else {
            val pathForFile = url.substringBefore('#').trimEnd('/')
            val decoded = Uri.decode(if (scheme == "file") Uri.parse(pathForFile).path ?: pathForFile else pathForFile)
            Log.d(MDZIP_DEBUG, "  parsed.path=${parsed.path} decoded=$decoded")
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
            val target = backStack.last()
            resetCurrentPageState()
            backStack = backStack.dropLast(1)
            currentFile = target
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

    if (showFindDialog) {
        WebViewRegexFindDialog(
            query = regexQuery,
            result = regexFindUiState,
            onQueryChange = {
                regexQuery = it
                if (it.isBlank()) regexFindUiState = RegexFindUiState()
            },
            onSearch = {
                if (regexQuery.isBlank()) {
                    clearRegexFind(webViewRef.value) { regexFindUiState = it }
                } else {
                    searchInWebViewByRegex(webViewRef.value, regexQuery) { regexFindUiState = it }
                }
            },
            onPrevious = { moveRegexFindMatch(webViewRef.value, forward = false) { regexFindUiState = it } },
            onNext = { moveRegexFindMatch(webViewRef.value, forward = true) { regexFindUiState = it } },
            onClear = { clearRegexFind(webViewRef.value) { regexFindUiState = it } },
            onDismiss = { showFindDialog = false }
        )
    }

    OfflineTtsEngineVoiceDialogs(
        showEngineDialog = ttsController.showTtsEngineDialog,
        onDismissEngineDialog = { ttsController.showTtsEngineDialog = false },
        showVoiceDialog = ttsController.showTtsVoiceDialog,
        onDismissVoiceDialog = { ttsController.showTtsVoiceDialog = false },
        ttsEngineLoading = ttsController.ttsEngineLoading,
        availableTtsEngines = ttsController.availableTtsEngines,
        selectedTtsEnginePackage = ttsController.selectedTtsEnginePackage,
        selectedTtsVoiceName = ttsController.selectedTtsVoiceName,
        effectiveTtsEngine = ttsController.effectiveTtsEngine,
        onRefreshEngines = { ttsController.requestRefreshTtsEngines() },
        onSelectEngine = { ttsController.selectEngine(it) },
        onSelectDefaultVoice = { ttsController.selectDefaultVoice() },
        onSelectVoice = { ttsController.selectVoice(it) }
    )

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
                        WebViewRegexFindAction(enabled = webViewRef.value != null) {
                            showFindDialog = true
                        }
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
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.epub_more_actions))
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) context.getString(R.string.epub_tts_stop)
                                            else context.getString(R.string.webview_tts_play)
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) {
                                            ttsController.stop()
                                        } else {
                                            ttsController.start()
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.epub_tts_engine)) },
                                    onClick = {
                                        showMoreMenu = false
                                        ttsController.showTtsEngineDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.epub_tts_voice)) },
                                    onClick = {
                                        showMoreMenu = false
                                        ttsController.showTtsVoiceDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                            }
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
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(loadError!!, color = MaterialTheme.colorScheme.error)
                }
                htmlContent != null -> {
                    val bg = MaterialTheme.colorScheme.surface
                    val fg = MaterialTheme.colorScheme.onSurface
                    val bgHex = "#%02x%02x%02x".format(
                        (bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt()
                    )
                    val fgHex = "#%02x%02x%02x".format(
                        (fg.red * 255).toInt(), (fg.green * 255).toInt(), (fg.blue * 255).toInt()
                    )
                    val baseUrl = "file://${currentFile.parentFile?.absolutePath}/"
                    val fullHtml = buildMdViewerFullHtml(
                        htmlContent!!,
                        baseUrl,
                        katexCss,
                        katexJs,
                        autoRenderJs,
                        mermaidJs,
                        syntaxHighlightCss,
                        syntaxHighlightJs,
                        bgHex,
                        fgHex
                    )
                    androidx.compose.runtime.key(currentFile.absolutePath) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    setBackgroundColor(Color.TRANSPARENT)
                                    addJavascriptInterface(FootnoteToastHandler(context), "androidFootnote")
                                    addJavascriptInterface(PageReadyJsBridge {
                                        regexFindUiState = RegexFindUiState()
                                        installRegexFindBridge(this)
                                        evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                                        pageLoading = false
                                        loadingMessage = "页面已准备完成"
                                    }, "androidPageReady")
                                    @SuppressLint("SetJavaScriptEnabled")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = false
                                    settings.allowFileAccess = true
                                    webViewClient = MdZipWebViewClient(linkHolder, contentDir, currentFile, context) { w ->
                                        regexFindUiState = RegexFindUiState()
                                        installRegexFindBridge(w)
                                        w.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                                        pageLoading = false
                                        loadingMessage = "页面已准备完成"
                                    }
                                    val loadKey = "$baseUrl|${fullHtml.hashCode()}"
                                    tag = loadKey
                                    loadDataWithBaseURL(baseUrl, fullHtml, "text/html", "UTF-8", null)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { webView ->
                                webViewRef.value = webView
                                val loadKey = "$baseUrl|${fullHtml.hashCode()}"
                                if (webView.tag != loadKey) {
                                    pageLoading = true
                                    loadingMessage = "正在初始化页面样式与脚本…"
                                    webView.tag = loadKey
                                    webView.loadDataWithBaseURL(baseUrl, fullHtml, "text/html", "UTF-8", null)
                                }
                                webView.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                            }
                        )
                    }
                    if (loading || (pageLoading && showPageLoadingHint)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Card {
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                    Text(
                                        if (loading) "正在准备文档" else "正在准备页面",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        loadingMessage,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text("正在准备文档", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(loadingMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
    private val context: android.content.Context,
    private val onPageFinished: ((WebView) -> Unit)? = null
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (handleFootnoteClick(view, url, context)) return true
        val frag = url.substringAfter('#', "")
        val baseUrl = "file://${currentFile.parentFile?.absolutePath ?: ""}/"
        val isSamePageAnchor = frag.isNotBlank() && !frag.startsWith("fn") && !frag.startsWith("fnref") &&
            (url.trimStart().startsWith("#") || (url.startsWith(baseUrl) && url.length > baseUrl.length && url[baseUrl.length] == '#'))
        if (isSamePageAnchor) {
            scrollToAnchor(view, frag)
            return true
        }
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
    var showFindDialog by remember { mutableStateOf(false) }
    var regexQuery by remember { mutableStateOf("") }
    var regexFindUiState by remember { mutableStateOf(RegexFindUiState()) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var currentPageToken by remember { mutableStateOf(initialIndexFile.absolutePath) }
    val ttsController = rememberStandaloneWebViewTtsController(
        preferredLocale = remember { Locale.getDefault() },
        contentToken = "htmlzip:${currentPageToken.substringBefore('#')}",
        documentTitle = zipFileName,
        webViewProvider = { webViewRef.value },
        scrollRatioProvider = { currentWebViewScrollRatio(webViewRef.value) }
    )

    // 词典查询状态
    var dictLookupResult by remember { mutableStateOf<DictLookupResult?>(null) }
    var dictLoaded by remember { mutableStateOf<StarDictLoaded?>(null) }

    // 加载词典（只加载一次）
    LaunchedEffect(Unit) {
        val dicts = listImportedStarDicts(context)
        val firstDict = dicts.firstOrNull()
        if (firstDict != null) {
            dictLoaded = loadImportedStarDict(context, firstDict.id)
        }
    }

    // 词典查询函数
    fun lookupWord(word: String) {
        val loaded = dictLoaded
        if (loaded == null) {
            dictLookupResult = DictLookupResult(word, null, "没有可用的词典，请先导入词典")
            return
        }
        val found = lookupExactWord(loaded, word)
        if (found == null) {
            dictLookupResult = DictLookupResult(word, null, "词典中未找到 \"$word\"")
            return
        }
        val definition = readStarDictExplanation(context, loaded.summary.id, loaded, found)
        dictLookupResult = DictLookupResult(word, definition, null)
    }

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

    if (showFindDialog) {
        WebViewRegexFindDialog(
            query = regexQuery,
            result = regexFindUiState,
            onQueryChange = {
                regexQuery = it
                if (it.isBlank()) regexFindUiState = RegexFindUiState()
            },
            onSearch = {
                if (regexQuery.isBlank()) {
                    clearRegexFind(webViewRef.value) { regexFindUiState = it }
                } else {
                    searchInWebViewByRegex(webViewRef.value, regexQuery) { regexFindUiState = it }
                }
            },
            onPrevious = { moveRegexFindMatch(webViewRef.value, forward = false) { regexFindUiState = it } },
            onNext = { moveRegexFindMatch(webViewRef.value, forward = true) { regexFindUiState = it } },
            onClear = { clearRegexFind(webViewRef.value) { regexFindUiState = it } },
            onDismiss = { showFindDialog = false }
        )
    }

    OfflineTtsEngineVoiceDialogs(
        showEngineDialog = ttsController.showTtsEngineDialog,
        onDismissEngineDialog = { ttsController.showTtsEngineDialog = false },
        showVoiceDialog = ttsController.showTtsVoiceDialog,
        onDismissVoiceDialog = { ttsController.showTtsVoiceDialog = false },
        ttsEngineLoading = ttsController.ttsEngineLoading,
        availableTtsEngines = ttsController.availableTtsEngines,
        selectedTtsEnginePackage = ttsController.selectedTtsEnginePackage,
        selectedTtsVoiceName = ttsController.selectedTtsVoiceName,
        effectiveTtsEngine = ttsController.effectiveTtsEngine,
        onRefreshEngines = { ttsController.requestRefreshTtsEngines() },
        onSelectEngine = { ttsController.selectEngine(it) },
        onSelectDefaultVoice = { ttsController.selectDefaultVoice() },
        onSelectVoice = { ttsController.selectVoice(it) }
    )

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
                actions = {
                    WebViewRegexFindAction(enabled = webViewRef.value != null) {
                        showFindDialog = true
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.epub_more_actions))
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) context.getString(R.string.epub_tts_stop)
                                        else context.getString(R.string.webview_tts_play)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) {
                                        ttsController.stop()
                                    } else {
                                        ttsController.start()
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_tts_engine)) },
                                onClick = {
                                    showMoreMenu = false
                                    ttsController.showTtsEngineDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_tts_voice)) },
                                onClick = {
                                    showMoreMenu = false
                                    ttsController.showTtsVoiceDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
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
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        webViewClient = HtmlZipWebViewClient(contentDir, { url ->
                            pendingExternalUrl = url
                        }) { view ->
                            currentPageToken = view.url ?: currentPageToken
                            regexFindUiState = RegexFindUiState()
                            installRegexFindBridge(view)
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

private class RemoteHtmlWebViewClient(
    private val onExternalUrl: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onPageFinished: ((WebView) -> Unit)? = null
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val scheme = request.url?.scheme?.lowercase() ?: ""
        return when (scheme) {
            "mailto", "tel", "sms", "intent" -> {
                onExternalUrl(url)
                true
            }
            else -> false
        }
    }

    @Suppress("DEPRECATION")
    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        if (view == null || failingUrl != view.url) return
        onError("网页加载失败：${description ?: "未知错误"}（错误码：$errorCode）")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { onPageFinished?.invoke(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlViewerScreen(
    initialLocation: HtmlViewerLocation,
    onBack: () -> Unit
) {
    KeepScreenOnEffect()
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var currentLocation by remember(initialLocation) { mutableStateOf(initialLocation) }
    var localBackStack by remember { mutableStateOf(listOf<HtmlViewerLocation>()) }
    var localHtmlContent by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(initialLocation.localFileUri != null) }
    var pageLoading by remember { mutableStateOf(initialLocation.localFileUri == null) }
    var loadingMessage by remember { mutableStateOf("加载中…") }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    var showFindDialog by remember { mutableStateOf(false) }
    var regexQuery by remember { mutableStateOf("") }
    var regexFindUiState by remember { mutableStateOf(RegexFindUiState()) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var pendingAnchor by remember { mutableStateOf<String?>(null) }
    var currentPageToken by remember { mutableStateOf(initialLocation.localFileUri ?: initialLocation.initialUrl) }

    val ttsController = rememberStandaloneWebViewTtsController(
        preferredLocale = remember { Locale.getDefault() },
        contentToken = currentPageToken,
        documentTitle = currentLocation.title,
        webViewProvider = { webViewRef.value },
        scrollRatioProvider = { currentWebViewScrollRatio(webViewRef.value) }
    )
    val linkHolder = remember { LinkCallbackHolder() }
    val resourceHolder = remember { ResourceResolverHolder(initialLocation.localFileUri ?: "") }

    fun navigateTo(location: HtmlViewerLocation, pushCurrent: Boolean = true) {
        if (pushCurrent) {
            localBackStack = localBackStack + currentLocation
        }
        currentLocation = location
        currentPageToken = location.localFileUri ?: location.initialUrl
        loadError = null
        if (location.localFileUri != null) {
            loading = true
            pageLoading = false
            loadingMessage = "正在读取 HTML…"
        } else {
            loading = false
            pageLoading = true
            loadingMessage = "正在加载网页…"
        }
    }

    linkHolder.onLink = { url ->
        val localUri = currentLocation.localFileUri
        val parsed = Uri.parse(url)
        val scheme = parsed.scheme?.lowercase() ?: ""
        val fragment = parsed.fragment?.takeIf { it.isNotBlank() }
        val mainHandler = Handler(Looper.getMainLooper())
        if (localUri != null) {
            when {
                scheme == "http" || scheme == "https" -> {
                    mainHandler.post {
                        navigateTo(HtmlViewerLocation(initialUrl = url, title = url, localFileUri = null))
                    }
                }
                scheme == "mailto" || scheme == "tel" || scheme == "sms" || scheme == "intent" -> {
                    mainHandler.post { pendingExternalUrl = url }
                }
                else -> {
                    val resolved = resolveRelativeToCurrent(context, localUri, url)
                    if (resolved != null) {
                        val targetUri = Uri.parse(resolved)
                        val name = DocumentFile.fromSingleUri(context, targetUri)?.name
                            ?: targetUri.lastPathSegment
                            ?: "HTML"
                        if (name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)) {
                            mainHandler.post {
                                pendingAnchor = fragment
                                navigateTo(
                                    HtmlViewerLocation(
                                        initialUrl = resolved,
                                        title = name,
                                        localFileUri = resolved
                                    )
                                )
                            }
                        } else {
                            mainHandler.post {
                                Toast.makeText(context, "当前 HTML 查看器只支持继续打开 HTML/HTM 链接", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else if (MdLinkUtils.looksLikeExternalUrl(url)) {
                        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                        mainHandler.post {
                            navigateTo(HtmlViewerLocation(initialUrl = normalized, title = normalized, localFileUri = null))
                        }
                    } else {
                        mainHandler.post {
                            Toast.makeText(context, "无法解析 HTML 链接：$url", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(currentLocation.localFileUri) {
        val localUri = currentLocation.localFileUri ?: run {
            localHtmlContent = null
            loadError = null
            loading = false
            pageLoading = true
            loadingMessage = "正在加载网页…"
            return@LaunchedEffect
        }
        regexFindUiState = RegexFindUiState()
        loading = true
        pageLoading = false
        loadingMessage = "正在读取 HTML…"
        localHtmlContent = null
        loadError = null
        val decoded = withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStreamSafe(Uri.parse(localUri))
            if (stream == null) {
                loadError = "无法打开 HTML 文件：$localUri"
                return@withContext null
            }
            stream.use { raw ->
                raw.readBytesUpTo(MAX_LOCAL_HTML_BYTES).decodeToString().dropLastWhile { it == '\uFFFD' }
            }
        }
        localHtmlContent = decoded
        loading = false
        pageLoading = decoded != null
        loadingMessage = if (decoded != null) "正在初始化页面…" else loadingMessage
    }

    BackHandler {
        val webView = webViewRef.value
        when {
            currentLocation.localFileUri == null && webView != null && webView.canGoBack() -> webView.goBack()
            localBackStack.isNotEmpty() -> {
                val previous = localBackStack.last()
                localBackStack = localBackStack.dropLast(1)
                currentLocation = previous
                currentPageToken = previous.localFileUri ?: previous.initialUrl
            }
            else -> onBack()
        }
    }

    if (pendingExternalUrl != null) {
        val urlToShow = pendingExternalUrl!!
        AlertDialog(
            onDismissRequest = { pendingExternalUrl = null },
            title = { Text("链接") },
            text = {
                Column {
                    Text(urlToShow, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
                    } catch (_: Exception) {
                    }
                    pendingExternalUrl = null
                }) { Text("复制链接") }
                TextButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToShow))
                        context.startActivity(Intent.createChooser(intent, "用浏览器打开"))
                    } catch (_: Exception) {
                    }
                    pendingExternalUrl = null
                }) { Text("用浏览器打开") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalUrl = null }) { Text("取消") }
            }
        )
    }

    if (showFindDialog) {
        WebViewRegexFindDialog(
            query = regexQuery,
            result = regexFindUiState,
            onQueryChange = {
                regexQuery = it
                if (it.isBlank()) regexFindUiState = RegexFindUiState()
            },
            onSearch = {
                if (regexQuery.isBlank()) {
                    clearRegexFind(webViewRef.value) { regexFindUiState = it }
                } else {
                    searchInWebViewByRegex(webViewRef.value, regexQuery) { regexFindUiState = it }
                }
            },
            onPrevious = { moveRegexFindMatch(webViewRef.value, forward = false) { regexFindUiState = it } },
            onNext = { moveRegexFindMatch(webViewRef.value, forward = true) { regexFindUiState = it } },
            onClear = { clearRegexFind(webViewRef.value) { regexFindUiState = it } },
            onDismiss = { showFindDialog = false }
        )
    }

    OfflineTtsEngineVoiceDialogs(
        showEngineDialog = ttsController.showTtsEngineDialog,
        onDismissEngineDialog = { ttsController.showTtsEngineDialog = false },
        showVoiceDialog = ttsController.showTtsVoiceDialog,
        onDismissVoiceDialog = { ttsController.showTtsVoiceDialog = false },
        ttsEngineLoading = ttsController.ttsEngineLoading,
        availableTtsEngines = ttsController.availableTtsEngines,
        selectedTtsEnginePackage = ttsController.selectedTtsEnginePackage,
        selectedTtsVoiceName = ttsController.selectedTtsVoiceName,
        effectiveTtsEngine = ttsController.effectiveTtsEngine,
        onRefreshEngines = { ttsController.requestRefreshTtsEngines() },
        onSelectEngine = { ttsController.selectEngine(it) },
        onSelectDefaultVoice = { ttsController.selectDefaultVoice() },
        onSelectVoice = { ttsController.selectVoice(it) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(currentLocation.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val webView = webViewRef.value
                        when {
                            currentLocation.localFileUri == null && webView != null && webView.canGoBack() -> webView.goBack()
                            localBackStack.isNotEmpty() -> {
                                val previous = localBackStack.last()
                                localBackStack = localBackStack.dropLast(1)
                                currentLocation = previous
                                currentPageToken = previous.localFileUri ?: previous.initialUrl
                            }
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    WebViewRegexFindAction(enabled = webViewRef.value != null) {
                        showFindDialog = true
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.epub_more_actions))
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) context.getString(R.string.epub_tts_stop)
                                        else context.getString(R.string.webview_tts_play)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) ttsController.stop() else ttsController.start()
                                },
                                leadingIcon = {
                                    Icon(
                                        if (ttsController.ttsIsSpeaking || ttsController.ttsIsStarting) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_tts_engine)) },
                                onClick = {
                                    showMoreMenu = false
                                    ttsController.showTtsEngineDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_tts_voice)) },
                                onClick = {
                                    showMoreMenu = false
                                    ttsController.showTtsVoiceDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
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
                loadError != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SelectionContainer {
                            Text(loadError!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                currentLocation.localFileUri != null && localHtmlContent == null && loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                Text("正在准备 HTML", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Text(loadingMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                else -> {
                    val localFileUri = currentLocation.localFileUri
                    if (localFileUri != null) {
                        resourceHolder.currentUri = localFileUri
                        val htmlContent = localHtmlContent.orEmpty()
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    setBackgroundColor(Color.TRANSPARENT)
                                    addJavascriptInterface(FootnoteToastHandler(context), "androidFootnote")
                                    addJavascriptInterface(PageReadyJsBridge {
                                        regexFindUiState = RegexFindUiState()
                                        installRegexFindBridge(this)
                                        installEpubTtsBridge(this)
                                        pageLoading = false
                                        loadingMessage = "页面已准备完成"
                                        currentPageToken = currentLocation.localFileUri ?: currentLocation.initialUrl
                                        pendingAnchor?.let { anchor ->
                                            scrollToAnchor(this, anchor)
                                            pendingAnchor = null
                                        }
                                    }, "androidPageReady")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = LinkInterceptClient(linkHolder, context, resourceHolder) { view ->
                                        regexFindUiState = RegexFindUiState()
                                        installRegexFindBridge(view)
                                        installEpubTtsBridge(view)
                                        pageLoading = false
                                        loadingMessage = "页面已准备完成"
                                        currentPageToken = currentLocation.localFileUri ?: currentLocation.initialUrl
                                        pendingAnchor?.let { anchor ->
                                            scrollToAnchor(view, anchor)
                                            pendingAnchor = null
                                        }
                                    }
                                    webViewRef.value = this
                                    loadDataWithBaseURL(MD_VIEWER_BASE_URL, htmlContent, "text/html", "UTF-8", null)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { webView ->
                                webViewRef.value = webView
                                resourceHolder.currentUri = localFileUri
                                val loadKey = "html-local|$localFileUri|${htmlContent.hashCode()}"
                                if (webView.tag != loadKey) {
                                    pageLoading = true
                                    loadingMessage = "正在初始化页面…"
                                    webView.tag = loadKey
                                    webView.loadDataWithBaseURL(MD_VIEWER_BASE_URL, htmlContent, "text/html", "UTF-8", null)
                                }
                            }
                        )
                    } else {
                        val remoteUrl = currentLocation.initialUrl
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    setBackgroundColor(Color.TRANSPARENT)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    webViewClient = RemoteHtmlWebViewClient(
                                        onExternalUrl = { url -> pendingExternalUrl = url },
                                        onError = { error ->
                                            loadError = error
                                            pageLoading = false
                                        }
                                    ) { view ->
                                        regexFindUiState = RegexFindUiState()
                                        installRegexFindBridge(view)
                                        installEpubTtsBridge(view)
                                        pageLoading = false
                                        loadingMessage = "页面已准备完成"
                                        loadError = null
                                        currentPageToken = view.url ?: remoteUrl
                                    }
                                    webViewRef.value = this
                                    loadUrl(remoteUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { webView ->
                                webViewRef.value = webView
                                val loadKey = "html-remote|$remoteUrl"
                                if (webView.tag != loadKey) {
                                    loadError = null
                                    pageLoading = true
                                    loadingMessage = "正在加载网页…"
                                    webView.tag = loadKey
                                    webView.loadUrl(remoteUrl)
                                }
                            }
                        )
                    }
                    if (loading || pageLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Card {
                                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                    Text(
                                        if (loading) "正在准备 HTML" else "正在准备页面",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        loadingMessage,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** WebViewClient：允许 file 协议下 contentDir 内跳转，外链弹窗；子资源从 cache 提供。 */
private class HtmlZipWebViewClient(
    private val contentDir: File,
    private val onExternalUrl: (String) -> Unit,
    private val onPageFinished: ((WebView) -> Unit)? = null
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

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { onPageFinished?.invoke(it) }
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

// ---- EPUB 电子书查看器 ----

private const val EPUB_DEBUG = "EpubViewer"

/** EPUB 电子书查看器：支持章节导航、目录、缩放、收藏夹。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubViewerScreen(
    extractResult: EpubExtractResult,
    zipFileName: String,
    epubUri: Uri,
    bookNoteLoadedData: BookNoteLoadedData?,
    bookNoteEntries: List<BookNoteEntry>,
    bookNoteInProgress: Boolean,
    onRequestOpenBookNotes: () -> Unit,
    onBookNoteEntriesChanged: (List<BookNoteEntry>) -> Unit,
    onBack: () -> Unit,
    logDebug: ((String) -> Unit)? = null
) {
    KeepScreenOnEffect()
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }
    val scope = rememberCoroutineScope()
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var scalePercent by remember { mutableStateOf(100) }
    var scalePercentLoaded by remember { mutableStateOf(false) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    var showToc by remember { mutableStateOf(false) }
    var tocQuery by remember { mutableStateOf("") }
    var showBookmarks by remember { mutableStateOf(false) }
    var showAddBookmark by remember { mutableStateOf(false) }
    var pendingBookmarkQuote by remember { mutableStateOf("") }
    var currentChapterIndex by remember { mutableStateOf(0) }
    var currentScrollRatio by remember { mutableStateOf(0f) }
    var pendingProgrammaticScrollRatio by remember { mutableStateOf<Float?>(null) }
    var showFindDialog by remember { mutableStateOf(false) }
    var showFullTextSearchDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var editingBookNote by remember { mutableStateOf<BookNoteEditorState?>(null) }
    var deleteBookNoteConfirm by remember { mutableStateOf<BookNoteEntry?>(null) }
    var regexQuery by remember { mutableStateOf("") }
    var regexFindUiState by remember { mutableStateOf(RegexFindUiState()) }
    var fullTextQuery by remember { mutableStateOf("") }
    var fullTextSearchResults by remember { mutableStateOf<List<EpubFullTextSearchResult>>(emptyList()) }
    var fullTextSearchError by remember { mutableStateOf<String?>(null) }
    var fullTextSearching by remember { mutableStateOf(false) }
    var pendingFullTextHighlightQuery by remember { mutableStateOf<String?>(null) }
    var showTtsEngineDialog by remember { mutableStateOf(false) }
    var showTtsVoiceDialog by remember { mutableStateOf(false) }
    var availableTtsEngines by remember { mutableStateOf<List<EpubOfflineTtsEngine>>(emptyList()) }
    var ttsEngineLoading by remember { mutableStateOf(false) }
    var ttsStatusMessage by remember { mutableStateOf<String?>(null) }
    var ttsIsStarting by remember { mutableStateOf(false) }
    var ttsIsSpeaking by remember { mutableStateOf(false) }
    var ttsRequestJob by remember { mutableStateOf<Job?>(null) }
    var currentChapterTtsSegments by remember { mutableStateOf<List<EpubTtsSegment>>(emptyList()) }
    var ttsSegmentsLoading by remember { mutableStateOf(false) }
    var ttsActiveSegmentId by remember { mutableStateOf<String?>(null) }
    var ttsSpeakingChapterIndex by remember { mutableStateOf<Int?>(null) }
    var pendingTtsStartChapterIndex by remember { mutableStateOf<Int?>(null) }
    var ttsPendingRefreshAttempted by remember { mutableStateOf(false) }
    var autoAdvanceTtsChapterIndex by remember { mutableStateOf<Int?>(null) }
    val preferredTtsLocale = remember(extractResult.bookInfo.language) {
        preferredEpubTtsLocale(extractResult.bookInfo.language)
    }
    val selectedTtsEnginePackage by prefs.epubTtsEnginePackage.collectAsState(initial = null)
    val selectedTtsVoiceName by prefs.epubTtsVoiceName.collectAsState(initial = null)
    val epubTtsSpeedPercent by prefs.epubTtsSpeedPercent.collectAsState(initial = 100)
    val epubTtsAutoNextChapter by prefs.epubTtsAutoNextChapter.collectAsState(initial = true)
    val hideReaderFloatingNextButton by prefs.hideReaderFloatingNextButton.collectAsState(initial = false)
    val floatingButtonXPercent by prefs.readerFloatingNextButtonXPercent.collectAsState(initial = 100)
    val floatingButtonYPercent by prefs.readerFloatingNextButtonYPercent.collectAsState(initial = 82)
    val ttsSession = remember { EpubTtsSession(context) }

    // 词典查询状态
    var dictLookupResult by remember { mutableStateOf<DictLookupResult?>(null) }
    var dictLoaded by remember { mutableStateOf<StarDictLoaded?>(null) }
    var dictLoading by remember { mutableStateOf(false) }
    val dictAreaExpanded by prefs.epubDictAreaExpanded.collectAsState(initial = false)
    val persistedDictLookupWords by prefs.epubDictLookupWords.collectAsState(initial = emptyList())
    // 词典查询历史记录（用于回退）
    var dictHistory by remember { mutableStateOf<List<DictLookupResult>>(emptyList()) }
    var dictLookupHistoryRestored by remember { mutableStateOf(false) }

    // 加载词典（只加载一次）
    LaunchedEffect(Unit) {
        dictLoading = true
        val dicts = listImportedStarDicts(context)
        val firstDict = dicts.firstOrNull()
        if (firstDict != null) {
            dictLoaded = loadImportedStarDict(context, firstDict.id)
        }
        dictLoading = false
    }

    // 生成词形变体的候选词列表
    fun generateWordCandidates(word: String): List<String> {
        val candidates = mutableListOf<String>()
        val lowerWord = word.lowercase()

        // 尝试去掉常见后缀
        // 过去式/过去分词 -ed
        if (lowerWord.endsWith("ied")) {
            candidates.add(word.dropLast(3) + "y")  // tried -> try
        }
        if (lowerWord.endsWith("ed") && word.length > 3) {
            candidates.add(word.dropLast(1))  // moved -> move
            candidates.add(word.dropLast(2))  // stopped -> stop (doubled consonant)
            candidates.add(word.dropLast(3))  // stopped -> stop
        }

        // 现在分词 -ing
        if (lowerWord.endsWith("ying") && word.length > 4) {
            candidates.add(word.dropLast(4) + "ie")  // dying -> die
        }
        if (lowerWord.endsWith("ing") && word.length > 4) {
            candidates.add(word.dropLast(3))  // moving -> move
            candidates.add(word.dropLast(3) + "e")  // moving -> move (if stored as move)
            candidates.add(word.dropLast(4))  // stopping -> stop (doubled consonant)
        }

        // 复数 -s/-es
        if (lowerWord.endsWith("ies") && word.length > 4) {
            candidates.add(word.dropLast(3) + "y")  // countries -> country
        }
        if (lowerWord.endsWith("es") && word.length > 3) {
            candidates.add(word.dropLast(2))  // watches -> watch
            candidates.add(word.dropLast(1))  // watches -> watche (try anyway)
        }
        if (lowerWord.endsWith("s") && word.length > 2) {
            candidates.add(word.dropLast(1))  // cats -> cat
        }

        // 比较级/最高级 -er/-est
        if (lowerWord.endsWith("ier")) {
            candidates.add(word.dropLast(3) + "y")  // happier -> happy
        }
        if (lowerWord.endsWith("er") && word.length > 3) {
            candidates.add(word.dropLast(1))  // bigger -> big (doubled consonant)
            candidates.add(word.dropLast(2))  // taller -> tall
            candidates.add(word.dropLast(1) + "e")  // nicer -> nice
        }
        if (lowerWord.endsWith("iest")) {
            candidates.add(word.dropLast(4) + "y")  // happiest -> happy
        }
        if (lowerWord.endsWith("est") && word.length > 4) {
            candidates.add(word.dropLast(2))  // biggest -> big (doubled consonant)
            candidates.add(word.dropLast(3))  // tallest -> tall
            candidates.add(word.dropLast(2) + "e")  // nicest -> nice
        }

        // 副词 -ly
        if (lowerWord.endsWith("ily")) {
            candidates.add(word.dropLast(3) + "y")  // happily -> happy
        }
        if (lowerWord.endsWith("ly") && word.length > 3) {
            candidates.add(word.dropLast(2))  // slowly -> slow
            candidates.add(word.dropLast(1))  // quickly -> quick
        }

        return candidates.distinct()
    }

    // 词典查询函数（不修改历史，用于恢复和通用复用）
    fun resolveLookupResult(word: String): DictLookupResult {
        val loaded = dictLoaded
        if (loaded == null) {
            return DictLookupResult(word, null, "没有可用的词典，请先导入词典")
        }

        // 尝试精确匹配
        var found = lookupExactWord(loaded, word)
        var matchedWord = word

        // 如果精确匹配失败，尝试去掉常见后缀
        if (found == null) {
            val candidates = generateWordCandidates(word)
            for (candidate in candidates) {
                found = lookupExactWord(loaded, candidate)
                if (found != null) {
                    matchedWord = candidate
                    break
                }
            }
        }

        if (found == null) {
            return DictLookupResult(word, null, "词典中未找到 \"$word\"")
        }
        val definition = readStarDictExplanation(context, loaded.summary.id, loaded, found)
        return DictLookupResult(matchedWord, definition, null)
    }

    fun persistDictLookupStack() {
        val words = buildList {
            dictHistory.forEach { if (it.word.isNotBlank()) add(it.word) }
            dictLookupResult?.word?.let { if (it.isNotBlank()) add(it) }
        }
        scope.launch {
            prefs.setEpubDictLookupWords(words)
        }
    }

    // 词典查询函数
    fun lookupWord(word: String) {
        // 如果当前有查询结果，先保存到历史记录
        val current = dictLookupResult
        if (current != null && current.word.isNotBlank()) {
            dictHistory = dictHistory + current
        }
        dictLookupResult = resolveLookupResult(word)
        persistDictLookupStack()
    }

    // 回退到上一个查询结果
    fun goBackInDictHistory() {
        if (dictHistory.isNotEmpty()) {
            val last = dictHistory.last()
            dictHistory = dictHistory.dropLast(1)
            dictLookupResult = last
            persistDictLookupStack()
        }
    }

    // 词典加载完成后，恢复上次查询栈（最后一个作为当前，其余用于回退）
    LaunchedEffect(dictLoaded, persistedDictLookupWords, dictLookupHistoryRestored) {
        if (dictLookupHistoryRestored) return@LaunchedEffect
        if (dictLoaded == null) return@LaunchedEffect
        if (persistedDictLookupWords.isEmpty()) {
            dictLookupHistoryRestored = true
            return@LaunchedEffect
        }
        val restored = persistedDictLookupWords.map { resolveLookupResult(it) }
        if (restored.isNotEmpty()) {
            dictHistory = if (restored.size > 1) restored.dropLast(1) else emptyList()
            dictLookupResult = restored.last()
        }
        dictLookupHistoryRestored = true
    }

    // 剪贴板监听器 - 当词典区域展开时，监控剪贴板变化并自动查词
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }
    val lookupWordRef = remember { { word: String -> lookupWord(word) } }
    DisposableEffect(clipboardManager, dictAreaExpanded) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            if (dictAreaExpanded) {
                val clip = clipboardManager?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0)?.text?.toString()?.trim()
                    if (!text.isNullOrBlank()) {
                        lookupWordRef(text)
                    }
                }
            }
        }
        clipboardManager?.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboardManager?.removePrimaryClipChangedListener(listener)
        }
    }

    // 旧收藏夹管理器仅用于把历史数据迁移到全局读书笔记
    val bookmarkManager = remember(epubUri, extractResult.cacheDir.absolutePath, extractResult.isEncrypted) {
        EpubBookmarkManager(
            context = context,
            cacheDir = extractResult.cacheDir.takeIf { extractResult.isEncrypted },
            scopedUri = epubUri.toString()
        )
    }
    val bookmarks by bookmarkManager.bookmarks.collectAsState()
    val legacyEpubBookmarks = bookmarks.filter { it.epubUri == epubUri.toString() }

    val chapters = extractResult.chapters
    val currentBookTitle = extractResult.bookInfo.title.ifBlank { zipFileName }
    val currentBookNoteEntries = remember(bookNoteEntries, currentBookTitle) {
        bookNoteEntries.filter { it.matchesBookTitle(currentBookTitle) }
    }
    val currentBookBookmarkEntries = remember(currentBookNoteEntries) {
        currentBookNoteEntries
            .filter { it.hasLocation() }
            .sortedByDescending { it.createdAt }
    }
    val visualBookmarks = remember(currentBookBookmarkEntries, epubUri, zipFileName) {
        currentBookBookmarkEntries.map { entry ->
            EpubBookmark(
                id = entry.id.toString(),
                epubUri = epubUri.toString(),
                epubFileName = zipFileName,
                chapterIndex = entry.chapterIndex ?: 0,
                chapterTitle = entry.chapterTitle ?: entry.chapterInfo.orEmpty(),
                scrollPosition = 0,
                scrollRatio = entry.scrollRatio ?: 0f,
                note = entry.content,
                createTime = entry.createdAt,
                highlightText = entry.quote.orEmpty()
            )
        }
    }
    val contentDir = extractResult.contentDir
    val opfDir = extractResult.opfDir
    val chapterScrollRatios = remember { mutableMapOf<Int, Float>() }
    var legacyBookmarkMigrationDone by remember(epubUri) { mutableStateOf(false) }

    fun currentChapterLocationLabel(): String {
        val chapter = chapters.getOrNull(currentChapterIndex)
        return "第${currentChapterIndex + 1}章 - ${chapter?.title ?: ""} · ${formatEpubChapterProgressCompact(currentScrollRatio)}"
    }

    fun decodeEvaluateJavascriptString(result: String?): String {
        if (result == null || result == "null") return ""
        return runCatching { org.json.JSONObject("{\"value\":$result}").optString("value", "") }
            .getOrElse { result.removePrefix("\"").removeSuffix("\"").replace("\\n", "\n").replace("\\\"", "\"") }
            .trim()
    }

    fun requestCurrentBookmarkQuote(onResult: (String) -> Unit) {
        val view = webViewRef.value ?: run {
            onResult("")
            return
        }
        val quotedSelector = org.json.JSONObject.quote(EPUB_BOOKMARK_TARGET_SELECTOR)
        val js = """
            (function() {
                function normalize(value) {
                    return String(value || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
                }
                function shorten(value) {
                    if (value.length > $EPUB_BOOKMARK_QUOTE_MAX_LENGTH) {
                        return value.slice(0, $EPUB_BOOKMARK_QUOTE_MAX_LENGTH).trim() + '…';
                    }
                    return value;
                }
                var selector = $quotedSelector;
                var selection = window.getSelection ? window.getSelection() : null;
                var selectedText = normalize(selection && selection.rangeCount > 0 ? selection.toString() : '');
                if (selectedText) {
                    return shorten(selectedText);
                }
                var viewportWidth = window.innerWidth || document.documentElement.clientWidth || 0;
                var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                var x = Math.floor(viewportWidth / 2);
                var y = Math.floor(viewportHeight / 2);
                if (selection && selection.rangeCount > 0) {
                    var range = selection.getRangeAt(0);
                    var rect = range && range.getBoundingClientRect ? range.getBoundingClientRect() : null;
                    if (rect && (rect.width > 0 || rect.height > 0)) {
                        x = Math.floor(Math.max(0, Math.min(viewportWidth - 1, rect.left + rect.width / 2)));
                        y = Math.floor(Math.max(0, Math.min(viewportHeight - 1, rect.top + rect.height / 2)));
                    }
                }
                var el = document.elementFromPoint(x, y);
                if (!el) return '';
                var container = el.closest ? el.closest(selector) : el;
                while (container && container.tagName && container.tagName.toLowerCase() === 'div' && container.children.length === 1) {
                    var child = container.children[0];
                    if (!child || !child.matches || !child.matches(selector)) break;
                    container = child;
                }
                var text = normalize((container && (container.innerText || container.textContent)) || (el.innerText || el.textContent));
                return shorten(text);
            })();
        """.trimIndent()
        view.evaluateJavascript(js) { result ->
            onResult(decodeEvaluateJavascriptString(result))
        }
    }

    fun buildCurrentPositionBookNote(
        id: Long,
        content: String,
        quote: String,
        createdAt: Long = System.currentTimeMillis()
    ): BookNoteEntry {
        val chapter = chapters.getOrNull(currentChapterIndex)
        return BookNoteEntry(
            id = id,
            bookTitle = currentBookTitle,
            chapterInfo = currentChapterLocationLabel(),
            quote = quote.takeIf { it.isNotBlank() },
            content = content.trimEnd(),
            chapterIndex = currentChapterIndex,
            chapterTitle = chapter?.title,
            scrollRatio = currentScrollRatio.coerceIn(0f, 1f),
            createdAt = createdAt
        )
    }

    fun upsertBookNoteEntry(updatedEntry: BookNoteEntry) {
        onBookNoteEntriesChanged(
            if (bookNoteEntries.any { it.id == updatedEntry.id }) {
                bookNoteEntries.map { entry -> if (entry.id == updatedEntry.id) updatedEntry else entry }
            } else {
                bookNoteEntries + updatedEntry
            }
        )
    }

    fun goToBookNote(entry: BookNoteEntry) {
        val targetChapterIndex = entry.chapterIndex
        val targetScrollRatio = entry.scrollRatio
        if (targetChapterIndex == null || targetScrollRatio == null || targetChapterIndex !in chapters.indices) {
            Toast.makeText(context, "这条读书笔记没有可跳转的位置", Toast.LENGTH_SHORT).show()
            return
        }
        chapterScrollRatios[currentChapterIndex] = currentScrollRatio.coerceIn(0f, 1f)
        chapterScrollRatios[targetChapterIndex] = targetScrollRatio.coerceIn(0f, 1f)
        currentChapterIndex = targetChapterIndex
        currentScrollRatio = targetScrollRatio.coerceIn(0f, 1f)
        pendingProgrammaticScrollRatio = targetScrollRatio.coerceIn(0f, 1f)
        showBookmarks = false
    }

    LaunchedEffect(bookNoteLoadedData, legacyEpubBookmarks, currentBookTitle, bookNoteEntries) {
        if (legacyBookmarkMigrationDone || bookNoteLoadedData == null || legacyEpubBookmarks.isEmpty()) return@LaunchedEffect
        val merged = bookNoteEntries.toMutableList()
        var added = false
        legacyEpubBookmarks.forEach { bookmark ->
            val exists = merged.any {
                it.matchesBookTitle(currentBookTitle) && it.matchesLocation(bookmark.chapterIndex, bookmark.scrollRatio)
            }
            if (!exists) {
                merged += BookNoteEntry(
                    id = (merged.maxOfOrNull { entry -> entry.id } ?: 0L) + 1L,
                    bookTitle = currentBookTitle,
                    chapterInfo = "第${bookmark.chapterIndex + 1}章 - ${bookmark.chapterTitle.ifBlank { "未命名章节" }} · ${formatEpubBookmarkPosition(bookmark.scrollRatio)}",
                    quote = bookmark.highlightText.takeIf { it.isNotBlank() },
                    content = bookmark.note,
                    chapterIndex = bookmark.chapterIndex,
                    chapterTitle = bookmark.chapterTitle,
                    scrollRatio = bookmark.scrollRatio,
                    createdAt = bookmark.createTime
                )
                added = true
            }
        }
        legacyBookmarkMigrationDone = true
        if (added) {
            onBookNoteEntriesChanged(merged)
        }
        bookmarkManager.clearBookmarksForEpub(epubUri.toString())
    }

    fun requestRefreshTtsEngines() {
        scope.launch {
            ttsEngineLoading = true
            availableTtsEngines = withContext(Dispatchers.IO) {
                loadOfflineTtsEngines(context, preferredTtsLocale)
            }
            ttsEngineLoading = false
        }
    }

    val effectiveTtsEngine = remember(availableTtsEngines, selectedTtsEnginePackage) {
        availableTtsEngines.firstOrNull { it.packageName == selectedTtsEnginePackage }
            ?: availableTtsEngines.firstOrNull()
    }
    val effectiveTtsVoice = remember(effectiveTtsEngine, selectedTtsVoiceName) {
        selectedTtsVoiceName?.let { selectedVoice ->
            effectiveTtsEngine?.offlineVoices?.firstOrNull { it.name == selectedVoice }
        }
    }

    fun publishTtsStatus(message: String, toast: Boolean = true) {
        ttsStatusMessage = message
        if (toast) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun rememberedChapterScrollRatio(index: Int, fallback: Float = 0f): Float {
        return chapterScrollRatios[index] ?: fallback
    }

    fun switchToChapter(index: Int, fallbackScrollRatio: Float = 0f) {
        if (index !in chapters.indices) return
        chapterScrollRatios[currentChapterIndex] = currentScrollRatio.coerceIn(0f, 1f)
        val restoredRatio = rememberedChapterScrollRatio(index, fallbackScrollRatio).coerceIn(0f, 1f)
        currentChapterIndex = index
        currentScrollRatio = restoredRatio
        pendingProgrammaticScrollRatio = restoredRatio
    }

    fun applyLiteralFindToCurrentChapter(rawQuery: String, targetView: WebView? = webViewRef.value) {
        if (rawQuery.isBlank()) return
        regexQuery = rawQuery
        searchInWebViewByRegex(targetView, Regex.escape(rawQuery)) { regexFindUiState = it }
    }

    fun runFullTextSearch() {
        val query = fullTextQuery.trim()
        if (query.isBlank()) {
            fullTextSearchResults = emptyList()
            fullTextSearchError = "请输入要搜索的文字"
            return
        }
        scope.launch {
            fullTextSearching = true
            fullTextSearchError = null
            try {
                fullTextSearchResults = searchEpubAcrossChapters(extractResult, query)
            } catch (error: Exception) {
                fullTextSearchResults = emptyList()
                fullTextSearchError = "全文查找失败：${error.message ?: error.javaClass.simpleName}"
            } finally {
                fullTextSearching = false
            }
        }
    }

    LaunchedEffect(epubUri) {
        val restored = withContext(Dispatchers.IO) {
            prefs.getEpubZoomPercentForUri(epubUri.toString())
        }
        if (restored != null) {
            scalePercent = restored
        }
        scalePercentLoaded = true
    }

    LaunchedEffect(scalePercentLoaded, scalePercent, epubUri) {
        if (!scalePercentLoaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            prefs.setEpubZoomPercentForUri(epubUri.toString(), scalePercent)
        }
    }

    logDebug?.invoke("[EPUB] 打开 $zipFileName")
    logDebug?.invoke("[EPUB] 章节数=${chapters.size}")
    logDebug?.invoke("[EPUB] contentDir=${contentDir.absolutePath}")

    // 初始恢复进度标志
    var isRestoringProgress by remember { mutableStateOf(true) }
    var restoredProgressMarker by remember { mutableStateOf<EpubReadingProgress?>(null) }

    // 恢复阅读进度
    LaunchedEffect(Unit) {
        val uriString = epubUri.toString()
        val uriHash = uriString.hashCode().toUInt().toString(16)
        Log.d("EpubViewer", "尝试恢复进度: URI=$uriString, hash=$uriHash")
        logDebug?.invoke("[EPUB] 尝试恢复进度: URI hash=$uriHash")

        val progress = bookmarkManager.loadProgress(uriString)
        if (progress != null) {
            // 先设置滚动比例，再设置章节索引，避免中间状态
            currentScrollRatio = progress.scrollRatio
            currentChapterIndex = progress.chapterIndex
            pendingProgrammaticScrollRatio = progress.scrollRatio
            chapterScrollRatios[progress.chapterIndex] = progress.scrollRatio.coerceIn(0f, 1f)
            restoredProgressMarker = progress
            Log.d("EpubViewer", "恢复进度成功: 章节${progress.chapterIndex}, 比例${progress.scrollRatio}")
            logDebug?.invoke("[EPUB] 恢复进度: 章节${progress.chapterIndex}, 比例${progress.scrollRatio}")
        } else {
            Log.d("EpubViewer", "没有找到保存的进度")
            logDebug?.invoke("[EPUB] 没有找到保存的进度")
        }
        isRestoringProgress = false
        Log.d("EpubViewer", "恢复阶段结束，isRestoringProgress=false")
    }

    // 保存阅读进度（章节变化或滚动位置变化时）
    LaunchedEffect(currentChapterIndex, currentScrollRatio) {
        // 恢复进度期间不保存
        if (isRestoringProgress) {
            Log.d("EpubViewer", "跳过保存（正在恢复进度）")
            return@LaunchedEffect
        }
        // 延迟保存，避免频繁写入
        kotlinx.coroutines.delay(500)
        val progress = EpubReadingProgress(
            epubUri = epubUri.toString(),
            epubFileName = zipFileName,
            chapterIndex = currentChapterIndex,
            chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: "",
            scrollPosition = 0,
            scrollRatio = currentScrollRatio,
            lastReadTime = System.currentTimeMillis()
        )
        Log.d("EpubViewer", "准备保存进度: URI=${epubUri.toString()}, 章节$currentChapterIndex, 比例$currentScrollRatio")
        bookmarkManager.saveProgress(progress)
        logDebug?.invoke("[EPUB] 保存进度: 章节$currentChapterIndex, 比例${"%.2f".format(currentScrollRatio)}")
    }

    val currentChapter = chapters.getOrNull(currentChapterIndex)
    val filteredTocEntries = remember(chapters, tocQuery) {
        val query = tocQuery.trim()
        chapters.mapIndexedNotNull { index, chapter ->
            val title = chapter.title ?: chapter.href.substringBeforeLast(".")
            val matches = query.isBlank() ||
                title.contains(query, ignoreCase = true) ||
                chapter.href.contains(query, ignoreCase = true) ||
                (index + 1).toString().contains(query) ||
                "第${index + 1}章".contains(query, ignoreCase = true)
            if (matches) index to chapter else null
        }
    }
    val chapterFile = if (currentChapter != null) {
        getEpubChapterFile(extractResult, currentChapter)
    } else null
    val currentChapterBookmarks = remember(visualBookmarks, currentChapterIndex) {
        visualBookmarks.filter { it.chapterIndex == currentChapterIndex }
    }
    val currentChapterRestoredMarker = remember(restoredProgressMarker, currentChapterIndex) {
        restoredProgressMarker?.takeIf { it.chapterIndex == currentChapterIndex }
    }
    val latestScalePercent by rememberUpdatedState(scalePercent)
    val latestChapterIndex by rememberUpdatedState(currentChapterIndex)
    val latestScrollRatio by rememberUpdatedState(currentScrollRatio)
    val latestIsRestoringProgress by rememberUpdatedState(isRestoringProgress)

    DisposableEffect(bookmarkManager, epubUri, zipFileName, chapters) {
        onDispose {
            if (!latestIsRestoringProgress) {
                val progress = EpubReadingProgress(
                    epubUri = epubUri.toString(),
                    epubFileName = zipFileName,
                    chapterIndex = latestChapterIndex,
                    chapterTitle = chapters.getOrNull(latestChapterIndex)?.title ?: "",
                    scrollPosition = 0,
                    scrollRatio = latestScrollRatio,
                    lastReadTime = System.currentTimeMillis()
                )
                bookmarkManager.saveProgress(progress)
                logDebug?.invoke("[EPUB] 退出时保存进度: 章节${latestChapterIndex}, 比例${"%.2f".format(latestScrollRatio)}")
            }
        }
    }

    DisposableEffect(ttsSession) {
        onDispose {
            ttsRequestJob?.cancel()
            setEpubTtsHighlight(webViewRef.value, null, scrollToSegment = false)
            ttsSession.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        requestRefreshTtsEngines()
    }

    LaunchedEffect(showTtsEngineDialog) {
        if (showTtsEngineDialog) {
            requestRefreshTtsEngines()
        }
    }

    LaunchedEffect(chapterFile?.absolutePath) {
        currentChapterTtsSegments = emptyList()
        ttsSegmentsLoading = chapterFile != null
        ttsActiveSegmentId = null
        setEpubTtsHighlight(webViewRef.value, null, scrollToSegment = false)
    }

    LaunchedEffect(currentChapterIndex) {
        regexFindUiState = RegexFindUiState()
        ttsActiveSegmentId = null
        setEpubTtsHighlight(webViewRef.value, null, scrollToSegment = false)
        if ((ttsIsSpeaking || pendingTtsStartChapterIndex != null || ttsIsStarting) && currentChapterIndex != (ttsSpeakingChapterIndex ?: pendingTtsStartChapterIndex ?: currentChapterIndex)) {
            ttsRequestJob?.cancel()
            ttsRequestJob = null
            ttsSession.stop()
            ttsIsStarting = false
            ttsIsSpeaking = false
            ttsSpeakingChapterIndex = null
            pendingTtsStartChapterIndex = null
            ttsPendingRefreshAttempted = false
            autoAdvanceTtsChapterIndex = null
            publishTtsStatus(context.getString(R.string.epub_tts_chapter_switched))
        }
    }

    LaunchedEffect(autoAdvanceTtsChapterIndex) {
        val finishedChapterIndex = autoAdvanceTtsChapterIndex ?: return@LaunchedEffect
        autoAdvanceTtsChapterIndex = null
        val nextIndex = finishedChapterIndex + 1
        if (nextIndex in chapters.indices) {
            pendingTtsStartChapterIndex = nextIndex
            ttsPendingRefreshAttempted = false
            switchToChapter(nextIndex)
            showToc = false
        } else {
            publishTtsStatus(context.getString(R.string.epub_tts_finished))
        }
    }

    LaunchedEffect(
        pendingTtsStartChapterIndex,
        currentChapterTtsSegments,
        ttsSegmentsLoading,
        selectedTtsEnginePackage,
        selectedTtsVoiceName,
        epubTtsSpeedPercent,
        availableTtsEngines,
        ttsEngineLoading
    ) {
        val targetChapterIndex = pendingTtsStartChapterIndex ?: return@LaunchedEffect
        if (targetChapterIndex != currentChapterIndex) return@LaunchedEffect
        if (ttsSegmentsLoading) {
            publishTtsStatus(context.getString(R.string.epub_tts_preparing))
            return@LaunchedEffect
        }
        if (currentChapterTtsSegments.isEmpty()) {
            pendingTtsStartChapterIndex = null
            ttsPendingRefreshAttempted = false
            publishTtsStatus(context.getString(R.string.epub_tts_no_text))
            return@LaunchedEffect
        }
        if (ttsEngineLoading) {
            publishTtsStatus(context.getString(R.string.epub_tts_engine_loading))
            return@LaunchedEffect
        }
        if (availableTtsEngines.isEmpty()) {
            if (!ttsPendingRefreshAttempted) {
                ttsPendingRefreshAttempted = true
                requestRefreshTtsEngines()
                publishTtsStatus(context.getString(R.string.epub_tts_engine_loading))
                return@LaunchedEffect
            }
            pendingTtsStartChapterIndex = null
            ttsPendingRefreshAttempted = false
            publishTtsStatus(context.getString(R.string.epub_tts_engine_empty))
            return@LaunchedEffect
        }
        val chosenEnginePackage = selectedTtsEnginePackage
            ?.takeIf { selected -> availableTtsEngines.any { it.packageName == selected } }
            ?: availableTtsEngines.firstOrNull()?.packageName
        if (chosenEnginePackage == null) {
            pendingTtsStartChapterIndex = null
            ttsPendingRefreshAttempted = false
            publishTtsStatus(context.getString(R.string.epub_tts_engine_empty))
            return@LaunchedEffect
        }
        pendingTtsStartChapterIndex = null
        ttsPendingRefreshAttempted = false
        val chapterTitle = currentChapter?.title ?: "第${targetChapterIndex + 1}章"
        val utteranceId = "epub_${epubUri.hashCode()}_${targetChapterIndex}_${System.currentTimeMillis()}"
        val speakLocale = effectiveTtsVoice
            ?.localeTag
            ?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
            ?.let(Locale::forLanguageTag)
            ?: preferredTtsLocale
        val startSegmentIndex = resolveTtsStartSegmentIndex(currentChapterTtsSegments, currentScrollRatio)
        val speakChunks = currentChapterTtsSegments
            .drop(startSegmentIndex)
            .flatMap { segment ->
                splitTextForTts(segment.text).map { chunkText ->
                    EpubTtsChunk(
                        domId = segment.domId,
                        topRatio = segment.topRatio,
                        text = chunkText
                    )
                }
            }
        if (speakChunks.isEmpty()) {
            pendingTtsStartChapterIndex = null
            ttsPendingRefreshAttempted = false
            publishTtsStatus(context.getString(R.string.epub_tts_no_text))
            return@LaunchedEffect
        }
        ttsRequestJob?.cancel()
        ttsRequestJob = scope.launch {
            ttsSession.speak(
                enginePackage = chosenEnginePackage,
                voiceName = effectiveTtsVoice?.name,
                preferredLocale = speakLocale,
                speechRate = epubTtsSpeedPercent / 100f,
                utteranceId = utteranceId,
                chunks = speakChunks,
                onPreparing = {
                    ttsIsStarting = true
                    ttsIsSpeaking = false
                    ttsSpeakingChapterIndex = null
                    publishTtsStatus(context.getString(R.string.epub_tts_engine_loading), toast = false)
                },
                onStart = {
                    ttsIsStarting = false
                    ttsIsSpeaking = true
                    ttsSpeakingChapterIndex = targetChapterIndex
                    publishTtsStatus(context.getString(R.string.epub_tts_running, chapterTitle))
                },
                onChunkStart = { chunk, _, _ ->
                    ttsActiveSegmentId = chunk.domId
                    setEpubTtsHighlight(webViewRef.value, chunk.domId, scrollToSegment = true)
                },
                onFinish = { completed, message ->
                    ttsRequestJob = null
                    ttsIsStarting = false
                    ttsIsSpeaking = false
                    ttsSpeakingChapterIndex = null
                    ttsActiveSegmentId = null
                    setEpubTtsHighlight(webViewRef.value, null, scrollToSegment = false)
                    if (!message.isNullOrBlank()) {
                        publishTtsStatus(message)
                    }
                    if (completed) {
                        if (epubTtsAutoNextChapter && targetChapterIndex < chapters.lastIndex) {
                            autoAdvanceTtsChapterIndex = targetChapterIndex
                        } else {
                            publishTtsStatus(context.getString(R.string.epub_tts_finished))
                        }
                    }
                },
                onError = { error ->
                    ttsRequestJob = null
                    ttsIsStarting = false
                    ttsIsSpeaking = false
                    ttsSpeakingChapterIndex = null
                    ttsActiveSegmentId = null
                    setEpubTtsHighlight(webViewRef.value, null, scrollToSegment = false)
                    publishTtsStatus(error)
                }
            )
        }
    }

    // 跳转到指定章节
    fun goToChapter(index: Int, scrollRatio: Float = 0f) {
        if (index in chapters.indices) {
            switchToChapter(index, scrollRatio)
            showToc = false
        }
    }

    fun goToPreviousChapter() {
        if (currentChapterIndex > 0) {
            switchToChapter(currentChapterIndex - 1)
        }
    }

    fun goToPreviousChapterLastPage() {
        if (currentChapterIndex > 0) {
            switchToChapter(currentChapterIndex - 1, 1f)
        }
    }

    fun goToNextChapter() {
        if (currentChapterIndex < chapters.size - 1) {
            switchToChapter(currentChapterIndex + 1)
        }
    }

    fun goToNextChapterFirstPage() {
        if (currentChapterIndex < chapters.size - 1) {
            switchToChapter(currentChapterIndex + 1, 0f)
        }
    }


    // 添加收藏：现在直接写入全局读书笔记中的带定位记录
    fun requestAddBookmark() {
        if (bookNoteLoadedData == null && !bookNoteInProgress) {
            onRequestOpenBookNotes()
        }
        requestCurrentBookmarkQuote { quote ->
            pendingBookmarkQuote = quote
            showAddBookmark = true
        }
    }

    BackHandler {
        when {
            showToc -> showToc = false
            showBookmarks -> showBookmarks = false
            showAddBookmark -> showAddBookmark = false
            editingBookNote != null -> editingBookNote = null
            else -> onBack()
        }
    }

    // 外链对话框
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

    if (showFindDialog) {
        WebViewRegexFindDialog(
            query = regexQuery,
            result = regexFindUiState,
            onQueryChange = {
                regexQuery = it
                if (it.isBlank()) regexFindUiState = RegexFindUiState()
            },
            onSearch = {
                if (regexQuery.isBlank()) {
                    clearRegexFind(webViewRef.value) { regexFindUiState = it }
                } else {
                    searchInWebViewByRegex(webViewRef.value, regexQuery) { regexFindUiState = it }
                }
            },
            onPrevious = { moveRegexFindMatch(webViewRef.value, forward = false) { regexFindUiState = it } },
            onNext = { moveRegexFindMatch(webViewRef.value, forward = true) { regexFindUiState = it } },
            onClear = { clearRegexFind(webViewRef.value) { regexFindUiState = it } },
            onDismiss = { showFindDialog = false }
        )
    }

    if (showFullTextSearchDialog) {
        EpubFullTextSearchDialog(
            query = fullTextQuery,
            searching = fullTextSearching,
            error = fullTextSearchError,
            results = fullTextSearchResults,
            onQueryChange = {
                fullTextQuery = it
                if (it.isBlank()) {
                    fullTextSearchResults = emptyList()
                    fullTextSearchError = null
                }
            },
            onSearch = { runFullTextSearch() },
            onClear = {
                fullTextQuery = ""
                fullTextSearchResults = emptyList()
                fullTextSearchError = null
            },
            onSelectResult = { result ->
                val query = fullTextQuery.trim()
                showFullTextSearchDialog = false
                if (query.isBlank()) return@EpubFullTextSearchDialog
                showFindDialog = true
                if (result.chapterIndex == currentChapterIndex) {
                    pendingFullTextHighlightQuery = null
                    applyLiteralFindToCurrentChapter(query)
                } else {
                    pendingFullTextHighlightQuery = query
                    goToChapter(result.chapterIndex)
                }
            },
            onDismiss = { showFullTextSearchDialog = false }
        )
    }

    OfflineTtsEngineVoiceDialogs(
        showEngineDialog = showTtsEngineDialog,
        onDismissEngineDialog = { showTtsEngineDialog = false },
        showVoiceDialog = showTtsVoiceDialog,
        onDismissVoiceDialog = { showTtsVoiceDialog = false },
        ttsEngineLoading = ttsEngineLoading,
        availableTtsEngines = availableTtsEngines,
        selectedTtsEnginePackage = selectedTtsEnginePackage,
        selectedTtsVoiceName = selectedTtsVoiceName,
        effectiveTtsEngine = effectiveTtsEngine,
        onRefreshEngines = { requestRefreshTtsEngines() },
        onSelectEngine = { engine ->
            scope.launch {
                runCatching {
                    prefs.setEpubTtsEnginePackage(engine.packageName)
                    prefs.setEpubTtsVoiceName(null)
                }.onSuccess {
                    ttsSession.shutdown()
                    publishTtsStatus(context.getString(R.string.epub_tts_selected_engine, engine.label))
                    showTtsEngineDialog = false
                }.onFailure { error ->
                    publishTtsStatus(
                        context.getString(
                            R.string.epub_tts_engine_save_failed,
                            error.message ?: error.javaClass.simpleName
                        )
                    )
                }
            }
        },
        onSelectDefaultVoice = {
            scope.launch {
                runCatching {
                    prefs.setEpubTtsVoiceName(null)
                }.onSuccess {
                    ttsSession.shutdown()
                    publishTtsStatus(context.getString(R.string.epub_tts_selected_voice, context.getString(R.string.epub_tts_voice_default_choice)))
                    showTtsVoiceDialog = false
                }.onFailure { error ->
                    publishTtsStatus(
                        context.getString(
                            R.string.epub_tts_voice_save_failed,
                            error.message ?: error.javaClass.simpleName
                        )
                    )
                }
            }
        },
        onSelectVoice = { voice ->
            scope.launch {
                runCatching {
                    prefs.setEpubTtsVoiceName(voice.name)
                }.onSuccess {
                    ttsSession.shutdown()
                    publishTtsStatus(context.getString(R.string.epub_tts_selected_voice, voice.label))
                    showTtsVoiceDialog = false
                }.onFailure { error ->
                    publishTtsStatus(
                        context.getString(
                            R.string.epub_tts_voice_save_failed,
                            error.message ?: error.javaClass.simpleName
                        )
                    )
                }
            }
        }
    )

    // 目录对话框
    if (showToc) {
        val tocListState = rememberLazyListState()
        LaunchedEffect(showToc, tocQuery, currentChapterIndex, filteredTocEntries.size) {
            if (!showToc || filteredTocEntries.isEmpty()) return@LaunchedEffect
            val targetIndex = filteredTocEntries.indexOfFirst { it.first == currentChapterIndex }
                .takeIf { it >= 0 }
                ?: 0
            tocListState.scrollToItem((targetIndex - 3).coerceAtLeast(0))
        }
        AlertDialog(
            onDismissRequest = { showToc = false },
            title = { Text("目录 - ${extractResult.bookInfo.title}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                ) {
                    OutlinedTextField(
                        value = tocQuery,
                        onValueChange = { tocQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("筛选章节") },
                        placeholder = { Text("输入章节号、标题或文件名") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (tocQuery.isBlank()) {
                            "当前第 ${currentChapterIndex + 1} 章，共 ${chapters.size} 章"
                        } else {
                            "匹配到 ${filteredTocEntries.size} / ${chapters.size} 章"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (filteredTocEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "没有匹配的章节",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            state = tocListState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filteredTocEntries.size) { listIndex ->
                                val (index, chapter) = filteredTocEntries[listIndex]
                                val title = chapter.title ?: chapter.href.substringBeforeLast(".")
                                TextButton(
                                    onClick = { goToChapter(index) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (index == currentChapterIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "第 ${index + 1} 章${if (index == currentChapterIndex) " · 当前位置" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (index == currentChapterIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showToc = false }) { Text("关闭") }
            }
        )
    }

    // 收藏夹对话框
    if (showBookmarks) {
        var bookmarkFilterQuery by remember(showBookmarks, currentBookTitle, currentBookBookmarkEntries.size) { mutableStateOf("") }
        val filteredBookmarkEntries = remember(currentBookBookmarkEntries, bookmarkFilterQuery) {
            val query = bookmarkFilterQuery.trim()
            if (query.isBlank()) {
                currentBookBookmarkEntries
            } else {
                currentBookBookmarkEntries.filter { entry ->
                    listOf(
                        entry.chapterInfo.orEmpty(),
                        entry.chapterTitle.orEmpty(),
                        entry.quote.orEmpty(),
                        entry.content
                    ).any { it.contains(query, ignoreCase = true) }
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("收藏夹 - $zipFileName") },
            text = {
                if (bookNoteLoadedData == null && bookNoteInProgress) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                } else if (currentBookBookmarkEntries.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(32.dp))
                        Icon(
                            Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "暂无收藏",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "点击下方“新增当前位置收藏”可创建带定位的读书笔记",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = bookmarkFilterQuery,
                            onValueChange = { bookmarkFilterQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("过滤当前书收藏") },
                            placeholder = { Text("输入章节、引文或感想") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (bookmarkFilterQuery.isBlank()) {
                                "共 ${currentBookBookmarkEntries.size} 条定位笔记"
                            } else {
                                "匹配到 ${filteredBookmarkEntries.size} / ${currentBookBookmarkEntries.size} 条"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        if (filteredBookmarkEntries.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "没有匹配的收藏",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(filteredBookmarkEntries.size) { index ->
                                    val bookmark = filteredBookmarkEntries[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                onClick = { goToBookNote(bookmark) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = bookmark.chapterIndex?.let { "第${it + 1}章" } ?: "整本书",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = bookmark.formattedTime,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = bookmark.chapterTitle ?: bookmark.chapterInfo ?: "未命名章节",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = bookmark.scrollRatio?.let { formatEpubBookmarkPosition(it) } ?: "未绑定具体位置",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    bookmark.quote?.takeIf { it.isNotBlank() }?.let { quote ->
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "引文：$quote",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (bookmark.content.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = bookmark.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = {
                                            editingBookNote = BookNoteEditorState(
                                                editingId = bookmark.id,
                                                bookTitle = bookmark.bookTitle,
                                                chapterInfo = bookmark.chapterInfo.orEmpty(),
                                                quote = bookmark.quote.orEmpty(),
                                                content = bookmark.content,
                                                chapterIndex = bookmark.chapterIndex,
                                                chapterTitle = bookmark.chapterTitle,
                                                scrollRatio = bookmark.scrollRatio,
                                                createdAt = bookmark.createdAt
                                            )
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("编辑")
                                        }
                                        TextButton(onClick = {
                                            deleteBookNoteConfirm = bookmark
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("删除")
                                        }
                                    }
                                }
                            }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (bookNoteLoadedData == null && !bookNoteInProgress) {
                        onRequestOpenBookNotes()
                    }
                    requestAddBookmark()
                }) { Text("新增当前位置收藏") }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarks = false }) { Text("关闭") }
            }
        )
    }

    // 添加收藏对话框
    if (showAddBookmark) {
        var noteText by remember(showAddBookmark, currentChapterIndex, currentScrollRatio) { mutableStateOf("") }
        var quoteText by remember(showAddBookmark, currentChapterIndex, currentScrollRatio) { mutableStateOf(pendingBookmarkQuote) }
        AlertDialog(
            onDismissRequest = { showAddBookmark = false },
            title = { Text("添加收藏") },
            text = {
                Column {
                    Text(
                        "当前位置：${currentChapterLocationLabel()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = quoteText,
                        onValueChange = { quoteText = it },
                        label = { Text("引文（优先使用当前选中内容，否则抓取当前段落，长文本会截断）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("感想（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val existing = currentBookBookmarkEntries.firstOrNull {
                        it.matchesLocation(currentChapterIndex, currentScrollRatio)
                    }
                    if (existing != null) {
                        editingBookNote = BookNoteEditorState(
                            editingId = existing.id,
                            bookTitle = existing.bookTitle,
                            chapterInfo = currentChapterLocationLabel(),
                            quote = if (quoteText.isNotBlank()) quoteText else existing.quote.orEmpty(),
                            content = if (noteText.isNotBlank()) noteText else existing.content,
                            chapterIndex = currentChapterIndex,
                            chapterTitle = currentChapter?.title,
                            scrollRatio = currentScrollRatio,
                            createdAt = existing.createdAt
                        )
                        Toast.makeText(context, "该位置已收藏，已转为编辑", Toast.LENGTH_SHORT).show()
                    } else {
                        upsertBookNoteEntry(
                            buildCurrentPositionBookNote(
                                id = (bookNoteEntries.maxOfOrNull { it.id } ?: 0L) + 1L,
                                content = noteText,
                                quote = quoteText
                            )
                        )
                        Toast.makeText(context, "已添加收藏", Toast.LENGTH_SHORT).show()
                    }
                    showAddBookmark = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmark = false }) { Text("取消") }
            }
        )
    }

    editingBookNote?.let { state ->
        BookNoteEditorDialog(
            state = state,
            inProgress = bookNoteInProgress,
            onDismiss = { if (!bookNoteInProgress) editingBookNote = null },
            onReassociateCurrentPosition = {
                editingBookNote = state.copy(
                    chapterInfo = currentChapterLocationLabel(),
                    quote = state.quote.ifBlank { pendingBookmarkQuote },
                    chapterIndex = currentChapterIndex,
                    chapterTitle = currentChapter?.title,
                    scrollRatio = currentScrollRatio
                )
            },
            onClearLocation = {
                editingBookNote = state.copy(
                    chapterInfo = "",
                    chapterIndex = null,
                    chapterTitle = null,
                    scrollRatio = null
                )
            },
            onConfirm = { updated ->
                val normalizedTitle = updated.bookTitle.trim()
                val normalizedContent = updated.content.trimEnd()
                val normalizedQuote = updated.quote.trim().takeIf { it.isNotEmpty() }
                val normalizedChapterInfo = updated.chapterInfo.trim().takeIf { it.isNotEmpty() }
                if (normalizedTitle.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.book_note_book_title_required), Toast.LENGTH_SHORT).show()
                    return@BookNoteEditorDialog
                }
                if (normalizedContent.isBlank() && normalizedChapterInfo == null && normalizedQuote == null) {
                    Toast.makeText(context, context.getString(R.string.book_note_content_or_position_required), Toast.LENGTH_SHORT).show()
                    return@BookNoteEditorDialog
                }
                val updatedEntry = BookNoteEntry(
                    id = updated.editingId ?: ((bookNoteEntries.maxOfOrNull { it.id } ?: 0L) + 1L),
                    bookTitle = normalizedTitle,
                    chapterInfo = normalizedChapterInfo,
                    quote = normalizedQuote,
                    content = normalizedContent,
                    chapterIndex = updated.chapterIndex,
                    chapterTitle = updated.chapterTitle,
                    scrollRatio = updated.scrollRatio,
                    createdAt = updated.createdAt
                )
                upsertBookNoteEntry(updatedEntry)
                Toast.makeText(context, "已更新", Toast.LENGTH_SHORT).show()
                editingBookNote = null
            }
        )
    }

    deleteBookNoteConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteBookNoteConfirm = null },
            title = { Text("删除读书笔记") },
            text = { Text("确定删除这条读书笔记吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBookNoteEntriesChanged(bookNoteEntries.filterNot { it.id == entry.id })
                        deleteBookNoteConfirm = null
                    },
                    enabled = !bookNoteInProgress
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteBookNoteConfirm = null }, enabled = !bookNoteInProgress) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(zipFileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (currentChapter != null) {
                            Text(
                                currentChapter.title ?: currentChapter.href.substringBeforeLast("."),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showToc) {
                            showToc = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 目录按钮
                    IconButton(onClick = { showToc = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "目录")
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            BadgedBox(
                                badge = {
                                    if (currentBookBookmarkEntries.isNotEmpty()) {
                                        Badge { Text("${currentBookBookmarkEntries.size}", style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.epub_more_actions))
                            }
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (ttsIsSpeaking) context.getString(R.string.epub_tts_stop)
                                        else context.getString(R.string.epub_tts_play)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    if (ttsIsSpeaking) {
                                        ttsRequestJob?.cancel()
                                        ttsRequestJob = null
                                        ttsActiveSegmentId = null
                                        setEpubTtsHighlight(webViewRef.value, null, scrollToSegment = false)
                                        ttsSession.stop()
                                        ttsIsStarting = false
                                        ttsIsSpeaking = false
                                        ttsSpeakingChapterIndex = null
                                        pendingTtsStartChapterIndex = null
                                        ttsPendingRefreshAttempted = false
                                        autoAdvanceTtsChapterIndex = null
                                        publishTtsStatus(context.getString(R.string.epub_tts_stopped))
                                    } else {
                                        ttsPendingRefreshAttempted = false
                                        pendingTtsStartChapterIndex = currentChapterIndex
                                        publishTtsStatus(context.getString(R.string.epub_tts_start_requested, currentChapter?.title ?: "第${currentChapterIndex + 1}章"))
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        if (ttsIsSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_tts_voice)) },
                                onClick = {
                                    showMoreMenu = false
                                    showTtsVoiceDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_action_find)) },
                                onClick = {
                                    showMoreMenu = false
                                    showFindDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("全文查找") },
                                onClick = {
                                    showMoreMenu = false
                                    if (fullTextQuery.isBlank() && regexQuery.isNotBlank()) {
                                        fullTextQuery = regexQuery
                                    }
                                    showFullTextSearchDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_action_add_bookmark)) },
                                onClick = {
                                    showMoreMenu = false
                                    requestAddBookmark()
                                },
                                leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_action_view_bookmarks)) },
                                onClick = {
                                    showMoreMenu = false
                                    if (bookNoteLoadedData == null && !bookNoteInProgress) {
                                        onRequestOpenBookNotes()
                                    }
                                    showBookmarks = true
                                },
                                leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_action_zoom_in)) },
                                onClick = {
                                    showMoreMenu = false
                                    scalePercent = minOf(200, scalePercent + 25)
                                    val appliedScalePercent = scalePercent
                                    webViewRef.value?.evaluateJavascript("document.body.style.zoom = ${appliedScalePercent / 100.0}", null)
                                    scope.launch(Dispatchers.IO) {
                                        prefs.setEpubZoomPercentForUri(epubUri.toString(), appliedScalePercent)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.epub_action_zoom_out)) },
                                onClick = {
                                    showMoreMenu = false
                                    scalePercent = maxOf(50, scalePercent - 25)
                                    val appliedScalePercent = scalePercent
                                    webViewRef.value?.evaluateJavascript("document.body.style.zoom = ${appliedScalePercent / 100.0}", null)
                                    scope.launch(Dispatchers.IO) {
                                        prefs.setEpubZoomPercentForUri(epubUri.toString(), appliedScalePercent)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.ZoomOut, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            val hasDictLoaded = dictLoaded != null
            val hasDictResult = dictLookupResult != null
            val hasMultipleChapters = chapters.size > 1
            // 只有在有词典或多章节时才显示底部栏
            if (hasDictLoaded || hasMultipleChapters) {
                // 展开时使用更大的高度，收起时使用较小高度
                val barHeight = when {
                    !dictAreaExpanded -> 56.dp
                    else -> 200.dp
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight),
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 左侧：上一章按钮（窄一些）- 只在多章节时显示
                        if (hasMultipleChapters) {
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .fillMaxHeight()
                                    .clickable(enabled = currentChapterIndex > 0) {
                                        goToPreviousChapter()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "上一章",
                                        tint = if (currentChapterIndex > 0)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                    if (dictAreaExpanded) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "上一章",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentChapterIndex > 0)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                }
                            }
                        }

                        // 中间：章节信息 + 词典结果（宽一些）
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            // 顶部：章节信息 + 展开/收起按钮
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${currentChapterIndex + 1} / ${chapters.size} · ${formatEpubChapterProgressCompact(currentScrollRatio)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    ttsStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (ttsIsSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                // 展开/收起按钮（有词典时显示）
                                if (hasDictLoaded) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                prefs.setEpubDictAreaExpanded(!dictAreaExpanded)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (dictAreaExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = if (dictAreaExpanded) "收起" else "展开",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // 词典/调试信息区域（可滚动，可选择文本）- 展开时显示
                            if (dictAreaExpanded) {
                                dictLookupResult?.let { result ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                            .verticalScroll(rememberScrollState()),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        // 单词标题行：左边单词，右边回退按钮
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                result.word.ifBlank { "取词结果" },
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (result.error != null)
                                                    MaterialTheme.colorScheme.error
                                                else
                                                    MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            // 回退按钮（只在有历史记录时显示）
                                            if (dictHistory.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { goBackInDictHistory() },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "回退到上一个单词",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        // 内容（可选择）
                                        SelectionContainer {
                                            when {
                                                result.error != null -> {
                                                    Text(
                                                        result.error,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                result.definition != null -> {
                                                    Text(
                                                        result.definition,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } ?: run {
                                    // 展开但没有查询结果时，显示提示
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "选择文字并复制到剪贴板查询",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // 右侧：下一章按钮（窄一些）- 只在多章节时显示
                        if (hasMultipleChapters) {
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .fillMaxHeight()
                                    .clickable(enabled = currentChapterIndex < chapters.size - 1) {
                                        goToNextChapter()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "下一章",
                                        tint = if (currentChapterIndex < chapters.size - 1)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                    if (dictAreaExpanded) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "下一章",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentChapterIndex < chapters.size - 1)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                chapterFile == null || !chapterFile.exists() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无法加载章节内容", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    // 章节内图片/链接相对路径是相对于当前章节文件所在目录，必须用章节目录作为 baseUrl
                    val chapterBaseDir = chapterFile.parentFile ?: opfDir
                    val baseUrl = "file://${chapterBaseDir.absolutePath}/"
                    val bg = MaterialTheme.colorScheme.surface
                    val fg = MaterialTheme.colorScheme.onSurface
                    val bgHex = "#%02x%02x%02x".format(
                        (bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt()
                    )
                    val fgHex = "#%02x%02x%02x".format(
                        (fg.red * 255).toInt(), (fg.green * 255).toInt(), (fg.blue * 255).toInt()
                    )

                    // 检测源文件类型，注入对应样式（支持混合章节：按章节标记优先）
                    val cacheIsLlmContent = File(extractResult.cacheDir, ".llm_source").exists()
                    val cacheIsTxtContent = File(extractResult.cacheDir, ".txt_source").exists()

                    val chapterFileForUpdate = chapterFile
                    val onScrollRatioChanged: (Float) -> Unit = { ratio ->
                        currentScrollRatio = ratio
                        chapterScrollRatios[currentChapterIndex] = ratio.coerceIn(0f, 1f)
                    }
                    AndroidView(
                        factory = { ctx ->
                            GestureWebView(
                                ctx,
                                onScrollRatioChange = onScrollRatioChanged,
                                onEdgeNavigatePreviousChapter = { goToPreviousChapterLastPage() },
                                onEdgeNavigateNextChapter = { goToNextChapterFirstPage() }
                            ).apply {
                                setBackgroundColor(Color.TRANSPARENT)
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = false
                                settings.allowFileAccess = true
                                webViewClient = EpubWebViewClient(opfDir, { url ->
                                    pendingExternalUrl = url
                                }) { view ->
                                    regexFindUiState = RegexFindUiState()
                                    installRegexFindBridge(view)
                                    pendingFullTextHighlightQuery?.let { pendingQuery ->
                                        applyLiteralFindToCurrentChapter(pendingQuery, view)
                                        pendingFullTextHighlightQuery = null
                                    }
                                    installEpubTtsBridge(view)
                                    // 页面加载完成后恢复滚动位置
                                    (view as? GestureWebView)?.restoreScrollPosition()
                                    // 页面完成后再应用最新缩放，避免旧闭包值覆盖。
                                    view.evaluateJavascript("document.body.style.zoom = ${latestScalePercent / 100.0}", null)
                                    scope.launch {
                                        ttsSegmentsLoading = true
                                        currentChapterTtsSegments = collectEpubTtsSegments(view)
                                        ttsSegmentsLoading = false
                                        if (ttsActiveSegmentId != null) {
                                            setEpubTtsHighlight(view, ttsActiveSegmentId, scrollToSegment = false)
                                        }
                                    }
                                }
                                webViewRef.value = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webView ->
                            webViewRef.value = webView
                            try {
                                val htmlContent = chapterFileForUpdate.readText()
                                val chapterIsLlm = htmlContent.contains("<!--LM_CHAPTER_TYPE:LLM-->") || cacheIsLlmContent
                                val chapterIsTxt = htmlContent.contains("<!--LM_CHAPTER_TYPE:TXT-->") || cacheIsTxtContent
                                // 根据源文件类型注入不同样式
                                val extraStyles = when {
                                    chapterIsLlm -> """
                                        h1 { font-size: 1.4em; margin: 0 0 0.5em 0; border-bottom: 1px solid #ddd; padding-bottom: 0.3em; }
                                        details, div { margin: 0; padding: 0; }
                                        .assistant-block { margin: 0.5em 0; padding: 0.5em; background: ${bgHex}ee; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                                        .assistant-label { color: #1976d2; font-weight: bold; font-size: 0.85em; margin: 0 0 0.3em 0; }
                                        .assistant-content { color: $fgHex; }
                                        .other-block { margin: 0.3em 0; }
                                        .other-label { color: #999; font-size: 0.8em; cursor: pointer; padding: 0; margin: 0; }
                                        .other-label:hover { color: #666; }
                                        .other-content { color: #888; font-size: 0.9em; padding: 0.5em; background: #f5f5f5; border-radius: 4px; margin-top: 0.3em; }
                                        details[open] .other-label { color: #666; }
                                        .content { white-space: pre-wrap; word-wrap: break-word; margin: 0; }
                                        .role-label { display: block; margin: 0; }
                                    """
                                    chapterIsTxt -> """
                                        p { margin: 0.5em 0; text-indent: 2em; line-height: 1.8; }
                                    """
                                    else -> "" // EPUB使用原始样式
                                }
                                val styledHtml = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta charset="UTF-8">
                                        <meta name="viewport" content="width=device-width, initial-scale=1">
                                        <style>
                                            body {
                                                background: $bgHex;
                                                color: $fgHex;
                                                font-size: 16px;
                                                padding: 16px;
                                                line-height: 1.6;
                                                font-family: sans-serif;
                                            }
                                            img { max-width: 100%; height: auto; }
                                            a { color: #2196F3; }
                                            .lm-epub-bookmark-marker {
                                                position: absolute;
                                                right: 6px;
                                                min-width: 22px;
                                                height: 22px;
                                                padding: 0 6px;
                                                border-radius: 999px;
                                                background: rgba(255, 179, 0, 0.92);
                                                color: #1f1f1f;
                                                font-size: 11px;
                                                line-height: 22px;
                                                text-align: center;
                                                box-shadow: 0 2px 8px rgba(0,0,0,0.18);
                                                transform: translateY(-50%);
                                                z-index: 9999;
                                                opacity: 0.72;
                                                pointer-events: none;
                                            }
                                            .lm-epub-bookmark-marker.active {
                                                opacity: 1;
                                                background: rgba(255, 111, 0, 0.96);
                                                color: #ffffff;
                                                box-shadow: 0 4px 12px rgba(255, 111, 0, 0.35);
                                            }
                                            .lm-epub-last-read-marker {
                                                background: rgba(33, 150, 243, 0.95);
                                                color: #ffffff;
                                                opacity: 0.92;
                                                box-shadow: 0 4px 12px rgba(33, 150, 243, 0.28);
                                            }
                                            .lm-epub-last-read-marker.active {
                                                background: rgba(13, 110, 253, 0.98);
                                                color: #ffffff;
                                                box-shadow: 0 6px 16px rgba(13, 110, 253, 0.34);
                                            }
                                            [data-lm-tts-id] {
                                                transition: background-color 0.22s ease, box-shadow 0.22s ease;
                                                scroll-margin-top: 72px;
                                            }
                                            .lm-epub-tts-active {
                                                background: rgba(255, 235, 59, 0.28);
                                                box-shadow: 0 0 0 3px rgba(255, 235, 59, 0.18);
                                                border-radius: 8px;
                                            }
                                            $extraStyles
                                        </style>
                                    </head>
                                    <body>$htmlContent
                                    <script>
                                        ${buildEpubBookmarkMarkerScript(currentChapterBookmarks, currentChapterRestoredMarker)}
                                    </script>
                                    </body>
                                    </html>
                                """.trimIndent()
                                val loadKey = "$baseUrl|${chapterFileForUpdate.absolutePath}|${styledHtml.hashCode()}"
                                if (webView.tag != loadKey) {
                                    webView.tag = loadKey
                                    // 设置待恢复的滚动位置
                                    webView.setPendingScrollRatio(currentScrollRatio)
                                    webView.loadDataWithBaseURL(baseUrl, styledHtml, "text/html", "UTF-8", null)
                                } else {
                                    // 仅在显式触发（恢复进度/跳转目录/跳转收藏）时才程序化滚动，
                                    // 避免用户手势滚动时被重复拉回导致闪烁。
                                    val ratioToApply = pendingProgrammaticScrollRatio
                                    if (ratioToApply != null && ratioToApply > 0f) {
                                        webView.scrollToRatio(ratioToApply)
                                    }
                                    pendingProgrammaticScrollRatio = null
                                }
                                webView.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                            } catch (e: Exception) {
                                Log.e(EPUB_DEBUG, "加载章节失败", e)
                            }
                        }
                    )
                    if (!hideReaderFloatingNextButton) {
                        DraggableNextReadButton(
                            contentDescription = "下一页",
                            enabled = true,
                            onClick = { (webViewRef.value as? GestureWebView)?.scrollNextPage() },
                            initialXPercent = floatingButtonXPercent,
                            initialYPercent = floatingButtonYPercent,
                            onPositionChangePercent = { xPercent, yPercent ->
                                scope.launch {
                                    prefs.setReaderFloatingNextButtonPositionPercent(xPercent, yPercent)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** WebViewClient：拦截外链，加载本地资源。 */
private class EpubWebViewClient(
    private val opfDir: File,
    private val onExternalUrl: (String) -> Unit,
    private val onPageFinished: ((WebView) -> Unit)? = null
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
                // 只允许访问opfDir内的文件
                if (file.exists() && file.absolutePath.startsWith(opfDir.absolutePath)) {
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
            if (file.exists() && file.isFile && file.absolutePath.startsWith(opfDir.absolutePath)) {
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

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { onPageFinished?.invoke(it) }
    }
}

private fun <T> List<T>.getOrNull(index: Int): T? = if (index in indices) this[index] else null

private fun buildEpubBookmarkMarkerScript(
    bookmarks: List<EpubBookmark>,
    restoredProgress: EpubReadingProgress? = null
): String {
    if (bookmarks.isEmpty() && restoredProgress == null) {
        return """
            (function() {
                document.querySelectorAll('.lm-epub-bookmark-marker').forEach(function(node) { node.remove(); });
                document.querySelectorAll('.lm-epub-last-read-marker').forEach(function(node) { node.remove(); });
            })();
        """.trimIndent()
    }
    val markersJson = bookmarks.joinToString(prefix = "[", postfix = "]") { bookmark ->
        val note = bookmark.note.ifBlank { bookmark.chapterTitle.ifBlank { "书签" } }
        val label = if (bookmark.note.isBlank()) "签" else bookmark.note.take(2)
        """{
            ratio:${bookmark.scrollRatio.coerceIn(0f, 1f)},
            note:${org.json.JSONObject.quote(note)},
            label:${org.json.JSONObject.quote(label)}
        }""".trimIndent()
    }
    val restoredMarkerJson = restoredProgress?.let {
        """{
            ratio:${it.scrollRatio.coerceIn(0f, 1f)},
            note:${org.json.JSONObject.quote("上次阅读到这里")},
            label:${org.json.JSONObject.quote("上次")}
        }""".trimIndent()
    } ?: "null"
    return """
        (function() {
            var markers = $markersJson;
            var restoredMarker = $restoredMarkerJson;
            function ensureMarkers() {
                document.querySelectorAll('.lm-epub-bookmark-marker').forEach(function(node) { node.remove(); });
                document.querySelectorAll('.lm-epub-last-read-marker').forEach(function(node) { node.remove(); });
                var body = document.body;
                if (!body) return;
                if (window.getComputedStyle(body).position === 'static') {
                    body.style.position = 'relative';
                }
                var docEl = document.documentElement;
                var viewH = window.innerHeight || docEl.clientHeight || 0;
                var docH = Math.max(docEl.scrollHeight, body.scrollHeight, docEl.offsetHeight, body.offsetHeight);
                var maxScroll = Math.max(1, docH - viewH);
                markers.forEach(function(marker, index) {
                    var node = document.createElement('div');
                    node.className = 'lm-epub-bookmark-marker';
                    node.textContent = marker.label || '签';
                    node.title = marker.note || ('书签 ' + (index + 1));
                    node.dataset.targetY = String(Math.round(marker.ratio * maxScroll));
                    node.style.top = (marker.ratio * maxScroll + 16) + 'px';
                    body.appendChild(node);
                });
                if (restoredMarker) {
                    var restoredNode = document.createElement('div');
                    restoredNode.className = 'lm-epub-bookmark-marker lm-epub-last-read-marker';
                    restoredNode.textContent = restoredMarker.label || '上次';
                    restoredNode.title = restoredMarker.note || '上次阅读到这里';
                    restoredNode.dataset.targetY = String(Math.round(restoredMarker.ratio * maxScroll));
                    restoredNode.style.top = (restoredMarker.ratio * maxScroll + 16) + 'px';
                    restoredNode.style.right = '38px';
                    body.appendChild(restoredNode);
                }
                updateActiveState();
            }
            function updateActiveState() {
                var currentY = window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
                document.querySelectorAll('.lm-epub-bookmark-marker, .lm-epub-last-read-marker').forEach(function(node) {
                    var targetY = Number(node.dataset.targetY || '0');
                    var active = Math.abs(targetY - currentY) <= 28;
                    node.classList.toggle('active', active);
                });
            }
            ensureMarkers();
            var resizeTimer = null;
            window.addEventListener('resize', function() {
                if (resizeTimer) window.clearTimeout(resizeTimer);
                resizeTimer = window.setTimeout(ensureMarkers, 60);
            }, { passive: true });
            document.addEventListener('scroll', updateActiveState, { passive: true });
        })();
    """.trimIndent()
}

private fun formatEpubBookmarkPosition(scrollRatio: Float): String {
    val percent = (scrollRatio.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
    val section = when {
        percent <= 5 -> "开头"
        percent <= 25 -> "前段"
        percent <= 45 -> "前中段"
        percent <= 65 -> "中段"
        percent <= 85 -> "后中段"
        percent <= 97 -> "后段"
        else -> "末尾"
    }
    return "章节内$section ($percent%)"
}

private fun formatEpubChapterProgressCompact(scrollRatio: Float): String {
    val percent = (scrollRatio.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
    val section = when {
        percent <= 5 -> "开头"
        percent <= 25 -> "前段"
        percent <= 45 -> "前中段"
        percent <= 65 -> "中段"
        percent <= 85 -> "后中段"
        percent <= 97 -> "后段"
        else -> "末尾"
    }
    return "本章$section $percent%"
}

@Composable
private fun OfflineTtsEngineVoiceDialogs(
    showEngineDialog: Boolean,
    onDismissEngineDialog: () -> Unit,
    showVoiceDialog: Boolean,
    onDismissVoiceDialog: () -> Unit,
    ttsEngineLoading: Boolean,
    availableTtsEngines: List<EpubOfflineTtsEngine>,
    selectedTtsEnginePackage: String?,
    selectedTtsVoiceName: String?,
    effectiveTtsEngine: EpubOfflineTtsEngine?,
    onRefreshEngines: () -> Unit,
    onSelectEngine: (EpubOfflineTtsEngine) -> Unit,
    onSelectDefaultVoice: () -> Unit,
    onSelectVoice: (EpubOfflineTtsVoice) -> Unit
) {
    val context = LocalContext.current

    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = onDismissEngineDialog,
            title = { Text(context.getString(R.string.epub_tts_engine_title)) },
            text = {
                when {
                    ttsEngineLoading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(context.getString(R.string.epub_tts_engine_loading))
                        }
                    }
                    availableTtsEngines.isEmpty() -> {
                        SelectionContainer {
                            Text(context.getString(R.string.epub_tts_engine_empty))
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(availableTtsEngines) { engine ->
                                val selected = selectedTtsEnginePackage == engine.packageName
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    onClick = { onSelectEngine(engine) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.RadioButton(
                                            selected = selected,
                                            onClick = null
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(engine.label, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                text = context.getString(R.string.epub_tts_engine_voice_count, engine.offlineVoiceCount),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (engine.supportsPreferredLocale) {
                                                Text(
                                                    text = context.getString(R.string.epub_tts_engine_locale_hint),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (engine.sampleLocaleTags.isNotEmpty()) {
                                                Text(
                                                    text = engine.sampleLocaleTags.joinToString(" · "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = context.getString(R.string.epub_tts_engine_package, engine.packageName),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissEngineDialog) {
                    Text(context.getString(R.string.common_close))
                }
            },
            dismissButton = {
                TextButton(onClick = onRefreshEngines) {
                    Text(context.getString(R.string.common_refresh))
                }
            }
        )
    }

    if (showVoiceDialog) {
        AlertDialog(
            onDismissRequest = onDismissVoiceDialog,
            title = { Text(context.getString(R.string.epub_tts_voice_title)) },
            text = {
                when {
                    ttsEngineLoading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(context.getString(R.string.epub_tts_voice_loading))
                        }
                    }
                    effectiveTtsEngine == null -> {
                        SelectionContainer {
                            Text(context.getString(R.string.epub_tts_engine_empty))
                        }
                    }
                    effectiveTtsEngine.offlineVoices.isEmpty() -> {
                        SelectionContainer {
                            Text(context.getString(R.string.epub_tts_voice_empty))
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            item {
                                val selected = selectedTtsVoiceName == null
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    onClick = onSelectDefaultVoice
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
                                        Spacer(Modifier.width(12.dp))
                                        Text(context.getString(R.string.epub_tts_voice_default_choice), modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            items(effectiveTtsEngine.offlineVoices) { voice ->
                                val selected = selectedTtsVoiceName == voice.name
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    onClick = { onSelectVoice(voice) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(voice.label, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                text = context.getString(R.string.epub_tts_voice_locale, voice.localeTag),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (voice.supportsPreferredLocale) {
                                                Text(
                                                    text = context.getString(R.string.epub_tts_engine_locale_hint),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissVoiceDialog) {
                    Text(context.getString(R.string.common_close))
                }
            },
            dismissButton = {
                TextButton(onClick = onRefreshEngines) {
                    Text(context.getString(R.string.common_refresh))
                }
            }
        )
    }
}

private class StandaloneWebViewTtsController(
    private val context: Context,
    private val prefs: Preferences,
    private val scope: CoroutineScope,
    private val preferredLocale: Locale
) {
    var showTtsEngineDialog by mutableStateOf(false)
    var showTtsVoiceDialog by mutableStateOf(false)
    var availableTtsEngines by mutableStateOf<List<EpubOfflineTtsEngine>>(emptyList())
    var ttsEngineLoading by mutableStateOf(false)
    var ttsStatusMessage by mutableStateOf<String?>(null)
    var ttsIsStarting by mutableStateOf(false)
    var ttsIsSpeaking by mutableStateOf(false)
    var ttsActiveSegmentId by mutableStateOf<String?>(null)
    var selectedTtsEnginePackage by mutableStateOf<String?>(null)
    var selectedTtsVoiceName by mutableStateOf<String?>(null)
    var ttsSpeedPercent by mutableStateOf(100)

    private var ttsRequestJob by mutableStateOf<Job?>(null)
    private val ttsSession = EpubTtsSession(context)
    private var contentToken: String? = null
    private var contentInitialized = false
    private var documentTitleProvider: () -> String = { "当前页面" }
    private var webViewProvider: () -> WebView? = { null }
    private var scrollRatioProvider: () -> Float = { 0f }

    val effectiveTtsEngine: EpubOfflineTtsEngine?
        get() = availableTtsEngines.firstOrNull { it.packageName == selectedTtsEnginePackage }
            ?: availableTtsEngines.firstOrNull()

    val effectiveTtsVoice: EpubOfflineTtsVoice?
        get() = selectedTtsVoiceName?.let { selectedVoice ->
            effectiveTtsEngine?.offlineVoices?.firstOrNull { it.name == selectedVoice }
        }

    fun bind(
        documentTitleProvider: () -> String,
        webViewProvider: () -> WebView?,
        scrollRatioProvider: () -> Float,
        selectedTtsEnginePackage: String?,
        selectedTtsVoiceName: String?,
        ttsSpeedPercent: Int
    ) {
        this.documentTitleProvider = documentTitleProvider
        this.webViewProvider = webViewProvider
        this.scrollRatioProvider = scrollRatioProvider
        this.selectedTtsEnginePackage = selectedTtsEnginePackage
        this.selectedTtsVoiceName = selectedTtsVoiceName
        this.ttsSpeedPercent = ttsSpeedPercent
    }

    fun requestRefreshTtsEngines() {
        scope.launch {
            ttsEngineLoading = true
            availableTtsEngines = withContext(Dispatchers.IO) {
                loadOfflineTtsEngines(context, preferredLocale)
            }
            ttsEngineLoading = false
        }
    }

    fun onContentChanged(newToken: String) {
        if (!contentInitialized) {
            contentInitialized = true
            contentToken = newToken
            return
        }
        if (contentToken == newToken) return
        contentToken = newToken
        if (ttsIsSpeaking || ttsIsStarting || ttsRequestJob != null) {
            stop(context.getString(R.string.webview_tts_content_switched))
        } else {
            ttsActiveSegmentId = null
            setEpubTtsHighlight(webViewProvider(), null, scrollToSegment = false)
        }
    }

    fun start() {
        val title = documentTitleProvider().ifBlank { "当前页面" }
        publishStatus(context.getString(R.string.epub_tts_start_requested, title))
        startInternal(title)
    }

    fun stop(message: String = context.getString(R.string.epub_tts_stopped)) {
        ttsRequestJob?.cancel()
        ttsRequestJob = null
        ttsSession.stop()
        ttsIsStarting = false
        ttsIsSpeaking = false
        ttsActiveSegmentId = null
        setEpubTtsHighlight(webViewProvider(), null, scrollToSegment = false)
        publishStatus(message)
    }

    fun shutdown() {
        ttsRequestJob?.cancel()
        ttsRequestJob = null
        setEpubTtsHighlight(webViewProvider(), null, scrollToSegment = false)
        ttsSession.shutdown()
    }

    fun selectEngine(engine: EpubOfflineTtsEngine) {
        scope.launch {
            runCatching {
                prefs.setEpubTtsEnginePackage(engine.packageName)
                prefs.setEpubTtsVoiceName(null)
            }.onSuccess {
                ttsSession.shutdown()
                publishStatus(context.getString(R.string.epub_tts_selected_engine, engine.label))
                showTtsEngineDialog = false
            }.onFailure { error ->
                publishStatus(
                    context.getString(
                        R.string.epub_tts_engine_save_failed,
                        error.message ?: error.javaClass.simpleName
                    )
                )
            }
        }
    }

    fun selectDefaultVoice() {
        scope.launch {
            runCatching {
                prefs.setEpubTtsVoiceName(null)
            }.onSuccess {
                ttsSession.shutdown()
                publishStatus(context.getString(R.string.epub_tts_selected_voice, context.getString(R.string.epub_tts_voice_default_choice)))
                showTtsVoiceDialog = false
            }.onFailure { error ->
                publishStatus(
                    context.getString(
                        R.string.epub_tts_voice_save_failed,
                        error.message ?: error.javaClass.simpleName
                    )
                )
            }
        }
    }

    fun selectVoice(voice: EpubOfflineTtsVoice) {
        scope.launch {
            runCatching {
                prefs.setEpubTtsVoiceName(voice.name)
            }.onSuccess {
                ttsSession.shutdown()
                publishStatus(context.getString(R.string.epub_tts_selected_voice, voice.label))
                showTtsVoiceDialog = false
            }.onFailure { error ->
                publishStatus(
                    context.getString(
                        R.string.epub_tts_voice_save_failed,
                        error.message ?: error.javaClass.simpleName
                    )
                )
            }
        }
    }

    private fun startInternal(documentTitle: String) {
        ttsRequestJob?.cancel()
        ttsRequestJob = scope.launch {
            val view = webViewProvider()
            if (view == null) {
                publishStatus(context.getString(R.string.webview_tts_no_text))
                return@launch
            }
            publishStatus(context.getString(R.string.webview_tts_preparing), toast = false)
            val segments = collectEpubTtsSegments(view)
            if (segments.isEmpty()) {
                publishStatus(context.getString(R.string.webview_tts_no_text))
                return@launch
            }
            if (ttsEngineLoading) {
                publishStatus(context.getString(R.string.epub_tts_engine_loading))
                return@launch
            }
            if (availableTtsEngines.isEmpty()) {
                requestRefreshTtsEngines()
                publishStatus(context.getString(R.string.epub_tts_engine_loading))
                return@launch
            }
            val chosenEnginePackage = selectedTtsEnginePackage
                ?.takeIf { selected -> availableTtsEngines.any { it.packageName == selected } }
                ?: availableTtsEngines.firstOrNull()?.packageName
            if (chosenEnginePackage == null) {
                publishStatus(context.getString(R.string.epub_tts_engine_empty))
                return@launch
            }
            val speakLocale = effectiveTtsVoice
                ?.localeTag
                ?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
                ?.let(Locale::forLanguageTag)
                ?: preferredLocale
            val startSegmentIndex = resolveTtsStartSegmentIndex(segments, scrollRatioProvider())
            val speakChunks = segments
                .drop(startSegmentIndex)
                .flatMap { segment ->
                    splitTextForTts(segment.text).map { chunkText ->
                        EpubTtsChunk(
                            domId = segment.domId,
                            topRatio = segment.topRatio,
                            text = chunkText
                        )
                    }
                }
            if (speakChunks.isEmpty()) {
                publishStatus(context.getString(R.string.webview_tts_no_text))
                return@launch
            }
            val utteranceId = "webview_${System.currentTimeMillis()}"
            ttsSession.speak(
                enginePackage = chosenEnginePackage,
                voiceName = effectiveTtsVoice?.name,
                preferredLocale = speakLocale,
                speechRate = ttsSpeedPercent / 100f,
                utteranceId = utteranceId,
                chunks = speakChunks,
                onPreparing = {
                    ttsIsStarting = true
                    ttsIsSpeaking = false
                    publishStatus(context.getString(R.string.epub_tts_engine_loading), toast = false)
                },
                onStart = {
                    ttsIsStarting = false
                    ttsIsSpeaking = true
                    publishStatus(context.getString(R.string.epub_tts_running, documentTitle))
                },
                onChunkStart = { chunk, _, _ ->
                    ttsActiveSegmentId = chunk.domId
                    setEpubTtsHighlight(webViewProvider(), chunk.domId, scrollToSegment = true)
                },
                onFinish = { completed, message ->
                    ttsRequestJob = null
                    ttsIsStarting = false
                    ttsIsSpeaking = false
                    ttsActiveSegmentId = null
                    setEpubTtsHighlight(webViewProvider(), null, scrollToSegment = false)
                    if (!message.isNullOrBlank()) {
                        publishStatus(message)
                    } else if (completed) {
                        publishStatus(context.getString(R.string.webview_tts_finished))
                    }
                },
                onError = { error ->
                    ttsRequestJob = null
                    ttsIsStarting = false
                    ttsIsSpeaking = false
                    ttsActiveSegmentId = null
                    setEpubTtsHighlight(webViewProvider(), null, scrollToSegment = false)
                    publishStatus(error)
                }
            )
        }
    }

    private fun publishStatus(message: String, toast: Boolean = true) {
        ttsStatusMessage = message
        if (toast) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun rememberStandaloneWebViewTtsController(
    preferredLocale: Locale,
    contentToken: String,
    documentTitle: String,
    webViewProvider: () -> WebView?,
    scrollRatioProvider: () -> Float
): StandaloneWebViewTtsController {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }
    val scope = rememberCoroutineScope()
    val controller = remember(preferredLocale) {
        StandaloneWebViewTtsController(context, prefs, scope, preferredLocale)
    }
    val selectedTtsEnginePackage by prefs.epubTtsEnginePackage.collectAsState(initial = null)
    val selectedTtsVoiceName by prefs.epubTtsVoiceName.collectAsState(initial = null)
    val epubTtsSpeedPercent by prefs.epubTtsSpeedPercent.collectAsState(initial = 100)

    controller.bind(
        documentTitleProvider = { documentTitle },
        webViewProvider = webViewProvider,
        scrollRatioProvider = scrollRatioProvider,
        selectedTtsEnginePackage = selectedTtsEnginePackage,
        selectedTtsVoiceName = selectedTtsVoiceName,
        ttsSpeedPercent = epubTtsSpeedPercent
    )

    LaunchedEffect(Unit) {
        controller.requestRefreshTtsEngines()
    }

    LaunchedEffect(controller.showTtsEngineDialog) {
        if (controller.showTtsEngineDialog) {
            controller.requestRefreshTtsEngines()
        }
    }

    LaunchedEffect(contentToken) {
        controller.onContentChanged(contentToken)
    }

    DisposableEffect(controller) {
        onDispose {
            controller.shutdown()
        }
    }

    return controller
}

private fun currentWebViewScrollRatio(view: WebView?): Float {
    if (view == null) return 0f
    val viewportHeight = view.height.toFloat().coerceAtLeast(1f)
    val contentHeight = (view.contentHeight * view.scale).coerceAtLeast(viewportHeight)
    val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(1f)
    return (view.scrollY / maxScroll).coerceIn(0f, 1f)
}

data class EpubOfflineTtsEngine(
    val packageName: String,
    val label: String,
    val offlineVoiceCount: Int,
    val supportsPreferredLocale: Boolean,
    val sampleLocaleTags: List<String>,
    val offlineVoices: List<EpubOfflineTtsVoice>
)

data class EpubOfflineTtsVoice(
    val name: String,
    val label: String,
    val localeTag: String,
    val supportsPreferredLocale: Boolean
)

data class EpubTtsSegment(
    val domId: String,
    val text: String,
    val topRatio: Float
)

data class EpubTtsChunk(
    val domId: String,
    val topRatio: Float,
    val text: String
)

fun preferredEpubTtsLocale(languageTag: String?): Locale {
    val normalized = languageTag
        ?.trim()
        ?.replace('_', '-')
        ?.takeIf { it.isNotEmpty() }
        ?: return Locale.getDefault()
    return runCatching { Locale.forLanguageTag(normalized) }
        .getOrNull()
        ?.takeIf { it.language.isNotBlank() && it.language != "und" }
        ?: Locale.getDefault()
}

private fun extractReadableTextFromHtml(html: String): String {
    val withoutScript = html
        .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
        .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
        .replace(Regex("(?is)<!--.*?-->"), " ")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|div|h[1-6]|li|blockquote|tr|section|article|details|summary|pre)>"), "\n")
    val plain = HtmlCompat.fromHtml(withoutScript, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    return plain
        .replace('\u00A0', ' ')
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

private val EPUB_TTS_BOOTSTRAP_JS = """
(function() {
    if (window.__lmEpubTts) {
        return true;
    }

    var styleId = 'lm-epub-tts-style';

    var containerSelector = 'p, li, blockquote, pre, h1, h2, h3, h4, h5, h6, td, th, figcaption, .assistant-content, .content, div';
    var skipSelector = 'script, style, noscript, summary, .assistant-label, .other-label';

    function ensureStyle() {
        if (document.getElementById(styleId)) {
            return;
        }
        var style = document.createElement('style');
        style.id = styleId;
        style.textContent = '[data-lm-tts-id]{transition:background-color .22s ease,box-shadow .22s ease;scroll-margin-top:72px;}' +
            '.lm-epub-tts-active{background:rgba(255,235,59,0.28)!important;box-shadow:0 0 0 3px rgba(255,235,59,0.18)!important;border-radius:8px;}';
        (document.head || document.documentElement).appendChild(style);
    }

    function normalizeText(value) {
        return String(value || '')
            .replace(/\u00a0/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function shouldSkipTextNode(node) {
        if (!node || !node.nodeValue || !normalizeText(node.nodeValue)) {
            return true;
        }
        var parent = node.parentElement;
        if (!parent) {
            return true;
        }
        if (parent.closest(skipSelector)) {
            return true;
        }
        if (parent.closest('details:not([open])')) {
            return true;
        }
        return false;
    }

    function resolveContainer(node) {
        var parent = node.parentElement;
        if (!parent) {
            return null;
        }
        var container = parent.closest(containerSelector);
        if (!container || !document.body.contains(container)) {
            return null;
        }
        if (container.matches(skipSelector)) {
            return null;
        }
        if (container.closest('details:not([open])')) {
            return null;
        }
        return container;
    }

    function ensureSegmentId(container, index) {
        var existing = container.getAttribute('data-lm-tts-id');
        if (existing) {
            return existing;
        }
        var generated = 'lm-tts-segment-' + index;
        container.setAttribute('data-lm-tts-id', generated);
        return generated;
    }

    function collect() {
        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
        var ordered = [];
        var segmentMap = new Map();
        var node;
        while ((node = walker.nextNode())) {
            if (shouldSkipTextNode(node)) {
                continue;
            }
            var container = resolveContainer(node);
            if (!container) {
                continue;
            }
            var entry = segmentMap.get(container);
            if (!entry) {
                entry = {
                    element: container,
                    parts: []
                };
                segmentMap.set(container, entry);
                ordered.push(entry);
            }
            entry.parts.push(node.nodeValue);
        }

        var docEl = document.documentElement;
        var body = document.body;
        var viewHeight = window.innerHeight || docEl.clientHeight || 0;
        var docHeight = Math.max(docEl.scrollHeight, body.scrollHeight, docEl.offsetHeight, body.offsetHeight, viewHeight);
        var maxScroll = Math.max(1, docHeight - viewHeight);

        return ordered.map(function(entry, index) {
            var text = normalizeText(entry.parts.join(' '));
            if (!text) {
                return null;
            }
            var domId = ensureSegmentId(entry.element, index);
            var rect = entry.element.getBoundingClientRect();
            var top = Math.max(0, rect.top + (window.scrollY || window.pageYOffset || 0));
            var topRatio = Math.min(1, Math.max(0, top / maxScroll));
            return {
                id: domId,
                text: text,
                topRatio: topRatio
            };
        }).filter(function(item) { return !!item; });
    }

    function clearHighlight() {
        ensureStyle();
        document.querySelectorAll('.lm-epub-tts-active').forEach(function(node) {
            node.classList.remove('lm-epub-tts-active');
        });
    }

    function highlight(segmentId, shouldScroll) {
        ensureStyle();
        clearHighlight();
        if (!segmentId) {
            return false;
        }
        var target = null;
        document.querySelectorAll('[data-lm-tts-id]').forEach(function(node) {
            if (target == null && node.getAttribute('data-lm-tts-id') === segmentId) {
                target = node;
            }
        });
        if (!target) {
            return false;
        }
        target.classList.add('lm-epub-tts-active');
        if (shouldScroll) {
            target.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' });
        }
        return true;
    }

    window.__lmEpubTts = {
        collect: collect,
        clear: function() { return highlight(null, false); },
        highlight: highlight
    };
    ensureStyle();
    return true;
})();
""".trimIndent()

private fun parseEpubTtsSegments(rawResult: String?): List<EpubTtsSegment> {
    val decoded = decodeJsStringResult(rawResult) ?: return emptyList()
    return runCatching {
        val array = org.json.JSONArray(decoded)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val domId = item.optString("id").trim()
                val text = item.optString("text").trim()
                if (domId.isEmpty() || text.isEmpty()) continue
                add(
                    EpubTtsSegment(
                        domId = domId,
                        text = text,
                        topRatio = item.optDouble("topRatio", 0.0).toFloat().coerceIn(0f, 1f)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun installEpubTtsBridge(view: WebView?, onInstalled: (() -> Unit)? = null) {
    if (view == null) {
        onInstalled?.invoke()
        return
    }
    view.post {
        view.evaluateJavascript(EPUB_TTS_BOOTSTRAP_JS) {
            onInstalled?.invoke()
        }
    }
}

private suspend fun collectEpubTtsSegments(view: WebView?): List<EpubTtsSegment> {
    if (view == null) return emptyList()
    return suspendCancellableCoroutine { continuation ->
        installEpubTtsBridge(view) {
            view.post {
                view.evaluateJavascript(
                    """(function(){
                        try {
                            return JSON.stringify(window.__lmEpubTts.collect());
                        } catch (error) {
                            return JSON.stringify([]);
                        }
                    })();""".trimIndent()
                ) { rawResult ->
                    if (continuation.isActive) {
                        continuation.resume(parseEpubTtsSegments(rawResult))
                    }
                }
            }
        }
    }
}

private fun setEpubTtsHighlight(view: WebView?, domId: String?, scrollToSegment: Boolean) {
    if (view == null) return
    installEpubTtsBridge(view) {
        val js = if (domId == null) {
            "window.__lmEpubTts.clear();"
        } else {
            val quotedDomId = org.json.JSONObject.quote(domId)
            "window.__lmEpubTts.highlight($quotedDomId, ${if (scrollToSegment) "true" else "false"});"
        }
        view.post { view.evaluateJavascript(js, null) }
    }
}

private fun resolveTtsStartSegmentIndex(segments: List<EpubTtsSegment>, currentScrollRatio: Float): Int {
    if (segments.isEmpty()) return 0
    val lastBeforeOrAtCurrent = segments.indexOfLast { it.topRatio <= currentScrollRatio + 0.01f }
    return if (lastBeforeOrAtCurrent >= 0) lastBeforeOrAtCurrent else 0
}

private suspend fun initTextToSpeechWithTimeout(
    context: Context,
    enginePackage: String?,
    timeoutMs: Long = 8_000L
): TextToSpeech? {
    Log.d(EPUB_DEBUG, "[TTS] init request engine=${enginePackage ?: "<default>"} timeoutMs=$timeoutMs")
    val result = withTimeoutOrNull(timeoutMs) {
        initTextToSpeech(context, enginePackage)
    }
    if (result == null) {
        Log.w(EPUB_DEBUG, "[TTS] init timeout/null engine=${enginePackage ?: "<default>"} timeoutMs=$timeoutMs")
    } else {
        Log.d(EPUB_DEBUG, "[TTS] init success engine=${enginePackage ?: "<default>"}")
    }
    return result
}

private suspend fun initTextToSpeech(context: Context, enginePackage: String?): TextToSpeech? =
    withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val appContext = context.applicationContext
            val holder = arrayOfNulls<TextToSpeech>(1)
            var resumed = false
            Log.d(EPUB_DEBUG, "[TTS] constructing TextToSpeech on main thread engine=${enginePackage ?: "<default>"}")
            val listener = TextToSpeech.OnInitListener { status ->
                val tts = holder[0]
                Log.d(EPUB_DEBUG, "[TTS] onInit engine=${enginePackage ?: "<default>"} status=$status resumed=$resumed active=${continuation.isActive}")
                if (resumed) {
                    tts?.shutdown()
                    return@OnInitListener
                }
                resumed = true
                if (!continuation.isActive) {
                    tts?.shutdown()
                    return@OnInitListener
                }
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    continuation.resume(tts)
                } else {
                    tts?.shutdown()
                    continuation.resume(null)
                }
            }
            val tts = if (enginePackage.isNullOrBlank()) {
                TextToSpeech(appContext, listener)
            } else {
                TextToSpeech(appContext, listener, enginePackage)
            }
            holder[0] = tts
            continuation.invokeOnCancellation {
                Log.w(EPUB_DEBUG, "[TTS] init cancelled engine=${enginePackage ?: "<default>"} resumed=$resumed")
                if (!resumed) {
                    tts.shutdown()
                }
            }
        }
    }

suspend fun inspectOfflineTtsEngine(
    context: Context,
    packageName: String,
    label: String,
    preferredLocale: Locale
): EpubOfflineTtsEngine? {
    Log.d(EPUB_DEBUG, "[TTS] inspect engine start package=$packageName label=$label locale=${preferredLocale.toLanguageTag()}")
    val tts = initTextToSpeechWithTimeout(context, packageName, timeoutMs = 4_000L) ?: return null
    return try {
        val offlineVoices = tts.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
        Log.d(EPUB_DEBUG, "[TTS] inspect engine voices package=$packageName offlineCount=${offlineVoices.size} total=${tts.voices?.size ?: 0}")
        if (offlineVoices.isEmpty()) return null
        val preferredLanguage = preferredLocale.language
        val voiceList = offlineVoices.map { voice ->
            val localeTag = voice.locale?.toLanguageTag()?.takeIf { it.isNotBlank() } ?: "und"
            val matchesPreferred = voice.locale?.language?.equals(preferredLanguage, ignoreCase = true) == true
            EpubOfflineTtsVoice(
                name = voice.name,
                label = buildString {
                    append(localeTag)
                    append(" · ")
                    append(voice.name)
                },
                localeTag = localeTag,
                supportsPreferredLocale = matchesPreferred
            )
        }.sortedWith(
            compareByDescending<EpubOfflineTtsVoice> { it.supportsPreferredLocale }
                .thenBy { it.localeTag }
                .thenBy { it.name }
        )
        val localeTags = offlineVoices
            .mapNotNull { it.locale?.toLanguageTag()?.takeIf { tag -> tag.isNotBlank() } }
            .distinct()
            .sorted()
        val supportsPreferredLocale = offlineVoices.any { voice ->
            val voiceLocale = voice.locale ?: return@any false
            voiceLocale.language.equals(preferredLanguage, ignoreCase = true)
        }
        EpubOfflineTtsEngine(
            packageName = packageName,
            label = label.ifBlank { packageName },
            offlineVoiceCount = offlineVoices.size,
            supportsPreferredLocale = supportsPreferredLocale,
            sampleLocaleTags = localeTags.take(3),
            offlineVoices = voiceList
        )
    } finally {
        Log.d(EPUB_DEBUG, "[TTS] inspect engine shutdown package=$packageName")
        tts.shutdown()
    }
}

suspend fun loadOfflineTtsEngines(
    context: Context,
    preferredLocale: Locale
): List<EpubOfflineTtsEngine> {
    Log.d(EPUB_DEBUG, "[TTS] load engines start locale=${preferredLocale.toLanguageTag()}")
    val bootstrap = initTextToSpeechWithTimeout(context, null, timeoutMs = 4_000L) ?: return emptyList()
    val engines = try {
        bootstrap.engines.orEmpty().map { it.name to it.label }
    } finally {
        bootstrap.shutdown()
    }
    Log.d(EPUB_DEBUG, "[TTS] load engines discovered count=${engines.size} names=${engines.joinToString { it.first }}")
    val result = engines.mapNotNull { (packageName, label) ->
        runCatching {
            inspectOfflineTtsEngine(context, packageName, label, preferredLocale)
        }.onFailure { error ->
            Log.e(EPUB_DEBUG, "[TTS] inspect engine failure package=$packageName", error)
        }.getOrNull()
    }.sortedWith(
        compareByDescending<EpubOfflineTtsEngine> { it.supportsPreferredLocale }
            .thenByDescending { it.offlineVoiceCount }
            .thenBy { it.label.lowercase(Locale.getDefault()) }
    )
    Log.d(EPUB_DEBUG, "[TTS] load engines result count=${result.size} names=${result.joinToString { it.packageName }}")
    return result
}

private fun splitTextForTts(text: String, maxChunkLength: Int = 3000): List<String> {
    val normalized = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    if (normalized.isEmpty()) return emptyList()
    if (normalized.length <= maxChunkLength) return listOf(normalized)

    val chunks = mutableListOf<String>()
    val paragraphs = normalized
        .split(Regex("\\n{2,}"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun flush(buffer: StringBuilder) {
        val chunk = buffer.toString().trim()
        if (chunk.isNotEmpty()) {
            chunks += chunk
        }
        buffer.clear()
    }

    fun appendSegment(segment: String, buffer: StringBuilder) {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.length <= maxChunkLength) {
            val prefix = if (buffer.isEmpty()) "" else "\n\n"
            if (buffer.length + prefix.length + trimmed.length > maxChunkLength) {
                flush(buffer)
            }
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append(trimmed)
            return
        }

        val sentenceParts = trimmed
            .split(Regex("(?<=[。！？!?；;.:])\\s+|(?<=\\.)\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sentenceParts.size <= 1) {
            var start = 0
            while (start < trimmed.length) {
                val end = (start + maxChunkLength).coerceAtMost(trimmed.length)
                val piece = trimmed.substring(start, end).trim()
                if (piece.isNotEmpty()) {
                    if (buffer.isNotEmpty()) flush(buffer)
                    chunks += piece
                }
                start = end
            }
            return
        }

        sentenceParts.forEach { sentence ->
            appendSegment(sentence, buffer)
        }
    }

    val buffer = StringBuilder()
    paragraphs.forEach { paragraph ->
        appendSegment(paragraph, buffer)
    }
    flush(buffer)
    return chunks
}

private class EpubTtsSession(
    context: Context
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var currentEnginePackage: String? = null

    private fun buildSpeakFailureMessage(
        enginePackage: String?,
        voiceName: String?,
        preferredLocale: Locale,
        detail: String
    ): String {
        val engineLabel = enginePackage ?: "system-default"
        val voiceLabel = voiceName ?: "default"
        return appContext.getString(
            R.string.epub_tts_start_failed_detail,
            engineLabel,
            voiceLabel,
            preferredLocale.toLanguageTag(),
            detail
        )
    }

    suspend fun speak(
        enginePackage: String?,
        voiceName: String?,
        preferredLocale: Locale,
        speechRate: Float,
        utteranceId: String,
        chunks: List<EpubTtsChunk>,
        onPreparing: () -> Unit,
        onStart: () -> Unit,
        onChunkStart: (chunk: EpubTtsChunk, index: Int, total: Int) -> Unit,
        onFinish: (completed: Boolean, message: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        val normalizedChunks = chunks
            .map { chunk -> chunk.copy(text = chunk.text.trim()) }
            .filter { it.text.isNotEmpty() }
        if (normalizedChunks.isEmpty()) {
            onError(appContext.getString(R.string.epub_tts_no_text))
            return
        }
        val totalTextLength = normalizedChunks.sumOf { it.text.length }
        val normalizedSpeechRate = speechRate.coerceIn(0.5f, 3.0f)
        Log.d(EPUB_DEBUG, "[TTS] speak request engine=${enginePackage ?: "<default>"} voice=${voiceName ?: "<default>"} locale=${preferredLocale.toLanguageTag()} speechRate=$normalizedSpeechRate textLength=$totalTextLength chunks=${normalizedChunks.size} utteranceId=$utteranceId")
        mainHandler.post(onPreparing)
        val instance = ensureEngine(enginePackage, voiceName, preferredLocale, normalizedSpeechRate, onError) ?: return
        var started = false
        var finished = false
        var completedCount = 0
        val utteranceInfo = normalizedChunks.mapIndexed { index, chunk ->
            "$utteranceId#$index" to Pair(index, chunk)
        }.toMap()

        fun finishOnce(completed: Boolean, message: String?) {
            if (finished) return
            finished = true
            onFinish(completed, message)
        }

        instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val (chunkIndex, chunk) = utteranceInfo[utteranceId] ?: return
                Log.d(EPUB_DEBUG, "[TTS] onStart utteranceId=$utteranceId engine=$currentEnginePackage")
                if (!started) {
                    started = true
                    mainHandler.post(onStart)
                }
                mainHandler.post { onChunkStart(chunk, chunkIndex, normalizedChunks.size) }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId !in utteranceInfo) return
                Log.d(EPUB_DEBUG, "[TTS] onDone utteranceId=$utteranceId engine=$currentEnginePackage")
                completedCount += 1
                if (completedCount >= normalizedChunks.size) {
                    mainHandler.post { finishOnce(true, null) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId !in utteranceInfo) return
                Log.e(EPUB_DEBUG, "[TTS] onError(deprecated) utteranceId=$utteranceId engine=$currentEnginePackage")
                mainHandler.post {
                    finishOnce(
                        false,
                        buildSpeakFailureMessage(
                            currentEnginePackage,
                            voiceName,
                            preferredLocale,
                            "deprecated-callback utteranceId=$utteranceId"
                        )
                    )
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId !in utteranceInfo) return
                Log.e(EPUB_DEBUG, "[TTS] onError utteranceId=$utteranceId engine=$currentEnginePackage errorCode=$errorCode")
                mainHandler.post {
                    finishOnce(
                        false,
                        buildSpeakFailureMessage(
                            currentEnginePackage,
                            voiceName,
                            preferredLocale,
                            "errorCode=$errorCode utteranceId=$utteranceId"
                        )
                    )
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId != null && utteranceId !in utteranceInfo) return
                Log.w(EPUB_DEBUG, "[TTS] onStop utteranceId=$utteranceId engine=$currentEnginePackage interrupted=$interrupted")
                if (!interrupted) return
                mainHandler.post {
                    finishOnce(false, appContext.getString(R.string.epub_tts_stopped))
                }
            }
        })
        instance.stop()
        normalizedChunks.forEachIndexed { index, chunk ->
            val chunkUtteranceId = "$utteranceId#$index"
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = instance.speak(chunk.text, queueMode, null, chunkUtteranceId)
            Log.d(EPUB_DEBUG, "[TTS] speak returned result=$result engine=$currentEnginePackage utteranceId=$chunkUtteranceId chunk=${index + 1}/${normalizedChunks.size} chunkLength=${chunk.text.length} domId=${chunk.domId}")
            if (result == TextToSpeech.ERROR) {
                onError(
                    buildSpeakFailureMessage(
                        currentEnginePackage,
                        voiceName,
                        preferredLocale,
                        "speakResult=$result chunk=${index + 1}/${normalizedChunks.size} chunkLength=${chunk.text.length} domId=${chunk.domId}"
                    )
                )
                return
            }
        }
    }

    fun stop() {
        Log.d(EPUB_DEBUG, "[TTS] session stop engine=$currentEnginePackage")
        tts?.stop()
    }

    fun shutdown() {
        Log.d(EPUB_DEBUG, "[TTS] session shutdown engine=$currentEnginePackage")
        tts?.stop()
        tts?.shutdown()
        tts = null
        currentEnginePackage = null
    }

    private suspend fun ensureEngine(
        enginePackage: String?,
        voiceName: String?,
        preferredLocale: Locale,
        speechRate: Float,
        onError: (String) -> Unit
    ): TextToSpeech? {
        val normalizedEngine = enginePackage?.trim()?.takeIf { it.isNotEmpty() }
        val current = tts
        val reusable = current != null && currentEnginePackage == normalizedEngine
        Log.d(EPUB_DEBUG, "[TTS] ensureEngine target=${normalizedEngine ?: "<default>"} reusable=$reusable current=$currentEnginePackage voice=${voiceName ?: "<default>"}")
        val instance = if (reusable) {
            current
        } else {
            shutdown()
            initTextToSpeechWithTimeout(appContext, normalizedEngine, timeoutMs = 10_000L)?.also {
                tts = it
                currentEnginePackage = normalizedEngine
            }
        }
        if (instance == null) {
            Log.e(EPUB_DEBUG, "[TTS] ensureEngine failed target=${normalizedEngine ?: "<default>"}")
            onError(appContext.getString(R.string.epub_tts_engine_init_failed, normalizedEngine ?: "system-default"))
            return null
        }
        val languageResult = instance.setLanguage(preferredLocale)
        Log.d(EPUB_DEBUG, "[TTS] setLanguage primary locale=${preferredLocale.toLanguageTag()} result=$languageResult engine=$currentEnginePackage")
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallbackLocale = Locale.getDefault()
            val fallbackResult = instance.setLanguage(fallbackLocale)
            Log.d(EPUB_DEBUG, "[TTS] setLanguage fallback locale=${fallbackLocale.toLanguageTag()} result=$fallbackResult engine=$currentEnginePackage")
            if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(EPUB_DEBUG, "[TTS] language unsupported engine=$currentEnginePackage preferred=${preferredLocale.toLanguageTag()} fallback=${fallbackLocale.toLanguageTag()}")
                onError(appContext.getString(R.string.epub_tts_engine_language_failed, preferredLocale.toLanguageTag()))
                return null
            }
        }
        val normalizedVoiceName = voiceName?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedVoiceName != null) {
            val chosenVoice = instance.voices.orEmpty().firstOrNull { voice ->
                !voice.isNetworkConnectionRequired && voice.name == normalizedVoiceName
            }
            if (chosenVoice != null) {
                instance.voice = chosenVoice
                Log.d(EPUB_DEBUG, "[TTS] voice applied engine=$currentEnginePackage voice=${chosenVoice.name} locale=${chosenVoice.locale?.toLanguageTag()}")
            } else {
                Log.w(EPUB_DEBUG, "[TTS] requested voice missing engine=$currentEnginePackage voice=$normalizedVoiceName")
            }
        }
        val speechRateResult = instance.setSpeechRate(speechRate)
        Log.d(EPUB_DEBUG, "[TTS] setSpeechRate rate=$speechRate result=$speechRateResult engine=$currentEnginePackage")
        return instance
    }
}

/** 支持章节切换和滚动位置恢复的 WebView */
private class GestureWebView(
    context: Context,
    private val onScrollRatioChange: ((Float) -> Unit)? = null,
    private val onEdgeNavigatePreviousChapter: (() -> Unit)? = null,
    private val onEdgeNavigateNextChapter: (() -> Unit)? = null
) : WebView(context) {
    private var pendingScrollRatio: Float? = null
    private var isRestoringScroll = false

    private var lastReportedRatio: Float = -1f

    private fun navigateBySingleTap(x: Float): Boolean {
        val viewWidth = width.toFloat().coerceAtLeast(1f)
        val edgeWidth = viewWidth * 0.3f
        return when {
            x < edgeWidth -> {
                jumpByViewport(-1)
                true
            }
            x > viewWidth - edgeWidth -> {
                jumpByViewport(1)
                true
            }
            else -> false
        }
    }

    private fun selectParagraphAt(x: Float, y: Float): Boolean {
        val viewportWidth = width.toFloat().coerceAtLeast(1f)
        val viewportHeight = height.toFloat().coerceAtLeast(1f)
        val clampedX = x.coerceIn(0f, viewportWidth - 1f)
        val clampedY = y.coerceIn(0f, viewportHeight - 1f)
        val quotedSelector = org.json.JSONObject.quote(EPUB_BOOKMARK_TARGET_SELECTOR)
        val js = """
            (function() {
                function normalize(value) {
                    return String(value || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
                }
                var selector = $quotedSelector;
                var el = document.elementFromPoint(${clampedX.toInt()}, ${clampedY.toInt()});
                if (!el) return false;
                var container = el.closest ? el.closest(selector) : el;
                while (container && container.tagName && container.tagName.toLowerCase() === 'div' && container.children.length === 1) {
                    var child = container.children[0];
                    if (!child || !child.matches || !child.matches(selector)) break;
                    container = child;
                }
                var text = normalize((container && (container.innerText || container.textContent)) || (el.innerText || el.textContent));
                if (!text) return false;
                if (!container) container = el;
                var selection = window.getSelection ? window.getSelection() : null;
                if (!selection) return false;
                var range = document.createRange();
                range.selectNodeContents(container);
                selection.removeAllRanges();
                selection.addRange(range);
                return true;
            })();
        """.trimIndent()
        post {
            evaluateJavascript(js, null)
        }
        return true
    }

    private fun jumpByViewport(direction: Int) {
        val signedDirection = direction.coerceIn(-1, 1)
        if (signedDirection == 0) return
        val js = """
            (function() {
                var viewHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                var docHeight = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight, viewHeight);
                var maxScroll = Math.max(0, docHeight - viewHeight);
                var currentY = window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
                var targetY = currentY + ($signedDirection * viewHeight);
                var reachedStart = false;
                var reachedEnd = false;
                if (targetY < 0) {
                    targetY = 0;
                    reachedStart = true;
                }
                if (targetY > maxScroll) {
                    targetY = maxScroll;
                    reachedEnd = true;
                }
                window.scrollTo(0, targetY);
                return JSON.stringify({
                    reachedStart: reachedStart,
                    reachedEnd: reachedEnd,
                    maxScroll: maxScroll,
                    currentY: currentY,
                    targetY: targetY
                });
            })();
        """.trimIndent()
        post {
            evaluateJavascript(js) { result ->
                val normalized = result
                    ?.removePrefix("\"")
                    ?.removeSuffix("\"")
                    ?.replace("\\\"", "\"")
                    ?: return@evaluateJavascript
                val reachedStart = normalized.contains("\"reachedStart\":true")
                val reachedEnd = normalized.contains("\"reachedEnd\":true")
                when {
                    signedDirection < 0 && reachedStart -> onEdgeNavigatePreviousChapter?.invoke()
                    signedDirection > 0 && reachedEnd -> onEdgeNavigateNextChapter?.invoke()
                }
            }
        }
    }

    private val tapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return navigateBySingleTap(e.x)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            return selectParagraphAt(e.x, e.y)
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        tapDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        // 如果正在恢复滚动位置，不报告滚动比例
        if (isRestoringScroll) return
        // 使用 JavaScript 获取准确的滚动比例
        evaluateJavascript("(function() { var docH = document.documentElement.scrollHeight; var viewH = window.innerHeight; if (docH <= viewH) return 0; return window.scrollY / (docH - viewH); })();") { result ->
            try {
                val ratio = result.trim().toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
                // 只在比例变化超过 1% 时才报告，避免频繁更新
                if (kotlin.math.abs(ratio - lastReportedRatio) > 0.01f) {
                    lastReportedRatio = ratio
                    onScrollRatioChange?.invoke(ratio)
                }
            } catch (_: Exception) {}
        }
    }

    /** 设置待恢复的滚动位置 */
    /** 向前翻一页（与右侧点击行为一致），到末尾时触发章节切换回调 */
    fun scrollNextPage() = jumpByViewport(1)

    fun setPendingScrollRatio(ratio: Float) {
        pendingScrollRatio = ratio
        alpha = if (ratio > 0f) 0f else 1f
    }

    private fun buildScrollToRatioScript(ratio: Float): String {
        return """
            (function() {
                var ratio = $ratio;
                var docHeight = document.documentElement.scrollHeight;
                var viewHeight = window.innerHeight;
                if (docHeight > viewHeight) {
                    var maxScroll = docHeight - viewHeight;
                    var targetY;
                    if (ratio >= 0.995) {
                        targetY = maxScroll;
                    } else {
                        var adjustedRatio = ratio * 0.97;
                        targetY = Math.floor(adjustedRatio * maxScroll);
                    }
                    window.scrollTo(0, targetY);
                }
            })();
        """.trimIndent()
    }

    /** 直接滚动到指定比例（用于恢复进度时内容未变的情况） */
    fun scrollToRatio(ratio: Float) {
        if (ratio <= 0f) return
        val js = buildScrollToRatioScript(ratio)
        post { evaluateJavascript(js, null) }
    }

    /** 尝试恢复滚动位置，在页面加载完成后调用 */
    fun restoreScrollPosition() {
        val ratio = pendingScrollRatio ?: return
        pendingScrollRatio = null
        if (ratio <= 0f) {
            alpha = 1f
            return
        }
        isRestoringScroll = true
        val js = buildScrollToRatioScript(ratio)
        // 延迟执行，确保页面已渲染
        postDelayed({
            evaluateJavascript(js, null)
            postDelayed({
                isRestoringScroll = false
                alpha = 1f
            }, 120)
        }, 100)
    }
}

/** PDF 查看器页面数据 */
data class PdfPageData(
    val pageIndex: Int,
    val bitmap: Bitmap,
    val pageWidth: Int,
    val pageHeight: Int
)

/**
 * PDF 文件查看器
 * 使用 Android 内置的 PdfRenderer 渲染 PDF 页面
 * 按需加载页面，支持大文件
 * 支持左右点击翻页，自适应屏幕宽度
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    uri: String,
    fileName: String,
    prefs: Preferences,
    bookNoteLoadedData: BookNoteLoadedData?,
    bookNoteEntries: List<BookNoteEntry>,
    bookNoteInProgress: Boolean,
    onRequestOpenBookNotes: () -> Unit,
    onBookNoteEntriesChanged: (List<BookNoteEntry>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }

    var pageCount by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var initialPageLoaded by remember(uri) { mutableStateOf(false) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageLoading by remember { mutableStateOf(false) }
    var fitToWidthZoom by remember { mutableStateOf<Float?>(null) } // 自适应宽度的缩放比例
    var zoom by remember { mutableStateOf(1f) } // 用户调整的缩放（相对于自适应）
    var zoomLoaded by remember(uri) { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var pageInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) } // 原始页面宽高
    val hideReaderFloatingNextButton by prefs.hideReaderFloatingNextButton.collectAsState(initial = false)
    val floatingButtonXPercent by prefs.readerFloatingNextButtonXPercent.collectAsState(initial = 100)
    val floatingButtonYPercent by prefs.readerFloatingNextButtonYPercent.collectAsState(initial = 82)
    var showBookmarks by remember { mutableStateOf(false) }
    var showAddBookmark by remember { mutableStateOf(false) }
    var showGoToPage by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var editingBookNote by remember { mutableStateOf<BookNoteEditorState?>(null) }
    var deleteBookNoteConfirm by remember { mutableStateOf<BookNoteEntry?>(null) }
    // 缓存最近渲染的页面
    val pageCache = remember { mutableMapOf<Int, Bitmap>() }
    val currentBookTitle = fileName
    val currentBookBookmarkEntries = remember(bookNoteEntries, currentBookTitle) {
        bookNoteEntries
            .filter { it.matchesBookTitle(currentBookTitle) }
            .sortedByDescending { it.createdAt }
    }

    fun currentPageLocationLabel(): String = "第${currentPage + 1}页 / 共${pageCount}页"

    fun upsertBookNoteEntry(updatedEntry: BookNoteEntry) {
        onBookNoteEntriesChanged(
            if (bookNoteEntries.any { it.id == updatedEntry.id }) {
                bookNoteEntries.map { entry -> if (entry.id == updatedEntry.id) updatedEntry else entry }
            } else {
                bookNoteEntries + updatedEntry
            }
        )
    }

    fun goToBookNote(entry: BookNoteEntry) {
        val targetPage = entry.chapterIndex
        if (targetPage == null || targetPage !in 0 until pageCount) {
            Toast.makeText(context, "这条读书笔记没有可跳转的位置", Toast.LENGTH_SHORT).show()
            return
        }
        currentPage = targetPage
        showBookmarks = false
    }

    fun requestAddBookmark() {
        if (bookNoteLoadedData == null && !bookNoteInProgress) {
            onRequestOpenBookNotes()
        }
        showAddBookmark = true
    }

    // 初始加载：获取页数和第一页尺寸
    LaunchedEffect(uri) {
        isLoading = true
        errorMsg = null
        try {
            val result = withContext(Dispatchers.IO) {
                val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                    ?: return@withContext null
                val renderer = PdfRenderer(pfd)
                val c = renderer.pageCount
                // 获取第一页尺寸用于计算自适应缩放
                val firstPage = if (c > 0) renderer.openPage(0) else null
                val w = firstPage?.width ?: 0
                val h = firstPage?.height ?: 0
                firstPage?.close()
                renderer.close()
                pfd.close()
                Triple(c, w, h)
            }
            if (result != null && result.first > 0) {
                pageCount = result.first
                pageInfo = Pair(result.second, result.third)
                val savedPage = prefs.getPdfLastPageForUri(uri)?.coerceIn(0, result.first - 1) ?: 0
                currentPage = savedPage
                val savedZoom = prefs.getPdfZoomPercentForUri(uri)?.coerceIn(50, 300) ?: 100
                zoom = (savedZoom / 100f).coerceIn(0.5f, 3f)
                zoomLoaded = true
                initialPageLoaded = true
                // 计算自适应宽度的缩放比例
                if (result.second > 0 && screenWidthPx > 0) {
                    fitToWidthZoom = screenWidthPx / result.second.toFloat()
                }
            } else {
                errorMsg = "PDF 文件为空"
            }
        } catch (e: Exception) {
            Log.e("PdfViewer", "加载 PDF 失败", e)
            errorMsg = "加载失败: ${e.message}"
        }
        isLoading = false
    }

    LaunchedEffect(uri, currentPage, initialPageLoaded) {
        if (initialPageLoaded) {
            prefs.setPdfLastPageForUri(uri, currentPage)
        }
    }

    LaunchedEffect(uri, zoom, zoomLoaded) {
        if (zoomLoaded) {
            prefs.setPdfZoomPercentForUri(uri, (zoom * 100f).roundToInt())
        }
    }

    // 渲染当前页面
    fun renderPage(pageIndex: Int, zoomLevel: Float) {
        if (pageIndex !in 0 until pageCount) return

        // 检查缓存
        val cacheKey = (pageIndex * 1000 + (zoomLevel * 100).toInt())
        pageCache[cacheKey]?.let {
            currentPageBitmap = it
            return
        }

        scope.launch {
            pageLoading = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                        ?: return@withContext null
                    val renderer = PdfRenderer(pfd)
                    val page = renderer.openPage(pageIndex)

                    val width = (page.width * zoomLevel).toInt().coerceAtLeast(100)
                    val height = (page.height * zoomLevel).toInt().coerceAtLeast(100)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()
                    pfd.close()
                    bmp
                }
                if (bitmap != null) {
                    // 清理旧缓存，只保留最近的3页
                    if (pageCache.size > 3) {
                        pageCache.keys.sorted().take(pageCache.size - 3).forEach { pageCache.remove(it) }
                    }
                    pageCache[cacheKey] = bitmap
                    currentPageBitmap = bitmap
                }
            } catch (e: Exception) {
                Log.e("PdfViewer", "渲染页面失败", e)
            }
            pageLoading = false
        }
    }

    // 当页码或缩放变化时渲染页面
    LaunchedEffect(currentPage, zoom, pageCount, fitToWidthZoom) {
        if (pageCount > 0 && !isLoading && fitToWidthZoom != null) {
            val actualZoom = fitToWidthZoom!! * zoom
            renderPage(currentPage, actualZoom)
        }
    }

    // 处理点击翻页
    fun handleTap(x: Float, screenWidth: Float) {
        val edgeZone = screenWidth * 0.3f
        when {
            x < edgeZone -> {
                // 左侧点击 -> 上一页
                if (currentPage > 0) currentPage--
            }
            x > screenWidth - edgeZone -> {
                // 右侧点击 -> 下一页
                if (currentPage < pageCount - 1) currentPage++
            }
        }
    }

    BackHandler {
        when {
            showBookmarks -> showBookmarks = false
            showAddBookmark -> showAddBookmark = false
            editingBookNote != null -> editingBookNote = null
            else -> onBack()
        }
    }

    if (showBookmarks) {
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("收藏夹 - $fileName") },
            text = {
                if (bookNoteLoadedData == null && bookNoteInProgress) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                } else if (currentBookBookmarkEntries.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "暂无收藏",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "点击下方“新增当前位置收藏”可创建定位笔记",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(currentBookBookmarkEntries.size) { index ->
                            val bookmark = currentBookBookmarkEntries[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                onClick = { goToBookNote(bookmark) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = bookmark.chapterIndex?.let { "第${it + 1}页" } ?: "整本书",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = bookmark.formattedTime,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = bookmark.chapterInfo ?: "未绑定位置",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (bookmark.content.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = bookmark.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = {
                                            editingBookNote = BookNoteEditorState(
                                                editingId = bookmark.id,
                                                bookTitle = bookmark.bookTitle,
                                                chapterInfo = bookmark.chapterInfo.orEmpty(),
                                                quote = "",
                                                content = bookmark.content,
                                                chapterIndex = bookmark.chapterIndex,
                                                chapterTitle = bookmark.chapterTitle,
                                                scrollRatio = bookmark.scrollRatio,
                                                createdAt = bookmark.createdAt
                                            )
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("编辑")
                                        }
                                        TextButton(onClick = { deleteBookNoteConfirm = bookmark }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("删除")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { requestAddBookmark() }) { Text("新增当前位置收藏") }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarks = false }) { Text("关闭") }
            }
        )
    }

    if (showGoToPage) {
        var inputText by remember(showGoToPage) { mutableStateOf("") }
        var inputError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showGoToPage = false },
            title = { Text("跳转到指定页") },
            text = {
                Column {
                    Text(
                        "当前第 ${currentPage + 1} 页，共 $pageCount 页",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            inputError = null
                        },
                        label = { Text("目标页码（1 - $pageCount）") },
                        isError = inputError != null,
                        supportingText = inputError?.let { { Text(it) } },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = inputText.trim().toIntOrNull()
                    if (target == null) {
                        inputError = "请输入有效的数字"
                    } else if (target < 1 || target > pageCount) {
                        inputError = "页码范围：1 - $pageCount"
                    } else {
                        currentPage = target - 1
                        showGoToPage = false
                    }
                }) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showGoToPage = false }) { Text("取消") }
            }
        )
    }

    if (showAddBookmark) {
        var noteText by remember(showAddBookmark, currentPage) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddBookmark = false },
            title = { Text("添加收藏") },
            text = {
                Column {
                    Text(
                        "当前位置：${currentPageLocationLabel()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("感想（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val existing = currentBookBookmarkEntries.firstOrNull { it.chapterIndex == currentPage }
                    if (existing != null) {
                        editingBookNote = BookNoteEditorState(
                            editingId = existing.id,
                            bookTitle = existing.bookTitle,
                            chapterInfo = currentPageLocationLabel(),
                            quote = "",
                            content = if (noteText.isNotBlank()) noteText else existing.content,
                            chapterIndex = currentPage,
                            chapterTitle = "第${currentPage + 1}页",
                            scrollRatio = null,
                            createdAt = existing.createdAt
                        )
                        Toast.makeText(context, "该位置已收藏，已转为编辑", Toast.LENGTH_SHORT).show()
                    } else {
                        upsertBookNoteEntry(
                            BookNoteEntry(
                                id = (bookNoteEntries.maxOfOrNull { it.id } ?: 0L) + 1L,
                                bookTitle = currentBookTitle,
                                chapterInfo = currentPageLocationLabel(),
                                quote = null,
                                content = noteText.trimEnd(),
                                chapterIndex = currentPage,
                                chapterTitle = "第${currentPage + 1}页",
                                scrollRatio = null,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        Toast.makeText(context, "已添加收藏", Toast.LENGTH_SHORT).show()
                    }
                    showAddBookmark = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmark = false }) { Text("取消") }
            }
        )
    }

    editingBookNote?.let { state ->
        BookNoteEditorDialog(
            state = state,
            inProgress = bookNoteInProgress,
            onDismiss = { if (!bookNoteInProgress) editingBookNote = null },
            onReassociateCurrentPosition = {
                editingBookNote = state.copy(
                    chapterInfo = currentPageLocationLabel(),
                    chapterIndex = currentPage,
                    chapterTitle = "第${currentPage + 1}页",
                    scrollRatio = null
                )
            },
            onClearLocation = {
                editingBookNote = state.copy(
                    chapterInfo = "",
                    chapterIndex = null,
                    chapterTitle = null,
                    scrollRatio = null
                )
            },
            onConfirm = { updated ->
                val normalizedTitle = updated.bookTitle.trim()
                val normalizedContent = updated.content.trimEnd()
                val normalizedChapterInfo = updated.chapterInfo.trim().takeIf { it.isNotEmpty() }
                if (normalizedTitle.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.book_note_book_title_required), Toast.LENGTH_SHORT).show()
                    return@BookNoteEditorDialog
                }
                if (normalizedContent.isBlank() && normalizedChapterInfo == null) {
                    Toast.makeText(context, context.getString(R.string.book_note_content_or_position_required), Toast.LENGTH_SHORT).show()
                    return@BookNoteEditorDialog
                }
                val updatedEntry = BookNoteEntry(
                    id = updated.editingId ?: ((bookNoteEntries.maxOfOrNull { it.id } ?: 0L) + 1L),
                    bookTitle = normalizedTitle,
                    chapterInfo = normalizedChapterInfo,
                    quote = null,
                    content = normalizedContent,
                    chapterIndex = updated.chapterIndex,
                    chapterTitle = updated.chapterTitle,
                    scrollRatio = null,
                    createdAt = updated.createdAt
                )
                upsertBookNoteEntry(updatedEntry)
                Toast.makeText(context, "已更新", Toast.LENGTH_SHORT).show()
                editingBookNote = null
            }
        )
    }

    deleteBookNoteConfirm?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteBookNoteConfirm = null },
            title = { Text("删除读书笔记") },
            text = { Text("确定删除这条读书笔记吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBookNoteEntriesChanged(bookNoteEntries.filterNot { it.id == entry.id })
                        deleteBookNoteConfirm = null
                    },
                    enabled = !bookNoteInProgress
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteBookNoteConfirm = null }, enabled = !bookNoteInProgress) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("添加收藏") },
                                onClick = {
                                    showMoreMenu = false
                                    requestAddBookmark()
                                },
                                leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("查看收藏") },
                                onClick = {
                                    showMoreMenu = false
                                    if (bookNoteLoadedData == null && !bookNoteInProgress) {
                                        onRequestOpenBookNotes()
                                    }
                                    showBookmarks = true
                                },
                                leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null) }
                            )
                        }
                    }
                    // 缩放按钮
                    IconButton(onClick = {
                        zoom = (zoom - 0.25f).coerceAtLeast(0.5f)
                    }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "缩小")
                    }
                    Text("${(zoom * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = {
                        zoom = (zoom + 0.25f).coerceAtMost(3f)
                    }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "放大")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (pageCount > 0) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一页")
                    }
                    Text(
                        "${currentPage + 1} / $pageCount",
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showGoToPage = true },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(
                        onClick = { if (currentPage < pageCount - 1) currentPage++ },
                        enabled = currentPage < pageCount - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一页")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("加载中...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                errorMsg != null -> {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
                pageLoading && currentPageBitmap == null -> {
                    Text("正在渲染页面...", style = MaterialTheme.typography.bodyLarge)
                }
                currentPageBitmap != null -> {
                    var boxSizePx by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                boxSizePx = androidx.compose.ui.geometry.Size(
                                    coordinates.size.width.toFloat(),
                                    coordinates.size.height.toFloat()
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        handleTap(offset.x, boxSizePx.width)
                                    },
                                    onLongPress = {
                                        Toast.makeText(
                                            context,
                                            "提示：PDF 页面渲染为图像，暂不支持文字选择。\n如需选择文字，请使用专业 PDF 阅读器。",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                bitmap = currentPageBitmap!!.asImageBitmap(),
                                contentDescription = "第 ${currentPage + 1} 页",
                                modifier = Modifier.wrapContentSize()
                            )
                        }
                        if (pageLoading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            )
                        }
                        if (!hideReaderFloatingNextButton) {
                            DraggableNextReadButton(
                                contentDescription = "下一页",
                                enabled = currentPage < pageCount - 1,
                                onClick = {
                                    if (currentPage < pageCount - 1) {
                                        currentPage++
                                    }
                                },
                                initialXPercent = floatingButtonXPercent,
                                initialYPercent = floatingButtonYPercent,
                                onPositionChangePercent = { xPercent, yPercent ->
                                    scope.launch {
                                        prefs.setReaderFloatingNextButtonPositionPercent(xPercent, yPercent)
                                    }
                                }
                            )
                        }
                    }
                }
                pageCount == 0 -> {
                    Text("PDF 为空", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
