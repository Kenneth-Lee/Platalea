package com.kenny.localmanager.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kenny.localmanager.R
import com.kenny.localmanager.SHORTCUT_TAB_PLAYER
import com.kenny.localmanager.SHORTCUT_TAB_QUICK_NOTE
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.requestPinnedTabShortcut
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Config tab 功能对象：呈现 + 设置对话框群开闭 + 导入导出 workflow（browser 域消费）。 */
internal interface ConfigTabFeature : MainTabFeature {
    val importExport: ConfigImportExportWorkflowState
    /** 宿主返回键协调：是否有 Config 域对话框开着。 */
    val hasOpenConfigDialog: Boolean
    /** 关闭一个开着的 Config 对话框（优先级与原返回键分支顺序一致：密钥 → 缓存 → Git 配置）。 */
    fun closeTopConfigDialog()
}

/**
 * Config tab 工厂：设置项 prefs 镜像、四个对话框/全屏状态、导入导出 workflow 创建
 * 与对话框组合全部内聚。跨域输入：browser 根（读函数）与文件变化回调（browserTab.refresh）。
 */
@Composable
internal fun configTabFeature(
    context: Context,
    prefs: Preferences,
    scope: CoroutineScope,
    networkConfig: NetworkServiceConfigState,
    browserRootUri: () -> String?,
    onFilesChanged: () -> Unit
): ConfigTabFeature {
    val importExport = rememberConfigImportExportWorkflow(
        prefs = prefs,
        rootUriProvider = { browserRootUri() },
        onConfigFileWritten = { onFilesChanged() }
    )
    val state = remember {
        ConfigTabState(context, prefs, scope, networkConfig, browserRootUri, onFilesChanged, importExport)
    }
    LaunchedEffect(state) {
        state.observePrefs()
    }
    return state
}

