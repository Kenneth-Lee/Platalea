package com.kenny.localmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.copyDocumentTo
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.getDirectoryToOpen
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.emptyTrash
import com.kenny.localmanager.file.getTrashItemCount
import com.kenny.localmanager.file.getTrashUriIfExists
import com.kenny.localmanager.file.isInsideDirectory
import com.kenny.localmanager.file.moveToTrash
import com.kenny.localmanager.file.quickObfuscate
import com.kenny.localmanager.file.quickDeobfuscate
import com.kenny.localmanager.file.isQuickObfuscatedFileName
import com.kenny.localmanager.file.restoreFromTrash
import com.kenny.localmanager.file.listFilesSafe
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.moveDocumentTo
import com.kenny.localmanager.file.renameDocument
import com.kenny.localmanager.file.toModel
import com.kenny.localmanager.file.compressToZip
import com.kenny.localmanager.file.unzipToParent
import com.kenny.localmanager.file.isZipEncrypted
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.generateDefaultKey
import com.kenny.localmanager.gpg.listEncryptionPublicKeyRings
import com.kenny.localmanager.gpg.KeyInfo
import com.kenny.localmanager.gpg.listDecryptionSecretKeys
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
import com.kenny.localmanager.gpg.loadPublicKeyRings
import com.kenny.localmanager.gpg.loadSecretKeyRings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 文件列表项副标题：大小与修改时间（与排序方式对应，便于对照）。 */
private fun fileItemSubtitle(model: DocumentFileModel): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(model.lastModified))
    return if (model.displaySize.isNotEmpty()) "${model.displaySize}  $dateStr" else dateStr
}

