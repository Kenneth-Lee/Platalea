package com.kenny.localmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.writeBytesFull
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings

const val QUICK_NOTE_FILE_NAME: String = ".lm.note.md"
const val QUICK_NOTE_GPG_FILE_NAME: String = ".lm.note.md.gpg"

private const val QUICK_NOTE_SECTION_TITLE = "思想火花"
private const val QUICK_NOTE_SECTION_HEADING = "## 思想火花"

data class QuickNoteFileInfo(
    val uri: Uri,
    val fileName: String,
    val isEncrypted: Boolean
)

data class QuickNoteEntry(
    val id: Long,
    val category: String?,
    val text: String,
    val checked: Boolean,
    val deleted: Boolean
)

data class QuickNoteLoadedData(
    val fileInfo: QuickNoteFileInfo,
    val rawText: String,
    val entries: List<QuickNoteEntry>,
    val ignoredSectionContent: String = "",
    val sourcePassword: String? = null
)

sealed class QuickNoteOpenResult {
    data class Success(val data: QuickNoteLoadedData) : QuickNoteOpenResult()
    data object RequiresPassword : QuickNoteOpenResult()
    data class Error(val message: String) : QuickNoteOpenResult()
}

private data class QuickNoteSectionRange(val startLine: Int, val endLine: Int)

private data class QuickNoteEditorState(
    val editingId: Long? = null,
    val category: String = "",
    val text: String = "",
    val checked: Boolean = false,
    val deleted: Boolean = false
)

private data class QuickNoteCategoryEditState(
    val originalCategory: String,
    val replacementCategory: String = originalCategory
)

private data class QuickNoteParseResult(
    val entries: List<QuickNoteEntry>,
    val ignoredSectionContent: String
)

private val QUICK_NOTE_SECTION_BOUNDARY_REGEX = Regex("^#{1,2}\\s+.+$")

private suspend fun readQuickNoteRawText(
    context: Context,
    fileInfo: QuickNoteFileInfo,
    password: String? = null
): Result<String> {
    val plainBytes = if (fileInfo.isEncrypted) {
        val secretKeys = loadSecretKeyRings(context)
            ?: return Result.failure(IllegalStateException("未找到默认私钥，请先生成密钥对"))
        val passphrase = password?.toCharArray()
            ?: return Result.failure(IllegalStateException("缺少密钥密码，无法读取加密笔记"))
        val decrypted = context.contentResolver.openInputStreamSafe(fileInfo.uri)?.use { input ->
            GpgHelper.decryptWithSecretKey(input, secretKeys, passphrase) { }
        }
        decrypted ?: return Result.failure(IllegalStateException("解密失败，请检查密钥密码"))
    } else {
        context.contentResolver.openInputStreamSafe(fileInfo.uri)?.use { it.readBytes() }
            ?: return Result.failure(IllegalStateException("无法读取快速笔记文件"))
    }
    return Result.success(String(plainBytes, Charsets.UTF_8))
}

suspend fun openQuickNoteData(
    context: Context,
    rootUriString: String,
    password: String? = null
): QuickNoteOpenResult {
    val rootUri = Uri.parse(rootUriString)
    val fileInfo = resolveQuickNoteFile(context, rootUri)
        ?: return QuickNoteOpenResult.Error("无法创建或定位快速笔记文件")
    if (fileInfo.isEncrypted && password == null) {
        return QuickNoteOpenResult.RequiresPassword
    }
    val rawText = readQuickNoteRawText(context, fileInfo, password).getOrElse {
        return QuickNoteOpenResult.Error(it.message ?: "无法读取快速笔记文件")
    }
    val parsed = parseQuickNoteSectionContent(rawText)
    return QuickNoteOpenResult.Success(
        QuickNoteLoadedData(
            fileInfo = fileInfo,
            rawText = rawText,
            entries = parsed.entries,
            ignoredSectionContent = parsed.ignoredSectionContent,
            sourcePassword = password
        )
    )
}

