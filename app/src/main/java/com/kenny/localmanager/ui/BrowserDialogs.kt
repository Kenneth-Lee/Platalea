package com.kenny.localmanager.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import com.kenny.localmanager.file.copyDocumentTo
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.getTrashUriIfExists
import com.kenny.localmanager.file.humanReadableRootUri
import com.kenny.localmanager.file.listFilesSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Browser 域弹窗集：根目录切换（最近根列表 + 更换根确认）与外源保存覆盖确认。
 *
 * 状态与 IO 编排在 [BrowserRootSwitchWorkflow] / [BrowserSaveWorkflow]；
 * SAF 选择器（treeLauncher）归宿主，经注入回调协作。Route 开关（弹窗显隐）为工作流内部状态。
 */

/**
 * 根目录切换工作流：最近根选择、回收站检查（有内容先确认清空）、新根应用。
 * 宿主经 [open] 发起；[selectNewRoot] 的回收站检查后经 onLaunchPicker 决定直开 SAF 还是先确认。
 */
@Composable
internal fun rememberRootSwitchWorkflow(
    context: Context,
    browserTab: BrowserTabState,
): BrowserRootSwitchWorkflow {
    val scope = rememberCoroutineScope()
    return remember(context, browserTab) { BrowserRootSwitchWorkflow(context, scope, browserTab) }
}

internal class BrowserRootSwitchWorkflow(
    private val context: Context,
    private val scope: CoroutineScope,
    private val browserTab: BrowserTabState,
) {
    var showRootSwitchDialog by mutableStateOf(false)
        private set
    var showChangeRootConfirm by mutableStateOf(false)
        private set

    /** 当前根的回收站 uri（无则 null），随根变化在宿主 LaunchedEffect 调 [refreshTrashUri] 维护。 */
    var cachedTrashUri by mutableStateOf<Uri?>(null)

    fun open() {
        showRootSwitchDialog = true
    }

    fun dismissRootSwitch() {
        showRootSwitchDialog = false
    }

    fun dismissChangeRootConfirm() {
        showChangeRootConfirm = false
    }

    fun refreshTrashUri(rootUri: String?) {
        scope.launch {
            cachedTrashUri = rootUri?.let { r ->
                withContext(Dispatchers.IO) {
                    val root = Uri.parse(normalizeContentUriString(r))
                    getTrashUriIfExists(context, root, root)
                }
            }
        }
    }

    /** 最近根列表中选中某项：同根忽略，异根记录并应用。 */
    fun pickRecentRoot(recentRootUri: String, initialDirUriSink: (String) -> Unit) {
        val normalized = normalizeContentUriString(recentRootUri)
        val previousRoot = browserTab.rootUri?.let { normalizeContentUriString(it) }
        showRootSwitchDialog = false
        if (previousRoot == normalized) return
        applyNewRoot(previousRoot, normalized, initialDirUriSink)
    }

    /** 点"选择新目录"：回收站有内容先弹确认，否则直接开 SAF。 */
    fun selectNewRoot(onLaunchPicker: () -> Unit) {
        showRootSwitchDialog = false
        val r = browserTab.rootUri ?: run {
            onLaunchPicker()
            return
        }
        if (r.isEmpty()) {
            onLaunchPicker()
            return
        }
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                cachedTrashUri?.let { trash ->
                    val doc = if (trash.toString().contains("/tree/")) {
                        DocumentFile.fromTreeUri(context, trash)
                    } else {
                        DocumentFile.fromSingleUri(context, trash)
                    }
                    doc?.listFilesSafe()?.size ?: 0
                } ?: 0
            }
            withContext(Dispatchers.Main.immediate) {
                if (count > 0) showChangeRootConfirm = true
                else onLaunchPicker()
            }
        }
    }

    /** 记录根切换并应用（treeLauncher 回调后宿主调用）。 */
    fun applyNewRoot(previousRoot: String?, normalized: String, initialDirUriSink: (String) -> Unit) {
        scope.launch {
            browserTab.config.recordRecentRootSwitch(previousRoot, normalized)
            browserTab.config.setRootUri(normalized)
        }
        browserTab.backStack.clear()
        browserTab.changeRoot(normalized, refresh = false)
        initialDirUriSink(normalized)
    }
}

