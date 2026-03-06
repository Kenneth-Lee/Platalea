package com.kenny.localmanager.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 播放列表：名称 + 曲目（uri + 显示名），用于持久化与恢复播放进度。
 */
data class Playlist(
    val id: String,
    val name: String,
    val uris: List<String>,
    val names: List<String>
) {
    val trackCount: Int get() = uris.size

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("uris", JSONArray(uris))
        put("names", JSONArray(names))
    }

    companion object {
        fun fromJson(obj: JSONObject): Playlist = Playlist(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", ""),
            uris = jsonArrayToList(obj.optJSONArray("uris")),
            names = jsonArrayToList(obj.optJSONArray("names"))
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
