package com.kenny.localmanager.player

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.kenny.localmanager.R
import com.kenny.localmanager.data.PLAYER_AUDIO_ENGINE_EXO_PLAYER
import com.kenny.localmanager.data.PLAYER_AUDIO_ENGINE_MEDIA_PLAYER
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_BASS
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_CAR
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_FLAT
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_HEADPHONE
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_VOCAL
import com.kenny.localmanager.data.PlayerAudioSettings
import com.kenny.localmanager.data.PlayerListBookmark
import com.kenny.localmanager.data.Playlist
import com.kenny.localmanager.file.deleteDocument
import com.kenny.localmanager.file.collectMusicFilesRecursively
import com.kenny.localmanager.player.PlaylistSortOrder
import com.kenny.localmanager.player.PlaylistTrackSortField
import com.kenny.localmanager.player.PlaylistTrackSorter
import com.kenny.localmanager.player.PlaybackService
import com.kenny.localmanager.player.PlaybackState
import com.kenny.localmanager.player.PlaybackTimeFormat
import com.kenny.localmanager.player.TrackMetadata
import com.kenny.localmanager.player.ACTION_PLAY
import com.kenny.localmanager.player.ACTION_RELOAD_PLAYLIST
import com.kenny.localmanager.player.ACTION_RESUME
import com.kenny.localmanager.player.EXTRA_PLAYLIST_ID
import com.kenny.localmanager.player.EXTRA_START_INDEX
import com.kenny.localmanager.player.EXTRA_START_POSITION_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kenny.localmanager.config.PlayerConfigView
import com.kenny.localmanager.ui.PlataleaApp
import com.kenny.localmanager.ui.RECENT_TYPE_PLAYLIST


private suspend fun resortPlaylistTracks(
    context: Context,
    config: PlayerConfigView,
    pl: Playlist,
    field: PlaylistTrackSortField,
    order: PlaylistSortOrder,
    playbackState: PlaybackState?,
    onStopPlayback: () -> Unit
): Playlist {
    val previousUris = pl.uris
    val previousTrackIndex = playbackState?.takeIf { it.playlistId == pl.id }?.trackIndex ?: 0
    val previousPositionMs = playbackState?.takeIf { it.playlistId == pl.id }?.positionMs ?: 0

    var working = pl
    if (pl.isDirectorySource) {
        working = refreshDirectoryPlaylistFromSource(context, pl) ?: return pl
    } else if (pl.uris.size < 2) {
        return pl
    }

    val updated = if (working.uris.size >= 2) {
        withContext(Dispatchers.IO) {
            PlaylistTrackSorter.sortTracks(context, working, field, order)
        }
    } else {
        working
    }
    config.updatePlaylist(updated)
    syncPlaybackAfterPlaylistUpdate(
        context = context,
        playlistId = pl.id,
        previousUris = previousUris,
        previousTrackIndex = previousTrackIndex,
        previousPositionMs = previousPositionMs,
        updated = updated,
        playbackState = playbackState,
        onStopPlayback = onStopPlayback
    )
    return updated
}


private fun trackFilterTarget(
    field: PlayerTrackFilterField,
    fileName: String,
    metadata: TrackMetadata?
): String {
    return when (field) {
        PlayerTrackFilterField.FILE_NAME -> fileName
        PlayerTrackFilterField.TITLE -> metadata?.title.orEmpty()
        PlayerTrackFilterField.ALBUM -> metadata?.album.orEmpty()
        PlayerTrackFilterField.ARTIST -> metadata?.artist?.ifBlank { null }
            ?: metadata?.albumArtist.orEmpty()
    }
}


@Composable
private fun PlaybackBottomControlBar(
    isPlaying: Boolean,
    enabled: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPrev,
                enabled = enabled,
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = context.getString(R.string.player_prev_desc))
            }
            Button(
                onClick = onPlayPause,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) context.getString(R.string.player_pause_desc) else context.getString(R.string.player_play_desc),
                    modifier = Modifier.size(24.dp)
                )
            }
            OutlinedButton(
                onClick = onNext,
                enabled = enabled,
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = context.getString(R.string.player_next_desc))
            }
        }
    }
}


private suspend fun removeTracksFromPlaylistByIndices(
    context: Context,
    config: PlayerConfigView,
    playlist: Playlist,
    removeIndices: Set<Int>,
    playbackState: PlaybackState?,
    onStopPlayback: () -> Unit
): PlaylistBulkRemoveResult = withContext(Dispatchers.IO) {
    val validIndices = removeIndices
        .filter { it in playlist.uris.indices }
        .sortedDescending()
    if (validIndices.isEmpty()) {
        return@withContext PlaylistBulkRemoveResult(
            removedCount = 0,
            failedNames = emptyList(),
            removedIndices = emptySet(),
            updatedPlaylist = playlist
        )
    }

    val previousUris = playlist.uris
    val previousTrackIndex = playbackState?.takeIf { it.playlistId == playlist.id }?.trackIndex ?: 0
    val previousPositionMs = playbackState?.takeIf { it.playlistId == playlist.id }?.positionMs ?: 0

    val uris = playlist.uris.toMutableList()
    val names = playlist.names.toMutableList()
    val failedNames = mutableListOf<String>()
    val removedIndices = mutableSetOf<Int>()
    var removedCount = 0

    validIndices.forEach { index ->
        if (playlist.isDirectorySource) {
            val deleted = context.contentResolver.deleteDocument(Uri.parse(uris[index]))
            if (!deleted) {
                failedNames += names.getOrNull(index) ?: uris[index].substringAfterLast('/')
                return@forEach
            }
        }
        if (index in uris.indices) {
            uris.removeAt(index)
            names.removeAt(index)
            removedIndices += index
            removedCount++
        }
    }

    val updated = playlist.copy(uris = uris, names = names)
    if (removedCount > 0) {
        config.updatePlaylist(updated)
        syncPlaybackAfterPlaylistUpdate(
            context = context,
            playlistId = playlist.id,
            previousUris = previousUris,
            previousTrackIndex = previousTrackIndex,
            previousPositionMs = previousPositionMs,
            updated = updated,
            playbackState = playbackState,
            onStopPlayback = onStopPlayback
        )
    }

    PlaylistBulkRemoveResult(
        removedCount = removedCount,
        failedNames = failedNames,
        removedIndices = removedIndices,
        updatedPlaylist = updated
    )
}


private data class PlaylistBulkRemoveResult(
    val removedCount: Int,
    val failedNames: List<String>,
    val removedIndices: Set<Int>,
    val updatedPlaylist: Playlist
)

private fun shiftIndexAfterRemovals(index: Int, removedAsc: List<Int>): Int? {
    var removedBefore = 0
    for (removed in removedAsc) {
        if (removed == index) return null
        if (removed < index) removedBefore++
    }
    return index - removedBefore
}


private fun updateDuplicateGroupsAfterRemoval(
    groups: List<DuplicateSongGroup>,
    removedIndices: Set<Int>
): List<DuplicateSongGroup> {
    if (removedIndices.isEmpty()) return groups
    val removedAsc = removedIndices.sorted()
    return groups.mapNotNull { group ->
        val updatedEntries = group.entries.mapNotNull { entry ->
            val newIndex = shiftIndexAfterRemovals(entry.originalIndex, removedAsc)
            if (newIndex == null) null else entry.copy(originalIndex = newIndex)
        }
        if (updatedEntries.size < 2) {
            null
        } else {
            group.copy(entries = updatedEntries.sortedBy { it.originalIndex })
        }
    }
}


