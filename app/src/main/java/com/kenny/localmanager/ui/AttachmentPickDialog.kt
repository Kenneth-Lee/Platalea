package com.kenny.localmanager.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.listChildrenFast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 独立于 [FileBrowserScreen] 的附件选择对话框：单次只选一个文件或目录，不影响主浏览状态。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AttachmentPickDialog(
    rootUri: String,
    hideDotFiles: Boolean,
    onDismiss: () -> Unit,
    onPicked: (DocumentFileModel) -> Unit
) {
    val context = LocalContext.current
    val backStack = remember { SnapshotStateList<String>() }
    var currentUri by remember(rootUri) { mutableStateOf(normalizeContentUriString(rootUri)) }
    var items by remember(currentUri) { mutableStateOf<List<DocumentFileModel>>(emptyList()) }
    var loading by remember(currentUri) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var localRefresh by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val canGoBack = backStack.isNotEmpty()

    BackHandler {
        if (canGoBack) {
            currentUri = backStack.removeAt(backStack.lastIndex)
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(currentUri, localRefresh) {
        loading = true
        error = null
        try {
            items = withContext(Dispatchers.IO) { listChildrenFast(context, currentUri) }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
            items = emptyList()
        }
        loading = false
    }

    val displayItems = remember(items, hideDotFiles) {
        if (hideDotFiles) items.filter { !it.name.startsWith(".") } else items
    }

    val directoryTitle = remember(currentUri) {
        val uri = Uri.parse(currentUri)
        val doc = DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
        doc?.name ?: context.getString(R.string.directory_root_name)
    }

    fun navigateInto(uri: String) {
        backStack.add(currentUri)
        currentUri = normalizeContentUriString(uri)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.attachment_pick_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                directoryTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (canGoBack) {
                                currentUri = backStack.removeAt(backStack.lastIndex)
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { localRefresh++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    stringResource(R.string.attachment_pick_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Box(Modifier.fillMaxSize()) {
                    when {
                        loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        error != null -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(error!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        displayItems.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("目录为空", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(displayItems, key = { it.uri.toString() }) { item ->
                                    FileItem(
                                        model = item,
                                        isInPendingList = false,
                                        onClick = {
                                            if (item.isDirectory) {
                                                navigateInto(item.uri.toString())
                                            }
                                        },
                                        onLongClick = {
                                            if (item.isDirectory) {
                                                onPicked(item)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.attachment_pick_file_hint),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        onDoubleClick = {
                                            if (item.isDirectory) {
                                                onPicked(item)
                                            } else {
                                                onPicked(item)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
