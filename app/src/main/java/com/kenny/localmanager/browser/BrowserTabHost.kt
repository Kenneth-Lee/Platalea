package com.kenny.localmanager.browser

import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import com.kenny.localmanager.family.BulletinForwardPayload
import com.kenny.localmanager.data.AudioMetaAnnotation
import com.kenny.localmanager.dict.importStarDict
import com.kenny.localmanager.file.ArchiveCompressionFormat
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.UnzipResult
import com.kenny.localmanager.file.addToZip
import com.kenny.localmanager.file.copyDocumentTo
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.extractZipEntries
import com.kenny.localmanager.file.emptyTrash
import com.kenny.localmanager.file.fileExtensionKey
import com.kenny.localmanager.file.invalidateArchiveViewerCaches
import com.kenny.localmanager.file.isGalleryImageFile
import com.kenny.localmanager.file.isInsideDirectory
import com.kenny.localmanager.file.launchExternalOpen
import com.kenny.localmanager.file.moveDocumentTo
import com.kenny.localmanager.file.renameDocumentResult
import java.io.FileNotFoundException
import com.kenny.localmanager.file.moveToTrash
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.pathFromRoot
import com.kenny.localmanager.file.relativePathFromCurrentDir
import com.kenny.localmanager.file.removeZipEntries
import com.kenny.localmanager.file.restoreFromTrash
import com.kenny.localmanager.file.unzipToParent
import com.kenny.localmanager.player.PlaybackState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kenny.localmanager.archiveops.ArchiveOperationRequest
import com.kenny.localmanager.archiveops.ArchiveOpsModule
import com.kenny.localmanager.archivereader.ArchiveReaderModule
import com.kenny.localmanager.archivereader.PicZipViewState
import com.kenny.localmanager.config.BrowserConfigView
import com.kenny.localmanager.config.ConfigImportExportModule
import com.kenny.localmanager.config.OperationTargetConfigView
import com.kenny.localmanager.crypto.CryptoModule
import com.kenny.localmanager.crypto.CryptoRequest
import com.kenny.localmanager.crypto.GpgOpState
import com.kenny.localmanager.obfuscate.QuickObfuscateModule
import com.kenny.localmanager.player.DetailMetadataKind
import com.kenny.localmanager.player.EditableAudioAnnotationValidation
import com.kenny.localmanager.player.PlayerTabFeature
import com.kenny.localmanager.player.isMp3AudioFile
import com.kenny.localmanager.player.mergeEditableAudioAnnotationForBatch
import com.kenny.localmanager.player.readEditableAudioAnnotation
import com.kenny.localmanager.player.saveEditableAudioAnnotationToMp3
import com.kenny.localmanager.player.saveEditableAudioAnnotationWithJaudiotagger
import com.kenny.localmanager.player.supportsJaudiotaggerWrite
import com.kenny.localmanager.player.validateEditableAudioAnnotationForm
import com.kenny.localmanager.archivereader.ArchiveReaderIntents
import com.kenny.localmanager.archiveops.ArchiveOpsIntents
import com.kenny.localmanager.crypto.CryptoIntents
import com.kenny.localmanager.epub.EpubReaderIntents
import com.kenny.localmanager.file.isExtractableArchive
import com.kenny.localmanager.viewer.ViewerIntents
import com.kenny.localmanager.ui.MainTab
import com.kenny.localmanager.ui.OperationRunState
import com.kenny.localmanager.ui.RootService
import com.kenny.localmanager.ui.TransientRoute
import com.kenny.localmanager.ui.normalizeContentUriString
import com.kenny.localmanager.viewer.ReadingLauncher
import com.kenny.localmanager.viewer.ViewerSessionsModule
import com.kenny.localmanager.zipedit.ZipAddRequest
import com.kenny.localmanager.zipedit.ZipEditDialogs
import com.kenny.localmanager.zipedit.ZipEditModule
import com.kenny.localmanager.zipedit.ZipManagerRouteState
import com.kenny.localmanager.zipedit.ZipManagerScreen
import com.kenny.localmanager.ui.RECENT_TYPE_EXTERNAL_OPEN

// 文件浏览模块（《主界面 AppShell 分层设计》Step 2）：目录浏览、待处理面板、批次确认的组合宿主。
// 导航开关（showPendingList 等 Route 事实）由宿主持有、经 state 注入；内容实现全部内聚本文件。
// 批量操作的发起经共享对象直呼（archiveOps）或模块直呼（CryptoModule），跨域弹窗请求经出口回调上交宿主。

// 批量操作执行 helper（原主程序顶层平移）
private fun doCopyPendingToCurrentDir(
    context: Context,
    displayUri: String,
    pendingList: List<DocumentFileModel>,
    rootUri: String?,
    copyMoveLog: ((String) -> Unit)?,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    onSuccessAll: () -> Unit,
    onRefreshDone: () -> Unit
) {
    val targetDirUri = normalizeContentUriString(displayUri)
    copyMoveLog?.invoke(context.getString(R.string.browser_log_copy_target, targetDirUri))
    val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
    runBatchOperation(
        context.getString(R.string.pending_list_action_copy),
        pendingList.map { it.name },
        { state ->
            copyMoveLog?.invoke(context.getString(R.string.browser_log_copy_result, state.okCount, state.failCount))
            if (state.failCount == 0 && state.okCount > 0) onSuccessAll()
            onRefreshDone()
        }
    ) { state ->
        state.runEach(pendingList) { model ->
            withContext(Dispatchers.IO) {
                if (copyDocumentTo(context, model.uri, Uri.parse(targetDirUri), treeUri, copyMoveLog) != null) null else ""
            }
        }
    }
}

private fun doMovePendingToCurrentDir(
    context: Context,
    displayUri: String,
    pendingList: List<DocumentFileModel>,
    rootUri: String?,
    copyMoveLog: ((String) -> Unit)?,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    onSuccessAll: () -> Unit,
    onRefreshDone: () -> Unit
) {
    val targetDirUri = normalizeContentUriString(displayUri)
    copyMoveLog?.invoke(context.getString(R.string.browser_log_move_target, targetDirUri))
    val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
    runBatchOperation(
        context.getString(R.string.pending_list_action_move),
        pendingList.map { it.name },
        { state ->
            copyMoveLog?.invoke(context.getString(R.string.browser_log_move_result, state.okCount, state.failCount))
            if (state.failCount == 0 && state.okCount > 0) onSuccessAll()
            onRefreshDone()
        }
    ) { state ->
        state.runEach(pendingList) { model ->
            withContext(Dispatchers.IO) {
                if (moveDocumentTo(context, model.uri, Uri.parse(targetDirUri), treeUri, copyMoveLog)) null else ""
            }
        }
    }
}

private val sevenZSplitPartRegex = Regex("(?i)^(.*\\.7z)\\.(\\d{3})$")

private data class SevenZSplitPendingRef(
    val baseName: String,
    val partIndex: Int
)

private data class PendingArchiveExtractTarget(
    val model: DocumentFileModel
)

