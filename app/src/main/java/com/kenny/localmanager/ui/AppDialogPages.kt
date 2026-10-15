package com.kenny.localmanager.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kenny.localmanager.R
import com.kenny.localmanager.family.FamilyNetworkManager
import com.kenny.localmanager.file.emptyTrash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================================
// 跨域向导功能页组装（P2 引入，P5 归位独立文件）：Dialog UI 组件留在域文件，
// 此处只做组合接线与返回键。页面生存期由 DialogPageBinder 绑定域状态
// （置位入栈 / 清空出栈）；组合根（PlataleaApp）持引用接线后交给 Binder。
// ============================================================================

/**
 * 向导页返回键：对齐原主界面 BackHandler 的分支次序——进度取消优先于
 * 向导关闭（runWithProgress 运行中 back = 取消进度而非关向导）。
 */
@Composable
private fun DialogPageBackHandler(
    progressOp: OperationProgress?,
    progressCancelAction: (() -> Unit)?,
    onDialogBack: () -> Unit
) {
    // 低优先：向导自身的关闭（先注册）
    BackHandler { onDialogBack() }
    // 高优先：进度取消（后注册，enabled 时抢先）
    BackHandler(enabled = progressOp != null) { progressCancelAction?.invoke() }
}

/**
 * 快速遮罩向导页（单文件 / 批量）。执行走自有 quickInProgress 或执行页，
 * 不经宿主 runWithProgress，页内 back 无进度取消分支。
 */
internal fun quickObfuscateDialogPage(
    workflow: QuickObfuscateWorkflowState,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    onQuickFinished: () -> Unit,
    onBatchFinished: () -> Unit
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        DialogPageBackHandler(progressOp = null, progressCancelAction = null) {
            if (workflow.batchTarget != null) workflow.resetIfBatchActive()
            else workflow.resetIfQuickActive()
        }
        QuickObfuscateDialogs(
            workflow = workflow,
            runBatchOperation = runBatchOperation,
            onQuickFinished = onQuickFinished,
            onBatchFinished = onBatchFinished
        )
    }
    return page
}

/** GPG 加解密 / .pass 保护向导页（PassView / PassEdit 走全屏查看器，不经此页）。 */
internal fun cryptoDialogPage(
    workflow: CryptoWorkflowState,
    scope: CoroutineScope,
    rootUriProvider: () -> String?,
    currentDirUriProvider: () -> String,
    runWithProgress: suspend (String, Int?, suspend ((Int) -> Unit) -> Unit) -> Unit,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onBatchFinished: () -> Unit
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        DialogPageBackHandler(progressOp = null, progressCancelAction = null) {
            if (!workflow.inProgress) workflow.close()
        }
        CryptoDialogs(
            workflow = workflow,
            scope = scope,
            rootUri = rootUriProvider(),
            currentDirUri = currentDirUriProvider(),
            runWithProgress = runWithProgress,
            runBatchOperation = runBatchOperation,
            onRefresh = onRefresh,
            onBatchFinished = onBatchFinished
        )
    }
    return page
}

/** 压缩 / 解压向导页（确认后由 executeArchiveOperation 执行文件操作）。 */
internal fun archiveOpsDialogPage(
    context: Context,
    scope: CoroutineScope,
    workflow: ArchiveOperationWorkflowState,
    browserTab: BrowserTabState,
    displayUriProvider: () -> String,
    progressOpState: MutableState<OperationProgress?>,
    progressCancelActionState: MutableState<(() -> Unit)?>,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        DialogPageBackHandler(progressOpState.value, progressCancelActionState.value) {
            workflow.close()
        }
        ArchiveOperationDialogs(
            workflow = workflow,
            onConfirm = {
                executeArchiveOperation(
                    context = context,
                    scope = scope,
                    workflow = workflow,
                    browserTab = browserTab,
                    displayUri = displayUriProvider(),
                    progressOpState = progressOpState,
                    progressCancelActionState = progressCancelActionState,
                    runBatchOperation = runBatchOperation
                )
            }
        )
    }
    return page
}

