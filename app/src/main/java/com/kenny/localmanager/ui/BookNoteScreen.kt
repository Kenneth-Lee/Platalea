package com.kenny.localmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.writeBytesFull
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BOOK_NOTE_SECTION_HEADINGS = setOf("## 读书笔记", "## Book Notes")
private val BOOK_NOTE_SECTION_BOUNDARY_REGEX = Regex("^#{1,2}\\s+.+$")
private val BOOK_NOTE_CHAPTER_REGEX = Regex("^\\*\\s*(?:章节|Chapter)[：:]\\s*(.*)$", RegexOption.IGNORE_CASE)
private val BOOK_NOTE_QUOTE_REGEX = Regex("^\\*\\s*(?:引文|Quote)[：:]\\s*(.*)$", RegexOption.IGNORE_CASE)

data class BookNoteEntry(
    val id: Long,
    val bookTitle: String,
    val chapterInfo: String?,
    val quote: String?,
    val content: String,
    val chapterIndex: Int? = null,
    val chapterTitle: String? = null,
    val scrollRatio: Float? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(createdAt))
}

fun BookNoteEntry.hasLocation(): Boolean =
    chapterIndex != null || scrollRatio != null || !chapterInfo.isNullOrBlank()

fun BookNoteEntry.displayLocation(wholeBookLabel: String): String = when {
    !chapterInfo.isNullOrBlank() -> chapterInfo
    !chapterTitle.isNullOrBlank() -> chapterTitle
    else -> wholeBookLabel
}

fun BookNoteEntry.matchesBookTitle(title: String): Boolean =
    bookTitle.trim() == title.trim()

fun BookNoteEntry.matchesLocation(chapterIndex: Int, scrollRatio: Float, tolerance: Float = 0.05f): Boolean {
    val savedChapterIndex = this.chapterIndex ?: return false
    val savedScrollRatio = this.scrollRatio ?: return false
    return savedChapterIndex == chapterIndex && kotlin.math.abs(savedScrollRatio - scrollRatio) <= tolerance
}

data class BookNoteLoadedData(
    val fileInfo: QuickNoteFileInfo,
    val rawText: String,
    val entries: List<BookNoteEntry>,
    val ignoredSectionContent: String = "",
    val sourcePassword: String? = null
)

sealed class BookNoteOpenResult {
    data class Success(val data: BookNoteLoadedData) : BookNoteOpenResult()
    data object RequiresPassword : BookNoteOpenResult()
    data class Error(val message: String) : BookNoteOpenResult()
}

data class BookNoteEditorState(
    val editingId: Long? = null,
    val bookTitle: String = "",
    val chapterInfo: String = "",
    val quote: String = "",
    val content: String = "",
    val chapterIndex: Int? = null,
    val chapterTitle: String? = null,
    val scrollRatio: Float? = null,
    val createdAt: Long = System.currentTimeMillis()
)

private data class BookNoteSectionRange(val startLine: Int, val endLine: Int)

private data class BookNoteParseResult(
    val entries: List<BookNoteEntry>,
    val ignoredSectionContent: String
)

private fun extractBookNoteMetadataJson(line: String): String? {
    val trimmed = line.trim()
    val prefix = "<!-- lm-book-note "
    val suffix = "-->"
    if (!trimmed.startsWith(prefix) || !trimmed.endsWith(suffix)) return null
    return trimmed.removePrefix(prefix).removeSuffix(suffix).trim().takeIf {
        it.startsWith("{") && it.endsWith("}")
    }
}

private suspend fun readBookNoteRawText(
    context: Context,
    fileInfo: QuickNoteFileInfo,
    password: String? = null
): Result<String> {
    val plainBytes = if (fileInfo.isEncrypted) {
        val secretKeys = loadSecretKeyRings(context)
            ?: return Result.failure(IllegalStateException(context.getString(R.string.book_note_missing_default_secret_key)))
        val passphrase = password?.toCharArray()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.book_note_missing_key_password)))
        val decrypted = context.contentResolver.openInputStreamSafe(fileInfo.uri)?.use { input ->
            GpgHelper.decryptWithSecretKey(input, secretKeys, passphrase) { }
        }
        decrypted ?: return Result.failure(IllegalStateException(context.getString(R.string.book_note_decrypt_failed)))
    } else {
        context.contentResolver.openInputStreamSafe(fileInfo.uri)?.use { it.readBytes() }
            ?: return Result.failure(IllegalStateException(context.getString(R.string.book_note_read_failed)))
    }
    return Result.success(String(plainBytes, Charsets.UTF_8))
}

