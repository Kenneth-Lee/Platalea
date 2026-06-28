package com.kenny.localmanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.git.RemotePubkeyInfo
import com.kenny.localmanager.git.cloneToTree
import com.kenny.localmanager.git.commitAndPush
import com.kenny.localmanager.git.deduplicateRemotePubkeys
import com.kenny.localmanager.git.deleteRemotePubkey
import com.kenny.localmanager.git.encodePublicKeyRingToArmored
import com.kenny.localmanager.git.exportPubkeyToSysgit
import com.kenny.localmanager.git.listRemotePubkeys
import com.kenny.localmanager.gpg.KeyInfo
import com.kenny.localmanager.gpg.listPublicKeyInfos
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.mergePublicKeyRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubkeyShareScreen(
    prefs: Preferences,
    rootUri: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logErrorPrefix = remember { context.getString(R.string.git_log_error, "").trimEnd() }
    val logDebugPrefix = remember { context.getString(R.string.git_log_debug_prefix) }

    var repoUrl by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var httpsPassword by remember { mutableStateOf("") }

    var syncState by remember { mutableStateOf<SyncState>(SyncState.Idle) }
    val syncLogs = remember { mutableStateListOf<String>() }

    var remotePubkeys by remember { mutableStateOf<List<RemotePubkeyInfo>>(emptyList()) }
    var localPubkeys by remember { mutableStateOf<List<KeyInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    var pendingDeleteRemote by remember { mutableStateOf<RemotePubkeyInfo?>(null) }
    var pendingImport by remember { mutableStateOf<RemotePubkeyInfo?>(null) }
    var pendingExport by remember { mutableStateOf<KeyInfo?>(null) }

    BackHandler { onDismiss() }

    // 加载配置
    LaunchedEffect(prefs) {
        repoUrl = prefs.gitRepoUrl.first() ?: ""
        userName = prefs.gitUserName.first() ?: ""
        httpsPassword = prefs.gitHttpsPassword.first() ?: ""
    }

    // 同步并加载公钥列表
    fun syncAndLoad() {
        if (rootUri.isNullOrBlank() || repoUrl.isBlank()) {
            syncState = SyncState.Error(context.getString(R.string.pubkey_share_git_not_configured))
            loading = false
            return
        }
        syncState = SyncState.Syncing
        syncLogs.clear()
        syncLogs.add(context.getString(R.string.pubkey_share_syncing_log))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                cloneToTree(
                    context = context,
                    treeRootUri = rootUri,
                    repoUrl = repoUrl,
                    userName = userName.ifBlank { null },
                    userEmail = null,
                    httpsPassword = httpsPassword.ifBlank { null },
                    log = { msg -> syncLogs.add(msg) }
                )
            }
            if (result.isSuccess) {
                // 去重公钥文件
                syncLogs.add(context.getString(R.string.pubkey_share_check_duplicates))
                val deletedCount = withContext(Dispatchers.IO) {
                    deduplicateRemotePubkeys(context, rootUri) { msg -> syncLogs.add(msg) }
                }
                if (deletedCount > 0) {
                    syncLogs.add(context.getString(R.string.pubkey_share_cleaned_duplicates, deletedCount))
                    val pushResult = withContext(Dispatchers.IO) {
                        commitAndPush(
                            context = context,
                            treeRootUri = rootUri,
                            repoUrl = repoUrl,
                            commitMessage = context.getString(R.string.pubkey_share_commit_clean_duplicates),
                            userName = userName.ifBlank { null },
                            httpsPassword = httpsPassword.ifBlank { null },
                            log = { msg -> syncLogs.add(msg) }
                        )
                    }
                    if (pushResult.isFailure) {
                        syncState = SyncState.Error(
                            context.getString(
                                R.string.pubkey_share_clean_push_failed,
                                pushResult.exceptionOrNull()?.message ?: context.getString(R.string.common_unknown_error)
                            )
                        )
                        loading = false
                        return@launch
                    }
                }
                syncState = SyncState.Success(context.getString(R.string.pubkey_share_sync_success))
                syncLogs.add(context.getString(R.string.pubkey_share_sync_success))
                // 加载公钥列表
                withContext(Dispatchers.IO) {
                    remotePubkeys = listRemotePubkeys(context, rootUri)
                    localPubkeys = listPublicKeyInfos(loadPublicKeyRings(context))
                }
            } else {
                syncState = SyncState.Error(
                    result.exceptionOrNull()?.message ?: context.getString(R.string.pubkey_share_sync_failed)
                )
            }
            loading = false
        }
    }

    // 提交并推送变更
    fun commitAndPushChanges(message: String, onComplete: (Boolean) -> Unit) {
        if (rootUri.isNullOrBlank() || repoUrl.isBlank()) {
            onComplete(false)
            return
        }
        syncState = SyncState.Syncing
        syncLogs.clear()
        syncLogs.add(context.getString(R.string.pubkey_share_committing))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                commitAndPush(
                    context = context,
                    treeRootUri = rootUri,
                    repoUrl = repoUrl,
                    commitMessage = message,
                    userName = userName.ifBlank { null },
                    httpsPassword = httpsPassword.ifBlank { null },
                    log = { msg -> syncLogs.add(msg) }
                )
            }
            if (result.isSuccess) {
                syncState = SyncState.Success(context.getString(R.string.pubkey_share_pushed))
                onComplete(true)
            } else {
                syncState = SyncState.Error(
                    result.exceptionOrNull()?.message ?: context.getString(R.string.pubkey_share_push_failed)
                )
                onComplete(false)
            }
        }
    }

    // 初始同步
    LaunchedEffect(refreshTrigger) {
        loading = true
        syncAndLoad()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pubkey_share_title)) },
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
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 同步状态 + 刷新按钮
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (val state = syncState) {
                    is SyncState.Idle -> {
                        Text(stringResource(R.string.pubkey_share_ready), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is SyncState.Syncing -> {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pubkey_share_syncing), color = MaterialTheme.colorScheme.primary)
                    }
                    is SyncState.Success -> {
                        Text(state.message, color = MaterialTheme.colorScheme.primary)
                    }
                    is SyncState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { refreshTrigger++ },
                    enabled = syncState !is SyncState.Syncing
                ) {
                    Text(stringResource(R.string.pubkey_share_refresh))
                }
            }

            // Git 日志窗口
            if (syncLogs.isNotEmpty()) {
                val logScrollState = rememberScrollState()
                LaunchedEffect(syncLogs.size) {
                    logScrollState.animateScrollTo(logScrollState.maxValue)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .padding(vertical = 4.dp)
                        .verticalScroll(logScrollState)
                ) {
                    for (line in syncLogs) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (line.startsWith(logErrorPrefix) || line.startsWith(logDebugPrefix))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 远程公钥
                    item {
                        Text(
                            stringResource(R.string.pubkey_share_remote_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (remotePubkeys.isEmpty()) {
                            Text(
                                stringResource(R.string.pubkey_share_remote_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    items(remotePubkeys, key = { it.filename }) { pk ->
                        RemotePubkeyItem(
                            pubkey = pk,
                            onImport = { pendingImport = pk },
                            onDelete = { pendingDeleteRemote = pk },
                            enabled = syncState !is SyncState.Syncing
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }

                    // 本地公钥
                    item {
                        Text(
                            stringResource(R.string.pubkey_share_local_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (localPubkeys.isEmpty()) {
                            Text(
                                stringResource(R.string.pubkey_share_local_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    items(localPubkeys, key = { it.keyId }) { pk ->
                        LocalPubkeyItem(
                            pubkey = pk,
                            alreadyExported = remotePubkeys.any { it.keyId == pk.keyId },
                            onExport = { pendingExport = pk },
                            enabled = syncState !is SyncState.Syncing
                        )
                    }
                }
            }
        }
    }

    // 导入确认对话框
    pendingImport?.let { pk ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.pubkey_share_import_title)) },
            text = { Text(stringResource(R.string.pubkey_share_import_confirm, pk.userId)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val (ok, err) = withContext(Dispatchers.IO) {
                            mergePublicKeyRing(context, pk.ring)
                        }
                        if (ok) {
                            Toast.makeText(context, context.getString(R.string.pubkey_share_import_success), Toast.LENGTH_SHORT).show()
                            localPubkeys = withContext(Dispatchers.IO) {
                                listPublicKeyInfos(loadPublicKeyRings(context))
                            }
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.pubkey_share_import_failed, err),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    pendingImport = null
                }) { Text(stringResource(R.string.pubkey_share_import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 删除远程公钥确认对话框
    pendingDeleteRemote?.let { pk ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRemote = null },
            title = { Text(stringResource(R.string.pubkey_share_delete_remote_title)) },
            text = { Text(stringResource(R.string.pubkey_share_delete_remote_confirm, pk.userId)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                deleteRemotePubkey(context, rootUri ?: "", pk.filename)
                            }
                            if (deleted) {
                                commitAndPushChanges(context.getString(R.string.pubkey_share_commit_delete, pk.userId)) { success ->
                                    if (success) {
                                        remotePubkeys = remotePubkeys.filter { it.filename != pk.filename }
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.pubkey_share_deleted_pushed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.pubkey_share_delete_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        pendingDeleteRemote = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRemote = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 导出到远程确认对话框
    pendingExport?.let { pk ->
        // 检查是否已经导出
        val alreadyExported = remotePubkeys.any { it.keyId == pk.keyId }
        if (alreadyExported) {
            AlertDialog(
                onDismissRequest = { pendingExport = null },
                title = { Text(stringResource(R.string.pubkey_share_already_exported_title)) },
                text = { Text(stringResource(R.string.pubkey_share_already_exported_message, pk.primaryUserId)) },
                confirmButton = {
                    TextButton(onClick = { pendingExport = null }) { Text(stringResource(R.string.common_ok)) }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { pendingExport = null },
                title = { Text(stringResource(R.string.pubkey_share_export_title)) },
                text = { Text(stringResource(R.string.pubkey_share_export_confirm, pk.primaryUserId)) },
                confirmButton = {
                    TextButton(onClick = {
                        val pkToExport = pk
                        pendingExport = null
                        scope.launch {
                            val rings = withContext(Dispatchers.IO) { loadPublicKeyRings(context) }
                            val ring = rings?.getPublicKeyRing(pkToExport.keyId)
                            if (ring != null) {
                                val armored = withContext(Dispatchers.IO) { encodePublicKeyRingToArmored(ring) }
                                val exported = withContext(Dispatchers.IO) {
                                    exportPubkeyToSysgit(context, rootUri ?: "", pkToExport.keyIdHex, armored, pkToExport.keyId)
                                }
                                if (exported) {
                                    commitAndPushChanges(
                                        context.getString(R.string.pubkey_share_commit_add, pkToExport.primaryUserId)
                                    ) { success ->
                                        if (success) {
                                            // 重新加载远程列表
                                            scope.launch {
                                                val newRemote = withContext(Dispatchers.IO) {
                                                    listRemotePubkeys(context, rootUri ?: "")
                                                }
                                                remotePubkeys = newRemote
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.pubkey_share_exported_pushed),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.pubkey_share_export_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.pubkey_share_key_not_found),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }) { Text(stringResource(R.string.pubkey_share_export_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingExport = null }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
    }
}

@Composable
private fun RemotePubkeyItem(
    pubkey: RemotePubkeyInfo,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    pubkey.userId,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "ID: ${pubkey.keyId.toString(16).uppercase().takeLast(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onImport, enabled = enabled) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = stringResource(R.string.pubkey_share_import_local_desc),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun LocalPubkeyItem(
    pubkey: KeyInfo,
    alreadyExported: Boolean,
    onExport: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    pubkey.primaryUserId,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "ID: ${pubkey.keyIdHex.takeLast(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (alreadyExported) {
                Text(
                    stringResource(R.string.pubkey_share_exported_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                IconButton(onClick = onExport, enabled = enabled) {
                    Icon(
                        Icons.Default.Upload,
                        contentDescription = stringResource(R.string.pubkey_share_export_remote_desc),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
