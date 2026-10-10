package com.kenny.localmanager.file

import android.content.Context
import android.net.Uri
import java.io.EOFException
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * zip central directory 条目的轻量描述（fd-seek 解析，不解压不复制）。
 */
data class ZipCdEntry(
    val name: String,
    val isDirectory: Boolean,
    val isEncrypted: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long,
    /** 条目修改时间（epoch 毫秒）；无法解析时为 0。 */
    val lastModifiedTime: Long
)

private const val EOCD_SIG = 0x06054b50          // PK\5\6
private const val ZIP64_EOCD_LOCATOR_SIG = 0x07064b50  // PK\6\7
private const val ZIP64_EOCD_SIG = 0x06064b50    // PK\6\6
private const val CD_ENTRY_SIG = 0x02014b50      // PK\1\2
private const val MAX_ZIP_COMMENT = 65535

/**
 * 通过随机访问 fd 直接解析 zip 的 EOCD + central directory：
 * 只读文件尾部的目录结构（几十 KB 量级），与压缩包总大小无关，
 * 打开几 GB 的 zip 也能瞬时完成。
 *
 * 前置条件：provider 返回的 fd 可 seek（本地 DocumentsProvider 均满足）。
 * fd 不可 seek（云端/管道 provider）、非 zip 或结构损坏时返回 null，
 * 调用方应回退到全量复制 + zip4j 的旧路径。
 */
fun readZipCentralDirectoryFromUri(context: Context, uri: Uri): List<ZipCdEntry>? {
    return try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            val channel = FileInputStream(afd.fileDescriptor).channel
            val base = afd.startOffset
            val logicalSize = if (afd.declaredLength >= 0) afd.declaredLength else channel.size() - base
            if (logicalSize <= 0) null else parseZipCentralDirectory(channel, base, logicalSize)
        }
    } catch (_: Exception) {
        null
    }
}

/** EOCD 摘要：目录定位信息，用作 zip 内容指纹（任何实质内容变化必变）。 */
data class ZipEocdFingerprint(
    val cdSize: Long,
    val cdOffset: Long,
    val totalEntries: Long
)

/**
 * 仅读尾部 EOCD（最坏 64KB）拿目录指纹，不解析条目。
 * 供缓存失效判定使用：零复制、与大包大小无关。返回 null 表示非 zip / 不可 seek。
 */
fun readZipEocdFingerprintFromUri(context: Context, uri: Uri): ZipEocdFingerprint? {
    return try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            val channel = FileInputStream(afd.fileDescriptor).channel
            val base = afd.startOffset
            val logicalSize = if (afd.declaredLength >= 0) afd.declaredLength else channel.size() - base
            if (logicalSize <= 0) null else parseZipEocd(channel, base, logicalSize)
        }
    } catch (_: Exception) {
        null
    }
}

/** 解析 EOCD（含 zip64 定位），供指纹与完整目录解析共用。 */
internal fun parseZipEocd(
    channel: FileChannel,
    baseOffset: Long,
    logicalSize: Long
): ZipEocdFingerprint? {
    // ---- 1) 尾部搜索 EOCD（允许最长 64KB 注释）----
    val tailLen = minOf(logicalSize, (22L + MAX_ZIP_COMMENT))
    val tailStart = logicalSize - tailLen
    val tail = readBytes(channel, baseOffset + tailStart, tailLen.toInt()) ?: return null

    var eocdIdx = -1
    var i = tail.size - 22
    while (i >= 0) {
        if (readIntLE(tail, i) == EOCD_SIG) { eocdIdx = i; break }
        i--
    }
    if (eocdIdx < 0) return null

    val buf = ByteBuffer.wrap(tail, eocdIdx, 22).order(ByteOrder.LITTLE_ENDIAN)
    buf.position(eocdIdx + 10) // position 为绝对索引：跳过签名与磁盘号×2，读总条目数
    var totalEntries = readUShort(buf).toLong()
    var cdSize = readUInt(buf)
    var cdOffset = readUInt(buf)

    // ---- 2) zip64 判定：字段饱和值时从 zip64 EOCD 取真实值 ----
    val needsZip64 = totalEntries == 0xFFFFL || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL
    if (needsZip64) {
        val locatorPos = eocdIdx - 20
        if (locatorPos < 0) return null
        val loc = ByteBuffer.wrap(tail, locatorPos, 20).order(ByteOrder.LITTLE_ENDIAN)
        if (loc.int != ZIP64_EOCD_LOCATOR_SIG) return null
        loc.position(locatorPos + 8)
        val zip64EocdOffset = loc.long
        if (zip64EocdOffset < 0 || zip64EocdOffset + 56 > logicalSize) return null
        val z64 = readBytes(channel, baseOffset + zip64EocdOffset, 56) ?: return null
        val zbuf = ByteBuffer.wrap(z64).order(ByteOrder.LITTLE_ENDIAN)
        if (zbuf.int != ZIP64_EOCD_SIG) return null
        zbuf.position(32)
        totalEntries = zbuf.long
        cdSize = zbuf.long
        cdOffset = zbuf.long
    }
    if (cdOffset < 0 || cdSize < 0 || cdOffset + cdSize > logicalSize || totalEntries < 0) return null
    return ZipEocdFingerprint(cdSize = cdSize, cdOffset = cdOffset, totalEntries = totalEntries)
}

