package com.kenny.localmanager.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 播放列表：名称 + 备注（可选）+ 曲目（uri + 显示名），用于持久化与恢复播放进度。
 */
data class Playlist(
    val id: String,
    val name: String,
    val note: String = "",
    val uris: List<String>,
    val names: List<String>,
    val sourceType: String = SOURCE_TYPE_MANUAL,
    val sourceUri: String? = null
) {
    val trackCount: Int get() = uris.size
    val isDirectorySource: Boolean get() = sourceType == SOURCE_TYPE_DIRECTORY && !sourceUri.isNullOrBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("note", note)
        put("uris", JSONArray(uris))
        put("names", JSONArray(names))
        put("sourceType", sourceType)
        put("sourceUri", sourceUri ?: "")
    }

    companion object {
        const val SOURCE_TYPE_MANUAL = "manual"
        const val SOURCE_TYPE_DIRECTORY = "directory"

        fun fromJson(obj: JSONObject): Playlist = Playlist(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", ""),
            note = obj.optString("note", ""),
            uris = jsonArrayToList(obj.optJSONArray("uris")),
            names = jsonArrayToList(obj.optJSONArray("names")),
            sourceType = obj.optString("sourceType", SOURCE_TYPE_MANUAL).ifBlank { SOURCE_TYPE_MANUAL },
            sourceUri = obj.optString("sourceUri").trim().ifBlank { null }
        )

        private fun jsonArrayToList(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).map { arr.getString(it) }
        }

        fun listToJson(playlists: List<Playlist>): String =
            JSONArray(playlists.map { it.toJson() }).toString()

        fun listFromJson(json: String): List<Playlist> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrElse { emptyList() }
        }
    }
}
