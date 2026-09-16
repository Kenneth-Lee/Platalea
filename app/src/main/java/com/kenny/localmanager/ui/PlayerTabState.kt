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
import com.kenny.localmanager.player.ACTION_PLAY
import com.kenny.localmanager.player.ACTION_RESUME
import com.kenny.localmanager.player.EXTRA_PLAYLIST_ID
import android.net.Uri
import com.kenny.localmanager.file.collectMusicFilesRecursively
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.player.ACTION_RELOAD_PLAYLIST
import com.kenny.localmanager.player.ACTION_REMOVE_PLAYLIST_TRACK
import com.kenny.localmanager.player.EXTRA_START_INDEX
import com.kenny.localmanager.player.EXTRA_START_POSITION_MS
import com.kenny.localmanager.player.PlaybackService
import com.kenny.localmanager.player.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal suspend fun createNewPlaybackPlaylistAndStart(
    context: Context,
    config: PlayerConfigView,
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
    withContext(Dispatchers.IO) { config.addPlaylist(playlist) }
    val intent = Intent(context, PlaybackService::class.java).apply {
        action = ACTION_PLAY
        putExtra(EXTRA_PLAYLIST_ID, playlist.id)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    return playlist
}

private suspend fun appendToPlaybackPlaylistAndStart(
    context: Context,
    config: PlayerConfigView,
    target: Playlist,
    audioList: List<DocumentFileModel>
): String? {
    if (audioList.isEmpty()) return null
    val result = withContext(Dispatchers.IO) {
        config.appendTracksToPlaylist(
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
    /** 目录整体建列表并播放（browser 域"目录播放"出口：收集、建列表、记录最近、切 tab 编排内聚于此）。 */
    fun requestCreateDirectoryPlaylist(directoryUri: String, directoryName: String)
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
    config: PlayerConfigView,
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
            config = config,
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
    val config: PlayerConfigView,
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
                config = config,
                recordRecentOpen = recordRecentOpen,
                playbackState = playbackState,
                onRequestExitApp = onRequestExitApp
            )
        )
    }
    override val overlay: (@Composable () -> Unit)? = { PlayerPlaybackTargetDialog(state = this) }

    /** 常驻接线：服务播放状态与配置快照的镜像 collect（消费者分布在多个 tab，宿主顶层调用）。 */
    fun attach() {
        scope.launch {
            PlaybackService.playbackState.collect { playbackState = it }
        }
        scope.launch {
            config.playlists.collect {
                playlists = it
                playlistsLoaded = true
            }
        }
        scope.launch {
            config.playerLastPlaylistId.collect { playerLastPlaylistId = it }
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
            val playlist = createNewPlaybackPlaylistAndStart(context, config, audioList) ?: return@launch
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
            val msg = appendToPlaybackPlaylistAndStart(context, config, target, audioList)
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

    /** 目录整体建列表并播放（原 BrowserTabHost onCreateMusicPlaylist 编排）。 */
    override fun requestCreateDirectoryPlaylist(directoryUri: String, directoryName: String) {
        scope.launch {
            val playlist = withContext(Dispatchers.IO) {
                createDirectoryPlaybackPlaylistAndStart(context, config, directoryUri, directoryName)
            }
            if (playlist == null) {
                Toast.makeText(context, context.getString(R.string.browser_no_music_in_dir), Toast.LENGTH_SHORT).show()
            } else {
                recordRecentOpen(
                    RECENT_TYPE_PLAYLIST,
                    playlist.id,
                    playlist.displayName(context.getString(R.string.player_unnamed_playlist)),
                    null,
                    playlist.id
                )
                onOpenPlayerTab()
                Toast.makeText(context, context.getString(R.string.browser_playlist_from_dir_started), Toast.LENGTH_SHORT).show()
            }
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

// ===== 播放列表持久化编排（自 FileBrowserApp.kt 平移；配置经 PlayerConfigView）=====

private fun notifyPlaybackTrackRemoved(context: Context, playlistId: String, removedIndex: Int) {
    val intent = Intent(context, PlaybackService::class.java).apply {
        action = ACTION_REMOVE_PLAYLIST_TRACK
        putExtra(EXTRA_PLAYLIST_ID, playlistId)
        putExtra(EXTRA_START_INDEX, removedIndex)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
}


private fun notifyPlaybackPlaylistReloaded(
    context: Context,
    playlistId: String,
    startIndex: Int,
    startPositionMs: Int
) {
    val intent = Intent(context, PlaybackService::class.java).apply {
        action = ACTION_RELOAD_PLAYLIST
        putExtra(EXTRA_PLAYLIST_ID, playlistId)
        putExtra(EXTRA_START_INDEX, startIndex)
        putExtra(EXTRA_START_POSITION_MS, startPositionMs)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
}


internal fun syncPlaybackAfterPlaylistUpdate(
    context: Context,
    playlistId: String,
    previousUris: List<String>,
    previousTrackIndex: Int,
    previousPositionMs: Int,
    updated: Playlist,
    playbackState: PlaybackState?,
    onStopPlayback: () -> Unit
) {
    val state = playbackState ?: return
    if (state.playlistId != playlistId) return
    if (updated.uris.isEmpty()) {
        onStopPlayback()
        return
    }
    val currentUri = previousUris.getOrNull(previousTrackIndex)
    val newIndex = currentUri?.let { uri -> updated.uris.indexOf(uri) } ?: -1
    if (newIndex >= 0) {
        notifyPlaybackPlaylistReloaded(context, playlistId, newIndex, previousPositionMs)
    } else {
        notifyPlaybackPlaylistReloaded(context, playlistId, 0, 0)
    }
}


internal suspend fun removePlaylistTrackAt(
    context: Context,
    config: PlayerConfigView,
    pl: Playlist,
    trackIndex: Int,
    playbackState: PlaybackState?,
    deleteSourceFile: Boolean,
    onStopPlayback: () -> Unit
) {
    if (trackIndex !in pl.uris.indices) return
    if (deleteSourceFile && pl.isDirectorySource) {
        val deleted = context.contentResolver.deleteDocument(Uri.parse(pl.uris[trackIndex]))
        if (!deleted) {
            Toast.makeText(context, context.getString(R.string.browser_delete_source_failed), Toast.LENGTH_SHORT).show()
            return
        }
    }
    val uris = pl.uris.toMutableList().apply { removeAt(trackIndex) }
    val names = pl.names.toMutableList().apply {
        if (trackIndex in indices) removeAt(trackIndex)
    }
    config.updatePlaylist(pl.copy(uris = uris, names = names))
    if (playbackState?.playlistId == pl.id) {
        if (uris.isEmpty()) {
            onStopPlayback()
        } else {
            notifyPlaybackTrackRemoved(context, pl.id, trackIndex)
        }
    }
}


private suspend fun createDirectoryPlaybackPlaylistAndStart(
    context: Context,
    config: PlayerConfigView,
    directoryUri: String,
    directoryName: String
): Playlist? {
    val audioList = withContext(Dispatchers.IO) {
        collectMusicFilesRecursively(context, directoryUri).map { it.model }
    }
    if (audioList.isEmpty()) return null
    return createNewPlaybackPlaylistAndStart(
        context = context,
        config = config,
        audioList = audioList,
        playlistName = directoryName.ifBlank {
            context.getString(
                R.string.player_playlist_default_name,
                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            )
        },
        sourceType = Playlist.SOURCE_TYPE_DIRECTORY,
        sourceUri = directoryUri
    )
}
