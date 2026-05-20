package com.kenny.localmanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kenny.localmanager.ftp.FtpServerManager

data class FtpTabRouteState(
    val rootUri: String?,
    val displayUri: String,
    val ftpManager: FtpServerManager,
    val ftpPort: Int,
    val ftpPassword: String?,
    val ftpTimeoutMinutes: Int,
    val onRequestExitApp: () -> Unit
)

@Composable
fun FtpTabRoute(state: FtpTabRouteState) {
    val rootUri = state.rootUri
    if (rootUri != null) {
        FtpScreen(
            manager = state.ftpManager,
            treeRootUri = rootUri,
            currentDirUri = state.displayUri,
            port = state.ftpPort,
            password = state.ftpPassword,
            timeoutMinutes = state.ftpTimeoutMinutes,
            showBackButton = false,
            onDismiss = state.onRequestExitApp
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先选择根目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
