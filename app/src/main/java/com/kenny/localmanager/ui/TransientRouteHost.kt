package com.kenny.localmanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.kenny.localmanager.R
import com.kenny.localmanager.file.isHtmlFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 独立全屏 Route（TransientRoute）：从任意 tab 启动、盖住整个主界面的
 * 一次性全屏流程（批次 D 统一）。
 *
 * 两族分支：
 * - 即时型（Viewer / MarkdownViewer / HtmlViewer / PdfViewer）：参数为纯数据，
 *   进入即渲染、退出即销毁；
 * - 工作流型（WorkflowViewer / PassViewer / PassEditor）：携带宿主创建的
 *   workflow 引用，是"请求 -> 密码 -> 解压"状态机的终态，密码 / 进度等
 *   伴随状态仍在 workflow 中。
 *
 * AppShell 只持有一个聚合槽（activeRoute）与本 Host 一个渲染点，不感知
 * 具体功能种类；新增全屏功能在此加分支即可（PlataleaApp 零改动）。
 */
internal sealed interface TransientRoute {

    /** 普通文件查看器（文本/十六进制预览）。 */
    data class Viewer(
        val uri: String,
        val fileName: String,
        val isEncrypted: Boolean
    ) : TransientRoute

    /** Markdown / RST 查看器。 */
    data class MarkdownViewer(
        val uri: String,
        val fileName: String,
        val isEncrypted: Boolean
    ) : TransientRoute

    /** HTML 查看器（本地文件或 URL）。 */
    data class HtmlViewer(val state: HtmlViewState) : TransientRoute

    /** PDF 查看器。 */
    data class PdfViewer(
        val uri: String,
        val fileName: String
    ) : TransientRoute

    /**
     * 工作流型：压缩包 / EPUB 阅读域全屏（Archive 与 Epub 两个 workflow
     * 都实现 ViewerHostWorkflow，经 ArchiveViewerRouteHost 统一分派）。
     */
    data class WorkflowViewer(val workflow: ViewerHostWorkflow) : TransientRoute

    /** 工作流型：Crypto 域 .pass 保护内容查看全屏。 */
    data class PassViewer(val workflow: CryptoWorkflowState) : TransientRoute

    /** 工作流型：Crypto 域 .pass 保护内容编辑全屏。 */
    data class PassEditor(val workflow: CryptoWorkflowState) : TransientRoute
}

/** HTML 查看器路由参数。 */
data class HtmlViewState(
    val location: HtmlViewerLocation,
    val recentKey: String,
    val recentUri: String,
    val recentTitle: String
)

internal fun htmlViewerRouteFromFile(uri: String, name: String): TransientRoute.HtmlViewer =
    TransientRoute.HtmlViewer(
        state = HtmlViewState(
            location = HtmlViewerLocation(
                initialUrl = uri,
                title = name,
                localFileUri = uri
            ),
            recentKey = uri,
            recentUri = uri,
            recentTitle = name
        )
    )

internal fun htmlViewerRouteFromUrl(url: String): TransientRoute.HtmlViewer =
    TransientRoute.HtmlViewer(
        state = HtmlViewState(
            location = HtmlViewerLocation(
                initialUrl = url,
                title = url,
                localFileUri = null
            ),
            recentKey = url,
            recentUri = url,
            recentTitle = url
        )
    )

/**
 * 查看器类临时 Route 的统一 Host。
 *
 * 只负责渲染当前 route 对应的全屏界面，导航决策（关闭或切换到下一个 route）
 * 全部通过 [onRouteChange] 回传给 AppShell，Host 自身不持有跨组合状态。
 */