suspend fun openBookNoteData(
    context: Context,
    rootUriString: String,
    password: String? = null
): BookNoteOpenResult {
    val rootUri = Uri.parse(rootUriString)
    val fileInfo = resolveBookNoteFile(context, rootUri)
        ?: return BookNoteOpenResult.Error(context.getString(R.string.book_note_resolve_failed))
    if (fileInfo.isEncrypted && password == null) {
        return BookNoteOpenResult.RequiresPassword
    }
    val rawText = readBookNoteRawText(context, fileInfo, password).getOrElse {
        return BookNoteOpenResult.Error(it.message ?: context.getString(R.string.book_note_read_failed))
    }
    val parsed = parseBookNoteSectionContent(rawText)
    return BookNoteOpenResult.Success(
        BookNoteLoadedData(
            fileInfo = fileInfo,
            rawText = rawText,
            entries = parsed.entries,
            ignoredSectionContent = parsed.ignoredSectionContent,
            sourcePassword = password
        )
    )
}

suspend fun saveBookNoteData(
    context: Context,
    currentData: BookNoteLoadedData,
    entries: List<BookNoteEntry>
): Result<BookNoteLoadedData> {
    val latestRawText = readBookNoteRawText(context, currentData.fileInfo, currentData.sourcePassword)
        .getOrElse { return Result.failure(it) }
    val mergedText = mergeBookNoteSection(context, latestRawText, entries)
    val outBytes = mergedText.toByteArray(Charsets.UTF_8)
    val ok = if (currentData.fileInfo.isEncrypted) {
        val secretKeys = loadSecretKeyRings(context)
            ?: return Result.failure(IllegalStateException(context.getString(R.string.book_note_missing_default_keypair)))
        val defaultKeyId = secretKeys.iterator().asSequence().firstOrNull()?.publicKey?.keyID
            ?: return Result.failure(IllegalStateException(context.getString(R.string.book_note_missing_default_key)))
        val publicKeys = loadPublicKeyRings(context)
        val publicKeyRing = findPublicKeyRing(publicKeys, defaultKeyId)
            ?: return Result.failure(IllegalStateException(context.getString(R.string.book_note_missing_default_public_key)))
        val encrypted = GpgHelper.encryptWithPublicKey(outBytes, publicKeyRing, currentData.fileInfo.fileName)
            ?: return Result.failure(IllegalStateException(context.getString(R.string.book_note_encrypt_failed)))
        context.contentResolver.writeBytesFull(currentData.fileInfo.uri, encrypted)
    } else {
        context.contentResolver.writeBytesFull(currentData.fileInfo.uri, outBytes)
    }
    return if (ok) {
        Result.success(currentData.copy(rawText = mergedText, entries = entries))
    } else {
        Result.failure(IllegalStateException(context.getString(R.string.book_note_save_failed)))
    }
}

