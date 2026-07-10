package com.kenny.localmanager.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
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
import com.kenny.localmanager.family.FamilyNetworkState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class PendingBoardAccess(
    val service: FamilyDiscoveredService,
    val accessPassword: String?,
    val canManageBoards: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyNetworkScreen(
    manager: FamilyNetworkManager,
    networkPassword: String?,
    localServiceEnabled: Boolean,
    familyNetworkUserName: String = "",
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
    var pendingBoardService by remember { mutableStateOf<FamilyDiscoveredService?>(null) }
    var showBoardPasswordDialog by remember { mutableStateOf(false) }
    var boardPasswordInput by remember { mutableStateOf("") }
    var boardPasswordError by remember { mutableStateOf<String?>(null) }
    var boardPasswordInProgress by remember { mutableStateOf(false) }
    var pendingBoardAccess by remember { mutableStateOf<PendingBoardAccess?>(null) }
    var boardPickerLoading by remember { mutableStateOf(false) }
    var showClearPasswordsDialog by remember { mutableStateOf(false) }
    var showBoardMenu by remember { mutableStateOf(false) }
    var showDeleteBoardDialog by remember { mutableStateOf(false) }
    var deleteBoardInProgress by remember { mutableStateOf(false) }
    var showRemoteShutdownDialog by remember { mutableStateOf(false) }
    var remoteShutdownInProgress by remember { mutableStateOf(false) }
    var showRenameBoardDialog by remember { mutableStateOf(false) }
    var renameBoardName by remember { mutableStateOf("") }
    var renameBoardInProgress by remember { mutableStateOf(false) }
    var renameBoardError by remember { mutableStateOf<String?>(null) }
    var renameExistingNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var messageForwardSelectionMode by remember { mutableStateOf(false) }
    var selectedForwardMessageIds by remember { mutableStateOf(setOf<String>()) }

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

    LaunchedEffect(networkPassword, localServiceEnabled, familyNetworkUserName, familyNetworkHostName) {
        manager.configure(
            networkPassword,
            localServiceEnabled,
            familyNetworkUserName.ifBlank { null },
            familyNetworkHostName.ifBlank { null }
        )
    }

    BackHandler(enabled = boardSession != null) {
        manager.closeBulletinBoard()
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

    DisposableEffect(manager, activity, networkPassword, localServiceEnabled, familyNetworkUserName, familyNetworkHostName) {
        manager.configure(
            networkPassword,
            localServiceEnabled,
            familyNetworkUserName.ifBlank { null },
            familyNetworkHostName.ifBlank { null }
        )
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var bulletinForwardPayload by remember { mutableStateOf<BulletinForwardPayload?>(null) }

    fun presentBoardPicker(service: FamilyDiscoveredService, accessPassword: String?) {
        scope.launch {
            boardPickerLoading = true
            val canManage = manager.remoteCanManageBoard(service, accessPassword)
            manager.rememberBoardAccessPassword(service.deviceKey, accessPassword)
            pendingBoardAccess = PendingBoardAccess(service, accessPassword, canManage)
            boardPickerLoading = false
        }
    }

    fun openBoardWithAccess(service: FamilyDiscoveredService, accessPassword: String?) {
        presentBoardPicker(service, accessPassword)
    }

    fun showPasswordDialog(service: FamilyDiscoveredService, wrongPassword: Boolean) {
        pendingBoardService = service
        boardPasswordInput = ""
        boardPasswordError = if (wrongPassword) {
            context.getString(R.string.family_board_password_wrong)
        } else {
            null
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
        if (manager.hasRememberedBoardAccessPassword(service.deviceKey)) {
            val cached = manager.getRememberedBoardAccessPassword(service.deviceKey)
            scope.launch {
                boardPasswordInProgress = true
                val result = manager.probeBoardAccess(service, accessPassword = cached)
                boardPasswordInProgress = false
                result.onSuccess { openBoardWithAccess(service, cached) }
                    .onFailure {
                        manager.forgetBoardAccessPassword(service.deviceKey)
                        showPasswordDialog(service, wrongPassword = true)
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
                manager.rememberBoardAccessPassword(service.deviceKey, null)
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
                                manager.rememberBoardAccessPassword(service.deviceKey, password)
                                openBoardWithAccess(service, password)
                            } else {
                                boardPasswordError = context.getString(R.string.family_board_password_wrong)
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

    if (showClearPasswordsDialog) {
        AlertDialog(
            onDismissRequest = { showClearPasswordsDialog = false },
            title = { Text(stringResource(R.string.family_board_clear_passwords_title)) },
            text = { Text(stringResource(R.string.family_board_clear_passwords_confirm)) },
            confirmButton = {
                Button(onClick = {
                    val count = manager.clearAllBoardAccessPasswords()
                    showClearPasswordsDialog = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.family_board_clear_passwords_done, count),
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearPasswordsDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (boardPickerLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.family_board_picker_title)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.family_board_picker_loading))
                }
            },
            confirmButton = {}
        )
    }

    pendingBoardAccess?.let { access ->
        BoardPickerDialog(
            service = access.service,
            accessPassword = access.accessPassword,
            canManageBoards = access.canManageBoards,
            rootUri = rootUri,
            hideDotFiles = hideDotFiles,
            manager = manager,
            onDismiss = { pendingBoardAccess = null },
            onSelectBoard = { board ->
                pendingBoardAccess = null
                manager.openBulletinBoard(
                    service = access.service,
                    boardId = board.id,
                    boardName = board.name,
                    boardRoleIds = board.roleIds,
                    accessPassword = access.accessPassword
                )
            }
        )
    }

    if (showRenameBoardDialog && boardSession != null) {
        val session = boardSession
        val trimmedRenameName = renameBoardName.trim()
        val renameNameTaken = trimmedRenameName.isNotEmpty() &&
            trimmedRenameName != session.boardName.trim() &&
            trimmedRenameName in renameExistingNames
        AlertDialog(
            onDismissRequest = {
                if (!renameBoardInProgress) {
                    showRenameBoardDialog = false
                    renameBoardError = null
                }
            },
            title = { Text(stringResource(R.string.family_board_rename_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameBoardName,
                        onValueChange = {
                            renameBoardName = it
                            renameBoardError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.family_board_create_name_label)) },
                        singleLine = true,
                        enabled = !renameBoardInProgress,
                        isError = renameNameTaken || renameBoardError != null
                    )
                    if (renameNameTaken) {
                        Text(
                            stringResource(R.string.family_board_name_duplicate, trimmedRenameName),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    renameBoardError?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !renameBoardInProgress &&
                        trimmedRenameName.isNotEmpty() &&
                        !renameNameTaken &&
                        trimmedRenameName != session.boardName.trim(),
                    onClick = {
                        renameBoardInProgress = true
                        manager.renameBoardEntry(
                            service = session.service,
                            accessPassword = session.accessPassword,
                            canManage = session.canManageBoard,
                            boardId = session.boardId,
                            newName = trimmedRenameName
                        ) { result ->
                            renameBoardInProgress = false
                            result.onSuccess {
                                showRenameBoardDialog = false
                                renameBoardError = null
                            }.onFailure { error ->
                                renameBoardError = error.message ?: error.javaClass.simpleName
                            }
                        }
                    }
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !renameBoardInProgress,
                    onClick = {
                        showRenameBoardDialog = false
                        renameBoardError = null
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

    if (showRemoteShutdownDialog && boardSession != null) {
        val session = boardSession
        AlertDialog(
            onDismissRequest = { if (!remoteShutdownInProgress) showRemoteShutdownDialog = false },
            title = { Text(context.getString(R.string.family_board_remote_shutdown_title)) },
            text = { Text(context.getString(R.string.family_board_remote_shutdown_confirm, session.service.displayHostName)) },
            confirmButton = {
                Button(
                    enabled = !remoteShutdownInProgress,
                    onClick = {
                        remoteShutdownInProgress = true
                        manager.requestRemotePowerShutdown(session.service, session.accessPassword) { result ->
                            remoteShutdownInProgress = false
                            result.onSuccess {
                                showRemoteShutdownDialog = false
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.family_board_remote_shutdown_requested, session.service.displayHostName),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !remoteShutdownInProgress,
                    onClick = { showRemoteShutdownDialog = false }
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
                        } else {
                            stringResource(R.string.family_network_title)
                        }
                    )
                },
                navigationIcon = if (boardSession != null) {
                    {
                        IconButton(onClick = { manager.closeBulletinBoard() }) {
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
                        if (hasRoot || canManageBoard) {
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
                                                manager.exportBoardpack(
                                                    service = session.service,
                                                    board = BulletinBoardInfo(
                                                        id = session.boardId,
                                                        name = session.boardName,
                                                        revision = session.revision,
                                                        messageCount = session.messages.count { it.isConversationMessage }
                                                    ),
                                                    accessPassword = session.accessPassword
                                                ) { packResult ->
                                                    packResult.onSuccess { bytes ->
                                                        manager.saveBoardpackToRoot(exportRoot, session.boardName, bytes) { saveResult ->
                                                            saveResult.onSuccess { path ->
                                                                Toast.makeText(
                                                                    context,
                                                                    context.getString(R.string.family_board_pack_export_success, path),
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }.onFailure { error ->
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
                                                    }.onFailure { error ->
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
                                        )
                                    }
                                    if (canManageBoard) {
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
                                    if (canManageBoard &&
                                        session.boardId == BulletinBoardDefaults.DEFAULT_BOARD_ID &&
                                        session.service.supportsPowerShutdown
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.family_board_remote_shutdown_title),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                showBoardMenu = false
                                                showRemoteShutdownDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (state.rememberedBoardAccessCount > 0) {
                            IconButton(onClick = { showClearPasswordsDialog = true }) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = stringResource(R.string.family_board_clear_passwords)
                                )
                            }
                        }
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
        if (boardSession == null) {
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
                onUpdate = { id, content -> manager.updateBoardMessage(id, content) },
                onDelete = { manager.deleteBoardMessage(it) },
                onForwardMessages = { messages ->
                    bulletinForwardPayload = BulletinForwardPayload.Messages(boardSession, messages)
                },
                messageSelectionMode = messageForwardSelectionMode,
                onMessageSelectionModeChange = { active ->
                    messageForwardSelectionMode = active
                    if (!active) selectedForwardMessageIds = emptySet()
                },
                selectedMessageIds = selectedForwardMessageIds,
                onSelectedMessageIdsChange = { selectedForwardMessageIds = it }
            )
        }
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
private fun BoardPickerDialog(
    service: FamilyDiscoveredService,
    accessPassword: String?,
    canManageBoards: Boolean,
    rootUri: String?,
    hideDotFiles: Boolean,
    manager: FamilyNetworkManager,
    onDismiss: () -> Unit,
    onSelectBoard: (BulletinBoardInfo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var boards by remember { mutableStateOf<List<BulletinBoardInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newBoardName by remember { mutableStateOf("") }
    var actionInProgress by remember { mutableStateOf(false) }
    var showBoardpackPickDialog by remember { mutableStateOf(false) }
    var pendingImportFile by remember { mutableStateOf<DocumentFileModel?>(null) }
    var pendingImportName by remember { mutableStateOf("") }
    var importPackNameLoading by remember { mutableStateOf(false) }
    var importNameConflict by remember { mutableStateOf(false) }
    var importPackNameError by remember { mutableStateOf<String?>(null) }
    var importPackSourceName by remember { mutableStateOf("") }
    var createNameError by remember { mutableStateOf<String?>(null) }
    var createSelectedRoleIds by remember { mutableStateOf(setOf<String>()) }

    val targetDeviceLabel = service.displayHostName.ifBlank { service.serviceName }
    val existingBoardNames = remember(boards) { boards.map { it.name.trim() }.toSet() }
    val availableRoles = remember(boards, service.isSelf) {
        collectKnownRoleOptions(manager, service.isSelf, boards)
    }

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

    LaunchedEffect(service.deviceKey, accessPassword) {
        reloadBoards()
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
                pendingImportName = ""
                importNameConflict = false
                importPackNameError = null
                importPackSourceName = ""
            }
        )
    }

    LaunchedEffect(pendingImportFile?.uri) {
        val file = pendingImportFile ?: return@LaunchedEffect
        importPackNameLoading = true
        importPackNameError = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = BulletinBoardPack.readFromDocumentFile(context, file).getOrThrow()
                BulletinBoardPack.peekBoardName(context, bytes)
            }
        }
        importPackNameLoading = false
        result.onSuccess { packName ->
            importPackSourceName = packName
            importNameConflict = packName in existingBoardNames
            pendingImportName = if (importNameConflict) {
                BulletinBoardPack.suggestUniqueBoardName(context, packName, existingBoardNames)
            } else {
                packName
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
            onDismissRequest = {
                if (!actionInProgress) pendingImportFile = null
            },
            title = { Text(stringResource(R.string.family_board_import_pack_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.family_board_import_pack_confirm_message,
                            file.name,
                            targetDeviceLabel
                        )
                    )
                    if (importPackNameLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.family_board_picker_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
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
                                stringResource(
                                    R.string.family_board_import_name_conflict,
                                    importPackSourceName
                                ),
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
                        importPackNameError?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = canConfirmImport,
                    onClick = {
                        actionInProgress = true
                        manager.importBoardpackFromFile(
                            file = file,
                            service = service,
                            accessPassword = accessPassword,
                            importName = trimmedImportName
                        ) { result ->
                            actionInProgress = false
                            pendingImportFile = null
                            result.onSuccess { board ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.family_board_pack_import_success, board.name),
                                    Toast.LENGTH_SHORT
                                ).show()
                                reloadBoards()
                            }.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.family_board_pack_failed,
                                        err.message ?: err.javaClass.simpleName
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
                    enabled = !actionInProgress,
                    onClick = { pendingImportFile = null }
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showCreateDialog) {
        val trimmedCreateName = newBoardName.trim()
        val createNameTaken = trimmedCreateName.isNotEmpty() && trimmedCreateName in existingBoardNames
        AlertDialog(
            onDismissRequest = {
                if (!actionInProgress) {
                    showCreateDialog = false
                    newBoardName = ""
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
                        isError = createNameTaken || createNameError != null
                    )
                    if (createNameTaken) {
                        Text(
                            stringResource(R.string.family_board_name_duplicate, trimmedCreateName),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    createNameError?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (canManageBoards) {
                        BoardRoleAccessSelector(
                            availableRoles = availableRoles,
                            selectedRoleIds = createSelectedRoleIds,
                            onSelectionChange = { createSelectedRoleIds = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !actionInProgress && trimmedCreateName.isNotEmpty() && !createNameTaken,
                    onClick = {
                        actionInProgress = true
                        manager.createBoardEntry(
                            service = service,
                            accessPassword = accessPassword,
                            canManage = canManageBoards,
                            name = newBoardName,
                            roleIds = createSelectedRoleIds.toList()
                        ) { result ->
                            actionInProgress = false
                            result.onSuccess {
                                showCreateDialog = false
                                newBoardName = ""
                                createSelectedRoleIds = emptySet()
                                createNameError = null
                                reloadBoards()
                            }.onFailure { error ->
                                createNameError = error.message ?: error.javaClass.simpleName
                            }
                        }
                    }
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !actionInProgress,
                    onClick = {
                        showCreateDialog = false
                        newBoardName = ""
                        createNameError = null
                    }
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!actionInProgress) onDismiss() },
        title = { Text(stringResource(R.string.family_board_picker_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                    error != null -> {
                        Text(
                            error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    boards.isEmpty() -> {
                        Text(
                            stringResource(R.string.family_board_picker_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(
                                items = boards,
                                key = { index, board -> "${board.id}#$index" }
                            ) { _index, board ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !actionInProgress) {
                                            onSelectBoard(board)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            board.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            stringResource(
                                                R.string.family_board_picker_item_meta,
                                                board.messageCount
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
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
                if (canManageBoards) {
                    TextButton(
                        enabled = !actionInProgress,
                        onClick = {
                            if (rootUri.isNullOrBlank()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.family_board_import_pack_no_root),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                showBoardpackPickDialog = true
                            }
                        }
                    ) { Text(stringResource(R.string.family_board_import_pack)) }
                    Button(
                        enabled = !actionInProgress,
                        onClick = {
                            showCreateDialog = true
                            newBoardName = ""
                            createSelectedRoleIds = emptySet()
                        }
                    ) { Text(stringResource(R.string.family_board_create)) }
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !actionInProgress,
                onClick = onDismiss
            ) { Text(stringResource(R.string.common_cancel)) }
        }
    )
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
            FamilyWorkLogPane(state.logLines)
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
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onForwardMessages: (List<BulletinMessage>) -> Unit,
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
    val composeTargets = remember(session.agents, session.participants, session.commands, commandDescriptions) {
        BulletinBoardMention.buildComposeTargets(
            session.agents,
            session.participants,
            session.commands,
            commandDescriptions
        )
    }
    var editingMessage by remember { mutableStateOf<BulletinMessage?>(null) }
    var editingText by remember { mutableStateOf("") }
    var deletingMessage by remember { mutableStateOf<BulletinMessage?>(null) }
    var showAttachmentPickDialog by remember { mutableStateOf(false) }
    var pendingDownloadConflict by remember { mutableStateOf<BulletinAttachmentRef?>(null) }
    var previewAttachment by remember { mutableStateOf<BulletinAttachmentRef?>(null) }
    var previewMeta by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(session.messages.size) {
        if (session.messages.isNotEmpty() && session.messages.size >= previousMessageCount) {
            listState.animateScrollToItem(session.messages.lastIndex)
        }
        previousMessageCount = session.messages.size
    }

    if (editingMessage != null) {
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text(stringResource(R.string.family_board_edit_title)) },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = editingMessage ?: return@Button
                    onUpdate(target.id, editingText)
                    editingMessage = null
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
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
            onDismiss = {
                previewAttachment = null
                previewMeta = null
                previewError = null
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
                    stringResource(R.string.bulletin_forward_select_messages),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onMessageSelectionModeChange(false) }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        enabled = selectedMessageIds.isNotEmpty() && !transferInProgress,
                        onClick = {
                            val selected = conversationMessages.filter { it.id in selectedMessageIds }
                            onForwardMessages(selected)
                            onMessageSelectionModeChange(false)
                        }
                    ) {
                        Text(
                            stringResource(
                                R.string.bulletin_forward_messages_action,
                                selectedMessageIds.size
                            )
                        )
                    }
                }
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
                    onPost(draftMessage.text)
                    draftMessage = TextFieldValue("")
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${message.authorLabel} · $timeLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    onDismiss: () -> Unit,
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
            TextButton(onClick = onDownload, enabled = !loading) {
                Text(stringResource(R.string.attachment_preview_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
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
            Text(
                "${formatByteCount(progress.downloadedBytes)} / ${formatByteCount(progress.totalBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun FamilyWorkLogPane(logLines: List<String>) {
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
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
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