suspend fun saveQuickNoteData(
    context: Context,
    currentData: QuickNoteLoadedData,
    entries: List<QuickNoteEntry>
): Result<QuickNoteLoadedData> {
    val latestRawText = readQuickNoteRawText(context, currentData.fileInfo, currentData.sourcePassword)
        .getOrElse { return Result.failure(it) }
    val mergedText = mergeQuickNoteSection(latestRawText, entries)
    val outBytes = mergedText.toByteArray(Charsets.UTF_8)
    val ok = if (currentData.fileInfo.isEncrypted) {
        val secretKeys = loadSecretKeyRings(context)
            ?: return Result.failure(IllegalStateException("未找到默认密钥，请先生成密钥对"))
        val defaultKeyId = secretKeys.iterator().asSequence().firstOrNull()?.publicKey?.keyID
            ?: return Result.failure(IllegalStateException("未找到默认密钥"))
        val publicKeys = loadPublicKeyRings(context)
        val publicKeyRing = findPublicKeyRing(publicKeys, defaultKeyId)
            ?: return Result.failure(IllegalStateException("未找到默认公钥"))
        val encrypted = GpgHelper.encryptWithPublicKey(outBytes, publicKeyRing, currentData.fileInfo.fileName)
            ?: return Result.failure(IllegalStateException("快速笔记加密失败"))
        context.contentResolver.writeBytesFull(currentData.fileInfo.uri, encrypted)
    } else {
        context.contentResolver.writeBytesFull(currentData.fileInfo.uri, outBytes)
    }
    return if (ok) {
        Result.success(currentData.copy(rawText = mergedText, entries = entries))
    } else {
        Result.failure(IllegalStateException("快速笔记保存失败"))
    }
}

private fun resolveQuickNoteFile(context: Context, rootUri: Uri): QuickNoteFileInfo? {
    val gpgUri = findChildByName(context, rootUri, QUICK_NOTE_GPG_FILE_NAME)
    if (gpgUri != null) {
        return QuickNoteFileInfo(gpgUri, QUICK_NOTE_GPG_FILE_NAME, true)
    }
    val plainUri = findChildByName(context, rootUri, QUICK_NOTE_FILE_NAME)
    if (plainUri != null) {
        return QuickNoteFileInfo(plainUri, QUICK_NOTE_FILE_NAME, false)
    }
    val created = createFileWithBytes(
        context = context,
        targetParentUri = rootUri,
        treeUri = rootUri,
        fileName = QUICK_NOTE_FILE_NAME,
        mimeType = "text/markdown",
        bytes = ByteArray(0)
    )
    if (!created) return null
    val createdUri = findChildByName(context, rootUri, QUICK_NOTE_FILE_NAME) ?: return null
    return QuickNoteFileInfo(createdUri, QUICK_NOTE_FILE_NAME, false)
}

private fun parseQuickNoteSectionContent(fullText: String): QuickNoteParseResult {
    val sectionLines = getQuickNoteSectionLines(fullText)
        ?: return QuickNoteParseResult(entries = emptyList(), ignoredSectionContent = "")
    val entries = mutableListOf<QuickNoteEntry>()
    val ignoredLines = mutableListOf<String>()
    var currentCategory: String? = null
    var currentChecked = false
    var currentLines: MutableList<String>? = null
    var nextId = 1L

    fun flushCurrent() {
        val lines = currentLines ?: return
        val joined = lines.joinToString("\n")
        val (content, deleted) = unwrapDeletedText(joined)
        entries += QuickNoteEntry(
            id = nextId++,
            category = currentCategory?.takeIf { it.isNotBlank() },
            text = content,
            checked = currentChecked,
            deleted = deleted
        )
        currentLines = null
    }

    for (line in sectionLines) {
        val trimmed = line.trimEnd()
        when {
            trimmed.trim() == QUICK_NOTE_SECTION_HEADING -> Unit
            trimmed.isBlank() -> Unit
            trimmed.trimStart().startsWith("### ") -> {
                flushCurrent()
                currentCategory = trimmed.trimStart().removePrefix("### ").trim()
            }
            QUICK_NOTE_ENTRY_REGEX.matches(trimmed.trimStart()) -> {
                flushCurrent()
                val match = QUICK_NOTE_ENTRY_REGEX.matchEntire(trimmed.trimStart()) ?: continue
                currentChecked = match.groupValues[1].equals("x", ignoreCase = true)
                currentLines = mutableListOf(match.groupValues[2])
            }
            currentLines != null && (line.startsWith("  ") || line.startsWith("\t")) -> {
                currentLines?.add(line.removePrefix("  ").removePrefix("\t"))
            }
            else -> ignoredLines += line
        }
    }
    flushCurrent()
    return QuickNoteParseResult(
        entries = entries,
        ignoredSectionContent = ignoredLines.joinToString("\n").trimEnd()
    )
}

