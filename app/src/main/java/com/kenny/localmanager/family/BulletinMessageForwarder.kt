package com.kenny.localmanager.family

import java.io.ByteArrayInputStream

object BulletinMessageForwarder {
    fun relayAttachments(
        sourceSession: BulletinBoardOpenSession,
        sourceDownload: BulletinAttachmentDownloadTransport,
        target: BulletinForwardTarget,
        targetTransport: BulletinAttachmentTransport,
        message: BulletinMessage,
        uploaderDevice: String?,
        boardStore: BulletinBoardStore? = null
    ): Result<List<BulletinAttachmentRef>> {
        if (message.attachments.isEmpty()) return Result.success(emptyList())
        return runCatching {
            message.attachments.map { attachment ->
                if (sourceSession.isHost && target.service.isSelf && boardStore != null) {
                    boardStore.attachments.duplicateReadyAttachment(
                        sourceBoardId = sourceSession.boardId,
                        attachmentId = attachment.id,
                        targetBoardId = target.boardId
                    ) ?: throw IllegalStateException("复制附件失败：${attachment.name}")
                } else {
                    reuploadAttachment(
                        sourceSession = sourceSession,
                        sourceDownload = sourceDownload,
                        targetTransport = targetTransport,
                        targetBoardId = target.boardId,
                        attachment = attachment,
                        uploaderDevice = uploaderDevice
                    )
                }
            }
        }
    }

    private fun reuploadAttachment(
        sourceSession: BulletinBoardOpenSession,
        sourceDownload: BulletinAttachmentDownloadTransport,
        targetTransport: BulletinAttachmentTransport,
        targetBoardId: String,
        attachment: BulletinAttachmentRef,
        uploaderDevice: String?
    ): BulletinAttachmentRef {
        return when (attachment.kind) {
            BulletinAttachmentKind.FILE -> reuploadFileAttachment(
                sourceDownload = sourceDownload,
                targetTransport = targetTransport,
                sourceSession = sourceSession,
                targetBoardId = targetBoardId,
                attachment = attachment,
                uploaderDevice = uploaderDevice
            )
            BulletinAttachmentKind.DIRECTORY -> reuploadDirectoryAttachment(
                sourceDownload = sourceDownload,
                targetTransport = targetTransport,
                sourceSession = sourceSession,
                targetBoardId = targetBoardId,
                attachment = attachment,
                uploaderDevice = uploaderDevice
            )
        }
    }

    private fun reuploadFileAttachment(
        sourceDownload: BulletinAttachmentDownloadTransport,
        targetTransport: BulletinAttachmentTransport,
        sourceSession: BulletinBoardOpenSession,
        targetBoardId: String,
        attachment: BulletinAttachmentRef,
        uploaderDevice: String?
    ): BulletinAttachmentRef {
        val totalSize = attachment.size.coerceAtLeast(1L)
        val bytes = readAllBytes(totalSize) { start, end ->
            sourceDownload.downloadBlob(
                boardId = sourceSession.boardId,
                attachmentId = attachment.id,
                fileId = null,
                rangeStart = start,
                rangeEndInclusive = end
            ).getOrElse { throw it }.bytes
        }
        val init = targetTransport.initFileUpload(
            boardId = targetBoardId,
            name = attachment.name,
            size = bytes.size.toLong(),
            mime = attachment.mime,
            uploaderDevice = uploaderDevice
        ).getOrElse { throw it }
        uploadBytesInChunks(
            transport = targetTransport,
            boardId = targetBoardId,
            attachmentId = init.attachmentId,
            fileId = null,
            chunkSize = init.chunkSize,
            data = bytes
        )
        return targetTransport.completeUpload(targetBoardId, init.attachmentId).getOrElse { throw it }
    }

    private fun reuploadDirectoryAttachment(
        sourceDownload: BulletinAttachmentDownloadTransport,
        targetTransport: BulletinAttachmentTransport,
        sourceSession: BulletinBoardOpenSession,
        targetBoardId: String,
        attachment: BulletinAttachmentRef,
        uploaderDevice: String?
    ): BulletinAttachmentRef {
        val meta = sourceDownload.getAttachmentMeta(sourceSession.boardId, attachment.id)
            .getOrElse { throw it }
        val files = meta.optJSONArray("files")
            ?: throw IllegalStateException("目录附件缺少 files 列表")
        val sourceFileIdByPath = buildMap {
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                put(file.getString("path"), file.getString("file_id"))
            }
        }
        val entries = buildList {
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                add(
                    BulletinDirectoryEntry(
                        path = file.getString("path"),
                        size = file.getLong("size")
                    )
                )
            }
        }
        if (entries.isEmpty()) {
            throw IllegalStateException("目录附件 ${attachment.name} 为空")
        }
        val init = targetTransport.initDirectoryUpload(
            boardId = targetBoardId,
            name = attachment.name,
            entries = entries,
            uploaderDevice = uploaderDevice
        ).getOrElse { throw it }
        init.directoryFiles.forEach { slot ->
            val sourceFileId = sourceFileIdByPath[slot.path]
                ?: throw IllegalStateException("源目录附件缺少文件：${slot.path}")
            val bytes = readAllBytes(slot.size.coerceAtLeast(1L)) { start, end ->
                sourceDownload.downloadBlob(
                    boardId = sourceSession.boardId,
                    attachmentId = attachment.id,
                    fileId = sourceFileId,
                    rangeStart = start,
                    rangeEndInclusive = end
                ).getOrElse { throw it }.bytes
            }
            uploadBytesInChunks(
                transport = targetTransport,
                boardId = targetBoardId,
                attachmentId = init.attachmentId,
                fileId = slot.fileId,
                chunkSize = init.chunkSize,
                data = bytes
            )
        }
        return targetTransport.completeUpload(targetBoardId, init.attachmentId).getOrElse { throw it }
    }

    private fun readAllBytes(totalSize: Long, readRange: (Long, Long) -> ByteArray): ByteArray {
        if (totalSize <= 0) return ByteArray(0)
        val chunkSize = BulletinAttachmentDefaults.CHUNK_SIZE.toLong()
        val output = ByteArray(totalSize.toInt())
        var offset = 0
        var start = 0L
        while (start < totalSize) {
            val end = minOf(totalSize - 1, start + chunkSize - 1)
            val chunk = readRange(start, end)
            chunk.copyInto(output, offset)
            offset += chunk.size
            start = end + 1
        }
        return output
    }

    private fun uploadBytesInChunks(
        transport: BulletinAttachmentTransport,
        boardId: String,
        attachmentId: String,
        fileId: String?,
        chunkSize: Int,
        data: ByteArray
    ) {
        ByteArrayInputStream(data).use { stream ->
            val buffer = ByteArray(chunkSize)
            var chunkIndex = 0
            var uploaded = 0
            while (uploaded < data.size) {
                val toRead = minOf(chunkSize, data.size - uploaded)
                val read = stream.read(buffer, 0, toRead)
                if (read <= 0) break
                val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                if (fileId == null) {
                    transport.uploadFileChunk(boardId, attachmentId, chunkIndex, chunk).getOrElse { throw it }
                } else {
                    transport.uploadDirectoryFileChunk(boardId, attachmentId, fileId, chunkIndex, chunk)
                        .getOrElse { throw it }
                }
                chunkIndex++
                uploaded += read
            }
        }
    }
}
