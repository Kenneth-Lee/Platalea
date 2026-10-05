package com.kenny.localmanager.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.icu.text.Transliterator
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import android.widget.Toast
import com.kenny.localmanager.R
import com.kenny.localmanager.data.Playlist
import com.kenny.localmanager.data.RecentOpenItem
import com.kenny.localmanager.diagnostics.CrashReportStore
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.emptyTrash
import com.kenny.localmanager.file.compressToZip
import com.kenny.localmanager.file.compressTo7z
import com.kenny.localmanager.file.ArchiveCompressionFormat
import com.kenny.localmanager.file.buildArchiveOutputName
import com.kenny.localmanager.file.compressSingleFileToFormat
import com.kenny.localmanager.file.compressTarArchive
import com.kenny.localmanager.file.unzipToParent
import com.kenny.localmanager.file.UnzipResult
import com.kenny.localmanager.player.TrackMetadata
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.getGpgKeyDir
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat

internal const val CONFIG_EXPORT_FILE_NAME = "local_manager_config.json"

internal enum class ConfigExportResult {
    SUCCESS,
    FILE_EXISTS,
    FAILED
}

internal typealias RunProgressBlock = suspend (
    label: String,
    total: Int?,
    block: suspend ((Int) -> Unit) -> Unit
) -> Unit

/**
 * tab 的退出恢复策略（AppShell 编排约定）：
 * - PERSISTENT：常规 tab，退出时保存、下次启动恢复到它
 * - TRANSIENT：会话型 tab（网络服务等），退出不作为恢复目标，
 *   启动恢复时回落到最近的 PERSISTENT tab
 */
internal enum class TabExitRestorePolicy { PERSISTENT, TRANSIENT }

internal enum class MainTab(
    val key: String,
    val labelRes: Int,
    val exitRestorePolicy: TabExitRestorePolicy = TabExitRestorePolicy.PERSISTENT
) {
    DIRECTORY("directory", R.string.main_tab_directory),
    RECENT("recent", R.string.main_tab_recent),
    PLAYER("player", R.string.main_tab_player),
    FTP("ftp", R.string.main_tab_ftp, TabExitRestorePolicy.TRANSIENT),
    LOCAL_SHARE("local_share", R.string.main_tab_local_share, TabExitRestorePolicy.TRANSIENT),
    FAMILY_NETWORK("family_network", R.string.main_tab_family_network, TabExitRestorePolicy.TRANSIENT),
    CONFIG("config", R.string.main_tab_config),
    GIT_SHARE("git_share", R.string.main_tab_git_share),
    GIT_PROJECTS("git_projects", R.string.main_tab_git_projects),
    BOOK_NOTE("book_note", R.string.main_tab_book_note),
    QUICK_NOTE("quick_note", R.string.main_tab_quick_note),
    QUICK_CRYPTO("quick_crypto", R.string.main_tab_quick_crypto),
    DICTIONARY("dictionary", R.string.main_tab_dictionary),
    ABOUT("about", R.string.main_menu_about);

    companion object {
        fun fromKey(key: String?): MainTab {
            return values().firstOrNull { it.key == key } ?: DIRECTORY
        }

        fun fromLaunchTarget(target: String?): MainTab? {
            if (target.isNullOrBlank()) return null
            return values().firstOrNull { it.key == target }
        }
    }
}

private fun mainTabIcon(tab: MainTab): ImageVector {
    return when (tab) {
        MainTab.DIRECTORY -> Icons.Default.Folder
        MainTab.RECENT -> Icons.AutoMirrored.Filled.List
        MainTab.PLAYER -> Icons.AutoMirrored.Filled.QueueMusic
        MainTab.FTP -> Icons.Default.Wifi
        MainTab.LOCAL_SHARE -> Icons.Default.Devices
        MainTab.FAMILY_NETWORK -> Icons.Default.Home
        MainTab.CONFIG -> Icons.Default.Settings
        MainTab.GIT_SHARE -> Icons.Default.Share
        MainTab.GIT_PROJECTS -> Icons.Default.FolderOpen
        MainTab.BOOK_NOTE -> Icons.AutoMirrored.Filled.Article
        MainTab.QUICK_NOTE -> Icons.Default.Edit
        MainTab.QUICK_CRYPTO -> Icons.Default.Lock
        MainTab.DICTIONARY -> Icons.AutoMirrored.Filled.MenuBook
        MainTab.ABOUT -> Icons.Default.Info
    }
}

private suspend fun runWithUiProgress(
    setProgressState: (OperationProgress?) -> Unit,
    label: String,
    total: Int? = null,
    block: suspend ((Int) -> Unit) -> Unit
) {
    setProgressState(OperationProgress(label, 0, total))
    delay(50)
    try {
        val setProgress: (Int) -> Unit = { i -> setProgressState(OperationProgress(label, i, total)) }
        block(setProgress)
    } finally {
        setProgressState(null)
    }
}