fun parseQuickNoteEntries(fullText: String): List<QuickNoteEntry> =
    parseQuickNoteSectionContent(fullText).entries

private fun mergeQuickNoteSection(fullText: String, entries: List<QuickNoteEntry>): String {
    val normalized = fullText.replace("\r\n", "\n")
    val sectionText = buildQuickNoteSection(entries)
    val range = findQuickNoteSectionRange(normalized)
    if (range == null) {
        return if (normalized.isBlank()) {
            sectionText + "\n"
        } else {
            normalized.trimEnd() + "\n\n" + sectionText + "\n"
        }
    }
    val lines = normalized.split("\n")
    val before = lines.subList(0, range.startLine).joinToString("\n").trimEnd()
    val after = lines.subList(range.endLine, lines.size).joinToString("\n").trimStart('\n')
    return buildString {
        if (before.isNotEmpty()) {
            append(before)
            append("\n\n")
        }
        append(sectionText)
        if (after.isNotBlank()) {
            append("\n\n")
            append(after)
        } else {
            append("\n")
        }
    }
}

private fun buildQuickNoteSection(entries: List<QuickNoteEntry>): String {
    val orderedCategories = buildQuickNoteCategoryOrder(entries)
    return buildString {
        append(QUICK_NOTE_SECTION_HEADING)
        if (entries.isNotEmpty()) append("\n\n")
        orderedCategories.forEachIndexed { index, category ->
            if (category != null) {
                append("### ")
                append(category)
                append("\n")
            }
            entries.filter { normalizeQuickNoteCategory(it.category) == category }.forEach { entry ->
                append(serializeQuickNoteEntry(entry))
                append("\n")
            }
            if (index != orderedCategories.lastIndex) append("\n")
        }
    }.trimEnd()
}

private fun serializeQuickNoteEntry(entry: QuickNoteEntry): String {
    val contentLines = entry.text.replace("\r\n", "\n").split("\n").toMutableList()
    if (contentLines.isEmpty()) contentLines += ""
    if (entry.deleted) {
        if (contentLines.size == 1) {
            contentLines[0] = "~~${contentLines[0]}~~"
        } else {
            contentLines[0] = "~~${contentLines.first()}"
            val lastIndex = contentLines.lastIndex
            contentLines[lastIndex] = "${contentLines[lastIndex]}~~"
        }
    }
    return buildString {
        append("* [")
        append(if (entry.checked) "x" else " ")
        append("] ")
        append(contentLines.first())
        contentLines.drop(1).forEach { line ->
            append("\n  ")
            append(line)
        }
    }
}

private fun getQuickNoteSectionLines(fullText: String): List<String>? {
    val normalized = fullText.replace("\r\n", "\n")
    val range = findQuickNoteSectionRange(normalized) ?: return null
    val lines = normalized.split("\n")
    return lines.subList(range.startLine, range.endLine)
}

private fun findQuickNoteSectionRange(fullText: String): QuickNoteSectionRange? {
    val lines = fullText.replace("\r\n", "\n").split("\n")
    var startIndex = -1
    for (index in lines.indices) {
        if (lines[index].trim() == QUICK_NOTE_SECTION_HEADING) {
            startIndex = index
            break
        }
    }
    if (startIndex < 0) return null
    var endIndex = lines.size
    for (index in (startIndex + 1) until lines.size) {
        val trimmed = lines[index].trimStart()
        if (QUICK_NOTE_SECTION_BOUNDARY_REGEX.matches(trimmed) && !trimmed.startsWith("### ")) {
            endIndex = index
            break
        }
    }
    return QuickNoteSectionRange(startLine = startIndex, endLine = endIndex)
}

