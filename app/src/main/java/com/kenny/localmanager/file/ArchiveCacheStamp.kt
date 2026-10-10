package com.kenny.localmanager.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * 打包阅读器缓存（.md/.rst/.html/.pic/.vid/.llm.zip、.epub）的失效判定。
 *
 * 旧方案只比较「缓存生成时间 >= 列表加载时的文件 mtime 快照」，有四个破绽：
 * 1. mtime 用的是目录列表加载时的快照，文件在列表加载后更新则比较失真；
 * 2. 同步工具（Syncthing/rsync -t 等）保留源文件 mtime，内容新而时间旧；
 * 3. 个别 provider 的 lastModified 恒为 0，比较恒真，缓存永不更新；
 * 4. mtime 精度/同秒同大小替换。
 *
 * 本方案在打开时**实时**采集文件指纹并与缓存侧存档严格比对：
 * `mtime | size | zip EOCD（cdSize/cdOffset/totalEntries）`
 * 内容任何实质变化（含 mtime 被保留、同秒替换）指纹必变；任何一项拿不到
 * （实时 query 失败 / mtime<=0）都视为不可信而不复用缓存。
 */
private const val ARCHIVE_STAMP_FILE = ".stamp"

/** 实时采集文件指纹；mtime 无效（<=0）或 query 失败返回 null（视为不可信）。 */
internal fun buildArchiveStamp(context: Context, uri: Uri): String? {
    val stats = queryDocumentStats(context, uri) ?: return null
    if (stats.first <= 0L) return null
    val fp = readZipEocdFingerprintFromUri(context, uri)
    val fpPart = if (fp != null) "${fp.cdSize}|${fp.cdOffset}|${fp.totalEntries}" else "-"
    return "v1|${stats.first}|${stats.second}|$fpPart"
}

/** 缓存重建完成后记录指纹（各 extract 函数在写 .cache_ts 处一并调用）。 */
internal fun writeArchiveStamp(context: Context, zipUri: Uri, cacheDir: File) {
    runCatching {
        val stamp = buildArchiveStamp(context, zipUri) ?: return
        File(cacheDir, ARCHIVE_STAMP_FILE).writeText(stamp)
    }
}

/** 打开时的缓存有效性判定：缓存侧与文件实时指纹严格一致才可复用。 */
fun isArchiveCacheStampValid(context: Context, cacheDir: File, target: DocumentFileModel): Boolean {
    val cached = runCatching {
        File(cacheDir, ARCHIVE_STAMP_FILE).takeIf { it.exists() }?.readText()?.trim()
    }.getOrNull() ?: return false
    if (cached.isEmpty()) return false
    val fresh = buildArchiveStamp(context, target.uri) ?: return false
    return cached == fresh
}

/** 实时查询文档的 lastModified 与 size（打开时刻的值，非列表快照）。 */
private fun queryDocumentStats(context: Context, uri: Uri): Pair<Long, Long>? {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0) && !cursor.isNull(1)) {
                cursor.getLong(0) to cursor.getLong(1)
            } else null
        }
    } catch (_: Exception) {
        null
    }
}
