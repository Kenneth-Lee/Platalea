package com.kenny.localmanager.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import com.kenny.localmanager.data.Preferences
import com.kenny.localmanager.player.ACTION_NEXT
import com.kenny.localmanager.player.ACTION_PAUSE
import com.kenny.localmanager.player.ACTION_PREV
import com.kenny.localmanager.player.ACTION_RESUME
import com.kenny.localmanager.player.ACTION_SEEK
import com.kenny.localmanager.player.ACTION_STOP
import com.kenny.localmanager.player.EXTRA_POSITION_MS
import com.kenny.localmanager.player.PlaybackService
import com.kenny.localmanager.player.PlaybackState

data class PlayerTabRouteState(
    val context: Context,
    val prefs: Preferences,
    val playbackState: PlaybackState?,
    val onRequestExitApp: () -> Unit
)

@Composable
fun PlayerTabRoute(state: PlayerTabRouteState) {
    PlaybackScreen(
        prefs = state.prefs,
        playbackState = state.playbackState,
        onStopPlayback = {
            val intent = Intent(state.context, PlaybackService::class.java).setAction(ACTION_STOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) state.context.startForegroundService(intent) else state.context.startService(intent)
        },
        onPlayPrev = {
            val intent = Intent(state.context, PlaybackService::class.java).setAction(ACTION_PREV)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) state.context.startForegroundService(intent) else state.context.startService(intent)
        },
        onPlayNext = {
            val intent = Intent(state.context, PlaybackService::class.java).setAction(ACTION_NEXT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) state.context.startForegroundService(intent) else state.context.startService(intent)
        },
        onPlayPause = {
            val action = if (state.playbackState?.isPlaying == true) ACTION_PAUSE else ACTION_RESUME
            val intent = Intent(state.context, PlaybackService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) state.context.startForegroundService(intent) else state.context.startService(intent)
        },
        onSeek = { positionMs ->
            val intent = Intent(state.context, PlaybackService::class.java).apply {
                action = ACTION_SEEK
                putExtra(EXTRA_POSITION_MS, positionMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) state.context.startForegroundService(intent) else state.context.startService(intent)
        },
        showBackButton = false,
        onDismiss = state.onRequestExitApp
    )
}
