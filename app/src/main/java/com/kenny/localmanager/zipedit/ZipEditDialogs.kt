package com.kenny.localmanager.zipedit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.ZipEditStage
import com.kenny.localmanager.ui.ReliablePasswordInputField
import com.kenny.localmanager.ui.formatBytes

// ============================================================================
// ZIP 编辑域 —— 对话框 UI
//
// 三态分派：待确认（加入确认）、进行中（进度）、有结果（成功含改名明细 /
// 失败含原因）。组件无状态，全部读写 ZipEditWorkflowState；确认后的文件
// 操作经 onConfirm 上抛宿主执行（中转重写、目录刷新、目标配置回写）。
// ============================================================================

@Composable
internal fun ZipEditDialogs(
    workflow: ZipEditWorkflowState,
    onConfirmAdd: () -> Unit,
    onCancelProgress: () -> Unit,
    onSubmitPassword: (String) -> Unit
) {
    val request = workflow.addRequest
    when {
        workflow.passwordRequest != null -> ZipEditPasswordDialog(workflow, onSubmitPassword)
        workflow.inProgress -> ZipEditProgressDialog(workflow, onCancelProgress)
        workflow.result != null -> ZipEditResultDialog(workflow)
        request != null -> ZipAddConfirmDialog(request, onConfirmAdd, workflow)
    }
}

/** 密码收集：目标加密且会话无可用密码。确定后由宿主带密码重试加入。 */
@Composable
private fun ZipEditPasswordDialog(
    workflow: ZipEditWorkflowState,
    onSubmitPassword: (String) -> Unit
) {
    val pwRequest = workflow.passwordRequest ?: return
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { workflow.cancelPassword() },
        title = { Text(stringResource(R.string.zip_edit_password_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.zip_edit_password_hint, pwRequest.targetName),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (pwRequest.wrongPrevious) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.zip_edit_password_wrong),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                // 标题与提示已说明用途，label 传空；掩码显示 + 应用内安全键盘
                ReliablePasswordInputField(
                    value = input,
                    onValueChange = { input = it },
                    label = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = input.isNotEmpty(),
                onClick = {
                    workflow.submitPassword()
                    onSubmitPassword(input)
                }
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = { workflow.cancelPassword() }) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** 加入确认：源清单 + 目标名。 */
@Composable
private fun ZipAddConfirmDialog(
    request: ZipAddRequest,
    onConfirmAdd: () -> Unit,
    workflow: ZipEditWorkflowState
) {
    AlertDialog(
        onDismissRequest = { workflow.cancelRequest() },
        title = { Text(stringResource(R.string.zip_edit_add_title)) },
        text = {
            Column {
                Text(stringResource(R.string.zip_edit_add_confirm, request.targetName, request.sources.size), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                val preview = request.sources.take(8).joinToString("\n") { it.name } +
                    if (request.sources.size > 8) "\n…" else ""
                Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onConfirmAdd) { Text(stringResource(R.string.zip_edit_add_action)) }
        },
        dismissButton = { TextButton(onClick = { workflow.cancelRequest() }) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** 进度：阶段文案 + 线性进度。取消语义在宿主（置位取消标志）。 */
@Composable
private fun ZipEditProgressDialog(
    workflow: ZipEditWorkflowState,
    onCancelProgress: () -> Unit
) {
    val stageLabel = when (workflow.stage) {
        ZipEditStage.COPY_TARGET -> stringResource(R.string.zip_edit_stage_copy)
        ZipEditStage.MODIFY -> stringResource(R.string.zip_edit_stage_modify)
        ZipEditStage.WRITE_BACK -> stringResource(R.string.zip_edit_stage_write_back)
        null -> ""
    }
    val (done, total) = workflow.progress
    val (bytesDone, bytesTotal) = workflow.bytesProgress
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.zip_edit_progress_title)) },
        text = {
            Column {
                Text(stageLabel, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                val fraction = when {
                    bytesTotal > 0 -> (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
                    total > 0 -> done.toFloat() / total
                    else -> null
                }
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    if (bytesTotal > 0) {
                        Text(
                            stringResource(
                                R.string.browser_progress_bytes,
                                formatBytes(bytesDone),
                                formatBytes(bytesTotal),
                                (fraction * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { },
        dismissButton = { TextButton(onClick = onCancelProgress) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** 结果：成功（含同名改名明细 / 加密提示）/ 失败（原因）。 */
@Composable
private fun ZipEditResultDialog(workflow: ZipEditWorkflowState) {
    val result = workflow.result ?: return
    AlertDialog(
        onDismissRequest = { workflow.dismissResult() },
        title = {
            Text(
                stringResource(
                    if (result.ok) R.string.zip_edit_result_ok_title else R.string.zip_edit_result_fail_title
                )
            )
        },
        text = {
            Column {
                if (result.ok) {
                    if (result.renamed.isEmpty()) {
                        Text(stringResource(R.string.zip_edit_result_ok), color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        Text(stringResource(R.string.zip_edit_result_ok), color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.zip_edit_result_renamed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        val detail = result.renamed.joinToString("\n") { (from, to) -> "$from -> $to" }
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (result.addedEncrypted) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.zip_edit_result_encrypted_kept),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (result.wrongPassword) {
                    Text(stringResource(R.string.zip_edit_password_wrong_result), color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Text(result.error ?: "", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { workflow.dismissResult() }) { Text(stringResource(R.string.common_ok)) }
        }
    )
}
