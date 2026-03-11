package com.kenny.localmanager.file

import android.content.Context
import java.io.File

/** 缓存项：显示名、对应目录（为 null 表示“其他”）、当前大小（字节）。 */
data class CacheEntry(
    val displayName: String,
    val dir: File?,
    val sizeBytes: Long
)

private val NAMED_CACHE_DIRS = listOf(
    "md_zip_cache" to "Markdown/RST 压缩包缓存",
    "html_zip_cache" to "HTML 压缩包缓存",
    "pic_zip_cache" to "图片压缩包缓存",
    "epub_cache" to "EPUB 电子书缓存"
)

fun dirSize(file: File): Long {
    if (!file.exists()) return 0L
    return if (file.isDirectory) {
        file.listFiles()?.sumOf { dirSize(it) } ?: 0L
    } else {
        file.length()
    }
}

/** 返回所有缓存分类及当前大小（需在 IO 线程调用）。 */
fun getCacheEntries(context: Context): List<CacheEntry> {
    val cacheDir = context.cacheDir
    val namedDirs = NAMED_CACHE_DIRS.map { (dirName, displayName) ->
        val dir = File(cacheDir, dirName)
        CacheEntry(displayName, dir, dirSize(dir))
    }
    var otherSize = 0L
    cacheDir.listFiles()?.forEach { f ->
        if (NAMED_CACHE_DIRS.none { it.first == f.name }) {
            otherSize += dirSize(f)
        }
    }
    return namedDirs + CacheEntry("其他临时缓存", null, otherSize)
}

/** 清空指定缓存项；为 null 时清空“其他”（删除 cacheDir 下除四类命名目录外的所有内容）。 */
fun clearCacheEntry(context: Context, entry: CacheEntry) {
    val cacheDir = context.cacheDir
    if (entry.dir != null) {
        entry.dir.deleteRecursively()
        entry.dir.mkdirs()
    } else {
        cacheDir.listFiles()?.forEach { f ->
            if (NAMED_CACHE_DIRS.none { it.first == f.name }) {
                f.deleteRecursively()
            }
        }
    }
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024))
    return "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
