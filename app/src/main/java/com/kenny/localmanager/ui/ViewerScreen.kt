package com.kenny.localmanager.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.readBytesFromOffset
import com.kenny.localmanager.file.writeBytesAtOffset
import com.kenny.localmanager.file.writeBytesFull
import com.kenny.localmanager.gpg.GpgHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.ByteArrayOutputStream
import android.widget.Toast

private const val MAX_PREVIEW_BYTES = 4096
private const val MAX_MARKDOWN_BYTES = 512 * 1024
private const val PAGE_SIZE = 4096
private const val MAX_TEXT_EDIT_BYTES = 512 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    fileUri: String,
    fileName: String,
    isEncrypted: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isMarkdown = remember(fileName) { fileName.endsWith(".md", ignoreCase = true) }
    var viewMode by remember(fileName) {
        mutableStateOf(if (fileName.endsWith(".md", ignoreCase = true)) 2 else 0)
    } // 0 = text, 1 = hex, 2 = markdown(仅 .md)
    var bytesState by remember { mutableStateOf<ByteArray?>(null) }
    var mdContent by remember { mutableStateOf<String?>(null) }
    var mdLoadError by remember { mutableStateOf<String?>(null) }
    var mdLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    // 编辑模式状态
    var isEditMode by remember { mutableStateOf(false) }
    var textEditContent by remember { mutableStateOf<String?>(null) }
    var textEditLoading by remember { mutableStateOf(false) }
    var hexPageIndex by remember { mutableStateOf(0) }
    var hexPageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var hexPageLoading by remember { mutableStateOf(false) }
    var fileSize by remember { mutableStateOf(0L) }
    var saveInProgress by remember { mutableStateOf(false) }

    val uri = remember(fileUri) { Uri.parse(fileUri) }
    val canEdit = !isEncrypted

    // 初始加载 / 刷新
    LaunchedEffect(fileUri, isEncrypted, refreshKey) {
        if (isEditMode) return@LaunchedEffect
        bytesState = null
        loadError = null
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            cr.openInputStreamSafe(uri)?.use { raw ->
                val bytes = if (isEncrypted) {
                    GpgHelper.decryptStream(raw) ?: run {
                        loadError = "解密失败或需要密码"
                        return@withContext
                    }
                } else {
                    raw.readBytes(MAX_PREVIEW_BYTES)
                }
                bytesState = bytes
            } ?: run { loadError = "无法打开文件" }
        }
    }

    // 进入文本编辑时加载全文
    LaunchedEffect(isEditMode, viewMode) {
        if (!isEditMode || viewMode != 0 || isEncrypted) return@LaunchedEffect
        textEditLoading = true
        textEditContent = null
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            cr.openInputStreamSafe(uri)?.use { raw ->
                val bytes = raw.readBytes(MAX_TEXT_EDIT_BYTES)
                val decoded = bytes.decodeToString()
                val trimmed = decoded.dropLastWhile { it == '\uFFFD' }
                textEditContent = trimmed
            }
        }
        textEditLoading = false
    }

    // 进入十六进制编辑时获取文件大小；切换页时加载该页
    LaunchedEffect(isEditMode, viewMode, hexPageIndex) {
        if (!isEditMode || viewMode != 1 || isEncrypted) return@LaunchedEffect
        hexPageLoading = true
        withContext(Dispatchers.IO) {
            val doc = DocumentFile.fromSingleUri(context, uri)
            val size = doc?.length() ?: 0L
            fileSize = size
            val offset = hexPageIndex * PAGE_SIZE.toLong()
            if (offset < size) {
                val cr = context.contentResolver
                val page = cr.readBytesFromOffset(uri, offset, PAGE_SIZE) ?: byteArrayOf()
                hexPageBytes = page
            } else {
                hexPageBytes = byteArrayOf()
            }
        }
        hexPageLoading = false
    }

    // 进入 Markdown 渲染时加载全文
    LaunchedEffect(fileUri, isEncrypted, viewMode, refreshKey) {
        if (viewMode != 2 || !isMarkdown) return@LaunchedEffect
        mdLoading = true
        mdContent = null
        mdLoadError = null
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            cr.openInputStreamSafe(uri)?.use { raw ->
                val bytes = if (isEncrypted) {
                    GpgHelper.decryptStream(raw) ?: run {
                        mdLoadError = "解密失败或需要密码"
                        return@withContext
                    }
                } else {
                    raw.readBytes(MAX_MARKDOWN_BYTES)
                }
                val decoded = bytes.decodeToString()
                val trimmed = decoded.dropLastWhile { it == '\uFFFD' }
                mdContent = trimmed
            } ?: run { mdLoadError = "无法打开文件" }
        }
        mdLoading = false
    }

    val content: Result<String> = remember(bytesState) {
        bytesState?.let { raw ->
            val decoded = raw.decodeToString()
            val trimmed = decoded.dropLastWhile { it == '\uFFFD' }
            Result.success(trimmed)
        }
            ?: loadError?.let { Result.failure<String>(IllegalStateException(it)) }
            ?: Result.failure(IllegalStateException("加载中…"))
    }
    val hexLines = remember(bytesState) { bytesState?.toHexLines() ?: emptyList() }

    val totalHexPages = if (fileSize > 0) ((fileSize + PAGE_SIZE - 1) / PAGE_SIZE).toInt() else 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(fileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isEditMode) {
                        TextButton(
                            onClick = {
                                isEditMode = false
                                textEditContent = null
                                hexPageBytes = null
                            }
                        ) { Text("放弃") }
                        TextButton(
                            onClick = {
                                saveInProgress = true
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        if (viewMode == 0) {
                                            val text = textEditContent ?: ""
                                            context.contentResolver.writeBytesFull(uri, text.toByteArray(Charsets.UTF_8))
                                        } else {
                                            val page = hexPageBytes ?: return@withContext false
                                            writeBytesAtOffset(context, uri, hexPageIndex * PAGE_SIZE.toLong(), page)
                                        }
                                    }
                                    saveInProgress = false
                                    if (ok) {
                                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                        isEditMode = false
                                        textEditContent = null
                                        hexPageBytes = null
                                        refreshKey++
                                        bytesState = null
                                    } else {
                                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text(if (saveInProgress) "保存中…" else "保存") }
                    } else {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(end = 4.dp)) {
                            val count = if (isMarkdown) 3 else 2
                            SegmentedButton(
                                selected = viewMode == 0,
                                onClick = { viewMode = 0 },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = count),
                                icon = {}
                            ) { Text("文本") }
                            SegmentedButton(
                                selected = viewMode == 1,
                                onClick = { viewMode = 1 },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = count),
                                icon = {}
                            ) { Text("十六进制") }
                            if (isMarkdown) {
                                SegmentedButton(
                                    selected = viewMode == 2,
                                    onClick = { viewMode = 2 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = count),
                                    icon = {}
                                ) { Text("渲染") }
                            }
                        }
                        if (canEdit && viewMode != 2) {
                            IconButton(onClick = { isEditMode = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑")
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
                isEditMode && viewMode == 0 -> {
                    if (textEditLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val text = textEditContent ?: ""
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { new ->
                                    // 修复：部分 IME 在回车时会把当前行再发一遍，导致出现两行相同内容
                                    val lastLine = text.lines().lastOrNull().orEmpty()
                                    textEditContent = if (lastLine.isNotEmpty() && new == text + "\n" + lastLine) {
                                        text + "\n"
                                    } else {
                                        new
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                maxLines = Int.MAX_VALUE,
                                singleLine = false
                            )
                            if (text.isEmpty()) {
                                Text(
                                    "（空文件）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                isEditMode && viewMode == 1 -> {
                    if (hexPageLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("页（每页 ${PAGE_SIZE} 字节）", style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (hexPageIndex > 0) hexPageIndex-- },
                                        enabled = hexPageIndex > 0
                                    ) { Text("◀") }
                                    Text(
                                        " ${hexPageIndex + 1} / $totalHexPages ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    IconButton(
                                        onClick = { if (hexPageIndex < totalHexPages - 1) hexPageIndex++ },
                                        enabled = hexPageIndex < totalHexPages - 1
                                    ) { Text("▶") }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val page = hexPageBytes ?: byteArrayOf()
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(16),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(page.toList()) { index: Int, byteValue: Byte ->
                                    HexByteCell(
                                        value = byteValue,
                                        onValueChange = { newByte ->
                                            val copy = (hexPageBytes ?: byteArrayOf()).copyOf()
                                            if (copy.size > index) copy[index] = newByte
                                            hexPageBytes = copy
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                viewMode == 2 && isMarkdown -> {
                    if (mdLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else when {
                        mdLoadError != null -> Text(
                            mdLoadError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        mdContent != null -> MarkdownRenderView(
                            markdown = mdContent!!,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                        else -> {}
                    }
                }
                viewMode == 0 -> {
                    content.fold(
                        onSuccess = { text ->
                            SelectionContainer {
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (bytesState != null && bytesState!!.size >= MAX_PREVIEW_BYTES) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "（仅显示前 $MAX_PREVIEW_BYTES 字节）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        onFailure = { e ->
                            Text(
                                e.message ?: "无法加载",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    )
                }
                viewMode == 1 -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        hexLines.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MarkdownRenderView(
    markdown: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color
) {
    val bgHex = "#%02x%02x%02x".format(
        (backgroundColor.red * 255).toInt(),
        (backgroundColor.green * 255).toInt(),
        (backgroundColor.blue * 255).toInt()
    )
    val fgHex = "#%02x%02x%02x".format(
        (textColor.red * 255).toInt(),
        (textColor.green * 255).toInt(),
        (textColor.blue * 255).toInt()
    )
    val html = remember(markdown, bgHex, fgHex) {
        val parser = Parser.builder().build()
        val document = parser.parse(markdown)
        val renderer = HtmlRenderer.builder().build()
        val bodyHtml = renderer.render(document)
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
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
        <body>$bodyHtml</body>
        </html>
        """.trimIndent()
    }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                webViewClient = WebViewClient()
                settings.domStorageEnabled = false
                settings.javaScriptEnabled = false
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun HexByteCell(
    value: Byte,
    onValueChange: (Byte) -> Unit
) {
    var text by remember(value) { mutableStateOf("%02X".format(value.toInt() and 0xFF)) }
    BasicTextField(
        value = text,
        onValueChange = { new ->
            val filtered = new.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(2)
            text = filtered
            if (filtered.length == 2) {
                val intVal = filtered.toInt(16)
                onValueChange(intVal.toByte())
            }
        },
        modifier = Modifier
            .width(32.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        singleLine = true,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                inner()
            }
        }
    )
}

/** 每行 16 字节：左侧十六进制，右侧 ASCII（不可见字符显示为点）. */
private fun ByteArray.toHexLines(): List<String> {
    return toList().chunked(16).map { chunk: List<Byte> ->
        val hex = chunk.joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }
        val ascii = chunk.map { b -> if (b.toInt() in 32..126) b.toInt().toChar() else '.' }.joinToString("")
        "%-48s  %s".format(hex, ascii)
    }
}

private fun java.io.InputStream.readBytes(maxLen: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    var n = 0
    while (total < maxLen && read(buf).also { n = it } != -1) {
        out.write(buf, 0, minOf(n, maxLen - total))
        total += n
    }
    return out.toByteArray()
}
