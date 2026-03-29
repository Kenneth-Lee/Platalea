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

private fun shortcutMetaForTab(tabKey: String): ShortcutMeta? {
    return when (tabKey) {
        SHORTCUT_TAB_PLAYER -> ShortcutMeta("播放器", "本地管家 - 播放器")
        SHORTCUT_TAB_QUICK_NOTE -> ShortcutMeta("速记", "本地管家 - 速记")
        else -> null
    }
}

fun requestPinnedTabShortcut(
    context: Context,
    tabKey: String,
    callbackIntent: PendingIntent? = null
): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return "系统版本过低：仅 Android 8.0+ 支持固定桌面快捷方式"
    }
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        ?: return "无法获取 ShortcutManager，创建快捷方式失败"
    if (!shortcutManager.isRequestPinShortcutSupported) {
        return "当前桌面不支持固定快捷方式"
    }
    val meta = shortcutMetaForTab(tabKey)
        ?: return "不支持为该页面创建桌面快捷方式：$tabKey"

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
        "桌面拒绝了快捷方式请求，请确认桌面支持并重试"
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
