package com.kenny.localmanager.player

/**
 * 播放器状态，供 UI 显示及下拉菜单控制。
 */
data class PlaybackState(
    val dirUri: String,
    val trackIndex: Int,
    val totalTracks: Int,
    val trackName: String,
    val positionMs: Int,
    val durationMs: Int,
    val isPlaying: Boolean
)
