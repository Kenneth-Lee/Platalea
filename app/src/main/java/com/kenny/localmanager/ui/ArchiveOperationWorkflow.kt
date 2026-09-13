package com.kenny.localmanager.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kenny.localmanager.R
import com.kenny.localmanager.file.ArchiveCompressionFormat
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isArchiveEncrypted
import com.kenny.localmanager.file.isRarV5Archive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================================
// Archive 操作向导域 —— 数据类型与状态机
//
// 覆盖"打包 / 解包"类操作向导（对话框形态，与阅读器域的全屏查看器相对）：
//   Unzip           解压压缩包到当前目录（点击可解压文件触发）
//   CompressFile    单文件 / 目录压缩为归档（长按菜单，格式可选）
//   CompressPending 批量压缩待处理列表（通用格式选择器 / 固定 ZIP / 固定 7Z）
//
// 单槽：同一时刻至多一个向导。本域只管表单状态与加密检测；确认后的文件
// 操作（进度、取消、目录刷新）由宿主编排执行，通过 onConfirm 回调上抛。
// ============================================================================

/** 向导请求：入口功能经 workflow.open() 发起，携带操作目标。 */
internal sealed interface ArchiveOperationRequest {
    /** 解压压缩包。加密检测结果由 workflow 持有（encrypted）。 */
    data class Unzip(val target: DocumentFileModel) : ArchiveOperationRequest

    /** 单文件 / 目录压缩为归档，格式由用户在向导中选择。 */
    data class CompressFile(val target: DocumentFileModel) : ArchiveOperationRequest

    /**
     * 批量压缩待处理列表。open 时对列表快照。
     * @param fixedFormat null = 通用格式选择器（自动命名）；ZIP / SEVEN_Z = 固定格式 + 自定义输出名
     */
    data class CompressPending(
        val items: List<DocumentFileModel>,
        val fixedFormat: ArchiveCompressionFormat?,
        val defaultName: String
    ) : ArchiveOperationRequest
}

/**
 * Archive 操作向导工作流状态：单槽（同一时刻至多一个向导）。
 * 表单字段（密码 / 确认 / 格式 / 输出名）按请求种类取用，确认后由宿主读取执行。
 */
@Stable
internal class ArchiveOperationWorkflowState(
    private val context: Context,
    private val scope: CoroutineScope
) {
    var request by mutableStateOf<ArchiveOperationRequest?>(null)
        private set

    /** 仅 Unzip：null = 检测中，true/false = 结果。密码错误重试时置回 true。 */
    var encrypted by mutableStateOf<Boolean?>(null)
        private set

    /** Unzip：必填密码；Compress：可选密码（supportsPassword 时）。 */
    var password by mutableStateOf("")

    /** Compress：可选密码确认。 */
    var passwordConfirm by mutableStateOf("")

    /** CompressFile / CompressPending(选择器)：用户选择的压缩格式。 */
    var format by mutableStateOf(ArchiveCompressionFormat.ZIP)

    /** CompressPending(固定格式)：用户输入的输出名。 */
    var pendingName by mutableStateOf("")

    /** 发起向导：重置全部表单；Unzip 异步检测加密（含 rar v5 拦截）。 */
    fun open(request: ArchiveOperationRequest) {
        this.request = request
        encrypted = null
        password = ""
        passwordConfirm = ""
        format = ArchiveCompressionFormat.ZIP
        pendingName = (request as? ArchiveOperationRequest.CompressPending)?.defaultName ?: ""
        if (request is ArchiveOperationRequest.Unzip) {
            val target = request.target
            scope.launch {
                if (target.name.endsWith(".rar", ignoreCase = true)) {
                    val rarV5 = withContext(Dispatchers.IO) { isRarV5Archive(context, target.uri) }
                    if (rarV5) {
                        Toast.makeText(context, context.getString(R.string.browser_rar5_unsupported), Toast.LENGTH_LONG).show()
                        close()
                        return@launch
                    }
                }
                encrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
            }
        }
    }

    /** 密码输入：清空时联动清空确认（与原向导一致）。 */
    fun updatePassword(value: String) {
        password = value
        if (value.isEmpty()) passwordConfirm = ""
    }

    fun updatePasswordConfirm(value: String) {
        passwordConfirm = value
    }

    fun updateFormat(value: ArchiveCompressionFormat) {
        format = value
    }

    fun updatePendingName(value: String) {
        pendingName = value
    }

    /** 关闭向导并重置全部表单（取消 / 成功完成 / 不可恢复失败）。 */
    fun close() {
        request = null
        encrypted = null
        password = ""
        passwordConfirm = ""
        format = ArchiveCompressionFormat.ZIP
        pendingName = ""
    }

    /** 解压需要密码 / 密码错误：保留向导让用户重试（加密标志置回 true 以启用输入框）。 */
    fun markUnzipPasswordRequired() {
        encrypted = true
    }
}

@Composable
internal fun rememberArchiveOperationWorkflow(context: Context, scope: CoroutineScope): ArchiveOperationWorkflowState {
    return remember(context, scope) { ArchiveOperationWorkflowState(context.applicationContext, scope) }
}
