package com.kenny.localmanager.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/** 获取本机局域网 IPv4 地址（非回环），用于显示连接说明 */
fun getLocalIpAddress(): String? {
    return try {
        val scored = NetworkInterface.getNetworkInterfaces()
            ?.toList()
            ?.flatMap { ni ->
                ni.inetAddresses.toList().mapNotNull { addr ->
                    val ipv4 = (addr as? Inet4Address)?.hostAddress?.trim().orEmpty()
                    if (ipv4.isEmpty() || addr.isLoopbackAddress) return@mapNotNull null
                    val score = scoreIpv4Candidate(ni, ipv4)
                    Triple(score, ipv4, ni.name.orEmpty())
                }
            }
            .orEmpty()
        scored.maxByOrNull { it.first }?.second
    } catch (_: Exception) {
        null
    }
}

private fun scoreIpv4Candidate(networkInterface: NetworkInterface, ip: String): Int {
    val name = networkInterface.name?.lowercase(Locale.ROOT).orEmpty()
    var score = 0

    // Prefer RFC1918/local addresses for LAN discovery and local service advertisement.
    if (isPrivateIpv4(ip)) score += 100
    if (ip.startsWith("169.254.")) score -= 40

    if (name.startsWith("wlan") || name.contains("wifi") || name.startsWith("ap")) score += 40
    if (name.startsWith("eth") || name.startsWith("en")) score += 25
    if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") || name.startsWith("wwan")) {
        score -= 60
    }

    runCatching {
        if (!networkInterface.isUp) score -= 100
        if (networkInterface.isVirtual) score -= 20
        if (networkInterface.isPointToPoint) score -= 20
    }

    return score
}

private fun isPrivateIpv4(ip: String): Boolean {
    val parts = ip.split('.')
    if (parts.size != 4) return false
    val a = parts[0].toIntOrNull() ?: return false
    val b = parts[1].toIntOrNull() ?: return false
    if (a !in 0..255 || b !in 0..255) return false
    return when {
        a == 10 -> true
        a == 172 && b in 16..31 -> true
        a == 192 && b == 168 -> true
        a == 127 -> true
        a == 169 && b == 254 -> true
        else -> false
    }
}
