package com.kenny.localmanager.family

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class BulletinBoardStore(context: Context) {
    private val rootDir = File(context.filesDir, "family_boards")
    private val lock = ReentrantReadWriteLock()
    val attachments = BulletinAttachmentStore(rootDir)

    init {
        ensureDefaultBoard()
    }

    fun ensureDefaultBoard() {
        lock.write {
            boardDir(BulletinBoardDefaults.DEFAULT_BOARD_ID).mkdirs()
            val metaFile = metaFile(BulletinBoardDefaults.DEFAULT_BOARD_ID)
            if (!metaFile.exists()) {
                metaFile.writeText(
                    JSONObject().apply {
                        put("id", BulletinBoardDefaults.DEFAULT_BOARD_ID)
                        put("name", BulletinBoardDefaults.DEFAULT_BOARD_NAME)
                        put("revision", 0L)
                    }.toString()
                )
            }
            val messagesFile = messagesFile(BulletinBoardDefaults.DEFAULT_BOARD_ID)
            if (!messagesFile.exists()) {
                messagesFile.writeText("[]")
            }
        }
    }

    fun listBoards(): List<BulletinBoardInfo> = lock.read {
        rootDir.listFiles()?.mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            val meta = readMeta(dir.name) ?: return@mapNotNull null
            val activeCount = readMessages(dir.name).count { it.isConversationMessage }
            BulletinBoardInfo(
                id = meta.getString("id"),
                name = meta.optString("name", dir.name),
                revision = meta.optLong("revision"),
                messageCount = activeCount
            )
        }.orEmpty().sortedBy { it.id }
    }

    fun snapshot(boardId: String): BulletinBoardSnapshot? = lock.read {
        val meta = readMeta(boardId) ?: return@read null
        val messages = readMessages(boardId).filter { !it.deleted }.sortedBy { it.seq }
        BulletinBoardSnapshot(
            boardId = boardId,
            boardName = meta.optString("name", boardId),
            revision = meta.optLong("revision"),
            messages = messages
        )
    }

    fun isBoardNameTaken(name: String, excludeBoardId: String? = null): Boolean = lock.read {
        isBoardNameTakenLocked(name.trim(), excludeBoardId)
    }

    fun renameBoard(boardId: String, newName: String): BulletinBoardInfo? = lock.write {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return@write null
        val meta = readMeta(boardId) ?: return@write null
        if (isBoardNameTakenLocked(trimmed, boardId)) return@write null
        meta.put("name", trimmed)
        writeMeta(boardId, meta)
        val activeCount = readMessages(boardId).count { it.isConversationMessage }
        BulletinBoardInfo(
            id = meta.getString("id"),
            name = trimmed,
            revision = meta.optLong("revision"),
            messageCount = activeCount
        )
    }

    fun createBoard(name: String): BulletinBoardInfo? = lock.write {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@write null
        if (isBoardNameTakenLocked(trimmed, excludeBoardId = null)) return@write null
        val boardId = UUID.randomUUID().toString()
        boardDir(boardId).mkdirs()
        writeMeta(
            boardId,
            JSONObject().apply {
                put("id", boardId)
                put("name", trimmed)
                put("revision", 0L)
                put("created_at", System.currentTimeMillis())
            }
        )
        writeMessages(boardId, emptyList())
        BulletinBoardInfo(id = boardId, name = trimmed, revision = 0L, messageCount = 0)
    }

    fun exportBoardpack(boardId: String): ByteArray? = lock.read {
        if (readMeta(boardId) == null) return@read null
        runCatching {
            BulletinBoardPack.exportBoardDir(boardDir(boardId))
        }.getOrElse { throw IllegalStateException(it.message ?: "导出 boardpack 失败") }
    }

    fun importBoardpack(
        data: ByteArray,
        name: String? = null,
        roleIds: List<String>? = null
    ): BulletinBoardInfo = lock.write {
        runCatching {
            BulletinBoardPack.importIntoRoot(rootDir, data, name, roleIds)
        }.getOrElse { error ->
            val packError = error as? BulletinBoardPack.PackException
            throw IllegalStateException(packError?.message ?: error.message ?: "导入 boardpack 失败")
        }
    }

    fun deleteBoard(boardId: String): Boolean = lock.write {
        if (readMeta(boardId) == null) return@write false
        val boardCount = rootDir.listFiles()?.count { file ->
            file.isDirectory && readMeta(file.name) != null
        } ?: 0
        if (boardCount <= 1) return@write false
        boardDir(boardId).deleteRecursively()
    }

    fun appendMessage(
        boardId: String,
        authorLabel: String,
        content: String,
        authorDevice: String? = null,
        attachments: List<BulletinAttachmentRef> = emptyList()
    ): BulletinMessage? = lock.write {
        val trimmed = content.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return@write null
        attachments.forEach { ref ->
            if (!this.attachments.isAttachmentReady(boardId, ref.id)) return@write null
        }
        val meta = readMeta(boardId) ?: return@write null
        val messages = readMessages(boardId)
        val nextSeq = (messages.maxOfOrNull { it.seq } ?: 0L) + 1L
        val now = System.currentTimeMillis()
        val message = BulletinMessage(
            id = UUID.randomUUID().toString(),
            seq = nextSeq,
            authorLabel = authorLabel.trim().ifEmpty { "访客" },
            content = trimmed,
            createdAt = now,
            updatedAt = now,
            authorDevice = authorDevice?.trim()?.takeIf { it.isNotEmpty() },
            attachments = attachments
        )
        messages += message
        writeMessages(boardId, messages)
        writeMeta(boardId, meta.put("revision", meta.optLong("revision") + 1L))
        message
    }

    fun updateMessage(boardId: String, messageId: String, content: String): BulletinMessage? = lock.write {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return@write null
        val meta = readMeta(boardId) ?: return@write null
        val messages = readMessages(boardId)
        val index = messages.indexOfFirst { it.id == messageId && !it.deleted }
        if (index < 0) return@write null
        val now = System.currentTimeMillis()
        val updated = messages[index].copy(content = trimmed, updatedAt = now)
        messages[index] = updated
        writeMessages(boardId, messages)
        writeMeta(boardId, meta.put("revision", meta.optLong("revision") + 1L))
        updated
    }

    fun deleteMessage(boardId: String, messageId: String): Boolean = lock.write {
        val meta = readMeta(boardId) ?: return@write false
        val messages = readMessages(boardId)
        val index = messages.indexOfFirst { it.id == messageId && !it.deleted }
        if (index < 0) return@write false
        val target = messages[index]
        target.attachments.forEach { ref ->
            attachments.deleteAttachment(boardId, ref.id)
        }
        val now = System.currentTimeMillis()
        messages[index] = target.copy(deleted = true, updatedAt = now)
        writeMessages(boardId, messages)
        writeMeta(boardId, meta.put("revision", meta.optLong("revision") + 1L))
        true
    }

    private fun boardDir(boardId: String): File = File(rootDir, boardId)

    private fun metaFile(boardId: String): File = File(boardDir(boardId), "meta.json")

    private fun messagesFile(boardId: String): File = File(boardDir(boardId), "messages.json")

    private fun readMeta(boardId: String): JSONObject? {
        val file = metaFile(boardId)
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    private fun writeMeta(boardId: String, meta: JSONObject) {
        metaFile(boardId).writeText(meta.toString())
    }

    private fun readMessages(boardId: String): MutableList<BulletinMessage> {
        val file = messagesFile(boardId)
        if (!file.exists()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    add(BulletinMessage.fromJson(arr.getJSONObject(i)))
                }
            }.toMutableList()
        }.getOrElse { mutableListOf() }
    }

    private fun writeMessages(boardId: String, messages: List<BulletinMessage>) {
        val arr = JSONArray()
        messages.forEach { arr.put(it.toJson()) }
        messagesFile(boardId).writeText(arr.toString())
    }

    private fun isBoardNameTakenLocked(name: String, excludeBoardId: String?): Boolean {
        if (name.isEmpty()) return false
        return rootDir.listFiles()?.any { dir ->
            if (!dir.isDirectory) return@any false
            if (dir.name == excludeBoardId) return@any false
            val meta = readMeta(dir.name) ?: return@any false
            meta.optString("name", dir.name).trim() == name
        } == true
    }
}
