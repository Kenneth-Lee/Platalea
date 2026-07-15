package com.kenny.localmanager.family

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.min

class BulletinAttachmentStore(private val boardsRoot: File) {
    private val lock = ReentrantReadWriteLock()

    fun initFileUpload(
        boardId: String,
        name: String,
        size: Long,
        sha256: String? = null,
        mime: String? = null,
        uploaderDevice: String? = null
    ): InitUploadResult? = lock.write {
        if (readMeta(boardId) == null || size < 0 || name.isBlank()) return@write null
        val attachmentId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val dir = attachmentDir(boardId, attachmentId)
        dir.mkdirs()
        val meta = JSONObject().apply {
            put("id", attachmentId)
            put("board_id", boardId)
            put("kind", BulletinAttachmentKind.FILE.wire)
            put("name", name.trim())
            put("status", BulletinAttachmentStatus.UPLOADING.wire)
            put("size", size)
            sha256?.takeIf { it.isNotBlank() }?.let { put("sha256", it) }
            mime?.takeIf { it.isNotBlank() }?.let { put("mime", it) }
            put("chunk_size", BulletinAttachmentDefaults.CHUNK_SIZE)
            put("uploaded_chunks", JSONArray())
            put("created_at", now)
            put("uploader_device", uploaderDevice.orEmpty())
        }
        writeAttachmentMeta(boardId, attachmentId, meta)
        File(dir, "blob").createNewFile()
        InitUploadResult(attachmentId, BulletinAttachmentDefaults.CHUNK_SIZE, emptyList())
    }

    fun initDirectoryUpload(
        boardId: String,
        name: String,
        entries: List<BulletinDirectoryEntry>,
        uploaderDevice: String? = null
    ): InitUploadResult? = lock.write {
        if (readMeta(boardId) == null || name.isBlank() || entries.isEmpty()) return@write null
        val attachmentId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val dir = attachmentDir(boardId, attachmentId)
        dir.mkdirs()
        val filesDir = File(dir, "files")
        filesDir.mkdirs()
        val slots = entries.map { entry ->
            val fileId = UUID.randomUUID().toString()
            val fileDir = File(filesDir, fileId)
            fileDir.mkdirs()
            File(fileDir, "blob").createNewFile()
            writeFileMemberMeta(
                fileDir,
                JSONObject().apply {
                    put("file_id", fileId)
                    put("path", entry.path)
                    put("size", entry.size)
                    entry.sha256?.let { put("sha256", it) }
                    put("uploaded_chunks", JSONArray())
                }
            )
            BulletinDirectoryFileSlot(fileId, entry.path, entry.size, entry.sha256)
        }
        val totalSize = entries.sumOf { it.size }
        val manifest = JSONObject().apply {
            put("version", 1)
            put(
                "entries",
                JSONArray().apply {
                    slots.forEach { slot ->
                        put(
                            JSONObject().apply {
                                put("file_id", slot.fileId)
                                put("path", slot.path)
                                put("size", slot.size)
                                slot.sha256?.let { put("sha256", it) }
                            }
                        )
                    }
                }
            )
        }
        File(filesDir, "manifest.json").writeText(manifest.toString())
        val meta = JSONObject().apply {
            put("id", attachmentId)
            put("board_id", boardId)
            put("kind", BulletinAttachmentKind.DIRECTORY.wire)
            put("name", name.trim())
            put("status", BulletinAttachmentStatus.UPLOADING.wire)
            put("file_count", slots.size)
            put("total_size", totalSize)
            put("chunk_size", BulletinAttachmentDefaults.CHUNK_SIZE)
            put("created_at", now)
            put("uploader_device", uploaderDevice.orEmpty())
        }
        writeAttachmentMeta(boardId, attachmentId, meta)
        InitUploadResult(attachmentId, BulletinAttachmentDefaults.CHUNK_SIZE, slots)
    }

