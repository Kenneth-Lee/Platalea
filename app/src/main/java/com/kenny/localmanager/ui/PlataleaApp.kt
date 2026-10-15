package com.kenny.localmanager.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kenny.localmanager.R
import com.kenny.localmanager.data.RecentOpenItem
import com.kenny.localmanager.diagnostics.CrashReportStore
import com.kenny.localmanager.gpg.getGpgKeyDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PlataleaApp——应用组合根（P5 定型）。
 *
 * 只做三件事：**创建**（域对象唯一创建点）、**接线**（跨域出口命令式直呼，
 * 向导页经 DialogPageBinder 绑定域状态）、**组合栈顶**（顶层 when 分派：
 * 解锁门 → 无根门 → 导航栈顶功能页 → 全屏 Route → tab 层页面）。
 * 判断归位后主界面不持有任何功能域的显示判断（见 docs/主界面判断归位设计.md）。
 */
@Composable
fun PlataleaApp(
    initialFileUri: MutableState<String?>? = null,
    initialLaunchTarget: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ==== 创建：域服务与共享状态 ====
    val config = rememberConfigService(context)
    var recentRootUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var initialDirUri by remember { mutableStateOf<String?>(null) }
    val browserTab = rememberBrowserTabState(context, config.browser, initialDirUri)
    val rootSwitch = rememberRootSwitchWorkflow(context, browserTab)
    val browserSave = rememberBrowserSaveWorkflow(context, browserTab)
    val quickObfuscate = rememberQuickObfuscateWorkflow(context)
    var transientRoute by remember { mutableStateOf<TransientRoute?>(null) }
    val markdownViewerSessionCache = rememberViewerSessions(browserTab.rootUri)
    val crypto = rememberCryptoWorkflow()
    val archiveReader = rememberArchiveReaderWorkflow(context, scope)
    val epubReader = rememberEpubReaderWorkflow(context, scope)
    val archiveOps = rememberArchiveOperationWorkflow(context, scope)

    // ==== 创建：导航服务（主界面全部的显示状态）====
    fun persistTabChoice(tab: MainTab, previousTab: MainTab?) {
        scope.launch {
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
    val nav = rememberNavigationState(onTabSwitched = ::persistTabChoice)
    // 速记/书摘功能域服务（功能独立化）：数据与收尾协程住服务，页面只是投影；
    // tab 切换 = 页面退出（onDispose 触发收尾）+ 新页面启动，服务不动
    val quickNoteService = remember { QuickNoteService(context, scope, markdownViewerSessionCache) }
    val bookNoteService = remember { BookNoteService(context, scope, markdownViewerSessionCache) }
    LaunchedEffect(browserTab.rootUri) {
        quickNoteService.rootUri = browserTab.rootUri
        bookNoteService.rootUri = browserTab.rootUri
    }
    val quickNoteTab = quickNoteTabFeature(
        config = config.quickNote,
        service = quickNoteService
    )
    val bookNoteTab = bookNoteTabFeature(
        service = bookNoteService
    )
    var saveCompletedToken by remember { mutableStateOf(0) }
    var recentOpenItems by remember { mutableStateOf<List<RecentOpenItem>>(emptyList()) }

    // ==== 创建：宿主能力（tab 层与功能页共用）====
    fun recordRecentOpen(
        type: String, key: String, title: String,
        uri: String? = null, playlistId: String? = null
    ) {
        scope.launch {
            config.shell.addRecentOpenItem(type, key, title, uri, playlistId)
        }
    }
    val readingLauncher = ReadingLauncher(archiveReader, epubReader) { transientRoute = it }

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

    var lastBackPressTime by remember { mutableStateOf(0L) }
    fun finishCurrentActivity() {
        (context as? Activity)?.finish()
    }
    val launchShortcutTarget = MainTab.fromLaunchTarget(initialLaunchTarget)
    val bypassExitDoubleConfirm = launchShortcutTarget == MainTab.PLAYER || launchShortcutTarget == MainTab.QUICK_NOTE
    fun requestExitApp() {
        if ((nav.currentTab ?: MainTab.DIRECTORY) == MainTab.QUICK_NOTE) {
            quickNoteService.requestExitApp { finishCurrentActivity() }
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

    // ==== 启动行为（门前提探测 / 恢复 / 外源入口）====
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
    LaunchedEffect(config.shell, initialLaunchTarget) {
        if (CrashReportStore.hasUnseenCrash(context)) {
            Toast.makeText(context, context.getString(R.string.crash_report_found_toast), Toast.LENGTH_SHORT).show()
            withContext(Dispatchers.IO) {
                CrashReportStore.exportLatestToRootAndClear(context)
            }
            nav.switchTab(MainTab.ABOUT)
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
    LaunchedEffect(browserSave.pendingSaveFileUri, browserTab.rootUri) {
        browserSave.consumeIfReady { dir ->
            initialDirUri = dir
            browserTab.navigateTo(dir)
            saveCompletedToken++
        }
    }

    // 全屏 Route 聚合槽（批次 D）：即时型查看器最先，工作流终态次之
    val activeRoute: TransientRoute? = transientRoute
        ?: if (archiveReader.routeActive) TransientRoute.WorkflowViewer(archiveReader) else null
        ?: if (epubReader.routeActive) TransientRoute.WorkflowViewer(epubReader) else null
        ?: crypto.passEdit?.let { TransientRoute.PassEditor(crypto) }
        ?: crypto.passContent?.let { TransientRoute.PassViewer(crypto) }
        ?: (crypto.request as? CryptoRequest.PassEdit)?.let { TransientRoute.PassEditor(crypto) }
        ?: (crypto.request as? CryptoRequest.PassView)?.let { TransientRoute.PassViewer(crypto) }

    // ==== 接线：跨域向导功能页（页面工厂 + DialogPageBinder 绑定域状态）====
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
    // 公告转发页（P4：请求槽在家庭网络域 FamilyNetworkManager，宿主不中转）
    val bulletinPage = remember {
        val manager = networkServices.familyNetworkManager
        bulletinForwardDialogPage(
            manager = manager,
            familyNetworkForegroundProvider = { networkServices.familyNetworkForeground },
            localServiceEnabledProvider = { networkServices.config.localNetworkServiceEnabled },
            onDismiss = manager::clearForwardRequest,
            onComplete = {
                browserTab.finishPendingList()
                manager.clearForwardRequest()
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
    DialogPageBinder(nav, quickMaskPage) { quickObfuscate.batchTarget != null || quickObfuscate.quickTarget != null }
    DialogPageBinder(nav, cryptoPage) {
        val r = crypto.request
        r != null && r !is CryptoRequest.PassView && r !is CryptoRequest.PassEdit
    }
    DialogPageBinder(nav, archiveOpsPage) { archiveOps.request != null }
    DialogPageBinder(nav, rootSwitchPage) { rootSwitch.showRootSwitchDialog || rootSwitch.showChangeRootConfirm }
    val bulletinForwardRequest by networkServices.familyNetworkManager.forwardRequest.collectAsState()
    DialogPageBinder(nav, bulletinPage) { bulletinForwardRequest != null }
    DialogPageBinder(nav, overwritePage) { browserSave.overwriteConfirm != null }

    // ==== 组合栈顶：顶层 when 分派（互斥的显示阶段）====
    when {
        showUnlockGate -> StartupUnlockGateScreen(onUnlocked = { unlockedByStartup = true })
        browserTab.rootUri == null && initialDirUri == null && browserSave.overwriteConfirm == null -> NoRootGateScreen(
            externalSavePending = browserSave.pendingSaveFileUri != null,
            treeLauncher = treeLauncher
        )
        // 功能页（导航栈栈顶，如批量执行页）：整页自绘，返回键页内自理
        nav.top != null -> nav.top?.let { it.content() }
        activeRoute != null -> TransientViewerRouteHost(
            route = activeRoute!!,
            config = config.reader,
            markdownViewerSessionCache = markdownViewerSessionCache,
            bookNote = bookNoteService,
            recordRecentOpen = { type, key, title, uri, playlistId ->
                recordRecentOpen(type, key, title, uri, playlistId)
            },
            onRouteChange = { transientRoute = it },
            browserTab = browserTab,
            scope = scope,
        )
        else -> MainTabsScreen(
            MainTabsScreenHost(
                config = config,
                browserTab = browserTab,
                rootSwitch = rootSwitch,
                browserSave = browserSave,
                quickObfuscate = quickObfuscate,
                crypto = crypto,
                archiveOps = archiveOps,
                archiveReader = archiveReader,
                epubReader = epubReader,
                markdownViewerSessionCache = markdownViewerSessionCache,
                networkServices = networkServices,
                quickNoteService = quickNoteService,
                bookNoteService = bookNoteService,
                quickNoteTab = quickNoteTab,
                bookNoteTab = bookNoteTab,
                nav = nav,
                scope = scope,
                requestExitApp = ::requestExitApp,
                recordRecentOpen = ::recordRecentOpen,
                runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
                startBatchOperation = { label, names, onFinished, block -> startBatchOperation(label, names, onFinished, block) },
                readingLauncher = readingLauncher,
                recentOpenItems = recentOpenItems,
                initialDirUri = initialDirUri,
                saveCompletedToken = saveCompletedToken,
                launchShortcutTarget = launchShortcutTarget,
                progressOp = progressOp,
                progressCancelAction = progressCancelAction,
                transientRoute = { transientRoute = it },
            )
        )
    }
    // 宿主操作进度对话框（全局层挂载，任何显示阶段可见）
    progressOp?.let { OperationProgressDialog(it, progressCancelAction) }
}

/** 宿主进度运行器（P2）：状态置位 -> 执行 -> 清理，取消动作业主自定。 */
internal suspend fun runWithUiProgress(
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

internal fun normalizeContentUriString(s: String): String =
    com.kenny.localmanager.file.normalizeContentUriString(s)
