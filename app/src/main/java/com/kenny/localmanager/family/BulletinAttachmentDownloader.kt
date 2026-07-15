package com.kenny.localmanager.family

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import kotlin.coroutines.coroutineContext
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.File

data class BlobDownloadResult(
    val statusCode: Int,
    val bytes: ByteArray,
    val totalSize: Long,
    val contentRange: String?
)

data class BlobDownloadStream(
    val statusCode: Int,
    val totalSize: Long,
    val contentRange: String?,
    val inputStream: InputStream,
    private val closeAction: () -> Unit = {}
) : Closeable {
    override fun close() {
        runCatching { inputStream.close() }
        runCatching { closeAction() }
    }
}

interface BulletinAttachmentDownloadTransport {
    fun getAttachmentMeta(boardId: String, attachmentId: String): Result<JSONObject>

    fun downloadBlobStream(
        boardId: String,
        attachmentId: String,
        fileId: String?
    ): Result<BlobDownloadStream>

    fun downloadBlob(
        boardId: String,
        attachmentId: String,
        fileId: String?,
        rangeStart: Long,
        rangeEndInclusive: Long
    ): Result<BlobDownloadResult>
}

object BulletinAttachmentDownloader {
    fun targetExists(context: Context, rootUri: String, ref: BulletinAttachmentRef): Boolean =
        BulletinAttachmentDownloadPaths.targetExists(context, rootUri, ref)

    suspend fun downloadAttachment(
        context: Context,
        rootUri: String,
        boardId: String,
        ref: BulletinAttachmentRef,
        transport: BulletinAttachmentDownloadTransport,
        conflict: BulletinAttachmentDownloadConflict,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<BulletinAttachmentDownloadResult> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = BulletinAttachmentDownloadPaths.downloadRoot(context, rootUri)
                ?: return@withContext Result.failure(IllegalStateException(context.getString(R.string.family_msg_95592)))
            val targetName = BulletinAttachmentDownloadPaths.resolveTargetName(context, downloadDir, ref, conflict)
            if (conflict == BulletinAttachmentDownloadConflict.OVERWRITE) {
                downloadDir.findFile(targetName)?.let { existing ->
                    deleteSafEntry(context, existing)
                }
            }

            val totalBytes = when (ref.kind) {
                BulletinAttachmentKind.FILE -> ref.size.coerceAtLeast(1L)
                BulletinAttachmentKind.DIRECTORY -> ref.totalSize.coerceAtLeast(ref.size).coerceAtLeast(1L)
            }
            onProgress?.invoke(0L, totalBytes)

            val resultUri = when (ref.kind) {
                BulletinAttachmentKind.FILE -> downloadSingleFile(
                    context, transport, boardId, ref, downloadDir, targetName, totalBytes, onProgress
                )
                BulletinAttachmentKind.DIRECTORY -> downloadDirectory(
                    context, transport, boardId, ref, downloadDir, targetName, totalBytes, onProgress
                )
            } ?: return@withContext Result.failure(IllegalStateException(context.getString(R.string.family_msg_31754)))

            val savedPath = "${BulletinAttachmentDownloadPaths.rootDirName(context)}/$targetName"
            Result.success(BulletinAttachmentDownloadResult(resultUri, savedPath))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun downloadSingleFile(
        context: Context,
        transport: BulletinAttachmentDownloadTransport,
        boardId: String,
        ref: BulletinAttachmentRef,
        downloadDir: DocumentFile,
        targetName: String,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)?
    ): Uri? {
        val mime = guessMimeType(ref.name)
        val created = downloadDir.createFile(mime, targetName)
            ?: throw IllegalStateException(context.getString(R.string.family_msg_53162))
        return try {
            downloadToSafFileStream(
                context = context,
                transport = transport,
                boardId = boardId,
                attachmentId = ref.id,
                fileId = null,
                totalSize = ref.size,
                targetUri = created.uri,
                onProgress = { downloaded, _ -> onProgress?.invoke(downloaded, totalBytes) }
            )
            created.uri
        } catch (e: Throwable) {
            deleteSafEntry(context, created)
            throw e
        }
    }

