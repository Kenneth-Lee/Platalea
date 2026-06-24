package com.kenny.localmanager.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.session.MediaSession
import android.media.session.PlaybackState as PlatformPlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.kenny.localmanager.MainActivity
import com.kenny.localmanager.R
import com.kenny.localmanager.data.PLAYER_AUDIO_ENGINE_EXO_PLAYER
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_BASS
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_CAR
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_FLAT
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_HEADPHONE
import com.kenny.localmanager.data.PLAYER_AUDIO_PRESET_VOCAL
import com.kenny.localmanager.data.PlayerAudioSettings
import com.kenny.localmanager.data.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

private const val CHANNEL_ID = "playback_channel"
private const val NOTIFICATION_ID = 9001
private const val TAG = "PlaybackService"

const val ACTION_PLAY = "com.kenny.localmanager.player.PLAY"
const val ACTION_STOP = "com.kenny.localmanager.player.STOP"
const val ACTION_PREV = "com.kenny.localmanager.player.PREV"
const val ACTION_NEXT = "com.kenny.localmanager.player.NEXT"
const val ACTION_SEEK = "com.kenny.localmanager.player.SEEK"
const val ACTION_PAUSE = "com.kenny.localmanager.player.PAUSE"
const val ACTION_RESUME = "com.kenny.localmanager.player.RESUME"
const val ACTION_REMOVE_PLAYLIST_TRACK = "com.kenny.localmanager.player.REMOVE_PLAYLIST_TRACK"
const val ACTION_RELOAD_PLAYLIST = "com.kenny.localmanager.player.RELOAD_PLAYLIST"

const val EXTRA_URIS = "uris"
const val EXTRA_NAMES = "names"
const val EXTRA_DIR_URI = "dir_uri"
const val EXTRA_PLAYLIST_ID = "playlist_id"
const val EXTRA_START_INDEX = "start_index"
const val EXTRA_POSITION_MS = "position_ms"
const val EXTRA_START_POSITION_MS = "start_position_ms"

@UnstableApi
class PlaybackService : Service() {

