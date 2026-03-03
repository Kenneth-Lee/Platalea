package com.kenny.localmanager.util

import java.net.NetworkInterface

/** 获取本机局域网 IPv4 地址（非回环），用于显示连接说明 */
fun getLocalIpAddress(): String? {
    return try {
        NetworkInterface.getNetworkInterfaces()?.toList()?.flatMap { ni ->
            ni.inetAddresses.toList().mapNotNull { addr ->
                if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false)
                    addr.hostAddress else null
            }
        }?.firstOrNull()
    } catch (_: Exception) {
        null
    }
}
