package com.kenny.localmanager.ftp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

class MultipartUploadParserTest {
    @Test
    fun `normalizeUploadRelativePath strips windows separators and traversal segments`() {
        assertEquals("nested/demo.txt", normalizeUploadRelativePath("\\nested\\demo.txt"))
        assertEquals("nested/demo.txt", normalizeUploadRelativePath("./nested/../nested/demo.txt"))
        assertEquals("demo.txt", normalizeUploadRelativePath("/demo.txt"))
    }

    @Test
    fun `parseMultipartUploads preserves nested relative paths and binary payload bytes`() {
        val boundary = "----TestBoundary"
        val payload1 = "hello\nworld".toByteArray(StandardCharsets.UTF_8)
        val payload2 = byteArrayOf(0x00, 0x01, 0x7F.toByte(), 0xFF.toByte())

        val body = buildMultipartBody(boundary, listOf(
            "folder/nested/demo.txt" to payload1,
            "root.bin" to payload2
        ))

        val files = parseMultipartUploads(body, "multipart/form-data; boundary=$boundary")

        assertEquals(2, files.size)
        assertEquals("folder/nested/demo.txt", files[0].relativePath)
        assertArrayEquals(payload1, files[0].bytes)
        assertEquals("root.bin", files[1].relativePath)
        assertArrayEquals(payload2, files[1].bytes)
    }

    @Test
    fun `parseMultipartUploadsFromFile reads multipart payload from disk cache`() {
        val boundary = "----DiskBoundary"
        val payload1 = "large file payload".repeat(2048).toByteArray(StandardCharsets.UTF_8)
        val payload2 = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0xFF.toByte())

        val bodyFile = File.createTempFile("multipart-disk", ".bin")
        bodyFile.writeBytes(buildMultipartBody(boundary, listOf(
            "folder/large.txt" to payload1,
            "small.bin" to payload2
        )))

        try {
            val files = parseMultipartUploadsFromFile(bodyFile, "multipart/form-data; boundary=$boundary")
            assertEquals(2, files.size)
            assertEquals("folder/large.txt", files[0].relativePath)
            assertArrayEquals(payload1, files[0].bytes)
            assertEquals("small.bin", files[1].relativePath)
            assertArrayEquals(payload2, files[1].bytes)
        } finally {
            bodyFile.delete()
        }
    }

    private fun buildMultipartBody(boundary: String, parts: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((filename, payload) in parts) {
            out.write("--$boundary\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            out.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n".toByteArray(StandardCharsets.ISO_8859_1)
            )
            out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            out.write(payload)
            out.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        }
        out.write("--$boundary--\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        return out.toByteArray()
    }
}