    companion object {
        internal val _playbackState = MutableStateFlow<PlaybackState?>(null)
        val playbackState: StateFlow<PlaybackState?> = _playbackState.asStateFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: Preferences

    private var mediaPlayer: MediaPlayer? = null
    private var exoPlayer: ExoPlayer? = null
    private var playlistUris: List<String> = emptyList()
    private var playlistNames: List<String> = emptyList()
    private var currentTrackMetadata: TrackMetadata = TrackMetadata()
    private var dirUri: String = ""
    private var playlistId: String? = null
    private var playlistName: String? = null
    private var currentIndex = AtomicInteger(0)
    private var progressUpdateRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var bluetoothOutputConnected: Boolean = false
    private var pausedByBluetoothDisconnect: Boolean = false
    private var resumeWhenAudioFocusGained: Boolean = false
    private var isPlayerPrepared: Boolean = false
    private var lastPauseOrigin: PauseOrigin = PauseOrigin.OTHER
    private var playerAudioSettings: PlayerAudioSettings = PlayerAudioSettings()
    private var playerAudioSettingsJob: Job? = null
    private var exoAutoStartWhenReady: Boolean = false
    private var currentPlaybackSourceUri: Uri? = null
    private var currentPlaybackUri: Uri? = null
    private var currentPlaybackSource: String = "direct"
    private var currentDiagnosticsMetadata: PlaybackDiagnostics = PlaybackDiagnostics()
    private var bufferEventCount: Int = 0
    private var playerErrorCount: Int = 0
    private var lastPlayerError: String? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val playbackAudioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    private val binder = LocalBinder()

    private enum class PauseOrigin {
        USER,
        NOISY,
        BLUETOOTH_DISCONNECT,
        AUDIO_FOCUS,
        OTHER
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeWhenAudioFocusGained || lastPauseOrigin == PauseOrigin.AUDIO_FOCUS) {
                    resumeWhenAudioFocusGained = false
                    resumePlayback(skipAudioFocusRequest = true, origin = PauseOrigin.AUDIO_FOCUS)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isCurrentPlayerPlaying()) {
                    resumeWhenAudioFocusGained = true
                    pausePlayback(PauseOrigin.AUDIO_FOCUS)
                } else if (lastPauseOrigin == PauseOrigin.AUDIO_FOCUS) {
                    resumeWhenAudioFocusGained = true
                }
            }
        }
    }

    // 音频输出设备断开（如蓝牙耳机断开）的广播接收器
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (!isCurrentPlayerPlaying()) return
                // 仅在蓝牙输出路径断开导致暂停时，才允许重连后自动续播。
                pausedByBluetoothDisconnect = bluetoothOutputConnected
                pausePlayback(PauseOrigin.NOISY)
            }
        }
    }

    private val bluetoothConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            refreshBluetoothOutputState()
                            tryResumeAfterBluetoothReconnect()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            val wasPlaying = isCurrentPlayerPlaying()
                            refreshBluetoothOutputState()
                            if (wasPlaying) {
                                pausedByBluetoothDisconnect = true
                                pausePlayback(PauseOrigin.BLUETOOTH_DISCONNECT)
                            } else if (lastPauseOrigin == PauseOrigin.NOISY) {
                                pausedByBluetoothDisconnect = true
                            }
                        }
                        else -> refreshBluetoothOutputState()
                    }
                }
            }
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            resumePlayback()
        }

        override fun onPause() {
            pausePlayback(PauseOrigin.USER)
        }

        override fun onSkipToNext() {
            playNext()
        }

        override fun onSkipToPrevious() {
            playPrev()
        }

        override fun onStop() {
            stopPlayback()
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Preferences(applicationContext)
        audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        playerAudioSettingsJob = scope.launch {
            prefs.playerAudioSettings.collect { settings ->
                playerAudioSettings = settings
                applyAudioEffectsToCurrentPlayer()
            }
        }
        createChannel()
        initMediaSession()
        refreshBluetoothOutputState()
        // 注册音频输出设备断开广播
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, filter)
        }
        val bluetoothFilter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothConnectionReceiver, bluetoothFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothConnectionReceiver, bluetoothFilter)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val plId = intent.getStringExtra(EXTRA_PLAYLIST_ID)
                val startIndexHint = intent.getIntExtra(EXTRA_START_INDEX, -1)
                val startPositionHintMs = intent.getIntExtra(EXTRA_START_POSITION_MS, -1)
                if (plId != null) {
                    scope.launch {
                        val pl = withContext(Dispatchers.IO) { prefs.getPlaylistById(plId) }
                        if (pl != null) {
                            startPlayback(
                                pl.uris,
                                pl.names,
                                dir = "",
                                playlistId = pl.id,
                                playlistName = pl.name,
                                startIndexHint = startIndexHint,
                                startPositionHintMs = startPositionHintMs
                            )
                        } else {
                            stopSelf()
                        }
                    }
                } else {
                    val uris = intent.getStringArrayListExtra(EXTRA_URIS) ?: return START_NOT_STICKY
                    val names = intent.getStringArrayListExtra(EXTRA_NAMES) ?: return START_NOT_STICKY
                    val dir = intent.getStringExtra(EXTRA_DIR_URI) ?: ""
                    startPlayback(uris, names, dir, null, null)
                }
            }
            ACTION_STOP -> stopPlayback()
            ACTION_PREV -> playPrev()
            ACTION_NEXT -> playNext()
            ACTION_PAUSE -> pausePlayback(PauseOrigin.USER)
            ACTION_RESUME -> {
                if (hasActivePlayer()) {
                    resumePlayback()
                } else {
                    scope.launch {
                        val lastPlaylistId = withContext(Dispatchers.IO) {
                            prefs.playerLastPlaylistId.first()
                        }
                        if (lastPlaylistId.isNullOrBlank()) {
                            Log.w(TAG, "ACTION_RESUME ignored because there is no saved playlist id")
                            stopSelf()
                            return@launch
                        }
                        val lastPlaylist = withContext(Dispatchers.IO) {
                            prefs.getPlaylistById(lastPlaylistId)
                        }
                        if (lastPlaylist == null || lastPlaylist.uris.isEmpty()) {
                            Log.w(TAG, "ACTION_RESUME failed because saved playlist=$lastPlaylistId is missing or empty")
                            stopSelf()
                            return@launch
                        }
                        startPlayback(
                            uris = lastPlaylist.uris,
                            names = lastPlaylist.names,
                            dir = "",
                            playlistId = lastPlaylist.id,
                            playlistName = lastPlaylist.name
                        )
                    }
                }
            }
            ACTION_SEEK -> {
                val posMs = intent.getIntExtra(EXTRA_POSITION_MS, 0)
                val durationMs = currentPlayerDurationMs()
                val target = if (durationMs > 0) posMs.coerceIn(0, durationMs) else posMs.coerceAtLeast(0)
                seekCurrentPlayerTo(target)
                updateState(positionMs = target)
            }
            ACTION_REMOVE_PLAYLIST_TRACK -> {
                val plId = intent.getStringExtra(EXTRA_PLAYLIST_ID) ?: return START_NOT_STICKY
                val removedIndex = intent.getIntExtra(EXTRA_START_INDEX, -1)
                scope.launch { applyPlaylistTrackRemoval(plId, removedIndex) }
            }
            ACTION_RELOAD_PLAYLIST -> {
                val plId = intent.getStringExtra(EXTRA_PLAYLIST_ID) ?: return START_NOT_STICKY
                val startIndexHint = intent.getIntExtra(EXTRA_START_INDEX, -1)
                val startPositionHintMs = intent.getIntExtra(EXTRA_START_POSITION_MS, -1)
                scope.launch { reloadPlaylistFromStorage(plId, startIndexHint, startPositionHintMs) }
            }
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun currentPlaybackPositionMsSafe(): Int {
        return currentPlayerPositionMs()
    }

    private fun hasActivePlayer(): Boolean = mediaPlayer != null || exoPlayer != null

    private fun isCurrentPlayerPlaying(): Boolean = mediaPlayer?.isPlaying ?: (exoPlayer?.isPlaying ?: false)

    private fun currentPlayerPositionMs(): Int {
        mediaPlayer?.let { mp ->
            return try {
                mp.currentPosition
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to read MediaPlayer current position", e)
                0
            }
        }
        return exoPlayer?.currentPosition?.coerceAtLeast(0L)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
    }

    private fun currentPlayerDurationMs(): Int {
        mediaPlayer?.let { mp ->
            return try {
                mp.duration.coerceAtLeast(0)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to read MediaPlayer duration", e)
                0
            }
        }
        val duration = exoPlayer?.duration ?: return 0
        return if (duration == C.TIME_UNSET || duration < 0) 0 else duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun currentAudioSessionId(): Int = mediaPlayer?.audioSessionId ?: (exoPlayer?.audioSessionId ?: 0)

    private fun seekCurrentPlayerTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        exoPlayer?.seekTo(positionMs.toLong())
    }

    private fun startCurrentPlayer() {
        mediaPlayer?.start()
        exoPlayer?.play()
    }

    private fun pauseCurrentPlayer() {
        mediaPlayer?.pause()
        exoPlayer?.pause()
    }

    private fun releaseCurrentPlayer() {
        releaseAudioEffects()
        mediaPlayer?.release()
        mediaPlayer = null
        exoPlayer?.release()
        exoPlayer = null
        isPlayerPrepared = false
        exoAutoStartWhenReady = false
    }

    private fun resetPlaybackDiagnostics(sourceUri: Uri) {
        currentPlaybackSourceUri = sourceUri
        currentPlaybackUri = sourceUri
        currentPlaybackSource = "direct"
        currentDiagnosticsMetadata = readPlaybackDiagnosticsMetadata(sourceUri)
        bufferEventCount = 0
        playerErrorCount = 0
        lastPlayerError = null
    }

    private fun recordPlayerError(message: String) {
        playerErrorCount++
        lastPlayerError = message
    }

    private fun persistPlaybackState(positionMsOverride: Long? = null) {
        val currentPlaylistId = playlistId
        val currentDirUri = dirUri
        val idx = currentIndex.get()
        val pos = positionMsOverride ?: currentPlaybackPositionMsSafe().toLong()
        scope.launch(Dispatchers.IO) {
            if (currentPlaylistId != null) {
                prefs.setPlayerLastStateForPlaylist(currentPlaylistId, idx, pos)
            } else {
                prefs.setPlayerLastState(currentDirUri, idx, pos)
            }
        }
    }

    private fun startPlayback(
        uris: List<String>,
        names: List<String>,
        dir: String,
        playlistId: String?,
        playlistName: String?,
        startIndexHint: Int = -1,
        startPositionHintMs: Int = -1
    ) {
        if (uris.isEmpty()) {
            stopSelf()
            return
        }
        pausedByBluetoothDisconnect = false
        lastPauseOrigin = PauseOrigin.OTHER
        // 切换列表前先保存当前列表进度，否则再切回来时无法恢复
        val oldPlaylistId = this.playlistId
        if (oldPlaylistId != null && playlistUris.isNotEmpty()) {
            val idx = currentIndex.get()
            if (idx in playlistUris.indices) {
                val pos = currentPlaybackPositionMsSafe().toLong()
                scope.launch(Dispatchers.IO) {
                    prefs.setPlayerLastStateForPlaylist(oldPlaylistId, idx, pos)
                }
            }
        }
        playlistUris = uris
        playlistNames = names
        dirUri = dir
        this.playlistId = playlistId
        this.playlistName = playlistName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        scope.launch {
            var startIndex = 0
            var startPositionMs = 0
            if (playlistId != null) {
                if (startIndexHint in uris.indices) {
                    startIndex = startIndexHint
                    startPositionMs = startPositionHintMs.coerceAtLeast(0)
                } else {
                    val resume = withContext(Dispatchers.IO) { prefs.getPlayerResumeStateForPlaylist(playlistId) }
                    if (resume != null && resume.first in uris.indices) {
                        startIndex = resume.first
                        startPositionMs = resume.second.toInt().coerceAtLeast(0)
                    } else {
                        startIndex = 0
                        startPositionMs = 0
                        Log.i(
                            TAG,
                            "No valid resume state for playlist=$playlistId, fallback to track 0 from beginning"
                        )
                        withContext(Dispatchers.IO) {
                            prefs.setPlayerLastStateForPlaylist(playlistId, 0, 0L)
                        }
                    }
                }
            } else if (dir.isNotEmpty()) {
                val lastDir = withContext(Dispatchers.IO) { prefs.playerLastDirUri.first() }
                val lastIndex = withContext(Dispatchers.IO) { prefs.playerLastIndex.first() }
                val lastPos = withContext(Dispatchers.IO) { prefs.playerLastPositionMs.first() }
                if (lastDir == dir && lastIndex in uris.indices) {
                    startIndex = lastIndex
                    startPositionMs = lastPos.toInt().coerceAtLeast(0)
                }
            }
            currentIndex.set(startIndex)
            playTrackAtIndex(startIndex, startPositionMs)
        }
    }

    private fun playTrackAtIndex(index: Int, seekToMs: Int = 0) {
        if (index !in playlistUris.indices) {
            if (playlistUris.isNotEmpty()) updateState(isPlaying = false)
            return
        }
        pausedByBluetoothDisconnect = false
        resumeWhenAudioFocusGained = false
        lastPauseOrigin = PauseOrigin.OTHER
        releaseCurrentPlayer()

        val uri = Uri.parse(playlistUris[index])
        val name = playlistNames.getOrElse(index) { uri.lastPathSegment ?: "?" }
        currentTrackMetadata = readTrackMetadata(uri)
        resetPlaybackDiagnostics(uri)

        currentIndex.set(index)
        updateState(
            trackIndex = index,
            trackName = name,
            positionMs = seekToMs,
            durationMs = 0,
            isPlaying = false
        )
        updateNotification()

        if (playerAudioSettings.engine == PLAYER_AUDIO_ENGINE_EXO_PLAYER) {
            startExoPlayerTrack(index, uri, uri, name, seekToMs)
        } else {
            startMediaPlayerTrack(index, uri, uri, name, seekToMs)
        }
    }

    private fun startMediaPlayerTrack(index: Int, sourceUri: Uri, playbackUri: Uri, name: String, seekToMs: Int) {
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(playbackAudioAttributes)
                if (playerAudioSettings.keepAwake) {
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                }
                setDataSource(applicationContext, playbackUri)
                setOnPreparedListener {
                    isPlayerPrepared = true
                    applyAudioEffectsToCurrentPlayer()
                    if (seekToMs > 0) it.seekTo(seekToMs.coerceAtMost(it.duration))
                    if (!requestAudioFocusForPlayback()) {
                        updateState(
                            trackIndex = index,
                            trackName = name,
                            positionMs = it.currentPosition,
                            durationMs = it.duration,
                            isPlaying = false
                        )
                        updateNotification()
                        return@setOnPreparedListener
                    }
                    it.start()
                    updateState(isPlaying = true)
                    updateNotification()
                    startProgressUpdates()
                }
                setOnCompletionListener {
                    progressUpdateRunnable?.let(handler::removeCallbacks)
                    playNext()
                }
                setOnErrorListener { _, what, extra ->
                    recordPlayerError("MediaPlayer what=$what extra=$extra")
                    Log.e(TAG, "MediaPlayer error, what=$what, extra=$extra, track=$name, uri=$sourceUri, playbackUri=$playbackUri")
                    removeCurrentTrackFromPlaylistAndAdvance()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            recordPlayerError(e.message ?: e.javaClass.simpleName)
            Log.e(TAG, "Failed to start track index=$index, track=$name, uri=$sourceUri, playbackUri=$playbackUri", e)
            updateState(
                trackIndex = index,
                trackName = name,
                positionMs = 0,
                durationMs = 0,
                isPlaying = false
            )
            removeCurrentTrackFromPlaylistAndAdvance()
        }
    }

    private fun startExoPlayerTrack(index: Int, sourceUri: Uri, playbackUri: Uri, name: String, seekToMs: Int) {
        try {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    if (playerAudioSettings.highQualityOutput) 30_000 else 15_000,
                    if (playerAudioSettings.highQualityOutput) 120_000 else 50_000,
                    1_500,
                    3_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            val exoAudioAttributes = Media3AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            val player = ExoPlayer.Builder(applicationContext)
                .setAudioAttributes(exoAudioAttributes, false)
                .setHandleAudioBecomingNoisy(false)
                .setLoadControl(loadControl)
                .setWakeMode(if (playerAudioSettings.keepAwake) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NONE)
                .build()
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(
                            if (playerAudioSettings.highQualityOutput) {
                                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                            } else {
                                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                            }
                        )
                        .setIsGaplessSupportRequired(playerAudioSettings.highQualityOutput)
                        .build()
                )
                .build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            isPlayerPrepared = true
                            applyAudioEffectsToCurrentPlayer()
                            if (!exoAutoStartWhenReady) {
                                updateState(
                                    trackIndex = index,
                                    trackName = name,
                                    positionMs = currentPlayerPositionMs(),
                                    durationMs = currentPlayerDurationMs(),
                                    isPlaying = isCurrentPlayerPlaying()
                                )
                                updateNotification()
                                return
                            }
                            exoAutoStartWhenReady = false
                            if (!requestAudioFocusForPlayback()) {
                                updateState(
                                    trackIndex = index,
                                    trackName = name,
                                    positionMs = currentPlayerPositionMs(),
                                    durationMs = currentPlayerDurationMs(),
                                    isPlaying = false
                                )
                                updateNotification()
                                return
                            }
                            player.play()
                            updateState(isPlaying = true)
                            updateNotification()
                            startProgressUpdates()
                        }
                        Player.STATE_ENDED -> {
                            progressUpdateRunnable?.let(handler::removeCallbacks)
                            playNext()
                        }
                    }
                }

                override fun onIsLoadingChanged(isLoading: Boolean) {
                    if (isLoading) bufferEventCount++
                    updateState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    recordPlayerError("ExoPlayer ${error.errorCodeName}")
                    Log.e(TAG, "ExoPlayer error, code=${error.errorCode}, track=$name, uri=$sourceUri, playbackUri=$playbackUri", error)
                    removeCurrentTrackFromPlaylistAndAdvance()
                }
            })
            exoPlayer = player
            exoAutoStartWhenReady = true
            player.setMediaItem(MediaItem.fromUri(playbackUri))
            if (seekToMs > 0) player.seekTo(seekToMs.toLong())
            player.prepare()
        } catch (e: Exception) {
            recordPlayerError(e.message ?: e.javaClass.simpleName)
            Log.e(TAG, "Failed to start ExoPlayer track index=$index, track=$name, uri=$sourceUri, playbackUri=$playbackUri", e)
            updateState(
                trackIndex = index,
                trackName = name,
                positionMs = 0,
                durationMs = 0,
                isPlaying = false
            )
            removeCurrentTrackFromPlaylistAndAdvance()
        }
    }

    private suspend fun applyPlaylistTrackRemoval(plId: String, removedIndex: Int) {
        if (plId != playlistId) return
        val pl = withContext(Dispatchers.IO) { prefs.getPlaylistById(plId) }
        if (pl == null) {
            stopPlayback()
            return
        }
        playlistUris = pl.uris
        playlistNames = pl.names
        if (pl.uris.isEmpty()) {
            stopPlayback()
            return
        }
        val cur = currentIndex.get()
        when {
            removedIndex < 0 -> updateState()
            removedIndex < cur -> {
                val newCur = cur - 1
                currentIndex.set(newCur)
                updateState(
                    trackIndex = newCur,
                    trackName = playlistNames.getOrElse(newCur) { "" }
                )
                persistPlaybackState()
            }
            removedIndex == cur -> {
                val newIndex = removedIndex.coerceAtMost(pl.uris.lastIndex)
                playTrackAtIndex(newIndex, 0)
            }
            else -> updateState()
        }
    }

    private suspend fun reloadPlaylistFromStorage(
        plId: String,
        startIndexHint: Int,
        startPositionHintMs: Int
    ) {
        val pl = withContext(Dispatchers.IO) { prefs.getPlaylistById(plId) } ?: return
        if (plId != playlistId) return
        playlistUris = pl.uris
        playlistNames = pl.names
        if (pl.uris.isEmpty()) {
            stopPlayback()
            return
        }
        val index = when {
            startIndexHint in pl.uris.indices -> startIndexHint
            else -> currentIndex.get().coerceIn(0, pl.uris.lastIndex)
        }
        val positionMs = if (startIndexHint in pl.uris.indices) {
            startPositionHintMs.coerceAtLeast(0)
        } else {
            0
        }
        playTrackAtIndex(index, positionMs)
    }

    private fun removeCurrentTrackFromPlaylistAndAdvance() {
        progressUpdateRunnable?.let(handler::removeCallbacks)
        savePosition()
        val idx = currentIndex.get()
        val plId = playlistId
        if (plId == null) {
            playNext()
            return
        }
        scope.launch {
            val pl = withContext(Dispatchers.IO) { prefs.getPlaylistById(plId) }
            if (pl == null || idx !in pl.uris.indices) {
                playNext()
                return@launch
            }
            val newUris = pl.uris.toMutableList().apply { removeAt(idx) }
            val newNames = pl.names.toMutableList().apply {
                if (idx in indices) removeAt(idx)
            }
            if (newUris.isEmpty()) {
                val updated = pl.copy(uris = emptyList(), names = emptyList())
                withContext(Dispatchers.IO) { prefs.updatePlaylist(updated) }
                playlistUris = emptyList()
                playlistNames = emptyList()
                stopPlayback()
                return@launch
            }
            val updated = pl.copy(uris = newUris, names = newNames)
            withContext(Dispatchers.IO) { prefs.updatePlaylist(updated) }
            playlistUris = newUris
            playlistNames = newNames
            val newIndex = idx.coerceAtMost(newUris.lastIndex)
            playTrackAtIndex(newIndex, 0)
        }
    }

    private fun startProgressUpdates() {
        progressUpdateRunnable?.let { handler.removeCallbacks(it) }
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (isCurrentPlayerPlaying()) {
                    updateState(
                        positionMs = currentPlayerPositionMs(),
                        durationMs = currentPlayerDurationMs(),
                        isPlaying = true
                    )
                    updateNotification()
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(progressUpdateRunnable!!)
    }

    private fun updateState(
        trackIndex: Int = currentIndex.get(),
        trackName: String = playlistNames.getOrElse(trackIndex) { "" },
        positionMs: Int = currentPlayerPositionMs(),
        durationMs: Int = currentPlayerDurationMs(),
        isPlaying: Boolean = isCurrentPlayerPlaying()
    ) {
        PlaybackService._playbackState.value = PlaybackState(
            dirUri = dirUri,
            trackIndex = trackIndex,
            totalTracks = playlistUris.size,
            trackName = trackName,
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            playlistId = playlistId,
            playlistName = playlistName,
            metadata = currentTrackMetadata,
            diagnostics = buildPlaybackDiagnostics()
        )
        updateMediaSessionPlaybackState(
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs
        )
        updateMediaSessionMetadata(
            trackName = trackName,
            trackIndex = trackIndex,
            totalTracks = playlistUris.size,
            durationMs = durationMs,
            metadata = currentTrackMetadata
        )
    }

    private fun readTrackMetadata(uri: Uri): TrackMetadata {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(applicationContext, uri)
                TrackMetadata(
                    title = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    albumArtist = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    genre = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    year = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read audio metadata from uri=$uri", e)
            TrackMetadata()
        }
    }

    private fun MediaMetadataRetriever.extractTrimmedMetadata(keyCode: Int): String? =
        extractMetadata(keyCode)?.trim()?.takeIf { it.isNotEmpty() }

    private fun readPlaybackDiagnosticsMetadata(uri: Uri): PlaybackDiagnostics {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(applicationContext, uri)
                PlaybackDiagnostics(
                    mimeType = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                    bitrate = retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                    sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    } else {
                        null
                    },
                    bitsPerSample = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        retriever.extractTrimmedMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    } else {
                        null
                    }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read playback diagnostics metadata from uri=$uri", e)
            PlaybackDiagnostics()
        }
    }

    private fun buildPlaybackDiagnostics(): PlaybackDiagnostics {
        val outputDevice = describeCurrentOutputDevice()
        return currentDiagnosticsMetadata.copy(
            engine = if (playerAudioSettings.engine == PLAYER_AUDIO_ENGINE_EXO_PLAYER) "Media3 ExoPlayer" else "系统 MediaPlayer",
            sourceUri = currentPlaybackSourceUri?.toString().orEmpty(),
            playbackUri = currentPlaybackUri?.toString().orEmpty(),
            playbackSource = currentPlaybackSource,
            outputDevice = outputDevice.first,
            outputDeviceSource = outputDevice.second,
            exoOffloadActive = exoPlayer?.isSleepingForOffload == true,
            bufferEvents = bufferEventCount,
            playerErrors = playerErrorCount,
            lastError = lastPlayerError,
            sourceQuality = describeSourceQuality(currentDiagnosticsMetadata.mimeType),
            audioEffects = describeAudioEffects(),
            highQualityOutput = if (playerAudioSettings.highQualityOutput) "开启" else "关闭"
        )
    }

    private fun describeCurrentOutputDevice(): Pair<String, String> {
        val manager = audioManager ?: return "未知输出" to "AudioManager 不可用"
        return try {
            val mediaRouteDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val mediaAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                manager.getAudioDevicesForAttributes(mediaAttributes)
            } else {
                emptyList()
            }
            if (mediaRouteDevices.isNotEmpty()) {
                return describeAudioDevice(mediaRouteDevices.first()) to "媒体音频路由"
            }
            val outputDevices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .sortedWith(compareBy { audioDeviceFallbackPriority(it.type) })
            outputDevices.firstOrNull()?.let { describeAudioDevice(it) to "可用输出优先级" }
                ?: ("未知输出" to "未发现可用输出设备")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to describe current audio output device", e)
            "未知输出" to "查询失败：${e.javaClass.simpleName}"
        }
    }

    private fun describeSourceQuality(mimeType: String?): String? = when (mimeType?.lowercase()) {
        "audio/mpeg" -> "有损压缩（MP3）"
        "audio/aac", "audio/aac-adts", "audio/mp4", "audio/x-m4a" -> "有损压缩（AAC/MP4）"
        "audio/ogg", "audio/vorbis", "audio/opus" -> "有损压缩（Ogg/Opus）"
        "audio/flac" -> "无损压缩（FLAC）"
        "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "PCM/无压缩"
        else -> null
    }

    private fun describeAudioEffects(): String {
        if (!playerAudioSettings.audioEffectsEnabled) return "关闭"
        return "开启：${audioEffectPresetLabel(playerAudioSettings.effectPreset)}"
    }

    private fun audioEffectPresetLabel(preset: String): String = when (preset) {
        PLAYER_AUDIO_PRESET_VOCAL -> "人声"
        PLAYER_AUDIO_PRESET_BASS -> "低音"
        PLAYER_AUDIO_PRESET_CAR -> "车载"
        PLAYER_AUDIO_PRESET_HEADPHONE -> "耳机"
        else -> "平直"
    }

    private fun describeAudioDevice(device: AudioDeviceInfo): String {
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
        return if (name == null) audioDeviceTypeLabel(device.type) else "${audioDeviceTypeLabel(device.type)}：$name"
    }

    private fun audioDeviceFallbackPriority(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> 0
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> 1
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> 2
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 3
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 4
        else -> 5
    }

    private fun audioDeviceTypeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "扬声器"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙 A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙通话"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙 LE 耳机"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "蓝牙 LE 音箱"
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 音频"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        else -> "输出$type"
    }

    private fun applyAudioEffectsToCurrentPlayer() {
        releaseAudioEffects()
        if (!isPlayerPrepared || !playerAudioSettings.audioEffectsEnabled) return
        val sessionId = currentAudioSessionId()
        if (sessionId <= 0) return
        try {
            equalizer = Equalizer(0, sessionId).apply {
                configureEqualizerPreset(this, playerAudioSettings.effectPreset)
                enabled = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable equalizer for session=$sessionId", e)
        }
        try {
            val bassStrength = when (playerAudioSettings.effectPreset) {
                PLAYER_AUDIO_PRESET_BASS -> 650
                PLAYER_AUDIO_PRESET_CAR -> 450
                PLAYER_AUDIO_PRESET_HEADPHONE -> 300
                else -> 0
            }.toShort()
            bassBoost = BassBoost(0, sessionId).apply {
                if (strengthSupported) setStrength(bassStrength)
                enabled = bassStrength > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable bass boost for session=$sessionId", e)
        }
        try {
            val targetGainMb = when (playerAudioSettings.effectPreset) {
                PLAYER_AUDIO_PRESET_VOCAL -> 250
                PLAYER_AUDIO_PRESET_CAR -> 350
                PLAYER_AUDIO_PRESET_HEADPHONE -> 150
                else -> 0
            }
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(targetGainMb)
                enabled = targetGainMb > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable loudness enhancer for session=$sessionId", e)
        }
    }

    private fun configureEqualizerPreset(equalizer: Equalizer, preset: String) {
        val bandCount = equalizer.numberOfBands.toInt()
        if (bandCount <= 0) return
        val range = equalizer.bandLevelRange
        val minLevel = range[0]
        val maxLevel = range[1]
        for (band in 0 until bandCount) {
            val normalized = if (bandCount == 1) 0f else band.toFloat() / (bandCount - 1).toFloat()
            val level = when (preset) {
                PLAYER_AUDIO_PRESET_VOCAL -> when {
                    normalized < 0.25f -> -250
                    normalized < 0.75f -> 450
                    else -> 100
                }
                PLAYER_AUDIO_PRESET_BASS -> when {
                    normalized < 0.35f -> 550
                    normalized > 0.8f -> 150
                    else -> 0
                }
                PLAYER_AUDIO_PRESET_CAR -> when {
                    normalized < 0.25f -> 450
                    normalized < 0.7f -> 200
                    else -> 350
                }
                PLAYER_AUDIO_PRESET_HEADPHONE -> when {
                    normalized < 0.25f -> 250
                    normalized < 0.75f -> 150
                    else -> 300
                }
                else -> 0
            }.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
            equalizer.setBandLevel(band.toShort(), level)
        }
    }

    private fun releaseAudioEffects() {
        equalizer?.release()
        equalizer = null
        bassBoost?.release()
        bassBoost = null
        loudnessEnhancer?.release()
        loudnessEnhancer = null
    }

    private fun playNext() {
        progressUpdateRunnable?.let(handler::removeCallbacks)
        savePosition()
        val next = currentIndex.get() + 1
        if (next >= playlistUris.size) {
            stopPlayback()
            return
        }
        playTrackAtIndex(next)
    }

    private fun playPrev() {
        progressUpdateRunnable?.let(handler::removeCallbacks)
        savePosition()
        if (hasActivePlayer() && currentPlayerPositionMs() > 3000) {
            seekCurrentPlayerTo(0)
            updateState(positionMs = 0, isPlaying = isCurrentPlayerPlaying())
            return
        }
        val prev = currentIndex.get() - 1
        if (prev < 0) {
            seekCurrentPlayerTo(0)
            updateState(positionMs = 0)
            return
        }
        playTrackAtIndex(prev)
    }

    private fun pausePlayback(origin: PauseOrigin = PauseOrigin.USER) {
        if (isCurrentPlayerPlaying()) {
            pauseCurrentPlayer()
            val pausedPositionMs = currentPlaybackPositionMsSafe().toLong()
            lastPauseOrigin = origin
            if (origin != PauseOrigin.AUDIO_FOCUS) {
                resumeWhenAudioFocusGained = false
            }
            if (origin == PauseOrigin.USER || origin == PauseOrigin.OTHER) {
                pausedByBluetoothDisconnect = false
            }
            progressUpdateRunnable?.let(handler::removeCallbacks)
            persistPlaybackState(positionMsOverride = pausedPositionMs)
            updateState(positionMs = pausedPositionMs.toInt(), isPlaying = false)
            updateNotification()
            if (origin == PauseOrigin.USER) {
                abandonAudioFocus()
            }
        }
    }

    private fun resumePlayback(skipAudioFocusRequest: Boolean = false, origin: PauseOrigin = PauseOrigin.OTHER) {
        if (!hasActivePlayer()) return
        if (!skipAudioFocusRequest && !requestAudioFocusForPlayback()) {
            return
        }
        if (!isPlayerPrepared) {
            resumeWhenAudioFocusGained = true
            return
        }
        if (!isCurrentPlayerPlaying()) {
            startCurrentPlayer()
            pausedByBluetoothDisconnect = false
            resumeWhenAudioFocusGained = false
            lastPauseOrigin = origin
            updateState(isPlaying = true)
            startProgressUpdates()
            updateNotification()
        }
    }

    private fun savePosition() {
        persistPlaybackState()
    }

    private fun stopPlayback() {
        progressUpdateRunnable?.let(handler::removeCallbacks)
        val lastPositionMs = currentPlaybackPositionMsSafe().toLong()
        persistPlaybackState(positionMsOverride = lastPositionMs)
        releaseCurrentPlayer()
        playlistUris = emptyList()
        playlistNames = emptyList()
        currentTrackMetadata = TrackMetadata()
        dirUri = ""
        playlistId = null
        playlistName = null
        pausedByBluetoothDisconnect = false
        resumeWhenAudioFocusGained = false
        lastPauseOrigin = PauseOrigin.OTHER
        PlaybackService._playbackState.value = null
        updateMediaSessionPlaybackState(isPlaying = false, positionMs = 0, durationMs = 0)
        mediaSession?.setMetadata(null)
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestAudioFocusForPlayback(): Boolean {
        val manager = audioManager ?: run {
            Log.e(TAG, "AudioManager unavailable when requesting audio focus")
            return false
        }
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAudioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .build()
                .also { audioFocusRequest = it }
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                resumeWhenAudioFocusGained = false
                true
            }

            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                resumeWhenAudioFocusGained = true
                Log.i(TAG, "Audio focus request delayed, waiting for focus gain before playback resumes")
                false
            }

            else -> {
                resumeWhenAudioFocusGained = false
                Log.w(TAG, "Audio focus request failed with result=$result")
                false
            }
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "LocalManagerPlayback").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(mediaSessionCallback)
            isActive = true
        }
    }

    private fun updateMediaSessionMetadata(
        trackName: String,
        trackIndex: Int,
        totalTracks: Int,
        durationMs: Int,
        metadata: TrackMetadata
    ) {
        val safeTrackName = metadata.title?.ifBlank { null }
            ?: trackName.ifBlank { getString(R.string.playback_notification_title) }
        val safeTrackNumber = (trackIndex + 1).coerceAtLeast(1).toLong()
        val safeTotal = totalTracks.coerceAtLeast(0).toLong()
        val safeDuration = durationMs.coerceAtLeast(0).toLong()
        val artistName = metadata.artist?.ifBlank { null }
            ?: metadata.albumArtist?.ifBlank { null }
            ?: playlistName?.ifBlank { null }
            ?: getString(R.string.app_name)
        val albumName = metadata.album?.ifBlank { null }
            ?: playlistName?.ifBlank { null }
            ?: getString(R.string.app_name)
        val sessionMetadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, safeTrackName)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, safeTrackName)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, albumName)
            .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, safeTrackNumber)
            .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, safeTotal)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, safeDuration)
            .build()
        mediaSession?.setMetadata(sessionMetadata)
    }

    private fun updateMediaSessionPlaybackState(isPlaying: Boolean, positionMs: Int, durationMs: Int) {
        val actions = PlatformPlaybackState.ACTION_PLAY or
            PlatformPlaybackState.ACTION_PAUSE or
            PlatformPlaybackState.ACTION_PLAY_PAUSE or
            PlatformPlaybackState.ACTION_SKIP_TO_NEXT or
            PlatformPlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlatformPlaybackState.ACTION_STOP or
            PlatformPlaybackState.ACTION_SEEK_TO
        val clampedPosition = positionMs.coerceAtLeast(0).toLong().coerceAtMost(durationMs.coerceAtLeast(0).toLong())
        val state = if (isPlaying) PlatformPlaybackState.STATE_PLAYING else PlatformPlaybackState.STATE_PAUSED
        val playbackState = PlatformPlaybackState.Builder()
            .setActions(actions)
            .setState(state, clampedPosition, if (isPlaying) 1.0f else 0.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun refreshBluetoothOutputState() {
        bluetoothOutputConnected = isBluetoothOutputConnected()
    }

    private fun isBluetoothOutputConnected(): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return false
        return try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun tryResumeAfterBluetoothReconnect() {
        if (!pausedByBluetoothDisconnect) return
        if (!bluetoothOutputConnected) return
        if (isCurrentPlayerPlaying()) {
            pausedByBluetoothDisconnect = false
            return
        }
        resumePlayback(origin = PauseOrigin.BLUETOOTH_DISCONNECT)
    }

    private fun buildNotification(): Notification {
        val state = _playbackState.value
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prev = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val next = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseAction = if (state?.isPlaying == true) ACTION_PAUSE else ACTION_RESUME
        val pauseIcon = if (state?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val pauseText = if (state?.isPlaying == true) getString(R.string.playback_pause) else getString(R.string.playback_resume)
        val playPause = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).setAction(pauseAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = state?.trackName ?: getString(R.string.playback_notification_title)
        val text = if (state != null) "${state.trackIndex + 1} / ${state.totalTracks}" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.playback_prev), prev)
            .addAction(pauseIcon, pauseText, playPause)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.playback_next), next)
            .addAction(android.R.drawable.ic_delete, getString(R.string.playback_stop), stop)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        progressUpdateRunnable?.let(handler::removeCallbacks)
        playerAudioSettingsJob?.cancel()
        playerAudioSettingsJob = null
        if (playlistUris.isNotEmpty()) {
            persistPlaybackState(positionMsOverride = currentPlaybackPositionMsSafe().toLong())
        }
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(bluetoothConnectionReceiver)
        } catch (_: Exception) {}
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
        abandonAudioFocus()
        releaseAudioEffects()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlayerPrepared = false
        resumeWhenAudioFocusGained = false
        PlaybackService._playbackState.value = null
        super.onDestroy()
    }

    fun updateNotification() {
        if (PlaybackService._playbackState.value == null) return
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification())
    }
}
