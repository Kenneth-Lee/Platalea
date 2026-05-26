package com.kenny.localmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import com.kenny.localmanager.R
import com.kenny.localmanager.data.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONFIG_MIN_PREVIEW_BYTES = 1024
private const val CONFIG_MAX_PREVIEW_BYTES = 10 * 1024 * 1024

data class ConfigTabRouteState(
    val filterVisible: Boolean,
    val onFilterVisibleChange: (Boolean) -> Unit,
    val hideDotFiles: Boolean,
    val onHideDotFilesChange: (Boolean) -> Unit,
    val startupDecryptKey: Boolean,
    val onStartupDecryptKeyChange: (Boolean) -> Unit,
    val viewerPreviewBytes: Int,
    val onViewerPreviewBytesChange: (Int) -> Unit,
    val ftpPassword: String,
    val onFtpPasswordChange: (String) -> Unit,
    val networkServiceTimeoutMinutes: Int,
    val onNetworkServiceTimeoutMinutesChange: (Int) -> Unit,
    val onOpenGitConfig: () -> Unit,
    val onManageKeys: () -> Unit,
    val onOpenCacheManagement: () -> Unit,
    val onExportConfig: () -> Unit,
    val onCreatePlayerShortcut: () -> Unit,
    val onCreateQuickNoteShortcut: () -> Unit
)

@Composable
fun ConfigTabRoute(state: ConfigTabRouteState) {
    ConfigPanel(
        filterVisible = state.filterVisible,
        onFilterVisibleChange = state.onFilterVisibleChange,
        hideDotFiles = state.hideDotFiles,
        onHideDotFilesChange = state.onHideDotFilesChange,
        startupDecryptKey = state.startupDecryptKey,
        onStartupDecryptKeyChange = state.onStartupDecryptKeyChange,
        viewerPreviewBytes = state.viewerPreviewBytes,
        onViewerPreviewBytesChange = state.onViewerPreviewBytesChange,
        ftpPassword = state.ftpPassword,
        onFtpPasswordChange = state.onFtpPasswordChange,
        networkServiceTimeoutMinutes = state.networkServiceTimeoutMinutes,
        onNetworkServiceTimeoutMinutesChange = state.onNetworkServiceTimeoutMinutesChange,
        onOpenGitConfig = state.onOpenGitConfig,
        onManageKeys = state.onManageKeys,
        onOpenCacheManagement = state.onOpenCacheManagement,
        onExportConfig = state.onExportConfig,
        onCreatePlayerShortcut = state.onCreatePlayerShortcut,
        onCreateQuickNoteShortcut = state.onCreateQuickNoteShortcut,
        showCloseButton = false,
        onClose = null
    )
}

