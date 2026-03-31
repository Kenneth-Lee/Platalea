package com.kenny.localmanager

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build

private const val SHORTCUT_ID_PREFIX = "local_manager_"
const val SHORTCUT_TAB_PLAYER = "player"
const val SHORTCUT_TAB_QUICK_NOTE = "quick_note"
const val ACTION_SHORTCUT_PINNED = "com.kenny.localmanager.action.SHORTCUT_PINNED"
const val EXTRA_SHORTCUT_NEXT_TAB = "extra_shortcut_next_tab"

private data class ShortcutMeta(
    val shortLabel: String,
    val longLabel: String
)

private fun shortcutMetaForTab(context: Context, tabKey: String): ShortcutMeta? {
    return when (tabKey) {
        SHORTCUT_TAB_PLAYER -> ShortcutMeta(
            context.getString(R.string.shortcut_player_short_label),
            context.getString(R.string.shortcut_player_long_label)
        )
        SHORTCUT_TAB_QUICK_NOTE -> ShortcutMeta(
            context.getString(R.string.shortcut_quick_note_short_label),
            context.getString(R.string.shortcut_quick_note_long_label)
        )
        else -> null
    }
}

fun requestPinnedTabShortcut(
    context: Context,
    tabKey: String,
    callbackIntent: PendingIntent? = null
): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return context.getString(R.string.shortcut_error_old_system)
    }
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        ?: return context.getString(R.string.shortcut_error_manager_unavailable)
    if (!shortcutManager.isRequestPinShortcutSupported) {
        return context.getString(R.string.shortcut_error_unsupported_launcher)
    }
    val meta = shortcutMetaForTab(context, tabKey)
        ?: return context.getString(R.string.shortcut_error_unsupported_tab, tabKey)

    val launchIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(MainActivity.LAUNCH_TARGET_EXTRA, tabKey)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    val shortcut = ShortcutInfo.Builder(context, "$SHORTCUT_ID_PREFIX$tabKey")
        .setShortLabel(meta.shortLabel)
        .setLongLabel(meta.longLabel)
        .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher))
        .setIntent(launchIntent)
        .build()

    return if (shortcutManager.requestPinShortcut(shortcut, callbackIntent?.intentSender)) {
        null
    } else {
        context.getString(R.string.shortcut_error_request_rejected)
    }
}

fun buildShortcutPinnedCallback(
    context: Context,
    nextTabKey: String,
    requestCode: Int
): PendingIntent {
    val callbackIntent = Intent(context, ShortcutPinnedReceiver::class.java).apply {
        action = ACTION_SHORTCUT_PINNED
        putExtra(EXTRA_SHORTCUT_NEXT_TAB, nextTabKey)
    }
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        callbackIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
