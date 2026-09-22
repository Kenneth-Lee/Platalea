package com.kenny.localmanager.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.openInputStreamSafe
import com.kenny.localmanager.family.BulletinAttachmentKind
import com.kenny.localmanager.family.BulletinAttachmentDownloadConflict
import com.kenny.localmanager.family.BulletinAttachmentDownloader
import com.kenny.localmanager.family.BulletinAttachmentDownloadProgress
import com.kenny.localmanager.family.BulletinAttachmentUploadPhase
import com.kenny.localmanager.family.BulletinAttachmentUploadProgress
import com.kenny.localmanager.family.BulletinAttachmentRef
import com.kenny.localmanager.family.BulletinBoardDefaults
import com.kenny.localmanager.family.BulletinBoardInfo
import com.kenny.localmanager.family.BulletinBoardMention
import com.kenny.localmanager.family.BulletinMentionTextField
import com.kenny.localmanager.family.BulletinBoardOpenSession
import com.kenny.localmanager.family.BulletinBoardPack
import com.kenny.localmanager.family.BulletinMessage
import com.kenny.localmanager.family.FamilyDiscoveredService
import com.kenny.localmanager.family.FamilyNetworkManager
import com.kenny.localmanager.family.FamilyRemoteConfigConflictException
import com.kenny.localmanager.family.FamilyRemoteConfigSnapshot
import com.kenny.localmanager.family.FamilyNetworkState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private data class BoardListSession(
    val service: FamilyDiscoveredService,
    val accessPassword: String?,
    val canManageBoards: Boolean
)

internal data class BoardCreationValidationResult(
    val isValid: Boolean,
    val trimmedName: String,
    val error: String? = null
)

internal fun validateBoardCreationInput(name: String, existingNames: Set<String>): BoardCreationValidationResult {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) {
        return BoardCreationValidationResult(isValid = false, trimmedName = "")
    }
    if (trimmedName in existingNames) {
        return BoardCreationValidationResult(isValid = false, trimmedName = trimmedName, error = "duplicate")
    }
    return BoardCreationValidationResult(isValid = true, trimmedName = trimmedName)
}

@Stable
private class BoardListUiState {
    var showActionsMenu by mutableStateOf(false)
    var showCreateDialog by mutableStateOf(false)
    var newBoardName by mutableStateOf("")
    var actionInProgress by mutableStateOf(false)
    var showBoardpackPickDialog by mutableStateOf(false)
    var pendingImportFile by mutableStateOf<DocumentFileModel?>(null)
    var pendingImportName by mutableStateOf("")
    var importPackNameLoading by mutableStateOf(false)
    var importNameConflict by mutableStateOf(false)
    var importPackNameError by mutableStateOf<String?>(null)
    var importPackSourceName by mutableStateOf("")
    var createNameError by mutableStateOf<String?>(null)
    var createSelectedRoleIds by mutableStateOf(setOf<String>())
    var showRemoteShutdownDialog by mutableStateOf(false)
    var remoteShutdownInProgress by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FamilyNetworkScreen(
    readerConfig: ReaderConfigView,
    manager: FamilyNetworkManager,
    networkPassword: String?,
    localServiceEnabled: Boolean,
    familyNetworkUserName: String = "",
    familyNetworkAvatarId: String = "",
    familyNetworkHostName: String = "",
    timeoutMinutes: Int = 0,
    rootUri: String? = null,
    hideDotFiles: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val state by manager.state.collectAsState()
    var remainingMinutes by remember(timeoutMinutes) {
        mutableStateOf(if (timeoutMinutes > 0) timeoutMinutes else null)
    }
    val boardSession = state.openBoardSession
    var boardListSession by remember { mutableStateOf<BoardListSession?>(null) }
    val boardListService = boardListSession?.service
    val boardListUiState = remember { BoardListUiState() }
    var boardListRefreshTick by remember { mutableIntStateOf(0) }
    var pendingBoardService by remember { mutableStateOf<FamilyDiscoveredService?>(null) }
    var showBoardPasswordDialog by remember { mutableStateOf(false) }
    var boardPasswordInput by remember { mutableStateOf("") }
    var boardPasswordError by remember { mutableStateOf<String?>(null) }
    var boardPasswordInProgress by remember { mutableStateOf(false) }
    var showBoardMenu by remember { mutableStateOf(false) }
    var showDeleteBoardDialog by remember { mutableStateOf(false) }
    var deleteBoardInProgress by remember { mutableStateOf(false) }
    var showRefreshAvatarsDialog by remember { mutableStateOf(false) }
    var refreshAvatarsInProgress by remember { mutableStateOf(false) }
    var showRenameBoardDialog by remember { mutableStateOf(false) }
    var renameBoardName by remember { mutableStateOf("") }
    var renameBoardInProgress by remember { mutableStateOf(false) }
    var renameBoardError by remember { mutableStateOf<String?>(null) }
    var renameExistingNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAiManagerDialog by remember { mutableStateOf(false) }
    var aiManagerUpdating by remember { mutableStateOf(false) }
    var aiManagerError by remember { mutableStateOf<String?>(null) }
    var messageForwardSelectionMode by remember { mutableStateOf(false) }
    var selectedForwardMessageIds by remember { mutableStateOf(setOf<String>()) }
    val markdownViewerSessionCache = rememberLocalViewerSessions()
    var attachmentViewerFile by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var attachmentMarkdownFile by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var attachmentHtmlLocation by remember { mutableStateOf<HtmlViewerLocation?>(null) }
    var attachmentPdfFile by remember { mutableStateOf<Pair<String, String>?>(null) }
    var attachmentImageFile by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showExportPackConfirmDialog by remember { mutableStateOf(false) }
    var exportPackSummary by remember { mutableStateOf<BulletinBoardPack.PackSummary?>(null) }
    var exportPackProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var exportPackStage by remember { mutableStateOf<String?>(null) }
    var exportPackInProgress by remember { mutableStateOf(false) }
    var showRemoteConfigDialog by remember { mutableStateOf(false) }
    var remoteConfigSnapshot by remember { mutableStateOf<FamilyRemoteConfigSnapshot?>(null) }
    var remoteConfigLoading by remember { mutableStateOf(false) }
    var remoteConfigSaving by remember { mutableStateOf(false) }
    var remoteConfigError by remember { mutableStateOf<String?>(null) }
    var remoteConfigSelectedRoleId by remember { mutableStateOf<String?>(null) }
    var remoteConfigRoleMenuExpanded by remember { mutableStateOf(false) }
    var remoteConfigRoleLabel by remember { mutableStateOf("") }
    var remoteConfigRoleCanCreate by remember { mutableStateOf(false) }
    var remoteConfigRoleCanManage by remember { mutableStateOf(false) }
    var remoteConfigRoleAllowedModels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var remoteConfigRolePassword by remember { mutableStateOf("") }
    var remoteConfigPendingRevision by remember { mutableStateOf<Long?>(null) }
    var remoteConfigIgnorePendingUpToRevision by remember { mutableStateOf<Long?>(null) }
    var remoteConfigConflictRevision by remember { mutableStateOf<Long?>(null) }
    var remoteConfigConflictPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var remoteConfigConflictSnapshot by remember { mutableStateOf<FamilyRemoteConfigSnapshot?>(null) }
    var remoteConfigShowAddRoleDialog by remember { mutableStateOf(false) }
    var remoteConfigNewRoleId by remember { mutableStateOf("") }
    var remoteConfigShowAllowedModelsDialog by remember { mutableStateOf(false) }
    var remoteConfigAvailableAgentModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var remoteConfigAvailableAgentModelsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(boardSession?.boardId) {
        messageForwardSelectionMode = false
        selectedForwardMessageIds = emptySet()
    }

    LaunchedEffect(showRenameBoardDialog, boardSession?.boardId, boardSession?.service?.deviceKey) {
        val session = boardSession
        if (!showRenameBoardDialog || session == null) {
            renameExistingNames = emptySet()
            return@LaunchedEffect
        }
        renameBoardName = session.boardName
        renameBoardError = null
        manager.fetchBoardList(session.service, session.accessPassword)
            .onSuccess { result ->
                renameExistingNames = result.boards.map { it.name.trim() }.toSet()
            }
    }

    var showUserRolesDialog by remember { mutableStateOf(false) }
    var showBoardAccessDialog by remember { mutableStateOf(false) }
    var boardAccessRoleIds by remember { mutableStateOf(setOf<String>()) }
    var boardAccessInProgress by remember { mutableStateOf(false) }
    var boardAccessError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(networkPassword, localServiceEnabled, familyNetworkUserName, familyNetworkAvatarId, familyNetworkHostName) {
        manager.configure(
            networkPassword,
            localServiceEnabled,
            familyNetworkUserName.ifBlank { null },
            familyNetworkAvatarId.ifBlank { null },
            familyNetworkHostName.ifBlank { null }
        )
    }

    BackHandler(enabled = boardSession != null || boardListSession != null) {
        when {
            boardSession != null -> manager.closeBulletinBoard()
            boardListSession != null -> boardListSession = null
        }
    }

    LaunchedEffect(timeoutMinutes, onDismiss) {
        remainingMinutes = if (timeoutMinutes > 0) timeoutMinutes else null
        if (timeoutMinutes <= 0 || onDismiss == null) {
            return@LaunchedEffect
        }
        while (true) {
            val left = remainingMinutes ?: break
            if (left <= 0) break
            delay(60_000)
            remainingMinutes = left - 1
        }
        if (remainingMinutes == 0) {
            onDismiss()
        }
    }

    DisposableEffect(manager, activity, networkPassword, localServiceEnabled, familyNetworkUserName, familyNetworkAvatarId, familyNetworkHostName) {
        manager.configure(
            networkPassword,
            localServiceEnabled,
            familyNetworkUserName.ifBlank { null },
            familyNetworkAvatarId.ifBlank { null },
            familyNetworkHostName.ifBlank { null }
        )
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            manager.clearTemporaryAttachmentCache()
        }
    }

    var bulletinForwardPayload by remember { mutableStateOf<BulletinForwardPayload?>(null) }

    fun openBoardListPage(service: FamilyDiscoveredService, accessPassword: String?) {
        scope.launch {
            val canManage = manager.remoteCanManageBoard(service, accessPassword)
            manager.rememberBoardAccessPassword(service.deviceUuid, accessPassword)
            boardListSession = BoardListSession(service, accessPassword, canManage)
        }
    }

    fun openBoardWithAccess(service: FamilyDiscoveredService, accessPassword: String?) {
        openBoardListPage(service, accessPassword)
    }

    fun boardPasswordProbeErrorMessage(error: Throwable): String {
        val detail = error.message ?: error.javaClass.simpleName
        return if (manager.isLikelyAuthFailure(error)) {
            context.getString(R.string.family_board_password_wrong_detail, detail)
        } else {
            context.getString(
                R.string.family_board_password_probe_failed,
                detail
            )
        }
    }

    fun showPasswordDialog(
        service: FamilyDiscoveredService,
        wrongPassword: Boolean,
        initialError: Throwable? = null
    ) {
        pendingBoardService = service
        boardPasswordInput = ""
        boardPasswordError = when {
            initialError != null -> boardPasswordProbeErrorMessage(initialError)
            wrongPassword -> context.getString(R.string.family_board_password_wrong)
            else -> null
        }
        showBoardPasswordDialog = true
    }

    fun requestOpenBoard(service: FamilyDiscoveredService) {
        if (service.isSelf && !localServiceEnabled) {
            Toast.makeText(context, context.getString(R.string.family_network_local_service_disabled), Toast.LENGTH_SHORT).show()
            return
        }
        if (service.isSelf) {
            openBoardWithAccess(service, accessPassword = null)
            return
        }
        if (manager.hasRememberedBoardAccessPassword(service.deviceUuid)) {
            val cached = manager.getRememberedBoardAccessPassword(service.deviceUuid)
            scope.launch {
                boardPasswordInProgress = true
                val result = manager.probeBoardAccess(service, accessPassword = cached)
                boardPasswordInProgress = false
                result.onSuccess { openBoardWithAccess(service, cached) }
                    .onFailure { error ->
                        manager.forgetBoardAccessPassword(service.deviceUuid)
                        showPasswordDialog(
                            service,
                            wrongPassword = manager.isLikelyAuthFailure(error),
                            initialError = error
                        )
                    }
            }
            return
        }
        if (service.requiresPasswordAuth) {
            showPasswordDialog(service, wrongPassword = false)
            return
        }
        scope.launch {
            boardPasswordInProgress = true
            val probe = manager.probeBoardAccess(service, accessPassword = null)
            boardPasswordInProgress = false
            if (probe.isSuccess) {
                manager.rememberBoardAccessPassword(service.deviceUuid, null)
                openBoardWithAccess(service, null)
            } else {
                showPasswordDialog(service, wrongPassword = false)
            }
        }
    }

