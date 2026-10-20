package com.kenny.localmanager.crypto

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import com.kenny.localmanager.ui.DialogPageBackHandler
import com.kenny.localmanager.ui.DialogPageBinder
import com.kenny.localmanager.ui.NavigationState
import com.kenny.localmanager.ui.OperationRunState
import com.kenny.localmanager.ui.TransientRoute
import com.kenny.localmanager.ui.appPage

/**
 * 加密模块（功能模块化第一批样板）：GPG / 密码保护文件功能的模块壳。
 *
 * 为什么引入：此前 CryptoWorkflowState 在组合根创建，目录域经依赖包层层
 * 传递引用才能调用——调用边经过组装点，未来调用方（如音乐播放的加密音乐）
 * 必须再拉一条传递链。模块化后调用边回到使用点：任何域用协议参数
 * （文件模型 + 目录 uri）直呼 CryptoModule.service.open 发起；向导页的
 * 显示与出栈由导航服务执行（request 非空即入栈，调用方不参与）。
 *
 * 生命周期：进程级存活（Activity 重建不丢向导状态）；核心状态类
 * CryptoWorkflowState 保持普通类不变，测试直接构造实例，不经单例。
 */
internal object CryptoModule {
    private var instance: CryptoWorkflowState? = null

    val service: CryptoWorkflowState
        get() = instance ?: CryptoWorkflowState().also { instance = it }

    /**
     * 当前激活的 pass 全屏 Route（查看/编辑二选一），无则 null。
     * "pass 页何时显示"的判断归模块；组合根聚合栈顶时只问不判，
     * 不再认识 request 的类型分支。
     */
    fun activePassRoute(): TransientRoute? {
        val s = service
        return when {
            s.passEdit != null || s.request is CryptoRequest.PassEdit -> TransientRoute.PassEditor
            s.passContent != null || s.request is CryptoRequest.PassView -> TransientRoute.PassViewer
            else -> null
        }
    }

    /** 仅测试用：重置单例。 */
    fun resetForTest() {
        instance = null
    }
}

/**
 * 加密向导页绑定（模块内聚）：向导页工厂 + 显示条件都在模块文件内，
 * 组合根只调本函数一行。request 置位入栈 / 清空出栈；pass 查看/编辑
 * 两入口走全屏 Route（经 activeRoute 聚合），不入向导栈。
 *
 * 宿主服务（进度执行/目录刷新等）由组合根传入——依赖注入发生在这一行，
 * 向导的内部组装（密码框/公钥选择/返回键次序）不向组合根暴露。
 */
@Composable
internal fun CryptoWizardBinding(
    nav: NavigationState,
    scope: CoroutineScope,
    rootUriProvider: () -> String?,
    currentDirUriProvider: () -> String,
    runWithProgress: suspend (String, Int?, suspend ((Int) -> Unit) -> Unit) -> Unit,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onBatchFinished: () -> Unit
) {
    val page = remember {
        val workflow = CryptoModule.service
        appPage {
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
    }
    DialogPageBinder(nav, page) {
        val r = CryptoModule.service.request
        r != null && r !is CryptoRequest.PassView && r !is CryptoRequest.PassEdit
    }
}
