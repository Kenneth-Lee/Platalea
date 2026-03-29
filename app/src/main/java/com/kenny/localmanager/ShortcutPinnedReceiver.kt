package com.kenny.localmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SHORTCUT_PINNED) return
        val nextTab = intent.getStringExtra(EXTRA_SHORTCUT_NEXT_TAB).orEmpty()
        if (nextTab.isBlank()) return

        val error = requestPinnedTabShortcut(context, nextTab)
        if (error != null) {
            Log.w("ShortcutPinnedReceiver", "request second shortcut failed: $error")
        }
    }
}
