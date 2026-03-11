package com.kenny.localmanager.file

import android.content.Context
import java.io.File

/** 仅图片缓存区分加密/非加密；其余类型整类清空。 */
private val CACHE_TYPES_FULL_CLEAR = listOf(
    "md_zip_cache" to "Markdown/RST 压缩包缓存",
    "html_zip_cache" to "HTML 压缩包缓存",
    "epub_cache" to "EPUB 电子书缓存"
)
private const val PIC_ZIP_CACHE_DIR = "pic_zip_cache"
private const val PIC_ZIP_DISPLAY_NAME = "图片压缩包缓存"

/** 缓存项：显示名、当前大小、清空时的回调。 */
data class CacheEntry(
    val displayName: String,
    val sizeBytes: Long,
    val onClear: (Context) -> Unit
)

fun dirSize(file: File): Long {
    if (!file.exists()) return 0L
    return if (file.isDirectory) {
        file.listFiles()?.sumOf { dirSize(it) } ?: 0L
    } else {
        file.length()
    }
}

private fun isEncryptedSubdir(subdir: File): Boolean = File(subdir, ".encrypted").exists()

/** 返回所有缓存分类。图片缓存拆成（非加密）（加密）两项，其余每类一项、清空时整类删除。需在 IO 线程调用。 */
fun getCacheEntries(context: Context): List<CacheEntry> {
    val cacheDir = context.cacheDir
    val result = mutableListOf<CacheEntry>()

    for ((dirName, displayName) in CACHE_TYPES_FULL_CLEAR) {
        val dir = File(cacheDir, dirName)
        val size = dirSize(dir)
        result += CacheEntry(displayName, size) { ctx ->
            val d = File(ctx.cacheDir, dirName)
            d.deleteRecursively()
            d.mkdirs()
        }
    }

    val picDir = File(cacheDir, PIC_ZIP_CACHE_DIR)
    if (picDir.exists() && picDir.isDirectory) {
        var sizeNonEnc = 0L
        var sizeEnc = 0L
        picDir.listFiles()?.forEach { sub ->
            if (!sub.isDirectory) return@forEach
            val size = dirSize(sub)
            if (isEncryptedSubdir(sub)) sizeEnc += size else sizeNonEnc += size
        }
        result += CacheEntry("$PIC_ZIP_DISPLAY_NAME（非加密）", sizeNonEnc) { ctx ->
            File(ctx.cacheDir, PIC_ZIP_CACHE_DIR).listFiles()?.forEach { sub ->
                if (sub.isDirectory && !isEncryptedSubdir(sub)) sub.deleteRecursively()
            }
        }
        result += CacheEntry("$PIC_ZIP_DISPLAY_NAME（加密）", sizeEnc) { ctx ->
            File(ctx.cacheDir, PIC_ZIP_CACHE_DIR).listFiles()?.forEach { sub ->
                if (sub.isDirectory && isEncryptedSubdir(sub)) sub.deleteRecursively()
            }
        }
    } else {
        result += CacheEntry("$PIC_ZIP_DISPLAY_NAME（非加密）", 0L) { ctx ->
            File(ctx.cacheDir, PIC_ZIP_CACHE_DIR).let { it.deleteRecursively(); it.mkdirs() }
        }
        result += CacheEntry("$PIC_ZIP_DISPLAY_NAME（加密）", 0L) { ctx ->
            File(ctx.cacheDir, PIC_ZIP_CACHE_DIR).listFiles()?.forEach { sub ->
                if (sub.isDirectory && isEncryptedSubdir(sub)) sub.deleteRecursively()
            }
        }
    }

    var otherSize = 0L
    cacheDir.listFiles()?.forEach { f ->
        if (f.name !in CACHE_TYPES_FULL_CLEAR.map { it.first } && f.name != PIC_ZIP_CACHE_DIR) {
            otherSize += dirSize(f)
        }
    }
    result += CacheEntry("其他临时缓存", otherSize) { ctx ->
        ctx.cacheDir.listFiles()?.forEach { f ->
            if (f.name !in CACHE_TYPES_FULL_CLEAR.map { it.first } && f.name != PIC_ZIP_CACHE_DIR) {
                f.deleteRecursively()
            }
        }
    }

    return result
}

/** 执行清空（会调用 entry.onClear）。 */
fun clearCacheEntry(context: Context, entry: CacheEntry) {
    entry.onClear(context)
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024))
    return "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
