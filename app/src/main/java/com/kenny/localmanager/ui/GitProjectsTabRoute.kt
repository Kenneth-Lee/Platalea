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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
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

    val syncFailedDefault = stringResource(R.string.git_sync_failed_default)
    val createSuccessDefault = stringResource(R.string.git_projects_create_success)
    val createFailedDefault = stringResource(R.string.git_projects_create_failed)
    val logErrorPrefix = remember { context.getString(R.string.git_log_error, "").trimEnd() }
    val noSyncRecordLabel = stringResource(R.string.git_projects_no_sync_record)
    val branchUnknownLabel = stringResource(R.string.git_projects_branch_unknown)

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
        if (millis <= 0L) return noSyncRecordLabel
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

    fun appendError(message: String) {
        appendLog(context.getString(R.string.git_log_error, message))
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
                Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
                return
            }
            normalizedBaseRepoUrl.isBlank() -> {
                Toast.makeText(context, context.getString(R.string.git_projects_no_base_config_toast), Toast.LENGTH_LONG).show()
                return
            }
            projectName.isBlank() -> {
                Toast.makeText(context, context.getString(R.string.git_projects_name_required), Toast.LENGTH_SHORT).show()
                return
            }
            !isValidProjectPath(localDirName) -> {
                Toast.makeText(context, context.getString(R.string.git_projects_name_invalid), Toast.LENGTH_LONG).show()
                return
            }
        }
        val resolvedRootUri = requireNotNull(normalizedRootUri)
        creatingProject = true
        syncState = ManagedGitSyncState.Running(context.getString(R.string.git_projects_creating_git))
        resetLogs(context.getString(R.string.git_projects_log_create_start, projectName))
        showCreateDialog = false
        createProjectName = ""
        scope.launch {
            appendLog(context.getString(R.string.git_projects_log_derive_url))
            val repoUrlResult = deriveRepoUrlFromBaseRepo(normalizedBaseRepoUrl, projectName)
            val repoUrl = repoUrlResult.getOrElse {
                creatingProject = false
                val message = it.message ?: context.getString(R.string.git_projects_error_derive_url)
                syncState = ManagedGitSyncState.Error(message)
                appendError(message)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                return@launch
            }
            if (managedProjects.any { it.localDirName == localDirName }) {
                creatingProject = false
                val message = context.getString(R.string.git_projects_error_duplicate_git)
                syncState = ManagedGitSyncState.Error(message)
                appendError(context.getString(R.string.git_projects_error_duplicate_dir, localDirName))
                Toast.makeText(context, context.getString(R.string.git_projects_error_duplicate_dir, localDirName), Toast.LENGTH_LONG).show()
                return@launch
            }
            if (managedProjects.any { it.repoUrl == repoUrl }) {
                creatingProject = false
                val message = context.getString(R.string.git_projects_error_already_in_list)
                syncState = ManagedGitSyncState.Error(message)
                appendError(context.getString(R.string.git_projects_error_already_managed, repoUrl))
                Toast.makeText(context, context.getString(R.string.git_projects_error_already_managed, repoUrl), Toast.LENGTH_LONG).show()
                return@launch
            }
            val existingChild = resolvePathUnderRoot(context, resolvedRootUri, localDirName)
            if (existingChild != null) {
                creatingProject = false
                val message = context.getString(R.string.git_projects_error_root_dir_exists)
                syncState = ManagedGitSyncState.Error(message)
                appendError(context.getString(R.string.git_projects_error_dir_exists, localDirName))
                Toast.makeText(context, context.getString(R.string.git_projects_error_dir_exists, localDirName), Toast.LENGTH_LONG).show()
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
                val message = result.getOrNull() ?: createSuccessDefault
                syncState = ManagedGitSyncState.Success(message)
                appendLog(message)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } else {
                val message = result.exceptionOrNull()?.message ?: createFailedDefault
                syncState = ManagedGitSyncState.Error(message)
                appendError(message)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun syncProject(project: ManagedGitProject) {
        val normalizedRootUri = state.rootUri?.let { normalizeContentUriString(it) }
        if (normalizedRootUri.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
            return
        }
        val resolvedRootUri = requireNotNull(normalizedRootUri)
        busyProjectId = project.id
        syncState = ManagedGitSyncState.Running(context.getString(R.string.git_projects_sync_running, project.projectName))
        resetLogs(context.getString(R.string.git_projects_log_sync_start, project.projectName))
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
            val message = result.getOrNull() ?: result.exceptionOrNull()?.message ?: syncFailedDefault
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
                appendError(message)
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
                val message = result.exceptionOrNull()?.message ?: context.getString(R.string.git_projects_load_branches_failed)
                pendingBranchProject = null
                syncState = ManagedGitSyncState.Error(message)
                appendError(message)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun switchProjectBranch(project: ManagedGitProject, branchName: String) {
        val normalizedRootUri = state.rootUri?.let { normalizeContentUriString(it) }
        if (normalizedRootUri.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
            return
        }
        val resolvedRootUri = requireNotNull(normalizedRootUri)
        busyProjectId = project.id
        pendingBranchProject = null
        syncState = ManagedGitSyncState.Running(context.getString(R.string.git_projects_switch_running, project.projectName))
        resetLogs(context.getString(R.string.git_projects_log_switch_start, project.projectName, branchName))
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
            val switchFailed = context.getString(R.string.git_projects_switch_failed)
            val message = result.getOrNull() ?: result.exceptionOrNull()?.message ?: switchFailed
            if (result.isSuccess) {
                syncState = ManagedGitSyncState.Success(message)
                appendLog(message)
                state.prefs.updateManagedGitProject(project.copy(branchName = branchName, lastSyncAt = System.currentTimeMillis()))
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } else {
                syncState = ManagedGitSyncState.Error(message)
                appendError(message)
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
            Text(stringResource(R.string.main_tab_git_projects), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Button(onClick = { showCreateDialog = true }, enabled = busyProjectId == null && !creatingProject) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.git_projects_create_new))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (baseRepoUrl.isNullOrBlank()) {
                stringResource(R.string.git_projects_no_base_config)
            } else {
                stringResource(R.string.git_projects_base_config_hint, baseRepoUrl.orEmpty())
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
                    Text(stringResource(R.string.git_projects_ready), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        color = if (line.contains(logErrorPrefix)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (managedProjects.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.git_projects_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        text = stringResource(R.string.git_projects_local_path, project.localDirName),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (busyProjectId == project.id) {
                                        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        IconButton(onClick = { pendingDelete = project }, enabled = busyProjectId == null) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.git_projects_remove_record_desc)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.git_projects_last_sync, formatTime(project.lastSyncAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(
                                    R.string.git_projects_current_branch,
                                    project.branchName ?: branchUnknownLabel
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { syncProject(project) },
                                    modifier = Modifier.weight(1f),
                                    enabled = busyProjectId == null
                                ) {
                                    Text(stringResource(R.string.git_projects_sync_download))
                                }
                                OutlinedButton(
                                    onClick = { openBranchSwitcher(project) },
                                    modifier = Modifier.weight(1f),
                                    enabled = busyProjectId == null
                                ) {
                                    Text(stringResource(R.string.git_projects_switch_branch))
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
            title = { Text(stringResource(R.string.git_projects_create_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = createProjectName,
                        onValueChange = { createProjectName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.git_projects_project_name_label)) },
                        placeholder = { Text(stringResource(R.string.git_projects_project_name_placeholder)) }
                    )
                    Text(
                        stringResource(R.string.git_projects_create_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (creatingProject) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.git_projects_creating), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { createManagedProject() }, enabled = !creatingProject) {
                    Text(stringResource(R.string.git_projects_create_and_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, enabled = !creatingProject) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.git_projects_remove_title)) },
            text = {
                Text(stringResource(R.string.git_projects_remove_confirm, project.projectName, project.localDirName))
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
                    Text(stringResource(R.string.git_projects_remove_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
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
            title = { Text(stringResource(R.string.git_projects_switch_branch_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.git_projects_switch_branch_hint, project.projectName, project.localDirName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when {
                        branchDialogLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.git_projects_loading_branches), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        branchOptions.isEmpty() -> {
                            Text(stringResource(R.string.git_projects_no_branches), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                                                    Text(
                                                        stringResource(R.string.git_projects_cached_branch),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
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
                    Text(stringResource(R.string.git_projects_switch_and_sync))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingBranchProject = null },
                    enabled = !branchDialogLoading && busyProjectId == null
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
