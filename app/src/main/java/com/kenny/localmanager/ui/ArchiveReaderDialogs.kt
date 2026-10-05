package com.kenny.localmanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
// ============================================================================
// Archive 阅读器域 —— 请求阶段 UI（对话框）
//
// 按 workflow.request / encrypted / inProgress / loadError 渲染：
// 密码输入（ArchivePasswordDialog）、检测/解压进度、加载日志（html.zip）、解析错误。
// 组件无状态，全部读写 ArchiveReaderWorkflowState。
// （EPUB 族对话框已分离至 EpubReaderDialogs，批次 C；密码/日志/纯文本错误组件两族共享）
// ============================================================================

@Composable
internal fun ArchiveReaderDialogs(workflow: ArchiveReaderWorkflowState) {
    val request = workflow.request
    if (request != null) {
        ArchiveReaderRequestDialogs(request, workflow)
    }

}

@Composable
private fun ArchiveReaderRequestDialogs(
    request: ArchiveReaderRequest,
    workflow: ArchiveReaderWorkflowState
) {
    val target = request.target
    val encrypted = workflow.encrypted
    when (request.kind) {
        ArchiveReaderKind.MD_ZIP -> {
            if (encrypted == true) {
                ArchivePasswordDialog(
                    title = stringResource(R.string.context_view_zip_markdown),
                    targetName = target.name,
                    workflow = workflow
                )
            } else if (encrypted == null) {
                // 正在检测加密状态或正在解压非加密 zip
                ArchiveProcessingDialog(
                    title = stringResource(R.string.context_view_zip_markdown),
                    targetName = target.name
                )
            }
        }
        ArchiveReaderKind.HTML_ZIP -> {
            if (encrypted == true) {
                ArchivePasswordDialog(
                    title = stringResource(R.string.context_view_zip_html),
                    targetName = target.name,
                    workflow = workflow
                )
            } else if (encrypted == null) {
                // 加载中或显示错误
                ArchiveLogDialog(
                    title = stringResource(R.string.context_view_zip_html),
                    workflow = workflow
                )
            }
        }
        ArchiveReaderKind.PIC_ZIP -> {
            if (encrypted == true) {
                ArchivePasswordDialog(
                    title = stringResource(R.string.browser_view_pic_zip_title),
                    targetName = target.name,
                    workflow = workflow
                )
            } else if (encrypted == null) {
                ArchiveProcessingDialog(
                    title = stringResource(R.string.browser_view_pic_zip_title),
                    targetName = target.name
                )
            } else if (encrypted == false && workflow.inProgress) {
                ArchiveExtractingDialog(
                    title = stringResource(R.string.browser_view_pic_zip_title),
                    targetName = target.name
                )
            }
        }
        ArchiveReaderKind.VID_ZIP -> {
            if (encrypted == true) {
                ArchivePasswordDialog(
                    title = stringResource(R.string.browser_view_vid_zip_title),
                    targetName = target.name,
                    workflow = workflow
                )
            } else if (encrypted == null) {
                ArchiveProcessingDialog(
                    title = stringResource(R.string.browser_view_vid_zip_title),
                    targetName = target.name
                )
            } else if (encrypted == false && workflow.inProgress) {
                ArchiveExtractingDialog(
                    title = stringResource(R.string.browser_view_vid_zip_title),
                    targetName = target.name
                )
            }
        }
    }

}

/** 加密压缩包密码输入对话框。 */
@Composable
internal fun ArchivePasswordDialog(
    title: String,
    targetName: String,
    workflow: ReaderRequestWorkflow
) {
    AlertDialog(
        onDismissRequest = { workflow.cancelRequest() },
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(R.string.browser_zip_encrypted_prompt, targetName), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                ReliablePasswordInputField(
                    value = workflow.password,
                    onValueChange = { workflow.updatePassword(it) },
                    label = { Text(stringResource(R.string.browser_zip_password_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !workflow.inProgress
                )
                if (workflow.inProgress) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { workflow.submitPassword() }
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { workflow.cancelRequest() }) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** 检测加密状态 / 处理文件进度对话框。 */
@Composable
internal fun ArchiveProcessingDialog(title: String, targetName: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(R.string.browser_processing_file, targetName), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {}
    )
}

/** 解压中进度对话框（非加密，已确认 encrypted == false）。 */
@Composable
internal fun ArchiveExtractingDialog(title: String, targetName: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(R.string.browser_extracting_file, targetName), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {}
    )
}

/** 加载日志对话框（html.zip / epub）：滚动日志 + 出错时关闭按钮。 */
@Composable
internal fun ArchiveLogDialog(
    title: String,
    workflow: ReaderRequestWorkflow
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (workflow.inProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.browser_loading_dots), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    workflow.log,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            if (workflow.loadError != null) {
                TextButton(onClick = { workflow.dismissRequestError() }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    )
}

/** 纯文本错误对话框（TXT / LLM 解析失败）。 */
@Composable
internal fun ArchivePlainErrorDialog(
    title: String,
    error: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        }
    )
}