private fun parseSevenZSplitPendingRef(fileName: String): SevenZSplitPendingRef? {
    val trimmed = fileName.trim()
    val match = sevenZSplitPartRegex.matchEntire(trimmed) ?: return null
    val baseName = match.groupValues[1]
    val partIndex = match.groupValues[2].toIntOrNull() ?: return null
    return SevenZSplitPendingRef(baseName = baseName, partIndex = partIndex)
}

private fun pendingUriParentKey(uri: Uri): String {
    val raw = uri.toString()
    return raw.substringBeforeLast('/', raw)
}

private fun buildPendingArchiveExtractTargets(pendingList: List<DocumentFileModel>): List<PendingArchiveExtractTarget> {
    val archives = pendingList.filter { !it.isDirectory && isExtractableArchive(it.name) }
    if (archives.isEmpty()) return emptyList()

    val grouped = LinkedHashMap<String, MutableList<DocumentFileModel>>()
    archives.forEach { item ->
        val splitRef = parseSevenZSplitPendingRef(item.name)
        val key = if (splitRef != null) {
            "${pendingUriParentKey(item.uri)}|${splitRef.baseName.lowercase(Locale.ROOT)}"
        } else {
            item.uri.toString()
        }
        grouped.getOrPut(key) { mutableListOf() }.add(item)
    }

    return grouped.entries.map { (key, values) ->
        val representative = values.minWithOrNull(
            compareBy<DocumentFileModel> {
                parseSevenZSplitPendingRef(it.name)?.partIndex ?: Int.MAX_VALUE
            }.thenBy { it.name.lowercase(Locale.ROOT) }
        ) ?: values.first()
        PendingArchiveExtractTarget(model = representative)
    }
}

private fun doExtractPendingArchivesToCurrentDir(
    context: Context,
    displayUri: String,
    pendingListSnapshot: List<DocumentFileModel>,
    rootUri: String?,
    runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    onAllSuccess: () -> Unit,
    onRefreshDone: () -> Unit
) {
    val extractTargets = buildPendingArchiveExtractTargets(pendingListSnapshot)
    if (extractTargets.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.browser_no_extract_candidates), Toast.LENGTH_SHORT).show()
        return
    }

    val targetDirUri = Uri.parse(normalizeContentUriString(displayUri))
    val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
    runBatchOperation(
        context.getString(R.string.browser_op_extract),
        extractTargets.map { it.model.name },
        { state ->
            if (state.failCount == 0 && state.okCount > 0) onAllSuccess()
            onRefreshDone()
        }
    ) { state ->
        state.runEach(extractTargets) { target ->
            withContext(Dispatchers.IO) {
                when (
                    val result = unzipToParent(
                        context = context,
                        zipUri = target.model.uri,
                        parentDirUri = targetDirUri,
                        treeUri = treeUri,
                        archiveName = target.model.name,
                        password = null,
                        setProgress = { _, _ -> },
                        shouldCancel = { state.cancelRequested }
                    )
                ) {
                    is UnzipResult.Success -> null
                    is UnzipResult.Cancelled -> context.getString(R.string.browser_operation_cancelled)
                    is UnzipResult.PasswordRequired -> context.getString(R.string.browser_enter_password)
                    is UnzipResult.WrongPassword -> context.getString(R.string.browser_wrong_password_retry)
                    is UnzipResult.CorruptedFile -> context.getString(R.string.browser_extract_failed_corrupt)
                    is UnzipResult.UnsupportedFormat -> context.getString(R.string.browser_extract_failed_format)
                    is UnzipResult.IOError -> result.message ?: context.getString(R.string.common_unknown_error)
                }
            }
        }
    }
}

private fun saveBatchEditableImageExif(
    context: Context,
    files: List<DocumentFileModel>,
    patch: EditableImageExif
): List<String> {
    if (files.isEmpty()) return emptyList()
    val errors = mutableListOf<String>()
    for (file in files) {
        val current = readEditableImageExif(context, file.uri)
        if (current == null) {
            errors += "${file.name}: read source failed (file missing or unreadable)"
            continue
        }
        val merged = mergeEditableImageExifForBatch(current, patch)
        val error = saveEditableImageExif(context, file.uri, merged)
        if (error != null) {
            errors += "${file.name}: $error"
        }
    }
    return errors
}
private suspend fun saveBatchEditableAudioAnnotation(
    context: Context,
    files: List<DocumentFileModel>,
    patch: EditableAudioAnnotation,
    config: BrowserConfigView
): List<String> {
    if (files.isEmpty()) return emptyList()
    val errors = mutableListOf<String>()
    for (file in files) {
        val current = readEditableAudioAnnotation(context, file.uri)
        val merged = mergeEditableAudioAnnotationForBatch(current, patch)
        val error = if (isMp3AudioFile(file.name, "")) {
            saveEditableAudioAnnotationToMp3(context, file.uri, merged)
        } else if (supportsJaudiotaggerWrite(file.name, "")) {
            saveEditableAudioAnnotationWithJaudiotagger(context, file.uri, merged, file.name)
        } else {
            runCatching {
                config.setAudioMetaAnnotation(
                    file.uri.toString(),
                    AudioMetaAnnotation(
                        title = merged.title,
                        artist = merged.artist,
                        album = merged.album
                    )
                )
            }.exceptionOrNull()?.let { e ->
                "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
            }
        }
        if (error != null) {
            errors += "${file.name}: $error"
        }
    }
    return errors
}

