package com.kenny.localmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.kenny.localmanager.R
import com.kenny.localmanager.file.cleanEpubCache
import com.kenny.localmanager.file.cleanHtmlZipCache
import com.kenny.localmanager.file.cleanMdZipCache
import com.kenny.localmanager.file.cleanPicZipCache
import java.io.File

// ============================================================================
// Archive 阅读器域 —— 查看器阶段宿主
//
// 解压完成后的全屏渲染分派：按 ArchiveViewerRoute 子类调用各查看器 Screen
// （MdZip / HtmlZip / Epub 在 MarkdownViewerScreen.kt，PicZip 在 PicZipViewerScreen.kt，
//  VidZip 在 VidZipViewerScreen.kt）。统一处理"最近打开"记录与加密退出善后
// （保留 / 删除缓存确认经 onEncryptedExitRequest 上抛宿主渲染）。
// ============================================================================

@Composable
internal fun ArchiveViewerRouteHost(
    workflow: ArchiveReaderWorkflowState,
    readerConfig: ReaderConfigView,
    markdownViewerSessionCache: MarkdownViewerSessionCache,
    bookNote: BookNoteTabFeature,
    recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    onSavePicZipImage: suspend (File, String) -> Boolean,    onEncryptedExitRequest: (EncryptedCacheExitDialogState) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun requestEncryptedExit(title: String, onCleanup: () -> Unit) {
        onEncryptedExitRequest(
            EncryptedCacheExitDialogState(
                title = title,
                onDelete = {
                    onCleanup()
                    workflow.closeViewer()
                },
                onKeep = {
                    workflow.closeViewer()
                }
            )
        )
    }

    when (val state = workflow.viewer) {
        is MdZipViewState -> {
            LaunchedEffect(state.zipUri) {
                recordRecentOpen(
                    RECENT_TYPE_ZIP_VIEWER,
                    state.zipUri.toString(),
                    state.zipFileName,
                    state.zipUri.toString(),
                    null
                )
            }
            fun close() {
                if (state.isEncrypted) {
                    requestEncryptedExit(
                        title = context.getString(R.string.browser_exit_encrypted_md),
                        onCleanup = { cleanMdZipCache(context, state.zipUri) }
                    )
                } else {
                    workflow.closeViewer()
                }
            }
            BackHandler { close() }
            MdZipViewerScreen(
                config = readerConfig,
                initialTargetFile = state.targetFile,
                contentDir = state.contentDir,
                zipFileName = state.zipFileName,
                sessionCache = markdownViewerSessionCache,
                onBack = { close() },
                logDebug = null
            )
        }
        is HtmlZipViewState -> {
            LaunchedEffect(state.zipUri) {
                recordRecentOpen(
                    RECENT_TYPE_ZIP_VIEWER,
                    state.zipUri.toString(),
                    state.zipFileName,
                    state.zipUri.toString(),
                    null
                )
            }
            fun close() {
                if (state.isEncrypted) {
                    requestEncryptedExit(
                        title = context.getString(R.string.browser_exit_encrypted_html),
                        onCleanup = { cleanHtmlZipCache(context, state.zipUri) }
                    )
                } else {
                    workflow.closeViewer()
                }
            }
            BackHandler { close() }
            HtmlZipViewerScreen(
                config = readerConfig,
                initialIndexFile = state.indexFile,
                contentDir = state.contentDir,
                zipFileName = state.zipFileName,
                onBack = { close() },
                logDebug = null
            )
        }
        is EpubViewState -> {
            LaunchedEffect(state.epubUri) {
                recordRecentOpen(
                    RECENT_TYPE_EPUB_RENDERER,
                    state.epubUri.toString(),
                    state.zipFileName,
                    state.epubUri.toString(),
                    null
                )
            }
            fun close() {
                if (state.isEncrypted) {
                    requestEncryptedExit(
                        title = context.getString(R.string.browser_exit_encrypted_book),
                        onCleanup = { cleanEpubCache(context, state.epubUri) }
                    )
                } else {
                    workflow.closeViewer()
                }
            }
            BackHandler { close() }
            EpubViewerScreen(
                config = readerConfig,
                extractResult = state.extractResult,
                zipFileName = state.zipFileName,
                epubUri = state.epubUri,
                bookNoteLoadedData = bookNote.bookNoteData,
                bookNoteEntries = bookNote.bookNoteEntries,
                bookNoteInProgress = bookNote.bookNoteInProgress,
                onRequestOpenBookNotes = bookNote::requestOpenBookNotes,
                onBookNoteEntriesChanged = bookNote::updateBookNoteEntries,
                onBack = { close() },
                logDebug = null
            )
        }
        is PicZipViewState -> {
            LaunchedEffect(state.zipUri, state.recordRecent) {
                if (state.recordRecent) {
                    recordRecentOpen(
                        RECENT_TYPE_ZIP_VIEWER,
                        state.zipUri.toString(),
                        state.zipFileName,
                        state.zipUri.toString(),
                        null
                    )
                }
            }
            fun close() {
                if (state.isEncrypted) {
                    requestEncryptedExit(
                        title = context.getString(R.string.browser_exit_encrypted_pic),
                        onCleanup = { cleanPicZipCache(context, state.zipUri) }
                    )
                } else {
                    state.cleanupCacheDir?.deleteRecursively()
                    workflow.closeViewer()
                }
            }
            BackHandler { close() }
            PicZipViewerScreen(
                contentDir = state.contentDir,
                imagePaths = state.imagePaths,
                zipFileName = state.zipFileName,
                isEncrypted = state.isEncrypted,
                password = state.password,
                initialIndex = state.initialIndex,
                onSaveCurrentImage = { sourceFile, fileName ->
                    onSavePicZipImage(sourceFile, fileName)
                },
                onBack = { close() }
            )
        }
        is VidZipViewState -> {
            LaunchedEffect(state.zipUri, state.recordRecent) {
                if (state.recordRecent) {
                    recordRecentOpen(
                        RECENT_TYPE_ZIP_VIEWER,
                        state.zipUri.toString(),
                        state.zipFileName,
                        state.zipUri.toString(),
                        null
                    )
                }
            }
            fun close() {
                if (state.isEncrypted) {
                    requestEncryptedExit(
                        title = context.getString(R.string.browser_exit_encrypted_vid),
                        onCleanup = { state.cleanupCacheDir?.deleteRecursively() }
                    )
                } else {
                    workflow.closeViewer()
                }
            }
            BackHandler { close() }
            VidZipViewerScreen(
                contentDir = state.contentDir,
                videoPaths = state.videoPaths,
                zipUri = state.zipUri,
                isEncrypted = state.isEncrypted,
                password = state.password,
                onClose = { close() }
            )
        }
        null -> Unit
    }
}
