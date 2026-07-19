package com.kenny.localmanager.family

import android.content.Context
import com.kenny.localmanager.R
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class BulletinBoardStore(context: Context) {
    val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "family_boards")
    private val lock = ReentrantReadWriteLock()
    val attachments = BulletinAttachmentStore(rootDir)

    init {
        ensureDefaultBoard()
    }

    fun ensureDefaultBoard() {
        lock.write {
            val metaFile = metaFile(BulletinBoardDefaults.DEFAULT_BOARD_ID)
            val hasAnyBoard = rootDir.listFiles()?.any { dir ->
                dir.isDirectory && readMeta(dir.name) != null
            } == true
            if (!metaFile.exists() && hasAnyBoard) {
                // Keep existing boards stable: do not force-create an empty "default" board
                // when the store already has valid boards.
                return@write
            }
            boardDir(BulletinBoardDefaults.DEFAULT_BOARD_ID).mkdirs()
            if (!metaFile.exists()) {
                metaFile.writeText(
                    JSONObject().apply {
                        put("id", BulletinBoardDefaults.DEFAULT_BOARD_ID)
                        put("name", appContext.getString(R.string.family_board_default_name))
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
            boardInfoFromMeta(dir.name) ?: return@mapNotNull null
        }.orEmpty().sortedBy { it.id }
    }

    fun getBoardInfo(boardId: String): BulletinBoardInfo? = lock.read {
        boardInfoFromMeta(boardId)
    }

    private fun boardInfoFromMeta(boardId: String): BulletinBoardInfo? {
        val meta = readMeta(boardId) ?: return null
        val activeCount = readMessages(boardId).count { it.isConversationMessage }
        return BulletinBoardInfo(
            id = meta.getString("id"),
            name = meta.optString("name", boardId),
            revision = meta.optLong("revision"),
            messageCount = activeCount,
            roleIds = BulletinBoardInfo.roleIdsFromMeta(meta)
        )
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
            messageCount = activeCount,
            roleIds = BulletinBoardInfo.roleIdsFromMeta(meta)
        )
    }

    fun updateBoard(
        boardId: String,
        name: String? = null,
        roleIds: List<String>? = null
    ): BulletinBoardInfo? = lock.write {
        val meta = readMeta(boardId) ?: return@write null
        if (name != null) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@write null
            if (isBoardNameTakenLocked(trimmed, boardId)) return@write null
            meta.put("name", trimmed)
        }
        if (roleIds != null) {
            meta.put("role_ids", JSONArray(normalizeRoleIds(roleIds)))
        }
        writeMeta(boardId, meta)
        boardInfoFromMeta(boardId)
    }

    private fun normalizeRoleIds(roleIds: List<String>): List<String> =
        roleIds.map { it.trim() }.filter { it.isNotEmpty() && it != FamilyNetworkRoles.ADMIN_ROLE_ID }.distinct()

    fun createBoard(name: String, roleIds: List<String> = emptyList()): BulletinBoardInfo? = lock.write {
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
                put("role_ids", JSONArray(normalizeRoleIds(roleIds)))
            }
        )
        writeMessages(boardId, emptyList())
        BulletinBoardInfo(
            id = boardId,
            name = trimmed,
            revision = 0L,
            messageCount = 0,
            roleIds = normalizeRoleIds(roleIds)
        )
    }

    fun exportBoardpack(
        boardId: String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): ByteArray? = lock.read {
        if (readMeta(boardId) == null) return@read null
        runCatching {
            BulletinBoardPack.exportBoardDir(appContext, boardDir(boardId), onProgress = onProgress, isCancelled = isCancelled)
        }.getOrElse { throw IllegalStateException(it.message ?: appContext.getString(R.string.family_msg_74262)) }
    }

    fun estimateBoardpack(boardId: String): BulletinBoardPack.PackSummary? = lock.read {
        if (readMeta(boardId) == null) return@read null
        runCatching {
            BulletinBoardPack.summarizeBoardDir(appContext, boardDir(boardId))
        }.getOrNull()
    }

    fun exportBoardpackToRoot(
        boardId: String,
        rootUri: String,
        fileName: String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Result<String> = lock.read {
        if (readMeta(boardId) == null) {
            return@read Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_65793, boardId)))
        }
        runCatching {
            BulletinBoardPack.exportBoardDirToRoot(
                context = appContext,
                rootUri = rootUri,
                fileName = fileName,
                boardDir = boardDir(boardId),
                onProgress = onProgress,
                isCancelled = isCancelled
            ).getOrThrow()
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                val packError = error as? BulletinBoardPack.PackException
                Result.failure(IllegalStateException(packError?.message ?: error.message ?: appContext.getString(R.string.family_msg_74262)))
            }
        )
    }

    fun exportBoardpackToOutputStream(
        boardId: String,
        output: OutputStream,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Result<Unit> = lock.read {
        if (readMeta(boardId) == null) {
            return@read Result.failure(IllegalStateException(appContext.getString(R.string.family_msg_65793, boardId)))
        }
        runCatching {
            BulletinBoardPack.exportBoardDirToOutputStream(
                context = appContext,
                boardDir = boardDir(boardId),
                output = output,
                onProgress = onProgress,
                isCancelled = isCancelled
            )
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                val packError = error as? BulletinBoardPack.PackException
                Result.failure(IllegalStateException(packError?.message ?: error.message ?: appContext.getString(R.string.family_msg_74262)))
            }
        )
    }

    fun importBoardpack(
        data: ByteArray,
        name: String? = null,
        roleIds: List<String>? = null,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): BulletinBoardInfo = lock.write {
        runCatching {
            BulletinBoardPack.importIntoRoot(appContext, rootDir, data, name, roleIds, onProgress, isCancelled)
        }.getOrElse { error ->
            val packError = error as? BulletinBoardPack.PackException
            throw IllegalStateException(packError?.message ?: error.message ?: appContext.getString(R.string.family_msg_68600))
        }
    }

    fun importBoardpackFromStream(
        inputStream: InputStream,
        name: String? = null,
        roleIds: List<String>? = null,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): BulletinBoardInfo = lock.write {
        runCatching {
            BulletinBoardPack.importIntoRoot(appContext, rootDir, inputStream, name, roleIds, onProgress, isCancelled)
        }.getOrElse { error ->
            val packError = error as? BulletinBoardPack.PackException
            throw IllegalStateException(packError?.message ?: error.message ?: appContext.getString(R.string.family_msg_68600))
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
            authorLabel = authorLabel.trim().ifEmpty { appContext.getString(R.string.family_board_guest_label) },
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
