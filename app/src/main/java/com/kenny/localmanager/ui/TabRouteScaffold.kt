package com.kenny.localmanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kenny.localmanager.R

data class TabPasswordPromptState(
    val required: Boolean,
    val password: String,
    val inProgress: Boolean,
    val onPasswordChange: (String) -> Unit,
    val onConfirm: (String) -> Unit,
    val onDismiss: () -> Unit
)

interface BaseTabRouteController<T> {
    val data: T?
    val inProgress: Boolean
    val passwordPromptState: TabPasswordPromptState
    fun persistIfNeeded(reason: String, onFinished: ((Boolean) -> Unit)? = null)
}

@Composable
fun <T> TabRouteContent(
    controller: BaseTabRouteController<T>,
    loadingText: String,
    content: @Composable (T) -> Unit
) {
    val data = controller.data
    if (data != null) {
        content(data)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (controller.inProgress) {
                CircularProgressIndicator()
            } else {
                Text(loadingText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TabPasswordPromptDialog(
    state: TabPasswordPromptState,
    fileName: String,
    passwordLabel: String? = null
) {
    if (!state.required) return
    val resolvedPasswordLabel = passwordLabel ?: stringResource(R.string.tab_password_label)
    GpgPasswordDialog(
        isDecrypt = true,
        fileName = fileName,
        password = state.password,
        passwordLabel = resolvedPasswordLabel,
        inProgress = state.inProgress,
        onPasswordChange = state.onPasswordChange,
        onConfirm = state.onConfirm,
        onDismiss = state.onDismiss
    )
}