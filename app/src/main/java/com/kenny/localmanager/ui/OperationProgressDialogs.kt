package com.kenny.localmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kenny.localmanager.R
import java.util.Locale

/**
 * 通用操作进度件：进度数据 + 取消标志 + 进度弹窗（各域长任务共用，AppShell 渲染）。
 */
internal data class OperationProgress(val label: String, val current: Int = 0, val total: Int? = null)

internal class OperationCancelFlag {
    @Volatile
    private var cancelled: Boolean = false

    fun requestCancel() {
        cancelled = true
    }

    fun isCancelled(): Boolean {
        return cancelled
    }
}

@Composable
internal fun OperationProgressDialog(progress: OperationProgress, onCancel: (() -> Unit)? = null) {
    Dialog(onDismissRequest = { }) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.browser_progress_in_progress, progress.label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                if (progress.total != null && progress.total > 0) {
                    val frac = (progress.current.toFloat() / progress.total).coerceIn(0f, 1f)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { frac })
                    Spacer(Modifier.height(8.dp))
                    val progressText = if (progress.total >= 1000) {
                        String.format(Locale.getDefault(), "%.1f%%", frac * 100f)
                    } else {
                        stringResource(R.string.browser_progress_items, progress.current, progress.total ?: 0)
                    }
                    Text(progressText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_processing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onCancel != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                }
            }
        }
    }
}