    fun writeFileChunk(
        boardId: String,
        attachmentId: String,
        chunkIndex: Int,
        data: ByteArray
    ): ChunkWriteResult? = lock.write {
        if (chunkIndex < 0 || data.isEmpty()) return@write null
        val meta = readAttachmentMeta(boardId, attachmentId) ?: return@write null
        if (meta.optString("status") != BulletinAttachmentStatus.UPLOADING.wire) return@write null
        if (meta.optString("kind") != BulletinAttachmentKind.FILE.wire) return@write null
        val size = meta.optLong("size")
        val chunkSize = meta.optInt("chunk_size", BulletinAttachmentDefaults.CHUNK_SIZE)
        val offset = chunkIndex.toLong() * chunkSize
        if (offset >= size) return@write null
        val blob = File(attachmentDir(boardId, attachmentId), "blob")
        RandomAccessFile(blob, "rw").use { raf ->
            raf.seek(offset)
            raf.write(data)
        }
        val chunks = readChunkSet(meta)
        chunks.add(chunkIndex)
        meta.put("uploaded_chunks", JSONArray(chunks.sorted()))
        writeAttachmentMeta(boardId, attachmentId, meta)
        ChunkWriteResult(chunkIndex, data.size)
    }

    fun writeDirectoryFileChunk(
        boardId: String,
        attachmentId: String,
        fileId: String,
        chunkIndex: Int,
        data: ByteArray
    ): ChunkWriteResult? = lock.write {
        if (chunkIndex < 0 || data.isEmpty()) return@write null
        val attachmentMeta = readAttachmentMeta(boardId, attachmentId) ?: return@write null
        if (attachmentMeta.optString("status") != BulletinAttachmentStatus.UPLOADING.wire) return@write null
        if (attachmentMeta.optString("kind") != BulletinAttachmentKind.DIRECTORY.wire) return@write null
        val fileDir = File(File(attachmentDir(boardId, attachmentId), "files"), fileId)
        if (!fileDir.isDirectory) return@write null
        val fileMeta = readFileMemberMeta(fileDir) ?: return@write null
        val size = fileMeta.optLong("size")
        val chunkSize = attachmentMeta.optInt("chunk_size", BulletinAttachmentDefaults.CHUNK_SIZE)
        val offset = chunkIndex.toLong() * chunkSize
        if (offset >= size) return@write null
        val blob = File(fileDir, "blob")
        RandomAccessFile(blob, "rw").use { raf ->
            raf.seek(offset)
            raf.write(data)
        }
        val chunks = readChunkSet(fileMeta)
        chunks.add(chunkIndex)
        fileMeta.put("uploaded_chunks", JSONArray(chunks.sorted()))
        writeFileMemberMeta(fileDir, fileMeta)
        ChunkWriteResult(chunkIndex, data.size)
    }

    fun completeUpload(boardId: String, attachmentId: String): BulletinAttachmentRef? = lock.write {
        val meta = readAttachmentMeta(boardId, attachmentId) ?: return@write null
        if (meta.optString("status") != BulletinAttachmentStatus.UPLOADING.wire) return@write null
        val kind = BulletinAttachmentKind.fromWire(meta.optString("kind", "file"))
            ?: return@write null
        val chunkSize = meta.optInt("chunk_size", BulletinAttachmentDefaults.CHUNK_SIZE)
        when (kind) {
            BulletinAttachmentKind.FILE -> {
                val size = meta.optLong("size")
                if (!chunksComplete(readChunkSet(meta), size, chunkSize)) return@write null
                val blob = File(attachmentDir(boardId, attachmentId), "blob")
                meta.optString("sha256").takeIf { it.isNotBlank() }?.let { expected ->
                    val actual = sha256Hex(blob)
                    if (!actual.equals(expected, ignoreCase = true)) return@write null
                }
            }
            BulletinAttachmentKind.DIRECTORY -> {
                val filesDir = File(attachmentDir(boardId, attachmentId), "files")
                val manifest = readManifest(filesDir) ?: return@write null
                for (i in 0 until manifest.length()) {
                    val entry = manifest.getJSONObject(i)
                    val fileId = entry.getString("file_id")
                    val fileDir = File(filesDir, fileId)
                    val fileMeta = readFileMemberMeta(fileDir) ?: return@write null
                    val fileSize = fileMeta.optLong("size")
                    if (!chunksComplete(readChunkSet(fileMeta), fileSize, chunkSize)) return@write null
                    fileMeta.optString("sha256").takeIf { it.isNotBlank() }?.let { expected ->
                        val actual = sha256Hex(File(fileDir, "blob"))
                        if (!actual.equals(expected, ignoreCase = true)) return@write null
                    }
                }
            }
        }
        meta.put("status", BulletinAttachmentStatus.READY.wire)
        meta.put("completed_at", System.currentTimeMillis())
        writeAttachmentMeta(boardId, attachmentId, meta)
        attachmentRefFromMeta(meta)
    }

