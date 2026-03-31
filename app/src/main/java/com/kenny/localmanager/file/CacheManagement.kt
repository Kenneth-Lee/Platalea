package com.kenny.localmanager.file

import android.content.Context
import com.kenny.localmanager.R
import java.io.File

private val BROWSABLE_CACHE_DIRS = listOf(
    "md_zip_cache",
    "html_zip_cache",
    "epub_cache",
    "pic_zip_cache"
)
private const val PIC_ZIP_CACHE_DIR = "pic_zip_cache"

/** 缓存项：显示名、当前大小、清空时的回调。 */
data class CacheEntry(
    val displayName: String,
    val description: String,
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

private fun collectBrowsableCacheChildren(context: Context): List<File> {
    return BROWSABLE_CACHE_DIRS.flatMap { dirName ->
        File(context.cacheDir, dirName).listFiles().orEmpty().toList()
    }
}

private fun isEncryptedPicCacheChild(file: File): Boolean {
    return file.parentFile?.name == PIC_ZIP_CACHE_DIR && file.isDirectory && isEncryptedSubdir(file)
}

private fun isNonEncryptedBrowsableCacheChild(file: File): Boolean {
    val parentName = file.parentFile?.name ?: return false
    if (parentName !in BROWSABLE_CACHE_DIRS) return false
    if (file.isDirectory) return !isEncryptedSubdir(file)
    return true
}

/** 返回缓存分类。仅保留三类：非加密展开浏览缓存、加密图片缓存、其他缓存。需在 IO 线程调用。 */
fun getCacheEntries(context: Context): List<CacheEntry> {
    val cacheDir = context.cacheDir
    val rootEntries = cacheDir.listFiles().orEmpty()
    val result = mutableListOf<CacheEntry>()
    val browsableChildren = collectBrowsableCacheChildren(context)
    val nonEncryptedBrowsable = browsableChildren.filter(::isNonEncryptedBrowsableCacheChild)
    val encryptedPic = browsableChildren.filter(::isEncryptedPicCacheChild)
    val managedBrowsableDirs = BROWSABLE_CACHE_DIRS.toSet()

    result += CacheEntry(
        context.getString(R.string.cache_browsable_name),
        context.getString(R.string.cache_browsable_desc),
        nonEncryptedBrowsable.sumOf(::dirSize)
    ) { ctx ->
        BROWSABLE_CACHE_DIRS.forEach { dirName ->
            File(ctx.cacheDir, dirName).listFiles()?.forEach { child ->
                if (isNonEncryptedBrowsableCacheChild(child)) child.deleteRecursively()
            }
        }
    }

    result += CacheEntry(
        context.getString(R.string.cache_encrypted_pic_name),
        context.getString(R.string.cache_encrypted_pic_desc),
        encryptedPic.sumOf(::dirSize)
    ) { ctx ->
        File(ctx.cacheDir, PIC_ZIP_CACHE_DIR).listFiles()?.forEach { child ->
            if (isEncryptedPicCacheChild(child)) child.deleteRecursively()
        }
    }

    var otherSize = 0L
    rootEntries.forEach { f ->
        if (f.name !in managedBrowsableDirs) {
            otherSize += dirSize(f)
        } else {
            f.listFiles()?.forEach { child ->
                if (!isNonEncryptedBrowsableCacheChild(child) && !isEncryptedPicCacheChild(child)) {
                    otherSize += dirSize(child)
                }
            }
        }
    }
    result += CacheEntry(
        context.getString(R.string.cache_other_name),
        context.getString(R.string.cache_other_desc),
        otherSize
    ) { ctx ->
        ctx.cacheDir.listFiles()?.forEach { f ->
            if (f.name !in managedBrowsableDirs) {
                f.deleteRecursively()
            } else {
                f.listFiles()?.forEach { child ->
                    if (!isNonEncryptedBrowsableCacheChild(child) && !isEncryptedPicCacheChild(child)) {
                        child.deleteRecursively()
                    }
                }
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
