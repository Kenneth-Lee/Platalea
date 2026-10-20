package com.kenny.localmanager.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel

/**
 * 待处理项拷贝/移动到当前目录的确认对话框（拆解设计 第四批：会话化迁出）。
 */
@Composable
internal fun QuickCopyMoveConfirmDialog(
    isCopy: Boolean,
    pendingList: List<DocumentFileModel>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = if (isCopy) stringResource(R.string.browser_confirm_copy_title) else stringResource(R.string.browser_confirm_move_title)
    val actionLabel = if (isCopy) stringResource(R.string.pending_list_action_copy) else stringResource(R.string.pending_list_action_move)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    stringResource(R.string.browser_pending_compress_confirm, pendingList.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                val preview = pendingList.take(20)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    preview.forEach { item ->
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (pendingList.size > preview.size) {
                        Text(
                            stringResource(R.string.browser_pending_more_items, pendingList.size - preview.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(actionLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
