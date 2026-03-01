package com.kenny.localmanager.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.listFilesSafe
import com.kenny.localmanager.file.moveDocumentTo
import com.kenny.localmanager.file.renameDocument
import com.kenny.localmanager.file.toModel
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
fun FileBrowserApp() {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }
    var rootUri by remember { mutableStateOf<String?>(null) }
    var viewingFile by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rootUri = prefs.rootUri.first()?.let { normalizeContentUriString(it) }
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
        rootUri == null -> {
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
            ViewerScreen(
                fileUri = uri,
                fileName = name,
                isEncrypted = isEncrypted,
                onBack = { viewingFile = null }
            )
        }
        else -> {
            val currentUri = remember(rootUri) { mutableStateOf(rootUri!!) }
            val backStack = remember(rootUri) { mutableStateListOf<String>() }
            val pendingList = remember { mutableStateListOf<DocumentFileModel>() }
            var showPendingList by remember { mutableStateOf(false) }
            var showConfigDialog by remember { mutableStateOf(false) }
            var refreshTrigger by remember { mutableStateOf(0) }
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
                    onOpenConfig = { showConfigDialog = true }
                )
                if (debugEnabled) {
                    DebugPanel(
                        debugLog = debugLog,
                        onClear = { debugLog.clear() }
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
                    onDebugEnabledChange = { scope.launch { prefs.setDebugEnabled(it) } }
                )
            }
        }
    }
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
    onOpenConfig: () -> Unit
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
fun ConfigDialog(
    onDismiss: () -> Unit,
    debugEnabled: Boolean,
    onDebugEnabledChange: (Boolean) -> Unit
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
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

@Composable
fun DebugPanel(
    debugLog: List<String>,
    onClear: () -> Unit
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
                TextButton(onClick = onClear) { Text("清空") }
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