    private suspend fun downloadDirectory(
        context: Context,
        transport: BulletinAttachmentDownloadTransport,
        boardId: String,
        ref: BulletinAttachmentRef,
        downloadDir: DocumentFile,
        targetName: String,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)?
    ): Uri? {
        val meta = transport.getAttachmentMeta(boardId, ref.id).getOrElse { throw it }
        val files = meta.optJSONArray("files")
            ?: throw IllegalStateException(context.getString(R.string.family_msg_03372))
        val attachmentDir = downloadDir.createDirectory(targetName)
            ?: throw IllegalStateException(context.getString(R.string.family_msg_18940))

        var aggregateDownloaded = 0L
        for (i in 0 until files.length()) {
            coroutineContext.ensureActive()
            val entry = files.getJSONObject(i)
            val fileId = entry.getString("file_id")
            val relativePath = entry.getString("path")
            val fileSize = entry.getLong("size")
            val before = aggregateDownloaded
            downloadToDirectoryEntryStream(
                context = context,
                transport = transport,
                boardId = boardId,
                attachmentId = ref.id,
                fileId = fileId,
                totalSize = fileSize,
                attachmentDir = attachmentDir,
                relativePath = relativePath,
                onProgress = { downloaded, _ ->
                    onProgress?.invoke(before + downloaded, totalBytes)
                }
            )
            aggregateDownloaded += fileSize
        }
        return attachmentDir.uri
    }

    private suspend fun downloadToSafFileStream(
        context: Context,
        transport: BulletinAttachmentDownloadTransport,
        boardId: String,
        attachmentId: String,
        fileId: String?,
        totalSize: Long,
        targetUri: Uri,
        onProgress: ((downloadedInFile: Long, totalInFile: Long) -> Unit)?
    ) {
        val stream = transport.downloadBlobStream(boardId, attachmentId, fileId).getOrElse { throw it }
        stream.use {
            if (stream.statusCode !in listOf(200, 206)) {
                throw IllegalStateException(context.getString(R.string.family_msg_96683))
            }
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { output ->
                val buffer = ByteArray(DEFAULT_DOWNLOAD_CHUNK_SIZE_BYTES.toInt())
                var totalRead = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = stream.inputStream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    totalRead += read
                    onProgress?.invoke(totalRead, totalSize)
                }
                output.flush()
                if (totalRead < totalSize) {
                    throw IllegalStateException(context.getString(R.string.family_msg_14374, totalRead, totalSize))
                }
            } ?: throw IllegalStateException(context.getString(R.string.family_msg_80037))
        }
    }

    private suspend fun downloadToDirectoryEntryStream(
        context: Context,
        transport: BulletinAttachmentDownloadTransport,
        boardId: String,
        attachmentId: String,
        fileId: String?,
        totalSize: Long,
        attachmentDir: DocumentFile,
        relativePath: String,
        onProgress: ((downloadedInFile: Long, totalInFile: Long) -> Unit)?
    ) {
        val parts = relativePath.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw IllegalStateException(context.getString(R.string.family_msg_37067))
        var parent = attachmentDir
        for (segment in parts.dropLast(1)) {
            val safe = BulletinAttachmentDownloadPaths.sanitizeSegment(segment)
            parent = parent.findFile(safe)?.takeIf { it.isDirectory }
                ?: parent.createDirectory(safe)
                ?: throw IllegalStateException(context.getString(R.string.family_msg_05412))
        }
        val fileName = BulletinAttachmentDownloadPaths.sanitizeSegment(parts.last())
        parent.findFile(fileName)?.let { existing ->
            if (!existing.isDirectory) {
                deleteSafEntry(context, existing)
            }
        }
        val mime = guessMimeType(fileName)
        val created = parent.createFile(mime, fileName)
            ?: throw IllegalStateException(context.getString(R.string.family_msg_53162))
        downloadToSafFileStream(
            context = context,
            transport = transport,
            boardId = boardId,
            attachmentId = attachmentId,
            fileId = fileId,
            totalSize = totalSize,
            targetUri = created.uri,
            onProgress = onProgress
        )
    }

    private fun deleteSafEntry(context: Context, doc: DocumentFile) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, doc.uri) }
    }

    private fun cachePartFile(context: Context, attachmentId: String, fileId: String?): File {
        val suffix = fileId ?: "root"
        return File(context.cacheDir, "bulletin_download/$attachmentId/$suffix.part")
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }
}

