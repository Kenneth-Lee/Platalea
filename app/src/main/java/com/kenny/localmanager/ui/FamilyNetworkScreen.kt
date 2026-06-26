package com.kenny.localmanager.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
    timeoutMinutes: Int = 0,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by manager.state.collectAsState()
    var remainingMinutes by remember(timeoutMinutes) {
        mutableStateOf(if (timeoutMinutes > 0) timeoutMinutes else null)
    }
    val boardSession = state.openBoardSession

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

    DisposableEffect(manager, activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        manager.start()
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            manager.closeBulletinBoard()
            manager.stop()
        }
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
                        IconButton(onClick = { manager.refreshOpenBoard() }) {
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
                    .padding(16.dp)
                    .navigationBarsPadding(),
                state = state,
                remainingMinutes = remainingMinutes,
                onRefresh = { manager.refresh() },
                onOpenBoard = { service -> manager.openBulletinBoard(service) }
            )
        } else {
            BulletinBoardPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
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
    onRefresh: () -> Unit,
    onOpenBoard: (FamilyDiscoveredService) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null)
                            Text(
                                stringResource(R.string.family_network_local_service),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            if (state.isRunning) stringResource(R.string.family_network_running) else stringResource(R.string.family_network_stopped),
                            color = if (state.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Text(stringResource(R.string.family_network_bulletin_hint))
                    Text(stringResource(R.string.family_network_service_type, state.serviceType))
                    if (state.serviceName.isNotBlank()) {
                        Text(stringResource(R.string.family_network_service_name, state.serviceName))
                    }
                    if (state.port > 0) {
                        Text(
                            stringResource(R.string.family_network_port, state.port),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        state.localIp?.let { stringResource(R.string.family_network_local_ip, it) }
                            ?: stringResource(R.string.family_network_local_ip_unknown),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        if (state.isRegistered) {
                            stringResource(R.string.family_network_registered)
                        } else {
                            stringResource(R.string.family_network_not_registered)
                        }
                    )
                    Text(
                        if (state.isDiscovering) {
                            stringResource(R.string.family_network_discovery_active)
                        } else {
                            stringResource(R.string.family_network_discovery_idle)
                        }
                    )
                    state.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                        Text(
                            stringResource(R.string.family_network_last_error, error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    remainingMinutes?.let { mins ->
                        Text(
                            stringResource(R.string.network_service_auto_close, mins),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.network_service_keep_screen_on),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.family_network_refresh))
                    }
                }
            }
        }

        item {
            Text(
                stringResource(R.string.family_network_discovered_services_count, state.discoveredServices.size),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (state.discoveredServices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.family_network_empty_hint),
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

    LaunchedEffect(session.service.deviceKey, session.boardId) {
        while (true) {
            delay(3000)
            onRefresh()
        }
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    session.service.serviceName.ifBlank { stringResource(R.string.family_network_unknown_service_name) },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.family_network_service_host_port, session.service.host, session.service.port),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (session.isHost) {
                        stringResource(R.string.family_board_role_host)
                    } else {
                        stringResource(R.string.family_board_role_guest)
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    stringResource(R.string.family_board_revision, session.revision),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                session.lastError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (session.loading && session.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(session.messages, key = { it.id }) { message ->
                    BulletinMessageCard(
                        message = message,
                        isHost = session.isHost,
                        onEdit = {
                            editingMessage = message
                            editingText = message.content
                        },
                        onDelete = { deletingMessage = message }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = draftMessage,
                    onValueChange = { draftMessage = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.family_board_message_label)) },
                    placeholder = { Text(stringResource(R.string.family_board_message_placeholder)) },
                    minLines = 2,
                    maxLines = 5
                )
                Button(
                    onClick = {
                        onPost(draftMessage)
                        draftMessage = ""
                    },
                    enabled = draftMessage.trim().isNotEmpty(),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun BulletinMessageCard(
    message: BulletinMessage,
    isHost: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val timeLabel = remember(message.updatedAt) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(message.updatedAt))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(message.authorLabel, style = MaterialTheme.typography.labelMedium)
                    Text(timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isHost) {
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.family_board_edit_title))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                        }
                    }
                }
            }
            Text(message.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FamilyWorkLogPane(logLines: List<String>) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.family_network_work_log),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.family_network_copy_log))
        }
    }
    Spacer(Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(8.dp)
        ) {
            logLines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
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
            },
            contentColor = if (service.isSelf) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    ) {
        val primaryTextColor = if (service.isSelf) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        val secondaryTextColor = if (service.isSelf) {
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        service.serviceName.ifBlank { stringResource(R.string.family_network_unknown_service_name) },
                        style = MaterialTheme.typography.titleSmall,
                        color = primaryTextColor
                    )
                    Text(
                        stringResource(R.string.family_network_service_host_port, service.host.ifBlank { "?" }, service.port),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = primaryTextColor
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (service.isSelf) {
                        stringResource(R.string.family_network_open_self_board)
                    } else {
                        stringResource(R.string.family_network_open_board)
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                stringResource(R.string.family_network_service_type_line, service.serviceType),
                color = secondaryTextColor
            )
        }
    }
}