    fun getAttachmentMeta(boardId: String, attachmentId: String): JSONObject? = lock.read {
        val meta = readAttachmentMeta(boardId, attachmentId) ?: return@read null
        enrichMetaForGet(boardId, attachmentId, meta)
    }

    fun readFileBlob(boardId: String, attachmentId: String, rangeHeader: String?): BlobReadResult? =
        lock.read {
            val meta = readAttachmentMeta(boardId, attachmentId) ?: return@read null
            if (meta.optString("status") != BulletinAttachmentStatus.READY.wire) return@read null
            if (meta.optString("kind") != BulletinAttachmentKind.FILE.wire) return@read null
            val blob = File(attachmentDir(boardId, attachmentId), "blob")
            readBlobWithRange(blob, meta.optLong("size"), rangeHeader)
        }

    fun openFileBlob(boardId: String, attachmentId: String): BlobStreamResult? = lock.read {
        val meta = readAttachmentMeta(boardId, attachmentId) ?: return@read null
        if (meta.optString("status") != BulletinAttachmentStatus.READY.wire) return@read null
        if (meta.optString("kind") != BulletinAttachmentKind.FILE.wire) return@read null
        val blob = File(attachmentDir(boardId, attachmentId), "blob")
        BlobStreamResult(FileInputStream(blob), meta.optLong("size"), null, 200)
    }

    fun readDirectoryFileBlob(
        boardId: String,
        attachmentId: String,
        fileId: String,
        rangeHeader: String?
    ): BlobReadResult? = lock.read {
        val meta = readAttachmentMeta(boardId, attachmentId) ?: return@read null
        if (meta.optString("status") != BulletinAttachmentStatus.READY.wire) return@read null
        if (meta.optString("kind") != BulletinAttachmentKind.DIRECTORY.wire) return@read null
        val fileDir = File(File(attachmentDir(boardId, attachmentId), "files"), fileId)
        val fileMeta = readFileMemberMeta(fileDir) ?: return@read null
        val blob = File(fileDir, "blob")
        readBlobWithRange(blob, fileMeta.optLong("size"), rangeHeader)
    }

    fun openDirectoryFileBlob(boardId: String, attachmentId: String, fileId: String): BlobStreamResult? = lock.read {
        val meta = readAttachmentMeta(boardId, attachmentId) ?: return@read null
        if (meta.optString("status") != BulletinAttachmentStatus.READY.wire) return@read null
        if (meta.optString("kind") != BulletinAttachmentKind.DIRECTORY.wire) return@read null
        val fileDir = File(File(attachmentDir(boardId, attachmentId), "files"), fileId)
        val fileMeta = readFileMemberMeta(fileDir) ?: return@read null
        val blob = File(fileDir, "blob")
        BlobStreamResult(FileInputStream(blob), fileMeta.optLong("size"), null, 200)
    }

    fun deleteAttachment(boardId: String, attachmentId: String): Boolean = lock.write {
        val dir = attachmentDir(boardId, attachmentId)
        if (!dir.exists()) return@write false
        dir.deleteRecursively()
        true
    }

    fun isAttachmentReady(boardId: String, attachmentId: String): Boolean = lock.read {
        readAttachmentMeta(boardId, attachmentId)?.optString("status") ==
            BulletinAttachmentStatus.READY.wire
    }