private fun unwrapDeletedText(text: String): Pair<String, Boolean> {
    val lines = text.split("\n").toMutableList()
    if (lines.isEmpty()) return "" to false
    return when {
        lines.size == 1 && lines[0].startsWith("~~") && lines[0].endsWith("~~") && lines[0].length >= 4 -> {
            lines[0].removePrefix("~~").removeSuffix("~~") to true
        }
        lines.size > 1 && lines.first().startsWith("~~") && lines.last().endsWith("~~") -> {
            lines[0] = lines.first().removePrefix("~~")
            val lastIndex = lines.lastIndex
            lines[lastIndex] = lines.last().removeSuffix("~~")
            lines.joinToString("\n") to true
        }
        else -> text to false
    }
}

private fun buildQuickNoteCategoryOrder(entries: List<QuickNoteEntry>): List<String?> {
    val ordered = mutableListOf<String?>()
    entries.forEach { entry ->
        val category = normalizeQuickNoteCategory(entry.category)
        if (!ordered.contains(category)) ordered += category
    }
    return buildList {
        if (ordered.contains(null)) add(null)
        addAll(ordered.filterNotNull())
    }
}

private fun normalizeQuickNoteCategory(category: String?): String? =
    category?.trim()?.takeIf { it.isNotEmpty() }

private fun quickNoteCategoryNames(entries: List<QuickNoteEntry>): List<String> =
    buildQuickNoteCategoryOrder(entries).filterNotNull()

private fun renameQuickNoteCategory(entries: List<QuickNoteEntry>, from: String, to: String?): List<QuickNoteEntry> {
    val normalizedFrom = normalizeQuickNoteCategory(from) ?: return entries
    val normalizedTo = normalizeQuickNoteCategory(to)
    return entries.map { entry ->
        if (normalizeQuickNoteCategory(entry.category) == normalizedFrom) entry.copy(category = normalizedTo) else entry
    }
}

private fun nextQuickNoteEntryId(entries: List<QuickNoteEntry>): Long =
    (entries.maxOfOrNull { it.id } ?: 0L) + 1L

