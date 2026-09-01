package com.kenny.localmanager.family

internal fun parseTotalSizeFromContentRange(header: String?): Long? {
    if (header.isNullOrBlank()) return null
    val total = header.substringAfter('/', "").trim().toLongOrNull()
    return total?.takeIf { it > 0 }
}
