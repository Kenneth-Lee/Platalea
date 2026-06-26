package com.kenny.localmanager.family

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.family.BulletinAttachmentDefaults.CHUNK_SIZE
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
                ?: return@withContext Result.failure(IllegalStateException("无法访问下载目录"))
            val targetName = BulletinAttachmentDownloadPaths.resolveTargetName(downloadDir, ref, conflict)
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
            } ?: return@withContext Result.failure(IllegalStateException("附件下载失败"))

            val savedPath = "${BulletinAttachmentDownloadPaths.ROOT_DIR_NAME}/$targetName"
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
        val cacheFile = cachePartFile(context, ref.id, null)
        downloadToCacheFile(
            transport = transport,
            boardId = boardId,
            attachmentId = ref.id,
            fileId = null,
            totalSize = ref.size,
            cacheFile = cacheFile,
            onProgress = { downloaded, _ -> onProgress?.invoke(downloaded, totalBytes) }
        )
        return exportCacheFileToSaf(context, cacheFile, downloadDir, targetName, ref.name)
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
            ?: throw IllegalStateException("目录附件缺少 files 列表")
        val attachmentDir = downloadDir.createDirectory(targetName)
            ?: throw IllegalStateException("无法创建目录：$targetName")

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

    private suspend fun downloadToCacheFile(
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
                val end = minOf(offset + CHUNK_SIZE - 1, totalSize - 1)
                val chunk = transport.downloadBlob(boardId, attachmentId, fileId, offset, end)
                    .getOrElse { throw it }
                if (chunk.bytes.isEmpty()) {
                    throw IllegalStateException("下载附件分块为空：offset=$offset")
                }
                raf.seek(offset)
                raf.write(chunk.bytes)
                offset += chunk.bytes.size
                onProgress?.invoke(offset, totalSize)
            }
        }
        if (offset < totalSize) {
            throw IllegalStateException("下载未完成：$offset / $totalSize 字节")
        }
    }

    private fun exportCacheFileToSaf(
        context: Context,
        cacheFile: File,
        parentDir: DocumentFile,
        targetName: String,
        originalName: String
    ): Uri? {
        val mime = guessMimeType(originalName)
        val created = parentDir.createFile(mime, targetName) ?: return null
        copyFileToUri(context, cacheFile, created.uri)
        return created.uri
    }

    private fun exportCacheFileToSafPath(
        context: Context,
        cacheFile: File,
        attachmentDir: DocumentFile,
        relativePath: String
    ) {
        val parts = relativePath.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw IllegalStateException("目录附件路径为空")
        val fileName = BulletinAttachmentDownloadPaths.sanitizeSegment(parts.last())
        var parent = attachmentDir
        for (segment in parts.dropLast(1)) {
            val safe = BulletinAttachmentDownloadPaths.sanitizeSegment(segment)
            parent = parent.findFile(safe)?.takeIf { it.isDirectory }
                ?: parent.createDirectory(safe)
                ?: throw IllegalStateException("无法创建子目录：$safe")
        }
        parent.findFile(fileName)?.let { existing ->
            if (!existing.isDirectory) {
                deleteSafEntry(context, existing)
            }
        }
        val mime = guessMimeType(fileName)
        val created = parent.createFile(mime, fileName)
            ?: throw IllegalStateException("无法创建文件：$fileName")
        copyFileToUri(context, cacheFile, created.uri)
    }

    private fun deleteSafEntry(context: Context, doc: DocumentFile) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, doc.uri) }
    }

    private fun copyFileToUri(context: Context, source: File, destUri: Uri) {
        source.inputStream().use { input ->
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                input.copyTo(output)
            } ?: throw IllegalStateException("无法写入 SAF 文件")
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
                ?: throw IllegalStateException("附件不存在：$attachmentId")
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
        } ?: throw IllegalStateException("读取附件分块失败")
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