private val QUICK_NOTE_ENTRY_REGEX = Regex("^\\* \\[([xX ]?)\\]\\s?(.*)$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteScreen(
    loadedData: QuickNoteLoadedData,
    startWithAddDialog: Boolean,
    inProgress: Boolean,
    onEntriesChanged: (List<QuickNoteEntry>) -> Unit
) {
    val composeContext = LocalContext.current
    val clipboardManager = composeContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    var entries by remember(loadedData.fileInfo.uri, loadedData.rawText) { mutableStateOf(loadedData.entries) }
    var editorState by remember(loadedData.fileInfo.uri, startWithAddDialog) {
        mutableStateOf(if (startWithAddDialog) QuickNoteEditorState() else null)
    }
    var deleteConfirmEntry by remember { mutableStateOf<QuickNoteEntry?>(null) }
    var categoryEditState by remember { mutableStateOf<QuickNoteCategoryEditState?>(null) }
    var showIgnoredContentDialog by remember(loadedData.fileInfo.uri, loadedData.rawText) {
        mutableStateOf(loadedData.ignoredSectionContent.isNotBlank())
    }
    val categoryExpandedStates = remember(loadedData.fileInfo.uri) { mutableStateMapOf<String, Boolean>() }
    val existingCategories = remember(entries) { quickNoteCategoryNames(entries) }

    LaunchedEffect(entries) {
        onEntriesChanged(entries)
    }

    val groupedEntries = remember(entries) {
        buildQuickNoteCategoryOrder(entries).map { category ->
            category to entries.filter { normalizeQuickNoteCategory(it.category) == category }
        }
    }
    groupedEntries.forEach { (category, _) ->
        val key = quickNoteCategoryKey(category)
        if (key !in categoryExpandedStates) {
            categoryExpandedStates[key] = category == null
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(QUICK_NOTE_SECTION_TITLE)
                            Text(
                                if (loadedData.fileInfo.isEncrypted) "${loadedData.fileInfo.fileName} · 已加密" else loadedData.fileInfo.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { editorState = QuickNoteEditorState() }, enabled = !inProgress) {
                            Icon(Icons.Default.Add, contentDescription = "新增记录")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (inProgress) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有记录，点击右上角新增。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                groupedEntries.forEach { (category, categoryEntries) ->
                    val categoryKey = quickNoteCategoryKey(category)
                    val expanded = if (category == null) true else categoryExpandedStates[categoryKey] == true
                    if (category != null) {
                        item(key = "header-$categoryKey") {
                            QuickNoteCategoryHeader(
                                title = category,
                                count = categoryEntries.size,
                                expanded = expanded,
                                enabled = !inProgress,
                                onToggle = {
                                    categoryExpandedStates[categoryKey] = !expanded
                                },
                                onEditCategory = {
                                    categoryEditState = QuickNoteCategoryEditState(category)
                                }
                            )
                        }
                    } else {
                        item(key = "header-$categoryKey") {
                            QuickNoteStaticHeader(
                                title = "未分类",
                                count = categoryEntries.size
                            )
                        }
                    }
                    if (expanded) {
                        items(categoryEntries, key = { it.id }) { entry ->
                            QuickNoteEntryRow(
                                entry = entry,
                                enabled = !inProgress,
                                onToggleChecked = { checked ->
                                    entries = entries.map {
                                        if (it.id == entry.id) it.copy(checked = checked) else it
                                    }
                                },
                                onToggleDeleted = {
                                    entries = entries.map {
                                        if (it.id == entry.id) it.copy(deleted = !it.deleted) else it
                                    }
                                },
                                onEdit = {
                                    editorState = QuickNoteEditorState(
                                        editingId = entry.id,
                                        category = entry.category.orEmpty(),
                                        text = entry.text,
                                        checked = entry.checked,
                                        deleted = entry.deleted
                                    )
                                },
                                onDelete = {
                                    deleteConfirmEntry = entry
                                }
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    editorState?.let { state ->
        QuickNoteEditorDialog(
            state = state,
            existingCategories = existingCategories,
            inProgress = inProgress,
            onDismiss = { if (!inProgress) editorState = null },
            onConfirm = { updated ->
                val normalizedCategory = normalizeQuickNoteCategory(updated.category)
                val normalizedText = updated.text.trimEnd()
                if (normalizedText.isBlank()) {
                    Toast.makeText(
                        composeContext,
                        "内容不能为空",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@QuickNoteEditorDialog
                }
                entries = if (updated.editingId == null) {
                    val newEntry = QuickNoteEntry(
                        id = nextQuickNoteEntryId(entries),
                        category = normalizedCategory,
                        text = normalizedText,
                        checked = updated.checked,
                        deleted = updated.deleted
                    )
                    categoryExpandedStates[quickNoteCategoryKey(normalizedCategory)] = true
                    entries + newEntry
                } else {
                    categoryExpandedStates[quickNoteCategoryKey(normalizedCategory)] = true
                    entries.map { entry ->
                        if (entry.id == updated.editingId) {
                            entry.copy(
                                category = normalizedCategory,
                                text = normalizedText,
                                checked = updated.checked,
                                deleted = updated.deleted
                            )
                        } else {
                            entry
                        }
                    }
                }
                editorState = null
            }
        )
    }

    categoryEditState?.let { state ->
        QuickNoteCategoryDialog(
            state = state,
            existingCategories = existingCategories.filter { it != state.originalCategory },
            inProgress = inProgress,
            onDismiss = { if (!inProgress) categoryEditState = null },
            onConfirm = { replacementCategory ->
                entries = renameQuickNoteCategory(entries, state.originalCategory, replacementCategory)
                categoryExpandedStates.remove(quickNoteCategoryKey(state.originalCategory))
                categoryExpandedStates[quickNoteCategoryKey(normalizeQuickNoteCategory(replacementCategory))] = true
                categoryEditState = null
            }
        )
    }

    deleteConfirmEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteConfirmEntry = null },
            title = { Text("删除记录") },
            text = {
                Text(
                    "确定删除这条记录吗？\n\n${entry.text.lineSequence().firstOrNull()?.trim().orEmpty()}",
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = entry.id
                        deleteConfirmEntry = null
                        entries = entries.filterNot { it.id == targetId }
                    },
                    enabled = !inProgress
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmEntry = null }, enabled = !inProgress) {
                    Text("取消")
                }
            }
        )
    }

    if (showIgnoredContentDialog && loadedData.ignoredSectionContent.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showIgnoredContentDialog = false },
            title = { Text("发现未接管的数据") },
            text = {
                Column {
                    Text(
                        "“思想火花”章节里有一些不符合当前记录协议的文本。它们不会被读入为记录，退出功能时会被整段覆盖。请先复制保存。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = loadedData.ignoredSectionContent,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        readOnly = true,
                        enabled = false,
                        label = { Text("将被覆盖的原始内容") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager?.setPrimaryClip(
                            ClipData.newPlainText("快速笔记未接管内容", loadedData.ignoredSectionContent)
                        )
                        Toast.makeText(composeContext, "已复制未接管内容", Toast.LENGTH_SHORT).show()
                        showIgnoredContentDialog = false
                    }
                ) {
                    Text("复制并继续")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIgnoredContentDialog = false }) {
                    Text("继续")
                }
            }
        )
    }

}

@Composable
private fun QuickNoteCategoryHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onEditCategory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onEditCategory, enabled = enabled, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "修改类别", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun QuickNoteStaticHeader(
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickNoteEntryRow(
    entry: QuickNoteEntry,
    enabled: Boolean,
    onToggleChecked: (Boolean) -> Unit,
    onToggleDeleted: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = entry.checked,
                onCheckedChange = { checked -> onToggleChecked(checked) },
                enabled = enabled
            )
            Text(
                text = entry.text.lineSequence().firstOrNull()?.trim().orEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled, onClick = onEdit)
                    .padding(vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (entry.deleted) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (entry.deleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onToggleDeleted, enabled = enabled) {
                Text(if (entry.deleted) "取消标删" else "标删")
            }
            IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除记录", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun quickNoteCategoryKey(category: String?): String = category ?: "__default__"

@Composable
private fun QuickNoteEditorDialog(
    state: QuickNoteEditorState,
    existingCategories: List<String>,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (QuickNoteEditorState) -> Unit
) {
    var category by remember(state.editingId) { mutableStateOf(state.category) }
    var text by remember(state.editingId) { mutableStateOf(state.text) }
    var checked by remember(state.editingId) { mutableStateOf(state.checked) }
    var deleted by remember(state.editingId) { mutableStateOf(state.deleted) }

    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text(if (state.editingId == null) "新增记录" else "编辑记录") },
        text = {
            Column {
                QuickNoteCategoryField(
                    value = category,
                    existingCategories = existingCategories,
                    enabled = !inProgress,
                    label = "类别（留空为未分类）",
                    onValueChange = { category = it }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    enabled = !inProgress
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已完成", modifier = Modifier.weight(1f))
                    Switch(checked = checked, onCheckedChange = { checked = it }, enabled = !inProgress)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("标记删除", modifier = Modifier.weight(1f))
                    Switch(checked = deleted, onCheckedChange = { deleted = it }, enabled = !inProgress)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        QuickNoteEditorState(
                            editingId = state.editingId,
                            category = category,
                            text = text,
                            checked = checked,
                            deleted = deleted
                        )
                    )
                },
                enabled = !inProgress
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun QuickNoteCategoryDialog(
    state: QuickNoteCategoryEditState,
    existingCategories: List<String>,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var category by remember(state.originalCategory) { mutableStateOf(state.replacementCategory) }

    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("修改类别") },
        text = {
            Column {
                Text(
                    "将“${state.originalCategory}”下的所有记录一次性移动到新类别。留空表示改为未分类。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                QuickNoteCategoryField(
                    value = category,
                    existingCategories = existingCategories,
                    enabled = !inProgress,
                    label = "新类别",
                    onValueChange = { category = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(category) }, enabled = !inProgress) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun QuickNoteCategoryField(
    value: String,
    existingCategories: List<String>,
    enabled: Boolean,
    label: String,
    onValueChange: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            trailingIcon = {
                if (existingCategories.isNotEmpty()) {
                    IconButton(onClick = { showMenu = !showMenu }, enabled = enabled) {
                        Icon(Icons.Default.ExpandMore, contentDescription = "选择已有类别")
                    }
                }
            }
        )
        DropdownMenu(
            expanded = showMenu && existingCategories.isNotEmpty(),
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("未分类") },
                onClick = {
                    onValueChange("")
                    showMenu = false
                }
            )
            existingCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onValueChange(category)
                        showMenu = false
                    }
                )
            }
        }
    }
}