package com.kenny.localmanager.file

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZipEditorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun createZipWith(vararg names: String): File {
        val zip = tmp.newFile("base.zip")
        ZipFile(zip).use { zf ->
            names.forEach { name ->
                val f = File(tmp.root, name)
                f.parentFile?.mkdirs()
                f.writeText("content of $name")
                zf.addFile(f, ZipParameters())
            }
        }
        return zip
    }

    @Test
    fun uniqueEntryNameAppendsCounterOnConflict() {
        assertEquals("a.txt", uniqueEntryName("a.txt", setOf("b.txt")))
        assertEquals("a (1).txt", uniqueEntryName("a.txt", setOf("a.txt")))
        assertEquals("a (2).txt", uniqueEntryName("a.txt", setOf("a.txt", "a (1).txt")))
        assertEquals("dir (1)", uniqueEntryName("dir", setOf("dir")))
        assertEquals("noext (1)", uniqueEntryName("noext", setOf("noext")))
    }

    @Test
    fun listEntriesOfFileReturnsEntryDetails() {
        val zip = createZipWith("x.txt")
        val entries = listEntriesOfFile(zip)
        assertEquals(1, entries.size)
        assertEquals("x.txt", entries[0].name)
        assertEquals("content of x.txt".length.toLong(), entries[0].size)
        assertFalse(entries[0].isDirectory)
    }

    @Test
    fun addFilesToZipFileAppendsNewEntries() {
        val zip = createZipWith("x.txt")
        val src1 = File(tmp.root, "y.txt").apply { writeText("hello y") }
        val src2 = File(tmp.root, "z.txt").apply { writeText("hello z") }
        val applied = addFilesToZipFile(zip, listOf(src1, src2))

        assertEquals(listOf("y.txt" to "y.txt", "z.txt" to "z.txt"), applied)
        val names = listEntriesOfFile(zip).map { it.name }
        assertTrue(names.containsAll(listOf("x.txt", "y.txt", "z.txt")))
    }

    @Test
    fun addFilesToZipFileRenamesOnNameConflict() {
        val zip = createZipWith("x.txt")
        val src = File(tmp.root, "x.txt")
        src.writeText("different content")
        val applied = addFilesToZipFile(zip, listOf(src))

        assertEquals(listOf("x.txt" to "x (1).txt"), applied)
        val entries = listEntriesOfFile(zip)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.name == "x.txt" })
        assertTrue(entries.any { it.name == "x (1).txt" })
    }

    @Test
    fun addFilesToZipFileAddsDirectoryEntries() {
        val zip = createZipWith("x.txt")
        val dir = File(tmp.root, "pics").apply { mkdirs() }
        File(dir, "a.png").writeText("png data")

        val applied = addFilesToZipFile(zip, listOf(dir))

        assertEquals(listOf("pics" to "pics"), applied)
        val entries = listEntriesOfFile(zip)
        assertTrue(entries.any { it.isDirectory })
        assertTrue(entries.any { it.name == "pics/a.png" })
    }

    @Test
    fun removeEntriesFromZipFileDeletesMatchingEntries() {
        val zip = createZipWith("keep.txt", "drop.txt", "drop2.txt")
        val changed = removeEntriesFromZipFile(zip, listOf("drop.txt", "drop2.txt"))

        assertTrue(changed)
        val names = listEntriesOfFile(zip).map { it.name }
        assertEquals(listOf("keep.txt"), names)
    }

    @Test
    fun removeEntriesFromZipFileIgnoresUnknownNames() {
        val zip = createZipWith("keep.txt")
        val changed = removeEntriesFromZipFile(zip, listOf("not-there.txt"))

        assertFalse(changed)
        assertEquals(listOf("keep.txt"), listEntriesOfFile(zip).map { it.name })
    }

    @Test
    fun addThenRemoveRoundTripKeepsContentIntact() {
        val zip = createZipWith("base.txt")
        addFilesToZipFile(zip, listOf(File(tmp.root, "extra.txt").apply { writeText("extra") }))
        removeEntriesFromZipFile(zip, listOf("extra.txt"))

        val entries = listEntriesOfFile(zip)
        assertEquals(listOf("base.txt"), entries.map { it.name })
    }

    // ------------------------------------------------------------------
    // 加密 zip：探针实证（zip4j 2.11.5）——remove 无需密码；add 需先验证
    // 密码（错密码 add 不报错会产出多密码混合包）；算法与 AES 强度保持。
    // ------------------------------------------------------------------

    private fun createAesZip(password: String): File {
        val zip = tmp.newFile("enc.zip")
        val src = File(tmp.root, "e1.txt").apply { writeText("secret one") }
        val params = ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
            aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
        }
        ZipFile(zip, password.toCharArray()).use { it.addFile(src, params) }
        return zip
    }

    @Test
    fun probeEncryptionDetectsAesStrengthAndNullForPlain() {
        val enc = createAesZip("pw")
        val info = probeEncryption(enc)
        assertEquals(net.lingala.zip4j.model.enums.EncryptionMethod.AES, info?.method)
        assertEquals(net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256, info?.aesKeyStrength)

        val plain = createZipWith("x.txt")
        assertEquals(null, probeEncryption(plain))
    }

    @Test
    fun verifyZipPasswordRejectsWrongAndAcceptsRight() {
        val enc = createAesZip("pw")
        assertFalse(verifyZipPassword(enc, "wrong".toCharArray()))
        assertTrue(verifyZipPassword(enc, "pw".toCharArray()))
    }

    @Test
    fun addFilesToEncryptedZipKeepsEncryptionAndStrength() {
        val enc = createAesZip("pw")
        val src = File(tmp.root, "new.txt").apply { writeText("new secret") }
        addFilesToZipFile(enc, listOf(src), probeEncryption(enc), "pw".toCharArray())

        val headers = ZipFile(enc, "pw".toCharArray()).use { it.fileHeaders }
        val newHeader = headers.first { it.fileName == "new.txt" }
        assertTrue(newHeader.isEncrypted)
        assertEquals(net.lingala.zip4j.model.enums.EncryptionMethod.AES, newHeader.encryptionMethod)
        assertEquals(
            net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256,
            newHeader.aesExtraDataRecord.aesKeyStrength
        )
        // 旧条目仍可解
        ZipFile(enc, "pw".toCharArray()).use { z ->
            val text = z.getInputStream(z.getFileHeader("e1.txt")).use { it!!.readBytes().decodeToString() }
            assertEquals("secret one", text)
        }
    }

    @Test
    fun addFilesToEncryptedZipRejectsWrongPasswordBeforeAnyWrite() {
        val enc = createAesZip("pw")
        val before = enc.readBytes()
        val src = File(tmp.root, "new.txt").apply { writeText("n") }
        try {
            addFilesToZipFile(enc, listOf(src), probeEncryption(enc), "bad".toCharArray())
            throw AssertionError("expected rejection")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(enc.readBytes().contentEquals(before))
    }

    @Test
    fun removeEntriesFromEncryptedZipNeedsNoPassword() {
        val enc = createAesZip("pw")
        assertTrue(removeEntriesFromZipFile(enc, listOf("e1.txt")))
        assertTrue(listEntriesOfFile(enc).isEmpty())
    }

}