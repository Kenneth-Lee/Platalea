package com.kenny.localmanager.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.HtmlZipExtractResult
import com.kenny.localmanager.file.HtmlZipParseResult
import com.kenny.localmanager.file.MdZipExtractResult
import com.kenny.localmanager.file.PicZipExtractResult
import com.kenny.localmanager.file.extractHtmlZipToCache
import com.kenny.localmanager.file.extractHtmlZipToCacheWithProgress
import com.kenny.localmanager.file.extractMdZipToCache
import com.kenny.localmanager.file.extractPicZipToCache
import com.kenny.localmanager.file.findHtmlZipIndexFile
import com.kenny.localmanager.file.findMdZipCacheTarget
import com.kenny.localmanager.file.getHtmlZipCacheDir
import com.kenny.localmanager.file.isArchiveCacheStampValid
import com.kenny.localmanager.file.getHtmlZipCacheTimestamp
import com.kenny.localmanager.file.getMdZipCacheDir
import com.kenny.localmanager.file.getMdZipCacheTimestamp
import com.kenny.localmanager.file.getPicZipCacheDir
import com.kenny.localmanager.file.getPicZipCacheTimestamp
import com.kenny.localmanager.file.getVidZipCacheDir
import com.kenny.localmanager.file.getVidZipCacheTimestamp
import com.kenny.localmanager.file.isArchiveEncrypted
import com.kenny.localmanager.file.isHtmlZipCacheEncrypted
import com.kenny.localmanager.file.isMdZipCacheEncrypted
import com.kenny.localmanager.file.isPicZipCacheEncrypted
import com.kenny.localmanager.file.isVidZipCacheEncrypted
import com.kenny.localmanager.file.readVidZipVideoPaths
import com.kenny.localmanager.file.tryArchivePassword
import com.kenny.localmanager.file.tryPicZipPassword
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
/** 阅读工作流请求阶段的通用能力（密码 / 进度 / 日志 / 错误）：共享对话框组件面向此接口。 */
internal interface ReaderRequestWorkflow {
    val encrypted: Boolean?
    val password: String
    val inProgress: Boolean
    val log: String
    val loadError: String?
    fun updatePassword(value: String)
    fun submitPassword()
    fun cancelRequest()
    fun dismissRequestError()
    fun dismissErrorOnly()
}

/** 阅读工作流查看器阶段的通用能力：查看器装配层（ArchiveViewerRouteHost）面向此接口。 */
internal interface ViewerHostWorkflow {
    val viewer: ArchiveViewerState?
    fun closeViewer()

    /**
     * 本工作流是否占据独立全屏 Route（E1 打开型全屏化）：
     * request（密码 / 处理中）、viewer、错误残留任一活跃即占据。
     */
    val routeActive: Boolean

    /** request / 错误阶段对话框（Route 激活、viewer 未就绪时由 Host 渲染）。 */
    @Composable fun RequestPhaseDialogs()

    /** 加密缓存退出确认对话框（查看器阶段，域内自持状态）。 */
    val encryptedExitDialog: EncryptedCacheExitDialogState?

    /** 请求退出确认：删除缓存退出 / 保留缓存退出（动作与关闭查看器由域内统一封装）。 */
    fun requestEncryptedExit(title: String, onCleanup: () -> Unit)

    /** 关闭退出确认对话框（不退出查看器）。 */
    fun dismissEncryptedExit()
}

// ============================================================================
// Archive 阅读器域 —— 状态机（请求阶段逻辑）
//
// 单槽工作流：open(kind, target) 后经历"检查缓存 / 加密 -> 密码输入 -> 解压"，
// 产出 ArchiveViewerState 进入查看器阶段。宿主只与本类交互，不感知组内差异。
// （EPUB 族 TXT/LLM/EPUB/LLM_ZIP 已分离至 EpubReaderWorkflowState，批次 C）
// ============================================================================

