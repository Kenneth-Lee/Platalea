package com.kenny.localmanager.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kenny.localmanager.git.cloneToTree
import com.kenny.localmanager.git.deleteLocalGitCache
import com.kenny.localmanager.git.deleteSysgitFromTree
import com.kenny.localmanager.git.hasLocalGitCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    data class Success(val message: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

@Composable
fun GitConfigDialog(
    prefs: Preferences,
    rootUri: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var repoUrl by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var httpsPassword by remember { mutableStateOf("") }
    var configApplied by remember { mutableStateOf(false) }
    var configDirty by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<SyncStatus>(SyncStatus.Idle) }
    var progressText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 加载保存的配置
    LaunchedEffect(prefs) {
        repoUrl = prefs.gitRepoUrl.first() ?: ""
        userName = prefs.gitUserName.first() ?: ""
        userEmail = prefs.gitUserEmail.first() ?: ""
        httpsPassword = prefs.gitHttpsPassword.first() ?: ""
        configApplied = prefs.gitConfigApplied.first()
        configDirty = false
    }

    fun saveConfig() {
        scope.launch {
            prefs.setGitRepoUrl(repoUrl)
            prefs.setGitUserName(userName)
            prefs.setGitUserEmail(userEmail)
            prefs.setGitHttpsPassword(httpsPassword)
        }
    }

    fun markConfigDirty() {
        configDirty = true
        if (configApplied) {
            configApplied = false
            scope.launch { prefs.setGitConfigApplied(false) }
        }
    }

    fun applyConfig() {
        if (rootUri.isNullOrBlank()) {
            Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
            return
        }
        if (repoUrl.isBlank()) {
            Toast.makeText(context, "请填写仓库地址", Toast.LENGTH_SHORT).show()
            return
        }
        saveConfig()
        syncStatus = SyncStatus.Syncing
        progressText = "正在连接..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                cloneToTree(
                    context = context,
                    treeRootUri = rootUri,
                    repoUrl = repoUrl,
                    userName = userName.ifBlank { null },
                    userEmail = userEmail.ifBlank { null },
                    httpsPassword = httpsPassword.ifBlank { null },
                    log = { msg -> progressText = msg }
                )
            }
            if (result.isSuccess) {
                syncStatus = SyncStatus.Success(result.getOrNull() ?: "同步成功")
                configApplied = true
                configDirty = false
                prefs.setGitConfigApplied(true)
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: "同步失败"
                syncStatus = SyncStatus.Error(errMsg)
            }
        }
    }

    fun deleteLocalRepo() {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (repoUrl.isNotBlank()) {
                    deleteLocalGitCache(context, repoUrl)
                }
                if (!rootUri.isNullOrBlank()) {
                    deleteSysgitFromTree(context, rootUri)
                }
            }
            configApplied = false
            prefs.setGitConfigApplied(false)
            syncStatus = SyncStatus.Idle
            Toast.makeText(context, "已删除本地仓库", Toast.LENGTH_SHORT).show()
        }
        showDeleteConfirm = false
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Git 配置", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("克隆/同步到根目录 .sysgit，仅支持 HTTPS。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                // 仓库地址
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("仓库地址", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it; markConfigDirty() },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("https://gitcode.com/用户/仓库.git") }
                    )
                }
                Spacer(Modifier.height(8.dp))

                // HTTPS 密码
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("HTTPS 密码", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = httpsPassword,
                        onValueChange = { httpsPassword = it; markConfigDirty() },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("私有库需填令牌/密码") }
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Git 用户名
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Git 用户名", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it; markConfigDirty() },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("本地 commit 显示名") }
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Git 邮箱
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Git 邮箱", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = userEmail,
                        onValueChange = { userEmail = it; markConfigDirty() },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("本地 commit 邮箱") }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 状态显示
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("状态: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    when (val status = syncStatus) {
                        is SyncStatus.Idle -> {
                            if (repoUrl.isBlank()) {
                                Text("未配置", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (configApplied && !configDirty) {
                                Text("已同步", color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("配置未应用", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        is SyncStatus.Syncing -> {
                            CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("同步中...", color = MaterialTheme.colorScheme.primary)
                        }
                        is SyncStatus.Success -> {
                            Text("已同步", color = MaterialTheme.colorScheme.primary)
                        }
                        is SyncStatus.Error -> {
                            Text("同步失败", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // 进度/错误信息
                if (syncStatus is SyncStatus.Syncing || syncStatus is SyncStatus.Error) {
                    Spacer(Modifier.height(8.dp))
                    if (syncStatus is SyncStatus.Syncing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = if (syncStatus is SyncStatus.Syncing) progressText else (syncStatus as? SyncStatus.Error)?.message ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncStatus is SyncStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(24.dp))

                // 操作按钮
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { applyConfig() },
                        enabled = syncStatus !is SyncStatus.Syncing && repoUrl.isNotBlank()
                    ) {
                        Text("应用配置")
                    }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = syncStatus !is SyncStatus.Syncing,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("删除本地仓库")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        saveConfig()
                        onDismiss()
                    }) {
                        Text("关闭")
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除本地 .sysgit 目录和缓存吗？\n这可以解决本地冲突问题，但需要重新同步。") },
            confirmButton = {
                TextButton(
                    onClick = { deleteLocalRepo() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}
