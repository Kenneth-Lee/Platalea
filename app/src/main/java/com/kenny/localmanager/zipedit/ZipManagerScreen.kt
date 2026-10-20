package com.kenny.localmanager.zipedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.kenny.localmanager.R
import com.kenny.localmanager.file.ZipEntryInfo
import com.kenny.localmanager.file.expandSelectedZipEntries
import com.kenny.localmanager.file.listZipEntries
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ============================================================================
// ZIP 编辑域 —— 目标条目管理界面
//
// 只读列出目标 zip 全部条目，支持勾选删除。数据加载在本组件（IO 协程），
// 删除执行上抛宿主（onRemoveEntries），进度与结果由宿主经 ZipEditDialogs
// 呈现；删除成功后宿主递增 refreshTrigger 触发重新加载。
// ============================================================================

internal data class ZipManagerRouteState(
    val modifier: Modifier = Modifier,
    val targetUri: String,
    val targetName: String,
    val treeUri: String?,
    val refreshTrigger: Int,
    val onRemoveEntries: (List<String>) -> Unit,
    val onExtractEntries: (List<String>) -> Unit,
    val onAddFiles: (List<Uri>) -> Unit,
    val onDismiss: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZipManagerScreen(state: ZipManagerRouteState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var entries by remember(state.targetUri, state.refreshTrigger) { mutableStateOf<List<ZipEntryInfo>?>(null) }
    var loadError by remember(state.targetUri, state.refreshTrigger) { mutableStateOf(false) }
    var selected by remember(state.targetUri, state.refreshTrigger) { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmExtract by remember { mutableStateOf(false) }

    val addFilesLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) state.onAddFiles(uris) }

    androidx.activity.compose.BackHandler { state.onDismiss() }

    LaunchedEffect(state.targetUri, state.refreshTrigger) {
        val loaded = withContext(Dispatchers.IO) {
            listZipEntries(context, Uri.parse(state.targetUri), state.treeUri?.let(Uri::parse))
        }
        entries = loaded
        loadError = loaded == null
    }

    Scaffold(
        modifier = state.modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.zip_manager_title, state.targetName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = state.onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = { addFilesLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.zip_manager_add_files))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (!selected.isEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.zip_manager_selected_count, selected.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        TextButton(onClick = { selected = emptySet() }) {
                            Text(stringResource(R.string.zip_manager_clear_selection))
                        }
                        Button(onClick = { confirmExtract = true }) {
                            Text(stringResource(R.string.zip_manager_extract_action))
                        }
                        Button(onClick = { confirmDelete = true }) {
                            Text(stringResource(R.string.zip_manager_delete_action))
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            entries == null && !loadError -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.zip_manager_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            loadError -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.zip_manager_load_failed), color = MaterialTheme.colorScheme.error)
            }
            entries?.isEmpty() == true -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.zip_manager_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val list = entries.orEmpty()
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(list, key = { it.name }) { entry ->
                        val isSel = entry.name in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (isSel) selected - entry.name else selected + entry.name
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSel,
                                onCheckedChange = {
                                    selected = if (isSel) selected - entry.name else selected + entry.name
                                }
                            )
                            Column(Modifier.padding(end = 12.dp)) {
                                Text(
                                    entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val entryMeta = stringResource(R.string.zip_manager_entry_meta, entry.size, entry.compressedSize)
                                Text(
                                    if (entry.isEncrypted) "$entryMeta  ·  ${stringResource(R.string.zip_edit_encrypted_badge)}" else entryMeta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmExtract) {
        AlertDialog(
            onDismissRequest = { confirmExtract = false },
            title = { Text(stringResource(R.string.zip_manager_extract_action)) },
            text = {
                // 勾选目录条目会展开为其中全部文件，确认框按展开后的文件数计
                val entryNames = entries.orEmpty().map { it.name }
                val fileCount = expandSelectedZipEntries(entryNames, selected).count { !it.endsWith("/") }
                Text(stringResource(R.string.zip_manager_extract_confirm, fileCount))
            },
            confirmButton = {
                Button(onClick = {
                    confirmExtract = false
                    val toExtract = selected.toList()
                    if (toExtract.isNotEmpty()) state.onExtractEntries(toExtract)
                }) { Text(stringResource(R.string.zip_manager_extract_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmExtract = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.zip_manager_delete_confirm_title)) },
            text = { Text(stringResource(R.string.zip_manager_delete_confirm, selected.size)) },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    val toDelete = selected.toList()
                    if (toDelete.isNotEmpty()) state.onRemoveEntries(toDelete)
                }) { Text(stringResource(R.string.zip_manager_delete_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}
