package com.kenny.localmanager.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.CacheEntry
import com.kenny.localmanager.file.clearCacheEntry
import com.kenny.localmanager.file.formatSize
import com.kenny.localmanager.file.getCacheEntries
import com.kenny.localmanager.gpg.KeyInfo
import com.kenny.localmanager.gpg.deleteAllPublicKeys
import com.kenny.localmanager.gpg.deletePublicKeyById
import com.kenny.localmanager.gpg.deleteSecretKeys
import com.kenny.localmanager.gpg.generateDefaultKey
import com.kenny.localmanager.gpg.getAllPublicKeyRingsBytes
import com.kenny.localmanager.gpg.getSecretKeyRingBytes
import com.kenny.localmanager.gpg.getSinglePublicKeyRingBytes
import com.kenny.localmanager.gpg.listGpgKeyFiles
import com.kenny.localmanager.gpg.listPublicKeyInfos
import com.kenny.localmanager.gpg.listSecretKeyInfos
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings
import com.kenny.localmanager.gpg.mergePublicKeyRing
import com.kenny.localmanager.gpg.parsePublicKeyRingFromStream
import com.kenny.localmanager.gpg.parseSecretKeyRingFromStream
import com.kenny.localmanager.gpg.saveSecretKeyRing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 配置域弹窗集：缓存管理、密钥管理（workflow 状态 + UI 两层）、生成密钥对。
 *
 * 密钥管理：状态与 IO 编排在 [KeyManagementWorkflowState]；launcher 与 UI 在
 * [KeyManagementDialog]（组合期设施留 UI 层，回调接 workflow）。
 */