@Composable
internal fun TransientViewerRouteHost(
    route: TransientRoute,
    config: ReaderConfigView,
    markdownViewerSessionCache: ViewerSessions,
    bookNote: BookNoteTabFeature,
    recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    onRouteChange: (TransientRoute?) -> Unit,
    browserTab: BrowserTabState,
    scope: CoroutineScope,
    onEncryptedExitRequest: (EncryptedCacheExitDialogState) -> Unit
) {
    when (route) {
        is TransientRoute.MarkdownViewer -> {
            BackHandler { onRouteChange(null) }
            MarkdownViewerScreen(
                config = config,
                initialFileUri = route.uri,
                initialFileName = route.fileName,
                isEncrypted = route.isEncrypted,
                sessionCache = markdownViewerSessionCache,
                onBack = { onRouteChange(null) },
                onOpenFile = { openUri, openName, openEncrypted ->
                    when {
                        openName.endsWith(".md", ignoreCase = true) || openName.endsWith(".rst", ignoreCase = true) ->
                            onRouteChange(TransientRoute.MarkdownViewer(openUri, openName, openEncrypted))
                        isHtmlFile(openName) ->
                            onRouteChange(htmlViewerRouteFromFile(openUri, openName))
                        else ->
                            onRouteChange(TransientRoute.Viewer(openUri, openName, openEncrypted))
                    }
                }
            )
        }
        is TransientRoute.HtmlViewer -> {
            LaunchedEffect(route.state.recentKey) {
                recordRecentOpen(
                    RECENT_TYPE_HTML_VIEWER,
                    route.state.recentKey,
                    route.state.recentTitle,
                    route.state.recentUri,
                    null
                )
            }
            BackHandler { onRouteChange(null) }
            HtmlViewerScreen(
                config = config,
                initialLocation = route.state.location,
                onBack = { onRouteChange(null) }
            )
        }
        is TransientRoute.PdfViewer -> {
            LaunchedEffect(route.uri) {
                recordRecentOpen(
                    RECENT_TYPE_PDF_VIEWER,
                    route.uri,
                    route.fileName,
                    route.uri,
                    null
                )
            }
            BackHandler { onRouteChange(null) }
            PdfViewerScreen(
                config = config,
                uri = route.uri,
                fileName = route.fileName,
                bookNoteLoadedData = bookNote.bookNoteData,
                bookNoteEntries = bookNote.bookNoteEntries,
                bookNoteInProgress = bookNote.bookNoteInProgress,
                onRequestOpenBookNotes = bookNote::requestOpenBookNotes,
                onBookNoteEntriesChanged = bookNote::updateBookNoteEntries,
                onBack = { onRouteChange(null) }
            )
        }
        is TransientRoute.WorkflowViewer -> {
            ArchiveViewerHostLayer(
                workflow = route.workflow,
                browserTab = browserTab,
                readerConfig = config,
                markdownViewerSessionCache = markdownViewerSessionCache,
                bookNote = bookNote,
                recordRecentOpen = recordRecentOpen,
                onEncryptedExitRequest = onEncryptedExitRequest
            )
        }
        is TransientRoute.PassViewer -> {
            // Route 构造条件即 passContent 非空；此处判空防御重组间隙（关闭瞬间）
            val content = route.workflow.passContent
            if (content != null) {
                BackHandler { route.workflow.closePassContent() }
                PassContentViewerScreen(
                    config = config,
                    innerFileName = content.innerFileName,
                    decryptedBytes = content.decryptedBytes,
                    onBack = { route.workflow.closePassContent() }
                )
            }
        }
        is TransientRoute.PassEditor -> {
            val state = route.workflow.passEdit
            if (state != null) {
                val context = LocalContext.current
                BackHandler { route.workflow.closePassEdit() }
                PassEditScreen(
                    fileName = state.innerName,
                    initialDecryptedBytes = state.decryptedBytes,
                    onSave = { newBytes ->
                        scope.launch {
                            val ok = savePassEditReencrypted(context, state, newBytes)
                            route.workflow.closePassEdit()
                            browserTab.refresh()
                            Toast.makeText(
                                context,
                                if (ok) context.getString(R.string.browser_saved_and_reencrypted)
                                else context.getString(R.string.viewer_save_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onBack = { route.workflow.closePassEdit() }
                )
            }
        }
        is TransientRoute.Viewer -> {
            BackHandler { onRouteChange(null) }
            ViewerScreen(
                fileUri = route.uri,
                fileName = route.fileName,
                isEncrypted = route.isEncrypted,
                onBack = { onRouteChange(null) },
                onOpenMarkdownView = if (route.fileName.endsWith(".md", ignoreCase = true) || route.fileName.endsWith(".rst", ignoreCase = true)) {
                    {
                        onRouteChange(
                            TransientRoute.MarkdownViewer(
                                route.uri,
                                route.fileName,
                                route.isEncrypted
                            )
                        )
                    }
                } else null
            )
        }
    }
}