/**
 * Archive 阅读器工作流状态：单槽（同一时刻至多一个请求在处理）。
 *
 * 阶段一 request != null：检查加密 / 密码输入 / 解压中，渲染 [ArchiveReaderDialogs]。
 * 阶段二 viewer != null：解压完成进入全屏查看器，渲染 [ArchiveViewerRouteHost]。
 */
@Stable
internal class ArchiveReaderWorkflowState(
    private val context: Context,
    private val scope: CoroutineScope
) : ReaderRequestWorkflow, ViewerHostWorkflow {
    var request by mutableStateOf<ArchiveReaderRequest?>(null)
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
    override var encryptedExitDialog by mutableStateOf<EncryptedCacheExitDialogState?>(null)
        private set

    /** 最近一次请求的种类（request 清空后仍可用于错误对话框归属判断）。 */
    var lastKind by mutableStateOf<ArchiveReaderKind?>(null)
        private set

    /** 从某个 Tab 发起阅读请求（Recent / Directory 的打开回调入口）。 */
    fun open(kind: ArchiveReaderKind, target: DocumentFileModel) {
        encrypted = null
        password = ""
        inProgress = false
        loadError = null
        log = ""
        request = ArchiveReaderRequest(kind, target)
        lastKind = kind
        when (kind) {
            ArchiveReaderKind.MD_ZIP -> openMdZip(target)
            ArchiveReaderKind.HTML_ZIP -> openHtmlZip(target)
            ArchiveReaderKind.PIC_ZIP -> openPicZip(target)
            ArchiveReaderKind.VID_ZIP -> openVidZip(target)
        }
    }

    /** 宿主直入查看器（目录图片画廊直接以 PicZip 渲染，不经过请求阶段）。 */
    fun openViewer(route: ArchiveViewerState) {
        viewer = route
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
            ArchiveReaderKind.MD_ZIP -> submitMdZipPassword(req.target)
            ArchiveReaderKind.HTML_ZIP -> submitHtmlZipPassword(req.target)
            ArchiveReaderKind.PIC_ZIP -> submitPicZipPassword(req.target)
            ArchiveReaderKind.VID_ZIP -> submitVidZipPassword(req.target)
        }
    }

    /** 查看器关闭（含缓存清理在内的善后由调用侧 RouteHost 决定）。 */
    override fun closeViewer() {
        viewer = null
    }

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
        get() = request != null || viewer != null

    @Composable
    override fun RequestPhaseDialogs() = ArchiveReaderDialogs(this)

    // ------------------------------------------------------------------
    // 请求阶段：检查缓存 / 加密状态，非加密直接解压进入查看器
    // ------------------------------------------------------------------

    private fun openMdZip(target: DocumentFileModel) = scope.launch {
        // 先检查是否有有效缓存（非加密时）
        val cacheDir = getMdZipCacheDir(context, target.uri)
        if (!isMdZipCacheEncrypted(cacheDir) && isArchiveCacheStampValid(context, cacheDir, target)) {
            val contentDir = File(cacheDir, "content")
            val hasContent = contentDir.exists() &&
                (contentDir.listFiles()?.isNotEmpty() == true)
            if (hasContent) {
                val isRstZip = target.name.endsWith(".rst.zip", ignoreCase = true)
                val cachedTarget = findMdZipCacheTarget(context, cacheDir, isRstZip)
                request = null
                viewer = MdZipViewState(
                    targetFile = cachedTarget,
                    contentDir = contentDir,
                    zipFileName = target.name,
                    zipUri = target.uri,
                    isEncrypted = false
                )
                return@launch
            }
            // 缓存内容为空，废弃并重新解压
            cacheDir.deleteRecursively()
        }
        encrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
        // 非加密的直接解压
        if (encrypted == false) {
            inProgress = true
            val result = withContext(Dispatchers.IO) { extractMdZipToCache(context, target.uri, null, target.name) }
            inProgress = false
            request = null
            if (result != null) {
                viewer = MdZipViewState(
                    targetFile = result.targetFile,  // 可能为 null
                    contentDir = result.contentDir,
                    zipFileName = target.name,
                    zipUri = target.uri,
                    isEncrypted = false
                )
            } else {
                Toast.makeText(context, context.getString(R.string.browser_extract_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openHtmlZip(target: DocumentFileModel) = scope.launch {
        log = context.getString(R.string.browser_html_zip_preparing, target.name)
        val cacheDir = getHtmlZipCacheDir(context, target.uri)
        if (!isHtmlZipCacheEncrypted(cacheDir) && isArchiveCacheStampValid(context, cacheDir, target)) {
            val contentDir = File(cacheDir, "content")
            if (contentDir.exists() && contentDir.listFiles()?.isNotEmpty() == true) {
                val cachedIndex = findHtmlZipIndexFile(contentDir)
                request = null
                viewer = HtmlZipViewState(
                    indexFile = cachedIndex,
                    contentDir = contentDir,
                    zipFileName = target.name,
                    zipUri = target.uri,
                    isEncrypted = false
                )
                return@launch
            }
            cacheDir.deleteRecursively()
        }
        log += context.getString(R.string.browser_checking_encryption)
        encrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
        log += context.getString(R.string.browser_encryption_status, encrypted.toString())
        if (encrypted == false) {
            inProgress = true
            val result = withContext(Dispatchers.IO) {
                extractHtmlZipToCacheWithProgress(context, target.uri, null, target.name) { entry ->
                    log += "$entry\n"
                }
            }
            inProgress = false
            request = null
            when (result) {
                is HtmlZipParseResult.Success -> {
                    viewer = HtmlZipViewState(
                        indexFile = result.result.indexFile,
                        contentDir = result.result.contentDir,
                        zipFileName = target.name,
                        zipUri = target.uri,
                        isEncrypted = false
                    )
                }
                is HtmlZipParseResult.Error -> {
                    val detail = result.detail?.let { "\n$it" } ?: ""
                    log += context.getString(R.string.browser_log_error_detail, result.message ?: "", detail)
                    loadError = "${result.message}$detail"
                }
            }
        } else if (encrypted == true) {
            log += context.getString(R.string.browser_file_encrypted_need_password)
        }
    }





    private fun openPicZip(target: DocumentFileModel) = scope.launch {
        val cacheDir = getPicZipCacheDir(context, target.uri)
        if (!isPicZipCacheEncrypted(cacheDir) && isArchiveCacheStampValid(context, cacheDir, target)) {
            val contentDir = File(cacheDir, "content")
            val listFile = File(cacheDir, ".image_list")
            if (contentDir.exists() && listFile.exists()) {
                val paths = listFile.readText().lineSequence().filter { it.isNotBlank() }.toList()
                val lastIndexFile = File(cacheDir, ".last_index")
                val initialIndex = lastIndexFile.takeIf { it.exists() }?.readText()?.toIntOrNull()?.coerceIn(0, paths.size - 1) ?: 0
                request = null
                viewer = PicZipViewState(
                    contentDir = contentDir,
                    imagePaths = paths,
                    zipFileName = target.name,
                    zipUri = target.uri,
                    isEncrypted = false,
                    password = null,
                    initialIndex = initialIndex
                )
                return@launch
            }
            cacheDir.deleteRecursively()
        }
        encrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
        if (encrypted == false) {
            inProgress = true
            val result = withContext(Dispatchers.IO) { extractPicZipToCache(context, target.uri, null, target.name) }
            if (result != null) {
                request = null
                viewer = PicZipViewState(
                    contentDir = result.contentDir,
                    imagePaths = result.imagePaths,
                    zipFileName = target.name,
                    zipUri = target.uri,
                    isEncrypted = false,
                    password = null,
                    initialIndex = 0
                )
            } else {
                request = null
                Toast.makeText(context, context.getString(R.string.browser_extract_failed), Toast.LENGTH_SHORT).show()
            }
            inProgress = false
        }
    }

    private fun openVidZip(target: DocumentFileModel) = scope.launch {
        val cacheDir = getVidZipCacheDir(context, target.uri)
        if (!isVidZipCacheEncrypted(cacheDir) && isArchiveCacheStampValid(context, cacheDir, target)) {
            val contentDir = File(cacheDir, "content")
            val listFile = File(cacheDir, ".video_list")
            if (contentDir.exists() && listFile.exists()) {
                val paths = listFile.readText().lineSequence().filter { it.isNotBlank() }.toList()
                if (paths.isNotEmpty()) {
                    request = null
                    viewer = VidZipViewState(
                        contentDir = contentDir,
                        videoPaths = paths,
                        zipFileName = target.name,
                        zipUri = target.uri,
                        isEncrypted = false,
                        password = null,
                        cleanupCacheDir = cacheDir
                    )
                    return@launch
                }
            }
            cacheDir.deleteRecursively()
        }
        encrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
        if (encrypted == false) {
            inProgress = true
            val paths = withContext(Dispatchers.IO) {
                readVidZipVideoPaths(context, target.uri, null, target.name)
            }
            inProgress = false
            if (paths != null && paths.isNotEmpty()) {
                val contentDir = File(cacheDir, "content")
                request = null
                viewer = VidZipViewState(
                    contentDir = contentDir,
                    videoPaths = paths,
                    zipFileName = target.name,
                    zipUri = target.uri,
                    isEncrypted = false,
                    password = null,
                    cleanupCacheDir = cacheDir
                )
            } else {
                request = null
                Toast.makeText(context, context.getString(R.string.browser_no_video_in_archive), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------------------------------------------
    // 密码阶段：优先复用加密缓存，否则带密码解压
    // ------------------------------------------------------------------

    private fun submitMdZipPassword(target: DocumentFileModel) = scope.launch {
        inProgress = true
        val pwd = password.toCharArray()
        val result = withContext(Dispatchers.IO) {
            val cacheDir = getMdZipCacheDir(context, target.uri)
            val contentDir = File(cacheDir, "content")
            val isRstZip = target.name.endsWith(".rst.zip", ignoreCase = true)
            if (
                isMdZipCacheEncrypted(cacheDir) &&
                isArchiveCacheStampValid(context, cacheDir, target) &&
                contentDir.exists() &&
                contentDir.listFiles()?.isNotEmpty() == true &&
                tryArchivePassword(context, target.uri, pwd)
            ) {
                MdZipExtractResult(
                    cacheDir = cacheDir,
                    contentDir = contentDir,
                    targetFile = findMdZipCacheTarget(context, cacheDir, isRstZip),
                    isEncrypted = true
                )
            } else {
                extractMdZipToCache(context, target.uri, pwd, target.name)
            }
        }
        inProgress = false
        if (result != null) {
            request = null
            password = ""
            viewer = MdZipViewState(
                targetFile = result.targetFile,  // 可能为 null
                contentDir = result.contentDir,
                zipFileName = target.name,
                zipUri = target.uri,
                isEncrypted = true
            )
        } else {
            Toast.makeText(context, context.getString(R.string.browser_extract_failed_password), Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitHtmlZipPassword(target: DocumentFileModel) = scope.launch {
        inProgress = true
        val pwd = password.toCharArray()
        val result = withContext(Dispatchers.IO) {
            val cacheDir = getHtmlZipCacheDir(context, target.uri)
            val contentDir = File(cacheDir, "content")
            if (
                isHtmlZipCacheEncrypted(cacheDir) &&
                isArchiveCacheStampValid(context, cacheDir, target) &&
                contentDir.exists() &&
                contentDir.listFiles()?.isNotEmpty() == true &&
                tryArchivePassword(context, target.uri, pwd)
            ) {
                HtmlZipExtractResult(
                    cacheDir = cacheDir,
                    contentDir = contentDir,
                    indexFile = findHtmlZipIndexFile(contentDir),
                    isEncrypted = true
                )
            } else {
                extractHtmlZipToCache(context, target.uri, pwd, target.name)
            }
        }
        inProgress = false
        if (result != null) {
            request = null
            password = ""
            viewer = HtmlZipViewState(
                indexFile = result.indexFile,
                contentDir = result.contentDir,
                zipFileName = target.name,
                zipUri = target.uri,
                isEncrypted = true
            )
        } else {
            Toast.makeText(context, context.getString(R.string.browser_extract_failed_password), Toast.LENGTH_SHORT).show()
        }
    }



    private fun submitPicZipPassword(target: DocumentFileModel) = scope.launch {
        inProgress = true
        val pwd = password.toCharArray()
        var result: PicZipExtractResult? = null
        var cachedInitialIndex = 0
        withContext(Dispatchers.IO) {
            val cacheDir = getPicZipCacheDir(context, target.uri)
            val contentDir = File(cacheDir, "content")
            val listFile = File(cacheDir, ".image_list")
            if (contentDir.exists() && listFile.exists() && tryPicZipPassword(context, target.uri, pwd)) {
                val paths = listFile.readText().lineSequence().filter { it.isNotBlank() }.toList()
                val lastIndexFile = File(cacheDir, ".last_index")
                cachedInitialIndex = lastIndexFile.takeIf { it.exists() }?.readText()?.toIntOrNull()?.coerceIn(0, (paths.size - 1).coerceAtLeast(0)) ?: 0
                result = PicZipExtractResult(cacheDir, contentDir, paths, true)
            }
            if (result == null) {
                result = extractPicZipToCache(context, target.uri, pwd, target.name)
            }
        }
        val res = result
        if (res != null) {
            request = null
            password = ""
            viewer = PicZipViewState(
                contentDir = res.contentDir,
                imagePaths = res.imagePaths,
                zipFileName = target.name,
                zipUri = target.uri,
                isEncrypted = true,
                password = pwd,
                initialIndex = cachedInitialIndex
            )
        } else {
            Toast.makeText(context, context.getString(R.string.browser_extract_failed_password), Toast.LENGTH_SHORT).show()
        }
        inProgress = false
    }

    private fun submitVidZipPassword(target: DocumentFileModel) = scope.launch {
        inProgress = true
        val pwd = password.toCharArray()
        val result = withContext(Dispatchers.IO) {
            readVidZipVideoPaths(context, target.uri, pwd, target.name)
        }
        if (result != null && result.isNotEmpty()) {
            val cacheDir = getVidZipCacheDir(context, target.uri)
            request = null
            password = ""
            viewer = VidZipViewState(
                contentDir = File(cacheDir, "content"),
                videoPaths = result,
                zipFileName = target.name,
                zipUri = target.uri,
                isEncrypted = true,
                password = pwd,
                cleanupCacheDir = cacheDir
            )
        } else {
            Toast.makeText(context, context.getString(R.string.browser_extract_failed_password), Toast.LENGTH_SHORT).show()
        }
        inProgress = false
    }
}

/**
 * 归档查看器模块（功能模块化）：组合根 attach 一行接线；启动调用点
 * （目录域 openViewer、ReadingLauncher 分流表）直呼 service；
 * routeActive 即"当前激活的查看 Route"，组合根聚合时只问不判。
 */
internal object ArchiveReaderModule {
    private var instance: ArchiveReaderWorkflowState? = null

    val service: ArchiveReaderWorkflowState
        get() = instance ?: error("ArchiveReaderModule 未接线：组合根需先 attach")

    fun attach(context: Context, scope: CoroutineScope) {
        instance = ArchiveReaderWorkflowState(context.applicationContext, scope)
    }

    fun resetForTest() {
        instance = null
    }
}
