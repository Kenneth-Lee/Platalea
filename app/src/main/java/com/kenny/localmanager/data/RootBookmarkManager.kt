package com.kenny.localmanager.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 根目录书签管理：按不同根 URI 保存各自的书签列表，存储在应用私有目录，不在文件树中暴露。
 *
 * 结构：rootBookmarks.json
 * [
 *   { "root": "<normalized-root-uri>", "paths": ["/", "/foo/bar"] },
 *   ...
 * ]
 */
class RootBookmarkManager(context: Context) {

    private val bookmarksFile = File(context.filesDir, "root_bookmarks.json")

    private val _bookmarksByRoot = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val bookmarksByRoot: StateFlow<Map<String, List<String>>> = _bookmarksByRoot.asStateFlow()

    init {
        load()
    }

    private fun load() {
        try {
            if (!bookmarksFile.exists()) {
                _bookmarksByRoot.value = emptyMap()
                return
            }
            val text = bookmarksFile.readText()
            if (text.isBlank()) {
                _bookmarksByRoot.value = emptyMap()
                return
            }
            val arr = JSONArray(text)
            val map = mutableMapOf<String, List<String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val root = obj.getString("root")
                val pathsJson = obj.optJSONArray("paths") ?: JSONArray()
                val paths = mutableListOf<String>()
                for (j in 0 until pathsJson.length()) {
                    val p = pathsJson.optString(j, "").trim()
                    if (p.isNotEmpty()) paths += p
                }
                map[root] = paths
            }
            _bookmarksByRoot.value = map
        } catch (_: Exception) {
            _bookmarksByRoot.value = emptyMap()
        }
    }

    private fun save() {
        try {
            val arr = JSONArray()
            _bookmarksByRoot.value.forEach { (root, paths) ->
                val obj = JSONObject()
                obj.put("root", root)
                val pathsArr = JSONArray()
                paths.forEach { pathsArr.put(it) }
                obj.put("paths", pathsArr)
                arr.put(obj)
            }
            bookmarksFile.writeText(arr.toString())
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun normalizeContentUriStringLocal(s: String): String {
        if (!s.startsWith("content://")) return s
        return s.replace("android ", "android.")
    }

    fun getBookmarksForRoot(rootUri: String?): List<String> {
        if (rootUri == null) return emptyList()
        val normRoot = normalizeContentUriStringLocal(rootUri)
        return _bookmarksByRoot.value[normRoot].orEmpty()
    }

    fun setBookmarksForRoot(rootUri: String?, paths: List<String>) {
        if (rootUri == null) return
        val normRoot = normalizeContentUriStringLocal(rootUri)
        val distinct = paths.distinct()
        val current = _bookmarksByRoot.value.toMutableMap()
        if (distinct.isEmpty()) {
            current.remove(normRoot)
        } else {
            current[normRoot] = distinct
        }
        _bookmarksByRoot.value = current
        save()
    }

    fun exportAll(): Map<String, List<String>> = _bookmarksByRoot.value

    fun importAll(bookmarksByRoot: Map<String, List<String>>) {
        _bookmarksByRoot.value = bookmarksByRoot
            .mapKeys { normalizeContentUriStringLocal(it.key) }
            .mapValues { (_, paths) -> paths.distinct().filter { it.isNotBlank() } }
            .filterValues { it.isNotEmpty() }
        save()
    }
}

