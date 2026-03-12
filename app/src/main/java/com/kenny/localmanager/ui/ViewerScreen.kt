package com.kenny.localmanager.ui

import android.net.Uri
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import java.io.ByteArrayOutputStream
import android.widget.Toast

private const val MAX_PREVIEW_BYTES = 4096
private const val PAGE_SIZE = 4096
private const val MAX_TEXT_EDIT_BYTES = 512 * 1024

private data class TextRegexFindUiState(
    val matches: List<IntRange> = emptyList(),
    val currentIndex: Int = -1,
    val error: String? = null
) {
    val hasMatches: Boolean get() = matches.isNotEmpty()
    val count: Int get() = matches.size
}

private fun buildTextFindRegex(pattern: String): Regex {
    val slashForm = Regex("^/((?:\\\\.|[^/])*)/([a-zA-Z]*)$").matchEntire(pattern)
    if (slashForm != null) {
        val source = slashForm.groupValues[1]
        val flags = slashForm.groupValues[2]
        val options = buildSet {
            flags.forEach { flag ->
                when (flag.lowercaseChar()) {
                    'i' -> add(RegexOption.IGNORE_CASE)
                    'm' -> add(RegexOption.MULTILINE)
                    's' -> add(RegexOption.DOT_MATCHES_ALL)
                    'u', 'g' -> Unit
                    else -> throw IllegalArgumentException("不支持的正则标志: $flag")
                }
            }
        }
        return Regex(source, options)
    }
    return Regex(pattern)
}

private fun findTextRegexMatches(text: String, pattern: String): TextRegexFindUiState {
    if (pattern.isBlank()) return TextRegexFindUiState()
    return try {
        val regex = buildTextFindRegex(pattern)
        val matches = regex.findAll(text)
            .mapNotNull { match ->
                if (match.value.isEmpty()) null else match.range
            }
            .toList()
        TextRegexFindUiState(matches = matches, currentIndex = if (matches.isNotEmpty()) 0 else -1)
    } catch (e: Exception) {
        TextRegexFindUiState(error = e.message ?: "无效正则")
    }
}

private fun applyTextMatchSelection(value: TextFieldValue, range: IntRange): TextFieldValue {
    return value.copy(selection = TextRange(range.first, range.last + 1))
}

@Composable
private fun TextRegexFindDialog(
    query: String,
    result: TextRegexFindUiState,
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
                    text = "只在文本编辑模式下查找，匹配后会直接选中对应文本。",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    fileUri: String,
    fileName: String,
    isEncrypted: Boolean,
    onBack: () -> Unit,
    onOpenMarkdownView: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewMode by remember { mutableStateOf(0) } // 0 = text, 1 = hex
    var bytesState by remember { mutableStateOf<ByteArray?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    // 编辑模式状态
    var isEditMode by remember { mutableStateOf(false) }
    var textEditValue by remember { mutableStateOf<TextFieldValue?>(null) }
    var textEditLoading by remember { mutableStateOf(false) }
    var hexPageIndex by remember { mutableStateOf(0) }
    var hexPageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var hexPageLoading by remember { mutableStateOf(false) }
    var fileSize by remember { mutableStateOf(0L) }
    var saveInProgress by remember { mutableStateOf(false) }
    var showTextFindDialog by remember { mutableStateOf(false) }
    var textFindQuery by remember { mutableStateOf("") }
    var textFindState by remember { mutableStateOf(TextRegexFindUiState()) }

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
        textEditValue = null
        textFindState = TextRegexFindUiState()
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            cr.openInputStreamSafe(uri)?.use { raw ->
                val bytes = raw.readBytes(MAX_TEXT_EDIT_BYTES)
                val decoded = bytes.decodeToString()
                val trimmed = decoded.dropLastWhile { it == '\uFFFD' }
                textEditValue = TextFieldValue(trimmed)
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

    fun applyTextFindState(state: TextRegexFindUiState, targetIndex: Int = state.currentIndex) {
        textFindState = state.copy(currentIndex = targetIndex)
        val value = textEditValue ?: return
        if (state.hasMatches && targetIndex in state.matches.indices) {
            textEditValue = applyTextMatchSelection(value, state.matches[targetIndex])
        }
    }

    if (showTextFindDialog) {
        TextRegexFindDialog(
            query = textFindQuery,
            result = textFindState,
            onQueryChange = {
                textFindQuery = it
                if (it.isBlank()) textFindState = TextRegexFindUiState()
            },
            onSearch = {
                val value = textEditValue ?: return@TextRegexFindDialog
                applyTextFindState(findTextRegexMatches(value.text, textFindQuery))
            },
            onPrevious = {
                if (!textFindState.hasMatches) return@TextRegexFindDialog
                val nextIndex = if (textFindState.currentIndex <= 0) textFindState.matches.lastIndex else textFindState.currentIndex - 1
                applyTextFindState(textFindState, nextIndex)
            },
            onNext = {
                if (!textFindState.hasMatches) return@TextRegexFindDialog
                val nextIndex = if (textFindState.currentIndex >= textFindState.matches.lastIndex) 0 else textFindState.currentIndex + 1
                applyTextFindState(textFindState, nextIndex)
            },
            onClear = {
                textFindQuery = ""
                textFindState = TextRegexFindUiState()
            },
            onDismiss = { showTextFindDialog = false }
        )
    }

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
                        if (viewMode == 0) {
                            IconButton(
                                onClick = { showTextFindDialog = true },
                                enabled = !textEditLoading && textEditValue != null
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "查找")
                            }
                        }
                        TextButton(
                            onClick = {
                                isEditMode = false
                                textEditValue = null
                                hexPageBytes = null
                                textFindQuery = ""
                                textFindState = TextRegexFindUiState()
                            }
                        ) { Text("放弃") }
                        TextButton(
                            onClick = {
                                saveInProgress = true
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        if (viewMode == 0) {
                                            val text = textEditValue?.text ?: ""
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
                                        textEditValue = null
                                        hexPageBytes = null
                                        textFindQuery = ""
                                        textFindState = TextRegexFindUiState()
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
                            SegmentedButton(
                                selected = viewMode == 0,
                                onClick = { viewMode = 0 },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = {}
                            ) { Text("文本") }
                            SegmentedButton(
                                selected = viewMode == 1,
                                onClick = { viewMode = 1 },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                icon = {}
                            ) { Text("十六进制") }
                        }
                        if (onOpenMarkdownView != null) {
                            TextButton(onClick = onOpenMarkdownView) { Text("Markdown渲染") }
                        }
                        if (canEdit) {
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
                        val textValue = textEditValue ?: TextFieldValue("")
                        val text = textValue.text
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            OutlinedTextField(
                                value = textValue,
                                onValueChange = { new ->
                                    // 修复：部分 IME 在回车时会把当前行再发一遍，导致出现两行相同内容
                                    val lastLine = text.lines().lastOrNull().orEmpty()
                                    val nextText = if (lastLine.isNotEmpty() && new.text == text + "\n" + lastLine) {
                                        text + "\n"
                                    } else {
                                        new.text
                                    }
                                    textEditValue = new.copy(text = nextText)
                                    if (textFindQuery.isNotBlank()) {
                                        textFindState = findTextRegexMatches(nextText, textFindQuery)
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
