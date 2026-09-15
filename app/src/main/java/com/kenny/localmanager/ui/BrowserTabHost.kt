package com.kenny.localmanager.ui

import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.kenny.localmanager.data.AudioMetaAnnotation
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import android.widget.Toast
import com.kenny.localmanager.R
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.copyDocumentTo
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.emptyTrash
import com.kenny.localmanager.file.isInsideDirectory
import com.kenny.localmanager.file.moveToTrash
import com.kenny.localmanager.file.restoreFromTrash
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.moveDocumentTo
import com.kenny.localmanager.file.ArchiveCompressionFormat
import com.kenny.localmanager.file.unzipToParent
import com.kenny.localmanager.file.UnzipResult
import com.kenny.localmanager.player.PlaybackState
import com.kenny.localmanager.dict.importStarDict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 文件浏览模块（《主界面 AppShell 分层设计》Step 2）：目录浏览、待处理面板、批次确认的组合宿主。
// 导航开关（showPendingList 等 Route 事实）由宿主持有、经 state 注入；内容实现全部内聚本文件。
// 批量操作的发起经共享对象直呼（archiveOps/crypto...），跨域弹窗请求经出口回调上交宿主。

// 批量操作执行 helper（原 FileBrowserApp 顶层平移）
private fun doCopyPendingToCurrentDir(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    displayUri: String,
    pendingList: List<DocumentFileModel>,
    rootUri: String?,
    copyMoveLog: ((String) -> Unit)?,
    runWithProgress: RunProgressBlock,
    onSuccessAll: () -> Unit,
    onRefreshDone: () -> Unit
) {
    val targetDirUri = normalizeContentUriString(displayUri)
    copyMoveLog?.invoke(context.getString(R.string.browser_log_copy_target, targetDirUri))
    val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
    scope.launch {
        runWithProgress(context.getString(R.string.pending_list_action_copy), pendingList.size) { setProgress ->
            val (ok, fail) = withContext(Dispatchers.IO) {
                var o = 0
                var f = 0
                pendingList.forEachIndexed { index, model ->
                    if (copyDocumentTo(context, model.uri, Uri.parse(targetDirUri), treeUri, copyMoveLog) != null) o++
                    else f++
                    withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                }
                Pair(o, f)
            }
            copyMoveLog?.invoke(context.getString(R.string.browser_log_copy_result, ok, fail))
            if (fail == 0 && ok > 0) {
                onSuccessAll()
                Toast.makeText(context, context.getString(R.string.browser_copy_all_ok, ok), Toast.LENGTH_SHORT).show()
            } else if (ok > 0) {
                Toast.makeText(context, context.getString(R.string.browser_copy_partial, ok, fail), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.browser_copy_failed), Toast.LENGTH_SHORT).show()
            }
            onRefreshDone()
        }
    }
}

