package com.kenny.localmanager.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.git.cloneToTree
import com.kenny.localmanager.git.commitAndPush
import com.kenny.localmanager.git.copyFileToShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Git 域分享工作流：文件分享到 Git 仓库（确认 → 同步 → 复制到 share → 提交推送 → 日志）。
 * 配置经 [GitConfigView] 投影读取；UI（确认弹窗与日志窗口）在 [GitShareDialogs]。
 */
internal class GitShareWorkflowState(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val gitConfig: GitConfigView,
) {
    /** 待确认分享目标（null 表示无确认弹窗）。 */
    var target by mutableStateOf<DocumentFileModel?>(null)
        private set
    val logs = mutableStateListOf<String>()
    var inProgress by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    /** 目录域入口：请求分享某文件（弹确认）。 */
    fun request(model: DocumentFileModel) {
        target = model
    }

    fun cancelRequest() {
        target = null
    }

    private fun fail(resId: Int, arg: String? = null) {
        logs.add(if (arg != null) context.getString(resId, arg) else context.getString(resId))
        inProgress = false
        done = true
    }

    /** 确认分享：执行 同步→复制→提交推送 全链（rootUri 为空时提示并中止）。 */
    fun confirm(rootUri: String?) {
        val model = target ?: return
        target = null
        val r = rootUri?.let { normalizeContentUriString(it) }
        if (r == null) {
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
            return
        }
        logs.clear()
        inProgress = true
        done = false
        scope.launch {
            val repoUrl = gitConfig.gitRepoUrl.first() ?: ""
            val userName = gitConfig.gitUserName.first() ?: ""
            val httpsPassword = gitConfig.gitHttpsPassword.first() ?: ""
            if (repoUrl.isBlank()) {
                fail(R.string.browser_share_git_error_configure)
                return@launch
            }
            // 同步
            logs.add(context.getString(R.string.browser_share_git_syncing))
            val syncResult = withContext(Dispatchers.IO) {
                cloneToTree(context, r, repoUrl,
                    userName = userName.ifBlank { null },
                    httpsPassword = httpsPassword.ifBlank { null },
                    log = { msg -> logs.add(msg) })
            }
            if (syncResult.isFailure) {
                fail(R.string.browser_share_git_error_sync, syncResult.exceptionOrNull()?.message ?: "")
                return@launch
            }
            // 复制到 share
            logs.add(context.getString(R.string.browser_share_git_copying))
            val copied = withContext(Dispatchers.IO) {
                copyFileToShare(context, r, model.uri, model.name)
            }
            if (!copied) {
                fail(R.string.browser_share_git_error_copy)
                return@launch
            }
            logs.add(context.getString(R.string.browser_share_git_copied))
            // 提交推送
            logs.add(context.getString(R.string.browser_git_committing_pushing))
            val pushResult = withContext(Dispatchers.IO) {
                commitAndPush(context, r, repoUrl,
                    commitMessage = context.getString(R.string.browser_share_git_commit_message, model.name),
                    userName = userName.ifBlank { null },
                    httpsPassword = httpsPassword.ifBlank { null },
                    log = { msg -> logs.add(msg) })
            }
            if (pushResult.isSuccess) {
                logs.add(context.getString(R.string.browser_share_git_success))
            } else {
                logs.add(context.getString(R.string.browser_share_git_error_push, pushResult.exceptionOrNull()?.message ?: ""))
            }
            inProgress = false
            done = true
        }
    }

    /** 关闭日志窗口（仅完成后允许）。 */
    fun closeLogsIfDone() {
        if (!inProgress) {
            done = false
            logs.clear()
        }
    }
}

@Composable
internal fun rememberGitShareWorkflow(context: Context, gitConfig: GitConfigView): GitShareWorkflowState {
    val scope = rememberCoroutineScope()
    return remember(context, gitConfig) { GitShareWorkflowState(context, scope, gitConfig) }
}

/** Git 分享双弹窗：分享确认 + 执行日志（滚动跟随，错误行高亮）。 */
@Composable
internal fun GitShareDialogs(
    workflow: GitShareWorkflowState,
    rootUri: String?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    workflow.target?.let { model ->
        AlertDialog(
            onDismissRequest = workflow::cancelRequest,
            title = { Text(stringResource(R.string.browser_share_to_git_title)) },
            text = { Text(stringResource(R.string.browser_share_to_git_confirm, model.name)) },
            confirmButton = {
                TextButton(onClick = { workflow.confirm(rootUri) }) { Text(stringResource(R.string.browser_share_action)) }
            },
            dismissButton = {
                TextButton(onClick = workflow::cancelRequest) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    if (workflow.logs.isNotEmpty() && (workflow.inProgress || workflow.done)) {
        AlertDialog(
            onDismissRequest = workflow::closeLogsIfDone,
            title = { Text(stringResource(R.string.browser_share_to_git_title)) },
            text = {
                val logScrollState = rememberScrollState()
                LaunchedEffect(workflow.logs.size) {
                    logScrollState.animateScrollTo(logScrollState.maxValue)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(logScrollState)
                ) {
                    for (line in workflow.logs) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (line.startsWith(context.getString(R.string.browser_error_prefix)) || line.startsWith(context.getString(R.string.browser_debug_prefix)))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                if (workflow.inProgress) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = workflow::closeLogsIfDone) { Text(stringResource(R.string.common_close)) }
                }
            }
        )
    }
}
