package com.kenny.localmanager.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isQuickObfuscatedFileName
import com.kenny.localmanager.file.quickToggleObfuscation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Crypto 域快速遮罩（单文件/批量）工作流：目标、密码表单、执行与收尾。
 * UI（QuickObfuscatePasswordDialog 复用）在 [QuickObfuscateDialogs]；
 * 完成回调（刷新目录/待处理列表收尾）由宿主注入。
 */
internal class QuickObfuscateWorkflowState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    // 单文件槽
    var quickTarget by mutableStateOf<DocumentFileModel?>(null)
    var quickPassword by mutableStateOf("")
    var quickInProgress by mutableStateOf(false)

    // 批量槽（待处理列表遮罩）
    var batchTarget by mutableStateOf<List<DocumentFileModel>?>(null)
    var batchPassword by mutableStateOf("")
    var batchInProgress by mutableStateOf(false)

    fun requestQuick(model: DocumentFileModel) {
        quickTarget = model
        quickPassword = ""
    }

    fun requestBatch(list: List<DocumentFileModel>) {
        batchTarget = list
        batchPassword = ""
    }

    fun closeQuick() {
        quickTarget = null
        quickPassword = ""
    }

    fun closeBatch() {
        batchTarget = null
        batchPassword = ""
    }

    fun resetIfQuickActive() {
        if (quickTarget != null) closeQuick()
    }

    fun resetIfBatchActive() {
        if (batchTarget != null) closeBatch()
    }

    /** 执行单文件遮罩切换；[onFinished] 宿主收尾（刷新目录）。 */
    fun confirmQuick(pwd: String, onFinished: (ok: Boolean, name: String) -> Unit) {
        val model = quickTarget ?: return
        quickInProgress = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                quickToggleObfuscation(context, model.uri, pwd.toCharArray())
            }
            quickInProgress = false
            closeQuick()
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.browser_mask_done else R.string.browser_mask_failed, model.name),
                Toast.LENGTH_SHORT
            ).show()
            onFinished(ok, model.name)
        }
    }

    /** 执行批量遮罩（E2：交全屏执行页，[runBatchOperation] 宿主编排注入，[onFinished] 待处理收尾）。 */
    fun confirmBatch(
        pwd: String,
        runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
        onFinished: () -> Unit,
    ) {
        val list = batchTarget ?: return
        closeBatch()
        runBatchOperation(
            context.getString(R.string.pending_list_action_mask),
            list.map { it.name },
            { _ -> onFinished() }
        ) { state ->
            state.runEach(list) { model ->
                withContext(Dispatchers.IO) {
                    quickToggleObfuscation(context, model.uri, pwd.toCharArray())
                    null
                }
            }
        }
    }
}

@Composable
internal fun rememberQuickObfuscateWorkflow(context: Context): QuickObfuscateWorkflowState {
    val scope = rememberCoroutineScope()
    return remember(context) { QuickObfuscateWorkflowState(context, scope) }
}

/** 快速遮罩双槽弹窗（单文件/批量），完成后回调宿主收尾。 */
@Composable
internal fun QuickObfuscateDialogs(
    workflow: QuickObfuscateWorkflowState,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    onQuickFinished: () -> Unit,
    onBatchFinished: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    workflow.quickTarget?.let { model ->
        QuickObfuscatePasswordDialog(
            isMasked = isQuickObfuscatedFileName(model.name),
            fileName = model.name,
            password = workflow.quickPassword,
            inProgress = workflow.quickInProgress,
            onPasswordChange = { workflow.quickPassword = it },
            onConfirm = { pwd -> workflow.confirmQuick(pwd) { _, _ -> onQuickFinished() } },
            onDismiss = workflow::closeQuick
        )
    }
    workflow.batchTarget?.let { list ->
        QuickObfuscatePasswordDialog(
            isMasked = list.all { isQuickObfuscatedFileName(it.name) },
            fileName = context.getString(R.string.browser_file_count, list.size),
            password = workflow.batchPassword,
            inProgress = workflow.batchInProgress,
            onPasswordChange = { workflow.batchPassword = it },
            onConfirm = { pwd -> workflow.confirmBatch(pwd, runBatchOperation) { onBatchFinished() } },
            onDismiss = workflow::closeBatch
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickObfuscatePasswordDialog(
    isMasked: Boolean,
    fileName: String,
    password: String,
    inProgress: Boolean = false,
    onPasswordChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val passwordLabelText = stringResource(R.string.browser_enter_password)
    val passwordConfirmed = password.isNotBlank()
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    context.getString(R.string.context_quick_mask),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isMasked) {
                        context.getString(R.string.browser_mask_remove_hint)
                    } else {
                        context.getString(R.string.browser_mask_add_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                PasswordConfirmationFields(
                    password = password,
                    onPasswordChange = onPasswordChange,
                    passwordLabel = passwordLabelText,
                    enabled = !inProgress
                )
                if (inProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_masking), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !inProgress) { Text(stringResource(R.string.common_cancel)) }
                    Button(onClick = { onConfirm(password) }, enabled = !inProgress && passwordConfirmed) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}
