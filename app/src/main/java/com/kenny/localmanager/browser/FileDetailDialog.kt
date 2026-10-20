package com.kenny.localmanager.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.graphics.pdf.PdfRenderer
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kenny.localmanager.R
import com.kenny.localmanager.data.AudioMetaAnnotation
import com.kenny.localmanager.data.DirectoryScrollMemory
import com.kenny.localmanager.dict.isStarDictImportCandidate
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.EpubParseResult
import com.kenny.localmanager.file.RecursiveFileSearchCriteria
import com.kenny.localmanager.file.RecursiveFileSearchHit
import com.kenny.localmanager.file.ZipFirstLevelResult
import com.kenny.localmanager.file.buildExternalOpenIntent
import com.kenny.localmanager.file.extractEpubToCache
import com.kenny.localmanager.file.fileExtensionKey
import com.kenny.localmanager.file.getArchiveFirstLevelEntries
import com.kenny.localmanager.file.getEpubCacheDir
import com.kenny.localmanager.file.guessExternalOpenMimeType
import com.kenny.localmanager.file.isExtractableArchive
import com.kenny.localmanager.file.isGalleryImageFile
import com.kenny.localmanager.file.isGpgEncryptedFile
import com.kenny.localmanager.file.isMarkdownFile
import com.kenny.localmanager.file.isPassFile
import com.kenny.localmanager.file.isPdfFile
import com.kenny.localmanager.file.isPicZip
import com.kenny.localmanager.file.isVidZip
import com.kenny.localmanager.file.isHtmlFile
import com.kenny.localmanager.file.listChildrenFast
import com.kenny.localmanager.file.loadEpubFromCache
import com.kenny.localmanager.file.renameDocument
import com.kenny.localmanager.file.searchFilesRecursively
import com.kenny.localmanager.file.parseSizeRange
import com.kenny.localmanager.file.parseTimeRange
import com.kenny.localmanager.file.toExternalLaunchUri
import com.kenny.localmanager.player.PlaybackState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kenny.localmanager.config.BrowserConfigView
import com.kenny.localmanager.player.DetailMetadataKind
import com.kenny.localmanager.player.EditableAudioAnnotationValidation
import com.kenny.localmanager.player.formatPlaybackTime
import com.kenny.localmanager.player.inferDetailMetadataKind
import com.kenny.localmanager.player.isMp3AudioFile
import com.kenny.localmanager.player.readEditableAudioAnnotation
import com.kenny.localmanager.player.saveEditableAudioAnnotationToMp3
import com.kenny.localmanager.player.saveEditableAudioAnnotationWithJaudiotagger
import com.kenny.localmanager.player.supportsJaudiotaggerWrite
import com.kenny.localmanager.player.validateEditableAudioAnnotationForm
import com.kenny.localmanager.ui.ReliablePasswordInputField
import com.kenny.localmanager.ui.normalizeContentUriString
import com.kenny.localmanager.file.isEpubFile
import com.kenny.localmanager.file.isLlmFile
import com.kenny.localmanager.file.isTxtFile
import com.kenny.localmanager.ui.PlataleaApp

/**
 * 文件详情对话框（拆解设计 第四批：会话化迁出）。
 * 状态随会话生命周期：每次打开重建（remember 以 activeTarget.uri 为键），
 * 关闭即销毁，无残留输入。
 */
