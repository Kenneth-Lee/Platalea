package com.kenny.localmanager.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kenny.localmanager.git.cloneToTree
import com.kenny.localmanager.git.deleteLocalGitCache
import com.kenny.localmanager.git.deleteSysgitFromTree
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
    val syncSuccessDefault = stringResource(R.string.git_sync_success_default)
    val syncFailedDefault = stringResource(R.string.git_sync_failed_default)
    val logErrorPrefix = remember { context.getString(R.string.git_log_error, "").trimEnd() }
    val logDebugPrefix = stringResource(R.string.git_log_debug_prefix)

    var repoUrl by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var httpsPassword by remember { mutableStateOf("") }
    var configApplied by remember { mutableStateOf(false) }
    var configDirty by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<SyncStatus>(SyncStatus.Idle) }
    val syncLogs = remember { mutableStateListOf<String>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        repoUrl = prefs.gitRepoUrl.first() ?: ""
        userName = prefs.gitUserName.first() ?: ""
        userEmail = prefs.gitUserEmail.first() ?: ""
        httpsPassword = prefs.gitHttpsPassword.first() ?: ""
        configApplied = prefs.gitConfigApplied.first()
        configDirty = false
    }

    fun persistConfig(
        newRepoUrl: String = repoUrl,
        newUserName: String = userName,
        newUserEmail: String = userEmail,
        newHttpsPassword: String = httpsPassword
    ) {
        scope.launch {
            prefs.setGitRepoUrl(newRepoUrl)
            prefs.setGitUserName(newUserName)
            prefs.setGitUserEmail(newUserEmail)
            prefs.setGitHttpsPassword(newHttpsPassword)
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
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
            return
        }
        if (repoUrl.isBlank()) {
            Toast.makeText(context, context.getString(R.string.git_config_repo_url_required), Toast.LENGTH_SHORT).show()
            return
        }
        persistConfig()
        syncStatus = SyncStatus.Syncing
        syncLogs.clear()
        syncLogs.add(context.getString(R.string.git_config_connecting))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                cloneToTree(
                    context = context,
                    treeRootUri = rootUri,
                    repoUrl = repoUrl,
                    userName = userName.ifBlank { null },
                    userEmail = userEmail.ifBlank { null },
                    httpsPassword = httpsPassword.ifBlank { null },
                    log = { msg -> syncLogs.add(msg) }
                )
            }
            if (result.isSuccess) {
                syncStatus = SyncStatus.Success(result.getOrNull() ?: syncSuccessDefault)
                configApplied = true
                configDirty = false
                prefs.setGitConfigApplied(true)
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: syncFailedDefault
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
            syncStatus = SyncStatus.Idle
            syncLogs.clear()
            prefs.setGitConfigApplied(false)
            Toast.makeText(context, context.getString(R.string.git_config_local_deleted), Toast.LENGTH_SHORT).show()
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
                Text(stringResource(R.string.git_config_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.git_config_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.git_config_repo_url_label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = {
                            repoUrl = it
                            persistConfig(newRepoUrl = it)
                            markConfigDirty()
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.git_config_repo_url_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.git_config_https_password_label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = httpsPassword,
                        onValueChange = {
                            httpsPassword = it
                            persistConfig(newHttpsPassword = it)
                            markConfigDirty()
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.git_config_https_password_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.git_config_user_name_label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = userName,
                        onValueChange = {
                            userName = it
                            persistConfig(newUserName = it)
                            markConfigDirty()
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.git_config_user_name_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.git_config_user_email_label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(100.dp))
                    OutlinedTextField(
                        value = userEmail,
                        onValueChange = {
                            userEmail = it
                            persistConfig(newUserEmail = it)
                            markConfigDirty()
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.git_config_user_email_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.git_config_status_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    when (syncStatus) {
                        is SyncStatus.Idle -> {
                            if (repoUrl.isBlank()) {
                                Text(stringResource(R.string.git_config_status_not_configured), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (configApplied && !configDirty) {
                                Text(stringResource(R.string.git_config_status_synced), color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text(stringResource(R.string.git_config_status_dirty), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        is SyncStatus.Syncing -> {
                            CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.git_config_status_syncing), color = MaterialTheme.colorScheme.primary)
                        }
                        is SyncStatus.Success -> {
                            Text(stringResource(R.string.git_config_status_synced), color = MaterialTheme.colorScheme.primary)
                        }
                        is SyncStatus.Error -> {
                            Text(stringResource(R.string.git_config_status_sync_failed), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (syncLogs.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val logScrollState = rememberScrollState()
                    LaunchedEffect(syncLogs.size) {
                        logScrollState.animateScrollTo(logScrollState.maxValue)
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
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

                Spacer(Modifier.height(24.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { applyConfig() },
                            enabled = syncStatus !is SyncStatus.Syncing && repoUrl.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.git_config_apply))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = syncStatus !is SyncStatus.Syncing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.git_config_delete_local))
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.common_close))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.git_config_delete_confirm_title)) },
            text = { Text(stringResource(R.string.git_config_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = { deleteLocalRepo() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
