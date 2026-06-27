package com.kenny.localmanager.family

import android.content.Context
import android.net.Uri
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

    fun exportBoardDir(boardDir: File, sourceDevice: String = "android"): ByteArray {
        val metaFile = File(boardDir, "meta.json")
        if (!metaFile.exists()) {
            throw PackException("board_not_found", "留言板目录无效：${boardDir.name}")
        }
        val meta = runCatching { JSONObject(metaFile.readText()) }
            .getOrElse { throw PackException("invalid_board", "meta.json 不是合法 JSON") }
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
                                throw PackException("invalid_boardpack", "manifest.json 无效")
                            }
                    }
                    extracted[entry.name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            validateManifest(manifest)
        }
        if (!extracted.containsKey("${BOARD_PREFIX}meta.json")) {
            throw PackException("invalid_boardpack", "包内缺少 board/meta.json")
        }

        val newBoardId = UUID.randomUUID().toString()
        val boardDir = File(rootDir, newBoardId)
        boardDir.mkdirs()
        extracted.forEach { (entryName, bytes) ->
            if (!entryName.startsWith(BOARD_PREFIX) || entryName.endsWith("/")) return@forEach
            val relative = entryName.removePrefix(BOARD_PREFIX)
            if (relative.isBlank() || relative.contains("..")) {
                throw PackException("invalid_boardpack", "包内路径非法：$entryName")
            }
            val target = File(boardDir, relative)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { it.write(bytes) }
        }

        val metaFile = File(boardDir, "meta.json")
        val meta = runCatching { JSONObject(metaFile.readText()) }
            .getOrElse {
                boardDir.deleteRecursively()
                throw PackException("invalid_board", "board/meta.json 不是合法 JSON")
            }
        val trimmedName = name?.trim().orEmpty()
        if (name != null && trimmedName.isEmpty()) {
            boardDir.deleteRecursively()
            throw PackException("invalid_board", "覆盖名称不能为空")
        }
        if (trimmedName.isNotEmpty()) {
            meta.put("name", trimmedName)
        }
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
            ?: throw IllegalStateException("无法访问根目录")
        val exportDir = rootDoc.findFile(BulletinBoardExporter.EXPORT_DIR_NAME)?.takeIf { it.isDirectory }
            ?: rootDoc.createDirectory(BulletinBoardExporter.EXPORT_DIR_NAME)
            ?: throw IllegalStateException("无法创建导出目录")
        val safeName = sanitizePackFileName(fileName)
        exportDir.findFile(safeName)?.takeIf { !it.isDirectory }?.delete()
        val created = exportDir.createFile("application/zip", safeName)
            ?: throw IllegalStateException("无法创建 boardpack 文件")
        context.contentResolver.openOutputStream(created.uri)?.use { output ->
            output.write(data)
        } ?: throw IllegalStateException("无法写入 boardpack 文件")
        "${BulletinBoardExporter.EXPORT_DIR_NAME}/$safeName"
    }

    fun writeToUri(context: Context, uri: Uri, data: ByteArray): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(data)
        } ?: throw IllegalStateException("无法写入文件")
    }

    fun readFromUri(context: Context, uri: Uri): Result<ByteArray> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取文件")
    }

    fun defaultPackFileName(boardName: String): String {
        val safeBoard = sanitizePackFileName(boardName).removeSuffix(".boardpack")
        val ts = fileTimeFormat.format(Date())
        return "${safeBoard}_$ts.boardpack"
    }

    private fun validateManifest(manifest: JSONObject?) {
        if (manifest == null) {
            throw PackException("invalid_boardpack", "包内缺少 manifest.json")
        }
        if (manifest.optString("format") != FORMAT) {
            throw PackException("unsupported_boardpack", "不支持的包格式：${manifest.optString("format")}")
        }
        if (manifest.optInt("version") != VERSION) {
            throw PackException(
                "unsupported_boardpack",
                "不支持的包版本：${manifest.optInt("version")}，当前仅支持 v$VERSION"
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
