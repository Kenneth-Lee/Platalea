package com.kenny.localmanager.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuickNoteTabController(
    val data: QuickNoteLoadedData?,
    val startWithAddDialog: Boolean,
    val password: String,
    val passwordRequired: Boolean,
    val inProgress: Boolean,
    val requestOpen: (Boolean, String?) -> Unit,
    val requestOpenWithCachedPassword: (Boolean) -> Unit,
    val updateEntries: (List<QuickNoteEntry>) -> Unit,
    val updatePassword: (String) -> Unit,
    val dismissPasswordPrompt: () -> Unit,
    val persistIfNeeded: (String, ((Boolean) -> Unit)?) -> Unit
)

@Composable
fun rememberQuickNoteTabController(
    rootUri: String?,
    isActive: Boolean,
    markdownViewerSessionCache: MarkdownViewerSessionCache
): QuickNoteTabController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var quickNoteData by remember { mutableStateOf<QuickNoteLoadedData?>(null) }
    var quickNoteEntriesSnapshot by remember { mutableStateOf<List<QuickNoteEntry>>(emptyList()) }
    var quickNoteLastSavedHash by remember { mutableStateOf<Int?>(null) }
    var quickNoteStartWithAddDialog by remember { mutableStateOf(false) }
    var quickNotePassword by remember { mutableStateOf("") }
    var quickNotePasswordRequired by remember { mutableStateOf(false) }
    var quickNoteInProgress by remember { mutableStateOf(false) }

    fun resetQuickNotePromptState() {
        quickNotePassword = ""
        quickNotePasswordRequired = false
        quickNoteInProgress = false
    }

    fun persistQuickNoteIfNeeded(reason: String, onFinished: ((Boolean) -> Unit)? = null) {
        val currentData = quickNoteData ?: run {
            onFinished?.invoke(true)
            return
        }
        if (quickNoteInProgress) {
            onFinished?.invoke(false)
            return
        }
        val snapshot = quickNoteEntriesSnapshot
        val snapshotHash = snapshot.hashCode()
        if (quickNoteLastSavedHash == snapshotHash) {
            onFinished?.invoke(true)
            return
        }
        quickNoteInProgress = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                saveQuickNoteData(context, currentData, snapshot)
            }
            quickNoteInProgress = false
            result.onSuccess { saved ->
                markdownViewerSessionCache.invalidateByUri(saved.fileInfo.uri.toString())
                quickNoteData = saved
                quickNoteEntriesSnapshot = saved.entries
                quickNoteLastSavedHash = saved.entries.hashCode()
                onFinished?.invoke(true)
            }.onFailure { throwable ->
                Toast.makeText(context, "快速笔记自动保存失败（$reason）：${throwable.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                onFinished?.invoke(false)
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
                    quickNoteEntriesSnapshot = result.data.entries
                    quickNoteLastSavedHash = result.data.entries.hashCode()
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

    LaunchedEffect(isActive, rootUri, quickNoteData, quickNoteInProgress) {
        if (isActive && rootUri != null && quickNoteData == null && !quickNoteInProgress) {
            requestOpenQuickNote(false, SecretKeyPasswordCache.get()?.let { String(it) })
        }
    }

    LaunchedEffect(isActive, quickNoteData, quickNoteStartWithAddDialog) {
        if (isActive && quickNoteData != null && quickNoteStartWithAddDialog) {
            quickNoteStartWithAddDialog = false
        }
    }

    var previousIsActive by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (previousIsActive && !isActive) {
            persistQuickNoteIfNeeded("切换标签")
        }
        previousIsActive = isActive
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isActive) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && isActive) {
                persistQuickNoteIfNeeded("进入后台")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return QuickNoteTabController(
        data = quickNoteData,
        startWithAddDialog = quickNoteStartWithAddDialog,
        password = quickNotePassword,
        passwordRequired = quickNotePasswordRequired,
        inProgress = quickNoteInProgress,
        requestOpen = ::requestOpenQuickNote,
        requestOpenWithCachedPassword = { startWithAddDialog ->
            requestOpenQuickNote(startWithAddDialog, SecretKeyPasswordCache.get()?.let { String(it) })
        },
        updateEntries = { quickNoteEntriesSnapshot = it },
        updatePassword = { if (!quickNoteInProgress) quickNotePassword = it },
        dismissPasswordPrompt = {
            if (!quickNoteInProgress) {
                resetQuickNotePromptState()
                quickNoteStartWithAddDialog = false
            }
        },
        persistIfNeeded = ::persistQuickNoteIfNeeded
    )
}

@Composable
fun QuickNoteTabRoute(
    prefs: Preferences,
    controller: QuickNoteTabController
) {
    val data = controller.data
    if (data != null) {
        QuickNoteScreen(
            prefs = prefs,
            loadedData = data,
            startWithAddDialog = controller.startWithAddDialog,
            inProgress = controller.inProgress,
            onEntriesChanged = controller.updateEntries
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (controller.inProgress) {
                CircularProgressIndicator()
            } else {
                Text("正在打开快速笔记…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}