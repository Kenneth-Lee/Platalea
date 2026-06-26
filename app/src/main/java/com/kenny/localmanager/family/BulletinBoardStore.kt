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
            val activeCount = readMessages(dir.name).count { !it.deleted }
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

    fun appendMessage(boardId: String, authorLabel: String, content: String): BulletinMessage? = lock.write {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return@write null
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
            updatedAt = now
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
        val now = System.currentTimeMillis()
        messages[index] = messages[index].copy(deleted = true, updatedAt = now)
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
}
