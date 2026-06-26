package com.kenny.localmanager.family

import org.json.JSONArray
import org.json.JSONObject

class BulletinBoardHttpHandler(
    private val store: BulletinBoardStore
) {
    fun handle(method: String, path: String, body: String): FamilyHttpResponse {
        val normalizedPath = path.substringBefore('?').trimEnd('/').ifEmpty { "/" }
        return when {
            method == "GET" && normalizedPath == "/boards" -> listBoards()
            method == "GET" && normalizedPath.startsWith("/boards/") && normalizedPath.endsWith("/messages") -> {
                val boardId = normalizedPath.removePrefix("/boards/").removeSuffix("/messages")
                getMessages(boardId)
            }
            method == "POST" && normalizedPath.startsWith("/boards/") && normalizedPath.endsWith("/messages") -> {
                val boardId = normalizedPath.removePrefix("/boards/").removeSuffix("/messages")
                createMessage(boardId, body)
            }
            method == "PUT" && normalizedPath.startsWith("/boards/") && normalizedPath.contains("/messages/") -> {
                val parts = normalizedPath.removePrefix("/boards/").split("/messages/")
                if (parts.size != 2) return notFound()
                updateMessage(parts[0], parts[1], body)
            }
            method == "DELETE" && normalizedPath.startsWith("/boards/") && normalizedPath.contains("/messages/") -> {
                val parts = normalizedPath.removePrefix("/boards/").split("/messages/")
                if (parts.size != 2) return notFound()
                deleteMessage(parts[0], parts[1])
            }
            else -> notFound()
        }
    }

    private fun listBoards(): FamilyHttpResponse {
        val boards = store.listBoards()
        val body = JSONObject().apply {
            put("ok", true)
            put("boards", JSONArray().apply {
                boards.forEach { board ->
                    put(
                        JSONObject().apply {
                            put("id", board.id)
                            put("name", board.name)
                            put("revision", board.revision)
                            put("message_count", board.messageCount)
                        }
                    )
                }
            })
        }
        return FamilyHttpResponse(200, body.toString())
    }

    private fun getMessages(boardId: String): FamilyHttpResponse {
        val snapshot = store.snapshot(boardId)
            ?: return FamilyHttpResponse(404, jsonError("board_not_found", "留言板不存在：$boardId"))
        return FamilyHttpResponse(
            200,
            snapshot.copy(canManage = false).toJson().toString()
        )
    }

    private fun createMessage(boardId: String, bodyText: String): FamilyHttpResponse {
        val payload = parseJson(bodyText) ?: return badRequest("invalid_json", "请求体不是合法 JSON")
        val content = payload.optString("content")
        val authorLabel = payload.optString("author_label", "访客")
        val authorDevice = payload.optString("author_device").takeIf { it.isNotBlank() }
        val message = store.appendMessage(boardId, authorLabel, content, authorDevice)
            ?: return badRequest("invalid_message", "消息内容不能为空或留言板不存在")
        val snapshot = store.snapshot(boardId)
        return FamilyHttpResponse(
            200,
            JSONObject().apply {
                put("ok", true)
                put("message", message.toJson())
                put("revision", snapshot?.revision ?: 0L)
            }.toString()
        )
    }

    private fun updateMessage(boardId: String, messageId: String, bodyText: String): FamilyHttpResponse {
        val payload = parseJson(bodyText) ?: return badRequest("invalid_json", "请求体不是合法 JSON")
        val content = payload.optString("content")
        val message = store.updateMessage(boardId, messageId, content)
            ?: return FamilyHttpResponse(404, jsonError("message_not_found", "消息不存在或内容为空"))
        val snapshot = store.snapshot(boardId)
        return FamilyHttpResponse(
            200,
            JSONObject().apply {
                put("ok", true)
                put("message", message.toJson())
                put("revision", snapshot?.revision ?: 0L)
            }.toString()
        )
    }

    private fun deleteMessage(boardId: String, messageId: String): FamilyHttpResponse {
        val deleted = store.deleteMessage(boardId, messageId)
        if (!deleted) {
            return FamilyHttpResponse(404, jsonError("message_not_found", "消息不存在"))
        }
        val snapshot = store.snapshot(boardId)
        return FamilyHttpResponse(
            200,
            JSONObject().apply {
                put("ok", true)
                put("revision", snapshot?.revision ?: 0L)
            }.toString()
        )
    }

    private fun parseJson(bodyText: String): JSONObject? =
        runCatching { JSONObject(bodyText) }.getOrNull()

    private fun badRequest(code: String, message: String): FamilyHttpResponse =
        FamilyHttpResponse(400, jsonError(code, message))

    private fun notFound(): FamilyHttpResponse =
        FamilyHttpResponse(404, jsonError("not_found", "接口不存在"))

    private fun jsonError(code: String, message: String): String =
        JSONObject().apply {
            put("ok", false)
            put("error", code)
            put("message", message)
        }.toString()
}

data class FamilyHttpResponse(
    val statusCode: Int,
    val body: String,
    val contentType: String = "application/json; charset=utf-8"
)
