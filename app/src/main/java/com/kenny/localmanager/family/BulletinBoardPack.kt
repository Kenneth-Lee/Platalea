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
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
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

    data class PackSummary(
        val boardName: String,
        val messageCount: Int,
        val attachmentCount: Int,
        val totalBytes: Long,
        val entryCount: Int,
        val sourceFileSize: Long? = null
    )

    data class PackPreview(
        val summary: PackSummary,
        val manifestBoardId: String? = null
    )

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

    fun exportBoardDir(
        context: Context,
        boardDir: File,
        sourceDevice: String = "android",
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): ByteArray {
        val buffer = ByteArrayOutputStream()
        exportBoardDirToOutputStream(
            context = context,
            boardDir = boardDir,
            output = buffer,
            sourceDevice = sourceDevice,
            onProgress = onProgress,
            isCancelled = isCancelled
        )
        return buffer.toByteArray()
    }

    fun exportBoardDirToOutputStream(
        context: Context,
        boardDir: File,
        output: OutputStream,
        sourceDevice: String = "android",
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ) {
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
        val manifestBytes = manifest.toString(2).toByteArray(Charsets.UTF_8)
        val totalBytes = (stats.totalBytes + manifestBytes.size.toLong()).coerceAtLeast(1L)
        ZipOutputStream(output).use { zip ->
            onProgress?.invoke(0, progressUnits(totalBytes))
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifestBytes)
            zip.closeEntry()
            var writtenBytes = manifestBytes.size.toLong()
            onProgress?.invoke(progressUnits(writtenBytes), progressUnits(totalBytes))
            var lastProgressBytes = writtenBytes
            boardDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    ensureNotCancelled(isCancelled)
                    val relative = file.relativeTo(boardDir).path.replace('\\', '/')
                    zip.putNextEntry(ZipEntry("$BOARD_PREFIX$relative"))
                    val beforeFile = writtenBytes
                    file.inputStream().use { input ->
                        TransferStreams.copyBuffered(
                            input = input,
                            output = zip,
                            onBeforeRead = { ensureNotCancelled(isCancelled) },
                            onBytesCopied = { copiedInFile ->
                                writtenBytes = beforeFile + copiedInFile
                                if (TransferStreams.shouldEmitProgress(writtenBytes, lastProgressBytes, totalBytes)) {
                                    onProgress?.invoke(progressUnits(writtenBytes), progressUnits(totalBytes))
                                    lastProgressBytes = writtenBytes
                                }
                            }
                        )
                    }
                    zip.closeEntry()
                }
        }
    }

    fun exportBoardDirToRoot(
        context: Context,
        rootUri: String,
        fileName: String,
        boardDir: File,
        sourceDevice: String = "android",
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
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
            exportBoardDirToOutputStream(
                context = context,
                boardDir = boardDir,
                output = output,
                sourceDevice = sourceDevice,
                onProgress = onProgress,
                isCancelled = isCancelled
            )
        } ?: throw IllegalStateException(context.getString(R.string.family_msg_25388))
        "${BulletinBoardExporter.exportDirName(context)}/$safeName"
    }

    fun summarizeBoardDir(context: Context, boardDir: File): PackSummary {
        val metaFile = File(boardDir, "meta.json")
        if (!metaFile.exists()) {
            throw PackException("board_not_found", context.getString(R.string.family_msg_02583, boardDir.name))
        }
        val meta = runCatching { JSONObject(metaFile.readText()) }
            .getOrElse { throw PackException("invalid_board", context.getString(R.string.family_msg_93179)) }
        val stats = collectStats(boardDir)
        val entryCount = boardDir.walkTopDown().count { it.isFile }
        return PackSummary(
            boardName = meta.optString("name", boardDir.name),
            messageCount = stats.messageCount,
            attachmentCount = stats.attachmentCount,
            totalBytes = stats.totalBytes,
            entryCount = entryCount + 1
        )
    }

    fun importIntoRoot(
        context: Context,
        rootDir: File,
        data: ByteArray,
        name: String? = null,
        roleIds: List<String>? = null,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): BulletinBoardInfo {
        rootDir.mkdirs()
        ensureNotCancelled(isCancelled)
        val preview = previewImport(context, ByteArrayInputStream(data))
        val totalEntries = preview.summary.entryCount.coerceAtLeast(1)
        onProgress?.invoke(0, totalEntries)

        val newBoardId = UUID.randomUUID().toString()
        val boardDir = File(rootDir, newBoardId)
        boardDir.mkdirs()
        var processed = 0
        var manifestBytes: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                ensureNotCancelled(isCancelled)
                if (!entry.isDirectory) {
                    processed += 1
                    if (entry.name == MANIFEST_NAME) {
                        manifestBytes = readZipEntryFully(zip)
                    } else {
                        if (!entry.name.startsWith(BOARD_PREFIX) || entry.name.endsWith("/")) {
                            throw PackException("invalid_boardpack", context.getString(R.string.family_msg_09808, entry.name))
                        }
                        val relative = entry.name.removePrefix(BOARD_PREFIX)
                        if (relative.isBlank() || relative.contains("..")) {
                            throw PackException("invalid_boardpack", context.getString(R.string.family_msg_09808, entry.name))
                        }
                        val target = File(boardDir, relative)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            copyZipEntry(zip, output, isCancelled)
                        }
                    }
                    onProgress?.invoke(processed, totalEntries)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val manifest = runCatching { JSONObject(String(manifestBytes ?: throw PackException("invalid_boardpack", context.getString(R.string.family_msg_40120)), Charsets.UTF_8)) }
            .getOrElse {
                boardDir.deleteRecursively()
                throw PackException("invalid_boardpack", context.getString(R.string.family_msg_35962))
            }
        validateManifest(context, manifest)
        if (!File(boardDir, "meta.json").isFile) {
            boardDir.deleteRecursively()
            throw PackException("invalid_boardpack", context.getString(R.string.family_msg_34360))
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

    fun importIntoRoot(
        context: Context,
        rootDir: File,
        inputStream: InputStream,
        name: String? = null,
        roleIds: List<String>? = null,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): BulletinBoardInfo {
        rootDir.mkdirs()
        val tempFile = File.createTempFile("boardpack_import_", ".zip", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { output ->
                TransferStreams.copyBuffered(
                    input = inputStream,
                    output = output,
                    onBeforeRead = { ensureNotCancelled(isCancelled) }
                )
            }
            ensureNotCancelled(isCancelled)
            val preview = tempFile.inputStream().use { storedInput ->
                previewImport(context, storedInput)
            }
            val totalEntries = preview.summary.entryCount.coerceAtLeast(1)
            onProgress?.invoke(0, totalEntries)

            val newBoardId = UUID.randomUUID().toString()
            val boardDir = File(rootDir, newBoardId)
            boardDir.mkdirs()
            var processed = 0
            var manifestBytes: ByteArray? = null
            tempFile.inputStream().use { storedInput ->
                ZipInputStream(storedInput).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        ensureNotCancelled(isCancelled)
                        if (!entry.isDirectory) {
                            processed += 1
                            if (entry.name == MANIFEST_NAME) {
                                manifestBytes = readZipEntryFully(zip)
                            } else {
                                if (!entry.name.startsWith(BOARD_PREFIX) || entry.name.endsWith("/")) {
                                    throw PackException("invalid_boardpack", context.getString(R.string.family_msg_09808, entry.name))
                                }
                                val relative = entry.name.removePrefix(BOARD_PREFIX)
                                if (relative.isBlank() || relative.contains("..")) {
                                    throw PackException("invalid_boardpack", context.getString(R.string.family_msg_09808, entry.name))
                                }
                                val target = File(boardDir, relative)
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { output ->
                                    copyZipEntry(zip, output, isCancelled)
                                }
                            }
                            onProgress?.invoke(processed, totalEntries)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            val manifest = runCatching {
                JSONObject(
                    String(
                        manifestBytes ?: throw PackException("invalid_boardpack", context.getString(R.string.family_msg_40120)),
                        Charsets.UTF_8
                    )
                )
            }.getOrElse {
                boardDir.deleteRecursively()
                throw PackException("invalid_boardpack", context.getString(R.string.family_msg_35962))
            }
            validateManifest(context, manifest)
            if (!File(boardDir, "meta.json").isFile) {
                boardDir.deleteRecursively()
                throw PackException("invalid_boardpack", context.getString(R.string.family_msg_34360))
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
        } finally {
            tempFile.delete()
        }
    }

    fun previewImport(context: Context, inputStream: InputStream): PackPreview {
        var manifest: JSONObject? = null
        var boardId: String? = null
        var entryCount = 0
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entryCount += 1
                    if (entry.name == MANIFEST_NAME) {
                        manifest = runCatching { JSONObject(String(zip.readBytes(), Charsets.UTF_8)) }
                            .getOrElse {
                                throw PackException("invalid_boardpack", context.getString(R.string.family_msg_35962))
                            }
                        boardId = manifest?.optJSONObject("source")?.optString("board_id")?.takeIf { it.isNotBlank() }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        validateManifest(context, manifest)
        val stats = manifest?.optJSONObject("stats")
        val summary = PackSummary(
            boardName = manifest?.optJSONObject("source")?.optString("board_name").orEmpty().ifBlank {
                context.getString(R.string.family_msg_09744)
            },
            messageCount = stats?.optInt("message_count") ?: 0,
            attachmentCount = stats?.optInt("attachment_count") ?: 0,
            totalBytes = stats?.optLong("total_bytes") ?: 0L,
            entryCount = entryCount,
            sourceFileSize = null
        )
        return PackPreview(summary = summary, manifestBoardId = boardId)
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

    fun saveStreamToRoot(
        context: Context,
        rootUri: String,
        fileName: String,
        inputStream: InputStream,
        totalBytes: Long? = null,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Result<String> = runCatching {
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
            ?: throw IllegalStateException(context.getString(R.string.family_msg_54649))
        val exportDir = BulletinBoardExporter.findOrCreateExportDir(context, rootDoc)
            ?: throw IllegalStateException(context.getString(R.string.family_msg_54837))
        val safeName = sanitizePackFileName(fileName)
        exportDir.findFile(safeName)?.takeIf { !it.isDirectory }?.delete()
        val created = exportDir.createFile("application/octet-stream", safeName)
            ?: throw IllegalStateException(context.getString(R.string.family_msg_78543))
        val boundedTotal = progressUnits((totalBytes ?: 0L).coerceAtLeast(1L))
        val profile = TransferStreams.Profiles.BOARDPACK_LOCAL_IO
        val startedAtMs = System.currentTimeMillis()
        onProgress?.invoke(0, boundedTotal)
        context.contentResolver.openOutputStream(created.uri)?.use { output ->
            var copied = 0L
            var lastProgressBytes = 0L
            TransferStreams.copyBuffered(
                input = inputStream,
                output = output,
                bufferSize = profile.bufferSizeBytes,
                onBeforeRead = { ensureNotCancelled(isCancelled) },
                onBytesCopied = { current ->
                    copied = current
                    if (totalBytes != null && totalBytes > 0) {
                        if (TransferStreams.shouldEmitProgress(copied, lastProgressBytes, totalBytes, profile.progressStepBytes)) {
                            onProgress?.invoke(progressUnits(copied), progressUnits(totalBytes))
                            lastProgressBytes = copied
                        }
                    }
                }
            )
        } ?: throw IllegalStateException(context.getString(R.string.family_msg_25388))
        val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
        val speed = TransferStreams.bytesPerSecond(totalBytes ?: 0L, elapsedMs)
        android.util.Log.i(
            "BulletinBoardPack",
            "transfer profile=${profile.name} stage=save_stream_to_root bytes=${totalBytes ?: 0L} elapsed_ms=$elapsedMs speed_Bps=$speed"
        )
        if (totalBytes != null && totalBytes > 0) {
            onProgress?.invoke(progressUnits(totalBytes), progressUnits(totalBytes))
        }
        "${BulletinBoardExporter.exportDirName(context)}/$safeName"
    }

    private fun readZipEntryFully(zip: ZipInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        copyZipEntry(zip, output, isCancelled = null)
        return output.toByteArray()
    }

    private fun copyZipEntry(
        zip: ZipInputStream,
        output: OutputStream,
        isCancelled: (() -> Boolean)?
    ) {
        TransferStreams.copyBuffered(
            input = zip,
            output = output,
            onBeforeRead = { ensureNotCancelled(isCancelled) }
        )
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

    private fun progressUnits(value: Long): Int = value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun ensureNotCancelled(isCancelled: (() -> Boolean)?) {
        if (isCancelled?.invoke() == true) {
            throw CancellationException("boardpack_transfer_cancelled")
        }
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
