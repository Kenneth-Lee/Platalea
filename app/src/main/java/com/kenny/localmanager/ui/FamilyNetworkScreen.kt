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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.kenny.localmanager.family.BulletinBoardOpenSession
import com.kenny.localmanager.family.BulletinMessage
import com.kenny.localmanager.family.FamilyDiscoveredService
import com.kenny.localmanager.family.FamilyNetworkManager
import com.kenny.localmanager.family.FamilyNetworkState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyNetworkScreen(
    manager: FamilyNetworkManager,
    networkPassword: String?,
    localServiceEnabled: Boolean,
    familyNetworkUserName: String = "",
    familyNetworkHostPassword: String = "",
    timeoutMinutes: Int = 0,
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
    val boardAccessCache = remember { mutableStateMapOf<String, String?>() }

    LaunchedEffect(networkPassword, localServiceEnabled, familyNetworkUserName, familyNetworkHostPassword) {
        manager.configure(
            networkPassword,
            localServiceEnabled,
            familyNetworkUserName.ifBlank { null },
            familyNetworkHostPassword.ifBlank { null }
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
        while (remainingMinutes != null && remainingMinutes!! > 0) {
            delay(60_000)
            remainingMinutes = remainingMinutes?.minus(1)
        }
        if (remainingMinutes == 0) {
            onDismiss()
        }
    }

    DisposableEffect(manager, activity, networkPassword, localServiceEnabled, familyNetworkUserName, familyNetworkHostPassword) {
        manager.configure(
            networkPassword,
            localServiceEnabled,
            familyNetworkUserName.ifBlank { null },
            familyNetworkHostPassword.ifBlank { null }
        )
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        manager.start()
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            manager.closeBulletinBoard()
            manager.stop()
        }
    }

    fun openBoardWithAccess(service: FamilyDiscoveredService, accessPassword: String?) {
        boardAccessCache[service.deviceKey] = accessPassword
        manager.openBulletinBoard(service, accessPassword = accessPassword)
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
        boardAccessCache[service.deviceKey]?.let { cached ->
            scope.launch {
                boardPasswordInProgress = true
                val result = manager.probeBoardAccess(service, accessPassword = cached)
                boardPasswordInProgress = false
                result.onSuccess { openBoardWithAccess(service, cached) }
                    .onFailure {
                        boardAccessCache.remove(service.deviceKey)
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
                        IconButton(onClick = {
                            manager.refreshOpenBoard(showLoadingIndicator = boardSession.messages.isEmpty())
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.family_network_refresh))
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
                onRefresh = { manager.refresh() },
                onOpenBoard = { service -> requestOpenBoard(service) }
            )
        } else {
            BulletinBoardPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                session = boardSession,
                onRefresh = { manager.refreshOpenBoard() },
                onPost = { manager.postBoardMessage(it) },
                onUpdate = { id, content -> manager.updateBoardMessage(id, content) },
                onDelete = { manager.deleteBoardMessage(it) }
            )
        }
    }
}

@Composable
private fun FamilyPeerListPane(
    modifier: Modifier,
    state: FamilyNetworkState,
    remainingMinutes: Int?,
    localServicePasswordSet: Boolean,
    onRefresh: () -> Unit,
    onOpenBoard: (FamilyDiscoveredService) -> Unit
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
                onRefresh = onRefresh
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
            items(state.discoveredServices, key = { it.deviceKey }) { service ->
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
    onRefresh: () -> Unit
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
                if (state.serviceName.isNotBlank()) {
                    Text(stringResource(R.string.family_network_service_name, state.serviceName), style = MaterialTheme.typography.bodySmall)
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
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.family_network_refresh))
                }
            }
        }
    }
}

@Composable
private fun BulletinBoardPage(
    modifier: Modifier,
    session: BulletinBoardOpenSession,
    onRefresh: () -> Unit,
    onPost: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var draftMessage by remember(session.service.deviceKey) { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<BulletinMessage?>(null) }
    var editingText by remember { mutableStateOf("") }
    var deletingMessage by remember { mutableStateOf<BulletinMessage?>(null) }
    val listState = rememberLazyListState()
    var previousMessageCount by remember(session.service.deviceKey) { mutableIntStateOf(0) }

    LaunchedEffect(session.service.deviceKey, session.boardId) {
        while (true) {
            delay(3000)
            onRefresh()
        }
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
                            session.canManageBoard -> stringResource(R.string.family_board_role_remote_host)
                            else -> stringResource(R.string.family_board_role_guest)
                        }
                    )
                    append(" · ")
                    append(session.service.host)
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
        } else if (session.messages.isEmpty()) {
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
                items(
                    items = session.messages,
                    key = { it.id },
                    contentType = { "bulletin_message" }
                ) { message ->
                    BulletinMessageRow(
                        message = message,
                        canManage = session.canManageBoard,
                        onEdit = {
                            editingMessage = message
                            editingText = message.content
                        },
                        onDelete = { deletingMessage = message }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextField(
                value = draftMessage,
                onValueChange = { draftMessage = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.family_board_message_placeholder)) },
                singleLine = false,
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            IconButton(
                onClick = {
                    onPost(draftMessage)
                    draftMessage = ""
                },
                enabled = draftMessage.trim().isNotEmpty(),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.family_board_send))
            }
        }
    }
}

@Composable
private fun BulletinMessageRow(
    message: BulletinMessage,
    canManage: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val timeLabel = remember(message.updatedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(message.updatedAt))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${message.authorLabel} · $timeLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                message.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (canManage) {
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
                        service.serviceName.ifBlank { stringResource(R.string.family_network_unknown_service_name) },
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