/** 缓存管理弹窗：列出应用缓存目录并支持单项/全部清理（CONFIG tab 呈现）。 */
@Composable
internal fun CacheManagementDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    var entries by remember { mutableStateOf<List<CacheEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { getCacheEntries(context) }
        loading = false
    }
    fun refresh() {
        scope.launch {
            entries = withContext(Dispatchers.IO) { getCacheEntries(context) }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 400.dp)
            ) {
                Text(stringResource(R.string.cache_management_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                if (loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.padding(24.dp))
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        entries.forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.displayName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    if (entry.description.isNotBlank()) {
                                        Text(
                                            entry.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(formatSize(entry.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { clearCacheEntry(context, entry) }
                                            refresh()
                                            Toast.makeText(context, context.getString(R.string.cache_cleared_entry, entry.displayName), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) { Text(stringResource(R.string.cache_clear_entry)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (!loading && entries.isNotEmpty()) {
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    entries.forEach { clearCacheEntry(context, it) }
                                }
                                refresh()
                                Toast.makeText(context, context.getString(R.string.cache_clear_all_done), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.cache_clear_all)) }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
}

/** 密钥删除待确认目标。 */
internal sealed class PendingDelete {
    object Secret : PendingDelete()
    object AllPublic : PendingDelete()
    data class SinglePublic(val keyId: Long) : PendingDelete()
}

/**
 * 密钥管理工作流状态：列表加载、导入/导出/删除编排。
 * UI（含 ActivityResult launcher）在 [KeyManagementDialog]，回调接此处方法。
 */
internal class KeyManagementWorkflowState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onKeysChanged: () -> Unit
) {
    var publicKeys by mutableStateOf<List<KeyInfo>>(emptyList())
        private set
    var secretKeys by mutableStateOf<List<KeyInfo>>(emptyList())
        private set
    var keyFiles by mutableStateOf<List<Pair<String, Long>>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var refreshTrigger by mutableStateOf(0)
        private set

    var importAsPrivate by mutableStateOf(true)
    var pendingDelete by mutableStateOf<PendingDelete?>(null)
    var deleteConfirmInput by mutableStateOf("")
    var showGenerateKeyDialog by mutableStateOf(false)

    // 导出待办（launcher 回调消费）
    var pendingExportFilename by mutableStateOf("")
    var pendingExportSecret by mutableStateOf(false)
    var pendingExportKeyId by mutableStateOf<Long?>(null)

    fun requestRefresh() {
        refreshTrigger++
    }

    fun loadKeys() {
        scope.launch {
            loading = true
            withContext(Dispatchers.IO) {
                val pubRings = loadPublicKeyRings(context)
                val secRings = loadSecretKeyRings(context)
                publicKeys = listPublicKeyInfos(pubRings)
                secretKeys = listSecretKeyInfos(secRings)
                keyFiles = listGpgKeyFiles(context)
            }
            loading = false
        }
    }

    fun importFromUri(uri: Uri) {
        val asPriv = importAsPrivate
        scope.launch {
            val (ok, err) = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    if (asPriv) {
                        parseSecretKeyRingFromStream(bytes.inputStream())?.let { saveSecretKeyRing(context, it) }
                            ?: Pair(false, context.getString(R.string.gpg_parse_secret_failed))
                    } else {
                        parsePublicKeyRingFromStream(bytes.inputStream())?.let { mergePublicKeyRing(context, it) }
                            ?: Pair(false, context.getString(R.string.gpg_parse_public_failed))
                    }
                } ?: Pair(false, context.getString(R.string.gpg_read_file_failed))
            }
            if (ok) {
                Toast.makeText(context, context.getString(R.string.gpg_import_success), Toast.LENGTH_SHORT).show()
                requestRefresh()
                onKeysChanged()
            } else {
                Toast.makeText(context, context.getString(R.string.gpg_import_failed, err), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun requestExport(filename: String, secret: Boolean, keyId: Long?) {
        pendingExportFilename = filename
        pendingExportSecret = secret
        pendingExportKeyId = keyId
    }

    fun exportToUri(uri: Uri) {
        val filename = pendingExportFilename
        if (filename.isEmpty()) return
        val secret = pendingExportSecret
        val keyId = pendingExportKeyId
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                when {
                    secret -> getSecretKeyRingBytes(context)
                    keyId != null -> getSinglePublicKeyRingBytes(context, keyId)
                    else -> getAllPublicKeyRingsBytes(context)
                }
            }
            if (bytes != null) {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                Toast.makeText(context, context.getString(R.string.gpg_export_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.gpg_export_failed), Toast.LENGTH_SHORT).show()
            }
            pendingExportFilename = ""
            pendingExportKeyId = null
            pendingExportSecret = false
        }
    }

    fun requestDelete(target: PendingDelete) {
        pendingDelete = target
        deleteConfirmInput = ""
    }

    fun confirmDeleteIfReady() {
        val pending = pendingDelete ?: return
        if (deleteConfirmInput.trim().lowercase() != "yes") return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                when (pending) {
                    is PendingDelete.Secret -> deleteSecretKeys(context)
                    is PendingDelete.AllPublic -> deleteAllPublicKeys(context)
                    is PendingDelete.SinglePublic -> deletePublicKeyById(context, pending.keyId).first
                }
            }
            if (ok) {
                Toast.makeText(context, context.getString(R.string.browser_deleted), Toast.LENGTH_SHORT).show()
                requestRefresh()
                onKeysChanged()
            } else {
                Toast.makeText(context, context.getString(R.string.common_delete_failed), Toast.LENGTH_SHORT).show()
            }
            pendingDelete = null
            deleteConfirmInput = ""
        }
    }

    fun cancelDelete() {
        pendingDelete = null
        deleteConfirmInput = ""
    }
}

/** 密钥管理弹窗：生成/导入/导出/删除密钥（CONFIG tab 呈现，含公钥分享跳转）。 */
@Composable
internal fun KeyManagementDialog(
    context: Context,
    onDismiss: () -> Unit,
    onKeysChanged: () -> Unit = {},
    onOpenPubkeyShare: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val workflow = remember(context) { KeyManagementWorkflowState(context, scope, onKeysChanged) }
    val keyImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) workflow.importFromUri(uri)
    }
    val keyExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pgp-keys")) { uri ->
        if (uri != null) workflow.exportToUri(uri)
    }
    LaunchedEffect(workflow.refreshTrigger) {
        workflow.loadKeys()
    }
    val publicKeys = workflow.publicKeys
    val secretKeys = workflow.secretKeys
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.gpg_keys_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.gpg_keys_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { workflow.showGenerateKeyDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gpg_generate_keypair)) }
                if (onOpenPubkeyShare != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onDismiss(); onOpenPubkeyShare() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gpg_pubkey_share_git)) }
                }
                Spacer(Modifier.height(16.dp))
                if (workflow.loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.gpg_public_keys_section), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row {
                                TextButton(onClick = { workflow.importAsPrivate = false; keyImportLauncher.launch(arrayOf("application/pgp-keys", "application/octet-stream", "*/*")) }) { Text(stringResource(R.string.gpg_import_public_key)) }
                                if (publicKeys.isNotEmpty()) {
                                    TextButton(onClick = { workflow.requestDelete(PendingDelete.AllPublic) }) { Text(stringResource(R.string.gpg_delete_all_public_keys)) }
                                    TextButton(onClick = {
                                        workflow.requestExport("pubring.asc", secret = false, keyId = null)
                                        keyExportLauncher.launch("pubring.asc")
                                    }) { Text(stringResource(R.string.gpg_export_all_public_keys)) }
                                }
                            }
                        }
                        if (publicKeys.isEmpty()) Text(stringResource(R.string.gpg_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else publicKeys.forEachIndexed { _, k ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${k.keyIdHex} ${k.primaryUserId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                val keyId = k.keyId
                                TextButton(onClick = {
                                    val filename = "pubkey_${k.keyIdHex.replace("0x", "")}.asc"
                                    workflow.requestExport(filename, secret = false, keyId = keyId)
                                    keyExportLauncher.launch(filename)
                                }) { Text(stringResource(R.string.gpg_export_key), style = MaterialTheme.typography.labelSmall) }
                                if (publicKeys.size > 1) {
                                    TextButton(onClick = { workflow.requestDelete(PendingDelete.SinglePublic(keyId)) }) { Text(stringResource(R.string.gpg_delete_key_short), style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.gpg_secret_keys_section), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row {
                                TextButton(onClick = { workflow.importAsPrivate = true; keyImportLauncher.launch(arrayOf("application/pgp-keys", "application/octet-stream", "*/*")) }) { Text(stringResource(R.string.gpg_import_secret_key)) }
                                if (secretKeys.isNotEmpty()) {
                                    TextButton(onClick = {
                                        workflow.requestExport("secring.asc", secret = true, keyId = null)
                                        keyExportLauncher.launch("secring.asc")
                                    }) { Text(stringResource(R.string.gpg_export_secret_key)) }
                                    TextButton(onClick = { workflow.requestDelete(PendingDelete.Secret) }) { Text(stringResource(R.string.common_delete)) }
                                }
                            }
                        }
                        if (secretKeys.isEmpty()) Text(stringResource(R.string.gpg_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else secretKeys.forEach { k ->
                            Text("${k.keyIdHex} ${k.primaryUserId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.gpg_key_files_section), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (workflow.keyFiles.isEmpty()) Text(stringResource(R.string.gpg_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else for (pair in workflow.keyFiles) {
                            Text("${pair.first} (${pair.second} B)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
    workflow.pendingDelete?.let { pending ->
        val (title, message) = when (pending) {
            is PendingDelete.Secret -> context.getString(R.string.gpg_confirm_delete_secret_title) to context.getString(R.string.gpg_confirm_delete_secret_message)
            is PendingDelete.AllPublic -> context.getString(R.string.gpg_confirm_delete_all_public_title) to context.getString(R.string.gpg_confirm_delete_all_public_message)
            is PendingDelete.SinglePublic -> context.getString(R.string.gpg_confirm_delete_public_title) to context.getString(R.string.gpg_confirm_delete_public_message)
        }
        AlertDialog(
            onDismissRequest = workflow::cancelDelete,
            title = { Text(title) },
            text = {
                Column {
                    Text(message)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = workflow.deleteConfirmInput,
                        onValueChange = { workflow.deleteConfirmInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.gpg_confirm_delete_input_label)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = workflow::confirmDeleteIfReady,
                    enabled = workflow.deleteConfirmInput.trim().lowercase() == "yes"
                ) { Text(stringResource(R.string.gpg_confirm_delete_action)) }
            },
            dismissButton = { TextButton(onClick = workflow::cancelDelete) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    if (workflow.showGenerateKeyDialog) {
        GenerateKeyDialog(
            context = context,
            hasExistingKeys = secretKeys.isNotEmpty() || publicKeys.isNotEmpty(),
            onDismiss = { workflow.showGenerateKeyDialog = false },
            onSuccess = { workflow.requestRefresh(); onKeysChanged() }
        )
    }
}

/** 生成密钥对弹窗：身份/口令表单 + 既有密钥风险确认（KeyManagementDialog 子弹窗）。 */
@Composable
internal fun GenerateKeyDialog(
    context: Context,
    hasExistingKeys: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit = {}
) {
    var identity by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var confirmKeyRisk by remember { mutableStateOf(false) }
    var generatingInProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!generatingInProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.gpg_generate_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = identity,
                    onValueChange = { if (!generatingInProgress) identity = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.gpg_user_id_label)) },
                    singleLine = true,
                    enabled = !generatingInProgress
                )
                Spacer(Modifier.height(8.dp))
                PasswordConfirmationFields(
                    password = passphrase,
                    onPasswordChange = { if (!generatingInProgress) passphrase = it },
                    passwordLabel = context.getString(R.string.gpg_key_protect_password_label),
                    enabled = !generatingInProgress
                )
                if (hasExistingKeys) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.gpg_generate_overwrite_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = confirmKeyRisk,
                            onCheckedChange = { if (!generatingInProgress) confirmKeyRisk = it },
                            enabled = !generatingInProgress
                        )
                        Text(
                            stringResource(R.string.gpg_generate_risk_ack),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (generatingInProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.gpg_generating), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !generatingInProgress) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = {
                            if (identity.isNotBlank() && !generatingInProgress) {
                                generatingInProgress = true
                                scope.launch {
                                    val (ok, errMsg) = withContext(Dispatchers.IO) {
                                        generateDefaultKey(context, identity.trim(), passphrase.toCharArray())
                                    }
                                    generatingInProgress = false
                                    if (ok) {
                                        Toast.makeText(context, context.getString(R.string.gpg_key_generated), Toast.LENGTH_SHORT).show()
                                        onSuccess()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.gpg_generate_failed, errMsg ?: context.getString(R.string.common_unknown_error)), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !generatingInProgress && (!hasExistingKeys || confirmKeyRisk)
                    ) { Text(stringResource(R.string.gpg_generate_action)) }
                }
            }
        }
    }
}
