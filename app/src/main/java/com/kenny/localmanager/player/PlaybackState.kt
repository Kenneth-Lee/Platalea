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
    val playlistName: String? = null
)
