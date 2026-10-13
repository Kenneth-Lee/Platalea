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
import androidx.activity.compose.ManagedActivityResultLauncher
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
import androidx.compose.runtime.MutableState
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
import kotlinx.coroutines.CoroutineScope
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
    ENCRYPT_FAILED,
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
    // Crypto 域快速遮罩工作流（单文件/批量）
    val quickObfuscate = rememberQuickObfuscateWorkflow(context)
    // 查看器类临时 Route：统一由 TransientRoute 建模，渲染在 TransientViewerRouteHost 中
    var transientRoute by remember { mutableStateOf<TransientRoute?>(null) }
    val markdownViewerSessionCache = rememberViewerSessions(browserTab.rootUri)
    // Crypto 域（GPG 加解密 / .pass 保护查看编辑）单槽工作流状态
    val crypto = rememberCryptoWorkflow()

    // AppShell navigation + shared UI surfaces
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

    // 导航服务（《主界面判断归位设计》P1）：主界面全部的显示状态——
    // tab 层（currentTab）+ 功能页栈；tab 持久化决策经回调交宿主
    val nav = rememberNavigationState(onTabSwitched = ::persistTabChoice)

    fun switchMainTab(tab: MainTab) {
        nav.switchTab(tab)
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
        nav.restore(restoreTab)
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
    // EPUB 阅读器域：.epub / .llm.zip / .txt / .llm 统一经 epub_cache 渲染（批次 C）
    val epubReader = rememberEpubReaderWorkflow(context, scope)
    // 阅读启动路由表：各 tab 经统一入口启动阅读，宿主不再散装回调（批次 B1）
    val readingLauncher = ReadingLauncher(archiveReader, epubReader) { transientRoute = it }

    // 批量操作执行页（E2 样板）已入导航栈：以功能页 push/pop，
    // 栈顶即当前执行页，关闭经 OperationRunState.onClose pop。
    /**
     * 批量操作执行页编排（E2）：创建执行状态并以功能页入导航栈，协程内逐项
     * 执行（状态翻转 / 取消检查由 OperationRunState.runEach 封装），结束后
     * 停留结果页；收尾动作（清待处理列表 / 刷新等）在执行结束时立即执行，
     * 用户随后点关闭退回主界面（pop 导航栈）。
     */
    fun startBatchOperation(
        label: String,
        names: List<String>,
        onFinished: (OperationRunState) -> Unit = {},
        block: suspend (OperationRunState) -> Unit
    ) {
        val state = OperationRunState(label, names)
        lateinit var page: AppPage
        state.onClose = { nav.pop(page) }
        page = appPage { OperationScreen(state = state) }
        nav.push(page)
        scope.launch {
            try {
                block(state)
            } finally {
                state.finished = true
                onFinished(state)
            }
        }
    }

    // 独立全屏 Route 聚合槽（批次 D）：即时型槽 + 各工作流终态聚合为单一
    // activeRoute，顶层 when 与 Host 均不感知具体功能种类。优先级：即时型
    // 查看器最先（最新打开覆盖阅读器），工作流终态次之。执行页在导航栈中，
    // 全屏接管期间不可能同时打开查看器，两层互斥。
    val activeRoute: TransientRoute? = transientRoute
        ?: if (archiveReader.routeActive) TransientRoute.WorkflowViewer(archiveReader) else null
        ?: if (epubReader.routeActive) TransientRoute.WorkflowViewer(epubReader) else null
        ?: crypto.passEdit?.let { TransientRoute.PassEditor(crypto) }
        ?: crypto.passContent?.let { TransientRoute.PassViewer(crypto) }
        ?: (crypto.request as? CryptoRequest.PassEdit)?.let { TransientRoute.PassEditor(crypto) }
        ?: (crypto.request as? CryptoRequest.PassView)?.let { TransientRoute.PassViewer(crypto) }


    val quickNoteTab = quickNoteTabFeature(
        config = config.quickNote,
        rootUri = browserTab.rootUri,
        isActive = nav.currentTab == MainTab.QUICK_NOTE,
        markdownViewerSessionCache = markdownViewerSessionCache
    )
    val bookNoteTab = bookNoteTabFeature(
        rootUri = browserTab.rootUri,
        isActive = nav.currentTab == MainTab.BOOK_NOTE,
        markdownViewerSessionCache = markdownViewerSessionCache
    )

    // Archive 操作向导域（P2 上移主函数：向导功能页需要在此组装）
    val archiveOps = rememberArchiveOperationWorkflow(context, scope)

    // ---- 退出协商（原 MainTabsRootContent 局部函数上移，tab 层与功能页共用）----
    var lastBackPressTime by remember { mutableStateOf(0L) }
    fun finishCurrentActivity() {
        (context as? Activity)?.finish()
    }

    fun requestExitApp() {
        if ((nav.currentTab ?: MainTab.DIRECTORY) == MainTab.QUICK_NOTE) {
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

    // ---- 宿主操作进度（P2 上移主函数：进度对话框在全局层挂载，任何显示阶段可见）----
    val progressOpState = remember { mutableStateOf<OperationProgress?>(null) }
    var progressOp by progressOpState
    val progressCancelActionState = remember { mutableStateOf<(() -> Unit)?>(null) }
    var progressCancelAction by progressCancelActionState

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

    // 网络服务域（FTP / WebUI / 家庭网络）（P2 上移主函数：公告转发页需要组装；
    // 服务生命周期 effect 常驻执行，不再随 MainTabsRootContent 阶段性重组）
    val networkServices = networkServicesTabs(
        context = context,
        config = config.network,
        readerConfig = config.reader,
        activeMainTab = nav.currentTab ?: MainTab.DIRECTORY,
        rootUri = browserTab.rootUri,
        displayUri = browserTab.currentUri ?: initialDirUri ?: browserTab.rootUri ?: "",
        familyRootUri = browserTab.rootUri ?: initialDirUri,
        hideDotFiles = browserTab.hideDotFiles,
        onRequestExitApp = { requestExitApp() }
    )

    // 公告转发中转状态（P2 上移主函数：原 MainTabsRootContent 局部状态，
    // 置位经 browser 菜单 onForwardToBulletin，由 bulletinPage 绑定显示）
    var bulletinForwardPayload by remember { mutableStateOf<BulletinForwardPayload?>(null) }

    // ---- P2 跨域向导功能页：DialogPageBinder 把域状态绑定到页面栈 ----
    // （置位入栈 / 清空出栈；向导对话框在页面内组合，组件本体留在域文件）
    val quickMaskPage = remember {
        quickObfuscateDialogPage(
            workflow = quickObfuscate,
            runBatchOperation = { label, names, onFinished, block -> startBatchOperation(label, names, onFinished, block) },
            onQuickFinished = { browserTab.refresh() },
            onBatchFinished = {
                browserTab.finishPendingList()
                browserTab.refresh()
            }
        )
    }
    val cryptoPage = remember {
        cryptoDialogPage(
            workflow = crypto,
            scope = scope,
            rootUriProvider = { browserTab.rootUri },
            currentDirUriProvider = { browserTab.currentUri ?: initialDirUri ?: browserTab.rootUri!! },
            runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
            runBatchOperation = { label, names, onFinished, block -> startBatchOperation(label, names, onFinished, block) },
            onRefresh = { browserTab.refresh() },
            onBatchFinished = { browserTab.finishPendingList() }
        )
    }
    val archiveOpsPage = remember {
        archiveOpsDialogPage(
            context = context,
            scope = scope,
            workflow = archiveOps,
            browserTab = browserTab,
            displayUriProvider = { browserTab.currentUri ?: initialDirUri ?: browserTab.rootUri!! },
            progressOpState = progressOpState,
            progressCancelActionState = progressCancelActionState,
            runBatchOperation = { label, names, onFinished, block -> startBatchOperation(label, names, onFinished, block) }
        )
    }
    val rootSwitchPage = remember {
        rootSwitchDialogPage(
            workflow = rootSwitch,
            browserTab = browserTab,
            recentRootUrisProvider = { recentRootUris },
            initialDirUriSink = { initialDirUri = it },
            treeLauncher = treeLauncher,
            scope = scope,
            runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
            context = context
        )
    }
    val bulletinPage = remember {
        bulletinForwardDialogPage(
            payloadProvider = { bulletinForwardPayload },
            networkServices = networkServices,
            onDismiss = { bulletinForwardPayload = null },
            onComplete = {
                browserTab.finishPendingList()
                bulletinForwardPayload = null
            },
            onBack = { requestExitApp() }
        )
    }
    val overwritePage = remember {
        overwriteConfirmDialogPage(
            browserSave = browserSave,
            onOverwriteSaved = { dir ->
                initialDirUri = dir
                browserTab.navigateTo(dir)
                saveCompletedToken++
            },
            onSkipped = { dir -> initialDirUri = dir },
            onBack = { requestExitApp() }
        )
    }
    // PassView / PassEdit 走全屏查看器（activeRoute 聚合），不经对话框页
    DialogPageBinder(nav, quickMaskPage) { quickObfuscate.batchTarget != null || quickObfuscate.quickTarget != null }
    DialogPageBinder(nav, cryptoPage) {
        val r = crypto.request
        r != null && r !is CryptoRequest.PassView && r !is CryptoRequest.PassEdit
    }
    DialogPageBinder(nav, archiveOpsPage) { archiveOps.request != null }
    DialogPageBinder(nav, rootSwitchPage) { rootSwitch.showRootSwitchDialog || rootSwitch.showChangeRootConfirm }
    DialogPageBinder(nav, bulletinPage) { bulletinForwardPayload != null }
    DialogPageBinder(nav, overwritePage) { browserSave.overwriteConfirm != null }

    @Composable
    fun MainTabsRootContent() {
        val displayUri = browserTab.currentUri ?: initialDirUri ?: browserTab.rootUri!!
        val activeMainTab = nav.currentTab ?: MainTab.DIRECTORY

        fun requestOpenQuickNoteTabFromDirectory() {
            requestOpenMainTab(MainTab.QUICK_NOTE)
            quickNoteTab.requestOpenWithCachedPassword()
        }


        // App-level transient dialogs / playback-target workflow
        LaunchedEffect(saveCompletedToken) { if (saveCompletedToken > 0) browserTab.refresh() }


        // DirectoryRoot derived UI state
        LaunchedEffect(browserTab.rootUri) {
            rootSwitch.refreshTrashUri(browserTab.rootUri)
        }

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

        // tab 层返回键（P2 后）：向导类分支已随功能页迁移（页内 BackHandler），
        // 此处只保留 tab 页内对话框、阅读器请求、目录返回与退出协商。
        // 注：viewer 阶段（passContent/passEdit/阅读器全屏）由
        // TransientViewerRouteHost 内的 BackHandler 处理，主界面此时不在组合中。
        BackHandler {
            when {
                progressOp != null -> {
                    progressCancelAction?.invoke()
                }
                configTab.hasOpenConfigDialog -> configTab.closeTopConfigDialog()
                browserTab.showPendingDeleteConfirm -> browserTab.showPendingDeleteConfirm = false
                browserTab.showPendingList -> browserTab.showPendingList = false
                networkServices.family.isBulletinBoardOpen -> networkServices.family.closeBulletinBoard()
                activeMainTab != MainTab.DIRECTORY -> requestExitApp()
                archiveReader.request?.kind in ArchiveReaderCancelableKinds -> archiveReader.cancelRequest()
                epubReader.request?.kind in EpubReaderCancelableKinds -> epubReader.cancelRequest()
                browserTab.canGoBack -> browserTab.goBack()
                else -> {
                    requestExitApp()
                }
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
            Box(Modifier.weight(1f)) {
        val browserTabHostDependencies = BrowserTabHostDependencies(
            scope = scope,
            archiveReader = archiveReader,
            epubReader = epubReader,
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
            onForwardToBulletin = { payload -> bulletinForwardPayload = payload },
            onBatchMask = quickObfuscate::requestBatch,
            onAddToPlayback = { audioList -> playerTab.requestAddToPlayback(audioList) },
            onAddDirectoryToPlayback = { uri, name -> playerTab.requestCreateDirectoryPlaylist(uri, name) },
            onOpenQuickNote = { requestOpenQuickNoteTabFromDirectory() },
            requestOpenPlayerTab = { switchMainTab(MainTab.PLAYER) },
            runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
            runBatchOperation = { label, names, onFinished, block -> startBatchOperation(label, names, onFinished, block) },
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
                            readingLauncher = readingLauncher
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
        // 功能页（导航栈栈顶，如批量执行页）：整页自绘，返回键页内自理。
        // 全屏接管期间不可能同时打开查看器，与 activeRoute 两层互斥
        nav.top != null -> {
            nav.top?.let { it.content() }
        }
        activeRoute != null -> {
            // 独立全屏 Route 单槽（批次 D）：即时型 + 工作流型统一在此渲染，
            // 宿主不再感知具体功能种类
            TransientViewerRouteHost(
                route = activeRoute!!,
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
                onRouteChange = { transientRoute = it },
                browserTab = browserTab,
                scope = scope,
            )
        }
        else -> MainTabsRootContent()
    }
    // 宿主操作进度对话框（P2 上移全局层）：runWithProgress 可运行于任何显示阶段
    // （tab 层 / 向导功能页 / 阅读器），进度指示不应依赖某一层组合存活
    progressOp?.let { OperationProgressDialog(it, progressCancelAction) }
}

// ============================================================================
// P2 跨域向导功能页组装：Dialog UI 组件留在域文件，此处只做组合接线与
// 返回键。页面生存期由 DialogPageBinder 绑定域状态（置位入栈 / 清空出栈）。
// ============================================================================

/**
 * 向导页返回键：对齐原主界面 BackHandler 的分支次序——进度取消优先于
 * 向导关闭（runWithProgress 运行中 back = 取消进度而非关向导）。
 */
@Composable
private fun DialogPageBackHandler(
    progressOp: OperationProgress?,
    progressCancelAction: (() -> Unit)?,
    onDialogBack: () -> Unit
) {
    // 低优先：向导自身的关闭（先注册）
    BackHandler { onDialogBack() }
    // 高优先：进度取消（后注册，enabled 时抢先）
    BackHandler(enabled = progressOp != null) { progressCancelAction?.invoke() }
}

/**
 * 快速遮罩向导页（单文件 / 批量）。执行走自有 quickInProgress 或执行页，
 * 不经宿主 runWithProgress，页内 back 无进度取消分支。
 */
private fun quickObfuscateDialogPage(
    workflow: QuickObfuscateWorkflowState,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    onQuickFinished: () -> Unit,
    onBatchFinished: () -> Unit
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        DialogPageBackHandler(progressOp = null, progressCancelAction = null) {
            if (workflow.batchTarget != null) workflow.resetIfBatchActive()
            else workflow.resetIfQuickActive()
        }
        QuickObfuscateDialogs(
            workflow = workflow,
            runBatchOperation = runBatchOperation,
            onQuickFinished = onQuickFinished,
            onBatchFinished = onBatchFinished
        )
    }
    return page
}

/** GPG 加解密 / .pass 保护向导页（PassView / PassEdit 走全屏查看器，不经此页）。 */
private fun cryptoDialogPage(
    workflow: CryptoWorkflowState,
    scope: CoroutineScope,
    rootUriProvider: () -> String?,
    currentDirUriProvider: () -> String,
    runWithProgress: suspend (String, Int?, suspend ((Int) -> Unit) -> Unit) -> Unit,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onBatchFinished: () -> Unit
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        DialogPageBackHandler(progressOp = null, progressCancelAction = null) {
            if (!workflow.inProgress) workflow.close()
        }
        CryptoDialogs(
            workflow = workflow,
            scope = scope,
            rootUri = rootUriProvider(),
            currentDirUri = currentDirUriProvider(),
            runWithProgress = runWithProgress,
            runBatchOperation = runBatchOperation,
            onRefresh = onRefresh,
            onBatchFinished = onBatchFinished
        )
    }
    return page
}

/** 压缩 / 解压向导页（确认后由 executeArchiveOperation 执行文件操作）。 */
private fun archiveOpsDialogPage(
    context: Context,
    scope: CoroutineScope,
    workflow: ArchiveOperationWorkflowState,
    browserTab: BrowserTabState,
    displayUriProvider: () -> String,
    progressOpState: MutableState<OperationProgress?>,
    progressCancelActionState: MutableState<(() -> Unit)?>,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        DialogPageBackHandler(progressOpState.value, progressCancelActionState.value) {
            workflow.close()
        }
        ArchiveOperationDialogs(
            workflow = workflow,
            onConfirm = {
                executeArchiveOperation(
                    context = context,
                    scope = scope,
                    workflow = workflow,
                    browserTab = browserTab,
                    displayUri = displayUriProvider(),
                    progressOpState = progressOpState,
                    progressCancelActionState = progressCancelActionState,
                    runBatchOperation = runBatchOperation
                )
            }
        )
    }
    return page
}

/** 换根页：根选择对话框与清空回收站确认同域同页（先后最多一个显示）。 */
private fun rootSwitchDialogPage(
    workflow: BrowserRootSwitchWorkflow,
    browserTab: BrowserTabState,
    recentRootUrisProvider: () -> List<String>,
    initialDirUriSink: (String) -> Unit,
    treeLauncher: ManagedActivityResultLauncher<Uri?, Uri?>,
    scope: CoroutineScope,
    runWithProgress: suspend (String, Int?, suspend ((Int) -> Unit) -> Unit) -> Unit,
    context: Context
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        DialogPageBackHandler(progressOp = null, progressCancelAction = null) {
            when {
                workflow.showChangeRootConfirm -> workflow.dismissChangeRootConfirm()
                else -> workflow.dismissRootSwitch()
            }
        }
        if (workflow.showRootSwitchDialog) {
            RootSwitchDialog(
                recentRootUris = recentRootUrisProvider(),
                workflow = workflow,
                onPickRoot = { rootUri -> workflow.pickRecentRoot(rootUri, initialDirUriSink) },
                onLaunchPicker = { treeLauncher.launch(null) }
            )
        }
        if (workflow.showChangeRootConfirm && browserTab.rootUri != null) {
            val r = browserTab.rootUri!!
            val root = Uri.parse(normalizeContentUriString(r))
            ChangeRootConfirmDialog(
                onEmptyAndChange = {
                    scope.launch {
                        workflow.dismissChangeRootConfirm()
                        runWithProgress(context.getString(R.string.main_menu_empty_trash), null) { _ ->
                            val ok = withContext(Dispatchers.IO) { emptyTrash(context, root, root) }
                            if (ok) Toast.makeText(context, context.getString(R.string.browser_trash_emptied), Toast.LENGTH_SHORT).show()
                            treeLauncher.launch(null)
                            browserTab.refresh()
                        }
                    }
                },
                onChangeDirectly = {
                    workflow.dismissChangeRootConfirm()
                    treeLauncher.launch(null)
                },
                onCancel = workflow::dismissChangeRootConfirm
            )
        }
    }
    return page
}

/** 公告转发页：转发目标选择流程（完成 / 取消退回）。 */
private fun bulletinForwardDialogPage(
    payloadProvider: () -> BulletinForwardPayload?,
    networkServices: NetworkServicesTabs,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        // 对齐原主界面 BackHandler 行为：转发流程无对话框级 back 分支，
        // 落到退出协商
        BackHandler { onBack() }
        payloadProvider()?.let { payload ->
            BulletinForwardTargetFlow(
                manager = networkServices.familyNetworkManager,
                payload = payload,
                localServiceEnabled = networkServices.config.localNetworkServiceEnabled,
                keepEphemeralSession = !networkServices.familyNetworkForeground,
                onDismiss = onDismiss,
                onComplete = onComplete
            )
        }
    }
    return page
}

/** 外源保存覆盖确认页：目标目录就绪且重名时由 BrowserSaveWorkflow 置位。 */
private fun overwriteConfirmDialogPage(
    browserSave: BrowserSaveWorkflow,
    onOverwriteSaved: (String) -> Unit,
    onSkipped: (String) -> Unit,
    onBack: () -> Unit
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        // 对齐原主界面 BackHandler 行为：覆盖确认无对话框级 back 分支，
        // 落到退出协商
        BackHandler { onBack() }
        browserSave.overwriteConfirm?.let { (_, fileName) ->
            OverwriteSaveConfirmDialog(
                fileName = fileName,
                onOverwrite = { browserSave.confirmOverwrite(onOverwriteSaved) },
                onSkip = { browserSave.skipOverwrite(onSkipped) }
            )
        }
    }
    return page
}

internal fun normalizeContentUriString(s: String): String =
    com.kenny.localmanager.file.normalizeContentUriString(s)