@Composable
private fun BatchPendingMetadataDialog(
    files: List<DocumentFileModel>,
    context: Context,
    config: BrowserConfigView,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val detailScope = rememberCoroutineScope()
    val batchKind = pendingBatchMetadataKind(files) ?: return
    val validatingFail = remember(files) { mutableStateOf<String?>(null) }
    var batchSaving by remember(files) { mutableStateOf(false) }
    val initialImage = remember(files) {
        if (batchKind == DetailMetadataKind.IMAGE) runCatching { readEditableImageExif(context, files.first().uri) }.getOrNull() ?: EditableImageExif() else null
    }
    val initialAudio = remember(files) {
        if (batchKind == DetailMetadataKind.AUDIO) runCatching { readEditableAudioAnnotation(context, files.first().uri) }.getOrElse { EditableAudioAnnotation() } else null
    }
    var imageForm by remember(files) { mutableStateOf(initialImage ?: EditableImageExif()) }
    var imageValidation by remember(files) { mutableStateOf(EditableImageExifValidation()) }
    var audioForm by remember(files) { mutableStateOf(initialAudio ?: EditableAudioAnnotation()) }
    var audioValidation by remember(files) { mutableStateOf(EditableAudioAnnotationValidation()) }

    LaunchedEffect(files, batchKind) {
        if (batchKind == DetailMetadataKind.IMAGE) {
            imageForm = withContext(Dispatchers.IO) { runCatching { readEditableImageExif(context, files.first().uri) }.getOrNull() ?: EditableImageExif() }
            imageValidation = EditableImageExifValidation()
        } else if (batchKind == DetailMetadataKind.AUDIO) {
            audioForm = withContext(Dispatchers.IO) { runCatching { readEditableAudioAnnotation(context, files.first().uri) }.getOrElse { EditableAudioAnnotation() } }
            audioValidation = EditableAudioAnnotationValidation()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!batchSaving) onDismiss() },
        title = {
            Text(
                when (batchKind) {
                    DetailMetadataKind.IMAGE -> stringResource(R.string.browser_type_specific_edit_image_meta_title)
                    DetailMetadataKind.AUDIO -> stringResource(R.string.browser_type_specific_edit_audio_meta_title)
                    else -> stringResource(R.string.context_details)
                }
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    context.getString(R.string.browser_type_specific_empty).takeIf { false } ?: "仅修改已填写的字段，空白项保持原值；未填写项不会被覆盖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                when (batchKind) {
                    DetailMetadataKind.IMAGE -> {
                        OutlinedTextField(
                            value = imageForm.takenTime,
                            onValueChange = { imageForm = imageForm.copy(takenTime = it); imageValidation = imageValidation.copy(takenTimeError = null) },
                            label = { Text(stringResource(R.string.browser_meta_taken_time_label)) },
                            isError = !imageValidation.takenTimeError.isNullOrBlank(),
                            supportingText = { imageValidation.takenTimeError?.let { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imageForm.artist,
                            onValueChange = { imageForm = imageForm.copy(artist = it); imageValidation = imageValidation.copy(artistError = null) },
                            label = { Text(stringResource(R.string.browser_meta_artist_label)) },
                            isError = !imageValidation.artistError.isNullOrBlank(),
                            supportingText = { imageValidation.artistError?.let { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imageForm.description,
                            onValueChange = { imageForm = imageForm.copy(description = it); imageValidation = imageValidation.copy(descriptionError = null) },
                            label = { Text(stringResource(R.string.browser_meta_description_label)) },
                            isError = !imageValidation.descriptionError.isNullOrBlank(),
                            supportingText = { imageValidation.descriptionError?.let { Text(it) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imageForm.software,
                            onValueChange = { imageForm = imageForm.copy(software = it); imageValidation = imageValidation.copy(softwareError = null) },
                            label = { Text(stringResource(R.string.browser_meta_software_label)) },
                            isError = !imageValidation.softwareError.isNullOrBlank(),
                            supportingText = { imageValidation.softwareError?.let { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = imageForm.copyright,
                            onValueChange = { imageForm = imageForm.copy(copyright = it); imageValidation = imageValidation.copy(copyrightError = null) },
                            label = { Text(stringResource(R.string.browser_meta_copyright_label)) },
                            isError = !imageValidation.copyrightError.isNullOrBlank(),
                            supportingText = { imageValidation.copyrightError?.let { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    DetailMetadataKind.AUDIO -> {
                        OutlinedTextField(
                            value = audioForm.title,
                            onValueChange = { audioForm = audioForm.copy(title = it); audioValidation = audioValidation.copy(titleError = null) },
                            label = { Text(stringResource(R.string.browser_meta_title_label)) },
                            isError = !audioValidation.titleError.isNullOrBlank(),
                            supportingText = { audioValidation.titleError?.let { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = audioForm.artist,
                            onValueChange = { audioForm = audioForm.copy(artist = it); audioValidation = audioValidation.copy(artistError = null) },
                            label = { Text(stringResource(R.string.browser_meta_artist_label)) },
                            isError = !audioValidation.artistError.isNullOrBlank(),
                            supportingText = { audioValidation.artistError?.let { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = audioForm.album,
                            onValueChange = { audioForm = audioForm.copy(album = it); audioValidation = audioValidation.copy(albumError = null) },
                            label = { Text(stringResource(R.string.browser_meta_album_label)) },
                            isError = !audioValidation.albumError.isNullOrBlank(),
                            supportingText = { audioValidation.albumError?.let { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {}
                }
                validatingFail.value?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (batchSaving) return@TextButton
                    val hasError = when (batchKind) {
                        DetailMetadataKind.IMAGE -> {
                            val validation = validateEditableImageExifForm(context, imageForm)
                            imageValidation = validation
                            validation.hasError
                        }
                        DetailMetadataKind.AUDIO -> {
                            val validation = validateEditableAudioAnnotationForm(context, audioForm)
                            audioValidation = validation
                            validation.hasError
                        }
                        else -> false
                    }
                    if (hasError) {
                        validatingFail.value = context.getString(R.string.browser_meta_fix_form_errors)
                        return@TextButton
                    }
                    validatingFail.value = null
                    detailScope.launch {
                        batchSaving = true
                        val errors = withContext(Dispatchers.IO) {
                            when (batchKind) {
                                DetailMetadataKind.IMAGE -> saveBatchEditableImageExif(context, files, imageForm)
                                DetailMetadataKind.AUDIO -> saveBatchEditableAudioAnnotation(context, files, audioForm, config)
                                else -> emptyList()
                            }
                        }
                        batchSaving = false
                        if (errors.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.browser_type_specific_save_success), Toast.LENGTH_SHORT).show()
                            onSaved()
                            onDismiss()
                        } else {
                            validatingFail.value = errors.joinToString("; ")
                        }
                    }
                },
                enabled = !batchSaving
            ) {
                Text(if (batchSaving) stringResource(R.string.browser_type_specific_saving) else stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!batchSaving) onDismiss() }, enabled = !batchSaving) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
/**
 * 文件浏览功能对象：目录会话、后退栈、待处理列表、面板与过滤开关的唯一所有者。
 * 其他模块需要目录信息或完成动作后的刷新，通过本对象的属性与方法访问。
 */
internal class BrowserTabState(
    val context: Context,
    val config: BrowserConfigView,
) {
    // ===== 目录会话（单一所有权）=====
    /** 当前根：查询根服务（唯一属地），本域不再持有镜像。 */
    val rootUri: String? get() = RootService.rootUri
    var currentUri by mutableStateOf<String?>(null)
        private set
    private val refreshRevision = mutableIntStateOf(0)
    val refreshKey: Int get() = refreshRevision.intValue

    fun refresh() {
        refreshRevision.intValue++
    }

    fun navigateTo(uri: String) {
        currentUri = uri
    }

    fun ensureCurrentUri(initialDirUri: String?) {
        if (rootUri == null) return
        val normalizedRoot = normalizeContentUriString(rootUri!!)
        val target = initialDirUri?.let { normalizeContentUriString(it) } ?: normalizedRoot
        if (currentUri == null) {
            currentUri = target
        } else if (!currentUri!!.startsWith(normalizedRoot)) {
            currentUri = target
        }
    }

    // ===== 后退栈 =====
    val backStack = mutableStateListOf<String>()
    val canGoBack: Boolean get() = backStack.isNotEmpty()

    fun goBack() {
        if (backStack.isNotEmpty()) currentUri = backStack.removeAt(backStack.lastIndex)
    }

    // ===== 待处理列表（跨模块共享选择模型）=====
    val pendingList = mutableStateListOf<DocumentFileModel>()

    fun clearPendingList() {
        pendingList.clear()
    }

    /** 批量功能收尾：清列表 + 关面板（各调用方自行追加刷新/切 tab 编排）。 */
    fun finishPendingList() {
        clearPendingList()
        showPendingList = false
    }

    // ===== 面板开关 =====
    var showPendingList by mutableStateOf(false)
    var showPendingDeleteConfirm by mutableStateOf(false)
    var showBatchRename by mutableStateOf(false)


    // ===== 过滤与外开偏好（prefs 镜像，Host 内 collect 接线）=====
    var filterVisible by mutableStateOf(true)
    var hideDotFiles by mutableStateOf(false)
    var preferredExternalPackages by mutableStateOf<Map<String, String>>(emptyMap())

    // ===== 批次详情对话框 =====
    var pendingBatchDetails by mutableStateOf<List<DocumentFileModel>?>(null)
}

/**
 * BrowserTabHost 的依赖引用：功能对象、宿主派生值与跨域出口回调。
 * 不持有状态所有权——状态全部在 BrowserTabState 内。
 */
internal class BrowserTabHostDependencies(
    val scope: CoroutineScope,
    val playbackState: PlaybackState?,
    val initialDirUri: String?,
    val cachedTrashUri: Uri?,
    val setTransientRoute: (TransientRoute?) -> Unit,
    val recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    // ---- 跨域出口（P4：命令式直呼目标域对象，不再经宿主回调中转）----
    val playerTab: PlayerTabFeature,
    // ---- 导航出口（经宿主：切 tab 是导航服务的判断）----
    val onForwardToBulletin: (BulletinForwardPayload) -> Unit,
    val onOpenQuickNote: () -> Unit,
    /** 通用切 tab 出口（导航服务）：会话句柄与宿主编排共用。 */
    val switchTab: (MainTab) -> Unit,
    // ---- 宿主编排能力 ----
    val runWithProgress: suspend (String, Int?, suspend ((Int) -> Unit) -> Unit) -> Unit,
    val runBatchOperation: (String, List<String>, (OperationRunState) -> Unit, suspend (OperationRunState) -> Unit) -> Unit,
    val operationTarget: OperationTargetConfigView,
)

@Composable
internal fun BrowserTabHost(
    state: BrowserTabState,
    deps: BrowserTabHostDependencies,
) {
    val context = state.context
    val scope = deps.scope
    val config = state.config
    val readingLauncher = remember(deps.setTransientRoute) {
        ReadingLauncher(deps.setTransientRoute)
    }
    val playbackState = deps.playbackState
    val initialDirUri = deps.initialDirUri
    val cachedTrashUri = deps.cachedTrashUri
    val setTransientRoute = deps.setTransientRoute
    val recordRecentOpen = deps.recordRecentOpen
    val playerTab = deps.playerTab
    val onForwardToBulletin = deps.onForwardToBulletin
    val onOpenQuickNote = deps.onOpenQuickNote
    val runWithProgress = deps.runWithProgress
    val runBatchOperation = deps.runBatchOperation
    // 目录会话别名（组合期间读取即订阅快照）
    val rootUri = state.rootUri
    val currentUri = state.currentUri
    val displayUri = currentUri ?: initialDirUri ?: rootUri!!
    val refreshKey = state.refreshKey
    fun refresh() = state.refresh()
    val backStack = state.backStack
    val pendingList = state.pendingList
    // 会话句柄（拆解设计 §2.1）：浏览器对外部动作开放的受限命令接口。
    // 功能域动作经它发收尾命令；实现只调域内既有方法，不新造状态。
    val sessionHandle = remember(state) {
        object : BrowserSessionHandle {
            override fun refresh() = state.refresh()
            override fun addToPending(item: DocumentFileModel) { state.pendingList.add(item) }
            override fun removeFromPending(item: DocumentFileModel) { state.pendingList.remove(item) }
            override fun switchTab(tab: MainTab) = deps.switchTab(tab)
            override fun openViewerRoute(route: TransientRoute) = setTransientRoute(route)
        }
    }
    // 能力声明注册（拆解设计 §2.2）：声明归各域，注册在组装层一行/域。
    // 重复 id 被注册表拒绝（Activity 重建安全）。
    remember(state) {
        val registry = BrowserActions.registry
        ArchiveReaderIntents.registerAll(registry)
        EpubReaderIntents.registerAll(registry)
        ViewerIntents.registerAll(registry)
        ArchiveOpsIntents.registerAll(registry)
        CryptoIntents.registerAll(registry)
        true
    }
    fun clearPendingList() = state.clearPendingList()
    var showPendingList by state::showPendingList
    var showPendingDeleteConfirm by state::showPendingDeleteConfirm
    var showBatchRename by state::showBatchRename
    var pendingBatchDetails by state::pendingBatchDetails
    var filterVisible by state::filterVisible
    var hideDotFiles by state::hideDotFiles
    var preferredExternalPackages by state::preferredExternalPackages

    // ---------------------------------------------------------------------
    // ZIP 编辑域：目标配置 + 加入/删除工作流 + 管理界面（域内闭环）
    // 目标为通用"操作目标文件"（zip 编辑是当前唯一使用者）；ZipManager 亦可
    // 从上下文菜单直接编辑任意 zip（会话式，不动目标配置）。
    // ---------------------------------------------------------------------
    val zipEditConfig = deps.operationTarget
    var zipEditCancelFlag by remember { mutableStateOf(false) }

    /** ZipManager 会话：fromConfig = 编辑配置目标（写回回写配置），否则为临时编辑 */
    data class ZipManagerSession(val uri: String, val name: String, val fromConfig: Boolean)
    var zipManagerSession by remember { mutableStateOf<ZipManagerSession?>(null) }
    var zipManagerRefresh by remember { mutableStateOf(0) }
    val zipEditTargetUriValue by zipEditConfig.operationTarget.collectAsState(initial = null)
    var zipEditTargetName by remember { mutableStateOf<String?>(null) }
    // 显示用：从应用根目录算起的目标路径（显示时再相对当前目录换算）；操作传参仍用 zipEditTargetName（文件名）
    var zipEditTargetRootPath by remember { mutableStateOf<String?>(null) }
    // 加密目标的会话内密码（不持久化）：按目标 URI 单槽缓存，验证失败或换目标即失效
    var zipEditPasswordFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(zipEditTargetUriValue) {
        val uri = zipEditTargetUriValue ?: run {
            zipEditTargetName = null
            zipEditTargetRootPath = null
            zipEditPasswordFor = null
            return@LaunchedEffect
        }
        if (zipEditPasswordFor?.first != uri) zipEditPasswordFor = null
        val name = withContext(Dispatchers.IO) {
            runCatching { DocumentFile.fromSingleUri(context, Uri.parse(uri))?.takeIf { it.exists() }?.name }.getOrNull()
        }
        if (name == null) {
            // 目标失效：发现即清（与最近列表残留治理同语义）
            zipEditConfig.setOperationTarget(null)
            Toast.makeText(context, context.getString(R.string.zip_edit_target_invalid), Toast.LENGTH_LONG).show()
        }
        zipEditTargetName = name
        // 路径换算失败（rootUri 为空、非本根 provider 等）时退回文件名
        zipEditTargetRootPath = if (name == null) null
        else withContext(Dispatchers.IO) {
            runCatching { pathFromRoot(context, rootUri, uri) }.getOrNull()?.takeIf { it.isNotEmpty() && !it.startsWith("content://") } ?: name
        }
    }

    fun beginZipEditExecution() {
        zipEditCancelFlag = false
        ZipEditModule.service.begin()
    }

    /**
     * 执行加入：targetUri 为本次编辑对象（配置目标或临时编辑的 zip）；
     * writeBackToConfig = 完成后把新 URI 回写目标配置（临时编辑不写）。
     */
    fun executeZipAdd(
        request: ZipAddRequest,
        targetUri: String,
        writeBackToConfig: Boolean,
        password: String? = zipEditPasswordFor?.takeIf { it.first == targetUri }?.second
    ) {
        beginZipEditExecution()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                addToZip(
                    context = context,
                    targetUri = Uri.parse(targetUri),
                    treeUri = rootUri?.let { Uri.parse(it) },
                    sourceUris = request.sources.map { it.uri },
                    setProgress = { stage, done, total, bytesDone, bytesTotal -> ZipEditModule.service.updateProgress(stage, done, total, bytesDone, bytesTotal) },
                    shouldCancel = { zipEditCancelFlag },
                    password = password?.toCharArray()
                )
            }
            when {
                result.needsPassword -> ZipEditModule.service.requestPassword(request.targetName)
                result.wrongPassword -> {
                    zipEditPasswordFor = null
                    ZipEditModule.service.requestPassword(request.targetName, wrongPrevious = true)
                }
                result.ok -> {
                    val newUriString = result.newTargetUri?.toString()
                    // 编辑写回后使该 zip 的打包阅读器解压缓存失效（新旧 URI 都清），防止查看旧内容
                    withContext(Dispatchers.IO) {
                        invalidateArchiveViewerCaches(context, Uri.parse(targetUri), result.newTargetUri ?: Uri.parse(targetUri))
                    }
                    ViewerSessionsModule.service.invalidateByUri(targetUri)
                    newUriString?.let { ViewerSessionsModule.service.invalidateByUri(it) }
                    if (newUriString != null && newUriString != targetUri) {
                        if (writeBackToConfig) zipEditConfig.setOperationTarget(newUriString)
                        zipManagerSession = zipManagerSession?.let { it.copy(uri = newUriString) }
                    }
                    if (result.addedEncrypted && password != null) {
                        // 密码缓存在写回后的新 URI 上（rename 后旧 URI 已失效）
                        zipEditPasswordFor = (newUriString ?: targetUri) to password
                    }
                    Toast.makeText(context, context.getString(R.string.zip_edit_result_ok), Toast.LENGTH_SHORT).show()
                    zipManagerRefresh++
                    ZipEditModule.service.dismissResult()
                }
                else -> ZipEditModule.service.finish(result)
            }
        }
    }

    fun executeZipRemove(entryNames: List<String>, session: ZipManagerSession) {
        beginZipEditExecution()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                removeZipEntries(
                    context = context,
                    targetUri = Uri.parse(session.uri),
                    treeUri = rootUri?.let { Uri.parse(it) },
                    entryNames = entryNames,
                    setProgress = { stage, done, total, bytesDone, bytesTotal -> ZipEditModule.service.updateProgress(stage, done, total, bytesDone, bytesTotal) },
                    shouldCancel = { zipEditCancelFlag }
                )
            }
            // 删除同样使打包阅读器缓存失效（新旧 URI 都清）
            withContext(Dispatchers.IO) {
                invalidateArchiveViewerCaches(context, Uri.parse(session.uri), result.newTargetUri ?: Uri.parse(session.uri))
            }
            ViewerSessionsModule.service.invalidateByUri(session.uri)
            result.newTargetUri?.let { ViewerSessionsModule.service.invalidateByUri(it.toString()) }
            if (result.ok && result.newTargetUri != null && result.newTargetUri.toString() != session.uri) {
                if (session.fromConfig) zipEditConfig.setOperationTarget(result.newTargetUri.toString())
                zipManagerSession = session.copy(uri = result.newTargetUri.toString())
            }
            if (result.ok) {
                zipManagerRefresh++
                Toast.makeText(context, context.getString(R.string.zip_edit_result_ok), Toast.LENGTH_SHORT).show()
                ZipEditModule.service.dismissResult()
            } else {
                ZipEditModule.service.finish(result)
            }
        }
    }

    /** 待解压的条目（密码环重试时取用）：会话 + 选中条目。 */
    var zipExtractPending by remember { mutableStateOf<Pair<ZipManagerSession, List<String>>?>(null) }

    /** 把选中条目解压到应用根目录：顶层同名自动改名；加密条目走密码环。 */
    fun executeZipExtract(
        entryNames: List<String>,
        session: ZipManagerSession,
        password: String? = null
    ) {
        val destDir = rootUri ?: run {
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
            return
        }
        beginZipEditExecution()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                extractZipEntries(
                    context = context,
                    zipUri = Uri.parse(session.uri),
                    treeUri = rootUri?.let { Uri.parse(it) },
                    destDirUri = Uri.parse(destDir),
                    entryNames = entryNames,
                    setProgress = { stage, done, total, bytesDone, bytesTotal -> ZipEditModule.service.updateProgress(stage, done, total, bytesDone, bytesTotal) },
                    shouldCancel = { zipEditCancelFlag },
                    password = password?.toCharArray()
                )
            }
            when {
                result.needsPassword -> {
                    zipExtractPending = session to entryNames
                    ZipEditModule.service.requestPassword(session.name)
                }
                result.wrongPassword -> {
                    zipExtractPending = session to entryNames
                    ZipEditModule.service.requestPassword(session.name, wrongPrevious = true)
                }
                result.ok -> {
                    zipExtractPending = null
                    state.refresh()
                    // 成功含改名明细走结果对话框（与其他解压反馈一致）
                    ZipEditModule.service.finish(result)
                }
                else -> ZipEditModule.service.finish(result)
            }
        }
    }

    /** 待执行加入的目标 URI（向导确认后取用；密码重试环亦据此重试） */
    var zipEditPendingTarget by remember { mutableStateOf<String?>(null) }

    /** 发起加入向导（确认框）：targetUri 为本次编辑对象。 */
    // SAF 同一文件可能以 document 形式或 tree 形式出现，字符串直等不可靠；用 documentId 归一化比较
    fun Uri.docKey(): String? = try { DocumentsContract.getDocumentId(this) } catch (_: Exception) { toString() }

    fun requestZipAdd(sources: List<DocumentFileModel>, targetUri: String, targetName: String) {
        // 拦截把目标 zip 自身加入自身：产物是过时快照嵌套，体积膨胀且无意义
        val targetKey = Uri.parse(targetUri).docKey()
        if (sources.any { it.uri.docKey() == targetKey }) {
            Toast.makeText(context, context.getString(R.string.zip_edit_self_add_blocked), Toast.LENGTH_LONG).show()
            return
        }
        ZipEditModule.service.requestAdd(sources, targetName)
        zipEditPendingTarget = targetUri
    }

    val fileListLazyState = rememberLazyListState()

    val copyMoveLog: ((String) -> Unit)? = null

    suspend fun openDirectoryImageGallery(
        currentItem: DocumentFileModel,
        currentDirUri: String,
        siblingImages: List<DocumentFileModel>
    ) {
        val normalizedImages = siblingImages
            .filter { !it.isDirectory && isGalleryImageFile(it.name) }
            .distinctBy { it.uri.toString() }
        if (normalizedImages.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.browser_no_images_in_dir), Toast.LENGTH_SHORT).show()
            return
        }
        val initialIndex = normalizedImages.indexOfFirst { it.uri == currentItem.uri }
        if (initialIndex < 0) {
            Toast.makeText(context, context.getString(R.string.browser_image_position_failed, currentItem.name), Toast.LENGTH_SHORT).show()
            return
        }
        val galleryRoot = File(context.cacheDir, "pic_dir_gallery")
        val galleryKey = currentDirUri.hashCode().toUInt().toString(16)
        val cacheDir = File(galleryRoot, galleryKey)
        val contentDir = File(cacheDir, "content")
        withContext(Dispatchers.IO) {
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
            contentDir.mkdirs()
            normalizedImages.forEachIndexed { index, model ->
                val targetFile = File(contentDir, model.name)
                val bytes = context.contentResolver.openInputStreamSafe(model.uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException(context.getString(R.string.browser_image_read_failed, index + 1, model.name, model.uri))
                targetFile.outputStream().use { it.write(bytes) }
            }
            File(cacheDir, ".last_index").writeText(initialIndex.toString())
        }
        val dirTitle = DocumentFile.fromTreeUri(context, Uri.parse(currentDirUri))?.name
            ?: DocumentFile.fromSingleUri(context, Uri.parse(currentDirUri))?.name
            ?: currentItem.name
        ArchiveReaderModule.service.openViewer(
            PicZipViewState(
                contentDir = contentDir,
                imagePaths = normalizedImages.map { it.name },
                zipFileName = dirTitle,
                zipUri = currentItem.uri,
                isEncrypted = false,
                password = null,
                initialIndex = initialIndex,
                recordRecent = false,
                cleanupCacheDir = cacheDir
            )
        )
    }

    // 文件浏览派生状态（原宿主平移）
        var currentDirPath by remember { mutableStateOf("") }
        LaunchedEffect(displayUri, rootUri) {
            currentDirPath = withContext(Dispatchers.IO) {
                pathFromRoot(context, rootUri, displayUri)
            }
        }

        var dirCache by remember { mutableStateOf<Map<String, CachedDir>>(emptyMap()) }
        LaunchedEffect(rootUri) {
            dirCache = emptyMap()
        }

        val doCopyHere: () -> Unit = {
            doCopyPendingToCurrentDir(
                context = context,
                displayUri = displayUri,
                pendingList = pendingList.toList(),
                rootUri = rootUri,
                copyMoveLog = copyMoveLog,
                runBatchOperation = runBatchOperation,
                onSuccessAll = {
                    clearPendingList()
                    showPendingList = false
                },
                onRefreshDone = { refresh() }
            )
        }
        val doMoveHere: () -> Unit = {
            doMovePendingToCurrentDir(
                context = context,
                displayUri = displayUri,
                pendingList = pendingList.toList(),
                rootUri = rootUri,
                copyMoveLog = copyMoveLog,
                runBatchOperation = runBatchOperation,
                onSuccessAll = {
                    clearPendingList()
                    showPendingList = false
                },
                onRefreshDone = { refresh() }
            )
        }
        val doExtractArchivesHere: () -> Unit = {
            doExtractPendingArchivesToCurrentDir(
                context = context,
                displayUri = displayUri,
                pendingListSnapshot = pendingList.toList(),
                rootUri = rootUri,
                runBatchOperation = runBatchOperation,
                onAllSuccess = {
                    pendingList.removeAll { !it.isDirectory && isExtractableArchive(it.name) }
                    if (pendingList.isEmpty()) {
                        showPendingList = false
                    }
                },
                onRefreshDone = { refresh() }
            )
        }

    // —— 待处理面板（原宿主平移；显示开关 showPendingList 为宿主持有的 Route 事实）——
                        BrowserTabRoute(
                            BrowserTabRouteState(
                                modifier = Modifier.fillMaxSize(),
                                config = config,
                                session = sessionHandle,
                                currentUri = displayUri,
                                refreshTrigger = refreshKey,
                                dirCache = dirCache,
                                onCacheDir = { uri, items -> dirCache = dirCache + (uri to CachedDir(items)) },
                                pendingList = pendingList,
                                rootUri = rootUri,
                                listState = fileListLazyState,
                                onNavigate = { uri ->
                                    backStack.add(displayUri)
                                    state.navigateTo(normalizeContentUriString(uri))
                                },
                                onBack = {
                                    if (backStack.isNotEmpty()) {
                                        state.goBack()
                                    }
                                },
                                canGoBack = state.canGoBack,
                                onEmptyTrash = rootUri?.let { r ->
                                    {
                                        scope.launch {
                                            val root = Uri.parse(normalizeContentUriString(r))
                                            runWithProgress(context.getString(R.string.main_menu_empty_trash), null) { _ ->
                                                val ok = withContext(Dispatchers.IO) { emptyTrash(context, root, root) }
                                                if (ok) {
                                                    Toast.makeText(context, context.getString(R.string.browser_trash_emptied), Toast.LENGTH_SHORT).show()
                                                    refresh()
                                                } else {
                                                    Toast.makeText(context, context.getString(R.string.browser_empty_trash_failed), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                onChangeRoot = RootSwitchModule.service::open,
                                onRestoreFromTrash = rootUri?.let { r ->
                                    { model ->
                                        scope.launch {
                                            val root = Uri.parse(normalizeContentUriString(r))
                                            runWithProgress(context.getString(R.string.context_restore), null) { _ ->
                                                val ok = withContext(Dispatchers.IO) { restoreFromTrash(context, model.uri, root, root) }
                                                if (ok) {
                                                    Toast.makeText(context, context.getString(R.string.browser_restored_to_root), Toast.LENGTH_SHORT).show()
                                                    refresh()
                                                } else {
                                                    Toast.makeText(context, context.getString(R.string.browser_restore_failed), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                filterVisible = filterVisible,
                                hideDotFiles = hideDotFiles,
                                isViewingTrash = cachedTrashUri != null && (displayUri == cachedTrashUri.toString() || isInsideDirectory(Uri.parse(displayUri), cachedTrashUri!!)),
                                preferredExternalPackages = preferredExternalPackages,
                                onOpenFile = { uri, name, _ ->
                                    setTransientRoute(TransientRoute.Viewer(uri, name, false))
                                },
                                onOpenWithOtherApp = { uri, name, packageName, rememberChoice, forcedMimeType ->
                                    val opened = launchExternalOpen(context, uri, packageName, name, forcedMimeType)
                                    if (opened) {
                                        recordRecentOpen(
                                            RECENT_TYPE_EXTERNAL_OPEN,
                                            uri,
                                            name,
                                            uri,
                                            null
                                        )
                                        if (rememberChoice) {
                                            val extension = fileExtensionKey(name)
                                            if (extension != null && !packageName.isNullOrBlank()) {
                                                scope.launch { config.setExternalOpenPackageForExtension(extension, packageName) }
                                            }
                                        }
                                    } else {
                                        val extension = fileExtensionKey(name)
                                        if (!packageName.isNullOrBlank() && extension != null) {
                                            scope.launch { config.clearExternalOpenPackageForExtension(extension) }
                                            Toast.makeText(context, context.getString(R.string.browser_external_app_unavailable), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.browser_no_app_to_open), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    opened
                                },
                                onClearExternalOpenPreference = { name ->
                                    val extension = fileExtensionKey(name)
                                    if (extension != null) {
                                        scope.launch { config.clearExternalOpenPackageForExtension(extension) }
                                    }
                                },
                                onAddToPendingList = { item -> pendingList.add(item) },
                                onRemoveFromPendingList = { item -> pendingList.remove(item) },
                                onCopyPendingToCurrentDir = doCopyHere,
                                onMovePendingToCurrentDir = doMoveHere,
                                onShowPendingList = { show -> showPendingList = show },
                                onRefresh = { refresh() },
                                onCreateQuickNote = {
                                    onOpenQuickNote()
                                },
                                onCreateMusicPlaylist = playerTab::requestCreateDirectoryPlaylist,
                                onRequestGpgEncrypt = { fileModel, dirUri ->
                                    CryptoModule.service.open(CryptoRequest.Gpg(GpgOpState.Encrypt(fileModel, dirUri)))
                                },
                                onRequestQuickMask = QuickObfuscateModule.service::requestQuick,
                                onConfirmDelete = { model, deletePermanently ->
                                    scope.launch {
                                        val label = if (deletePermanently) context.getString(R.string.common_delete) else context.getString(R.string.browser_moved_to_trash)
                                        runWithProgress(label, null) { _ ->
                                            val ok = withContext(Dispatchers.IO) {
                                                if (deletePermanently) {
                                                    context.contentResolver.deleteDocument(model.uri)
                                                } else {
                                                    val root = rootUri?.let { Uri.parse(normalizeContentUriString(it)) } ?: return@withContext false
                                                    moveToTrash(context, model.uri, root, root)
                                                }
                                            }
                                            if (ok) {
                                                Toast.makeText(context, if (deletePermanently) context.getString(R.string.browser_deleted) else context.getString(R.string.browser_moved_to_trash), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.browser_operation_failed), Toast.LENGTH_SHORT).show()
                                            }
                                            refresh()
                                        }
                                    }
                                },
                                onForwardToBulletin = { items ->
                                    if (items.isEmpty()) return@BrowserTabRouteState
                                    onForwardToBulletin(BulletinForwardPayload.Files(items))
                                },
                                onRequestOpenUrl = readingLauncher::htmlUrl,
                                onRequestDirectoryImageView = { item, dirUri, siblings ->
                                    scope.launch {
                                        runCatching {
                                            openDirectoryImageGallery(item, dirUri, siblings)
                                        }.onFailure { error ->
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.browser_open_dir_images_failed, error.message ?: error.javaClass.simpleName),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                onCompressArchiveRequest = { ArchiveOpsModule.service.open(ArchiveOperationRequest.CompressFile(it)) },
                                onRequestPassProtect = { model -> CryptoModule.service.open(CryptoRequest.PassProtect(model)) },
                                onRequestPassEdit = { model, dirUri -> CryptoModule.service.open(CryptoRequest.PassEdit(model, dirUri)) },
                                onRequestImportConfig = { model ->
                                    ConfigImportExportModule.service.startImportFromUri(model.uri)
                                },
                                onRequestImportStarDict = { model ->
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            importStarDict(context, model.uri, model.name)
                                        }
                                        result.onSuccess {
                                            val message = if (it.evicted != null) {
                                                context.getString(R.string.browser_dict_imported_evicted, it.imported.name, it.imported.wordCount, it.evicted.name)
                                            } else {
                                                context.getString(R.string.browser_dict_imported, it.imported.name, it.imported.wordCount)
                                            }
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }.onFailure {
                                            Toast.makeText(context, context.getString(R.string.browser_dict_import_failed, it.message ?: context.getString(R.string.common_unknown_error)), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                playbackState = playbackState,
                                onOpenPlaybackScreen = { deps.switchTab(MainTab.PLAYER) },
                                zipEditTargetUri = zipEditTargetUriValue,
                                zipEditTargetName = zipEditTargetRootPath?.let { relativePathFromCurrentDir(it, currentDirPath) } ?: zipEditTargetName,
                                zipEditTargetIsZip = zipEditTargetName?.endsWith(".zip", ignoreCase = true) == true,
                                onSetZipEditTarget = { model ->
                                    scope.launch {
                                        zipEditConfig.setOperationTarget(model.uri.toString())
                                        Toast.makeText(context, context.getString(R.string.zip_edit_target_set, model.name), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onAddToZipEditTarget = { model ->
                                    val targetUri = zipEditTargetUriValue
                                    val targetName = zipEditTargetName
                                    if (targetUri == null || targetName == null) {
                                        Toast.makeText(context, context.getString(R.string.zip_edit_no_target), Toast.LENGTH_LONG).show()
                                    } else {
                                        requestZipAdd(listOf(model), targetUri, targetName)
                                    }
                                },
                                onOpenZipManager = {
                                    val targetUri = zipEditTargetUriValue
                                    val targetName = zipEditTargetName
                                    if (targetUri == null || targetName == null) {
                                        Toast.makeText(context, context.getString(R.string.zip_edit_no_target), Toast.LENGTH_LONG).show()
                                    } else {
                                        zipManagerSession = ZipManagerSession(targetUri, targetName, fromConfig = true)
                                    }
                                },
                                onEditZipContents = { model ->
                                    zipManagerSession = ZipManagerSession(model.uri.toString(), model.name, fromConfig = false)
                                },
                                onClearZipEditTarget = {
                                    scope.launch {
                                        zipEditConfig.setOperationTarget(null)
                                        Toast.makeText(context, context.getString(R.string.zip_edit_target_cleared), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        )
        if (showPendingList) {
            PendingListScreen(
                pendingList = pendingList,
                currentDirPath = currentDirPath,
                rootUri = rootUri,
                zipEditTargetName = zipEditTargetRootPath?.let { relativePathFromCurrentDir(it, currentDirPath) } ?: zipEditTargetName,
                onShowBatchDetails = { pendingBatchDetails = pendingList.toList().takeIf { pendingBatchMetadataKind(it) != null } },
                onRemove = { pendingList.remove(it) },
                onCopyHere = doCopyHere,
                onMoveHere = doMoveHere,
                onExtractArchivesHere = doExtractArchivesHere,
                onRequestDelete = { showPendingDeleteConfirm = true },
                onRequestForwardToBulletin = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        onForwardToBulletin(BulletinForwardPayload.Files(pendingList.toList()))
                    }
                },
                onRequestBatchMask = {
                    val list = pendingList.filter { !it.isDirectory }
                    if (list.isEmpty()) Toast.makeText(context, context.getString(R.string.browser_no_mask_candidates), Toast.LENGTH_SHORT).show()
                    else QuickObfuscateModule.service.requestBatch(list)
                },
                onRequestBatchGpgEncrypt = {
                    if (pendingList.any { it.name.endsWith(".gpg", ignoreCase = true) }) {
                        Toast.makeText(context, context.getString(R.string.browser_batch_encrypt_has_gpg), Toast.LENGTH_SHORT).show()
                    } else {
                        val list = pendingList.filter { !it.isDirectory }
                        if (list.isEmpty()) Toast.makeText(context, context.getString(R.string.browser_no_encrypt_candidates), Toast.LENGTH_SHORT).show()
                        else {
                            CryptoModule.service.open(CryptoRequest.Gpg(GpgOpState.BatchEncrypt(list, displayUri)))
                        }
                    }
                },
                onRequestBatchGpgDecrypt = {
                    if (pendingList.any { !it.name.endsWith(".gpg", ignoreCase = true) }) {
                        Toast.makeText(context, context.getString(R.string.browser_batch_decrypt_has_non_gpg), Toast.LENGTH_SHORT).show()
                    } else {
                        val list = pendingList.filter { !it.isDirectory }
                        if (list.isEmpty()) Toast.makeText(context, context.getString(R.string.browser_no_decrypt_candidates), Toast.LENGTH_SHORT).show()
                        else {
                            CryptoModule.service.open(CryptoRequest.Gpg(GpgOpState.BatchDecrypt(list, displayUri)))
                        }
                    }
                },
                onRequestBatchRename = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        showBatchRename = true
                    }
                },
                onRequestCompressArchive = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        ArchiveOpsModule.service.open(ArchiveOperationRequest.CompressPending(pendingList.toList(), null, ""))
                    }
                },
                onRequestCompressToZip = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        ArchiveOpsModule.service.open(ArchiveOperationRequest.CompressPending(pendingList.toList(), ArchiveCompressionFormat.ZIP, "archive_$ts.zip"))
                    }
                },
                onRequestCompressTo7z = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        ArchiveOpsModule.service.open(ArchiveOperationRequest.CompressPending(pendingList.toList(), ArchiveCompressionFormat.SEVEN_Z, "archive_$ts.7z"))
                    }
                },
                onClearFilteredList = { toRemove ->
                    val snapshot = toRemove.toList()
                    pendingList.removeAll(snapshot)
                },
                onAddToPlayback = playerTab::requestAddToPlayback,
                onAddToZipEditTarget = if (zipEditTargetName?.endsWith(".zip", ignoreCase = true) == true) {
                    { items ->
                        val targetUri = zipEditTargetUriValue
                        val targetName = zipEditTargetName
                        if (targetUri == null || targetName == null) {
                            Toast.makeText(context, context.getString(R.string.zip_edit_no_target), Toast.LENGTH_LONG).show()
                        } else {
                            requestZipAdd(items, targetUri, targetName)
                        }
                    }
                } else null,
                onDismiss = { showPendingList = false }
            )
        }
        ZipEditDialogs(
            workflow = ZipEditModule.service,
            onConfirmAdd = {
                val request = ZipEditModule.service.addRequest ?: return@ZipEditDialogs
                val targetUri = zipEditPendingTarget ?: zipEditTargetUriValue ?: return@ZipEditDialogs
                val writeBack = targetUri == zipEditTargetUriValue
                executeZipAdd(request, targetUri, writeBack)
            },
            onCancelProgress = { zipEditCancelFlag = true },
            onSubmitPassword = { pw ->
                val request = ZipEditModule.service.addRequest
                if (request != null) {
                    val targetUri = zipEditPendingTarget ?: zipEditTargetUriValue ?: return@ZipEditDialogs
                    val writeBack = targetUri == zipEditTargetUriValue
                    executeZipAdd(request, targetUri, writeBack, pw)
                } else {
                    val (session, entries) = zipExtractPending ?: return@ZipEditDialogs
                    executeZipExtract(entries, session, pw)
                }
            }
        )
        zipManagerSession?.let { session ->
            ZipManagerScreen(
                state = ZipManagerRouteState(
                    targetUri = session.uri,
                    targetName = session.name,
                    treeUri = rootUri,
                    refreshTrigger = zipManagerRefresh,
                    onRemoveEntries = { names -> executeZipRemove(names, session) },
                    onExtractEntries = { names -> executeZipExtract(names, session) },
                    onAddFiles = { uris ->
                        // SAF 选择器返回的文件解析为模型后走加入向导（IO 查询显示名）
                        scope.launch {
                            val models = withContext(Dispatchers.IO) {
                                uris.mapNotNull { u ->
                                    runCatching {
                                        val doc = DocumentFile.fromSingleUri(context, u) ?: return@runCatching null
                                        val name = doc.name ?: return@runCatching null
                                        DocumentFileModel(
                                            name = name,
                                            isDirectory = false,
                                            uri = u,
                                            lastModified = 0L,
                                            size = doc.length()
                                        )
                                    }.getOrNull()
                                }
                            }
                            if (models.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.zip_edit_copy_source_failed), Toast.LENGTH_SHORT).show()
                            } else {
                                requestZipAdd(models, session.uri, session.name)
                            }
                        }
                    },
                    onDismiss = { zipManagerSession = null }
                )
            )
        }
        pendingBatchDetails?.let { files ->
            BatchPendingMetadataDialog(
                files = files,
                context = context,
                config = config,
                onDismiss = { pendingBatchDetails = null },
                onSaved = { pendingBatchDetails = null }
            )
        }
        if (showBatchRename && pendingList.isNotEmpty()) {
            val renameTargets = pendingList.toList()
            BatchRenameDialog(
                items = renameTargets,
                onDismiss = { showBatchRename = false },
                onConfirm = { pairs ->
                    showBatchRename = false
                    // E2：执行段交全屏执行页，成功/失败与失败原因逐项展示
                    runBatchOperation(
                        context.getString(R.string.batch_rename_progress),
                        pairs.map { it.first.name },
                        { _ ->
                            refresh()
                            state.finishPendingList()
                        }
                    ) { state ->
                        state.runEach(pairs) { (model, newName) ->
                            withContext(Dispatchers.IO) {
                                context.contentResolver.renameDocumentResult(model.uri, newName).fold(
                                    onSuccess = { null },
                                    onFailure = { e ->
                                        when (e) {
                                            is FileNotFoundException, is IllegalArgumentException ->
                                                context.getString(R.string.batch_rename_err_not_found)
                                            else -> e.message?.takeIf { it.isNotBlank() }
                                                ?: context.getString(R.string.batch_rename_err_failed)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }

        if (showPendingDeleteConfirm && pendingList.isNotEmpty()) {
            val toDelete = pendingList.toList()
            PendingDeleteConfirmDialog(
                items = toDelete,
                hasRoot = rootUri != null,
                onDismiss = { showPendingDeleteConfirm = false },
                onConfirm = { deletePermanently ->
                    showPendingDeleteConfirm = false
                    val label = if (deletePermanently) context.getString(R.string.common_delete) else context.getString(R.string.browser_moved_to_trash)
                    val root = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                    // E2：执行段交全屏执行页（并补强逐项失败检测；原实现不感知单项失败）
                    runBatchOperation(
                        label,
                        toDelete.map { it.name },
                        { _ ->
                            refresh()
                            state.finishPendingList()
                        }
                    ) { state ->
                        state.runEach(toDelete) { model ->
                            withContext(Dispatchers.IO) {
                                if (deletePermanently) {
                                    if (context.contentResolver.deleteDocument(model.uri)) null else ""
                                } else {
                                    if (root != null && moveToTrash(context, model.uri, root, root)) null else ""
                                }
                            }
                        }
                    }
                }
            )
        }

}