private fun resolveBookNoteFile(context: Context, rootUri: Uri): QuickNoteFileInfo? {
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

private fun parseBookNoteSectionContent(fullText: String): BookNoteParseResult {
    val sectionLines = getBookNoteSectionLines(fullText)
        ?: return BookNoteParseResult(entries = emptyList(), ignoredSectionContent = "")
    val entries = mutableListOf<BookNoteEntry>()
    val ignoredLines = mutableListOf<String>()
    var currentBookTitle: String? = null
    val currentLines = mutableListOf<String>()
    var nextId = 1L

    fun flushCurrent() {
        val title = currentBookTitle?.trim()
        if (title.isNullOrBlank()) {
            if (currentLines.any { it.isNotBlank() }) {
                ignoredLines += currentLines
            }
        } else {
            entries += parseBookNoteEntry(nextId++, title, currentLines)
        }
        currentBookTitle = null
        currentLines.clear()
    }

    for (line in sectionLines) {
        val trimmed = line.trim()
        when {
            trimmed in BOOK_NOTE_SECTION_HEADINGS -> Unit
            line.trimStart().startsWith("### ") -> {
                flushCurrent()
                currentBookTitle = line.trimStart().removePrefix("### ").trim()
            }
            currentBookTitle != null -> currentLines += line
            trimmed.isBlank() -> Unit
            else -> ignoredLines += line
        }
    }
    flushCurrent()
    return BookNoteParseResult(
        entries = entries,
        ignoredSectionContent = ignoredLines.joinToString("\n").trimEnd()
    )
}

private fun parseBookNoteEntry(id: Long, bookTitle: String, rawLines: List<String>): BookNoteEntry {
    val lines = rawLines.dropWhile { it.isBlank() }
    var chapterInfo: String? = null
    var quote: String? = null
    var chapterIndex: Int? = null
    var chapterTitle: String? = null
    var scrollRatio: Float? = null
    var createdAt = System.currentTimeMillis()
    val bodyLines = mutableListOf<String>()
    var metadataPhase = true
    for (line in lines) {
        val trimmed = line.trim()
        val metadataJsonText = if (metadataPhase) extractBookNoteMetadataJson(trimmed) else null
        when {
            metadataPhase && trimmed.isBlank() && bodyLines.isEmpty() -> Unit
            metadataPhase && BOOK_NOTE_CHAPTER_REGEX.matches(trimmed) -> {
                chapterInfo = BOOK_NOTE_CHAPTER_REGEX.matchEntire(trimmed)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
            metadataPhase && BOOK_NOTE_QUOTE_REGEX.matches(trimmed) -> {
                quote = BOOK_NOTE_QUOTE_REGEX.matchEntire(trimmed)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
            metadataPhase && metadataJsonText != null -> {
                runCatching { JSONObject(metadataJsonText) }.getOrNull()?.let { json ->
                    if (!json.isNull("chapterIndex")) chapterIndex = json.optInt("chapterIndex")
                    chapterTitle = json.optString("chapterTitle", "").takeIf { it.isNotBlank() }
                    if (!json.isNull("scrollRatio")) {
                        scrollRatio = json.optDouble("scrollRatio", 0.0).toFloat().coerceIn(0f, 1f)
                    }
                    createdAt = json.optLong("createdAt", createdAt)
                }
            }
            else -> {
                metadataPhase = false
                bodyLines += line
            }
        }
    }
    return BookNoteEntry(
        id = id,
        bookTitle = bookTitle,
        chapterInfo = chapterInfo,
        quote = quote,
        content = bodyLines.joinToString("\n").trim('\n'),
        chapterIndex = chapterIndex,
        chapterTitle = chapterTitle,
        scrollRatio = scrollRatio,
        createdAt = createdAt
    )
}

private fun mergeBookNoteSection(context: Context, fullText: String, entries: List<BookNoteEntry>): String {
    val normalized = fullText.replace("\r\n", "\n")
    val sectionText = buildBookNoteSection(context, entries)
    val range = findBookNoteSectionRange(normalized)
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

private fun buildBookNoteSection(context: Context, entries: List<BookNoteEntry>): String {
    return buildString {
        append(context.getString(R.string.book_note_section_heading_markdown))
        if (entries.isNotEmpty()) append("\n\n")
        entries.forEachIndexed { index, entry ->
            append(serializeBookNoteEntry(context, entry))
            if (index != entries.lastIndex) {
                append("\n\n")
            }
        }
    }.trimEnd()
}

private fun serializeBookNoteEntry(context: Context, entry: BookNoteEntry): String {
    val normalizedBookTitle = entry.bookTitle.trim()
    val normalizedChapter = entry.chapterInfo?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedQuote = entry.quote?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedContent = entry.content.trimEnd()
    return buildString {
        append("### ")
        append(normalizedBookTitle)
        append("\n\n")
        if (normalizedChapter != null) {
            append(context.getString(R.string.book_note_file_chapter_line, normalizedChapter))
            append("\n")
        }
        if (normalizedQuote != null) {
            append(context.getString(R.string.book_note_file_quote_line, normalizedQuote))
            append("\n")
        }
        append(buildBookNoteMetadataComment(entry))
        append("\n")
        if (normalizedChapter != null || normalizedQuote != null) {
            append("\n")
        }
        if (normalizedContent.isNotBlank()) {
            append(normalizedContent)
        }
    }.trimEnd()
}

private fun buildBookNoteMetadataComment(entry: BookNoteEntry): String {
    val json = JSONObject().apply {
        if (entry.chapterIndex != null) put("chapterIndex", entry.chapterIndex)
        if (!entry.chapterTitle.isNullOrBlank()) put("chapterTitle", entry.chapterTitle)
        if (entry.scrollRatio != null) put("scrollRatio", entry.scrollRatio)
        put("createdAt", entry.createdAt)
    }
    return "<!-- lm-book-note ${json} -->"
}

private fun getBookNoteSectionLines(fullText: String): List<String>? {
    val normalized = fullText.replace("\r\n", "\n")
    val range = findBookNoteSectionRange(normalized) ?: return null
    val lines = normalized.split("\n")
    return lines.subList(range.startLine, range.endLine)
}

private fun findBookNoteSectionRange(fullText: String): BookNoteSectionRange? {
    val lines = fullText.replace("\r\n", "\n").split("\n")
    var startIndex = -1
    for (index in lines.indices) {
        if (lines[index].trim() in BOOK_NOTE_SECTION_HEADINGS) {
            startIndex = index
            break
        }
    }
    if (startIndex < 0) return null
    var endIndex = lines.size
    for (index in (startIndex + 1) until lines.size) {
        val trimmed = lines[index].trimStart()
        if (BOOK_NOTE_SECTION_BOUNDARY_REGEX.matches(trimmed) && !trimmed.startsWith("### ")) {
            endIndex = index
            break
        }
    }
    return BookNoteSectionRange(startLine = startIndex, endLine = endIndex)
}

private fun nextBookNoteEntryId(entries: List<BookNoteEntry>): Long =
    (entries.maxOfOrNull { it.id } ?: 0L) + 1L

private fun bookNoteMatchesQuery(entry: BookNoteEntry, query: String): Boolean {
    if (query.isBlank()) return true
    return listOf(
        entry.bookTitle,
        entry.chapterInfo.orEmpty(),
        entry.chapterTitle.orEmpty(),
        entry.quote.orEmpty(),
        entry.content
    ).any { it.contains(query, ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookNoteScreen(
    loadedData: BookNoteLoadedData,
    inProgress: Boolean,
    onEntriesChanged: (List<BookNoteEntry>) -> Unit
) {
    val composeContext = LocalContext.current
    val clipboardManager = composeContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    var entries by remember(loadedData.fileInfo.uri, loadedData.rawText) { mutableStateOf(loadedData.entries) }
    var filterQuery by remember(loadedData.fileInfo.uri, loadedData.rawText) { mutableStateOf("") }
    var editorState by remember { mutableStateOf<BookNoteEditorState?>(null) }
    var deleteConfirmEntry by remember { mutableStateOf<BookNoteEntry?>(null) }
    var showIgnoredContentDialog by remember(loadedData.fileInfo.uri, loadedData.rawText) {
        mutableStateOf(loadedData.ignoredSectionContent.isNotBlank())
    }
    val bookExpandedStates = remember(loadedData.fileInfo.uri) { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(entries) {
        onEntriesChanged(entries)
    }

    val filteredEntries = remember(entries, filterQuery) {
        entries.filter { bookNoteMatchesQuery(it, filterQuery.trim()) }
    }
    val groupedEntries = remember(filteredEntries) {
        filteredEntries
            .groupBy { it.bookTitle.trim() }
            .toList()
            .sortedByDescending { (_, notes) -> notes.maxOfOrNull { it.createdAt } ?: 0L }
            .map { (bookTitle, notes) ->
                bookTitle to notes.sortedByDescending { it.createdAt }
            }
    }
    groupedEntries.forEach { (bookTitle, _) ->
        if (bookTitle !in bookExpandedStates) {
            bookExpandedStates[bookTitle] = true
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.book_note_title))
                            Text(
                                if (loadedData.fileInfo.isEncrypted) {
                                    stringResource(R.string.book_note_file_encrypted, loadedData.fileInfo.fileName)
                                } else {
                                    loadedData.fileInfo.fileName
                                },
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
                onClick = { editorState = BookNoteEditorState() },
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.book_note_add_desc))
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
                    stringResource(R.string.book_note_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item("filter") {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.book_note_filter_label)) },
                        placeholder = { Text(stringResource(R.string.book_note_filter_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                }
                item("summary") {
                    Text(
                        text = if (filterQuery.isBlank()) {
                            stringResource(R.string.book_note_group_summary, groupedEntries.size, filteredEntries.size)
                        } else {
                            stringResource(R.string.book_note_filter_summary, groupedEntries.size, filteredEntries.size)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (filteredEntries.isEmpty()) {
                    item("empty-filter") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.book_note_filter_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                groupedEntries.forEach { (bookTitle, notes) ->
                    item("header-$bookTitle") {
                        val expanded = bookExpandedStates[bookTitle] != false
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { bookExpandedStates[bookTitle] = !expanded }) {
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bookTitle, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.book_note_book_count, notes.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (bookExpandedStates[bookTitle] != false) {
                        items(notes, key = { it.id }) { entry ->
                            BookNoteEntryCard(
                                entry = entry,
                                enabled = !inProgress,
                                showBookTitle = false,
                                onEdit = {
                                    editorState = BookNoteEditorState(
                                        editingId = entry.id,
                                        bookTitle = entry.bookTitle,
                                        chapterInfo = entry.chapterInfo.orEmpty(),
                                        quote = entry.quote.orEmpty(),
                                        content = entry.content,
                                        chapterIndex = entry.chapterIndex,
                                        chapterTitle = entry.chapterTitle,
                                        scrollRatio = entry.scrollRatio,
                                        createdAt = entry.createdAt
                                    )
                                },
                                onDelete = { deleteConfirmEntry = entry }
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
        BookNoteEditorDialog(
            state = state,
            inProgress = inProgress,
            onDismiss = { if (!inProgress) editorState = null },
            onConfirm = { updated ->
                val normalizedTitle = updated.bookTitle.trim()
                val normalizedContent = updated.content.trimEnd()
                val normalizedQuote = updated.quote.trim().takeIf { it.isNotEmpty() }
                val normalizedChapterInfo = updated.chapterInfo.trim().takeIf { it.isNotEmpty() }
                if (normalizedTitle.isBlank()) {
                    Toast.makeText(
                        composeContext,
                        composeContext.getString(R.string.book_note_book_title_required),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@BookNoteEditorDialog
                }
                if (normalizedContent.isBlank() && normalizedChapterInfo == null && normalizedQuote == null) {
                    Toast.makeText(
                        composeContext,
                        composeContext.getString(R.string.book_note_content_or_position_required),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@BookNoteEditorDialog
                }
                val updatedEntry = BookNoteEntry(
                    id = updated.editingId ?: nextBookNoteEntryId(entries),
                    bookTitle = normalizedTitle,
                    chapterInfo = normalizedChapterInfo,
                    quote = normalizedQuote,
                    content = normalizedContent,
                    chapterIndex = updated.chapterIndex,
                    chapterTitle = updated.chapterTitle,
                    scrollRatio = updated.scrollRatio,
                    createdAt = updated.createdAt
                )
                entries = if (updated.editingId == null) {
                    entries + updatedEntry
                } else {
                    entries.map { entry -> if (entry.id == updated.editingId) updatedEntry else entry }
                }
                editorState = null
            }
        )
    }

    deleteConfirmEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteConfirmEntry = null },
            title = { Text(stringResource(R.string.book_note_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.book_note_delete_confirm, entry.bookTitle),
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
                    Text(stringResource(R.string.common_delete))
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
            title = { Text(stringResource(R.string.book_note_ignored_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.book_note_ignored_desc),
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
                        label = { Text(stringResource(R.string.book_note_ignored_label)) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager?.setPrimaryClip(
                            ClipData.newPlainText(
                                composeContext.getString(R.string.book_note_ignored_clip_label),
                                loadedData.ignoredSectionContent
                            )
                        )
                        Toast.makeText(
                            composeContext,
                            composeContext.getString(R.string.book_note_ignored_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                        showIgnoredContentDialog = false
                    }
                ) {
                    Text(stringResource(R.string.book_note_copy_and_continue))
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
fun BookNoteManagerDialog(
    bookTitle: String,
    currentChapterInfo: String,
    entries: List<BookNoteEntry>,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onAddCurrent: () -> Unit,
    onEdit: (BookNoteEntry) -> Unit,
    onDelete: (BookNoteEntry) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.book_note_manager_title)) },
        text = {
            Column {
                Text(bookTitle, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.book_note_current_location, currentChapterInfo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (inProgress) {
                    Text(
                        stringResource(R.string.book_note_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (entries.isEmpty()) {
                    Text(
                        stringResource(R.string.book_note_manager_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            BookNoteEntryCard(
                                entry = entry,
                                enabled = !inProgress,
                                showBookTitle = false,
                                onEdit = { onEdit(entry) },
                                onDelete = { onDelete(entry) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddCurrent, enabled = !inProgress) {
                Text(stringResource(R.string.book_note_add_current_chapter))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
fun BookNoteEditorDialog(
    state: BookNoteEditorState,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onReassociateCurrentPosition: (() -> Unit)? = null,
    onClearLocation: (() -> Unit)? = null,
    onConfirm: (BookNoteEditorState) -> Unit
) {
    var bookTitle by remember(state) { mutableStateOf(state.bookTitle) }
    var chapterInfo by remember(state) { mutableStateOf(state.chapterInfo) }
    var quote by remember(state) { mutableStateOf(state.quote) }
    var content by remember(state) { mutableStateOf(state.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (state.editingId == null) {
                    stringResource(R.string.book_note_add_title)
                } else {
                    stringResource(R.string.book_note_edit_title)
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = bookTitle,
                    onValueChange = { bookTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.book_note_field_book_title)) },
                    enabled = !inProgress,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = chapterInfo,
                    onValueChange = { chapterInfo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.book_note_field_chapter)) },
                    enabled = !inProgress,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = quote,
                    onValueChange = { quote = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.book_note_field_quote)) },
                    enabled = !inProgress,
                    minLines = 2,
                    maxLines = 4
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    label = { Text(stringResource(R.string.book_note_field_content)) },
                    enabled = !inProgress,
                    minLines = 6,
                    maxLines = 10
                )
                if (onReassociateCurrentPosition != null || onClearLocation != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onReassociateCurrentPosition?.let { callback ->
                            TextButton(onClick = callback, enabled = !inProgress) {
                                Text(stringResource(R.string.book_note_reassociate_position))
                            }
                        }
                        onClearLocation?.let { callback ->
                            TextButton(onClick = callback, enabled = !inProgress) {
                                Text(stringResource(R.string.book_note_clear_location))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        state.copy(
                            bookTitle = bookTitle,
                            chapterInfo = chapterInfo,
                            quote = quote,
                            content = content
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
private fun BookNoteEntryCard(
    entry: BookNoteEntry,
    enabled: Boolean,
    showBookTitle: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val wholeBookLabel = stringResource(R.string.book_note_whole_book)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .fillMaxWidth(0.8f)
            ) {
                if (showBookTitle) {
                    Text(
                        entry.bookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    entry.displayLocation(wholeBookLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.chapterInfo?.takeIf { it.isNotBlank() }?.let { chapterInfo ->
                    Text(
                        chapterInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onEdit, enabled = enabled) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.book_note_edit_desc))
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
            }
        }
        entry.quote?.takeIf { it.isNotBlank() }?.let { quote ->
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.book_note_quote_display, quote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (entry.content.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(entry.content, style = MaterialTheme.typography.bodyMedium)
        } else if (entry.hasLocation()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.book_note_location_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}