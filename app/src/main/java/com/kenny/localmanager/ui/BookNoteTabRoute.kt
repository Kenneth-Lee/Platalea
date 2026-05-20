package com.kenny.localmanager.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BookNoteTabController(
    override val data: BookNoteLoadedData?,
    val entriesSnapshot: List<BookNoteEntry>,
    override val inProgress: Boolean,
    override val passwordPromptState: TabPasswordPromptState,
    val requestOpen: (String?) -> Unit,
    val requestOpenWithCachedPassword: () -> Unit,
    val requestLoad: () -> Unit,
    val updateEntries: (List<BookNoteEntry>) -> Unit,
    private val persistHandler: (String, ((Boolean) -> Unit)?) -> Unit
) : BaseTabRouteController<BookNoteLoadedData> {
    override fun persistIfNeeded(reason: String, onFinished: ((Boolean) -> Unit)?) {
        persistHandler(reason, onFinished)
    }
}

@Composable
fun rememberBookNoteTabController(
    rootUri: String?,
    isActive: Boolean,
    markdownViewerSessionCache: MarkdownViewerSessionCache
): BookNoteTabController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bookNoteData by remember { mutableStateOf<BookNoteLoadedData?>(null) }
    var bookNoteEntriesSnapshot by remember { mutableStateOf<List<BookNoteEntry>>(emptyList()) }
    var bookNoteLastSavedHash by remember { mutableStateOf<Int?>(null) }
    var bookNotePassword by remember { mutableStateOf("") }
    var bookNotePasswordRequired by remember { mutableStateOf(false) }
    var bookNoteInProgress by remember { mutableStateOf(false) }
    var bookNoteLoadRequested by remember { mutableStateOf(false) }

    fun resetBookNotePromptState() {
        bookNotePassword = ""
        bookNotePasswordRequired = false
        bookNoteInProgress = false
    }

    fun persistBookNoteIfNeeded(reason: String, onFinished: ((Boolean) -> Unit)? = null) {
        val currentData = bookNoteData ?: run {
            onFinished?.invoke(true)
            return
        }
        if (bookNoteInProgress) {
            onFinished?.invoke(false)
            return
        }
        val snapshot = bookNoteEntriesSnapshot
        val snapshotHash = snapshot.hashCode()
        if (bookNoteLastSavedHash == snapshotHash) {
            onFinished?.invoke(true)
            return
        }
        bookNoteInProgress = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                saveBookNoteData(context, currentData, snapshot)
            }
            bookNoteInProgress = false
            result.onSuccess { saved ->
                markdownViewerSessionCache.invalidateByUri(saved.fileInfo.uri.toString())
                bookNoteData = saved
                bookNoteEntriesSnapshot = saved.entries
                bookNoteLastSavedHash = saved.entries.hashCode()
                onFinished?.invoke(true)
            }.onFailure { throwable ->
                Toast.makeText(context, "读书笔记自动保存失败（$reason）：${throwable.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                onFinished?.invoke(false)
            }
        }
    }

    fun requestOpenBookNote(password: String? = null) {
        val root = rootUri?.let { normalizeContentUriString(it) }
        if (root == null) {
            Toast.makeText(context, "请先选择根目录", Toast.LENGTH_SHORT).show()
            return
        }
        bookNoteLoadRequested = true
        bookNoteInProgress = true
        scope.launch {
            when (val result = withContext(Dispatchers.IO) {
                openBookNoteData(context, root, password)
            }) {
                is BookNoteOpenResult.Success -> {
                    bookNoteData = result.data
                    bookNoteEntriesSnapshot = result.data.entries
                    bookNoteLastSavedHash = result.data.entries.hashCode()
                    bookNotePasswordRequired = false
                    bookNotePassword = ""
                    bookNoteLoadRequested = false
                }
                BookNoteOpenResult.RequiresPassword -> {
                    bookNotePasswordRequired = true
                }
                is BookNoteOpenResult.Error -> {
                    bookNoteLoadRequested = false
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
            }
            bookNoteInProgress = false
        }
    }

    LaunchedEffect(isActive, bookNoteData) {
        if (isActive && bookNoteData == null) {
            bookNoteLoadRequested = true
        }
    }

    LaunchedEffect(rootUri, bookNoteData, bookNoteInProgress, bookNoteLoadRequested) {
        if (bookNoteLoadRequested && rootUri != null && bookNoteData == null && !bookNoteInProgress) {
            requestOpenBookNote(SecretKeyPasswordCache.get()?.let { String(it) })
        }
    }

    LaunchedEffect(bookNoteData, bookNoteEntriesSnapshot, bookNoteInProgress) {
        if (bookNoteData == null || bookNoteInProgress) return@LaunchedEffect
        val snapshotHash = bookNoteEntriesSnapshot.hashCode()
        if (bookNoteLastSavedHash != snapshotHash) {
            persistBookNoteIfNeeded("更新记录")
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && bookNoteData != null) {
                persistBookNoteIfNeeded("进入后台")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return BookNoteTabController(
        data = bookNoteData,
        entriesSnapshot = bookNoteEntriesSnapshot,
        inProgress = bookNoteInProgress,
        passwordPromptState = TabPasswordPromptState(
            required = bookNotePasswordRequired,
            password = bookNotePassword,
            inProgress = bookNoteInProgress,
            onPasswordChange = { if (!bookNoteInProgress) bookNotePassword = it },
            onConfirm = { pwd ->
                if (!bookNoteInProgress) {
                    requestOpenBookNote(pwd)
                }
            },
            onDismiss = {
                if (!bookNoteInProgress) {
                    resetBookNotePromptState()
                    bookNoteLoadRequested = false
                }
            }
        ),
        requestOpen = ::requestOpenBookNote,
        requestOpenWithCachedPassword = {
            requestOpenBookNote(SecretKeyPasswordCache.get()?.let { String(it) })
        },
        requestLoad = { bookNoteLoadRequested = true },
        updateEntries = {
            bookNoteEntriesSnapshot = it
            bookNoteLoadRequested = true
        },
        persistHandler = ::persistBookNoteIfNeeded
    )
}

@Composable
fun BookNoteTabRoute(controller: BookNoteTabController) {
    TabRouteContent(
        controller = controller,
        loadingText = "正在打开读书笔记…"
    ) { data ->
        BookNoteScreen(
            loadedData = data,
            inProgress = controller.inProgress,
            onEntriesChanged = controller.updateEntries
        )
    }
}