private fun doMovePendingToCurrentDir(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    displayUri: String,
    pendingList: List<DocumentFileModel>,
    rootUri: String?,
    copyMoveLog: ((String) -> Unit)?,
    runWithProgress: RunProgressBlock,
    onSuccessAll: () -> Unit,
    onRefreshDone: () -> Unit
) {
    val targetDirUri = normalizeContentUriString(displayUri)
    copyMoveLog?.invoke(context.getString(R.string.browser_log_move_target, targetDirUri))
    val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
    scope.launch {
        runWithProgress(context.getString(R.string.pending_list_action_move), pendingList.size) { setProgress ->
            val (ok, fail) = withContext(Dispatchers.IO) {
                var o = 0
                var f = 0
                pendingList.forEachIndexed { index, model ->
                    if (moveDocumentTo(context, model.uri, Uri.parse(targetDirUri), treeUri, copyMoveLog)) o++
                    else f++
                    withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                }
                Pair(o, f)
            }
            copyMoveLog?.invoke(context.getString(R.string.browser_log_move_result, ok, fail))
            if (fail == 0 && ok > 0) {
                onSuccessAll()
                Toast.makeText(context, context.getString(R.string.browser_move_all_ok, ok), Toast.LENGTH_SHORT).show()
            } else if (ok > 0) {
                Toast.makeText(context, context.getString(R.string.browser_move_partial, ok, fail), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.browser_move_failed), Toast.LENGTH_SHORT).show()
            }
            onRefreshDone()
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
    scope: kotlinx.coroutines.CoroutineScope,
    displayUri: String,
    pendingListSnapshot: List<DocumentFileModel>,
    rootUri: String?,
    runWithProgress: RunProgressBlock,
    setCancelAction: (((() -> Unit)?) -> Unit)? = null,
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
    scope.launch {
        val cancelFlag = OperationCancelFlag()
        setCancelAction?.invoke { cancelFlag.requestCancel() }
        try {
            runWithProgress(context.getString(R.string.browser_op_extract), extractTargets.size) { setProgress ->
                var successCount = 0
                var failedCount = 0
                val failedDetails = mutableListOf<String>()
                val passwordRequiredNames = mutableListOf<String>()

                extractTargets.forEachIndexed { index, target ->
                    val result = withContext(Dispatchers.IO) {
                        unzipToParent(
                            context = context,
                            zipUri = target.model.uri,
                            parentDirUri = targetDirUri,
                            treeUri = treeUri,
                            archiveName = target.model.name,
                            password = null,
                            setProgress = { _, _ -> },
                            shouldCancel = { cancelFlag.isCancelled() }
                        )
                    }
                    when (result) {
                        is UnzipResult.Success -> successCount++
                        is UnzipResult.Cancelled -> {
                            Toast.makeText(context, context.getString(R.string.browser_operation_cancelled), Toast.LENGTH_SHORT).show()
                            onRefreshDone()
                            return@runWithProgress
                        }
                        is UnzipResult.PasswordRequired,
                        is UnzipResult.WrongPassword -> {
                            failedCount++
                            passwordRequiredNames += target.model.name
                        }
                        is UnzipResult.CorruptedFile -> {
                            failedCount++
                            failedDetails += "${target.model.name}: ${context.getString(R.string.browser_extract_failed_corrupt)}"
                        }
                        is UnzipResult.UnsupportedFormat -> {
                            failedCount++
                            failedDetails += "${target.model.name}: ${context.getString(R.string.browser_extract_failed_format)}"
                        }
                        is UnzipResult.IOError -> {
                            failedCount++
                            val msg = result.message ?: context.getString(R.string.common_unknown_error)
                            failedDetails += "${target.model.name}: $msg"
                        }
                    }
                    setProgress(index + 1)
                }

                if (passwordRequiredNames.isNotEmpty()) {
                    failedDetails += context.getString(
                        R.string.browser_extract_batch_password_required,
                        passwordRequiredNames.joinToString(",")
                    )
                }

                if (failedCount == 0 && successCount > 0) {
                    onAllSuccess()
                    Toast.makeText(
                        context,
                        context.getString(R.string.browser_extract_batch_done, successCount),
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (successCount > 0) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.browser_extract_batch_partial, successCount, failedCount),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.browser_extract_batch_failed), Toast.LENGTH_SHORT).show()
                }

                if (failedDetails.isNotEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.browser_extract_failed_detail, failedDetails.joinToString("; ")),
                        Toast.LENGTH_LONG
                    ).show()
                }
                onRefreshDone()
            }
        } finally {
            setCancelAction?.invoke(null)
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
        val merged = mergeEditableImageExifForBatch(current, patch)
        val error = saveEditableImageExif(context, file.uri, merged)
        if (error != null) {
            errors += "${file.name}: $error"
        }
    }
    return errors
}
private fun saveBatchEditableAudioAnnotation(
    context: Context,
    files: List<DocumentFileModel>,
    patch: EditableAudioAnnotation,
    prefs: Preferences
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
                runBlocking {
                    prefs.setAudioMetaAnnotation(
                        file.uri.toString(),
                        AudioMetaAnnotation(
                            title = merged.title,
                            artist = merged.artist,
                            album = merged.album
                        )
                    )
                }
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
    prefs: Preferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val detailScope = rememberCoroutineScope()
    val batchKind = pendingBatchMetadataKind(files) ?: return
    val validatingFail = remember(files) { mutableStateOf<String?>(null) }
    var batchSaving by remember(files) { mutableStateOf(false) }
    val initialImage = remember(files) {
        if (batchKind == DetailMetadataKind.IMAGE) runCatching { readEditableImageExif(context, files.first().uri) }.getOrElse { EditableImageExif() } else null
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
            imageForm = withContext(Dispatchers.IO) { runCatching { readEditableImageExif(context, files.first().uri) }.getOrElse { EditableImageExif() } }
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
                                DetailMetadataKind.AUDIO -> saveBatchEditableAudioAnnotation(context, files, audioForm, prefs)
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
    val prefs: Preferences,
) {
    // ===== 目录会话（单一所有权）=====
    var rootUri by mutableStateOf<String?>(null)
        private set
    var currentUri by mutableStateOf<String?>(null)
        private set
    private val refreshRevision = mutableIntStateOf(0)
    val refreshKey: Int get() = refreshRevision.intValue

    fun refresh() {
        refreshRevision.intValue++
    }

    suspend fun restoreRoot() {
        rootUri = prefs.rootUri.first()?.let { normalizeContentUriString(it) }
    }

    fun changeRoot(uri: String, refresh: Boolean = true) {
        val normalized = normalizeContentUriString(uri)
        rootUri = normalized
        currentUri = normalized
        if (refresh) refresh()
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
    val archiveReader: ArchiveReaderWorkflowState,
    val archiveOps: ArchiveOperationWorkflowState,
    val crypto: CryptoWorkflowState,
    val configImportExportWorkflow: ConfigImportExportWorkflowState,
    val markdownViewerSessionCache: MarkdownViewerSessionCache,
    val playbackState: PlaybackState?,
    val initialDirUri: String?,
    val cachedTrashUri: Uri?,
    val finishPendingListAndReturn: () -> Unit,
    val setTransientRoute: (TransientRoute?) -> Unit,
    val recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    val onChangeRoot: () -> Unit,
    val onShowFileDetail: (DocumentFileModel) -> Unit,
    val onQuickMask: (DocumentFileModel) -> Unit,
    val onShareToGit: (DocumentFileModel) -> Unit,
    val onForwardToBulletin: (BulletinForwardPayload) -> Unit,
    val onOpenQuickNote: () -> Unit,
    val requestOpenPlayerTab: () -> Unit,
    val runWithProgress: suspend (String, Int?, suspend ((Int) -> Unit) -> Unit) -> Unit,
    val onSetProgressCancel: ((() -> Unit)?) -> Unit,
    val onBatchMask: (List<DocumentFileModel>) -> Unit,
    val onAddToPlayback: (List<DocumentFileModel>) -> Unit,
)

@Composable
internal fun BrowserTabHost(
    state: BrowserTabState,
    deps: BrowserTabHostDependencies,
) {
    val context = state.context
    val scope = deps.scope
    val prefs = state.prefs
    val archiveReader = deps.archiveReader
    val archiveOps = deps.archiveOps
    val crypto = deps.crypto
    val configImportExportWorkflow = deps.configImportExportWorkflow
    val markdownViewerSessionCache = deps.markdownViewerSessionCache
    val playbackState = deps.playbackState
    val initialDirUri = deps.initialDirUri
    val cachedTrashUri = deps.cachedTrashUri
    val finishPendingListAndReturn = deps.finishPendingListAndReturn
    val setTransientRoute = deps.setTransientRoute
    val recordRecentOpen = deps.recordRecentOpen
    val onChangeRoot = deps.onChangeRoot
    val onShowFileDetail = deps.onShowFileDetail
    val onQuickMask = deps.onQuickMask
    val onShareToGit = deps.onShareToGit
    val onForwardToBulletin = deps.onForwardToBulletin
    val onOpenQuickNote = deps.onOpenQuickNote
    val requestOpenPlayerTab = deps.requestOpenPlayerTab
    val runWithProgress = deps.runWithProgress
    val onSetProgressCancel = deps.onSetProgressCancel
    val onBatchMask = deps.onBatchMask
    val onAddToPlayback = deps.onAddToPlayback
    // 目录会话别名（组合期间读取即订阅快照）
    val rootUri = state.rootUri
    val currentUri = state.currentUri
    val displayUri = currentUri ?: initialDirUri ?: rootUri!!
    val refreshKey = state.refreshKey
    fun refresh() = state.refresh()
    val backStack = state.backStack
    val pendingList = state.pendingList
    fun clearPendingList() = state.clearPendingList()
    var showPendingList by state::showPendingList
    var showPendingDeleteConfirm by state::showPendingDeleteConfirm
    var pendingBatchDetails by state::pendingBatchDetails
    var filterVisible by state::filterVisible
    var hideDotFiles by state::hideDotFiles
    var preferredExternalPackages by state::preferredExternalPackages

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
        archiveReader.openViewer(
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
                scope = scope,
                displayUri = displayUri,
                pendingList = pendingList.toList(),
                rootUri = rootUri,
                copyMoveLog = copyMoveLog,
                runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
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
                scope = scope,
                displayUri = displayUri,
                pendingList = pendingList.toList(),
                rootUri = rootUri,
                copyMoveLog = copyMoveLog,
                runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
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
                scope = scope,
                displayUri = displayUri,
                pendingListSnapshot = pendingList.toList(),
                rootUri = rootUri,
                runWithProgress = { label, total, block -> runWithProgress(label, total, block) },
                setCancelAction = { action -> onSetProgressCancel(action) },
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
                        DirectoryTabRoute(
                            DirectoryTabRouteState(
                                modifier = Modifier.fillMaxSize(),
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
                                onChangeRoot = { onChangeRoot() },
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
                                                scope.launch { prefs.setExternalOpenPackageForExtension(extension, packageName) }
                                            }
                                        }
                                    } else {
                                        val extension = fileExtensionKey(name)
                                        if (!packageName.isNullOrBlank() && extension != null) {
                                            scope.launch { prefs.clearExternalOpenPackageForExtension(extension) }
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
                                        scope.launch { prefs.clearExternalOpenPackageForExtension(extension) }
                                    }
                                },
                                onOpenMarkdownView = { uri, name, encrypted ->
                                    setTransientRoute(TransientRoute.MarkdownViewer(uri, name, encrypted))
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
                                onCreateMusicPlaylist = { uri, name ->
                                    scope.launch {
                                        val playlist = withContext(Dispatchers.IO) {
                                            createDirectoryPlaybackPlaylistAndStart(context, prefs, uri, name)
                                        }
                                        if (playlist == null) {
                                            Toast.makeText(context, context.getString(R.string.browser_no_music_in_dir), Toast.LENGTH_SHORT).show()
                                        } else {
                                            recordRecentOpen(
                                                RECENT_TYPE_PLAYLIST,
                                                playlist.id,
                                                playlist.displayName(context.getString(R.string.player_unnamed_playlist)),
                                                null,
                                                playlist.id
                                            )
                                            requestOpenPlayerTab()
                                            Toast.makeText(context, context.getString(R.string.browser_playlist_from_dir_started), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onRequestGpgDecrypt = { fileModel, dirUri ->
                                    crypto.open(CryptoRequest.Gpg(GpgOpState.Decrypt(fileModel, dirUri)))
                                },
                                onRequestGpgEncrypt = { fileModel, dirUri ->
                                    crypto.open(CryptoRequest.Gpg(GpgOpState.Encrypt(fileModel, dirUri)))
                                },
                                onRequestQuickMask = { model ->
                                    onQuickMask(model)
                                },
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
                                onShareFileToGit = { model ->
                                    onShareToGit(model)
                                },
                                onForwardToBulletin = { items ->
                                    if (items.isEmpty()) return@DirectoryTabRouteState
                                    onForwardToBulletin(BulletinForwardPayload.Files(items))
                                },
                                onUnzipRequest = { archiveOps.open(ArchiveOperationRequest.Unzip(it)) },
                                onRequestMdZipView = { archiveReader.open(ArchiveReaderKind.MD_ZIP, it) },
                                onRequestHtmlView = {
                                    setTransientRoute(htmlViewerRouteFromFile(it.uri.toString(), it.name))
                                },
                                onRequestHtmlZipView = { archiveReader.open(ArchiveReaderKind.HTML_ZIP, it) },
                                onRequestOpenUrl = { url ->
                                    setTransientRoute(htmlViewerRouteFromUrl(url))
                                },
                                onRequestLlmZipView = { archiveReader.open(ArchiveReaderKind.LLM_ZIP, it) },
                                onRequestEpubView = { archiveReader.open(ArchiveReaderKind.EPUB, it) },
                                onRequestTxtView = { archiveReader.open(ArchiveReaderKind.TXT, it) },
                                onRequestLlmView = { archiveReader.open(ArchiveReaderKind.LLM, it) },
                                onRequestPicZipView = { archiveReader.open(ArchiveReaderKind.PIC_ZIP, it) },
                                onRequestVidZipView = { archiveReader.open(ArchiveReaderKind.VID_ZIP, it) },
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
                                onRequestPdfView = { setTransientRoute(TransientRoute.PdfViewer(it.uri.toString(), it.name)) },
                                onCompressArchiveRequest = { archiveOps.open(ArchiveOperationRequest.CompressFile(it)) },
                                onRequestPassProtect = { model -> crypto.open(CryptoRequest.PassProtect(model)) },
                                onRequestPassView = { model -> crypto.open(CryptoRequest.PassView(model)) },
                                onRequestPassEdit = { model, dirUri -> crypto.open(CryptoRequest.PassEdit(model, dirUri)) },
                                onRequestImportConfig = { model ->
                                    scope.launch {
                                        val jsonString = withContext(Dispatchers.IO) {
                                            try {
                                                context.contentResolver.openInputStream(model.uri)?.use { it.bufferedReader().readText() } ?: ""
                                            } catch (_: Exception) {
                                                ""
                                            }
                                        }
                                        configImportExportWorkflow.startImportFromJson(jsonString)
                                    }
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
                                onOpenPlaybackScreen = { requestOpenPlayerTab() }
                            )
                        )
        if (showPendingList) {
            PendingListScreen(
                pendingList = pendingList,
                currentDirPath = currentDirPath,
                rootUri = rootUri,
                onShowFileDetails = { file -> onShowFileDetail(file) },
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
                    else onBatchMask(list)
                },
                onRequestBatchGpgEncrypt = {
                    if (pendingList.any { it.name.endsWith(".gpg", ignoreCase = true) }) {
                        Toast.makeText(context, context.getString(R.string.browser_batch_encrypt_has_gpg), Toast.LENGTH_SHORT).show()
                    } else {
                        val list = pendingList.filter { !it.isDirectory }
                        if (list.isEmpty()) Toast.makeText(context, context.getString(R.string.browser_no_encrypt_candidates), Toast.LENGTH_SHORT).show()
                        else {
                            crypto.open(CryptoRequest.Gpg(GpgOpState.BatchEncrypt(list, displayUri)))
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
                            crypto.open(CryptoRequest.Gpg(GpgOpState.BatchDecrypt(list, displayUri)))
                        }
                    }
                },
                onRequestCompressArchive = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        archiveOps.open(ArchiveOperationRequest.CompressPending(pendingList.toList(), null, ""))
                    }
                },
                onRequestCompressToZip = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        archiveOps.open(ArchiveOperationRequest.CompressPending(pendingList.toList(), ArchiveCompressionFormat.ZIP, "archive_$ts.zip"))
                    }
                },
                onRequestCompressTo7z = {
                    if (pendingList.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.browser_list_empty), Toast.LENGTH_SHORT).show()
                    } else {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        archiveOps.open(ArchiveOperationRequest.CompressPending(pendingList.toList(), ArchiveCompressionFormat.SEVEN_Z, "archive_$ts.7z"))
                    }
                },
                onClearFilteredList = { toRemove ->
                    val snapshot = toRemove.toList()
                    pendingList.removeAll(snapshot)
                },
                onAddToPlayback = { audioList -> onAddToPlayback(audioList)
                },
                onDismiss = { showPendingList = false }
            )
        }
        pendingBatchDetails?.let { files ->
            BatchPendingMetadataDialog(
                files = files,
                context = context,
                prefs = prefs,
                onDismiss = { pendingBatchDetails = null },
                onSaved = { pendingBatchDetails = null }
            )
        }
        if (showPendingDeleteConfirm && pendingList.isNotEmpty()) {
            val toDelete = pendingList.toList()
            PendingDeleteConfirmDialog(
                items = toDelete,
                hasRoot = rootUri != null,
                onDismiss = { showPendingDeleteConfirm = false },
                onConfirm = { deletePermanently ->
                    scope.launch {
                        val label = if (deletePermanently) context.getString(R.string.common_delete) else context.getString(R.string.browser_moved_to_trash)
                        val root = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                        runWithProgress(label, toDelete.size) { setProgress ->
                            withContext(Dispatchers.IO) {
                                toDelete.forEachIndexed { index, model ->
                                    if (deletePermanently) {
                                        context.contentResolver.deleteDocument(model.uri)
                                    } else {
                                        if (root != null) moveToTrash(context, model.uri, root, root)
                                    }
                                    withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                                }
                            }
                        }
                        refresh()
                        showPendingDeleteConfirm = false
                        finishPendingListAndReturn()
                        Toast.makeText(context, if (deletePermanently) context.getString(R.string.browser_deleted_count, toDelete.size) else context.getString(R.string.browser_moved_to_trash_count, toDelete.size), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

}
