package com.kenny.localmanager.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.graphics.pdf.PdfRenderer
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kenny.localmanager.R
import com.kenny.localmanager.data.AudioMetaAnnotation
import com.kenny.localmanager.data.DirectoryScrollMemory
import com.kenny.localmanager.dict.isStarDictImportCandidate
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.EpubParseResult
import com.kenny.localmanager.file.RecursiveFileSearchCriteria
import com.kenny.localmanager.file.RecursiveFileSearchHit
import com.kenny.localmanager.file.ZipFirstLevelResult
import com.kenny.localmanager.file.buildExternalOpenIntent
import com.kenny.localmanager.file.extractEpubToCache
import com.kenny.localmanager.file.fileExtensionKey
import com.kenny.localmanager.file.getArchiveFirstLevelEntries
import com.kenny.localmanager.file.getEpubCacheDir
import com.kenny.localmanager.file.guessExternalOpenMimeType
import com.kenny.localmanager.file.isExtractableArchive
import com.kenny.localmanager.file.isGalleryImageFile
import com.kenny.localmanager.file.isGpgEncryptedFile
import com.kenny.localmanager.file.isMarkdownFile
import com.kenny.localmanager.file.isPassFile
import com.kenny.localmanager.file.isPdfFile
import com.kenny.localmanager.file.isPicZip
import com.kenny.localmanager.file.isVidZip
import com.kenny.localmanager.file.isHtmlFile
import com.kenny.localmanager.file.listChildrenFast
import com.kenny.localmanager.file.loadEpubFromCache
import com.kenny.localmanager.file.renameDocument
import com.kenny.localmanager.file.searchFilesRecursively
import com.kenny.localmanager.file.parseSizeRange
import com.kenny.localmanager.file.parseTimeRange
import com.kenny.localmanager.file.toExternalLaunchUri
import com.kenny.localmanager.player.PlaybackState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kenny.localmanager.config.BrowserConfigView
import com.kenny.localmanager.player.DetailMetadataKind
import com.kenny.localmanager.player.EditableAudioAnnotationValidation
import com.kenny.localmanager.player.formatPlaybackTime
import com.kenny.localmanager.player.inferDetailMetadataKind
import com.kenny.localmanager.player.isMp3AudioFile
import com.kenny.localmanager.player.readEditableAudioAnnotation
import com.kenny.localmanager.player.saveEditableAudioAnnotationToMp3
import com.kenny.localmanager.player.saveEditableAudioAnnotationWithJaudiotagger
import com.kenny.localmanager.player.supportsJaudiotaggerWrite
import com.kenny.localmanager.player.validateEditableAudioAnnotationForm
import com.kenny.localmanager.ui.ReliablePasswordInputField
import com.kenny.localmanager.ui.normalizeContentUriString
import com.kenny.localmanager.file.isEpubFile
import com.kenny.localmanager.file.isLlmFile
import com.kenny.localmanager.file.isTxtFile
import com.kenny.localmanager.ui.PlataleaApp

@Composable
private fun fileItemSubtitle(model: DocumentFileModel): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(model.lastModified))
    return if (model.displaySize.isNotEmpty()) "${model.displaySize}  $dateStr" else dateStr
}


