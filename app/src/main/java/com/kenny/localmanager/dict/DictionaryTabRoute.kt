package com.kenny.localmanager.dict

import androidx.compose.runtime.Composable
import com.kenny.localmanager.config.DictionaryConfigView

internal data class DictionaryTabRouteState(
    val config: DictionaryConfigView,
    val onRequestExitApp: () -> Unit
)

@Composable
internal fun DictionaryTabRoute(state: DictionaryTabRouteState) {
    DictionaryScreen(config = state.config, showBackButton = false, onBack = state.onRequestExitApp)
}
