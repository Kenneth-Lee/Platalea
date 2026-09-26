package com.kenny.localmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.ZipEditResult
import com.kenny.localmanager.file.ZipEditStage

// ============================================================================
// ZIP 编辑域 —— 加入工作流状态机
//
// 覆盖"把文件加入 ZIP 编辑目标"向导（对话框形态）。单槽：同一时刻至多一个
// 向导。本域只管请求确认与进度呈现；确认后的文件操作（中转重写、目录刷新、
// 目标配置回写）由宿主编排执行，通过 begin 回调上抛执行权。
// ============================================================================

/** 加入请求：入口功能经 workflow.requestAdd() 发起，携带源列表与目标显示名。 */
internal data class ZipAddRequest(
    val sources: List<DocumentFileModel>,
    val targetName: String
)

/** 密码请求：目标加密且会话内无可用密码时由宿主触发，收集后重试加入。 */
internal data class ZipPasswordRequest(
    val targetName: String,
    val wrongPrevious: Boolean = false
)

@Stable
internal class ZipEditWorkflowState {
    /** 待确认的加入请求；null = 无向导。 */
    var addRequest by mutableStateOf<ZipAddRequest?>(null)
        private set

    /** 宿主执行中；进度对话框呈现。 */
    var inProgress by mutableStateOf(false)
        private set

    /** 进度阶段、(done, total) 项数与 (bytesDone, bytesTotal) 字节数（bytesTotal>0 时 UI 按字节显示）。 */
    var stage by mutableStateOf<ZipEditStage?>(null)
    var progress by mutableStateOf(0 to 0)
        private set
    var bytesProgress by mutableStateOf(0L to 0L)
        private set

    /** 一次执行的结果；完成后呈现结果对话框（成功含改名明细 / 失败含原因）。 */
    var result by mutableStateOf<ZipEditResult?>(null)
        private set

    /** 待收集的密码请求；null = 无密码框。分派优先级最高（执行链暂停等待）。 */
    var passwordRequest by mutableStateOf<ZipPasswordRequest?>(null)
        private set

    /** 发起加入向导：重置状态。 */
    fun requestAdd(sources: List<DocumentFileModel>, targetName: String) {
        addRequest = ZipAddRequest(sources, targetName)
        inProgress = false
        stage = null
        progress = 0 to 0
        result = null
        passwordRequest = null
    }

    /** 取消向导（确认对话框 / 进度中断）。 */
    fun cancelRequest() {
        addRequest = null
    }

    /** 宿主确认后开始执行：进入进行中态，执行权与取消语义在宿主。 */
    fun begin() {
        inProgress = true
    }

    /** 目标需要密码（或上次密码错误）：进入密码收集态，等待重试。 */
    fun requestPassword(targetName: String, wrongPrevious: Boolean = false) {
        inProgress = false
        stage = null
        passwordRequest = ZipPasswordRequest(targetName, wrongPrevious)
    }

    /** 用户取消密码输入：放弃整个加入向导。 */
    fun cancelPassword() {
        passwordRequest = null
        addRequest = null
        result = null
    }

    /** 用户提交密码：清密码态，交宿主带新密码重试（addRequest 保留）。 */
    fun submitPassword() {
        passwordRequest = null
    }

    /** 宿主上报进度（bytesTotal>0 时 UI 优先按字节显示进度）。 */
    fun updateProgress(stage: ZipEditStage, done: Int, total: Int, bytesDone: Long = 0L, bytesTotal: Long = 0L) {
        this.stage = stage
        this.progress = done to total
        this.bytesProgress = bytesDone to bytesTotal
    }

    /** 宿主执行完毕：进入结果态。 */
    fun finish(result: ZipEditResult) {
        inProgress = false
        stage = null
        this.result = result
    }

    /** 关闭结果（成功完成 / 用户知悉失败）；同时结束进行中态，防止进度框残留。 */
    fun dismissResult() {
        inProgress = false
        stage = null
        result = null
        addRequest = null
        passwordRequest = null
    }

    /** 删除链复用：直接进入进行中态（ZipManager 内发起，无确认表单）。 */
    fun beginRemove() {
        inProgress = true
        stage = null
        progress = 0 to 0
        bytesProgress = 0L to 0L
        result = null
        passwordRequest = null
    }
}

@Composable
internal fun rememberZipEditWorkflow(): ZipEditWorkflowState {
    return remember { ZipEditWorkflowState() }
}
