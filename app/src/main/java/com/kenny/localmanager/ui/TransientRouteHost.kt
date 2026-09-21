package com.kenny.localmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * 临时 Route（TransientRoute）：一次性的全屏查看流程。
 *
 * 按主界面 Tab 与 Route 分层改造方案（docs/主界面Tab与Route分层改造方案.md §3.3），
 * 这类流程在进入时创建状态、结束后销毁，不要求在 App 顶层长期保留。
 * 首批收编普通查看器 / Markdown 查看器 / HTML 查看器 / PDF 查看器，
 * 压缩查看器等更复杂的流程仍留在 AppShell，待后续批次迁入。
 */
sealed interface TransientRoute {

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
    markdownViewerSessionCache: MarkdownViewerSessionCache,
    bookNote: BookNoteTabFeature,
    recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    onRouteChange: (TransientRoute?) -> Unit
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
