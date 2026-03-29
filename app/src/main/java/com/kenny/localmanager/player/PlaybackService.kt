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
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState as PlatformPlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kenny.localmanager.MainActivity
import com.kenny.localmanager.R
import com.kenny.localmanager.data.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

const val ACTION_PLAY = "com.kenny.localmanager.player.PLAY"
const val ACTION_STOP = "com.kenny.localmanager.player.STOP"
const val ACTION_PREV = "com.kenny.localmanager.player.PREV"
const val ACTION_NEXT = "com.kenny.localmanager.player.NEXT"
const val ACTION_SEEK = "com.kenny.localmanager.player.SEEK"
const val ACTION_PAUSE = "com.kenny.localmanager.player.PAUSE"
const val ACTION_RESUME = "com.kenny.localmanager.player.RESUME"

const val EXTRA_URIS = "uris"
const val EXTRA_NAMES = "names"
const val EXTRA_DIR_URI = "dir_uri"
const val EXTRA_PLAYLIST_ID = "playlist_id"
const val EXTRA_START_INDEX = "start_index"
const val EXTRA_POSITION_MS = "position_ms"
const val EXTRA_START_POSITION_MS = "start_position_ms"

class PlaybackService : Service() {

    companion object {
        internal val _playbackState = MutableStateFlow<PlaybackState?>(null)
        val playbackState: StateFlow<PlaybackState?> = _playbackState.asStateFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: Preferences

    private var mediaPlayer: MediaPlayer? = null
    private var playlistUris: List<String> = emptyList()
    private var playlistNames: List<String> = emptyList()
    private var dirUri: String = ""
    private var playlistId: String? = null
    private var playlistName: String? = null
    private var currentIndex = AtomicInteger(0)
    private var progressUpdateRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var mediaSession: MediaSession? = null
    private var bluetoothOutputConnected: Boolean = false
    private var pausedByBluetoothDisconnect: Boolean = false
    private var lastPauseOrigin: PauseOrigin = PauseOrigin.OTHER

    private val binder = LocalBinder()

    private enum class PauseOrigin {
        USER,
        NOISY,
        BLUETOOTH_DISCONNECT,
        OTHER
    }

    // 音频输出设备断开（如蓝牙耳机断开）的广播接收器
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val mp = mediaPlayer ?: return
                if (!mp.isPlaying) return
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
                            val wasPlaying = mediaPlayer?.isPlaying == true
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
            ACTION_RESUME -> resumePlayback()
            ACTION_SEEK -> {
                val posMs = intent.getIntExtra(EXTRA_POSITION_MS, 0)
                mediaPlayer?.let { mp ->
                    val target = posMs.coerceIn(0, mp.duration)
                    mp.seekTo(target)
                    updateState(positionMs = target)
                }
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
                val pos = (mediaPlayer?.currentPosition ?: 0).toLong()
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
        lastPauseOrigin = PauseOrigin.OTHER
        mediaPlayer?.release()
        mediaPlayer = null

        val uri = Uri.parse(playlistUris[index])
        val name = playlistNames.getOrElse(index) { uri.lastPathSegment ?: "?" }

        try {
            val mp = MediaPlayer().apply {
                setDataSource(applicationContext, uri)
                setOnPreparedListener {
                    if (seekToMs > 0) it.seekTo(seekToMs.coerceAtMost(it.duration))
                    it.start()
                    updateState(isPlaying = true)
                    updateNotification()
                    startProgressUpdates()
                }
                setOnCompletionListener {
                    handler.removeCallbacks(progressUpdateRunnable ?: return@setOnCompletionListener)
                    playNext()
                }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
            mediaPlayer = mp
            currentIndex.set(index)
            updateState(
                trackIndex = index,
                trackName = name,
                positionMs = seekToMs,
                durationMs = 0,
                isPlaying = false
            )
            updateNotification()
        } catch (e: Exception) {
            updateState(
                trackIndex = index,
                trackName = name,
                positionMs = 0,
                durationMs = 0,
                isPlaying = false
            )
            playNext()
        }
    }

    private fun startProgressUpdates() {
        progressUpdateRunnable?.let { handler.removeCallbacks(it) }
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                val mp = mediaPlayer ?: return
                if (mp.isPlaying) {
                    updateState(
                        positionMs = mp.currentPosition,
                        durationMs = mp.duration,
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
        positionMs: Int = mediaPlayer?.currentPosition ?: 0,
        durationMs: Int = mediaPlayer?.duration ?: 0,
        isPlaying: Boolean = mediaPlayer?.isPlaying ?: false
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
            playlistName = playlistName
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
            durationMs = durationMs
        )
    }

    private fun playNext() {
        handler.removeCallbacks(progressUpdateRunnable ?: return)
        savePosition()
        val next = currentIndex.get() + 1
        if (next >= playlistUris.size) {
            stopPlayback()
            return
        }
        playTrackAtIndex(next)
    }

    private fun playPrev() {
        handler.removeCallbacks(progressUpdateRunnable ?: return)
        savePosition()
        val mp = mediaPlayer
        if (mp != null && mp.currentPosition > 3000) {
            mp.seekTo(0)
            updateState(positionMs = 0, isPlaying = mp.isPlaying)
            return
        }
        val prev = currentIndex.get() - 1
        if (prev < 0) {
            mediaPlayer?.seekTo(0)
            updateState(positionMs = 0)
            return
        }
        playTrackAtIndex(prev)
    }

    private fun pausePlayback(origin: PauseOrigin = PauseOrigin.USER) {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            lastPauseOrigin = origin
            if (origin == PauseOrigin.USER || origin == PauseOrigin.OTHER) {
                pausedByBluetoothDisconnect = false
            }
            progressUpdateRunnable?.let { handler.removeCallbacks(it) }
            updateState(isPlaying = false)
            updateNotification()
        }
    }

    private fun resumePlayback() {
        val mp = mediaPlayer ?: return
        if (!mp.isPlaying) {
            mp.start()
            pausedByBluetoothDisconnect = false
            lastPauseOrigin = PauseOrigin.OTHER
            updateState(isPlaying = true)
            startProgressUpdates()
            updateNotification()
        }
    }

    private fun savePosition() {
        scope.launch(Dispatchers.IO) {
            val idx = currentIndex.get()
            val pos = (mediaPlayer?.currentPosition ?: 0).toLong()
            if (playlistId != null) {
                prefs.setPlayerLastStateForPlaylist(playlistId!!, idx, pos)
            } else {
                prefs.setPlayerLastState(dirUri, idx, pos)
            }
        }
    }

    private fun stopPlayback() {
        handler.removeCallbacks(progressUpdateRunnable ?: return)
        savePosition()
        scope.launch(Dispatchers.IO) {
            val idx = currentIndex.get()
            val pos = (mediaPlayer?.currentPosition ?: 0).toLong()
            if (playlistId != null) {
                prefs.setPlayerLastStateForPlaylist(playlistId!!, idx, pos)
            } else {
                prefs.setPlayerLastState(dirUri, idx, pos)
            }
        }
        mediaPlayer?.release()
        mediaPlayer = null
        playlistUris = emptyList()
        playlistNames = emptyList()
        dirUri = ""
        playlistId = null
        playlistName = null
        pausedByBluetoothDisconnect = false
        lastPauseOrigin = PauseOrigin.OTHER
        PlaybackService._playbackState.value = null
        updateMediaSessionPlaybackState(isPlaying = false, positionMs = 0, durationMs = 0)
        mediaSession?.setMetadata(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        durationMs: Int
    ) {
        val safeTrackName = trackName.ifBlank { getString(R.string.playback_notification_title) }
        val safeTrackNumber = (trackIndex + 1).coerceAtLeast(1).toLong()
        val safeTotal = totalTracks.coerceAtLeast(0).toLong()
        val safeDuration = durationMs.coerceAtLeast(0).toLong()
        val albumName = playlistName?.ifBlank { null } ?: getString(R.string.app_name)
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, safeTrackName)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, safeTrackName)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, albumName)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, albumName)
            .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, safeTrackNumber)
            .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, safeTotal)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, safeDuration)
            .build()
        mediaSession?.setMetadata(metadata)
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
        val mp = mediaPlayer ?: return
        if (!pausedByBluetoothDisconnect) return
        if (!bluetoothOutputConnected) return
        if (mp.isPlaying) {
            pausedByBluetoothDisconnect = false
            return
        }
        resumePlayback()
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
        handler.removeCallbacks(progressUpdateRunnable ?: return)
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
        mediaPlayer?.release()
        mediaPlayer = null
        PlaybackService._playbackState.value = null
        super.onDestroy()
    }

    fun updateNotification() {
        if (PlaybackService._playbackState.value == null) return
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification())
    }
}
