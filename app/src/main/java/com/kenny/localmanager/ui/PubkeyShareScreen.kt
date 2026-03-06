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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    var repoUrl by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var httpsPassword by remember { mutableStateOf("") }

    var syncState by remember { mutableStateOf<SyncState>(SyncState.Idle) }
    var syncMessage by remember { mutableStateOf("") }

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
            syncState = SyncState.Error("请先配置 Git 仓库")
            loading = false
            return
        }
        syncState = SyncState.Syncing
        syncMessage = "正在同步..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                cloneToTree(
                    context = context,
                    treeRootUri = rootUri,
                    repoUrl = repoUrl,
                    userName = userName.ifBlank { null },
                    userEmail = null,
                    httpsPassword = httpsPassword.ifBlank { null },
                    log = { msg -> syncMessage = msg }
                )
            }
            if (result.isSuccess) {
                // 去重公钥文件
                syncMessage = "检查重复公钥..."
                val deletedCount = withContext(Dispatchers.IO) {
                    deduplicateRemotePubkeys(context, rootUri) { msg -> syncMessage = msg }
                }
                if (deletedCount > 0) {
                    syncMessage = "清理了 $deletedCount 个重复公钥，正在推送..."
                    val pushResult = withContext(Dispatchers.IO) {
                        commitAndPush(
                            context = context,
                            treeRootUri = rootUri,
                            repoUrl = repoUrl,
                            commitMessage = "清理重复公钥",
                            userName = userName.ifBlank { null },
                            httpsPassword = httpsPassword.ifBlank { null },
                            log = { msg -> syncMessage = msg }
                        )
                    }
                    if (pushResult.isFailure) {
                        syncState = SyncState.Error("清理推送失败: ${pushResult.exceptionOrNull()?.message}")
                        loading = false
                        return@launch
                    }
                }
                syncState = SyncState.Success("同步成功")
                // 加载公钥列表
                withContext(Dispatchers.IO) {
                    remotePubkeys = listRemotePubkeys(context, rootUri)
                    localPubkeys = listPublicKeyInfos(loadPublicKeyRings(context))
                }
            } else {
                syncState = SyncState.Error(result.exceptionOrNull()?.message ?: "同步失败")
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
        syncMessage = "正在提交..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                commitAndPush(
                    context = context,
                    treeRootUri = rootUri,
                    repoUrl = repoUrl,
                    commitMessage = message,
                    userName = userName.ifBlank { null },
                    httpsPassword = httpsPassword.ifBlank { null },
                    log = { msg -> syncMessage = msg }
                )
            }
            if (result.isSuccess) {
                syncState = SyncState.Success("已推送")
                onComplete(true)
            } else {
                syncState = SyncState.Error(result.exceptionOrNull()?.message ?: "推送失败")
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
                title = { Text("公钥分享") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
            // 同步状态
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (val state = syncState) {
                    is SyncState.Idle -> {
                        Text("准备就绪", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is SyncState.Syncing -> {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(syncMessage, color = MaterialTheme.colorScheme.primary)
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
                    Text("刷新")
                }
            }

            if (syncState is SyncState.Syncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }

            Spacer(Modifier.height(16.dp))

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
                            "远程公钥 (.sysgit/pubkey/)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (remotePubkeys.isEmpty()) {
                            Text(
                                "暂无远程公钥",
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
                            "本地公钥",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (localPubkeys.isEmpty()) {
                            Text(
                                "暂无本地公钥",
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
            title = { Text("导入公钥") },
            text = { Text("确定要将「${pk.userId}」导入到本地公钥库吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val (ok, err) = withContext(Dispatchers.IO) {
                            mergePublicKeyRing(context, pk.ring)
                        }
                        if (ok) {
                            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                            localPubkeys = withContext(Dispatchers.IO) {
                                listPublicKeyInfos(loadPublicKeyRings(context))
                            }
                        } else {
                            Toast.makeText(context, "导入失败: $err", Toast.LENGTH_SHORT).show()
                        }
                    }
                    pendingImport = null
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            }
        )
    }

    // 删除远程公钥确认对话框
    pendingDeleteRemote?.let { pk ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRemote = null },
            title = { Text("删除远程公钥") },
            text = { Text("确定要删除远程公钥「${pk.userId}」吗？\n删除后会自动提交并推送。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                deleteRemotePubkey(context, rootUri ?: "", pk.filename)
                            }
                            if (deleted) {
                                commitAndPushChanges("删除公钥: ${pk.userId}") { success ->
                                    if (success) {
                                        remotePubkeys = remotePubkeys.filter { it.filename != pk.filename }
                                        Toast.makeText(context, "已删除并推送", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                        pendingDeleteRemote = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRemote = null }) { Text("取消") }
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
                title = { Text("已导出") },
                text = { Text("「${pk.primaryUserId}」已经存在于远程公钥库中。") },
                confirmButton = {
                    TextButton(onClick = { pendingExport = null }) { Text("确定") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { pendingExport = null },
                title = { Text("导出到远程") },
                text = { Text("确定要将「${pk.primaryUserId}」导出到 .sysgit/pubkey/ 吗？\n导出后会自动提交并推送。") },
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
                                    commitAndPushChanges("添加公钥: ${pkToExport.primaryUserId}") { success ->
                                        if (success) {
                                            // 重新加载远程列表
                                            scope.launch {
                                                val newRemote = withContext(Dispatchers.IO) {
                                                    listRemotePubkeys(context, rootUri ?: "")
                                                }
                                                remotePubkeys = newRemote
                                                Toast.makeText(context, "已导出并推送", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "找不到公钥", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("导出") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingExport = null }) { Text("取消") }
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
                Icon(Icons.Default.Download, contentDescription = "导入本地", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
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
                    "已导出",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                IconButton(onClick = onExport, enabled = enabled) {
                    Icon(Icons.Default.Upload, contentDescription = "导出到远程", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
