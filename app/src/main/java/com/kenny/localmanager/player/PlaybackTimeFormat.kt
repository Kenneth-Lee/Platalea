package com.kenny.localmanager.player

import java.util.Locale

object PlaybackTimeFormat {
    fun formatMs(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0) / 1000)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    /** 支持纯秒数，或 m:ss / mm:ss / h:mm:ss。 */
    fun parseToMs(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0L
        if (trimmed.all { it.isDigit() }) {
            return trimmed.toLongOrNull()?.times(1000)
        }
        val parts = trimmed.split(':')
        if (parts.isEmpty() || parts.any { it.isBlank() || !it.all(Char::isDigit) }) return null
        val numbers = parts.map { it.toLong() }
        val seconds = when (numbers.size) {
            2 -> numbers[0] * 60 + numbers[1]
            3 -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
            else -> return null
        }
        return seconds * 1000
    }
}
