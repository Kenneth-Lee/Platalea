package com.kenny.localmanager.epub

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "EpubBookmark"

/** EPUB收藏夹条目 */
data class EpubBookmark(
    val id: String,                    // 唯一标识
    val epubUri: String,               // EPUB文件URI
    val epubFileName: String,          // EPUB文件名
    val chapterIndex: Int,             // 章节索引
    val chapterTitle: String,          // 章节标题
    val scrollPosition: Int,           // 滚动位置（像素）
    val scrollRatio: Float,            // 滚动比例（0-1），用于不同屏幕尺寸
    val note: String = "",             // 用户备注
    val createTime: Long,              // 创建时间戳
    val highlightText: String = ""     // 高亮文本（可选）
) {
    val formattedTime: String
        get() = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(createTime))
}

/** EPUB阅读进度 */
data class EpubReadingProgress(
    val epubUri: String,               // EPUB文件URI
    val epubFileName: String,          // EPUB文件名
    val chapterIndex: Int,             // 当前章节索引
    val chapterTitle: String,          // 当前章节标题
    val scrollPosition: Int,           // 滚动位置
    val scrollRatio: Float,            // 滚动比例
    val lastReadTime: Long             // 最后阅读时间
)

/** EPUB收藏夹和阅读进度管理器 */
class EpubBookmarkManager(
    private val context: Context,
    private val cacheDir: File? = null,
    private val scopedUri: String? = null
) {
    private val bookmarksFile = cacheDir?.let { File(it, ".epub_bookmarks.json") }
        ?: File(context.filesDir, "epub_bookmarks.json")

    private val _bookmarks = MutableStateFlow<List<EpubBookmark>>(emptyList())
    val bookmarks: StateFlow<List<EpubBookmark>> = _bookmarks.asStateFlow()

    init {
        if (cacheDir != null && !scopedUri.isNullOrBlank()) {
            clearPersistentDataForUri(scopedUri)
        }
        loadBookmarks()
    }

    // ---- 收藏夹管理 ----

    /** 添加收藏 */
    fun addBookmark(bookmark: EpubBookmark): Boolean {
        val current = _bookmarks.value.toMutableList()
        // 检查是否已存在相同位置的收藏
        val exists = current.any {
            it.epubUri == bookmark.epubUri &&
            it.chapterIndex == bookmark.chapterIndex &&
            Math.abs(it.scrollRatio - bookmark.scrollRatio) < 0.05f
        }
        if (exists) {
            Log.w(TAG, "收藏已存在")
            return false
        }
        current.add(0, bookmark) // 新收藏放在最前面
        _bookmarks.value = current
        saveBookmarks()
        return true
    }

    /** 删除收藏 */
    fun removeBookmark(bookmarkId: String) {
        val current = _bookmarks.value.toMutableList()
        current.removeAll { it.id == bookmarkId }
        _bookmarks.value = current
        saveBookmarks()
    }

    /** 更新收藏备注 */
    fun updateBookmarkNote(bookmarkId: String, note: String) {
        val current = _bookmarks.value.toMutableList()
        val index = current.indexOfFirst { it.id == bookmarkId }
        if (index >= 0) {
            current[index] = current[index].copy(note = note)
            _bookmarks.value = current
            saveBookmarks()
        }
    }

    /** 获取指定EPUB的所有收藏 */
    fun getBookmarksForEpub(epubUri: String): List<EpubBookmark> {
        return _bookmarks.value.filter { it.epubUri == epubUri }
    }

    /** 清除指定EPUB的所有收藏 */
    fun clearBookmarksForEpub(epubUri: String) {
        val current = _bookmarks.value.toMutableList()
        current.removeAll { it.epubUri == epubUri }
        _bookmarks.value = current
        saveBookmarks()
    }

    /** 生成唯一ID */
    fun generateId(): String {
        return System.currentTimeMillis().toString(16) +
               (0..1000).random().toString(16)
    }

    // ---- 阅读进度管理 ----

    /** 保存阅读进度 */
    fun saveProgress(progress: EpubReadingProgress) {
        try {
            val json = JSONObject().apply {
                put("epubUri", progress.epubUri)
                put("epubFileName", progress.epubFileName)
                put("chapterIndex", progress.chapterIndex)
                put("chapterTitle", progress.chapterTitle)
                put("scrollPosition", progress.scrollPosition)
                put("scrollRatio", progress.scrollRatio)
                put("lastReadTime", progress.lastReadTime)
            }
            // 使用URI hash作为文件名，支持多本书
            val progressFile = getProgressFileForUri(progress.epubUri)
            progressFile.parentFile?.mkdirs()
            progressFile.writeText(json.toString())
            Log.d(TAG, "保存阅读进度: ${progress.epubFileName} 章节${progress.chapterIndex}, 比例${progress.scrollRatio}, URI hash=${progress.epubUri.hashCode().toUInt().toString(16)}, 文件=${progressFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "保存阅读进度失败", e)
        }
    }

    /** 加载阅读进度 */
    fun loadProgress(epubUri: String): EpubReadingProgress? {
        return try {
            val file = getProgressFileForUri(epubUri)
            Log.d(TAG, "加载阅读进度: URI hash=${epubUri.hashCode().toUInt().toString(16)}, 文件=${file.absolutePath}, 存在=${file.exists()}")
            if (!file.exists()) return null
            val json = JSONObject(file.readText())
            val progress = EpubReadingProgress(
                epubUri = json.getString("epubUri"),
                epubFileName = json.optString("epubFileName", ""),
                chapterIndex = json.getInt("chapterIndex"),
                chapterTitle = json.optString("chapterTitle", ""),
                scrollPosition = json.optInt("scrollPosition", 0),
                scrollRatio = json.optDouble("scrollRatio", 0.0).toFloat(),
                lastReadTime = json.optLong("lastReadTime", System.currentTimeMillis())
            )
            Log.d(TAG, "加载阅读进度成功: ${progress.epubFileName} 章节${progress.chapterIndex}, 比例${progress.scrollRatio}")
            progress
        } catch (e: Exception) {
            Log.e(TAG, "加载阅读进度失败", e)
            null
        }
    }

    /** 清除阅读进度 */
    fun clearProgress(epubUri: String) {
        try {
            val file = getProgressFileForUri(epubUri)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "清除阅读进度失败", e)
        }
    }

    // ---- 私有方法 ----

    private fun getProgressFileForUri(uri: String): File {
        if (cacheDir != null) {
            return File(cacheDir, ".epub_progress.json")
        }
        val key = uri.hashCode().toUInt().toString(16)
        return File(context.filesDir, "epub_progress_$key.json")
    }

    private fun clearPersistentDataForUri(uri: String) {
        try {
            val persistentBookmarksFile = File(context.filesDir, "epub_bookmarks.json")
            if (persistentBookmarksFile.exists()) {
                val json = JSONArray(persistentBookmarksFile.readText())
                val filtered = JSONArray()
                var removed = 0
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    if (item.optString("epubUri") == uri) {
                        removed++
                    } else {
                        filtered.put(item)
                    }
                }
                if (removed > 0) {
                    persistentBookmarksFile.writeText(filtered.toString())
                    Log.w(TAG, "已清除持久区中遗留的加密文件收藏: uri=$uri, count=$removed")
                }
            }
            val persistentProgressFile = File(
                context.filesDir,
                "epub_progress_${uri.hashCode().toUInt().toString(16)}.json"
            )
            if (persistentProgressFile.exists()) {
                persistentProgressFile.delete()
                Log.w(TAG, "已清除持久区中遗留的加密文件进度: uri=$uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理持久区遗留加密阅读数据失败", e)
        }
    }

    private fun loadBookmarks() {
        try {
            if (!bookmarksFile.exists()) {
                _bookmarks.value = emptyList()
                return
            }
            val json = JSONArray(bookmarksFile.readText())
            val list = mutableListOf<EpubBookmark>()
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                list.add(EpubBookmark(
                    id = item.getString("id"),
                    epubUri = item.getString("epubUri"),
                    epubFileName = item.optString("epubFileName", ""),
                    chapterIndex = item.getInt("chapterIndex"),
                    chapterTitle = item.optString("chapterTitle", ""),
                    scrollPosition = item.optInt("scrollPosition", 0),
                    scrollRatio = item.optDouble("scrollRatio", 0.0).toFloat(),
                    note = item.optString("note", ""),
                    createTime = item.getLong("createTime"),
                    highlightText = item.optString("highlightText", "")
                ))
            }
            _bookmarks.value = list
            Log.d(TAG, "加载收藏夹: ${list.size}条")
        } catch (e: Exception) {
            Log.e(TAG, "加载收藏夹失败", e)
            _bookmarks.value = emptyList()
        }
    }

    private fun saveBookmarks() {
        try {
            bookmarksFile.parentFile?.mkdirs()
            val json = JSONArray()
            _bookmarks.value.forEach { bookmark ->
                json.put(JSONObject().apply {
                    put("id", bookmark.id)
                    put("epubUri", bookmark.epubUri)
                    put("epubFileName", bookmark.epubFileName)
                    put("chapterIndex", bookmark.chapterIndex)
                    put("chapterTitle", bookmark.chapterTitle)
                    put("scrollPosition", bookmark.scrollPosition)
                    put("scrollRatio", bookmark.scrollRatio)
                    put("note", bookmark.note)
                    put("createTime", bookmark.createTime)
                    put("highlightText", bookmark.highlightText)
                })
            }
            bookmarksFile.writeText(json.toString())
            Log.d(TAG, "保存收藏夹: ${_bookmarks.value.size}条")
        } catch (e: Exception) {
            Log.e(TAG, "保存收藏夹失败", e)
        }
    }
}
