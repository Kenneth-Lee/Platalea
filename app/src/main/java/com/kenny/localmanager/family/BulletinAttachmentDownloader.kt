package com.kenny.localmanager.family

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

data class BlobDownloadResult(
    val statusCode: Int,
    val bytes: ByteArray,
    val totalSize: Long,
    val contentRange: String?
)

interface BulletinAttachmentDownloadTransport {
    fun getAttachmentMeta(boardId: String, attachmentId: String): Result<JSONObject>

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
            downloadToSafFile(
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
            val cacheFile = cachePartFile(context, ref.id, fileId)
            val before = aggregateDownloaded
            downloadToCacheFile(
                context = context,
                transport = transport,
                boardId = boardId,
                attachmentId = ref.id,
                fileId = fileId,
                totalSize = fileSize,
                cacheFile = cacheFile,
                onProgress = { downloaded, _ ->
                    onProgress?.invoke(before + downloaded, totalBytes)
                }
            )
            aggregateDownloaded += fileSize
            exportCacheFileToSafPath(context, cacheFile, attachmentDir, relativePath)
        }
        return attachmentDir.uri
    }

    private suspend fun downloadToSafFile(
        context: Context,
        transport: BulletinAttachmentDownloadTransport,
        boardId: String,
        attachmentId: String,
        fileId: String?,
        totalSize: Long,
        targetUri: Uri,
        onProgress: ((downloadedInFile: Long, totalInFile: Long) -> Unit)?
    ) {
        var offset = 0L
        context.contentResolver.openOutputStream(targetUri, "wt")?.use { output ->
            while (offset < totalSize) {
                coroutineContext.ensureActive()
                val end = minOf(offset + DEFAULT_DOWNLOAD_CHUNK_SIZE_BYTES - 1, totalSize - 1)
                val chunk = transport.downloadBlob(boardId, attachmentId, fileId, offset, end)
                    .getOrElse { throw it }
                if (chunk.bytes.isEmpty()) {
                    throw IllegalStateException(context.getString(R.string.family_msg_00942, offset))
                }
                output.write(chunk.bytes)
                offset += chunk.bytes.size
                onProgress?.invoke(offset, totalSize)
            }
            output.flush()
        } ?: throw IllegalStateException(context.getString(R.string.family_msg_80037))
        if (offset < totalSize) {
            throw IllegalStateException(context.getString(R.string.family_msg_14374, offset, totalSize))
        }
    }

    private suspend fun downloadToCacheFile(
        context: Context,
        transport: BulletinAttachmentDownloadTransport,
        boardId: String,
        attachmentId: String,
        fileId: String?,
        totalSize: Long,
        cacheFile: File,
        onProgress: ((downloadedInFile: Long, totalInFile: Long) -> Unit)?
    ) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.delete()
        var offset = 0L
        RandomAccessFile(cacheFile, "rw").use { raf ->
            while (offset < totalSize) {
                coroutineContext.ensureActive()
                val end = minOf(offset + DEFAULT_DOWNLOAD_CHUNK_SIZE_BYTES - 1, totalSize - 1)
                val chunk = transport.downloadBlob(boardId, attachmentId, fileId, offset, end)
                    .getOrElse { throw it }
                if (chunk.bytes.isEmpty()) {
                    throw IllegalStateException(context.getString(R.string.family_msg_00942, offset))
                }
                raf.seek(offset)
                raf.write(chunk.bytes)
                offset += chunk.bytes.size
                onProgress?.invoke(offset, totalSize)
            }
        }
        if (offset < totalSize) {
            throw IllegalStateException(context.getString(R.string.family_msg_14374, offset, totalSize))
        }
    }

    private fun exportCacheFileToSafPath(
        context: Context,
        cacheFile: File,
        attachmentDir: DocumentFile,
        relativePath: String
    ) {
        val parts = relativePath.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw IllegalStateException(context.getString(R.string.family_msg_37067))
        val fileName = BulletinAttachmentDownloadPaths.sanitizeSegment(parts.last())
        var parent = attachmentDir
        for (segment in parts.dropLast(1)) {
            val safe = BulletinAttachmentDownloadPaths.sanitizeSegment(segment)
            parent = parent.findFile(safe)?.takeIf { it.isDirectory }
                ?: parent.createDirectory(safe)
                ?: throw IllegalStateException(context.getString(R.string.family_msg_05412))
        }
        parent.findFile(fileName)?.let { existing ->
            if (!existing.isDirectory) {
                deleteSafEntry(context, existing)
            }
        }
        val mime = guessMimeType(fileName)
        val created = parent.createFile(mime, fileName)
            ?: throw IllegalStateException(context.getString(R.string.family_msg_53162))
        copyFileToUri(context, cacheFile, created.uri)
    }

    private fun deleteSafEntry(context: Context, doc: DocumentFile) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, doc.uri) }
    }

    private fun copyFileToUri(context: Context, source: File, destUri: Uri) {
        source.inputStream().use { input ->
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                input.copyTo(output)
            } ?: throw IllegalStateException(context.getString(R.string.family_msg_80037))
        }
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
    private val downloadBlobRequest: (
        path: String,
        rangeStart: Long,
        rangeEndInclusive: Long
    ) -> Result<BlobDownloadResult>,
    private val fetchMeta: (boardId: String, attachmentId: String) -> Result<JSONObject>
) : BulletinAttachmentDownloadTransport {
    override fun getAttachmentMeta(boardId: String, attachmentId: String): Result<JSONObject> =
        fetchMeta(boardId, attachmentId)

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
}
