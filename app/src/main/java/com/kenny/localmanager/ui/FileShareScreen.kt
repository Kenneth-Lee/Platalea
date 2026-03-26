package com.kenny.localmanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.git.SharedFileInfo
import com.kenny.localmanager.git.cloneToTree
import com.kenny.localmanager.git.commitAndPush
import com.kenny.localmanager.git.copySharedFileToRoot
import com.kenny.localmanager.git.deleteSharedFile
import com.kenny.localmanager.git.listSharedFiles
import com.kenny.localmanager.git.rootHasFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class ShareSyncState {
    object Idle : ShareSyncState()
    object Syncing : ShareSyncState()
    data class Success(val message: String) : ShareSyncState()
    data class Error(val message: String) : ShareSyncState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileShareScreen(
    prefs: Preferences,
    rootUri: String?,
    showBackButton: Boolean = true,
    autoRefreshOnEnter: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var repoUrl by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var httpsPassword by remember { mutableStateOf("") }

    var syncState by remember { mutableStateOf<ShareSyncState>(ShareSyncState.Idle) }
    val syncLogs = remember { mutableStateListOf<String>() }

    var sharedFiles by remember { mutableStateOf<List<SharedFileInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(autoRefreshOnEnter) }
    var refreshTrigger by remember { mutableStateOf(0) }

    var pendingDelete by remember { mutableStateOf<SharedFileInfo?>(null) }
    var pendingCopy by remember { mutableStateOf<SharedFileInfo?>(null) }
    var pendingCopyOverwrite by remember { mutableStateOf<SharedFileInfo?>(null) }

    BackHandler { onDismiss() }

    LaunchedEffect(prefs) {
        repoUrl = prefs.gitRepoUrl.first() ?: ""
        userName = prefs.gitUserName.first() ?: ""
        httpsPassword = prefs.gitHttpsPassword.first() ?: ""
    }

    // 先读取本地 .sysgit/share 缓存，让 Tab 进入时可立即显示已有内容。
    LaunchedEffect(rootUri) {
        if (rootUri.isNullOrBlank()) {
            sharedFiles = emptyList()
            if (!autoRefreshOnEnter) loading = false
            return@LaunchedEffect
        }
        val cached = withContext(Dispatchers.IO) {
            listSharedFiles(context, rootUri)
        }
        sharedFiles = cached
        if (!autoRefreshOnEnter) {
            loading = false
            if (cached.isNotEmpty()) {
                syncState = ShareSyncState.Success("已显示缓存内容")
            }
        }
    }

    fun syncAndLoad() {
        if (rootUri.isNullOrBlank() || repoUrl.isBlank()) {
            syncState = ShareSyncState.Error("请先配置 Git 仓库")
            loading = false
            return
        }
        syncState = ShareSyncState.Syncing
        syncLogs.clear()
        syncLogs.add("正在同步...")
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
                syncState = ShareSyncState.Success("同步成功")
                syncLogs.add("同步成功")
                withContext(Dispatchers.IO) {
                    sharedFiles = listSharedFiles(context, rootUri)
                }
            } else {
                syncState = ShareSyncState.Error(
                    result.exceptionOrNull()?.message ?: "同步失败"
                )
            }
            loading = false
        }
    }

    fun commitAndPushChanges(message: String, onComplete: (Boolean) -> Unit) {
        if (rootUri.isNullOrBlank() || repoUrl.isBlank()) {
            onComplete(false)
            return
        }
        syncState = ShareSyncState.Syncing
        syncLogs.clear()
        syncLogs.add("正在提交...")
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
                syncState = ShareSyncState.Success("已推送")
                onComplete(true)
            } else {
                syncState = ShareSyncState.Error(
                    result.exceptionOrNull()?.message ?: "推送失败"
                )
                onComplete(false)
            }
        }
    }

    LaunchedEffect(refreshTrigger, autoRefreshOnEnter) {
        if (!autoRefreshOnEnter && refreshTrigger == 0) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        syncAndLoad()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文件共享") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                    is ShareSyncState.Idle -> {
                        Text("准备就绪", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is ShareSyncState.Syncing -> {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("同步中…", color = MaterialTheme.colorScheme.primary)
                    }
                    is ShareSyncState.Success -> {
                        Text(state.message, color = MaterialTheme.colorScheme.primary)
                    }
                    is ShareSyncState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { refreshTrigger++ },
                    enabled = syncState !is ShareSyncState.Syncing
                ) {
                    Text("刷新")
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
                            color = if (line.startsWith("错误") || line.startsWith("[调试]"))
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
                if (sharedFiles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (!autoRefreshOnEnter && refreshTrigger == 0) "点击右上角刷新加载共享文件" else "暂无共享文件",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                ".sysgit/share/ (${sharedFiles.size} 个文件)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        items(sharedFiles, key = { it.filename }) { file ->
                            SharedFileItem(
                                file = file,
                                onCopy = { pendingCopy = file },
                                onDelete = { pendingDelete = file },
                                enabled = syncState !is ShareSyncState.Syncing
                            )
                        }
                    }
                }
            }
        }
    }

    // 复制到根目录确认
    pendingCopy?.let { file ->
        scope.launch(Dispatchers.IO) {
            val exists = rootHasFile(context, rootUri ?: "", file.filename)
            withContext(Dispatchers.Main) {
                if (exists) {
                    pendingCopy = null
                    pendingCopyOverwrite = file
                } else {
                    // 不存在，直接复制
                    pendingCopy = null
                    doCopyToRoot(
                        scope, context, rootUri, file, false,
                        onSuccess = {
                            Toast.makeText(context, "已复制到根目录", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = {
                            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // 覆盖确认对话框
    pendingCopyOverwrite?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingCopyOverwrite = null },
            title = { Text("文件已存在") },
            text = { Text("根目录下已存在「${file.filename}」，是否覆盖？") },
            confirmButton = {
                TextButton(onClick = {
                    val f = file
                    pendingCopyOverwrite = null
                    doCopyToRoot(
                        scope, context, rootUri, f, true,
                        onSuccess = {
                            Toast.makeText(context, "已覆盖", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = {
                            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
                        }
                    )
                }) { Text("覆盖") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCopyOverwrite = null }) { Text("取消") }
            }
        )
    }

    // 删除确认对话框
    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除共享文件") },
            text = { Text("确定要删除「${file.filename}」吗？\n删除后会自动提交并推送。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val f = file
                        pendingDelete = null
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) {
                                deleteSharedFile(context, rootUri ?: "", f.filename)
                            }
                            if (deleted) {
                                commitAndPushChanges("删除共享文件: ${f.filename}") { success ->
                                    if (success) {
                                        sharedFiles = sharedFiles.filter { it.filename != f.filename }
                                        Toast.makeText(context, "已删除并推送", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

private fun doCopyToRoot(
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    rootUri: String?,
    file: SharedFileInfo,
    overwrite: Boolean,
    onSuccess: () -> Unit,
    onFailure: () -> Unit
) {
    scope.launch {
        val ok = withContext(Dispatchers.IO) {
            copySharedFileToRoot(context, rootUri ?: "", file.filename, overwrite)
        }
        if (ok) onSuccess() else onFailure()
    }
}

@Composable
private fun SharedFileItem(
    file: SharedFileInfo,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    val sizeText = when {
        file.size < 1024 -> "${file.size} B"
        file.size < 1024 * 1024 -> "%.1f KB".format(file.size / 1024.0)
        file.size < 1024L * 1024 * 1024 -> "%.1f MB".format(file.size / (1024.0 * 1024))
        else -> "%.1f GB".format(file.size / (1024.0 * 1024 * 1024))
    }
    val dateText = if (file.lastModified > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified))
    } else {
        ""
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.filename,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    listOfNotNull(sizeText, dateText).joinToString("  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCopy, enabled = enabled) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "复制到根目录",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