/** 换根页：根选择对话框与清空回收站确认同域同页（先后最多一个显示）。 */
internal fun rootSwitchDialogPage(
    workflow: BrowserRootSwitchWorkflow,
    browserTab: BrowserTabState,
    recentRootUrisProvider: () -> List<String>,
    initialDirUriSink: (String) -> Unit,
    treeLauncher: ManagedActivityResultLauncher<Uri?, Uri?>,
    scope: CoroutineScope,
    runWithProgress: suspend (String, Int?, suspend ((Int) -> Unit) -> Unit) -> Unit,
    context: Context
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        DialogPageBackHandler(progressOp = null, progressCancelAction = null) {
            when {
                workflow.showChangeRootConfirm -> workflow.dismissChangeRootConfirm()
                else -> workflow.dismissRootSwitch()
            }
        }
        if (workflow.showRootSwitchDialog) {
            RootSwitchDialog(
                recentRootUris = recentRootUrisProvider(),
                workflow = workflow,
                onPickRoot = { rootUri -> workflow.pickRecentRoot(rootUri, initialDirUriSink) },
                onLaunchPicker = { treeLauncher.launch(null) }
            )
        }
        if (workflow.showChangeRootConfirm && browserTab.rootUri != null) {
            val r = browserTab.rootUri!!
            val root = Uri.parse(normalizeContentUriString(r))
            ChangeRootConfirmDialog(
                onEmptyAndChange = {
                    scope.launch {
                        workflow.dismissChangeRootConfirm()
                        runWithProgress(context.getString(R.string.main_menu_empty_trash), null) { _ ->
                            val ok = withContext(Dispatchers.IO) { emptyTrash(context, root, root) }
                            if (ok) Toast.makeText(context, context.getString(R.string.browser_trash_emptied), Toast.LENGTH_SHORT).show()
                            treeLauncher.launch(null)
                            browserTab.refresh()
                        }
                    }
                },
                onChangeDirectly = {
                    workflow.dismissChangeRootConfirm()
                    treeLauncher.launch(null)
                },
                onCancel = workflow::dismissChangeRootConfirm
            )
        }
    }
    return page
}

/**
 * 公告转发页：转发目标选择流程（完成 / 取消退回）。
 * P4：请求槽在 FamilyNetworkManager（域状态），页面投影 forwardRequest；
 * familyNetworkForeground 需组合期读取，经 provider 注入。
 */
internal fun bulletinForwardDialogPage(
    manager: FamilyNetworkManager,
    familyNetworkForegroundProvider: () -> Boolean,
    localServiceEnabledProvider: () -> Boolean,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        // 对齐原主界面 BackHandler 行为：转发流程无对话框级 back 分支，
        // 落到退出协商
        BackHandler { onBack() }
        val payload by manager.forwardRequest.collectAsState()
        payload?.let {
            BulletinForwardTargetFlow(
                manager = manager,
                payload = it,
                localServiceEnabled = localServiceEnabledProvider(),
                keepEphemeralSession = !familyNetworkForegroundProvider(),
                onDismiss = onDismiss,
                onComplete = onComplete
            )
        }
    }
    return page
}

/** 外源保存覆盖确认页：目标目录就绪且重名时由 BrowserSaveWorkflow 置位。 */
internal fun overwriteConfirmDialogPage(
    browserSave: BrowserSaveWorkflow,
    onOverwriteSaved: (String) -> Unit,
    onSkipped: (String) -> Unit,
    onBack: () -> Unit
): AppPage {
    lateinit var page: AppPage
    page = appPage {
        // 对齐原主界面 BackHandler 行为：覆盖确认无对话框级 back 分支，
        // 落到退出协商
        BackHandler { onBack() }
        browserSave.overwriteConfirm?.let { (_, fileName) ->
            OverwriteSaveConfirmDialog(
                fileName = fileName,
                onOverwrite = { browserSave.confirmOverwrite(onOverwriteSaved) },
                onSkip = { browserSave.skipOverwrite(onSkipped) }
            )
        }
    }
    return page
}
