package com.kenny.localmanager.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel

/**
 * 删除确认对话框（拆解设计 第四批）：会话化后状态内聚——
 * "移入回收站/永久删除"的选择随对话框生命周期，关闭即重置。
 */
@Composable
internal fun DeleteConfirmDialog(
    target: DocumentFileModel,
    hasRoot: Boolean,
    onConfirm: (permanently: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deletePermanently by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browser_confirm_delete_title)) },
        text = {
            Column {
                Text(stringResource(R.string.browser_delete_confirm_single, target.name), color = MaterialTheme.colorScheme.onSurface)
                if (hasRoot) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !deletePermanently,
                            onClick = { deletePermanently = false }
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.browser_move_to_trash_recoverable), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = deletePermanently,
                            onClick = { deletePermanently = true }
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.browser_delete_permanently), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_no_trash_permanent_delete), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(deletePermanently) }
            ) { Text(if (hasRoot && !deletePermanently) stringResource(R.string.browser_moved_to_trash) else stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
