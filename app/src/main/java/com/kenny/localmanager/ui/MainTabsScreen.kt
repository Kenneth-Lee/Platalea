package com.kenny.localmanager.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import com.kenny.localmanager.data.RecentOpenItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * tab 层页面（P5 从主函数局部函数 MainTabsRootContent 归位）：组合根之外
 * 最大的显示原子——tab 栏模板 + 全部 tab 内容（MainTabContentHost）。
 *
 * 与组合根的关系：域对象与宿主能力由组合根创建、经 [host] 接线传入；
 * 本页面只做 tab 层的组合与返回键（页内对话框、阅读器请求、目录返回、
 * 退出协商），不再反向触碰组合根状态。
 */
@Composable
internal fun MainTabsScreen(host: MainTabsScreenHost) {
    val context = LocalContext.current
    with(host) {
        val displayUri = browserTab.currentUri ?: initialDirUri ?: browserTab.rootUri!!
        val activeMainTab = nav.currentTab ?: MainTab.DIRECTORY

        fun requestOpenQuickNoteTabFromDirectory() {
            nav.switchTab(MainTab.QUICK_NOTE)
            quickNoteService.requestOpenWithCachedPassword(true)
        }

        // App-level transient dialogs / playback-target workflow
        LaunchedEffect(saveCompletedToken) { if (saveCompletedToken > 0) browserTab.refresh() }

        // DirectoryRoot derived UI state
        LaunchedEffect(browserTab.rootUri) {
            RootSwitchModule.service.refreshTrashUri(browserTab.rootUri)
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

        // PlayerTab 域：播放器 tab 功能对象（接口面：MainTabFeature + PlayerTabApi，实现不可见）
        val playerTab = playerTabFeature(
            context = context,
            config = config.player,
            scope = scope,
            autoplayShortcut = launchShortcutTarget == MainTab.PLAYER,
            onRequestExitApp = { requestExitApp() },
            recordRecentOpen = { type, key, title, uri, playlistId ->
                recordRecentOpen(type, key, title, uri, playlistId)
            },
            onBatchFinished = { browserTab.finishPendingList() },
            onOpenPlayerTab = { nav.switchTab(MainTab.PLAYER) },
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
                ArchiveReaderModule.service.request?.kind in ArchiveReaderCancelableKinds -> ArchiveReaderModule.service.cancelRequest()
                EpubReaderModule.service.request?.kind in EpubReaderCancelableKinds -> EpubReaderModule.service.cancelRequest()
                browserTab.canGoBack -> browserTab.goBack()
                else -> {
                    requestExitApp()
                }
            }
        }

        // P3：tab 层页面模板（tab 栏渲染与项分类归页面框架，主界面不渲染 tab 栏）
        MainTabsScaffold(
            nav = nav,
            header = {
                SavingProgressHeader(
                    pubEncryptInProgress = CryptoModule.service.pubEncryptInProgress,
                    saveInProgress = BrowserSaveModule.service.inProgress
                )
            }
        ) {
            val browserTabHostDependencies = BrowserTabHostDependencies(
                scope = scope,
                configImportExportWorkflow = configTab.importExport,
                markdownViewerSessionCache = markdownViewerSessionCache,
                playbackState = playerTab.playbackState,
                initialDirUri = initialDirUri,
                cachedTrashUri = RootSwitchModule.service.cachedTrashUri,
                setTransientRoute = transientRoute,
                recordRecentOpen = { type, key, title, uri, playlistId ->
                    recordRecentOpen(type, key, title, uri, playlistId)
                },
                playerTab = playerTab,
                onForwardToBulletin = { payload -> networkServices.familyNetworkManager.requestForward(payload) },
                onOpenQuickNote = { requestOpenQuickNoteTabFromDirectory() },
                requestOpenPlayerTab = { nav.switchTab(MainTab.PLAYER) },
                runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
                runBatchOperation = { label, names, onFinished, block ->
                    startBatchOperation(label, names, onFinished, block)
                },
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
                                                    recordRecentOpen(type, key, title, uri, playlistId)
                                                },
                                                switchMainTab = { tab -> nav.switchTab(tab) },
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
        }
    }
}

/**
 * tab 层页面的接线快照（组合根创建后传入；字段即原局部捕获，语义不变）。
 * launchShortcutTarget/autoplayShortcut 等启动参数也经此下发。
 */
internal class MainTabsScreenHost(
    // 域对象（组合根创建）
    val config: ConfigService,
    val browserTab: BrowserTabState,
    val markdownViewerSessionCache: ViewerSessions,
    val networkServices: NetworkServicesTabs,
    // 域服务（功能独立化：速记/书摘数据与收尾住服务，跨域出口直呼）
    val quickNoteService: QuickNoteService,
    val bookNoteService: BookNoteService,
    // 速记/书摘 tab 装配（content/overlay 的薄壳，由组合根套 service 生成）
    val quickNoteTab: MainTabFeature,
    val bookNoteTab: MainTabFeature,
    val nav: NavigationState,
    val scope: CoroutineScope,
    // 宿主能力（组合根函数）
    val requestExitApp: () -> Unit,
    val recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    val runWithProgress: suspend (String, Int?, suspend ((Int) -> Unit) -> Unit) -> Unit,
    val startBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    val readingLauncher: ReadingLauncher,
    // 共享状态投影
    val recentOpenItems: List<RecentOpenItem>,
    val initialDirUri: String?,
    val saveCompletedToken: Int,
    val launchShortcutTarget: MainTab?,
    val progressOp: OperationProgress?,
    val progressCancelAction: (() -> Unit)?,
    val transientRoute: (TransientRoute?) -> Unit,
)