    /** 将已就绪附件复制到另一块留言板（本机 store 内操作）。 */
    fun duplicateReadyAttachment(
        sourceBoardId: String,
        attachmentId: String,
        targetBoardId: String
    ): BulletinAttachmentRef? = lock.write {
        if (readMeta(targetBoardId) == null) return@write null
        val sourceMeta = readAttachmentMeta(sourceBoardId, attachmentId) ?: return@write null
        if (sourceMeta.optString("status") != BulletinAttachmentStatus.READY.wire) return@write null
        val sourceDir = attachmentDir(sourceBoardId, attachmentId)
        if (!sourceDir.exists()) return@write null

        val newAttachmentId = UUID.randomUUID().toString()
        val targetDir = attachmentDir(targetBoardId, newAttachmentId)
        targetDir.mkdirs()
        val now = System.currentTimeMillis()

        when (sourceMeta.optString("kind")) {
            BulletinAttachmentKind.FILE.wire -> {
                File(sourceDir, "blob").let { blob ->
                    File(targetDir, "blob").writeBytes(blob.readBytes())
                }
                val meta = JSONObject(sourceMeta.toString()).apply {
                    put("id", newAttachmentId)
                    put("board_id", targetBoardId)
                    put("created_at", now)
                    put("completed_at", now)
                }
                writeAttachmentMeta(targetBoardId, newAttachmentId, meta)
                attachmentRefFromMeta(meta)
            }
            BulletinAttachmentKind.DIRECTORY.wire -> {
                val sourceFilesDir = File(sourceDir, "files")
                val targetFilesDir = File(targetDir, "files")
                targetFilesDir.mkdirs()
                val manifest = readManifest(sourceFilesDir) ?: return@write null
                val newManifestEntries = JSONArray()
                var totalSize = 0L
                for (i in 0 until manifest.length()) {
                    val entry = manifest.getJSONObject(i)
                    val oldFileId = entry.getString("file_id")
                    val newFileId = UUID.randomUUID().toString()
                    val sourceFileDir = File(sourceFilesDir, oldFileId)
                    val targetFileDir = File(targetFilesDir, newFileId)
                    targetFileDir.mkdirs()
                    File(sourceFileDir, "blob").let { blob ->
                        File(targetFileDir, "blob").writeBytes(blob.readBytes())
                    }
                    readFileMemberMeta(sourceFileDir)?.let { fileMeta ->
                        writeFileMemberMeta(
                            targetFileDir,
                            JSONObject(fileMeta.toString()).apply { put("file_id", newFileId) }
                        )
                    }
                    val size = entry.optLong("size")
                    totalSize += size
                    newManifestEntries.put(
                        JSONObject().apply {
                            put("file_id", newFileId)
                            put("path", entry.optString("path"))
                            put("size", size)
                            entry.optString("sha256").takeIf { it.isNotBlank() }?.let { put("sha256", it) }
                        }
                    )
                }
                File(targetFilesDir, "manifest.json").writeText(
                    JSONObject().apply {
                        put("version", 1)
                        put("entries", newManifestEntries)
                    }.toString()
                )
                val meta = JSONObject(sourceMeta.toString()).apply {
                    put("id", newAttachmentId)
                    put("board_id", targetBoardId)
                    put("created_at", now)
                    put("total_size", totalSize)
                    put("file_count", newManifestEntries.length())
                    put("completed_at", now)
                }
                writeAttachmentMeta(targetBoardId, newAttachmentId, meta)
                attachmentRefFromMeta(meta)
            }
            else -> null
        }
    }

    fun attachmentRefFromMeta(meta: JSONObject): BulletinAttachmentRef {
        val kind = BulletinAttachmentKind.fromWire(meta.optString("kind", "file"))
            ?: BulletinAttachmentKind.FILE
        return BulletinAttachmentRef(
            id = meta.getString("id"),
            kind = kind,
            name = meta.optString("name", ""),
            size = meta.optLong("size"),
            totalSize = meta.optLong("total_size", meta.optLong("size")),
            fileCount = meta.optInt("file_count"),
            sha256 = meta.optString("sha256").takeIf { it.isNotBlank() },
            mime = meta.optString("mime").takeIf { it.isNotBlank() }
        )
    }