private suspend fun refreshDirectoryPlaylistFromSource(
    context: Context,
    pl: Playlist
): Playlist? {
    val sourceUri = pl.sourceUri ?: return null
    val audioList = withContext(Dispatchers.IO) {
        collectMusicFilesRecursively(context, sourceUri).map { it.model }
    }
    val deduped = dedupeAudioList(audioList)
    return pl.copy(
        uris = deduped.map { it.uri.toString() },
        names = deduped.map { it.name }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackScreen(
    config: PlayerConfigView,
    recordRecentOpen: (type: String, key: String, title: String, uri: String?, playlistId: String?) -> Unit,
    playbackState: PlaybackState?,
    onStopPlayback: () -> Unit,
    onPlayPrev: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (positionMs: Int) -> Unit = {},
    showBackButton: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var lastPlaylistId by remember { mutableStateOf<String?>(null) }
    var playerBookmarks by remember { mutableStateOf<List<PlayerListBookmark>>(emptyList()) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showRenamePlaylistDialog by remember { mutableStateOf(false) }
    var playlistRenameTarget by remember { mutableStateOf<Playlist?>(null) }
    var playlistRenameText by remember { mutableStateOf("") }
    var showSortPlaylistDialog by remember { mutableStateOf(false) }
    var playlistSortTarget by remember { mutableStateOf<Playlist?>(null) }
    var playlistSortField by remember { mutableStateOf(PlaylistTrackSortField.ARTIST) }
    var playlistSortOrder by remember { mutableStateOf(PlaylistSortOrder.ASCENDING) }
    var playlistSortInProgress by remember { mutableStateOf(false) }
    var showDuplicateSongsDialog by remember { mutableStateOf(false) }
    var duplicateSongsLoading by remember { mutableStateOf(false) }
    var duplicateSongsMutating by remember { mutableStateOf(false) }
    var duplicateSongGroups by remember { mutableStateOf<List<DuplicateSongGroup>>(emptyList()) }
    var duplicateSongsTargetPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var showBookmarkManagerDialog by remember { mutableStateOf(false) }
    var bookmarkNoteInput by remember { mutableStateOf("") }
    var bookmarkPendingPlaylistId by remember { mutableStateOf<String?>(null) }
    var bookmarkPendingDirUri by remember { mutableStateOf<String?>(null) }
    var bookmarkPendingTrackIndex by remember { mutableIntStateOf(0) }
    var bookmarkPendingPositionMs by remember { mutableLongStateOf(0L) }
    var bookmarkPendingTrackName by remember { mutableStateOf("") }
    var editingBookmarkId by remember { mutableStateOf<String?>(null) }
    var editingBookmarkNote by remember { mutableStateOf("") }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var trackActionMenuIndex by remember { mutableStateOf<Int?>(null) }
    var playlistActionMenuIndex by remember { mutableStateOf<Int?>(null) }
    var pendingTrackTransfer by remember { mutableStateOf<PlaylistTrackTransfer?>(null) }
    var playerAudioSettings by remember { mutableStateOf(PlayerAudioSettings()) }
    var showPlayerAudioSettingsDialog by remember { mutableStateOf(false) }
    var showPlaybackDiagnostics by remember { mutableStateOf(false) }
    var showPreviewSettingsDialog by remember { mutableStateOf(false) }
    var previewSettingsTarget by remember { mutableStateOf<Playlist?>(null) }
    var previewEnabledInput by remember { mutableStateOf(false) }
    var previewStartInput by remember { mutableStateOf("") }
    var previewMaxDurationInput by remember { mutableStateOf("") }
    var previewTimeError by remember { mutableStateOf<String?>(null) }
    var trackFilterText by remember { mutableStateOf("") }
    var trackFilterField by remember { mutableStateOf(PlayerTrackFilterField.FILE_NAME) }
    var showTrackFilterFieldMenu by remember { mutableStateOf(false) }
    var showBulkDeleteTracksDialog by remember { mutableStateOf(false) }
    var bulkDeleteTrackIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var bulkDeleteTrackCount by remember { mutableIntStateOf(0) }
    var bulkDeleteWasFiltered by remember { mutableStateOf(false) }
    var bulkDeleteHiddenMode by remember { mutableStateOf(false) }
    var bulkDeleteTracksInProgress by remember { mutableStateOf(false) }
    var trackMetadataCache by remember(selectedPlaylistId) { mutableStateOf<Map<String, TrackMetadata>>(emptyMap()) }
    var trackMetadataLoading by remember(selectedPlaylistId) { mutableStateOf(false) }
    LaunchedEffect(config) {
        config.playlists.collect { playlists = it }
    }
    LaunchedEffect(config) {
        config.playerLastPlaylistId.collect { lastPlaylistId = it }
    }
    LaunchedEffect(config) {
        config.playerListBookmarks.collect { playerBookmarks = it }
    }
    LaunchedEffect(config) {
        config.playerAudioSettings.collect { playerAudioSettings = it }
    }
    val playlistById = remember(playlists) { playlists.associateBy { it.id } }
    val selectedPlaylist = selectedPlaylistId?.let { id -> playlists.find { it.id == id } }
    if (selectedPlaylist == null && selectedPlaylistId != null) selectedPlaylistId = null

    LaunchedEffect(selectedPlaylist?.id, trackFilterField) {
        val current = selectedPlaylist ?: return@LaunchedEffect
        if (trackFilterField == PlayerTrackFilterField.FILE_NAME) {
            trackMetadataLoading = false
            return@LaunchedEffect
        }
        val missing = current.uris.filterNot { trackMetadataCache.containsKey(it) }
        if (missing.isEmpty()) {
            trackMetadataLoading = false
            return@LaunchedEffect
        }
        trackMetadataLoading = true
        val loaded = withContext(Dispatchers.IO) {
            missing.associateWith { uri -> readTrackMetadata(context, uri) }
        }
        trackMetadataCache = trackMetadataCache + loaded
        trackMetadataLoading = false
    }

    BackHandler(enabled = selectedPlaylistId != null) {
        selectedPlaylistId = null
    }

    fun openRenamePlaylistDialog(pl: Playlist) {
        playlistRenameTarget = pl
        playlistRenameText = pl.displayName(context.getString(R.string.player_unnamed_playlist))
        showRenamePlaylistDialog = true
    }

    fun openSortPlaylistDialog(pl: Playlist) {
        playlistSortTarget = pl
        playlistSortField = PlaylistTrackSortField.ARTIST
        playlistSortOrder = PlaylistSortOrder.ASCENDING
        showSortPlaylistDialog = true
    }

    fun openDuplicateSongsDialog(pl: Playlist) {
        duplicateSongsTargetPlaylist = pl
        duplicateSongGroups = emptyList()
        duplicateSongsMutating = false
        duplicateSongsLoading = true
        showDuplicateSongsDialog = true
        scope.launch {
            val groups = runCatching {
                findDuplicateSongGroups(context, pl, trackMetadataCache)
            }.getOrElse { error ->
                Log.e("PlataleaApp", "Failed to scan duplicate songs for playlist=${pl.id}", error)
                Toast.makeText(
                    context,
                    context.getString(R.string.player_duplicate_tracks_scan_failed, error.message ?: error.javaClass.simpleName),
                    Toast.LENGTH_SHORT
                ).show()
                emptyList()
            }
            if (duplicateSongsTargetPlaylist?.id == pl.id) {
                duplicateSongGroups = groups
                duplicateSongsLoading = false
            }
        }
    }

    fun openPreviewSettingsDialog(pl: Playlist) {
        previewSettingsTarget = pl
        previewEnabledInput = pl.previewEnabled
        previewStartInput = if (pl.previewStartMs > 0L) PlaybackTimeFormat.formatMs(pl.previewStartMs) else ""
        previewMaxDurationInput = if (pl.previewMaxDurationMs > 0L) PlaybackTimeFormat.formatMs(pl.previewMaxDurationMs) else ""
        previewTimeError = null
        showPreviewSettingsDialog = true
    }

    fun previewListHint(pl: Playlist): String {
        val start = PlaybackTimeFormat.formatMs(pl.previewStartMs)
        return if (pl.previewMaxDurationMs > 0L) {
            context.getString(
                R.string.player_preview_list_hint,
                start,
                PlaybackTimeFormat.formatMs(pl.previewMaxDurationMs)
            )
        } else {
            context.getString(R.string.player_preview_list_hint_no_limit, start)
        }
    }

    fun recordPlayedPlaylist(pl: Playlist) {
        recordRecentOpen(
            RECENT_TYPE_PLAYLIST,
            pl.id,
            pl.displayName(context.getString(R.string.player_unnamed_playlist)),
            null,
            pl.id
        )
    }

    fun startPlaylist(pl: Playlist) {
        recordPlayedPlaylist(pl)
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_PLAY
            putExtra(EXTRA_PLAYLIST_ID, pl.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    fun startPlaylistFromIndex(pl: Playlist, index: Int, startPositionMs: Int = 0) {
        recordPlayedPlaylist(pl)
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_PLAY
            putExtra(EXTRA_PLAYLIST_ID, pl.id)
            putExtra(EXTRA_START_INDEX, index)
            putExtra(EXTRA_START_POSITION_MS, startPositionMs.coerceAtLeast(0))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    fun resumeLastPlayback(playlist: Playlist?) {
        if (playlist != null) {
            startPlaylist(playlist)
            return
        }
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_RESUME
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    suspend fun createEmptyPlaylist(name: String) {
        val fallbackName = context.getString(
            R.string.player_playlist_default_name,
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        )
        val playlist = Playlist(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { fallbackName },
            uris = emptyList(),
            names = emptyList()
        )
        config.addPlaylist(playlist)
        selectedPlaylistId = playlist.id
        showCreatePlaylistDialog = false
        newPlaylistName = ""
        Toast.makeText(context, context.getString(R.string.browser_empty_playlist_created), Toast.LENGTH_SHORT).show()
    }

    suspend fun removeTrackEntryAfterMove(source: Playlist, trackIndex: Int) {
        removePlaylistTrackAt(
            context = context,
            config = config,
            pl = source,
            trackIndex = trackIndex,
            playbackState = playbackState,
            deleteSourceFile = false,
            onStopPlayback = onStopPlayback
        )
    }

    suspend fun transferTrackToPlaylist(transfer: PlaylistTrackTransfer, target: Playlist) {
        if (target.isDirectorySource) {
            Toast.makeText(context, context.getString(R.string.browser_playlist_dir_no_move_target), Toast.LENGTH_SHORT).show()
            return
        }
        if (target.id == transfer.source.id) return
        val trackUri = transfer.source.uris.getOrNull(transfer.trackIndex) ?: return
        val trackName = transfer.source.names.getOrElse(transfer.trackIndex) { trackUri.substringAfterLast('/') }
        val result = config.appendTracksToPlaylist(target.id, listOf(trackUri), listOf(trackName))
        if (!result.found) {
            Toast.makeText(context, context.getString(R.string.browser_playlist_target_missing), Toast.LENGTH_SHORT).show()
            return
        }
        if (transfer.move) {
            removeTrackEntryAfterMove(transfer.source, transfer.trackIndex)
        }
        pendingTrackTransfer = null
        val action = if (transfer.move) context.getString(R.string.pending_list_action_move) else context.getString(R.string.pending_list_action_copy)
        val duplicateHint = if (result.skippedCount > 0) context.getString(R.string.browser_playlist_duplicate_hint) else ""
        Toast.makeText(context, context.getString(R.string.browser_playlist_action_result, action, target.name, duplicateHint), Toast.LENGTH_SHORT).show()
    }

    fun promptSaveCurrentBookmark() {
        val state = playbackState
        if (state?.playlistId == null) {
            Toast.makeText(context, context.getString(R.string.player_bookmark_playlist_only), Toast.LENGTH_SHORT).show()
            return
        }
        bookmarkPendingPlaylistId = state.playlistId
        bookmarkPendingDirUri = null
        bookmarkPendingTrackIndex = state.trackIndex
        bookmarkPendingPositionMs = state.positionMs.toLong()
        bookmarkPendingTrackName = state.trackName
        bookmarkNoteInput = ""
        showAddBookmarkDialog = true
    }

    if (showPlayerAudioSettingsDialog) {
        val engines = listOf(
            PLAYER_AUDIO_ENGINE_MEDIA_PLAYER to context.getString(R.string.browser_system_media_player),
            PLAYER_AUDIO_ENGINE_EXO_PLAYER to "Media3 ExoPlayer"
        )
        val presets = listOf(
            PLAYER_AUDIO_PRESET_FLAT to context.getString(R.string.browser_flat_preset),
            PLAYER_AUDIO_PRESET_VOCAL to context.getString(R.string.browser_vocal_preset),
            PLAYER_AUDIO_PRESET_BASS to context.getString(R.string.browser_bass_preset),
            PLAYER_AUDIO_PRESET_CAR to context.getString(R.string.browser_car_preset),
            PLAYER_AUDIO_PRESET_HEADPHONE to context.getString(R.string.browser_headphone_preset)
        )
        fun updatePlayerAudioSettings(transform: (PlayerAudioSettings) -> PlayerAudioSettings) {
            scope.launch { config.updatePlayerAudioSettings(transform) }
        }
        AlertDialog(
            onDismissRequest = { showPlayerAudioSettingsDialog = false },
            title = { Text(stringResource(R.string.browser_player_config_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.browser_player_settings_local_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.browser_player_engine), style = MaterialTheme.typography.titleSmall)
                    engines.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { updatePlayerAudioSettings { it.copy(engine = value) } }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = playerAudioSettings.engine == value,
                                onCheckedChange = { updatePlayerAudioSettings { it.copy(engine = value) } }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(stringResource(R.string.browser_engine_switch_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.browser_wake_lock), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.browser_wake_lock_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = playerAudioSettings.keepAwake,
                            onCheckedChange = { enabled -> updatePlayerAudioSettings { it.copy(keepAwake = enabled) } }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.browser_audio_effects), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.browser_audio_effects_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = playerAudioSettings.audioEffectsEnabled,
                            onCheckedChange = { enabled -> updatePlayerAudioSettings { it.copy(audioEffectsEnabled = enabled) } }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.browser_audio_preset), style = MaterialTheme.typography.titleSmall)
                    presets.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { updatePlayerAudioSettings { it.copy(effectPreset = value, audioEffectsEnabled = true) } }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = playerAudioSettings.effectPreset == value,
                                onCheckedChange = { updatePlayerAudioSettings { it.copy(effectPreset = value, audioEffectsEnabled = true) } }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.browser_high_quality_output), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.browser_high_quality_output_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = playerAudioSettings.highQualityOutput,
                            onCheckedChange = { enabled -> updatePlayerAudioSettings { it.copy(highQualityOutput = enabled) } }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlayerAudioSettingsDialog = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    val restorePlaylist = lastPlaylistId?.let { id -> playlists.find { it.id == id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selectedPlaylist?.displayName(context.getString(R.string.player_unnamed_playlist))
                            ?: context.getString(R.string.player_screen_title)
                    )
                },
                actions = {
                    if (selectedPlaylist != null) {
                        IconButton(onClick = { startPlaylist(selectedPlaylist) }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = context.getString(R.string.player_resume_current_playlist)
                            )
                        }
                    }
                    if (playbackState?.playlistId != null) {
                        TextButton(onClick = { promptSaveCurrentBookmark() }) {
                            Text(context.getString(R.string.player_mark_action))
                        }
                    }
                    TextButton(onClick = { showBookmarkManagerDialog = true }) {
                        Text(context.getString(R.string.player_bookmarks_action))
                    }
                    IconButton(onClick = { showPlayerAudioSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.browser_player_config_title))
                    }
                },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = {
                            if (selectedPlaylist != null) selectedPlaylistId = null
                            else onDismiss()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.common_back))
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (playbackState != null) {
                val state = playbackState
                var seekSliderPosition by remember(state.trackIndex, state.trackName) { mutableStateOf(state.positionMs.toFloat()) }
                var seekDragging by remember { mutableStateOf(false) }
                LaunchedEffect(state.positionMs, state.durationMs) {
                    if (!seekDragging) {
                        seekSliderPosition = state.positionMs.toFloat().coerceIn(0f, maxOf(1f, state.durationMs.toFloat()))
                    }
                }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${state.trackName} (${state.trackIndex + 1}/${state.totalTracks})",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val metadataText = formatTrackMetadata(context, state.metadata)
                        if (metadataText.isNotEmpty()) {
                            Text(
                                metadataText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (state.previewActive) {
                            val previewPl = state.playlistId?.let { id -> playlistById[id] }
                            val previewStartText = formatPlaybackTime((previewPl?.previewStartMs ?: 0L).toInt())
                            val previewEndText = state.previewWindowEndMs?.let { formatPlaybackTime(it) }
                            Text(
                                if (previewEndText != null) {
                                    context.getString(R.string.player_preview_playing_hint, previewStartText, previewEndText)
                                } else {
                                    context.getString(R.string.player_preview_playing_hint_no_end, previewStartText)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 2
                            )
                        }
                        if (state.durationMs > 0) {
                            Spacer(Modifier.height(4.dp))
                            Slider(
                                value = seekSliderPosition,
                                onValueChange = {
                                    seekDragging = true
                                    seekSliderPosition = it
                                },
                                onValueChangeFinished = {
                                    onSeek(seekSliderPosition.toInt().coerceIn(0, state.durationMs))
                                    seekDragging = false
                                },
                                valueRange = 0f..maxOf(1f, state.durationMs.toFloat()),
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    activeTrackColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                                )
                            )
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                formatPlaybackTime(state.positionMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (state.durationMs > 0) formatPlaybackTime(state.durationMs) else "--:--",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        val diagnostics = state.diagnostics
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { onPlayPause() }
                            ) {
                                Text(
                                    if (state.isPlaying) {
                                        stringResource(R.string.player_pause_desc)
                                    } else {
                                        stringResource(R.string.player_play_desc)
                                    }
                                )
                            }
                            TextButton(onClick = { showPlaybackDiagnostics = !showPlaybackDiagnostics }) {
                                Text(
                                    if (showPlaybackDiagnostics) {
                                        stringResource(R.string.browser_hide_diagnostics)
                                    } else {
                                        stringResource(R.string.browser_playback_diagnostics)
                                    }
                                )
                            }
                        }
                        if (showPlaybackDiagnostics) {
                            val diagLines = listOfNotNull(
                                context.getString(R.string.browser_engine_value, diagnostics.engine.ifBlank { context.getString(R.string.common_unknown_error) }),
                                context.getString(R.string.browser_source_value, if (diagnostics.playbackSource == "direct") context.getString(R.string.browser_source_direct) else diagnostics.playbackSource),
                                context.getString(R.string.browser_output_value, diagnostics.outputDevice.ifBlank { context.getString(R.string.common_unknown_error) }),
                                diagnostics.outputDeviceSource.takeIf { it.isNotBlank() }?.let { context.getString(R.string.browser_output_based_on, it) },
                                context.getString(R.string.browser_offload_status, if (diagnostics.exoOffloadActive) context.getString(R.string.browser_offload_active) else context.getString(R.string.browser_offload_inactive)),
                                context.getString(R.string.browser_hq_output_value, diagnostics.highQualityOutput.ifBlank { context.getString(R.string.common_unknown_error) }),
                                context.getString(R.string.browser_effects_value, diagnostics.audioEffects.ifBlank { context.getString(R.string.common_unknown_error) }),
                                context.getString(R.string.browser_buffer_events, diagnostics.bufferEvents),
                                context.getString(R.string.browser_error_count, diagnostics.playerErrors),
                                diagnostics.lastError?.let { context.getString(R.string.browser_last_error, it) },
                                diagnostics.mimeType?.let { context.getString(R.string.browser_type_value, it) },
                                diagnostics.sourceQuality?.let { context.getString(R.string.browser_source_nature, it) },
                                diagnostics.bitrate?.let { context.getString(R.string.browser_bitrate, it) },
                                diagnostics.sampleRate?.let { context.getString(R.string.browser_sample_rate, it) },
                                diagnostics.bitsPerSample?.let { context.getString(R.string.browser_bit_depth, it) }
                            )
                            Text(
                                diagLines.joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                            )
                        }
                    }
                }
            } else {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            context.getString(R.string.player_not_playing),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (restorePlaylist != null) context.getString(R.string.player_resume_hint, restorePlaylist.displayName()) else context.getString(R.string.player_select_playlist_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (selectedPlaylist != null) {
                val pl = selectedPlaylist
                val filterText = trackFilterText
                val trackEntries = remember(pl, trackMetadataCache) {
                    pl.uris.mapIndexed { idx, uri ->
                        PlayerTrackEntry(
                            originalIndex = idx,
                            uri = uri,
                            name = pl.names.getOrElse(idx) { uri.substringAfterLast('/') },
                            metadata = trackMetadataCache[uri]
                        )
                    }
                }
                val filteredTrackEntries = remember(trackEntries, filterText, trackFilterField) {
                    if (filterText.isBlank()) {
                        trackEntries
                    } else {
                        runCatching { Regex(filterText) }.getOrNull()?.let { regex ->
                            trackEntries.filter { entry ->
                                regex.containsMatchIn(trackFilterTarget(trackFilterField, entry.name, entry.metadata))
                            }
                        } ?: trackEntries
                    }
                }
                val hiddenTrackEntries = remember(trackEntries, filteredTrackEntries) {
                    val visibleIndices = filteredTrackEntries.asSequence().map { it.originalIndex }.toHashSet()
                    trackEntries.filterNot { it.originalIndex in visibleIndices }
                }
                val selectedPlaylistTrackListState = remember(pl.id) { LazyListState() }
                LaunchedEffect(pl.id, playbackState?.playlistId, playbackState?.trackIndex, filteredTrackEntries) {
                    val state = playbackState ?: return@LaunchedEffect
                    if (state.playlistId != pl.id) return@LaunchedEffect
                    val target = filteredTrackEntries.indexOfFirst { it.originalIndex == state.trackIndex }
                        .takeIf { it >= 0 }
                        ?: state.trackIndex.coerceIn(0, maxOf(0, filteredTrackEntries.lastIndex))
                    selectedPlaylistTrackListState.animateScrollToItem((target - 2).coerceAtLeast(0))
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pl.isDirectorySource) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openRenamePlaylistDialog(pl) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            pl.displayName(context.getString(R.string.player_unnamed_playlist)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pl.isDirectorySource) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pl.isDirectorySource) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                context.getString(R.string.browser_playlist_dir_list_refresh_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    if (pl.isDirectorySource || pl.uris.size >= 2) {
                        IconButton(onClick = { openSortPlaylistDialog(pl) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = context.getString(R.string.player_resort_playlist),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = context.getString(R.string.player_rename_playlist),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = trackFilterText,
                        onValueChange = { trackFilterText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(context.getString(R.string.player_track_filter_placeholder)) },
                        trailingIcon = {
                            if (trackFilterText.isNotEmpty()) {
                                IconButton(onClick = { trackFilterText = "" }) {
                                    Icon(Icons.Default.RemoveCircle, contentDescription = context.getString(R.string.directory_filter_clear), Modifier.size(20.dp))
                                }
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        OutlinedButton(onClick = { showTrackFilterFieldMenu = true }) {
                            Text(
                                when (trackFilterField) {
                                    PlayerTrackFilterField.FILE_NAME -> context.getString(R.string.player_track_filter_field_file_name)
                                    PlayerTrackFilterField.TITLE -> context.getString(R.string.player_track_filter_field_title)
                                    PlayerTrackFilterField.ALBUM -> context.getString(R.string.player_track_filter_field_album)
                                    PlayerTrackFilterField.ARTIST -> context.getString(R.string.player_track_filter_field_artist)
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = showTrackFilterFieldMenu,
                            onDismissRequest = { showTrackFilterFieldMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.player_track_filter_field_file_name)) },
                                onClick = {
                                    trackFilterField = PlayerTrackFilterField.FILE_NAME
                                    showTrackFilterFieldMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.player_track_filter_field_title)) },
                                onClick = {
                                    trackFilterField = PlayerTrackFilterField.TITLE
                                    showTrackFilterFieldMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.player_track_filter_field_album)) },
                                onClick = {
                                    trackFilterField = PlayerTrackFilterField.ALBUM
                                    showTrackFilterFieldMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.player_track_filter_field_artist)) },
                                onClick = {
                                    trackFilterField = PlayerTrackFilterField.ARTIST
                                    showTrackFilterFieldMenu = false
                                }
                            )
                        }
                    }
                }
                if (trackMetadataLoading && trackFilterField != PlayerTrackFilterField.FILE_NAME) {
                    Text(
                        context.getString(R.string.player_track_filter_loading_metadata),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bulkDeleteTracksInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            context.getString(R.string.player_bulk_delete_tracks_in_progress),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(
                        enabled = !bulkDeleteTracksInProgress && filteredTrackEntries.isNotEmpty(),
                        onClick = {
                            bulkDeleteHiddenMode = false
                            bulkDeleteTrackIndices = filteredTrackEntries.map { it.originalIndex }.toSet()
                            bulkDeleteTrackCount = bulkDeleteTrackIndices.size
                            bulkDeleteWasFiltered = trackFilterText.isNotBlank()
                            showBulkDeleteTracksDialog = true
                        }
                    ) {
                        val actionText = if (filteredTrackEntries.isNotEmpty()) {
                            context.getString(R.string.player_bulk_delete_tracks_action_count, filteredTrackEntries.size)
                        } else {
                            context.getString(R.string.player_bulk_delete_tracks_action)
                        }
                        Text(
                            actionText,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(
                        enabled = !bulkDeleteTracksInProgress && hiddenTrackEntries.isNotEmpty(),
                        onClick = {
                            bulkDeleteHiddenMode = true
                            bulkDeleteTrackIndices = hiddenTrackEntries.map { it.originalIndex }.toSet()
                            bulkDeleteTrackCount = bulkDeleteTrackIndices.size
                            bulkDeleteWasFiltered = trackFilterText.isNotBlank()
                            showBulkDeleteTracksDialog = true
                        }
                    ) {
                        Text(
                            if (hiddenTrackEntries.isNotEmpty()) {
                                context.getString(R.string.player_bulk_delete_hidden_tracks_action_count, hiddenTrackEntries.size)
                            } else {
                                context.getString(R.string.player_bulk_delete_hidden_tracks_action)
                            },
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (pl.uris.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(context.getString(R.string.player_list_empty), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = selectedPlaylistTrackListState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(filteredTrackEntries.size) { i ->
                            val entry = filteredTrackEntries[i]
                            val name = entry.name
                            val isCurrentTrack = playbackState?.playlistId == pl.id && playbackState.trackIndex == entry.originalIndex
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isCurrentTrack) MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { startPlaylistFromIndex(pl, entry.originalIndex) }
                                    .padding(start = 6.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${entry.originalIndex + 1}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(22.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Box {
                                    IconButton(
                                        onClick = { trackActionMenuIndex = i },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = context.getString(R.string.main_menu),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = trackActionMenuIndex == i,
                                        onDismissRequest = { trackActionMenuIndex = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_play_from_here)) },
                                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                            onClick = {
                                                trackActionMenuIndex = null
                                                startPlaylistFromIndex(pl, entry.originalIndex)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_move_up)) },
                                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                                            enabled = entry.originalIndex > 0,
                                            onClick = {
                                                trackActionMenuIndex = null
                                                if (entry.originalIndex > 0) scope.launch {
                                                    val uris = pl.uris.toMutableList()
                                                    val names = pl.names.toMutableList()
                                                    uris.add(entry.originalIndex - 1, uris.removeAt(entry.originalIndex))
                                                    names.add(entry.originalIndex - 1, names.removeAt(entry.originalIndex))
                                                    config.updatePlaylist(pl.copy(uris = uris, names = names))
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_move_down)) },
                                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                                            enabled = entry.originalIndex < pl.uris.size - 1,
                                            onClick = {
                                                trackActionMenuIndex = null
                                                if (entry.originalIndex < pl.uris.size - 1) scope.launch {
                                                    val uris = pl.uris.toMutableList()
                                                    val names = pl.names.toMutableList()
                                                    uris.add(entry.originalIndex + 1, uris.removeAt(entry.originalIndex))
                                                    names.add(entry.originalIndex + 1, names.removeAt(entry.originalIndex))
                                                    config.updatePlaylist(pl.copy(uris = uris, names = names))
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.browser_copy_to_list)) },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                            onClick = {
                                                pendingTrackTransfer = PlaylistTrackTransfer(pl, entry.originalIndex, move = false)
                                                trackActionMenuIndex = null
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.browser_move_to_list)) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                            onClick = {
                                                pendingTrackTransfer = PlaylistTrackTransfer(pl, entry.originalIndex, move = true)
                                                trackActionMenuIndex = null
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.common_delete)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                trackActionMenuIndex = null
                                                scope.launch {
                                                    removePlaylistTrackAt(
                                                        context = context,
                                                        config = config,
                                                        pl = pl,
                                                        trackIndex = entry.originalIndex,
                                                        playbackState = playbackState,
                                                        deleteSourceFile = pl.isDirectorySource,
                                                        onStopPlayback = onStopPlayback
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        context.getString(R.string.player_playlist_section),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showCreatePlaylistDialog = true }) {
                        Text(context.getString(R.string.player_new_list))
                    }
                }
                if (playlists.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            context.getString(R.string.player_no_playlists_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(playlists.size) { i ->
                            val pl = playlists[i]
                            val isCurrent = playbackState?.playlistId == pl.id
                            val isDangerousPlaylist = pl.isDirectorySource
                            val rowBackground = when {
                                isDangerousPlaylist && isCurrent -> MaterialTheme.colorScheme.errorContainer
                                isDangerousPlaylist -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
                                isCurrent -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(rowBackground)
                                    .clickable { selectedPlaylistId = pl.id }
                                    .padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pl.displayName(context.getString(R.string.player_unnamed_playlist)),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isDangerousPlaylist) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    val bookmarkCount = playerBookmarks.count { it.playlistId == pl.id }
                                    Text(
                                        context.getString(R.string.browser_playlist_track_bookmark, pl.trackCount, bookmarkCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isDangerousPlaylist) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (pl.previewEnabled) {
                                        Text(
                                            previewListHint(pl),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isDangerousPlaylist) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (isDangerousPlaylist) {
                                        Text(
                                            context.getString(R.string.browser_playlist_dir_list_hint),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                Box {
                                    IconButton(
                                        onClick = { playlistActionMenuIndex = i },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = context.getString(R.string.main_menu),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = playlistActionMenuIndex == i,
                                        onDismissRequest = { playlistActionMenuIndex = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_play_desc)) },
                                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                if (selectedPlaylistId == pl.id) {
                                                    val entries = pl.uris.mapIndexed { idx, uri ->
                                                        PlayerTrackEntry(
                                                            originalIndex = idx,
                                                            uri = uri,
                                                            name = pl.names.getOrElse(idx) { uri.substringAfterLast('/') },
                                                            metadata = trackMetadataCache[uri]
                                                        )
                                                    }
                                                    val filtered = if (trackFilterText.isBlank()) {
                                                        entries
                                                    } else {
                                                        runCatching { Regex(trackFilterText) }.getOrNull()?.let { regex ->
                                                            entries.filter { entry ->
                                                                regex.containsMatchIn(trackFilterTarget(trackFilterField, entry.name, entry.metadata))
                                                            }
                                                        } ?: entries
                                                    }
                                                    if (filtered.isNotEmpty()) {
                                                        startPlaylistFromIndex(pl, filtered.first().originalIndex)
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.player_track_filter_no_result), Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    startPlaylist(pl)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_move_up)) },
                                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                                            enabled = i > 0,
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                if (i > 0) scope.launch {
                                                    val ids = playlists.map { it.id }.toMutableList()
                                                    ids.removeAt(i)
                                                    ids.add(i - 1, pl.id)
                                                    config.updatePlaylistOrder(ids)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_move_down)) },
                                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                                            enabled = i < playlists.size - 1,
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                if (i < playlists.size - 1) scope.launch {
                                                    val ids = playlists.map { it.id }.toMutableList()
                                                    ids.removeAt(i)
                                                    ids.add(i + 1, pl.id)
                                                    config.updatePlaylistOrder(ids)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_rename_playlist)) },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                openRenamePlaylistDialog(pl)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_resort_playlist)) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                                            enabled = pl.isDirectorySource || pl.uris.size >= 2,
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                openSortPlaylistDialog(pl)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_find_duplicate_tracks)) },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                            enabled = pl.uris.size >= 2,
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                openDuplicateSongsDialog(pl)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.player_preview_settings)) },
                                            leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                openPreviewSettingsDialog(pl)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(context.getString(R.string.common_delete)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                playlistActionMenuIndex = null
                                                scope.launch {
                                                    if (playbackState?.playlistId == pl.id) {
                                                        onStopPlayback()
                                                    }
                                                    config.removePlaylist(pl.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PlaybackBottomControlBar(
                isPlaying = playbackState?.isPlaying == true,
                enabled = playbackState != null || restorePlaylist != null,
                onPrev = {
                    if (playbackState != null) onPlayPrev()
                },
                onPlayPause = {
                    if (playbackState != null) onPlayPause()
                    else resumeLastPlayback(restorePlaylist)
                },
                onNext = {
                    if (playbackState != null) onPlayNext()
                }
            )
        }
        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                },
                title = { Text(stringResource(R.string.browser_new_empty_playlist)) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text(stringResource(R.string.browser_playlist_name_label)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = { scope.launch { createEmptyPlaylist(newPlaylistName) } }) {
                        Text(context.getString(R.string.common_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCreatePlaylistDialog = false
                        newPlaylistName = ""
                    }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        pendingTrackTransfer?.let { transfer ->
            val targetPlaylists = playlists.filter { it.id != transfer.source.id && !it.isDirectorySource }
            AlertDialog(
                onDismissRequest = { pendingTrackTransfer = null },
                title = { Text(if (transfer.move) stringResource(R.string.browser_move_to_playlist) else stringResource(R.string.browser_copy_to_playlist)) },
                text = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (targetPlaylists.isEmpty()) {
                            Text(
                                context.getString(R.string.browser_playlist_no_normal_move_target),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            targetPlaylists.forEach { playlist ->
                                OutlinedButton(
                                    onClick = { scope.launch { transferTrackToPlaylist(transfer, playlist) } },
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
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { pendingTrackTransfer = null }) {
                        Text(context.getString(R.string.common_cancel))
                    }
                }
            )
        }
        if (showPreviewSettingsDialog) {
            val target = previewSettingsTarget
            AlertDialog(
                onDismissRequest = {
                    showPreviewSettingsDialog = false
                    previewSettingsTarget = null
                    previewTimeError = null
                },
                title = { Text(context.getString(R.string.player_preview_settings_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(context.getString(R.string.player_preview_enabled), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    context.getString(R.string.player_preview_enabled_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = previewEnabledInput,
                                onCheckedChange = { previewEnabledInput = it }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = previewStartInput,
                            onValueChange = {
                                previewStartInput = it
                                previewTimeError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(context.getString(R.string.player_preview_start_label)) },
                            supportingText = { Text(context.getString(R.string.player_preview_start_hint)) },
                            singleLine = true,
                            enabled = previewEnabledInput
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = previewMaxDurationInput,
                            onValueChange = {
                                previewMaxDurationInput = it
                                previewTimeError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(context.getString(R.string.player_preview_max_duration_label)) },
                            supportingText = { Text(context.getString(R.string.player_preview_max_duration_hint)) },
                            singleLine = true,
                            enabled = previewEnabledInput
                        )
                        previewTimeError?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val settingsTarget = target ?: return@Button
                        val startMs = if (previewEnabledInput) {
                            PlaybackTimeFormat.parseToMs(previewStartInput) ?: run {
                                previewTimeError = context.getString(R.string.player_preview_time_invalid)
                                return@Button
                            }
                        } else {
                            0L
                        }
                        val maxMs = if (previewEnabledInput && previewMaxDurationInput.isNotBlank()) {
                            PlaybackTimeFormat.parseToMs(previewMaxDurationInput) ?: run {
                                previewTimeError = context.getString(R.string.player_preview_time_invalid)
                                return@Button
                            }
                        } else {
                            0L
                        }
                        scope.launch {
                            config.updatePlaylist(
                                settingsTarget.copy(
                                    previewEnabled = previewEnabledInput,
                                    previewStartMs = startMs.coerceAtLeast(0L),
                                    previewMaxDurationMs = maxMs.coerceAtLeast(0L)
                                )
                            )
                            if (playbackState?.playlistId == settingsTarget.id) {
                                val reloadIntent = Intent(context, PlaybackService::class.java).apply {
                                    action = ACTION_RELOAD_PLAYLIST
                                    putExtra(EXTRA_PLAYLIST_ID, settingsTarget.id)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(reloadIntent)
                                } else {
                                    context.startService(reloadIntent)
                                }
                            }
                            showPreviewSettingsDialog = false
                            previewSettingsTarget = null
                            previewTimeError = null
                            Toast.makeText(context, context.getString(R.string.player_preview_saved), Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(context.getString(R.string.common_save)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPreviewSettingsDialog = false
                        previewSettingsTarget = null
                        previewTimeError = null
                    }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        if (showRenamePlaylistDialog) {
            val target = playlistRenameTarget
            AlertDialog(
                onDismissRequest = {
                    showRenamePlaylistDialog = false
                    playlistRenameTarget = null
                    playlistRenameText = ""
                },
                title = { Text(context.getString(R.string.player_rename_playlist_title)) },
                text = {
                    OutlinedTextField(
                        value = playlistRenameText,
                        onValueChange = { playlistRenameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(context.getString(R.string.player_playlist_name_label)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val newName = playlistRenameText.trim()
                        if (newName.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.player_playlist_name_required), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val renameTarget = target ?: return@Button
                        scope.launch {
                            config.updatePlaylist(renameTarget.copy(name = newName, note = ""))
                            showRenamePlaylistDialog = false
                            playlistRenameTarget = null
                            playlistRenameText = ""
                        }
                    }) { Text(context.getString(R.string.common_save)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showRenamePlaylistDialog = false
                        playlistRenameTarget = null
                        playlistRenameText = ""
                    }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        if (showSortPlaylistDialog) {
            val sortTarget = playlistSortTarget
            val sortFieldOptions = listOf(
                PlaylistTrackSortField.ARTIST to R.string.player_resort_field_artist,
                PlaylistTrackSortField.ALBUM to R.string.player_resort_field_album,
                PlaylistTrackSortField.YEAR to R.string.player_resort_field_year,
                PlaylistTrackSortField.DIRECTORY to R.string.player_resort_field_directory,
                PlaylistTrackSortField.RANDOM to R.string.player_resort_field_random
            )
            AlertDialog(
                onDismissRequest = {
                    if (!playlistSortInProgress) {
                        showSortPlaylistDialog = false
                        playlistSortTarget = null
                    }
                },
                title = { Text(context.getString(R.string.player_resort_playlist_title)) },
                text = {
                    if (playlistSortInProgress) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(context.getString(R.string.player_resort_in_progress))
                        }
                    } else {
                        Column {
                            if (sortTarget?.isDirectorySource == true) {
                                Text(
                                    context.getString(R.string.player_resort_directory_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            sortFieldOptions.forEach { (field, labelRes) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { playlistSortField = field }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = playlistSortField == field,
                                        onClick = { playlistSortField = field }
                                    )
                                    Text(context.getString(labelRes))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { playlistSortOrder = PlaylistSortOrder.ASCENDING }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = playlistSortOrder == PlaylistSortOrder.ASCENDING,
                                    onClick = { playlistSortOrder = PlaylistSortOrder.ASCENDING }
                                )
                                Text(context.getString(R.string.player_resort_order_asc))
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { playlistSortOrder = PlaylistSortOrder.DESCENDING }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = playlistSortOrder == PlaylistSortOrder.DESCENDING,
                                    onClick = { playlistSortOrder = PlaylistSortOrder.DESCENDING }
                                )
                                Text(context.getString(R.string.player_resort_order_desc))
                            }
                        }
                    }
                },
                confirmButton = {
                    val canResort = sortTarget != null && (sortTarget.isDirectorySource || sortTarget.uris.size >= 2)
                    Button(
                        enabled = !playlistSortInProgress && canResort,
                        onClick = {
                            val pl = sortTarget ?: return@Button
                            if (!pl.isDirectorySource && pl.uris.size < 2) {
                                Toast.makeText(context, context.getString(R.string.player_resort_too_few), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            playlistSortInProgress = true
                            scope.launch {
                                try {
                                    val updated = resortPlaylistTracks(
                                        context = context,
                                        config = config,
                                        pl = pl,
                                        field = playlistSortField,
                                        order = playlistSortOrder,
                                        playbackState = playbackState,
                                        onStopPlayback = onStopPlayback
                                    )
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.player_resort_done, updated.trackCount),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    playlistSortInProgress = false
                                    showSortPlaylistDialog = false
                                    playlistSortTarget = null
                                }
                            }
                        }
                    ) { Text(context.getString(R.string.common_ok)) }
                },
                dismissButton = {
                    TextButton(
                        enabled = !playlistSortInProgress,
                        onClick = {
                            showSortPlaylistDialog = false
                            playlistSortTarget = null
                        }
                    ) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        if (showDuplicateSongsDialog) {
            val targetPlaylist = duplicateSongsTargetPlaylist
            val duplicateGroupListState = remember(targetPlaylist?.id) { LazyListState() }
            fun closeDuplicateSongsDialog() {
                showDuplicateSongsDialog = false
                duplicateSongsLoading = false
                duplicateSongsMutating = false
                duplicateSongGroups = emptyList()
                duplicateSongsTargetPlaylist = null
            }
            Dialog(
                onDismissRequest = { closeDuplicateSongsDialog() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 620.dp)
                            .padding(16.dp)
                    ) {
                        Text(context.getString(R.string.player_duplicate_tracks_title), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            context.getString(R.string.player_duplicate_tracks_rule_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (targetPlaylist?.isDirectorySource == true) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                context.getString(R.string.player_duplicate_tracks_directory_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (duplicateSongsLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(context.getString(R.string.player_duplicate_tracks_scanning))
                            }
                        } else if (duplicateSongGroups.isEmpty()) {
                            Text(
                                context.getString(R.string.player_duplicate_tracks_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                context.getString(
                                    R.string.player_duplicate_tracks_found_summary,
                                    duplicateSongGroups.size,
                                    duplicateSongGroups.sumOf { it.entries.size }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (duplicateSongsMutating) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        context.getString(R.string.player_duplicate_tracks_mutating),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                state = duplicateGroupListState,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items = duplicateSongGroups, key = { it.key }) { group ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    group.displayTitle,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    context.getString(R.string.player_duplicate_tracks_group_count, group.entries.size),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            group.entries.forEachIndexed { entryIndex, entry ->
                                                Column(Modifier.fillMaxWidth()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.Top,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                context.getString(R.string.player_duplicate_tracks_entry_label, entry.originalIndex + 1),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                entry.metadata?.title?.takeIf { it.isNotBlank() } ?: entry.name,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            val metaText = entry.metadata?.let { formatTrackMetadata(context, it) }.orEmpty()
                                                            if (metaText.isNotBlank()) {
                                                                Text(
                                                                    metaText,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                        TextButton(
                                                            enabled = !duplicateSongsLoading && !duplicateSongsMutating,
                                                            onClick = {
                                                                val currentPlaylist = targetPlaylist ?: return@TextButton
                                                                scope.launch {
                                                                    duplicateSongsMutating = true
                                                                    try {
                                                                        val result = removeTracksFromPlaylistByIndices(
                                                                            context = context,
                                                                            config = config,
                                                                            playlist = currentPlaylist,
                                                                            removeIndices = setOf(entry.originalIndex),
                                                                            playbackState = playbackState,
                                                                            onStopPlayback = onStopPlayback
                                                                        )
                                                                        if (result.removedCount > 0) {
                                                                            Toast.makeText(
                                                                                context,
                                                                                context.getString(R.string.player_duplicate_tracks_delete_done, result.removedCount),
                                                                                Toast.LENGTH_SHORT
                                                                            ).show()
                                                                        }
                                                                        if (result.failedNames.isNotEmpty()) {
                                                                            val firstFailed = result.failedNames.first()
                                                                            Toast.makeText(
                                                                                context,
                                                                                context.getString(
                                                                                    R.string.player_duplicate_tracks_delete_partial_failed,
                                                                                    result.failedNames.size,
                                                                                    firstFailed
                                                                                ),
                                                                                Toast.LENGTH_LONG
                                                                            ).show()
                                                                        }
                                                                        duplicateSongsTargetPlaylist = result.updatedPlaylist
                                                                        duplicateSongGroups = updateDuplicateGroupsAfterRemoval(
                                                                            duplicateSongGroups,
                                                                            result.removedIndices
                                                                        )
                                                                    } finally {
                                                                        duplicateSongsMutating = false
                                                                    }
                                                                }
                                                            }
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = context.getString(R.string.common_delete)
                                                            )
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(context.getString(R.string.common_delete))
                                                        }
                                                    }
                                                    if (entryIndex < group.entries.lastIndex) {
                                                        Spacer(Modifier.height(6.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { closeDuplicateSongsDialog() }) {
                                Text(context.getString(R.string.common_close))
                            }
                        }
                    }
                }
            }
        }
        if (showBulkDeleteTracksDialog && selectedPlaylist != null) {
            val targetPlaylist = selectedPlaylist
            AlertDialog(
                onDismissRequest = {
                    if (!bulkDeleteTracksInProgress) {
                        showBulkDeleteTracksDialog = false
                    }
                },
                title = { Text(context.getString(R.string.player_bulk_delete_tracks_confirm_title)) },
                text = {
                    Column {
                        Text(
                            if (bulkDeleteHiddenMode) {
                                if (targetPlaylist.isDirectorySource) {
                                    context.getString(
                                        R.string.player_bulk_delete_hidden_tracks_confirm_directory,
                                        bulkDeleteTrackCount
                                    )
                                } else {
                                    context.getString(
                                        R.string.player_bulk_delete_hidden_tracks_confirm_regular,
                                        bulkDeleteTrackCount
                                    )
                                }
                            } else if (targetPlaylist.isDirectorySource) {
                                context.getString(
                                    R.string.player_bulk_delete_tracks_confirm_directory,
                                    bulkDeleteTrackCount
                                )
                            } else {
                                context.getString(
                                    R.string.player_bulk_delete_tracks_confirm_regular,
                                    bulkDeleteTrackCount
                                )
                            },
                            color = if (targetPlaylist.isDirectorySource) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (bulkDeleteHiddenMode) {
                                context.getString(R.string.player_bulk_delete_hidden_tracks_scope_filtered)
                            } else if (bulkDeleteWasFiltered) {
                                context.getString(R.string.player_bulk_delete_tracks_scope_filtered)
                            } else {
                                context.getString(R.string.player_bulk_delete_tracks_scope_all)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !bulkDeleteTracksInProgress && bulkDeleteTrackIndices.isNotEmpty(),
                        onClick = {
                            val removeSet = bulkDeleteTrackIndices
                            bulkDeleteTracksInProgress = true
                            scope.launch {
                                try {
                                    val result = removeTracksFromPlaylistByIndices(
                                        context = context,
                                        config = config,
                                        playlist = targetPlaylist,
                                        removeIndices = removeSet,
                                        playbackState = playbackState,
                                        onStopPlayback = onStopPlayback
                                    )
                                    if (result.removedCount > 0) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.player_bulk_delete_tracks_done, result.removedCount),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    if (result.failedNames.isNotEmpty()) {
                                        Toast.makeText(
                                            context,
                                            if (bulkDeleteHiddenMode) {
                                                context.getString(
                                                    R.string.player_bulk_delete_hidden_tracks_partial_failed,
                                                    result.failedNames.size,
                                                    result.failedNames.first()
                                                )
                                            } else {
                                                context.getString(
                                                    R.string.player_bulk_delete_tracks_partial_failed,
                                                    result.failedNames.size,
                                                    result.failedNames.first()
                                                )
                                            },
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } finally {
                                    bulkDeleteTracksInProgress = false
                                    showBulkDeleteTracksDialog = false
                                    bulkDeleteTrackIndices = emptySet()
                                    bulkDeleteTrackCount = 0
                                    bulkDeleteWasFiltered = false
                                    bulkDeleteHiddenMode = false
                                }
                            }
                        }
                    ) {
                        Text(context.getString(R.string.common_delete))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !bulkDeleteTracksInProgress,
                        onClick = {
                            showBulkDeleteTracksDialog = false
                            bulkDeleteHiddenMode = false
                        }
                    ) {
                        Text(context.getString(R.string.common_cancel))
                    }
                }
            )
        }
        if (showAddBookmarkDialog) {
            AlertDialog(
                onDismissRequest = { showAddBookmarkDialog = false },
                title = { Text(context.getString(R.string.player_save_playback_bookmark_title)) },
                text = {
                    Column {
                        Text(
                            context.getString(
                                R.string.player_bookmark_position,
                                bookmarkPendingTrackIndex + 1,
                                formatPlaybackTime(bookmarkPendingPositionMs.toInt())
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (bookmarkPendingTrackName.isNotBlank()) {
                            Text(
                                bookmarkPendingTrackName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = bookmarkNoteInput,
                            onValueChange = { bookmarkNoteInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(context.getString(R.string.player_bookmark_note_label)) },
                            placeholder = { Text(context.getString(R.string.player_bookmark_note_placeholder)) },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pid = bookmarkPendingPlaylistId
                            if (pid == null) return@Button
                            scope.launch {
                                config.addPlayerListBookmark(
                                    playlistId = pid,
                                    dirUri = bookmarkPendingDirUri,
                                    trackIndex = bookmarkPendingTrackIndex,
                                    positionMs = bookmarkPendingPositionMs,
                                    trackName = bookmarkPendingTrackName,
                                    note = bookmarkNoteInput
                                )
                                showAddBookmarkDialog = false
                                Toast.makeText(context, context.getString(R.string.player_bookmark_saved), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) { Text(context.getString(R.string.common_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddBookmarkDialog = false }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        editingBookmarkId?.let { bookmarkId ->
            AlertDialog(
                onDismissRequest = { editingBookmarkId = null },
                title = { Text(context.getString(R.string.player_edit_bookmark_note)) },
                text = {
                    OutlinedTextField(
                        value = editingBookmarkNote,
                        onValueChange = { editingBookmarkNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(context.getString(R.string.common_note)) },
                        minLines = 2,
                        maxLines = 4
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                config.updatePlayerListBookmarkNote(bookmarkId, editingBookmarkNote)
                                editingBookmarkId = null
                            }
                        }
                    ) { Text(context.getString(R.string.common_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { editingBookmarkId = null }) { Text(context.getString(R.string.common_cancel)) }
                }
            )
        }
        if (showBookmarkManagerDialog) {
            Dialog(onDismissRequest = { showBookmarkManagerDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .heightIn(max = 560.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                context.getString(R.string.player_bookmark_manager_title),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { showBookmarkManagerDialog = false }) {
                                Text(context.getString(R.string.common_close))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (playerBookmarks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(context.getString(R.string.player_no_saved_bookmarks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(playerBookmarks.sortedByDescending { it.savedAt }, key = { it.id }) { bm ->
                                    val playlist = bm.playlistId?.let { playlistById[it] }
                                    val jumpEnabled = playlist != null && bm.trackIndex in playlist.uris.indices
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                            Text(
                                                playlist?.name ?: context.getString(R.string.player_playlist_missing),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                context.getString(
                                                    R.string.player_bookmark_position_compact,
                                                    bm.trackIndex + 1,
                                                    formatPlaybackTime(bm.positionMs.toInt())
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (bm.trackName.isNotBlank()) {
                                                Text(
                                                    bm.trackName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (bm.note.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    bm.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        editingBookmarkId = bm.id
                                                        editingBookmarkNote = bm.note
                                                    }
                                                ) {
                                                    Text(context.getString(R.string.common_note))
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            config.deletePlayerListBookmark(bm.id)
                                                        }
                                                    }
                                                ) {
                                                    Text(context.getString(R.string.common_delete))
                                                }
                                                Button(
                                                    onClick = {
                                                        playlist?.let {
                                                            startPlaylistFromIndex(it, bm.trackIndex, bm.positionMs.toInt())
                                                            showBookmarkManagerDialog = false
                                                        }
                                                    },
                                                    enabled = jumpEnabled
                                                ) {
                                                    Text(context.getString(R.string.player_jump))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
