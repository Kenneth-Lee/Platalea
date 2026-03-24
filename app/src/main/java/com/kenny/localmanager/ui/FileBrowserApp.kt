package com.kenny.localmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.material.icons.filled.ArrowBack
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import android.widget.Toast
import android.provider.DocumentsContract
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.kenny.localmanager.MainActivity
import com.kenny.localmanager.R
import com.kenny.localmanager.data.ConfigPlaylistImportMode
import com.kenny.localmanager.data.Playlist
import com.kenny.localmanager.data.configJsonContainsKeys
import com.kenny.localmanager.data.configJsonPlaylistCount
import com.kenny.localmanager.data.exportConfig
import com.kenny.localmanager.data.importConfig
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.DirectoryAccessException
import com.kenny.localmanager.file.copyDocumentTo
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.getDirectoryToOpen
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.emptyTrash
import com.kenny.localmanager.file.getTrashUriIfExists
import com.kenny.localmanager.file.isInsideDirectory
import com.kenny.localmanager.file.moveToTrash
import com.kenny.localmanager.file.quickObfuscate
import com.kenny.localmanager.file.quickDeobfuscate
import com.kenny.localmanager.file.isQuickObfuscatedFileName
import com.kenny.localmanager.file.restoreFromTrash
import com.kenny.localmanager.file.listChildrenFast
import com.kenny.localmanager.file.listFilesSafe
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.moveDocumentTo
import com.kenny.localmanager.file.renameDocument
import com.kenny.localmanager.file.toModel
import com.kenny.localmanager.file.compressToZip
import com.kenny.localmanager.file.compressTo7z
import com.kenny.localmanager.file.unzipToParent
import com.kenny.localmanager.file.UnzipResult
import com.kenny.localmanager.file.isArchiveEncrypted
import com.kenny.localmanager.file.getArchiveFirstLevelEntries
import com.kenny.localmanager.file.isRarV5Archive
import com.kenny.localmanager.file.ZipFirstLevelResult
import com.kenny.localmanager.file.extractMdZipToCache
import com.kenny.localmanager.file.getMdZipCacheDir
import com.kenny.localmanager.file.getMdZipCacheTimestamp
import com.kenny.localmanager.file.findMdZipCacheTarget
import com.kenny.localmanager.file.isMdZipCacheEncrypted
import com.kenny.localmanager.file.cleanMdZipCache
import com.kenny.localmanager.file.cleanHtmlZipCache
import com.kenny.localmanager.file.getHtmlZipCacheDir
import com.kenny.localmanager.file.getHtmlZipCacheTimestamp
import com.kenny.localmanager.file.findHtmlZipIndexFile
import com.kenny.localmanager.file.isHtmlZipCacheEncrypted
import com.kenny.localmanager.file.extractHtmlZipToCache
import com.kenny.localmanager.file.extractHtmlZipToCacheWithProgress
import com.kenny.localmanager.file.HtmlZipParseResult
import com.kenny.localmanager.file.EpubExtractResult
import com.kenny.localmanager.file.EpubParseResult
import com.kenny.localmanager.file.extractEpubToCache
import com.kenny.localmanager.file.prepareTxtAsEpub
import com.kenny.localmanager.file.loadEpubFromCache
import com.kenny.localmanager.file.getEpubChapterFile
import com.kenny.localmanager.file.getEpubCacheDir
import com.kenny.localmanager.file.getEpubCacheTimestamp
import com.kenny.localmanager.file.isEpubCacheEncrypted
import com.kenny.localmanager.file.cleanEpubCache
import com.kenny.localmanager.file.PicZipExtractResult
import com.kenny.localmanager.file.extractPicZipToCache
import com.kenny.localmanager.file.getPicZipCacheDir
import com.kenny.localmanager.file.tryPicZipPassword
import com.kenny.localmanager.file.getPicZipCacheTimestamp
import com.kenny.localmanager.file.isPicZipCacheEncrypted
import com.kenny.localmanager.file.cleanPicZipCache
import com.kenny.localmanager.file.CacheEntry
import com.kenny.localmanager.file.getCacheEntries
import com.kenny.localmanager.file.clearCacheEntry
import com.kenny.localmanager.file.formatSize
import com.kenny.localmanager.ui.EpubViewerScreen
import com.kenny.localmanager.ui.PicZipViewerScreen
import com.kenny.localmanager.ui.PdfViewerScreen
import com.kenny.localmanager.player.PlaybackService
import com.kenny.localmanager.player.PlaybackState
import com.kenny.localmanager.player.ACTION_NEXT
import com.kenny.localmanager.player.ACTION_PAUSE
import com.kenny.localmanager.player.ACTION_PLAY
import com.kenny.localmanager.player.ACTION_PREV
import com.kenny.localmanager.player.ACTION_RESUME
import com.kenny.localmanager.player.ACTION_STOP
import com.kenny.localmanager.player.EXTRA_DIR_URI
import com.kenny.localmanager.player.EXTRA_NAMES
import com.kenny.localmanager.player.ACTION_SEEK
import com.kenny.localmanager.player.EXTRA_PLAYLIST_ID
import com.kenny.localmanager.player.EXTRA_POSITION_MS
import com.kenny.localmanager.player.EXTRA_START_INDEX
import com.kenny.localmanager.player.EXTRA_URIS
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.GpgHelper.GpgEncryptedKind
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.generateDefaultKey
import com.kenny.localmanager.gpg.listEncryptionPublicKeyRings
import com.kenny.localmanager.gpg.KeyInfo
import com.kenny.localmanager.gpg.listGpgKeyFiles
import com.kenny.localmanager.gpg.listPublicKeyInfos
import com.kenny.localmanager.gpg.listSecretKeyInfos
import com.kenny.localmanager.gpg.deleteAllPublicKeys
import com.kenny.localmanager.gpg.deletePublicKeyById
import com.kenny.localmanager.gpg.deleteSecretKeys
import com.kenny.localmanager.gpg.getAllPublicKeyRingsBytes
import com.kenny.localmanager.gpg.getSecretKeyRingBytes
import com.kenny.localmanager.gpg.getSinglePublicKeyRingBytes
import com.kenny.localmanager.gpg.mergePublicKeyRing
import com.kenny.localmanager.gpg.parsePublicKeyRingFromStream
import com.kenny.localmanager.gpg.parseSecretKeyRingFromStream
import com.kenny.localmanager.gpg.saveSecretKeyRing
import com.kenny.localmanager.gpg.getGpgKeyDir
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import com.kenny.localmanager.dict.importStarDict
import com.kenny.localmanager.dict.isStarDictImportCandidate
import com.kenny.localmanager.git.cloneToTree
import com.kenny.localmanager.git.commitAndPush
import com.kenny.localmanager.git.copyFileToShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CONFIG_EXPORT_FILE_NAME = "local_manager_config.json"

private data class ExternalAppTarget(
    val packageName: String,
    val label: String
)

/** 文件列表项副标题：大小与修改时间（与排序方式对应，便于对照）。 */
private fun fileItemSubtitle(model: DocumentFileModel): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(model.lastModified))
    return if (model.displaySize.isNotEmpty()) "${model.displaySize}  $dateStr" else dateStr
}

private fun formatPlaybackTime(ms: Int): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/** 规范化 content URI 字符串，修正 authority 中可能被错误成空格的句点（如 android .externalstorage -> android.externalstorage） */
private fun normalizeContentUriString(s: String): String {
    if (!s.startsWith("content://")) return s
    return s.replace("android ", "android.")
}

private fun fileExtensionKey(name: String): String? {
    val dotIndex = name.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex >= name.lastIndex) return null
    return name.substring(dotIndex + 1).trim().lowercase().ifBlank { null }
}