private class ConfigTabState(
    private val context: Context,
    private val prefs: Preferences,
    private val scope: CoroutineScope,
    private val networkConfig: NetworkServiceConfigState,
    private val browserRootUri: () -> String?,
    private val onFilesChanged: () -> Unit,
    override val importExport: ConfigImportExportWorkflowState
) : ConfigTabFeature {
    // 设置项镜像（组合期读即订阅）
    private var filterVisible by mutableStateOf(false)
    private var hideDotFiles by mutableStateOf(false)
    private var startupDecryptKey by mutableStateOf(false)
    private var viewerPreviewBytes by mutableStateOf(4096)

    // 设置对话框群
    private var showGitConfigDialog by mutableStateOf(false)
    private var showKeyManagementDialog by mutableStateOf(false)
    private var showCacheManagementDialog by mutableStateOf(false)
    private var showPubkeyShareScreen by mutableStateOf(false)

    fun observePrefs() {
        scope.launch {
            prefs.filterVisible.collect { filterVisible = it }
        }
        scope.launch {
            prefs.hideDotFiles.collect { hideDotFiles = it }
        }
        scope.launch {
            prefs.startupDecryptKey.collect { startupDecryptKey = it }
        }
        scope.launch {
            prefs.viewerPreviewBytes.collect { viewerPreviewBytes = it }
        }
    }

    override val tab: MainTab get() = MainTab.CONFIG
    override val content: @Composable () -> Unit = {
        ConfigTabRoute(
            ConfigTabRouteState(
                filterVisible = filterVisible,
                onFilterVisibleChange = { scope.launch { prefs.setFilterVisible(it) } },
                hideDotFiles = hideDotFiles,
                onHideDotFilesChange = { scope.launch { prefs.setHideDotFiles(it) } },
                startupDecryptKey = startupDecryptKey,
                onStartupDecryptKeyChange = { enabled ->
                    scope.launch { prefs.setStartupDecryptKey(enabled) }
                    if (!enabled) SecretKeyPasswordCache.clear()
                },
                viewerPreviewBytes = viewerPreviewBytes,
                onViewerPreviewBytesChange = { scope.launch { prefs.setViewerPreviewBytes(it) } },
                ftpPassword = networkConfig.ftpPassword ?: "",
                onFtpPasswordChange = { s -> networkConfig.setFtpPassword(s.ifBlank { null }) },
                networkServiceTimeoutMinutes = networkConfig.networkServiceTimeoutMinutes,
                onNetworkServiceTimeoutMinutesChange = { networkConfig.setNetworkServiceTimeoutMinutes(it) },
                localNetworkServiceEnabled = networkConfig.localNetworkServiceEnabled,
                onLocalNetworkServiceEnabledChange = { enabled -> networkConfig.setLocalNetworkServiceEnabled(enabled) },
                familyNetworkUserName = networkConfig.familyNetworkUserName,
                onFamilyNetworkUserNameChange = { name -> networkConfig.setFamilyNetworkUserName(name) },
                familyNetworkAvatarId = networkConfig.familyNetworkAvatarId,
                onFamilyNetworkAvatarIdChange = { avatarId -> networkConfig.setFamilyNetworkAvatarId(avatarId) },
                familyNetworkHostName = networkConfig.familyNetworkHostName,
                onFamilyNetworkHostNameChange = { name -> networkConfig.setFamilyNetworkHostName(name) },
                onOpenGitConfig = { showGitConfigDialog = true },
                onManageKeys = { showKeyManagementDialog = true },
                onOpenCacheManagement = { showCacheManagementDialog = true },
                onExportConfig = { importExport.openExportDialog() },
                onCreatePlayerShortcut = {
                    val playerErr = requestPinnedTabShortcut(context, SHORTCUT_TAB_PLAYER)
                    if (playerErr == null) {
                        Toast.makeText(context, context.getString(R.string.config_player_shortcut_requested), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.config_player_shortcut_failed, playerErr), Toast.LENGTH_LONG).show()
                    }
                },
                onCreateQuickNoteShortcut = {
                    val quickNoteErr = requestPinnedTabShortcut(context, SHORTCUT_TAB_QUICK_NOTE)
                    if (quickNoteErr == null) {
                        Toast.makeText(context, context.getString(R.string.config_quick_note_shortcut_requested), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.config_quick_note_shortcut_failed, quickNoteErr), Toast.LENGTH_LONG).show()
                    }
                }
            )
        )
    }
    override val overlay: (@Composable () -> Unit)? = {
        ConfigImportExportDialogs(state = importExport)
        if (showCacheManagementDialog) {
            CacheManagementDialog(context = context, onDismiss = { showCacheManagementDialog = false })
        }
        if (showGitConfigDialog) {
            GitConfigDialog(
                prefs = prefs,
                rootUri = browserRootUri()?.let { normalizeContentUriString(it) },
                onDismiss = {
                    showGitConfigDialog = false
                    onFilesChanged()
                }
            )
        }
        if (showKeyManagementDialog) {
            KeyManagementDialog(
                context = context,
                onDismiss = { showKeyManagementDialog = false },
                onKeysChanged = { onFilesChanged() },
                onOpenPubkeyShare = {
                    showKeyManagementDialog = false
                    showPubkeyShareScreen = true
                }
            )
        }
        if (showPubkeyShareScreen) {
            PubkeyShareScreen(
                prefs = prefs,
                rootUri = browserRootUri()?.let { normalizeContentUriString(it) },
                onDismiss = {
                    showPubkeyShareScreen = false
                    onFilesChanged()
                }
            )
        }
    }

    override val hasOpenConfigDialog: Boolean
        get() = showKeyManagementDialog || showCacheManagementDialog || showGitConfigDialog

    override fun closeTopConfigDialog() {
        when {
            showKeyManagementDialog -> showKeyManagementDialog = false
            showCacheManagementDialog -> showCacheManagementDialog = false
            showGitConfigDialog -> showGitConfigDialog = false
        }
    }
}
