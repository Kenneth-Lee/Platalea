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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kenny.localmanager.R
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.gpg.SecretKeyPasswordCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuickNoteTabController(
    override val data: QuickNoteLoadedData?,
    val startWithAddDialog: Boolean,
    override val inProgress: Boolean,
    override val passwordPromptState: TabPasswordPromptState,
    val requestOpen: (Boolean, String?) -> Unit,
    val requestOpenWithCachedPassword: (Boolean) -> Unit,
    val updateEntries: (List<QuickNoteEntry>) -> Unit,
    private val persistHandler: (String, ((Boolean) -> Unit)?) -> Unit
) : BaseTabRouteController<QuickNoteLoadedData> {
    override fun persistIfNeeded(reason: String, onFinished: ((Boolean) -> Unit)?) {
        persistHandler(reason, onFinished)
    }
}

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
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.quick_note_auto_save_failed,
                        reason,
                        throwable.message ?: context.getString(R.string.common_unknown_error)
                    ),
                    Toast.LENGTH_LONG
                ).show()
                onFinished?.invoke(false)
            }
        }
    }

    fun requestOpenQuickNote(startWithAddDialog: Boolean, password: String? = null) {
        val root = rootUri?.let { normalizeContentUriString(it) }
        if (root == null) {
            Toast.makeText(context, context.getString(R.string.common_select_root_first), Toast.LENGTH_SHORT).show()
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

    val persistReasonTabSwitch = context.getString(R.string.quick_note_persist_reason_tab_switch)
    val persistReasonBackground = context.getString(R.string.quick_note_persist_reason_background)

    var previousIsActive by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (previousIsActive && !isActive) {
            persistQuickNoteIfNeeded(persistReasonTabSwitch)
        }
        previousIsActive = isActive
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isActive, persistReasonBackground) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && isActive) {
                persistQuickNoteIfNeeded(persistReasonBackground)
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
        inProgress = quickNoteInProgress,
        passwordPromptState = TabPasswordPromptState(
            required = quickNotePasswordRequired,
            password = quickNotePassword,
            inProgress = quickNoteInProgress,
            onPasswordChange = { if (!quickNoteInProgress) quickNotePassword = it },
            onConfirm = { pwd ->
                if (!quickNoteInProgress) {
                    requestOpenQuickNote(quickNoteStartWithAddDialog, pwd)
                }
            },
            onDismiss = {
                if (!quickNoteInProgress) {
                    resetQuickNotePromptState()
                    quickNoteStartWithAddDialog = false
                }
            }
        ),
        requestOpen = ::requestOpenQuickNote,
        requestOpenWithCachedPassword = { startWithAddDialog ->
            requestOpenQuickNote(startWithAddDialog, SecretKeyPasswordCache.get()?.let { String(it) })
        },
        updateEntries = { quickNoteEntriesSnapshot = it },
        persistHandler = ::persistQuickNoteIfNeeded
    )
}

@Composable
fun QuickNoteTabRoute(
    prefs: Preferences,
    controller: QuickNoteTabController
) {
    TabRouteContent(
        controller = controller,
        loadingText = stringResource(R.string.quick_note_opening)
    ) { data ->
        QuickNoteScreen(
            prefs = prefs,
            loadedData = data,
            startWithAddDialog = controller.startWithAddDialog,
            inProgress = controller.inProgress,
            onEntriesChanged = controller.updateEntries
        )
    }
}