package com.kenny.localmanager.epub

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kenny.localmanager.R
import com.kenny.localmanager.browser.BrowserActions
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.isArchiveCacheStampValid
import com.kenny.localmanager.file.EpubExtractResult
import com.kenny.localmanager.file.EpubParseResult
import com.kenny.localmanager.file.extractEpubToCache
import com.kenny.localmanager.file.extractLlmZipToCache
import com.kenny.localmanager.file.getEpubCacheDir
import com.kenny.localmanager.file.getEpubCacheTimestamp
import com.kenny.localmanager.file.isArchiveEncrypted
import com.kenny.localmanager.file.isEpubCacheEncrypted
import com.kenny.localmanager.file.loadEpubFromCache
import com.kenny.localmanager.file.prepareLlmAsEpub
import com.kenny.localmanager.file.prepareTxtAsEpub
import com.kenny.localmanager.file.tryArchivePassword
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kenny.localmanager.archivereader.ArchiveReaderWorkflowState
import com.kenny.localmanager.archivereader.ArchiveViewerState
import com.kenny.localmanager.archivereader.EncryptedCacheExitDialogState
import com.kenny.localmanager.archivereader.EpubViewState
import com.kenny.localmanager.archivereader.ReaderRequestWorkflow
import com.kenny.localmanager.archivereader.ViewerHostWorkflow
import com.kenny.localmanager.viewer.ArchiveViewerRouteHost
import com.kenny.localmanager.viewer.ReadingLauncher
// ============================================================================
// EPUB 阅读器域 —— 状态机（批次 C：自 Archive 阅读器域分离）
//
// epub 渲染器 = 一个模块多种来源：.epub / .llm.zip（解压）与 .txt / .llm（转换），
// 共用 epub_cache，统一产出 EpubViewState 进入 EPUB 渲染器。
// 单槽工作流与 ArchiveReaderWorkflowState 同构：请求阶段（缓存检查 / 密码 /
// 转换进度）→ 查看器阶段。
// ============================================================================

/** EPUB 渲染器来源种类。 */
internal enum class EpubReaderKind {
    TXT,      // .txt（转换为 EPUB 渲染）
    LLM,      // .llm 对话导出文件（转换为 EPUB 渲染）
    EPUB,     // .epub
    LLM_ZIP   // .llm.zip（解压后走 EPUB 渲染器）
}

/** 待处理阅读请求：检查加密 → 密码 → 解压/转换阶段的输入参数。 */
internal data class EpubReaderRequest(
    val kind: EpubReaderKind,
    val target: DocumentFileModel
)

/** 返回键可直接取消的请求种类（不含 TXT / LLM：转换在后台，无中间对话框）。 */
internal val EpubReaderCancelableKinds: Set<EpubReaderKind> = setOf(
    EpubReaderKind.EPUB,
    EpubReaderKind.LLM_ZIP
)

/**
 * EPUB 阅读器工作流状态：单槽（同一时刻至多一个请求在处理）。
 *
 * 阶段一 request != null：检查加密 / 密码输入 / 解压或转换中，渲染 [EpubReaderDialogs]。
 * 阶段二 viewer != null：产出 EpubViewState 进入全屏渲染器（复用 ArchiveViewerRouteHost）。
 */
