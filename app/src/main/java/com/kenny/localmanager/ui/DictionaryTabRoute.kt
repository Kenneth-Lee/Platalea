package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable

internal data class DictionaryTabRouteState(
    val config: DictionaryConfigView,
    val onRequestExitApp: () -> Unit
)

@Composable
internal fun DictionaryTabRoute(state: DictionaryTabRouteState) {
    DictionaryScreen(config = state.config, showBackButton = false, onBack = state.onRequestExitApp)
}
