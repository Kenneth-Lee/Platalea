package com.kenny.localmanager.ui

import android.annotation.SuppressLint
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
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.GestureDetector
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.getDirectoryToOpen
import com.kenny.localmanager.file.listHtmlZipContentFiles
import com.kenny.localmanager.file.listMdZipContentFiles
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.epub.EpubBookmark
import com.kenny.localmanager.epub.EpubBookmarkManager
import com.kenny.localmanager.epub.EpubReadingProgress
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.BottomAppBar
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Size
import com.kenny.localmanager.file.EpubExtractResult
import com.kenny.localmanager.file.getEpubChapterFile

private const val MAX_MARKDOWN_BYTES = 512 * 1024
private const val MAX_RST_BYTES = 512 * 1024
private const val STANDALONE_MD_CACHE_LIMIT = 6

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
    val preprocessed = md.replace("~~~", "~~")
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
                        var line = code.substring(i, lineEnd);
                        out += span("hl-comment", line);
                        i = lineEnd;
                        continue;
                    }

                    if (ch === '"' || ch === "'" || ch === "`") {
                        var q = ch;
                        var j = i + 1;
                        while (j < code.length) {
                            if (code[j] === "\\\\") {
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

/** 简易 reStructuredText → HTML，覆盖常用语法（标题、粗/斜体、代码、链接、列表、代码块）。 */
private fun rstToHtml(rst: String): String {
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
            out.append("<").append(tag).append(">").append(escapeHtml(line.trim())).append("</").append(tag).append(">")
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
                    if (lines[i].isNotBlank()) caption.add(rstInlineToHtml(escapeHtml(lines[i].trimStart())))
                    i++
                }
                val style = listOfNotNull(width, height).filter { it.isNotBlank() }.joinToString("; ")
                out.append("<figure>")
                out.append("<img src=\"").append(escapeHtml(path)).append("\" alt=\"").append(alt).append("\"")
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
            if (restOfLine.isNotBlank()) mathLines.add(escapeHtml(restOfLine))
            i++
            while (i < lines.size && (lines[i].isEmpty() || lines[i].startsWith(" ") || lines[i].startsWith("\t"))) {
                if (lines[i].isNotBlank()) mathLines.add(escapeHtml(lines[i].trimStart()))
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
        if (line.trim().startsWith(".. code-block::") || line.trim().startsWith(".. sourcecode::")) {
            val directive = line.trim()
            val langRaw = directive.substringAfter("::", "").trim()
            val language = normalizeCodeLanguage(langRaw)
            i++
            while (i < lines.size && lines[i].trimStart().startsWith(":")) i++
            if (i < lines.size && lines[i].isBlank()) i++
            val codeLines = mutableListOf<String>()
            while (i < lines.size && (lines[i].isEmpty() || lines[i].startsWith(" ") || lines[i].startsWith("\t"))) {
                if (lines[i].isNotBlank()) codeLines.add(escapeHtml(lines[i].trimStart()))
                i++
            }
            if (codeLines.isNotEmpty()) {
                out.append(buildCodeBlockHtml(codeLines, language))
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
                out.append("<li>").append(rstInlineToHtml(escapeHtml(lines[i].trim().drop(1).trim()))).append("</li>")
                i++
            }
            out.append("</ul>")
            continue
        }
        if (line.trim().matches(Regex("^\\d+\\.\\s.+"))) {
            out.append("<ol>")
            while (i < lines.size && lines[i].trim().matches(Regex("^\\d+\\.\\s.+"))) {
                out.append("<li>").append(rstInlineToHtml(escapeHtml(lines[i].trim().replaceFirst(Regex("^\\d+\\.\\s"), "")))).append("</li>")
                i++
            }
            out.append("</ol>")
            continue
        }
        if (line.trim() == ".." || (line.trim().startsWith("::") && line.trim().length == 2)) {
            i++
            val codeLines = mutableListOf<String>()
            while (i < lines.size && (lines[i].isEmpty() || lines[i].startsWith(" ") || lines[i].startsWith("\t"))) {
                if (lines[i].isNotBlank()) codeLines.add(escapeHtml(lines[i].trimStart()))
                i++
            }
            if (codeLines.isNotEmpty()) {
                out.append(buildCodeBlockHtml(codeLines))
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
                if (lines[i].isNotBlank()) codeLines.add(escapeHtml(lines[i].trimStart()))
                i++
            }
            if (codeLines.isNotEmpty()) out.append(buildCodeBlockHtml(codeLines))
            continue
        }
        // RST：仅空行分段；连续非空行合并为一段，用空格连接
        val paraLines = mutableListOf(line)
        while (i + 1 < lines.size && lines[i + 1].isNotBlank() && !rstLineStartsBlock(lines[i + 1], lines.getOrNull(i + 2)) { peekUnderlineChar(it) }) {
            i++
            paraLines.add(lines[i])
        }
        val paraText = paraLines.joinToString(" ")
        out.append("<p>").append(rstInlineToHtml(escapeHtml(paraText))).append("</p>")
        i++
    }
    return out.toString()
}

private fun rstLineStartsBlock(line: String, next: String?, peekUnderlineChar: (String) -> Char?): Boolean {
    val t = line.trim()
    if (t.startsWith(".. figure::") || t.startsWith(".. math::")) return true
    if (t.startsWith(".. code-block::") || t.startsWith(".. sourcecode::")) return true
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
private const val MD_VIEWER_BASE_URL = "https://local-md.invalid/"

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
th, td { border: 1px solid rgba(128,128,128,0.4); padding: 6px 10px; text-align: left; }
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
document.querySelectorAll('a[href^="#fn"], sup.footnote-ref a').forEach(function(a) {
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
            current.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("正则查找") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("正则表达式") },
                    placeholder = { Text("例如 /error|warn/i 或 (?i)error") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "使用 JavaScript 正则语法；支持 /pattern/flags 与 (?i)pattern 形式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                when {
                    result.error != null -> Text(result.error, color = MaterialTheme.colorScheme.error)
                    result.hasMatches -> Text("第 ${result.currentIndex + 1} / ${result.count} 处", color = MaterialTheme.colorScheme.primary)
                    query.isNotBlank() -> Text("未找到匹配", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text("输入正则后点击“查找”。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onSearch) { Text("查找") }
                    TextButton(onClick = onPrevious, enabled = result.hasMatches) { Text("上一个") }
                    TextButton(onClick = onNext, enabled = result.hasMatches) { Text("下一个") }
                    TextButton(onClick = onClear, enabled = result.hasMatches || query.isNotBlank()) { Text("清除") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
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
        if (isRst) rstToHtml(trimmed)
        else markdownToHtml(trimmed)
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
                document.querySelectorAll('a[href^="#fn"], sup.footnote-ref a').forEach(function(a) {
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
    var pendingInternalFile by remember { mutableStateOf<File?>(null) }
    var showFindDialog by remember { mutableStateOf(false) }
    var regexQuery by remember { mutableStateOf("") }
    var regexFindUiState by remember { mutableStateOf(RegexFindUiState()) }

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
                pageLoading = sessionCache.pageReadyMap[cacheKey] != true
                loadingMessage = if (pageLoading) "正在初始化页面样式与脚本…" else "已完成，正在显示页面…"
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
    onBack: () -> Unit,
    logDebug: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var scalePercent by remember { mutableStateOf(100) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showAddBookmark by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<EpubBookmark?>(null) }
    var currentChapterIndex by remember { mutableStateOf(0) }
    var currentScrollRatio by remember { mutableStateOf(0f) }
    var showFindDialog by remember { mutableStateOf(false) }
    var regexQuery by remember { mutableStateOf("") }
    var regexFindUiState by remember { mutableStateOf(RegexFindUiState()) }

    // 收藏夹管理器
    val bookmarkManager = remember { EpubBookmarkManager(context) }
    val bookmarks by bookmarkManager.bookmarks.collectAsState()
    val epubBookmarks = bookmarks.filter { it.epubUri == epubUri.toString() }

    val chapters = extractResult.chapters
    val contentDir = extractResult.contentDir
    val opfDir = extractResult.opfDir

    logDebug?.invoke("[EPUB] 打开 $zipFileName")
    logDebug?.invoke("[EPUB] 章节数=${chapters.size}")
    logDebug?.invoke("[EPUB] contentDir=${contentDir.absolutePath}")

    // 恢复阅读进度
    LaunchedEffect(Unit) {
        val progress = bookmarkManager.loadProgress(epubUri.toString())
        if (progress != null) {
            currentChapterIndex = progress.chapterIndex
            currentScrollRatio = progress.scrollRatio
            logDebug?.invoke("[EPUB] 恢复进度: 章节${progress.chapterIndex}, 比例${progress.scrollRatio}")
        }
    }

    // 保存阅读进度（章节变化时）
    LaunchedEffect(currentChapterIndex) {
        val progress = EpubReadingProgress(
            epubUri = epubUri.toString(),
            epubFileName = zipFileName,
            chapterIndex = currentChapterIndex,
            chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: "",
            scrollPosition = 0,
            scrollRatio = currentScrollRatio,
            lastReadTime = System.currentTimeMillis()
        )
        bookmarkManager.saveProgress(progress)
        logDebug?.invoke("[EPUB] 保存进度: 章节$currentChapterIndex")
    }

    val currentChapter = chapters.getOrNull(currentChapterIndex)
    val chapterFile = if (currentChapter != null) {
        getEpubChapterFile(extractResult, currentChapter)
    } else null

    LaunchedEffect(currentChapterIndex) {
        regexFindUiState = RegexFindUiState()
    }

    // 跳转到指定章节
    fun goToChapter(index: Int) {
        if (index in chapters.indices) {
            currentChapterIndex = index
            currentScrollRatio = 0f
            showToc = false
        }
    }

    // 跳转到收藏位置
    fun goToBookmark(bookmark: EpubBookmark) {
        currentChapterIndex = bookmark.chapterIndex
        currentScrollRatio = bookmark.scrollRatio
        showBookmarks = false
    }

    // 添加收藏
    fun addBookmark(note: String = "") {
        val bookmark = EpubBookmark(
            id = bookmarkManager.generateId(),
            epubUri = epubUri.toString(),
            epubFileName = zipFileName,
            chapterIndex = currentChapterIndex,
            chapterTitle = currentChapter?.title ?: "",
            scrollPosition = 0,
            scrollRatio = currentScrollRatio,
            note = note,
            createTime = System.currentTimeMillis()
        )
        if (bookmarkManager.addBookmark(bookmark)) {
            Toast.makeText(context, "已添加收藏", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "该位置已收藏", Toast.LENGTH_SHORT).show()
        }
        showAddBookmark = false
    }

    BackHandler {
        when {
            showToc -> showToc = false
            showBookmarks -> showBookmarks = false
            showAddBookmark -> showAddBookmark = false
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

    // 目录对话框
    if (showToc) {
        AlertDialog(
            onDismissRequest = { showToc = false },
            title = { Text("目录 - ${extractResult.bookInfo.title}") },
            text = {
                LazyColumn {
                    items(chapters.size) { index ->
                        val chapter = chapters[index]
                        TextButton(
                            onClick = { goToChapter(index) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = chapter.title ?: chapter.href.substringBeforeLast("."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (index == currentChapterIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
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
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = { Text("收藏夹 - $zipFileName") },
            text = {
                if (epubBookmarks.isEmpty()) {
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
                            "点击顶部收藏按钮可添加当前位置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(epubBookmarks.size) { index ->
                            val bookmark = epubBookmarks[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                onClick = { goToBookmark(bookmark) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "第${bookmark.chapterIndex + 1}章",
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
                                        text = bookmark.chapterTitle.ifBlank { "未命名章节" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (bookmark.note.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = bookmark.note,
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
                                            editingBookmark = bookmark
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("编辑")
                                        }
                                        TextButton(onClick = {
                                            bookmarkManager.removeBookmark(bookmark.id)
                                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
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
            },
            confirmButton = {
                TextButton(onClick = { showBookmarks = false }) { Text("关闭") }
            }
        )
    }

    // 添加收藏对话框
    if (showAddBookmark) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddBookmark = false },
            title = { Text("添加收藏") },
            text = {
                Column {
                    Text(
                        "当前位置：第${currentChapterIndex + 1}章 - ${currentChapter?.title ?: ""}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("备注（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { addBookmark(noteText) }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmark = false }) { Text("取消") }
            }
        )
    }

    // 编辑收藏对话框
    editingBookmark?.let { bookmark ->
        var noteText by remember { mutableStateOf(bookmark.note) }
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text("编辑收藏") },
            text = {
                Column {
                    Text(
                        "位置：第${bookmark.chapterIndex + 1}章 - ${bookmark.chapterTitle}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    bookmarkManager.updateBookmarkNote(bookmark.id, noteText)
                    Toast.makeText(context, "已更新", Toast.LENGTH_SHORT).show()
                    editingBookmark = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) { Text("取消") }
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
                    WebViewRegexFindAction(enabled = webViewRef.value != null) {
                        showFindDialog = true
                    }
                    // 目录按钮
                    IconButton(onClick = { showToc = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "目录")
                    }
                    // 收藏按钮
                    IconButton(onClick = { showAddBookmark = true }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "添加收藏")
                    }
                    // 收藏夹按钮
                    IconButton(onClick = { showBookmarks = true }) {
                        BadgedBox(
                            badge = {
                                if (epubBookmarks.isNotEmpty()) {
                                    Badge { Text("${epubBookmarks.size}", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Bookmarks, contentDescription = "收藏夹")
                        }
                    }
                    // 缩小
                    IconButton(onClick = {
                        scalePercent = maxOf(50, scalePercent - 25)
                        webViewRef.value?.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                    }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "缩小")
                    }
                    // 放大
                    IconButton(onClick = {
                        scalePercent = minOf(200, scalePercent + 25)
                        webViewRef.value?.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
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
            if (chapters.size > 1) {
                androidx.compose.material3.BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 上一章
                        TextButton(
                            onClick = {
                                if (currentChapterIndex > 0) {
                                    currentChapterIndex--
                                }
                            },
                            enabled = currentChapterIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一章")
                            Spacer(Modifier.width(4.dp))
                            Text("上一章")
                        }

                        // 页码显示
                        Text(
                            "${currentChapterIndex + 1} / ${chapters.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 下一章
                        TextButton(
                            onClick = {
                                if (currentChapterIndex < chapters.size - 1) {
                                    currentChapterIndex++
                                }
                            },
                            enabled = currentChapterIndex < chapters.size - 1
                        ) {
                            Text("下一章")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一章")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (chapters.size > 1) 56.dp else 0.dp)
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

                    val chapterFileForUpdate = chapterFile
                    val chaptersSize = chapters.size
                    val onChapterChanged: (Int) -> Unit = { newIndex ->
                        currentChapterIndex = newIndex
                    }
                    AndroidView(
                        factory = { ctx ->
                            GestureWebView(ctx, chaptersSize, onChapterChanged).apply {
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
                                }
                                webViewRef.value = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webView ->
                            webViewRef.value = webView
                            // 更新当前章节索引给GestureWebView
                            webView.currentChapterIndex = currentChapterIndex
                            try {
                                val htmlContent = chapterFileForUpdate.readText()
                                // 注入基础样式
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
                                        </style>
                                    </head>
                                    <body>$htmlContent</body>
                                    </html>
                                """.trimIndent()
                                val loadKey = "$baseUrl|${chapterFileForUpdate.absolutePath}|${styledHtml.hashCode()}"
                                if (webView.tag != loadKey) {
                                    webView.tag = loadKey
                                    webView.loadDataWithBaseURL(baseUrl, styledHtml, "text/html", "UTF-8", null)
                                }
                                webView.evaluateJavascript("document.body.style.zoom = ${scalePercent / 100.0}", null)
                            } catch (e: Exception) {
                                Log.e(EPUB_DEBUG, "加载章节失败", e)
                            }
                        }
                    )
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

/** 支持双击切换章节的WebView：左侧双击上一章，右侧双击下一章 */
private class GestureWebView(
    context: Context,
    private val totalChapters: Int,
    private val onChapterChange: (Int) -> Unit
) : WebView(context) {
    var currentChapterIndex: Int = 0
    private var lastClickTime: Long = 0
    private var lastClickX: Float = 0f
    private val doubleClickTimeout: Long = 300 // 双击时间阈值（毫秒）
    private val edgeZoneRatio = 0.3f // 左右边缘区域占比（30%）

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val width = width.toFloat()
            val leftZone = width * edgeZoneRatio
            val rightZone = width * (1 - edgeZoneRatio)

            val newChapter = when {
                e.x < leftZone -> {
                    // 左侧双击 -> 上一章
                    maxOf(currentChapterIndex - 1, 0)
                }
                e.x > rightZone -> {
                    // 右侧双击 -> 下一章
                    minOf(currentChapterIndex + 1, totalChapters - 1)
                }
                else -> {
                    // 中间区域，不切换
                    return false
                }
            }

            if (newChapter != currentChapterIndex) {
                Handler(Looper.getMainLooper()).post {
                    onChapterChange(newChapter)
                }
                return true
            }
            return false
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 先让GestureDetector处理双击检测
        if (gestureDetector.onTouchEvent(event)) {
            return true
        }
        return super.onTouchEvent(event)
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }

    var pageCount by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageLoading by remember { mutableStateOf(false) }
    var fitToWidthZoom by remember { mutableStateOf<Float?>(null) } // 自适应宽度的缩放比例
    var zoom by remember { mutableStateOf(1f) } // 用户调整的缩放（相对于自适应）
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var pageInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) } // 原始页面宽高
    // 缓存最近渲染的页面
    val pageCache = remember { mutableMapOf<Int, Bitmap>() }

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

    BackHandler { onBack() }

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
                        modifier = Modifier.weight(1f),
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
                    // 使用 Box 包裹并添加点击检测
                    val boxWidth = with(LocalDensity.current) { currentPageBitmap!!.width.toDp() }
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
                                detectTapGestures { offset ->
                                    handleTap(offset.x, boxSizePx.width)
                                }
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
                                modifier = Modifier
                                    .wrapContentSize()
                                    .pointerInput(Unit) {
                                        // 长按检测 - 显示提示（PDF作为位图不支持文字选择）
                                        detectTapGestures(
                                            onLongPress = {
                                                Toast.makeText(
                                                    context,
                                                    "提示：PDF 页面渲染为图像，暂不支持文字选择。\n如需选择文字，请使用专业 PDF 阅读器。",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        )
                                    }
                            )
                        }
                        if (pageLoading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
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
