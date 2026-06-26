package com.kenny.localmanager.family

import org.json.JSONArray
import org.json.JSONObject

object BulletinBoardDefaults {
    const val DEFAULT_BOARD_ID = "default"
    const val DEFAULT_BOARD_NAME = "默认留言板"
}

data class BulletinBoardInfo(
    val id: String,
    val name: String,
    val revision: Long,
    val messageCount: Int
)

data class BulletinMessage(
    val id: String,
    val seq: Long,
    val authorLabel: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("seq", seq)
        put("author_label", authorLabel)
        put("content", content)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("deleted", deleted)
    }

    companion object {
        fun fromJson(obj: JSONObject): BulletinMessage = BulletinMessage(
            id = obj.getString("id"),
            seq = obj.optLong("seq"),
            authorLabel = obj.optString("author_label", ""),
            content = obj.optString("content", ""),
            createdAt = obj.optLong("created_at"),
            updatedAt = obj.optLong("updated_at"),
            deleted = obj.optBoolean("deleted", false)
        )
    }
}

data class BulletinBoardSnapshot(
    val boardId: String,
    val boardName: String,
    val revision: Long,
    val messages: List<BulletinMessage>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ok", true)
        put("board_id", boardId)
        put("board_name", boardName)
        put("revision", revision)
        put("messages", JSONArray(messages.map { it.toJson() }))
    }
}

data class BulletinBoardOpenSession(
    val service: FamilyDiscoveredService,
    val boardId: String,
    val boardName: String,
    val isHost: Boolean,
    val revision: Long = 0L,
    val messages: List<BulletinMessage> = emptyList(),
    val loading: Boolean = false,
    val lastError: String? = null
)
