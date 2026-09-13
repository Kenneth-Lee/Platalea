package com.kenny.localmanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.ArchiveCompressionFormat
import com.kenny.localmanager.file.buildArchiveOutputName

// ============================================================================
// Archive 操作向导域 —— 对话框 UI
//
// 按 workflow.request 分派三种向导表单：解压确认、单文件压缩、批量压缩。
// 组件无状态，全部读写 ArchiveOperationWorkflowState；确认后的文件操作
// 经 onConfirm 上抛宿主执行（进度、取消、目录刷新属宿主编排）。
// ============================================================================

@Composable
internal fun ArchiveOperationDialogs(
    workflow: ArchiveOperationWorkflowState,
    onConfirm: () -> Unit
) {
    when (val req = workflow.request) {
        is ArchiveOperationRequest.Unzip -> UnzipWizardDialog(req, workflow, onConfirm)
        is ArchiveOperationRequest.CompressFile -> CompressFileWizardDialog(req, workflow, onConfirm)
        is ArchiveOperationRequest.CompressPending -> PendingCompressWizardDialog(req, workflow, onConfirm)
        null -> Unit
    }
}

/** 解压向导：确认文案 + 加密状态提示 + 密码（检测出加密后启用）。 */
@Composable
private fun UnzipWizardDialog(
    request: ArchiveOperationRequest.Unzip,
    workflow: ArchiveOperationWorkflowState,
    onConfirm: () -> Unit
) {
    val encrypted = workflow.encrypted
    AlertDialog(
        onDismissRequest = { workflow.close() },
        title = { Text(stringResource(R.string.browser_extract_archive_title)) },
        text = {
            Column {
                Text(stringResource(R.string.browser_extract_archive_confirm, request.target.name), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                ) {
                    when (encrypted) {
                        true -> Text(
                            stringResource(R.string.browser_archive_encrypted_prompt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        null -> Text(
                            stringResource(R.string.browser_detecting_encryption),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> { }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (encrypted != false) {
                    ReliablePasswordInputField(
                        value = workflow.password,
                        onValueChange = { workflow.updatePassword(it) },
                        label = { Text(stringResource(R.string.browser_archive_password_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = encrypted == true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (encrypted == true && workflow.password.isBlank()) return@Button
                    onConfirm()
                }
            ) { Text(stringResource(R.string.browser_extract_action)) }
        },
        dismissButton = { TextButton(onClick = { workflow.close() }) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** 单文件 / 目录压缩向导：格式选择 + 自动命名提示 + 可选密码。 */
@Composable
private fun CompressFileWizardDialog(
    request: ArchiveOperationRequest.CompressFile,
    workflow: ArchiveOperationWorkflowState,
    onConfirm: () -> Unit
) {
    val baseName = if (request.target.name.contains(".")) request.target.name.substringBeforeLast(".") else request.target.name
    val generatedName = buildArchiveOutputName(baseName, workflow.format)
    val passwordReady = isOptionalPasswordConfirmed(workflow.password, workflow.passwordConfirm)
    AlertDialog(
        onDismissRequest = { workflow.close() },
        title = { Text(stringResource(R.string.browser_compress_archive_title)) },
        text = {
            Column {
                Text(stringResource(R.string.browser_compress_archive_confirm, request.target.name, generatedName), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                FormatDropdown(workflow)
                Spacer(Modifier.height(8.dp))
                AutoNameHint(generatedName)
                FormatNotices(workflow.format)
                OptionalPasswordFields(workflow)
            }
        },
        confirmButton = {
            Button(
                enabled = generatedName.isNotBlank() && (!workflow.format.supportsPassword || passwordReady),
                onClick = { onConfirm() }
            ) { Text(stringResource(R.string.browser_compress_action)) }
        },
        dismissButton = { TextButton(onClick = { workflow.close() }) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** 批量压缩向导：通用格式选择器（自动命名）或固定格式（自定义输出名）。 */
@Composable
private fun PendingCompressWizardDialog(
    request: ArchiveOperationRequest.CompressPending,
    workflow: ArchiveOperationWorkflowState,
    onConfirm: () -> Unit
) {
    val fixed = request.fixedFormat
    if (fixed == null) {
        // 通用：格式下拉 + 自动名（archive 前缀）+ 可选密码
        val generatedName = buildArchiveOutputName("archive", workflow.format)
        val passwordReady = isOptionalPasswordConfirmed(workflow.password, workflow.passwordConfirm)
        AlertDialog(
            onDismissRequest = { workflow.close() },
            title = { Text(stringResource(R.string.browser_compress_pending_archive_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.browser_pending_compress_archive_desc, request.items.size), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    FormatDropdown(workflow)
                    Spacer(Modifier.height(8.dp))
                    AutoNameHint(generatedName)
                    FormatNotices(workflow.format)
                    OptionalPasswordFields(workflow)
                }
            },
            confirmButton = {
                Button(
                    enabled = generatedName.isNotBlank() && (!workflow.format.supportsPassword || passwordReady),
                    onClick = { onConfirm() }
                ) { Text(stringResource(R.string.browser_compress_action)) }
            },
            dismissButton = { TextButton(onClick = { workflow.close() }) { Text(stringResource(R.string.common_cancel)) } }
        )
    } else {
        // 固定格式（ZIP / 7Z）：自定义输出名 + 可选密码
        val isZip = fixed == ArchiveCompressionFormat.ZIP
        val passwordReady = isOptionalPasswordConfirmed(workflow.password, workflow.passwordConfirm)
        AlertDialog(
            onDismissRequest = { workflow.close() },
            title = {
                Text(
                    stringResource(
                        if (isZip) R.string.browser_compress_pending_zip_title else R.string.browser_compress_pending_7z_title
                    )
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(
                            if (isZip) R.string.browser_pending_compress_zip_desc else R.string.browser_pending_compress_7z_desc,
                            request.items.size
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = workflow.pendingName,
                        onValueChange = { workflow.updatePendingName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.browser_filename_label)) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = workflow.password,
                        onValueChange = { workflow.updatePassword(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.browser_password_optional_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (workflow.password.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = workflow.passwordConfirm,
                            onValueChange = { workflow.updatePasswordConfirm(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.browser_password_confirm_label)) },
                            singleLine = true,
                            isError = workflow.passwordConfirm.isNotEmpty() && workflow.password != workflow.passwordConfirm,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        if (workflow.passwordConfirm.isNotEmpty() && workflow.password != workflow.passwordConfirm) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.browser_password_mismatch),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = workflow.pendingName.isNotBlank() && passwordReady,
                    onClick = { onConfirm() }
                ) { Text(stringResource(R.string.browser_compress_action)) }
            },
            dismissButton = { TextButton(onClick = { workflow.close() }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

/** 压缩格式下拉选择（ExposedDropdownMenu）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatDropdown(workflow: ArchiveOperationWorkflowState) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = workflow.format.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.browser_compress_format_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ArchiveCompressionFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format.label) },
                    onClick = {
                        workflow.updateFormat(format)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 自动命名提示。 */
@Composable
private fun AutoNameHint(generatedName: String) {
    Text(
        text = stringResource(R.string.browser_compress_auto_name_hint, generatedName),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 格式相关提示：仅单文件警告 / tar 系不支持密码。 */
@Composable
private fun FormatNotices(format: ArchiveCompressionFormat) {
    if (format.compressesSingleFileOnly) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.browser_compress_single_file_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    } else if (!format.supportsPassword) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.browser_compress_tar_no_password),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 可选密码 + 确认（格式支持密码时显示），含不匹配提示。 */
@Composable
private fun OptionalPasswordFields(workflow: ArchiveOperationWorkflowState) {
    if (!workflow.format.supportsPassword) return
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = workflow.password,
        onValueChange = { workflow.updatePassword(it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.browser_password_optional_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation()
    )
    if (workflow.password.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = workflow.passwordConfirm,
            onValueChange = { workflow.updatePasswordConfirm(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.browser_password_confirm_label)) },
            singleLine = true,
            isError = workflow.passwordConfirm.isNotEmpty() && workflow.password != workflow.passwordConfirm,
            visualTransformation = PasswordVisualTransformation()
        )
    }
}
