package com.kenny.localmanager.file

import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ZipOperationsTest {
    @Test
    fun buildZipParametersForCompressionWithPasswordEncryptsFiles() {
        val params = buildZipParametersForCompression("secret".toCharArray())

        assertTrue(params.isEncryptFiles)
        assertEquals(EncryptionMethod.AES, params.encryptionMethod)
        assertEquals(AesKeyStrength.KEY_STRENGTH_256, params.aesKeyStrength)
    }

    @Test
    fun buildZipParametersWithoutPasswordLeavesFilesUnencrypted() {
        val params = buildZipParametersForCompression(null)

        assertEquals(false, params.isEncryptFiles)
    }

    @Test
    fun tryArchivePasswordFileSupportsSevenZipPasswords() {
        val tempDir = Files.createTempDirectory("zipops-7z-test").toFile()
        val archiveFile = File(tempDir, "test.7z")
        val contentFile = File(tempDir, "hello.txt")
        contentFile.writeText("secret content")

        SevenZOutputFile(archiveFile, "secret".toCharArray()).use { out ->
            val entry = out.createArchiveEntry(contentFile, "hello.txt")
            out.putArchiveEntry(entry)
            contentFile.inputStream().use { input ->
                val buffer = ByteArray(1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
            out.closeArchiveEntry()
            out.finish()
        }

        assertTrue(tryArchivePasswordFile(archiveFile, "secret".toCharArray()))
        assertFalse(tryArchivePasswordFile(archiveFile, "wrong".toCharArray()))

        tempDir.deleteRecursively()
    }
}
