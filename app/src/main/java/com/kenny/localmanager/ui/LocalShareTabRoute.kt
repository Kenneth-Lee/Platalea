package com.kenny.localmanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kenny.localmanager.R
import com.kenny.localmanager.ftp.FtpServerManager

data class WebUiTabRouteState(
    val rootUri: String?,
    val displayUri: String,
    val manager: FtpServerManager,
    val port: Int,
    val password: String?,
    val networkServiceTimeoutMinutes: Int,
    val onRequestExitApp: () -> Unit
)

@Composable
fun WebUiTabRoute(state: WebUiTabRouteState) {
    val rootUri = state.rootUri
    if (rootUri != null) {
        WebUiScreen(
            manager = state.manager,
            treeRootUri = rootUri,
            currentDirUri = state.displayUri,
            port = state.port,
            password = state.password,
            timeoutMinutes = state.networkServiceTimeoutMinutes,
            showBackButton = false,
            onDismiss = state.onRequestExitApp
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.common_select_root_first), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
