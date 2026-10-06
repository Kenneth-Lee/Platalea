package com.kenny.localmanager.ui

import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isMusicFileName
import com.kenny.localmanager.file.moveToTrash
import com.kenny.localmanager.file.pathFromRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

internal fun pendingBatchMetadataKind(items: List<DocumentFileModel>): DetailMetadataKind? {
    if (items.isEmpty()) return null
    val nonDir = items.filterNot { it.isDirectory }
    if (nonDir.isEmpty() || nonDir.size != items.size) return null
    val kinds = nonDir.map { inferDetailMetadataKind(it.name, "", false) }.distinct()
    return if (kinds.size == 1 && kinds[0] in setOf(DetailMetadataKind.IMAGE, DetailMetadataKind.AUDIO)) {
        kinds[0]
    } else {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingListScreen(
    pendingList: List<DocumentFileModel>,
    currentDirPath: String = "",
    rootUri: String? = null,
    mode: PendingListMode = PendingListMode.NORMAL,
    title: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    onShowFileDetails: (DocumentFileModel) -> Unit = {},
    onShowBatchDetails: () -> Unit = {},
    onRemove: (DocumentFileModel) -> Unit,
    onCopyHere: () -> Unit = {},
    onMoveHere: () -> Unit = {},
    onExtractArchivesHere: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
    onRequestBatchMask: () -> Unit = {},
    onRequestBatchGpgEncrypt: () -> Unit = {},
    onRequestBatchGpgDecrypt: () -> Unit = {},
    onRequestBatchRename: () -> Unit = {},
    onRequestCompressArchive: () -> Unit = {},
    onRequestCompressToZip: () -> Unit = {},
    onRequestCompressTo7z: () -> Unit = {},
    onRequestForwardToBulletin: () -> Unit = {},
    onClearFilteredList: (List<DocumentFileModel>) -> Unit = {},
    onAddToPlayback: (List<DocumentFileModel>) -> Unit = {},
    onAddToZipEditTarget: ((List<DocumentFileModel>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPickMode = mode == PendingListMode.DIRECTORY_PICK
    var filterText by remember { mutableStateOf("") }
    val filteredPendingItems = if (filterText.isBlank()) pendingList
    else runCatching { Regex(filterText) }.getOrNull()?.let { regex ->
        pendingList.filter { regex.containsMatchIn(it.name) }
    } ?: pendingList
    val screenTitle = title ?: if (isPickMode) {
        context.getString(R.string.directory_pick_list_title, pendingList.size)
    } else {
        stringResource(R.string.browser_pending_list_title, pendingList.size)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {}
    ) { padding ->
        if (pendingList.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.browser_list_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (!isPickMode) {
                Text(
                    stringResource(R.string.browser_current_dir, currentDirPath),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.browser_filename_regex_hint)) },
                        label = null
                    )
                    if (filterText.isNotEmpty()) {
                        IconButton(onClick = { filterText = "" }) {
                            Icon(Icons.Default.RemoveCircle, contentDescription = stringResource(R.string.pending_list_action_clear_filter), Modifier.size(20.dp))
                        }
                    }
                }
                if (!isPickMode) {
                val audioFromFiltered = filteredPendingItems.filter { !it.isDirectory && isMusicFileName(it.name) }
                val batchDetailKind = pendingBatchMetadataKind(filteredPendingItems)
                val pendingActions = mutableListOf(
                    context.getString(R.string.pending_list_action_copy) to onCopyHere,
                    context.getString(R.string.pending_list_action_move) to onMoveHere,
                    context.getString(R.string.pending_list_action_extract) to onExtractArchivesHere,
                    context.getString(R.string.pending_list_action_delete) to onRequestDelete,
                    context.getString(R.string.pending_list_action_clear_filter) to {
                        onClearFilteredList(filteredPendingItems)
                    },
                    context.getString(R.string.pending_list_action_playback) to {
                        if (audioFromFiltered.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.browser_no_audio_in_list), Toast.LENGTH_SHORT).show()
                        } else {
                            onAddToPlayback(audioFromFiltered)
                        }
                    },
                    context.getString(R.string.pending_list_action_mask) to onRequestBatchMask,
                    context.getString(R.string.pending_list_action_rename) to onRequestBatchRename,
                    context.getString(R.string.pending_list_action_gpg_encrypt) to onRequestBatchGpgEncrypt,
                    context.getString(R.string.pending_list_action_gpg_decrypt) to onRequestBatchGpgDecrypt,
                    context.getString(R.string.pending_list_action_archive) to onRequestCompressArchive,
                    context.getString(R.string.pending_forward_to_bulletin) to onRequestForwardToBulletin,
                )
                if (onAddToZipEditTarget != null) {
                    // 仅在操作目标为 zip 时提供（目标概念是通用的，加入是 zip 专属动作）
                    pendingActions.add(
                        context.getString(R.string.zip_edit_pending_action) to {
                            onAddToZipEditTarget(filteredPendingItems)
                        }
                    )
                }
                if (batchDetailKind != null) {
                    pendingActions.add(context.getString(R.string.context_details) to onShowBatchDetails)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    pendingActions.chunked(5).forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            rowActions.forEach { (label, action) ->
                                TextButton(
                                    onClick = action,
                                    shape = RectangleShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    colors = ButtonDefaults.textButtonColors(),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minWidth = 0.dp, minHeight = 30.dp)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            repeat(5 - rowActions.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredPendingItems) { item ->
                    val relativePath = remember(item.uri, rootUri) {
                        pathFromRoot(context, rootUri, normalizeContentUriString(item.uri.toString()))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            Modifier.size(24.dp),
                            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                relativePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onShowFileDetails(item) }) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = stringResource(R.string.context_details),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onRemove(item) }) {
                            Icon(
                                Icons.Default.RemoveCircle,
                                contentDescription = stringResource(R.string.browser_remove_from_list),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            if (isPickMode && onConfirm != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(context.getString(R.string.directory_pick_cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = pendingList.isNotEmpty()
                    ) {
                        Text(confirmLabel ?: context.getString(R.string.directory_pick_confirm_generic))
                    }
                }
            }
            }
        }
    }
}

/**
 * 待处理列表删除确认对话框：列出待删项并选择移入废纸篓或永久删除。
 * 执行编排（删除、刷新、清列表）由宿主经 onConfirm 注入。
 */
@Composable
internal fun PendingDeleteConfirmDialog(
    items: List<DocumentFileModel>,
    hasRoot: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (permanently: Boolean) -> Unit
) {
    var deletePermanently by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browser_confirm_delete_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.browser_confirm_delete_count, items.size), color = MaterialTheme.colorScheme.onSurface)
                if (hasRoot) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !deletePermanently,
                            onClick = { deletePermanently = false }
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.browser_move_to_trash_recoverable), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = deletePermanently,
                            onClick = { deletePermanently = true }
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.browser_delete_permanently), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_no_trash_permanent_delete), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (item.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            Modifier.size(20.dp),
                            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(deletePermanently) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(if (hasRoot && !deletePermanently) stringResource(R.string.browser_moved_to_trash) else stringResource(R.string.browser_confirm_delete_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}