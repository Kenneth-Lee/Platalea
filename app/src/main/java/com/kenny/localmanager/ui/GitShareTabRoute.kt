package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable

internal data class GitShareTabRouteState(
    val config: GitConfigView,
    val rootUri: String?,
    val onRequestExitApp: () -> Unit
)

@Composable
internal fun GitShareTabRoute(state: GitShareTabRouteState) {
    FileShareScreen(
        config = state.config,
        rootUri = state.rootUri?.let { normalizeContentUriString(it) },
        showBackButton = false,
        autoRefreshOnEnter = false,
        onDismiss = state.onRequestExitApp
    )
}
