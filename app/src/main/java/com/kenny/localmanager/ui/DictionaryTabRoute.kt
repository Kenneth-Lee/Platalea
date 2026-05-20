package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable

data class DictionaryTabRouteState(
    val onRequestExitApp: () -> Unit
)

@Composable
fun DictionaryTabRoute(state: DictionaryTabRouteState) {
    DictionaryScreen(showBackButton = false, onBack = state.onRequestExitApp)
}