private fun getCompressedPayloadExtension(name: String): String? {
    if (!name.endsWith(".zip", ignoreCase = true)) return null
    val withoutZip = name.dropLast(4)
    val dotIndex = withoutZip.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex >= withoutZip.length - 1) return null
    return withoutZip.substring(dotIndex + 1)
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
internal fun BrowserScreen(
    modifier: Modifier = Modifier,
    config: BrowserConfigView,
    session: BrowserSessionHandle,
    dialogSession: DialogSession? = null,
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
    isViewingTrash: Boolean = false,
    filterVisible: Boolean = true,
    hideDotFiles: Boolean = false,
    preferredExternalPackages: Map<String, String> = emptyMap(),
    onRefresh: () -> Unit,
    onChangeRoot: () -> Unit = {},
    zipEditTargetUri: String? = null,
    zipEditTargetName: String? = null,
    zipEditTargetIsZip: Boolean = false,
    playbackState: PlaybackState? = null,
    onOpenPlaybackScreen: () -> Unit = {}
) {
    val directoryScrollMinItems = 30
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember(currentUri) { mutableStateOf<List<DocumentFileModel>>(emptyList()) }
    var loading by remember(currentUri) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuTarget by remember { mutableStateOf<DocumentFileModel?>(null) }
    var filterText by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(FileSortOrder.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    // 查找会话（第四批）：输入与结果共享状态，随会话生命周期。
    val searchState = rememberSearchSessionState()

    val currentDirectoryName = remember(currentUri) {
        val uri = Uri.parse(currentUri)
        val doc = DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
        doc?.name ?: context.getString(R.string.directory_root_name)
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
                } catch (e: CancellationException) {
                    // 效果重启/离开组合的取消不是加载失败（写状态会与新效果竞态残留）
                    throw e
                } catch (e: Exception) {
                    error = e.message ?: context.getString(R.string.browser_loading_failed)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: context.getString(R.string.browser_loading_failed)
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
    val galleryImageItems = remember(sortedItems, hideDotFiles) {
        sortedItems.filter { item ->
            !item.isDirectory &&
                isGalleryImageFile(item.name) &&
                (!hideDotFiles || !item.name.startsWith("."))
        }
    }

    /** 动作快照（拆解设计第三批）：菜单与单击分发共用的会话判断依据。 */
    fun actionSnapshot(target: DocumentFileModel) = BrowserActionSnapshot(
        currentUri = currentUri,
        pendingUris = pendingList.map { it.uri.toString() }.toSet(),
        zipEditTargetUri = zipEditTargetUri,
        zipEditTargetIsZip = zipEditTargetIsZip,
        isViewingTrash = isViewingTrash,
        gallerySiblings = if (isGalleryImageFile(target.name)) galleryImageItems else emptyList(),
        externalOpenPreferences = preferredExternalPackages,
    )

    fun actionContext(target: DocumentFileModel) = BrowserActionContext(
        target = target,
        session = session,
        snapshot = actionSnapshot(target),
        scope = scope,
        appContext = context,
    )

    fun defaultOpenMenuLabel(target: DocumentFileModel): String {
        if (target.isDirectory) return context.getString(R.string.context_default_enter_directory)
        // 已迁移打开意图（拆解设计 第四批全部动作化）：标题取槽位最小
        // 适用意图的声明；槽 900 兜底命中且外开偏好存在时显示偏好应用。
        BrowserActions.registry.matchIntent(target)?.let { intent ->
            val preferredLabel = BrowserIntents.fallbackPreferredLabelRes(target, actionSnapshot(target))
            return context.getString(preferredLabel ?: intent.labelRes)
        }
        return context.getString(R.string.context_open_with_internal_viewer)
    }

    fun openItemByDefault(target: DocumentFileModel) {
        if (target.isDirectory) {
            onNavigate(target.uri.toString())
            return
        }
        // 已迁移打开意图（拆解设计 第四批全部动作化）：单击分发先查
        // 声明注册表，槽 900 兜底意图覆盖外开偏好与内置查看器。
        BrowserActions.registry.matchIntent(target)?.let {
            it.open(actionContext(target))
        }
    }

    fun buildDirectoryHeadFingerprint(list: List<DocumentFileModel>): String {
        if (list.isEmpty()) return "0"
        val head = list.asSequence()
            .take(8)
            .joinToString("|") { "${it.name}#${it.lastModified}" }
        return "${list.size}:$head"
    }

    suspend fun persistDirectoryScrollMemory(uriToSave: String, itemsToSave: List<DocumentFileModel>, wasLoading: Boolean) {
        if (wasLoading) return
        if (itemsToSave.size < directoryScrollMinItems) {
            config.removeDirectoryScrollMemory(uriToSave)
            return
        }
        val maxIndex = (itemsToSave.size - 1).coerceAtLeast(0)
        val index = listState.firstVisibleItemIndex.coerceIn(0, maxIndex)
        val anchorItem = itemsToSave.getOrNull(index)
        val now = System.currentTimeMillis()
        config.setDirectoryScrollMemory(
            DirectoryScrollMemory(
                dirUri = uriToSave,
                anchorUri = anchorItem?.uri?.toString(),
                anchorName = anchorItem?.name.orEmpty(),
                index = index,
                offset = listState.firstVisibleItemScrollOffset.coerceAtLeast(0),
                itemCount = itemsToSave.size,
                headFingerprint = buildDirectoryHeadFingerprint(itemsToSave),
                savedAt = now,
                lastAccessAt = now
            )
        )
    }

    var restoredForUri by remember(currentUri) { mutableStateOf(false) }
    LaunchedEffect(currentUri, loading, displayItems) {
        if (restoredForUri || loading) return@LaunchedEffect
        restoredForUri = true
        if (displayItems.size < directoryScrollMinItems) return@LaunchedEffect
        val memory = withContext(Dispatchers.IO) { config.getDirectoryScrollMemory(currentUri) } ?: return@LaunchedEffect
        val fallbackMaxIndex = (displayItems.size - 1).coerceAtLeast(0)
        val anchorIndex = memory.anchorUri?.let { uri ->
            displayItems.indexOfFirst { it.uri.toString() == uri }
        } ?: -1
        val nameAnchorIndex = if (anchorIndex >= 0 || memory.anchorName.isBlank()) {
            -1
        } else {
            displayItems.indexOfFirst { it.name == memory.anchorName }
        }
        val currentFingerprint = buildDirectoryHeadFingerprint(displayItems)
        val targetIndex = when {
            anchorIndex >= 0 -> anchorIndex
            nameAnchorIndex >= 0 -> nameAnchorIndex
            memory.headFingerprint == currentFingerprint -> memory.index.coerceIn(0, fallbackMaxIndex)
            else -> -1
        }
        if (targetIndex < 0) return@LaunchedEffect
        val targetOffset = memory.offset.coerceAtLeast(0)
        runCatching {
            listState.scrollToItem(targetIndex, targetOffset)
        }
    }

    val latestCurrentUriState = androidx.compose.runtime.rememberUpdatedState(currentUri)
    val latestDisplayItemsState = androidx.compose.runtime.rememberUpdatedState(displayItems)
    val latestLoadingState = androidx.compose.runtime.rememberUpdatedState(loading)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentUri) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val uriToSave = latestCurrentUriState.value
                val itemsToSave = latestDisplayItemsState.value
                val wasLoading = latestLoadingState.value
                scope.launch(Dispatchers.IO) {
                    persistDirectoryScrollMemory(uriToSave, itemsToSave, wasLoading)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val uriToSave = latestCurrentUriState.value
            val itemsToSave = latestDisplayItemsState.value
            val wasLoading = latestLoadingState.value
            // [死锁修复] 原实现 runBlocking{ withContext(IO){ DataStore.edit } } 在主线程
            // dispose 路径上同步等待 DataStore，与 Main 队列上的挂起协程互等（DataStore 官方
            // 明确禁止主线程 runBlocking）。改用 NonCancellable 的独立 IO 协程 fire-and-forget：
            // 不依赖已随组合取消的 scope，也不阻塞主线程；仅丢「dispose 后进程立即死亡」的
            // 极端窗口（滚动位置记忆，非关键数据，ON_STOP 兜底同路径）。
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                persistDirectoryScrollMemory(uriToSave, itemsToSave, wasLoading)
            }
        }
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
                            doc?.name ?: context.getString(R.string.directory_root_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (canGoBack) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.common_back))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = context.getString(R.string.common_refresh))
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.browser_sort_desc))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                FileSortOrder.entries.forEach { order ->
                                    val isCurrent = sortOrder == order
                                    val direction = if (isCurrent) (if (sortAscending) " ↑" else " ↓") else ""
                                    DropdownMenuItem(
                                        text = { Text(stringResource(order.labelRes) + direction) },
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
                            Icon(Icons.Default.MoreVert, contentDescription = context.getString(R.string.main_menu))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.config_change_root)) },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onChangeRoot()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.main_menu_open_url)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    session.openDialog(DialogSession.OpenUrl)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_generate_dir_playlist)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    session.createMusicPlaylist(currentUri, currentDirectoryName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_file_search_title)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    searchState.reset()
                                    session.openDialog(DialogSession.Search)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.main_menu_add_filtered_to_pending)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val toAdd = filteredItems.filter { item -> !pendingList.any { it.uri == item.uri } }
                                    toAdd.forEach { session.addToPending(it) }
                                    Toast.makeText(context, context.getString(R.string.main_menu_add_filtered_done, toAdd.size), Toast.LENGTH_SHORT).show()
                                }
                            )
                            if (rootUri != null) {
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.main_menu_empty_trash)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        session.emptyTrash()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.debug_trigger_crash)) },
                                leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    throw IllegalStateException("debug crash trigger from PlataleaApp")
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
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalCount = items.size
                    val visibleCount = displayItems.size
                    val countLabel = if (visibleCount != totalCount) {
                        context.getString(R.string.browser_directory_item_count_filtered, visibleCount, totalCount)
                    } else {
                        context.getString(R.string.browser_directory_item_count, totalCount)
                    }
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                            placeholder = { Text(context.getString(R.string.directory_filter_placeholder)) },
                            label = null
                        )
                        if (filterText.isNotEmpty()) {
                            IconButton(onClick = { filterText = "" }) {
                                Icon(Icons.Default.RemoveCircle, contentDescription = context.getString(R.string.directory_filter_clear), Modifier.size(20.dp))
                            }
                        }
                    }
                }
                playbackState?.let { state ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { session.openPlaybackScreen() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            context.getString(R.string.directory_now_playing, state.trackName, state.trackIndex + 1, state.totalTracks),
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
                            onClick = { session.openDialog(DialogSession.QuickCopy) },
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = context.getString(R.string.fab_copy_to_current))
                        }
                        FloatingActionButton(
                            onClick = { session.openDialog(DialogSession.QuickMove) },
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = context.getString(R.string.fab_move_to_current))
                        }
                        FloatingActionButton(
                            onClick = { session.showPendingList(true) },
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f),
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = context.getString(R.string.fab_pending_list))
                        }
                    }
                    FloatingActionButton(
                        onClick = { showFabMenu = true },
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = context.getString(R.string.fab_new))
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
                        if (zipEditTargetName != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = { session.openZipManagerFromConfig() },
                                    label = { Text(stringResource(R.string.zip_edit_bar_label, zipEditTargetName), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) },
                                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, Modifier.size(18.dp)) }
                                )
                                TextButton(onClick = { session.clearZipEditTarget() }) {
                                    Text(stringResource(R.string.zip_edit_bar_clear))
                                }
                            }
                        }
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
                                onClick = { openItemByDefault(item) },
                                onLongClick = {
                                    contextMenuTarget = item
                                    showContextMenu = true
                                },
                                onDoubleClick = {
                                    if (pendingList.any { it.uri == item.uri }) session.removeFromPending(item)
                                    else session.addToPending(item)
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
        val openMenuLabel = defaultOpenMenuLabel(menuTarget)
        val menuMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Dialog(onDismissRequest = { showContextMenu = false; contextMenuTarget = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = menuMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        menuTarget.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            openItemByDefault(menuTarget)
                            contextMenuTarget = null
                        }
                    ) {
                        Text(
                            openMenuLabel,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                        // 已迁移菜单动作（拆解设计第三批）：注册序即菜单序，
                        // 适用项依次出列，执行体直呼各域服务或会话句柄。
                        val actionCtx = actionContext(menuTarget)
                        BrowserActions.registry.menuActions(menuTarget, actionCtx.snapshot).forEach { action ->
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    action.run(actionContext(menuTarget))
                                    contextMenuTarget = null
                                }
                            ) {
                                Text(
                                    stringResource(action.labelRes(menuTarget, actionCtx.snapshot)),
                                    color = if (action.emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    TextButton(onClick = { showContextMenu = false; contextMenuTarget = null }) {
                        Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurface)
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
                    Text(context.getString(R.string.new_menu_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            showFabMenu = false
                            session.openDialog(DialogSession.CreateFile)
                        }
                    ) { Text(context.getString(R.string.new_menu_file), color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showFabMenu = false
                            session.openDialog(DialogSession.CreateDir)
                        }
                    ) { Text(context.getString(R.string.new_menu_folder), color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(
                        onClick = {
                            showFabMenu = false
                            session.createQuickNote()
                        }
                    ) { Text(context.getString(R.string.new_menu_quick_note), color = MaterialTheme.colorScheme.onSurface) }
                    TextButton(onClick = { showFabMenu = false }) { Text(context.getString(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }

    dialogSession?.let { currentDialog ->
        when (currentDialog) {
            is DialogSession.Delete -> DeleteConfirmDialog(
                target = currentDialog.target,
                hasRoot = rootUri != null,
                onConfirm = { permanently ->
                    session.closeDialog()
                    session.confirmDelete(currentDialog.target, permanently)
                },
                onDismiss = { session.closeDialog() }
            )
            is DialogSession.Detail -> FileDetailDialog(
                target = currentDialog.target,
                config = config,
                onRenamed = { oldTarget, renamed ->
                    items = items.map { if (it.uri == oldTarget.uri) renamed else it }
                },
                onDismiss = { session.closeDialog() }
            )
            is DialogSession.OpenUrl -> OpenUrlDialog(
                onConfirm = { url ->
                    session.closeDialog()
                    session.openUrl(url)
                },
                onDismiss = { session.closeDialog() }
            )
            is DialogSession.Search -> if (searchState.resultsVisible) {
                FileSearchResultsDialog(
                    results = searchState.results,
                    pendingUris = pendingList.map { it.uri.toString() }.toSet(),
                    onAddAll = {
                        val existingUris = pendingList.map { it.uri.toString() }.toHashSet()
                        val toAdd = searchState.results.map { it.model }.filter { existingUris.add(it.uri.toString()) }
                        toAdd.forEach { session.addToPending(it) }
                        Toast.makeText(context, if (toAdd.isEmpty()) context.getString(R.string.browser_no_new_pending_items) else context.getString(R.string.main_menu_add_filtered_done, toAdd.size), Toast.LENGTH_SHORT).show()
                    },
                    onRefine = { searchState.resultsVisible = false }
                )
            } else {
                FileSearchDialog(
                    namePattern = searchState.namePattern,
                    contentPattern = searchState.contentPattern,
                    sizeRange = searchState.sizeRange,
                    modified = searchState.modified,
                    searching = searchState.running,
                    error = searchState.error,
                    onNamePatternChange = {
                        searchState.namePattern = it
                        if (searchState.error != null) searchState.error = null
                    },
                    onContentPatternChange = {
                        searchState.contentPattern = it
                        if (searchState.error != null) searchState.error = null
                    },
                    onSizeRangeChange = {
                        searchState.sizeRange = it
                        if (searchState.error != null) searchState.error = null
                    },
                    onModifiedChange = {
                        searchState.modified = it
                        if (searchState.error != null) searchState.error = null
                    },
                    searchProgress = searchState.progress,
                    onSearch = { runFileSearch(searchState, context, scope, currentUri) },
                    onSearchCancel = { searchState.cancelled = true },
                    onDismiss = { if (!searchState.running) session.closeDialog() }
                )
            }
                        is DialogSession.CreateFile -> CreateFileDialog(
                currentUri = currentUri,
                onCreated = {
                    session.closeDialog()
                    onRefresh()
                },
                onDismiss = { session.closeDialog() }
            )
            is DialogSession.CreateDir -> CreateDirDialog(
                currentUri = currentUri,
                onCreated = {
                    session.closeDialog()
                    onRefresh()
                },
                onDismiss = { session.closeDialog() }
            )
            is DialogSession.QuickCopy -> QuickCopyMoveConfirmDialog(
                isCopy = true,
                pendingList = pendingList,
                onConfirm = {
                    session.closeDialog()
                    session.copyPendingToDir()
                },
                onDismiss = { session.closeDialog() }
            )
            is DialogSession.QuickMove -> QuickCopyMoveConfirmDialog(
                isCopy = false,
                pendingList = pendingList,
                onConfirm = {
                    session.closeDialog()
                    session.movePendingToDir()
                },
                onDismiss = { session.closeDialog() }
            )
            is DialogSession.ExternalOpen -> ExternalOpenDialog(
                target = currentDialog.target,
                session = session,
                onDismiss = { session.closeDialog() }
            )
            else -> {}
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
    val compressedPayloadExt = getCompressedPayloadExtension(model.name)
    val showCompressedTag = !model.isDirectory && compressedPayloadExt != null
    val iconName = if (showCompressedTag) "payload.${compressedPayloadExt}" else model.name
    val icon = when {
        model.isDirectory -> Icons.Default.Folder
        iconName.endsWith(".gpg", ignoreCase = true) -> Icons.Default.Lock
        iconName.endsWith(".pass", ignoreCase = true) -> Icons.Default.Lock
        iconName.endsWith(".qx", ignoreCase = true) -> Icons.Default.LockOpen
        iconName.endsWith(".pic", ignoreCase = true) || iconName.endsWith(".png", ignoreCase = true) || iconName.endsWith(".jpg", ignoreCase = true) || iconName.endsWith(".jpeg", ignoreCase = true) || iconName.endsWith(".gif", ignoreCase = true) || iconName.endsWith(".webp", ignoreCase = true) || iconName.endsWith(".bmp", ignoreCase = true) || iconName.endsWith(".heic", ignoreCase = true) -> Icons.Default.Image
        iconName.endsWith(".vid", ignoreCase = true) || iconName.endsWith(".mp4", ignoreCase = true) || iconName.endsWith(".mkv", ignoreCase = true) || iconName.endsWith(".avi", ignoreCase = true) || iconName.endsWith(".mov", ignoreCase = true) || iconName.endsWith(".webm", ignoreCase = true) -> Icons.Default.PlayArrow
        iconName.endsWith(".html", ignoreCase = true) || iconName.endsWith(".htm", ignoreCase = true) -> Icons.AutoMirrored.Filled.Article
        isEpubFile(iconName) || isTxtFile(iconName) || isLlmFile(iconName) -> Icons.AutoMirrored.Filled.MenuBook
        isPdfFile(iconName) -> Icons.Default.PictureAsPdf
        isExtractableArchive(iconName) -> Icons.Default.Archive
        iconName.endsWith(".md", ignoreCase = true) || iconName.endsWith(".rst", ignoreCase = true) -> Icons.Default.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    val iconTint = when {
        model.isDirectory -> MaterialTheme.colorScheme.primary
        iconName.endsWith(".gpg", ignoreCase = true) -> Color.Red
        iconName.endsWith(".pass", ignoreCase = true) -> Color.Red
        iconName.endsWith(".qx", ignoreCase = true) -> Color.Red
        iconName.endsWith(".pic", ignoreCase = true) || iconName.endsWith(".png", ignoreCase = true) || iconName.endsWith(".jpg", ignoreCase = true) || iconName.endsWith(".jpeg", ignoreCase = true) || iconName.endsWith(".gif", ignoreCase = true) || iconName.endsWith(".webp", ignoreCase = true) || iconName.endsWith(".bmp", ignoreCase = true) || iconName.endsWith(".heic", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
        iconName.endsWith(".vid", ignoreCase = true) || iconName.endsWith(".mp4", ignoreCase = true) || iconName.endsWith(".mkv", ignoreCase = true) || iconName.endsWith(".avi", ignoreCase = true) || iconName.endsWith(".mov", ignoreCase = true) || iconName.endsWith(".webm", ignoreCase = true) -> MaterialTheme.colorScheme.secondary
        isEpubFile(iconName) -> Color(0xFF8B4513)
        isPdfFile(iconName) -> Color(0xFFD32F2F)
        isExtractableArchive(iconName) -> MaterialTheme.colorScheme.tertiary
        iconName.endsWith(".md", ignoreCase = true) || iconName.endsWith(".rst", ignoreCase = true) -> Color.Blue
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
            Box(modifier = Modifier.size(32.dp)) {
                Icon(
                    icon,
                    contentDescription = null,
                    Modifier
                        .size(32.dp)
                        .align(Alignment.Center),
                    tint = iconTint
                )
                if (showCompressedTag) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                if (hasPreferredExternalApp) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                }
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
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = stringResource(R.string.browser_already_in_pending_list),
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private enum class FileSortOrder(@androidx.annotation.StringRes val labelRes: Int) {
    NAME(R.string.browser_name_field),
    TIME(R.string.browser_modified_field),
    SIZE(R.string.browser_size_field)
}



internal data class CachedDir(val items: List<DocumentFileModel>)
