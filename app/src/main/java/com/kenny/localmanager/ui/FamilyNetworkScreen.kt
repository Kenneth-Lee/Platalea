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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.family.FamilyChatMessage
import com.kenny.localmanager.family.FamilyDiscoveredService
import com.kenny.localmanager.family.FamilyNetworkManager
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
    var selectedConversationKey by remember { mutableStateOf<String?>(null) }
    var draftMessage by remember(selectedConversationKey) { mutableStateOf("") }
    var remainingMinutes by remember(timeoutMinutes) {
        mutableStateOf(if (timeoutMinutes > 0) timeoutMinutes else null)
    }
    val remoteServices = state.discoveredServices.filterNot { it.isSelf }
    val selectedService = remoteServices.firstOrNull { it.conversationKey == selectedConversationKey }

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
        selectedConversationKey = null
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        manager.start()
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            manager.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedService != null) selectedService.serviceName else stringResource(R.string.family_network_title)
                    )
                },
                navigationIcon = if (selectedService != null) {
                    {
                        IconButton(onClick = { selectedConversationKey = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                } else {
                    { }
                },
                actions = {
                    IconButton(onClick = { manager.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.family_network_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (selectedService == null) {
            FamilyPeerListPane(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .navigationBarsPadding(),
                state = state,
                remainingMinutes = remainingMinutes,
                remoteServices = remoteServices,
                onRefresh = { manager.refresh() },
                onOpenService = { service -> selectedConversationKey = service.conversationKey }
            )
        } else {
            FamilyConversationPage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                service = selectedService,
                messages = manager.getMessages(selectedService.conversationKey),
                draftMessage = draftMessage,
                onDraftChange = { draftMessage = it },
                onSend = {
                    manager.sendMessage(selectedService, draftMessage)
                    draftMessage = ""
                }
            )
        }
    }
}

@Composable
private fun FamilyPeerListPane(
    modifier: Modifier,
    state: com.kenny.localmanager.family.FamilyNetworkState,
    remainingMinutes: Int?,
    remoteServices: List<FamilyDiscoveredService>,
    onRefresh: () -> Unit,
    onOpenService: (FamilyDiscoveredService) -> Unit
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
                stringResource(R.string.family_network_discovered_services_count, remoteServices.size),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (remoteServices.isEmpty()) {
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
            items(remoteServices, key = { it.conversationKey }) { service ->
                FamilyNetworkServiceCard(
                    service = service,
                    onClick = { onOpenService(service) }
                )
            }
        }

        item {
            FamilyWorkLogPane(state.logLines)
        }
    }
}

@Composable
private fun FamilyConversationPage(
    modifier: Modifier,
    service: FamilyDiscoveredService,
    messages: List<FamilyChatMessage>,
    draftMessage: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.family_network_target_peer),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    service.serviceName.ifBlank { stringResource(R.string.family_network_unknown_service_name) },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.family_network_service_host_port, service.host.ifBlank { "?" }, service.port),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    stringResource(R.string.family_network_service_type_line, service.serviceType),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FamilyConversationPane(
            modifier = Modifier.weight(1f),
            service = service,
            messages = messages,
            draftMessage = draftMessage,
            onDraftChange = onDraftChange,
            onSend = onSend
        )
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
                        stringResource(R.string.family_network_self_badge)
                    } else {
                        stringResource(R.string.family_network_open_conversation)
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                stringResource(R.string.family_network_service_type_line, service.serviceType),
                color = secondaryTextColor
            )
            if (service.attributes.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.family_network_service_attr_line,
                        service.attributes.entries.joinToString(", ") { (key, value) -> "$key=$value" }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }
            if (!service.isSelf) {
                Text(
                    stringResource(R.string.family_network_service_card_hint),
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FamilyConversationPane(
    modifier: Modifier,
    service: FamilyDiscoveredService,
    messages: List<FamilyChatMessage>,
    draftMessage: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val latestOutgoingFailure = messages.lastOrNull { !it.incoming && it.deliveryError != null }?.deliveryError
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.family_network_chat_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        latestOutgoingFailure?.let { error ->
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    stringResource(R.string.family_network_send_failed, error),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.family_network_no_messages),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    FamilyChatMessageBubble(message)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
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
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.family_network_message_label)) },
                    placeholder = { Text(stringResource(R.string.family_network_message_placeholder)) },
                    minLines = 2,
                    maxLines = 5
                )
                Button(
                    onClick = onSend,
                    enabled = draftMessage.trim().isNotEmpty(),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun FamilyChatMessageBubble(message: FamilyChatMessage) {
    val containerColor = when {
        message.deliveryError != null -> MaterialTheme.colorScheme.errorContainer
        message.incoming -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (message.incoming) {
                    stringResource(R.string.family_network_incoming_from, message.senderName)
                } else {
                    stringResource(R.string.family_network_outgoing_to, message.senderName)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(message.content, style = MaterialTheme.typography.bodyMedium)
            message.deliveryError?.let { error ->
                Text(
                    stringResource(R.string.family_network_send_failed, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}