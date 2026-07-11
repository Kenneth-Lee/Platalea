package com.kenny.localmanager.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.family.BulletinBoardInfo
import com.kenny.localmanager.family.BulletinBoardOpenSession
import com.kenny.localmanager.family.BulletinForwardTarget
import com.kenny.localmanager.family.BulletinMessage
import com.kenny.localmanager.family.FamilyDiscoveredService
import com.kenny.localmanager.family.FamilyNetworkManager
import com.kenny.localmanager.file.DocumentFileModel
import kotlinx.coroutines.launch

sealed class BulletinForwardPayload {
    data class Files(
        val items: List<DocumentFileModel>,
        val textContent: String = ""
    ) : BulletinForwardPayload()

    data class Messages(
        val sourceSession: BulletinBoardOpenSession,
        val messages: List<BulletinMessage>
    ) : BulletinForwardPayload()
}

@Composable
fun BulletinForwardTargetFlow(
    manager: FamilyNetworkManager,
    payload: BulletinForwardPayload,
    localServiceEnabled: Boolean,
    keepEphemeralSession: Boolean,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val networkState by manager.state.collectAsState()

    var phase by remember { mutableStateOf(ForwardPhase.DEVICE) }
    var selectedService by remember { mutableStateOf<FamilyDiscoveredService?>(null) }
    var accessPassword by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordInProgress by remember { mutableStateOf(false) }
    var boards by remember { mutableStateOf<List<BulletinBoardInfo>>(emptyList()) }
    var boardsLoading by remember { mutableStateOf(false) }
    var boardsError by remember { mutableStateOf<String?>(null) }
    var forwardInProgress by remember { mutableStateOf(false) }
    var optionalText by remember { mutableStateOf(
        (payload as? BulletinForwardPayload.Files)?.textContent.orEmpty()
    ) }

    DisposableEffect(keepEphemeralSession) {
        if (keepEphemeralSession) {
            manager.beginEphemeralSession()
        }
        onDispose {
            if (keepEphemeralSession) {
                manager.endEphemeralSession()
            }
        }
    }

    val devices = remember(networkState.discoveredServices, localServiceEnabled) {
        buildList {
            if (localServiceEnabled) {
                networkState.discoveredServices.firstOrNull { it.isSelf }?.let { add(it) }
                    ?: manager.resolveLocalSelfService()?.let { add(it) }
            }
            networkState.discoveredServices.filter { !it.isSelf }.forEach { add(it) }
        }
    }

    fun finishForward(result: Result<*>) {
        forwardInProgress = false
        result.onSuccess {
            Toast.makeText(
                context,
                context.getString(R.string.bulletin_forward_success),
                Toast.LENGTH_SHORT
            ).show()
            onComplete()
        }.onFailure { error ->
            Toast.makeText(
                context,
                context.getString(
                    R.string.bulletin_forward_failed,
                    error.message ?: error.javaClass.simpleName
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun forwardToBoard(board: BulletinBoardInfo) {
        val service = selectedService ?: return
        val target = BulletinForwardTarget(
            service = service,
            boardId = board.id,
            boardName = board.name,
            accessPassword = accessPassword
        )
        forwardInProgress = true
        when (payload) {
            is BulletinForwardPayload.Files -> {
                manager.forwardFilesAsDirectoryMessage(
                    target = target,
                    items = payload.items,
                    textContent = optionalText
                ) { result -> finishForward(result) }
            }
            is BulletinForwardPayload.Messages -> {
                manager.forwardMessagesToBoard(
                    sourceSession = payload.sourceSession,
                    target = target,
                    messages = payload.messages
                ) { result -> finishForward(result) }
            }
        }
    }

    fun loadBoards(service: FamilyDiscoveredService, password: String?) {
        scope.launch {
            boardsLoading = true
            boardsError = null
            val result = manager.fetchBoardList(service, password)
            boardsLoading = false
            result.onSuccess { list -> boards = list.boards }
                .onFailure { err ->
                    boardsError = err.message ?: err.javaClass.simpleName
                }
        }
    }

    when (phase) {
        ForwardPhase.DEVICE -> {
            AlertDialog(
                onDismissRequest = { if (!forwardInProgress) onDismiss() },
                title = { Text(stringResource(R.string.bulletin_forward_pick_device)) },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { manager.refresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
                            }
                        }
                        if (devices.isEmpty()) {
                            Text(
                                if (localServiceEnabled) {
                                    stringResource(R.string.bulletin_forward_no_devices)
                                } else {
                                    stringResource(R.string.bulletin_forward_no_remote_devices)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(devices, key = { it.deviceKey }) { service ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !forwardInProgress) {
                                                selectedService = service
                                                if (service.isSelf) {
                                                    accessPassword = null
                                                    phase = ForwardPhase.BOARD
                                                    loadBoards(service, null)
                                                } else if (manager.hasRememberedBoardAccessPassword(service.deviceKey)) {
                                                    accessPassword = manager.getRememberedBoardAccessPassword(service.deviceKey)
                                                    phase = ForwardPhase.BOARD
                                                    loadBoards(service, accessPassword)
                                                } else if (service.requiresPasswordAuth) {
                                                    phase = ForwardPhase.PASSWORD
                                                } else {
                                                    scope.launch {
                                                        passwordInProgress = true
                                                        val probe = manager.probeBoardAccess(service, accessPassword = null)
                                                        passwordInProgress = false
                                                        if (probe.isSuccess) {
                                                            accessPassword = null
                                                            manager.rememberBoardAccessPassword(service.deviceKey, null)
                                                            phase = ForwardPhase.BOARD
                                                            loadBoards(service, null)
                                                        } else {
                                                            phase = ForwardPhase.PASSWORD
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (service.isSelf) {
                                                stringResource(R.string.bulletin_forward_local_device)
                                            } else {
                                                service.displayHostName.ifBlank { service.serviceName }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "${service.host}:${service.port}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        enabled = !forwardInProgress,
                        onClick = onDismiss
                    ) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
        ForwardPhase.PASSWORD -> {
            val service = selectedService
            if (service == null) {
                phase = ForwardPhase.DEVICE
                return
            }
            AlertDialog(
                onDismissRequest = {
                    if (!passwordInProgress) {
                        phase = ForwardPhase.DEVICE
                        passwordInput = ""
                        passwordError = null
                    }
                },
                title = { Text(stringResource(R.string.family_board_password_title)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                passwordError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.family_board_password_label)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !passwordInProgress
                        )
                        passwordError?.let { error ->
                            Spacer(Modifier.height(8.dp))
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !passwordInProgress,
                        onClick = {
                            scope.launch {
                                passwordInProgress = true
                                val password = passwordInput.trim().ifEmpty { null }
                                val result = manager.probeBoardAccess(service, accessPassword = password)
                                passwordInProgress = false
                                if (result.isSuccess) {
                                    accessPassword = password
                                    manager.rememberBoardAccessPassword(service.deviceKey, password)
                                    passwordInput = ""
                                    passwordError = null
                                    phase = ForwardPhase.BOARD
                                    loadBoards(service, password)
                                } else {
                                    val error = result.exceptionOrNull()
                                    val detail = error?.message ?: error?.javaClass?.simpleName ?: "unknown probe failure"
                                    passwordError = if (error != null && manager.isLikelyAuthFailure(error)) {
                                        context.getString(R.string.family_board_password_wrong_detail, detail)
                                    } else {
                                        context.getString(R.string.family_board_password_probe_failed, detail)
                                    }
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.common_ok)) }
                },
                dismissButton = {
                    TextButton(
                        enabled = !passwordInProgress,
                        onClick = {
                            phase = ForwardPhase.DEVICE
                            passwordInput = ""
                            passwordError = null
                        }
                    ) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
        ForwardPhase.BOARD -> {
            val service = selectedService
            if (service == null) {
                phase = ForwardPhase.DEVICE
                return
            }
            val isFilePayload = payload is BulletinForwardPayload.Files
            AlertDialog(
                onDismissRequest = { if (!forwardInProgress && !boardsLoading) onDismiss() },
                title = { Text(stringResource(R.string.family_board_picker_title)) },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        if (isFilePayload) {
                            OutlinedTextField(
                                value = optionalText,
                                onValueChange = { optionalText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.bulletin_forward_optional_message)) },
                                minLines = 1,
                                maxLines = 3,
                                enabled = !forwardInProgress
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        when {
                            boardsLoading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
                            }
                            boardsError != null -> {
                                Text(
                                    boardsError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            boards.isEmpty() -> {
                                Text(
                                    stringResource(R.string.family_board_picker_empty),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(boards, key = { it.id }) { board ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !forwardInProgress) {
                                                    forwardToBoard(board)
                                                }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(board.name, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    stringResource(
                                                        R.string.family_board_picker_item_meta,
                                                        board.messageCount
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (forwardInProgress) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.bulletin_forward_in_progress))
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        enabled = !forwardInProgress,
                        onClick = onDismiss
                    ) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
    }
}

private enum class ForwardPhase {
    DEVICE,
    PASSWORD,
    BOARD
}
