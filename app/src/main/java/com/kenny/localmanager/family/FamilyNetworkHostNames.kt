package com.kenny.localmanager.family

import android.content.Context
import android.os.Build
import android.provider.Settings

object FamilyNetworkHostNames {
    const val TXT_HOST_NAME = "host_name"
    private const val LEGACY_PREFIX = "LocalManager-"

    fun resolveDisplayHostName(
        context: Context,
        configuredHostName: String?,
        instanceId: String
    ): String {
        configuredHostName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val deviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (deviceName != null) return deviceName
        val model = Build.MODEL?.trim()?.takeIf { it.isNotEmpty() }
        if (model != null) return model
        val suffix = instanceId.takeLast(4).ifBlank { "dev" }
        return "Android-$suffix"
    }

    /** mDNS 服务实例名：尽量保留可读字符，去掉 DNS-SD 不友好的符号。 */
    fun sanitizeMdnsServiceName(displayHostName: String): String {
        val trimmed = displayHostName.trim()
        if (trimmed.isEmpty()) return fallbackAsciiName()
        val cleaned = trimmed
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[\\x00-\\x1F]"), "")
            .trim('-', '.', ' ')
            .take(63)
        if (cleaned.isNotEmpty() && isMdnsSafe(cleaned)) return cleaned
        val ascii = trimmed
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^A-Za-z0-9\\-_.]"), "")
            .trim('-', '.')
            .take(63)
        if (ascii.isNotEmpty()) return ascii
        return fallbackAsciiName()
    }

    fun displayHostName(serviceName: String, attributes: Map<String, String>): String {
        attributes[TXT_HOST_NAME]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return formatLegacyServiceName(serviceName)
    }

    fun formatLegacyServiceName(serviceName: String): String {
        val trimmed = serviceName.trim()
        if (trimmed.startsWith(LEGACY_PREFIX, ignoreCase = true)) {
            return trimmed.removePrefix(LEGACY_PREFIX).ifBlank { trimmed }
        }
        return trimmed
    }

    private fun isMdnsSafe(value: String): Boolean {
        if (value.startsWith('-') || value.endsWith('-')) return false
        return value.none { it.code in 0..0x1F || it == 127.toChar() }
    }

    private fun fallbackAsciiName(): String {
        val suffix = (System.currentTimeMillis() % 100_000).toString()
        return "host-$suffix"
    }
}
