package com.kenny.localmanager.epub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.archivereader.ArchiveLogDialog
import com.kenny.localmanager.archivereader.ArchivePasswordDialog
import com.kenny.localmanager.archivereader.ArchivePlainErrorDialog
import com.kenny.localmanager.archivereader.ArchiveProcessingDialog
import com.kenny.localmanager.archivereader.ArchiveReaderDialogs
// ============================================================================
// EPUB 阅读器域 —— 请求阶段 UI（对话框，批次 C：自 ArchiveReaderDialogs 分离）
//
// 按 workflow.request / encrypted / inProgress / loadError 渲染：
// 密码输入（ArchivePasswordDialog，共享组件）、解压进度、加载日志、
// 解析错误（TXT / LLM / EPUB）。组件无状态，全部读写 EpubReaderWorkflowState。
// ============================================================================

@Composable
internal fun EpubReaderDialogs(workflow: EpubReaderWorkflowState) {
    val request = workflow.request
    if (request != null) {
        EpubReaderRequestDialogs(request, workflow)
    }

    // EPUB 错误对话框（独立于请求：密码取消后残留的解析错误，此时 request 已清空）
    if (request == null && workflow.lastKind == EpubReaderKind.EPUB && workflow.loadError != null) {
        AlertDialog(
            onDismissRequest = { workflow.dismissErrorOnly() },
            title = { Text(stringResource(R.string.browser_epub_parse_failed_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        workflow.log,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { workflow.dismissErrorOnly() }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }
}

@Composable
private fun EpubReaderRequestDialogs(
    request: EpubReaderRequest,
    workflow: EpubReaderWorkflowState
) {
    val target = request.target
    val encrypted = workflow.encrypted
    when (request.kind) {
        EpubReaderKind.LLM_ZIP -> {
            if (encrypted == true) {
                ArchivePasswordDialog(
                    title = stringResource(R.string.context_view_zip_llm),
                    targetName = target.name,
                    workflow = workflow
                )
            } else if (encrypted == null) {
                ArchiveProcessingDialog(
                    title = stringResource(R.string.context_view_zip_llm),
                    targetName = target.name
                )
            }
        }
        EpubReaderKind.EPUB -> {
            if (encrypted == true) {
                ArchivePasswordDialog(
                    title = stringResource(R.string.browser_view_epub_title),
                    targetName = target.name,
                    workflow = workflow
                )
            } else {
                // encrypted == null 或 encrypted == false，都显示加载对话框
                ArchiveLogDialog(
                    title = stringResource(R.string.browser_open_epub_title),
                    workflow = workflow
                )
            }
        }
        EpubReaderKind.TXT, EpubReaderKind.LLM -> Unit // 无中间对话框，仅错误对话框
    }

    // TXT 解析失败对话框
    if (request.kind == EpubReaderKind.TXT && workflow.loadError != null) {
        ArchivePlainErrorDialog(
            title = stringResource(R.string.browser_txt_parse_failed_title),
            error = workflow.loadError ?: "",
            onDismiss = { workflow.dismissRequestError() }
        )
    }
    // LLM 解析失败对话框
    if (request.kind == EpubReaderKind.LLM && workflow.loadError != null) {
        ArchivePlainErrorDialog(
            title = stringResource(R.string.browser_llm_parse_failed_title),
            error = workflow.loadError ?: "",
            onDismiss = { workflow.dismissRequestError() }
        )
    }
}
