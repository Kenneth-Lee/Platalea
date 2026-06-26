package com.kenny.localmanager.family

import org.json.JSONArray
import org.json.JSONObject

object FamilyNetworkAuth {
    const val PASSWORD_HEADER = "X-Network-Service-Password"
}

enum class FamilyNetworkAuthLevel {
    OPEN,
    GUEST,
    HOST;

    val canManageBoard: Boolean
        get() = this == OPEN || this == HOST
}

object BulletinBoardDefaults {
    const val DEFAULT_BOARD_ID = "default"
    const val DEFAULT_BOARD_NAME = "默认留言板"
}

data class BulletinBoardInfo(
    val id: String,
    val name: String,
    val revision: Long,
    val messageCount: Int
) {
    companion object {
        fun fromJson(obj: JSONObject): BulletinBoardInfo = BulletinBoardInfo(
            id = obj.getString("id"),
            name = obj.optString("name", obj.getString("id")),
            revision = obj.optLong("revision"),
            messageCount = obj.optInt("message_count")
        )
    }
}

data class BulletinMessage(
    val id: String,
    val seq: Long,
    val authorLabel: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val authorDevice: String? = null,
    val attachments: List<BulletinAttachmentRef> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("seq", seq)
        put("author_label", authorLabel)
        put("content", content)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("deleted", deleted)
        authorDevice?.takeIf { it.isNotBlank() }?.let { put("author_device", it) }
        if (attachments.isNotEmpty()) {
            put("attachments", JSONArray(attachments.map { it.toJson() }))
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): BulletinMessage {
            val attachmentList = buildList {
                val arr = obj.optJSONArray("attachments") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    add(BulletinAttachmentRef.fromJson(arr.getJSONObject(i)))
                }
            }
            return BulletinMessage(
                id = obj.getString("id"),
                seq = obj.optLong("seq"),
                authorLabel = obj.optString("author_label", ""),
                content = obj.optString("content", ""),
                createdAt = obj.optLong("created_at"),
                updatedAt = obj.optLong("updated_at"),
                deleted = obj.optBoolean("deleted", false),
                authorDevice = obj.optString("author_device").takeIf { it.isNotBlank() },
                attachments = attachmentList
            )
        }
    }
}

data class BulletinBoardSnapshot(
    val boardId: String,
    val boardName: String,
    val revision: Long,
    val messages: List<BulletinMessage>,
    val canManage: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ok", true)
        put("board_id", boardId)
        put("board_name", boardName)
        put("revision", revision)
        put("can_manage", canManage)
        put("messages", JSONArray(messages.map { it.toJson() }))
    }
}

data class BulletinBoardOpenSession(
    val service: FamilyDiscoveredService,
    val boardId: String,
    val boardName: String,
    val isHost: Boolean,
    val canManageBoard: Boolean = isHost,
    val accessPassword: String? = null,
    val revision: Long = 0L,
    val messages: List<BulletinMessage> = emptyList(),
    val loading: Boolean = false,
    val lastError: String? = null
)
