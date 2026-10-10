package com.kenny.localmanager.file

import net.lingala.zip4j.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** fd-seek 解析 zip central directory 的内核测试（零复制路径）。 */
class ZipCentralDirectoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun parse(file: File): List<ZipCdEntry>? {
        RandomAccessFile(file, "r").use { raf ->
            return parseZipCentralDirectory(raf.channel, 0L, file.length())
        }
    }

    private fun writeSimpleZip(target: File, comment: String? = null) {
        ZipOutputStream(target.outputStream().buffered()).use { zos ->
            if (comment != null) zos.setComment(comment)
            zos.putNextEntry(ZipEntry("dir/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("dir/a.txt"))
            zos.write("hello zip cd".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("dir/b/c.txt"))
            zos.write(byteArrayOf(1, 2, 3, 4, 5))
            zos.closeEntry()
        }
    }

    @Test
    fun 普通zip_名字大小目录与时间均可解析() {
        val f = tmp.newFile("plain.zip")
        writeSimpleZip(f)

        val entries = parse(f)!!

        assertEquals(3, entries.size)
        assertEquals(listOf("dir/", "dir/a.txt", "dir/b/c.txt"), entries.map { it.name })
        assertTrue(entries[0].isDirectory)
        assertFalse(entries[1].isDirectory)
        assertEquals("hello zip cd".length.toLong(), entries[1].uncompressedSize)
        assertTrue(entries[1].uncompressedSize > 0)
        assertTrue(entries[1].lastModifiedTime > 0L)
        entries.forEach { assertFalse(it.isEncrypted) }
    }

    @Test
    fun 尾部注释zip_仍能定位EOCD() {
        val f = tmp.newFile("comment.zip")
        writeSimpleZip(f, comment = "x".repeat(300))

        val entries = parse(f)

        assertEquals(3, entries?.size)
    }

    @Test
    fun 加密zip_条目名明文可读且加密标志置位() {
        val f = tmp.newFile("enc.zip")
        ZipFile(f).use { zip ->
            zip.setPassword("secret123".toCharArray())
            val params = net.lingala.zip4j.model.ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
                fileNameInZip = "pics/photo.jpg"
            }
            zip.addStream(java.io.ByteArrayInputStream(ByteArray(64)), params)
        }

        val entries = parse(f)!!

        assertEquals(1, entries.size)
        assertEquals("pics/photo.jpg", entries[0].name)
        assertTrue(entries[0].isEncrypted)
    }

    @Test
    fun 非zip与截断文件_返回null() {
        val txt = tmp.newFile("not.zip")
        txt.writeText("这不是 zip 文件，只是普通文本内容用于触发解析失败。")

        assertNull(parse(txt))

        val zip = tmp.newFile("cut.zip")
        writeSimpleZip(zip)
        val truncated = tmp.newFile("trunc.zip")
        truncated.outputStream().use { out ->
            zip.inputStream().use { it.copyTo(out) }
        }
        // 砍掉尾部 EOCD（保留前 2/3）
        val keep = (truncated.length() * 2 / 3).toInt()
        RandomAccessFile(truncated, "rw").use { it.setLength(keep.toLong()) }

        assertNull(parse(truncated))
    }

    @Test
    fun zip64EOCD分支_字段饱和时从zip64记录取值() {
        val f = tmp.newFile("z64.zip")
        writeSimpleZip(f)
        val bytes = f.readBytes()
        // 无注释时 EOCD 固定在尾部 22 字节
        val eocdOff = bytes.size - 22
        val cdOffset = ByteBuffer.wrap(bytes, eocdOff + 16, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val cdSize = ByteBuffer.wrap(bytes, eocdOff + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

        // 构造 zip64 EOCD(56B) + locator(20B)，插到 EOCD 前；EOCD 的 totalEntries 置 0xFFFF 触发 zip64 分支
        val z64 = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
        z64.putInt(0x06064b50); z64.putLong(44)
        z64.putShort(45); z64.putShort(45)
        z64.putInt(0); z64.putInt(0)
        z64.putLong(3); z64.putLong(3)
        z64.putLong(cdSize); z64.putLong(cdOffset)

        val loc = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        loc.putInt(0x07064b50); loc.putInt(0)
        loc.putLong((bytes.size - 22).toLong()); loc.putInt(1) // zip64 EOCD 插在原 EOCD 位置

        val patched = ByteBuffer.allocate(bytes.size + 76)
        patched.put(bytes, 0, eocdOff).put(z64.array()).put(loc.array()).put(bytes, eocdOff, 22)
        patched.array()[patched.position() - 22 + 10] = 0xFF.toByte() // totalEntries 低字节
        patched.array()[patched.position() - 22 + 11] = 0xFF.toByte() // totalEntries 高字节
        val out = tmp.newFile("z64-patched.zip")
        out.writeBytes(patched.array())

        val entries = parse(out)

        assertEquals(3, entries?.size)
        assertEquals("dir/a.txt", entries?.get(1)?.name)
    }

    @Test
    fun 多级目录与空文件_逐条解析完整() {
        val f = tmp.newFile("multi.zip")
        ZipOutputStream(f.outputStream().buffered()).use { zos ->
            repeat(20) { i ->
                zos.putNextEntry(ZipEntry("n$i/x$i.txt"))
                zos.write(ByteArray(i)) // 含空文件（0 字节）
                zos.closeEntry()
            }
        }

        val entries = parse(f)!!

        assertEquals(20, entries.size)
        assertEquals(0L, entries[0].uncompressedSize)
        assertEquals(19L, entries[19].uncompressedSize)
        assertFalse(entries[0].isDirectory)
    }

    @Test
    fun EOCD指纹_内容变化指纹必变() {
        val f1 = tmp.newFile("fp1.zip")
        writeSimpleZip(f1)
        val f2 = tmp.newFile("fp2.zip")
        ZipOutputStream(f2.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("dir/")); zos.closeEntry()
            zos.putNextEntry(ZipEntry("dir/a.txt")); zos.write("hello world longer".toByteArray()); zos.closeEntry()
            zos.putNextEntry(ZipEntry("dir/b.txt")); zos.write(byteArrayOf(1)); zos.closeEntry()
        }

        val fp1 = java.io.RandomAccessFile(f1, "r").use { parseZipEocd(it.channel, 0L, f1.length()) }
        val fp2 = java.io.RandomAccessFile(f2, "r").use { parseZipEocd(it.channel, 0L, f2.length()) }

        assertEquals(3L, fp1?.totalEntries)
        assertEquals(3L, fp2?.totalEntries)
        // 大小不同 → cdOffset/cdSize 必不同（指纹区分内容变化）
        org.junit.Assert.assertNotEquals(fp1, fp2)
    }

    @Test
    fun EOCD指纹_同一文件稳定不变() {
        val f = tmp.newFile("fp-stable.zip")
        writeSimpleZip(f)
        val a = java.io.RandomAccessFile(f, "r").use { parseZipEocd(it.channel, 0L, f.length()) }
        val b = java.io.RandomAccessFile(f, "r").use { parseZipEocd(it.channel, 0L, f.length()) }
        org.junit.Assert.assertEquals(a, b)
    }
}
