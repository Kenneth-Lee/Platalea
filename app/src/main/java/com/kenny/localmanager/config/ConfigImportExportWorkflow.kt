package com.kenny.localmanager.config

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.data.ConfigExportCategory
import com.kenny.localmanager.data.ConfigPlaylistImportMode
import com.kenny.localmanager.data.configJsonCategories
import com.kenny.localmanager.data.configJsonContainsKeys
import com.kenny.localmanager.data.configJsonPlaylistCount
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.GpgHelper.GpgEncryptedKind
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kenny.localmanager.ui.ReliablePasswordInputField
import com.kenny.localmanager.ui.normalizeContentUriString

/**
 * 配置导入 / 导出工作流的状态持有者。
 *
 * 属于 Config 域的独立工作流（见 docs/主界面Tab与Route分层改造方案.md §3.3）：
 * 一次导入或导出是短生命周期流程，结束后销毁全部状态。
 * 由 AppShell 持有实例，通过 [rememberConfigImportExportWorkflow] 创建；
 * 触发入口：
 * - BrowserTab 选中导出文件 → [startImportFromJson]
 * - ConfigTab 导出按钮 → [openExportDialog]
 * 工作流写盘后通过 onConfigFileWritten 通知目录刷新。
 */
@Stable
class ConfigImportExportWorkflowState internal constructor(
    private val context: Context,
    private val config: ConfigEditingView,
    private val scope: CoroutineScope,
    private val rootUriProvider: () -> String?,
    private val onConfigFileWritten: () -> Unit
) {
    // ---- 导入流程状态 ----（对话框 UI 在同模块 ConfigImportExportDialogs 中写入）
    var showImportCategoryDialog by mutableStateOf(false)
        internal set
    var showImportKeyConfirmDialog by mutableStateOf(false)
        internal set
    var showImportPlaylistConfirmDialog by mutableStateOf(false)
        internal set
    var pendingImportJson by mutableStateOf<String?>(null)
        private set
    var pendingImportAvailableCategories by mutableStateOf<Set<ConfigExportCategory>>(emptySet())
        private set
    var pendingImportSelectedCategories by mutableStateOf<Set<ConfigExportCategory>>(emptySet())
        private set
    var pendingImportPlaylistCount by mutableIntStateOf(0)
        private set
    var pendingImportPlaylistMode by mutableStateOf(ConfigPlaylistImportMode.OVERWRITE)
        internal set

    // ---- 导入解密状态（.json.gpg 文件）----
    /** 待解密的密文（对称加密导出文件），解密成功后清空。 */
    var pendingEncryptedBytes by mutableStateOf<ByteArray?>(null)
        private set
    var showImportPasswordDialog by mutableStateOf(false)
        internal set
    var importPassword by mutableStateOf("")
        internal set

    // ---- 导出流程状态 ----
    var showExportConfigDialog by mutableStateOf(false)
        internal set
    var exportConfigFileName by mutableStateOf(CONFIG_EXPORT_FILE_NAME)
        internal set
    /** 导出时默认启用 GPG 对称加密，生成 .json.gpg 文件。 */
    var exportEncrypt by mutableStateOf(true)
        internal set
    var exportPassword by mutableStateOf("")
        internal set
    var exportGitCategory by mutableStateOf(true)
        internal set
    var exportMusicCategory by mutableStateOf(true)
        internal set
    var exportRecentCategory by mutableStateOf(true)
        internal set
    var exportEpubCategory by mutableStateOf(true)
        internal set
    var exportGpgCategory by mutableStateOf(true)
        internal set
    var exportOtherCategory by mutableStateOf(true)
        internal set

    fun toggleImportCategory(category: ConfigExportCategory, checked: Boolean) {
        pendingImportSelectedCategories =
            if (checked) pendingImportSelectedCategories + category
            else pendingImportSelectedCategories - category
    }

    fun clearImportState() {
        pendingImportJson = null
        pendingImportAvailableCategories = emptySet()
        pendingImportSelectedCategories = emptySet()
        pendingImportPlaylistCount = 0
        pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
        showImportCategoryDialog = false
        showImportKeyConfirmDialog = false
        showImportPlaylistConfirmDialog = false
        pendingEncryptedBytes = null
        importPassword = ""
        showImportPasswordDialog = false
    }

    fun startImportFromJson(jsonString: String) {
        if (jsonString.isBlank()) {
            Toast.makeText(context, context.getString(R.string.browser_config_import_read_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val categories = configJsonCategories(jsonString)
        if (categories.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.browser_config_import_no_category), Toast.LENGTH_SHORT).show()
            return
        }
        pendingImportJson = jsonString
        pendingImportAvailableCategories = categories
        pendingImportSelectedCategories = categories
        pendingImportPlaylistCount = 0
        pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
        showImportCategoryDialog = true
    }

    /**
     * 从文件 uri 启动导入：自动识别 GPG 加密（.json.gpg）与明文 .json。
     * 对称加密的文件先弹密码框解密，再进入分类选择流程。
     */
    fun startImportFromUri(uri: android.net.Uri) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (_: Exception) {
                    null
                }
            }
            if (bytes == null || bytes.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.browser_config_import_read_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            when (withContext(Dispatchers.IO) { GpgHelper.detectEncryptedKind(ByteArrayInputStream(bytes)) }) {
                GpgEncryptedKind.SYMMETRIC -> {
                    pendingEncryptedBytes = bytes
                    importPassword = ""
                    showImportPasswordDialog = true
                }
                GpgEncryptedKind.PUBLIC_KEY -> {
                    val plain = withContext(Dispatchers.IO) {
                        GpgHelper.decryptStream(ByteArrayInputStream(bytes))
                    }
                    if (plain == null) {
                        Toast.makeText(context, context.getString(R.string.config_import_pubkey_failed), Toast.LENGTH_LONG).show()
                    } else {
                        startImportFromJson(String(plain, Charsets.UTF_8))
                    }
                }
                else -> startImportFromJson(String(bytes, Charsets.UTF_8))
            }
        }
    }

    /** 用密码解密待导入密文：成功进入分类选择，失败提示并保留密码框供重试。 */
    internal fun tryDecryptPendingImport(password: String) {
        val bytes = pendingEncryptedBytes ?: return
        if (password.isBlank()) return
        scope.launch {
            val plain = withContext(Dispatchers.IO) {
                GpgHelper.decryptSymmetric(ByteArrayInputStream(bytes), password.toCharArray())
            }
            if (plain == null) {
                Toast.makeText(context, context.getString(R.string.config_import_decrypt_failed), Toast.LENGTH_LONG).show()
            } else {
                pendingEncryptedBytes = null
                showImportPasswordDialog = false
                startImportFromJson(String(plain, Charsets.UTF_8))
            }
        }
    }

    fun openExportDialog() {
        exportConfigFileName = CONFIG_EXPORT_FILE_NAME
        exportEncrypt = true
        exportPassword = ""
        exportGitCategory = true
        exportMusicCategory = true
        exportRecentCategory = true
        exportEpubCategory = true
        exportGpgCategory = true
        exportOtherCategory = true
        showExportConfigDialog = true
    }

    internal fun continueImportAfterPlaylistChoice() {
        val jsonString = pendingImportJson ?: return
        val selectedCategories = pendingImportSelectedCategories
        if (ConfigExportCategory.GPG in selectedCategories && configJsonContainsKeys(jsonString)) {
            showImportKeyConfirmDialog = true
        } else {
            scope.launch { performImport(jsonString, importKeys = false, categories = selectedCategories) }
        }
    }

    internal fun continueImportAfterCategoryChoice() {
        val jsonString = pendingImportJson ?: return
        val selectedCategories = pendingImportSelectedCategories
        scope.launch {
            val hasExistingPlaylists = withContext(Dispatchers.IO) { config.hasExistingPlaylists() }
            val playlistCount = if (ConfigExportCategory.MUSIC in selectedCategories) configJsonPlaylistCount(jsonString) else 0
            pendingImportPlaylistCount = playlistCount
            if (playlistCount > 0 && hasExistingPlaylists) {
                pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
                showImportPlaylistConfirmDialog = true
            } else {
                pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
                continueImportAfterPlaylistChoice()
            }
        }
    }

    internal suspend fun performImport(
        jsonString: String,
        importKeys: Boolean,
        categories: Set<ConfigExportCategory>
    ) {
        val ok = config.importConfigJson(
            jsonString,
            importKeys = importKeys,
            playlistImportMode = pendingImportPlaylistMode,
            categories = categories
        )
        onConfigFileWritten()
        val msg = when {
            !ok -> context.getString(R.string.browser_config_import_json_failed)
            ConfigExportCategory.MUSIC in categories && pendingImportPlaylistCount > 0 && pendingImportPlaylistMode == ConfigPlaylistImportMode.APPEND && importKeys -> context.getString(R.string.browser_config_imported_music_append_keys)
            ConfigExportCategory.MUSIC in categories && pendingImportPlaylistCount > 0 && pendingImportPlaylistMode == ConfigPlaylistImportMode.APPEND -> context.getString(R.string.browser_config_imported_music_append)
            ConfigExportCategory.MUSIC in categories && pendingImportPlaylistCount > 0 && importKeys -> context.getString(R.string.browser_config_imported_music_replace_keys)
            ConfigExportCategory.MUSIC in categories && pendingImportPlaylistCount > 0 -> context.getString(R.string.browser_config_imported_music_replace)
            ConfigExportCategory.GPG in categories && importKeys -> context.getString(R.string.browser_config_imported_with_keys)
            pendingImportPlaylistCount > 0 && pendingImportPlaylistMode == ConfigPlaylistImportMode.APPEND -> context.getString(R.string.browser_config_imported_playlist_append)
            else -> context.getString(R.string.browser_config_imported)
        }
        clearImportState()
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    internal fun scopeLaunchImportKeys(jsonString: String, importKeys: Boolean) {
        scope.launch {
            performImport(jsonString, importKeys = importKeys, categories = pendingImportSelectedCategories)
        }
    }

    internal fun launchExport(fileName: String, categories: Set<ConfigExportCategory>, password: String?) {
        scope.launch {
            when (exportToRoot(fileName, categories, password)) {
                ConfigExportResult.SUCCESS -> {
                    Toast.makeText(context, context.getString(R.string.config_export_success), Toast.LENGTH_SHORT).show()
                    onConfigFileWritten()
                }
                ConfigExportResult.FILE_EXISTS -> {
                    Toast.makeText(context, context.getString(R.string.config_export_file_exists, fileName), Toast.LENGTH_LONG).show()
                }
                ConfigExportResult.ENCRYPT_FAILED -> {
                    Toast.makeText(context, context.getString(R.string.config_export_encrypt_failed), Toast.LENGTH_SHORT).show()
                }
                ConfigExportResult.FAILED -> {
                    Toast.makeText(context, context.getString(R.string.config_export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    internal suspend fun exportToRoot(fileName: String, categories: Set<ConfigExportCategory>, password: String?): ConfigExportResult {
        if (categories.isEmpty()) return ConfigExportResult.FAILED
        val targetRoot = rootUriProvider()?.let { normalizeContentUriString(it) } ?: return ConfigExportResult.FAILED
        val targetUri = android.net.Uri.parse(targetRoot)
        val jsonBytes = config.exportConfigJson(categories).toByteArray(Charsets.UTF_8)
        val (writeBytes, mime) = if (password != null) {
            val encrypted = withContext(Dispatchers.IO) {
                GpgHelper.encryptSymmetric(jsonBytes, password.toCharArray(), fileName)
            } ?: return ConfigExportResult.ENCRYPT_FAILED
            encrypted to "application/octet-stream"
        } else {
            jsonBytes to "application/json"
        }
        val existing = findChildByName(context, targetUri, fileName)
        if (existing != null) return ConfigExportResult.FILE_EXISTS
        return if (createFileWithBytes(
                context,
                targetUri,
                targetUri,
                fileName,
                mime,
                writeBytes
            )) ConfigExportResult.SUCCESS else ConfigExportResult.FAILED
    }
}

/**
 * 规范化导出文件名：加密导出补齐 .json.gpg 后缀，明文导出补齐 .json。
 */
internal fun normalizeConfigExportFileName(input: String, encrypt: Boolean): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank() || trimmed.contains('/') || trimmed.contains('\\')) return null
    val hasJson = trimmed.endsWith(".json", ignoreCase = true)
    val hasJsonGpg = trimmed.endsWith(".json.gpg", ignoreCase = true)
    return when {
        encrypt -> when {
            hasJsonGpg -> trimmed
            hasJson -> "$trimmed.gpg"
            else -> "$trimmed.json.gpg"
        }
        else -> when {
            hasJsonGpg -> trimmed.dropLast(".gpg".length)
            hasJson -> trimmed
            else -> "$trimmed.json"
        }
    }
}

/**
 * 配置导入 / 导出模块（功能模块化）：组合根（配置 tab 装配处）attach 一行
 * 接线；导出入口（配置页按钮）与目录域另存链路直呼 service。
 * rootUri 变化不影响进行中的流程（导出时实时读取）。
 */
internal object ConfigImportExportModule {
    private var instance: ConfigImportExportWorkflowState? = null

    val service: ConfigImportExportWorkflowState
        get() = instance ?: error("ConfigImportExportModule 未接线：需先 attach")

    fun attach(
        context: android.content.Context,
        config: ConfigEditingView,
        scope: CoroutineScope,
        rootUriProvider: () -> String?,
        onConfigFileWritten: () -> Unit
    ) {
        instance = ConfigImportExportWorkflowState(context, config, scope, rootUriProvider, onConfigFileWritten)
    }

    fun resetForTest() {
        instance = null
    }
}
internal const val CONFIG_EXPORT_FILE_NAME = "local_manager_config.json"

internal enum class ConfigExportResult {
    SUCCESS,
    FILE_EXISTS,
    ENCRYPT_FAILED,
    FAILED
}


/** 渲染配置导入 / 导出工作流的全部对话框。无活动流程时不显示任何内容。 */
@Composable
fun ConfigImportExportDialogs(state: ConfigImportExportWorkflowState) {
    val context = LocalContext.current
    if (state.showImportCategoryDialog && state.pendingImportJson != null) {
        AlertDialog(
            onDismissRequest = { state.clearImportState() },
            title = { Text(context.getString(R.string.config_import_category_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = context.getString(R.string.config_import_category_hint),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (ConfigExportCategory.GIT in state.pendingImportAvailableCategories) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = ConfigExportCategory.GIT in state.pendingImportSelectedCategories,
                                onCheckedChange = { checked ->
                                    state.toggleImportCategory(ConfigExportCategory.GIT, checked)
                                }
                            )
                            Text(context.getString(R.string.config_export_category_git))
                        }
                    }
                    if (ConfigExportCategory.MUSIC in state.pendingImportAvailableCategories) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = ConfigExportCategory.MUSIC in state.pendingImportSelectedCategories,
                                onCheckedChange = { checked ->
                                    state.toggleImportCategory(ConfigExportCategory.MUSIC, checked)
                                }
                            )
                            Text(context.getString(R.string.config_export_category_music))
                        }
                    }
                    if (ConfigExportCategory.RECENT in state.pendingImportAvailableCategories) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = ConfigExportCategory.RECENT in state.pendingImportSelectedCategories,
                                onCheckedChange = { checked ->
                                    state.toggleImportCategory(ConfigExportCategory.RECENT, checked)
                                }
                            )
                            Text(context.getString(R.string.config_export_category_recent))
                        }
                    }
                    if (ConfigExportCategory.EPUB in state.pendingImportAvailableCategories) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = ConfigExportCategory.EPUB in state.pendingImportSelectedCategories,
                                onCheckedChange = { checked ->
                                    state.toggleImportCategory(ConfigExportCategory.EPUB, checked)
                                }
                            )
                            Text(context.getString(R.string.config_export_category_epub))
                        }
                    }
                    if (ConfigExportCategory.GPG in state.pendingImportAvailableCategories) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = ConfigExportCategory.GPG in state.pendingImportSelectedCategories,
                                onCheckedChange = { checked ->
                                    state.toggleImportCategory(ConfigExportCategory.GPG, checked)
                                }
                            )
                            Text(context.getString(R.string.config_export_category_gpg))
                        }
                    }
                    if (ConfigExportCategory.OTHER in state.pendingImportAvailableCategories) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = ConfigExportCategory.OTHER in state.pendingImportSelectedCategories,
                                onCheckedChange = { checked ->
                                    state.toggleImportCategory(ConfigExportCategory.OTHER, checked)
                                }
                            )
                            Text(context.getString(R.string.config_export_category_other))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (state.pendingImportSelectedCategories.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.config_import_select_one_category), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    state.showImportCategoryDialog = false
                    state.continueImportAfterCategoryChoice()
                }) { Text(context.getString(R.string.common_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { state.clearImportState() }) { Text(context.getString(R.string.common_cancel)) }
            }
        )
    }
    if (state.showImportPlaylistConfirmDialog && state.pendingImportJson != null) {
        AlertDialog(
            onDismissRequest = { state.clearImportState() },
            title = { Text(stringResource(R.string.browser_import_playlist_title)) },
            text = {
                Text(
                    stringResource(R.string.browser_import_playlist_prompt, state.pendingImportPlaylistCount),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(onClick = {
                    state.showImportPlaylistConfirmDialog = false
                    state.pendingImportPlaylistMode = ConfigPlaylistImportMode.APPEND
                    state.continueImportAfterPlaylistChoice()
                }) { Text(stringResource(R.string.browser_append)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        state.showImportPlaylistConfirmDialog = false
                        state.pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
                        state.continueImportAfterPlaylistChoice()
                    }) { Text(stringResource(R.string.browser_overwrite)) }
                    TextButton(onClick = { state.clearImportState() }) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        )
    }
    if (state.showImportKeyConfirmDialog && state.pendingImportJson != null) {
        val jsonToImport = state.pendingImportJson!!
        AlertDialog(
            onDismissRequest = {
                state.clearImportState()
            },
            title = { Text(stringResource(R.string.config_import_category_title)) },
            text = {
                Text(
                    stringResource(R.string.browser_import_config_keys_prompt),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        state.showImportKeyConfirmDialog = false
                        state.scopeLaunchImportKeys(jsonToImport, importKeys = true)
                    }
                ) { Text(stringResource(R.string.browser_replace_all)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state.showImportKeyConfirmDialog = false
                        state.scopeLaunchImportKeys(jsonToImport, importKeys = false)
                    }
                ) { Text(stringResource(R.string.browser_skip_keys)) }
            }
        )
    }
    if (state.showImportPasswordDialog && state.pendingEncryptedBytes != null) {
        AlertDialog(
            onDismissRequest = { state.clearImportState() },
            title = { Text(stringResource(R.string.config_import_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.config_import_password_hint),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ReliablePasswordInputField(
                        value = state.importPassword,
                        onValueChange = { state.importPassword = it },
                        label = { Text(stringResource(R.string.config_import_password_field)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (state.importPassword.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.config_import_password_required), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    state.tryDecryptPendingImport(state.importPassword)
                }) { Text(stringResource(R.string.common_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { state.clearImportState() }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    if (state.showExportConfigDialog) {
        AlertDialog(
            onDismissRequest = { state.showExportConfigDialog = false },
            title = { Text(context.getString(R.string.config_export_dialog_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = state.exportConfigFileName,
                        onValueChange = { state.exportConfigFileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(context.getString(R.string.config_export_file_name_label)) },
                        placeholder = { Text(CONFIG_EXPORT_FILE_NAME) }
                    )
                    Text(
                        text = context.getString(R.string.config_export_category_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.exportGitCategory, onCheckedChange = { state.exportGitCategory = it })
                        Text(context.getString(R.string.config_export_category_git))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.exportMusicCategory, onCheckedChange = { state.exportMusicCategory = it })
                        Text(context.getString(R.string.config_export_category_music))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.exportRecentCategory, onCheckedChange = { state.exportRecentCategory = it })
                        Text(context.getString(R.string.config_export_category_recent))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.exportEpubCategory, onCheckedChange = { state.exportEpubCategory = it })
                        Text(context.getString(R.string.config_export_category_epub))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.exportGpgCategory, onCheckedChange = { state.exportGpgCategory = it })
                        Text(context.getString(R.string.config_export_category_gpg))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.exportOtherCategory, onCheckedChange = { state.exportOtherCategory = it })
                        Text(context.getString(R.string.config_export_category_other))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.exportEncrypt,
                            onCheckedChange = {
                                state.exportEncrypt = it
                                if (!it) state.exportPassword = ""
                            }
                        )
                        Text(context.getString(R.string.config_export_encrypt_toggle))
                    }
                    if (state.exportEncrypt) {
                        ReliablePasswordInputField(
                            value = state.exportPassword,
                            onValueChange = { state.exportPassword = it },
                            label = { Text(context.getString(R.string.config_export_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            requireConfirmation = true,
                            supportingText = {
                                Text(
                                    context.getString(R.string.config_export_password_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (state.exportEncrypt && state.exportPassword.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.config_export_password_required), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val normalizedFileName = normalizeConfigExportFileName(state.exportConfigFileName, state.exportEncrypt)
                    if (normalizedFileName == null) {
                        Toast.makeText(context, context.getString(R.string.config_export_invalid_file_name), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val categories = buildSet {
                        if (state.exportGitCategory) add(ConfigExportCategory.GIT)
                        if (state.exportMusicCategory) add(ConfigExportCategory.MUSIC)
                        if (state.exportRecentCategory) add(ConfigExportCategory.RECENT)
                        if (state.exportEpubCategory) add(ConfigExportCategory.EPUB)
                        if (state.exportGpgCategory) add(ConfigExportCategory.GPG)
                        if (state.exportOtherCategory) add(ConfigExportCategory.OTHER)
                    }
                    if (categories.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.config_export_select_one_category), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    state.showExportConfigDialog = false
                    state.launchExport(
                        normalizedFileName,
                        categories,
                        if (state.exportEncrypt) state.exportPassword else null
                    )
                }) {
                    Text(context.getString(R.string.config_export))
                }
            },
            dismissButton = {
                TextButton(onClick = { state.showExportConfigDialog = false }) {
                    Text(context.getString(R.string.common_cancel))
                }
            }
        )
    }
}