@Composable
internal fun FileDetailDialog(
    target: DocumentFileModel,
    config: BrowserConfigView,
    onRenamed: (oldTarget: DocumentFileModel, renamed: DocumentFileModel) -> Unit,
    onDismiss: () -> Unit,
) {
    var activeTarget by remember { mutableStateOf(target) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val detailTarget = activeTarget
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    var detailRenameValue by remember(detailTarget.uri) { mutableStateOf(detailTarget.name) }
    var detailMeta by remember(detailTarget.uri) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val detailRenameFocus = remember { FocusRequester() }
    // 详情面板的“压缩包内容”区覆盖所有 zip（含 pic/vid 阅读器形态）；其余压缩格式同解压判定
    val isArchiveFile = isExtractableArchive(detailTarget.name) || isPicZip(detailTarget.name) || isVidZip(detailTarget.name)
    var zipDetailResult by remember(detailTarget.uri) { mutableStateOf<ZipFirstLevelResult?>(null) }
    var showTypeSpecificMeta by remember(detailTarget.uri) { mutableStateOf(false) }
    var typeSpecificMetaLoaded by remember(detailTarget.uri) { mutableStateOf(false) }
    var typeSpecificMetaLoading by remember(detailTarget.uri) { mutableStateOf(false) }
    var typeSpecificMetaRows by remember(detailTarget.uri) { mutableStateOf<List<DetailMetadataRow>>(emptyList()) }
    var typeSpecificMetaError by remember(detailTarget.uri) { mutableStateOf<String?>(null) }
    var showImageExifEditDialog by remember(detailTarget.uri) { mutableStateOf(false) }
    var showAudioMetaEditDialog by remember(detailTarget.uri) { mutableStateOf(false) }
    val detailScope = rememberCoroutineScope()

    LaunchedEffect(detailTarget.uri) {
        withContext(Dispatchers.IO) {
            try {
                val uri = detailTarget.uri
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )
                val meta = mutableMapOf<String, String>()
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        for (i in 0 until cursor.columnCount) {
                            val colName = cursor.getColumnName(i)
                            val value = cursor.getString(i) ?: continue
                            meta[colName] = value
                        }
                    }
                }
                detailMeta = meta
            } catch (_: Exception) {}
        }
    }

    val docId = detailMeta[DocumentsContract.Document.COLUMN_DOCUMENT_ID]
    val fullPath = if (docId != null) {
        val parts = docId.split(":", limit = 2)
        if (parts.size == 2) "/${parts[1]}" else docId
    } else {
        detailTarget.uri.path ?: detailTarget.uri.toString()
    }
    val displayPath = if (detailTarget.isDirectory) {
        fullPath
    } else {
        fullPath.substringBeforeLast('/', fullPath).ifEmpty { "/" }
    }
    val mimeType = detailMeta[DocumentsContract.Document.COLUMN_MIME_TYPE] ?: ""
    val sizeStr = detailTarget.displaySize.ifEmpty {
        detailMeta[DocumentsContract.Document.COLUMN_SIZE]?.toLongOrNull()?.let { sz ->
            when {
                sz < 1024 -> "$sz B"
                sz < 1024 * 1024 -> "%.1f KB".format(sz / 1024.0)
                sz < 1024 * 1024 * 1024 -> "%.1f MB".format(sz / (1024.0 * 1024))
                else -> "%.1f GB".format(sz / (1024.0 * 1024 * 1024))
            }
        } ?: ""
    }
    val fmt = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    }
    val lastModifiedStr = if (detailTarget.lastModified > 0) fmt.format(java.util.Date(detailTarget.lastModified)) else ""
    val lastModifiedFromMeta = detailMeta[DocumentsContract.Document.COLUMN_LAST_MODIFIED]?.toLongOrNull()
    val lastModifiedMetaStr = if (lastModifiedFromMeta != null && lastModifiedFromMeta > 0) fmt.format(java.util.Date(lastModifiedFromMeta)) else ""
    val displayModified = lastModifiedStr.ifEmpty { lastModifiedMetaStr }
    val detailMetaKind = inferDetailMetadataKind(detailTarget.name, mimeType, detailTarget.isDirectory)

    suspend fun loadTypeSpecificMetadataNow(forceReload: Boolean = false) {
        if (!showTypeSpecificMeta || detailTarget.isDirectory) return
        if (!forceReload && typeSpecificMetaLoaded) return
        typeSpecificMetaLoading = true
        typeSpecificMetaError = null
        val rows = try {
            withContext(Dispatchers.IO) {
                loadTypeSpecificMetadataRows(
                    context = context,
                    uri = detailTarget.uri,
                    fileName = detailTarget.name,
                    mimeType = mimeType,
                    isDirectory = detailTarget.isDirectory
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            typeSpecificMetaError = e.message ?: e.javaClass.simpleName
            emptyList()
        }
        val mergedRows = if (detailMetaKind == DetailMetadataKind.AUDIO) {
            val annotation = withContext(Dispatchers.IO) {
                config.getAudioMetaAnnotation(detailTarget.uri.toString())
            }
            applyAudioMetaAnnotationRows(context, rows, annotation)
        } else {
            rows
        }
        typeSpecificMetaRows = mergedRows
        typeSpecificMetaLoaded = true
        typeSpecificMetaLoading = false

        if (isArchiveFile && zipDetailResult == null) {
            zipDetailResult = withContext(Dispatchers.IO) {
                getArchiveFirstLevelEntries(context, detailTarget.uri, detailTarget.name, null)
            }
        }
    }

    LaunchedEffect(showTypeSpecificMeta, detailTarget.uri, mimeType) {
        loadTypeSpecificMetadataNow(forceReload = false)
    }

    if (showAudioMetaEditDialog) {
        AudioMetaEditDialog(
            target = detailTarget,
            mimeType = mimeType,
            config = config,
            scope = detailScope,
            onSaved = {
                typeSpecificMetaLoaded = false
                detailScope.launch { loadTypeSpecificMetadataNow(forceReload = true) }
            },
            onDismiss = { showAudioMetaEditDialog = false }
        )
    }

    if (showImageExifEditDialog) {
        ImageExifEditDialog(
            target = detailTarget,
            scope = detailScope,
            onSaved = {
                typeSpecificMetaLoaded = false
                detailScope.launch { loadTypeSpecificMetadataNow(forceReload = true) }
            },
            onDismiss = { showImageExifEditDialog = false }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.context_details), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))

                Text(stringResource(R.string.browser_full_path), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(displayPath, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = {
                        clipboardManager?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.browser_full_path), displayPath))
                        Toast.makeText(context, context.getString(R.string.browser_path_copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.browser_copy_path), Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.browser_rename_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = detailRenameValue,
                        onValueChange = { detailRenameValue = it },
                        singleLine = false,
                        minLines = 1,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(detailRenameFocus),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val newName = detailRenameValue.trim()
                                if (newName.isNotEmpty() && newName != detailTarget.name) {
                                    try {
                                        val renamedUri = context.contentResolver.renameDocument(detailTarget.uri, newName)
                                        if (renamedUri != null) {
                                            val renamed = detailTarget.copy(name = newName, uri = renamedUri)
                                            onRenamed(detailTarget, renamed)
                                            activeTarget = renamed
                                            Toast.makeText(context, context.getString(R.string.browser_renamed), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.browser_rename_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(context, context.getString(R.string.browser_rename_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = detailRenameValue.trim().let { it.isNotEmpty() && it != detailTarget.name }
                        ) { Text(stringResource(R.string.common_ok)) }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.browser_file_properties_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                @Composable
                fun MetaRow(label: String, value: String) {
                    if (value.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                Text(value, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                MetaRow(context.getString(R.string.browser_type_field), if (detailTarget.isDirectory) context.getString(R.string.browser_folder_type) else mimeType.ifEmpty { context.getString(R.string.browser_file_type) })
                if (!detailTarget.isDirectory) MetaRow(context.getString(R.string.browser_size_field), sizeStr)
                MetaRow(context.getString(R.string.browser_modified_field), displayModified)

                if (!detailTarget.isDirectory) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.browser_type_specific_section_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showTypeSpecificMeta = !showTypeSpecificMeta }) {
                            Text(
                                if (showTypeSpecificMeta) {
                                    stringResource(R.string.browser_type_specific_collapse)
                                } else {
                                    stringResource(R.string.browser_type_specific_expand)
                                }
                            )
                        }
                    }

                    if (showTypeSpecificMeta) {
                        Spacer(Modifier.height(8.dp))
                        if (detailMetaKind == DetailMetadataKind.IMAGE) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showImageExifEditDialog = true }
                                ) {
                                    Text(stringResource(R.string.browser_type_specific_edit_image_meta))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        } else if (detailMetaKind == DetailMetadataKind.AUDIO) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showAudioMetaEditDialog = true }
                                ) {
                                    Text(stringResource(R.string.browser_type_specific_edit_audio_meta))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        when {
                            typeSpecificMetaLoading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.browser_type_specific_loading),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            typeSpecificMetaError != null -> {
                                Text(
                                    stringResource(R.string.browser_type_specific_error, typeSpecificMetaError ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            typeSpecificMetaRows.isNotEmpty() -> {
                                typeSpecificMetaRows.forEach { row ->
                                    if (row.isSection) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = row.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        MetaRow(row.label, row.value)
                                    }
                                }
                            }
                            else -> {
                                Text(
                                    stringResource(R.string.browser_type_specific_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                    }
                }

                if (isArchiveFile) {
                    ZipContentSection(
                        target = detailTarget,
                        result = zipDetailResult,
                        onResult = { zipDetailResult = it }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
            }
        }
    }


}