@Composable
private fun ScrollableMainTabBar(
    activeMainTab: MainTab,
    onSwitchMainTab: (MainTab) -> Unit
) {
    val context = LocalContext.current
    fun trySwitchTab(tab: MainTab) {
        onSwitchMainTab(tab)
    }
    val fixedTabs = listOf(MainTab.DIRECTORY, MainTab.RECENT)
    val candidateTabs = listOf(
        MainTab.CONFIG,
        MainTab.GIT_PROJECTS,
        MainTab.BOOK_NOTE,
        MainTab.QUICK_NOTE,
        MainTab.QUICK_CRYPTO,
        MainTab.PLAYER,
        MainTab.DICTIONARY,
        MainTab.FTP,
        MainTab.LOCAL_SHARE,
        MainTab.FAMILY_NETWORK,
        MainTab.GIT_SHARE,
        MainTab.ABOUT
    )
    var candidateUsageOrder by remember { mutableStateOf(candidateTabs) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var pendingOverflowPromotion by remember { mutableStateOf<MainTab?>(null) }
    var previousActiveTab by remember { mutableStateOf<MainTab?>(null) }

    fun moveCandidateToFront(tab: MainTab) {
        if (tab !in candidateTabs) return
        candidateUsageOrder = listOf(tab) + candidateUsageOrder.filter { it != tab }
    }

    LaunchedEffect(activeMainTab) {
        if (previousActiveTab == activeMainTab) return@LaunchedEffect
        val pending = pendingOverflowPromotion
        if (pending != null && activeMainTab != pending) {
            moveCandidateToFront(pending)
            pendingOverflowPromotion = null
        }
        if (activeMainTab in candidateTabs) {
            moveCandidateToFront(activeMainTab)
        }
        previousActiveTab = activeMainTab
    }

    val dynamicTabs = candidateUsageOrder.take(2)
    val overflowTabs = candidateTabs.filter { it !in dynamicTabs }

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fixedTabs.forEach { tab ->
                val selected = activeMainTab == tab
                val label = context.getString(tab.labelRes)
                OutlinedButton(
                    onClick = { trySwitchTab(tab) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(mainTabIcon(tab), contentDescription = label, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            dynamicTabs.forEach { tab ->
                val selected = activeMainTab == tab
                val label = context.getString(tab.labelRes)
                OutlinedButton(
                    onClick = { trySwitchTab(tab) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(mainTabIcon(tab), contentDescription = label, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val overflowSelected = activeMainTab in overflowTabs
                OutlinedButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (overflowSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (overflowSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.main_tab_more), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            context.getString(R.string.main_tab_more),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false }
                ) {
                    overflowTabs.forEach { tab ->
                        val label = context.getString(tab.labelRes)
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = {
                                Icon(
                                    mainTabIcon(tab),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                pendingOverflowPromotion = tab
                                trySwitchTab(tab)
                            }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlataleaApp(
    initialFileUri: androidx.compose.runtime.MutableState<String?>? = null,
    initialLaunchTarget: String? = null
) {
    val context = LocalContext.current
    // 配置功能（域服务）：持久配置唯一创建入口；各域经窄投影消费，不感知存储实现
    val config = rememberConfigService(context)

    // AppShell + DirectoryRoot state (long-lived while the app shell is active)
    // 目录域共享数据对象（AppShell 分层设计 §2.4）：root、当前路径、刷新信号
    var recentRootUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var initialDirUri by remember { mutableStateOf<String?>(null) }
    val browserTab = rememberBrowserTabState(context, config.browser, initialDirUri)

    // Transient viewer / editor routes (short-lived feature flows)
    // Browser 域工作流：根目录切换（最近根/换根确认）与外源保存（覆盖确认）
    val rootSwitch = rememberRootSwitchWorkflow(context, browserTab)
    val browserSave = rememberBrowserSaveWorkflow(context, browserTab)
    // Git 域分享工作流（分享到 Git 仓库：确认→同步→复制→推送）
    val gitShare = rememberGitShareWorkflow(context, config.git)
    // Crypto 域快速遮罩工作流（单文件/批量）
    val quickObfuscate = rememberQuickObfuscateWorkflow(context)
    // 查看器类临时 Route：统一由 TransientRoute 建模，渲染在 TransientViewerRouteHost 中
    var transientRoute by remember { mutableStateOf<TransientRoute?>(null) }
    val markdownViewerSessionCache = rememberViewerSessions(browserTab.rootUri)
    // Crypto 域（GPG 加解密 / .pass 保护查看编辑）单槽工作流状态
    val crypto = rememberCryptoWorkflow()
    var encryptedCacheExitDialog by remember { mutableStateOf<EncryptedCacheExitDialogState?>(null) }

    // AppShell navigation + shared UI surfaces
    var currentMainTab by remember { mutableStateOf<MainTab?>(null) }
    var showFileDetail by remember { mutableStateOf<DocumentFileModel?>(null) }
    var saveCompletedToken by remember { mutableStateOf(0) }
    var recentOpenItems by remember { mutableStateOf<List<RecentOpenItem>>(emptyList()) }

    // App shell commands / helpers
    val scope = rememberCoroutineScope()

    // Startup behavior
    val launchShortcutTarget = MainTab.fromLaunchTarget(initialLaunchTarget)
    val bypassExitDoubleConfirm = launchShortcutTarget == MainTab.PLAYER || launchShortcutTarget == MainTab.QUICK_NOTE

    var startupDecryptKeyEnabled by remember { mutableStateOf(false) }
    var hasSecretKeyFile by remember { mutableStateOf(false) }
    var unlockedByStartup by remember { mutableStateOf(false) }
    LaunchedEffect(config.editing) {
        config.editing.startupDecryptKey.collect { startupDecryptKeyEnabled = it }
    }
    LaunchedEffect(Unit) {
        hasSecretKeyFile = withContext(Dispatchers.IO) {
            File(getGpgKeyDir(context), "secring.gpg").exists()
        }
    }
    val showUnlockGate = startupDecryptKeyEnabled && hasSecretKeyFile && !unlockedByStartup

    var viewerPreviewBytes by remember { mutableStateOf(4096) }
    LaunchedEffect(config.editing) {
        config.editing.viewerPreviewBytes.collect { viewerPreviewBytes = it }
    }
    LaunchedEffect(config.shell) {
        config.shell.recentOpenItems.collect { recentOpenItems = it }
    }
    LaunchedEffect(config.browser) {
        config.browser.recentRootUris.collect { recentRootUris = it }
    }
    LaunchedEffect(initialFileUri?.value) {
        val uriStr = initialFileUri?.value ?: return@LaunchedEffect
        initialFileUri.value = null
        browserSave.offer(uriStr)
    }

    // App 级 Tab 命令入口：所有跨域"切到某 Tab"的请求统一走这里，避免各处直接改 currentMainTab
    fun persistTabChoice(tab: MainTab, previousTab: MainTab?) {
        scope.launch {
            val previousTab = previousTab
            if (tab.exitRestorePolicy == TabExitRestorePolicy.TRANSIENT) {
                if (previousTab != null && previousTab.exitRestorePolicy == TabExitRestorePolicy.PERSISTENT) {
                    config.shell.setLastNonServerMainTab(previousTab.key)
                }
            } else {
                config.shell.setLastNonServerMainTab(tab.key)
            }
            config.shell.setLastMainTab(tab.key)
        }
    }

    fun switchMainTab(tab: MainTab) {
        if (currentMainTab == tab) return
        val previousTab = currentMainTab
        currentMainTab = tab
        persistTabChoice(tab, previousTab)
    }

    fun requestOpenMainTab(tab: MainTab) {
        switchMainTab(tab)
    }

    LaunchedEffect(config.shell, initialLaunchTarget) {
        if (CrashReportStore.hasUnseenCrash(context)) {
            Toast.makeText(context, context.getString(R.string.crash_report_found_toast), Toast.LENGTH_SHORT).show()
            withContext(Dispatchers.IO) {
                CrashReportStore.exportLatestToRootAndClear(context)
            }
            switchMainTab(MainTab.ABOUT)
            CrashReportStore.markLatestSeen(context)
            return@LaunchedEffect
        }
        val launchTab = MainTab.fromLaunchTarget(initialLaunchTarget)
        val savedTab = MainTab.fromKey(config.shell.lastMainTab.first())
        val savedNonServerTab = MainTab.fromKey(config.shell.lastNonServerMainTab.first())
        val restoreTab = if (launchTab != null) {
            launchTab
        } else if (savedTab.exitRestorePolicy == TabExitRestorePolicy.TRANSIENT) {
            savedNonServerTab
        } else {
            savedTab
        }
        currentMainTab = restoreTab
    }

    fun recordRecentOpen(
        type: String,
        key: String,
        title: String,
        uri: String? = null,
        playlistId: String? = null
    ) {
        scope.launch {
            config.shell.addRecentOpenItem(
                type = type,
                key = key,
                title = title,
                uri = uri,
                playlistId = playlistId
            )
        }
    }

    // Archive 阅读器域：压缩包 / 文档阅读的临时 Route（请求 -> 密码 -> 解压 -> 查看器）
    val archiveReader = rememberArchiveReaderWorkflow(context, scope)

    suspend fun savePicZipImageToRoot(sourceFile: File, fileName: String): Boolean {
        val targetRoot = browserTab.rootUri?.let { normalizeContentUriString(it) }
        if (targetRoot == null) {
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
            return false
        }
        val bytes = withContext(Dispatchers.IO) {
            runCatching { sourceFile.readBytes() }.getOrNull()
        } ?: run {
            Toast.makeText(context, context.getString(R.string.browser_save_image_read_failed), Toast.LENGTH_SHORT).show()
            return false
        }
        val targetUri = Uri.parse(targetRoot)
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
        var outName = fileName
        var copyIndex = 1
        while (findChildByName(context, targetUri, outName) != null) {
            outName = "$baseName ($copyIndex)$ext"
            copyIndex++
        }
        val mimeType = when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> "application/octet-stream"
        }
        val ok = withContext(Dispatchers.IO) {
            createFileWithBytes(context, targetUri, targetUri, outName, mimeType, bytes)
        }
        Toast.makeText(context, if (ok) context.getString(R.string.browser_saved_to_root, outName) else context.getString(R.string.viewer_save_failed), Toast.LENGTH_SHORT).show()
        return ok
    }


    val quickNoteTab = quickNoteTabFeature(
        config = config.quickNote,
        rootUri = browserTab.rootUri,
        isActive = currentMainTab == MainTab.QUICK_NOTE,
        markdownViewerSessionCache = markdownViewerSessionCache
    )
    val bookNoteTab = bookNoteTabFeature(
        rootUri = browserTab.rootUri,
        isActive = currentMainTab == MainTab.BOOK_NOTE,
        markdownViewerSessionCache = markdownViewerSessionCache
    )

    // Config 域：配置导入 / 导出工作流（独立状态持有者，写盘后通过回调刷新目录）

    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val normalized = normalizeContentUriString(uri.toString())
            val previousRoot = browserTab.rootUri?.let { normalizeContentUriString(it) }
            rootSwitch.applyNewRoot(previousRoot, normalized) { initialDirUri = it }
        }
    }

    LaunchedEffect(browserSave.pendingSaveFileUri, browserTab.rootUri) {
        browserSave.consumeIfReady { dir ->
            initialDirUri = dir
            browserTab.navigateTo(dir)
            saveCompletedToken++
        }
    }

    @Composable
    fun MainTabsRootContent() {
        val displayUri = browserTab.currentUri ?: initialDirUri ?: browserTab.rootUri!!
        val activeMainTab = currentMainTab ?: MainTab.DIRECTORY

        fun requestOpenQuickNoteTabFromDirectory() {
            requestOpenMainTab(MainTab.QUICK_NOTE)
            quickNoteTab.requestOpenWithCachedPassword()
        }

        var bulletinForwardPayload by remember { mutableStateOf<BulletinForwardPayload?>(null) }

        // App-level transient dialogs / playback-target workflow
        LaunchedEffect(saveCompletedToken) { if (saveCompletedToken > 0) browserTab.refresh() }
        var lastBackPressTime by remember { mutableStateOf(0L) }
        fun finishCurrentActivity() {
            (context as? Activity)?.finish()
        }

        fun requestExitApp() {
            if (activeMainTab == MainTab.QUICK_NOTE) {
                quickNoteTab.requestExitApp(context) { finishCurrentActivity() }
                return
            }
            if (bypassExitDoubleConfirm) {
                finishCurrentActivity()
            } else {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) finishCurrentActivity()
                else {
                    lastBackPressTime = now
                    Toast.makeText(context, context.getString(R.string.browser_press_again_to_exit), Toast.LENGTH_SHORT).show()
                }
            }
        }


        // Long-running operation status
        val progressOpState = remember { mutableStateOf<OperationProgress?>(null) }
        var progressOp by progressOpState
        val progressCancelActionState = remember { mutableStateOf<(() -> Unit)?>(null) }
        var progressCancelAction by progressCancelActionState

        // DirectoryRoot derived UI state
        LaunchedEffect(browserTab.rootUri) {
            rootSwitch.refreshTrashUri(browserTab.rootUri)
        }

        // 网络服务域（FTP / WebUI / 家庭网络）：三服务对象 + 共享配置 + 生命周期接线全部内聚于工厂
        val networkServices = networkServicesTabs(
            context = context,
            config = config.network,
            readerConfig = config.reader,
            activeMainTab = currentMainTab ?: MainTab.DIRECTORY,
            rootUri = browserTab.rootUri,
            displayUri = browserTab.currentUri ?: initialDirUri ?: browserTab.rootUri!!,
            familyRootUri = browserTab.rootUri ?: initialDirUri,
            hideDotFiles = browserTab.hideDotFiles,
            onRequestExitApp = { requestExitApp() }
        )
        // Config 域：设置 tab 功能对象（设置项镜像、对话框群、导入导出 workflow 内聚于工厂）
        val configTab = configTabFeature(
            context = context,
            config = config.editing,
            gitConfig = config.git,
            readerConfig = config.reader,
            scope = scope,
            networkConfig = networkServices.config,
            browserRootUri = { browserTab.rootUri },
            onFilesChanged = { browserTab.refresh() }
        )
        // Git 分享临时流程（目录文件共享到 Git 仓库）

        // Archive 操作向导域：解压 / 压缩向导（表单状态在 ArchiveOperationWorkflowState，执行编排留宿主）
        val archiveOps = rememberArchiveOperationWorkflow(context, scope)

        // PlayerTab 域：播放器 tab 功能对象（接口面：MainTabFeature + PlayerTabApi，实现不可见）
        val playerTab = playerTabFeature(
            context = context,
            config = config.player,
            scope = scope,
            autoplayShortcut = launchShortcutTarget == MainTab.PLAYER,
            onRequestExitApp = { requestExitApp() },
            recordRecentOpen = { type, key, title, uri, playlistId -> recordRecentOpen(type, key, title, uri, playlistId) },
            onBatchFinished = { browserTab.finishPendingList() },
            onOpenPlayerTab = { switchMainTab(MainTab.PLAYER) },
        )

        BackHandler {
            when {
                progressOp != null -> {
                    progressCancelAction?.invoke()
                }
                quickObfuscate.batchTarget != null -> quickObfuscate.resetIfBatchActive()
                quickObfuscate.quickTarget != null -> quickObfuscate.resetIfQuickActive()
                crypto.passEdit != null -> crypto.closePassEdit()
                crypto.request != null -> {
                    val guarded = crypto.request is CryptoRequest.PassView || crypto.request is CryptoRequest.PassEdit
                    if (!guarded || !crypto.inProgress) crypto.close()
                }
                rootSwitch.showChangeRootConfirm -> rootSwitch.dismissChangeRootConfirm()
                configTab.hasOpenConfigDialog -> configTab.closeTopConfigDialog()
                browserTab.showPendingDeleteConfirm -> browserTab.showPendingDeleteConfirm = false
                browserTab.showPendingList -> browserTab.showPendingList = false
                networkServices.family.isBulletinBoardOpen -> networkServices.family.closeBulletinBoard()
                activeMainTab != MainTab.DIRECTORY -> requestExitApp()
                archiveOps.request != null -> archiveOps.close()
                archiveReader.request?.kind in ArchiveReaderCancelableKinds -> archiveReader.cancelRequest()
                transientRoute is TransientRoute.PdfViewer -> transientRoute = null
                browserTab.canGoBack -> browserTab.goBack()
                else -> {
                    requestExitApp()
                }
            }
        }
        val copyMoveLog: ((String) -> Unit)? = null
        suspend fun runWithProgress(
            label: String,
            total: Int? = null,
            block: suspend ((Int) -> Unit) -> Unit
        ) {
            progressCancelAction = null
            try {
                runWithUiProgress(
                    setProgressState = { progressOp = it },
                    label = label,
                    total = total,
                    block = block
                )
            } finally {
                progressCancelAction = null
            }
        }

        val finishPendingListAndReturn: () -> Unit = {
            browserTab.finishPendingList()
        }


        Column(Modifier.fillMaxSize()) {
            if (crypto.pubEncryptInProgress || browserSave.inProgress) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.viewer_saving), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
            progressOp?.let { OperationProgressDialog(it, progressCancelAction) }
            Box(Modifier.weight(1f)) {
        val browserTabHostDependencies = BrowserTabHostDependencies(
            scope = scope,
            archiveReader = archiveReader,
            archiveOps = archiveOps,
            crypto = crypto,
            configImportExportWorkflow = configTab.importExport,
            markdownViewerSessionCache = markdownViewerSessionCache,
            playbackState = playerTab.playbackState,
            initialDirUri = initialDirUri,
            cachedTrashUri = rootSwitch.cachedTrashUri,
            finishPendingListAndReturn = finishPendingListAndReturn,
            setTransientRoute = { transientRoute = it },
            recordRecentOpen = { type, key, title, uri, playlistId -> recordRecentOpen(type, key, title, uri, playlistId) },
            onChangeRoot = rootSwitch::open,
            onShowFileDetail = { showFileDetail = it },
            onQuickMask = quickObfuscate::requestQuick,
            onShareToGit = gitShare::request,
            onForwardToBulletin = { payload -> bulletinForwardPayload = payload },
            onBatchMask = quickObfuscate::requestBatch,
            onAddToPlayback = { audioList -> playerTab.requestAddToPlayback(audioList) },
            onAddDirectoryToPlayback = { uri, name -> playerTab.requestCreateDirectoryPlaylist(uri, name) },
            onOpenQuickNote = { requestOpenQuickNoteTabFromDirectory() },
            requestOpenPlayerTab = { switchMainTab(MainTab.PLAYER) },
            runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
            onSetProgressCancel = { action -> progressCancelAction = action },
            operationTarget = config.operationTarget,
        )
        val directoryTab = browserTabFeature(state = browserTab, deps = browserTabHostDependencies)
                MainTabContentHost(
                    activeMainTab = activeMainTab,
                    features = listOf(
            directoryTab,
            mainTabFeature(MainTab.RECENT) {
                RecentTabRoute(
                    RecentTabRouteState(
                        items = recentOpenItems.take(30),
                        onOpenRecentItem = { item ->
                        openRecentItemWithStaleGuard(
                            scope = scope,
                            context = context,
                            item = item,
                            isPlaylistPresent = { id -> config.player.playlists.first().any { it.id == id } },
                            onRemoveStaleItem = { stale ->
                                scope.launch { config.shell.removeRecentOpenItem(stale.type, stale.key) }
                            },
                            open = {
                        openRecentItemByType(
                            context = context,
                            item = item,
                            currentPlaybackState = playerTab.playbackState,
                            recordRecentOpen = { type, key, title, uri, playlistId ->
                                recordRecentOpen(
                                    type = type,
                                    key = key,
                                    title = title,
                                    uri = uri,
                                    playlistId = playlistId
                                )
                            },
                            switchMainTab = { requestOpenMainTab(it) },
                            onMdZipTarget = { archiveReader.open(ArchiveReaderKind.MD_ZIP, it) },
                            onHtmlZipTarget = { archiveReader.open(ArchiveReaderKind.HTML_ZIP, it) },
                            onHtmlTarget = { transientRoute = htmlViewerRouteFromFile(it.uri.toString(), it.name) },
                            onHtmlUrlTarget = { url ->
                                transientRoute = htmlViewerRouteFromUrl(url)
                            },
                            onPicZipTarget = { archiveReader.open(ArchiveReaderKind.PIC_ZIP, it) },
                            onVidZipTarget = { archiveReader.open(ArchiveReaderKind.VID_ZIP, it) },
                            onLlmZipTarget = { archiveReader.open(ArchiveReaderKind.LLM_ZIP, it) },
                            onTxtTarget = { archiveReader.open(ArchiveReaderKind.TXT, it) },
                            onLlmTarget = { archiveReader.open(ArchiveReaderKind.LLM, it) },
                            onEpubTarget = { archiveReader.open(ArchiveReaderKind.EPUB, it) },
                            onPdfTarget = { transientRoute = TransientRoute.PdfViewer(it.uri.toString(), it.name) }
                        )
                            }
                    )
                    },
                        onDeleteRecentItem = { item ->
                        scope.launch {
                            config.shell.removeRecentOpenItem(item.type, item.key)
                        }
                    },
                        onClearRecentItems = {
                            scope.launch {
                                config.shell.clearRecentOpenItems()
                            }
                        }
                    )
                )
            },
            networkServices.ftp,
            networkServices.webUi,
            networkServices.family,
            configTab,
            mainTabFeature(
                MainTab.GIT_SHARE,
                {
                        GitShareTabRoute(
                            GitShareTabRouteState(
                                config = config.git,
                                rootUri = browserTab.rootUri,
                                onRequestExitApp = { requestExitApp() }
                            )
                        )
                    }
            ),
            mainTabFeature(
                MainTab.GIT_PROJECTS,
                {
                        GitProjectsTabRoute(
                            GitProjectsTabRouteState(
                                config = config.git,
                                rootUri = browserTab.rootUri
                            )
                        )
                    }
            ),
            bookNoteTab,
            quickNoteTab,
            mainTabFeature(
                MainTab.QUICK_CRYPTO,
                {
                        QuickCryptoTabRoute()
                    }
            ),
            mainTabFeature(
                MainTab.DICTIONARY,
                {
                        DictionaryTabRoute(
                            DictionaryTabRouteState(
                                config = config.dictionary,
                                onRequestExitApp = { requestExitApp() }
                            )
                        )
                    }
            ),
            mainTabFeature(
                MainTab.ABOUT,
                {
                        AboutTabRoute()
                    }
            ),
            playerTab
                    )
                )
            } // （修复）Box(weight 1f) 闭合：原版此处闭合后 TabBar 才能位于 Box 之下、Column 之内

            ScrollableMainTabBar(
                activeMainTab = activeMainTab,
                onSwitchMainTab = { requestOpenMainTab(it) }
            )
        }
        bulletinForwardPayload?.let { payload ->
            BulletinForwardTargetFlow(
                manager = networkServices.familyNetworkManager,
                payload = payload,
                localServiceEnabled = networkServices.config.localNetworkServiceEnabled,
                keepEphemeralSession = !networkServices.familyNetworkForeground,
                onDismiss = { bulletinForwardPayload = null },
                onComplete = {
                    finishPendingListAndReturn()
                    bulletinForwardPayload = null
                }
            )
        }
        // Config 域：配置导入 / 导出工作流对话框（状态与逻辑在 ConfigImportExportWorkflowState 中）
        if (rootSwitch.showRootSwitchDialog) {
            RootSwitchDialog(
                recentRootUris = recentRootUris,
                workflow = rootSwitch,
                onPickRoot = { rootUri -> rootSwitch.pickRecentRoot(rootUri) { initialDirUri = it } },
                onLaunchPicker = { treeLauncher.launch(null) }
            )
        }
        // 共享文件到 Git
        GitShareDialogs(workflow = gitShare, rootUri = browserTab.rootUri)
        QuickObfuscateDialogs(
            workflow = quickObfuscate,
            runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
            onQuickFinished = { browserTab.refresh() },
            onBatchFinished = {
                finishPendingListAndReturn()
                browserTab.refresh()
            }
        )
        // Crypto 域对话框：.pass 保护/查看/编辑与 GPG 加解密向导（状态在 CryptoWorkflowState，执行编排经宿主注入）
        CryptoDialogs(
            workflow = crypto,
            scope = scope,
            rootUri = browserTab.rootUri,
            currentDirUri = displayUri,
            runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
            onRefresh = { browserTab.refresh() },
            onBatchFinished = { finishPendingListAndReturn() }
        )

        // Archive 阅读器域：密码 / 进度 / 解析错误对话框（状态与流程在 ArchiveReaderWorkflowState）
        ArchiveReaderDialogs(archiveReader)
        // Archive 操作向导域：解压 / 压缩表单对话框（确认后由宿主执行文件操作）
        ArchiveOperationDialogs(
            workflow = archiveOps,
            onConfirm = {
                executeArchiveOperation(
                    context = context,
                    scope = scope,
                    workflow = archiveOps,
                    browserTab = browserTab,
                    displayUri = displayUri,
                    progressOpState = progressOpState,
                    progressCancelActionState = progressCancelActionState
                )
            }
        )

        if (rootSwitch.showChangeRootConfirm && browserTab.rootUri != null) {
            val r = browserTab.rootUri!!
            val root = Uri.parse(normalizeContentUriString(r))
            ChangeRootConfirmDialog(
                onEmptyAndChange = {
                    scope.launch {
                        rootSwitch.dismissChangeRootConfirm()
                        runWithProgress(context.getString(R.string.main_menu_empty_trash), null) { _ ->
                            val ok = withContext(Dispatchers.IO) { emptyTrash(context, root, root) }
                            if (ok) Toast.makeText(context, context.getString(R.string.browser_trash_emptied), Toast.LENGTH_SHORT).show()
                            treeLauncher.launch(null)
                            browserTab.refresh()
                        }
                    }
                },
                onChangeDirectly = {
                    rootSwitch.dismissChangeRootConfirm()
                    treeLauncher.launch(null)
                },
                onCancel = rootSwitch::dismissChangeRootConfirm
            )
        }
    }
    // （修复）MainTabsRootContent 局部函数定义至此闭合。
    // 5b3f9ec 平移时丢失了此闭合括号，导致下方顶层 when 被吞进定义体，
    // else -> MainTabsRootContent() 成为永不可达的递归调用，主界面组合为空（白屏）。
    when {
        showUnlockGate -> {
            BackHandler { (context as? Activity)?.finish() }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                var unlockPwd by remember { mutableStateOf("") }
                var unlockInProgress by remember { mutableStateOf(false) }
                var unlockError by remember { mutableStateOf<String?>(null) }
                Column(
                    modifier = Modifier.widthIn(max = 320.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.browser_startup_decrypt_enabled),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        stringResource(R.string.browser_enter_private_key_unlock),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ReliablePasswordInputField(
                        value = unlockPwd,
                        onValueChange = { unlockPwd = it; unlockError = null },
                        label = { Text(stringResource(R.string.browser_private_key_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !unlockInProgress
                    )
                    unlockError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            if (unlockPwd.isEmpty() || unlockInProgress) return@Button
                            unlockInProgress = true
                            unlockError = null
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    GpgHelper.tryUnlockSecretKey(context, unlockPwd.toCharArray())
                                }
                                unlockInProgress = false
                                if (ok) {
                                    SecretKeyPasswordCache.set(unlockPwd.toCharArray())
                                    unlockedByStartup = true
                                } else {
                                    unlockError = context.getString(R.string.browser_password_wrong_or_decrypt_failed)
                                }
                            }
                        },
                        enabled = unlockPwd.isNotEmpty() && !unlockInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (unlockInProgress) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.browser_unlock_action))
                        }
                    }
                }
            }
        }
        browserTab.rootUri == null && initialDirUri == null && browserSave.overwriteConfirm == null -> {
            var lastBackPressTime by remember { mutableStateOf(0L) }
            BackHandler {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) (context as? Activity)?.finish()
                else {
                    lastBackPressTime = now
                    Toast.makeText(context, context.getString(R.string.browser_press_again_to_exit), Toast.LENGTH_SHORT).show()
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (browserSave.pendingSaveFileUri != null) stringResource(R.string.browser_external_open_save_prompt) else stringResource(R.string.browser_select_root_prompt),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { treeLauncher.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (browserSave.pendingSaveFileUri != null) stringResource(R.string.browser_select_save_location) else stringResource(R.string.browser_select_root_button))
                    }
                }
            }
        }
        transientRoute != null -> {
            TransientViewerRouteHost(
                route = transientRoute!!,
                config = config.reader,
                markdownViewerSessionCache = markdownViewerSessionCache,
                bookNote = bookNoteTab,
                recordRecentOpen = { type, key, title, uri, playlistId ->
                    recordRecentOpen(
                        type = type,
                        key = key,
                        title = title,
                        uri = uri,
                        playlistId = playlistId
                    )
                },
                onRouteChange = { transientRoute = it }
            )
        }
        archiveReader.viewer != null -> {
            // Archive 阅读器域：解压完成后的全屏查看器（渲染与退出善后在 ArchiveViewerRouteHost）
            ArchiveViewerRouteHost(
                workflow = archiveReader,
                readerConfig = config.reader,
                markdownViewerSessionCache = markdownViewerSessionCache,
                bookNote = bookNoteTab,
                recordRecentOpen = { type, key, title, uri, playlistId ->
                    recordRecentOpen(
                        type = type,
                        key = key,
                        title = title,
                        uri = uri,
                        playlistId = playlistId
                    )
                },
                onSavePicZipImage = { sourceFile, fileName ->
                    savePicZipImageToRoot(sourceFile, fileName)
                },
                onEncryptedExitRequest = { encryptedCacheExitDialog = it }
            )
        }
        crypto.passContent != null -> {
            val content = crypto.passContent!!
            BackHandler { crypto.closePassContent() }
            PassContentViewerScreen(
                config = config.reader,
                innerFileName = content.innerFileName,
                decryptedBytes = content.decryptedBytes,
                onBack = { crypto.closePassContent() }
            )
        }
        crypto.passEdit != null -> {
            val state = crypto.passEdit!!
            BackHandler { crypto.closePassEdit() }
            PassEditScreen(
                fileName = state.innerName,
                initialDecryptedBytes = state.decryptedBytes,
                onSave = { newBytes ->
                    val ctx = context
                    scope.launch {
                        val ok = savePassEditReencrypted(ctx, state, newBytes)
                        crypto.closePassEdit()
                        browserTab.refresh()
                        Toast.makeText(ctx, if (ok) ctx.getString(R.string.browser_saved_and_reencrypted) else ctx.getString(R.string.viewer_save_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                onBack = { crypto.closePassEdit() }
            )
        }
        else -> MainTabsRootContent()
    }
    // Archive 阅读器域：加密缓存退出确认（渲染在顶层，确保全屏查看器激活时也能弹出）
    encryptedCacheExitDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { encryptedCacheExitDialog = null },
            title = { Text(dialog.title) },
            text = {
                Text(
                    stringResource(R.string.browser_delete_cache_exit_prompt),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(onClick = {
                    dialog.onDelete()
                    encryptedCacheExitDialog = null
                }) { Text(stringResource(R.string.browser_delete_cache_and_exit)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialog.onKeep()
                    encryptedCacheExitDialog = null
                }) { Text(stringResource(R.string.browser_keep_cache_and_exit)) }
            }
        )
    }
    browserSave.overwriteConfirm?.let { (_, fileName) ->
        OverwriteSaveConfirmDialog(
            fileName = fileName,
            onOverwrite = {
                browserSave.confirmOverwrite { dir ->
                    initialDirUri = dir
                    browserTab.navigateTo(dir)
                    saveCompletedToken++
                }
            },
            onSkip = {
                browserSave.skipOverwrite { dir -> initialDirUri = dir }
            }
        )
    }
}

internal fun normalizeContentUriString(s: String): String =
    com.kenny.localmanager.file.normalizeContentUriString(s)