/** 根目录切换弹窗：最近使用根列表 + 选择新目录入口。 */
@Composable
internal fun RootSwitchDialog(
    recentRootUris: List<String>,
    workflow: BrowserRootSwitchWorkflow,
    onPickRoot: (rootUri: String) -> Unit,
    onLaunchPicker: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = workflow::dismissRootSwitch,
        title = { Text(stringResource(R.string.root_switch_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.root_switch_hint))
                if (recentRootUris.isEmpty()) {
                    Text(stringResource(R.string.root_switch_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    recentRootUris.forEach { recentUri ->
                        val displayName = humanReadableRootUri(recentUri)
                        OutlinedButton(
                            onClick = { onPickRoot(recentUri) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                displayName,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { workflow.selectNewRoot(onLaunchPicker) }) { Text(stringResource(R.string.browser_select_new_root)) }
        },
        dismissButton = {
            TextButton(onClick = workflow::dismissRootSwitch) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** 更换根确认弹窗：回收站有内容时选择清空或直接换。纯 UI，执行逻辑由宿主注入。 */
@Composable
internal fun ChangeRootConfirmDialog(
    onEmptyAndChange: () -> Unit,
    onChangeDirectly: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.browser_change_root_title)) },
        text = { Text(stringResource(R.string.browser_change_root_trash_prompt)) },
        confirmButton = {
            Button(onClick = onEmptyAndChange) { Text(stringResource(R.string.browser_empty_then_change)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onChangeDirectly) { Text(stringResource(R.string.browser_change_directly)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    )
}

/**
 * 外源保存工作流：分享/另存进来的文件等目标根就绪后直接保存，重名时弹覆盖确认。
 * 宿主经 [offer] 提供来源；[consumeIfReady] 在 rootUri 变化的 LaunchedEffect 中调用。
 */
@Composable
internal fun rememberBrowserSaveWorkflow(
    context: Context,
    browserTab: BrowserTabState,
): BrowserSaveWorkflow {
    val scope = rememberCoroutineScope()
    return remember(context, browserTab) { BrowserSaveWorkflow(context, scope, browserTab) }
}

internal class BrowserSaveWorkflow(
    private val context: Context,
    private val scope: CoroutineScope,
    private val browserTab: BrowserTabState,
) {
    var pendingSaveFileUri by mutableStateOf<String?>(null)
        private set
    /** 覆盖确认目标：sourceUri to fileName；null 表示无待确认。 */
    var overwriteConfirm by mutableStateOf<Pair<String, String>?>(null)
        private set
    var inProgress by mutableStateOf(false)
        private set

    /** 宿主外源入口（分享接收）。 */
    fun offer(uriString: String) {
        pendingSaveFileUri = uriString
    }

    fun clearPending() {
        pendingSaveFileUri = null
    }

    private fun toast(resId: Int) {
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
    }

    /**
     * 目标根就绪后尝试消费 pending：可直接保存则执行复制，重名转 [overwriteConfirm]。
     * [onSaved] 保存成功回调（参数为已保存目录，宿主负责导航与刷新信号）。
     */
    fun consumeIfReady(onSaved: (dir: String) -> Unit) {
        val sourceUriStr = pendingSaveFileUri ?: return
        val targetRoot = browserTab.rootUri?.let { normalizeContentUriString(it) } ?: return
        if (overwriteConfirm != null || inProgress) return
        val sourceUri = Uri.parse(sourceUriStr)
        val sourceDoc = DocumentFile.fromSingleUri(context, sourceUri) ?: run {
            pendingSaveFileUri = null
            toast(R.string.browser_cannot_read_file)
            return
        }
        val fileName = sourceDoc.name ?: run {
            pendingSaveFileUri = null
            toast(R.string.browser_cannot_get_filename)
            return
        }
        val targetUri = Uri.parse(targetRoot)
        val existingUri = findChildByName(context, targetUri, fileName)
        if (existingUri != null) {
            overwriteConfirm = sourceUriStr to fileName
        } else {
            performCopy(sourceUriStr, targetRoot, onSaved)
        }
    }

    /** 用户确认覆盖：删除既有文件后复制。 */
    fun confirmOverwrite(onSaved: (dir: String) -> Unit) {
        val (sourceUriStr, fileName) = overwriteConfirm ?: return
        val targetRoot = browserTab.rootUri?.let { normalizeContentUriString(it) } ?: return
        scope.launch {
            inProgress = true
            val targetUri = Uri.parse(targetRoot)
            val existingUri = findChildByName(context, targetUri, fileName)
            if (existingUri != null) context.contentResolver.deleteDocument(existingUri)
            val copied = withContext(Dispatchers.IO) {
                copyDocumentTo(context, Uri.parse(sourceUriStr), targetUri, targetUri)
            }
            inProgress = false
            overwriteConfirm = null
            pendingSaveFileUri = null
            if (copied != null) {
                val dir = normalizeContentUriString(targetRoot)
                onSaved(dir)
                toast(R.string.browser_overwrite_save)
            } else {
                toast(R.string.viewer_save_failed)
            }
        }
    }

    /** 用户放弃覆盖。 */
    fun skipOverwrite(onSkipped: (dir: String) -> Unit) {
        val targetRoot = browserTab.rootUri?.let { normalizeContentUriString(it) } ?: return
        overwriteConfirm = null
        pendingSaveFileUri = null
        onSkipped(normalizeContentUriString(targetRoot))
    }

    private fun performCopy(sourceUriStr: String, targetRoot: String, onSaved: (dir: String) -> Unit) {
        scope.launch {
            inProgress = true
            val targetUri = Uri.parse(targetRoot)
            val copied = withContext(Dispatchers.IO) {
                copyDocumentTo(context, Uri.parse(sourceUriStr), targetUri, targetUri)
            }
            inProgress = false
            pendingSaveFileUri = null
            if (copied != null) {
                val dir = normalizeContentUriString(targetRoot)
                onSaved(dir)
                toast(R.string.browser_saved_to_current_dir)
            } else {
                toast(R.string.viewer_save_failed)
            }
        }
    }
}

/** 外源保存覆盖确认弹窗。纯 UI，执行逻辑由宿主注入。 */
@Composable
internal fun OverwriteSaveConfirmDialog(
    fileName: String,
    onOverwrite: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(R.string.browser_file_exists_title)) },
        text = { Text(stringResource(R.string.browser_file_exists_overwrite, fileName)) },
        confirmButton = {
            Button(onClick = onOverwrite) { Text(stringResource(R.string.file_share_overwrite_action)) }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.browser_do_not_overwrite)) }
        }
    )
}