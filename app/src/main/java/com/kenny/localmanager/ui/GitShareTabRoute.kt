package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable
import com.kenny.localmanager.data.Preferences

data class GitShareTabRouteState(
    val prefs: Preferences,
    val rootUri: String?,
    val onRequestExitApp: () -> Unit
)

@Composable
fun GitShareTabRoute(state: GitShareTabRouteState) {
    FileShareScreen(
        prefs = state.prefs,
        rootUri = state.rootUri?.let { normalizeContentUriString(it) },
        showBackButton = false,
        autoRefreshOnEnter = false,
        onDismiss = state.onRequestExitApp
    )
}
