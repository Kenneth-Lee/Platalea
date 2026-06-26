package com.kenny.localmanager.family

import org.json.JSONArray
import org.json.JSONObject

class BulletinBoardHttpHandler(
    private val store: BulletinBoardStore
) {
    fun handle(
        method: String,
        path: String,
        bodyBytes: ByteArray,
        authLevel: FamilyNetworkAuthLevel,
        headers: Map<String, String> = emptyMap()
    ): FamilyHttpResponse {
        val normalizedPath = path.substringBefore('?').trimEnd('/').ifEmpty { "/" }
        if (requiresHostAuth(method, normalizedPath) && !authLevel.canManageBoard) {
            return forbidden("修改或删除需要宿主密码")
        }
        return when {
            method == "GET" && normalizedPath == "/boards" -> listBoards()
            method == "GET" && normalizedPath.startsWith("/boards/") && normalizedPath.endsWith("/messages") -> {
                val boardId = normalizedPath.removePrefix("/boards/").removeSuffix("/messages")
                getMessages(boardId, authLevel)
            }
            method == "POST" && normalizedPath.startsWith("/boards/") && normalizedPath.endsWith("/messages") -> {
                val boardId = normalizedPath.removePrefix("/boards/").removeSuffix("/messages")
                createMessage(boardId, bodyBytes.decodeToString())
            }
            method == "PUT" && normalizedPath.startsWith("/boards/") && normalizedPath.contains("/messages/") -> {
                val parts = normalizedPath.removePrefix("/boards/").split("/messages/")
                if (parts.size != 2) return notFound()
                updateMessage(parts[0], parts[1], bodyBytes.decodeToString())
            }
            method == "DELETE" && normalizedPath.startsWith("/boards/") && normalizedPath.contains("/messages/") -> {
                val parts = normalizedPath.removePrefix("/boards/").split("/messages/")
                if (parts.size != 2) return notFound()
                deleteMessage(parts[0], parts[1])
            }
            method == "POST" && normalizedPath.endsWith("/attachments/init") -> {
                val boardId = boardIdFromAttachmentsPath(normalizedPath, suffix = "/attachments/init")
                    ?: return notFound()
                initAttachment(boardId, bodyBytes.decodeToString())
            }
            method == "PUT" && normalizedPath.contains("/attachments/") && normalizedPath.contains("/chunks/") -> {
                handleChunkPut(normalizedPath, bodyBytes)
            }
            method == "POST" && normalizedPath.contains("/attachments/") && normalizedPath.endsWith("/complete") -> {
                val boardId = boardIdFromCompletePath(normalizedPath) ?: return notFound()
                val attachmentId = attachmentIdFromCompletePath(normalizedPath) ?: return notFound()
                completeAttachment(boardId, attachmentId)
            }
            method == "GET" && normalizedPath.contains("/attachments/") && normalizedPath.endsWith("/blob") -> {
                handleBlobGet(normalizedPath, headers["range"])
            }
            method == "GET" && normalizedPath.contains("/attachments/") &&
                !normalizedPath.endsWith("/blob") && !normalizedPath.endsWith("/complete") -> {
                val parts = normalizedPath.removePrefix("/boards/").split("/attachments/")
                if (parts.size != 2) return notFound()
                getAttachmentMeta(parts[0], parts[1])
            }
            method == "DELETE" && normalizedPath.contains("/attachments/") -> {
                val parts = normalizedPath.removePrefix("/boards/").split("/attachments/")
                if (parts.size != 2) return notFound()
                deleteAttachment(parts[0], parts[1])
            }
            else -> notFound()
        }
    }

    private fun requiresHostAuth(method: String, path: String): Boolean {
        if (method == "DELETE" && path.contains("/attachments/")) return true
        if (method in setOf("PUT", "DELETE") && path.contains("/messages")) return true
        return false
    }

    private fun handleChunkPut(path: String, bodyBytes: ByteArray): FamilyHttpResponse {
        val fileChunk = Regex("""/boards/([^/]+)/attachments/([^/]+)/files/([^/]+)/chunks/(\d+)""")
            .find(path)
        if (fileChunk != null) {
            val (boardId, attachmentId, fileId, chunkIndex) = fileChunk.destructured
            val result = store.attachments.writeDirectoryFileChunk(
                boardId,
                attachmentId,
                fileId,
                chunkIndex.toIntOrNull() ?: return badRequest("invalid_attachment", "分块索引无效"),
                bodyBytes
            ) ?: return badRequest("invalid_attachment", "目录附件分块写入失败")
            return FamilyHttpResponse(
                200,
                JSONObject().apply {
                    put("ok", true)
                    put("chunk_index", result.chunkIndex)
                    put("received", result.received)
                }.toString()
            )
        }
        val fileOnly = Regex("""/boards/([^/]+)/attachments/([^/]+)/chunks/(\d+)""").find(path)
            ?: return notFound()
        val (boardId, attachmentId, chunkIndex) = fileOnly.destructured
        val result = store.attachments.writeFileChunk(
            boardId,
            attachmentId,
            chunkIndex.toIntOrNull() ?: return badRequest("invalid_attachment", "分块索引无效"),
            bodyBytes
        ) ?: return badRequest("invalid_attachment", "附件分块写入失败")
        return FamilyHttpResponse(
            200,
            JSONObject().apply {
                put("ok", true)
                put("chunk_index", result.chunkIndex)
                put("received", result.received)
            }.toString()
        )
    }

    private fun handleBlobGet(path: String, rangeHeader: String?): FamilyHttpResponse {
        val dirBlob = Regex("""/boards/([^/]+)/attachments/([^/]+)/files/([^/]+)/blob""").find(path)
        if (dirBlob != null) {
            val (boardId, attachmentId, fileId) = dirBlob.destructured
            val result = store.attachments.readDirectoryFileBlob(boardId, attachmentId, fileId, rangeHeader)
                ?: return FamilyHttpResponse(404, jsonError("attachment_not_found", "附件不存在或未就绪"))
            return blobResponse(result)
        }
        val fileBlob = Regex("""/boards/([^/]+)/attachments/([^/]+)/blob""").find(path)
            ?: return notFound()
        val (boardId, attachmentId) = fileBlob.destructured
        val result = store.attachments.readFileBlob(boardId, attachmentId, rangeHeader)
            ?: return FamilyHttpResponse(404, jsonError("attachment_not_found", "附件不存在或未就绪"))
        return blobResponse(result)
    }

    private fun blobResponse(result: BlobReadResult): FamilyHttpResponse {
        val headers = mutableMapOf<String, String>()
        headers["Accept-Ranges"] = "bytes"
        if (result.contentRange != null) {
            headers["Content-Range"] = result.contentRange
        }
        return FamilyHttpResponse(
            statusCode = result.statusCode,
            bodyBytes = result.bytes,
            contentType = "application/octet-stream",
            extraHeaders = headers
        )
    }

    private fun initAttachment(boardId: String, bodyText: String): FamilyHttpResponse {
        val payload = parseJson(bodyText) ?: return badRequest("invalid_json", "请求体不是合法 JSON")
        val kind = BulletinAttachmentKind.fromWire(payload.optString("kind", "file"))
            ?: return badRequest("invalid_attachment", "kind 无效")
        val uploaderDevice = payload.optString("uploader_device").takeIf { it.isNotBlank() }
        val result = when (kind) {
            BulletinAttachmentKind.FILE -> {
                val name = payload.optString("name")
                val size = payload.optLong("size", -1)
                if (name.isBlank() || size < 0) {
                    return badRequest("invalid_attachment", "单文件附件需要 name 与 size")
                }
                store.attachments.initFileUpload(
                    boardId = boardId,
                    name = name,
                    size = size,
                    sha256 = payload.optString("sha256").takeIf { it.isNotBlank() },
                    mime = payload.optString("mime").takeIf { it.isNotBlank() },
                    uploaderDevice = uploaderDevice
                )
            }
            BulletinAttachmentKind.DIRECTORY -> {
                val name = payload.optString("name")
                val entriesArr = payload.optJSONArray("entries") ?: JSONArray()
                val entries = buildList {
                    for (i in 0 until entriesArr.length()) {
                        val item = entriesArr.getJSONObject(i)
                        add(
                            BulletinDirectoryEntry(
                                path = item.optString("path"),
                                size = item.optLong("size"),
                                sha256 = item.optString("sha256").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }.filter { it.path.isNotBlank() && it.size >= 0 }
                if (name.isBlank() || entries.isEmpty()) {
                    return badRequest("invalid_attachment", "目录附件需要 name 与 entries")
                }
                store.attachments.initDirectoryUpload(boardId, name, entries, uploaderDevice)
            }
        } ?: return badRequest("invalid_attachment", "初始化附件失败")
        val body = JSONObject().apply {
            put("ok", true)
            put("attachment_id", result.attachmentId)
            put("chunk_size", result.chunkSize)
            put("status", BulletinAttachmentStatus.UPLOADING.wire)
            if (result.directoryFiles.isNotEmpty()) {
                put(
                    "files",
                    JSONArray().apply {
                        result.directoryFiles.forEach { slot ->
                            put(
                                JSONObject().apply {
                                    put("file_id", slot.fileId)
                                    put("path", slot.path)
                                    put("size", slot.size)
                                }
                            )
                        }
                    }
                )
            }
        }
        return FamilyHttpResponse(200, body.toString())
    }

    private fun completeAttachment(boardId: String, attachmentId: String): FamilyHttpResponse {
        val ref = store.attachments.completeUpload(boardId, attachmentId)
            ?: return badRequest("incomplete_upload", "附件未完成上传或校验失败")
        return FamilyHttpResponse(
            200,
            JSONObject().apply {
                put("ok", true)
                put("attachment_id", ref.id)
                put("status", BulletinAttachmentStatus.READY.wire)
            }.toString()
        )
    }

    private fun getAttachmentMeta(boardId: String, attachmentId: String): FamilyHttpResponse {
        val meta = store.attachments.getAttachmentMeta(boardId, attachmentId)
            ?: return FamilyHttpResponse(404, jsonError("attachment_not_found", "附件不存在：$attachmentId"))
        return FamilyHttpResponse(200, meta.toString())
    }

    private fun deleteAttachment(boardId: String, attachmentId: String): FamilyHttpResponse {
        if (!store.attachments.deleteAttachment(boardId, attachmentId)) {
            return FamilyHttpResponse(404, jsonError("attachment_not_found", "附件不存在"))
        }
        return FamilyHttpResponse(200, JSONObject().apply { put("ok", true) }.toString())
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

    private fun getMessages(boardId: String, authLevel: FamilyNetworkAuthLevel): FamilyHttpResponse {
        val snapshot = store.snapshot(boardId)
            ?: return FamilyHttpResponse(404, jsonError("board_not_found", "留言板不存在：$boardId"))
        return FamilyHttpResponse(
            200,
            snapshot.copy(canManage = authLevel.canManageBoard).toJson().toString()
        )
    }

    private fun createMessage(boardId: String, bodyText: String): FamilyHttpResponse {
        val payload = parseJson(bodyText) ?: return badRequest("invalid_json", "请求体不是合法 JSON")
        val content = payload.optString("content")
        val authorLabel = payload.optString("author_label", "访客")
        val authorDevice = payload.optString("author_device").takeIf { it.isNotBlank() }
        val attachmentRefs = parseAttachmentRefs(payload.optJSONArray("attachments"))
        val message = store.appendMessage(boardId, authorLabel, content, authorDevice, attachmentRefs)
            ?: return badRequest(
                "invalid_message",
                if (attachmentRefs.isNotEmpty()) "消息无效或附件未就绪" else "消息内容不能为空或留言板不存在"
            )
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

    private fun parseAttachmentRefs(array: JSONArray?): List<BulletinAttachmentRef> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(BulletinAttachmentRef.fromJson(array.getJSONObject(i)))
            }
        }
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

    private fun boardIdFromAttachmentsPath(path: String, suffix: String): String? {
        if (!path.startsWith("/boards/") || !path.endsWith(suffix)) return null
        return path.removePrefix("/boards/").removeSuffix(suffix).takeIf { it.isNotBlank() }
    }

    private fun boardIdFromCompletePath(path: String): String? =
        Regex("""/boards/([^/]+)/attachments/([^/]+)/complete""").find(path)?.groupValues?.getOrNull(1)

    private fun attachmentIdFromCompletePath(path: String): String? =
        Regex("""/boards/([^/]+)/attachments/([^/]+)/complete""").find(path)?.groupValues?.getOrNull(2)

    private fun parseJson(bodyText: String): JSONObject? =
        runCatching { JSONObject(bodyText) }.getOrNull()

    private fun badRequest(code: String, message: String): FamilyHttpResponse =
        FamilyHttpResponse(400, jsonError(code, message))

    private fun forbidden(message: String): FamilyHttpResponse =
        FamilyHttpResponse(403, jsonError("forbidden", message))

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
    val bodyBytes: ByteArray,
    val contentType: String = "application/json; charset=utf-8",
    val extraHeaders: Map<String, String> = emptyMap()
) {
    constructor(statusCode: Int, body: String, contentType: String = "application/json; charset=utf-8") :
        this(statusCode, body.toByteArray(Charsets.UTF_8), contentType)

    val body: String
        get() = bodyBytes.toString(Charsets.UTF_8)
}