    private fun enrichMetaForGet(boardId: String, attachmentId: String, meta: JSONObject): JSONObject {
        val copy = JSONObject(meta.toString())
        if (copy.optString("kind") == BulletinAttachmentKind.DIRECTORY.wire) {
            val filesDir = File(attachmentDir(boardId, attachmentId), "files")
            val manifest = readManifest(filesDir) ?: JSONArray()
            val files = JSONArray()
            for (i in 0 until manifest.length()) {
                val entry = manifest.getJSONObject(i)
                val fileId = entry.getString("file_id")
                val fileDir = File(filesDir, fileId)
                val fileMeta = readFileMemberMeta(fileDir)
                files.put(
                    JSONObject().apply {
                        put("file_id", fileId)
                        put("path", entry.optString("path"))
                        put("size", entry.optLong("size"))
                        put("uploaded_chunks", fileMeta?.optJSONArray("uploaded_chunks") ?: JSONArray())
                    }
                )
            }
            copy.put("files", files)
        }
        return copy
    }

    private fun readBlobWithRange(blob: File, totalSize: Long, rangeHeader: String?): BlobReadResult {
        val effectiveSize = if (totalSize > 0) totalSize else blob.length()
        if (rangeHeader.isNullOrBlank()) {
            val bytes = blob.readBytes()
            return BlobReadResult(200, bytes, effectiveSize, null)
        }
        val match = Regex("""bytes=(\d+)-(\d*)""").find(rangeHeader.trim())
            ?: return BlobReadResult(200, blob.readBytes(), effectiveSize, null)
        val start = match.groupValues[1].toLongOrNull() ?: 0L
        val endInclusive = match.groupValues[2].toLongOrNull()?.coerceAtMost(effectiveSize - 1)
            ?: (effectiveSize - 1)
        if (start > endInclusive || start >= effectiveSize) {
            return BlobReadResult(416, ByteArray(0), effectiveSize, null)
        }
        val length = (endInclusive - start + 1).toInt()
        val buffer = ByteArray(length)
        RandomAccessFile(blob, "r").use { raf ->
            raf.seek(start)
            raf.readFully(buffer)
        }
        return BlobReadResult(
            statusCode = 206,
            bytes = buffer,
            totalSize = effectiveSize,
            contentRange = "bytes $start-$endInclusive/$effectiveSize"
        )
    }

    private fun chunksComplete(chunks: Set<Int>, size: Long, chunkSize: Int): Boolean {
        if (size == 0L) return true
        val lastIndex = ((size - 1) / chunkSize).toInt()
        for (i in 0..lastIndex) {
            if (i !in chunks) return false
        }
        return true
    }

    private fun readChunkSet(meta: JSONObject): MutableSet<Int> {
        val arr = meta.optJSONArray("uploaded_chunks") ?: JSONArray()
        return buildSet {
            for (i in 0 until arr.length()) {
                add(arr.getInt(i))
            }
        }.toMutableSet()
    }

    private fun attachmentDir(boardId: String, attachmentId: String): File =
        File(File(boardsRoot, boardId), "attachments/$attachmentId")

    private fun readMeta(boardId: String): JSONObject? {
        val file = File(boardsRoot, boardId).let { File(it, "meta.json") }
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    private fun readAttachmentMeta(boardId: String, attachmentId: String): JSONObject? {
        val file = File(attachmentDir(boardId, attachmentId), "attachment.json")
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    private fun writeAttachmentMeta(boardId: String, attachmentId: String, meta: JSONObject) {
        File(attachmentDir(boardId, attachmentId), "attachment.json").writeText(meta.toString())
    }

    private fun readFileMemberMeta(fileDir: File): JSONObject? {
        val file = File(fileDir, "meta.json")
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    private fun writeFileMemberMeta(fileDir: File, meta: JSONObject) {
        File(fileDir, "meta.json").writeText(meta.toString())
    }

    private fun readManifest(filesDir: File): JSONArray? {
        val file = File(filesDir, "manifest.json")
        if (!file.exists()) return null
        return runCatching {
            JSONObject(file.readText()).optJSONArray("entries") ?: JSONArray()
        }.getOrNull()
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class InitUploadResult(
    val attachmentId: String,
    val chunkSize: Int,
    val directoryFiles: List<BulletinDirectoryFileSlot>
)

data class ChunkWriteResult(val chunkIndex: Int, val received: Int)

data class BlobReadResult(
    val statusCode: Int,
    val bytes: ByteArray,
    val totalSize: Long,
    val contentRange: String?
)

data class BlobStreamResult(
    val inputStream: java.io.InputStream,
    val totalSize: Long,
    val contentRange: String?,
    val statusCode: Int
)
