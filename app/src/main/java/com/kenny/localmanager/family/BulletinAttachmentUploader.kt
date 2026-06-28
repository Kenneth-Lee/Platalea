package com.kenny.localmanager.family

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.file.DocumentFileModel
import com.kenny.localmanager.file.listChildrenFast
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

object BulletinAttachmentUploader {
    suspend fun uploadItems(
        context: Context,
        transport: BulletinAttachmentTransport,
        boardId: String,
        items: List<DocumentFileModel>,
        uploaderDevice: String?,
        onAttachmentInit: ((attachmentId: String) -> Unit)? = null,
        onProgress: ((uploadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<List<BulletinAttachmentRef>> = withContext(Dispatchers.IO) {
        try {
            Result.success(runUploadItems(context, transport, boardId, items, uploaderDevice, onAttachmentInit, onProgress))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun runUploadItems(
        context: Context,
        transport: BulletinAttachmentTransport,
        boardId: String,
        items: List<DocumentFileModel>,
        uploaderDevice: String?,
        onAttachmentInit: ((attachmentId: String) -> Unit)?,
        onProgress: ((uploadedBytes: Long, totalBytes: Long) -> Unit)?
    ): List<BulletinAttachmentRef> {
            val refs = mutableListOf<BulletinAttachmentRef>()
            var aggregateUploaded = 0L
            val aggregateTotal = items.sumOf { item ->
                if (item.isDirectory) {
                    collectDirectoryEntries(context, item.uri.toString()).sumOf { it.size }
                } else {
                    item.size
                }
            }.coerceAtLeast(1L)
            for (item in items) {
                coroutineContext.ensureActive()
                val itemBytes = if (item.isDirectory) {
                    collectDirectoryEntries(context, item.uri.toString()).sumOf { it.size }
                } else {
                    item.size
                }
                val ref = if (item.isDirectory) {
                    uploadDirectory(
                        context, transport, boardId, item, uploaderDevice,
                        onAttachmentInit, onProgress,
                        aggregateUploaded, aggregateTotal
                    )
                } else {
                    uploadFile(
                        context, transport, boardId, item, uploaderDevice,
                        onAttachmentInit, onProgress,
                        aggregateUploaded, aggregateTotal
                    )
                }
                aggregateUploaded = minOf(aggregateTotal, aggregateUploaded + itemBytes)
                refs += ref
            }
            return refs
    }

    private suspend fun uploadFile(
        context: Context,
        transport: BulletinAttachmentTransport,
        boardId: String,
        item: DocumentFileModel,
        uploaderDevice: String?,
        onAttachmentInit: ((String) -> Unit)?,
        onProgress: ((Long, Long) -> Unit)?,
        bytesBeforeItem: Long,
        aggregateTotal: Long
    ): BulletinAttachmentRef {
        val mime = context.contentResolver.getType(item.uri)
        val init = transport.initFileUpload(
            boardId = boardId,
            name = item.name,
            size = item.size,
            mime = mime,
            uploaderDevice = uploaderDevice
        ).getOrElse { throw it }
        onAttachmentInit?.invoke(init.attachmentId)
        uploadStreamInChunks(
            transport = transport,
            boardId = boardId,
            attachmentId = init.attachmentId,
            fileId = null,
            chunkSize = init.chunkSize,
            totalSize = item.size,
            openStream = { context.contentResolver.openInputStream(item.uri) },
            onProgress = { uploadedInItem ->
                onProgress?.invoke(bytesBeforeItem + uploadedInItem, aggregateTotal)
            }
        )
        return transport.completeUpload(boardId, init.attachmentId).getOrElse { throw it }
    }

    private suspend fun uploadDirectory(
        context: Context,
        transport: BulletinAttachmentTransport,
        boardId: String,
        item: DocumentFileModel,
        uploaderDevice: String?,
        onAttachmentInit: ((String) -> Unit)?,
        onProgress: ((Long, Long) -> Unit)?,
        bytesBeforeItem: Long,
        aggregateTotal: Long
    ): BulletinAttachmentRef {
        val entries = collectDirectoryEntries(context, item.uri.toString())
        if (entries.isEmpty()) {
            throw IllegalStateException("目录 ${item.name} 为空，无法上传")
        }
        val init = transport.initDirectoryUpload(
            boardId = boardId,
            name = item.name,
            entries = entries,
            uploaderDevice = uploaderDevice
        ).getOrElse { throw it }
        onAttachmentInit?.invoke(init.attachmentId)
        val pathToUri = mapDirectoryEntryUris(context, item.uri.toString(), entries)
        var uploadedInDirectory = 0L
        init.directoryFiles.forEach { slot ->
            val uri = pathToUri[slot.path]
                ?: throw IllegalStateException("目录内找不到文件：${slot.path}")
            val fileOffset = uploadedInDirectory
            uploadStreamInChunks(
                transport = transport,
                boardId = boardId,
                attachmentId = init.attachmentId,
                fileId = slot.fileId,
                chunkSize = init.chunkSize,
                totalSize = slot.size,
                openStream = { context.contentResolver.openInputStream(uri) },
                onProgress = { uploadedInFile ->
                    onProgress?.invoke(bytesBeforeItem + fileOffset + uploadedInFile, aggregateTotal)
                }
            )
            uploadedInDirectory += slot.size
        }
        return transport.completeUpload(boardId, init.attachmentId).getOrElse { throw it }
    }

    private suspend fun uploadStreamInChunks(
        transport: BulletinAttachmentTransport,
        boardId: String,
        attachmentId: String,
        fileId: String?,
        chunkSize: Int,
        totalSize: Long,
        openStream: () -> InputStream?,
        onProgress: ((uploadedInStream: Long) -> Unit)? = null
    ) {
        val input = openStream() ?: throw IllegalStateException("无法读取文件流")
        input.use { stream ->
            val buffer = ByteArray(chunkSize)
            var chunkIndex = 0
            var uploaded = 0L
            while (uploaded < totalSize) {
                coroutineContext.ensureActive()
                val toRead = minOf(chunkSize.toLong(), totalSize - uploaded).toInt()
                val read = stream.read(buffer, 0, toRead)
                if (read <= 0) break
                val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                if (fileId == null) {
                    transport.uploadFileChunk(boardId, attachmentId, chunkIndex, chunk)
                        .getOrElse { throw it }
                } else {
                    transport.uploadDirectoryFileChunk(boardId, attachmentId, fileId, chunkIndex, chunk)
                        .getOrElse { throw it }
                }
                chunkIndex++
                uploaded += read
                onProgress?.invoke(uploaded)
            }
            if (uploaded < totalSize) {
                throw IllegalStateException("上传未完成：已传 $uploaded / $totalSize 字节")
            }
        }
    }

    fun collectDirectoryEntries(context: Context, rootUri: String): List<BulletinDirectoryEntry> {
        val pending = ArrayDeque<Pair<String, String>>()
        pending.addLast(rootUri to "")
        val entries = mutableListOf<BulletinDirectoryEntry>()
        while (pending.isNotEmpty()) {
            val (dirUri, relativeDir) = pending.removeFirst()
            listChildrenFast(context, dirUri).forEach { child ->
                val childPath = if (relativeDir.isBlank()) child.name else "$relativeDir/${child.name}"
                if (child.isDirectory) {
                    pending.addLast(child.uri.toString() to childPath)
                } else {
                    entries += BulletinDirectoryEntry(path = childPath, size = child.size)
                }
            }
        }
        return entries
    }

    private fun mapDirectoryEntryUris(
        context: Context,
        rootUri: String,
        entries: List<BulletinDirectoryEntry>
    ): Map<String, Uri> {
        val pending = ArrayDeque<Pair<String, String>>()
        pending.addLast(rootUri to "")
        val result = mutableMapOf<String, Uri>()
        val wanted = entries.map { it.path }.toSet()
        while (pending.isNotEmpty()) {
            val (dirUri, relativeDir) = pending.removeFirst()
            listChildrenFast(context, dirUri).forEach { child ->
                val childPath = if (relativeDir.isBlank()) child.name else "$relativeDir/${child.name}"
                if (child.isDirectory) {
                    pending.addLast(child.uri.toString() to childPath)
                } else if (childPath in wanted) {
                    result[childPath] = child.uri
                }
            }
        }
        return result.filterKeys { it in wanted }
    }

    /** 将多个文件/目录合并上传为**单个**目录附件。 */
    suspend fun uploadItemsAsSingleDirectory(
        context: Context,
        transport: BulletinAttachmentTransport,
        boardId: String,
        items: List<DocumentFileModel>,
        attachmentName: String,
        uploaderDevice: String?,
        onAttachmentInit: ((attachmentId: String) -> Unit)? = null,
        onProgress: ((uploadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<List<BulletinAttachmentRef>> = withContext(Dispatchers.IO) {
        try {
            if (items.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("未选择任何文件或目录"))
            }
            val entries = collectMultiItemDirectoryEntries(context, items)
            if (entries.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("所选文件/目录均为空，无法组成目录附件"))
            }
            val pathToUri = mapMultiItemEntryUris(context, items, entries)
            val aggregateTotal = entries.sumOf { it.size }.coerceAtLeast(1L)
            val init = transport.initDirectoryUpload(
                boardId = boardId,
                name = attachmentName,
                entries = entries,
                uploaderDevice = uploaderDevice
            ).getOrElse { throw it }
            onAttachmentInit?.invoke(init.attachmentId)
            var uploadedInDirectory = 0L
            init.directoryFiles.forEach { slot ->
                val uri = pathToUri[slot.path]
                    ?: throw IllegalStateException("目录内找不到文件：${slot.path}")
                val fileOffset = uploadedInDirectory
                uploadStreamInChunks(
                    transport = transport,
                    boardId = boardId,
                    attachmentId = init.attachmentId,
                    fileId = slot.fileId,
                    chunkSize = init.chunkSize,
                    totalSize = slot.size,
                    openStream = { context.contentResolver.openInputStream(uri) },
                    onProgress = { uploadedInFile ->
                        onProgress?.invoke(fileOffset + uploadedInFile, aggregateTotal)
                    }
                )
                uploadedInDirectory += slot.size
            }
            val ref = transport.completeUpload(boardId, init.attachmentId).getOrElse { throw it }
            Result.success(listOf(ref))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun collectMultiItemDirectoryEntries(
        context: Context,
        items: List<DocumentFileModel>
    ): List<BulletinDirectoryEntry> {
        val entries = mutableListOf<BulletinDirectoryEntry>()
        items.forEach { item ->
            if (item.isDirectory) {
                collectDirectoryEntries(context, item.uri.toString()).forEach { entry ->
                    val path = if (entry.path.isBlank()) item.name else "${item.name}/${entry.path}"
                    entries += entry.copy(path = path)
                }
            } else {
                entries += BulletinDirectoryEntry(path = item.name, size = item.size)
            }
        }
        return entries
    }

    fun mapMultiItemEntryUris(
        context: Context,
        items: List<DocumentFileModel>,
        entries: List<BulletinDirectoryEntry>
    ): Map<String, Uri> {
        val wanted = entries.map { it.path }.toSet()
        val result = mutableMapOf<String, Uri>()
        items.forEach { item ->
            if (item.isDirectory) {
                val relativeEntries = entries
                    .filter { it.path == item.name || it.path.startsWith("${item.name}/") }
                    .map { entry ->
                        val rel = entry.path.removePrefix("${item.name}/").trimStart('/')
                        entry.copy(path = rel)
                    }
                    .filter { it.path.isNotBlank() }
                mapDirectoryEntryUris(context, item.uri.toString(), relativeEntries).forEach { (rel, uri) ->
                    val fullPath = if (rel.isBlank()) item.name else "${item.name}/$rel"
                    result[fullPath] = uri
                }
            } else if (item.name in wanted) {
                result[item.name] = item.uri
            }
        }
        return result
    }
}

data class InitUploadSession(
    val attachmentId: String,
    val chunkSize: Int,
    val directoryFiles: List<BulletinDirectoryFileSlot> = emptyList()
)

interface BulletinAttachmentTransport {
    fun initFileUpload(
        boardId: String,
        name: String,
        size: Long,
        mime: String? = null,
        uploaderDevice: String? = null
    ): Result<InitUploadSession>

    fun initDirectoryUpload(
        boardId: String,
        name: String,
        entries: List<BulletinDirectoryEntry>,
        uploaderDevice: String? = null
    ): Result<InitUploadSession>

    fun uploadFileChunk(
        boardId: String,
        attachmentId: String,
        chunkIndex: Int,
        data: ByteArray
    ): Result<Unit>

    fun uploadDirectoryFileChunk(
        boardId: String,
        attachmentId: String,
        fileId: String,
        chunkIndex: Int,
        data: ByteArray
    ): Result<Unit>

    fun completeUpload(boardId: String, attachmentId: String): Result<BulletinAttachmentRef>
}

class LocalBulletinAttachmentTransport(
    private val store: BulletinBoardStore
) : BulletinAttachmentTransport {
    override fun initFileUpload(
        boardId: String,
        name: String,
        size: Long,
        mime: String?,
        uploaderDevice: String?
    ): Result<InitUploadSession> = runCatching {
        val result = store.attachments.initFileUpload(boardId, name, size, mime = mime, uploaderDevice = uploaderDevice)
            ?: throw IllegalStateException("初始化单文件附件失败")
        InitUploadSession(result.attachmentId, result.chunkSize)
    }

    override fun initDirectoryUpload(
        boardId: String,
        name: String,
        entries: List<BulletinDirectoryEntry>,
        uploaderDevice: String?
    ): Result<InitUploadSession> = runCatching {
        val result = store.attachments.initDirectoryUpload(boardId, name, entries, uploaderDevice)
            ?: throw IllegalStateException("初始化目录附件失败")
        InitUploadSession(result.attachmentId, result.chunkSize, result.directoryFiles)
    }

    override fun uploadFileChunk(
        boardId: String,
        attachmentId: String,
        chunkIndex: Int,
        data: ByteArray
    ): Result<Unit> = runCatching {
        store.attachments.writeFileChunk(boardId, attachmentId, chunkIndex, data)
            ?: throw IllegalStateException("写入附件分块失败：index=$chunkIndex")
    }

    override fun uploadDirectoryFileChunk(
        boardId: String,
        attachmentId: String,
        fileId: String,
        chunkIndex: Int,
        data: ByteArray
    ): Result<Unit> = runCatching {
        store.attachments.writeDirectoryFileChunk(boardId, attachmentId, fileId, chunkIndex, data)
            ?: throw IllegalStateException("写入目录附件分块失败：$fileId#$chunkIndex")
    }

    override fun completeUpload(boardId: String, attachmentId: String): Result<BulletinAttachmentRef> =
        runCatching {
            store.attachments.completeUpload(boardId, attachmentId)
                ?: throw IllegalStateException("完成附件上传失败：$attachmentId")
        }
}

class RemoteBulletinAttachmentTransport(
    private val service: FamilyDiscoveredService,
    private val accessPassword: String?,
    private val requestJson: (method: String, path: String, body: String?) -> Result<String>,
    private val requestBinary: (method: String, path: String, body: ByteArray) -> Result<Unit>
) : BulletinAttachmentTransport {
    override fun initFileUpload(
        boardId: String,
        name: String,
        size: Long,
        mime: String?,
        uploaderDevice: String?
    ): Result<InitUploadSession> = runCatching {
        val body = JSONObject().apply {
            put("kind", BulletinAttachmentKind.FILE.wire)
            put("name", name)
            put("size", size)
            mime?.let { put("mime", it) }
            uploaderDevice?.let { put("uploader_device", it) }
        }.toString()
        val text = requestJson("POST", "/boards/$boardId/attachments/init", body).getOrElse { throw it }
        parseInitResponse(text)
    }

    override fun initDirectoryUpload(
        boardId: String,
        name: String,
        entries: List<BulletinDirectoryEntry>,
        uploaderDevice: String?
    ): Result<InitUploadSession> = runCatching {
        val body = JSONObject().apply {
            put("kind", BulletinAttachmentKind.DIRECTORY.wire)
            put("name", name)
            put("entries", JSONArray().apply {
                entries.forEach { entry ->
                    put(
                        JSONObject().apply {
                            put("path", entry.path)
                            put("size", entry.size)
                            entry.sha256?.let { put("sha256", it) }
                        }
                    )
                }
            })
            uploaderDevice?.let { put("uploader_device", it) }
        }.toString()
        val text = requestJson("POST", "/boards/$boardId/attachments/init", body).getOrElse { throw it }
        parseInitResponse(text)
    }

    override fun uploadFileChunk(
        boardId: String,
        attachmentId: String,
        chunkIndex: Int,
        data: ByteArray
    ): Result<Unit> = requestBinary(
        "PUT",
        "/boards/$boardId/attachments/$attachmentId/chunks/$chunkIndex",
        data
    )

    override fun uploadDirectoryFileChunk(
        boardId: String,
        attachmentId: String,
        fileId: String,
        chunkIndex: Int,
        data: ByteArray
    ): Result<Unit> = requestBinary(
        "PUT",
        "/boards/$boardId/attachments/$attachmentId/files/$fileId/chunks/$chunkIndex",
        data
    )

    override fun completeUpload(boardId: String, attachmentId: String): Result<BulletinAttachmentRef> =
        runCatching {
            val text = requestJson(
                "POST",
                "/boards/$boardId/attachments/$attachmentId/complete",
                "{}"
            ).getOrElse { throw it }
            val json = JSONObject(text)
            if (!json.optBoolean("ok", false)) {
                throw IllegalStateException(json.optString("message", "完成附件上传失败"))
            }
            val metaText = requestJson(
                "GET",
                "/boards/$boardId/attachments/$attachmentId",
                null
            ).getOrElse { throw it }
            val meta = JSONObject(metaText)
            BulletinAttachmentRef.fromJson(meta)
        }

    private fun parseInitResponse(text: String): InitUploadSession {
        val json = JSONObject(text)
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("message", "初始化附件上传失败"))
        }
        val files = buildList {
            val arr = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                add(
                    BulletinDirectoryFileSlot(
                        fileId = item.getString("file_id"),
                        path = item.optString("path"),
                        size = item.optLong("size")
                    )
                )
            }
        }
        return InitUploadSession(
            attachmentId = json.getString("attachment_id"),
            chunkSize = json.optInt("chunk_size", BulletinAttachmentDefaults.CHUNK_SIZE),
            directoryFiles = files
        )
    }
}
