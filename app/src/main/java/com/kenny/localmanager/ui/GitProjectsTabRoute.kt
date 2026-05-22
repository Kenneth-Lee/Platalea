package com.kenny.localmanager.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.data.ManagedGitProject
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.file.resolvePathUnderRoot
import com.kenny.localmanager.git.deriveRepoUrlFromBaseRepo
import com.kenny.localmanager.git.getManagedProjectCurrentBranch
import com.kenny.localmanager.git.listManagedProjectBranches
import com.kenny.localmanager.git.ManagedGitBranchInfo
import com.kenny.localmanager.git.syncManagedProjectToTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class GitProjectsTabRouteState(
    val prefs: Preferences,
    val rootUri: String?
)

private sealed interface ManagedGitSyncState {
    data object Idle : ManagedGitSyncState
    data class Running(val message: String) : ManagedGitSyncState
    data class Success(val message: String) : ManagedGitSyncState
    data class Error(val message: String) : ManagedGitSyncState
}

@Composable
fun GitProjectsTabRoute(state: GitProjectsTabRouteState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseRepoUrl by state.prefs.gitRepoUrl.collectAsState(initial = null)
    val gitUserName by state.prefs.gitUserName.collectAsState(initial = null)
    val gitUserEmail by state.prefs.gitUserEmail.collectAsState(initial = null)
    val gitHttpsPassword by state.prefs.gitHttpsPassword.collectAsState(initial = null)
    val managedProjects by state.prefs.managedGitProjects.collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var createProjectName by remember { mutableStateOf("") }
    var creatingProject by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ManagedGitProject?>(null) }
    var busyProjectId by remember { mutableStateOf<String?>(null) }
    var syncState by remember { mutableStateOf<ManagedGitSyncState>(ManagedGitSyncState.Idle) }
    val syncLogs = remember { mutableStateListOf<String>() }
    var pendingBranchProject by remember { mutableStateOf<ManagedGitProject?>(null) }
    var branchOptions by remember { mutableStateOf<List<ManagedGitBranchInfo>>(emptyList()) }
    var selectedBranchName by remember { mutableStateOf<String?>(null) }
    var branchDialogLoading by remember { mutableStateOf(false) }

    fun formatTime(millis: Long): String {
        if (millis <= 0L) return "尚无记录"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }

    fun normalizeProjectPath(name: String): String =
        name.trim().removeSuffix(".git").trim().trim('/').split('/').map { it.trim() }.filter { it.isNotBlank() }.joinToString("/")

    fun isValidProjectPath(path: String): Boolean {
        if (path.isBlank()) return false
        val segments = path.split('/').filter { it.isNotBlank() }
        return segments.isNotEmpty() && segments.none { it == "." || it == ".." || it.contains('\\') }
    }

    fun appendLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        scope.launch {
            syncLogs.add("[$timestamp] $message")
        }
    }

    fun resetLogs(startMessage: String) {
        syncLogs.clear()
        appendLog(startMessage)
    }

    fun createManagedProject() {
        val normalizedRootUri = state.rootUri?.let { normalizeContentUriString(it) }
        val normalizedBaseRepoUrl = baseRepoUrl?.trim().orEmpty()
        val projectName = normalizeProjectPath(createProjectName)
        val localDirName = projectName
        when {
            normalizedRootUri.isNullOrBlank() -> {
                Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
                return
            }
            normalizedBaseRepoUrl.isBlank() -> {
                Toast.makeText(context, "当前没有系统 Git 基础仓库配置，无法创建", Toast.LENGTH_LONG).show()
                return
            }
            projectName.isBlank() -> {
                Toast.makeText(context, "项目名不能为空", Toast.LENGTH_SHORT).show()
                return
            }
            !isValidProjectPath(localDirName) -> {
                Toast.makeText(context, "项目名无效：支持 project 或 user/project 形式，不能包含空段、\\、. 或 ..", Toast.LENGTH_LONG).show()
                return
            }
        }
        val resolvedRootUri = normalizedRootUri!!
        creatingProject = true
        syncState = ManagedGitSyncState.Running("正在创建 Git")
        resetLogs("开始创建 Git：$projectName")
        showCreateDialog = false
        createProjectName = ""
        scope.launch {
            appendLog("正在根据基础仓库地址推导仓库 URL...")
            val repoUrlResult = deriveRepoUrlFromBaseRepo(normalizedBaseRepoUrl, projectName)
            val repoUrl = repoUrlResult.getOrElse {
                creatingProject = false
                syncState = ManagedGitSyncState.Error(it.message ?: "无法推导仓库地址")
                appendLog("错误: ${it.message ?: "无法根据基础仓库地址推导项目地址"}")
                Toast.makeText(context, it.message ?: "无法根据基础仓库地址推导项目地址", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (managedProjects.any { it.localDirName == localDirName }) {
                creatingProject = false
                syncState = ManagedGitSyncState.Error("已存在同名 Git")
                appendLog("错误: 已存在同名受管目录：$localDirName")
                Toast.makeText(context, "已存在同名受管目录：$localDirName", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (managedProjects.any { it.repoUrl == repoUrl }) {
                creatingProject = false
                syncState = ManagedGitSyncState.Error("该 Git 已在管理列表中")
                appendLog("错误: 该 Git 已经在管理列表中：$repoUrl")
                Toast.makeText(context, "该项目已经在受管列表中：$repoUrl", Toast.LENGTH_LONG).show()
                return@launch
            }
            val existingChild = resolvePathUnderRoot(context, resolvedRootUri, localDirName)
            if (existingChild != null) {
                creatingProject = false
                syncState = ManagedGitSyncState.Error("根目录已存在同名目录")
                appendLog("错误: 根目录下已存在同名目录：/$localDirName")
                Toast.makeText(context, "根目录下已存在同名目录：/$localDirName。为避免覆盖，已停止创建。", Toast.LENGTH_LONG).show()
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                syncManagedProjectToTree(
                    context = context,
                    treeRootUri = resolvedRootUri,
                    repoUrl = repoUrl,
                    localDirName = localDirName,
                    branchName = null,
                    userName = gitUserName,
                    userEmail = gitUserEmail,
                    httpsPassword = gitHttpsPassword,
                    log = { msg -> appendLog(msg) }
                )
            }
            creatingProject = false
            if (result.isSuccess) {
                val now = System.currentTimeMillis()
                val currentBranch = withContext(Dispatchers.IO) {
                    getManagedProjectCurrentBranch(context, repoUrl)
                }
                val project = ManagedGitProject(
                    id = UUID.randomUUID().toString(),
                    projectName = projectName,
                    repoUrl = repoUrl,
                    localDirName = localDirName,
                    branchName = currentBranch,
                    createdAt = now,
                    lastSyncAt = now,
                    lastPushAt = 0L
                )
                state.prefs.addManagedGitProject(project)
                syncState = ManagedGitSyncState.Success(result.getOrNull() ?: "创建成功")
                appendLog(result.getOrNull() ?: "创建成功")
                Toast.makeText(context, result.getOrNull() ?: "创建成功", Toast.LENGTH_LONG).show()
            } else {
                val message = result.exceptionOrNull()?.message ?: "创建失败"
                syncState = ManagedGitSyncState.Error(message)
                appendLog("错误: $message")
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun syncProject(project: ManagedGitProject) {
        val normalizedRootUri = state.rootUri?.let { normalizeContentUriString(it) }
        if (normalizedRootUri.isNullOrBlank()) {
            Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
            return
        }
        val resolvedRootUri = normalizedRootUri!!
        busyProjectId = project.id
        syncState = ManagedGitSyncState.Running("正在下载同步 ${project.projectName}")
        resetLogs("开始下载同步 Git：${project.projectName}")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                syncManagedProjectToTree(
                    context = context,
                    treeRootUri = resolvedRootUri,
                    repoUrl = project.repoUrl,
                    localDirName = project.localDirName,
                    branchName = project.branchName,
                    userName = gitUserName,
                    userEmail = gitUserEmail,
                    httpsPassword = gitHttpsPassword,
                    log = { msg -> appendLog(msg) }
                )
            }
            busyProjectId = null
            val message = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "同步失败"
            if (result.isSuccess) {
                val currentBranch = withContext(Dispatchers.IO) {
                    getManagedProjectCurrentBranch(context, project.repoUrl)
                }
                syncState = ManagedGitSyncState.Success(message)
                appendLog(message)
                state.prefs.updateManagedGitProject(project.copy(branchName = currentBranch, lastSyncAt = System.currentTimeMillis()))
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } else {
                syncState = ManagedGitSyncState.Error(message)
                appendLog("错误: $message")
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openBranchSwitcher(project: ManagedGitProject) {
        pendingBranchProject = project
        branchDialogLoading = true
        branchOptions = emptyList()
        selectedBranchName = project.branchName
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                listManagedProjectBranches(
                    context = context,
                    repoUrl = project.repoUrl,
                    userName = gitUserName,
                    httpsPassword = gitHttpsPassword
                )
            }
            branchDialogLoading = false
            if (result.isSuccess) {
                branchOptions = result.getOrNull().orEmpty()
                selectedBranchName = branchOptions.firstOrNull { it.isCurrent }?.name ?: project.branchName ?: branchOptions.firstOrNull()?.name
            } else {
                val message = result.exceptionOrNull()?.message ?: "加载分支失败"
                pendingBranchProject = null
                syncState = ManagedGitSyncState.Error(message)
                appendLog("错误: $message")
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun switchProjectBranch(project: ManagedGitProject, branchName: String) {
        val normalizedRootUri = state.rootUri?.let { normalizeContentUriString(it) }
        if (normalizedRootUri.isNullOrBlank()) {
            Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
            return
        }
        val resolvedRootUri = normalizedRootUri!!
        busyProjectId = project.id
        pendingBranchProject = null
        syncState = ManagedGitSyncState.Running("正在切换分支 ${project.projectName}")
        resetLogs("开始切换 Git 分支：${project.projectName} -> $branchName")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                syncManagedProjectToTree(
                    context = context,
                    treeRootUri = resolvedRootUri,
                    repoUrl = project.repoUrl,
                    localDirName = project.localDirName,
                    branchName = branchName,
                    userName = gitUserName,
                    userEmail = gitUserEmail,
                    httpsPassword = gitHttpsPassword,
                    log = { msg -> appendLog(msg) }
                )
            }
            busyProjectId = null
            val message = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "切换分支失败"
            if (result.isSuccess) {
                syncState = ManagedGitSyncState.Success(message)
                appendLog(message)
                state.prefs.updateManagedGitProject(project.copy(branchName = branchName, lastSyncAt = System.currentTimeMillis()))
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } else {
                syncState = ManagedGitSyncState.Error(message)
                appendLog("错误: $message")
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Git", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Button(onClick = { showCreateDialog = true }, enabled = busyProjectId == null && !creatingProject) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("新建 Git")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (baseRepoUrl.isNullOrBlank()) {
                "当前没有系统 Git 基础配置。需要先提供一个形如 https://host/用户/仓库.git 的基础仓库地址，才能创建新的 Git。"
            } else {
                "基础仓库：${baseRepoUrl.orEmpty()}\n新建时支持 project 或 user/project；本地目录固定与项目路径一致，如果根目录已存在同名路径，会直接报错。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (val stateValue = syncState) {
                is ManagedGitSyncState.Idle -> {
                    Text("准备就绪", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is ManagedGitSyncState.Running -> {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stateValue.message, color = MaterialTheme.colorScheme.primary)
                }
                is ManagedGitSyncState.Success -> {
                    Text(stateValue.message, color = MaterialTheme.colorScheme.primary)
                }
                is ManagedGitSyncState.Error -> {
                    Text(stateValue.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (syncLogs.isNotEmpty()) {
            val logScrollState = rememberScrollState()
            LaunchedEffect(syncLogs.size) {
                logScrollState.animateScrollTo(logScrollState.maxValue)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .verticalScroll(logScrollState)
            ) {
                for (line in syncLogs) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (line.contains("错误:")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (managedProjects.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有受管 Git。点击“新建 Git”后，会把远程仓库同步到根目录同名子目录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(managedProjects, key = { it.id }) { project ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(project.projectName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        text = project.repoUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "<当前根目录>/${project.localDirName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (busyProjectId == project.id) {
                                        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        IconButton(onClick = { pendingDelete = project }, enabled = busyProjectId == null) {
                                            Icon(Icons.Default.Delete, contentDescription = "移除管理记录")
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("上次下载同步：${formatTime(project.lastSyncAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("当前分支：${project.branchName ?: "未记录（将使用仓库当前分支）"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { syncProject(project) },
                                    modifier = Modifier.weight(1f),
                                    enabled = busyProjectId == null
                                ) {
                                    Text("下载同步")
                                }
                                OutlinedButton(
                                    onClick = { openBranchSwitcher(project) },
                                    modifier = Modifier.weight(1f),
                                    enabled = busyProjectId == null
                                ) {
                                    Text("切换分支")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!creatingProject) showCreateDialog = false
            },
            title = { Text("新建 Git") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = createProjectName,
                        onValueChange = { createProjectName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("项目名") },
                        placeholder = { Text("例如：local_manager 或 Kenneth-Lee-2025/local_manager") }
                    )
                    Text(
                        "点击创建后会立即关闭对话框，并在主页面日志区显示下载进度。项目路径支持 project 或 user/project，本地目录与项目路径一致。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (creatingProject) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("正在创建并下载项目…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { createManagedProject() }, enabled = !creatingProject) {
                    Text("创建并下载")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, enabled = !creatingProject) {
                    Text("取消")
                }
            }
        )
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("移除管理记录") },
            text = {
                Text("确定移除 Git「${project.projectName}」的管理记录吗？这不会删除当前根目录下的 /${project.localDirName}，也不会删除远程仓库。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            state.prefs.removeManagedGitProject(project.id)
                        }
                    }
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    pendingBranchProject?.let { project ->
        AlertDialog(
            onDismissRequest = {
                if (!branchDialogLoading && busyProjectId == null) {
                    pendingBranchProject = null
                }
            },
            title = { Text("切换分支") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Git：${project.projectName}\n切换分支会立即重新下载该分支内容并覆盖根目录下 /${project.localDirName}。若本地目录已有未提交改动，会直接拒绝切换。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when {
                        branchDialogLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("正在读取分支列表…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        branchOptions.isEmpty() -> {
                            Text("没有可切换的分支", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                branchOptions.forEach { branch ->
                                    val selected = selectedBranchName == branch.name
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { selectedBranchName = branch.name }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = selected, onClick = null)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(branch.name, style = MaterialTheme.typography.bodyLarge)
                                                if (branch.isCurrent) {
                                                    Text("当前缓存分支", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selected = selectedBranchName
                        if (!selected.isNullOrBlank()) {
                            switchProjectBranch(project, selected)
                        }
                    },
                    enabled = !branchDialogLoading && busyProjectId == null && !selectedBranchName.isNullOrBlank()
                ) {
                    Text("切换并同步")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingBranchProject = null },
                    enabled = !branchDialogLoading && busyProjectId == null
                ) {
                    Text("取消")
                }
            }
        )
    }
}
