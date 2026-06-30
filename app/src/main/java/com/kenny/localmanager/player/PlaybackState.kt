package com.kenny.localmanager.player

/**
 * 播放器状态，供 UI 显示及下拉菜单控制。
 * @param playlistId 若来自播放列表则非空；否则与 dirUri 兼容旧逻辑。
 */
data class PlaybackState(
    val dirUri: String,
    val trackIndex: Int,
    val totalTracks: Int,
    val trackName: String,
    val positionMs: Int,
    val durationMs: Int,
    val isPlaying: Boolean,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val previewActive: Boolean = false,
    val previewWindowEndMs: Int? = null,
    val metadata: TrackMetadata = TrackMetadata(),
    val diagnostics: PlaybackDiagnostics = PlaybackDiagnostics()
)

data class TrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: String? = null
)

data class PlaybackDiagnostics(
    val engine: String = "",
    val sourceUri: String = "",
    val playbackUri: String = "",
    val playbackSource: String = "direct",
    val outputDevice: String = "",
    val outputDeviceSource: String = "",
    val exoOffloadActive: Boolean = false,
    val bufferEvents: Int = 0,
    val playerErrors: Int = 0,
    val lastError: String? = null,
    val mimeType: String? = null,
    val sourceQuality: String? = null,
    val bitrate: String? = null,
    val sampleRate: String? = null,
    val bitsPerSample: String? = null,
    val audioEffects: String = "",
    val highQualityOutput: String = ""
)
