package com.kenny.localmanager.family

import android.content.Context
import android.net.Uri
import com.kenny.localmanager.R
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BulletinBoardPack {
    const val FORMAT = "localmanager.boardpack"
    const val VERSION = 1
    private const val MANIFEST_NAME = "manifest.json"
    private const val BOARD_PREFIX = "board/"
    private val fileTimeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    class PackException(val code: String, override val message: String) : Exception(message)

    fun peekBoardName(context: Context, data: ByteArray): String {
        var name: String? = null
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == "${BOARD_PREFIX}meta.json") {
                    val meta = runCatching { JSONObject(String(zip.readBytes(), Charsets.UTF_8)) }
                        .getOrElse {
                            throw PackException("invalid_board", context.getString(R.string.family_msg_04549))
                        }
                    name = meta.optString("name").trim()
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return name?.takeIf { it.isNotEmpty() }
            ?: throw PackException("invalid_boardpack", context.getString(R.string.family_msg_09744))
    }

    fun suggestUniqueBoardName(context: Context, baseName: String, existingNames: Set<String>): String {
        val trimmed = baseName.trim().ifBlank { "board" }
        if (trimmed !in existingNames) return trimmed
        val imported = context.getString(R.string.family_msg_47294, trimmed)
        if (imported !in existingNames) return imported
        var index = 2
        while (true) {
            val candidate = "$trimmed ($index)"
            if (candidate !in existingNames) return candidate
            index += 1
        }
    }

    fun exportBoardDir(context: Context, boardDir: File, sourceDevice: String = "android"): ByteArray {
        val metaFile = File(boardDir, "meta.json")
        if (!metaFile.exists()) {
            throw PackException("board_not_found", context.getString(R.string.family_msg_02583, boardDir.name))
        }
        val meta = runCatching { JSONObject(metaFile.readText()) }
            .getOrElse { throw PackException("invalid_board", context.getString(R.string.family_msg_93179)) }
        val boardId = meta.optString("id", boardDir.name)
        val boardName = meta.optString("name", boardId)
        val stats = collectStats(boardDir)
        val manifest = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exported_at", System.currentTimeMillis())
            put("source", JSONObject().apply {
                put("device", sourceDevice)
                put("board_id", boardId)
                put("board_name", boardName)
            })
            put("stats", JSONObject().apply {
                put("message_count", stats.messageCount)
                put("attachment_count", stats.attachmentCount)
                put("total_bytes", stats.totalBytes)
            })
        }
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            boardDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relative = file.relativeTo(boardDir).path.replace('\\', '/')
                    zip.putNextEntry(ZipEntry("$BOARD_PREFIX$relative"))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return buffer.toByteArray()
    }

    fun importIntoRoot(
        context: Context,
        rootDir: File,
        data: ByteArray,
        name: String? = null,
        roleIds: List<String>? = null
    ): BulletinBoardInfo {
        rootDir.mkdirs()
        val extracted = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            var entry = zip.nextEntry
            var manifest: JSONObject? = null
            while (entry != null) {
                if (!entry.isDirectory) {
                    val bytes = zip.readBytes()
                    if (entry.name == MANIFEST_NAME) {
                        manifest = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }
                            .getOrElse {
                                throw PackException("invalid_boardpack", context.getString(R.string.family_msg_35962))
                            }
                    }
                    extracted[entry.name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            validateManifest(context, manifest)
        }
        if (!extracted.containsKey("${BOARD_PREFIX}meta.json")) {
            throw PackException("invalid_boardpack", context.getString(R.string.family_msg_34360))
        }

        val newBoardId = UUID.randomUUID().toString()
        val boardDir = File(rootDir, newBoardId)
        boardDir.mkdirs()
        extracted.forEach { (entryName, bytes) ->
            if (!entryName.startsWith(BOARD_PREFIX) || entryName.endsWith("/")) return@forEach
            val relative = entryName.removePrefix(BOARD_PREFIX)
            if (relative.isBlank() || relative.contains("..")) {
                throw PackException("invalid_boardpack", context.getString(R.string.family_msg_09808, entryName))
            }
            val target = File(boardDir, relative)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { it.write(bytes) }
        }

        val metaFile = File(boardDir, "meta.json")
        val meta = runCatching { JSONObject(metaFile.readText()) }
            .getOrElse {
                boardDir.deleteRecursively()
                throw PackException("invalid_board", context.getString(R.string.family_msg_04549))
            }
        val trimmedName = name?.trim().orEmpty()
        if (name != null && trimmedName.isEmpty()) {
            boardDir.deleteRecursively()
            throw PackException("invalid_board", context.getString(R.string.family_msg_15321))
        }
        val finalName = trimmedName.ifEmpty { meta.optString("name", newBoardId).trim() }
        if (finalName.isEmpty()) {
            boardDir.deleteRecursively()
            throw PackException("invalid_board", context.getString(R.string.family_msg_82976))
        }
        if (isBoardNameTaken(rootDir, finalName)) {
            boardDir.deleteRecursively()
            throw PackException("board_name_duplicate", context.getString(R.string.family_msg_43889, finalName))
        }
        meta.put("name", finalName)
        if (roleIds != null) {
            meta.put("role_ids", JSONArray(normalizeRoleIds(roleIds)))
        }
        meta.put("id", newBoardId)
        meta.put("revision", 0L)
        meta.put("imported_at", System.currentTimeMillis())
        metaFile.writeText(meta.toString())

        val activeCount = countActiveMessages(File(boardDir, "messages.json"))
        val storedRoleIds = when {
            !meta.has("role_ids") -> null
            meta.isNull("role_ids") -> emptyList()
            else -> buildList {
                val arr = meta.optJSONArray("role_ids") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    add(arr.optString(i).trim())
                }
            }.filter { it.isNotEmpty() && it != "admin" }
        }
        return BulletinBoardInfo(
            id = newBoardId,
            name = meta.optString("name", newBoardId),
            revision = 0L,
            messageCount = activeCount,
            roleIds = storedRoleIds
        )
    }

    fun saveToRoot(
        context: Context,
        rootUri: String,
        fileName: String,
        data: ByteArray
    ): Result<String> = runCatching {
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
            ?: throw IllegalStateException(context.getString(R.string.family_msg_54649))
        val exportDir = BulletinBoardExporter.findOrCreateExportDir(context, rootDoc)
            ?: throw IllegalStateException(context.getString(R.string.family_msg_54837))
        val safeName = sanitizePackFileName(fileName)
        exportDir.findFile(safeName)?.takeIf { !it.isDirectory }?.delete()
        val created = exportDir.createFile("application/octet-stream", safeName)
            ?: throw IllegalStateException(context.getString(R.string.family_msg_78543))
        context.contentResolver.openOutputStream(created.uri)?.use { output ->
            output.write(data)
        } ?: throw IllegalStateException(context.getString(R.string.family_msg_25388))
        "${BulletinBoardExporter.exportDirName(context)}/$safeName"
    }

    fun writeToUri(context: Context, uri: Uri, data: ByteArray): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(data)
        } ?: throw IllegalStateException(context.getString(R.string.family_msg_11874))
    }

    fun readFromDocumentFile(context: Context, file: com.kenny.localmanager.file.DocumentFileModel): Result<ByteArray> =
        readFromUri(context, file.uri)

    fun readFromUri(context: Context, uri: Uri): Result<ByteArray> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException(context.getString(R.string.family_msg_94078))
    }

    fun defaultPackFileName(boardName: String): String {
        val safeBoard = sanitizePackFileName(boardName).removeSuffix(".boardpack")
        val ts = fileTimeFormat.format(Date())
        return "${safeBoard}_$ts.boardpack"
    }

    private fun isBoardNameTaken(rootDir: File, name: String): Boolean {
        if (name.isEmpty()) return false
        return rootDir.listFiles()?.any { dir ->
            if (!dir.isDirectory) return@any false
            val metaFile = File(dir, "meta.json")
            if (!metaFile.isFile) return@any false
            val meta = runCatching { JSONObject(metaFile.readText()) }.getOrNull() ?: return@any false
            meta.optString("name", dir.name).trim() == name
        } == true
    }

    private fun validateManifest(context: Context, manifest: JSONObject?) {
        if (manifest == null) {
            throw PackException("invalid_boardpack", context.getString(R.string.family_msg_40120))
        }
        if (manifest.optString("format") != FORMAT) {
            throw PackException(
                "unsupported_boardpack",
                context.getString(R.string.family_msg_24247, manifest.optString("format"))
            )
        }
        if (manifest.optInt("version") != VERSION) {
            throw PackException(
                "unsupported_boardpack",
                context.getString(R.string.family_msg_86457, manifest.optInt("version"), VERSION)
            )
        }
    }

    private fun normalizeRoleIds(roleIds: List<String>): List<String> =
        roleIds.map { it.trim() }.filter { it.isNotEmpty() && it != "admin" }.distinct()

    private fun sanitizePackFileName(name: String): String {
        val base = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "board" }
        return if (base.endsWith(".boardpack", ignoreCase = true)) base else "$base.boardpack"
    }

    private data class PackStats(val messageCount: Int, val attachmentCount: Int, val totalBytes: Long)

    private fun collectStats(boardDir: File): PackStats {
        val messagesFile = File(boardDir, "messages.json")
        var totalBytes = messagesFile.takeIf { it.isFile }?.length() ?: 0L
        totalBytes += File(boardDir, "meta.json").takeIf { it.isFile }?.length() ?: 0L
        var attachmentCount = 0
        val attachmentsRoot = File(boardDir, "attachments")
        if (attachmentsRoot.isDirectory) {
            attachmentsRoot.listFiles()?.forEach { attDir ->
                if (!attDir.isDirectory) return@forEach
                val meta = runCatching {
                    JSONObject(File(attDir, "attachment.json").readText())
                }.getOrNull() ?: return@forEach
                if (meta.optString("status") != "ready") return@forEach
                attachmentCount += 1
                totalBytes += attachmentBytes(attDir, meta)
            }
        }
        return PackStats(
            messageCount = countActiveMessages(messagesFile),
            attachmentCount = attachmentCount,
            totalBytes = totalBytes
        )
    }

    private fun attachmentBytes(attDir: File, meta: JSONObject): Long {
        return if (meta.optString("kind") == "directory") {
            val filesDir = File(attDir, "files")
            filesDir.walkTopDown().filter { it.name == "blob" && it.isFile }.sumOf { it.length() }
        } else {
            File(attDir, "blob").takeIf { it.isFile }?.length()
                ?: meta.optLong("size", 0L)
        }
    }

    private fun countActiveMessages(messagesFile: File): Int {
        if (!messagesFile.isFile) return 0
        val arr = runCatching { JSONArray(messagesFile.readText()) }.getOrElse { return 0 }
        var count = 0
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optBoolean("deleted")) continue
            val kind = item.optString("message_kind", BulletinMessageKind.MESSAGE)
            val content = item.optString("content", "")
            if (kind == BulletinMessageKind.AI_STATUS ||
                content.trimStart().startsWith("/ai status", ignoreCase = true)
            ) {
                continue
            }
            count += 1
        }
        return count
    }
}