@Stable
internal class EpubReaderWorkflowState(
    private val context: Context,
    private val scope: CoroutineScope
) : ReaderRequestWorkflow, ViewerHostWorkflow {
    var request by mutableStateOf<EpubReaderRequest?>(null)
        private set
    override var encrypted by mutableStateOf<Boolean?>(null)
        private set
    override var password by mutableStateOf("")
    override var inProgress by mutableStateOf(false)
        private set
    override var log by mutableStateOf("")
        private set
    override var loadError by mutableStateOf<String?>(null)
        private set
    override var viewer by mutableStateOf<ArchiveViewerState?>(null)
        private set

    /** 最近一次请求的种类（request 清空后仍可用于错误对话框归属判断）。 */
    var lastKind by mutableStateOf<EpubReaderKind?>(null)
        private set

    /** 从某个 Tab 发起阅读请求（Recent / Directory 的打开回调入口）。 */
    fun open(kind: EpubReaderKind, target: DocumentFileModel) {
        encrypted = null
        password = ""
        inProgress = false
        loadError = null
        log = ""
        request = EpubReaderRequest(kind, target)
        lastKind = kind
        when (kind) {
            EpubReaderKind.LLM_ZIP -> openLlmZip(target)
            EpubReaderKind.EPUB -> openEpub(target)
            EpubReaderKind.TXT -> openTxt(target)
            EpubReaderKind.LLM -> openLlm(target)
        }
    }

    override fun updatePassword(value: String) {
        if (!inProgress) password = value
    }

    /** 密码对话框取消（进行中不允许）。 */
    override fun cancelRequest() {
        if (!inProgress) {
            request = null
            password = ""
        }
    }

    /** 加载 / 解析错误对话框关闭：同时清请求与日志。 */
    override fun dismissRequestError() {
        request = null
        loadError = null
        log = ""
    }

    /** 独立错误对话框关闭（请求已清空，仅清错误与日志）。 */
    override fun dismissErrorOnly() {
        loadError = null
        log = ""
    }

    /** 密码对话框确认：按种类分派解压逻辑。 */
    override fun submitPassword() {
        val req = request ?: return
        if (inProgress || password.isBlank()) return
        when (req.kind) {
            EpubReaderKind.LLM_ZIP -> submitLlmZipPassword(req.target)
            EpubReaderKind.EPUB -> submitEpubPassword(req.target)
            EpubReaderKind.TXT, EpubReaderKind.LLM -> Unit // 无密码流程
        }
    }

    /** 渲染器关闭（含缓存清理在内的善后由调用侧 RouteHost 决定）。 */
    override fun closeViewer() {
        viewer = null
    }

    override var encryptedExitDialog by mutableStateOf<EncryptedCacheExitDialogState?>(null)
        private set

    override fun requestEncryptedExit(title: String, onCleanup: () -> Unit) {
        encryptedExitDialog = EncryptedCacheExitDialogState(
            title = title,
            onDelete = {
                onCleanup()
                closeViewer()
                encryptedExitDialog = null
            },
            onKeep = {
                closeViewer()
                encryptedExitDialog = null
            }
        )
    }

    override fun dismissEncryptedExit() {
        encryptedExitDialog = null
    }

    override val routeActive: Boolean
        get() = request != null || viewer != null ||
            (lastKind == EpubReaderKind.EPUB && loadError != null)

    @Composable
    override fun RequestPhaseDialogs() = EpubReaderDialogs(this)

    // ------------------------------------------------------------------
    // 请求阶段：检查缓存 / 加密状态，非加密直接解压或转换进入渲染器
    // ------------------------------------------------------------------

        private fun openLlmZip(target: DocumentFileModel) = scope.launch {
        val cacheDir = getEpubCacheDir(context, target.uri)
        if (!isEpubCacheEncrypted(cacheDir) && isArchiveCacheStampValid(context, cacheDir, target)) {
            val cached = loadEpubFromCache(context, cacheDir)
            if (cached != null) {
                request = null
                viewer = EpubViewState(
                    extractResult = cached,
                    zipFileName = target.name,
                    epubUri = target.uri,
                    isEncrypted = false
                )
                return@launch
            }
            cacheDir.deleteRecursively()
        }
        encrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
        if (encrypted == false) {
            inProgress = true
            val result = withContext(Dispatchers.IO) {
                extractLlmZipToCache(context, target.uri, null, target.name)
            }
            inProgress = false
            request = null
            if (result != null) {
                viewer = EpubViewState(
                    extractResult = result,
                    zipFileName = target.name,
                    epubUri = target.uri,
                    isEncrypted = false
                )
            } else {
                Toast.makeText(context, context.getString(R.string.browser_no_txt_llm_found), Toast.LENGTH_LONG).show()
            }
        }
    }

        private fun openEpub(target: DocumentFileModel) = scope.launch {
        inProgress = true
        log = context.getString(R.string.browser_html_zip_preparing, target.name)
        try {
            var cachedResult: EpubExtractResult? = null
            var encryptedStatus: Boolean? = null
            withContext(Dispatchers.IO) {
                log += context.getString(R.string.browser_checking_cache)
                val cacheDir = getEpubCacheDir(context, target.uri)
                if (!isEpubCacheEncrypted(cacheDir) && isArchiveCacheStampValid(context, cacheDir, target)) {
                    cachedResult = loadEpubFromCache(context, cacheDir)
                    if (cachedResult != null) {
                        log += context.getString(R.string.browser_using_cache)
                        return@withContext
                    }
                    cacheDir.deleteRecursively()
                }
                log += context.getString(R.string.browser_checking_encryption)
                encryptedStatus = isArchiveEncrypted(context, target.uri, target.name)
            }
            val cached = cachedResult
            if (cached != null) {
                viewer = EpubViewState(
                    extractResult = cached,
                    zipFileName = target.name,
                    epubUri = target.uri,
                    isEncrypted = false
                )
                request = null
                inProgress = false
                return@launch
            }
            encrypted = encryptedStatus
            if (encryptedStatus == false) {
                val result = withContext(Dispatchers.IO) {
                    extractEpubToCache(context, target.uri, null, target.name) { entry ->
                        log += "$entry\n"
                    }
                }
                when (result) {
                    is EpubParseResult.Success -> {
                        viewer = EpubViewState(
                            extractResult = result.result,
                            zipFileName = target.name,
                            epubUri = target.uri,
                            isEncrypted = false
                        )
                        request = null
                        inProgress = false
                    }
                    is EpubParseResult.Error -> {
                        val detail = result.detail?.let { "\n$it" } ?: ""
                        log += context.getString(R.string.browser_log_error_detail, result.message ?: "", detail)
                        loadError = "${result.message}$detail"
                        inProgress = false
                        // 保留 request 以便显示错误对话框
                    }
                }
            } else {
                log += context.getString(R.string.browser_file_encrypted_need_password)
                inProgress = false
            }
        } catch (e: Exception) {
            log += context.getString(R.string.browser_log_exception, e.javaClass.simpleName, e.message ?: "")
            loadError = "${e.javaClass.simpleName}: ${e.message}"
            inProgress = false
        }
    }

        private fun openTxt(target: DocumentFileModel) = scope.launch {
        inProgress = true
        try {
            val cacheDir = getEpubCacheDir(context, target.uri)
            var result: EpubExtractResult? = null
            // 检查是否有有效缓存
            if (isArchiveCacheStampValid(context, cacheDir, target)) {
                result = loadEpubFromCache(context, cacheDir)
                // 如果缓存加载失败，清除旧缓存
                if (result == null) {
                    cacheDir.deleteRecursively()
                }
            }
            // 如果没有有效缓存，重新生成
            if (result == null) {
                result = prepareTxtAsEpub(context, target.uri, target.name)
            }
            if (result != null) {
                viewer = EpubViewState(
                    extractResult = result,
                    zipFileName = target.name,
                    epubUri = target.uri,
                    isEncrypted = false
                )
                request = null
            } else {
                loadError = context.getString(R.string.browser_txt_parse_failed)
            }
        } catch (e: Exception) {
            loadError = "${e.javaClass.simpleName}: ${e.message}"
        }
        inProgress = false
    }

        private fun openLlm(target: DocumentFileModel) = scope.launch {
        inProgress = true
        try {
            val cacheDir = getEpubCacheDir(context, target.uri)
            var result: EpubExtractResult? = null
            // 检查是否有有效缓存
            if (isArchiveCacheStampValid(context, cacheDir, target)) {
                result = loadEpubFromCache(context, cacheDir)
                if (result == null) {
                    cacheDir.deleteRecursively()
                }
            }
            if (result == null) {
                result = prepareLlmAsEpub(context, target.uri, target.name)
            }
            if (result != null) {
                viewer = EpubViewState(
                    extractResult = result,
                    zipFileName = target.name,
                    epubUri = target.uri,
                    isEncrypted = false
                )
                request = null
            } else {
                loadError = context.getString(R.string.browser_llm_parse_failed)
            }
        } catch (e: Exception) {
            loadError = "${e.javaClass.simpleName}: ${e.message}"
        }
        inProgress = false
    }

        private fun submitLlmZipPassword(target: DocumentFileModel) = scope.launch {
        inProgress = true
        val pwd = password.toCharArray()
        val result = withContext(Dispatchers.IO) {
            val cacheDir = getEpubCacheDir(context, target.uri)
            if (
                isEpubCacheEncrypted(cacheDir) &&
                isArchiveCacheStampValid(context, cacheDir, target) &&
                tryArchivePassword(context, target.uri, pwd)
            ) {
                loadEpubFromCache(context, cacheDir)
            } else {
                extractLlmZipToCache(context, target.uri, pwd, target.name)
            }
        }
        inProgress = false
        if (result != null) {
            request = null
            password = ""
            viewer = EpubViewState(
                extractResult = result,
                zipFileName = target.name,
                epubUri = target.uri,
                isEncrypted = true
            )
        } else {
            Toast.makeText(context, context.getString(R.string.browser_extract_failed_password_content), Toast.LENGTH_SHORT).show()
        }
    }

        private fun submitEpubPassword(target: DocumentFileModel) = scope.launch {
        inProgress = true
        log = context.getString(R.string.browser_epub_decrypt_start)
        val pwd = password.toCharArray()
        val result = withContext(Dispatchers.IO) {
            val cacheDir = getEpubCacheDir(context, target.uri)
            if (
                isEpubCacheEncrypted(cacheDir) &&
                isArchiveCacheStampValid(context, cacheDir, target) &&
                tryArchivePassword(context, target.uri, pwd)
            ) {
                loadEpubFromCache(context, cacheDir)?.let { EpubParseResult.Success(it) }
                    ?: extractEpubToCache(context, target.uri, pwd, target.name) { entry ->
                        log += "$entry\n"
                    }
            } else {
                extractEpubToCache(context, target.uri, pwd, target.name) { entry ->
                    log += "$entry\n"
                }
            }
        }
        inProgress = false
        when (result) {
            is EpubParseResult.Success -> {
                request = null
                password = ""
                viewer = EpubViewState(
                    extractResult = result.result,
                    zipFileName = target.name,
                    epubUri = target.uri,
                    isEncrypted = true
                )
            }
            is EpubParseResult.Error -> {
                val detail = result.detail?.let { "\n$it" } ?: ""
                log += context.getString(R.string.browser_log_error_detail, result.message ?: "", detail)
                loadError = "${result.message}$detail"
            }
        }
    }
}

/**
 * EPUB/文本书阅读器模块（功能模块化）：组合根 attach 一行接线；启动
 * 调用点（ReadingLauncher 分流表）直呼 service；routeActive 即"当前
 * 激活的查看 Route"，组合根聚合时只问不判。
 */
internal object EpubReaderModule {
    private var instance: EpubReaderWorkflowState? = null

    val service: EpubReaderWorkflowState
        get() = instance ?: error("EpubReaderModule 未接线：组合根需先 attach")

    fun attach(context: Context, scope: CoroutineScope) {
        instance = EpubReaderWorkflowState(context.applicationContext, scope)
    }

    fun resetForTest() {
        instance = null
    }
}