private fun buildExternalOpenIntent(context: Context, uri: Uri, packageName: String? = null): Intent {
    val mimeType = context.contentResolver.getType(uri)
    return Intent(Intent.ACTION_VIEW).apply {
        if (mimeType.isNullOrBlank()) data = uri else setDataAndType(uri, mimeType)
        if (!packageName.isNullOrBlank()) setPackage(packageName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Suppress("DEPRECATION")
private fun queryExternalOpenTargets(context: Context, uriString: String): List<ExternalAppTarget> {
    val packageManager = context.packageManager
    return packageManager.queryIntentActivities(
        buildExternalOpenIntent(context, Uri.parse(uriString)),
        PackageManager.MATCH_DEFAULT_ONLY
    ).asSequence()
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
            if (packageName.isBlank() || packageName == context.packageName) return@mapNotNull null
            val label = runCatching { resolveInfo.loadLabel(packageManager)?.toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: packageName
            ExternalAppTarget(packageName = packageName, label = label)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
        .toList()
}

private fun launchExternalOpen(context: Context, uriString: String, packageName: String?): Boolean {
    return runCatching {
        context.startActivity(buildExternalOpenIntent(context, Uri.parse(uriString), packageName))
        true
    }.getOrElse { false }
}

/** 从根目录算起的相对路径（用于待处理列表显示当前目录） */
private fun pathFromRoot(context: Context, rootUri: String?, currentUri: String): String {
    if (rootUri == null) return currentUri
    val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(normalizeContentUriString(rootUri))) ?: return currentUri
    val currentDoc = if (currentUri.contains("/tree/")) DocumentFile.fromTreeUri(context, Uri.parse(currentUri))
        else DocumentFile.fromSingleUri(context, Uri.parse(currentUri))
    val current = currentDoc ?: return currentUri
    val parts = mutableListOf<String>()
    var c: DocumentFile? = current
    while (c != null) {
        if (c.uri == rootDoc.uri) break
        parts.add(0, c.name ?: "?")
        c = c.parentFile
    }
    return "/" + parts.joinToString("/")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserApp(
    initialFileUri: androidx.compose.runtime.MutableState<String?>? = null,
    initialLaunchTarget: String? = null
) {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }
    var rootUri by remember { mutableStateOf<String?>(null) }
    var initialDirUri by remember { mutableStateOf<String?>(null) }
    var pendingSaveFileUri by remember { mutableStateOf<String?>(null) }
    var showOverwriteConfirm by remember { mutableStateOf<Pair<String, String>?>(null) } // (sourceUri, fileName)
    var saveInProgress by remember { mutableStateOf(false) }
    var viewingFile by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var markdownViewFile by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    val markdownViewerSessionCache = remember { MarkdownViewerSessionCache() }
    var passContentView by remember { mutableStateOf<PassDecryptedContent?>(null) }
    var passEditRequest by remember { mutableStateOf<Pair<DocumentFileModel, String>?>(null) }
    var passEditPassword by remember { mutableStateOf("") }
    var passEditInProgress by remember { mutableStateOf(false) }
    var passEditTriedCache by remember { mutableStateOf(false) }
    var passEditState by remember { mutableStateOf<PassEditState?>(null) }
    var quickNoteData by remember { mutableStateOf<QuickNoteLoadedData?>(null) }
    var quickNoteStartWithAddDialog by remember { mutableStateOf(false) }
    var quickNotePassword by remember { mutableStateOf("") }
    var quickNotePasswordRequired by remember { mutableStateOf(false) }
    var quickNoteInProgress by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var mdZipViewState by remember { mutableStateOf<MdZipViewState?>(null) }
    var picZipViewState by remember { mutableStateOf<PicZipViewState?>(null) }
    var htmlZipViewState by remember { mutableStateOf<HtmlZipViewState?>(null) }
    var epubViewState by remember { mutableStateOf<EpubViewState?>(null) }
    var pdfViewState by remember { mutableStateOf<Pair<String, String>?>(null) } // (uri, fileName)
    var showDictionaryScreen by remember { mutableStateOf(false) }
    var currentUri by remember { mutableStateOf<String?>(null) }
    val fileBrowserBackStack = remember { mutableStateListOf<String>() }
    val fileListLazyState = rememberLazyListState()
    var viewerPreviewBytes by remember { mutableStateOf(4096) }
    var saveCompletedToken by remember { mutableStateOf(0) }
    var preferredExternalPackages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    var startupDecryptKeyEnabled by remember { mutableStateOf(false) }
    var hasSecretKeyFile by remember { mutableStateOf(false) }
    var unlockedByStartup by remember { mutableStateOf(false) }
    LaunchedEffect(prefs) {
        prefs.startupDecryptKey.collect { startupDecryptKeyEnabled = it }
    }
    LaunchedEffect(Unit) {
        hasSecretKeyFile = withContext(Dispatchers.IO) {
            File(getGpgKeyDir(context), "secring.gpg").exists()
        }
    }
    val showUnlockGate = startupDecryptKeyEnabled && hasSecretKeyFile && !unlockedByStartup

    LaunchedEffect(prefs) {
        prefs.viewerPreviewBytes.collect { viewerPreviewBytes = it }
    }
    LaunchedEffect(prefs) {
        prefs.externalOpenByExtension.collect { preferredExternalPackages = it }
    }
    LaunchedEffect(Unit) {
        rootUri = prefs.rootUri.first()?.let { normalizeContentUriString(it) }
    }
    LaunchedEffect(rootUri, initialDirUri) {
        if (rootUri != null) {
            val normalizedRoot = normalizeContentUriString(rootUri!!)
            val target = initialDirUri?.let { normalizeContentUriString(it) } ?: normalizedRoot
            if (currentUri == null) {
                currentUri = target
            } else if (!currentUri!!.startsWith(normalizedRoot)) {
                currentUri = target
            }
        }
    }
    LaunchedEffect(rootUri) {
        markdownViewerSessionCache.clear()
    }
    LaunchedEffect(initialFileUri?.value) {
        val uriStr = initialFileUri?.value ?: return@LaunchedEffect
        initialFileUri.value = null
        pendingSaveFileUri = uriStr
    }

    fun resetQuickNotePromptState() {
        quickNotePassword = ""
        quickNotePasswordRequired = false
        quickNoteInProgress = false
    }

    fun closeQuickNote() {
        quickNoteData = null
        quickNoteStartWithAddDialog = false
        resetQuickNotePromptState()
    }

    suspend fun savePicZipImageToRoot(sourceFile: File, fileName: String): Boolean {
        val targetRoot = rootUri?.let { normalizeContentUriString(it) }
        if (targetRoot == null) {
            Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
            return false
        }
        val bytes = withContext(Dispatchers.IO) {
            runCatching { sourceFile.readBytes() }.getOrNull()
        } ?: run {
            Toast.makeText(context, "保存失败：无法读取图片", Toast.LENGTH_SHORT).show()
            return false
        }
        val targetUri = Uri.parse(targetRoot)
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
        var outName = fileName
        var copyIndex = 1
        while (findChildByName(context, targetUri, outName) != null) {
            outName = "$baseName ($copyIndex)$ext"
            copyIndex++
        }
        val mimeType = when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> "application/octet-stream"
        }
        val ok = withContext(Dispatchers.IO) {
            createFileWithBytes(context, targetUri, targetUri, outName, mimeType, bytes)
        }
        Toast.makeText(context, if (ok) "已保存到根目录：$outName" else "保存失败", Toast.LENGTH_SHORT).show()
        return ok
    }

    fun requestCloseQuickNote(entries: List<QuickNoteEntry>) {
        val currentData = quickNoteData ?: run {
            closeQuickNote()
            return
        }
        if (quickNoteInProgress) return
        quickNoteInProgress = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                saveQuickNoteData(context, currentData, entries)
            }
            quickNoteInProgress = false
            result.onSuccess { saved ->
                markdownViewerSessionCache.invalidateByUri(saved.fileInfo.uri.toString())
                closeQuickNote()
                if (initialLaunchTarget == "quick_note") (context as? Activity)?.finish()
            }.onFailure { throwable ->
                Toast.makeText(context, throwable.message ?: "快速笔记保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestOpenQuickNote(startWithAddDialog: Boolean, password: String? = null) {
        val root = rootUri?.let { normalizeContentUriString(it) }
        if (root == null) {
            Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
            return
        }
        quickNoteStartWithAddDialog = startWithAddDialog
        quickNoteInProgress = true
        scope.launch {
            when (val result = withContext(Dispatchers.IO) {
                openQuickNoteData(context, root, password)
            }) {
                is QuickNoteOpenResult.Success -> {
                    quickNoteData = result.data
                    quickNotePasswordRequired = false
                    quickNotePassword = ""
                }
                QuickNoteOpenResult.RequiresPassword -> {
                    quickNotePasswordRequired = true
                }
                is QuickNoteOpenResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
            }
            quickNoteInProgress = false
        }
    }

    var quickNoteLaunchTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(initialLaunchTarget, rootUri) {
        if (initialLaunchTarget == "quick_note" && !quickNoteLaunchTriggered) {
            quickNoteLaunchTriggered = true
            if (rootUri != null) requestOpenQuickNote(false, SecretKeyPasswordCache.get()?.let { String(it) })
            else Toast.makeText(context, "请先选择根目录", Toast.LENGTH_LONG).show()
        }
    }

    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val normalized = normalizeContentUriString(uri.toString())
            scope.launch { prefs.setRootUri(normalized) }
            rootUri = normalized
        }
    }

    LaunchedEffect(pendingSaveFileUri, rootUri) {
        val sourceUriStr = pendingSaveFileUri ?: return@LaunchedEffect
        val targetRoot = rootUri?.let { normalizeContentUriString(it) } ?: return@LaunchedEffect
        if (showOverwriteConfirm != null || saveInProgress) return@LaunchedEffect
        val ctx = context
        val sourceUri = Uri.parse(sourceUriStr)
        val sourceDoc = DocumentFile.fromSingleUri(ctx, sourceUri) ?: run {
            pendingSaveFileUri = null
            Toast.makeText(ctx, "无法读取文件", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        val fileName = sourceDoc.name ?: run {
            pendingSaveFileUri = null
            Toast.makeText(ctx, "无法获取文件名", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        val targetUri = Uri.parse(targetRoot)
        val existingUri = findChildByName(ctx, targetUri, fileName)
        if (existingUri != null) {
            showOverwriteConfirm = sourceUriStr to fileName
        } else {
            saveInProgress = true
            val treeUri = Uri.parse(targetRoot)
            val copied = withContext(Dispatchers.IO) {
                copyDocumentTo(ctx, sourceUri, targetUri, treeUri)
            }
            saveInProgress = false
            pendingSaveFileUri = null
            if (copied != null) {
                val dir = normalizeContentUriString(targetRoot)
                initialDirUri = dir
                currentUri = dir
                saveCompletedToken++
                Toast.makeText(ctx, "已保存到当前目录", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    when {
        showUnlockGate -> {
            BackHandler { (context as? Activity)?.finish() }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                var unlockPwd by remember { mutableStateOf("") }
                var unlockInProgress by remember { mutableStateOf(false) }
                var unlockError by remember { mutableStateOf<String?>(null) }
                Column(
                    modifier = Modifier.widthIn(max = 320.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "启动解密密钥已开启",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "请输入私钥密码以解锁应用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = unlockPwd,
                        onValueChange = { unlockPwd = it; unlockError = null },
                        label = { Text("私钥密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !unlockInProgress
                    )
                    unlockError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            if (unlockPwd.isEmpty() || unlockInProgress) return@Button
                            unlockInProgress = true
                            unlockError = null
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    GpgHelper.tryUnlockSecretKey(context, unlockPwd.toCharArray())
                                }
                                unlockInProgress = false
                                if (ok) {
                                    SecretKeyPasswordCache.set(unlockPwd.toCharArray())
                                    unlockedByStartup = true
                                } else {
                                    unlockError = "密码错误或解密失败"
                                }
                            }
                        },
                        enabled = unlockPwd.isNotEmpty() && !unlockInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (unlockInProgress) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("解锁")
                        }
                    }
                }
            }
        }
        rootUri == null && initialDirUri == null && showOverwriteConfirm == null -> {
            var lastBackPressTime by remember { mutableStateOf(0L) }
            BackHandler {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) (context as? Activity)?.finish()
                else {
                    lastBackPressTime = now
                    Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (pendingSaveFileUri != null) "从其他应用打开文件，请选择保存位置" else "选择根目录以浏览文件",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { treeLauncher.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (pendingSaveFileUri != null) "选择保存位置" else "选择根目录")
                    }
                }
            }
        }
        markdownViewFile != null -> {
            val (uri, name, isEncrypted) = markdownViewFile!!
            BackHandler { markdownViewFile = null }
            MarkdownViewerScreen(
                initialFileUri = uri,
                initialFileName = name,
                isEncrypted = isEncrypted,
                sessionCache = markdownViewerSessionCache,
                onBack = { markdownViewFile = null },
                onOpenFile = { openUri, openName, openEncrypted ->
                    markdownViewFile = Triple(openUri, openName, openEncrypted)
                }
            )
        }
        mdZipViewState != null -> {
            val state = mdZipViewState!!
            BackHandler {
                if (state.isEncrypted) cleanMdZipCache(context, state.zipUri)
                mdZipViewState = null
            }
            MdZipViewerScreen(
                initialTargetFile = state.targetFile,
                contentDir = state.contentDir,
                zipFileName = state.zipFileName,
                sessionCache = markdownViewerSessionCache,
                onBack = {
                    if (state.isEncrypted) cleanMdZipCache(context, state.zipUri)
                    mdZipViewState = null
                },
                logDebug = null
            )
        }
        htmlZipViewState != null -> {
            val state = htmlZipViewState!!
            BackHandler {
                if (state.isEncrypted) cleanHtmlZipCache(context, state.zipUri)
                htmlZipViewState = null
            }
            HtmlZipViewerScreen(
                initialIndexFile = state.indexFile,
                contentDir = state.contentDir,
                zipFileName = state.zipFileName,
                onBack = {
                    if (state.isEncrypted) cleanHtmlZipCache(context, state.zipUri)
                    htmlZipViewState = null
                },
                logDebug = null
            )
        }
        epubViewState != null -> {
            val state = epubViewState!!
            BackHandler {
                if (state.isEncrypted) cleanEpubCache(context, state.epubUri)
                epubViewState = null
            }
            EpubViewerScreen(
                extractResult = state.extractResult,
                zipFileName = state.zipFileName,
                epubUri = state.epubUri,
                onBack = {
                    if (state.isEncrypted) cleanEpubCache(context, state.epubUri)
                    epubViewState = null
                },
                logDebug = null
            )
        }
        picZipViewState != null -> {
            val state = picZipViewState!!
            BackHandler { picZipViewState = null }
            PicZipViewerScreen(
                contentDir = state.contentDir,
                imagePaths = state.imagePaths,
                zipFileName = state.zipFileName,
                isEncrypted = state.isEncrypted,
                password = state.password,
                initialIndex = state.initialIndex,
                onSaveCurrentImage = { sourceFile, fileName ->
                    savePicZipImageToRoot(sourceFile, fileName)
                },
                onBack = { doDelete ->
                    if (doDelete == true) cleanPicZipCache(context, state.zipUri)
                    picZipViewState = null
                }
            )
        }
        pdfViewState != null -> {
            val (pdfUri, pdfName) = pdfViewState!!
            BackHandler { pdfViewState = null }
            PdfViewerScreen(
                uri = pdfUri,
                fileName = pdfName,
                onBack = { pdfViewState = null }
            )
        }
        passContentView != null -> {
            val content = passContentView!!
            BackHandler { passContentView = null }
            PassContentViewerScreen(
                innerFileName = content.innerFileName,
                decryptedBytes = content.decryptedBytes,
                onBack = { passContentView = null }
            )
        }
        passEditState != null -> {
            val state = passEditState!!
            BackHandler { passEditState = null }
            PassEditScreen(
                fileName = state.innerName,
                initialDecryptedBytes = state.decryptedBytes,
                onSave = { newBytes ->
                    val ctx = context
                    val fileModel = state.fileModel
                    val dirUri = state.dirUri
                    val treeUri = state.treeUri
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            val secRings = loadSecretKeyRings(ctx) ?: return@withContext false
                            val defaultKeyId = secRings.iterator().asSequence().firstOrNull()?.publicKey?.keyID ?: return@withContext false
                            val pubRings = loadPublicKeyRings(ctx) ?: return@withContext false
                            val pubKeyRing = findPublicKeyRing(pubRings, defaultKeyId) ?: return@withContext false
                            val encrypted = GpgHelper.encryptWithPublicKey(newBytes, pubKeyRing, fileModel.name) ?: return@withContext false
                            try { ctx.contentResolver.deleteDocument(fileModel.uri) } catch (_: Exception) { }
                            createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, fileModel.name, "application/octet-stream", encrypted)
                        }
                        passEditState = null
                        refreshTrigger++
                        Toast.makeText(ctx, if (ok) "已保存并重新加密" else "保存失败", Toast.LENGTH_SHORT).show()
                    }
                },
                onBack = { passEditState = null }
            )
        }
        quickNoteData != null -> {
            val data = quickNoteData!!
            QuickNoteScreen(
                loadedData = data,
                startWithAddDialog = quickNoteStartWithAddDialog,
                inProgress = quickNoteInProgress,
                onBack = { entries -> requestCloseQuickNote(entries) }
            )
        }
        showDictionaryScreen -> {
            BackHandler { showDictionaryScreen = false }
            DictionaryScreen(onBack = { showDictionaryScreen = false })
        }
        viewingFile != null -> {
            val (uri, name, isEncrypted) = viewingFile!!
            BackHandler { viewingFile = null }
            ViewerScreen(
                fileUri = uri,
                fileName = name,
                isEncrypted = isEncrypted,
                onBack = { viewingFile = null },
                onOpenMarkdownView = if (name.endsWith(".md", ignoreCase = true) || name.endsWith(".rst", ignoreCase = true)) {
                    { markdownViewFile = viewingFile; viewingFile = null }
                } else null
            )
        }
        else -> {
            val displayUri = currentUri ?: initialDirUri ?: rootUri!!
            val pendingList = remember { mutableStateListOf<DocumentFileModel>() }
            var showPendingList by remember { mutableStateOf(false) }
            var showPlaybackScreen by remember { mutableStateOf(initialLaunchTarget == "player") }
            var showPendingDeleteConfirm by remember { mutableStateOf(false) }
            var showPlaybackTargetDialog by remember { mutableStateOf(false) }
            var showConfigDialog by remember { mutableStateOf(false) }
            var showImportKeyConfirmDialog by remember { mutableStateOf(false) }
            var showImportPlaylistConfirmDialog by remember { mutableStateOf(false) }
            var pendingImportJson by remember { mutableStateOf<String?>(null) }
            var pendingImportPlaylistCount by remember { mutableStateOf(0) }
            var pendingImportPlaylistMode by remember { mutableStateOf(ConfigPlaylistImportMode.OVERWRITE) }
            var pendingPlaybackAudioList by remember { mutableStateOf<List<DocumentFileModel>>(emptyList()) }
            var showCacheManagementDialog by remember { mutableStateOf(false) }
            var showAboutDialog by remember { mutableStateOf(false) }
            var showKeyManagementDialog by remember { mutableStateOf(false) }
            LaunchedEffect(saveCompletedToken) { if (saveCompletedToken > 0) refreshTrigger++ }
            var lastBackPressTime by remember { mutableStateOf(0L) }
            var gpgState by remember { mutableStateOf<GpgOpState?>(null) }
            var gpgPassword by remember { mutableStateOf("") }
            var gpgDecryptMode by remember { mutableStateOf<GpgDecryptUiMode?>(null) }
            var gpgDecryptAutoTried by remember { mutableStateOf(false) }
            var gpgEncryptSelectedKeyId by remember { mutableStateOf<Long?>(null) }
            var gpgPubEncryptInProgress by remember { mutableStateOf(false) }
            var showChangeRootConfirm by remember { mutableStateOf(false) }
            var quickObfuscateOp by remember { mutableStateOf<Pair<DocumentFileModel, Boolean>?>(null) }
            var quickObfuscatePassword by remember { mutableStateOf("") }
            var quickObfuscateInProgress by remember { mutableStateOf(false) }
            var batchObfuscateOp by remember { mutableStateOf<Pair<List<DocumentFileModel>, Boolean>?>(null) }
            var batchObfuscatePassword by remember { mutableStateOf("") }
            var batchObfuscateInProgress by remember { mutableStateOf(false) }
            var passProtectTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var passProtectInProgress by remember { mutableStateOf(false) }
            var passViewTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var passViewPassword by remember { mutableStateOf("") }
            var passViewInProgress by remember { mutableStateOf(false) }
            var passViewTriedCache by remember { mutableStateOf(false) }
            var progressOp by remember { mutableStateOf<OperationProgress?>(null) }
            var currentDirPath by remember { mutableStateOf("") }
            LaunchedEffect(displayUri, rootUri) {
                currentDirPath = withContext(Dispatchers.IO) {
                    pathFromRoot(context, rootUri, displayUri)
                }
            }
            var cachedTrashUri by remember { mutableStateOf<Uri?>(null) }
            LaunchedEffect(rootUri) {
                cachedTrashUri = rootUri?.let { r ->
                    withContext(Dispatchers.IO) {
                        val root = Uri.parse(normalizeContentUriString(r))
                        getTrashUriIfExists(context, root, root)
                    }
                }
            }
            var dirCache by remember { mutableStateOf<Map<String, CachedDir>>(emptyMap()) }
            LaunchedEffect(rootUri) {
                dirCache = emptyMap()
            }
            val ftpManager = remember { com.kenny.localmanager.ftp.FtpServerManager(context) }
            var ftpPort by remember { mutableStateOf(2121) }
            var ftpPassword by remember { mutableStateOf<String?>(null) }
            var ftpTimeoutMinutes by remember { mutableStateOf(0) }
            var filterVisible by remember { mutableStateOf(true) }
            var showFtpScreen by remember { mutableStateOf(false) }
            var showGitConfigDialog by remember { mutableStateOf(false) }
            var showPubkeyShareScreen by remember { mutableStateOf(false) }
            var showFileShareScreen by remember { mutableStateOf(false) }
            var shareFileToGitTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            val shareGitLogs = remember { mutableStateListOf<String>() }
            var shareGitInProgress by remember { mutableStateOf(false) }
            var shareGitDone by remember { mutableStateOf(false) }
            var zipUnzipTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var zipCompressTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var sevenZCompressTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var zipUnzipPassword by remember { mutableStateOf("") }
            var zipUnzipEncrypted by remember { mutableStateOf<Boolean?>(null) }
            var zipCompressPassword by remember { mutableStateOf("") }
            var sevenZCompressPassword by remember { mutableStateOf("") }
            var mdZipTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var mdZipEncrypted by remember { mutableStateOf<Boolean?>(null) }
            var mdZipPassword by remember { mutableStateOf("") }
            var mdZipInProgress by remember { mutableStateOf(false) }
            var htmlZipTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var htmlZipEncrypted by remember { mutableStateOf<Boolean?>(null) }
            var htmlZipPassword by remember { mutableStateOf("") }
            var htmlZipInProgress by remember { mutableStateOf(false) }
            var htmlZipLog by remember { mutableStateOf("") }
            var htmlZipLoadError by remember { mutableStateOf<String?>(null) }
            var epubTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var epubEncrypted by remember { mutableStateOf<Boolean?>(null) }
            var epubPassword by remember { mutableStateOf("") }
            var epubInProgress by remember { mutableStateOf(false) }
            var epubLog by remember { mutableStateOf("") }
            var epubLoadError by remember { mutableStateOf<String?>(null) }
            var txtTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var txtInProgress by remember { mutableStateOf(false) }
            var txtLoadError by remember { mutableStateOf<String?>(null) }
            var picZipTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var picZipEncrypted by remember { mutableStateOf<Boolean?>(null) }
            var picZipPassword by remember { mutableStateOf("") }
            var picZipInProgress by remember { mutableStateOf(false) }
            var showPendingCompressToZip by remember { mutableStateOf(false) }
            var showPendingCompressTo7z by remember { mutableStateOf(false) }
            var pendingCompressZipName by remember { mutableStateOf("") }
            var pendingCompressPassword by remember { mutableStateOf("") }
            var pendingCompress7zName by remember { mutableStateOf("") }
            var pendingCompress7zPassword by remember { mutableStateOf("") }
            var playbackState by remember { mutableStateOf<PlaybackState?>(null) }
            var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
            LaunchedEffect(Unit) {
                PlaybackService.playbackState.collect { playbackState = it }
            }
            LaunchedEffect(prefs) {
                prefs.playlists.collect { playlists = it }
            }
            LaunchedEffect(zipUnzipTarget) {
                zipUnzipEncrypted = null
                zipUnzipPassword = ""
                val target = zipUnzipTarget ?: return@LaunchedEffect
                if (target.name.endsWith(".rar", ignoreCase = true)) {
                    val rarV5 = withContext(Dispatchers.IO) { isRarV5Archive(context, target.uri) }
                    if (rarV5) {
                        Toast.makeText(context, "暂不支持 RAR5 解压，请转为 ZIP 或 RAR4", Toast.LENGTH_LONG).show()
                        zipUnzipTarget = null
                        return@LaunchedEffect
                    }
                }
                zipUnzipEncrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
            }
            LaunchedEffect(mdZipTarget) {
                mdZipEncrypted = null
                mdZipPassword = ""
                mdZipInProgress = false
                val target = mdZipTarget ?: return@LaunchedEffect
                // 先检查是否有有效缓存（非加密时）
                val cacheDir = getMdZipCacheDir(context, target.uri)
                val cacheTs = getMdZipCacheTimestamp(cacheDir)
                if (cacheTs > 0 && !isMdZipCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
                    val contentDir = java.io.File(cacheDir, "content")
                    val hasContent = contentDir.exists() &&
                        (contentDir.listFiles()?.isNotEmpty() == true)
                    if (hasContent) {
                        val isRstZip = target.name.endsWith(".rst.zip", ignoreCase = true)
                        val cachedTarget = findMdZipCacheTarget(cacheDir, isRstZip)
                        mdZipTarget = null
                        mdZipViewState = MdZipViewState(
                            targetFile = cachedTarget,
                            contentDir = contentDir,
                            zipFileName = target.name,
                            zipUri = target.uri,
                            isEncrypted = false
                        )
                        return@LaunchedEffect
                    }
                    // 缓存内容为空，废弃并重新解压
                    cacheDir.deleteRecursively()
                }
                mdZipEncrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
                // 非加密的直接解压
                if (mdZipEncrypted == false) {
                    mdZipInProgress = true
                    val result = withContext(Dispatchers.IO) { extractMdZipToCache(context, target.uri, null, target.name) }
                    mdZipInProgress = false
                    mdZipTarget = null
                    if (result != null) {
                        mdZipViewState = MdZipViewState(
                            targetFile = result.targetFile,  // 可能为 null
                            contentDir = result.contentDir,
                            zipFileName = target.name,
                            zipUri = target.uri,
                            isEncrypted = false
                        )
                    } else {
                        Toast.makeText(context, "解压失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            LaunchedEffect(htmlZipTarget) {
                htmlZipEncrypted = null
                htmlZipPassword = ""
                htmlZipInProgress = false
                htmlZipLog = "准备加载: ${htmlZipTarget?.name ?: ""}\n"
                htmlZipLoadError = null
                val target = htmlZipTarget ?: return@LaunchedEffect
                val cacheDir = getHtmlZipCacheDir(context, target.uri)
                val cacheTs = getHtmlZipCacheTimestamp(cacheDir)
                if (cacheTs > 0 && !isHtmlZipCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
                    val contentDir = java.io.File(cacheDir, "content")
                    if (contentDir.exists() && contentDir.listFiles()?.isNotEmpty() == true) {
                        val cachedIndex = findHtmlZipIndexFile(contentDir)
                        htmlZipTarget = null
                        htmlZipViewState = HtmlZipViewState(
                            indexFile = cachedIndex,
                            contentDir = contentDir,
                            zipFileName = target.name,
                            zipUri = target.uri,
                            isEncrypted = false
                        )
                        return@LaunchedEffect
                    }
                    cacheDir.deleteRecursively()
                }
                htmlZipLog += "检查是否加密...\n"
                htmlZipEncrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
                htmlZipLog += "加密状态: $htmlZipEncrypted\n"
                if (htmlZipEncrypted == false) {
                    htmlZipInProgress = true
                    val result = withContext(Dispatchers.IO) {
                        extractHtmlZipToCacheWithProgress(context, target.uri, null, target.name) { log ->
                            htmlZipLog += "$log\n"
                        }
                    }
                    htmlZipInProgress = false
                    htmlZipTarget = null
                    when (result) {
                        is HtmlZipParseResult.Success -> {
                            htmlZipViewState = HtmlZipViewState(
                                indexFile = result.result.indexFile,
                                contentDir = result.result.contentDir,
                                zipFileName = target.name,
                                zipUri = target.uri,
                                isEncrypted = false
                            )
                        }
                        is HtmlZipParseResult.Error -> {
                            val detail = result.detail?.let { "\n$it" } ?: ""
                            htmlZipLog += "\n错误: ${result.message}$detail\n"
                            htmlZipLoadError = "${result.message}$detail"
                        }
                    }
                } else if (htmlZipEncrypted == true) {
                    htmlZipLog += "文件已加密，需要密码\n"
                }
            }
            LaunchedEffect(epubTarget) {
                val target = epubTarget ?: return@LaunchedEffect
                epubEncrypted = null
                epubPassword = ""
                epubInProgress = true
                epubLog = "准备加载: ${target.name}\n"
                epubLoadError = null
                try {
                    var cachedResult: EpubExtractResult? = null
                    var encrypted: Boolean? = null
                    withContext(Dispatchers.IO) {
                        epubLog += "检查缓存...\n"
                        val cacheDir = getEpubCacheDir(context, target.uri)
                        val cacheTs = getEpubCacheTimestamp(cacheDir)
                        if (cacheTs > 0 && !isEpubCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
                            cachedResult = loadEpubFromCache(cacheDir)
                            if (cachedResult != null) {
                                epubLog += "使用缓存\n"
                                return@withContext
                            }
                            cacheDir.deleteRecursively()
                        }
                        epubLog += "检查是否加密...\n"
                        encrypted = isArchiveEncrypted(context, target.uri, target.name)
                    }
                    val cached = cachedResult
                    if (cached != null) {
                        epubViewState = EpubViewState(
                            extractResult = cached,
                            zipFileName = target.name,
                            epubUri = target.uri,
                            isEncrypted = false
                        )
                        epubTarget = null
                        epubInProgress = false
                        return@LaunchedEffect
                    }
                    epubEncrypted = encrypted
                    if (encrypted == false) {
                        val result = withContext(Dispatchers.IO) {
                            extractEpubToCache(context, target.uri, null, target.name) { log ->
                                epubLog += "$log\n"
                            }
                        }
                        when (result) {
                            is EpubParseResult.Success -> {
                                epubViewState = EpubViewState(
                                    extractResult = result.result,
                                    zipFileName = target.name,
                                    epubUri = target.uri,
                                    isEncrypted = false
                                )
                                epubTarget = null
                                epubInProgress = false
                            }
                            is EpubParseResult.Error -> {
                                val detail = result.detail?.let { "\n$it" } ?: ""
                                epubLog += "\n错误: ${result.message}$detail\n"
                                epubLoadError = "${result.message}$detail"
                                epubInProgress = false
                                // 保留 epubTarget 以便显示错误对话框
                            }
                        }
                    } else {
                        epubLog += "文件已加密，需要密码\n"
                        epubInProgress = false
                    }
                } catch (e: Exception) {
                    epubLog += "\n异常: ${e.javaClass.simpleName}: ${e.message}\n"
                    epubLoadError = "${e.javaClass.simpleName}: ${e.message}"
                    epubInProgress = false
                }
            }
            // 处理 TXT 文件，转换为 EPUB 格式查看
            LaunchedEffect(txtTarget) {
                val target = txtTarget ?: return@LaunchedEffect
                txtInProgress = true
                txtLoadError = null
                try {
                    val cacheDir = getEpubCacheDir(context, target.uri)
                    val cacheTs = getEpubCacheTimestamp(cacheDir)

                    var result: EpubExtractResult? = null

                    // 检查是否有有效缓存
                    if (cacheTs > 0 && cacheTs >= target.lastModified) {
                        result = loadEpubFromCache(cacheDir)
                        // 如果缓存加载失败，清除旧缓存
                        if (result == null) {
                            Log.w("FileBrowserApp", "TXT cache load failed, clearing old cache")
                            cacheDir.deleteRecursively()
                        }
                    }

                    // 如果没有有效缓存，重新生成
                    if (result == null) {
                        // 将 TXT 文件复制到临时目录
                        val tempFile = File(context.cacheDir, "txt_temp/${target.name}")
                        tempFile.parentFile?.mkdirs()
                        context.contentResolver.openInputStream(target.uri)?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        // 转换为 EPUB 格式（使用 URI 作为缓存键）
                        result = prepareTxtAsEpub(context, tempFile, target.uri)
                    }

                    if (result != null) {
                        epubViewState = EpubViewState(
                            extractResult = result,
                            zipFileName = target.name,
                            epubUri = target.uri,
                            isEncrypted = false
                        )
                        txtTarget = null
                    } else {
                        txtLoadError = "无法解析 TXT 文件"
                    }
                } catch (e: Exception) {
                    Log.e("FileBrowserApp", "TXT processing failed", e)
                    txtLoadError = "${e.javaClass.simpleName}: ${e.message}"
                }
                txtInProgress = false
            }
            LaunchedEffect(picZipTarget) {
                picZipEncrypted = null
                picZipPassword = ""
                picZipInProgress = false
                val target = picZipTarget ?: return@LaunchedEffect
                val cacheDir = getPicZipCacheDir(context, target.uri)
                val cacheTs = getPicZipCacheTimestamp(cacheDir)
                if (cacheTs > 0 && !isPicZipCacheEncrypted(cacheDir) && cacheTs >= target.lastModified) {
                    val contentDir = java.io.File(cacheDir, "content")
                    val listFile = java.io.File(cacheDir, ".image_list")
                    if (contentDir.exists() && listFile.exists()) {
                        val paths = listFile.readText().lineSequence().filter { it.isNotBlank() }.toList()
                        val lastIndexFile = java.io.File(cacheDir, ".last_index")
                        val initialIndex = lastIndexFile.takeIf { it.exists() }?.readText()?.toIntOrNull()?.coerceIn(0, paths.size - 1) ?: 0
                        picZipTarget = null
                        picZipViewState = PicZipViewState(
                            contentDir = contentDir,
                            imagePaths = paths,
                            zipFileName = target.name,
                            zipUri = target.uri,
                            isEncrypted = false,
                            password = null,
                            initialIndex = initialIndex
                        )
                        return@LaunchedEffect
                    }
                    cacheDir.deleteRecursively()
                }
                picZipEncrypted = withContext(Dispatchers.IO) { isArchiveEncrypted(context, target.uri, target.name) }
                if (picZipEncrypted == false) {
                    picZipInProgress = true
                    val result = withContext(Dispatchers.IO) { extractPicZipToCache(context, target.uri, null, target.name) }
                    picZipInProgress = false
                    picZipTarget = null
                    if (result != null) {
                        picZipViewState = PicZipViewState(
                            contentDir = result.contentDir,
                            imagePaths = result.imagePaths,
                            zipFileName = target.name,
                            zipUri = target.uri,
                            isEncrypted = false,
                            password = null,
                            initialIndex = 0
                        )
                    } else {
                        Toast.makeText(context, "解压失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            LaunchedEffect(prefs) {
                prefs.ftpPort.collect { ftpPort = it }
            }
            LaunchedEffect(prefs) {
                prefs.ftpPassword.collect { ftpPassword = it }
            }
            LaunchedEffect(prefs) {
                prefs.ftpTimeoutMinutes.collect { ftpTimeoutMinutes = it }
            }
            LaunchedEffect(prefs) {
                prefs.filterVisible.collect { filterVisible = it }
            }
            BackHandler {
                when {
                    progressOp != null -> { } // 不响应返回，防止误触
                    showFtpScreen -> { ftpManager.stop(); showFtpScreen = false }
                    showFileShareScreen -> showFileShareScreen = false
                    batchObfuscateOp != null -> { batchObfuscateOp = null; batchObfuscatePassword = "" }
                    quickObfuscateOp != null -> { quickObfuscateOp = null; quickObfuscatePassword = "" }
                    passProtectTarget != null -> { passProtectTarget = null }
                    passViewTarget != null -> { if (!passViewInProgress) { passViewTarget = null; passViewPassword = "" } }
                    passEditRequest != null -> { if (!passEditInProgress) { passEditRequest = null; passEditPassword = "" } }
                    passEditState != null -> passEditState = null
                    showChangeRootConfirm -> showChangeRootConfirm = false
                    gpgState != null -> {
                        gpgState = null
                        gpgPassword = ""
                        gpgDecryptMode = null
                        gpgDecryptAutoTried = false
                        gpgEncryptSelectedKeyId = null
                    }
                    showKeyManagementDialog -> showKeyManagementDialog = false
                    showConfigDialog -> showConfigDialog = false
                    showCacheManagementDialog -> showCacheManagementDialog = false
                    showGitConfigDialog -> showGitConfigDialog = false
                    showAboutDialog -> showAboutDialog = false
                    showPendingDeleteConfirm -> showPendingDeleteConfirm = false
                    showPendingList -> showPendingList = false
                    showPlaybackScreen -> if (initialLaunchTarget == "player") (context as? Activity)?.finish() else showPlaybackScreen = false
                    zipUnzipTarget != null -> zipUnzipTarget = null
                    mdZipTarget != null -> { if (!mdZipInProgress) { mdZipTarget = null; mdZipPassword = "" } }
                    htmlZipTarget != null -> { if (!htmlZipInProgress) { htmlZipTarget = null; htmlZipPassword = "" } }
                    epubTarget != null -> { if (!epubInProgress) { epubTarget = null; epubPassword = "" } }
                    picZipTarget != null -> { if (!picZipInProgress) { picZipTarget = null; picZipPassword = "" } }
                    pdfViewState != null -> pdfViewState = null
                    zipCompressTarget != null -> zipCompressTarget = null
                    sevenZCompressTarget != null -> sevenZCompressTarget = null
                    showPendingCompressToZip -> showPendingCompressToZip = false
                    showPendingCompressTo7z -> showPendingCompressTo7z = false
                    fileBrowserBackStack.isNotEmpty() -> currentUri = fileBrowserBackStack.removeAt(fileBrowserBackStack.lastIndex)
                    else -> {
                        val now = System.currentTimeMillis()
                        if (now - lastBackPressTime < 2000) (context as? Activity)?.finish()
                        else {
                            lastBackPressTime = now
                            Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            var hideDotFiles by remember { mutableStateOf(false) }
            var startupDecryptKey by remember { mutableStateOf(false) }
            LaunchedEffect(prefs) {
                prefs.hideDotFiles.collect { hideDotFiles = it }
            }
            LaunchedEffect(prefs) {
                prefs.startupDecryptKey.collect { startupDecryptKey = it }
            }
            val copyMoveLog: ((String) -> Unit)? = null
            suspend fun runWithProgress(
                label: String,
                total: Int? = null,
                block: suspend ((Int) -> Unit) -> Unit
            ) {
                progressOp = OperationProgress(label, 0, total)
                delay(50)
                try {
                    val setProgress: (Int) -> Unit = { i -> progressOp = OperationProgress(label, i, total) }
                    block(setProgress)
                } finally {
                    progressOp = null
                }
            }

            fun clearPendingConfigImportState() {
                pendingImportJson = null
                pendingImportPlaylistCount = 0
                pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
                showImportKeyConfirmDialog = false
                showImportPlaylistConfirmDialog = false
            }

            fun clearPendingPlaybackTargetState() {
                pendingPlaybackAudioList = emptyList()
                showPlaybackTargetDialog = false
            }

            suspend fun createNewPlaybackPlaylist(audioList: List<DocumentFileModel>) {
                if (audioList.isEmpty()) return
                val playlist = Playlist(
                    id = java.util.UUID.randomUUID().toString(),
                    name = "播放列表 " + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                    uris = audioList.map { it.uri.toString() },
                    names = audioList.map { it.name }
                )
                withContext(Dispatchers.IO) { prefs.addPlaylist(playlist) }
                val intent = Intent(context, PlaybackService::class.java).apply {
                    action = ACTION_PLAY
                    putExtra(EXTRA_PLAYLIST_ID, playlist.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                pendingList.clear()
                showPlaybackScreen = true
                showPendingList = false
                clearPendingPlaybackTargetState()
                Toast.makeText(context, "已创建播放列表并加入 ${audioList.size} 首", Toast.LENGTH_SHORT).show()
            }

            suspend fun appendToPlaybackPlaylist(target: Playlist, audioList: List<DocumentFileModel>) {
                if (audioList.isEmpty()) return
                val result = withContext(Dispatchers.IO) {
                    prefs.appendTracksToPlaylist(
                        target.id,
                        audioList.map { it.uri.toString() },
                        audioList.map { it.name }
                    )
                }
                if (!result.found) {
                    Toast.makeText(context, "加入播放列表失败", Toast.LENGTH_SHORT).show()
                    return
                }
                val intent = Intent(context, PlaybackService::class.java).apply {
                    action = ACTION_PLAY
                    putExtra(EXTRA_PLAYLIST_ID, target.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                pendingList.clear()
                showPlaybackScreen = true
                showPendingList = false
                clearPendingPlaybackTargetState()
                val msg = when {
                    result.appendedCount > 0 && result.skippedCount > 0 ->
                        "已加入 ${result.appendedCount} 首到「${target.name}」，跳过 ${result.skippedCount} 首重复项，并开始播放"
                    result.appendedCount > 0 ->
                        "已加入 ${result.appendedCount} 首到「${target.name}」，并开始播放"
                    result.skippedCount > 0 ->
                        "所选音频已存在于「${target.name}」，已直接开始播放"
                    else ->
                        "已开始播放「${target.name}」"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }

            suspend fun exportConfigToRoot(): Boolean {
                val targetRoot = rootUri?.let { normalizeContentUriString(it) } ?: return false
                val targetUri = Uri.parse(targetRoot)
                val jsonBytes = exportConfig(context, prefs).toByteArray(Charsets.UTF_8)
                val existing = findChildByName(context, targetUri, CONFIG_EXPORT_FILE_NAME)
                if (existing != null && !context.contentResolver.deleteDocument(existing)) return false
                return createFileWithBytes(
                    context,
                    targetUri,
                    targetUri,
                    CONFIG_EXPORT_FILE_NAME,
                    "application/json",
                    jsonBytes
                )
            }

            suspend fun performConfigImport(jsonString: String, importKeys: Boolean) {
                val ok = importConfig(
                    context,
                    prefs,
                    jsonString,
                    importKeys = importKeys,
                    playlistImportMode = pendingImportPlaylistMode
                )
                refreshTrigger++
                val msg = when {
                    !ok -> "导入失败：无法解析 JSON"
                    pendingImportPlaylistCount > 0 && pendingImportPlaylistMode == ConfigPlaylistImportMode.APPEND && importKeys -> "配置已导入（播放列表已追加，含密钥）"
                    pendingImportPlaylistCount > 0 && pendingImportPlaylistMode == ConfigPlaylistImportMode.APPEND -> "配置已导入（播放列表已追加）"
                    pendingImportPlaylistCount > 0 && importKeys -> "配置已导入（播放列表已覆盖，含密钥）"
                    pendingImportPlaylistCount > 0 -> "配置已导入（播放列表已覆盖）"
                    importKeys -> "配置已导入（含密钥）"
                    else -> "配置已导入"
                }
                clearPendingConfigImportState()
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }

            fun continueConfigImportAfterPlaylistChoice() {
                val jsonString = pendingImportJson ?: return
                if (configJsonContainsKeys(jsonString)) {
                    showImportKeyConfirmDialog = true
                } else {
                    scope.launch { performConfigImport(jsonString, importKeys = true) }
                }
            }

            fun startConfigImport(jsonString: String) {
                if (jsonString.isBlank()) {
                    Toast.makeText(context, "导入失败：无法读取文件", Toast.LENGTH_SHORT).show()
                    return
                }
                pendingImportJson = jsonString
                pendingImportPlaylistCount = configJsonPlaylistCount(jsonString)
                scope.launch {
                    val hasExistingPlaylists = withContext(Dispatchers.IO) { prefs.playlists.first().isNotEmpty() }
                    if (pendingImportPlaylistCount > 0 && hasExistingPlaylists) {
                        pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
                        showImportPlaylistConfirmDialog = true
                    } else {
                        pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
                        continueConfigImportAfterPlaylistChoice()
                    }
                }
            }

            suspend fun detectGpgDecryptMode(op: GpgOpState): GpgDecryptUiMode = withContext(Dispatchers.IO) {
                val files = when (op) {
                    is GpgOpState.Decrypt -> listOf(op.fileModel)
                    is GpgOpState.BatchDecrypt -> op.list
                    else -> emptyList()
                }
                var hasSymmetric = false
                var hasPublicKey = false
                files.forEach { fileModel ->
                    val kind = context.contentResolver.openInputStreamSafe(fileModel.uri)?.use { input ->
                        GpgHelper.detectEncryptedKind(input)
                    } ?: GpgEncryptedKind.UNKNOWN
                    when (kind) {
                        GpgEncryptedKind.PUBLIC_KEY -> hasPublicKey = true
                        GpgEncryptedKind.SYMMETRIC, GpgEncryptedKind.UNKNOWN -> hasSymmetric = true
                    }
                }
                when {
                    hasSymmetric && hasPublicKey -> GpgDecryptUiMode.MIXED
                    hasPublicKey -> GpgDecryptUiMode.SECRET_KEY
                    else -> GpgDecryptUiMode.SYMMETRIC
                }
            }

            suspend fun decryptGpgFile(
                fileModel: DocumentFileModel,
                targetDirUri: String,
                treeUri: Uri?,
                symmetricPassword: CharArray?,
                keyPassphrase: CharArray?
            ): Boolean = withContext(Dispatchers.IO) {
                val encBytes = context.contentResolver.openInputStreamSafe(fileModel.uri)?.use { it.readBytes() } ?: return@withContext false
                val kind = GpgHelper.detectEncryptedKind(java.io.ByteArrayInputStream(encBytes))
                val decrypted = when (kind) {
                    GpgEncryptedKind.PUBLIC_KEY -> {
                        val secretRings = loadSecretKeyRings(context) ?: return@withContext false
                        val effectiveKeyPass = keyPassphrase ?: SecretKeyPasswordCache.get() ?: CharArray(0)
                        GpgHelper.decryptWithSecretKey(
                            java.io.ByteArrayInputStream(encBytes),
                            secretRings,
                            effectiveKeyPass
                        ) { }
                    }
                    GpgEncryptedKind.SYMMETRIC, GpgEncryptedKind.UNKNOWN -> {
                        val pwd = symmetricPassword ?: return@withContext false
                        GpgHelper.decryptSymmetric(
                            java.io.ByteArrayInputStream(encBytes),
                            pwd
                        ) { }
                    }
                } ?: return@withContext false
                val outName = fileModel.name.removeSuffix(".gpg").ifEmpty { fileModel.name + ".dec" }
                createFileWithBytes(context, Uri.parse(targetDirUri), treeUri, outName, "application/octet-stream", decrypted)
            }

            suspend fun encryptGpgFile(
                fileModel: DocumentFileModel,
                targetDirUri: String,
                treeUri: Uri?,
                symmetricPassword: CharArray?,
                publicKeyId: Long?
            ): Boolean = withContext(Dispatchers.IO) {
                val plain = context.contentResolver.openInputStreamSafe(fileModel.uri)?.use { it.readBytes() } ?: return@withContext false
                if (publicKeyId != null) {
                    val pubRings = loadPublicKeyRings(context) ?: return@withContext false
                    val pubKeyRing = findPublicKeyRing(pubRings, publicKeyId) ?: return@withContext false
                    val keyDesc = listEncryptionPublicKeyRings(pubRings).find { it.first == publicKeyId }?.second.orEmpty()
                    val safeName = keyDesc.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).trim()
                    val baseName = fileModel.name.removeSuffix(".gpg")
                    val outName = if (safeName.isNotEmpty()) "${baseName}_${safeName}.gpg" else "${baseName}.gpg"
                    val encrypted = GpgHelper.encryptWithPublicKey(plain, pubKeyRing, fileModel.name) ?: return@withContext false
                    createFileWithBytes(context, Uri.parse(targetDirUri), treeUri, outName, "application/octet-stream", encrypted)
                } else {
                    val pwd = symmetricPassword ?: return@withContext false
                    val encrypted = GpgHelper.encryptSymmetric(plain, pwd, fileModel.name) { } ?: return@withContext false
                    createFileWithBytes(context, Uri.parse(targetDirUri), treeUri, fileModel.name + ".gpg", "application/octet-stream", encrypted)
                }
            }

            val doCopyHere: () -> Unit = {
                val targetDirUri = normalizeContentUriString(displayUri)
                copyMoveLog?.invoke("[拷贝] 目标: $targetDirUri")
                val ctx = context
                val list = pendingList.toList()
                val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                scope.launch {
                    runWithProgress("拷贝", list.size) { setProgress ->
                        val (ok, fail) = withContext(Dispatchers.IO) {
                            var o = 0
                            var f = 0
                            list.forEachIndexed { index, model ->
                                if (copyDocumentTo(ctx, model.uri, Uri.parse(targetDirUri), treeUri, copyMoveLog) != null) o++
                                else f++
                                withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                            }
                            Pair(o, f)
                        }
                        copyMoveLog?.invoke("[拷贝] 结果: ok=$ok fail=$fail")
                        if (fail == 0 && ok > 0) {
                            pendingList.clear()
                            showPendingList = false
                            Toast.makeText(ctx, "已拷贝 $ok 项到本目录", Toast.LENGTH_SHORT).show()
                        } else if (ok > 0) {
                            Toast.makeText(ctx, "拷贝 $ok 项成功，$fail 项失败", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "拷贝失败", Toast.LENGTH_SHORT).show()
                        }
                        refreshTrigger++
                    }
                }
            }
            val doMoveHere: () -> Unit = {
                val targetDirUri = normalizeContentUriString(displayUri)
                copyMoveLog?.invoke("[移动] 目标: $targetDirUri")
                val ctx = context
                val list = pendingList.toList()
                val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                scope.launch {
                    runWithProgress("移动", list.size) { setProgress ->
                        val (ok, fail) = withContext(Dispatchers.IO) {
                            var o = 0
                            var f = 0
                            list.forEachIndexed { index, model ->
                                if (moveDocumentTo(ctx, model.uri, Uri.parse(targetDirUri), treeUri, copyMoveLog)) o++
                                else f++
                                withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                            }
                            Pair(o, f)
                        }
                        copyMoveLog?.invoke("[移动] 结果: ok=$ok fail=$fail")
                        if (fail == 0 && ok > 0) {
                            pendingList.clear()
                            showPendingList = false
                            Toast.makeText(ctx, "已移动 $ok 项到本目录", Toast.LENGTH_SHORT).show()
                        } else if (ok > 0) {
                            Toast.makeText(ctx, "移动 $ok 项成功，$fail 项失败", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "移动失败", Toast.LENGTH_SHORT).show()
                        }
                        refreshTrigger++
                    }
                }
            }
            val ftpRootUri = rootUri
            if (showFtpScreen && ftpRootUri != null) {
                FtpScreen(
                    manager = ftpManager,
                    treeRootUri = ftpRootUri,
                    currentDirUri = displayUri,
                    port = ftpPort,
                    password = ftpPassword,
                    timeoutMinutes = ftpTimeoutMinutes,
                    onDismiss = { ftpManager.stop(); showFtpScreen = false }
                )
            } else {
            Column(Modifier.fillMaxSize()) {
                if (gpgPubEncryptInProgress || saveInProgress) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("保存中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                }
                progressOp?.let { OperationProgressDialog(it) }
                FileBrowserScreen(
                    modifier = Modifier.fillMaxSize(),
                    currentUri = displayUri,
                    refreshTrigger = refreshTrigger,
                    dirCache = dirCache,
                    onCacheDir = { uri, items -> dirCache = dirCache + (uri to CachedDir(items)) },
                    pendingList = pendingList,
                    rootUri = rootUri,
                    listState = fileListLazyState,
                    onNavigate = { uri ->
                        fileBrowserBackStack.add(displayUri)
                        currentUri = normalizeContentUriString(uri)
                    },
                    onBack = {
                        if (fileBrowserBackStack.isNotEmpty()) {
                            currentUri = fileBrowserBackStack.removeAt(fileBrowserBackStack.lastIndex)
                        }
                    },
                    canGoBack = fileBrowserBackStack.isNotEmpty(),
                    onEmptyTrash = rootUri?.let { r ->
                        {
                            scope.launch {
                                val root = Uri.parse(normalizeContentUriString(r))
                                runWithProgress("清空回收站", null) { _ ->
                                    val ok = withContext(Dispatchers.IO) { emptyTrash(context, root, root) }
                                    if (ok) {
                                        Toast.makeText(context, "回收站已清空", Toast.LENGTH_SHORT).show()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(context, "清空失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    onRestoreFromTrash = rootUri?.let { r ->
                        { model ->
                            scope.launch {
                                val root = Uri.parse(normalizeContentUriString(r))
                                runWithProgress("恢复", null) { _ ->
                                    val ok = withContext(Dispatchers.IO) { restoreFromTrash(context, model.uri, root, root) }
                                    if (ok) {
                                        Toast.makeText(context, "已恢复到根目录", Toast.LENGTH_SHORT).show()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(context, "恢复失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    filterVisible = filterVisible,
                    hideDotFiles = hideDotFiles,
                    isViewingTrash = cachedTrashUri != null && (displayUri == cachedTrashUri.toString() || isInsideDirectory(Uri.parse(displayUri), cachedTrashUri!!)),
                    preferredExternalPackages = preferredExternalPackages,
                    onOpenFile = { uri, name, isEncrypted ->
                        viewingFile = Triple(uri, name, false)
                    },
                    onOpenWithOtherApp = { uri, name, packageName, rememberChoice ->
                        val opened = launchExternalOpen(context, uri, packageName)
                        if (opened) {
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
                                Toast.makeText(context, "记住的外部应用不可用，已恢复默认打开方式", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "没有可打开的应用", Toast.LENGTH_SHORT).show()
                            }
                        }
                        opened
                    },
                    onClearExternalOpenPreference = { name ->
                        val extension = fileExtensionKey(name) ?: return@FileBrowserScreen
                        scope.launch { prefs.clearExternalOpenPackageForExtension(extension) }
                    },
                    onOpenMarkdownView = { uri, name, encrypted ->
                        markdownViewFile = Triple(uri, name, encrypted)
                    },
                    onAddToPendingList = { pendingList.add(it) },
                    onRemoveFromPendingList = { pendingList.remove(it) },
                    onShowPendingList = { showPendingList = it },
                    onRefresh = { refreshTrigger++ },
                    onOpenConfig = { showConfigDialog = true },
                    onOpenAbout = { showAboutDialog = true },
                    onOpenQuickNote = { requestOpenQuickNote(false, SecretKeyPasswordCache.get()?.let { String(it) }) },
                    onCreateQuickNote = { requestOpenQuickNote(true, SecretKeyPasswordCache.get()?.let { String(it) }) },
                    onOpenDictionary = { showDictionaryScreen = true },
                    onRequestGpgDecrypt = { fileModel, dirUri ->
                        gpgPassword = ""
                        gpgDecryptMode = null
                        gpgDecryptAutoTried = false
                        gpgEncryptSelectedKeyId = null
                        gpgState = GpgOpState.Decrypt(fileModel, dirUri)
                    },
                    onRequestGpgEncrypt = { fileModel, dirUri ->
                        gpgPassword = ""
                        gpgDecryptMode = null
                        gpgDecryptAutoTried = false
                        gpgEncryptSelectedKeyId = null
                        gpgState = GpgOpState.Encrypt(fileModel, dirUri)
                    },
                    onRequestQuickObfuscate = { model ->
                        quickObfuscateOp = model to true
                        quickObfuscatePassword = ""
                    },
                    onRequestQuickDeobfuscate = { model ->
                        quickObfuscateOp = model to false
                        quickObfuscatePassword = ""
                    },
                    onConfirmDelete = { model, deletePermanently ->
                        scope.launch {
                            val label = if (deletePermanently) "删除" else "移到回收站"
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
                                    Toast.makeText(context, if (deletePermanently) "已删除" else "已移到回收站", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show()
                                }
                                refreshTrigger++
                            }
                        }
                    },
                    onOpenFtp = {
                        if (rootUri != null) showFtpScreen = true
                        else Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
                    },
                    onOpenFileShare = {
                        if (rootUri != null) showFileShareScreen = true
                        else Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
                    },
                    onShareFileToGit = { model ->
                        shareFileToGitTarget = model
                    },
                    onUnzipRequest = { zipUnzipTarget = it },
                    onRequestMdZipView = { mdZipTarget = it },
                    onRequestHtmlZipView = { htmlZipTarget = it },
                    onRequestEpubView = { epubTarget = it },
                    onRequestTxtView = { txtTarget = it },
                    onRequestPicZipView = { picZipTarget = it },
                    onRequestPdfView = { pdfViewState = Pair(it.uri.toString(), it.name) },
                    onCompressToZipRequest = { zipCompressTarget = it },
                    onCompressTo7zRequest = { sevenZCompressTarget = it },
                    onRequestPassProtect = { model -> passProtectTarget = model },
                    onRequestPassView = { model ->
                        passViewTarget = model
                        passViewPassword = ""
                        passViewTriedCache = false
                    },
                    onRequestPassEdit = { model, dirUri ->
                        passEditRequest = Pair(model, dirUri)
                        passEditTriedCache = false
                    },
                    onRequestImportConfig = { model ->
                        scope.launch {
                            val jsonString = withContext(Dispatchers.IO) {
                                try {
                                    context.contentResolver.openInputStream(model.uri)?.use { it.bufferedReader().readText() } ?: ""
                                } catch (_: Exception) {
                                    ""
                                }
                            }
                            startConfigImport(jsonString)
                        }
                    },
                    onRequestImportStarDict = { model ->
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                importStarDict(context, model.uri, model.name)
                            }
                            result.onSuccess {
                                Toast.makeText(context, "词典已导入：${it.name}（${it.wordCount} 词条）", Toast.LENGTH_LONG).show()
                            }.onFailure {
                                Toast.makeText(context, "导入失败：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    playbackState = playbackState,
                    onOpenPlaybackScreen = { showPlaybackScreen = true }
                )
            }
            if (showPendingList) {
                PendingListScreen(
                    pendingList = pendingList,
                    currentDirPath = currentDirPath,
                    onRemove = { pendingList.remove(it) },
                    onCopyHere = doCopyHere,
                    onMoveHere = doMoveHere,
                    onRequestDelete = { showPendingDeleteConfirm = true },
                    onRequestBatchObfuscate = {
                        val list = pendingList.filter { !it.isDirectory && !isQuickObfuscatedFileName(it.name) }
                        if (list.isEmpty()) Toast.makeText(context, "没有可混淆的文件（请勿选已 .qx 或文件夹）", Toast.LENGTH_SHORT).show()
                        else batchObfuscateOp = list to true
                    },
                    onRequestBatchDeobfuscate = {
                        val list = pendingList.filter { !it.isDirectory && isQuickObfuscatedFileName(it.name) }
                        if (list.isEmpty()) Toast.makeText(context, "没有可去混淆的文件（请只选 .qx 文件）", Toast.LENGTH_SHORT).show()
                        else batchObfuscateOp = list to false
                    },
                    onRequestBatchGpgEncrypt = {
                        if (pendingList.any { it.name.endsWith(".gpg", ignoreCase = true) }) {
                            Toast.makeText(context, "列表中存在 .gpg 文件，无法批量加密", Toast.LENGTH_SHORT).show()
                        } else {
                            val list = pendingList.filter { !it.isDirectory }
                            if (list.isEmpty()) Toast.makeText(context, "没有可加密的文件", Toast.LENGTH_SHORT).show()
                            else {
                                gpgPassword = ""
                                gpgDecryptMode = null
                                gpgDecryptAutoTried = false
                                gpgEncryptSelectedKeyId = null
                                gpgState = GpgOpState.BatchEncrypt(list, displayUri)
                            }
                        }
                    },
                    onRequestBatchGpgDecrypt = {
                        if (pendingList.any { !it.name.endsWith(".gpg", ignoreCase = true) }) {
                            Toast.makeText(context, "列表中存在非 .gpg 文件，无法批量解密", Toast.LENGTH_SHORT).show()
                        } else {
                            val list = pendingList.filter { !it.isDirectory }
                            if (list.isEmpty()) Toast.makeText(context, "没有可解密的文件", Toast.LENGTH_SHORT).show()
                            else {
                                gpgPassword = ""
                                gpgDecryptMode = null
                                gpgDecryptAutoTried = false
                                gpgEncryptSelectedKeyId = null
                                gpgState = GpgOpState.BatchDecrypt(list, displayUri)
                            }
                        }
                    },
                    onRequestCompressToZip = {
                        if (pendingList.isEmpty()) {
                            Toast.makeText(context, "列表为空", Toast.LENGTH_SHORT).show()
                        } else {
                            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                            pendingCompressZipName = "archive_$ts.zip"
                            pendingCompressPassword = ""
                            showPendingCompressToZip = true
                        }
                    },
                    onRequestCompressTo7z = {
                        if (pendingList.isEmpty()) {
                            Toast.makeText(context, "列表为空", Toast.LENGTH_SHORT).show()
                        } else {
                            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                            pendingCompress7zName = "archive_$ts.7z"
                            pendingCompress7zPassword = ""
                            showPendingCompressTo7z = true
                        }
                    },
                    onClearFilteredList = { toRemove ->
                        val snapshot = toRemove.toList()
                        pendingList.removeAll(snapshot)
                    },
                    onAddToPlayback = { audioList ->
                        if (audioList.isEmpty()) return@PendingListScreen
                        pendingPlaybackAudioList = audioList
                        if (playlists.isEmpty()) {
                            scope.launch { createNewPlaybackPlaylist(audioList) }
                        } else {
                            showPlaybackTargetDialog = true
                        }
                    },
                    onDismiss = { showPendingList = false }
                )
            }
            if (showPlaybackTargetDialog && pendingPlaybackAudioList.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { clearPendingPlaybackTargetState() },
                    title = { Text("加入播放") },
                    text = {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "请选择要把 ${pendingPlaybackAudioList.size} 首音频加入到哪里。",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            playlists.forEach { playlist ->
                                OutlinedButton(
                                    onClick = {
                                        scope.launch { appendToPlaybackPlaylist(playlist, pendingPlaybackAudioList) }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(playlist.name, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            "${playlist.trackCount} 首",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch { createNewPlaybackPlaylist(pendingPlaybackAudioList) }
                        }) { Text("新建列表") }
                    },
                    dismissButton = {
                        TextButton(onClick = { clearPendingPlaybackTargetState() }) { Text("取消") }
                    }
                )
            }
            if (showPlaybackScreen) {
                PlaybackScreen(
                    prefs = prefs,
                    playbackState = playbackState,
                    onStopPlayback = {
                        val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_STOP)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                    },
                    onPlayPrev = {
                        val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_PREV)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                    },
                    onPlayNext = {
                        val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_NEXT)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                    },
                    onPlayPause = {
                        val action = if (playbackState?.isPlaying == true) ACTION_PAUSE else ACTION_RESUME
                        val intent = Intent(context, PlaybackService::class.java).setAction(action)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                    },
                    onSeek = { positionMs ->
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = ACTION_SEEK
                            putExtra(EXTRA_POSITION_MS, positionMs)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                    },
                    onDismiss = { if (initialLaunchTarget == "player") (context as? Activity)?.finish() else showPlaybackScreen = false }
                )
            }
            if (showPendingDeleteConfirm && pendingList.isNotEmpty()) {
                val toDelete = pendingList.toList()
                var deletePermanently by remember { mutableStateOf(false) }
                val hasRoot = rootUri != null
                AlertDialog(
                    onDismissRequest = { showPendingDeleteConfirm = false },
                    title = { Text("确认删除") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text("确定要删除以下 ${toDelete.size} 项吗？", color = MaterialTheme.colorScheme.onSurface)
                            if (hasRoot) {
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.RadioButton(
                                        selected = !deletePermanently,
                                        onClick = { deletePermanently = false }
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text("移到回收站（可恢复）", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.RadioButton(
                                        selected = deletePermanently,
                                        onClick = { deletePermanently = true }
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text("完全删除（不可恢复）", style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Text("此目录无回收站，将完全删除。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(12.dp))
                            toDelete.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        Modifier.size(20.dp),
                                        tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch {
                                    val label = if (deletePermanently) "删除" else "移到回收站"
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
                                    toDelete.forEach { pendingList.remove(it) }
                                    refreshTrigger++
                                    showPendingDeleteConfirm = false
                                    showPendingList = false
                                    Toast.makeText(context, if (deletePermanently) "已删除 ${toDelete.size} 项" else "已移到回收站 ${toDelete.size} 项", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(if (hasRoot && !deletePermanently) "移到回收站" else "确定删除") }
                    },
                    dismissButton = { TextButton(onClick = { showPendingDeleteConfirm = false }) { Text("取消") } }
                )
            }
            if (showAboutDialog) {
                AboutDialog(onDismiss = { showAboutDialog = false })
            }
            if (showImportPlaylistConfirmDialog && pendingImportJson != null) {
                AlertDialog(
                    onDismissRequest = { clearPendingConfigImportState() },
                    title = { Text("导入播放列表") },
                    text = {
                        Text(
                            "导入配置包含 ${pendingImportPlaylistCount} 个播放列表。当前已有播放列表，导入时要覆盖当前列表，还是追加到当前列表？",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            showImportPlaylistConfirmDialog = false
                            pendingImportPlaylistMode = ConfigPlaylistImportMode.APPEND
                            continueConfigImportAfterPlaylistChoice()
                        }) { Text("追加") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                showImportPlaylistConfirmDialog = false
                                pendingImportPlaylistMode = ConfigPlaylistImportMode.OVERWRITE
                                continueConfigImportAfterPlaylistChoice()
                            }) { Text("覆盖") }
                            TextButton(onClick = { clearPendingConfigImportState() }) { Text("取消") }
                        }
                    }
                )
            }
            if (showImportKeyConfirmDialog && pendingImportJson != null) {
                val jsonToImport = pendingImportJson!!
                AlertDialog(
                    onDismissRequest = {
                        clearPendingConfigImportState()
                    },
                    title = { Text("导入配置") },
                    text = {
                        Text(
                            "导入的配置包含公钥/私钥，会覆盖本机现有密钥。请选择：",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showImportKeyConfirmDialog = false
                                scope.launch {
                                    performConfigImport(jsonToImport, importKeys = true)
                                }
                            }
                        ) { Text("全部替换") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showImportKeyConfirmDialog = false
                                scope.launch {
                                    performConfigImport(jsonToImport, importKeys = false)
                                }
                            }
                        ) { Text("跳过密钥（保留本机密钥）") }
                    }
                )
            }
            if (showCacheManagementDialog) {
                CacheManagementDialog(context = context, onDismiss = { showCacheManagementDialog = false })
            }
            if (showConfigDialog) {
                ConfigDialog(
                    onDismiss = { showConfigDialog = false },
                    filterVisible = filterVisible,
                    onFilterVisibleChange = { scope.launch { prefs.setFilterVisible(it) } },
                    hideDotFiles = hideDotFiles,
                    onHideDotFilesChange = { scope.launch { prefs.setHideDotFiles(it) } },
                    startupDecryptKey = startupDecryptKey,
                    onStartupDecryptKeyChange = { enabled ->
                        scope.launch { prefs.setStartupDecryptKey(enabled) }
                        if (!enabled) SecretKeyPasswordCache.clear()
                    },
                    viewerPreviewBytes = viewerPreviewBytes,
                    onViewerPreviewBytesChange = { scope.launch { prefs.setViewerPreviewBytes(it) } },
                    ftpPassword = ftpPassword ?: "",
                    onFtpPasswordChange = { s ->
                        ftpPassword = s.ifBlank { null }
                        scope.launch { prefs.setFtpPassword(s.ifBlank { null }) }
                    },
                    ftpTimeoutMinutes = ftpTimeoutMinutes,
                    onFtpTimeoutMinutesChange = { scope.launch { prefs.setFtpTimeoutMinutes(it) } },
                    onOpenGitConfig = { showConfigDialog = false; showGitConfigDialog = true },
                    onManageKeys = { showConfigDialog = false; showKeyManagementDialog = true },
                    onOpenCacheManagement = { showConfigDialog = false; showCacheManagementDialog = true },
                    onExportConfig = {
                        scope.launch {
                            val ok = exportConfigToRoot()
                            Toast.makeText(context, if (ok) "配置已导出到根目录" else "导出失败", Toast.LENGTH_SHORT).show()
                            if (ok) refreshTrigger++
                        }
                    },
                    onChangeRoot = {
                        val r = rootUri
                        if (r == null) {
                            treeLauncher.launch(null)
                            return@ConfigDialog
                        }
                        scope.launch {
                            val count = withContext(Dispatchers.IO) {
                                cachedTrashUri?.let { trash ->
                                    val doc = if (trash.toString().contains("/tree/")) {
                                        DocumentFile.fromTreeUri(context, trash)
                                    } else {
                                        DocumentFile.fromSingleUri(context, trash)
                                    }
                                    doc?.listFilesSafe()?.size ?: 0
                                } ?: 0
                            }
                            withContext(Dispatchers.Main.immediate) {
                                if (count > 0) showChangeRootConfirm = true
                                else treeLauncher.launch(null)
                            }
                        }
                    }
                )
            }
            if (showGitConfigDialog) {
                GitConfigDialog(
                    prefs = prefs,
                    rootUri = rootUri?.let { normalizeContentUriString(it) },
                    onDismiss = {
                        showGitConfigDialog = false
                        refreshTrigger++
                    }
                )
            }
            if (showKeyManagementDialog) {
                KeyManagementDialog(
                    context = context,
                    onDismiss = { showKeyManagementDialog = false },
                    onKeysChanged = { refreshTrigger++ },
                    onOpenPubkeyShare = {
                        showKeyManagementDialog = false
                        showPubkeyShareScreen = true
                    }
                )
            }
            if (showPubkeyShareScreen) {
                PubkeyShareScreen(
                    prefs = prefs,
                    rootUri = rootUri?.let { normalizeContentUriString(it) },
                    onDismiss = {
                        showPubkeyShareScreen = false
                        refreshTrigger++
                    }
                )
            }
            if (showFileShareScreen) {
                FileShareScreen(
                    prefs = prefs,
                    rootUri = rootUri?.let { normalizeContentUriString(it) },
                    onDismiss = {
                        showFileShareScreen = false
                        refreshTrigger++
                    }
                )
            }
            // 共享文件到 Git
            shareFileToGitTarget?.let { model ->
                AlertDialog(
                    onDismissRequest = { shareFileToGitTarget = null },
                    title = { Text("共享到 Git") },
                    text = { Text("确定要将「${model.name}」共享到 .sysgit/share/ 吗？\n将自动同步、复制并推送。") },
                    confirmButton = {
                        TextButton(onClick = {
                            val target = model
                            shareFileToGitTarget = null
                            val r = rootUri?.let { normalizeContentUriString(it) }
                            if (r == null) {
                                Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            shareGitLogs.clear()
                            shareGitInProgress = true
                            shareGitDone = false
                            scope.launch {
                                val repoUrl = prefs.gitRepoUrl.first() ?: ""
                                val userName = prefs.gitUserName.first() ?: ""
                                val httpsPassword = prefs.gitHttpsPassword.first() ?: ""
                                if (repoUrl.isBlank()) {
                                    shareGitLogs.add("错误: 请先配置 Git 仓库")
                                    shareGitInProgress = false
                                    shareGitDone = true
                                    return@launch
                                }
                                // 同步
                                shareGitLogs.add("正在同步仓库...")
                                val syncResult = withContext(Dispatchers.IO) {
                                    cloneToTree(context, r, repoUrl,
                                        userName = userName.ifBlank { null },
                                        httpsPassword = httpsPassword.ifBlank { null },
                                        log = { msg -> shareGitLogs.add(msg) })
                                }
                                if (syncResult.isFailure) {
                                    shareGitLogs.add("错误: 同步失败 - ${syncResult.exceptionOrNull()?.message}")
                                    shareGitInProgress = false
                                    shareGitDone = true
                                    return@launch
                                }
                                // 复制到 share
                                shareGitLogs.add("正在复制文件到 share 目录...")
                                val copied = withContext(Dispatchers.IO) {
                                    copyFileToShare(context, r, target.uri, target.name)
                                }
                                if (!copied) {
                                    shareGitLogs.add("错误: 复制到 share 目录失败")
                                    shareGitInProgress = false
                                    shareGitDone = true
                                    return@launch
                                }
                                shareGitLogs.add("文件已复制到 .sysgit/share/")
                                // 提交推送
                                shareGitLogs.add("正在提交并推送...")
                                val pushResult = withContext(Dispatchers.IO) {
                                    commitAndPush(context, r, repoUrl,
                                        commitMessage = "共享文件: ${target.name}",
                                        userName = userName.ifBlank { null },
                                        httpsPassword = httpsPassword.ifBlank { null },
                                        log = { msg -> shareGitLogs.add(msg) })
                                }
                                if (pushResult.isSuccess) {
                                    shareGitLogs.add("已共享并推送成功")
                                } else {
                                    shareGitLogs.add("错误: 推送失败 - ${pushResult.exceptionOrNull()?.message}")
                                }
                                shareGitInProgress = false
                                shareGitDone = true
                            }
                        }) { Text("共享") }
                    },
                    dismissButton = {
                        TextButton(onClick = { shareFileToGitTarget = null }) { Text("取消") }
                    }
                )
            }
            // 共享到 Git 日志窗口
            if (shareGitLogs.isNotEmpty() && (shareGitInProgress || shareGitDone)) {
                AlertDialog(
                    onDismissRequest = { if (!shareGitInProgress) { shareGitDone = false; shareGitLogs.clear() } },
                    title = { Text("共享到 Git") },
                    text = {
                        val logScrollState = rememberScrollState()
                        LaunchedEffect(shareGitLogs.size) {
                            logScrollState.animateScrollTo(logScrollState.maxValue)
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(logScrollState)
                        ) {
                            for (line in shareGitLogs) {
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (line.startsWith("错误") || line.startsWith("[调试]"))
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    confirmButton = {
                        if (shareGitInProgress) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { shareGitDone = false; shareGitLogs.clear() }) { Text("关闭") }
                        }
                    }
                )
            }
            quickObfuscateOp?.let { (model, isObfuscate) ->
                QuickObfuscatePasswordDialog(
                    isObfuscate = isObfuscate,
                    fileName = model.name,
                    password = quickObfuscatePassword,
                    inProgress = quickObfuscateInProgress,
                    onPasswordChange = { quickObfuscatePassword = it },
                    onConfirm = { pwd ->
                        quickObfuscateInProgress = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                if (isObfuscate) quickObfuscate(context, model.uri, pwd.toCharArray())
                                else quickDeobfuscate(context, model.uri, pwd.toCharArray())
                            }
                            quickObfuscateInProgress = false
                            quickObfuscateOp = null
                            quickObfuscatePassword = ""
                            if (ok) {
                                Toast.makeText(context, if (isObfuscate) "已混淆" else "已去混淆", Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                            } else {
                                Toast.makeText(context, if (isObfuscate) "混淆失败" else "去混淆失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDismiss = { quickObfuscateOp = null; quickObfuscatePassword = "" }
                )
            }
            batchObfuscateOp?.let { (list, isObfuscate) ->
                QuickObfuscatePasswordDialog(
                    isObfuscate = isObfuscate,
                    fileName = "共 ${list.size} 个文件",
                    password = batchObfuscatePassword,
                    inProgress = batchObfuscateInProgress,
                    onPasswordChange = { batchObfuscatePassword = it },
                    onConfirm = { pwd ->
                        batchObfuscateInProgress = true
                        scope.launch {
                            runWithProgress(if (isObfuscate) "混淆" else "去混淆", list.size) { setProgress ->
                                withContext(Dispatchers.IO) {
                                    list.forEachIndexed { index, model ->
                                        if (isObfuscate) quickObfuscate(context, model.uri, pwd.toCharArray())
                                        else quickDeobfuscate(context, model.uri, pwd.toCharArray())
                                        withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                                    }
                                }
                            }
                            batchObfuscateInProgress = false
                            batchObfuscateOp = null
                            batchObfuscatePassword = ""
                            Toast.makeText(context, if (isObfuscate) "已混淆 ${list.size} 个文件" else "已去混淆 ${list.size} 个文件", Toast.LENGTH_SHORT).show()
                            pendingList.clear()
                            showPendingList = false
                            refreshTrigger++
                        }
                    },
                    onDismiss = { batchObfuscateOp = null; batchObfuscatePassword = "" }
                )
            }
            // ---- 密码保护：加密 md/rst -> .pass ----
            passProtectTarget?.let { model ->
                AlertDialog(
                    onDismissRequest = { if (!passProtectInProgress) passProtectTarget = null },
                    title = { Text("密码保护") },
                    text = { Text("将 ${model.name} 用默认公钥加密为 ${model.name}.pass，原文件将被删除。") },
                    confirmButton = {
                        Button(
                            onClick = {
                                passProtectInProgress = true
                                val ctx = context
                                val dirUri = normalizeContentUriString(displayUri)
                                val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        val secRings = loadSecretKeyRings(ctx)
                                        if (secRings == null) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(ctx, "未找到默认密钥，请先生成密钥对", Toast.LENGTH_LONG).show()
                                            }
                                            return@withContext false
                                        }
                                        val defaultKeyId = secRings.iterator().asSequence().firstOrNull()?.publicKey?.keyID
                                        if (defaultKeyId == null) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(ctx, "未找到默认密钥", Toast.LENGTH_LONG).show()
                                            }
                                            return@withContext false
                                        }
                                        val pubRings = loadPublicKeyRings(ctx)
                                        val pubKeyRing = findPublicKeyRing(pubRings, defaultKeyId)
                                        if (pubKeyRing == null) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(ctx, "未找到默认公钥", Toast.LENGTH_LONG).show()
                                            }
                                            return@withContext false
                                        }
                                        val plain = ctx.contentResolver.openInputStreamSafe(model.uri)?.use { it.readBytes() }
                                            ?: return@withContext false
                                        val encrypted = GpgHelper.encryptWithPublicKey(plain, pubKeyRing, model.name)
                                            ?: return@withContext false
                                        val outName = model.name + ".pass"
                                        val created = createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", encrypted)
                                        if (created) {
                                            ctx.contentResolver.deleteDocument(model.uri)
                                        }
                                        created
                                    }
                                    passProtectInProgress = false
                                    passProtectTarget = null
                                    if (ok) {
                                        Toast.makeText(ctx, "已加密为 ${model.name}.pass", Toast.LENGTH_SHORT).show()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(ctx, "密码保护失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !passProtectInProgress
                        ) { Text(if (passProtectInProgress) "加密中…" else "确定") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { passProtectTarget = null },
                            enabled = !passProtectInProgress
                        ) { Text("取消") }
                    }
                )
            }
            // ---- 密码保护：查看 .pass 文件 ----
            passViewTarget?.let { model ->
                val secRings = loadSecretKeyRings(context)
                if (secRings == null) {
                    AlertDialog(
                        onDismissRequest = { passViewTarget = null },
                        title = { Text("查看密码") },
                        text = { Text("未找到默认私钥，无法解密。请先生成密钥对。") },
                        confirmButton = {
                            Button(onClick = { passViewTarget = null }) { Text("确定") }
                        }
                    )
                } else {
                    LaunchedEffect(model) {
                        if (passViewTriedCache) return@LaunchedEffect
                        val cached = SecretKeyPasswordCache.get() ?: run { passViewTriedCache = true; return@LaunchedEffect }
                        passViewTriedCache = true
                        passViewInProgress = true
                        val decrypted = withContext(Dispatchers.IO) {
                            val rings = loadSecretKeyRings(context) ?: return@withContext null
                            context.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                                GpgHelper.decryptWithSecretKey(input, rings, cached) { _ -> }
                            }
                        }
                        passViewInProgress = false
                        if (decrypted != null) {
                            passViewTarget = null
                            val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                            passContentView = PassDecryptedContent(innerName, decrypted)
                        } else {
                            Toast.makeText(context, "解密失败，请检查密码", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (SecretKeyPasswordCache.get() == null || passViewTriedCache) {
                    GpgPasswordDialog(
                        isDecrypt = true,
                        fileName = model.name,
                        password = passViewPassword,
                        passwordLabel = "密钥密码",
                        inProgress = passViewInProgress,
                        onPasswordChange = { if (!passViewInProgress) passViewPassword = it },
                        onConfirm = { pwd ->
                            if (passViewInProgress) return@GpgPasswordDialog
                            passViewInProgress = true
                            val ctx = context
                            scope.launch {
                                val decrypted = withContext(Dispatchers.IO) {
                                    val rings = loadSecretKeyRings(ctx) ?: return@withContext null
                                    ctx.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                                        GpgHelper.decryptWithSecretKey(
                                            input, rings, pwd.toCharArray()
                                        ) { _ -> }
                                    }
                                }
                                passViewInProgress = false
                                if (decrypted != null) {
                                    passViewTarget = null
                                    passViewPassword = ""
                                    // 根据内层扩展名确定渲染方式
                                    val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                                    passContentView = PassDecryptedContent(innerName, decrypted)
                                } else {
                                    Toast.makeText(ctx, "解密失败，请检查密码", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDismiss = {
                            if (!passViewInProgress) {
                                passViewTarget = null
                                passViewPassword = ""
                            }
                        }
                    )
                    }
                }
            }
            // ---- 密码保护：直接编辑 .pass 文件（解密后进入编辑界面） ----
            passEditRequest?.let { (model, dirUri) ->
                val secRings = loadSecretKeyRings(context)
                if (secRings == null) {
                    AlertDialog(
                        onDismissRequest = { passEditRequest = null },
                        title = { Text("直接编辑") },
                        text = { Text("未找到默认私钥，无法解密。请先生成密钥对。") },
                        confirmButton = {
                            Button(onClick = { passEditRequest = null }) { Text("确定") }
                        }
                    )
                } else {
                    LaunchedEffect(model) {
                        if (passEditTriedCache) return@LaunchedEffect
                        val cached = SecretKeyPasswordCache.get() ?: run { passEditTriedCache = true; return@LaunchedEffect }
                        passEditTriedCache = true
                        passEditInProgress = true
                        val decrypted = withContext(Dispatchers.IO) {
                            val rings = loadSecretKeyRings(context) ?: return@withContext null
                            context.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                                GpgHelper.decryptWithSecretKey(input, rings, cached) { _ -> }
                            }
                        }
                        passEditInProgress = false
                        if (decrypted != null) {
                            passEditRequest = null
                            val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                            val tree = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                            passEditState = PassEditState(model, dirUri, tree, innerName, decrypted)
                        } else {
                            Toast.makeText(context, "解密失败，请检查密码", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (SecretKeyPasswordCache.get() == null || passEditTriedCache) {
                    GpgPasswordDialog(
                        isDecrypt = true,
                        fileName = model.name,
                        password = passEditPassword,
                        passwordLabel = "密钥密码",
                        inProgress = passEditInProgress,
                        onPasswordChange = { if (!passEditInProgress) passEditPassword = it },
                        onConfirm = { pwd ->
                            if (passEditInProgress) return@GpgPasswordDialog
                            passEditInProgress = true
                            val ctx = context
                            scope.launch {
                                val decrypted = withContext(Dispatchers.IO) {
                                    val rings = loadSecretKeyRings(ctx) ?: return@withContext null
                                    ctx.contentResolver.openInputStreamSafe(model.uri)?.use { input ->
                                        GpgHelper.decryptWithSecretKey(
                                            input, rings, pwd.toCharArray()
                                        ) { _ -> }
                                    }
                                }
                                passEditInProgress = false
                                if (decrypted != null) {
                                    passEditRequest = null
                                    val innerName = model.name.removeSuffix(".pass").removeSuffix(".PASS")
                                    val tree = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                                    passEditState = PassEditState(model, dirUri, tree, innerName, decrypted)
                                } else {
                                    Toast.makeText(ctx, "解密失败，请检查密码", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDismiss = {
                            if (!passEditInProgress) {
                                passEditRequest = null
                            }
                        }
                    )
                    }
                }
            }
            if (quickNotePasswordRequired) {
                GpgPasswordDialog(
                    isDecrypt = true,
                    fileName = QUICK_NOTE_GPG_FILE_NAME,
                    password = quickNotePassword,
                    passwordLabel = "密钥密码",
                    inProgress = quickNoteInProgress,
                    onPasswordChange = { if (!quickNoteInProgress) quickNotePassword = it },
                    onConfirm = { pwd ->
                        if (!quickNoteInProgress) requestOpenQuickNote(quickNoteStartWithAddDialog, pwd)
                    },
                    onDismiss = {
                        if (!quickNoteInProgress) {
                            resetQuickNotePromptState()
                            quickNoteStartWithAddDialog = false
                        }
                    }
                )
            }
            zipUnzipTarget?.let { target ->
                val parentDirUri = Uri.parse(displayUri)
                val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                val encrypted = zipUnzipEncrypted
                AlertDialog(
                    onDismissRequest = { zipUnzipTarget = null; zipUnzipPassword = "" },
                    title = { Text("解压压缩包") },
                    text = {
                        Column {
                            Text("确定将 ${target.name} 解压到当前目录？", color = MaterialTheme.colorScheme.onSurface)
                            if (encrypted == true) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = zipUnzipPassword,
                                    onValueChange = { zipUnzipPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("密码（加密压缩包）") },
                                    singleLine = true
                                )
                            } else if (encrypted == null) {
                                Spacer(Modifier.height(8.dp))
                                Text("正在检测…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (encrypted == true && zipUnzipPassword.isBlank()) return@Button
                                scope.launch {
                                    progressOp = OperationProgress("解压", 0, null)
                                    delay(50)
                                    val pwd = if (encrypted == true) zipUnzipPassword.toCharArray() else null
                                    val result = withContext(Dispatchers.IO) {
                                        unzipToParent(
                                            context,
                                            target.uri,
                                            parentDirUri,
                                            treeUri,
                                            target.name,
                                            pwd
                                        ) { cur, tot ->
                                            scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress("解压", cur, tot) }
                                        }
                                    }
                                    // 延迟再关闭进度条，避免 setProgress 的 Main.immediate 晚于本行执行导致进度条不消失
                                    delay(120)
                                    progressOp = null
                                    when (result) {
                                        is UnzipResult.Success -> {
                                            zipUnzipTarget = null
                                            zipUnzipPassword = ""
                                            zipUnzipEncrypted = null
                                            Toast.makeText(context, "解压完成", Toast.LENGTH_SHORT).show()
                                            refreshTrigger++
                                        }
                                        is UnzipResult.PasswordRequired -> {
                                            // 需要密码但未提供，显示密码输入框让用户重试
                                            zipUnzipEncrypted = true
                                            Toast.makeText(context, "请输入密码", Toast.LENGTH_SHORT).show()
                                        }
                                        is UnzipResult.WrongPassword -> {
                                            // 密码错误，保留对话框让用户重试
                                            zipUnzipEncrypted = true
                                            Toast.makeText(context, "密码错误，请重试", Toast.LENGTH_SHORT).show()
                                        }
                                        is UnzipResult.CorruptedFile -> {
                                            zipUnzipTarget = null
                                            zipUnzipPassword = ""
                                            zipUnzipEncrypted = null
                                            Toast.makeText(context, "解压失败：文件已损坏", Toast.LENGTH_SHORT).show()
                                        }
                                        is UnzipResult.UnsupportedFormat -> {
                                            zipUnzipTarget = null
                                            zipUnzipPassword = ""
                                            zipUnzipEncrypted = null
                                            Toast.makeText(context, "解压失败：不支持的压缩格式", Toast.LENGTH_SHORT).show()
                                        }
                                        is UnzipResult.IOError -> {
                                            zipUnzipTarget = null
                                            zipUnzipPassword = ""
                                            zipUnzipEncrypted = null
                                            val msg = result.message ?: "未知错误"
                                            Toast.makeText(context, "解压失败：$msg", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) { Text("解压") }
                    },
                    dismissButton = { TextButton(onClick = { zipUnzipTarget = null; zipUnzipPassword = "" }) { Text("取消") } }
                )
            }
            // ---- .md.zip 密码输入对话框（仅加密 zip 时弹出） ----
            mdZipTarget?.let { target ->
                val encrypted = mdZipEncrypted
                if (encrypted == true) {
                    AlertDialog(
                        onDismissRequest = { if (!mdZipInProgress) { mdZipTarget = null; mdZipPassword = "" } },
                        title = { Text("查看压缩 Markdown") },
                        text = {
                            Column {
                                Text("${target.name} 已加密，请输入密码。", color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = mdZipPassword,
                                    onValueChange = { if (!mdZipInProgress) mdZipPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("ZIP 密码") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    enabled = !mdZipInProgress
                                )
                                if (mdZipInProgress) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (mdZipInProgress || mdZipPassword.isBlank()) return@Button
                                    mdZipInProgress = true
                                    val pwd = mdZipPassword.toCharArray()
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            extractMdZipToCache(context, target.uri, pwd, target.name)
                                        }
                                        mdZipInProgress = false
                                        if (result != null) {
                                            mdZipTarget = null
                                            mdZipPassword = ""
                                            mdZipViewState = MdZipViewState(
                                                targetFile = result.targetFile,  // 可能为 null
                                                contentDir = result.contentDir,
                                                zipFileName = target.name,
                                                zipUri = target.uri,
                                                isEncrypted = true
                                            )
                                        } else {
                                            Toast.makeText(context, "解压失败（请检查密码）", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { if (!mdZipInProgress) { mdZipTarget = null; mdZipPassword = "" } }
                            ) { Text("取消") }
                        }
                    )
                } else if (encrypted == null) {
                    // 正在检测加密状态或正在解压非加密 zip
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("查看压缩 Markdown") },
                        text = {
                            Column {
                                Text("正在处理 ${target.name}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        },
                        confirmButton = {}
                    )
                }
            }
            // ---- .html.zip 密码输入对话框 ----
            htmlZipTarget?.let { target ->
                val encrypted = htmlZipEncrypted
                if (encrypted == true) {
                    AlertDialog(
                        onDismissRequest = { if (!htmlZipInProgress) { htmlZipTarget = null; htmlZipPassword = "" } },
                        title = { Text("查看压缩 HTML") },
                        text = {
                            Column {
                                Text("${target.name} 已加密，请输入密码。", color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = htmlZipPassword,
                                    onValueChange = { if (!htmlZipInProgress) htmlZipPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("ZIP 密码") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    enabled = !htmlZipInProgress
                                )
                                if (htmlZipInProgress) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (htmlZipInProgress || htmlZipPassword.isBlank()) return@Button
                                    htmlZipInProgress = true
                                    val pwd = htmlZipPassword.toCharArray()
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            extractHtmlZipToCache(context, target.uri, pwd, target.name)
                                        }
                                        htmlZipInProgress = false
                                        if (result != null) {
                                            htmlZipTarget = null
                                            htmlZipPassword = ""
                                            htmlZipViewState = HtmlZipViewState(
                                                indexFile = result.indexFile,
                                                contentDir = result.contentDir,
                                                zipFileName = target.name,
                                                zipUri = target.uri,
                                                isEncrypted = true
                                            )
                                        } else {
                                            Toast.makeText(context, "解压失败（请检查密码）", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { if (!htmlZipInProgress) { htmlZipTarget = null; htmlZipPassword = "" } }
                            ) { Text("取消") }
                        }
                    )
                } else if (encrypted == null) {
                    // 加载中或显示错误
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("查看压缩 HTML") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 400.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (htmlZipInProgress) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("加载中...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    htmlZipLog,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        },
                        confirmButton = {
                            if (htmlZipLoadError != null) {
                                TextButton(onClick = { htmlZipTarget = null; htmlZipLoadError = null; htmlZipLog = "" }) {
                                    Text("关闭")
                                }
                            }
                        }
                    )
                }
            }
            // ---- .epub 密码输入对话框 ----
            epubTarget?.let { target ->
                val encrypted = epubEncrypted
                if (encrypted == true) {
                    AlertDialog(
                        onDismissRequest = { if (!epubInProgress) { epubTarget = null; epubPassword = "" } },
                        title = { Text("查看 EPUB 电子书") },
                        text = {
                            Column {
                                Text("${target.name} 已加密，请输入密码。", color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = epubPassword,
                                    onValueChange = { if (!epubInProgress) epubPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("ZIP 密码") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    enabled = !epubInProgress
                                )
                                if (epubInProgress) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (epubInProgress || epubPassword.isBlank()) return@Button
                                    epubInProgress = true
                                    epubLog = "开始解压加密EPUB...\n"
                                    val pwd = epubPassword.toCharArray()
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            extractEpubToCache(context, target.uri, pwd, target.name) { log ->
                                                epubLog += "$log\n"
                                            }
                                        }
                                        epubInProgress = false
                                        when (result) {
                                            is EpubParseResult.Success -> {
                                                epubTarget = null
                                                epubPassword = ""
                                                epubViewState = EpubViewState(
                                                    extractResult = result.result,
                                                    zipFileName = target.name,
                                                    epubUri = target.uri,
                                                    isEncrypted = true
                                                )
                                            }
                                            is EpubParseResult.Error -> {
                                                val detail = result.detail?.let { "\n$it" } ?: ""
                                                epubLog += "\n错误: ${result.message}$detail\n"
                                                epubLoadError = "${result.message}$detail"
                                            }
                                        }
                                    }
                                }
                            ) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { if (!epubInProgress) { epubTarget = null; epubPassword = "" } }
                            ) { Text("取消") }
                        }
                    )
                } else {
                    // encrypted == null 或 encrypted == false，都显示加载对话框
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("打开 EPUB") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 400.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (epubInProgress) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("加载中...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    epubLog,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        },
                        confirmButton = {
                            if (epubLoadError != null) {
                                TextButton(onClick = { epubTarget = null; epubLoadError = null; epubLog = "" }) {
                                    Text("关闭")
                                }
                            }
                        }
                    )
                }
            }
            // ---- EPUB 错误对话框（独立于epubTarget） ----
            epubLoadError?.let { error ->
                if (epubTarget == null) {
                    AlertDialog(
                        onDismissRequest = { epubLoadError = null; epubLog = "" },
                        title = { Text("EPUB 解析失败") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    epubLog,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { epubLoadError = null; epubLog = "" }) { Text("确定") }
                        }
                    )
                }
            }
            // ---- TXT 错误对话框 ----
            txtLoadError?.let { error ->
                AlertDialog(
                    onDismissRequest = { txtLoadError = null; txtTarget = null },
                    title = { Text("TXT 文件解析失败") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { txtLoadError = null; txtTarget = null }) { Text("确定") }
                    }
                )
            }
            // ---- .pic.zip 密码输入对话框 ----
            picZipTarget?.let { target ->
                val encrypted = picZipEncrypted
                if (encrypted == true) {
                    AlertDialog(
                        onDismissRequest = { if (!picZipInProgress) { picZipTarget = null; picZipPassword = "" } },
                        title = { Text("查看图片压缩包") },
                        text = {
                            Column {
                                Text("${target.name} 已加密，请输入密码。", color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = picZipPassword,
                                    onValueChange = { if (!picZipInProgress) picZipPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("ZIP 密码") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    enabled = !picZipInProgress
                                )
                                if (picZipInProgress) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (picZipInProgress || picZipPassword.isBlank()) return@Button
                                    picZipInProgress = true
                                    val pwd = picZipPassword.toCharArray()
                                    scope.launch {
                                        var result: PicZipExtractResult? = null
                                        var cachedInitialIndex = 0
                                        withContext(Dispatchers.IO) {
                                            val cacheDir = getPicZipCacheDir(context, target.uri)
                                            val contentDir = java.io.File(cacheDir, "content")
                                            val listFile = java.io.File(cacheDir, ".image_list")
                                            if (contentDir.exists() && listFile.exists() && tryPicZipPassword(context, target.uri, pwd)) {
                                                val paths = listFile.readText().lineSequence().filter { it.isNotBlank() }.toList()
                                                val lastIndexFile = java.io.File(cacheDir, ".last_index")
                                                cachedInitialIndex = lastIndexFile.takeIf { it.exists() }?.readText()?.toIntOrNull()?.coerceIn(0, (paths.size - 1).coerceAtLeast(0)) ?: 0
                                                result = PicZipExtractResult(cacheDir, contentDir, paths, true)
                                            }
                                            if (result == null) {
                                                result = extractPicZipToCache(context, target.uri, pwd, target.name)
                                            }
                                        }
                                        picZipInProgress = false
                                        val res = result
                                        if (res != null) {
                                            picZipTarget = null
                                            picZipPassword = ""
                                            picZipViewState = PicZipViewState(
                                                contentDir = res.contentDir,
                                                imagePaths = res.imagePaths,
                                                zipFileName = target.name,
                                                zipUri = target.uri,
                                                isEncrypted = true,
                                                password = pwd,
                                                initialIndex = cachedInitialIndex
                                            )
                                        } else {
                                            Toast.makeText(context, "解压失败（请检查密码）", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { if (!picZipInProgress) { picZipTarget = null; picZipPassword = "" } }
                            ) { Text("取消") }
                        }
                    )
                } else if (encrypted == null) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("查看图片压缩包") },
                        text = {
                            Column {
                                Text("正在处理 ${target.name}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        },
                        confirmButton = {}
                    )
                }
            }
            zipCompressTarget?.let { target ->
                val suggestedZipName = if (target.name.contains(".")) "${target.name.substringBeforeLast(".")}.zip" else "${target.name}.zip"
                val parentDirUri = Uri.parse(displayUri)
                val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                AlertDialog(
                    onDismissRequest = { zipCompressTarget = null; zipCompressPassword = "" },
                    title = { Text("压缩为 ZIP") },
                    text = {
                        Column {
                            Text("确定将 ${target.name} 压缩为 $suggestedZipName？", color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = zipCompressPassword,
                                onValueChange = { zipCompressPassword = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("密码（留空则不加密）") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch {
                                    progressOp = OperationProgress("压缩", 0, 1)
                                    delay(50)
                                    val pwd = zipCompressPassword.ifBlank { null }?.toCharArray()
                                    val ok = withContext(Dispatchers.IO) {
                                        compressToZip(
                                            context,
                                            listOf(target.uri),
                                            parentDirUri,
                                            treeUri,
                                            suggestedZipName,
                                            pwd
                                        ) { cur, tot ->
                                            scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress("压缩", cur, tot) }
                                        }
                                    }
                                    delay(120)
                                    progressOp = null
                                    zipCompressTarget = null
                                    zipCompressPassword = ""
                                    if (ok) {
                                        Toast.makeText(context, "压缩完成", Toast.LENGTH_SHORT).show()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(context, "压缩失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text("压缩") }
                    },
                    dismissButton = { TextButton(onClick = { zipCompressTarget = null; zipCompressPassword = "" }) { Text("取消") } }
                )
            }
            sevenZCompressTarget?.let { target ->
                val suggested7zName = if (target.name.contains(".")) "${target.name.substringBeforeLast(".")}.7z" else "${target.name}.7z"
                val parentDirUri = Uri.parse(displayUri)
                val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                AlertDialog(
                    onDismissRequest = { sevenZCompressTarget = null; sevenZCompressPassword = "" },
                    title = { Text("压缩为 7Z") },
                    text = {
                        Column {
                            Text("确定将 ${target.name} 压缩为 $suggested7zName？", color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = sevenZCompressPassword,
                                onValueChange = { sevenZCompressPassword = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("密码（留空则不加密）") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch {
                                    progressOp = OperationProgress("压缩", 0, 1)
                                    delay(50)
                                    val pwd = sevenZCompressPassword.ifBlank { null }?.toCharArray()
                                    var timeout = false
                                    val ok = withContext(Dispatchers.IO) {
                                        val executor = Executors.newSingleThreadExecutor()
                                        try {
                                            val future = executor.submit<Boolean> {
                                                compressTo7z(
                                                    context,
                                                    listOf(target.uri),
                                                    parentDirUri,
                                                    treeUri,
                                                    suggested7zName,
                                                    pwd
                                                ) { cur, tot ->
                                                    scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress("压缩", cur, tot) }
                                                }
                                            }
                                            future.get(2, TimeUnit.MINUTES)
                                        } catch (_: TimeoutException) {
                                            timeout = true
                                            false
                                        } finally {
                                            executor.shutdownNow()
                                        }
                                    }
                                    delay(120)
                                    progressOp = null
                                    sevenZCompressTarget = null
                                    sevenZCompressPassword = ""
                                    if (ok) {
                                        Toast.makeText(context, "压缩完成", Toast.LENGTH_SHORT).show()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(context, if (timeout) "压缩超时，请重试" else "压缩失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text("压缩") }
                    },
                    dismissButton = { TextButton(onClick = { sevenZCompressTarget = null; sevenZCompressPassword = "" }) { Text("取消") } }
                )
            }
            if (showPendingCompressToZip && pendingList.isNotEmpty()) {
                val rootTreeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                val rootDirUri = rootTreeUri ?: Uri.parse(displayUri)
                AlertDialog(
                    onDismissRequest = { showPendingCompressToZip = false },
                    title = { Text("压缩待处理列表为 ZIP") },
                    text = {
                        Column {
                            Text(
                                "将待处理列表中 ${pendingList.size} 项压缩为一个 ZIP 文件，保存到根目录。",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = pendingCompressZipName,
                                onValueChange = { pendingCompressZipName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("文件名") },
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pendingCompressPassword,
                                onValueChange = { pendingCompressPassword = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("密码（留空则不加密）") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            enabled = pendingCompressZipName.isNotBlank(),
                            onClick = {
                                val zipName = pendingCompressZipName.trim().let {
                                    if (it.endsWith(".zip", ignoreCase = true)) it else "$it.zip"
                                }
                                val items = pendingList.toList()
                                val pwd = pendingCompressPassword.ifBlank { null }?.toCharArray()
                                showPendingCompressToZip = false
                                scope.launch {
                                    progressOp = OperationProgress("压缩", 0, items.size)
                                    delay(50)
                                    val ok = withContext(Dispatchers.IO) {
                                        compressToZip(
                                            context,
                                            items.map { it.uri },
                                            rootDirUri,
                                            rootTreeUri,
                                            zipName,
                                            pwd
                                        ) { cur, tot ->
                                            scope.launch(Dispatchers.Main.immediate) {
                                                progressOp = OperationProgress("压缩", cur, tot)
                                            }
                                        }
                                    }
                                    delay(120)
                                    progressOp = null
                                    pendingCompressZipName = ""
                                    pendingCompressPassword = ""
                                    if (ok) {
                                        pendingList.clear()
                                        Toast.makeText(context, "压缩完成：$zipName", Toast.LENGTH_SHORT).show()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(context, "压缩失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text("压缩") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPendingCompressToZip = false }) { Text("取消") }
                    }
                )
            }
            if (showPendingCompressTo7z && pendingList.isNotEmpty()) {
                val rootTreeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                val rootDirUri = rootTreeUri ?: Uri.parse(displayUri)
                AlertDialog(
                    onDismissRequest = { showPendingCompressTo7z = false },
                    title = { Text("压缩待处理列表为 7Z") },
                    text = {
                        Column {
                            Text(
                                "将待处理列表中 ${pendingList.size} 项压缩为一个 7Z 文件，保存到根目录。",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = pendingCompress7zName,
                                onValueChange = { pendingCompress7zName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("文件名") },
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = pendingCompress7zPassword,
                                onValueChange = { pendingCompress7zPassword = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("密码（留空则不加密）") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            enabled = pendingCompress7zName.isNotBlank(),
                            onClick = {
                                val sevenZName = pendingCompress7zName.trim().let {
                                    if (it.endsWith(".7z", ignoreCase = true)) it else "$it.7z"
                                }
                                val items = pendingList.toList()
                                val pwd = pendingCompress7zPassword.ifBlank { null }?.toCharArray()
                                showPendingCompressTo7z = false
                                scope.launch {
                                    progressOp = OperationProgress("压缩", 0, items.size)
                                    delay(50)
                                    var timeout = false
                                    val ok = withContext(Dispatchers.IO) {
                                        val executor = Executors.newSingleThreadExecutor()
                                        try {
                                            val future = executor.submit<Boolean> {
                                                compressTo7z(
                                                    context,
                                                    items.map { it.uri },
                                                    rootDirUri,
                                                    rootTreeUri,
                                                    sevenZName,
                                                    pwd
                                                ) { cur, tot ->
                                                    scope.launch(Dispatchers.Main.immediate) {
                                                        progressOp = OperationProgress("压缩", cur, tot)
                                                    }
                                                }
                                            }
                                            future.get(2, TimeUnit.MINUTES)
                                        } catch (_: TimeoutException) {
                                            timeout = true
                                            false
                                        } finally {
                                            executor.shutdownNow()
                                        }
                                    }
                                    delay(120)
                                    progressOp = null
                                    pendingCompress7zName = ""
                                    pendingCompress7zPassword = ""
                                    if (ok) {
                                        pendingList.clear()
                                        showPendingList = false
                                        Toast.makeText(context, "压缩完成：$sevenZName", Toast.LENGTH_SHORT).show()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(context, if (timeout) "压缩超时，请重试" else "压缩失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text("压缩") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPendingCompressTo7z = false }) { Text("取消") }
                    }
                )
            }
            if (showChangeRootConfirm && rootUri != null) {
                val r = rootUri!!
                val root = Uri.parse(normalizeContentUriString(r))
                AlertDialog(
                    onDismissRequest = { showChangeRootConfirm = false },
                    title = { Text("更换根目录") },
                    text = { Text("当前根目录的回收站不为空，是否清空后再更换？") },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch {
                                showChangeRootConfirm = false
                                runWithProgress("清空回收站", null) { _ ->
                                    val ok = withContext(Dispatchers.IO) { emptyTrash(context, root, root) }
                                    if (ok) Toast.makeText(context, "回收站已清空", Toast.LENGTH_SHORT).show()
                                    treeLauncher.launch(null)
                                    refreshTrigger++
                                }
                            }
                        }) { Text("清空后更换") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                showChangeRootConfirm = false
                                treeLauncher.launch(null)
                            }) { Text("直接更换") }
                            TextButton(onClick = { showChangeRootConfirm = false }) { Text("取消") }
                        }
                    }
                )
            }
            var gpgInProgress by remember { mutableStateOf(false) }
            gpgState?.let { op ->
                val ctx = context
                val dirUri = normalizeContentUriString(op.dirUri)
                val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }

                fun resetGpgUiState() {
                    gpgState = null
                    gpgPassword = ""
                    gpgDecryptMode = null
                    gpgDecryptAutoTried = false
                    gpgEncryptSelectedKeyId = null
                }

                if (op.isDecrypt) {
                    LaunchedEffect(op) {
                        gpgDecryptMode = detectGpgDecryptMode(op)
                        gpgDecryptAutoTried = SecretKeyPasswordCache.get() == null
                    }

                    if (gpgDecryptMode == GpgDecryptUiMode.SECRET_KEY && !gpgDecryptAutoTried && SecretKeyPasswordCache.get() != null) {
                        LaunchedEffect(op, gpgDecryptMode) {
                            gpgDecryptAutoTried = true
                            gpgInProgress = true
                            val autoKeyPass = SecretKeyPasswordCache.get() ?: return@LaunchedEffect
                            val ok = when (op) {
                                is GpgOpState.BatchDecrypt -> {
                                    var allOk = true
                                    runWithProgress("解密", op.list.size) { setProgress ->
                                        op.list.forEachIndexed { index, fileModel ->
                                            if (!decryptGpgFile(fileModel, dirUri, treeUri, symmetricPassword = null, keyPassphrase = autoKeyPass)) {
                                                allOk = false
                                            }
                                            setProgress(index + 1)
                                        }
                                    }
                                    allOk
                                }
                                is GpgOpState.Decrypt -> decryptGpgFile(op.fileModel, dirUri, treeUri, symmetricPassword = null, keyPassphrase = autoKeyPass)
                                else -> false
                            }
                            gpgInProgress = false
                            if (ok) {
                                resetGpgUiState()
                                refreshTrigger++
                                if (op is GpgOpState.BatchDecrypt) {
                                    pendingList.clear()
                                    showPendingList = false
                                    Toast.makeText(ctx, "已解密 ${op.list.size} 个文件", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(ctx, "解密完成", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    val decryptMode = gpgDecryptMode
                    if (decryptMode != null && (decryptMode != GpgDecryptUiMode.SECRET_KEY || gpgDecryptAutoTried)) {
                        GpgPasswordDialog(
                            isDecrypt = true,
                            fileName = op.displayName,
                            password = gpgPassword,
                            passwordLabel = when (decryptMode) {
                                GpgDecryptUiMode.SECRET_KEY -> "密钥密码"
                                GpgDecryptUiMode.MIXED -> "密码（公钥文件会自动尝试私钥）"
                                GpgDecryptUiMode.SYMMETRIC -> "密码"
                            },
                            inProgress = gpgInProgress,
                            onPasswordChange = { if (!gpgInProgress) gpgPassword = it },
                            onConfirm = { pwd ->
                                if (gpgInProgress) return@GpgPasswordDialog
                                gpgInProgress = true
                                scope.launch {
                                    try {
                                        val pwdChars = pwd.toCharArray()
                                        val ok = when (op) {
                                            is GpgOpState.BatchDecrypt -> {
                                                runWithProgress("解密", op.list.size) { setProgress ->
                                                    op.list.forEachIndexed { index, fileModel ->
                                                        decryptGpgFile(
                                                            fileModel,
                                                            dirUri,
                                                            treeUri,
                                                            symmetricPassword = if (decryptMode == GpgDecryptUiMode.SECRET_KEY) null else pwdChars,
                                                            keyPassphrase = when (decryptMode) {
                                                                GpgDecryptUiMode.SYMMETRIC -> null
                                                                GpgDecryptUiMode.SECRET_KEY -> pwdChars
                                                                GpgDecryptUiMode.MIXED -> SecretKeyPasswordCache.get() ?: pwdChars
                                                            }
                                                        )
                                                        setProgress(index + 1)
                                                    }
                                                }
                                                true
                                            }
                                            is GpgOpState.Decrypt -> decryptGpgFile(
                                                op.fileModel,
                                                dirUri,
                                                treeUri,
                                                symmetricPassword = if (decryptMode == GpgDecryptUiMode.SECRET_KEY) null else pwdChars,
                                                keyPassphrase = when (decryptMode) {
                                                    GpgDecryptUiMode.SYMMETRIC -> null
                                                    GpgDecryptUiMode.SECRET_KEY -> pwdChars
                                                    GpgDecryptUiMode.MIXED -> SecretKeyPasswordCache.get() ?: pwdChars
                                                }
                                            )
                                            else -> false
                                        }
                                        if (ok) {
                                            if (op is GpgOpState.BatchDecrypt) {
                                                pendingList.clear()
                                                showPendingList = false
                                                Toast.makeText(ctx, "已解密 ${op.list.size} 个文件", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(ctx, "解密完成", Toast.LENGTH_SHORT).show()
                                            }
                                            resetGpgUiState()
                                            refreshTrigger++
                                        } else {
                                            Toast.makeText(ctx, "解密失败", Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        gpgInProgress = false
                                    }
                                }
                            },
                            onDismiss = { if (!gpgInProgress) resetGpgUiState() }
                        )
                    }
                } else {
                    val pubRings = loadPublicKeyRings(context)
                    val keys = listEncryptionPublicKeyRings(pubRings)
                    GpgEncryptDialog(
                        fileName = op.displayName,
                        keys = keys,
                        selectedKeyId = gpgEncryptSelectedKeyId,
                        password = gpgPassword,
                        inProgress = gpgInProgress || gpgPubEncryptInProgress,
                        onSelectPassword = {
                            if (!gpgInProgress && !gpgPubEncryptInProgress) gpgEncryptSelectedKeyId = null
                        },
                        onSelectKey = { keyId ->
                            if (!gpgInProgress && !gpgPubEncryptInProgress) gpgEncryptSelectedKeyId = keyId
                        },
                        onPasswordChange = {
                            if (!gpgInProgress && !gpgPubEncryptInProgress) {
                                gpgPassword = it
                                if (it.isNotEmpty()) gpgEncryptSelectedKeyId = null
                            }
                        },
                        onConfirm = { password, keyId ->
                            if (gpgInProgress || gpgPubEncryptInProgress) return@GpgEncryptDialog
                            if (keyId == null && password.isBlank()) return@GpgEncryptDialog
                            gpgInProgress = keyId == null
                            gpgPubEncryptInProgress = keyId != null
                            scope.launch {
                                try {
                                    val pwdChars = password.toCharArray()
                                    val ok = when (op) {
                                        is GpgOpState.BatchEncrypt -> {
                                            runWithProgress(if (keyId != null) "公钥加密" else "加密", op.list.size) { setProgress ->
                                                op.list.forEachIndexed { index, fileModel ->
                                                    encryptGpgFile(
                                                        fileModel,
                                                        dirUri,
                                                        treeUri,
                                                        symmetricPassword = if (keyId == null) pwdChars else null,
                                                        publicKeyId = keyId
                                                    )
                                                    setProgress(index + 1)
                                                }
                                            }
                                            true
                                        }
                                        is GpgOpState.Encrypt -> encryptGpgFile(
                                            op.fileModel,
                                            dirUri,
                                            treeUri,
                                            symmetricPassword = if (keyId == null) pwdChars else null,
                                            publicKeyId = keyId
                                        )
                                        else -> false
                                    }
                                    if (ok) {
                                        if (op is GpgOpState.BatchEncrypt) {
                                            pendingList.clear()
                                            showPendingList = false
                                            Toast.makeText(ctx, if (keyId != null) "已公钥加密 ${op.list.size} 个文件" else "已加密 ${op.list.size} 个文件", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(ctx, if (keyId != null) "公钥加密完成" else "加密完成", Toast.LENGTH_SHORT).show()
                                        }
                                        resetGpgUiState()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(ctx, "加密失败", Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    gpgInProgress = false
                                    gpgPubEncryptInProgress = false
                                }
                            }
                        },
                        onDismiss = { if (!gpgInProgress && !gpgPubEncryptInProgress) resetGpgUiState() }
                    )
                }
            }
            }
        }
    }
    showOverwriteConfirm?.let { (sourceUriStr, fileName) ->
        val targetRoot = rootUri?.let { normalizeContentUriString(it) } ?: return@let
        AlertDialog(
            onDismissRequest = {
                showOverwriteConfirm = null
                pendingSaveFileUri = null
                initialDirUri = targetRoot
            },
            title = { Text("文件已存在") },
            text = { Text("$fileName 已存在于当前目录，是否覆盖？") },
            confirmButton = {
                Button(onClick = {
                    val ctx = context
                    scope.launch {
                        saveInProgress = true
                        val targetUri = Uri.parse(targetRoot)
                        val existingUri = findChildByName(ctx, targetUri, fileName)
                        if (existingUri != null) ctx.contentResolver.deleteDocument(existingUri)
                        val copied = withContext(Dispatchers.IO) {
                            copyDocumentTo(ctx, Uri.parse(sourceUriStr), targetUri, targetUri)
                        }
                        saveInProgress = false
                        showOverwriteConfirm = null
                        pendingSaveFileUri = null
                        if (copied != null) {
                            val dir = normalizeContentUriString(targetRoot)
                            initialDirUri = dir
                            currentUri = dir
                            saveCompletedToken++
                            Toast.makeText(ctx, "已覆盖保存", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "保存失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("覆盖") }
            },
                dismissButton = {
                    TextButton(onClick = {
                        showOverwriteConfirm = null
                        pendingSaveFileUri = null
                        initialDirUri = normalizeContentUriString(targetRoot)
                    }) { Text("不覆盖") }
            }
        )
    }
}

/** 统一进度：label 文案，total 为 null 表示不定型进度，否则为 X/total 项 */
private data class OperationProgress(val label: String, val current: Int = 0, val total: Int? = null)

@Composable
private fun OperationProgressDialog(progress: OperationProgress) {
    Dialog(onDismissRequest = { }) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text("${progress.label} 中…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                if (progress.total != null && progress.total > 0) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { progress.current.toFloat() / progress.total })
                    Spacer(Modifier.height(8.dp))
                    Text("${progress.current} / ${progress.total} 项", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("处理中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 密码保护解密后的内容，暂存于内存中供渲染。 */
private data class PassDecryptedContent(val innerFileName: String, val decryptedBytes: ByteArray)

/** 判断文件名是否为压缩 Markdown/RST 文件（.md.zip 或 .rst.zip）。 */
private fun isCompressedMarkdown(name: String): Boolean =
    name.endsWith(".md.zip", ignoreCase = true) || name.endsWith(".rst.zip", ignoreCase = true)

/** .md.zip / .rst.zip 查看器状态。 */
private data class MdZipViewState(
    val targetFile: java.io.File?,  // null 表示未找到可渲染文件，此时显示目录内容
    val contentDir: java.io.File,
    val zipFileName: String,
    val zipUri: Uri,
    val isEncrypted: Boolean
)

/** 判断文件名是否为压缩 HTML 文件（.html.zip）。 */
private fun isCompressedHtml(name: String): Boolean =
    name.endsWith(".html.zip", ignoreCase = true)

/** 判断文件名是否为 .pic.zip 图片压缩包。 */
private fun isPicZip(name: String): Boolean =
    name.endsWith(".pic.zip", ignoreCase = true)

/** 判断文件名是否为可解压压缩包（.zip / .rar）。 */
private fun isExtractableArchive(name: String): Boolean =
    name.endsWith(".zip", ignoreCase = true) ||
        name.endsWith(".rar", ignoreCase = true) ||
        name.endsWith(".7z", ignoreCase = true)

/** 判断文件名是否为 EPUB 文件。 */
private fun isEpubFile(name: String): Boolean =
    name.endsWith(".epub", ignoreCase = true)

/** 判断文件名是否为 TXT 文件。 */
private fun isTxtFile(name: String): Boolean =
    name.endsWith(".txt", ignoreCase = true)

/** 判断文件名是否为 PDF 文件。 */
private fun isPdfFile(name: String): Boolean =
    name.endsWith(".pdf", ignoreCase = true)

/** .html.zip 查看器状态。 */
private data class HtmlZipViewState(
    val indexFile: java.io.File?,
    val contentDir: java.io.File,
    val zipFileName: String,
    val zipUri: Uri,
    val isEncrypted: Boolean
)

/** EPUB 查看器状态。 */
private data class EpubViewState(
    val extractResult: EpubExtractResult,
    val zipFileName: String,
    val epubUri: Uri,
    val isEncrypted: Boolean
)

/** .pic.zip 查看器状态。 */
private data class PicZipViewState(
    val contentDir: java.io.File,
    val imagePaths: List<String>,
    val zipFileName: String,
    val zipUri: Uri,
    val isEncrypted: Boolean,
    val password: CharArray? = null,
    val initialIndex: Int = 0
)

/** 直接编辑 .pass 时的状态：解密后的文件信息，用于编辑界面和存盘时重加密。 */
private data class PassEditState(
    val fileModel: DocumentFileModel,
    val dirUri: String,
    val treeUri: Uri?,
    val innerName: String,
    val decryptedBytes: ByteArray
) {
    override fun equals(other: Any?) = (other is PassEditState) && fileModel == other.fileModel && dirUri == other.dirUri && innerName == other.innerName && decryptedBytes.contentEquals(other.decryptedBytes)
    override fun hashCode() = 31 * (31 * fileModel.hashCode() + dirUri.hashCode()) + innerName.hashCode()
}

private sealed class GpgOpState {
    abstract val isDecrypt: Boolean
    abstract val dirUri: String
    abstract val displayName: String
    data class Decrypt(val fileModel: DocumentFileModel, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = true
        override val displayName get() = fileModel.name
    }
    data class Encrypt(val fileModel: DocumentFileModel, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = false
        override val displayName get() = fileModel.name
    }
    data class BatchDecrypt(val list: List<DocumentFileModel>, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = true
        override val displayName get() = "共 ${list.size} 个文件"
    }
    data class BatchEncrypt(val list: List<DocumentFileModel>, override val dirUri: String) : GpgOpState() {
        override val isDecrypt = false
        override val displayName get() = "共 ${list.size} 个文件"
    }
}

private enum class GpgDecryptUiMode {
    SYMMETRIC,
    SECRET_KEY,
    MIXED
}

/** 加密/解密方式：对称(密码)、公钥加密、私钥解密 */
private sealed class GpgMethod {
    object Symmetric : GpgMethod()
    object SecretKeyDec : GpgMethod()
}

internal data class CachedDir(val items: List<DocumentFileModel>)

private enum class FileSortOrder(val label: String) {
    NAME("名称"),
    TIME("更新时间"),
    SIZE("大小")
}

private fun fileListComparator(sortOrder: FileSortOrder, ascending: Boolean): Comparator<DocumentFileModel> {
    val dirFirst = compareBy<DocumentFileModel> { !it.isDirectory }
    val nameThen = compareBy<DocumentFileModel> { it.name.lowercase() }
    return when (sortOrder) {
        FileSortOrder.NAME -> dirFirst.then(if (ascending) nameThen else nameThen.reversed())
        FileSortOrder.TIME -> dirFirst.then(
            if (ascending) compareBy { it.lastModified } else compareByDescending { it.lastModified }
        ).then(nameThen)
        FileSortOrder.SIZE -> dirFirst.then(
            if (ascending) compareBy { it.size } else compareByDescending { it.size }
        ).then(nameThen)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileBrowserScreen(
    modifier: Modifier = Modifier,
    currentUri: String,
    refreshTrigger: Int,
    dirCache: Map<String, CachedDir>,
    onCacheDir: (uri: String, items: List<DocumentFileModel>) -> Unit,
    pendingList: List<DocumentFileModel>,
    rootUri: String?,
    listState: LazyListState,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    onEmptyTrash: (() -> Unit)? = null,
    onRestoreFromTrash: ((DocumentFileModel) -> Unit)? = null,
    isViewingTrash: Boolean = false,
    filterVisible: Boolean = true,
    hideDotFiles: Boolean = false,
    preferredExternalPackages: Map<String, String> = emptyMap(),
    onOpenFile: (uri: String, name: String, isEncrypted: Boolean) -> Unit,
    onOpenWithOtherApp: (uri: String, name: String, packageName: String?, rememberChoice: Boolean) -> Boolean = { _, _, _, _ -> false },
    onClearExternalOpenPreference: (name: String) -> Unit = {},
    onAddToPendingList: (DocumentFileModel) -> Unit,
    onRemoveFromPendingList: (DocumentFileModel) -> Unit,
    onShowPendingList: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenFtp: () -> Unit = {},
    onOpenFileShare: () -> Unit = {},
    onOpenQuickNote: () -> Unit = {},
    onCreateQuickNote: () -> Unit = {},
    onOpenDictionary: () -> Unit = {},
    onShareFileToGit: ((DocumentFileModel) -> Unit)? = null,
    onOpenMarkdownView: (uri: String, name: String, encrypted: Boolean) -> Unit = { _, _, _ -> },
    onRequestGpgDecrypt: (DocumentFileModel, String) -> Unit,
    onRequestGpgEncrypt: (DocumentFileModel, String) -> Unit,
    onRequestQuickObfuscate: ((DocumentFileModel) -> Unit)? = null,
    onRequestQuickDeobfuscate: ((DocumentFileModel) -> Unit)? = null,
    onConfirmDelete: ((DocumentFileModel, Boolean) -> Unit)? = null,
    onUnzipRequest: (DocumentFileModel) -> Unit = {},
    onRequestMdZipView: (DocumentFileModel) -> Unit = {},
    onRequestHtmlZipView: (DocumentFileModel) -> Unit = {},
    onRequestEpubView: (DocumentFileModel) -> Unit = {},
    onRequestPicZipView: (DocumentFileModel) -> Unit = {},
    onRequestPdfView: (DocumentFileModel) -> Unit = {},
    onCompressToZipRequest: (DocumentFileModel) -> Unit = {},
    onCompressTo7zRequest: (DocumentFileModel) -> Unit = {},
    onRequestPassProtect: ((DocumentFileModel) -> Unit)? = null,
    onRequestPassView: (DocumentFileModel) -> Unit = {},
    onRequestPassEdit: (DocumentFileModel, String) -> Unit = { _, _ -> },
    onRequestTxtView: (DocumentFileModel) -> Unit = {},
    onRequestImportConfig: ((DocumentFileModel) -> Unit)? = null,
    onRequestImportStarDict: ((DocumentFileModel) -> Unit)? = null,
    playbackState: PlaybackState? = null,
    onOpenPlaybackScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    var items by remember(currentUri) { mutableStateOf<List<DocumentFileModel>>(emptyList()) }
    var loading by remember(currentUri) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateDirDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newDirName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf<DocumentFileModel?>(null) }
    var showFileDetail by remember { mutableStateOf<DocumentFileModel?>(null) }
    var filterText by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(FileSortOrder.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var externalOpenTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
    var externalOpenOptions by remember { mutableStateOf<List<ExternalAppTarget>>(emptyList()) }

    fun showExternalOpenDialog(target: DocumentFileModel) {
        val options = queryExternalOpenTargets(context, target.uri.toString())
        if (options.isEmpty()) {
            Toast.makeText(context, "没有可打开的应用", Toast.LENGTH_SHORT).show()
            return
        }
        externalOpenTarget = target
        externalOpenOptions = options
    }

    LaunchedEffect(currentUri, refreshTrigger) {
        val normalizedUri = normalizeContentUriString(currentUri)
        val cached = dirCache[normalizedUri]
        if (cached != null) {
            items = cached.items
            loading = false
            error = null
            launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        listChildrenFast(context, currentUri)
                    }
                    items = result
                    onCacheDir(normalizedUri, result)
                } catch (e: Exception) {
                    error = e.message ?: "加载失败"
                    items = emptyList()
                }
            }
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val result = withContext(Dispatchers.IO) {
                listChildrenFast(context, currentUri)
            }
            items = result
            onCacheDir(normalizedUri, result)
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
            items = emptyList()
        }
        loading = false
    }

    val sortedItems = remember(items, sortOrder, sortAscending) {
        items.sortedWith(fileListComparator(sortOrder, sortAscending))
    }
    val filteredItems = remember(sortedItems, filterText, filterVisible, hideDotFiles) {
        var list = sortedItems
        if (hideDotFiles) list = list.filter { !it.name.startsWith(".") }
        if (filterVisible && filterText.isNotBlank()) {
            list = runCatching { Regex(filterText) }.getOrNull()?.let { regex ->
                list.filter { regex.containsMatchIn(it.name) }
            } ?: list
        }
        list
    }
    val displayItems = filteredItems

    var showFabMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        val doc = DocumentFile.fromTreeUri(context, Uri.parse(currentUri))
                            ?: DocumentFile.fromSingleUri(context, Uri.parse(currentUri))
                        Text(
                            doc?.name ?: "根目录",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (canGoBack) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                FileSortOrder.entries.forEach { order ->
                                    val isCurrent = sortOrder == order
                                    val direction = if (isCurrent) (if (sortAscending) " ↑" else " ↓") else ""
                                    DropdownMenuItem(
                                        text = { Text(order.label + direction) },
                                        onClick = {
                                            if (isCurrent) {
                                                sortAscending = !sortAscending
                                            } else {
                                                sortOrder = order
                                                sortAscending = true
                                            }
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("把当前过滤结果全部加入待处理列表") },
                                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val toAdd = filteredItems.filter { item -> !pendingList.any { it.uri == item.uri } }
                                    toAdd.forEach { onAddToPendingList(it) }
                                    Toast.makeText(context, "已加入 ${toAdd.size} 项到待处理列表", Toast.LENGTH_SHORT).show()
                                }
                            )
                            onEmptyTrash?.let { empty ->
                                DropdownMenuItem(
                                    text = { Text("清空回收站") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        empty()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("播放器") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenPlaybackScreen()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("FTP 数据交换") },
                                leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenFtp()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Git 文件共享") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenFileShare()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("快速笔记") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenQuickNote()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("词典") },
                                leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenDictionary()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("配置") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenConfig()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("关于") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenAbout()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (filterVisible) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = filterText,
                            onValueChange = { filterText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("正则过滤文件名，留空显示全部") },
                            label = null
                        )
                        if (filterText.isNotEmpty()) {
                            IconButton(onClick = { filterText = "" }) {
                                Icon(Icons.Default.RemoveCircle, contentDescription = "清除过滤", Modifier.size(20.dp))
                            }
                        }
                    }
                }
                playbackState?.let { state ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { onOpenPlaybackScreen() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "正在播放: ${state.trackName} (${state.trackIndex + 1}/${state.totalTracks})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!loading && error == null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (pendingList.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = { onShowPendingList(true) },
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Icon(Icons.Default.List, contentDescription = "待处理列表")
                        }
                    }
                    FloatingActionButton(
                        onClick = { showFabMenu = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新建")
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp, 8.dp, 8.dp, 88.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                        items(
                            count = displayItems.size,
                            key = { displayItems[it].uri.toString() }
                        ) { index ->
                            val item = displayItems[index]
                            FileItem(
                                model = item,
                                hasPreferredExternalApp = fileExtensionKey(item.name)?.let { preferredExternalPackages.containsKey(it) } == true,
                                isInPendingList = pendingList.any { it.uri == item.uri },
                                onClick = {
                                    if (item.isDirectory) {
                                        onNavigate(item.uri.toString())
                                    } else {
                                        when {
                                            isCompressedMarkdown(item.name) ->
                                                onRequestMdZipView(item)
                                            isCompressedHtml(item.name) ->
                                                onRequestHtmlZipView(item)
                                            isEpubFile(item.name) ->
                                                onRequestEpubView(item)
                                            isTxtFile(item.name) ->
                                                onRequestTxtView(item)
                                            isPicZip(item.name) ->
                                                onRequestPicZipView(item)
                                            isPdfFile(item.name) ->
                                                onRequestPdfView(item)
                                            isExtractableArchive(item.name) ->
                                                onUnzipRequest(item)
                                            item.name.endsWith(".gpg", ignoreCase = true) ->
                                                onRequestGpgDecrypt(item, currentUri)
                                            item.name.endsWith(".pass", ignoreCase = true) ->
                                                onRequestPassView(item)
                                            item.name.endsWith(".md", ignoreCase = true) || item.name.endsWith(".rst", ignoreCase = true) ->
                                                onOpenMarkdownView(item.uri.toString(), item.name, false)
                                            else -> {
                                                val preferredPackage = fileExtensionKey(item.name)?.let { preferredExternalPackages[it] }
                                                if (preferredPackage != null && onOpenWithOtherApp(item.uri.toString(), item.name, preferredPackage, false)) {
                                                    Unit
                                                } else {
                                                    val encrypted = item.name.endsWith(".gpg", ignoreCase = true)
                                                    onOpenFile(item.uri.toString(), item.name, encrypted)
                                                }
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    contextMenuTarget = item
                                    showContextMenu = true
                                },
                                onDoubleClick = {
                                    if (pendingList.any { it.uri == item.uri }) onRemoveFromPendingList(item)
                                    else onAddToPendingList(item)
                                }
                            )
                        }
                        }
                    }
                }
            }
        }
    }

    if (showContextMenu && contextMenuTarget != null) {
        val menuTarget = contextMenuTarget!!
        Dialog(onDismissRequest = { showContextMenu = false; contextMenuTarget = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        menuTarget.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    if (!menuTarget.isDirectory) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                showExternalOpenDialog(menuTarget)
                                contextMenuTarget = null
                            }
                        ) { Text("用其他应用打开", color = MaterialTheme.colorScheme.onSurface) }
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onClearExternalOpenPreference(menuTarget.name)
                                onOpenFile(menuTarget.uri.toString(), menuTarget.name, false)
                                contextMenuTarget = null
                            }
                        ) { Text("用内置查看器打开", color = MaterialTheme.colorScheme.onSurface) }
                        if (menuTarget.name.endsWith(".md", ignoreCase = true) || menuTarget.name.endsWith(".rst", ignoreCase = true)) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onOpenMarkdownView(menuTarget.uri.toString(), menuTarget.name, false)
                                    contextMenuTarget = null
                                }
                            ) { Text("渲染 (Markdown/RST)", color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (menuTarget.name.endsWith(".zip", ignoreCase = true)) {
                            if (isCompressedMarkdown(menuTarget.name)) {
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        onRequestMdZipView(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text("查看压缩 Markdown", color = MaterialTheme.colorScheme.onSurface) }
                            }
                            if (isCompressedHtml(menuTarget.name)) {
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        onRequestHtmlZipView(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text("查看压缩 HTML", color = MaterialTheme.colorScheme.onSurface) }
                            }
                            if (isPicZip(menuTarget.name)) {
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        onRequestPicZipView(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text("查看图片", color = MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                        if (!menuTarget.name.endsWith(".pass", ignoreCase = true)) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestGpgEncrypt(menuTarget, currentUri)
                                    contextMenuTarget = null
                                }
                            ) { Text("GnuPG 加密", color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if ((menuTarget.name.endsWith(".md", ignoreCase = true) || menuTarget.name.endsWith(".rst", ignoreCase = true))
                            && onRequestPassProtect != null) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestPassProtect(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text("密码保护", color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (menuTarget.name.endsWith(".pass", ignoreCase = true)) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestPassView(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text("查看密码", color = MaterialTheme.colorScheme.onSurface) }
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestPassEdit(menuTarget, currentUri)
                                    contextMenuTarget = null
                                }
                            ) { Text("直接编辑", color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (menuTarget.name.endsWith(".json", ignoreCase = true) && onRequestImportConfig != null) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestImportConfig(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text("导入配置", color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (isStarDictImportCandidate(menuTarget.name) && onRequestImportStarDict != null) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestImportStarDict(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text("导入星际词典", color = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                    if (isViewingTrash && onRestoreFromTrash != null) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onRestoreFromTrash(menuTarget)
                                contextMenuTarget = null
                            }
                        ) { Text("恢复", color = MaterialTheme.colorScheme.primary) }
                    }
                    if (!menuTarget.isDirectory) {
                        if (isQuickObfuscatedFileName(menuTarget.name)) {
                            onRequestQuickDeobfuscate?.let { fn ->
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        fn(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text("快速去混淆", color = MaterialTheme.colorScheme.onSurface) }
                            }
                        } else {
                            onRequestQuickObfuscate?.let { fn ->
                                TextButton(
                                    onClick = {
                                        showContextMenu = false
                                        fn(menuTarget)
                                        contextMenuTarget = null
                                    }
                                ) { Text("快速混淆", color = MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onCompressToZipRequest(menuTarget)
                            contextMenuTarget = null
                        }
                    ) { Text("压缩为 ZIP", color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onCompressTo7zRequest(menuTarget)
                            contextMenuTarget = null
                        }
                    ) { Text("压缩为 7Z", color = MaterialTheme.colorScheme.onSurface) }
                    if (!menuTarget.isDirectory && onShareFileToGit != null) {
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                onShareFileToGit.invoke(menuTarget)
                                contextMenuTarget = null
                            }
                        ) { Text("共享到 Git", color = MaterialTheme.colorScheme.onSurface) }
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            if (pendingList.any { it.uri == menuTarget.uri }) onRemoveFromPendingList(menuTarget)
                            else onAddToPendingList(menuTarget)
                            contextMenuTarget = null
                        }
                    ) {
                        Text(
                            if (pendingList.any { it.uri == menuTarget.uri }) "从待处理列表移除" else "加入待处理列表",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            showFileDetail = menuTarget
                            contextMenuTarget = null
                        }
                    ) { Text("详细信息", color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            contextMenuTarget = null
                            showDeleteConfirm = menuTarget
                        }
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showContextMenu = false; contextMenuTarget = null }) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }

    if (externalOpenTarget != null) {
        val target = externalOpenTarget!!
        Dialog(onDismissRequest = { externalOpenTarget = null; externalOpenOptions = emptyList() }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("选择打开应用", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        target.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    externalOpenOptions.forEach { option ->
                        TextButton(
                            onClick = {
                                val opened = onOpenWithOtherApp(target.uri.toString(), target.name, option.packageName, true)
                                if (opened) {
                                    externalOpenTarget = null
                                    externalOpenOptions = emptyList()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(option.label, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    option.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { externalOpenTarget = null; externalOpenOptions = emptyList() }) {
                            Text("取消")
                        }
                    }
                }
            }
        }
    }

    if (showFabMenu) {
        Dialog(onDismissRequest = { showFabMenu = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("新建", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            showFabMenu = false
                            newFileName = ""
                            showCreateFileDialog = true
                        }
                    ) { Text("新建文件", color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showFabMenu = false
                            newDirName = ""
                            showCreateDirDialog = true
                        }
                    ) { Text("新建文件夹", color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showFabMenu = false
                            onCreateQuickNote()
                        }
                    ) { Text("快速笔记", color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(onClick = { showFabMenu = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }

    if (showCreateFileDialog) {
        val createFileFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(150)
            createFileFocus.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("新建文件") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("文件名") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(createFileFocus),
                    placeholder = { Text("例如：newfile.txt") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFileName.trim()
                        if (name.isNotEmpty()) {
                            val uri = Uri.parse(currentUri)
                            val dir = if (currentUri.contains("/tree/")) {
                                DocumentFile.fromTreeUri(context, uri)
                            } else {
                                DocumentFile.fromSingleUri(context, uri)
                            }
                            when {
                                dir == null || !dir.isDirectory -> Toast.makeText(context, "无法访问目录", Toast.LENGTH_SHORT).show()
                                dir.createFile("application/octet-stream", name) == null -> Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show()
                                else -> { showCreateFileDialog = false; onRefresh() }
                            }
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("取消") }
            }
        )
    }

    if (showCreateDirDialog) {
        val createDirFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(150)
            createDirFocus.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { showCreateDirDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = newDirName,
                    onValueChange = { newDirName = it },
                    label = { Text("文件夹名") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(createDirFocus),
                    placeholder = { Text("例如：新文件夹") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newDirName.trim()
                        if (name.isNotEmpty()) {
                            val uri = Uri.parse(currentUri)
                            val dir = if (currentUri.contains("/tree/")) {
                                DocumentFile.fromTreeUri(context, uri)
                            } else {
                                DocumentFile.fromSingleUri(context, uri)
                            }
                            when {
                                dir == null || !dir.isDirectory -> Toast.makeText(context, "无法访问目录", Toast.LENGTH_SHORT).show()
                                dir.createDirectory(name) == null -> Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show()
                                else -> { showCreateDirDialog = false; onRefresh() }
                            }
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDirDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirm != null) {
        val target = showDeleteConfirm!!
        var deletePermanently by remember { mutableStateOf(false) }
        val hasRoot = rootUri != null
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("确定删除「${target.name}」吗？", color = MaterialTheme.colorScheme.onSurface)
                    if (hasRoot) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = !deletePermanently,
                                onClick = { deletePermanently = false }
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("移到回收站（可恢复）", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = deletePermanently,
                                onClick = { deletePermanently = true }
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("完全删除（不可恢复）", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("此目录无回收站，将完全删除。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = null
                        onConfirmDelete?.invoke(target, deletePermanently)
                    }
                ) { Text(if (hasRoot && !deletePermanently) "移到回收站" else "删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }

    if (showRenameDialog && actionTarget != null) {
        var target = actionTarget!!
        val renameFocus = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            delay(100)
            renameFocus.requestFocus()
            keyboardController?.show()
        }
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                actionTarget = null
            },
            title = { Text("操作: ${target.name}") },
            text = {
                Column {
                    Text("重命名为:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        label = { Text("新名称") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(renameFocus)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            context.contentResolver.renameDocument(target.uri, renameValue)
                            showRenameDialog = false
                            actionTarget = null
                            items = items.map { if (it.uri == target.uri) it.copy(name = renameValue) else it }
                        } catch (_: Exception) {}
                    }
                ) { Text("重命名") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; actionTarget = null }) { Text("取消") }
            }
        )
    }

    if (showFileDetail != null) {
        val detailTarget = showFileDetail!!
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        var detailRenameValue by remember(detailTarget.uri) { mutableStateOf(detailTarget.name) }
        var detailMeta by remember(detailTarget.uri) { mutableStateOf<Map<String, String>>(emptyMap()) }
        val detailRenameFocus = remember { FocusRequester() }
        val isArchiveFile = isExtractableArchive(detailTarget.name)
        var zipDetailResult by remember(detailTarget.uri) { mutableStateOf<ZipFirstLevelResult?>(null) }
        var zipDetailPassword by remember(detailTarget.uri) { mutableStateOf("") }
        var zipDetailShowPasswordInput by remember(detailTarget.uri) { mutableStateOf(false) }
        val detailScope = rememberCoroutineScope()

        LaunchedEffect(detailTarget.uri) {
            if (isArchiveFile) {
                zipDetailResult = withContext(Dispatchers.IO) {
                    getArchiveFirstLevelEntries(context, detailTarget.uri, detailTarget.name, null)
                }
            }
        }

        LaunchedEffect(detailTarget.uri) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = detailTarget.uri
                    val projection = arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    )
                    val meta = mutableMapOf<String, String>()
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            for (i in 0 until cursor.columnCount) {
                                val colName = cursor.getColumnName(i)
                                val value = cursor.getString(i) ?: continue
                                meta[colName] = value
                            }
                        }
                    }
                    detailMeta = meta
                } catch (_: Exception) {}
            }
        }

        val docId = detailMeta[DocumentsContract.Document.COLUMN_DOCUMENT_ID]
        val fullPath = if (docId != null) {
            val parts = docId.split(":", limit = 2)
            if (parts.size == 2) "/${parts[1]}" else docId
        } else {
            detailTarget.uri.path ?: detailTarget.uri.toString()
        }
        val mimeType = detailMeta[DocumentsContract.Document.COLUMN_MIME_TYPE] ?: ""
        val sizeStr = detailTarget.displaySize.ifEmpty {
            detailMeta[DocumentsContract.Document.COLUMN_SIZE]?.toLongOrNull()?.let { sz ->
                when {
                    sz < 1024 -> "$sz B"
                    sz < 1024 * 1024 -> "%.1f KB".format(sz / 1024.0)
                    sz < 1024 * 1024 * 1024 -> "%.1f MB".format(sz / (1024.0 * 1024))
                    else -> "%.1f GB".format(sz / (1024.0 * 1024 * 1024))
                }
            } ?: ""
        }
        val fmt = remember {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        }
        val lastModifiedStr = if (detailTarget.lastModified > 0) fmt.format(java.util.Date(detailTarget.lastModified)) else ""
        val lastModifiedFromMeta = detailMeta[DocumentsContract.Document.COLUMN_LAST_MODIFIED]?.toLongOrNull()
        val lastModifiedMetaStr = if (lastModifiedFromMeta != null && lastModifiedFromMeta > 0) fmt.format(java.util.Date(lastModifiedFromMeta)) else ""
        val displayModified = lastModifiedStr.ifEmpty { lastModifiedMetaStr }

        Dialog(onDismissRequest = { showFileDetail = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("详细信息", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))

                    Text("完整路径", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(fullPath, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = {
                            clipboardManager?.setPrimaryClip(ClipData.newPlainText("路径", fullPath))
                            Toast.makeText(context, "已复制路径", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制路径", Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("重命名", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = detailRenameValue,
                            onValueChange = { detailRenameValue = it },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(detailRenameFocus),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val newName = detailRenameValue.trim()
                                if (newName.isNotEmpty() && newName != detailTarget.name) {
                                    try {
                                        context.contentResolver.renameDocument(detailTarget.uri, newName)
                                        items = items.map { if (it.uri == detailTarget.uri) it.copy(name = newName) else it }
                                        showFileDetail = showFileDetail?.copy(name = newName)
                                        Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = detailRenameValue.trim().let { it.isNotEmpty() && it != detailTarget.name }
                        ) { Text("确定") }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("文件属性", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))

                    @Composable
                    fun MetaRow(label: String, value: String) {
                        if (value.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
                                SelectionContainer(modifier = Modifier.weight(1f)) {
                                    Text(value, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    MetaRow("类型", if (detailTarget.isDirectory) "文件夹" else mimeType.ifEmpty { "文件" })
                    if (!detailTarget.isDirectory) MetaRow("大小", sizeStr)
                    MetaRow("修改时间", displayModified)

                    if (isArchiveFile) {
                        Spacer(Modifier.height(16.dp))
                        Text("压缩包内容（第一层）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        when (val r = zipDetailResult) {
                            is ZipFirstLevelResult.Ok -> {
                                if (r.entries.isEmpty()) {
                                    Text("（空）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                                        r.entries.forEach { entry ->
                                            Text(entry, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                            is ZipFirstLevelResult.OkSingleDir -> {
                                Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                                    Text("${r.rootDirName}/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    if (r.children.isEmpty()) {
                                        Text("  （空）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        r.children.forEach { child ->
                                            Text("  $child", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                            ZipFirstLevelResult.Encrypted -> {
                                if (!zipDetailShowPasswordInput) {
                                    OutlinedButton(onClick = { zipDetailShowPasswordInput = true }) {
                                        Text("输入密码查看内容")
                                    }
                                } else {
                                    Column(Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = zipDetailPassword,
                                            onValueChange = { zipDetailPassword = it },
                                            label = { Text("密码") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            visualTransformation = PasswordVisualTransformation()
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { zipDetailShowPasswordInput = false; zipDetailPassword = "" }) { Text("取消") }
                                            Spacer(Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    detailScope.launch {
                                                        val res = withContext(Dispatchers.IO) {
                                                            getArchiveFirstLevelEntries(context, detailTarget.uri, detailTarget.name, zipDetailPassword.toCharArray())
                                                        }
                                                        zipDetailResult = res
                                                        if (res is ZipFirstLevelResult.Error) {
                                                            Toast.makeText(context, "密码错误或无法读取", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                enabled = zipDetailPassword.isNotEmpty()
                                            ) { Text("确定") }
                                        }
                                    }
                                }
                            }
                            ZipFirstLevelResult.Error -> {
                                Text("无法读取压缩包内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            null -> {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showFileDetail = null }) { Text("关闭") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(
    model: DocumentFileModel,
    hasPreferredExternalApp: Boolean = false,
    isInPendingList: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var pendingClickJob by remember { mutableStateOf<Job?>(null) }
    val icon = when {
        model.isDirectory -> Icons.Default.Folder
        model.name.endsWith(".gpg", ignoreCase = true) -> Icons.Default.Lock
        model.name.endsWith(".pass", ignoreCase = true) -> Icons.Default.Lock
        model.name.endsWith(".qx", ignoreCase = true) -> Icons.Default.LockOpen
        isCompressedMarkdown(model.name) -> Icons.Default.Article
        isCompressedHtml(model.name) -> Icons.Default.Article
        isEpubFile(model.name) -> Icons.Default.MenuBook
        isPdfFile(model.name) -> Icons.Default.PictureAsPdf
        isPicZip(model.name) -> Icons.Default.Archive
        isExtractableArchive(model.name) -> Icons.Default.Archive
        model.name.endsWith(".md", ignoreCase = true) || model.name.endsWith(".rst", ignoreCase = true) -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
    val iconTint = when {
        model.isDirectory -> MaterialTheme.colorScheme.primary
        model.name.endsWith(".gpg", ignoreCase = true) -> Color.Red
        model.name.endsWith(".pass", ignoreCase = true) -> Color.Red
        model.name.endsWith(".qx", ignoreCase = true) -> Color.Red
        isEpubFile(model.name) -> Color(0xFF8B4513)  // 棕色，适合书籍
        isPdfFile(model.name) -> Color(0xFFD32F2F)  // 红色，PDF 标志色
        isPicZip(model.name) -> MaterialTheme.colorScheme.tertiary
        isExtractableArchive(model.name) -> MaterialTheme.colorScheme.tertiary
        model.name.endsWith(".md", ignoreCase = true) || model.name.endsWith(".rst", ignoreCase = true) -> Color.Blue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val clickableOnClick: () -> Unit = if (onDoubleClick != null) {
        {
            if (pendingClickJob != null) {
                pendingClickJob?.cancel()
                pendingClickJob = null
                onDoubleClick()
            } else {
                pendingClickJob = scope.launch {
                    delay(300)
                    pendingClickJob = null
                    onClick()
                }
            }
        }
    } else {
        { onClick() }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = clickableOnClick, onLongClick = onLongClick)
            .padding(4.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgedBox(
                badge = {
                    if (hasPreferredExternalApp) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondary)
                    }
                }
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    Modifier.size(32.dp),
                    tint = iconTint
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = fileItemSubtitle(model),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isInPendingList) {
                Spacer(Modifier.size(8.dp))
                Icon(
                    Icons.Default.PlaylistAdd,
                    contentDescription = "已在待处理列表",
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingListScreen(
    pendingList: List<DocumentFileModel>,
    currentDirPath: String = "",
    onRemove: (DocumentFileModel) -> Unit,
    onCopyHere: () -> Unit = {},
    onMoveHere: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
    onRequestBatchObfuscate: () -> Unit = {},
    onRequestBatchDeobfuscate: () -> Unit = {},
    onRequestBatchGpgEncrypt: () -> Unit = {},
    onRequestBatchGpgDecrypt: () -> Unit = {},
    onRequestCompressToZip: () -> Unit = {},
    onRequestCompressTo7z: () -> Unit = {},
    onClearFilteredList: (List<DocumentFileModel>) -> Unit = {},
    onAddToPlayback: (List<DocumentFileModel>) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showHelpDialog by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }
    val filteredPendingItems = if (filterText.isBlank()) pendingList
    else runCatching { Regex(filterText) }.getOrNull()?.let { regex ->
        pendingList.filter { regex.containsMatchIn(it.name) }
    } ?: pendingList
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待处理列表 (${pendingList.size})") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "说明")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (pendingList.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("列表为空", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "当前目录：$currentDirPath",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("正则过滤文件名，留空显示全部") },
                        label = null
                    )
                    if (filterText.isNotEmpty()) {
                        IconButton(onClick = { filterText = "" }) {
                            Icon(Icons.Default.RemoveCircle, contentDescription = "清除过滤", Modifier.size(20.dp))
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCopyHere) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "拷贝到本处")
                    }
                    IconButton(onClick = onMoveHere) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = "移动到本处")
                    }
                    IconButton(onClick = onRequestDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = { onClearFilteredList(filteredPendingItems) }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空当前过滤结果")
                    }
                    val audioFromFiltered = filteredPendingItems.filter { !it.isDirectory && (it.name.endsWith(".mp3", ignoreCase = true) || it.name.endsWith(".ogg", ignoreCase = true)) }
                    IconButton(
                        onClick = {
                            if (audioFromFiltered.isEmpty()) {
                                Toast.makeText(context, "当前列表没有 MP3 或 OGG 文件", Toast.LENGTH_SHORT).show()
                            } else {
                                onAddToPlayback(audioFromFiltered)
                            }
                        }
                    ) {
                        Icon(Icons.Default.QueueMusic, contentDescription = "加入播放")
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRequestBatchObfuscate) {
                        Icon(Icons.Default.Lock, contentDescription = "混淆")
                    }
                    IconButton(onClick = onRequestBatchDeobfuscate) {
                        Icon(Icons.Default.LockOpen, contentDescription = "去混淆")
                    }
                    IconButton(onClick = onRequestBatchGpgEncrypt) {
                        Icon(Icons.Default.Lock, contentDescription = "加密", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onRequestBatchGpgDecrypt) {
                        Icon(Icons.Default.LockOpen, contentDescription = "解密", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onRequestCompressToZip) {
                        Icon(Icons.Default.Archive, contentDescription = "压缩为 ZIP")
                    }
                    IconButton(onClick = onRequestCompressTo7z) {
                        Icon(Icons.Default.Archive, contentDescription = "压缩为 7Z", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredPendingItems) { item ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            Modifier.size(24.dp),
                            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemove(item) }) {
                            Icon(
                                Icons.Default.RemoveCircle,
                                contentDescription = "从列表移除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            }
        }
    }
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("操作说明") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(8.dp))
                        Text("拷贝：将列表中所有项拷贝到当前目录（原文件保留）", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(8.dp))
                        Text("移动：将列表中所有项移动到当前目录（原位置删除）", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(8.dp))
                        Text("删除：可将列表中所有项移到回收站（可恢复），或直接永久删除（不可恢复）", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(8.dp))
                        Text("混淆：对列表中非 .qx 文件进行快速混淆（需同一密码）", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(8.dp))
                        Text("去混淆：对列表中 .qx 文件进行快速去混淆", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(8.dp))
                        Text("加密：对列表中非 .gpg 文件进行 GPG 加密，可选对称加密（密码）或公钥加密（选一个公钥，全部用该密钥加密）", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Archive, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(8.dp))
                        Text("压缩：支持将列表内容打包为 ZIP 或 7Z，可设置压缩包密码。", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(8.dp))
                        Text("解密：对列表中 .gpg 文件进行 GPG 解密（列表必须全是 .gpg）", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Clear, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(8.dp))
                        Text("清空列表：仅移除当前过滤结果中的项（未设过滤时即全部）。拷贝/移动/删除/混淆/加解密等批处理仍针对整个列表。", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QueueMusic, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(8.dp))
                        Text("加入播放：可选择新建播放列表，或把当前列表中的 MP3/OGG 追加到已有播放列表；新建列表会立即开始播放，追加到已有列表时会保留当前播放状态。", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Archive, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(8.dp))
                        Text("压缩：将待处理列表中所有文件和目录压缩为一个 ZIP 文件，保存到根目录，支持设置密码。", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("上方「当前目录」即执行拷贝/移动时的目标目录（从根目录起的路径）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("知道了") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    prefs: Preferences,
    playbackState: PlaybackState?,
    onStopPlayback: () -> Unit,
    onPlayPrev: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (positionMs: Int) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var lastPlaylistId by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showPlaylistNoteDialog by remember { mutableStateOf(false) }
    var playlistNoteEditTarget by remember { mutableStateOf<Playlist?>(null) }
    var playlistNoteEditText by remember { mutableStateOf("") }
    LaunchedEffect(prefs) {
        prefs.playlists.collect { playlists = it }
    }
    LaunchedEffect(prefs) {
        prefs.playerLastPlaylistId.collect { lastPlaylistId = it }
    }
    val selectedPlaylist = selectedPlaylistId?.let { id -> playlists.find { it.id == id } }
    if (selectedPlaylist == null && selectedPlaylistId != null) selectedPlaylistId = null

    BackHandler(enabled = selectedPlaylistId != null) {
        selectedPlaylistId = null
    }

    fun startPlaylist(pl: Playlist) {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_PLAY
            putExtra(EXTRA_PLAYLIST_ID, pl.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    fun startPlaylistFromIndex(pl: Playlist, index: Int) {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_PLAY
            putExtra(EXTRA_PLAYLIST_ID, pl.id)
            putExtra(EXTRA_START_INDEX, index)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(selectedPlaylist?.name ?: "播放器")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPlaylist != null) selectedPlaylistId = null
                        else onDismiss()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (selectedPlaylist != null) {
            val pl = selectedPlaylist
            if (pl.uris.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("列表已空", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                playlistNoteEditTarget = pl
                                playlistNoteEditText = pl.note
                                showPlaylistNoteDialog = true
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            if (pl.note.isNotBlank()) {
                                Text(pl.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            } else {
                                Text("点击添加备注", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(Icons.Filled.Edit, contentDescription = "编辑备注", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                    items(pl.uris.size) { i ->
                        val name = pl.names.getOrElse(i) { pl.uris[i].substringAfterLast('/') }
                        val isCurrentTrack = playbackState?.playlistId == pl.id && playbackState?.trackIndex == i
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { startPlaylistFromIndex(pl, i) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${i + 1}.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isCurrentTrack) {
                                Text(
                                    "正在播放",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            IconButton(onClick = { startPlaylistFromIndex(pl, i) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "从此处播放", Modifier.size(24.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (i > 0) scope.launch {
                                        val uris = pl.uris.toMutableList()
                                        val names = pl.names.toMutableList()
                                        uris.add(i - 1, uris.removeAt(i))
                                        names.add(i - 1, names.removeAt(i))
                                        prefs.updatePlaylist(pl.copy(uris = uris, names = names))
                                    }
                                },
                                enabled = i > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "上移", Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (i < pl.uris.size - 1) scope.launch {
                                        val uris = pl.uris.toMutableList()
                                        val names = pl.names.toMutableList()
                                        uris.add(i + 1, uris.removeAt(i))
                                        names.add(i + 1, names.removeAt(i))
                                        prefs.updatePlaylist(pl.copy(uris = uris, names = names))
                                    }
                                },
                                enabled = i < pl.uris.size - 1
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "下移", Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val uris = pl.uris.toMutableList().apply { removeAt(i) }
                                        val names = pl.names.toMutableList().apply { removeAt(i) }
                                        if (uris.isEmpty()) {
                                            prefs.removePlaylist(pl.id)
                                            selectedPlaylistId = null
                                        } else {
                                            prefs.updatePlaylist(pl.copy(uris = uris, names = names))
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                }
            }
        } else {
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (playbackState != null) {
                val state = playbackState
                var seekSliderPosition by remember(state.trackIndex, state.trackName) { mutableStateOf(state.positionMs.toFloat()) }
                var seekDragging by remember { mutableStateOf(false) }
                LaunchedEffect(state.positionMs, state.durationMs) {
                    if (!seekDragging) seekSliderPosition = state.positionMs.toFloat().coerceIn(0f, maxOf(1f, state.durationMs.toFloat()))
                }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            state.playlistName ?: "当前播放",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "${state.trackName} (${state.trackIndex + 1}/${state.totalTracks})",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.durationMs > 0) {
                            Spacer(Modifier.height(4.dp))
                            Slider(
                                value = seekSliderPosition,
                                onValueChange = {
                                    seekDragging = true
                                    seekSliderPosition = it
                                },
                                onValueChangeFinished = {
                                    onSeek(seekSliderPosition.toInt().coerceIn(0, state.durationMs))
                                    seekDragging = false
                                },
                                valueRange = 0f..maxOf(1f, state.durationMs.toFloat()),
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    activeTrackColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                                )
                            )
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                formatPlaybackTime(state.positionMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (state.durationMs > 0) formatPlaybackTime(state.durationMs) else "--:--",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = onPlayPrev) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
                            }
                            IconButton(onClick = onPlayPause) {
                                Icon(
                                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isPlaying) "暂停" else "播放"
                                )
                            }
                            IconButton(onClick = onPlayNext) {
                                Icon(Icons.Default.SkipNext, contentDescription = "下一首")
                            }
                        }
                    }
                }
            } else {
                val restorePlaylist = lastPlaylistId?.let { id -> playlists.find { it.id == id } }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "未在播放",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {}) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                            IconButton(onClick = { restorePlaylist?.let { startPlaylist(it) } }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (restorePlaylist != null) 1f else 0.5f))
                            }
                            IconButton(onClick = {}) {
                                Icon(Icons.Default.SkipNext, contentDescription = "下一首", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
            Text(
                "播放列表",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            if (playlists.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无播放列表。在待处理列表中勾选 MP3/OGG 后点击「加入播放」。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(playlists.size) { i ->
                        val pl = playlists[i]
                        val isCurrent = playbackState?.playlistId == pl.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { startPlaylist(pl) }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { startPlaylist(pl) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "播放",
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (pl.note.isNotBlank()) pl.note else pl.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = if (pl.note.isNotBlank()) 2 else 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("${pl.trackCount} 首", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { selectedPlaylistId = pl.id }) {
                                Icon(Icons.Default.List, contentDescription = "查看列表音乐", Modifier.size(24.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (i > 0) scope.launch {
                                        val ids = playlists.map { it.id }.toMutableList()
                                        ids.removeAt(i)
                                        ids.add(i - 1, pl.id)
                                        prefs.updatePlaylistOrder(ids)
                                    }
                                },
                                enabled = i > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "上移")
                            }
                            IconButton(
                                onClick = {
                                    if (i < playlists.size - 1) scope.launch {
                                        val ids = playlists.map { it.id }.toMutableList()
                                        ids.removeAt(i)
                                        ids.add(i + 1, pl.id)
                                        prefs.updatePlaylistOrder(ids)
                                    }
                                },
                                enabled = i < playlists.size - 1
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "下移")
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (playbackState?.playlistId == pl.id) {
                                            onStopPlayback()
                                        }
                                        prefs.removePlaylist(pl.id)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        }
        playlistNoteEditTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { playlistNoteEditTarget = null },
                title = { Text("播放列表备注") },
                text = {
                    OutlinedTextField(
                        value = playlistNoteEditText,
                        onValueChange = { playlistNoteEditText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注") },
                        minLines = 2,
                        maxLines = 4
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            prefs.updatePlaylist(target.copy(note = playlistNoteEditText.trim()))
                            playlistNoteEditTarget = null
                        }
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { playlistNoteEditTarget = null }) { Text("取消") } }
            )
        }
    }
}

@Composable
fun QuickObfuscatePasswordDialog(
    isObfuscate: Boolean,
    fileName: String,
    password: String,
    inProgress: Boolean = false,
    onPasswordChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (isObfuscate) "快速混淆" else "快速去混淆",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    singleLine = true,
                    enabled = !inProgress
                )
                if (inProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(if (isObfuscate) "混淆中…" else "去混淆中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !inProgress) { Text("取消") }
                    Button(onClick = { onConfirm(password) }, enabled = !inProgress) { Text("确定") }
                }
            }
        }
    }
}

@Composable
fun GpgPasswordDialog(
    isDecrypt: Boolean,
    fileName: String,
    password: String,
    passwordLabel: String = "密码",
    inProgress: Boolean = false,
    onPasswordChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (isDecrypt) "GnuPG 解密" else "GnuPG 加密",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(passwordLabel) },
                    singleLine = true,
                    enabled = !inProgress
                )
                if (inProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isDecrypt) "解密中…" else "加密中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !inProgress) { Text("取消") }
                    Button(
                        onClick = { onConfirm(password) },
                        enabled = !inProgress
                    ) { Text("确定") }
                }
            }
        }
    }
}

@Composable
fun GpgEncryptDialog(
    fileName: String,
    keys: List<Pair<Long, String>>,
    selectedKeyId: Long?,
    password: String,
    inProgress: Boolean = false,
    onSelectPassword: () -> Unit,
    onSelectKey: (Long) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirm: (String, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "GnuPG 加密",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !inProgress) { onSelectPassword() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedKeyId == null,
                        onClick = if (inProgress) null else onSelectPassword
                    )
                    Text("使用密码对称加密", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    singleLine = true,
                    enabled = !inProgress
                )
                if (keys.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("或选择一个公钥进行非对称加密", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    keys.forEach { (keyId, desc) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inProgress) { onSelectKey(keyId) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedKeyId == keyId,
                                onClick = if (inProgress) null else { { onSelectKey(keyId) } }
                            )
                            Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (inProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("加密中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !inProgress) { Text("取消") }
                    Button(
                        onClick = { onConfirm(password, selectedKeyId) },
                        enabled = !inProgress && (selectedKeyId != null || password.isNotBlank())
                    ) { Text("确定") }
                }
            }
        }
    }
}

/** 直接编辑 .pass 文件：解密后显示可编辑文本，存盘时若已修改则重新加密写回。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassEditScreen(
    fileName: String,
    initialDecryptedBytes: ByteArray,
    onSave: (ByteArray) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    var textContent by remember(initialDecryptedBytes) {
        mutableStateOf(initialDecryptedBytes.decodeToString())
    }
    var saveInProgress by remember { mutableStateOf(false) }

    BackHandler { if (!saveInProgress) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (!saveInProgress) onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (saveInProgress) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(
                        onClick = {
                            if (saveInProgress) return@TextButton
                            val newBytes = textContent.encodeToByteArray()
                            if (newBytes.contentEquals(initialDecryptedBytes)) {
                                onBack()
                            } else {
                                saveInProgress = true
                                onSave(newBytes)
                            }
                        },
                        enabled = !saveInProgress
                    ) { Text("保存") }
                }
            )
        }
    ) { padding ->
        OutlinedTextField(
            value = textContent,
            onValueChange = { textContent = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            minLines = 20,
            maxLines = Int.MAX_VALUE
        )
    }
}

@Composable
fun GpgMethodDialog(
    isDecrypt: Boolean,
    fileName: String,
    hasPublicKeys: Boolean,
    hasSecretKeys: Boolean,
    onSymmetric: () -> Unit,
    onPublicKey: () -> Unit,
    onSecretKey: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (isDecrypt) "GnuPG 解密" else "GnuPG 加密",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (isDecrypt) {
                    Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text("密码解密（对称）") }
                    if (hasSecretKeys) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onSecretKey, modifier = Modifier.fillMaxWidth()) { Text("私钥解密") }
                    }
                } else {
                    Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text("对称加密（密码）") }
                    if (hasPublicKeys) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onPublicKey, modifier = Modifier.fillMaxWidth()) { Text("公钥加密") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    }
}

@Composable
fun GpgPublicKeyPickerDialog(
    keys: List<Pair<Long, String>>,
    fileName: String,
    onConfirm: (keyId: Long, keyDesc: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedKeyId by remember { mutableStateOf<Long?>(keys.firstOrNull()?.first) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("公钥加密", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                keys.forEach { (keyId, desc) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedKeyId = keyId },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedKeyId == keyId,
                            onClick = { selectedKeyId = keyId }
                        )
                        Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = {
                        val kid = selectedKeyId
                        val desc = keys.find { it.first == kid }?.second ?: ""
                        if (kid != null) onConfirm(kid, desc)
                    }) { Text("加密") }
                }
            }
        }
    }
}

private val ABOUT_USAGE_TIPS = listOf(
    "你可以随时同步一个git库到本地，应用的很多其他功能也可以依靠这个库完成共享方面的功能",
    "内置的查看器可以同时看文本和二进制，也可以做文本和二进制编辑。",
    "可以用markdown渲染器直接查看markdown文件（.md或者.rst文件）。",
    "在其他应用中用本程序打开文件，可以把该文件保存到本程序的根目录中。",
    "双击文件可以把文件加入待处理列表进行批处理。",
    "混淆是一种不那么可靠，但速度极快的加解密功能，它仅加密文件头的内容，适合用于很大的文件的临时加解密。",
    "在配置中打开「显示过滤条件」，可以用正则表达式过滤文件名，还可以把所有过滤结果加入待处理列表。",
    "FTP数据交换功能可以启动一个ftp服务器，而且保持手机不休眠，所以记得主动退出它，或者设置一个自动退出时间。",
    "FTP传输（就算有密码保护）也是不安全的，请不要在不可靠的网络环境中使用。",
)

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrElse { "" }
    }
    val versionWarn = remember(versionName) {
        versionName.replace(Regex("[^0-9.]"), "").takeIf { s -> s.isNotEmpty() }?.toFloatOrNull()?.let { v -> v <= 1.0f } == true
    }
    val tip = remember { ABOUT_USAGE_TIPS.random() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Local Manager", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text("作者：柱子哥 <Kenneth-Lee-2012@qq.com>", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("版本：$versionName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (versionWarn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "本软件没有经过严肃的测试，请自担使用风险，作者不对任何数据破坏负责。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("随机提示（每次打开都更新）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

private const val MIN_PREVIEW_BYTES = 1024
private const val MAX_PREVIEW_BYTES = 10 * 1024 * 1024

@Composable
fun CacheManagementDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    var entries by remember { mutableStateOf<List<CacheEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { getCacheEntries(context) }
        loading = false
    }
    fun refresh() {
        loading = true
        scope.launch {
            entries = withContext(Dispatchers.IO) { getCacheEntries(context) }
            loading = false
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 400.dp)
            ) {
                Text("缓存管理", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                if (loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.padding(24.dp))
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        entries.forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.displayName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    Text(formatSize(entry.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { clearCacheEntry(context, entry) }
                                            refresh()
                                            Toast.makeText(context, "已清空 ${entry.displayName}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) { Text("清空") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (!loading && entries.isNotEmpty()) {
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    entries.forEach { clearCacheEntry(context, it) }
                                }
                                refresh()
                                Toast.makeText(context, "已清理全部缓存", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("清理全部") }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun DesktopShortcutButtons() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                try {
                    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                        Toast.makeText(context, "当前启动器不支持添加快捷方式到桌面", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    val intent = Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        putExtra(MainActivity.LAUNCH_TARGET_EXTRA, "player")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val shortcut = ShortcutInfoCompat.Builder(context, "launcher_player")
                        .setShortLabel(context.getString(R.string.launcher_player_name))
                        .setLongLabel(context.getString(R.string.launcher_player_name))
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher_player))
                        .setIntent(intent)
                        .build()
                    if (ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) {
                        Toast.makeText(context, "请将「管家播放器」放到桌面", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "当前启动器可能不支持添加快捷方式", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("添加「管家播放器」到桌面") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                try {
                    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                        Toast.makeText(context, "当前启动器不支持添加快捷方式到桌面", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    val intent = Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        putExtra(MainActivity.LAUNCH_TARGET_EXTRA, "quick_note")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val shortcut = ShortcutInfoCompat.Builder(context, "launcher_quick_note")
                        .setShortLabel(context.getString(R.string.launcher_quick_note_name))
                        .setLongLabel(context.getString(R.string.launcher_quick_note_name))
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher_quick_note))
                        .setIntent(intent)
                        .build()
                    if (ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) {
                        Toast.makeText(context, "请将「管家速记」放到桌面", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "当前启动器可能不支持添加快捷方式", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("添加「管家速记」到桌面") }
        Text(
            "删除桌面上的快捷方式不会卸载应用，可随时在此重新添加。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun ConfigDialog(
    onDismiss: () -> Unit,
    filterVisible: Boolean,
    onFilterVisibleChange: (Boolean) -> Unit,
    hideDotFiles: Boolean,
    onHideDotFilesChange: (Boolean) -> Unit,
    startupDecryptKey: Boolean,
    onStartupDecryptKeyChange: (Boolean) -> Unit,
    viewerPreviewBytes: Int,
    onViewerPreviewBytesChange: (Int) -> Unit,
    ftpPassword: String,
    onFtpPasswordChange: (String) -> Unit,
    ftpTimeoutMinutes: Int,
    onFtpTimeoutMinutesChange: (Int) -> Unit,
    onOpenGitConfig: () -> Unit,
    onManageKeys: () -> Unit,
    onOpenCacheManagement: () -> Unit,
    onExportConfig: () -> Unit,
    onChangeRoot: () -> Unit
) {
    var localViewerPreviewBytes by remember { mutableStateOf(viewerPreviewBytes.toString()) }
    var localFtpPassword by remember { mutableStateOf(ftpPassword) }
    var localFtpTimeoutMinutes by remember { mutableStateOf(ftpTimeoutMinutes.toString()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("配置", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("显示过滤条件", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = filterVisible, onCheckedChange = onFilterVisibleChange)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("不显示 . 开头的文件", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = hideDotFiles, onCheckedChange = onHideDotFilesChange)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("启动解密密钥", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("开启后启动需输入私钥密码解锁；解密成功则缓存在内存，后续使用密钥不再询问。不参与导出。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = startupDecryptKey, onCheckedChange = onStartupDecryptKeyChange)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("内部查看器预览长度（字节）", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = localViewerPreviewBytes,
                        onValueChange = { s ->
                            localViewerPreviewBytes = s
                            s.filter { it.isDigit() }.toIntOrNull()?.coerceIn(MIN_PREVIEW_BYTES, MAX_PREVIEW_BYTES)?.let { onViewerPreviewBytesChange(it) }
                        },
                        modifier = Modifier.width(120.dp),
                        singleLine = true
                    )
                }
                Text(
                    "范围：$MIN_PREVIEW_BYTES～${MAX_PREVIEW_BYTES / (1024 * 1024)}M",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FTP 密码", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 12.dp))
                    OutlinedTextField(
                        value = localFtpPassword,
                        onValueChange = { s ->
                            localFtpPassword = s
                            onFtpPasswordChange(s)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = {
                            Text("留空则无需密码", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FTP 倒计时（分钟）", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = localFtpTimeoutMinutes,
                        onValueChange = { s ->
                            localFtpTimeoutMinutes = s
                            s.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 1440)?.let { onFtpTimeoutMinutesChange(it) }
                        },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        placeholder = { Text("0=不退出", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
                Text("0 表示不自动退出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenGitConfig, modifier = Modifier.fillMaxWidth()) { Text("Git 配置") }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onManageKeys, modifier = Modifier.fillMaxWidth()) { Text("gpg钥匙管理") }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenCacheManagement, modifier = Modifier.fillMaxWidth()) { Text("缓存管理") }
                Spacer(Modifier.height(12.dp))
                Text("桌面入口", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(6.dp))
                DesktopShortcutButtons()
                Spacer(Modifier.height(12.dp))
                Button(onClick = onExportConfig, modifier = Modifier.fillMaxWidth()) { Text("导出配置") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onDismiss(); onChangeRoot() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("更换根目录") }
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

private sealed class PendingDelete {
    object Secret : PendingDelete()
    object AllPublic : PendingDelete()
    data class SinglePublic(val keyId: Long) : PendingDelete()
}

@Composable
fun KeyManagementDialog(
    context: Context,
    onDismiss: () -> Unit,
    onKeysChanged: () -> Unit = {},
    onOpenPubkeyShare: (() -> Unit)? = null
) {
    var publicKeys by remember { mutableStateOf<List<KeyInfo>>(emptyList()) }
    var secretKeys by remember { mutableStateOf<List<KeyInfo>>(emptyList()) }
    var keyFiles by remember { mutableStateOf(emptyList<Pair<String, Long>>()) }
    var loading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var importAsPrivate by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var deleteConfirmInput by remember { mutableStateOf("") }
    var pendingExportFilename by remember { mutableStateOf("") }
    var pendingExportSecret by remember { mutableStateOf(false) }
    var pendingExportKeyId by remember { mutableStateOf<Long?>(null) }
    var showGenerateKeyDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val asPriv = importAsPrivate
            scope.launch {
                val (ok, err) = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        if (asPriv) {
                            parseSecretKeyRingFromStream(bytes.inputStream())?.let { saveSecretKeyRing(context, it) }
                                ?: Pair(false, "无法解析私钥")
                        } else {
                            parsePublicKeyRingFromStream(bytes.inputStream())?.let { mergePublicKeyRing(context, it) }
                                ?: Pair(false, "无法解析公钥")
                        }
                    } ?: Pair(false, "无法读取文件")
                }
                if (ok) {
                    Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                    refreshTrigger++
                    onKeysChanged()
                } else {
                    Toast.makeText(context, "导入失败: $err", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val keyExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pgp-keys")) { uri ->
        if (uri != null && pendingExportFilename.isNotEmpty()) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    when {
                        pendingExportSecret -> getSecretKeyRingBytes(context)
                        pendingExportKeyId != null -> getSinglePublicKeyRingBytes(context, pendingExportKeyId!!)
                        else -> getAllPublicKeyRingsBytes(context)
                    }
                }
                if (bytes != null) {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    }
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                }
                pendingExportFilename = ""
                pendingExportKeyId = null
                pendingExportSecret = false
            }
        }
    }
    LaunchedEffect(refreshTrigger) {
        loading = true
        withContext(Dispatchers.IO) {
            val pubRings = loadPublicKeyRings(context)
            val secRings = loadSecretKeyRings(context)
            publicKeys = listPublicKeyInfos(pubRings)
            secretKeys = listSecretKeyInfos(secRings)
            keyFiles = listGpgKeyFiles(context)
        }
        loading = false
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("GnuPG 密钥管理", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("私钥仅保留一个；公钥可多个。生成密钥对或导入。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showGenerateKeyDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("生成密钥对") }
                if (onOpenPubkeyShare != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onDismiss(); onOpenPubkeyShare() }, modifier = Modifier.fillMaxWidth()) { Text("公钥分享 (Git 同步)") }
                }
                Spacer(Modifier.height(16.dp))
                if (loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("公钥", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row {
                                TextButton(onClick = { importAsPrivate = false; keyImportLauncher.launch(arrayOf("application/pgp-keys", "application/octet-stream", "*/*")) }) { Text("导入公钥") }
                                if (publicKeys.isNotEmpty()) {
                                    TextButton(onClick = { pendingDelete = PendingDelete.AllPublic; deleteConfirmInput = "" }) { Text("删除全部") }
                                    TextButton(onClick = {
                                        pendingExportFilename = "pubring.asc"
                                        pendingExportSecret = false
                                        pendingExportKeyId = null
                                        keyExportLauncher.launch("pubring.asc")
                                    }) { Text("导出全部") }
                                }
                            }
                        }
                        if (publicKeys.isEmpty()) Text("无", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else publicKeys.forEachIndexed { _, k ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${k.keyIdHex} ${k.primaryUserId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                val keyId = k.keyId
                                TextButton(onClick = {
                                        pendingExportFilename = "pubkey_${k.keyIdHex.replace("0x", "")}.asc"
                                        pendingExportSecret = false
                                        pendingExportKeyId = keyId
                                        keyExportLauncher.launch(pendingExportFilename)
                                }) { Text("导出", style = MaterialTheme.typography.labelSmall) }
                                if (publicKeys.size > 1) {
                                    TextButton(onClick = { pendingDelete = PendingDelete.SinglePublic(keyId); deleteConfirmInput = "" }) { Text("删", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("私钥", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row {
                                TextButton(onClick = { importAsPrivate = true; keyImportLauncher.launch(arrayOf("application/pgp-keys", "application/octet-stream", "*/*")) }) { Text("导入私钥") }
                                if (secretKeys.isNotEmpty()) {
                                    TextButton(onClick = {
                                        pendingExportFilename = "secring.asc"
                                        pendingExportSecret = true
                                        pendingExportKeyId = null
                                        keyExportLauncher.launch("secring.asc")
                                    }) { Text("导出私钥") }
                                    TextButton(onClick = { pendingDelete = PendingDelete.Secret; deleteConfirmInput = "" }) { Text("删除") }
                                }
                            }
                        }
                        if (secretKeys.isEmpty()) Text("无", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else secretKeys.forEach { k ->
                            Text("${k.keyIdHex} ${k.primaryUserId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("密钥文件", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (keyFiles.isEmpty()) Text("无", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else for (pair in keyFiles) {
                            Text("${pair.first} (${pair.second} B)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
    pendingDelete?.let { pending ->
        val (title, message) = when (pending) {
            is PendingDelete.Secret -> "确认删除私钥" to "将删除当前私钥，且无法恢复。请输入 yes 确认删除。"
            is PendingDelete.AllPublic -> "确认删除所有公钥" to "将删除全部公钥，且无法恢复。请输入 yes 确认删除。"
            is PendingDelete.SinglePublic -> "确认删除公钥" to "将删除该公钥，且无法恢复。请输入 yes 确认删除。"
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null; deleteConfirmInput = "" },
            title = { Text(title) },
            text = {
                Column {
                    Text(message)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deleteConfirmInput,
                        onValueChange = { deleteConfirmInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("输入 yes") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deleteConfirmInput.trim().lowercase() != "yes") return@Button
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                when (pending) {
                                    is PendingDelete.Secret -> deleteSecretKeys(context)
                                    is PendingDelete.AllPublic -> deleteAllPublicKeys(context)
                                    is PendingDelete.SinglePublic -> deletePublicKeyById(context, pending.keyId).first
                                }
                            }
                            if (ok) {
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                                onKeysChanged()
                            } else {
                                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                            }
                            pendingDelete = null
                            deleteConfirmInput = ""
                        }
                    },
                    enabled = deleteConfirmInput.trim().lowercase() == "yes"
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null; deleteConfirmInput = "" }) { Text("取消") } }
        )
    }
    if (showGenerateKeyDialog) {
        GenerateKeyDialog(
            context = context,
            onDismiss = { showGenerateKeyDialog = false },
            onSuccess = { refreshTrigger++; onKeysChanged() }
        )
    }
}

@Composable
fun GenerateKeyDialog(
    context: Context,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit = {}
) {
    var identity by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var generatingInProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!generatingInProgress) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("生成密钥对", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = identity,
                    onValueChange = { if (!generatingInProgress) identity = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("用户标识（如：姓名 <email@example.com>）") },
                    singleLine = true,
                    enabled = !generatingInProgress
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { if (!generatingInProgress) passphrase = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密钥保护密码（可留空表示无密码）") },
                    singleLine = true,
                    enabled = !generatingInProgress
                )
                if (generatingInProgress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("生成中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !generatingInProgress) { Text("取消") }
                    Button(
                        onClick = {
                            if (identity.isNotBlank() && !generatingInProgress) {
                                generatingInProgress = true
                                scope.launch {
                                    val (ok, errMsg) = withContext(Dispatchers.IO) {
                                        generateDefaultKey(context, identity.trim(), passphrase.toCharArray())
                                    }
                                    generatingInProgress = false
                                    if (ok) {
                                        Toast.makeText(context, "密钥已生成", Toast.LENGTH_SHORT).show()
                                        onSuccess()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "生成失败: ${errMsg ?: "未知错误"}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !generatingInProgress
                    ) { Text("生成") }
                }
            }
        }
    }
}

