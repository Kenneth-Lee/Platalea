package com.kenny.localmanager.family

import android.content.Context
import com.kenny.localmanager.R

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class BulletinBoardHttpHandler(
    private val context: Context,
    private val store: BulletinBoardStore
) {
    fun handle(
        method: String,
        path: String,
        bodyBytes: ByteArray,
        bodyStream: InputStream? = null,
        auth: FamilyNetworkSessionAuth,
        headers: Map<String, String> = emptyMap()
    ): FamilyHttpResponse {
        val normalizedPath = path.substringBefore('?').trimEnd('/').ifEmpty { "/" }
        if (requiresHostAuth(method, normalizedPath) && !auth.canManageBoard) {
            return forbidden(context.getString(R.string.family_http_remote_modify_forbidden))
        }
        return when {
            method == "GET" && normalizedPath == "/boards" -> listBoards(auth)
            method == "POST" && normalizedPath == "/boards" -> createBoard(bodyBytes.decodeToString())
            method == "PATCH" && normalizedPath.startsWith("/boards/") &&
                !normalizedPath.contains("/messages") && !normalizedPath.contains("/attachments") -> {
                val boardId = normalizedPath.removePrefix("/boards/")
                if (boardId.isBlank() || boardId.contains('/')) return notFound()
                patchBoard(boardId, bodyBytes.decodeToString())
            }
            method == "DELETE" && normalizedPath.startsWith("/boards/") &&
                !normalizedPath.contains("/messages") && !normalizedPath.contains("/attachments") -> {
                val boardId = normalizedPath.removePrefix("/boards/")
                if (boardId.isBlank() || boardId.contains('/')) return notFound()
                deleteBoard(boardId)
            }
            method == "GET" && normalizedPath.startsWith("/boards/") && normalizedPath.endsWith("/export.md") -> {
                val boardId = normalizedPath.removePrefix("/boards/").removeSuffix("/export.md")
                if (boardId.isBlank() || boardId.contains('/')) return notFound()
                exportBoard(boardId, auth)
            }
            method == "GET" && normalizedPath.startsWith("/boards/") && normalizedPath.endsWith("/export.boardpack") -> {
                val boardId = normalizedPath.removePrefix("/boards/").removeSuffix("/export.boardpack")
                if (boardId.isBlank() || boardId.contains('/')) return notFound()
                exportBoardpack(boardId, auth)
            }
            method == "POST" && normalizedPath == "/boards/import" -> {
                importBoardpack(path, bodyBytes, bodyStream)
            }
            method == "GET" && normalizedPath.startsWith("/boards/") && normalizedPath.endsWith("/messages") -> {
                val boardId = normalizedPath.removePrefix("/boards/").removeSuffix("/messages")
                getMessages(boardId, auth)
            }
            method == "POST" && normalizedPath.startsWith("/boards/") && normalizedPath.endsWith("/messages") -> {
                val boardId = normalizedPath.removePrefix("/boards/").removeSuffix("/messages")
                createMessage(boardId, bodyBytes.decodeToString(), auth)
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
        if (method == "POST" && path == "/boards") return true
        if (method == "PATCH" && path.startsWith("/boards/") &&
            !path.contains("/messages") && !path.contains("/attachments")
        ) {
            return true
        }
        if (method == "DELETE" && path.startsWith("/boards/") &&
            !path.contains("/messages") && !path.contains("/attachments")
        ) {
            return true
        }
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
                chunkIndex.toIntOrNull() ?: return badRequest("invalid_attachment", context.getString(R.string.family_msg_84534)),
                bodyBytes
            ) ?: return badRequest("invalid_attachment", context.getString(R.string.family_msg_53911))
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
            chunkIndex.toIntOrNull() ?: return badRequest("invalid_attachment", context.getString(R.string.family_msg_84534)),
            bodyBytes
        ) ?: return badRequest("invalid_attachment", context.getString(R.string.family_msg_61610))
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
            if (rangeHeader.isNullOrBlank()) {
                val stream = store.attachments.openDirectoryFileBlob(boardId, attachmentId, fileId)
                    ?: return FamilyHttpResponse(404, jsonError("attachment_not_found", context.getString(R.string.family_msg_48095)))
                return streamBlobResponse(stream)
            }
            val result = store.attachments.readDirectoryFileBlob(boardId, attachmentId, fileId, rangeHeader)
                ?: return FamilyHttpResponse(404, jsonError("attachment_not_found", context.getString(R.string.family_msg_48095)))
            return blobResponse(result)
        }
        val fileBlob = Regex("""/boards/([^/]+)/attachments/([^/]+)/blob""").find(path)
            ?: return notFound()
        val (boardId, attachmentId) = fileBlob.destructured
        if (rangeHeader.isNullOrBlank()) {
            val stream = store.attachments.openFileBlob(boardId, attachmentId)
                ?: return FamilyHttpResponse(404, jsonError("attachment_not_found", context.getString(R.string.family_msg_48095)))
            return streamBlobResponse(stream)
        }
        val result = store.attachments.readFileBlob(boardId, attachmentId, rangeHeader)
            ?: return FamilyHttpResponse(404, jsonError("attachment_not_found", context.getString(R.string.family_msg_48095)))
        return blobResponse(result)
    }

    private fun blobResponse(result: BlobReadResult): FamilyHttpResponse {
        val headers = mutableMapOf<String, String>()
        headers["Accept-Ranges"] = "bytes"
        if (result.contentRange != null) {
            headers["Content-Range"] = result.contentRange
        }
        headers["X-Blob-Stream"] = "0"
        return FamilyHttpResponse(
            statusCode = result.statusCode,
            bodyBytes = result.bytes,
            contentType = "application/octet-stream",
            extraHeaders = headers
        )
    }

    private fun streamBlobResponse(streamResult: BlobStreamResult): FamilyHttpResponse {
        val headers = mutableMapOf<String, String>()
        headers["Accept-Ranges"] = "bytes"
        if (streamResult.contentRange != null) {
            headers["Content-Range"] = streamResult.contentRange
        }
        headers["X-Blob-Stream"] = "1"
        return FamilyHttpResponse(
            statusCode = streamResult.statusCode,
            bodyBytes = ByteArray(0),
            contentType = "application/octet-stream",
            extraHeaders = headers,
            bodyStream = streamResult.inputStream,
            bodyLength = streamResult.totalSize
        )
    }

    private fun initAttachment(boardId: String, bodyText: String): FamilyHttpResponse {
        val payload = parseJson(bodyText) ?: return badRequest("invalid_json", context.getString(R.string.family_msg_62464))
        val kind = BulletinAttachmentKind.fromWire(payload.optString("kind", "file"))
            ?: return badRequest("invalid_attachment", context.getString(R.string.family_msg_91834))
        val uploaderDevice = payload.optString("uploader_device").takeIf { it.isNotBlank() }
        val result = when (kind) {
            BulletinAttachmentKind.FILE -> {
                val name = payload.optString("name")
                val size = payload.optLong("size", -1)
                if (name.isBlank() || size < 0) {
                    return badRequest("invalid_attachment", context.getString(R.string.family_msg_88641))
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
                    return badRequest("invalid_attachment", context.getString(R.string.family_msg_89635))
                }
                store.attachments.initDirectoryUpload(boardId, name, entries, uploaderDevice)
            }
        } ?: return badRequest("invalid_attachment", context.getString(R.string.family_msg_97057))
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
            ?: return badRequest("incomplete_upload", context.getString(R.string.family_msg_71159))
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
            ?: return FamilyHttpResponse(404, jsonError("attachment_not_found", context.getString(R.string.family_msg_34495)))
        return FamilyHttpResponse(200, meta.toString())
    }

    private fun deleteAttachment(boardId: String, attachmentId: String): FamilyHttpResponse {
        if (!store.attachments.deleteAttachment(boardId, attachmentId)) {
            return FamilyHttpResponse(404, jsonError("attachment_not_found", context.getString(R.string.family_msg_55026)))
        }
        return FamilyHttpResponse(200, JSONObject().apply { put("ok", true) }.toString())
    }

    private fun listBoards(auth: FamilyNetworkSessionAuth): FamilyHttpResponse {
        val boards = store.listBoards().filter { canAccessBoard(auth, it.roleIds) }
        val body = JSONObject().apply {
            put("ok", true)
            put("role_id", auth.sessionRoleId)
            put("role_class", auth.sessionRoleClass)
            put("can_manage", auth.canManageBoard)
            auth.roleLabel?.let { put("role_label", it) }
            put("boards", JSONArray().apply {
                boards.forEach { board ->
                    put(
                        JSONObject().apply {
                            put("id", board.id)
                            put("name", board.name)
                            put("revision", board.revision)
                            put("message_count", board.messageCount)
                            if (auth.canManageBoard) {
                                put("role_ids", board.roleIds?.let { JSONArray(it) } ?: JSONObject.NULL)
                            }
                        }
                    )
                }
            })
        }
        return FamilyHttpResponse(200, body.toString())
    }

    private fun createBoard(bodyText: String): FamilyHttpResponse {
        val payload = parseJson(bodyText) ?: return badRequest("invalid_json", context.getString(R.string.family_msg_62464))
        val name = payload.optString("name").trim()
        if (name.isEmpty()) return badRequest("invalid_board", context.getString(R.string.family_msg_82976))
        if (store.isBoardNameTaken(name)) {
            return badRequest("board_name_duplicate", context.getString(R.string.family_msg_76614))
        }
        val roleIds = parseRoleIdsFromPayload(payload) ?: emptyList()
        val board = store.createBoard(name, roleIds)
            ?: return badRequest("invalid_board", context.getString(R.string.family_msg_64304))
        return FamilyHttpResponse(200, boardResponseJson(board, includeRoleIds = true))
    }

    private fun patchBoard(boardId: String, bodyText: String): FamilyHttpResponse {
        val payload = parseJson(bodyText) ?: return badRequest("invalid_json", context.getString(R.string.family_msg_62464))
        val name = payload.optString("name").trim().ifEmpty { null }
        val roleIds = if (payload.has("role_ids")) parseRoleIdsFromPayload(payload) else null
        if (name == null && roleIds == null) {
            return badRequest("invalid_board", context.getString(R.string.family_msg_82976))
        }
        if (name != null && store.isBoardNameTaken(name, excludeBoardId = boardId)) {
            return badRequest("board_name_duplicate", context.getString(R.string.family_msg_76614))
        }
        val board = store.updateBoard(boardId, name = name, roleIds = roleIds)
            ?: return badRequest("board_rename_failed", context.getString(R.string.family_msg_90260))
        return FamilyHttpResponse(200, boardResponseJson(board, includeRoleIds = true))
    }

    private fun boardResponseJson(board: BulletinBoardInfo, includeRoleIds: Boolean): String =
        JSONObject().apply {
            put("ok", true)
            put("board", JSONObject().apply {
                put("id", board.id)
                put("name", board.name)
                put("revision", board.revision)
                put("message_count", board.messageCount)
                if (includeRoleIds) {
                    put("role_ids", board.roleIds?.let { JSONArray(it) } ?: JSONObject.NULL)
                }
            })
        }.toString()

    private fun parseRoleIdsFromPayload(payload: JSONObject): List<String>? {
        if (!payload.has("role_ids") || payload.isNull("role_ids")) return emptyList()
        val arr = payload.optJSONArray("role_ids") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                add(arr.optString(i).trim())
            }
        }.filter { it.isNotEmpty() && it != FamilyNetworkRoles.ADMIN_ROLE_ID }.distinct()
    }

    private fun renameBoard(boardId: String, bodyText: String): FamilyHttpResponse {
        return patchBoard(boardId, bodyText)
    }

    private fun deleteBoard(boardId: String): FamilyHttpResponse {
        if (!store.deleteBoard(boardId)) {
            return badRequest(
                "board_delete_failed",
                context.getString(R.string.family_msg_96259)
            )
        }
        return FamilyHttpResponse(200, JSONObject().apply { put("ok", true) }.toString())
    }

    private fun exportBoard(boardId: String, auth: FamilyNetworkSessionAuth): FamilyHttpResponse {
        if (!canAccessBoardForId(boardId, auth)) {
            return FamilyHttpResponse(404, jsonError("board_not_found", context.getString(R.string.family_msg_71757)))
        }
        val snapshot = store.snapshot(boardId)
            ?: return FamilyHttpResponse(404, jsonError("board_not_found", context.getString(R.string.family_msg_71757)))
        val markdown = BulletinBoardExporter.snapshotToMarkdown(context, snapshot)
        return FamilyHttpResponse(
            200,
            markdown,
            "text/markdown; charset=utf-8"
        )
    }

    private fun exportBoardpack(boardId: String, auth: FamilyNetworkSessionAuth): FamilyHttpResponse {
        if (!canAccessBoardForId(boardId, auth)) {
            return FamilyHttpResponse(404, jsonError("board_not_found", context.getString(R.string.family_msg_71757)))
        }
        val board = store.getBoardInfo(boardId)
            ?: return FamilyHttpResponse(404, jsonError("board_not_found", context.getString(R.string.family_msg_71757)))
        val tempFile = File.createTempFile("boardpack_export_", ".zip", context.cacheDir)
        return try {
            FileOutputStream(tempFile).use { output ->
                store.exportBoardpackToOutputStream(boardId, output).getOrThrow()
            }
            val stream = tempFile.inputStream()
            FamilyHttpResponse(
                statusCode = 200,
                bodyBytes = ByteArray(0),
                contentType = "application/vnd.localmanager.boardpack+zip",
                extraHeaders = mapOf(
                    "Content-Disposition" to "attachment; filename=\"${BulletinBoardPack.defaultPackFileName(board.name)}\""
                ),
                bodyStream = stream,
                bodyLength = tempFile.length(),
                onStreamClosed = { tempFile.delete() }
            )
        } catch (error: Throwable) {
            tempFile.delete()
            FamilyHttpResponse(500, jsonError("boardpack_export_failed", error.message ?: error.javaClass.simpleName))
        }
    }

    private fun importBoardpack(path: String, bodyBytes: ByteArray, bodyStream: InputStream?): FamilyHttpResponse {
        val query = path.substringAfter('?', "")
        val params = parseQueryParams(query)
        val name = params["name"]?.trim()?.ifEmpty { null }
        val roleIds = params["role_ids"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
        return try {
            val board = if (bodyStream != null) {
                store.importBoardpackFromStream(bodyStream, name = name, roleIds = roleIds)
            } else {
                store.importBoardpack(bodyBytes, name = name, roleIds = roleIds)
            }
            FamilyHttpResponse(200, boardResponseJson(board, includeRoleIds = true))
        } catch (error: Throwable) {
            badRequest("boardpack_import_failed", error.message ?: error.javaClass.simpleName)
        }
    }

    private fun getMessages(boardId: String, auth: FamilyNetworkSessionAuth): FamilyHttpResponse {
        if (!canAccessBoardForId(boardId, auth)) {
            return FamilyHttpResponse(404, jsonError("board_not_found", context.getString(R.string.family_msg_71757)))
        }
        val snapshot = store.snapshot(boardId)
            ?: return FamilyHttpResponse(404, jsonError("board_not_found", context.getString(R.string.family_msg_71757)))
        val participants = BulletinBoardMention.collectParticipants(snapshot.messages)
        val body = snapshot.copy(
            canManage = auth.canManageBoard,
            roleId = auth.sessionRoleId,
            roleLabel = auth.roleLabel,
            agents = emptyList(),
            participants = participants,
            commands = emptyList()
        ).toJson().apply {
            put("role_class", auth.sessionRoleClass)
        }.toString()
        return FamilyHttpResponse(200, body)
    }

    private fun createMessage(boardId: String, bodyText: String, auth: FamilyNetworkSessionAuth): FamilyHttpResponse {
        if (!canAccessBoardForId(boardId, auth)) {
            return FamilyHttpResponse(404, jsonError("board_not_found", context.getString(R.string.family_msg_71757)))
        }
        val payload = parseJson(bodyText) ?: return badRequest("invalid_json", context.getString(R.string.family_msg_62464))
        val content = payload.optString("content")
        val authorLabel = payload.optString("author_label", context.getString(R.string.family_board_guest_label))
        val authorDevice = payload.optString("author_device").takeIf { it.isNotBlank() }
        val attachmentRefs = parseAttachmentRefs(payload.optJSONArray("attachments"))
        val message = store.appendMessage(boardId, authorLabel, content, authorDevice, attachmentRefs)
            ?: return badRequest(
                "invalid_message",
                if (attachmentRefs.isNotEmpty()) context.getString(R.string.family_msg_55517) else context.getString(R.string.family_msg_84872)
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
        val payload = parseJson(bodyText) ?: return badRequest("invalid_json", context.getString(R.string.family_msg_62464))
        val content = payload.optString("content")
        val message = store.updateMessage(boardId, messageId, content)
            ?: return FamilyHttpResponse(404, jsonError("message_not_found", context.getString(R.string.family_msg_72023)))
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
            return FamilyHttpResponse(404, jsonError("message_not_found", context.getString(R.string.family_msg_33886)))
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

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { item ->
            if (item.isBlank()) return@mapNotNull null
            val key = item.substringBefore('=', "").trim()
            if (key.isEmpty()) return@mapNotNull null
            val value = item.substringAfter('=', "")
            key to java.net.URLDecoder.decode(value, Charsets.UTF_8.name())
        }.toMap()
    }

    private fun canAccessBoardForId(boardId: String, auth: FamilyNetworkSessionAuth): Boolean {
        val info = store.getBoardInfo(boardId) ?: return false
        return canAccessBoard(auth, info.roleIds)
    }

    private fun badRequest(code: String, message: String): FamilyHttpResponse =
        FamilyHttpResponse(400, jsonError(code, message))

    private fun forbidden(message: String): FamilyHttpResponse =
        FamilyHttpResponse(403, jsonError("forbidden", message))

    private fun notFound(): FamilyHttpResponse =
        FamilyHttpResponse(404, jsonError("not_found", context.getString(R.string.family_msg_00813)))

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
    val extraHeaders: Map<String, String> = emptyMap(),
    val bodyStream: java.io.InputStream? = null,
    val bodyLength: Long? = null,
    val onStreamClosed: (() -> Unit)? = null
) {
    constructor(statusCode: Int, body: String, contentType: String = "application/json; charset=utf-8") :
        this(statusCode, body.toByteArray(Charsets.UTF_8), contentType)

    val body: String
        get() = bodyBytes.toString(Charsets.UTF_8)
}
