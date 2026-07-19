package com.kenny.localmanager.family

import java.io.InputStream
import java.io.OutputStream

object TransferStreams {
    const val DEFAULT_IO_BUFFER_BYTES = 1024 * 1024
    const val DEFAULT_PROGRESS_STEP_BYTES = 1024 * 1024L

    data class TransferProfile(
        val name: String,
        val bufferSizeBytes: Int = DEFAULT_IO_BUFFER_BYTES,
        val progressStepBytes: Long = DEFAULT_PROGRESS_STEP_BYTES
    )

    object Profiles {
        val BOARDPACK_LOCAL_IO = TransferProfile(
            name = "boardpack_local_io",
            bufferSizeBytes = 1024 * 1024,
            progressStepBytes = 1024 * 1024L
        )
        val BOARDPACK_REMOTE_UPLOAD = TransferProfile(
            name = "boardpack_remote_upload",
            bufferSizeBytes = 1024 * 1024,
            progressStepBytes = 1024 * 1024L
        )
        val ATTACHMENT_DOWNLOAD = TransferProfile(
            name = "attachment_download",
            bufferSizeBytes = 1024 * 1024,
            progressStepBytes = 1024 * 1024L
        )
        val ATTACHMENT_CHUNK_UPLOAD = TransferProfile(
            name = "attachment_chunk_upload",
            bufferSizeBytes = 1024 * 1024,
            progressStepBytes = 1024 * 1024L
        )
    }

    fun copyBuffered(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int = DEFAULT_IO_BUFFER_BYTES,
        onBeforeRead: (() -> Unit)? = null,
        onBytesCopied: ((copiedBytes: Long) -> Unit)? = null
    ): Long {
        val buffer = ByteArray(bufferSize)
        var copied = 0L
        while (true) {
            onBeforeRead?.invoke()
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            copied += read.toLong()
            onBytesCopied?.invoke(copied)
        }
        return copied
    }

    fun readInChunks(
        input: InputStream,
        totalBytes: Long,
        chunkSize: Int,
        onBeforeRead: (() -> Unit)? = null,
        onChunk: (chunkIndex: Int, payload: ByteArray) -> Unit
    ): Long {
        val buffer = ByteArray(chunkSize)
        var uploaded = 0L
        var chunkIndex = 0
        while (uploaded < totalBytes) {
            onBeforeRead?.invoke()
            val toRead = minOf(chunkSize.toLong(), totalBytes - uploaded).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            val payload = if (read == buffer.size) buffer else buffer.copyOf(read)
            onChunk(chunkIndex, payload)
            chunkIndex++
            uploaded += read
        }
        return uploaded
    }

    fun shouldEmitProgress(
        currentBytes: Long,
        lastEmittedBytes: Long,
        totalBytes: Long? = null,
        stepBytes: Long = DEFAULT_PROGRESS_STEP_BYTES
    ): Boolean {
        if (currentBytes <= 0L) return false
        if (totalBytes != null && totalBytes > 0 && currentBytes >= totalBytes) return true
        return currentBytes - lastEmittedBytes >= stepBytes
    }

    fun bytesPerSecond(totalBytes: Long, elapsedMs: Long): Long {
        if (elapsedMs <= 0L) return 0L
        return (totalBytes * 1000L / elapsedMs).coerceAtLeast(0L)
    }
}
