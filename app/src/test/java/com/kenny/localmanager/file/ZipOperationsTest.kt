package com.kenny.localmanager.file

import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
