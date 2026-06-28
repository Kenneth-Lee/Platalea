package com.kenny.localmanager.ui

import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
 * 从已授权根目录浏览并选择文件/目录。
 *
 * 过滤与是否可选目录由 [policy] 决定；标题与提示文案由调用方传入，本组件不含业务用例硬编码。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DirectoryPickDialog(
    rootUri: String,
    hideDotFiles: Boolean,
    policy: DirectoryPickPolicy,
    titleRes: Int,
    onDismiss: () -> Unit,
    onPicked: (DocumentFileModel) -> Unit,
    hintRes: Int = R.string.directory_pick_hint_filtered,
    invalidPickHintRes: Int = R.string.directory_pick_item_not_allowed,
    noMatchingFilesHintRes: Int = R.string.directory_pick_no_matching_files,
) {
    val context = LocalContext.current
    val backStack = remember { SnapshotStateList<String>() }
    val normalizedRootUri = remember(rootUri) { normalizeContentUriString(rootUri) }
    var currentUri by remember(rootUri) { mutableStateOf(normalizedRootUri) }
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
            items = withContext(Dispatchers.IO) {
                listChildrenFast(context, currentUri)
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
            items = emptyList()
        }
        loading = false
    }

    val displayItems = remember(items, hideDotFiles, policy) {
        items.filter { item ->
            if (hideDotFiles && item.name.startsWith(".")) return@filter false
            if (item.isDirectory) return@filter true
            item.matchesDirectoryPickPolicy(policy)
        }
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

    fun confirmPick(item: DocumentFileModel) {
        if (item.isDirectory) {
            if (!policy.allowDirectories) {
                Toast.makeText(
                    context,
                    context.getString(R.string.directory_pick_directory_not_allowed),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            onPicked(item)
            return
        }
        if (!policy.allowFiles) {
            Toast.makeText(context, context.getString(invalidPickHintRes), Toast.LENGTH_SHORT).show()
            return
        }
        if (item.matchesDirectoryPickPolicy(policy)) {
            onPicked(item)
        } else {
            Toast.makeText(context, context.getString(invalidPickHintRes), Toast.LENGTH_SHORT).show()
        }
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
                                stringResource(titleRes),
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
                    stringResource(hintRes),
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
                                Text(
                                    stringResource(
                                        if (items.isNotEmpty()) noMatchingFilesHintRes
                                        else R.string.directory_pick_empty
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
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
                                        onLongClick = { confirmPick(item) },
                                        onDoubleClick = { confirmPick(item) }
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

/** 留言板附件选择（文件或目录）。 */
@Composable
fun AttachmentPickDialog(
    rootUri: String,
    hideDotFiles: Boolean,
    onDismiss: () -> Unit,
    onPicked: (DocumentFileModel) -> Unit
) {
    DirectoryPickDialog(
        rootUri = rootUri,
        hideDotFiles = hideDotFiles,
        policy = DirectoryPickPurpose.BULLETIN_ATTACHMENT.defaultPolicy(),
        titleRes = R.string.directory_pick_purpose_bulletin_attachment,
        hintRes = R.string.attachment_pick_hint,
        invalidPickHintRes = R.string.attachment_pick_file_hint,
        onDismiss = onDismiss,
        onPicked = onPicked
    )
}