/**
 * 解析内核：[channel] 为底层 fd 的通道，[baseOffset] 为逻辑 0 字节在 fd 中的绝对位置，
 * [logicalSize] 为逻辑文件长度。结构非法返回 null。
 */
internal fun parseZipCentralDirectory(
    channel: FileChannel,
    baseOffset: Long,
    logicalSize: Long
): List<ZipCdEntry>? {
    val eocd = parseZipEocd(channel, baseOffset, logicalSize) ?: return null
    val totalEntries = eocd.totalEntries
    val cdSize = eocd.cdSize
    val cdOffset = eocd.cdOffset

    // ---- 3) 顺序解析 central directory 条目 ----
    val cdBytes = readBytes(channel, baseOffset + cdOffset, cdSize.toInt()) ?: return null
    val entries = mutableListOf<ZipCdEntry>()
    var p = 0
    while (p + 46 <= cdBytes.size && entries.size < totalEntries) {
        if (readIntLE(cdBytes, p) != CD_ENTRY_SIG) return null
        val b = ByteBuffer.wrap(cdBytes, p, 46).order(ByteOrder.LITTLE_ENDIAN)
        b.position(p + 8)
        val flags = readUShort(b)
        b.position(p + 12) // 跳过压缩方法（offset 10），读 DOS time/date
        val dosTime = readUShort(b)
        val dosDate = readUShort(b)
        b.position(p + 20) // 跳过 crc32
        var compressedSize = readUInt(b)
        var uncompressedSize = readUInt(b)
        val nameLen = readUShort(b)
        val extraLen = readUShort(b)
        val commentLen = readUShort(b)
        b.position(p + 42) // 跳过 disk/internal/external attrs/lfh offset
        val nameStart = p + 46
        if (nameStart + nameLen + extraLen + commentLen > cdBytes.size) return null
        val utf8 = (flags and 0x800) != 0
        val name = String(cdBytes, nameStart, nameLen, if (utf8) Charsets.UTF_8 else Charsets.ISO_8859_1)

        // zip64 extra（id 0x0001）：按缺失顺序补 64 位字段
        if (uncompressedSize == 0xFFFFFFFFL || compressedSize == 0xFFFFFFFFL) {
            var ep = nameStart + nameLen
            val extraEnd = ep + extraLen
            while (ep + 4 <= extraEnd) {
                val id = readUShort(ByteBuffer.wrap(cdBytes, ep, 2).order(ByteOrder.LITTLE_ENDIAN))
                val len = readUShort(ByteBuffer.wrap(cdBytes, ep + 2, 2).order(ByteOrder.LITTLE_ENDIAN))
                if (id == 0x0001) {
                    val ebuf = ByteBuffer.wrap(cdBytes, ep + 4, len).order(ByteOrder.LITTLE_ENDIAN)
                    if (uncompressedSize == 0xFFFFFFFFL && ebuf.remaining() >= 8) uncompressedSize = ebuf.long
                    if (compressedSize == 0xFFFFFFFFL && ebuf.remaining() >= 8) compressedSize = ebuf.long
                    break
                }
                ep += 4 + len
            }
        }

        entries.add(
            ZipCdEntry(
                name = name,
                isDirectory = name.endsWith("/") || name.endsWith("\\"),
                isEncrypted = (flags and 0x1) != 0,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                lastModifiedTime = dosDateTimeToEpochMillis(dosDate, dosTime)
            )
        )
        p = nameStart + nameLen + extraLen + commentLen
    }
    return entries
}

/** 从 channel 指定绝对位置读满 [length] 字节；越界或 IO 失败返回 null。 */
private fun readBytes(channel: FileChannel, position: Long, length: Int): ByteArray? {
    if (length <= 0) return ByteArray(0)
    return try {
        val out = ByteArray(length)
        channel.position(position)
        val buf = ByteBuffer.wrap(out)
        while (buf.hasRemaining()) {
            val n = channel.read(buf)
            if (n < 0) throw EOFException()
        }
        out
    } catch (_: Exception) {
        null
    }
}

private fun readIntLE(bytes: ByteArray, offset: Int): Int =
    ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

private fun readUShort(buf: ByteBuffer): Int = buf.short.toInt() and 0xFFFF

private fun readUInt(buf: ByteBuffer): Long = buf.int.toLong() and 0xFFFFFFFFL

/** ZIP 条目时间戳（DOS 日期/时间）转 epoch 毫秒；非法值返回 0。 */
private fun dosDateTimeToEpochMillis(dosDate: Int, dosTime: Int): Long {
    val year = ((dosDate shr 9) and 0x7f) + 1980
    val month = ((dosDate shr 5) and 0x0f)
    val day = dosDate and 0x1f
    val hour = (dosTime shr 11) and 0x1f
    val minute = (dosTime shr 5) and 0x3f
    val second = (dosTime and 0x1f) * 2
    if (month < 1 || day < 1) return 0L
    return try {
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        cal.timeInMillis
    } catch (_: Exception) {
        0L
    }
}
