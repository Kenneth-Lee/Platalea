package com.kenny.localmanager.file

import java.util.Calendar

/**
 * 文件查找的大小/时间条件解析（纯逻辑，无 Android 依赖，供 UI 与单元测试复用）。
 *
 * 大小范围："1m-2m"（下限-上限）；"1m-" 不限上限；"-2m" 不限下限；留空不限。
 * 数值支持小数，单位 b/k/m/g/t（大小写不敏感，可带 b 后缀如 mb）。
 *
 * 修改时间："3d"/"4m"/"1y" 表示最近 3 天/4 个月/1 年内修改；
 * 前缀 - （如 "-3d"）表示该时长之前修改；留空不限。月/年按日历周期换算。
 */

internal data class SizeRange(
    val minBytes: Long?,
    val maxBytes: Long?
)

internal data class TimeRange(
    val afterMillis: Long?,
    val beforeMillis: Long?
)

private val sizeValueRegex = Regex(
    """^([0-9]+(?:\.[0-9]+)?)\s*([bkmtg](?:b)?)?$""",
    RegexOption.IGNORE_CASE
)

private val relativeTimeRegex = Regex(
    """^(-?)([0-9]+)\s*([dmy])$""",
    RegexOption.IGNORE_CASE
)

private fun parseSizeValue(text: String): Long? {
    val match = sizeValueRegex.matchEntire(text) ?: return null
    val value = match.groupValues[1].toDoubleOrNull() ?: return null
    if (!value.isFinite() || value < 0) return null
    val bytes = when (match.groupValues[2].lowercase()) {
        "", "b" -> value
        "k", "kb" -> value * 1024
        "m", "mb" -> value * 1024 * 1024
        "g", "gb" -> value * 1024.0 * 1024 * 1024
        "t", "tb" -> value * 1024.0 * 1024 * 1024 * 1024
        else -> return null
    }
    return bytes.toLong()
}

/** 解析大小范围；返回 null 表示格式无法识别（下限大于上限同样视为格式错误）。 */
internal fun parseSizeRange(raw: String): SizeRange? {
    val text = raw.trim()
    if (text.isEmpty()) return SizeRange(null, null)
    if ('-' !in text) return null
    val parts = text.split('-', limit = 2)
    val lower = parts.getOrNull(0)?.trim().orEmpty()
    val upper = parts.getOrNull(1)?.trim().orEmpty()
    val minBytes = if (lower.isEmpty()) null else parseSizeValue(lower) ?: return null
    val maxBytes = if (upper.isEmpty()) null else parseSizeValue(upper) ?: return null
    if (minBytes != null && maxBytes != null && minBytes > maxBytes) return null
    return SizeRange(minBytes, maxBytes)
}

/** 解析相对时间条件（nowMillis 由调用方注入便于测试）；返回 null 表示格式无法识别。 */
internal fun parseTimeRange(raw: String, nowMillis: Long): TimeRange? {
    val text = raw.trim()
    if (text.isEmpty()) return TimeRange(null, null)
    val match = relativeTimeRegex.matchEntire(text) ?: return null
    val count = match.groupValues[2].toIntOrNull() ?: return null
    if (count < 0) return null
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = nowMillis
    when (match.groupValues[3].lowercase()) {
        "d" -> calendar.add(Calendar.DAY_OF_MONTH, -count)
        "m" -> calendar.add(Calendar.MONTH, -count)
        "y" -> calendar.add(Calendar.YEAR, -count)
        else -> return null
    }
    val boundary = calendar.timeInMillis
    return if (match.groupValues[1].isEmpty()) {
        // 最近 N 单位内（含当前时刻）
        TimeRange(afterMillis = boundary, beforeMillis = null)
    } else {
        // N 单位之前
        TimeRange(afterMillis = null, beforeMillis = boundary)
    }
}