class LocalBulletinAttachmentDownloadTransport(
    private val store: BulletinBoardStore
) : BulletinAttachmentDownloadTransport {
    override fun getAttachmentMeta(boardId: String, attachmentId: String): Result<JSONObject> =
        runCatching {
            store.attachments.getAttachmentMeta(boardId, attachmentId)
                ?: throw IllegalStateException(store.appContext.getString(R.string.family_msg_34495, attachmentId))
        }

    override fun downloadBlobStream(
        boardId: String,
        attachmentId: String,
        fileId: String?
    ): Result<BlobDownloadStream> = runCatching {
        val result = if (fileId != null) {
            store.attachments.openDirectoryFileBlob(boardId, attachmentId, fileId)
        } else {
            store.attachments.openFileBlob(boardId, attachmentId)
        } ?: throw IllegalStateException(store.appContext.getString(R.string.family_msg_96683))
        BlobDownloadStream(result.statusCode, result.totalSize, result.contentRange, result.inputStream)
    }

    override fun downloadBlob(
        boardId: String,
        attachmentId: String,
        fileId: String?,
        rangeStart: Long,
        rangeEndInclusive: Long
    ): Result<BlobDownloadResult> = runCatching {
        val rangeHeader = "bytes=$rangeStart-$rangeEndInclusive"
        val result = if (fileId != null) {
            store.attachments.readDirectoryFileBlob(boardId, attachmentId, fileId, rangeHeader)
        } else {
            store.attachments.readFileBlob(boardId, attachmentId, rangeHeader)
        } ?: throw IllegalStateException(store.appContext.getString(R.string.family_msg_96683))
        BlobDownloadResult(result.statusCode, result.bytes, result.totalSize, result.contentRange)
    }
}

class RemoteBulletinAttachmentDownloadTransport(
    private val service: FamilyDiscoveredService,
    private val openBlobConnection: (java.net.URL, String) -> java.net.HttpURLConnection,
    private val downloadBlobRequest: (
        path: String,
        rangeStart: Long,
        rangeEndInclusive: Long
    ) -> Result<BlobDownloadResult>,
    private val fetchMeta: (boardId: String, attachmentId: String) -> Result<JSONObject>
) : BulletinAttachmentDownloadTransport {
    override fun getAttachmentMeta(boardId: String, attachmentId: String): Result<JSONObject> =
        fetchMeta(boardId, attachmentId)

    override fun downloadBlobStream(
        boardId: String,
        attachmentId: String,
        fileId: String?
    ): Result<BlobDownloadStream> {
        val path = if (fileId != null) {
            "/boards/$boardId/attachments/$attachmentId/files/$fileId/blob"
        } else {
            "/boards/$boardId/attachments/$attachmentId/blob"
        }
        return runCatching {
            val protocol = service.attributes["proto"]?.trim()?.lowercase()
            if (protocol != FAMILY_TLS_PROTOCOL) {
                throw IllegalStateException("unsupported protocol: ${service.serviceName}")
            }
            val fingerprint = service.attributes[FAMILY_TLS_FINGERPRINT_ATTR]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("missing fingerprint: ${service.serviceName}")
            val endpoint = java.net.URL("https://${service.host}:${service.port}$path")
            val connection = openBlobConnection(endpoint, fingerprint)
            val status = connection.responseCode
            if (status !in listOf(200, 206)) {
                val detail = connection.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                connection.disconnect()
                throw IllegalStateException(detail.ifBlank { "HTTP $status" })
            }
            val contentRange = connection.getHeaderField("Content-Range")
            val totalSize = connection.getHeaderField("Content-Length")?.toLongOrNull()
                ?: connection.contentLengthLong.takeIf { it > 0 }
                ?: parseTotalSizeFromContentRange(contentRange)
                ?: -1L
            BlobDownloadStream(
                statusCode = status,
                totalSize = totalSize,
                contentRange = contentRange,
                inputStream = connection.inputStream,
                closeAction = { connection.disconnect() }
            )
        }
    }

    override fun downloadBlob(
        boardId: String,
        attachmentId: String,
        fileId: String?,
        rangeStart: Long,
        rangeEndInclusive: Long
    ): Result<BlobDownloadResult> {
        val path = if (fileId != null) {
            "/boards/$boardId/attachments/$attachmentId/files/$fileId/blob"
        } else {
            "/boards/$boardId/attachments/$attachmentId/blob"
        }
        return downloadBlobRequest(path, rangeStart, rangeEndInclusive)
    }

    private fun parseTotalSizeFromContentRange(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        return header.substringAfter('/', "").toLongOrNull()?.takeIf { it > 0 }
    }
}
