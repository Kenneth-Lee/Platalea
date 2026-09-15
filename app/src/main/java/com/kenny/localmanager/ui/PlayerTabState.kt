package com.kenny.localmanager.ui

import android.content.Context
import android.util.Log
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kenny.localmanager.R
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.data.Playlist
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.player.ACTION_PLAY
import com.kenny.localmanager.player.ACTION_RESUME
import com.kenny.localmanager.player.EXTRA_PLAYLIST_ID
import com.kenny.localmanager.player.PlaybackService
import com.kenny.localmanager.player.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal suspend fun createNewPlaybackPlaylistAndStart(
    context: Context,
    prefs: Preferences,
    audioList: List<DocumentFileModel>,
    playlistName: String? = null,
    sourceType: String = Playlist.SOURCE_TYPE_MANUAL,
    sourceUri: String? = null
): Playlist? {
    val uniqueAudioList = dedupeAudioList(audioList)
    if (uniqueAudioList.isEmpty()) return null
    val playlist = Playlist(
        id = java.util.UUID.randomUUID().toString(),
        name = playlistName ?: context.getString(
            R.string.player_playlist_default_name,
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        ),
        uris = uniqueAudioList.map { it.uri.toString() },
        names = uniqueAudioList.map { it.name },
        sourceType = sourceType,
        sourceUri = sourceUri
    )
    withContext(Dispatchers.IO) { prefs.addPlaylist(playlist) }
    val intent = Intent(context, PlaybackService::class.java).apply {
        action = ACTION_PLAY
        putExtra(EXTRA_PLAYLIST_ID, playlist.id)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    return playlist
}

private suspend fun appendToPlaybackPlaylistAndStart(
    context: Context,
    prefs: Preferences,
    target: Playlist,
    audioList: List<DocumentFileModel>
): String? {
    if (audioList.isEmpty()) return null
    val result = withContext(Dispatchers.IO) {
        prefs.appendTracksToPlaylist(
            target.id,
            audioList.map { it.uri.toString() },
            audioList.map { it.name }
        )
    }
    val intent = Intent(context, PlaybackService::class.java).apply {
        action = ACTION_PLAY
        putExtra(EXTRA_PLAYLIST_ID, target.id)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    return when {
        result.appendedCount > 0 && result.skippedCount > 0 ->
            context.getString(R.string.player_append_started_with_skips, result.appendedCount, target.displayName(context.getString(R.string.player_unnamed_playlist)), result.skippedCount)
        result.appendedCount > 0 ->
            context.getString(R.string.player_append_started, result.appendedCount, target.displayName(context.getString(R.string.player_unnamed_playlist)))
        result.skippedCount > 0 ->
            context.getString(R.string.player_already_exists_started, target.displayName(context.getString(R.string.player_unnamed_playlist)))
        else ->
            context.getString(R.string.player_started_playlist, target.displayName(context.getString(R.string.player_unnamed_playlist)))
    }
}

/** 播放器域对外的窄 API：其他域（文件浏览批量加入、最近打开）只看这个面。 */
internal interface PlayerTabApi {
    val playbackState: PlaybackState?
    fun requestAddToPlayback(audioList: List<DocumentFileModel>)
}

/** 主程序持有的播放器 tab 接口：呈现（MainTabFeature）+ 跨域 API（PlayerTabApi）。 */
internal interface PlayerTabFeature : MainTabFeature, PlayerTabApi

/**
 * 播放器功能对象的工厂：主程序唯一的创建入口。
 * 创建、常驻接线（服务状态镜像 collect）、快捷方式恢复全部内聚于此，主程序不可见。
 */
@Composable
internal fun playerTabFeature(
    context: Context,
    prefs: Preferences,
    scope: CoroutineScope,
    autoplayShortcut: Boolean,
    onRequestExitApp: () -> Unit,
    recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    onBatchFinished: () -> Unit,
    onOpenPlayerTab: () -> Unit
): PlayerTabFeature {
    val state = remember(autoplayShortcut) {
        PlayerTabState(
            context = context,
            prefs = prefs,
            scope = scope,
            autoplayShortcutPendingInitial = autoplayShortcut,
            onRequestExitApp = onRequestExitApp,
            recordRecentOpen = recordRecentOpen,
            onBatchFinished = onBatchFinished,
            onOpenPlayerTab = onOpenPlayerTab
        )
    }
    LaunchedEffect(state) {
        state.attach()
    }
    return state
}

/**
 * 播放器功能对象：播放状态、播放列表快照与"加入播放列表"跨域流程的唯一所有者。
 */
private class PlayerTabState(
    val context: Context,
    val prefs: Preferences,
    private val scope: CoroutineScope,
    private val autoplayShortcutPendingInitial: Boolean,
    private val onRequestExitApp: () -> Unit,
    private val recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    private val onBatchFinished: () -> Unit,
    private val onOpenPlayerTab: () -> Unit,
) : PlayerTabFeature {
    // ===== 播放状态与播放列表快照 =====
    override var playbackState by mutableStateOf<PlaybackState?>(null)
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var playlistsLoaded by mutableStateOf(false)
        private set
    var playerLastPlaylistId by mutableStateOf<String?>(null)
        private set

    // ===== "加入播放列表"跨域流程 =====
    var showPlaybackTargetDialog by mutableStateOf(false)
    var pendingPlaybackAudioList by mutableStateOf<List<DocumentFileModel>>(emptyList())

    // ===== 快捷方式自动恢复（启动参数） =====
    private var autoplayShortcutPending by mutableStateOf(autoplayShortcutPendingInitial)

    // ===== MainTabFeature 投影 =====
    override val tab: MainTab get() = MainTab.PLAYER
    override val content: @Composable () -> Unit = {
        LaunchedEffect(
            autoplayShortcutPending,
            playbackState?.playlistId,
            playbackState?.isPlaying,
            playerLastPlaylistId,
            playlistsLoaded,
            playlists
        ) {
            if (!autoplayShortcutPending) return@LaunchedEffect
            if (tryResumeFromShortcut(
                    pending = true,
                    isPlayerTabActive = true,
                    hasPrivateOutput = context.hasPrivatePlaybackOutput()
                )
            ) {
                autoplayShortcutPending = false
            }
        }
        PlayerTabRoute(
            PlayerTabRouteState(
                context = context,
                prefs = prefs,
                playbackState = playbackState,
                onRequestExitApp = onRequestExitApp
            )
        )
    }
    override val overlay: (@Composable () -> Unit)? = { PlayerPlaybackTargetDialog(state = this) }

    /** 常驻接线：服务播放状态与 prefs 快照的镜像 collect（消费者分布在多个 tab，宿主顶层调用）。 */
    fun attach() {
        scope.launch {
            PlaybackService.playbackState.collect { playbackState = it }
        }
        scope.launch {
            prefs.playlists.collect {
                playlists = it
                playlistsLoaded = true
            }
        }
        scope.launch {
            prefs.playerLastPlaylistId.collect { playerLastPlaylistId = it }
        }
    }

    fun clearPendingTargetState() {
        pendingPlaybackAudioList = emptyList()
        showPlaybackTargetDialog = false
    }

    /** 文件浏览"加入播放"入口：无手动列表时直接新建，否则弹目标选择。 */
    override fun requestAddToPlayback(audioList: List<DocumentFileModel>) {
        if (audioList.isEmpty()) return
        pendingPlaybackAudioList = audioList
        if (playlists.isEmpty()) {
            createNewPlaylistAndStart(audioList)
        } else {
            showPlaybackTargetDialog = true
        }
    }

    /**
     * 快捷方式启动时的自动恢复（原宿主 LaunchedEffect 体）。
     * 由宿主在键变化时调用；返回 true 表示应清除 pending 标志，false 表示继续等待。
     */
    fun tryResumeFromShortcut(pending: Boolean, isPlayerTabActive: Boolean, hasPrivateOutput: Boolean): Boolean {
        if (!pending) return false
        if (!isPlayerTabActive) return false
        if (!hasPrivateOutput) return true
        if (playbackState?.isPlaying == true) return true
        val resumeIntent = when {
            playbackState != null -> Intent(context, PlaybackService::class.java).setAction(ACTION_RESUME)
            playerLastPlaylistId.isNullOrBlank() -> return true
            !playlistsLoaded -> return false
            playlists.none { it.id == playerLastPlaylistId } -> return true
            else -> Intent(context, PlaybackService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_PLAYLIST_ID, playerLastPlaylistId)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(resumeIntent)
        } else {
            context.startService(resumeIntent)
        }
        return true
    }

    /** 新建播放列表并开始播放（含记录最近、批量收尾、切 tab、Toast 编排）。 */
    fun createNewPlaylistAndStart(audioList: List<DocumentFileModel>) {
        scope.launch {
            val playlist = createNewPlaybackPlaylistAndStart(context, prefs, audioList) ?: return@launch
            recordRecentOpen(
                RECENT_TYPE_PLAYLIST,
                playlist.id,
                playlist.displayName(context.getString(R.string.player_unnamed_playlist)),
                null,
                playlist.id
            )
            onBatchFinished()
            onOpenPlayerTab()
            clearPendingTargetState()
            Toast.makeText(context, context.getString(R.string.browser_playlist_created, audioList.size), Toast.LENGTH_SHORT).show()
        }
    }

    /** 追加到既有播放列表并开始播放（含记录最近、批量收尾、切 tab、Toast 编排）。 */
    fun appendToPlaylistAndStart(target: Playlist, audioList: List<DocumentFileModel>) {
        scope.launch {
            val msg = appendToPlaybackPlaylistAndStart(context, prefs, target, audioList)
            if (msg == null) {
                Toast.makeText(context, context.getString(R.string.browser_add_to_playlist_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            recordRecentOpen(
                RECENT_TYPE_PLAYLIST,
                target.id,
                target.name,
                null,
                target.id
            )
            onBatchFinished()
            onOpenPlayerTab()
            clearPendingTargetState()
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}

/** 播放目标选择对话框：追加到既有列表或新建（原宿主平移）。 */
@Composable
private fun PlayerPlaybackTargetDialog(state: PlayerTabState) {
    if (!(state.showPlaybackTargetDialog && state.pendingPlaybackAudioList.isNotEmpty())) return
    val context = state.context
    val manualPlaylists = state.playlists.filterNot { it.isDirectorySource }
    AlertDialog(
        onDismissRequest = { state.clearPendingTargetState() },
        title = { Text(context.getString(R.string.player_add_to_playback_title)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    context.getString(R.string.player_add_to_playback_prompt, state.pendingPlaybackAudioList.size),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                manualPlaylists.forEach { playlist ->
                    OutlinedButton(
                        onClick = {
                            state.appendToPlaylistAndStart(playlist, state.pendingPlaybackAudioList)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(playlist.displayName(), color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                context.getString(R.string.player_track_count, playlist.trackCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (manualPlaylists.isEmpty()) {
                    Text(
                        context.getString(R.string.browser_no_normal_playlist_target),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                state.createNewPlaylistAndStart(state.pendingPlaybackAudioList)
            }) { Text(context.getString(R.string.player_new_list)) }
        },
        dismissButton = {
            TextButton(onClick = { state.clearPendingTargetState() }) { Text(context.getString(R.string.common_cancel)) }
        }
    )
}

private fun Context.hasPrivatePlaybackOutput(): Boolean {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val privateOutputs = outputs.filter { it.isPrivatePlaybackCandidate() }
            Log.i(
                "FileBrowserApp",
                "Shortcut autoplay route check: private=${privateOutputs.map { it.debugLabel() }}, all=${outputs.map { it.debugLabel() }}"
            )
            privateOutputs.isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn || audioManager.isWiredHeadsetOn)
        }
    } catch (_: Exception) {
        false
    }
}

private fun AudioDeviceInfo.isPrivatePlaybackCandidate(): Boolean {
    return when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID -> true
        else -> false
    }
}

private fun AudioDeviceInfo.debugLabel(): String {
    val typeName = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE_BROADCAST"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "BUILTIN_SPEAKER_SAFE"
        AudioDeviceInfo.TYPE_DOCK -> "DOCK"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI_ARC"
        AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI_EARC"
        AudioDeviceInfo.TYPE_HEARING_AID -> "HEARING_AID"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "LINE_ANALOG"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "LINE_DIGITAL"
        AudioDeviceInfo.TYPE_AUX_LINE -> "AUX_LINE"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        else -> "TYPE_$type"
    }
    return "$typeName(id=$id,name=${getProductName()})"
}
