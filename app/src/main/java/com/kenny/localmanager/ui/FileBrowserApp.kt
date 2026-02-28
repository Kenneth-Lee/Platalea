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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.listFilesSafe
import com.kenny.localmanager.file.renameDocument
import com.kenny.localmanager.file.toModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserApp() {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }
    var rootUri by remember { mutableStateOf<String?>(null) }
    var viewingFile by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rootUri = prefs.rootUri.first()
    }

    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch { prefs.setRootUri(uri.toString()) }
            rootUri = uri.toString()
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
            val backStack = remember { mutableStateListOf<String>() }
            FileBrowserScreen(
                currentUri = currentUri.value,
                onNavigate = { uri ->
                    backStack.add(currentUri.value)
                    currentUri.value = uri
                },
                onBack = {
                    if (backStack.isNotEmpty()) {
                        currentUri.value = backStack.removeAt(backStack.lastIndex)
                    }
                },
                canGoBack = backStack.isNotEmpty(),
                onOpenFile = { uri, name, isEncrypted ->
                    viewingFile = Triple(uri, name, isEncrypted)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    currentUri: String,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    onOpenFile: (uri: String, name: String, isEncrypted: Boolean) -> Unit
) {
    val context = LocalContext.current
    var items by remember(currentUri) { mutableStateOf<List<DocumentFileModel>>(emptyList()) }
    var loading by remember(currentUri) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }

    LaunchedEffect(currentUri) {
        loading = true
        error = null
        try {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(currentUri))
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        DocumentFile.fromTreeUri(context, Uri.parse(currentUri))?.name ?: "根目录",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(items) { item ->
                            FileItem(
                                model = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        onNavigate(item.uri.toString())
                                    } else {
                                        val encrypted = item.name.endsWith(".gpg", ignoreCase = true)
                                        onOpenFile(item.uri.toString(), item.name, encrypted)
                                    }
                                },
                                onLongClick = {
                                    actionTarget = item
                                    renameValue = item.name
                                    showRenameDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog && actionTarget != null) {
        var target = actionTarget!!
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
                    androidx.compose.material3.OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        label = { Text("新名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
        }
    }
}
