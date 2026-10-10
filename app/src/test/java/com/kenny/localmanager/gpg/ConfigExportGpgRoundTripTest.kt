package com.kenny.localmanager.gpg

import com.kenny.localmanager.gpg.GpgHelper.GpgEncryptedKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

/** 配置导出 .json.gpg 依赖的对称加解密回路与加密类型检测。 */
class ConfigExportGpgRoundTripTest {

    @Test
    fun `symmetric round trip preserves json bytes`() {
        val plain = """{"git":{"keys":[]},"playlists":[]}""".toByteArray()
        val password = "pw123".toCharArray()
        val encrypted = GpgHelper.encryptSymmetric(plain, password, "config.json")!!
        assertArrayEquals(plain, GpgHelper.decryptSymmetric(ByteArrayInputStream(encrypted), password))
    }

    @Test
    fun `encrypted payload is detected as symmetric`() {
        val encrypted = GpgHelper.encryptSymmetric("{}".toByteArray(), "pw".toCharArray(), "c")!!
        assertEquals(GpgEncryptedKind.SYMMETRIC, GpgHelper.detectEncryptedKind(ByteArrayInputStream(encrypted)))
    }

    @Test
    fun `plain json is not detected as encrypted`() {
        assertEquals(GpgEncryptedKind.UNKNOWN, GpgHelper.detectEncryptedKind(ByteArrayInputStream("{}".toByteArray())))
    }

    @Test
    fun `wrong password fails to decrypt`() {
        val encrypted = GpgHelper.encryptSymmetric("{}".toByteArray(), "right".toCharArray(), "c")!!
        assertNull(GpgHelper.decryptSymmetric(ByteArrayInputStream(encrypted), "wrong".toCharArray()))
    }

    @Test
    fun `empty password is rejected by encrypt`() {
        assertNull(GpgHelper.encryptSymmetric("{}".toByteArray(), CharArray(0), "c"))
    }
}
