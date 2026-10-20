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
    val dialogSession: DialogSession? = null,
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
    val isViewingTrash: Boolean = false,
    val filterVisible: Boolean = true,
    val hideDotFiles: Boolean = false,
    val preferredExternalPackages: Map<String, String> = emptyMap(),
    val onRefresh: () -> Unit,
    val onChangeRoot: () -> Unit,
    val zipEditTargetUri: String? = null,
    val zipEditTargetName: String? = null,
    val zipEditTargetIsZip: Boolean = false,
    val playbackState: PlaybackState? = null,
)

@Composable
internal fun BrowserTabRoute(state: BrowserTabRouteState) {
    BrowserScreen(
        modifier = state.modifier,
        session = state.session,
        dialogSession = state.dialogSession,
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
        isViewingTrash = state.isViewingTrash,
        filterVisible = state.filterVisible,
        hideDotFiles = state.hideDotFiles,
        preferredExternalPackages = state.preferredExternalPackages,
        onRefresh = state.onRefresh,
        onChangeRoot = state.onChangeRoot,
        zipEditTargetUri = state.zipEditTargetUri,
        zipEditTargetName = state.zipEditTargetName,
        zipEditTargetIsZip = state.zipEditTargetIsZip,
        playbackState = state.playbackState,
        config = state.config
    )
}
