package com.kenny.localmanager.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.kenny.localmanager.R
import com.kenny.localmanager.ftp.FtpServerManager
import com.kenny.localmanager.util.getLocalIpAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun toLocalShareLogLine(line: String): String {
    return line
        .replace("FTP 服务器", "本地共享服务")
        .replace("FTP server", "Local share service")
        .replace("FTP", "本地共享")
        .replace("ftp", "local-share")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalShareScreen(
    manager: FtpServerManager,
    treeRootUri: String,
    currentDirUri: String,
    port: Int,
    password: String?,
    timeoutMinutes: Int = 0,
    showBackButton: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    var logLines by remember { mutableStateOf(emptyList<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    var localIp by remember { mutableStateOf<String?>(null) }
    var webUrl by remember { mutableStateOf<String?>(null) }
    var connectionCount by remember { mutableStateOf(0) }
    var remainingMinutes by remember(timeoutMinutes) { mutableStateOf(if (timeoutMinutes > 0) timeoutMinutes else null) }

    suspend fun startServer(ip: String?) {
        val ok = withContext(Dispatchers.IO) {
            try {
                manager.start(port, treeRootUri, currentDirUri, password, ip) { }
            } catch (e: Throwable) {
                Log.e("LocalShareScreen", "manager.start failed", e)
                false
            }
        }
        logLines = manager.getLogLines()
        isRunning = manager.isRunning()
        connectionCount = manager.getConnectionCount()
        webUrl = manager.getWebUiUrl(ip)
        if (!ok) logLines = manager.getLogLines()
    }

    DisposableEffect(Unit) {
        try {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Throwable) {
            Log.e("LocalShareScreen", "DisposableEffect setup", e)
        }
        onDispose {
            try {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                manager.stop()
                manager.clearLog()
            } catch (e: Throwable) {
                Log.e("LocalShareScreen", "DisposableEffect onDispose", e)
            }
        }
    }

    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        if (owner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onDismiss()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        manager.clearLog()
        try {
            val ip = withContext(Dispatchers.IO) { getLocalIpAddress() }
            localIp = ip
            webUrl = manager.getWebUiUrl(ip)
            startServer(ip)
        } catch (e: Throwable) {
            Log.e("LocalShareScreen", "LaunchedEffect init failed", e)
            logLines = listOf(context.getString(R.string.local_share_start_exception, e.message ?: e.javaClass.simpleName))
        }
    }

    LaunchedEffect(Unit) {
        try {
            while (true) {
                delay(500)
                logLines = manager.getLogLines()
                isRunning = manager.isRunning()
                connectionCount = manager.getConnectionCount()
                webUrl = manager.getWebUiUrl(localIp)
            }
        } catch (e: Throwable) {
            Log.e("LocalShareScreen", "polling failed", e)
        }
    }

    LaunchedEffect(timeoutMinutes) {
        if (timeoutMinutes > 0) {
            var remaining = timeoutMinutes
            remainingMinutes = remaining
            while (remaining > 0) {
                delay(60_000)
                remaining--
                remainingMinutes = if (remaining > 0) remaining else null
                if (remaining <= 0) onDismiss()
            }
        }
    }

    BackHandler { onDismiss() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.local_share_title)) },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_close))
                        }
                    }
                } else {
                    { }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            val ip = localIp ?: stringResource(R.string.local_share_ip_fetching)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.local_share_server_address), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(ip, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(
                        if (isRunning) stringResource(R.string.local_share_running) else stringResource(R.string.local_share_stopped),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.local_share_connection_count, connectionCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        manager.stop()
                        isRunning = manager.isRunning()
                        connectionCount = manager.getConnectionCount()
                        logLines = manager.getLogLines()
                    },
                    enabled = isRunning
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.local_share_stop_action))
                }
                Button(
                    onClick = { scope.launch { startServer(localIp) } },
                    enabled = !isRunning
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.local_share_start_action))
                }
            }
            remainingMinutes?.let { mins ->
                Text(
                    stringResource(R.string.network_service_auto_close, mins),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.height(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.network_service_keep_screen_on), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.local_share_connection_help), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.local_share_credentials_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$ip:$port",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.local_share_client_examples),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.local_share_example_command, ip, port),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (webUrl != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.local_share_web_ui_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    webUrl ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.local_share_web_ui_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.local_share_port, port), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.local_share_work_log), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(
                    onClick = {
                        val text = logLines.joinToString("\n")
                        if (text.isNotEmpty()) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.local_share_log_clip_label), text))
                        }
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.local_share_copy_log))
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(8.dp)
                ) {
                    logLines.forEach { line ->
                        Text(
                            toLocalShareLogLine(line),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
