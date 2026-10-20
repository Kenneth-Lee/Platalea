package com.kenny.localmanager.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.file.UnzipResult
import com.kenny.localmanager.file.unzipToParent
import com.kenny.localmanager.file.compressToZip
import com.kenny.localmanager.file.compressTo7z
import com.kenny.localmanager.file.compressTarArchive
import com.kenny.localmanager.file.compressSingleFileToFormat
import com.kenny.localmanager.file.buildArchiveOutputName
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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

    /** CompressFile / CompressPending(选择器)：用户选择的压缩格式。 */
    var format by mutableStateOf(ArchiveCompressionFormat.ZIP)

    /** CompressPending(固定格式)：用户输入的输出名。 */
    var pendingName by mutableStateOf("")

    /** 发起向导：重置全部表单；Unzip 异步检测加密（含 rar v5 拦截）。 */
    fun open(request: ArchiveOperationRequest) {
        this.request = request
        encrypted = null
        password = ""
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

    fun updatePassword(value: String) {
        password = value
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
        format = ArchiveCompressionFormat.ZIP
        pendingName = ""
    }

    /** 解压需要密码 / 密码错误：保留向导让用户重试（加密标志置回 true 以启用输入框）。 */
    fun markUnzipPasswordRequired() {
        encrypted = true
    }
}

/**
 * 压缩 / 解压模块（功能模块化）：组合根 attach 一行接线，调用点（目录域
 * 待处理列表、文件长按菜单）直呼 service.open；向导页工厂与显示条件
 * 内聚本文件（ArchiveOpsWizardBinding）。Activity 重建 attach 重置。
 */
internal object ArchiveOpsModule {
    private var instance: ArchiveOperationWorkflowState? = null

    val service: ArchiveOperationWorkflowState
        get() = instance ?: error("ArchiveOpsModule 未接线：组合根需先 attach")

    fun attach(context: Context, scope: CoroutineScope) {
        instance = ArchiveOperationWorkflowState(context.applicationContext, scope)
    }

    fun resetForTest() {
        instance = null
    }
}

