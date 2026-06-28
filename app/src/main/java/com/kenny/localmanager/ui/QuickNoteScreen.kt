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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.writeBytesFull
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings
import kotlinx.coroutines.launch

const val QUICK_NOTE_FILE_NAME: String = ".lm.note.md"
const val QUICK_NOTE_GPG_FILE_NAME: String = ".lm.note.md.gpg"

private val QUICK_NOTE_SECTION_HEADINGS = setOf("## 思想火花", "## Quick notes")

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
            ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_note_missing_default_secret_key)))
        val passphrase = password?.toCharArray()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_note_missing_key_password)))
        val decrypted = context.contentResolver.openInputStreamSafe(fileInfo.uri)?.use { input ->
            GpgHelper.decryptWithSecretKey(input, secretKeys, passphrase) { }
        }
        decrypted ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_note_decrypt_failed)))
    } else {
        context.contentResolver.openInputStreamSafe(fileInfo.uri)?.use { it.readBytes() }
            ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_note_read_failed)))
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
        ?: return QuickNoteOpenResult.Error(context.getString(R.string.quick_note_resolve_failed))
    if (fileInfo.isEncrypted && password == null) {
        return QuickNoteOpenResult.RequiresPassword
    }
    val rawText = readQuickNoteRawText(context, fileInfo, password).getOrElse {
        return QuickNoteOpenResult.Error(it.message ?: context.getString(R.string.quick_note_read_failed))
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
    val mergedText = mergeQuickNoteSection(context, latestRawText, entries)
    val outBytes = mergedText.toByteArray(Charsets.UTF_8)
    val ok = if (currentData.fileInfo.isEncrypted) {
        val secretKeys = loadSecretKeyRings(context)
            ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_note_missing_default_keypair)))
        val defaultKeyId = secretKeys.iterator().asSequence().firstOrNull()?.publicKey?.keyID
            ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_note_missing_default_key)))
        val publicKeys = loadPublicKeyRings(context)
        val publicKeyRing = findPublicKeyRing(publicKeys, defaultKeyId)
            ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_note_missing_default_public_key)))
        val encrypted = GpgHelper.encryptWithPublicKey(outBytes, publicKeyRing, currentData.fileInfo.fileName)
            ?: return Result.failure(IllegalStateException(context.getString(R.string.quick_note_encrypt_failed)))
        context.contentResolver.writeBytesFull(currentData.fileInfo.uri, encrypted)
    } else {
        context.contentResolver.writeBytesFull(currentData.fileInfo.uri, outBytes)
    }
    return if (ok) {
        Result.success(currentData.copy(rawText = mergedText, entries = entries))
    } else {
        Result.failure(IllegalStateException(context.getString(R.string.quick_note_save_failed)))
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
            trimmed in QUICK_NOTE_SECTION_HEADINGS -> Unit
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

private fun mergeQuickNoteSection(context: Context, fullText: String, entries: List<QuickNoteEntry>): String {
    val normalized = fullText.replace("\r\n", "\n")
    val sectionText = buildQuickNoteSection(context, entries)
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

private fun buildQuickNoteSection(context: Context, entries: List<QuickNoteEntry>): String {
    val orderedCategories = buildQuickNoteCategoryOrder(entries)
    return buildString {
        append(context.getString(R.string.quick_note_section_heading_markdown))
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
        if (lines[index].trim() in QUICK_NOTE_SECTION_HEADINGS) {
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
    prefs: Preferences,
    loadedData: QuickNoteLoadedData,
    startWithAddDialog: Boolean,
    inProgress: Boolean,
    onEntriesChanged: (List<QuickNoteEntry>) -> Unit
) {
    val composeContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = composeContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val lastUsedCategory by prefs.quickNoteLastCategory.collectAsState(initial = null)
    var entries by remember(loadedData.fileInfo.uri, loadedData.rawText) { mutableStateOf(loadedData.entries) }
    var editorState by remember(loadedData.fileInfo.uri, startWithAddDialog) {
        mutableStateOf(
            if (startWithAddDialog) QuickNoteEditorState(category = lastUsedCategory.orEmpty()) else null
        )
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
                            Text(stringResource(R.string.quick_note_title))
                            Text(
                                if (loadedData.fileInfo.isEncrypted) stringResource(R.string.quick_note_file_encrypted, loadedData.fileInfo.fileName) else loadedData.fileInfo.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editorState = QuickNoteEditorState(category = lastUsedCategory.orEmpty()) },
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.quick_note_add_record))
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
                    stringResource(R.string.quick_note_empty),
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
                                title = stringResource(R.string.quick_note_uncategorized),
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
                        composeContext.getString(R.string.quick_note_content_required),
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
                    scope.launch {
                        prefs.setQuickNoteLastCategory(normalizedCategory)
                    }
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
            title = { Text(stringResource(R.string.quick_note_delete_record_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.quick_note_delete_record_confirm,
                        entry.text.lineSequence().firstOrNull()?.trim().orEmpty()
                    ),
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
                    Text(stringResource(R.string.quick_note_delete_record_title))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmEntry = null }, enabled = !inProgress) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showIgnoredContentDialog && loadedData.ignoredSectionContent.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showIgnoredContentDialog = false },
            title = { Text(stringResource(R.string.quick_note_ignored_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.quick_note_ignored_desc),
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
                        label = { Text(stringResource(R.string.quick_note_ignored_label)) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager?.setPrimaryClip(
                            ClipData.newPlainText(composeContext.getString(R.string.quick_note_ignored_clip_label), loadedData.ignoredSectionContent)
                        )
                        Toast.makeText(composeContext, composeContext.getString(R.string.quick_note_ignored_copied), Toast.LENGTH_SHORT).show()
                        showIgnoredContentDialog = false
                    }
                ) {
                    Text(stringResource(R.string.quick_note_copy_and_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showIgnoredContentDialog = false }) {
                    Text(stringResource(R.string.common_continue))
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
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.quick_note_edit_category), modifier = Modifier.size(16.dp))
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
                Text(if (entry.deleted) stringResource(R.string.quick_note_unmark_deleted) else stringResource(R.string.quick_note_mark_deleted))
            }
            IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.quick_note_delete_record_title), modifier = Modifier.size(18.dp))
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
        title = { Text(if (state.editingId == null) stringResource(R.string.quick_note_add_record) else stringResource(R.string.quick_note_edit_record)) },
        text = {
            Column {
                QuickNoteCategoryField(
                    value = category,
                    existingCategories = existingCategories,
                    enabled = !inProgress,
                    label = stringResource(R.string.quick_note_category_label),
                    onValueChange = { category = it }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.quick_note_content_label)) },
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
                    Text(stringResource(R.string.quick_note_completed), modifier = Modifier.weight(1f))
                    Switch(checked = checked, onCheckedChange = { checked = it }, enabled = !inProgress)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.quick_note_mark_delete_label), modifier = Modifier.weight(1f))
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
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text(stringResource(R.string.common_cancel))
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
        title = { Text(stringResource(R.string.quick_note_edit_category_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.quick_note_edit_category_desc, state.originalCategory),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                QuickNoteCategoryField(
                    value = category,
                    existingCategories = existingCategories,
                    enabled = !inProgress,
                    label = stringResource(R.string.quick_note_new_category_label),
                    onValueChange = { category = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(category) }, enabled = !inProgress) {
                Text(stringResource(R.string.common_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text(stringResource(R.string.common_cancel))
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
                        Icon(Icons.Default.ExpandMore, contentDescription = stringResource(R.string.quick_note_select_existing_category))
                    }
                }
            }
        )
        DropdownMenu(
            expanded = showMenu && existingCategories.isNotEmpty(),
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_note_uncategorized)) },
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