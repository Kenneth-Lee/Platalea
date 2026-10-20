package com.kenny.localmanager.browser

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R

/**
 * 规整用户输入的 URL：无 scheme 时补 https，仅接受 http/https 且主机非空。
 */
internal fun normalizeUserInputUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    val parsed = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
    val scheme = parsed.scheme?.lowercase()
    val host = parsed.host?.trim().orEmpty()
    return if ((scheme == "http" || scheme == "https") && host.isNotEmpty()) normalized else null
}

/**
 * 打开 URL 对话框（拆解设计 第四批：会话化迁出）。
 * 输入与错误状态随会话生命周期，关闭即销毁（设计预期改进）。
 */
@Composable
internal fun OpenUrlDialog(
    onConfirm: (url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_menu_open_url)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        if (error != null) error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.open_url_label)) },
                    placeholder = { Text(stringResource(R.string.open_url_placeholder)) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.open_url_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(err, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = normalizeUserInputUrl(input)
                if (normalized == null) {
                    error = ctx.getString(R.string.open_url_invalid)
                } else {
                    onConfirm(normalized)
                }
            }) {
                Text(stringResource(R.string.open_url_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}
