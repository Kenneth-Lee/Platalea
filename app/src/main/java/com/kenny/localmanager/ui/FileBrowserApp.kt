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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import android.widget.Toast
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.copyDocumentTo
import com.kenny.localmanager.file.createFileWithBytes
import com.kenny.localmanager.file.getDirectoryToOpen
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.listFilesSafe
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.file.moveDocumentTo
import com.kenny.localmanager.file.renameDocument
import com.kenny.localmanager.file.toModel
import com.kenny.localmanager.gpg.GpgHelper
import com.kenny.localmanager.gpg.findPublicKeyRing
import com.kenny.localmanager.gpg.generateDefaultKey
import com.kenny.localmanager.gpg.listEncryptionPublicKeys
import com.kenny.localmanager.gpg.findSecretKeyRing
import com.kenny.localmanager.gpg.KeyInfo
import com.kenny.localmanager.gpg.listDecryptionSecretKeys
import com.kenny.localmanager.gpg.listGpgKeyFiles
import com.kenny.localmanager.gpg.listPublicKeyInfos
import com.kenny.localmanager.gpg.listSecretKeyInfos
import com.kenny.localmanager.gpg.listSigningSecretKeys
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

/** 规范化 content URI 字符串，修正 authority 中可能被错误成空格的句点（如 android .externalstorage -> android.externalstorage） */
private fun normalizeContentUriString(s: String): String {
    if (!s.startsWith("content://")) return s
    return s.replace("android ", "android.")
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
    var viewingFile by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rootUri = prefs.rootUri.first()?.let { normalizeContentUriString(it) }
    }
    LaunchedEffect(initialFileUri?.value) {
        val uriStr = initialFileUri?.value ?: return@LaunchedEffect
        initialFileUri.value = null
        val dirUri = getDirectoryToOpen(context, Uri.parse(uriStr))?.toString()
        if (dirUri != null) initialDirUri = normalizeContentUriString(dirUri)
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

    when {
        rootUri == null && initialDirUri == null -> {
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
                        "选择根目录以浏览文件",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { treeLauncher.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("选择根目录")
                    }
                }
            }
        }
        viewingFile != null -> {
            val (uri, name, isEncrypted) = viewingFile!!
            BackHandler { viewingFile = null }
            ViewerScreen(
                fileUri = uri,
                fileName = name,
                isEncrypted = isEncrypted,
                onBack = { viewingFile = null }
            )
        }
        else -> {
            val currentUri = remember(rootUri, initialDirUri) { mutableStateOf(initialDirUri ?: rootUri!!) }
            val backStack = remember(rootUri) { mutableStateListOf<String>() }
            val pendingList = remember { mutableStateListOf<DocumentFileModel>() }
            var showPendingList by remember { mutableStateOf(false) }
            var showConfigDialog by remember { mutableStateOf(false) }
            var showKeyManagementDialog by remember { mutableStateOf(false) }
            var refreshTrigger by remember { mutableStateOf(0) }
            var lastBackPressTime by remember { mutableStateOf(0L) }
            var gpgState by remember { mutableStateOf<GpgOpState?>(null) }
            var gpgMethod by remember { mutableStateOf<GpgMethod?>(null) }
            var gpgPassword by remember { mutableStateOf("") }
            var showGpgKeyPicker by remember { mutableStateOf(false) }
            var selectedSigningKeyId by remember { mutableStateOf<Long?>(null) }
            var showVerifyResultDialog by remember { mutableStateOf<Triple<ByteArray, String, String>?>(null) }
            var gpgPubEncryptInProgress by remember { mutableStateOf(false) }
            BackHandler {
                when {
                    showVerifyResultDialog != null -> showVerifyResultDialog = null
                    gpgState != null -> {
                        if (showGpgKeyPicker) showGpgKeyPicker = false
                        else if (gpgMethod != null) gpgMethod = null
                        else { gpgState = null; gpgPassword = "" }
                    }
                    showKeyManagementDialog -> showKeyManagementDialog = false
                    showConfigDialog -> showConfigDialog = false
                    showPendingList -> showPendingList = false
                    backStack.isNotEmpty() -> currentUri.value = backStack.removeAt(backStack.lastIndex)
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
            val debugLog = remember { mutableStateListOf<String>() }
            LaunchedEffect(prefs) {
                prefs.debugEnabled.collect { debugEnabled = it }
            }
            fun logDebug(msg: String) {
                scope.launch(Dispatchers.Main.immediate) {
                    debugLog.add(msg)
                }
            }
            val copyMoveLog: ((String) -> Unit)? = if (debugEnabled) { { logDebug(it) } } else null
            Column(Modifier.fillMaxSize()) {
                if (gpgPubEncryptInProgress) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                FileBrowserScreen(
                    modifier = if (debugEnabled) Modifier.weight(1f) else Modifier.fillMaxSize(),
                    currentUri = currentUri.value,
                    refreshTrigger = refreshTrigger,
                    pendingList = pendingList,
                    onNavigate = { uri ->
                        backStack.add(currentUri.value)
                        currentUri.value = normalizeContentUriString(uri)
                    },
                    onBack = {
                        if (backStack.isNotEmpty()) {
                            currentUri.value = backStack.removeAt(backStack.lastIndex)
                        }
                    },
                    canGoBack = backStack.isNotEmpty(),
                    onChangeRoot = { treeLauncher.launch(null) },
                    onOpenFile = { uri, name, isEncrypted ->
                        viewingFile = Triple(uri, name, isEncrypted)
                    },
                    onAddToPendingList = { pendingList.add(it) },
                    onRemoveFromPendingList = { pendingList.remove(it) },
                    onCopyHere = {
                        var targetDirUri = currentUri.value
                        targetDirUri = normalizeContentUriString(targetDirUri)
                        copyMoveLog?.invoke("[拷贝] 目标: $targetDirUri")
                        val ctx = context
                        val list = pendingList.toList()
                        val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                        scope.launch {
                            val (ok, fail) = withContext(Dispatchers.IO) {
                                var o = 0
                                var f = 0
                                list.forEach { model ->
                                    if (copyDocumentTo(ctx, model.uri, Uri.parse(targetDirUri), treeUri, copyMoveLog) != null) o++
                                    else f++
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
                    },
                    onMoveHere = {
                        var targetDirUri = currentUri.value
                        targetDirUri = normalizeContentUriString(targetDirUri)
                        copyMoveLog?.invoke("[移动] 目标: $targetDirUri")
                        val ctx = context
                        val list = pendingList.toList()
                        val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                        scope.launch {
                            val (ok, fail) = withContext(Dispatchers.IO) {
                                var o = 0
                                var f = 0
                                list.forEach { model ->
                                    if (moveDocumentTo(ctx, model.uri, Uri.parse(targetDirUri), treeUri, copyMoveLog)) o++
                                    else f++
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
                    },
                    onShowPendingList = { showPendingList = it },
                    onRefresh = { refreshTrigger++ },
                    onOpenConfig = { showConfigDialog = true },
                    onRequestGpgDecrypt = { fileModel, dirUri ->
                        gpgMethod = null
                        showGpgKeyPicker = false
                        gpgState = GpgOpState.Decrypt(fileModel, dirUri)
                    },
                    onRequestGpgEncrypt = { fileModel, dirUri ->
                        gpgMethod = null
                        showGpgKeyPicker = false
                        gpgState = GpgOpState.Encrypt(fileModel, dirUri)
                    }
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
                    onRemove = { pendingList.remove(it) },
                    onDismiss = { showPendingList = false }
                )
            }
            if (showConfigDialog) {
                ConfigDialog(
                    onDismiss = { showConfigDialog = false },
                    debugEnabled = debugEnabled,
                    onDebugEnabledChange = { scope.launch { prefs.setDebugEnabled(it) } },
                    onManageKeys = { showConfigDialog = false; showKeyManagementDialog = true }
                )
            }
            if (showKeyManagementDialog) {
                KeyManagementDialog(
                    context = context,
                    onDismiss = { showKeyManagementDialog = false },
                    onKeysChanged = { refreshTrigger++ }
                )
            }
            if (gpgState != null && gpgMethod == null && !showGpgKeyPicker) {
                val op = gpgState!!
                val pubRings = loadPublicKeyRings(context)
                val secRings = loadSecretKeyRings(context)
                val decKeys = listDecryptionSecretKeys(secRings)
                val hasPubKeys = listEncryptionPublicKeys(pubRings).isNotEmpty()
                val hasAnyPubKeys = listPublicKeyInfos(pubRings).isNotEmpty()
                GpgMethodDialog(
                    isDecrypt = op.isDecrypt,
                    fileName = op.fileModel.name,
                    hasPublicKeys = hasPubKeys,
                    hasAnyPublicKeysForVerify = hasAnyPubKeys,
                    hasSecretKeys = decKeys.isNotEmpty(),
                    hasSigningKeys = listSigningSecretKeys(secRings).isNotEmpty(),
                    onSymmetric = { gpgMethod = GpgMethod.Symmetric },
                    onPublicKey = { showGpgKeyPicker = true },
                    onSecretKey = { gpgMethod = GpgMethod.SecretKeyDec },
                    onVerifySign = { gpgMethod = GpgMethod.VerifySign },
                    onSign = { selectedSigningKeyId = listSigningSecretKeys(secRings).firstOrNull()?.first; gpgMethod = GpgMethod.Sign },
                    onDismiss = { gpgState = null; gpgMethod = null; showGpgKeyPicker = false }
                )
            }
            if (gpgState != null && showGpgKeyPicker && gpgState is GpgOpState.Encrypt) {
                val op = gpgState!! as GpgOpState.Encrypt
                val pubRings = loadPublicKeyRings(context)
                val keys = listEncryptionPublicKeys(pubRings)
                GpgPublicKeyPickerDialog(
                    keys = keys,
                    fileName = op.fileModel.name,
                    onConfirm = { keyId, keyDesc ->
                        showGpgKeyPicker = false
                        val pubKeyRing = findPublicKeyRing(pubRings, keyId)
                        if (pubKeyRing != null) {
                            val safeName = keyDesc.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).trim()
                            val baseName = op.fileModel.name.removeSuffix(".gpg")
                            val outName = if (safeName.isNotEmpty()) "${baseName}_$safeName.gpg" else "${baseName}.gpg"
                            gpgPubEncryptInProgress = true
                            scope.launch {
                                try {
                                    val ok = withContext(Dispatchers.IO) {
                                        context.contentResolver.openInputStreamSafe(op.fileModel.uri)?.use { input ->
                                            val plain = input.readBytes()
                                            val encrypted = GpgHelper.encryptWithPublicKey(plain, pubKeyRing, op.fileModel.name)
                                            if (encrypted != null) {
                                                val dirUri = normalizeContentUriString(op.dirUri)
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
                    },
                    onDismiss = { showGpgKeyPicker = false; gpgState = null }
                )
            }
            if (gpgState != null && gpgMethod == GpgMethod.VerifySign) {
                val op = gpgState!!
                if (op is GpgOpState.Decrypt) {
                    LaunchedEffect(op) {
                        val ctx = context
                        val (plain, verified) = withContext(Dispatchers.IO) {
                            ctx.contentResolver.openInputStreamSafe(op.fileModel.uri)?.use { input ->
                                GpgHelper.verifySignature(input.readBytes(), loadPublicKeyRings(ctx))
                            } ?: (null to false)
                        }
                        if (verified) Toast.makeText(ctx, "验证通过", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(ctx, "验证失败", Toast.LENGTH_SHORT).show()
                        if (verified && plain != null && plain.isNotEmpty()) {
                            val dirUri = normalizeContentUriString(op.dirUri)
                            val outName = op.fileModel.name.removeSuffix(".gpg").ifEmpty { op.fileModel.name + ".dec" }
                            showVerifyResultDialog = Triple(plain, dirUri, outName)
                        }
                        gpgState = null
                        gpgMethod = null
                    }
                }
            }
            showVerifyResultDialog?.let { (plain, dirUri, outName) ->
                AlertDialog(
                    onDismissRequest = { showVerifyResultDialog = null },
                    title = { Text("验证通过") },
                    text = { Text("是否保存解密内容到新文件？") },
                    confirmButton = {
                        Button(onClick = {
                            val treeUri = rootUri?.let { Uri.parse(normalizeContentUriString(it)) }
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    createFileWithBytes(context, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", plain)
                                }
                                refreshTrigger++
                                showVerifyResultDialog = null
                                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("保存") }
                    },
                    dismissButton = { TextButton(onClick = { showVerifyResultDialog = null }) { Text("取消") } }
                )
            }
            var gpgInProgress by remember { mutableStateOf(false) }
            gpgState?.let { op ->
                if (gpgMethod == GpgMethod.Symmetric || gpgMethod == GpgMethod.SecretKeyDec || (gpgMethod == GpgMethod.Sign && op is GpgOpState.Encrypt && selectedSigningKeyId != null)) {
                    GpgPasswordDialog(
                        isDecrypt = op.isDecrypt,
                        fileName = op.fileModel.name,
                        password = gpgPassword,
                        passwordLabel = if (gpgMethod == GpgMethod.SecretKeyDec || gpgMethod == GpgMethod.Sign) "密钥密码" else "密码",
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
                                    val ok = withContext(Dispatchers.IO) {
                                    when {
                                        op.isDecrypt && gpgMethod == GpgMethod.SecretKeyDec -> {
                                            val secretRings = loadSecretKeyRings(ctx)
                                            if (secretRings == null) false
                                            else ctx.contentResolver.openInputStreamSafe(op.fileModel.uri)?.use { input ->
                                                val encBytes = input.readBytes()
                                                val decrypted = GpgHelper.decryptWithSecretKey(
                                                    java.io.ByteArrayInputStream(encBytes),
                                                    secretRings, pwd.toCharArray()
                                                ) { e ->
                                                    logDebug("[GPG] 私钥解密失败: ${op.fileModel.name}")
                                                    logDebug("  异常: ${e.javaClass.name}: ${e.message}")
                                                    e.stackTraceToString().lines().take(20).forEach { logDebug("    $it") }
                                                    logDebug("  输入: ${encBytes.size} bytes, 密钥密码长度: ${pwd.length}")
                                                }
                                                if (decrypted != null) {
                                                    logDebug("[GPG] 私钥解密成功: ${op.fileModel.name}, 输出=${decrypted.size} bytes")
                                                    val outName = op.fileModel.name.removeSuffix(".gpg").ifEmpty { op.fileModel.name + ".dec" }
                                                    createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", decrypted)
                                                } else false
                                            } ?: false
                                        }
                                        gpgMethod == GpgMethod.Sign && op is GpgOpState.Encrypt -> {
                                            val encOp = op as GpgOpState.Encrypt
                                            val keyId = selectedSigningKeyId!!
                                            val secretRings = loadSecretKeyRings(ctx)
                                            val secretRing = findSecretKeyRing(secretRings, keyId)
                                            if (secretRing == null) false
                                            else ctx.contentResolver.openInputStreamSafe(encOp.fileModel.uri)?.use { input ->
                                                val plain = input.readBytes()
                                                val signed = GpgHelper.signWithSecretKey(
                                                    plain, secretRing, pwd.toCharArray(), encOp.fileModel.name
                                                ) { e ->
                                                    logDebug("[GPG] 私钥签名失败: ${encOp.fileModel.name}")
                                                    logDebug("  异常: ${e.javaClass.name}: ${e.message}")
                                                }
                                                if (signed != null) {
                                                    val outName = encOp.fileModel.name + ".sig"
                                                    createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", signed)
                                                } else false
                                            } ?: false
                                        }
                                        op.isDecrypt -> {
                                            ctx.contentResolver.openInputStreamSafe(op.fileModel.uri)?.use { input ->
                                                val encBytes = input.readBytes()
                                                val decrypted = GpgHelper.decryptSymmetric(
                                                    java.io.ByteArrayInputStream(encBytes),
                                                    pwd.toCharArray()
                                                ) { e ->
                                                    logDebug("[GPG] 对称解密失败: ${op.fileModel.name}")
                                                    logDebug("  异常: ${e.javaClass.name}: ${e.message}")
                                                    e.stackTraceToString().lines().take(20).forEach { logDebug("    $it") }
                                                    logDebug("  输入: ${encBytes.size} bytes, 密码长度: ${pwd.length}")
                                                }
                                                if (decrypted != null) {
                                                    logDebug("[GPG] 对称解密成功: ${op.fileModel.name}, 算法=AES256/S2K=SHA-1(与加密一致), 输入=${encBytes.size} bytes, 输出=${decrypted.size} bytes, 密码长度=${pwd.length}")
                                                    val outName = op.fileModel.name.removeSuffix(".gpg").ifEmpty { op.fileModel.name + ".dec" }
                                                    createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", decrypted)
                                                } else false
                                            } ?: false
                                        }
                                        else -> {
                                            ctx.contentResolver.openInputStreamSafe(op.fileModel.uri)?.use { input ->
                                                val plain = input.readBytes()
                                                val encrypted = GpgHelper.encryptSymmetric(
                                                    plain, pwd.toCharArray(), op.fileModel.name
                                                ) { e ->
                                                    logDebug("[GPG] 对称加密失败: ${op.fileModel.name}")
                                                    logDebug("  异常: ${e.javaClass.name}: ${e.message}")
                                                    e.stackTraceToString().lines().take(20).forEach { logDebug("    $it") }
                                                    logDebug("  明文: ${plain.size} bytes, 密码长度: ${pwd.length}")
                                                }
                                                if (encrypted != null) {
                                                    logDebug("[GPG] 对称加密成功: ${op.fileModel.name}, 算法=AES256, S2K=SHA-1, 明文=${plain.size} bytes, 密文=${encrypted.size} bytes, 密码长度=${pwd.length}")
                                                    val outName = op.fileModel.name + ".gpg"
                                                    createFileWithBytes(ctx, Uri.parse(dirUri), treeUri, outName, "application/octet-stream", encrypted)
                                                } else false
                                            } ?: false
                                        }
                                    }
                                }
                                    if (ok) Toast.makeText(ctx, if (op.isDecrypt) "解密完成" else if (gpgMethod == GpgMethod.Sign) "签名完成" else "加密完成", Toast.LENGTH_SHORT).show()
                                    else Toast.makeText(ctx, if (op.isDecrypt) "解密失败（开启「显示调试窗口」可查看详情）" else "加密/签名失败", Toast.LENGTH_LONG).show()
                                    gpgState = null
                                    gpgMethod = null
                                    gpgPassword = ""
                                    selectedSigningKeyId = null
                                    refreshTrigger++
                                } finally {
                                    gpgInProgress = false
                                }
                            }
                        },
                        onDismiss = { if (!gpgInProgress) { gpgState = null; gpgMethod = null; gpgPassword = ""; selectedSigningKeyId = null } }
                    )
                }
            }
        }
    }
}

private sealed class GpgOpState(val isDecrypt: Boolean, val fileModel: DocumentFileModel, val dirUri: String) {
    class Decrypt(fileModel: DocumentFileModel, dirUri: String) : GpgOpState(true, fileModel, dirUri)
    class Encrypt(fileModel: DocumentFileModel, dirUri: String) : GpgOpState(false, fileModel, dirUri)
}

/** 加密/解密方式：对称(密码)、公钥加密、私钥解密、验证签名、私钥签名 */
private sealed class GpgMethod {
    object Symmetric : GpgMethod()
    object SecretKeyDec : GpgMethod()
    object VerifySign : GpgMethod()
    object Sign : GpgMethod()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    modifier: Modifier = Modifier,
    currentUri: String,
    refreshTrigger: Int,
    pendingList: List<DocumentFileModel>,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    onChangeRoot: () -> Unit,
    onOpenFile: (uri: String, name: String, isEncrypted: Boolean) -> Unit,
    onAddToPendingList: (DocumentFileModel) -> Unit,
    onRemoveFromPendingList: (DocumentFileModel) -> Unit,
    onCopyHere: () -> Unit,
    onMoveHere: () -> Unit,
    onShowPendingList: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenConfig: () -> Unit,
    onRequestGpgDecrypt: (DocumentFileModel, String) -> Unit,
    onRequestGpgEncrypt: (DocumentFileModel, String) -> Unit
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
    var showPendingMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<DocumentFileModel?>(null) }
    var filterText by remember { mutableStateOf("") }

    LaunchedEffect(currentUri, refreshTrigger) {
        loading = true
        error = null
        try {
            val uri = Uri.parse(currentUri)
            val doc = if (currentUri.contains("/tree/")) {
                DocumentFile.fromTreeUri(context, uri)
            } else {
                DocumentFile.fromSingleUri(context, uri)
            }
            if (doc == null || !doc.exists()) {
                error = "无法访问该目录"
                items = emptyList()
            } else {
                items = doc.listFilesSafe()
                    .mapNotNull { it.toModel() }
                    .sortedWith(
                        compareBy<DocumentFileModel> { !it.isDirectory }.thenBy { it.name.lowercase() }
                    )
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
            items = emptyList()
        }
        loading = false
    }

    val filteredItems = remember(items, filterText) {
        if (filterText.isBlank()) items
        else runCatching { Regex(filterText) }.getOrNull()?.let { regex ->
            items.filter { regex.containsMatchIn(it.name) }
        } ?: items
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
                        if (pendingList.isNotEmpty()) {
                            IconButton(onClick = { showPendingMenu = true }) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = "待处理列表")
                            }
                            DropdownMenu(
                                expanded = showPendingMenu,
                                onDismissRequest = { showPendingMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("拷贝到本处") },
                                    onClick = {
                                        showPendingMenu = false
                                        onCopyHere()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("移动到本处") },
                                    onClick = {
                                        showPendingMenu = false
                                        onMoveHere()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("待处理列表 (${pendingList.size})") },
                                    onClick = {
                                        showPendingMenu = false
                                        onShowPendingList(true)
                                    }
                                )
                            }
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = onChangeRoot) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "更换根目录")
                        }
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("配置") },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenConfig()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
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
                        Modifier.fillMaxSize(),
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
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            data = item.uri
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(Intent.createChooser(intent, null))
                                        } catch (_: android.content.ActivityNotFoundException) {
                                            val encrypted = item.name.endsWith(".gpg", ignoreCase = true)
                                            onOpenFile(item.uri.toString(), item.name, encrypted)
                                        }
                                    }
                                },
                                onLongClick = {
                                    contextMenuTarget = item
                                    showContextMenu = true
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
                                val enc = menuTarget.name.endsWith(".gpg", ignoreCase = true)
                                onOpenFile(menuTarget.uri.toString(), menuTarget.name, enc)
                                contextMenuTarget = null
                            }
                        ) { Text("用内置查看器打开", color = MaterialTheme.colorScheme.onSurface) }
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
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onAddToPendingList(menuTarget)
                            contextMenuTarget = null
                        }
                    ) { Text("加入待处理列表", color = MaterialTheme.colorScheme.onSurface) }
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
                            dir?.takeIf { it.isDirectory }?.createFile("application/octet-stream", name)
                            showCreateFileDialog = false
                            onRefresh()
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
                            dir?.takeIf { it.isDirectory }?.createDirectory(name)
                            showCreateDirDialog = false
                            onRefresh()
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
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("确定删除「${target.name}」吗？此操作不可恢复。", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (context.contentResolver.deleteDocument(target.uri)) {
                            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        } else {
                            Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteConfirm = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
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
    onLongClick: () -> Unit
) {
    val icon = when {
        model.isDirectory -> Icons.Default.Folder
        model.name.endsWith(".gpg", ignoreCase = true) -> Icons.Default.Lock
        else -> Icons.Default.InsertDriveFile
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                tint = if (model.isDirectory) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (model.displaySize.isNotEmpty()) {
                    Text(
                        model.displaySize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    onRemove: (DocumentFileModel) -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待处理列表 (${pendingList.size})") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
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
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(pendingList) { item ->
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
    hasAnyPublicKeysForVerify: Boolean,
    hasSecretKeys: Boolean,
    hasSigningKeys: Boolean,
    onSymmetric: () -> Unit,
    onPublicKey: () -> Unit,
    onSecretKey: () -> Unit,
    onVerifySign: () -> Unit,
    onSign: () -> Unit,
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
                    if (hasAnyPublicKeysForVerify) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onVerifySign, modifier = Modifier.fillMaxWidth()) { Text("验证签名（公钥）") }
                    }
                } else {
                    Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text("对称加密（密码）") }
                    if (hasPublicKeys) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onPublicKey, modifier = Modifier.fillMaxWidth()) { Text("公钥加密") }
                    }
                    if (hasSigningKeys) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onSign, modifier = Modifier.fillMaxWidth()) { Text("私钥签名") }
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

@Composable
fun ConfigDialog(
    onDismiss: () -> Unit,
    debugEnabled: Boolean,
    onDebugEnabledChange: (Boolean) -> Unit,
    onManageKeys: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp)) {
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
                Button(onClick = onManageKeys, modifier = Modifier.fillMaxWidth()) { Text("管理密钥") }
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
                        else publicKeys.forEachIndexed { i, k ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${k.keyIdHex} ${k.primaryUserId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                val keyId = k.keyId
                                if (keyId != null) {
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