/** 压缩 / 解压向导页绑定（模块内聚）：request 置位入栈 / 清空出栈。 */
@Composable
internal fun ArchiveOpsWizardBinding(
    nav: NavigationState,
    context: Context,
    scope: CoroutineScope,
    browserTab: BrowserTabState,
    displayUriProvider: () -> String,
    progressOpState: MutableState<OperationProgress?>,
    progressCancelActionState: MutableState<(() -> Unit)?>,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit
) {
    val page = remember {
        val workflow = ArchiveOpsModule.service
        appPage {
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
    }
    DialogPageBinder(nav, page) { ArchiveOpsModule.service.request != null }
}

// ============================================================================
// Archive 操作向导域 —— 确认后的文件操作执行编排
//
// 自 AppShell 下沉（Step 3 收尾）：进度 / 取消经宿主传入的两个 MutableState
// 回写（progressOpState / progressCancelActionState），目录刷新与待处理列表
// 清理经共享对象 browserTab 直呼。行为与宿主内联版逐 token 等价。
// ============================================================================

private fun compressArchiveItems(
    context: Context,
    sourceUris: List<Uri>,
    parentDirUri: Uri,
    treeUri: Uri?,
    baseName: String,
    format: ArchiveCompressionFormat,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean = { false },
    onBytesProgress: ((Long, Long) -> Unit)? = null
): Boolean {
    if (sourceUris.isEmpty()) return false
    val outputName = buildArchiveOutputName(baseName, format)
    if (format.compressesSingleFileOnly) {
        return sourceUris.all { uri ->
            val docName = DocumentFile.fromSingleUri(context, uri)?.name ?: "archive"
            compressSingleFileToFormat(
                context = context,
                sourceUri = uri,
                parentDirUri = parentDirUri,
                treeUri = treeUri,
                outputFileName = buildArchiveOutputName(docName, format),
                format = format,
                setProgress = setProgress,
                shouldCancel = shouldCancel,
                onBytesProgress = onBytesProgress
            )
        }
    }
    return when (format) {
        ArchiveCompressionFormat.ZIP -> compressToZip(context, sourceUris, parentDirUri, treeUri, outputName, password, setProgress, shouldCancel, onBytesProgress)
        ArchiveCompressionFormat.SEVEN_Z -> compressTo7z(context, sourceUris, parentDirUri, treeUri, outputName, password, setProgress, shouldCancel, onBytesProgress)
        ArchiveCompressionFormat.TAR -> compressTarArchive(context, sourceUris, parentDirUri, treeUri, outputName, format, password, setProgress, shouldCancel)
        ArchiveCompressionFormat.TAR_GZ -> compressTarArchive(context, sourceUris, parentDirUri, treeUri, outputName, format, password, setProgress, shouldCancel)
        ArchiveCompressionFormat.TAR_BZ2 -> compressTarArchive(context, sourceUris, parentDirUri, treeUri, outputName, format, password, setProgress, shouldCancel)
        ArchiveCompressionFormat.TAR_XZ -> compressTarArchive(context, sourceUris, parentDirUri, treeUri, outputName, format, password, setProgress, shouldCancel)
        ArchiveCompressionFormat.GZ -> false
        ArchiveCompressionFormat.BZ2 -> false
        ArchiveCompressionFormat.XZ -> false
    }
}

internal fun executeArchiveOperation(
    context: Context,
    scope: CoroutineScope,
    workflow: ArchiveOperationWorkflowState,
    browserTab: BrowserTabState,
    displayUri: String?,
    progressOpState: MutableState<OperationProgress?>,
    progressCancelActionState: MutableState<(() -> Unit)?>,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit
) {
    when (val req = workflow.request) {
        is ArchiveOperationRequest.Unzip -> {
            val target = req.target
            val parentDirUri = Uri.parse(displayUri)
            val treeUri = browserTab.rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
            scope.launch {
                val cancelFlag = OperationCancelFlag()
                progressCancelActionState.value = { cancelFlag.requestCancel() }
                progressOpState.value = OperationProgress(context.getString(R.string.browser_op_extract), 0, null)
                delay(50)
                val pwd = if (workflow.encrypted == true) workflow.password.toCharArray() else null
                val result = withContext(Dispatchers.IO) {
                unzipToParent(
                context,
                target.uri,
                parentDirUri,
                treeUri,
                target.name,
                pwd,
                setProgress = { cur, tot ->
                scope.launch(Dispatchers.Main.immediate) { progressOpState.value = OperationProgress(context.getString(R.string.browser_op_extract), cur, tot) }
                },
                shouldCancel = { cancelFlag.isCancelled() }
                )
                }
                // 延迟再关闭进度条，避免 setProgress 的 Main.immediate 晚于本行执行导致进度条不消失
                delay(120)
                progressOpState.value = null
                progressCancelActionState.value = null
                when (result) {
                is UnzipResult.Success -> {
                workflow.close()
                Toast.makeText(context, context.getString(R.string.browser_extract_complete), Toast.LENGTH_SHORT).show()
                browserTab.refresh()
                }
                is UnzipResult.Cancelled -> {
                Toast.makeText(context, context.getString(R.string.browser_operation_cancelled), Toast.LENGTH_SHORT).show()
                }
                is UnzipResult.PasswordRequired -> {
                // 需要密码但未提供，显示密码输入框让用户重试
                workflow.markUnzipPasswordRequired()
                Toast.makeText(context, context.getString(R.string.browser_enter_password), Toast.LENGTH_SHORT).show()
                }
                is UnzipResult.WrongPassword -> {
                // 密码错误，保留对话框让用户重试
                workflow.markUnzipPasswordRequired()
                Toast.makeText(context, context.getString(R.string.browser_wrong_password_retry), Toast.LENGTH_SHORT).show()
                }
                is UnzipResult.CorruptedFile -> {
                workflow.close()
                Toast.makeText(context, context.getString(R.string.browser_extract_failed_corrupt), Toast.LENGTH_SHORT).show()
                }
                is UnzipResult.UnsupportedFormat -> {
                workflow.close()
                Toast.makeText(context, context.getString(R.string.browser_extract_failed_format), Toast.LENGTH_SHORT).show()
                }
                is UnzipResult.IOError -> {
                workflow.close()
                val msg = result.message ?: context.getString(R.string.common_unknown_error)
                Toast.makeText(context, context.getString(R.string.browser_extract_failed_detail, msg), Toast.LENGTH_SHORT).show()
                }
                }
            }
        }
        is ArchiveOperationRequest.CompressFile -> {
            val target = req.target
            val format = workflow.format
            val archiveBaseName = if (target.name.contains(".")) target.name.substringBeforeLast(".") else target.name
            val outputName = buildArchiveOutputName(archiveBaseName, format)
            val pwd = workflow.password.ifBlank { null }?.toCharArray()
            val itemUris = listOf(target.uri)
            val parentDirUri = Uri.parse(displayUri)
            val treeUri = browserTab.rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
            scope.launch {
                val cancelFlag = OperationCancelFlag()
                progressCancelActionState.value = { cancelFlag.requestCancel() }
                progressOpState.value = OperationProgress(context.getString(R.string.browser_op_compress), 0, 1)
                delay(50)
                val ok = withContext(Dispatchers.IO) {
                val onBytes: (Long, Long) -> Unit = { done, total ->
                scope.launch(Dispatchers.Main.immediate) { progressOpState.value = progressOpState.value?.let { it.copy(bytesDone = done, bytesTotal = total) } ?: OperationProgress(context.getString(R.string.browser_op_compress), bytesDone = done, bytesTotal = total) }
                }
                if (format.compressesSingleFileOnly) {
                compressSingleFileToFormat(context, target.uri, parentDirUri, treeUri, outputName, format, { cur, tot ->
                scope.launch(Dispatchers.Main.immediate) { progressOpState.value = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot) }
                }, shouldCancel = { cancelFlag.isCancelled() }, onBytesProgress = onBytes)
                } else {
                compressArchiveItems(context, itemUris, parentDirUri, treeUri, archiveBaseName, format, pwd, { cur, tot ->
                scope.launch(Dispatchers.Main.immediate) { progressOpState.value = OperationProgress(context.getString(R.string.browser_op_compress), cur, tot) }
                }, shouldCancel = { cancelFlag.isCancelled() }, onBytesProgress = onBytes)
                }
                }
                delay(120)
                progressOpState.value = null
                progressCancelActionState.value = null
                workflow.close()
                if (ok) {
                Toast.makeText(context, context.getString(R.string.browser_compress_complete), Toast.LENGTH_SHORT).show()
                browserTab.refresh()
                } else {
                Toast.makeText(
                context,
                if (cancelFlag.isCancelled()) context.getString(R.string.browser_operation_cancelled) else context.getString(R.string.browser_compress_failed),
                Toast.LENGTH_SHORT
                ).show()
                }
            }
        }
        is ArchiveOperationRequest.CompressPending -> {
            val items = req.items
            val currentDirUri = Uri.parse(displayUri)
            val rootTreeUri = browserTab.rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
            when (req.fixedFormat) {
                null -> {
                    val format = workflow.format
                    val pwd = workflow.password.ifBlank { null }?.toCharArray()
                    val generatedPendingArchiveName = buildArchiveOutputName("archive", format)
                    // E2：执行段交全屏执行页（决策表单先关闭）
                    workflow.close()
                    if (format.compressesSingleFileOnly) {
                        // 单文件格式：逐项各自压缩（标准逐项执行）
                        runBatchOperation(
                            context.getString(R.string.browser_op_compress),
                            items.map { buildArchiveOutputName(it.name, format) },
                            { state ->
                                if (state.failCount == 0 && state.okCount == state.total) browserTab.finishPendingList()
                                browserTab.refresh()
                            }
                        ) { state ->
                            state.runEach(items) { item ->
                                withContext(Dispatchers.IO) {
                                    if (compressSingleFileToFormat(context, item.uri, currentDirUri, rootTreeUri, buildArchiveOutputName(item.name, format), format, { _, _ -> }, shouldCancel = { state.cancelRequested })) null else ""
                                }
                            }
                        }
                    } else {
                        // 打包格式：单产物，展示项内进度（文件计数 + 字节）
                        runBatchOperation(
                            context.getString(R.string.browser_op_compress),
                            listOf(generatedPendingArchiveName),
                            { state ->
                                if (state.failCount == 0 && state.okCount == state.total) browserTab.finishPendingList()
                                browserTab.refresh()
                            }
                        ) { state ->
                            state.runEach(listOf(Unit)) { _ ->
                                val progressItem = state.items[0]
                                val ok = withContext(Dispatchers.IO) {
                                    compressArchiveItems(
                                        context, items.map { it.uri }, currentDirUri, rootTreeUri, "archive", format, pwd,
                                        setProgress = { cur, tot -> progressItem.progressText = "$cur / $tot" },
                                        shouldCancel = { state.cancelRequested },
                                        onBytesProgress = { done, total -> progressItem.progressText = formatBytes(done) + " / " + formatBytes(total) }
                                    )
                                }
                                if (ok) null
                                else if (state.cancelRequested) context.getString(R.string.browser_operation_cancelled)
                                else ""
                            }
                        }
                    }
                }
                ArchiveCompressionFormat.ZIP -> {
                    val zipName = workflow.pendingName.trim().let {
                        if (it.endsWith(".zip", ignoreCase = true)) it else "$it.zip"
                    }
                    val pwd = workflow.password.ifBlank { null }?.toCharArray()
                    // E2：执行段交全屏执行页（决策表单先关闭）
                    workflow.close()
                    runBatchOperation(
                        context.getString(R.string.browser_op_compress),
                        listOf(zipName),
                        { state ->
                            if (state.failCount == 0 && state.okCount == state.total) browserTab.finishPendingList()
                            browserTab.refresh()
                        }
                    ) { state ->
                        state.runEach(listOf(Unit)) { _ ->
                            val progressItem = state.items[0]
                            val ok = withContext(Dispatchers.IO) {
                                compressToZip(
                                    context,
                                    items.map { it.uri },
                                    currentDirUri,
                                    rootTreeUri,
                                    zipName,
                                    pwd,
                                    setProgress = { cur, tot -> progressItem.progressText = "$cur / $tot" },
                                    shouldCancel = { state.cancelRequested }
                                )
                            }
                            if (ok) null
                            else if (state.cancelRequested) context.getString(R.string.browser_operation_cancelled)
                            else ""
                        }
                    }
                }
                ArchiveCompressionFormat.SEVEN_Z -> {
                    val sevenZName = workflow.pendingName.trim().let {
                        if (it.endsWith(".7z", ignoreCase = true)) it else "$it.7z"
                    }
                    val pwd = workflow.password.ifBlank { null }?.toCharArray()
                    // E2：执行段交全屏执行页（决策表单先关闭；保留 7z 两分钟超时保护）
                    workflow.close()
                    runBatchOperation(
                        context.getString(R.string.browser_op_compress),
                        listOf(sevenZName),
                        { state ->
                            if (state.failCount == 0 && state.okCount == state.total) browserTab.finishPendingList()
                            browserTab.refresh()
                        }
                    ) { state ->
                        state.runEach(listOf(Unit)) { _ ->
                        val progressItem = state.items[0]
                        var timeout = false
                        val ok = withContext(Dispatchers.IO) {
                            val executor = Executors.newSingleThreadExecutor()
                            try {
                                val future = executor.submit<Boolean> {
                                    compressTo7z(
                                        context,
                                        items.map { it.uri },
                                        currentDirUri,
                                        rootTreeUri,
                                        sevenZName,
                                        pwd,
                                        setProgress = { cur, tot -> progressItem.progressText = "$cur / $tot" },
                                        shouldCancel = { state.cancelRequested }
                                    )
                                }
                                future.get(2, TimeUnit.MINUTES)
                            } catch (_: TimeoutException) {
                                timeout = true
                                false
                            } finally {
                                executor.shutdownNow()
                            }
                        }
                        when {
                            ok -> null
                            state.cancelRequested -> context.getString(R.string.browser_operation_cancelled)
                            timeout -> context.getString(R.string.browser_compress_timeout)
                            else -> ""
                        }
                        }
                    }
                }
                else -> { }
            }
        }
        null -> { }
    }
}
