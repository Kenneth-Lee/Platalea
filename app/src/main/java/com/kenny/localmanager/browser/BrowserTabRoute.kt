package com.kenny.localmanager.browser

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.player.PlaybackState
import com.kenny.localmanager.config.BrowserConfigView

internal data class BrowserTabRouteState(
    val modifier: Modifier = Modifier,
    val config: BrowserConfigView,
    val session: BrowserSessionHandle,
    val currentUri: String,
    val refreshTrigger: Int,
    val dirCache: Map<String, CachedDir>,
    val onCacheDir: (uri: String, items: List<DocumentFileModel>) -> Unit,
    val pendingList: List<DocumentFileModel>,
    val rootUri: String?,
    val listState: LazyListState,
    val onNavigate: (String) -> Unit,
    val onBack: () -> Unit,
    val canGoBack: Boolean,
    val onEmptyTrash: (() -> Unit)? = null,
    val isViewingTrash: Boolean = false,
    val filterVisible: Boolean = true,
    val hideDotFiles: Boolean = false,
    val preferredExternalPackages: Map<String, String> = emptyMap(),
    val onOpenFile: (uri: String, name: String, isEncrypted: Boolean) -> Unit,
    val onOpenWithOtherApp: (uri: String, name: String, packageName: String?, rememberChoice: Boolean, forcedMimeType: String?) -> Boolean,
    val onClearExternalOpenPreference: (name: String) -> Unit,
    val onRefresh: () -> Unit,
    val onChangeRoot: () -> Unit,
    val onCreateQuickNote: () -> Unit,
    val onCreateMusicPlaylist: (String, String) -> Unit,
    val onConfirmDelete: ((DocumentFileModel, Boolean) -> Unit)? = null,
    val onRequestOpenUrl: (String) -> Unit,
    val zipEditTargetUri: String? = null,
    val zipEditTargetName: String? = null,
    val zipEditTargetIsZip: Boolean = false,
    val onOpenZipManager: () -> Unit = {},
    val playbackState: PlaybackState? = null,
)

@Composable
internal fun BrowserTabRoute(state: BrowserTabRouteState) {
    BrowserScreen(
        modifier = state.modifier,
        session = state.session,
        currentUri = state.currentUri,
        refreshTrigger = state.refreshTrigger,
        dirCache = state.dirCache,
        onCacheDir = state.onCacheDir,
        pendingList = state.pendingList,
        rootUri = state.rootUri,
        listState = state.listState,
        onNavigate = state.onNavigate,
        onBack = state.onBack,
        canGoBack = state.canGoBack,
        onEmptyTrash = state.onEmptyTrash,
        isViewingTrash = state.isViewingTrash,
        filterVisible = state.filterVisible,
        hideDotFiles = state.hideDotFiles,
        preferredExternalPackages = state.preferredExternalPackages,
        onOpenFile = state.onOpenFile,
        onOpenWithOtherApp = state.onOpenWithOtherApp,
        onClearExternalOpenPreference = state.onClearExternalOpenPreference,
        onRefresh = state.onRefresh,
        onChangeRoot = state.onChangeRoot,
        onCreateQuickNote = state.onCreateQuickNote,
        onCreateMusicPlaylist = state.onCreateMusicPlaylist,
        onConfirmDelete = state.onConfirmDelete,
        onRequestOpenUrl = state.onRequestOpenUrl,
        zipEditTargetUri = state.zipEditTargetUri,
        zipEditTargetName = state.zipEditTargetName,
        zipEditTargetIsZip = state.zipEditTargetIsZip,
        onOpenZipManager = state.onOpenZipManager,
        playbackState = state.playbackState,
        config = state.config
    )
}