    if (showBoardPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!boardPasswordInProgress) {
                    showBoardPasswordDialog = false
                    pendingBoardService = null
                    boardPasswordInput = ""
                    boardPasswordError = null
                }
            },
            title = { Text(stringResource(R.string.family_board_password_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.family_board_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = boardPasswordInput,
                        onValueChange = {
                            boardPasswordInput = it
                            boardPasswordError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.family_board_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !boardPasswordInProgress
                    )
                    boardPasswordError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !boardPasswordInProgress,
                    onClick = {
                        val service = pendingBoardService ?: return@Button
                        scope.launch {
                            boardPasswordInProgress = true
                            val password = boardPasswordInput.trim().ifEmpty { null }
                            val result = manager.probeBoardAccess(service, accessPassword = password)
                            boardPasswordInProgress = false
                            if (result.isSuccess) {
                                showBoardPasswordDialog = false
                                pendingBoardService = null
                                boardPasswordInput = ""
                                boardPasswordError = null
                                manager.rememberBoardAccessPassword(service.deviceUuid, password)
                                openBoardWithAccess(service, password)
                            } else {
                                boardPasswordError = boardPasswordProbeErrorMessage(
                                    result.exceptionOrNull() ?: IllegalStateException("unknown probe failure")
                                )
                            }
                        }
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !boardPasswordInProgress,
                    onClick = {
                        showBoardPasswordDialog = false
                        pendingBoardService = null
                        boardPasswordInput = ""
                        boardPasswordError = null
                    }
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showDeleteBoardDialog && boardSession != null) {
        val session = boardSession
        AlertDialog(
            onDismissRequest = {
                if (!deleteBoardInProgress) showDeleteBoardDialog = false
            },
            title = { Text(stringResource(R.string.family_board_delete_board_title)) },
            text = {
                Text(stringResource(R.string.family_board_delete_board_confirm, session.boardName))
            },
            confirmButton = {
                Button(
                    enabled = !deleteBoardInProgress,
                    onClick = {
                        scope.launch {
                            deleteBoardInProgress = true
                            val boardCount = manager.fetchBoardList(session.service, session.accessPassword)
                                .getOrNull()?.boards?.size ?: 0
                            if (boardCount <= 1) {
                                deleteBoardInProgress = false
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.family_board_delete_last_board),
                                    Toast.LENGTH_SHORT
                                ).show()
                                showDeleteBoardDialog = false
                                return@launch
                            }
                            manager.deleteBoardEntry(
                                service = session.service,
                                accessPassword = session.accessPassword,
                                canManage = session.canManageBoard,
                                boardId = session.boardId
                            ) { result ->
                                deleteBoardInProgress = false
                                showDeleteBoardDialog = false
                                result.onSuccess {
                                    manager.closeBulletinBoard()
                                }.onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        error.message ?: error.javaClass.simpleName,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleteBoardInProgress,
                    onClick = { showDeleteBoardDialog = false }
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showRefreshAvatarsDialog && boardSession != null) {
        AlertDialog(
            onDismissRequest = {
                if (!refreshAvatarsInProgress) {
                    showRefreshAvatarsDialog = false
                }
            },
            title = { Text(stringResource(R.string.family_board_avatar_refresh_title)) },
            text = {
                Text(stringResource(R.string.family_board_avatar_refresh_confirm))
            },
            confirmButton = {
                Button(
                    enabled = !refreshAvatarsInProgress,
                    onClick = {
                        refreshAvatarsInProgress = true
                        manager.refreshBoardMessageAvatarsByLatest { result ->
                            refreshAvatarsInProgress = false
                            showRefreshAvatarsDialog = false
                            result.onSuccess { changedCount ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.family_board_avatar_refresh_success, changedCount),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.family_board_avatar_refresh_failed,
                                        error.message ?: error.javaClass.simpleName
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) { Text(stringResource(R.string.common_apply)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !refreshAvatarsInProgress,
                    onClick = { showRefreshAvatarsDialog = false }
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (boardListUiState.showRemoteShutdownDialog && boardListSession != null) {
        val session = requireNotNull(boardListSession)
        AlertDialog(
            onDismissRequest = {
                if (!boardListUiState.remoteShutdownInProgress) {
                    boardListUiState.showRemoteShutdownDialog = false
                }
            },
            title = { Text(stringResource(R.string.family_board_remote_shutdown_title)) },
            text = {
                Text(stringResource(R.string.family_board_remote_shutdown_confirm, session.service.displayHostName))
            },
            confirmButton = {
                Button(
                    enabled = !boardListUiState.remoteShutdownInProgress,
                    onClick = {
                        boardListUiState.remoteShutdownInProgress = true
                        manager.requestRemotePowerShutdown(session.service, session.accessPassword) { result ->
                            boardListUiState.remoteShutdownInProgress = false
                            result.onSuccess { message ->
                                boardListUiState.showRemoteShutdownDialog = false
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.family_board_remote_shutdown_requested,
                                        session.service.displayHostName
                                    ) + "\n" + message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.family_board_remote_shutdown_failed,
                                        error.message ?: error.javaClass.simpleName
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !boardListUiState.remoteShutdownInProgress,
                    onClick = { boardListUiState.showRemoteShutdownDialog = false }
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (boardSession != null) {
                            boardSession.boardName
                        } else if (boardListService != null) {
                            boardListService.displayHostName.ifBlank {
                                boardListService.serviceName
                            }
                        } else {
                            stringResource(R.string.family_network_title)
                        }
                    )
                },
                navigationIcon = if (boardSession != null || boardListSession != null) {
                    {
                        IconButton(onClick = {
                            when {
                                boardSession != null -> manager.closeBulletinBoard()
                                boardListSession != null -> boardListSession = null
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                } else {
                    { }
                },
                actions = {
                    if (boardSession != null) {
                        val session = boardSession
                        val canManageBoard = session.canManageBoard
                        val hasRoot = !rootUri.isNullOrBlank()
                        IconButton(onClick = {
                            manager.refreshOpenBoard(showLoadingIndicator = session.messages.isEmpty())
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.family_network_refresh))
                        }
                        if (hasRoot || canManageBoard || session.messages.any { it.isConversationMessage }) {
                            Box {
                                IconButton(onClick = { showBoardMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.family_board_menu)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showBoardMenu,
                                    onDismissRequest = { showBoardMenu = false }
                                ) {
                                    if (hasRoot) {
                                        val exportRoot = rootUri!!
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.family_board_export)) },
                                            onClick = {
                                                showBoardMenu = false
                                                manager.exportOpenBoard(exportRoot) { result ->
                                                    result.onSuccess { path ->
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.family_board_export_success, path),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }.onFailure { error ->
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                R.string.family_board_export_failed,
                                                                error.message ?: error.javaClass.simpleName
                                                            ),
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.family_board_export_pack)) },
                                            onClick = {
                                                showBoardMenu = false
                                                scope.launch {
                                                    val messageCount = session.messages.count { it.isConversationMessage }
                                                    val attachmentCount = session.messages.sumOf { it.attachments.size }
                                                    val summary = withContext(Dispatchers.IO) {
                                                        manager.estimateExportBoardpack(session.boardId)
                                                    } ?: BulletinBoardPack.PackSummary(
                                                        boardName = session.boardName,
                                                        messageCount = messageCount,
                                                        attachmentCount = attachmentCount,
                                                        totalBytes = 0L,
                                                        entryCount = 0
                                                    )
                                                    exportPackSummary = summary
                                                    exportPackProgress = null
                                                    exportPackStage = context.getString(R.string.family_board_pack_stage_prepare)
                                                    showExportPackConfirmDialog = true
                                                }
                                            }
                                        )
                                    }
                                    if (canManageBoard) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.family_board_ai_manager_title)) },
                                            onClick = {
                                                showBoardMenu = false
                                                aiManagerError = null
                                                showAiManagerDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.family_board_access_edit)) },
                                            onClick = {
                                                showBoardMenu = false
                                                boardAccessRoleIds = session.boardRoleIds?.toSet().orEmpty()
                                                boardAccessError = null
                                                showBoardAccessDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.family_board_avatar_refresh_title)) },
                                            onClick = {
                                                showBoardMenu = false
                                                showRefreshAvatarsDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.family_board_rename)) },
                                            onClick = {
                                                showBoardMenu = false
                                                showRenameBoardDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.family_board_delete_board_title),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                showBoardMenu = false
                                                showDeleteBoardDialog = true
                                            }
                                        )
                                    }
                                    if (session.messages.any { it.isConversationMessage }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.bulletin_forward_select_messages)) },
                                            onClick = {
                                                showBoardMenu = false
                                                messageForwardSelectionMode = true
                                                selectedForwardMessageIds = emptySet()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (boardListSession != null) {
                        val session = boardListSession!!
                        IconButton(onClick = { boardListRefreshTick++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.family_network_refresh))
                        }
                        IconButton(onClick = { boardListUiState.showActionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.family_board_menu))
                        }
                        DropdownMenu(
                            expanded = boardListUiState.showActionsMenu,
                            onDismissRequest = { boardListUiState.showActionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.family_board_reenter_password)) },
                                onClick = {
                                    boardListUiState.showActionsMenu = false
                                    manager.forgetBoardAccessPassword(session.service.deviceUuid)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.family_board_reenter_password_done),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    pendingBoardService = session.service
                                    boardPasswordInput = ""
                                    boardPasswordError = null
                                    showBoardPasswordDialog = true
                                }
                            )
                            if (session.service.deviceUuid != null && manager.hasRememberedBoardAccessPassword(session.service.deviceUuid)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.family_board_clear_password_memory)) },
                                    onClick = {
                                        boardListUiState.showActionsMenu = false
                                        manager.forgetBoardAccessPassword(session.service.deviceUuid)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.family_board_clear_password_memory_done),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                            if (session.canManageBoards) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.family_board_remote_config_center)) },
                                    onClick = {
                                        boardListUiState.showActionsMenu = false
                                        showRemoteConfigDialog = true
                                        remoteConfigError = null
                                        remoteConfigSnapshot = null
                                        remoteConfigSelectedRoleId = null
                                        remoteConfigRoleMenuExpanded = false
                                        remoteConfigRolePassword = ""
                                        remoteConfigPendingRevision = null
                                        remoteConfigIgnorePendingUpToRevision = null
                                        remoteConfigConflictRevision = null
                                        remoteConfigConflictPaths = emptyList()
                                        remoteConfigConflictSnapshot = null
                                        remoteConfigLoading = true
                                        scope.launch {
                                            val result = manager.fetchRemoteSystemConfig(
                                                service = session.service,
                                                accessPassword = session.accessPassword
                                            )
                                            remoteConfigLoading = false
                                            result.onSuccess { snapshot ->
                                                remoteConfigSnapshot = snapshot
                                                val first = snapshot.roles.firstOrNull()
                                                if (first != null) {
                                                    remoteConfigSelectedRoleId = first.roleId
                                                    remoteConfigRoleLabel = first.label.orEmpty()
                                                    remoteConfigRoleCanCreate = first.canCreateBoards
                                                    remoteConfigRoleCanManage = first.canManageBoards
                                                    remoteConfigRoleAllowedModels = first.allowedAgentModels.orEmpty().toSet()
                                                }
                                                remoteConfigPendingRevision = null
                                            }.onFailure { error ->
                                                remoteConfigError = error.message ?: error.javaClass.simpleName
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.family_board_import_pack)) },
                                    onClick = {
                                        boardListUiState.showActionsMenu = false
                                        if (rootUri.isNullOrBlank()) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.family_board_import_pack_no_root),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            boardListUiState.showBoardpackPickDialog = true
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.family_board_create)) },
                                    onClick = {
                                        boardListUiState.showActionsMenu = false
                                        boardListUiState.showCreateDialog = true
                                        boardListUiState.newBoardName = ""
                                        boardListUiState.createSelectedRoleIds = emptySet()
                                    }
                                )
                            }
                            if (session.service.supportsPowerShutdown) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.family_board_remote_shutdown_title),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        boardListUiState.showActionsMenu = false
                                        boardListUiState.showRemoteShutdownDialog = true
                                    }
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { manager.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.family_network_refresh))
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
        if (boardSession == null && boardListSession == null) {
            FamilyPeerListPane(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                state = state,
                remainingMinutes = remainingMinutes,
                localServicePasswordSet = !networkPassword.isNullOrBlank(),
                onOpenBoard = { service -> requestOpenBoard(service) },
                onManageUserRoles = { showUserRolesDialog = true }
            )
        } else if (boardSession == null) {
            val session = boardListSession ?: return@Scaffold
            BoardListPane(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                service = session.service,
                accessPassword = session.accessPassword,
                canManageBoards = session.canManageBoards,
                rootUri = rootUri,
                hideDotFiles = hideDotFiles,
                manager = manager,
                uiState = boardListUiState,
                refreshSignal = boardListRefreshTick,
                onRequestReenterPassword = {
                    pendingBoardService = session.service
                    boardPasswordInput = ""
                    boardPasswordError = null
                    showBoardPasswordDialog = true
                },
                onSelectBoard = { board ->
                    manager.openBulletinBoard(
                        service = session.service,
                        boardId = board.id,
                        boardName = board.name,
                        boardRoleIds = board.roleIds,
                        aiManagerModel = board.aiManagerModel,
                        accessPassword = session.accessPassword
                    )
                }
            )
        } else {
            BulletinBoardPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                session = boardSession,
                manager = manager,
                attachmentUpload = state.attachmentUpload,
                attachmentDownload = state.attachmentDownload,
                rootUri = rootUri,
                hideDotFiles = hideDotFiles,
                onRefresh = { manager.refreshOpenBoard() },
                onPost = { manager.postBoardMessage(it) },
                onUpdate = { id, content, author -> manager.updateBoardMessage(id, content, author) },
                onDelete = { manager.deleteBoardMessage(it) },
                onOpenAttachmentInternal = { attachment ->
                    scope.launch {
                        val result = manager.prepareTemporaryAttachmentFile(boardSession, attachment)
                        result.onSuccess { prepared ->
                            openAttachmentByType(
                                fileUri = prepared.fileUri,
                                fileName = prepared.fileName,
                                attachmentViewerFile = { attachmentViewerFile = it },
                                attachmentMarkdownFile = { attachmentMarkdownFile = it },
                                attachmentHtmlLocation = { attachmentHtmlLocation = it },
                                attachmentPdfFile = { attachmentPdfFile = it },
                                attachmentImageFile = { attachmentImageFile = it }
                            )
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.attachment_preview_open_failed,
                                    error.message ?: error.javaClass.simpleName
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onForwardMessages = { messages ->
                    bulletinForwardPayload = BulletinForwardPayload.Messages(boardSession, messages)
                },
                onMergeMessages = { messages ->
                    manager.mergeBoardMessages(messages.map { it.id }) { result ->
                        result.onSuccess {
                            Toast.makeText(
                                context,
                                context.getString(R.string.family_board_merge_success_toast, messages.size),
                                Toast.LENGTH_SHORT
                            ).show()
                            messageForwardSelectionMode = false
                            selectedForwardMessageIds = emptySet()
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.family_board_merge_failed,
                                    error.message ?: error.javaClass.simpleName
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                messageSelectionMode = messageForwardSelectionMode,
                onOpenAttachmentTextHex = { attachment ->
                    scope.launch {
                        val result = manager.prepareTemporaryAttachmentFile(boardSession, attachment)
                        result.onSuccess { prepared ->
                            attachmentViewerFile = Triple(prepared.fileUri, prepared.fileName, false)
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.attachment_preview_open_failed,
                                    error.message ?: error.javaClass.simpleName
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onMessageSelectionModeChange = { active ->
                    messageForwardSelectionMode = active
                    if (!active) selectedForwardMessageIds = emptySet()
                },
                selectedMessageIds = selectedForwardMessageIds,
                onSelectedMessageIdsChange = { selectedForwardMessageIds = it }
            )
        }
    }

    if (showExportPackConfirmDialog && boardSession != null) {
        val session = boardSession
        val exportRoot = rootUri.orEmpty()
        val summary = exportPackSummary
        val exportProgress = exportPackProgress
        AlertDialog(
            onDismissRequest = {
                if (!exportPackInProgress) {
                    showExportPackConfirmDialog = false
                    exportPackSummary = null
                    exportPackProgress = null
                    exportPackStage = null
                    exportPackInProgress = false
                }
            },
            title = { Text(stringResource(R.string.family_board_export_pack)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.family_board_pack_summary,
                            summary?.boardName ?: session.boardName,
                            summary?.messageCount ?: session.messages.count { it.isConversationMessage },
                            summary?.attachmentCount ?: session.messages.sumOf { it.attachments.size }
                        )
                    )
                    Text(
                        stringResource(
                            R.string.family_board_pack_bytes,
                            summary?.takeIf { it.totalBytes > 0 }?.let { formatByteCount(it.totalBytes) } ?: "-"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (exportPackInProgress) {
                        Text(
                            exportPackStage ?: stringResource(R.string.family_board_pack_stage_prepare),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (exportProgress != null) {
                            val (current, total) = exportProgress
                            LinearProgressIndicator(
                                progress = { (current.toFloat() / total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            val stageLabel = exportPackStage ?: stringResource(R.string.family_board_pack_stage_export)
                            Text(
                                if (stageLabel == stringResource(R.string.family_board_pack_stage_export)) {
                                    "$stageLabel ${formatByteCount(current.toLong())} / ${formatByteCount(total.coerceAtLeast(1).toLong())}"
                                } else {
                                    stringResource(
                                        R.string.family_board_pack_progress,
                                        stageLabel,
                                        current,
                                        total.coerceAtLeast(1)
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !exportPackInProgress,
                    onClick = {
                        exportPackInProgress = true
                        exportPackStage = context.getString(R.string.family_board_pack_stage_export)
                        exportPackProgress = null
                        manager.exportBoardpackToRoot(
                            service = session.service,
                            board = BulletinBoardInfo(
                                id = session.boardId,
                                name = session.boardName,
                                revision = session.revision,
                                messageCount = session.messages.count { it.isConversationMessage }
                            ),
                            accessPassword = session.accessPassword,
                            rootUri = exportRoot,
                            onProgress = { processed, total ->
                                exportPackProgress = processed to total
                            }
                        ) { exportResult ->
                            exportResult.onSuccess { path ->
                                exportPackInProgress = false
                                showExportPackConfirmDialog = false
                                exportPackSummary = null
                                exportPackProgress = null
                                exportPackStage = null
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.family_board_pack_export_success, path),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure { error ->
                                exportPackInProgress = false
                                exportPackProgress = null
                                exportPackStage = null
                                if (error is CancellationException) {
                                    showExportPackConfirmDialog = false
                                    exportPackSummary = null
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.family_board_pack_failed,
                                            error.message ?: error.javaClass.simpleName
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(
                    enabled = true,
                    onClick = {
                        if (exportPackInProgress) {
                            manager.cancelBoardpackExport()
                        }
                        showExportPackConfirmDialog = false
                        exportPackSummary = null
                        exportPackProgress = null
                        exportPackStage = null
                        exportPackInProgress = false
                    }
                ) {
                    Text(
                        stringResource(
                            if (exportPackInProgress) R.string.common_cancel else R.string.common_cancel
                        )
                    )
                }
            }
        )
    }

    attachmentMarkdownFile?.let { (uri, name, encrypted) ->
        MarkdownViewerScreen(
            config = readerConfig,
            initialFileUri = uri,
            initialFileName = name,
            isEncrypted = encrypted,
            sessionCache = markdownViewerSessionCache,
            onBack = {
                attachmentMarkdownFile = null
                manager.clearTemporaryAttachmentCache()
            },
            onOpenFile = { openUri, openName, openEncrypted ->
                openAttachmentByType(
                    fileUri = openUri,
                    fileName = openName,
                    isEncrypted = openEncrypted,
                    attachmentViewerFile = { attachmentViewerFile = it },
                    attachmentMarkdownFile = { attachmentMarkdownFile = it },
                    attachmentHtmlLocation = { attachmentHtmlLocation = it },
                    attachmentPdfFile = { attachmentPdfFile = it },
                    attachmentImageFile = { attachmentImageFile = it }
                )
            }
        )
    }

    attachmentHtmlLocation?.let { location ->
        HtmlViewerScreen(
            config = readerConfig,
            initialLocation = location,
            onBack = {
                attachmentHtmlLocation = null
                manager.clearTemporaryAttachmentCache()
            }
        )
    }

    attachmentPdfFile?.let { (uri, name) ->
        BackHandler { attachmentPdfFile = null }
        PdfViewerScreen(
            config = readerConfig,
            uri = uri,
            fileName = name,
            bookNoteLoadedData = null,
            bookNoteEntries = emptyList(),
            bookNoteInProgress = false,
            onRequestOpenBookNotes = {},
            onBookNoteEntriesChanged = {},
            onBack = {
                attachmentPdfFile = null
                manager.clearTemporaryAttachmentCache()
            }
        )
    }

    attachmentImageFile?.let { (uri, name) ->
        BackHandler { attachmentImageFile = null }
        AttachmentImageViewerScreen(
            fileUri = uri,
            fileName = name,
            onBack = {
                attachmentImageFile = null
                manager.clearTemporaryAttachmentCache()
            }
        )
    }

    attachmentViewerFile?.let { (uri, name, encrypted) ->
        ViewerScreen(
            fileUri = uri,
            fileName = name,
            isEncrypted = encrypted,
            onBack = {
                attachmentViewerFile = null
                manager.clearTemporaryAttachmentCache()
            },
            onOpenMarkdownView = if (isMarkdownName(name)) {
                {
                    attachmentMarkdownFile = Triple(uri, name, encrypted)
                    attachmentViewerFile = null
                }
            } else {
                null
            }
        )
    }

    if (showUserRolesDialog) {
        FamilyUserRolesDialog(manager = manager, onDismiss = { showUserRolesDialog = false })
    }

    if (showBoardAccessDialog && boardSession != null) {
        val session = boardSession
        var accessAvailableRoles by remember(session.service.deviceKey) {
            mutableStateOf(collectKnownRoleOptions(manager, session.service.isSelf, emptyList()))
        }
        LaunchedEffect(showBoardAccessDialog, session.boardId) {
            if (!showBoardAccessDialog) return@LaunchedEffect
            boardAccessRoleIds = session.boardRoleIds?.toSet().orEmpty()
            if (session.service.isSelf) {
                accessAvailableRoles = manager.listUserRoles()
            } else {
                manager.fetchBoardList(session.service, session.accessPassword)
                    .onSuccess { result ->
                        accessAvailableRoles = collectKnownRoleOptions(manager, false, result.boards)
                    }
            }
        }
        AlertDialog(
            onDismissRequest = {
                if (!boardAccessInProgress) showBoardAccessDialog = false
            },
            title = { Text(stringResource(R.string.family_board_access_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardRoleAccessSelector(
                        availableRoles = accessAvailableRoles,
                        selectedRoleIds = boardAccessRoleIds,
                        onSelectionChange = { boardAccessRoleIds = it }
                    )
                    boardAccessError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !boardAccessInProgress,
                    onClick = {
                        boardAccessInProgress = true
                        boardAccessError = null
                        manager.updateBoardAccessEntry(
                            service = session.service,
                            accessPassword = session.accessPassword,
                            canManage = session.canManageBoard,
                            boardId = session.boardId,
                            roleIds = boardAccessRoleIds.toList()
                        ) { result ->
                            boardAccessInProgress = false
                            result.onSuccess {
                                showBoardAccessDialog = false
                            }.onFailure { error ->
                                boardAccessError = error.message ?: error.javaClass.simpleName
                            }
                        }
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !boardAccessInProgress,
                    onClick = { showBoardAccessDialog = false }
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showAiManagerDialog && boardSession != null) {
        val session = boardSession
        val aiModels = remember(session.agents) {
            session.agents.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        }
        AlertDialog(
            onDismissRequest = {
                if (!aiManagerUpdating) {
                    showAiManagerDialog = false
                    aiManagerError = null
                }
            },
            title = { Text(stringResource(R.string.family_board_ai_manager_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.family_board_ai_manager_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    aiManagerError?.takeIf { it.isNotBlank() }?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(
                        enabled = !aiManagerUpdating,
                        onClick = {
                            aiManagerUpdating = true
                            manager.updateBoardAiManagerEntry(
                                service = session.service,
                                accessPassword = session.accessPassword,
                                canManage = session.canManageBoard,
                                boardId = session.boardId,
                                aiManagerModel = null
                            ) { result ->
                                aiManagerUpdating = false
                                result.onSuccess {
                                    showAiManagerDialog = false
                                    aiManagerError = null
                                    manager.refreshOpenBoard()
                                }.onFailure { error ->
                                    aiManagerError = error.message ?: error.javaClass.simpleName
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.family_board_ai_manager_clear))
                    }
                    aiModels.forEach { model ->
                        Button(
                            enabled = !aiManagerUpdating,
                            onClick = {
                                aiManagerUpdating = true
                                manager.updateBoardAiManagerEntry(
                                    service = session.service,
                                    accessPassword = session.accessPassword,
                                    canManage = session.canManageBoard,
                                    boardId = session.boardId,
                                    aiManagerModel = model
                                ) { result ->
                                    aiManagerUpdating = false
                                    result.onSuccess {
                                        showAiManagerDialog = false
                                        aiManagerError = null
                                        manager.refreshOpenBoard()
                                    }.onFailure { error ->
                                        aiManagerError = error.message ?: error.javaClass.simpleName
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (session.aiManagerModel.equals(model, ignoreCase = true)) {
                                    stringResource(R.string.family_board_ai_manager_current, model)
                                } else {
                                    model
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    enabled = !aiManagerUpdating,
                    onClick = {
                        showAiManagerDialog = false
                        aiManagerError = null
                    }
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showRemoteConfigDialog && boardListSession != null) {
        val session = boardListSession!!
        val snapshot = remoteConfigSnapshot
        val selectedRole = snapshot?.roles?.firstOrNull { it.roleId == remoteConfigSelectedRoleId }
        val reloadRemoteConfig: () -> Unit = {
            remoteConfigLoading = true
            remoteConfigError = null
            scope.launch {
                val result = manager.fetchRemoteSystemConfig(
                    service = session.service,
                    accessPassword = session.accessPassword
                )

                remoteConfigLoading = false
                result.onSuccess {
                    remoteConfigSnapshot = it
                    remoteConfigPendingRevision = null
                    remoteConfigConflictRevision = null
                    remoteConfigConflictPaths = emptyList()
                    remoteConfigConflictSnapshot = null
                    if (remoteConfigSelectedRoleId == null || it.roles.none { role -> role.roleId == remoteConfigSelectedRoleId }) {
                        val first = it.roles.firstOrNull()
                        remoteConfigSelectedRoleId = first?.roleId
                        remoteConfigRoleLabel = first?.label.orEmpty()
                        remoteConfigRoleCanCreate = first?.canCreateBoards ?: false
                        remoteConfigRoleCanManage = first?.canManageBoards ?: false
                        remoteConfigRoleAllowedModels = first?.allowedAgentModels.orEmpty().toSet()
                        remoteConfigRolePassword = ""
                    }
                }.onFailure { error ->
                    remoteConfigError = error.message ?: error.javaClass.simpleName
                }

            }
        }

        if (remoteConfigShowAllowedModelsDialog) {
            AlertDialog(
                onDismissRequest = { remoteConfigShowAllowedModelsDialog = false },
                title = { Text(stringResource(R.string.family_board_remote_config_role_allowed_models)) },
                text = {
                    when {
                        remoteConfigAvailableAgentModelsLoading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.family_board_remote_config_loading))
                            }
                        }
                        remoteConfigAvailableAgentModels.isEmpty() -> {
                            Text(stringResource(R.string.family_board_remote_config_role_allowed_models_hint))
                        }
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                remoteConfigAvailableAgentModels.forEach { model ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = model in remoteConfigRoleAllowedModels,
                                            onCheckedChange = { checked ->
                                                remoteConfigRoleAllowedModels = if (checked) {
                                                    remoteConfigRoleAllowedModels + model
                                                } else {
                                                    remoteConfigRoleAllowedModels - model
                                                }
                                            }
                                        )
                                        Text(model)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { remoteConfigShowAllowedModelsDialog = false }) {
                        Text(stringResource(R.string.common_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { remoteConfigShowAllowedModelsDialog = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        LaunchedEffect(showRemoteConfigDialog, session.service.deviceKey, session.accessPassword) {
            if (!showRemoteConfigDialog) return@LaunchedEffect
            while (showRemoteConfigDialog) {
                val currentSnapshot = remoteConfigSnapshot
                if (currentSnapshot == null) {
                    delay(500)
                    continue
                }
                val baseRevision = currentSnapshot.revision
                manager.waitRemoteSystemConfigChanges(
                    service = session.service,
                    accessPassword = session.accessPassword,
                    sinceRevision = baseRevision,
                    timeoutMs = 25_000L
                ).onSuccess { event ->
                    if (event.changed && event.revision > baseRevision) {
                        val ignoredRevision = remoteConfigIgnorePendingUpToRevision
                        if (ignoredRevision != null && event.revision <= ignoredRevision) {
                            remoteConfigIgnorePendingUpToRevision = null
                        } else {
                            remoteConfigPendingRevision = event.revision
                        }
                    }
                }
            }
        }
        if (selectedRole != null && !remoteConfigSaving) {
            LaunchedEffect(selectedRole.roleId) {
                remoteConfigRoleLabel = selectedRole.label.orEmpty()
                remoteConfigRoleCanCreate = selectedRole.canCreateBoards
                remoteConfigRoleCanManage = selectedRole.canManageBoards
                remoteConfigRoleAllowedModels = selectedRole.allowedAgentModels.orEmpty().toSet()
                remoteConfigRolePassword = ""
                remoteConfigConflictRevision = null
                remoteConfigConflictPaths = emptyList()
                remoteConfigConflictSnapshot = null
            }
        }
        AlertDialog(
            onDismissRequest = {
                if (!remoteConfigSaving) {
                    showRemoteConfigDialog = false
                    remoteConfigError = null
                }
            },
            title = { Text(stringResource(R.string.family_board_remote_config_center)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (remoteConfigLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.family_board_remote_config_loading))
                        }
                    }
                    remoteConfigPendingRevision?.let { pendingRevision ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.family_board_remote_config_pending_notice,
                                        pendingRevision
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                TextButton(
                                    enabled = !remoteConfigLoading && !remoteConfigSaving,
                                    onClick = {
                                        reloadRemoteConfig()
                                    }
                                ) {
                                    Text(stringResource(R.string.family_board_remote_config_apply_latest))
                                }
                            }
                        }
                    }
                    remoteConfigConflictRevision?.let { conflictRevision ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.family_board_remote_config_conflict_notice,
                                        conflictRevision
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                if (remoteConfigConflictPaths.isNotEmpty()) {
                                    Text(
                                        remoteConfigConflictPaths.joinToString(prefix = "changed: "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        enabled = !remoteConfigSaving,
                                        onClick = {
                                            val serverSnapshot = remoteConfigConflictSnapshot
                                            if (serverSnapshot != null) {
                                                remoteConfigSnapshot = serverSnapshot
                                                val selectedAfterApply = serverSnapshot.roles
                                                    .firstOrNull { it.roleId == remoteConfigSelectedRoleId }
                                                    ?: serverSnapshot.roles.firstOrNull()
                                                remoteConfigSelectedRoleId = selectedAfterApply?.roleId
                                                remoteConfigRoleLabel = selectedAfterApply?.label.orEmpty()
                                                remoteConfigRoleCanCreate = selectedAfterApply?.canCreateBoards ?: false
                                                remoteConfigRoleCanManage = selectedAfterApply?.canManageBoards ?: false
                                                remoteConfigRoleAllowedModels = selectedAfterApply?.allowedAgentModels.orEmpty().toSet()
                                                remoteConfigRolePassword = ""
                                                remoteConfigPendingRevision = null
                                            } else {
                                                reloadRemoteConfig()
                                            }
                                            remoteConfigConflictRevision = null
                                            remoteConfigConflictPaths = emptyList()
                                            remoteConfigConflictSnapshot = null
                                        }
                                    ) {
                                        Text(stringResource(R.string.family_board_remote_config_apply_server))
                                    }
                                    TextButton(
                                        enabled = !remoteConfigSaving && remoteConfigSelectedRoleId != null,
                                        onClick = {
                                            val roleId = remoteConfigSelectedRoleId ?: return@TextButton
                                            remoteConfigSaving = true
                                            remoteConfigError = null
                                            val rolePayload = JSONObject().apply {
                                                put("label", remoteConfigRoleLabel)
                                                put("can_create_boards", remoteConfigRoleCanCreate)
                                                put("can_manage_boards", remoteConfigRoleCanManage)
                                                val allowedModels = remoteConfigRoleAllowedModels
                                                if (allowedModels.isEmpty()) {
                                                    put("allowed_agent_models", JSONObject.NULL)
                                                } else {
                                                    put("allowed_agent_models", JSONArray(allowedModels.toList().sortedBy { it.lowercase() }))
                                                }
                                                if (remoteConfigRolePassword.isNotBlank()) {
                                                    put("password", remoteConfigRolePassword)
                                                }
                                            }
                                            val patch = JSONObject().apply { put(roleId, rolePayload) }
                                            val retryRevision = conflictRevision
                                            scope.launch {
                                                val result = manager.patchRemoteSystemConfigRoles(
                                                    service = session.service,
                                                    accessPassword = session.accessPassword,
                                                    expectedRevision = retryRevision,
                                                    rolesPatch = patch
                                                )
                                                remoteConfigSaving = false
                                                result.onSuccess { patched ->
                                                    remoteConfigSnapshot = patched.snapshot ?: remoteConfigSnapshot
                                                    remoteConfigRolePassword = ""
                                                    remoteConfigConflictRevision = null
                                                    remoteConfigConflictPaths = emptyList()
                                                    remoteConfigConflictSnapshot = null
                                                    remoteConfigPendingRevision = null
                                                    remoteConfigIgnorePendingUpToRevision = patched.revision
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.family_board_remote_config_saved),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }.onFailure { error ->
                                                    if (error is FamilyRemoteConfigConflictException) {
                                                        remoteConfigConflictRevision = error.serverRevision
                                                        remoteConfigConflictPaths = error.changedPaths
                                                        remoteConfigConflictSnapshot = error.serverSnapshot
                                                    }
                                                    remoteConfigError = error.message ?: error.javaClass.simpleName
                                                }
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.family_board_remote_config_retry_draft))
                                    }
                                }
                            }
                        }
                    }
                    snapshot?.let { config ->
                        Text(
                            stringResource(R.string.family_board_remote_config_revision, config.revision),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.family_board_remote_config_roles),
                            style = MaterialTheme.typography.labelMedium
                        )
                        ExposedDropdownMenuBox(
                            expanded = remoteConfigRoleMenuExpanded,
                            onExpandedChange = { expanded ->
                                if (!remoteConfigSaving && !remoteConfigLoading) {
                                    remoteConfigRoleMenuExpanded = expanded
                                }
                            }
                        ) {
                            val selectedRoleDisplay = config.roles
                                .firstOrNull { it.roleId == remoteConfigSelectedRoleId }
                                ?.let { role ->
                                    val label = role.label.orEmpty().ifBlank { "-" }
                                    "${role.roleId} ($label)"
                                }
                                ?: remoteConfigSelectedRoleId.orEmpty()
                            OutlinedTextField(
                                value = selectedRoleDisplay,
                                onValueChange = {},
                                readOnly = true,
                                enabled = !remoteConfigSaving && !remoteConfigLoading,
                                label = { Text(stringResource(R.string.family_board_remote_config_role_selector)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = remoteConfigRoleMenuExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                singleLine = true
                            )
                            ExposedDropdownMenu(
                                expanded = remoteConfigRoleMenuExpanded,
                                onDismissRequest = { remoteConfigRoleMenuExpanded = false }
                            ) {
                                config.roles.forEach { role ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(role.roleId)
                                                Text(
                                                    role.label.orEmpty().ifBlank { "-" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            remoteConfigRoleMenuExpanded = false
                                            remoteConfigSelectedRoleId = role.roleId
                                            remoteConfigRoleLabel = role.label.orEmpty()
                                            remoteConfigRoleCanCreate = role.canCreateBoards
                                            remoteConfigRoleCanManage = role.canManageBoards
                                            remoteConfigRoleAllowedModels = role.allowedAgentModels.orEmpty().toSet()
                                            remoteConfigRolePassword = ""
                                        }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                enabled = !remoteConfigSaving && !remoteConfigLoading,
                                onClick = {
                                    remoteConfigShowAddRoleDialog = true
                                    remoteConfigNewRoleId = ""
                                    remoteConfigError = null
                                }
                            ) {
                                Text(stringResource(R.string.family_board_remote_config_add_role))
                            }
                            TextButton(
                                enabled = !remoteConfigSaving && !remoteConfigLoading && remoteConfigSelectedRoleId != null && remoteConfigSelectedRoleId != "admin",
                                onClick = {
                                    val roleId = remoteConfigSelectedRoleId ?: return@TextButton
                                    val baseRevision = remoteConfigSnapshot?.revision ?: return@TextButton
                                    remoteConfigSaving = true
                                    remoteConfigError = null
                                    val patch = JSONObject().apply { put(roleId, JSONObject.NULL) }
                                    scope.launch {
                                        val result = manager.patchRemoteSystemConfigRoles(
                                            service = session.service,
                                            accessPassword = session.accessPassword,
                                            expectedRevision = baseRevision,
                                            rolesPatch = patch
                                        )
                                        remoteConfigSaving = false
                                        result.onSuccess { patched ->
                                            val newSnapshot = patched.snapshot
                                            if (newSnapshot != null) {
                                                remoteConfigSnapshot = newSnapshot
                                                val first = newSnapshot.roles.firstOrNull()
                                                remoteConfigSelectedRoleId = first?.roleId
                                                remoteConfigRoleLabel = first?.label.orEmpty()
                                                remoteConfigRoleCanCreate = first?.canCreateBoards ?: false
                                                remoteConfigRoleCanManage = first?.canManageBoards ?: false
                                                remoteConfigRoleAllowedModels = first?.allowedAgentModels.orEmpty().toSet()
                                                remoteConfigRolePassword = ""
                                                remoteConfigPendingRevision = null
                                                remoteConfigIgnorePendingUpToRevision = patched.revision
                                                remoteConfigConflictRevision = null
                                                remoteConfigConflictPaths = emptyList()
                                                remoteConfigConflictSnapshot = null
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.family_board_remote_config_deleted, roleId),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                reloadRemoteConfig()
                                            }
                                        }.onFailure { error ->
                                            if (error is FamilyRemoteConfigConflictException) {
                                                remoteConfigConflictRevision = error.serverRevision
                                                remoteConfigConflictPaths = error.changedPaths
                                                remoteConfigConflictSnapshot = error.serverSnapshot
                                            }
                                            remoteConfigError = error.message ?: error.javaClass.simpleName
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.family_board_remote_config_delete_role))
                            }
                        }

                        if (remoteConfigSelectedRoleId != null) {
                            OutlinedTextField(
                                value = remoteConfigRoleLabel,
                                onValueChange = { remoteConfigRoleLabel = it },
                                label = { Text(stringResource(R.string.family_board_remote_config_role_label)) },
                                enabled = !remoteConfigSaving,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = remoteConfigRolePassword,
                                onValueChange = { remoteConfigRolePassword = it },
                                label = { Text(stringResource(R.string.family_board_remote_config_role_password)) },
                                enabled = !remoteConfigSaving,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            Button(
                                enabled = !remoteConfigSaving,
                                onClick = {
                                    remoteConfigShowAllowedModelsDialog = true
                                    if (remoteConfigAvailableAgentModels.isEmpty() && !remoteConfigAvailableAgentModelsLoading) {
                                        remoteConfigAvailableAgentModelsLoading = true
                                        scope.launch {
                                            val result = manager.fetchRemoteAgentModels(
                                                service = session.service,
                                                accessPassword = session.accessPassword
                                            )
                                            remoteConfigAvailableAgentModelsLoading = false
                                            result.onSuccess { models ->
                                                remoteConfigAvailableAgentModels = models
                                            }.onFailure { error ->
                                                remoteConfigError = error.message ?: error.javaClass.simpleName
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.family_board_remote_config_role_allowed_models))
                                    Text(
                                        text = if (remoteConfigRoleAllowedModels.isEmpty()) {
                                            stringResource(R.string.family_board_remote_config_role_allowed_models_hint)
                                        } else {
                                            remoteConfigRoleAllowedModels.joinToString(", ")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = remoteConfigRoleCanCreate,
                                    enabled = !remoteConfigSaving,
                                    onCheckedChange = { remoteConfigRoleCanCreate = it }
                                )
                                Text(stringResource(R.string.family_board_remote_config_can_create))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = remoteConfigRoleCanManage,
                                    enabled = !remoteConfigSaving,
                                    onCheckedChange = { remoteConfigRoleCanManage = it }
                                )
                                Text(stringResource(R.string.family_board_remote_config_can_manage))
                            }
                        }
                    }
                    remoteConfigError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !remoteConfigSaving && !remoteConfigLoading && snapshot != null && remoteConfigSelectedRoleId != null,
                    onClick = {
                        val roleId = remoteConfigSelectedRoleId ?: return@Button
                        val baseRevision = snapshot?.revision ?: return@Button
                        remoteConfigSaving = true
                        remoteConfigError = null
                        val rolePayload = JSONObject().apply {
                            put("label", remoteConfigRoleLabel)
                            put("can_create_boards", remoteConfigRoleCanCreate)
                            put("can_manage_boards", remoteConfigRoleCanManage)
                            val allowedModels = remoteConfigRoleAllowedModels
                            if (allowedModels.isEmpty()) {
                                put("allowed_agent_models", JSONObject.NULL)
                            } else {
                                put("allowed_agent_models", JSONArray(allowedModels.toList().sortedBy { it.lowercase() }))
                            }
                            if (remoteConfigRolePassword.isNotBlank()) {
                                put("password", remoteConfigRolePassword)
                            }
                        }
                        val patch = JSONObject().apply { put(roleId, rolePayload) }
                        scope.launch {
                            val result = manager.patchRemoteSystemConfigRoles(
                                service = session.service,
                                accessPassword = session.accessPassword,
                                expectedRevision = baseRevision,
                                rolesPatch = patch
                            )
                            remoteConfigSaving = false
                            result.onSuccess { patched ->
                                val newSnapshot = patched.snapshot
                                if (newSnapshot != null) {
                                    remoteConfigSnapshot = newSnapshot
                                    remoteConfigRolePassword = ""
                                    remoteConfigPendingRevision = null
                                    remoteConfigIgnorePendingUpToRevision = patched.revision
                                    remoteConfigConflictRevision = null
                                    remoteConfigConflictPaths = emptyList()
                                    remoteConfigConflictSnapshot = null
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.family_board_remote_config_saved),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showRemoteConfigDialog = false
                                } else {
                                    remoteConfigIgnorePendingUpToRevision = patched.revision
                                    reloadRemoteConfig()
                                    showRemoteConfigDialog = false
                                }
                            }.onFailure { error ->
                                if (error is FamilyRemoteConfigConflictException) {
                                    remoteConfigConflictRevision = error.serverRevision
                                    remoteConfigConflictPaths = error.changedPaths
                                    remoteConfigConflictSnapshot = error.serverSnapshot
                                }
                                remoteConfigError = error.message ?: error.javaClass.simpleName
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !remoteConfigSaving,
                    onClick = {
                        reloadRemoteConfig()
                    }
                ) {
                    Text(stringResource(R.string.family_board_remote_config_refresh))
                }
            }
        )

        if (remoteConfigShowAddRoleDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!remoteConfigSaving) {
                        remoteConfigShowAddRoleDialog = false
                    }
                },
                title = { Text(stringResource(R.string.family_board_remote_config_add_role)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = remoteConfigNewRoleId,
                            onValueChange = { remoteConfigNewRoleId = it },
                            label = { Text(stringResource(R.string.family_board_remote_config_new_role_id)) },
                            enabled = !remoteConfigSaving,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !remoteConfigSaving,
                        onClick = {
                            val roleId = remoteConfigNewRoleId.trim()
                            if (roleId.isEmpty()) {
                                remoteConfigError = context.getString(R.string.family_board_remote_config_new_role_id_required)
                                return@Button
                            }
                            val snapshotNow = remoteConfigSnapshot
                            if (snapshotNow == null) {
                                remoteConfigError = context.getString(R.string.family_board_remote_config_loading)
                                return@Button
                            }
                            if (snapshotNow.roles.any { it.roleId == roleId }) {
                                remoteConfigError = context.getString(
                                    R.string.family_board_remote_config_new_role_exists,
                                    roleId
                                )
                                return@Button
                            }

                            remoteConfigSaving = true
                            remoteConfigError = null
                            val rolePayload = JSONObject()
                            val patch = JSONObject().apply { put(roleId, rolePayload) }
                            scope.launch {
                                val result = manager.patchRemoteSystemConfigRoles(
                                    service = session.service,
                                    accessPassword = session.accessPassword,
                                    expectedRevision = snapshotNow.revision,
                                    rolesPatch = patch
                                )
                                remoteConfigSaving = false
                                result.onSuccess { patched ->
                                    val newSnapshot = patched.snapshot
                                    if (newSnapshot != null) {
                                        remoteConfigSnapshot = newSnapshot
                                        remoteConfigSelectedRoleId = roleId
                                        remoteConfigRoleLabel = newSnapshot.roles
                                            .firstOrNull { it.roleId == roleId }
                                            ?.label
                                            .orEmpty()
                                        remoteConfigRoleCanCreate = newSnapshot.roles
                                            .firstOrNull { it.roleId == roleId }
                                            ?.canCreateBoards
                                            ?: false
                                        remoteConfigRoleCanManage = newSnapshot.roles
                                            .firstOrNull { it.roleId == roleId }
                                            ?.canManageBoards
                                            ?: false
                                        remoteConfigRoleAllowedModels = newSnapshot.roles
                                            .firstOrNull { it.roleId == roleId }
                                            ?.allowedAgentModels
                                            .orEmpty()
                                            .toSet()
                                        remoteConfigRolePassword = ""
                                        remoteConfigPendingRevision = null
                                        remoteConfigIgnorePendingUpToRevision = patched.revision
                                        remoteConfigConflictRevision = null
                                        remoteConfigConflictPaths = emptyList()
                                        remoteConfigConflictSnapshot = null
                                    } else {
                                        remoteConfigIgnorePendingUpToRevision = patched.revision
                                        reloadRemoteConfig()
                                        remoteConfigSelectedRoleId = roleId
                                        remoteConfigRoleLabel = ""
                                        remoteConfigRoleCanCreate = false
                                        remoteConfigRoleCanManage = false
                                        remoteConfigRoleAllowedModels = emptySet()
                                        remoteConfigRolePassword = ""
                                    }
                                    remoteConfigShowAddRoleDialog = false
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.family_board_remote_config_created, roleId),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure { error ->
                                    if (error is FamilyRemoteConfigConflictException) {
                                        remoteConfigConflictRevision = error.serverRevision
                                        remoteConfigConflictPaths = error.changedPaths
                                        remoteConfigConflictSnapshot = error.serverSnapshot
                                    }
                                    remoteConfigError = error.message ?: error.javaClass.simpleName
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.family_board_remote_config_create_role))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !remoteConfigSaving,
                        onClick = { remoteConfigShowAddRoleDialog = false }
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }
    }

    bulletinForwardPayload?.let { payload ->
        BulletinForwardTargetFlow(
            manager = manager,
            payload = payload,
            localServiceEnabled = localServiceEnabled,
            keepEphemeralSession = false,
            onDismiss = { bulletinForwardPayload = null },
            onComplete = { bulletinForwardPayload = null }
        )
    }
}

@Composable
private fun BoardListPane(
    modifier: Modifier,
    service: FamilyDiscoveredService,
    accessPassword: String?,
    canManageBoards: Boolean,
    rootUri: String?,
    hideDotFiles: Boolean,
    manager: FamilyNetworkManager,
    uiState: BoardListUiState,
    refreshSignal: Int,
    onRequestReenterPassword: () -> Unit,
    onSelectBoard: (BulletinBoardInfo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var boards by remember { mutableStateOf<List<BulletinBoardInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val targetDeviceLabel = service.displayHostName.ifBlank { service.serviceName }
    val loginIdentityLabel = when {
        service.isSelf -> context.getString(R.string.family_network_local_service)
        canManageBoards -> context.getString(R.string.family_board_identity_admin)
        else -> context.getString(R.string.family_board_identity_user)
    }
    val existingBoardNames = remember(boards) { boards.map { it.name.trim() }.toSet() }
    val availableRoles = remember(boards, service.isSelf) {
        collectKnownRoleOptions(manager, service.isSelf, boards)
    }
    var importPackPreview by remember { mutableStateOf<BulletinBoardPack.PackPreview?>(null) }
    var importPackProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var importPackStage by remember { mutableStateOf<String?>(null) }

    val reloadBoards: () -> Unit = {
        scope.launch {
            loading = true
            error = null
            val result = manager.fetchBoardList(service, accessPassword)
            loading = false
            result.onSuccess { listResult -> boards = listResult.boards }
                .onFailure { err ->
                    error = err.message ?: err.javaClass.simpleName
                }
        }
    }

    with(uiState) {
        LaunchedEffect(service.deviceKey, accessPassword, refreshSignal) {
            reloadBoards()
        }

        Box(modifier = modifier) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.family_board_device_info_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.family_board_login_identity_label, loginIdentityLabel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.family_board_device_service_name_label, service.serviceName.ifBlank { "-" }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.family_board_device_service_type_label, service.serviceType),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(
                                    R.string.family_board_device_endpoint_label,
                                    if (service.host.isBlank() || service.port <= 0) "-" else "${service.host}:${service.port}"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.family_board_device_key_label, service.deviceKey),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(
                                    R.string.family_board_device_power_label,
                                    if (service.supportsPowerShutdown) stringResource(R.string.family_board_device_power_supported)
                                    else stringResource(R.string.family_board_device_power_unsupported)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.family_board_list_title), style = MaterialTheme.typography.titleSmall)
                                if (canManageBoards) {
                                    IconButton(onClick = { showActionsMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.family_board_menu))
                                    }
                                }
                            }
                            when {
                                loading -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Text(stringResource(R.string.family_board_picker_loading))
                                    }
                                }
                                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                boards.isEmpty() -> Text(
                                    stringResource(R.string.family_board_picker_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                else -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        boards.forEachIndexed { index, board ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(enabled = !actionInProgress) { onSelectBoard(board) }
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(board.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(
                                                        stringResource(R.string.family_board_picker_item_meta, board.messageCount),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (index < boards.lastIndex) HorizontalDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showBoardpackPickDialog && !rootUri.isNullOrBlank()) {
                DirectoryPickDialog(
                    rootUri = rootUri,
                    hideDotFiles = hideDotFiles,
                    policy = DirectoryPickPurpose.BULLETIN_BOARDPACK.defaultPolicy(),
                    titleRes = R.string.family_board_import_pick_title,
                    hintRes = R.string.family_board_import_pick_hint,
                    invalidPickHintRes = R.string.family_board_import_pick_invalid,
                    noMatchingFilesHintRes = R.string.family_board_import_pick_empty_dir,
                    onDismiss = { showBoardpackPickDialog = false },
                    onPicked = { file ->
                        showBoardpackPickDialog = false
                        pendingImportFile = file
                        pendingImportName = file.name.removeSuffix(".boardpack").trim().ifEmpty { "board" }
                        importNameConflict = false
                        importPackNameError = null
                        importPackSourceName = ""
                        importPackPreview = null
                        importPackProgress = null
                        importPackStage = null
                    }
                )
            }

            if (showCreateDialog) {
                val createValidation = validateBoardCreationInput(newBoardName, existingBoardNames)
                AlertDialog(
                    onDismissRequest = {
                        if (!actionInProgress) {
                            showCreateDialog = false
                            newBoardName = ""
                            createSelectedRoleIds = emptySet()
                            createNameError = null
                        }
                    },
                    title = { Text(stringResource(R.string.family_board_create_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newBoardName,
                                onValueChange = {
                                    newBoardName = it
                                    createNameError = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.family_board_create_name_label)) },
                                singleLine = true,
                                enabled = !actionInProgress,
                                isError = createValidation.error == "duplicate"
                            )
                            if (createValidation.error == "duplicate") {
                                Text(
                                    stringResource(R.string.family_board_name_duplicate, createValidation.trimmedName),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            BoardRoleAccessSelector(
                                availableRoles = availableRoles,
                                selectedRoleIds = createSelectedRoleIds,
                                onSelectionChange = { createSelectedRoleIds = it }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            enabled = !actionInProgress && createValidation.isValid,
                            onClick = {
                                actionInProgress = true
                                createNameError = null
                                manager.createBoardEntry(
                                    service = service,
                                    accessPassword = accessPassword,
                                    canManage = canManageBoards,
                                    name = createValidation.trimmedName,
                                    roleIds = createSelectedRoleIds.toList()
                                ) { result ->
                                    actionInProgress = false
                                    result.onSuccess {
                                        showCreateDialog = false
                                        newBoardName = ""
                                        createSelectedRoleIds = emptySet()
                                        createNameError = null
                                        reloadBoards()
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.family_board_create_title),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }.onFailure { error ->
                                        createNameError = error.message ?: error.javaClass.simpleName
                                        Toast.makeText(
                                            context,
                                            error.message ?: error.javaClass.simpleName,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.common_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !actionInProgress,
                            onClick = {
                                showCreateDialog = false
                                newBoardName = ""
                                createSelectedRoleIds = emptySet()
                                createNameError = null
                            }
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            LaunchedEffect(pendingImportFile?.uri) {
                val file = pendingImportFile ?: return@LaunchedEffect
                importPackNameLoading = true
                importPackNameError = null
                importPackPreview = null
                val previewThresholdBytes = 32L * 1024L * 1024L
                if (file.size > previewThresholdBytes) {
                    importPackNameLoading = false
                    importPackSourceName = ""
                    importNameConflict = pendingImportName.trim() in existingBoardNames
                    return@LaunchedEffect
                }
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            BulletinBoardPack.previewImport(context, input)
                        } ?: throw IllegalStateException(context.getString(R.string.family_msg_94078))
                    }
                }
                importPackNameLoading = false
                result.onSuccess { preview ->
                    importPackPreview = preview
                    importPackSourceName = preview.summary.boardName
                    importNameConflict = preview.summary.boardName in existingBoardNames
                    pendingImportName = if (importNameConflict) {
                        BulletinBoardPack.suggestUniqueBoardName(context, preview.summary.boardName, existingBoardNames)
                    } else {
                        preview.summary.boardName
                    }
                }.onFailure { error ->
                    importPackNameError = error.message ?: error.javaClass.simpleName
                }
            }

            pendingImportFile?.let { file ->
                val trimmedImportName = pendingImportName.trim()
                val importNameTaken = trimmedImportName.isNotEmpty() && trimmedImportName in existingBoardNames
                val canConfirmImport = !actionInProgress &&
                    !importPackNameLoading &&
                    trimmedImportName.isNotEmpty() &&
                    !importNameTaken &&
                    importPackNameError == null
                AlertDialog(
                    onDismissRequest = { if (!actionInProgress) pendingImportFile = null },
                    title = { Text(stringResource(R.string.family_board_import_pack_confirm_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.family_board_import_pack_confirm_message, file.name, targetDeviceLabel))
                            Text(
                                stringResource(R.string.family_board_pack_bytes, file.displaySize.ifBlank { "0 B" }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            importPackPreview?.summary?.let { summary ->
                                Text(
                                    stringResource(R.string.family_board_pack_summary, summary.boardName, summary.messageCount, summary.attachmentCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (summary.totalBytes > 0) {
                                    Text(
                                        stringResource(R.string.family_board_pack_bytes, formatByteCount(summary.totalBytes)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            when {
                                importPackNameLoading -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text(
                                            stringResource(R.string.family_board_picker_loading),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                importPackProgress != null && importPackStage != null -> {
                                    val (current, total) = importPackProgress!!
                                    val stageLabel = importPackStage!!
                                    Text(
                                        "$stageLabel ${formatByteCount(current.toLong())} / ${formatByteCount(total.coerceAtLeast(1).toLong())}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    LinearProgressIndicator(
                                        progress = { (current.toFloat() / total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                else -> {
                                    OutlinedTextField(
                                        value = pendingImportName,
                                        onValueChange = { pendingImportName = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.family_board_import_name_label)) },
                                        singleLine = true,
                                        enabled = !actionInProgress,
                                        isError = importNameTaken || importPackNameError != null
                                    )
                                    if (importNameConflict && importPackSourceName.isNotEmpty()) {
                                        Text(
                                            stringResource(R.string.family_board_import_name_conflict, importPackSourceName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (importNameTaken) {
                                        Text(
                                            stringResource(R.string.family_board_name_duplicate, trimmedImportName),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    importPackNameError?.let { err ->
                                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            enabled = canConfirmImport,
                            onClick = {
                                actionInProgress = true
                                importPackProgress = null
                                importPackStage = context.getString(R.string.family_board_pack_stage_import)
                                manager.importBoardpackFromFile(
                                    file = file,
                                    service = service,
                                    accessPassword = accessPassword,
                                    importName = trimmedImportName,
                                    onProgress = { processed, total ->
                                        importPackProgress = processed to total
                                    }
                                ) { result ->
                                    actionInProgress = false
                                    importPackProgress = null
                                    importPackStage = null
                                    pendingImportFile = null
                                    result.onSuccess {
                                        Toast.makeText(context, context.getString(R.string.family_board_pack_import_success, it.name), Toast.LENGTH_SHORT).show()
                                        reloadBoards()
                                    }.onFailure { err ->
                                        if (err !is CancellationException) {
                                            Toast.makeText(context, context.getString(R.string.family_board_pack_failed, err.message ?: err.javaClass.simpleName), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.common_ok)) }
                    },
                    dismissButton = {
                        TextButton(enabled = true, onClick = {
                            if (actionInProgress) {
                                manager.cancelBoardpackImport()
                            }
                            actionInProgress = false
                            importPackProgress = null
                            importPackStage = null
                            pendingImportFile = null
                        }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FamilyPeerListPane(
    modifier: Modifier,
    state: FamilyNetworkState,
    remainingMinutes: Int?,
    localServicePasswordSet: Boolean,
    onOpenBoard: (FamilyDiscoveredService) -> Unit,
    onManageUserRoles: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            CollapsibleLocalServiceCard(
                state = state,
                remainingMinutes = remainingMinutes,
                localServicePasswordSet = localServicePasswordSet,
                onManageUserRoles = onManageUserRoles
            )
        }

        item {
            Text(
                stringResource(R.string.family_network_discovered_services_count, state.discoveredServices.size),
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (state.discoveredServices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.family_network_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(
                items = state.discoveredServices,
                key = { index, service -> "${service.deviceKey}#$index" }
            ) { _index, service ->
                FamilyNetworkServiceCard(
                    service = service,
                    onClick = { onOpenBoard(service) }
                )
            }
        }

        item {
            FamilyWorkLogPane(
                logLines = state.logLines,
                expandedContentModifier = Modifier.fillParentMaxHeight()
            )
        }
    }
}

@Composable
private fun CollapsibleLocalServiceCard(
    state: FamilyNetworkState,
    remainingMinutes: Int?,
    localServicePasswordSet: Boolean,
    onManageUserRoles: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val statusLabel = if (state.isRunning) {
        stringResource(R.string.family_network_running)
    } else {
        stringResource(R.string.family_network_stopped)
    }
    val summary = buildString {
        if (!state.localServiceEnabled) {
            append(context.getString(R.string.family_network_local_service_disabled))
        } else {
            append(statusLabel)
            if (state.port > 0) append(" · ${state.port}")
            state.localIp?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Column {
                        Text(
                            stringResource(R.string.family_network_local_service),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.family_network_collapse else R.string.family_network_expand
                        )
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.family_network_bulletin_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )
                Text(stringResource(R.string.family_network_service_type, state.serviceType), style = MaterialTheme.typography.bodySmall)
                if (state.localHostDisplayName.isNotBlank()) {
                    Text(stringResource(R.string.family_network_service_name, state.localHostDisplayName), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (state.isRegistered) stringResource(R.string.family_network_registered)
                    else stringResource(R.string.family_network_not_registered),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (state.isDiscovering) stringResource(R.string.family_network_discovery_active)
                    else stringResource(R.string.family_network_discovery_idle),
                    style = MaterialTheme.typography.bodySmall
                )
                if (state.localServiceEnabled) {
                    Text(
                        if (localServicePasswordSet) {
                            stringResource(R.string.family_network_local_password_set)
                        } else {
                            stringResource(R.string.family_network_local_password_unset)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onManageUserRoles) {
                        Text(stringResource(R.string.family_user_roles_manage))
                    }
                }
                state.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                    Text(
                        stringResource(R.string.family_network_last_error, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                remainingMinutes?.let { mins ->
                    Text(
                        stringResource(R.string.network_service_auto_close, mins),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.network_service_keep_screen_on),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletinBoardPage(
    modifier: Modifier,
    session: BulletinBoardOpenSession,
    manager: FamilyNetworkManager,
    attachmentUpload: BulletinAttachmentUploadProgress?,
    attachmentDownload: BulletinAttachmentDownloadProgress?,
    rootUri: String?,
    hideDotFiles: Boolean,
    onRefresh: () -> Unit,
    onPost: (String) -> Unit,
    onUpdate: (String, String, String?) -> Unit,
    onDelete: (String) -> Unit,
    onOpenAttachmentInternal: (BulletinAttachmentRef) -> Unit,
    onOpenAttachmentTextHex: (BulletinAttachmentRef) -> Unit,
    onForwardMessages: (List<BulletinMessage>) -> Unit,
    onMergeMessages: (List<BulletinMessage>) -> Unit,
    messageSelectionMode: Boolean,
    onMessageSelectionModeChange: (Boolean) -> Unit,
    selectedMessageIds: Set<String>,
    onSelectedMessageIdsChange: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var draftMessage by remember(session.service.deviceKey, session.boardId) {
        mutableStateOf(TextFieldValue(""))
    }
    val commandDescriptions = remember(context) {
        mapOf(
            "/ai status" to context.getString(R.string.family_board_command_ai_status),
            "/ai stop" to context.getString(R.string.family_board_command_ai_stop)
        )
    }
    val aiManagerModel = session.aiManagerModel?.trim()?.takeIf { it.isNotEmpty() }
    val aiAgents = remember(session.agents) {
        session.agents.map { it.trim() }.filter { it.isNotEmpty() }
    }
    val composeAgentTargets = remember(aiManagerModel, aiAgents) {
        if (aiManagerModel != null) {
            emptyList()
        } else {
            aiAgents
        }
    }
    val composeTargets = remember(composeAgentTargets, session.participants, session.commands, commandDescriptions) {
        BulletinBoardMention.buildComposeTargets(
            composeAgentTargets,
            session.participants,
            session.commands,
            commandDescriptions
        )
    }
    var editingMessage by remember { mutableStateOf<BulletinMessage?>(null) }
    var editingText by remember { mutableStateOf("") }
    var editingAuthor by remember { mutableStateOf("") }
    var editingContentError by remember { mutableStateOf<String?>(null) }
    var deletingMessage by remember { mutableStateOf<BulletinMessage?>(null) }
    var showAttachmentPickDialog by remember { mutableStateOf(false) }
    var pendingDownloadConflict by remember { mutableStateOf<BulletinAttachmentRef?>(null) }
    var previewAttachment by remember { mutableStateOf<BulletinAttachmentRef?>(null) }
    var previewMeta by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var pendingMergeMessages by remember { mutableStateOf<List<BulletinMessage>?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var previousMessageCount by remember(session.service.deviceKey) { mutableIntStateOf(0) }

    LaunchedEffect(session.service.deviceKey, session.boardId) {
        while (true) {
            delay(3000)
            onRefresh()
        }
    }

    val conversationMessages = remember(session.messages) {
        session.messages.filter { it.isConversationMessage }
    }
    val selectedConversationMessages = remember(conversationMessages, selectedMessageIds) {
        conversationMessages.filter { it.id in selectedMessageIds }
    }
    val canMergeSelectedMessages = remember(selectedConversationMessages) {
        selectedConversationMessages.size >= 2 &&
            selectedConversationMessages.map { it.authorLabel.trim() }.distinct().size == 1
    }

    LaunchedEffect(session.messages.size) {
        if (session.messages.isNotEmpty() && session.messages.size >= previousMessageCount) {
            listState.animateScrollToItem(session.messages.lastIndex)
        }
        previousMessageCount = session.messages.size
    }

    if (editingMessage != null) {
        val canSaveEdit = editingText.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = {
                editingMessage = null
                editingContentError = null
            },
            title = { Text(stringResource(R.string.family_board_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editingAuthor,
                        onValueChange = { editingAuthor = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.family_board_author_label)) }
                    )
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = {
                            editingText = it
                            editingContentError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                        isError = editingContentError != null,
                        supportingText = {
                            editingContentError?.let { error ->
                                Text(error)
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = editingMessage ?: return@Button
                    if (editingText.trim().isEmpty()) {
                        editingContentError = context.getString(R.string.family_board_edit_empty_content)
                        return@Button
                    }
                    onUpdate(target.id, editingText, editingAuthor)
                    editingMessage = null
                    editingContentError = null
                }, enabled = canSaveEdit) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    editingMessage = null
                    editingContentError = null
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (deletingMessage != null) {
        AlertDialog(
            onDismissRequest = { deletingMessage = null },
            title = { Text(stringResource(R.string.family_board_delete_title)) },
            text = { Text(stringResource(R.string.family_board_delete_confirm)) },
            confirmButton = {
                Button(onClick = {
                    val target = deletingMessage ?: return@Button
                    onDelete(target.id)
                    deletingMessage = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessage = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    pendingMergeMessages?.let { mergeMessages ->
        val mergeAuthor = mergeMessages.firstOrNull()?.authorLabel.orEmpty()
        val mergeAttachmentCount = mergeMessages.sumOf { it.attachments.size }
        val mergeMessageLines = remember(mergeMessages) {
            mergeMessages.mapIndexed { index, msg ->
                val line = msg.content.trim().ifBlank { "(${msg.id.take(8)})" }
                "${index + 1}. $line"
            }
        }
        val mergeAttachmentNames = remember(mergeMessages) {
            mergeMessages
                .flatMap { it.attachments }
                .distinctBy { it.id }
                .map { attachment ->
                    val prefix = if (attachment.kind == BulletinAttachmentKind.DIRECTORY) "[DIR]" else "[FILE]"
                    "$prefix ${attachment.name}"
                }
        }
        AlertDialog(
            onDismissRequest = { pendingMergeMessages = null },
            title = { Text(stringResource(R.string.family_board_merge_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(
                            R.string.family_board_merge_confirm_message,
                            mergeMessages.size,
                            mergeAuthor
                        )
                    )
                    Text(
                        stringResource(R.string.family_board_merge_confirm_attachments, mergeAttachmentCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.family_board_merge_confirm_content_preview),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        mergeMessageLines.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (mergeAttachmentNames.isNotEmpty()) {
                        Text(
                            stringResource(R.string.family_board_merge_confirm_attachment_preview),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            mergeAttachmentNames.forEach { name ->
                                Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.family_board_merge_confirm_irreversible),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pendingMergeMessages ?: return@Button
                        pendingMergeMessages = null
                        onMergeMessages(target)
                    }
                ) {
                    Text(stringResource(R.string.family_board_merge_selected_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMergeMessages = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showAttachmentPickDialog && rootUri != null) {
        AttachmentPickDialog(
            rootUri = rootUri,
            hideDotFiles = hideDotFiles,
            onDismiss = { showAttachmentPickDialog = false },
            onPicked = { item ->
                showAttachmentPickDialog = false
                manager.uploadAttachmentAndPost(item, draftMessage.text)
                draftMessage = TextFieldValue("")
            }
        )
    }

    val uploadInProgress = attachmentUpload != null
    val downloadInProgress = attachmentDownload != null
    val transferInProgress = uploadInProgress || downloadInProgress

    fun requestAttachmentPreview(attachment: BulletinAttachmentRef) {
        previewAttachment = attachment
        previewMeta = null
        previewError = null
        previewLoading = true
        scope.launch {
            val result = manager.fetchAttachmentMeta(session, attachment.id)
            previewLoading = false
            result.onSuccess { previewMeta = it }
                .onFailure { previewError = it.message ?: it.javaClass.simpleName }
        }
    }

    fun requestAttachmentDownload(attachment: BulletinAttachmentRef) {
        if (rootUri.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.attachment_upload_no_root), Toast.LENGTH_SHORT).show()
            return
        }
        if (transferInProgress) return
        scope.launch {
            val exists = withContext(Dispatchers.IO) {
                BulletinAttachmentDownloader.targetExists(context, rootUri, attachment)
            }
            if (exists) {
                pendingDownloadConflict = attachment
            } else {
                manager.downloadBoardAttachment(rootUri, attachment)
            }
        }
    }

    previewAttachment?.let { attachment ->
        BulletinAttachmentPreviewDialog(
            attachment = attachment,
            meta = previewMeta,
            loading = previewLoading,
            error = previewError,
            canOpenInternal = attachment.kind == BulletinAttachmentKind.FILE,
            onDismiss = {
                previewAttachment = null
                previewMeta = null
                previewError = null
            },
            onOpenInternal = {
                previewAttachment = null
                previewMeta = null
                previewError = null
                onOpenAttachmentInternal(attachment)
            },
            onOpenTextHex = if (attachment.kind == BulletinAttachmentKind.FILE) {
                {
                    previewAttachment = null
                    previewMeta = null
                    previewError = null
                    onOpenAttachmentTextHex(attachment)
                }
            } else {
                null
            },
            onDownload = {
                previewAttachment = null
                previewMeta = null
                previewError = null
                requestAttachmentDownload(attachment)
            }
        )
    }

    pendingDownloadConflict?.let { attachment ->
        AlertDialog(
            onDismissRequest = { pendingDownloadConflict = null },
            title = { Text(stringResource(R.string.attachment_download_conflict_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.attachment_download_conflict_message,
                        attachment.name
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    manager.downloadBoardAttachment(
                        rootUri!!,
                        attachment,
                        BulletinAttachmentDownloadConflict.OVERWRITE
                    )
                    pendingDownloadConflict = null
                }) {
                    Text(stringResource(R.string.attachment_download_conflict_overwrite))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        manager.downloadBoardAttachment(
                            rootUri!!,
                            attachment,
                            BulletinAttachmentDownloadConflict.RENAME
                        )
                        pendingDownloadConflict = null
                    }) {
                        Text(stringResource(R.string.attachment_download_conflict_rename))
                    }
                    TextButton(onClick = { pendingDownloadConflict = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                buildString {
                    append(
                        when {
                            session.isHost -> stringResource(R.string.family_board_role_host)
                            !session.remoteRoleLabel.isNullOrBlank() ->
                                stringResource(R.string.family_board_role_remote, session.remoteRoleLabel)
                            !session.remoteRoleId.isNullOrBlank() ->
                                stringResource(R.string.family_board_role_remote, session.remoteRoleId)
                            session.canManageBoard -> stringResource(R.string.family_board_role_remote_host)
                            else -> stringResource(R.string.family_board_role_guest)
                        }
                    )
                    append(" · ")
                    append(session.service.displayHostName.ifBlank { session.service.host })
                    append(':')
                    append(session.service.port)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.family_board_revision, session.revision),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        session.aiManagerModel?.takeIf { it.isNotBlank() }?.let { aiManagerModel ->
            Text(
                stringResource(R.string.family_board_ai_manager_info, aiManagerModel),
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        session.lastError?.let { error ->
            Text(
                error,
                modifier = Modifier.padding(horizontal = 10.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }
        HorizontalDivider()

        if (session.loading && session.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else if (conversationMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.family_board_no_messages),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                itemsIndexed(
                    items = session.messages,
                    key = { index, message -> "${message.id}#${message.seq}#$index" },
                    contentType = { _index, _message -> "bulletin_message" }
                ) { _index, message ->
                    if (message.isAiStatus) {
                        BulletinAiStatusRow(message = message)
                    } else if (message.isConversationMessage) {
                        BulletinMessageRow(
                            message = message,
                            canManage = session.canManageBoard,
                            activeDownloadId = attachmentDownload?.attachmentId,
                            selectionMode = messageSelectionMode,
                            selected = message.id in selectedMessageIds,
                            onToggleSelect = {
                                onSelectedMessageIdsChange(
                                    if (message.id in selectedMessageIds) {
                                        selectedMessageIds - message.id
                                    } else {
                                        selectedMessageIds + message.id
                                    }
                                )
                            },
                            onEdit = {
                                editingMessage = message
                                editingText = message.content
                                editingAuthor = message.authorLabel
                            },
                            onDelete = { deletingMessage = message },
                            onAttachmentClick = { attachment -> requestAttachmentPreview(attachment) }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }

        HorizontalDivider()
        if (messageSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.family_board_select_messages_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onMessageSelectionModeChange(false) }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    if (session.canManageBoard) {
                        Button(
                            shape = RoundedCornerShape(0.dp),
                            enabled = canMergeSelectedMessages && !transferInProgress,
                            onClick = {
                                pendingMergeMessages = selectedConversationMessages
                            }
                        ) {
                            Text(stringResource(R.string.family_board_merge_selected_action))
                        }
                    }
                    Button(
                        shape = RoundedCornerShape(0.dp),
                        enabled = selectedMessageIds.isNotEmpty() && !transferInProgress,
                        onClick = {
                            onForwardMessages(selectedConversationMessages)
                            onMessageSelectionModeChange(false)
                        }
                    ) {
                        Text(stringResource(R.string.bulletin_forward_messages_action))
                    }
                }
            }
            if (session.canManageBoard && selectedConversationMessages.size > 1 && !canMergeSelectedMessages) {
                Text(
                    stringResource(R.string.family_board_merge_author_mismatch),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            HorizontalDivider()
        }
        if (uploadInProgress) {
            AttachmentUploadProgressBar(
                progress = attachmentUpload!!,
                onCancel = { manager.cancelAttachmentUpload() }
            )
        }
        if (downloadInProgress) {
            AttachmentDownloadProgressBar(
                progress = attachmentDownload!!,
                onCancel = { manager.cancelAttachmentDownload() }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!messageSelectionMode) {
            IconButton(
                onClick = {
                    if (rootUri.isNullOrBlank()) {
                        Toast.makeText(context, context.getString(R.string.attachment_upload_no_root), Toast.LENGTH_SHORT).show()
                    } else {
                        showAttachmentPickDialog = true
                    }
                },
                enabled = !transferInProgress,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Attachment,
                    contentDescription = stringResource(R.string.family_board_add_attachment)
                )
            }
            BulletinMentionTextField(
                value = draftMessage,
                onValueChange = { draftMessage = it },
                composeTargets = composeTargets,
                modifier = Modifier.weight(1f),
                enabled = !transferInProgress,
                maxLines = 3,
                placeholder = { Text(stringResource(R.string.family_board_message_placeholder)) }
            )
            IconButton(
                onClick = {
                    val raw = draftMessage.text.trim()
                    if (raw.isNotEmpty()) {
                        onPost(raw)
                        draftMessage = TextFieldValue("")
                    }
                },
                enabled = draftMessage.text.trim().isNotEmpty() && !transferInProgress,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.family_board_send))
            }
            }
        }
    }
}

@Composable
private fun BulletinAiStatusRow(message: BulletinMessage) {
    val timeLabel = remember(message.updatedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(message.updatedAt))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FamilyAvatarImage(
            avatarId = message.authorAvatarId,
            modifier = Modifier.size(18.dp),
            contentDescription = null
        )
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.family_board_ai_status_title, message.authorLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                message.aiStatusDetail.ifBlank { message.content },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun BulletinMessageRow(
    message: BulletinMessage,
    canManage: Boolean,
    activeDownloadId: String?,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAttachmentClick: (BulletinAttachmentRef) -> Unit
) {
    val context = LocalContext.current
    val timeLabel = remember(message.updatedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(message.updatedAt))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = selectionMode) { onToggleSelect() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            Spacer(Modifier.width(4.dp))
        }
        FamilyAvatarImage(
            avatarId = message.authorAvatarId,
            modifier = Modifier.size(22.dp),
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${message.authorLabel} · $timeLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (message.isEdited) {
                Text(
                    text = stringResource(R.string.family_board_message_edited, message.editorLabel.orEmpty()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (message.content.isNotBlank()) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (message.attachments.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                message.attachments.forEach { attachment ->
                    val sizeLabel = formatAttachmentSize(attachment)
                    val prefix = if (attachment.kind == BulletinAttachmentKind.DIRECTORY) "📁 " else "📄 "
                    val statusSuffix = if (activeDownloadId == attachment.id) {
                        " · ${context.getString(R.string.attachment_downloading)}"
                    } else {
                        ""
                    }
                    Text(
                        "$prefix${attachment.name}$sizeLabel$statusSuffix",
                        modifier = Modifier
                            .clickable { onAttachmentClick(attachment) }
                            .padding(vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        if (canManage && !selectionMode) {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.family_board_edit_title),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun BulletinAttachmentPreviewDialog(
    attachment: BulletinAttachmentRef,
    meta: org.json.JSONObject?,
    loading: Boolean,
    error: String?,
    canOpenInternal: Boolean,
    onDismiss: () -> Unit,
    onOpenInternal: () -> Unit,
    onOpenTextHex: (() -> Unit)? = null,
    onDownload: () -> Unit
) {
    val kindLabel = when (attachment.kind) {
        BulletinAttachmentKind.FILE -> stringResource(R.string.attachment_preview_kind_file)
        BulletinAttachmentKind.DIRECTORY -> stringResource(R.string.attachment_preview_kind_directory)
    }
    val sizeBytes = when (attachment.kind) {
        BulletinAttachmentKind.FILE -> meta?.optLong("size")?.takeIf { it > 0 } ?: attachment.size
        BulletinAttachmentKind.DIRECTORY -> meta?.optLong("total_size")?.takeIf { it > 0 }
            ?: attachment.totalSize
    }
    val fileEntries = remember(meta) {
        meta?.optJSONArray("files")?.let { files ->
            buildList {
                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    add(file.optString("path") to file.optLong("size"))
                }
            }
        }.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attachment_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    attachment.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sizeBytes > 0) {
                    Text(
                        stringResource(R.string.attachment_preview_size, formatByteCount(sizeBytes)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                when {
                    loading -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.attachment_preview_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    error != null -> {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    fileEntries.isNotEmpty() -> {
                        Text(
                            stringResource(R.string.attachment_preview_file_count, fileEntries.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.attachment_preview_files_header),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            fileEntries.forEach { (path, size) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        path,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (size > 0) {
                                        Text(
                                            formatByteCount(size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canOpenInternal) {
                    TextButton(onClick = onOpenInternal, enabled = !loading) {
                        Text(stringResource(R.string.attachment_preview_open_internal))
                    }
                }
                onOpenTextHex?.let { handler ->
                    TextButton(onClick = handler, enabled = !loading) {
                        Text(stringResource(R.string.attachment_preview_open_text_hex))
                    }
                }
                TextButton(onClick = onDownload, enabled = !loading) {
                    Text(stringResource(R.string.attachment_preview_download))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private fun isMarkdownName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".md") || lower.endsWith(".rst") || lower.endsWith(".txt")
}

private fun isHtmlName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".html") || lower.endsWith(".htm")
}

private fun isPdfName(name: String): Boolean {
    return name.endsWith(".pdf", ignoreCase = true)
}

private fun isImageName(name: String): Boolean {
    return name.endsWith(".png", ignoreCase = true) ||
        name.endsWith(".jpg", ignoreCase = true) ||
        name.endsWith(".jpeg", ignoreCase = true) ||
        name.endsWith(".gif", ignoreCase = true) ||
        name.endsWith(".webp", ignoreCase = true) ||
        name.endsWith(".bmp", ignoreCase = true)
}

private fun openAttachmentByType(
    fileUri: String,
    fileName: String,
    isEncrypted: Boolean = false,
    attachmentViewerFile: (Triple<String, String, Boolean>?) -> Unit,
    attachmentMarkdownFile: (Triple<String, String, Boolean>?) -> Unit,
    attachmentHtmlLocation: (HtmlViewerLocation?) -> Unit,
    attachmentPdfFile: (Pair<String, String>?) -> Unit,
    attachmentImageFile: (Pair<String, String>?) -> Unit
) {
    attachmentViewerFile(null)
    attachmentMarkdownFile(null)
    attachmentHtmlLocation(null)
    attachmentPdfFile(null)
    attachmentImageFile(null)
    when {
        isMarkdownName(fileName) -> attachmentMarkdownFile(Triple(fileUri, fileName, isEncrypted))
        isHtmlName(fileName) -> attachmentHtmlLocation(
            HtmlViewerLocation(
                initialUrl = fileUri,
                title = fileName,
                localFileUri = fileUri
            )
        )
        isPdfName(fileName) -> attachmentPdfFile(fileUri to fileName)
        isImageName(fileName) -> attachmentImageFile(fileUri to fileName)
        else -> attachmentViewerFile(Triple(fileUri, fileName, isEncrypted))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentImageViewerScreen(
    fileUri: String,
    fileName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(fileUri) {
        loading = true
        loadError = null
        bitmap = null
        scope.launch(Dispatchers.IO) {
            val uri = Uri.parse(fileUri)
            val loaded = runCatching {
                if (uri.scheme == "file") {
                    val path = uri.path ?: error("missing file path")
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(path, bounds)
                    val sampleSize = calculateImageSampleSize(bounds.outWidth, bounds.outHeight, 2048)
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    android.graphics.BitmapFactory.decodeFile(path, options)
                } else {
                    val cr = context.contentResolver
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    cr.openInputStreamSafe(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
                    } ?: error("open failed")
                    val sampleSize = calculateImageSampleSize(bounds.outWidth, bounds.outHeight, 2048)
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    cr.openInputStreamSafe(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream, null, options)
                    } ?: error("open failed")
                }
            }
            withContext(Dispatchers.Main.immediate) {
                bitmap = loaded.getOrNull()
                loadError = loaded.exceptionOrNull()?.message ?: if (loaded.isSuccess) null else context.getString(R.string.viewer_open_failed)
                loading = false
            }
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> CircularProgressIndicator()
                loadError != null -> Text(loadError!!, color = MaterialTheme.colorScheme.error)
                bitmap != null -> Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

private fun calculateImageSampleSize(width: Int, height: Int, maxPx: Int): Int {
    var sampleSize = 1
    while (width / sampleSize > maxPx || height / sampleSize > maxPx) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private fun formatByteCount(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    return when {
        bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
        bytesPerSecond < 1024 * 1024 -> "${"%.1f".format(bytesPerSecond / 1024.0)} KB/s"
        bytesPerSecond < 1024 * 1024 * 1024 -> "${"%.1f".format(bytesPerSecond / (1024.0 * 1024))} MB/s"
        else -> "${"%.2f".format(bytesPerSecond / (1024.0 * 1024 * 1024))} GB/s"
    }
}

@Composable
private fun AttachmentDownloadProgressBar(
    progress: BulletinAttachmentDownloadProgress,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val fraction = (progress.downloadedBytes.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            context.getString(R.string.attachment_download_progress, progress.itemName),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${formatByteCount(progress.downloadedBytes)} / ${formatByteCount(progress.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.attachment_download_speed, formatSpeed(progress.speedBytesPerSecond)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.attachment_download_cancel))
            }
        }
    }
}

@Composable
private fun AttachmentUploadProgressBar(
    progress: BulletinAttachmentUploadProgress,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val fraction = (progress.uploadedBytes.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f)
    val statusText = when (progress.phase) {
        BulletinAttachmentUploadPhase.UPLOADING -> context.getString(
            R.string.attachment_upload_progress,
            progress.itemName
        )
        BulletinAttachmentUploadPhase.POSTING -> context.getString(R.string.attachment_upload_posting)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${formatByteCount(progress.uploadedBytes)} / ${formatByteCount(progress.totalBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.attachment_upload_cancel))
            }
        }
    }
}

private fun formatAttachmentSize(attachment: BulletinAttachmentRef): String {
    val bytes = when (attachment.kind) {
        BulletinAttachmentKind.FILE -> attachment.size
        BulletinAttachmentKind.DIRECTORY -> attachment.totalSize
    }
    if (bytes <= 0) return ""
    return when {
        bytes < 1024 -> " ($bytes B)"
        bytes < 1024 * 1024 -> " (${"%.1f".format(bytes / 1024.0)} KB)"
        else -> " (${"%.1f".format(bytes / (1024.0 * 1024))} MB)"
    }
}

@Composable
private fun FamilyWorkLogPane(
    logLines: List<String>,
    expandedContentModifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.family_network_work_log),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    val content = logLines.joinToString("\n")
                    if (content.isNotEmpty()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.family_network_log_clip_label),
                                content
                            )
                        )
                    }
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.family_network_copy_log), modifier = Modifier.size(18.dp))
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    if (expanded) {
        Box(
            modifier = expandedContentModifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(6.dp)
            ) {
                logLines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilyNetworkServiceCard(
    service: FamilyDiscoveredService,
    onClick: (() -> Unit)?
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = MaterialTheme.shapes.medium
        )
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = if (service.isSelf) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        service.displayHostName.ifBlank { stringResource(R.string.family_network_unknown_service_name) },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!service.isSelf && service.requiresPasswordAuth) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = stringResource(R.string.family_network_requires_password),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    stringResource(R.string.family_network_service_host_port, service.host.ifBlank { "?" }, service.port),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (service.isSelf) {
                    stringResource(R.string.family_network_open_self_board)
                } else {
                    stringResource(R.string.family_network_open_board)
                },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