/** 规范化 content URI 字符串，修正 authority 中可能被错误成空格的句点（如 android .externalstorage -> android.externalstorage） */
private fun normalizeContentUriString(s: String): String {
    if (!s.startsWith("content://")) return s
    return s.replace("android ", "android.")
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
    initialFileUri: androidx.compose.runtime.MutableState<String?>? = null
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
    var currentUri by remember { mutableStateOf<String?>(null) }
    val fileBrowserBackStack = remember { mutableStateListOf<String>() }
    val fileListLazyState = rememberLazyListState()
    var viewerPreviewBytes by remember { mutableStateOf(4096) }
    var saveCompletedToken by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(prefs) {
        prefs.viewerPreviewBytes.collect { viewerPreviewBytes = it }
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
    LaunchedEffect(initialFileUri?.value) {
        val uriStr = initialFileUri?.value ?: return@LaunchedEffect
        initialFileUri.value = null
        pendingSaveFileUri = uriStr
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
                onBack = { markdownViewFile = null },
                onOpenFile = { openUri, openName, openEncrypted ->
                    markdownViewFile = Triple(openUri, openName, openEncrypted)
                }
            )
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
            var showPendingDeleteConfirm by remember { mutableStateOf(false) }
            var showConfigDialog by remember { mutableStateOf(false) }
            var showAboutDialog by remember { mutableStateOf(false) }
            var showKeyManagementDialog by remember { mutableStateOf(false) }
            var refreshTrigger by remember { mutableStateOf(0) }
            LaunchedEffect(saveCompletedToken) { if (saveCompletedToken > 0) refreshTrigger++ }
            var lastBackPressTime by remember { mutableStateOf(0L) }
            var gpgState by remember { mutableStateOf<GpgOpState?>(null) }
            var gpgMethod by remember { mutableStateOf<GpgMethod?>(null) }
            var gpgPassword by remember { mutableStateOf("") }
            var showGpgKeyPicker by remember { mutableStateOf(false) }
            var gpgPubEncryptInProgress by remember { mutableStateOf(false) }
            var showChangeRootConfirm by remember { mutableStateOf(false) }
            var quickObfuscateOp by remember { mutableStateOf<Pair<DocumentFileModel, Boolean>?>(null) }
            var quickObfuscatePassword by remember { mutableStateOf("") }
            var quickObfuscateInProgress by remember { mutableStateOf(false) }
            var batchObfuscateOp by remember { mutableStateOf<Pair<List<DocumentFileModel>, Boolean>?>(null) }
            var batchObfuscatePassword by remember { mutableStateOf("") }
            var batchObfuscateInProgress by remember { mutableStateOf(false) }
            var progressOp by remember { mutableStateOf<OperationProgress?>(null) }
            val currentDirPath = remember(displayUri, rootUri) { pathFromRoot(context, rootUri, displayUri) }
            val ftpManager = remember { com.kenny.localmanager.ftp.FtpServerManager(context) }
            var ftpPort by remember { mutableStateOf(2121) }
            var ftpPassword by remember { mutableStateOf<String?>(null) }
            var ftpTimeoutMinutes by remember { mutableStateOf(0) }
            var filterVisible by remember { mutableStateOf(true) }
            var showFtpScreen by remember { mutableStateOf(false) }
            var gitRepoUrl by remember { mutableStateOf<String?>(null) }
            var gitUserName by remember { mutableStateOf<String?>(null) }
            var gitUserEmail by remember { mutableStateOf<String?>(null) }
            var gitHttpsPassword by remember { mutableStateOf<String?>(null) }
            var showGitConfigDialog by remember { mutableStateOf(false) }
            var zipUnzipTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var zipCompressTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
            var zipUnzipPassword by remember { mutableStateOf("") }
            var zipUnzipEncrypted by remember { mutableStateOf<Boolean?>(null) }
            var zipCompressPassword by remember { mutableStateOf("") }
            LaunchedEffect(zipUnzipTarget) {
                zipUnzipEncrypted = null
                zipUnzipPassword = ""
                val target = zipUnzipTarget ?: return@LaunchedEffect
                zipUnzipEncrypted = withContext(Dispatchers.IO) { isZipEncrypted(context, target.uri) }
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
            LaunchedEffect(prefs) {
                prefs.gitRepoUrl.collect { gitRepoUrl = it }
            }
            LaunchedEffect(prefs) {
                prefs.gitUserName.collect { gitUserName = it }
            }
            LaunchedEffect(prefs) {
                prefs.gitUserEmail.collect { gitUserEmail = it }
            }
            LaunchedEffect(prefs) {
                prefs.gitHttpsPassword.collect { gitHttpsPassword = it }
            }
            BackHandler {
                when {
                    progressOp != null -> { } // 不响应返回，防止误触
                    showFtpScreen -> { ftpManager.stop(); showFtpScreen = false }
                    batchObfuscateOp != null -> { batchObfuscateOp = null; batchObfuscatePassword = "" }
                    quickObfuscateOp != null -> { quickObfuscateOp = null; quickObfuscatePassword = "" }
                    showChangeRootConfirm -> showChangeRootConfirm = false
                    gpgState != null -> {
                        if (showGpgKeyPicker) showGpgKeyPicker = false
                        else if (gpgMethod != null) gpgMethod = null
                        else { gpgState = null; gpgPassword = "" }
                    }
                    showKeyManagementDialog -> showKeyManagementDialog = false
                    showConfigDialog -> showConfigDialog = false
                    showGitConfigDialog -> showGitConfigDialog = false
                    showAboutDialog -> showAboutDialog = false
                    showPendingDeleteConfirm -> showPendingDeleteConfirm = false
                    showPendingList -> showPendingList = false
                    zipUnzipTarget != null -> zipUnzipTarget = null
                    zipCompressTarget != null -> zipCompressTarget = null
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
            var debugEnabled by remember { mutableStateOf(false) }
            var hideDotFiles by remember { mutableStateOf(false) }
            val debugLog = remember { mutableStateListOf<String>() }
            LaunchedEffect(prefs) {
                prefs.debugEnabled.collect { debugEnabled = it }
            }
            LaunchedEffect(prefs) {
                prefs.hideDotFiles.collect { hideDotFiles = it }
            }
            fun logDebug(msg: String) {
                scope.launch(Dispatchers.Main.immediate) {
                    debugLog.add(msg)
                }
            }
            val copyMoveLog: ((String) -> Unit)? = if (debugEnabled) { { logDebug(it) } } else null
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
                    modifier = if (debugEnabled) Modifier.weight(1f) else Modifier.fillMaxSize(),
                    currentUri = displayUri,
                    refreshTrigger = refreshTrigger,
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
                    onChangeRoot = {
                        val r = rootUri
                        if (r == null) {
                            treeLauncher.launch(null)
                            return@FileBrowserScreen
                        }
                        val root = Uri.parse(normalizeContentUriString(r))
                        val count = getTrashItemCount(context, root, root)
                        if (count > 0) showChangeRootConfirm = true
                        else treeLauncher.launch(null)
                    },
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
                    isViewingTrash = rootUri?.let { r ->
                        val root = Uri.parse(normalizeContentUriString(r))
                        val trashUri = getTrashUriIfExists(context, root, root)
                        trashUri != null && (displayUri == trashUri.toString() || isInsideDirectory(Uri.parse(displayUri), trashUri))
                    } ?: false,
                    onOpenFile = { uri, name, isEncrypted ->
                        viewingFile = Triple(uri, name, isEncrypted)
                    },
                    onOpenWithOtherApp = { uri, name ->
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, null))
                        } catch (_: android.content.ActivityNotFoundException) {
                            Toast.makeText(context, "没有可打开的应用", Toast.LENGTH_SHORT).show()
                        }
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
                    onRequestGpgDecrypt = { fileModel, dirUri ->
                        gpgMethod = null
                        showGpgKeyPicker = false
                        gpgState = GpgOpState.Decrypt(fileModel, dirUri)
                    },
                    onRequestGpgEncrypt = { fileModel, dirUri ->
                        gpgMethod = null
                        showGpgKeyPicker = false
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
                    onOpenGit = {
                        val r = rootUri?.let { normalizeContentUriString(it) }
                        when {
                            r == null -> Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
                            gitRepoUrl.isNullOrBlank() -> Toast.makeText(context, "请先在配置中填写 Git 仓库地址", Toast.LENGTH_SHORT).show()
                            !com.kenny.localmanager.git.isValidHttpsRepoUrl(gitRepoUrl!!) -> Toast.makeText(context, "请填写有效的 HTTPS 仓库地址", Toast.LENGTH_LONG).show()
                            else -> scope.launch {
                                runWithProgress("Git 同步…", null) { _ ->
                                    val result = withContext(Dispatchers.IO) {
                                        com.kenny.localmanager.git.cloneToTree(
                                            context, r, gitRepoUrl!!,
                                            userName = gitUserName, userEmail = gitUserEmail,
                                            httpsPassword = gitHttpsPassword
                                        ) { line -> logDebug(line) }
                                    }
                                    withContext(Dispatchers.Main.immediate) {
                                        result.fold(
                                            onSuccess = { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                logDebug("Git 下载: $msg")
                                                refreshTrigger++
                                            },
                                            onFailure = { e ->
                                                val err = "失败: ${e.message}"
                                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                                logDebug("Git 下载 $err")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    onUnzipRequest = { zipUnzipTarget = it },
                    onCompressToZipRequest = { zipCompressTarget = it }
                )
                if (debugEnabled) {
                    DebugPanel(
                        debugLog = debugLog,
                        onClear = { debugLog.clear() },
                        onCopyAll = {
                            val ctx = context
                            val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clip?.setPrimaryClip(ClipData.newPlainText("调试日志", debugLog.joinToString("\n")))
                            Toast.makeText(ctx, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
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
                            else gpgState = GpgOpState.BatchEncrypt(list, displayUri)
                        }
                    },
                    onRequestBatchGpgDecrypt = {
                        if (pendingList.any { !it.name.endsWith(".gpg", ignoreCase = true) }) {
                            Toast.makeText(context, "列表中存在非 .gpg 文件，无法批量解密", Toast.LENGTH_SHORT).show()
                        } else {
                            val list = pendingList.filter { !it.isDirectory }
                            if (list.isEmpty()) Toast.makeText(context, "没有可解密的文件", Toast.LENGTH_SHORT).show()
                            else gpgState = GpgOpState.BatchDecrypt(list, displayUri)
                        }
                    },
                    onClearFilteredList = { toRemove -> toRemove.forEach { pendingList.remove(it) } },
                    onDismiss = { showPendingList = false }
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
            if (showConfigDialog) {
                ConfigDialog(
                    onDismiss = { showConfigDialog = false },
                    debugEnabled = debugEnabled,
                    onDebugEnabledChange = { scope.launch { prefs.setDebugEnabled(it) } },
                    filterVisible = filterVisible,
                    onFilterVisibleChange = { scope.launch { prefs.setFilterVisible(it) } },
                    hideDotFiles = hideDotFiles,
                    onHideDotFilesChange = { scope.launch { prefs.setHideDotFiles(it) } },
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
                )
            }
            if (showGitConfigDialog) {
                GitConfigDialog(
                    onDismiss = { showGitConfigDialog = false },
                    repoUrl = gitRepoUrl.orEmpty(),
                    onRepoUrlChange = { s -> scope.launch { prefs.setGitRepoUrl(s.ifBlank { null }) } },
                    userName = gitUserName.orEmpty(),
                    onUserNameChange = { s -> scope.launch { prefs.setGitUserName(s.ifBlank { null }) } },
                    userEmail = gitUserEmail.orEmpty(),
                    onUserEmailChange = { s -> scope.launch { prefs.setGitUserEmail(s.ifBlank { null }) } },
                    httpsPassword = gitHttpsPassword.orEmpty(),
                    onHttpsPasswordChange = { s -> scope.launch { prefs.setGitHttpsPassword(s.ifBlank { null }) } }
                )
            }
            if (showKeyManagementDialog) {
                KeyManagementDialog(
                    context = context,
                    onDismiss = { showKeyManagementDialog = false },
                    onKeysChanged = { refreshTrigger++ }
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
                            refreshTrigger++
                        }
                    },
                    onDismiss = { batchObfuscateOp = null; batchObfuscatePassword = "" }
                )
            }
            zipUnzipTarget?.let { target ->
                val parentDirUri = Uri.parse(displayUri)
                val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                val encrypted = zipUnzipEncrypted
                AlertDialog(
                    onDismissRequest = { zipUnzipTarget = null; zipUnzipPassword = "" },
                    title = { Text("解压 ZIP") },
                    text = {
                        Column {
                            Text("确定将 ${target.name} 解压到当前目录？", color = MaterialTheme.colorScheme.onSurface)
                            if (encrypted == true) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = zipUnzipPassword,
                                    onValueChange = { zipUnzipPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("密码（加密 ZIP）") },
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
                                    val ok = withContext(Dispatchers.IO) {
                                        unzipToParent(
                                            context,
                                            target.uri,
                                            parentDirUri,
                                            treeUri,
                                            pwd
                                        ) { cur, tot ->
                                            scope.launch(Dispatchers.Main.immediate) { progressOp = OperationProgress("解压", cur, tot) }
                                        }
                                    }
                                    // 延迟再关闭进度条，避免 setProgress 的 Main.immediate 晚于本行执行导致进度条不消失
                                    delay(120)
                                    progressOp = null
                                    zipUnzipTarget = null
                                    zipUnzipPassword = ""
                                    zipUnzipEncrypted = null
                                    if (ok) {
                                        Toast.makeText(context, "解压完成", Toast.LENGTH_SHORT).show()
                                        refreshTrigger++
                                    } else {
                                        Toast.makeText(context, "解压失败（请检查密码或文件）", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) { Text("解压") }
                    },
                    dismissButton = { TextButton(onClick = { zipUnzipTarget = null; zipUnzipPassword = "" }) { Text("取消") } }
                )
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
            if (gpgState != null && gpgMethod == null && !showGpgKeyPicker) {
                val op = gpgState!!
                val pubRings = loadPublicKeyRings(context)
                val secRings = loadSecretKeyRings(context)
                val decKeys = listDecryptionSecretKeys(secRings)
                val hasPubKeys = listEncryptionPublicKeyRings(pubRings).isNotEmpty()
                GpgMethodDialog(
                    isDecrypt = op.isDecrypt,
                    fileName = op.displayName,
                    hasPublicKeys = hasPubKeys,
                    hasSecretKeys = decKeys.isNotEmpty(),
                    onSymmetric = { gpgMethod = GpgMethod.Symmetric },
                    onPublicKey = { showGpgKeyPicker = true },
                    onSecretKey = { gpgMethod = GpgMethod.SecretKeyDec },
                    onDismiss = { gpgState = null; gpgMethod = null; showGpgKeyPicker = false }
                )
            }
            if (gpgState != null && showGpgKeyPicker && (gpgState is GpgOpState.Encrypt || gpgState is GpgOpState.BatchEncrypt)) {
                val op = gpgState!!
                val pubRings = loadPublicKeyRings(context)
                val keys = listEncryptionPublicKeyRings(pubRings)
                GpgPublicKeyPickerDialog(
                    keys = keys,
                    fileName = op.displayName,
                    onConfirm = { keyId, keyDesc ->
                        showGpgKeyPicker = false
                        val pubKeyRing = findPublicKeyRing(pubRings, keyId)
                        if (pubKeyRing != null) {
                            when (op) {
                                is GpgOpState.Encrypt -> {
                                    val encOp = op
                                    val safeName = keyDesc.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).trim()
                                    val baseName = encOp.fileModel.name.removeSuffix(".gpg")
                                    val outName = if (safeName.isNotEmpty()) "${baseName}_$safeName.gpg" else "${baseName}.gpg"
                                    gpgPubEncryptInProgress = true
                                    scope.launch {
                                        try {
                                            val ok = withContext(Dispatchers.IO) {
                                                context.contentResolver.openInputStreamSafe(encOp.fileModel.uri)?.use { input ->
                                                    val plain = input.readBytes()
                                                    val encrypted = GpgHelper.encryptWithPublicKey(plain, pubKeyRing, encOp.fileModel.name)
                                                    if (encrypted != null) {
                                                        val dirUri = normalizeContentUriString(encOp.dirUri)
                                                        val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                                                        createFileWithBytes(context, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", encrypted)
                                                    } else false
                                                } ?: false
                                            }
                                            if (ok) Toast.makeText(context, "加密完成", Toast.LENGTH_SHORT).show()
                                            else Toast.makeText(context, "加密失败", Toast.LENGTH_SHORT).show()
                                            gpgState = null
                                            refreshTrigger++
                                        } finally {
                                            gpgPubEncryptInProgress = false
                                        }
                                    }
                                }
                                is GpgOpState.BatchEncrypt -> {
                                    val batchOp = op
                                    val safeName = keyDesc.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).trim()
                                    val dirUri = normalizeContentUriString(batchOp.dirUri)
                                    val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                                    gpgPubEncryptInProgress = true
                                    scope.launch {
                                        try {
                                            runWithProgress("公钥加密", batchOp.list.size) { setProgress ->
                                                withContext(Dispatchers.IO) {
                                                    batchOp.list.forEachIndexed { index, fileModel ->
                                                        context.contentResolver.openInputStreamSafe(fileModel.uri)?.use { input ->
                                                            val plain = input.readBytes()
                                                            val encrypted = GpgHelper.encryptWithPublicKey(plain, pubKeyRing, fileModel.name)
                                                            if (encrypted != null) {
                                                                val outName = if (safeName.isNotEmpty()) "${fileModel.name}_$safeName.gpg" else "${fileModel.name}.gpg"
                                                                createFileWithBytes(context, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", encrypted)
                                                            }
                                                        }
                                                        withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                                                    }
                                                }
                                            }
                                            Toast.makeText(context, "已公钥加密 ${batchOp.list.size} 个文件", Toast.LENGTH_SHORT).show()
                                            pendingList.clear()
                                            gpgState = null
                                            refreshTrigger++
                                        } finally {
                                            gpgPubEncryptInProgress = false
                                        }
                                    }
                                }
                                else -> { }
                            }
                        }
                    },
                    onDismiss = { showGpgKeyPicker = false; gpgState = null }
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
                if (gpgMethod == GpgMethod.Symmetric || gpgMethod == GpgMethod.SecretKeyDec) {
                    GpgPasswordDialog(
                        isDecrypt = op.isDecrypt,
                        fileName = op.displayName,
                        password = gpgPassword,
                        passwordLabel = if (gpgMethod == GpgMethod.SecretKeyDec) "密钥密码" else "密码",
                        inProgress = gpgInProgress,
                        onPasswordChange = { if (!gpgInProgress) gpgPassword = it },
                        onConfirm = { pwd ->
                            if (gpgInProgress) return@GpgPasswordDialog
                            gpgInProgress = true
                            val ctx = context
                            val dirUri = normalizeContentUriString(op.dirUri)
                            val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                            scope.launch {
                                try {
                                    val ok = when (op) {
                                        is GpgOpState.BatchDecrypt -> {
                                            runWithProgress("解密", op.list.size) { setProgress ->
                                                withContext(Dispatchers.IO) {
                                                    op.list.forEachIndexed { index, fileModel ->
                                                        when (gpgMethod) {
                                                            GpgMethod.SecretKeyDec -> {
                                                                val secretRings = loadSecretKeyRings(ctx)
                                                                if (secretRings != null) {
                                                                    ctx.contentResolver.openInputStreamSafe(fileModel.uri)?.use { input ->
                                                                        val encBytes = input.readBytes()
                                                                        val decrypted = GpgHelper.decryptWithSecretKey(
                                                                            java.io.ByteArrayInputStream(encBytes),
                                                                            secretRings, pwd.toCharArray()
                                                                        ) { _ -> }
                                                                        if (decrypted != null) {
                                                                            val outName = fileModel.name.removeSuffix(".gpg").ifEmpty { fileModel.name + ".dec" }
                                                                            createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", decrypted)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            else -> {
                                                                ctx.contentResolver.openInputStreamSafe(fileModel.uri)?.use { input ->
                                                                    val encBytes = input.readBytes()
                                                                    val decrypted = GpgHelper.decryptSymmetric(
                                                                        java.io.ByteArrayInputStream(encBytes),
                                                                        pwd.toCharArray()
                                                                    ) { _ -> }
                                                                    if (decrypted != null) {
                                                                        val outName = fileModel.name.removeSuffix(".gpg").ifEmpty { fileModel.name + ".dec" }
                                                                        createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", decrypted)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                                                    }
                                                }
                                            }
                                            true
                                        }
                                        is GpgOpState.BatchEncrypt -> {
                                            runWithProgress("加密", op.list.size) { setProgress ->
                                                withContext(Dispatchers.IO) {
                                                    op.list.forEachIndexed { index, fileModel ->
                                                        ctx.contentResolver.openInputStreamSafe(fileModel.uri)?.use { input ->
                                                            val plain = input.readBytes()
                                                            val encrypted = GpgHelper.encryptSymmetric(
                                                                plain, pwd.toCharArray(), fileModel.name
                                                            ) { _ -> }
                                                            if (encrypted != null) {
                                                                val outName = fileModel.name + ".gpg"
                                                                createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", encrypted)
                                                            }
                                                        }
                                                        withContext(Dispatchers.Main.immediate) { setProgress(index + 1) }
                                                    }
                                                }
                                            }
                                            true
                                        }
                                        else -> withContext(Dispatchers.IO) {
                                            when {
                                                op.isDecrypt && gpgMethod == GpgMethod.SecretKeyDec -> {
                                                    val secretRings = loadSecretKeyRings(ctx)
                                                    if (secretRings == null) false
                                                    else ctx.contentResolver.openInputStreamSafe((op as GpgOpState.Decrypt).fileModel.uri)?.use { input ->
                                                        val encBytes = input.readBytes()
                                                        val decrypted = GpgHelper.decryptWithSecretKey(
                                                            java.io.ByteArrayInputStream(encBytes),
                                                            secretRings, pwd.toCharArray()
                                                        ) { e ->
                                                            logDebug("[GPG] 私钥解密失败: ${(op as GpgOpState.Decrypt).fileModel.name}")
                                                            logDebug("  异常: ${e.javaClass.name}: ${e.message}")
                                                            e.stackTraceToString().lines().take(20).forEach { logDebug("    $it") }
                                                            logDebug("  输入: ${encBytes.size} bytes, 密钥密码长度: ${pwd.length}")
                                                        }
                                                        if (decrypted != null) {
                                                            logDebug("[GPG] 私钥解密成功: ${(op as GpgOpState.Decrypt).fileModel.name}, 输出=${decrypted.size} bytes")
                                                            val outName = (op as GpgOpState.Decrypt).fileModel.name.removeSuffix(".gpg").ifEmpty { (op as GpgOpState.Decrypt).fileModel.name + ".dec" }
                                                            createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", decrypted)
                                                        } else false
                                                    } ?: false
                                                }
                                                op.isDecrypt -> {
                                                    ctx.contentResolver.openInputStreamSafe((op as GpgOpState.Decrypt).fileModel.uri)?.use { input ->
                                                        val encBytes = input.readBytes()
                                                        val decrypted = GpgHelper.decryptSymmetric(
                                                            java.io.ByteArrayInputStream(encBytes),
                                                            pwd.toCharArray()
                                                        ) { e ->
                                                            logDebug("[GPG] 对称解密失败: ${(op as GpgOpState.Decrypt).fileModel.name}")
                                                            logDebug("  异常: ${e.javaClass.name}: ${e.message}")
                                                            e.stackTraceToString().lines().take(20).forEach { logDebug("    $it") }
                                                            logDebug("  输入: ${encBytes.size} bytes, 密码长度: ${pwd.length}")
                                                        }
                                                        if (decrypted != null) {
                                                            logDebug("[GPG] 对称解密成功: ${(op as GpgOpState.Decrypt).fileModel.name}, 算法=AES256/S2K=SHA-1(与加密一致), 输入=${encBytes.size} bytes, 输出=${decrypted.size} bytes, 密码长度=${pwd.length}")
                                                            val outName = (op as GpgOpState.Decrypt).fileModel.name.removeSuffix(".gpg").ifEmpty { (op as GpgOpState.Decrypt).fileModel.name + ".dec" }
                                                            createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", decrypted)
                                                        } else false
                                                    } ?: false
                                                }
                                                else -> {
                                                    ctx.contentResolver.openInputStreamSafe((op as GpgOpState.Encrypt).fileModel.uri)?.use { input ->
                                                        val plain = input.readBytes()
                                                        val encrypted = GpgHelper.encryptSymmetric(
                                                            plain, pwd.toCharArray(), (op as GpgOpState.Encrypt).fileModel.name
                                                        ) { e ->
                                                            logDebug("[GPG] 对称加密失败: ${(op as GpgOpState.Encrypt).fileModel.name}")
                                                            logDebug("  异常: ${e.javaClass.name}: ${e.message}")
                                                            e.stackTraceToString().lines().take(20).forEach { logDebug("    $it") }
                                                            logDebug("  明文: ${plain.size} bytes, 密码长度: ${pwd.length}")
                                                        }
                                                        if (encrypted != null) {
                                                            logDebug("[GPG] 对称加密成功: ${(op as GpgOpState.Encrypt).fileModel.name}, 算法=AES256, S2K=SHA-1, 明文=${plain.size} bytes, 密文=${encrypted.size} bytes, 密码长度=${pwd.length}")
                                                            val outName = (op as GpgOpState.Encrypt).fileModel.name + ".gpg"
                                                            createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", encrypted)
                                                        } else false
                                                    } ?: false
                                                }
                                            }
                                        }
                                    }
                                    if (ok) {
                                        Toast.makeText(ctx, when (op) {
                                            is GpgOpState.BatchDecrypt -> "已解密 ${op.list.size} 个文件"
                                            is GpgOpState.BatchEncrypt -> "已加密 ${op.list.size} 个文件"
                                            else -> if (op.isDecrypt) "解密完成" else "加密完成"
                                        }, Toast.LENGTH_SHORT).show()
                                        if (op is GpgOpState.BatchDecrypt || op is GpgOpState.BatchEncrypt) pendingList.clear()
                                    } else Toast.makeText(ctx, if (op.isDecrypt) "解密失败（开启「显示调试窗口」可查看详情）" else "加密失败", Toast.LENGTH_LONG).show()
                                    gpgState = null
                                    gpgMethod = null
                                    gpgPassword = ""
                                    refreshTrigger++
                                } finally {
                                    gpgInProgress = false
                                }
                            }
                        },
                        onDismiss = { if (!gpgInProgress) { gpgState = null; gpgMethod = null; gpgPassword = "" } }
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

/** 加密/解密方式：对称(密码)、公钥加密、私钥解密 */
private sealed class GpgMethod {
    object Symmetric : GpgMethod()
    object SecretKeyDec : GpgMethod()
}

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
fun FileBrowserScreen(
    modifier: Modifier = Modifier,
    currentUri: String,
    refreshTrigger: Int,
    pendingList: List<DocumentFileModel>,
    rootUri: String?,
    listState: LazyListState,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    onChangeRoot: () -> Unit,
    onEmptyTrash: (() -> Unit)? = null,
    onRestoreFromTrash: ((DocumentFileModel) -> Unit)? = null,
    isViewingTrash: Boolean = false,
    filterVisible: Boolean = true,
    hideDotFiles: Boolean = false,
    onOpenFile: (uri: String, name: String, isEncrypted: Boolean) -> Unit,
    onOpenWithOtherApp: (uri: String, name: String) -> Unit = { _, _ -> },
    onAddToPendingList: (DocumentFileModel) -> Unit,
    onRemoveFromPendingList: (DocumentFileModel) -> Unit,
    onShowPendingList: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenFtp: () -> Unit = {},
    onOpenGit: () -> Unit = {},
    onOpenMarkdownView: (uri: String, name: String, encrypted: Boolean) -> Unit = { _, _, _ -> },
    onRequestGpgDecrypt: (DocumentFileModel, String) -> Unit,
    onRequestGpgEncrypt: (DocumentFileModel, String) -> Unit,
    onRequestQuickObfuscate: ((DocumentFileModel) -> Unit)? = null,
    onRequestQuickDeobfuscate: ((DocumentFileModel) -> Unit)? = null,
    onConfirmDelete: ((DocumentFileModel, Boolean) -> Unit)? = null,
    onUnzipRequest: (DocumentFileModel) -> Unit = {},
    onCompressToZipRequest: (DocumentFileModel) -> Unit = {}
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
    var filterText by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(FileSortOrder.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(currentUri, refreshTrigger) {
        loading = true
        error = null
        try {
            val uri = Uri.parse(currentUri)
            var doc: DocumentFile? = null
            for (attempt in 0..3) {
                doc = if (currentUri.contains("/tree/")) {
                    DocumentFile.fromTreeUri(context, uri)
                } else {
                    DocumentFile.fromSingleUri(context, uri)
                }
                if (doc?.exists() == true) break
                if (attempt < 3) delay(100L * (attempt + 1))
            }
            val resolved = doc
            if (resolved == null || !resolved.exists()) {
                error = "无法访问该目录"
                items = emptyList()
            } else {
                items = resolved.listFilesSafe().mapNotNull { it.toModel() }
            }
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
                        IconButton(onClick = { onShowPendingList(true) }) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = "待处理列表")
                        }
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
                                text = { Text("更换根目录") },
                                onClick = {
                                    showOverflowMenu = false
                                    onChangeRoot()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("把当前过滤结果全部加入待处理列表") },
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
                                    onClick = {
                                        showOverflowMenu = false
                                        empty()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("FTP 数据交换") },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenFtp()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Git 同步") },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenGit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("配置") },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenConfig()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("关于") },
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
            }
        },
        floatingActionButton = {
            if (!loading && error == null) {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新建")
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp, 8.dp, 8.dp, 88.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredItems) { item ->
                            FileItem(
                                model = item,
                                isInPendingList = pendingList.any { it.uri == item.uri },
                                onClick = {
                                    if (item.isDirectory) {
                                        onNavigate(item.uri.toString())
                                    } else {
                                        when {
                                            item.name.endsWith(".zip", ignoreCase = true) ->
                                                onUnzipRequest(item)
                                            item.name.endsWith(".md", ignoreCase = true) || item.name.endsWith(".rst", ignoreCase = true) ->
                                                onOpenMarkdownView(item.uri.toString(), item.name, false)
                                            else -> {
                                                val encrypted = item.name.endsWith(".gpg", ignoreCase = true)
                                                onOpenFile(item.uri.toString(), item.name, encrypted)
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
                                onOpenWithOtherApp(menuTarget.uri.toString(), menuTarget.name)
                                contextMenuTarget = null
                            }
                        ) { Text("用其他应用打开", color = MaterialTheme.colorScheme.onSurface) }
                        TextButton(
                            onClick = {
                                showContextMenu = false
                                val enc = menuTarget.name.endsWith(".gpg", ignoreCase = true)
                                onOpenFile(menuTarget.uri.toString(), menuTarget.name, enc)
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
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onUnzipRequest(menuTarget)
                                    contextMenuTarget = null
                                }
                            ) { Text("解压 (ZIP)", color = MaterialTheme.colorScheme.onSurface) }
                        }
                        if (menuTarget.name.endsWith(".gpg", ignoreCase = true)) {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestGpgDecrypt(menuTarget, currentUri)
                                    contextMenuTarget = null
                                }
                            ) { Text("GnuPG 解密", color = MaterialTheme.colorScheme.onSurface) }
                        } else {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    onRequestGpgEncrypt(menuTarget, currentUri)
                                    contextMenuTarget = null
                                }
                            ) { Text("GnuPG 加密", color = MaterialTheme.colorScheme.onSurface) }
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
                            actionTarget = menuTarget
                            renameValue = menuTarget.name
                            showRenameDialog = true
                            contextMenuTarget = null
                        }
                    ) { Text("重命名", color = MaterialTheme.colorScheme.onSurface) }
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(
    model: DocumentFileModel,
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
        model.name.endsWith(".qx", ignoreCase = true) -> Icons.Default.LockOpen
        model.name.endsWith(".md", ignoreCase = true) || model.name.endsWith(".rst", ignoreCase = true) -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
    val iconTint = when {
        model.isDirectory -> MaterialTheme.colorScheme.primary
        model.name.endsWith(".gpg", ignoreCase = true) -> Color.Red
        model.name.endsWith(".qx", ignoreCase = true) -> Color.Red
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
            Icon(
                icon,
                contentDescription = null,
                Modifier.size(32.dp),
                tint = iconTint
            )
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
    onClearFilteredList: (List<DocumentFileModel>) -> Unit = {},
    onDismiss: () -> Unit
) {
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
                    Text("上方「当前目录」即执行拷贝/移动时的目标目录（从根目录起的路径）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("知道了") } }
        )
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
fun ConfigDialog(
    onDismiss: () -> Unit,
    debugEnabled: Boolean,
    onDebugEnabledChange: (Boolean) -> Unit,
    filterVisible: Boolean,
    onFilterVisibleChange: (Boolean) -> Unit,
    hideDotFiles: Boolean,
    onHideDotFilesChange: (Boolean) -> Unit,
    viewerPreviewBytes: Int,
    onViewerPreviewBytesChange: (Int) -> Unit,
    ftpPassword: String,
    onFtpPasswordChange: (String) -> Unit,
    ftpTimeoutMinutes: Int,
    onFtpTimeoutMinutesChange: (Int) -> Unit,
    onOpenGitConfig: () -> Unit,
    onManageKeys: () -> Unit
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
                    Text("显示调试窗口", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = debugEnabled, onCheckedChange = onDebugEnabledChange)
                }
                Spacer(Modifier.height(12.dp))
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
                    Text("FTP 密码", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(140.dp))
                    OutlinedTextField(
                        value = localFtpPassword,
                        onValueChange = { s ->
                            localFtpPassword = s
                            onFtpPasswordChange(s)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("留空则无需密码") }
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
                        placeholder = { Text("0=不退出") }
                    )
                }
                Text("0 表示不自动退出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenGitConfig, modifier = Modifier.fillMaxWidth()) { Text("Git 配置") }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onManageKeys, modifier = Modifier.fillMaxWidth()) { Text("gpg钥匙管理") }
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
    onKeysChanged: () -> Unit = {}
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

@Composable
fun DebugPanel(
    debugLog: List<String>,
    onClear: () -> Unit,
    onCopyAll: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("调试", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCopyAll) { Text("复制全部") }
                    TextButton(onClick = onClear) { Text("清空") }
                }
            }
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    debugLog.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
