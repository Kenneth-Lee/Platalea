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
import com.kenny.localmanager.file.EpubExtractResult
import com.kenny.localmanager.file.EpubParseResult
import com.kenny.localmanager.file.HtmlZipExtractResult
import com.kenny.localmanager.file.HtmlZipParseResult
import com.kenny.localmanager.file.MdZipExtractResult
import com.kenny.localmanager.file.PicZipExtractResult
import com.kenny.localmanager.file.extractEpubToCache
import com.kenny.localmanager.file.extractHtmlZipToCache
import com.kenny.localmanager.file.extractHtmlZipToCacheWithProgress
import com.kenny.localmanager.file.extractLlmZipToCache
import com.kenny.localmanager.file.extractMdZipToCache
import com.kenny.localmanager.file.extractPicZipToCache
import com.kenny.localmanager.file.findHtmlZipIndexFile
import com.kenny.localmanager.file.findMdZipCacheTarget
import com.kenny.localmanager.file.getEpubCacheDir
import com.kenny.localmanager.file.getEpubCacheTimestamp
import com.kenny.localmanager.file.getHtmlZipCacheDir
import com.kenny.localmanager.file.getHtmlZipCacheTimestamp
import com.kenny.localmanager.file.getMdZipCacheDir
import com.kenny.localmanager.file.getMdZipCacheTimestamp
import com.kenny.localmanager.file.getPicZipCacheDir
import com.kenny.localmanager.file.getPicZipCacheTimestamp
import com.kenny.localmanager.file.getVidZipCacheDir
import com.kenny.localmanager.file.getVidZipCacheTimestamp
import com.kenny.localmanager.file.isArchiveEncrypted
import com.kenny.localmanager.file.isEpubCacheEncrypted
import com.kenny.localmanager.file.isHtmlZipCacheEncrypted
import com.kenny.localmanager.file.isMdZipCacheEncrypted
import com.kenny.localmanager.file.isPicZipCacheEncrypted
import com.kenny.localmanager.file.isVidZipCacheEncrypted
import com.kenny.localmanager.file.loadEpubFromCache
import com.kenny.localmanager.file.prepareLlmAsEpub
import com.kenny.localmanager.file.prepareTxtAsEpub
import com.kenny.localmanager.file.readVidZipVideoPaths
import com.kenny.localmanager.file.tryArchivePassword
import com.kenny.localmanager.file.tryPicZipPassword
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
// ============================================================================
// Archive 阅读器域 —— 状态机（请求阶段逻辑）
//
// 单槽工作流：open(kind, target) 后经历"检查缓存 / 加密 -> 密码输入 -> 解压"，
// 产出 ArchiveViewerState 进入查看器阶段。宿主只与本类交互，不感知 8 组差异。
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
) {
    var request by mutableStateOf<ArchiveReaderRequest?>(null)
        private set
    var encrypted by mutableStateOf<Boolean?>(null)
        private set
    var password by mutableStateOf("")
    var inProgress by mutableStateOf(false)
        private set
    var log by mutableStateOf("")
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var viewer by mutableStateOf<ArchiveViewerState?>(null)
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
            ArchiveReaderKind.LLM_ZIP -> openLlmZip(target)
            ArchiveReaderKind.EPUB -> openEpub(target)
            ArchiveReaderKind.TXT -> openTxt(target)
            ArchiveReaderKind.LLM -> openLlm(target)
            ArchiveReaderKind.PIC_ZIP -> openPicZip(target)
            ArchiveReaderKind.VID_ZIP -> openVidZip(target)
        }
    }

    /** 宿主直入查看器（目录图片画廊直接以 PicZip 渲染，不经过请求阶段）。 */
    fun openViewer(route: ArchiveViewerState) {
        viewer = route
    }

    fun updatePassword(value: String) {
        if (!inProgress) password = value
    }

    /** 密码对话框取消（进行中不允许）。 */
    fun cancelRequest() {
        if (!inProgress) {
            request = null
            password = ""
        }
    }

    /** 加载 / 解析错误对话框关闭：同时清请求与日志。 */
    fun dismissRequestError() {
        request = null
        loadError = null
        log = ""
    }

    /** 独立错误对话框关闭（请求已清空，仅清错误与日志）。 */
    fun dismissErrorOnly() {
        loadError = null
        log = ""
    }

    /** 密码对话框确认：按种类分派解压逻辑。 */
    fun submitPassword() {
        val req = request ?: return
        if (inProgress || password.isBlank()) return
        when (req.kind) {
            ArchiveReaderKind.MD_ZIP -> submitMdZipPassword(req.target)
            ArchiveReaderKind.HTML_ZIP -> submitHtmlZipPassword(req.target)
            ArchiveReaderKind.LLM_ZIP -> submitLlmZipPassword(req.target)
            ArchiveReaderKind.EPUB -> submitEpubPassword(req.target)
            ArchiveReaderKind.PIC_ZIP -> submitPicZipPassword(req.target)
            ArchiveReaderKind.VID_ZIP -> submitVidZipPassword(req.target)
            ArchiveReaderKind.TXT, ArchiveReaderKind.LLM -> Unit // 无密码流程
        }
    }

    /** 查看器关闭（含缓存清理在内的善后由调用侧 RouteHost 决定）。 */
    fun closeViewer() {
        viewer = null
    }

    // ------------------------------------------------------------------
    // 请求阶段：检查缓存 / 加密状态，非加密直接解压进入查看器
    // ------------------------------------------------------------------

    private fun openMdZip(target: DocumentFileModel) = scope.launch {
        // 先检查是否有有效缓存（非加密时）
        val cacheDir = getMdZipCacheDir(context, target.uri)
        val cacheTs = getMdZipCacheTimestamp(cacheDir)
        if (cacheTs > 0 && !isMdZipCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
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
        val cacheTs = getHtmlZipCacheTimestamp(cacheDir)
        if (cacheTs > 0 && !isHtmlZipCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
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

    private fun openLlmZip(target: DocumentFileModel) = scope.launch {
        val cacheDir = getEpubCacheDir(context, target.uri)
        val cacheTs = getEpubCacheTimestamp(cacheDir)
        if (cacheTs > 0 && !isEpubCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
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
                val cacheTs = getEpubCacheTimestamp(cacheDir)
                if (cacheTs > 0 && !isEpubCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
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

    /** 处理 TXT 文件，转换为 EPUB 格式查看。 */
    private fun openTxt(target: DocumentFileModel) = scope.launch {
        inProgress = true
        try {
            val cacheDir = getEpubCacheDir(context, target.uri)
            val cacheTs = getEpubCacheTimestamp(cacheDir)
            var result: EpubExtractResult? = null
            // 检查是否有有效缓存
            if (cacheTs > 0 && cacheTs >= target.lastModified) {
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

    /** 处理 LLM 对话文件，转换为 EPUB 格式查看。 */
    private fun openLlm(target: DocumentFileModel) = scope.launch {
        inProgress = true
        try {
            val cacheDir = getEpubCacheDir(context, target.uri)
            val cacheTs = getEpubCacheTimestamp(cacheDir)
            var result: EpubExtractResult? = null
            // 检查是否有有效缓存
            if (cacheTs > 0 && cacheTs >= target.lastModified) {
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

    private fun openPicZip(target: DocumentFileModel) = scope.launch {
        val cacheDir = getPicZipCacheDir(context, target.uri)
        val cacheTs = getPicZipCacheTimestamp(cacheDir)
        if (cacheTs > 0 && !isPicZipCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
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
        val cacheTs = getVidZipCacheTimestamp(cacheDir)
        if (cacheTs > 0 && !isVidZipCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
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
            val cacheTs = getMdZipCacheTimestamp(cacheDir)
            val contentDir = File(cacheDir, "content")
            val isRstZip = target.name.endsWith(".rst.zip", ignoreCase = true)
            if (
                cacheTs > 0 &&
                isMdZipCacheEncrypted(cacheDir) &&
                cacheTs >= target.lastModified &&
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
            val cacheTs = getHtmlZipCacheTimestamp(cacheDir)
            val contentDir = File(cacheDir, "content")
            if (
                cacheTs > 0 &&
                isHtmlZipCacheEncrypted(cacheDir) &&
                cacheTs >= target.lastModified &&
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

    private fun submitLlmZipPassword(target: DocumentFileModel) = scope.launch {
        inProgress = true
        val pwd = password.toCharArray()
        val result = withContext(Dispatchers.IO) {
            val cacheDir = getEpubCacheDir(context, target.uri)
            val cacheTs = getEpubCacheTimestamp(cacheDir)
            if (
                cacheTs > 0 &&
                isEpubCacheEncrypted(cacheDir) &&
                cacheTs >= target.lastModified &&
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
            val cacheTs = getEpubCacheTimestamp(cacheDir)
            if (
                cacheTs > 0 &&
                isEpubCacheEncrypted(cacheDir) &&
                cacheTs >= target.lastModified &&
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

@Composable
internal fun rememberArchiveReaderWorkflow(context: Context, scope: CoroutineScope): ArchiveReaderWorkflowState {
    return remember(context, scope) { ArchiveReaderWorkflowState(context.applicationContext, scope) }
}
