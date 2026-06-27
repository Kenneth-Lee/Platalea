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

object BulletinMessageKind {
    const val MESSAGE = "message"
    const val AI_STATUS = "ai_status"
}

data class BulletinBoardInfo(
    val id: String,
    val name: String,
    val revision: Long,
    val messageCount: Int,
    val roleIds: List<String>? = null
) {
    companion object {
        fun fromJson(obj: JSONObject): BulletinBoardInfo {
            val roleIds = when {
                !obj.has("role_ids") -> null
                obj.isNull("role_ids") -> null
                else -> buildList {
                    val arr = obj.optJSONArray("role_ids") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        add(arr.optString(i).trim())
                    }
                }.filter { it.isNotEmpty() }
            }
            return BulletinBoardInfo(
                id = obj.getString("id"),
                name = obj.optString("name", obj.getString("id")),
                revision = obj.optLong("revision"),
                messageCount = obj.optInt("message_count"),
                roleIds = roleIds
            )
        }
    }
}

data class BulletinBoardListResult(
    val boards: List<BulletinBoardInfo>,
    val roleId: String? = null,
    val roleLabel: String? = null,
    val canManage: Boolean = false
)

data class BulletinMessage(
    val id: String,
    val seq: Long,
    val authorLabel: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val authorDevice: String? = null,
    val attachments: List<BulletinAttachmentRef> = emptyList(),
    val messageKind: String = BulletinMessageKind.MESSAGE
) {
    val isAiStatus: Boolean
        get() = messageKind == BulletinMessageKind.AI_STATUS ||
            content.trimStart().startsWith("/ai status", ignoreCase = true)

    val isConversationMessage: Boolean
        get() = !deleted && !isAiStatus

    val aiStatusDetail: String
        get() {
            val trimmed = content.trim()
            return if (trimmed.startsWith("/ai status", ignoreCase = true)) {
                trimmed.removePrefix("/ai status").trim()
            } else {
                trimmed
            }
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("seq", seq)
        put("author_label", authorLabel)
        put("content", content)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("deleted", deleted)
        if (messageKind != BulletinMessageKind.MESSAGE) {
            put("message_kind", messageKind)
        }
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
            val content = obj.optString("content", "")
            var messageKind = obj.optString("message_kind", BulletinMessageKind.MESSAGE)
                .ifBlank { BulletinMessageKind.MESSAGE }
            if (messageKind == BulletinMessageKind.MESSAGE &&
                content.trimStart().startsWith("/ai status", ignoreCase = true)
            ) {
                messageKind = BulletinMessageKind.AI_STATUS
            }
            return BulletinMessage(
                id = obj.getString("id"),
                seq = obj.optLong("seq"),
                authorLabel = obj.optString("author_label", ""),
                content = content,
                createdAt = obj.optLong("created_at"),
                updatedAt = obj.optLong("updated_at"),
                deleted = obj.optBoolean("deleted", false),
                authorDevice = obj.optString("author_device").takeIf { it.isNotBlank() },
                attachments = attachmentList,
                messageKind = messageKind
            )
        }
    }
}

data class BulletinBoardSnapshot(
    val boardId: String,
    val boardName: String,
    val revision: Long,
    val messages: List<BulletinMessage>,
    val canManage: Boolean = false,
    val roleId: String? = null,
    val roleLabel: String? = null,
    val agents: List<String> = emptyList(),
    val participants: List<String> = emptyList(),
    val commands: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ok", true)
        put("board_id", boardId)
        put("board_name", boardName)
        put("revision", revision)
        put("can_manage", canManage)
        put("messages", JSONArray(messages.map { it.toJson() }))
        put("agents", JSONArray(agents))
        put("participants", JSONArray(participants))
        put("commands", JSONArray(commands))
    }

    companion object {
        fun parseAgents(json: JSONObject): List<String> = parseStringArray(json.optJSONArray("agents"))
        fun parseParticipants(json: JSONObject): List<String> =
            parseStringArray(json.optJSONArray("participants"))
        fun parseCommands(json: JSONObject): List<String> = parseStringArray(json.optJSONArray("commands"))

        private fun parseStringArray(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    add(array.optString(i).trim())
                }
            }.filter { it.isNotEmpty() }
        }
    }
}

data class BulletinBoardOpenSession(
    val service: FamilyDiscoveredService,
    val boardId: String,
    val boardName: String,
    val isHost: Boolean,
    val canManageBoard: Boolean = isHost,
    val remoteRoleId: String? = null,
    val remoteRoleLabel: String? = null,
    val accessPassword: String? = null,
    val revision: Long = 0L,
    val messages: List<BulletinMessage> = emptyList(),
    val agents: List<String> = emptyList(),
    val participants: List<String> = emptyList(),
    val commands: List<String> = emptyList(),
    val loading: Boolean = false,
    val lastError: String? = null
)