@Composable
private fun ConfigPanel(
    filterVisible: Boolean,
    onFilterVisibleChange: (Boolean) -> Unit,
    hideDotFiles: Boolean,
    onHideDotFilesChange: (Boolean) -> Unit,
    startupDecryptKey: Boolean,
    onStartupDecryptKeyChange: (Boolean) -> Unit,
    viewerPreviewBytes: Int,
    onViewerPreviewBytesChange: (Int) -> Unit,
    ftpPassword: String,
    onFtpPasswordChange: (String) -> Unit,
    networkServiceTimeoutMinutes: Int,
    onNetworkServiceTimeoutMinutesChange: (Int) -> Unit,
    onOpenGitConfig: () -> Unit,
    onManageKeys: () -> Unit,
    onOpenCacheManagement: () -> Unit,
    onExportConfig: () -> Unit,
    onCreatePlayerShortcut: () -> Unit,
    onCreateQuickNoteShortcut: () -> Unit,
    showCloseButton: Boolean,
    onClose: (() -> Unit)?
) {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }
    val scope = rememberCoroutineScope()
    var localViewerPreviewBytes by remember { mutableStateOf(viewerPreviewBytes.toString()) }
    var localFtpPassword by remember { mutableStateOf(ftpPassword) }
    var localNetworkServiceTimeoutMinutes by remember { mutableStateOf(networkServiceTimeoutMinutes.toString()) }
    var showFtpConfigDialog by remember { mutableStateOf(false) }
    var showEpubTtsConfigDialog by remember { mutableStateOf(false) }
    var showEpubTtsEngineDialog by remember { mutableStateOf(false) }
    var showEpubTtsVoiceDialog by remember { mutableStateOf(false) }
    var epubTtsEngines by remember { mutableStateOf<List<EpubOfflineTtsEngine>>(emptyList()) }
    var epubTtsLoading by remember { mutableStateOf(false) }
    val selectedEpubTtsEnginePackage by prefs.epubTtsEnginePackage.collectAsState(initial = null)
    val selectedEpubTtsVoiceName by prefs.epubTtsVoiceName.collectAsState(initial = null)
    val epubTtsSpeedPercent by prefs.epubTtsSpeedPercent.collectAsState(initial = 100)
    val epubTtsAutoNextChapter by prefs.epubTtsAutoNextChapter.collectAsState(initial = true)
    val preferredTtsLocale = remember { preferredEpubTtsLocale(null) }
    val effectiveEpubTtsEngine = remember(epubTtsEngines, selectedEpubTtsEnginePackage) {
        epubTtsEngines.firstOrNull { it.packageName == selectedEpubTtsEnginePackage }
            ?: epubTtsEngines.firstOrNull()
    }
    val effectiveEpubTtsVoice = remember(effectiveEpubTtsEngine, selectedEpubTtsVoiceName) {
        selectedEpubTtsVoiceName?.let { selectedVoice ->
            effectiveEpubTtsEngine?.offlineVoices?.firstOrNull { it.name == selectedVoice }
        }
    }

    fun refreshEpubTtsEngines() {
        scope.launch {
            epubTtsLoading = true
            epubTtsEngines = withContext(Dispatchers.IO) {
                loadOfflineTtsEngines(context, preferredTtsLocale)
            }
            epubTtsLoading = false
        }
    }

    val ftpSummary = remember(localFtpPassword, localNetworkServiceTimeoutMinutes, context) {
        val passwordState = if (localFtpPassword.isBlank()) {
            context.getString(R.string.config_ftp_password_unset)
        } else {
            context.getString(R.string.config_ftp_password_set)
        }
        val timeoutValue = localNetworkServiceTimeoutMinutes.filter { it.isDigit() }.toIntOrNull() ?: networkServiceTimeoutMinutes
        context.getString(R.string.config_ftp_summary, passwordState, timeoutValue)
    }
    val epubTtsSummary = remember(
        effectiveEpubTtsEngine,
        selectedEpubTtsVoiceName,
        effectiveEpubTtsVoice,
        epubTtsSpeedPercent,
        epubTtsAutoNextChapter,
        context
    ) {
        val voiceLabel = when {
            effectiveEpubTtsEngine == null -> context.getString(R.string.config_epub_tts_unset)
            selectedEpubTtsVoiceName == null -> context.getString(R.string.epub_tts_voice_default_choice)
            else -> effectiveEpubTtsVoice?.label ?: context.getString(R.string.config_epub_tts_unset)
        }
        context.getString(
            R.string.config_epub_tts_summary,
            effectiveEpubTtsEngine?.label ?: context.getString(R.string.config_epub_tts_unset),
            voiceLabel,
            epubTtsSpeedPercent,
            if (epubTtsAutoNextChapter) context.getString(R.string.common_enabled) else context.getString(R.string.common_disabled)
        )
    }

    LaunchedEffect(Unit) {
        refreshEpubTtsEngines()
    }

    if (showFtpConfigDialog) {
        AlertDialog(
            onDismissRequest = { showFtpConfigDialog = false },
            title = { Text(context.getString(R.string.config_ftp_section)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = localFtpPassword,
                        onValueChange = { s ->
                            localFtpPassword = s
                            onFtpPasswordChange(s)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(context.getString(R.string.config_ftp_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = {
                            Text(
                                context.getString(R.string.config_ftp_password_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = localNetworkServiceTimeoutMinutes,
                        onValueChange = { s ->
                            localNetworkServiceTimeoutMinutes = s
                            s.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 1440)?.let { onNetworkServiceTimeoutMinutesChange(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(context.getString(R.string.config_ftp_timeout_minutes)) },
                        singleLine = true,
                        placeholder = { Text(context.getString(R.string.config_ftp_timeout_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        supportingText = {
                            Text(
                                context.getString(R.string.config_ftp_timeout_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFtpConfigDialog = false }) {
                    Text(context.getString(R.string.common_close))
                }
            }
        )
    }

    if (showEpubTtsConfigDialog) {
        AlertDialog(
            onDismissRequest = { showEpubTtsConfigDialog = false },
            title = { Text(context.getString(R.string.config_epub_tts_section)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            refreshEpubTtsEngines()
                            showEpubTtsEngineDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(context.getString(R.string.config_epub_tts_engine))
                            Text(
                                text = effectiveEpubTtsEngine?.label ?: context.getString(R.string.config_epub_tts_unset),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            refreshEpubTtsEngines()
                            showEpubTtsVoiceDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(context.getString(R.string.config_epub_tts_voice))
                            Text(
                                text = when {
                                    effectiveEpubTtsEngine == null -> context.getString(R.string.config_epub_tts_unset)
                                    selectedEpubTtsVoiceName == null -> context.getString(R.string.epub_tts_voice_default_choice)
                                    else -> effectiveEpubTtsVoice?.label ?: context.getString(R.string.config_epub_tts_unset)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text(context.getString(R.string.config_epub_tts_speed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Slider(
                            value = epubTtsSpeedPercent.toFloat(),
                            onValueChange = { value ->
                                scope.launch {
                                    prefs.setEpubTtsSpeedPercent(value.toInt().coerceIn(50, 300))
                                }
                            },
                            valueRange = 50f..300f,
                            steps = 24,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = context.getString(R.string.config_epub_tts_speed_value, epubTtsSpeedPercent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(context.getString(R.string.config_epub_tts_auto_next), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(context.getString(R.string.config_epub_tts_auto_next_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = epubTtsAutoNextChapter,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    prefs.setEpubTtsAutoNextChapter(enabled)
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEpubTtsConfigDialog = false }) {
                    Text(context.getString(R.string.common_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { refreshEpubTtsEngines() }) {
                    Text(context.getString(R.string.common_refresh))
                }
            }
        )
    }

    if (showEpubTtsEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEpubTtsEngineDialog = false },
            title = { Text(context.getString(R.string.epub_tts_engine_title)) },
            text = {
                when {
                    epubTtsLoading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(context.getString(R.string.epub_tts_engine_loading))
                        }
                    }
                    epubTtsEngines.isEmpty() -> {
                        SelectionContainer {
                            Text(context.getString(R.string.epub_tts_engine_empty))
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(epubTtsEngines) { engine ->
                                val selected = selectedEpubTtsEnginePackage == engine.packageName
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                prefs.setEpubTtsEnginePackage(engine.packageName)
                                                prefs.setEpubTtsVoiceName(null)
                                            }.onSuccess {
                                                Toast.makeText(context, context.getString(R.string.epub_tts_selected_engine, engine.label), Toast.LENGTH_SHORT).show()
                                                showEpubTtsEngineDialog = false
                                            }.onFailure { error ->
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.epub_tts_engine_save_failed, error.message ?: error.javaClass.simpleName),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = selected, onClick = null)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(engine.label, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                text = context.getString(R.string.epub_tts_engine_voice_count, engine.offlineVoiceCount),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (engine.supportsPreferredLocale) {
                                                Text(
                                                    text = context.getString(R.string.epub_tts_engine_locale_hint),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (engine.sampleLocaleTags.isNotEmpty()) {
                                                Text(
                                                    text = engine.sampleLocaleTags.joinToString(" · "),
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
                }
            },
            confirmButton = {
                TextButton(onClick = { showEpubTtsEngineDialog = false }) {
                    Text(context.getString(R.string.common_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { refreshEpubTtsEngines() }) {
                    Text(context.getString(R.string.common_refresh))
                }
            }
        )
    }

    if (showEpubTtsVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showEpubTtsVoiceDialog = false },
            title = { Text(context.getString(R.string.epub_tts_voice_title)) },
            text = {
                when {
                    epubTtsLoading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(context.getString(R.string.epub_tts_voice_loading))
                        }
                    }
                    effectiveEpubTtsEngine == null -> {
                        SelectionContainer {
                            Text(context.getString(R.string.epub_tts_engine_empty))
                        }
                    }
                    effectiveEpubTtsEngine.offlineVoices.isEmpty() -> {
                        SelectionContainer {
                            Text(context.getString(R.string.epub_tts_voice_empty))
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            item {
                                val selected = selectedEpubTtsVoiceName == null
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                prefs.setEpubTtsVoiceName(null)
                                            }.onSuccess {
                                                Toast.makeText(context, context.getString(R.string.epub_tts_selected_voice, context.getString(R.string.epub_tts_voice_default_choice)), Toast.LENGTH_SHORT).show()
                                                showEpubTtsVoiceDialog = false
                                            }.onFailure { error ->
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.epub_tts_voice_save_failed, error.message ?: error.javaClass.simpleName),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = selected, onClick = null)
                                        Spacer(Modifier.width(12.dp))
                                        Text(context.getString(R.string.epub_tts_voice_default_choice), modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            items(effectiveEpubTtsEngine.offlineVoices) { voice ->
                                val selected = selectedEpubTtsVoiceName == voice.name
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                prefs.setEpubTtsVoiceName(voice.name)
                                            }.onSuccess {
                                                Toast.makeText(context, context.getString(R.string.epub_tts_selected_voice, voice.label), Toast.LENGTH_SHORT).show()
                                                showEpubTtsVoiceDialog = false
                                            }.onFailure { error ->
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.epub_tts_voice_save_failed, error.message ?: error.javaClass.simpleName),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = selected, onClick = null)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(voice.label, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                text = context.getString(R.string.epub_tts_voice_locale, voice.localeTag),
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
                TextButton(onClick = { showEpubTtsVoiceDialog = false }) {
                    Text(context.getString(R.string.common_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { refreshEpubTtsEngines() }) {
                    Text(context.getString(R.string.common_refresh))
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(context.getString(R.string.config_show_filter), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Switch(checked = filterVisible, onCheckedChange = onFilterVisibleChange)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(context.getString(R.string.config_hide_dot_files), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Switch(checked = hideDotFiles, onCheckedChange = onHideDotFilesChange)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(context.getString(R.string.config_startup_decrypt_key), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(context.getString(R.string.config_startup_decrypt_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = startupDecryptKey, onCheckedChange = onStartupDecryptKeyChange)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(context.getString(R.string.config_viewer_preview_bytes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = localViewerPreviewBytes,
                onValueChange = { s ->
                    localViewerPreviewBytes = s
                    s.filter { it.isDigit() }.toIntOrNull()?.coerceIn(CONFIG_MIN_PREVIEW_BYTES, CONFIG_MAX_PREVIEW_BYTES)?.let { onViewerPreviewBytesChange(it) }
                },
                modifier = Modifier.width(120.dp),
                singleLine = true
            )
        }
        Text(
            context.getString(R.string.config_viewer_preview_range, CONFIG_MIN_PREVIEW_BYTES, CONFIG_MAX_PREVIEW_BYTES / (1024 * 1024)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                showFtpConfigDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(context.getString(R.string.config_ftp_section))
                Text(
                    text = ftpSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                refreshEpubTtsEngines()
                showEpubTtsConfigDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(context.getString(R.string.config_epub_tts_section))
                Text(
                    text = epubTtsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenGitConfig, modifier = Modifier.fillMaxWidth()) { Text(context.getString(R.string.config_git)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onManageKeys, modifier = Modifier.fillMaxWidth()) { Text(context.getString(R.string.config_gpg_keys)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenCacheManagement, modifier = Modifier.fillMaxWidth()) { Text(context.getString(R.string.config_cache)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onExportConfig, modifier = Modifier.fillMaxWidth()) { Text(context.getString(R.string.config_export)) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onCreatePlayerShortcut,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Text(context.getString(R.string.config_create_player_shortcut))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onCreateQuickNoteShortcut,
            modifier = Modifier.fillMaxWidth()
        ) { Text(context.getString(R.string.config_create_quick_note_shortcut)) }
        Spacer(Modifier.height(24.dp))
        if (showCloseButton && onClose != null) {
            TextButton(onClick = onClose) { Text(context.getString(R.string.common_close)) }
        }
    }
}

@Composable
fun ConfigDialog(
    onDismiss: () -> Unit,
    filterVisible: Boolean,
    onFilterVisibleChange: (Boolean) -> Unit,
    hideDotFiles: Boolean,
    onHideDotFilesChange: (Boolean) -> Unit,
    startupDecryptKey: Boolean,
    onStartupDecryptKeyChange: (Boolean) -> Unit,
    viewerPreviewBytes: Int,
    onViewerPreviewBytesChange: (Int) -> Unit,
    ftpPassword: String,
    onFtpPasswordChange: (String) -> Unit,
    networkServiceTimeoutMinutes: Int,
    onNetworkServiceTimeoutMinutesChange: (Int) -> Unit,
    onOpenGitConfig: () -> Unit,
    onManageKeys: () -> Unit,
    onOpenCacheManagement: () -> Unit,
    onExportConfig: () -> Unit,
    onCreatePlayerShortcut: () -> Unit,
    onCreateQuickNoteShortcut: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            ConfigPanel(
                filterVisible = filterVisible,
                onFilterVisibleChange = onFilterVisibleChange,
                hideDotFiles = hideDotFiles,
                onHideDotFilesChange = onHideDotFilesChange,
                startupDecryptKey = startupDecryptKey,
                onStartupDecryptKeyChange = onStartupDecryptKeyChange,
                viewerPreviewBytes = viewerPreviewBytes,
                onViewerPreviewBytesChange = onViewerPreviewBytesChange,
                ftpPassword = ftpPassword,
                onFtpPasswordChange = onFtpPasswordChange,
                networkServiceTimeoutMinutes = networkServiceTimeoutMinutes,
                onNetworkServiceTimeoutMinutesChange = onNetworkServiceTimeoutMinutesChange,
                onOpenGitConfig = onOpenGitConfig,
                onManageKeys = onManageKeys,
                onOpenCacheManagement = onOpenCacheManagement,
                onExportConfig = onExportConfig,
                onCreatePlayerShortcut = onCreatePlayerShortcut,
                onCreateQuickNoteShortcut = onCreateQuickNoteShortcut,
                showCloseButton = true,
                onClose = onDismiss
            )
        }
    }
}