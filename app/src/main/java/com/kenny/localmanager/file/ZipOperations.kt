package com.kenny.localmanager.file

import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.utils.MultiReadOnlySeekableByteChannel
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset
import java.util.Locale

private const val TAG = "ZipOperations"
private const val MIME_ZIP = "application/zip"
private const val MIME_7Z = "application/x-7z-compressed"
private const val ARCHIVE_PROGRESS_UNITS = 10_000
private val SEVEN_Z_SPLIT_PART_REGEX = Regex("(?i)^(.*\\.7z)\\.(\\d{3})$")

internal class ArchiveOperationCancelledException(message: String) : InterruptedIOException(message)

private fun throwIfCancelled(shouldCancel: () -> Boolean) {
    if (shouldCancel()) {
        throw ArchiveOperationCancelledException("archive operation cancelled")
    }
}

/**
 * 字节级进度上报器：带节流（避免 64KB chunk 粒度回调过频导致 UI 重组风暴），
 * done 超出 total 时顶格（产物字节数与源不一定相等，进度语义是“接近完成”而非精确计量）。
 */
private class BytesProgressReporter(
    private val onBytes: ((Long, Long) -> Unit)?,
    totalBytes: Long
) {
    var total: Long = totalBytes.coerceAtLeast(0L)
        private set
    private var done = 0L
    private var lastReported = 0L
    private var step: Long = 256L * 1024

    init {
        if (total > 0) step = (total / 400).coerceAtLeast(256L * 1024)
        report()
    }

    fun setTotal(value: Long) {
        total = value.coerceAtLeast(0L)
        if (total > 0) step = (total / 400).coerceAtLeast(256L * 1024)
    }

    fun add(n: Int) {
        if (n > 0 && onBytes != null) {
            done += n
            maybeReport()
        }
    }

    fun add(n: Long) {
        if (n > 0 && onBytes != null) {
            done += n
            maybeReport()
        }
    }

    private fun maybeReport() {
        if (done - lastReported >= step || done >= total) report()
    }

    private fun report() {
        if (onBytes != null && total > 0) onBytes.invoke(done.coerceAtMost(total), total)
        lastReported = done
    }
}

/** SAF 文档树总大小预扫（目录递归，与拷贝阶段遍历成本同量级）。 */
internal fun docTreeSize(context: Context, docUri: Uri, treeUri: Uri?): Long {
    val resolved = resolveDocUriWithTree(docUri, treeUri)
    val doc = DocumentFile.fromSingleUri(context, resolved) ?: return 0L
    if (!doc.isDirectory) return doc.length()
    val dirDoc = getDirectoryViaTree(context, docUri, treeUri) ?: return 0L
    return dirDoc.listFiles().orEmpty().sumOf { docTreeSize(context, it.uri, treeUri) }
}

/** 读入计数流（计压缩器实际读入的字节）。 */
internal class CountingInputStream(input: java.io.InputStream, private val onRead: (Int) -> Unit) : FilterInputStream(input) {
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = `in`.read(b, off, len)
        if (n > 0) onRead(n)
        return n
    }

    override fun read(): Int {
        val n = `in`.read()
        if (n >= 0) onRead(1)
        return n
    }
}

private fun copyStreamCancellable(
    input: java.io.InputStream,
    output: java.io.OutputStream,
    shouldCancel: () -> Boolean,
    bufferSize: Int = 64 * 1024,
    onChunkCopied: (Int) -> Unit = {}
) {
    val buffer = ByteArray(bufferSize)
    while (true) {
        throwIfCancelled(shouldCancel)
        val read = input.read(buffer)
        if (read <= 0) break
        output.write(buffer, 0, read)
        onChunkCopied(read)
    }
}

private class CancelAwareOutputStream(
    out: OutputStream,
    private val shouldCancel: () -> Boolean,
    private val onBytesWritten: (Int) -> Unit = {}
) : FilterOutputStream(out) {
    override fun write(b: Int) {
        throwIfCancelled(shouldCancel)
        out.write(b)
        onBytesWritten(1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        throwIfCancelled(shouldCancel)
        out.write(b, off, len)
        if (len > 0) onBytesWritten(len)
    }
}

private class ArchiveExtractProgressTracker(
    private val totalEntries: Int,
    private val totalBytes: Long,
    private val setProgress: (Int, Int) -> Unit
) {
    private var completedEntries = 0
    private var completedBytes = 0L
    private var lastUnits = -1

    init {
        setProgress(0, ARCHIVE_PROGRESS_UNITS)
    }

    fun onBytes(bytes: Int) {
        if (totalBytes <= 0L || bytes <= 0) return
        completedBytes += bytes.toLong()
        emit()
    }

    fun onEntryDone() {
        completedEntries++
        emit()
    }

    fun finish() {
        if (lastUnits != ARCHIVE_PROGRESS_UNITS) {
            lastUnits = ARCHIVE_PROGRESS_UNITS
            setProgress(ARCHIVE_PROGRESS_UNITS, ARCHIVE_PROGRESS_UNITS)
        }
    }

    private fun emit() {
        val fraction = if (totalBytes > 0L) {
            val safeBytes = completedBytes.coerceAtMost(totalBytes)
            safeBytes.toDouble() / totalBytes.toDouble()
        } else {
            completedEntries.toDouble() / totalEntries.coerceAtLeast(1).toDouble()
        }
        val units = (fraction * ARCHIVE_PROGRESS_UNITS).toInt().coerceIn(0, ARCHIVE_PROGRESS_UNITS)
        if (units != lastUnits) {
            lastUnits = units
            setProgress(units, ARCHIVE_PROGRESS_UNITS)
        }
    }
}

private fun rarEntryUnpackedSize(header: FileHeader): Long {
    return try {
        val value = header.javaClass.methods.firstOrNull {
            it.name == "getFullUnpackSize" && it.parameterCount == 0
        }?.invoke(header)
        when (value) {
            is Long -> value
            is Int -> value.toLong()
            else -> 0L
        }
    } catch (_: Exception) {
        0L
    }
}

/** 解压结果 */
sealed class UnzipResult {
    object Success : UnzipResult()
    object Cancelled : UnzipResult()
    object PasswordRequired : UnzipResult()
    object WrongPassword : UnzipResult()
    object CorruptedFile : UnzipResult()
    object UnsupportedFormat : UnzipResult()
    data class IOError(val message: String?) : UnzipResult()
}

private fun isRarName(name: String?): Boolean =
    name?.endsWith(".rar", ignoreCase = true) == true

private fun is7zName(name: String?): Boolean =
    name?.endsWith(".7z", ignoreCase = true) == true || parseSevenZSplitPart(name) != null

private data class SevenZSplitPart(
    val fullName: String,
    val baseName: String,
    val partIndex: Int
)

private sealed class SevenZSplitPartsResolveResult {
    object NotSplit : SevenZSplitPartsResolveResult()
    data class Ready(val parts: List<Pair<String, Uri>>) : SevenZSplitPartsResolveResult()
    data class Error(val message: String) : SevenZSplitPartsResolveResult()
}

private fun parseSevenZSplitPart(name: String?): SevenZSplitPart? {
    val rawName = name?.trim().orEmpty()
    if (rawName.isEmpty()) return null
    val m = SEVEN_Z_SPLIT_PART_REGEX.matchEntire(rawName) ?: return null
    val baseName = m.groupValues[1]
    val partIndex = m.groupValues[2].toIntOrNull() ?: return null
    return SevenZSplitPart(fullName = rawName, baseName = baseName, partIndex = partIndex)
}

private fun resolveSiblingDirectoryViaTree(context: Context, archiveUri: Uri, treeUri: Uri?): DocumentFile? {
    if (treeUri == null) return null
    val resolvedArchive = resolveDocUriWithTree(archiveUri, treeUri)
    val archiveDocId = try {
        DocumentsContract.getDocumentId(resolvedArchive)
    } catch (_: Exception) {
        return null
    }
    val parentDocId = archiveDocId.substringBeforeLast('/', "")
    if (parentDocId.isBlank()) return null
    val parentUri = try {
        DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
    } catch (_: Exception) {
        return null
    }
    return getDirectoryViaTree(context, parentUri, treeUri)
}

private fun resolveSevenZSplitParts(
    context: Context,
    archiveUri: Uri,
    archiveName: String?,
    treeUri: Uri?
): SevenZSplitPartsResolveResult {
    val normalizedUri = resolveDocUriWithTree(archiveUri, treeUri)
    val selectedDoc = DocumentFile.fromSingleUri(context, normalizedUri)
    val selectedName = archiveName ?: selectedDoc?.name ?: ""
    val selectedPart = parseSevenZSplitPart(selectedName) ?: return SevenZSplitPartsResolveResult.NotSplit

    val parentDir = resolveSiblingDirectoryViaTree(context, archiveUri, treeUri)
        ?: return SevenZSplitPartsResolveResult.Error(
            "7z 分卷自动补全失败：无法读取分卷所在目录（treeUri=${treeUri != null}）。"
        )

    val candidates = mutableMapOf<Int, Pair<String, Uri>>()
    parentDir.listFiles().orEmpty().forEach { child ->
        val childName = child.name ?: return@forEach
        val part = parseSevenZSplitPart(childName) ?: return@forEach
        if (!part.baseName.equals(selectedPart.baseName, ignoreCase = true)) return@forEach
        candidates[part.partIndex] = part.fullName to resolveDocUriWithTree(child.uri, treeUri)
    }

    if (candidates.isEmpty()) {
        return SevenZSplitPartsResolveResult.Error(
            "7z 分卷自动补全失败：目录中未找到同组分卷，目标=$selectedName。"
        )
    }
    if (1 !in candidates) {
        return SevenZSplitPartsResolveResult.Error(
            "7z 分卷不完整：缺少首卷 ${selectedPart.baseName}.001。"
        )
    }

    val maxIndex = candidates.keys.maxOrNull() ?: 1
    val missing = (1..maxIndex).filterNot { it in candidates }
    if (missing.isNotEmpty()) {
        val missingSuffix = missing.joinToString(",") { String.format(Locale.ROOT, "%03d", it) }
        return SevenZSplitPartsResolveResult.Error(
            "7z 分卷不完整：缺少分卷 $missingSuffix（基名 ${selectedPart.baseName}）。"
        )
    }

    val ordered = candidates.toSortedMap().values.toList()
    return SevenZSplitPartsResolveResult.Ready(ordered)
}

private fun isTarName(name: String?): Boolean {
    val lower = name?.lowercase(Locale.ROOT) ?: return false
    return lower.endsWith(".tar", ignoreCase = true) ||
        lower.endsWith(".tar.gz", ignoreCase = true) ||
        lower.endsWith(".tgz", ignoreCase = true) ||
        lower.endsWith(".tar.bz2", ignoreCase = true) ||
        lower.endsWith(".tbz2", ignoreCase = true) ||
        lower.endsWith(".tar.xz", ignoreCase = true) ||
        lower.endsWith(".txz", ignoreCase = true)
}

private enum class SingleCompressedFormat(val suffix: String) {
    GZ(".gz"),
    BZ2(".bz2"),
    XZ(".xz")
}

private fun detectSingleCompressedFormat(name: String?): SingleCompressedFormat? {
    val lower = name?.lowercase(Locale.ROOT) ?: return null
    return when {
        lower.endsWith(".gz") && !lower.endsWith(".tar.gz") && !lower.endsWith(".tgz") -> SingleCompressedFormat.GZ
        lower.endsWith(".bz2") && !lower.endsWith(".tar.bz2") && !lower.endsWith(".tbz2") -> SingleCompressedFormat.BZ2
        lower.endsWith(".xz") && !lower.endsWith(".tar.xz") && !lower.endsWith(".txz") -> SingleCompressedFormat.XZ
        else -> null
    }
}

private fun buildSingleCompressedOutputName(archiveName: String, format: SingleCompressedFormat): String {
    val fallback = "extracted"
    val trimmed = archiveName.trim().ifBlank { fallback }
    val lower = trimmed.lowercase(Locale.ROOT)
    if (!lower.endsWith(format.suffix)) return trimmed
    val output = trimmed.dropLast(format.suffix.length).trimEnd('.')
    return output.ifBlank { fallback }
}

private fun uncompressSingleToParent(
    context: Context,
    compressedFile: File,
    parentDirUri: Uri,
    treeUri: Uri?,
    archiveName: String?,
    format: SingleCompressedFormat,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean
): UnzipResult {
    val displayName = archiveName ?: compressedFile.name
    val outputName = buildSingleCompressedOutputName(displayName, format)
    val writer = try {
        ArchiveDocumentWriter(context, parentDirUri, treeUri)
    } catch (e: Exception) {
        return UnzipResult.IOError("解压失败：${e.message ?: "无法定位目标目录"}")
    }
    setProgress(0, 1)
    val writeResult = writer.writeFile(outputName, shouldCancel) { output ->
        val source = when (format) {
            SingleCompressedFormat.GZ -> GzipCompressorInputStream(compressedFile.inputStream())
            SingleCompressedFormat.BZ2 -> BZip2CompressorInputStream(compressedFile.inputStream())
            SingleCompressedFormat.XZ -> XZCompressorInputStream(compressedFile.inputStream())
        }
        source.use { input ->
            copyStreamCancellable(input, output, shouldCancel)
        }
    }
    if (writeResult != null) return writeResult
    setProgress(1, 1)
    return UnzipResult.Success
}

private fun openTarArchiveInputStream(file: File, archiveName: String?): TarArchiveInputStream {
    val lower = (archiveName ?: file.name).lowercase(Locale.ROOT)
    val raw = file.inputStream()
    val source = when {
        lower.endsWith(".tar.gz", ignoreCase = true) || lower.endsWith(".tgz", ignoreCase = true) -> GzipCompressorInputStream(raw)
        lower.endsWith(".tar.bz2", ignoreCase = true) || lower.endsWith(".tbz2", ignoreCase = true) -> BZip2CompressorInputStream(raw)
        lower.endsWith(".tar.xz", ignoreCase = true) || lower.endsWith(".txz", ignoreCase = true) -> XZCompressorInputStream(raw)
        else -> raw
    }
    return TarArchiveInputStream(source)
}

private fun hasRarSignature(file: File): Boolean {
    if (!file.exists() || file.length() < 7) return false
    return try {
        file.inputStream().use { input ->
            val header = ByteArray(7)
            val read = input.read(header)
            if (read < 7) return false
            // RAR4: 52 61 72 21 1A 07 00 / RAR5: ... 01 00
            header[0] == 0x52.toByte() &&
                header[1] == 0x61.toByte() &&
                header[2] == 0x72.toByte() &&
                header[3] == 0x21.toByte() &&
                header[4] == 0x1A.toByte() &&
                header[5] == 0x07.toByte() &&
                (header[6] == 0x00.toByte() || header[6] == 0x01.toByte())
        }
    } catch (_: Exception) {
        false
    }
}

private fun isRarV5Signature(file: File): Boolean {
    if (!hasRarSignature(file)) return false
    return try {
        file.inputStream().use { input ->
            val header = ByteArray(7)
            val read = input.read(header)
            read >= 7 && header[6] == 0x01.toByte()
        }
    } catch (_: Exception) {
        false
    }
}

/** 是否为 RAR5（当前解压库不支持）。 */
fun isRarV5Archive(context: Context, archiveUri: Uri): Boolean {
    val cache = File(context.cacheDir, "rar_ver_${System.currentTimeMillis()}.rar")
    return try {
        context.contentResolver.openInputStream(archiveUri)?.use { input ->
            cache.outputStream().use { output -> input.copyTo(output) }
        } ?: return false
        isRarV5Signature(cache)
    } catch (_: Exception) {
        false
    } finally {
        cache.delete()
    }
}

/** 用 treeUri 解析文档 URI，使 openInputStream 等有权限。 */
internal fun resolveDocUriWithTree(uri: Uri, treeUri: Uri?): Uri = when {
    treeUri != null -> try {
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getDocumentId(uri))
    } catch (_: Exception) { uri }
    else -> uri
}

/**
 * 从根 tree 遍历到目标目录，得到支持 listFiles() 的 DocumentFile。
 * SingleDocumentFile.listFiles() 会抛 UnsupportedOperationException，必须用树根遍历得到子目录。
 */
private fun getDirectoryViaTree(context: Context, dirUri: Uri, treeUri: Uri?): DocumentFile? {
    if (treeUri == null) return DocumentFile.fromSingleUri(context, dirUri)
    val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val dirId = try { DocumentsContract.getDocumentId(dirUri) } catch (_: Exception) { return null }
    if (!dirId.startsWith(rootId)) return null
    val relative = dirId.removePrefix(rootId).trimStart('/').trimStart(':')
    if (relative.isEmpty()) return root
    val segments = relative.split("/").filter { it.isNotEmpty() }
    var current: DocumentFile = root
    for (segment in segments) {
        current = current.findFile(segment) ?: return null
        if (!current.isDirectory) return null
    }
    return current
}

/**
 * 解压 zip 到父目录（与 zip 同目录）。
 * @param zipUri zip 文件 content URI
 * @param parentDirUri 解压目标目录（通常为 zip 所在目录）
 * @param password 加密 zip 的密码，无密码传 null
 * @param setProgress (current, total) 进度回调，total 为条目数
 * @return 解压结果，包含成功或具体错误原因
 */
fun unzipToParent(
    context: Context,
    zipUri: Uri,
    parentDirUri: Uri,
    treeUri: Uri?,
    archiveName: String?,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean = { false }
): UnzipResult {
    val cr = context.contentResolver
    throwIfCancelled(shouldCancel)

    when (val split = resolveSevenZSplitParts(context, zipUri, archiveName, treeUri)) {
        is SevenZSplitPartsResolveResult.Ready -> {
            return un7zSplitToParent(context, split.parts, parentDirUri, treeUri, password, setProgress, shouldCancel)
        }
        is SevenZSplitPartsResolveResult.Error -> {
            return UnzipResult.IOError(split.message)
        }
        SevenZSplitPartsResolveResult.NotSplit -> Unit
    }

    val cacheDir = File(context.cacheDir, "zip_unzip_${System.currentTimeMillis()}").apply { mkdirs() }
    val zipFile = File(cacheDir, "archive.zip")
    try {
        cr.openInputStream(zipUri)?.use { input ->
            zipFile.outputStream().use { output -> copyStreamCancellable(input, output, shouldCancel) }
        } ?: return UnzipResult.IOError(context.getString(R.string.zip_read_file_failed))
        if (isRarName(archiveName) || hasRarSignature(zipFile)) {
            return unrarToParent(context, zipFile, parentDirUri, treeUri, password, setProgress, shouldCancel)
        }
        if (is7zName(archiveName) || has7zSignature(zipFile)) {
            return un7zToParent(context, zipFile, parentDirUri, treeUri, password, setProgress, shouldCancel)
        }
        if (isTarName(archiveName)) {
            return untarToParent(context, zipFile, parentDirUri, treeUri, archiveName, setProgress, shouldCancel)
        }
        detectSingleCompressedFormat(archiveName)?.let { format ->
            return uncompressSingleToParent(context, zipFile, parentDirUri, treeUri, archiveName, format, setProgress, shouldCancel)
        }

        val zip = ZipFile(zipFile)
        if (zip.isEncrypted) {
            if (password == null || password.isEmpty()) return UnzipResult.PasswordRequired
            zip.setPassword(password)
        }
        val writer = try {
            ArchiveDocumentWriter(context, parentDirUri, treeUri)
        } catch (e: Exception) {
            return UnzipResult.IOError("ZIP 解压失败：${e.message ?: "无法定位目标目录"}")
        }
        val headers = zip.fileHeaders.orEmpty()
        val total = headers.size.coerceAtLeast(1)
        val totalBytes = headers
            .asSequence()
            .filter { !it.isDirectory }
            .map { it.uncompressedSize.coerceAtLeast(0L) }
            .sum()
        val progress = ArchiveExtractProgressTracker(total, totalBytes, setProgress)
        try {
            headers.forEach { header ->
                throwIfCancelled(shouldCancel)
                val path = sanitizeArchivePath(header.fileName.orEmpty())
                if (path.isEmpty()) {
                    progress.onEntryDone()
                    return@forEach
                }
                if (header.isDirectory) {
                    if (!writer.ensureDirectory(path)) {
                        return UnzipResult.IOError("ZIP 解压失败：创建目录失败 [$path]")
                    }
                } else {
                    val writeResult = writer.writeFile(path, shouldCancel) { output ->
                        zip.getInputStream(header).use { input ->
                            copyStreamCancellable(input, output, shouldCancel, onChunkCopied = progress::onBytes)
                        }
                    }
                    if (writeResult != null) return writeResult
                }
                progress.onEntryDone()
            }
        } catch (e: net.lingala.zip4j.exception.ZipException) {
            val msg = e.message.orEmpty().lowercase()
            return when {
                msg.contains("wrong password") || msg.contains("incorrect password") -> UnzipResult.WrongPassword
                msg.contains("password") -> UnzipResult.WrongPassword
                msg.contains("corrupt") || msg.contains("invalid") -> UnzipResult.CorruptedFile
                else -> UnzipResult.IOError(e.message)
            }
        }
        progress.finish()
        return UnzipResult.Success
    } catch (e: Exception) {
        Log.e(TAG, "unzipToParent failed", e)
        return parseUnzipError(e, password)
    } finally {
        zipFile.delete()
        cacheDir.deleteRecursively()
    }
}

private fun has7zSignature(file: File): Boolean {
    if (!file.exists() || file.length() < 6) return false
    return try {
        file.inputStream().use { input ->
            val header = ByteArray(6)
            val read = input.read(header)
            read >= 6 &&
                header[0] == 0x37.toByte() &&
                header[1] == 0x7A.toByte() &&
                header[2] == 0xBC.toByte() &&
                header[3] == 0xAF.toByte() &&
                header[4] == 0x27.toByte() &&
                header[5] == 0x1C.toByte()
        }
    } catch (_: Exception) {
        false
    }
}

private fun openSevenZFile(archiveFile: File, password: CharArray?): SevenZFile {
    return if (password != null && password.isNotEmpty()) {
        SevenZFile(archiveFile, password)
    } else {
        SevenZFile(archiveFile)
    }
}

private fun openSevenZFile(channel: SeekableByteChannel, password: CharArray?): SevenZFile {
    return if (password != null && password.isNotEmpty()) {
        SevenZFile(channel, password)
    } else {
        SevenZFile(channel)
    }
}

private class ArchiveDocumentWriter(
    private val context: Context,
    parentDirUri: Uri,
    treeUri: Uri?
) {
    private val cr = context.contentResolver
    private val rootDirUri: Uri = resolveParentForZip(context, parentDirUri, treeUri)
        ?: throw IllegalStateException("无法定位目标目录")
    private val dirUriCache = mutableMapOf<String, Uri>("" to rootDirUri)
    private val topLevelNameMap = mutableMapOf<String, String>()
    private val reservedTopLevelNames = mutableSetOf<String>()

    private fun splitNameAndExtension(name: String): Pair<String, String> {
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == name.length - 1) return name to ""
        return name.substring(0, dotIndex) to name.substring(dotIndex)
    }

    private fun nextConflictName(originalName: String, index: Int, isDirectory: Boolean): String {
        if (isDirectory) return "$originalName($index)"
        val (base, ext) = splitNameAndExtension(originalName)
        return "$base($index)$ext"
    }

    private fun reserveTopLevelName(originalName: String, isDirectory: Boolean): String {
        topLevelNameMap[originalName]?.let { return it }
        var candidate = originalName
        var suffix = 1
        while (
            candidate in reservedTopLevelNames ||
            findChildByName(context, rootDirUri, candidate) != null
        ) {
            candidate = nextConflictName(originalName, suffix, isDirectory)
            suffix++
        }
        topLevelNameMap[originalName] = candidate
        reservedTopLevelNames += candidate
        return candidate
    }

    private fun remapTopLevelPath(path: String, topLevelIsDirectory: Boolean): String {
        val normalized = sanitizeArchivePath(path)
        if (normalized.isEmpty()) return ""
        val first = normalized.substringBefore('/')
        val rest = normalized.substringAfter('/', "")
        val mappedFirst = reserveTopLevelName(first, topLevelIsDirectory)
        return if (rest.isBlank()) mappedFirst else "$mappedFirst/$rest"
    }

    private fun ensureTargetDir(relativeDir: String): Uri? {
        val normalized = sanitizeArchivePath(relativeDir).trimEnd('/')
        if (normalized.isEmpty()) return rootDirUri
        dirUriCache[normalized]?.let { return it }
        val segments = normalized.split('/').filter { it.isNotBlank() }
        var currentPath = ""
        var currentUri = rootDirUri
        for (segment in segments) {
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            val cached = dirUriCache[currentPath]
            if (cached != null) {
                currentUri = cached
                continue
            }
            val existing = findChildByName(context, currentUri, segment)
            val nextUri = if (existing != null) {
                existing
            } else {
                try {
                    DocumentsContract.createDocument(
                        cr,
                        currentUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        segment
                    )
                } catch (_: Exception) {
                    null
                }
            } ?: return null
            dirUriCache[currentPath] = nextUri
            currentUri = nextUri
        }
        return currentUri
    }

    fun ensureDirectory(path: String): Boolean {
        val mappedPath = remapTopLevelPath(path, topLevelIsDirectory = true)
        return ensureTargetDir(mappedPath) != null
    }

    fun writeFile(path: String, shouldCancel: () -> Boolean, writeBody: (OutputStream) -> Unit): UnzipResult? {
        val mappedPath = remapTopLevelPath(path, topLevelIsDirectory = path.contains('/'))
        val parentPath = mappedPath.substringBeforeLast('/', "")
        val parentUri = ensureTargetDir(parentPath)
            ?: return UnzipResult.IOError("解压失败：创建父目录失败 [$path]")
        val fileName = mappedPath.substringAfterLast('/').ifBlank {
            return UnzipResult.IOError("解压失败：无效文件名 [$path]")
        }
        val fileUri = try {
            DocumentsContract.createDocument(cr, parentUri, "application/octet-stream", fileName)
        } catch (_: Exception) {
            null
        } ?: return UnzipResult.IOError("解压失败：创建目标文件失败 [$path]")

        cr.openOutputStream(fileUri)?.use { output ->
            throwIfCancelled(shouldCancel)
            writeBody(output)
        } ?: return UnzipResult.IOError("解压失败：打开输出流失败 [$path]")
        return null
    }
}

private fun extractSevenZToParent(
    context: Context,
    parentDirUri: Uri,
    treeUri: Uri?,
    openArchive: () -> SevenZFile,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit,
    failedTag: String,
    shouldCancel: () -> Boolean
): UnzipResult {
    val writer = try {
        ArchiveDocumentWriter(context, parentDirUri, treeUri)
    } catch (e: Exception) {
        return UnzipResult.IOError("7z 解压失败：${e.message ?: "无法定位目标目录"}")
    }

    return try {
        openArchive().use { sevenZ ->
            val entries = sevenZ.entries.toList()
            val total = entries.size.coerceAtLeast(1)
            val totalBytes = entries
                .asSequence()
                .filter { !it.isDirectory }
                .map { it.size.coerceAtLeast(0L) }
                .sum()
            val progress = ArchiveExtractProgressTracker(total, totalBytes, setProgress)
            var entry = sevenZ.nextEntry
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (entry != null) {
                throwIfCancelled(shouldCancel)
                val path = sanitizeArchivePath(entry.name.orEmpty())
                if (path.isNotEmpty()) {
                    if (entry.isDirectory) {
                        if (!writer.ensureDirectory(path)) {
                            return UnzipResult.IOError("7z 解压失败：创建目录失败 [$path]")
                        }
                    } else {
                        val writeResult = writer.writeFile(path, shouldCancel) { output ->
                            while (true) {
                                throwIfCancelled(shouldCancel)
                                val read = sevenZ.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                progress.onBytes(read)
                            }
                        }
                        if (writeResult != null) return writeResult
                    }
                }
                progress.onEntryDone()
                entry = sevenZ.nextEntry
            }
            progress.finish()
        }
        UnzipResult.Success
    } catch (e: PasswordRequiredException) {
        Log.w(TAG, "$failedTag: password required")
        if (password == null || password.isEmpty()) UnzipResult.PasswordRequired
        else UnzipResult.WrongPassword
    } catch (e: Throwable) {
        Log.e(TAG, "$failedTag failed", e)
        parseUnzipError(e, password)
    }
}

private fun un7zSplitToParent(
    context: Context,
    splitParts: List<Pair<String, Uri>>,
    parentDirUri: Uri,
    treeUri: Uri?,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean
): UnzipResult {
    val cacheDir = File(context.cacheDir, "7z_split_unzip_${System.currentTimeMillis()}").apply { mkdirs() }
    val localParts = mutableListOf<File>()
    var channel: SeekableByteChannel? = null
    return try {
        splitParts.forEach { (name, uri) ->
            throwIfCancelled(shouldCancel)
            val localPart = File(cacheDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                localPart.outputStream().use { output -> copyStreamCancellable(input, output, shouldCancel) }
            } ?: return UnzipResult.IOError("7z 分卷读取失败：$name")
            localParts += localPart
        }
        channel = MultiReadOnlySeekableByteChannel.forFiles(*localParts.toTypedArray())
        extractSevenZToParent(
            context = context,
            parentDirUri = parentDirUri,
            treeUri = treeUri,
            openArchive = { openSevenZFile(channel!!, password) },
            password = password,
            setProgress = setProgress,
            failedTag = "un7zSplitToParent",
            shouldCancel = shouldCancel
        )
    } catch (e: Exception) {
        Log.e(TAG, "un7zSplitToParent failed", e)
        parseUnzipError(e, password)
    } finally {
        try {
            channel?.close()
        } catch (_: Exception) {
        }
        localParts.forEach { part ->
            if (part.exists()) part.delete()
        }
        cacheDir.deleteRecursively()
    }
}

private fun un7zToParent(
    context: Context,
    archiveFile: File,
    parentDirUri: Uri,
    treeUri: Uri?,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean
): UnzipResult {
    return extractSevenZToParent(
        context = context,
        parentDirUri = parentDirUri,
        treeUri = treeUri,
        openArchive = { openSevenZFile(archiveFile, password) },
        password = password,
        setProgress = setProgress,
        failedTag = "un7zToParent",
        shouldCancel = shouldCancel
    )
}

private fun parseUnzipError(e: Throwable, password: CharArray?): UnzipResult {
    if (e is ArchiveOperationCancelledException) return UnzipResult.Cancelled
    val msg = e.message.orEmpty().lowercase()
    return when {
        msg.contains("password") || msg.contains("encrypted") || msg.contains("decrypt") -> {
            if (password == null || password.isEmpty()) UnzipResult.PasswordRequired
            else UnzipResult.WrongPassword
        }
        msg.contains("corrupt") || msg.contains("invalid") || msg.contains("malformed") ||
        msg.contains("unexpected end") || msg.contains("truncated") || msg.contains("bad signature") -> {
            UnzipResult.CorruptedFile
        }
        msg.contains("unsupported") || msg.contains("unknown method") || msg.contains("not implemented") -> {
            UnzipResult.UnsupportedFormat
        }
        e is java.io.IOException -> UnzipResult.IOError(e.message)
        else -> UnzipResult.IOError(e.message)
    }
}

private fun unrarToParent(
    context: Context,
    rarFile: File,
    parentDirUri: Uri,
    treeUri: Uri?,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean
): UnzipResult {
    var archive: Archive? = null
    val writer = try {
        ArchiveDocumentWriter(context, parentDirUri, treeUri)
    } catch (e: Exception) {
        return UnzipResult.IOError("RAR 解压失败：${e.message ?: "无法定位目标目录"}")
    }
    return try {
        archive = openRarArchive(rarFile, password)
        if (archive == null) {
            return UnzipResult.CorruptedFile
        }
        if (isRarArchivePasswordProtected(archive) && (password == null || password.isEmpty())) {
            return UnzipResult.PasswordRequired
        }

        val headers = archive.fileHeaders.orEmpty()
        val total = headers.size.coerceAtLeast(1)
        val totalBytes = headers
            .asSequence()
            .filter { !it.isDirectory }
            .map { rarEntryUnpackedSize(it).coerceAtLeast(0L) }
            .sum()
        val progress = ArchiveExtractProgressTracker(total, totalBytes, setProgress)
        headers.forEach { header ->
            throwIfCancelled(shouldCancel)
            val path = sanitizeArchivePath(rarEntryName(header))
            if (path.isEmpty()) {
                progress.onEntryDone()
                return@forEach
            }
            if (header.isDirectory) {
                if (!writer.ensureDirectory(path)) {
                    return UnzipResult.IOError("RAR 解压失败：创建目录失败 [$path]")
                }
            } else {
                val writeResult = writer.writeFile(path, shouldCancel) { output ->
                    archive.extractFile(header, CancelAwareOutputStream(output, shouldCancel, progress::onBytes))
                }
                if (writeResult != null) return writeResult
            }
            progress.onEntryDone()
        }
        progress.finish()
        UnzipResult.Success
    } catch (e: Exception) {
        Log.e(TAG, "unrarToParent failed", e)
        parseUnzipError(e, password)
    } finally {
        try {
            archive?.close()
        } catch (_: Exception) {}
    }
}

private fun openRarArchive(rarFile: File, password: CharArray?): Archive? {
    return try {
        if (password != null && password.isNotEmpty()) {
            Archive(rarFile, String(password))
        } else {
            Archive(rarFile)
        }
    } catch (e: Exception) {
        Log.w(TAG, "openRarArchive failed: ${e.message}")
        null
    }
}

private fun rarEntryName(header: FileHeader): String {
    val raw = header.fileName ?: ""
    return raw.replace('\\', '/')
}

private fun sanitizeArchivePath(path: String): String {
    val normalized = path.trim().replace('\\', '/').trimStart('/')
    if (normalized.isEmpty()) return ""
    if (normalized.split('/').any { it == ".." }) return ""
    return normalized
}

private fun isRarArchivePasswordProtected(archive: Archive): Boolean {
    return try {
        archive.isPasswordProtected || archive.isEncrypted || archive.fileHeaders.orEmpty().any { it.isEncrypted }
    } catch (_: Exception) {
        false
    }
}

/**
 * 将本地目录/文件复制到 DocumentFile 父目录下。
 */
private fun copyLocalDirToDocument(
    context: Context,
    local: File,
    parentDocUri: Uri,
    treeUri: Uri?,
    shouldCancel: () -> Boolean,
    onEach: () -> Unit
): Uri? {
    throwIfCancelled(shouldCancel)
    val cr = context.contentResolver
    val parentResolved = resolveParentForZip(context, parentDocUri, treeUri) ?: return null
    return if (local.isDirectory) {
        val newDirUri = try {
            DocumentsContract.createDocument(cr, parentResolved, DocumentsContract.Document.MIME_TYPE_DIR, local.name)
        } catch (_: Exception) { null } ?: return null
        local.listFiles()?.orEmpty()?.forEach { child ->
            throwIfCancelled(shouldCancel)
            copyLocalDirToDocument(context, child, newDirUri, treeUri, shouldCancel, onEach)
            onEach()
        }
        newDirUri
    } else {
        val mime = "application/octet-stream"
        val newUri = try {
            DocumentsContract.createDocument(cr, parentResolved, mime, local.name)
        } catch (_: Exception) { null } ?: return null
        local.inputStream().use { inp ->
            cr.openOutputStream(newUri)?.use { out -> copyStreamCancellable(inp, out, shouldCancel) }
        }
        onEach()
        newUri
    }
}

@Suppress("UNUSED_PARAMETER")
private fun resolveParentForZip(context: Context, targetParentUri: Uri, treeUri: Uri?): Uri? {
    return resolveParentDocumentUriShared(targetParentUri, treeUri)
}

enum class ArchiveCompressionFormat(
    val label: String,
    val extension: String,
    val supportsPassword: Boolean = false,
    val compressesSingleFileOnly: Boolean = false
) {
    ZIP("ZIP", "zip", supportsPassword = true),
    SEVEN_Z("7Z", "7z", supportsPassword = true),
    TAR("tar", "tar"),
    TAR_GZ("tar.gz", "tar.gz"),
    TAR_BZ2("tar.bz2", "tar.bz2"),
    TAR_XZ("tar.xz", "tar.xz"),
    GZ("gz", "gz", compressesSingleFileOnly = true),
    BZ2("bz2", "bz2", compressesSingleFileOnly = true),
    XZ("xz", "xz", compressesSingleFileOnly = true)
}

fun buildArchiveOutputName(baseName: String, format: ArchiveCompressionFormat): String {
    val trimmed = baseName.trim().ifBlank { "archive" }
    val lower = trimmed.lowercase(Locale.ROOT)
    val withoutKnownArchive = when {
        lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> trimmed.substringBeforeLast(".", trimmed).substringBeforeLast(".", trimmed)
        lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> trimmed.substringBeforeLast(".", trimmed).substringBeforeLast(".", trimmed)
        lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> trimmed.substringBeforeLast(".", trimmed).substringBeforeLast(".", trimmed)
        lower.endsWith(".tar") || lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".gz") || lower.endsWith(".bz2") || lower.endsWith(".xz") -> trimmed.substringBeforeLast(".", trimmed)
        else -> trimmed.substringBeforeLast(".", trimmed)
    }
    val cleanBase = withoutKnownArchive.ifBlank { "archive" }
    return when (format) {
        ArchiveCompressionFormat.ZIP -> if (cleanBase.equals(trimmed, ignoreCase = true) && trimmed.endsWith(".zip", ignoreCase = true)) trimmed else "$cleanBase.${format.extension}"
        ArchiveCompressionFormat.SEVEN_Z -> if (cleanBase.equals(trimmed, ignoreCase = true) && trimmed.endsWith(".7z", ignoreCase = true)) trimmed else "$cleanBase.${format.extension}"
        ArchiveCompressionFormat.TAR -> if (trimmed.endsWith(".tar", ignoreCase = true)) trimmed else "$cleanBase.tar"
        ArchiveCompressionFormat.TAR_GZ -> if (trimmed.endsWith(".tar.gz", ignoreCase = true)) trimmed else "$cleanBase.tar.gz"
        ArchiveCompressionFormat.TAR_BZ2 -> if (trimmed.endsWith(".tar.bz2", ignoreCase = true)) trimmed else "$cleanBase.tar.bz2"
        ArchiveCompressionFormat.TAR_XZ -> if (trimmed.endsWith(".tar.xz", ignoreCase = true)) trimmed else "$cleanBase.tar.xz"
        ArchiveCompressionFormat.GZ -> if (trimmed.endsWith(".gz", ignoreCase = true)) trimmed else "$cleanBase.gz"
        ArchiveCompressionFormat.BZ2 -> if (trimmed.endsWith(".bz2", ignoreCase = true)) trimmed else "$cleanBase.bz2"
        ArchiveCompressionFormat.XZ -> if (trimmed.endsWith(".xz", ignoreCase = true)) trimmed else "$cleanBase.xz"
    }
}

fun buildZipParametersForCompression(password: CharArray?): ZipParameters {
    return ZipParameters().apply {
        compressionLevel = CompressionLevel.NORMAL
        if (password != null && password.isNotEmpty()) {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
    }
}

private fun resolveCompressionMimeType(fileName: String): String = when {
    fileName.endsWith(".tar.gz", ignoreCase = true) || fileName.endsWith(".tgz", ignoreCase = true) -> "application/gzip"
    fileName.endsWith(".tar.bz2", ignoreCase = true) || fileName.endsWith(".tbz2", ignoreCase = true) -> "application/x-bzip2"
    fileName.endsWith(".tar.xz", ignoreCase = true) || fileName.endsWith(".txz", ignoreCase = true) -> "application/x-xz"
    fileName.endsWith(".tar", ignoreCase = true) -> "application/x-tar"
    fileName.endsWith(".gz", ignoreCase = true) -> "application/gzip"
    fileName.endsWith(".bz2", ignoreCase = true) -> "application/x-bzip2"
    fileName.endsWith(".xz", ignoreCase = true) -> "application/x-xz"
    fileName.endsWith(".zip", ignoreCase = true) -> MIME_ZIP
    fileName.endsWith(".7z", ignoreCase = true) -> MIME_7Z
    else -> "application/octet-stream"
}

private fun addDocumentEntryToTar(
    tarOut: TarArchiveOutputStream,
    context: Context,
    docUri: Uri,
    treeUri: Uri?,
    entryRelativePath: String,
    nameHint: String?,
    shouldCancel: () -> Boolean,
    reporter: BytesProgressReporter,
    countBytes: Boolean
) {
    throwIfCancelled(shouldCancel)
    val doc = DocumentFile.fromSingleUri(context, resolveDocUriWithTree(docUri, treeUri)) ?: return
    val targetName = nameHint ?: doc.name ?: return
    if (doc.isDirectory) {
        val dirEntry = TarArchiveEntry(entryRelativePath.ifBlank { targetName }.trimStart('/'), true)
        tarOut.putArchiveEntry(dirEntry)
        tarOut.closeArchiveEntry()
        val dirDoc = getDirectoryViaTree(context, docUri, treeUri) ?: return
        dirDoc.listFiles().orEmpty().forEach { child ->
            throwIfCancelled(shouldCancel)
            val childUri = resolveDocUriWithTree(child.uri, treeUri)
            val childPath = if (entryRelativePath.isBlank()) child.name ?: "" else "$entryRelativePath/${child.name ?: ""}"
            addDocumentEntryToTar(tarOut, context, childUri, treeUri, childPath, child.name, shouldCancel, reporter, countBytes)
        }
    } else {
        val normalizedPath = entryRelativePath.ifBlank { targetName }.replace('\\', '/').trimStart('/')
        val fileEntry = TarArchiveEntry(normalizedPath)
        fileEntry.size = doc.length()
        tarOut.putArchiveEntry(fileEntry)
        context.contentResolver.openInputStream(resolveDocUriWithTree(docUri, treeUri))?.use { input ->
            val onChunk: (Int) -> Unit = if (countBytes) reporter::add else { _ -> }
            copyStreamCancellable(input, tarOut, shouldCancel, onChunkCopied = onChunk)
        }
        tarOut.closeArchiveEntry()
    }
}

fun compressSingleFileToFormat(
    context: Context,
    sourceUri: Uri,
    parentDirUri: Uri,
    treeUri: Uri?,
    outputFileName: String,
    format: ArchiveCompressionFormat,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean = { false },
    onBytesProgress: ((Long, Long) -> Unit)? = null
): Boolean {
    val resolvedUri = resolveDocUriWithTree(sourceUri, treeUri)
    val doc = DocumentFile.fromSingleUri(context, resolvedUri) ?: return false
    if (doc.isDirectory) return false
    val parentResolved = resolveParentForZip(context, parentDirUri, treeUri) ?: return false
    val cacheDir = File(context.cacheDir, "single_compress_${System.currentTimeMillis()}_${format.name}").apply { mkdirs() }
    val tempFile = File(cacheDir, outputFileName)
    var outUri: Uri? = null
    try {
        throwIfCancelled(shouldCancel)
        val reporter = BytesProgressReporter(onBytesProgress, docTreeSize(context, sourceUri, treeUri))
        val compressed = when (format) {
            ArchiveCompressionFormat.GZ -> tempFile.apply {
                outputStream().use { out ->
                    GzipCompressorOutputStream(out).use { gz ->
                        context.contentResolver.openInputStream(resolvedUri)?.use { input -> copyStreamCancellable(input, gz, shouldCancel, onChunkCopied = reporter::add) }
                    }
                }
            }
            ArchiveCompressionFormat.BZ2 -> tempFile.apply {
                outputStream().use { out ->
                    BZip2CompressorOutputStream(out).use { bz2 ->
                        context.contentResolver.openInputStream(resolvedUri)?.use { input -> copyStreamCancellable(input, bz2, shouldCancel, onChunkCopied = reporter::add) }
                    }
                }
            }
            ArchiveCompressionFormat.XZ -> tempFile.apply {
                outputStream().use { out ->
                    XZCompressorOutputStream(out).use { xz ->
                        context.contentResolver.openInputStream(resolvedUri)?.use { input -> copyStreamCancellable(input, xz, shouldCancel, onChunkCopied = reporter::add) }
                    }
                }
            }
            else -> return false
        }
        setProgress(1, 1)
        val mimeType = resolveCompressionMimeType(outputFileName)
        outUri = try {
            DocumentsContract.createDocument(context.contentResolver, parentResolved, mimeType, outputFileName)
        } catch (_: Exception) { null } ?: return false
        compressed.inputStream().use { inp ->
            context.contentResolver.openOutputStream(outUri)?.use { out -> copyStreamCancellable(inp, out, shouldCancel) }
        }
        return true
    } catch (e: ArchiveOperationCancelledException) {
        outUri?.let { uri ->
            runCatching { context.contentResolver.deleteDocument(uri) }
        }
        Log.i(TAG, "compressSingleFileToFormat cancelled")
        return false
    } catch (e: Exception) {
        Log.e(TAG, "compressSingleFileToFormat failed", e)
        return false
    } finally {
        tempFile.delete()
        cacheDir.deleteRecursively()
    }
}

fun compressTarArchive(
    context: Context,
    sourceUris: List<Uri>,
    parentDirUri: Uri,
    treeUri: Uri?,
    archiveFileName: String,
    compressionFormat: ArchiveCompressionFormat,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean = { false },
    onBytesProgress: ((Long, Long) -> Unit)? = null
): Boolean {
    if (password != null && password.isNotEmpty()) {
        Log.w(TAG, "compressTarArchive: password ignored for ${compressionFormat.extension}; tar-based formats do not support encryption in this app")
    }
    val cr = context.contentResolver
    val cacheDir = File(context.cacheDir, "tar_compress_${System.currentTimeMillis()}_${compressionFormat.name}").apply { mkdirs() }
    val total = sourceUris.size.coerceAtLeast(1)
    val tarPath = when (compressionFormat) {
        ArchiveCompressionFormat.TAR -> File(cacheDir, archiveFileName)
        ArchiveCompressionFormat.TAR_GZ, ArchiveCompressionFormat.TAR_BZ2, ArchiveCompressionFormat.TAR_XZ -> {
            if (archiveFileName.endsWith(".tar", ignoreCase = true)) {
                File(cacheDir, archiveFileName)
            } else {
                val rawName = archiveFileName.substringBeforeLast(".", archiveFileName)
                File(cacheDir, "$rawName.tar")
            }
        }
        else -> return false
    }
    val outputFile = when (compressionFormat) {
        ArchiveCompressionFormat.TAR -> tarPath
        ArchiveCompressionFormat.TAR_GZ -> File(cacheDir, archiveFileName)
        ArchiveCompressionFormat.TAR_BZ2 -> File(cacheDir, archiveFileName)
        ArchiveCompressionFormat.TAR_XZ -> File(cacheDir, archiveFileName)
        else -> return false
    }
    var outUri: Uri? = null
    try {
        throwIfCancelled(shouldCancel)
        val reporter = BytesProgressReporter(onBytesProgress, sourceUris.sumOf { docTreeSize(context, it, treeUri) })
        // 纯 tar：tar 写入即最终产物，计入 tar 阶段；带二次压缩：tar 阶段不计（每个字节在二次压缩读入时恰好计一次）
        val countTarBytes = compressionFormat == ArchiveCompressionFormat.TAR
        val tarOut = TarArchiveOutputStream(tarPath.outputStream())
        tarOut.use { out ->
            sourceUris.forEachIndexed { index, uri ->
                throwIfCancelled(shouldCancel)
                setProgress(index, total)
                val resolvedUri = resolveDocUriWithTree(uri, treeUri)
                val doc = DocumentFile.fromSingleUri(context, resolvedUri) ?: return false
                val entryName = when {
                    sourceUris.size == 1 -> doc.name ?: "archive"
                    else -> doc.name ?: "${index}"
                }
                addDocumentEntryToTar(out, context, resolvedUri, treeUri, entryName, doc.name, shouldCancel, reporter, countTarBytes)
            }
        }

        when (compressionFormat) {
            ArchiveCompressionFormat.TAR -> {
                // No extra compression step; tarPath is already the final output.
            }
            ArchiveCompressionFormat.TAR_GZ -> {
                GzipCompressorOutputStream(outputFile.outputStream()).use { gzip ->
                    tarPath.inputStream().use { tarIn -> copyStreamCancellable(tarIn, gzip, shouldCancel, onChunkCopied = reporter::add) }
                }
            }
            ArchiveCompressionFormat.TAR_BZ2 -> {
                BZip2CompressorOutputStream(outputFile.outputStream()).use { bz2 ->
                    tarPath.inputStream().use { tarIn -> copyStreamCancellable(tarIn, bz2, shouldCancel, onChunkCopied = reporter::add) }
                }
            }
            ArchiveCompressionFormat.TAR_XZ -> {
                XZCompressorOutputStream(outputFile.outputStream()).use { xz ->
                    tarPath.inputStream().use { tarIn -> copyStreamCancellable(tarIn, xz, shouldCancel, onChunkCopied = reporter::add) }
                }
            }
            else -> return false
        }

        val parentResolved = resolveParentForZip(context, parentDirUri, treeUri) ?: return false
        outUri = try {
            DocumentsContract.createDocument(cr, parentResolved, resolveCompressionMimeType(archiveFileName), archiveFileName)
        } catch (_: Exception) { null } ?: return false
        outputFile.inputStream().use { inp ->
            cr.openOutputStream(outUri)?.use { out -> copyStreamCancellable(inp, out, shouldCancel) }
        }
        setProgress(total, total)
        return true
    } catch (e: ArchiveOperationCancelledException) {
        outUri?.let { uri ->
            runCatching { context.contentResolver.deleteDocument(uri) }
        }
        Log.i(TAG, "compressTarArchive cancelled")
        return false
    } catch (e: Exception) {
        Log.e(TAG, "compressTarArchive failed", e)
        return false
    } finally {
        tarPath.delete()
        outputFile.delete()
        cacheDir.deleteRecursively()
    }
}

/**
 * 将若干文件/目录压缩为一个 zip，保存到指定目录。
 * @param sourceUris 要压缩的项（文件或目录）的 content URI
 * @param parentDirUri zip 输出所在目录
 * @param zipFileName 生成的 zip 文件名（含 .zip）
 * @param password 加密密码，无密码传 null
 * @param setProgress (current, total) 当前项/总项数
 * @param onBytesProgress 可选字节级进度（done, total），全程覆盖拷贝与压缩阶段
 * @return 成功 true
 */
fun compressToZip(
    context: Context,
    sourceUris: List<Uri>,
    parentDirUri: Uri,
    treeUri: Uri?,
    zipFileName: String,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean = { false },
    onBytesProgress: ((Long, Long) -> Unit)? = null
): Boolean {
    val cr = context.contentResolver
    val cacheDir = File(context.cacheDir, "zip_compress_${System.currentTimeMillis()}").apply { mkdirs() }
    val zipPath = File(cacheDir, zipFileName)
    var outUri: Uri? = null
    try {
        throwIfCancelled(shouldCancel)
        val reporter = BytesProgressReporter(onBytesProgress, sourceUris.sumOf { docTreeSize(context, it, treeUri) })
        val total = sourceUris.size
        val zip = ZipFile(zipPath).apply {
            if (password != null && password.isNotEmpty()) setPassword(password)
        }
        sourceUris.forEachIndexed { index, uri ->
            throwIfCancelled(shouldCancel)
            setProgress(index, total)
            val resolvedUri = resolveDocUriWithTree(uri, treeUri)
            val doc = DocumentFile.fromSingleUri(context, resolvedUri)
            if (doc == null) {
                Log.e(TAG, "compressToZip: DocumentFile.fromSingleUri returned null for $resolvedUri")
                return false
            }
            val name = doc.name
            if (name.isNullOrBlank()) {
                Log.e(TAG, "compressToZip: doc.name is null/blank for $resolvedUri")
                return false
            }
            val localEntry = File(cacheDir, name)
            if (doc.isDirectory) {
                localEntry.mkdirs()
                copyDocumentToLocal(context, resolvedUri, localEntry, treeUri, shouldCancel)
                addLocalDirToZipStream(zip, localEntry, name, password, reporter, shouldCancel)
                localEntry.deleteRecursively()
            } else {
                cr.openInputStream(resolvedUri)?.use { input ->
                    localEntry.outputStream().use { output -> copyStreamCancellable(input, output, shouldCancel) }
                } ?: run {
                    Log.e(TAG, "compressToZip: openInputStream failed for $resolvedUri")
                    return false
                }
                addLocalFileToZipStream(zip, localEntry, name, password, reporter, shouldCancel)
                localEntry.delete()
            }
        }
        setProgress(total, total)
        if (zip.fileHeaders.isEmpty()) {
            Log.e(TAG, "compressToZip: no entries added, skip creating output file")
            return false
        }
        val parentResolved = resolveParentForZip(context, parentDirUri, treeUri)
        if (parentResolved == null) {
            Log.e(TAG, "compressToZip: resolveParentForZip returned null for $parentDirUri")
            return false
        }
        outUri = try {
            DocumentsContract.createDocument(cr, parentResolved, MIME_ZIP, zipFileName)
        } catch (e: Exception) {
            Log.e(TAG, "compressToZip: createDocument failed", e)
            null
        } ?: return false
        zipPath.inputStream().use { inp ->
            cr.openOutputStream(outUri)?.use { out -> copyStreamCancellable(inp, out, shouldCancel) }
        }
        return true
    } catch (e: ArchiveOperationCancelledException) {
        outUri?.let { uri ->
            runCatching { context.contentResolver.deleteDocument(uri) }
        }
        Log.i(TAG, "compressToZip cancelled")
        return false
    } catch (e: Exception) {
        Log.e(TAG, "compressToZip failed", e)
        return false
    } finally {
        zipPath.delete()
        cacheDir.deleteRecursively()
    }
}

/** 本地文件流式写入 zip（字节计数穿透压缩阶段）。 */
private fun addLocalFileToZipStream(
    zip: ZipFile,
    local: File,
    entryName: String,
    password: CharArray?,
    reporter: BytesProgressReporter,
    shouldCancel: () -> Boolean
) {
    throwIfCancelled(shouldCancel)
    val params = buildZipParametersForCompression(password).apply {
        fileNameInZip = entryName
        entrySize = local.length()
    }
    local.inputStream().use { input ->
        zip.addStream(CountingInputStream(input, reporter::add), params)
    }
}

/** 本地目录流式写入 zip：目录条目 STORE 空流，子项递归（探针实证 addStream 支持）。 */
private fun addLocalDirToZipStream(
    zip: ZipFile,
    dir: File,
    entryPrefix: String,
    password: CharArray?,
    reporter: BytesProgressReporter,
    shouldCancel: () -> Boolean
) {
    throwIfCancelled(shouldCancel)
    val dirParams = buildZipParametersForCompression(null).apply {
        fileNameInZip = "$entryPrefix/"
        entrySize = 0L
        compressionMethod = CompressionMethod.STORE
    }
    zip.addStream(ByteArrayInputStream(ByteArray(0)), dirParams)
    dir.listFiles().orEmpty().sortedBy { it.name.lowercase() }.forEach { child ->
        val childName = "$entryPrefix/${child.name}"
        if (child.isDirectory) {
            addLocalDirToZipStream(zip, child, childName, password, reporter, shouldCancel)
        } else {
            addLocalFileToZipStream(zip, child, childName, password, reporter, shouldCancel)
        }
    }
}

/** 将若干文件/目录压缩为 7z，保存到指定目录。 */
fun compressTo7z(
    context: Context,
    sourceUris: List<Uri>,
    parentDirUri: Uri,
    treeUri: Uri?,
    sevenZFileName: String,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean = { false },
    onBytesProgress: ((Long, Long) -> Unit)? = null
): Boolean {
    val cr = context.contentResolver
    val cacheDir = File(context.cacheDir, "7z_compress_${System.currentTimeMillis()}").apply { mkdirs() }
    val sevenZPath = File(cacheDir, sevenZFileName)
    var outUri: Uri? = null
    try {
        throwIfCancelled(shouldCancel)
        val reporter = BytesProgressReporter(onBytesProgress, sourceUris.sumOf { docTreeSize(context, it, treeUri) })
        val total = sourceUris.size.coerceAtLeast(1)
        val out = if (password != null && password.isNotEmpty()) {
            SevenZOutputFile(sevenZPath, password)
        } else {
            SevenZOutputFile(sevenZPath)
        }
        out.use { sevenZOut ->
            sourceUris.forEachIndexed { index, uri ->
                throwIfCancelled(shouldCancel)
                setProgress(index, total)
                val resolvedUri = resolveDocUriWithTree(uri, treeUri)
                val doc = DocumentFile.fromSingleUri(context, resolvedUri) ?: return false
                val name = doc.name ?: return false
                val localEntry = File(cacheDir, name)
                if (doc.isDirectory) {
                    localEntry.mkdirs()
                    copyDocumentToLocal(context, resolvedUri, localEntry, treeUri, shouldCancel)
                    addLocalTo7z(sevenZOut, localEntry, name, shouldCancel, reporter)
                    localEntry.deleteRecursively()
                } else {
                    cr.openInputStream(resolvedUri)?.use { input ->
                        localEntry.outputStream().use { output -> copyStreamCancellable(input, output, shouldCancel) }
                    } ?: return false
                    addLocalTo7z(sevenZOut, localEntry, name, shouldCancel, reporter)
                    localEntry.delete()
                }
            }
            setProgress(total, total)
            sevenZOut.finish()
        }

        if (!sevenZPath.exists() || sevenZPath.length() <= 0L) return false
        val parentResolved = resolveParentForZip(context, parentDirUri, treeUri) ?: return false
        outUri = try {
            DocumentsContract.createDocument(cr, parentResolved, MIME_7Z, sevenZFileName)
        } catch (_: Exception) {
            null
        } ?: return false
        sevenZPath.inputStream().use { inp ->
            cr.openOutputStream(outUri)?.use { out -> copyStreamCancellable(inp, out, shouldCancel) }
        }
        return true
    } catch (e: ArchiveOperationCancelledException) {
        outUri?.let { uri ->
            runCatching { context.contentResolver.deleteDocument(uri) }
        }
        Log.i(TAG, "compressTo7z cancelled")
        return false
    } catch (e: Throwable) {
        Log.e(TAG, "compressTo7z failed", e)
        return false
    } finally {
        sevenZPath.delete()
        cacheDir.deleteRecursively()
    }
}

private fun addLocalTo7z(out: SevenZOutputFile, local: File, entryName: String, shouldCancel: () -> Boolean, reporter: BytesProgressReporter) {
    throwIfCancelled(shouldCancel)
    val normalized = entryName.replace('\\', '/')
    if (local.isDirectory) {
        val dirEntryName = if (normalized.endsWith('/')) normalized else "$normalized/"
        val dirEntry: SevenZArchiveEntry = out.createArchiveEntry(local, dirEntryName)
        out.putArchiveEntry(dirEntry)
        out.closeArchiveEntry()
        local.listFiles().orEmpty().sortedBy { it.name.lowercase() }.forEach { child ->
            throwIfCancelled(shouldCancel)
            val childName = "$normalized/${child.name}"
            addLocalTo7z(out, child, childName, shouldCancel, reporter)
        }
    } else {
        val fileEntry: SevenZArchiveEntry = out.createArchiveEntry(local, normalized)
        out.putArchiveEntry(fileEntry)
        try {
            local.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    throwIfCancelled(shouldCancel)
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    reporter.add(read)
                }
            }
        } finally {
            out.closeArchiveEntry()
        }
    }
}

/** 将 DocumentFile（目录）内容递归复制到已存在的本地目录 localDir。 */
private fun copyDocumentToLocal(context: Context, docUri: Uri, localDir: File, treeUri: Uri?) {
    copyDocumentToLocal(context, docUri, localDir, treeUri, shouldCancel = { false })
}

internal fun copyDocumentToLocal(
    context: Context,
    docUri: Uri,
    localDir: File,
    treeUri: Uri?,
    shouldCancel: () -> Boolean,
    onChunkCopied: (Int) -> Unit = {}
) {
    throwIfCancelled(shouldCancel)
    // 必须用树遍历得到的 DocumentFile，否则 SingleDocumentFile.listFiles() 会抛 UnsupportedOperationException
    val doc = getDirectoryViaTree(context, docUri, treeUri) ?: return
    if (!doc.isDirectory) return
    doc.listFiles().orEmpty().forEach { child ->
        throwIfCancelled(shouldCancel)
        val name = child.name ?: return@forEach
        val childUri = resolveDocUriWithTree(child.uri, treeUri)
        if (child.isDirectory) {
            File(localDir, name).apply { mkdirs() }.let { copyDocumentToLocal(context, childUri, it, treeUri, shouldCancel, onChunkCopied) }
        } else {
            context.contentResolver.openInputStream(childUri)?.use { input ->
                File(localDir, name).outputStream().use { output -> copyStreamCancellable(input, output, shouldCancel, onChunkCopied = onChunkCopied) }
            }
        }
    }
}

/** 检测 zip 是否加密（需先打开）。 */
fun isZipEncrypted(context: Context, zipUri: Uri): Boolean {
    return isArchiveEncrypted(context, zipUri)
}

/** 检测归档（zip/rar）是否加密。 */
fun isArchiveEncrypted(context: Context, archiveUri: Uri, archiveName: String? = null): Boolean {
    // 快路径：fd 可 seek 时仅读尾部 central directory 判断加密（零复制、与大包大小无关）。
    // 解析失败（非 zip / 不可 seek / 损坏）回退旧的全量复制路径。
    readZipCentralDirectoryFromUri(context, archiveUri)?.let { cd ->
        return cd.any { it.isEncrypted }
    }
    val cache = File(context.cacheDir, "zip_check_${System.currentTimeMillis()}.zip")
    return try {
        context.contentResolver.openInputStream(archiveUri)?.use { input ->
            cache.outputStream().use { output -> input.copyTo(output) }
        } ?: return false
        if (isRarName(archiveName) || hasRarSignature(cache)) {
            isRarEncrypted(cache)
        } else if (is7zName(archiveName) || has7zSignature(cache)) {
            is7zEncrypted(cache)
        } else {
            ZipFile(cache).isEncrypted
        }
    } catch (_: Exception) { false }
    finally { cache.delete() }
}

private fun is7zEncrypted(archiveFile: File): Boolean {
    return try {
        SevenZFile(archiveFile).use { sevenZ ->
            // 触发读取首个条目，部分场景下会在这里抛密码异常。
            sevenZ.nextEntry
            false
        }
    } catch (e: PasswordRequiredException) {
        true
    } catch (e: Throwable) {
        val msg = e.message.orEmpty()
        msg.contains("password", ignoreCase = true) ||
            msg.contains("encrypted", ignoreCase = true) ||
            msg.contains("decrypt", ignoreCase = true)
    }
}

private fun isRarEncrypted(rarFile: File): Boolean {
    var archive: Archive? = null
    return try {
        archive = Archive(rarFile)
        isRarArchivePasswordProtected(archive)
    } catch (e: Exception) {
        // 某些加密 RAR 在无密码打开时直接抛异常。
        val msg = e.message.orEmpty()
        msg.contains("password", ignoreCase = true) ||
            msg.contains("encrypted", ignoreCase = true) ||
            msg.contains("decrypt", ignoreCase = true)
    } finally {
        try {
            archive?.close()
        } catch (_: Exception) {}
    }
}

/** 获取 zip 第一层目录/文件列表时的结果。 */
sealed class ZipFirstLevelResult {
    /** 成功，entries 为第一层名称列表（已排序）。 */
    data class Ok(val entries: List<String>) : ZipFirstLevelResult()
    /** 顶层仅有一个目录（针对该目录压缩），rootDirName 为目录名，children 为该目录下第一层子项（已排序）。 */
    data class OkSingleDir(val rootDirName: String, val children: List<String>) : ZipFirstLevelResult()
    /** 加密 zip，需提供密码后再请求。 */
    object Encrypted : ZipFirstLevelResult()
    /** 读取失败或非 zip。 */
    object Error : ZipFirstLevelResult()
}

/**
 * 读取 zip 第一层内容列表（仅目录/文件名，不解压）。
 * 若顶层只有一个目录，则视为“针对该目录压缩”，返回该目录名及其第一层子项。
 * @param password 加密 zip 的密码，无密码传 null；为 Encrypted 时可带密码重试。
 */
fun getZipFirstLevelEntries(context: Context, zipUri: Uri, password: CharArray?): ZipFirstLevelResult {
    return getArchiveFirstLevelEntries(context, zipUri, null, password)
}

/** 由条目名列表聚合第一层清单（含单根目录展开语义）。 */
internal fun buildZipFirstLevel(names: List<String>): ZipFirstLevelResult {
    val firstLevel = mutableSetOf<String>()
    val firstLevelDirs = mutableSetOf<String>()
    for (path in names) {
        val top = path.substringBefore("/").ifEmpty { path }
        if (top.isNotEmpty()) {
            firstLevel.add(top)
            if (path.startsWith("$top/")) firstLevelDirs.add(top)
        }
    }
    if (firstLevel.size == 1) {
        val rootName = firstLevel.single()
        val prefix = "$rootName/"
        val hasChildrenUnderRoot = names.any { it.startsWith(prefix) }
        if (hasChildrenUnderRoot) {
            val childToDir = mutableMapOf<String, Boolean>()
            for (path in names) {
                if (!path.startsWith(prefix)) continue
                val rest = path.removePrefix(prefix)
                val childName = rest.substringBefore("/")
                if (childName.isEmpty()) continue
                val isDir = rest == "$childName/" || rest.startsWith("$childName/")
                childToDir[childName] = childToDir[childName] == true || isDir
            }
            val children = childToDir.entries.sortedBy { it.key }.map { (name, isDir) -> if (isDir) "$name/" else name }
            return ZipFirstLevelResult.OkSingleDir(rootName, children)
        }
    }
    return ZipFirstLevelResult.Ok(firstLevel.sorted().map { if (it in firstLevelDirs) "$it/" else it })
}

/** 读取归档第一层内容（zip/rar/7z）。 */
fun getArchiveFirstLevelEntries(
    context: Context,
    archiveUri: Uri,
    archiveName: String?,
    password: CharArray?
): ZipFirstLevelResult {
    // 快路径：fd 读尾部目录解析 zip 第一层（零复制、与大包大小无关）。
    // zip 的条目名在 central directory 中为明文，无需密码即可列出（与 ZIP 编辑页一致）；
    // rar/7z（含头加密）解析不到 EOCD 会返回 null，自然落入下方旧路径。
    readZipCentralDirectoryFromUri(context, archiveUri)?.let { cd ->
        return buildZipFirstLevel(cd.map { it.name })
    }
    val cache = File(context.cacheDir, "zip_list_${System.currentTimeMillis()}.zip")
    return try {
        context.contentResolver.openInputStream(archiveUri)?.use { input ->
            cache.outputStream().use { output -> input.copyTo(output) }
        } ?: return ZipFirstLevelResult.Error
        if (isRarName(archiveName) || hasRarSignature(cache)) {
            return getRarFirstLevelEntries(cache, password)
        }
        if (is7zName(archiveName) || has7zSignature(cache)) {
            return get7zFirstLevelEntries(cache, password)
        }
        if (isTarName(archiveName)) {
            return getTarFirstLevelEntries(cache, archiveName)
        }
        val zip = ZipFile(cache)
        if (zip.isEncrypted && password != null && password.isNotEmpty()) {
            zip.setPassword(password)
        }
        // 回退路径也不拦截加密：fileHeaders 读的是明文目录，免密可列
        return buildZipFirstLevel(zip.fileHeaders.map { it.fileName })
    } catch (_: Exception) {
        ZipFirstLevelResult.Error
    } finally {
        cache.delete()
    }
}

private fun getTarFirstLevelEntries(archiveFile: File, archiveName: String?): ZipFirstLevelResult {
    return try {
        val entries = mutableListOf<Pair<String, Boolean>>()
        openTarArchiveInputStream(archiveFile, archiveName).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val path = sanitizeArchivePath(entry.name.orEmpty())
                if (path.isNotEmpty()) {
                    entries += path to entry.isDirectory
                }
                entry = tar.nextEntry
            }
        }
        if (entries.isEmpty()) return ZipFirstLevelResult.Ok(emptyList())

        val firstLevel = mutableSetOf<String>()
        val firstLevelDirs = mutableSetOf<String>()
        entries.forEach { (path, isDir) ->
            val top = path.substringBefore("/")
            if (top.isNotEmpty()) {
                firstLevel.add(top)
                if (isDir || path.contains('/')) {
                    firstLevelDirs.add(top)
                }
            }
        }

        val sortedFirst = firstLevel.sorted().map { if (it in firstLevelDirs) "$it/" else it }
        if (firstLevel.size == 1) {
            val rootName = firstLevel.single()
            val prefix = "$rootName/"
            val childToDir = mutableMapOf<String, Boolean>()
            entries.forEach { (path, isDir) ->
                if (!path.startsWith(prefix)) return@forEach
                val rest = path.removePrefix(prefix)
                val childName = rest.substringBefore("/")
                if (childName.isEmpty()) return@forEach
                val dirChild = isDir || rest.startsWith("$childName/")
                childToDir[childName] = childToDir[childName] == true || dirChild
            }
            if (childToDir.isNotEmpty()) {
                val children = childToDir.entries
                    .sortedBy { it.key }
                    .map { (name, dir) -> if (dir) "$name/" else name }
                return ZipFirstLevelResult.OkSingleDir(rootName, children)
            }
        }
        ZipFirstLevelResult.Ok(sortedFirst)
    } catch (e: Exception) {
        ZipFirstLevelResult.Error
    }
}

private fun untarToParent(
    context: Context,
    archiveFile: File,
    parentDirUri: Uri,
    treeUri: Uri?,
    archiveName: String?,
    setProgress: (Int, Int) -> Unit,
    shouldCancel: () -> Boolean
): UnzipResult {
    val writer = try {
        ArchiveDocumentWriter(context, parentDirUri, treeUri)
    } catch (e: Exception) {
        return UnzipResult.IOError("TAR 解压失败：${e.message ?: "无法定位目标目录"}")
    }
    return try {
        val entries = mutableListOf<TarArchiveEntry>()
        openTarArchiveInputStream(archiveFile, archiveName).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                entries += entry
                entry = tar.nextEntry
            }
        }
        val total = entries.size.coerceAtLeast(1)
        val totalBytes = entries
            .asSequence()
            .filter { !it.isDirectory }
            .map { it.size.coerceAtLeast(0L) }
            .sum()
        val progress = ArchiveExtractProgressTracker(total, totalBytes, setProgress)

        openTarArchiveInputStream(archiveFile, archiveName).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                throwIfCancelled(shouldCancel)
                val path = sanitizeArchivePath(entry.name.orEmpty())
                if (path.isEmpty()) {
                    progress.onEntryDone()
                    entry = tar.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    if (!writer.ensureDirectory(path)) {
                        return UnzipResult.IOError("TAR 解压失败：创建目录失败 [$path]")
                    }
                } else {
                    val writeResult = writer.writeFile(path, shouldCancel) { output ->
                        copyStreamCancellable(tar, output, shouldCancel, onChunkCopied = progress::onBytes)
                    }
                    if (writeResult != null) return writeResult
                }
                progress.onEntryDone()
                entry = tar.nextEntry
            }
        }
        progress.finish()
        UnzipResult.Success
    } catch (e: Exception) {
        Log.e(TAG, "untarToParent failed", e)
        parseUnzipError(e, null)
    }
}

private fun get7zFirstLevelEntries(archiveFile: File, password: CharArray?): ZipFirstLevelResult {
    return try {
        openSevenZFile(archiveFile, password).use { sevenZ ->
            val firstLevel = mutableSetOf<String>()
            val firstLevelDirs = mutableSetOf<String>()
            for (entry in sevenZ.entries) {
                val path = sanitizeArchivePath(entry.name.orEmpty())
                if (path.isEmpty()) continue
                val top = path.substringBefore("/")
                if (top.isNotEmpty()) {
                    firstLevel.add(top)
                    if (entry.isDirectory || path.contains('/')) {
                        firstLevelDirs.add(top)
                    }
                }
            }
            val sortedFirst = firstLevel.sorted().map { if (it in firstLevelDirs) "$it/" else it }
            if (firstLevel.size == 1) {
                val rootName = firstLevel.single()
                val prefix = "$rootName/"
                val childToDir = mutableMapOf<String, Boolean>()
                for (entry in sevenZ.entries) {
                    val path = sanitizeArchivePath(entry.name.orEmpty())
                    if (!path.startsWith(prefix)) continue
                    val rest = path.removePrefix(prefix)
                    val childName = rest.substringBefore("/")
                    if (childName.isEmpty()) continue
                    val isDir = entry.isDirectory || rest.startsWith("$childName/")
                    childToDir[childName] = childToDir[childName] == true || isDir
                }
                if (childToDir.isNotEmpty()) {
                    val children = childToDir.entries
                        .sortedBy { it.key }
                        .map { (name, isDir) -> if (isDir) "$name/" else name }
                    return ZipFirstLevelResult.OkSingleDir(rootName, children)
                }
            }
            ZipFirstLevelResult.Ok(sortedFirst)
        }
    } catch (e: PasswordRequiredException) {
        ZipFirstLevelResult.Encrypted
    } catch (e: Throwable) {
        val msg = e.message.orEmpty()
        if (msg.contains("password", ignoreCase = true)) ZipFirstLevelResult.Encrypted
        else ZipFirstLevelResult.Error
    }
}

private fun getRarFirstLevelEntries(rarFile: File, password: CharArray?): ZipFirstLevelResult {
    var archive: Archive? = null
    return try {
        archive = openRarArchive(rarFile, password)
        if (archive == null) return ZipFirstLevelResult.Error
        if (isRarArchivePasswordProtected(archive) && (password == null || password.isEmpty())) {
            return ZipFirstLevelResult.Encrypted
        }

        val firstLevel = mutableSetOf<String>()
        val firstLevelDirs = mutableSetOf<String>()
        archive.fileHeaders.orEmpty().forEach { header ->
            val path = sanitizeArchivePath(rarEntryName(header))
            if (path.isEmpty()) return@forEach
            val top = path.substringBefore("/")
            if (top.isNotEmpty()) {
                firstLevel.add(top)
                if (header.isDirectory || path.contains("/")) {
                    firstLevelDirs.add(top)
                }
            }
        }

        val sortedFirst = firstLevel.sorted().map { if (it in firstLevelDirs) "$it/" else it }
        if (firstLevel.size == 1) {
            val rootName = firstLevel.single()
            val prefix = "$rootName/"
            val childToDir = mutableMapOf<String, Boolean>()
            archive.fileHeaders.orEmpty().forEach { header ->
                val path = sanitizeArchivePath(rarEntryName(header))
                if (!path.startsWith(prefix)) return@forEach
                val rest = path.removePrefix(prefix)
                val childName = rest.substringBefore("/")
                if (childName.isEmpty()) return@forEach
                val isDir = header.isDirectory || rest.startsWith("$childName/")
                childToDir[childName] = childToDir[childName] == true || isDir
            }
            if (childToDir.isNotEmpty()) {
                val children = childToDir.entries
                    .sortedBy { it.key }
                    .map { (name, isDir) -> if (isDir) "$name/" else name }
                return ZipFirstLevelResult.OkSingleDir(rootName, children)
            }
        }
        ZipFirstLevelResult.Ok(sortedFirst)
    } catch (e: Exception) {
        if (e.message?.contains("password", ignoreCase = true) == true) {
            ZipFirstLevelResult.Encrypted
        } else {
            ZipFirstLevelResult.Error
        }
    } finally {
        try {
            archive?.close()
        } catch (_: Exception) {}
    }
}

// ---- .md.zip 压缩 Markdown 查看 ----

/** 解压 .md.zip 到缓存目录后的结果。 */
data class MdZipExtractResult(
    val cacheDir: File,
    val contentDir: File,
    val targetFile: File?,  // null 表示未找到可渲染的 md/rst 文件
    val isEncrypted: Boolean
)

/** 获取 .md.zip 的缓存目录，基于 URI 哈希。 */
fun getMdZipCacheDir(context: Context, zipUri: Uri): File {
    val key = zipUri.toString().hashCode().toUInt().toString(16)
    return File(context.cacheDir, "md_zip_cache/$key")
}

/** 读取缓存时间戳（毫秒），无缓存返回 0。 */
fun getMdZipCacheTimestamp(cacheDir: File): Long {
    val tsFile = File(cacheDir, ".cache_ts")
    return if (tsFile.exists()) tsFile.readText().trim().toLongOrNull() ?: 0L else 0L
}

/** 在已有缓存目录中查找（或生成）可渲染的 md/rst 文件。
 * @param isRstZip true 表示来源为 .rst.zip，优先查找 rst；false 表示 .md.zip，优先查找 md。 */
fun findMdZipCacheTarget(context: Context, cacheDir: File, isRstZip: Boolean): File? {
    val contentDir = File(cacheDir, "content")
    if (!contentDir.exists() || !contentDir.isDirectory) return null
    return findOrCreateRenderableFile(context, contentDir, isRstZip)
}

/** 根据 zip 类型确定搜索候选文件名。 */
private fun candidatesForZipType(isRstZip: Boolean): List<String> =
    if (isRstZip) listOf("index.rst", "README.rst") else listOf("index.md", "README.md")

/**
 * 在目录树中查找 index/README 文件。若未找到，自动在有效目录中生成一个包含
 * 同类文件链接列表的 index 文件。
 */
private fun findOrCreateRenderableFile(context: Context, dir: File, isRstZip: Boolean): File? {
    val effectiveDir = unwrapSingleChildDir(dir)
    // 1. 查找已有的 index / README
    for (name in candidatesForZipType(isRstZip)) {
        findFileByName(effectiveDir, name)?.let { return it }
    }
    // 2. 收集同类文件
    val ext = if (isRstZip) "rst" else "md"
    val files = collectFilesWithExtension(effectiveDir, ext)
    if (files.isEmpty()) return null
    // 3. 生成 index
    val indexFile = File(effectiveDir, "index.$ext")
    indexFile.writeText(generateIndexContent(context, files, effectiveDir, isRstZip))
    return indexFile
}

/** 递归收集指定扩展名的文件，返回相对于 baseDir 的路径列表（排序后）。 */
private fun collectFilesWithExtension(baseDir: File, ext: String): List<Pair<String, File>> {
    val result = mutableListOf<Pair<String, File>>()
    fun walk(dir: File, prefix: String) {
        dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { child ->
            val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            if (child.isDirectory) {
                walk(child, rel)
            } else if (child.name.endsWith(".$ext", ignoreCase = true)) {
                result.add(rel to child)
            }
        }
    }
    walk(baseDir, "")
    return result
}

private data class IndexedChapterEntry(
    val relativePath: String,
    val sourceFile: File,
    val title: String
)

private fun normalizeArchiveRelativePath(rawPath: String): String {
    return rawPath
        .trim()
        .removePrefix("\uFEFF")
        .replace('\\', '/')
        .removePrefix("./")
}

private fun parseIndexedChapterLine(context: Context, rawLine: String, lineNumber: Int): Pair<String, String?>? {
    val line = rawLine.trim().removePrefix("\uFEFF")
    if (line.isBlank()) return null
    if (line.first() == '"' || line.first() == '\'') {
        val quote = line.first()
        val closingIndex = line.indexOf(quote, startIndex = 1)
        if (closingIndex < 0) {
            throw IllegalStateException(context.getString(R.string.zip_index_line_unclosed_quote, lineNumber))
        }
        val path = line.substring(1, closingIndex)
        val title = line.substring(closingIndex + 1).trim().ifBlank { null }
        return path to title
    }
    val firstWhitespace = line.indexOfFirst { it.isWhitespace() }
    if (firstWhitespace < 0) return line to null
    val path = line.substring(0, firstWhitespace)
    val title = line.substring(firstWhitespace + 1).trim().ifBlank { null }
    return path to title
}

private fun resolveIndexedChapterFile(relativePath: String, roots: List<File>): File? {
    val normalized = normalizeArchiveRelativePath(relativePath)
    if (normalized.isBlank() || normalized.startsWith("/") || normalized.split('/').any { it == ".." }) {
        return null
    }
    roots.forEach { root ->
        val candidate = File(root, normalized)
        if (candidate.isFile) return candidate
    }
    return null
}

private fun loadIndexedChapterFiles(context: Context, indexFile: File, roots: List<File>): List<IndexedChapterEntry> {
    val lines = indexFile.readLines()
    val chapters = mutableListOf<IndexedChapterEntry>()
    lines.forEachIndexed { index, rawLine ->
        val lineNumber = index + 1
        val parsed = parseIndexedChapterLine(context, rawLine, lineNumber) ?: return@forEachIndexed
        val normalized = normalizeArchiveRelativePath(parsed.first)
        if (!normalized.endsWith(".txt", ignoreCase = true) && !normalized.endsWith(".llm", ignoreCase = true)) {
            throw IllegalStateException(context.getString(R.string.zip_index_line_unsupported_chapter, lineNumber, normalized))
        }
        val sourceFile = resolveIndexedChapterFile(normalized, roots)
            ?: throw IllegalStateException(
                context.getString(
                    R.string.zip_index_line_chapter_missing,
                    lineNumber,
                    normalized,
                    roots.joinToString { it.absolutePath }
                )
            )
        chapters += IndexedChapterEntry(
            relativePath = normalized,
            sourceFile = sourceFile,
            title = parsed.second ?: normalized
        )
    }
    if (chapters.isEmpty()) {
        throw IllegalStateException(context.getString(R.string.zip_index_no_entries, indexFile.absolutePath))
    }
    return chapters
}

private fun toDefaultIndexedChapterEntries(files: List<Pair<String, File>>): List<IndexedChapterEntry> {
    return files.map { (relativePath, sourceFile) ->
        IndexedChapterEntry(
            relativePath = relativePath,
            sourceFile = sourceFile,
            title = relativePath.substringBeforeLast('.').ifBlank { sourceFile.nameWithoutExtension }
        )
    }
}

/** 生成 index 文件内容，包含所有同类文件的链接。 */
@Suppress("UNUSED_PARAMETER")
private fun generateIndexContent(
    context: Context,
    files: List<Pair<String, File>>,
    baseDir: File,
    isRstZip: Boolean
): String {
    val sb = StringBuilder()
    if (isRstZip) {
        sb.appendLine(context.getString(R.string.zip_generated_index_title_rst))
        sb.appendLine("====")
        sb.appendLine()
        for ((rel, _) in files) {
            val label = rel.substringBeforeLast('.').replace("/", " / ")
            sb.appendLine("- `$label <$rel>`_")
        }
    } else {
        sb.appendLine(context.getString(R.string.zip_generated_index_heading_md, context.getString(R.string.zip_generated_index_title_md)))
        sb.appendLine()
        for ((rel, _) in files) {
            val label = rel.substringBeforeLast('.').replace("/", " / ")
            sb.appendLine("- [$label]($rel)")
        }
    }
    return sb.toString()
}

/** 递归查找指定名称文件（先查直接子项，再查子目录）。 */
private fun findFileByName(dir: File, name: String): File? {
    dir.listFiles()?.forEach { child ->
        if (!child.isDirectory && child.name.equals(name, ignoreCase = true)) return child
    }
    dir.listFiles()?.forEach { child ->
        if (child.isDirectory) findFileByName(child, name)?.let { return it }
    }
    return null
}

/**
 * 解压 .md.zip / .rst.zip 到缓存目录，查找（或生成）可渲染的文件。
 * @param zipFileName 原始 zip 文件名，用于判断是 .md.zip 还是 .rst.zip
 * @return 解压结果（targetFile 可能为 null 表示压缩包为空），解压失败返回 null
 */
fun extractMdZipToCache(
    context: Context,
    zipUri: Uri,
    password: CharArray?,
    zipFileName: String
): MdZipExtractResult? {
    val isRstZip = zipFileName.endsWith(".rst.zip", ignoreCase = true)
    val cacheDir = getMdZipCacheDir(context, zipUri)
    cacheDir.deleteRecursively()
    cacheDir.mkdirs()
    val tmpZip = File(cacheDir, "__archive.zip")
    try {
        context.contentResolver.openInputStream(zipUri)?.use { input ->
            tmpZip.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        val zip = ZipFile(tmpZip)
        val encrypted = zip.isEncrypted
        if (encrypted) {
            if (password == null || password.isEmpty()) return null
            zip.setPassword(password)
        }
        val contentDir = File(cacheDir, "content").apply { mkdirs() }
        zip.extractAll(contentDir.path)
        tmpZip.delete()
        val target = findOrCreateRenderableFile(context, contentDir, isRstZip)
        if (target == null) {
            Log.w(TAG, "extractMdZipToCache: no files found in $zipUri")
        }
        File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
        if (encrypted) File(cacheDir, ".encrypted").createNewFile()
        return MdZipExtractResult(cacheDir, contentDir, target, encrypted)
    } catch (e: Exception) {
        Log.e(TAG, "extractMdZipToCache failed", e)
        cacheDir.deleteRecursively()
        return null
    } finally {
        if (tmpZip.exists()) tmpZip.delete()
    }
}

/** 缓存是否标记为加密来源。 */
fun isMdZipCacheEncrypted(cacheDir: File): Boolean = File(cacheDir, ".encrypted").exists()

/** 清理 .md.zip 缓存。 */
fun cleanMdZipCache(context: Context, zipUri: Uri) {
    getMdZipCacheDir(context, zipUri).deleteRecursively()
}

/** 递归列出目录中的所有文件（相对路径），用于显示目录内容。
 *  如果顶层只有一个子目录，则跳入该子目录显示其内容。 */
fun listMdZipContentFiles(contentDir: File): List<String> {
    val effectiveDir = unwrapSingleChildDir(contentDir)
    val result = mutableListOf<String>()
    fun walk(dir: File, prefix: String) {
        dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { child ->
            val relativePath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            if (child.isDirectory) {
                result.add("$relativePath/")
                walk(child, relativePath)
            } else {
                result.add(relativePath)
            }
        }
    }
    walk(effectiveDir, "")
    return result
}

/** 如果目录中只有一个子目录（无其他文件），则递归展开返回该子目录。 */
private fun unwrapSingleChildDir(dir: File): File {
    val children = dir.listFiles() ?: return dir
    if (children.size == 1 && children[0].isDirectory) {
        return unwrapSingleChildDir(children[0])
    }
    return dir
}

// ---- .html.zip 压缩 HTML 查看 ----

/** 解压 .html.zip 到缓存目录后的结果。 */
data class HtmlZipExtractResult(
    val cacheDir: File,
    val contentDir: File,
    val indexFile: File?,  // null 表示未找到 index.html
    val isEncrypted: Boolean
)

/** .html.zip 解析结果：成功或失败（带详细信息）。 */
sealed class HtmlZipParseResult {
    data class Success(val result: HtmlZipExtractResult) : HtmlZipParseResult()
    data class Error(val message: String, val detail: String? = null) : HtmlZipParseResult()
}

/** 获取 .html.zip 的缓存目录，基于 URI 哈希。 */
fun getHtmlZipCacheDir(context: Context, zipUri: Uri): File {
    val key = zipUri.toString().hashCode().toUInt().toString(16)
    return File(context.cacheDir, "html_zip_cache/$key")
}

/** 读取 .html.zip 缓存时间戳（毫秒），无缓存返回 0。 */
fun getHtmlZipCacheTimestamp(cacheDir: File): Long {
    val tsFile = File(cacheDir, ".cache_ts")
    return if (tsFile.exists()) tsFile.readText().trim().toLongOrNull() ?: 0L else 0L
}

/** 在已有缓存目录中查找入口 HTML 文件（index.html、README.html 或第一个 .html）。 */
fun findHtmlZipIndexFile(contentDir: File): File? {
    if (!contentDir.exists() || !contentDir.isDirectory) return null
    val effectiveDir = unwrapSingleChildDir(contentDir)
    for (name in listOf("index.html", "README.html", "Index.html", "readme.html")) {
        findFileByName(effectiveDir, name)?.let { return it }
    }
    val firstHtml = collectFilesWithExtension(effectiveDir, "html").firstOrNull()?.second
    return firstHtml
}

/** 缓存是否标记为加密来源（.html.zip）。 */
fun isHtmlZipCacheEncrypted(cacheDir: File): Boolean = File(cacheDir, ".encrypted").exists()

/** 解压 .html.zip 到缓存目录，查找 index.html（简化版，保持兼容）。
 * @return 解压结果（indexFile 可能为 null），解压失败返回 null */
fun extractHtmlZipToCache(
    context: Context,
    zipUri: Uri,
    password: CharArray?,
    zipFileName: String
): HtmlZipExtractResult? {
    val result = extractHtmlZipToCacheWithProgress(context, zipUri, password, zipFileName) {}
    return when (result) {
        is HtmlZipParseResult.Success -> result.result
        is HtmlZipParseResult.Error -> null
    }
}

/**
 * 解压 .html.zip 到缓存目录，带进度回调和详细错误信息。
 * @param onLog 进度日志回调
 * @return 解析结果（成功或失败）
 */
fun extractHtmlZipToCacheWithProgress(
    context: Context,
    zipUri: Uri,
    password: CharArray?,
    zipFileName: String,
    onLog: (String) -> Unit = {}
): HtmlZipParseResult {
    val cacheDir = getHtmlZipCacheDir(context, zipUri)
    cacheDir.deleteRecursively()
    cacheDir.mkdirs()
    val tmpZip = File(cacheDir, "__archive.zip")

    try {
        onLog(context.getString(R.string.zip_log_start_read_file, zipFileName))

        // 复制文件到临时位置
        val inputStream = context.contentResolver.openInputStream(zipUri)
        if (inputStream == null) {
            return HtmlZipParseResult.Error(
                context.getString(R.string.zip_error_open_file),
                context.getString(R.string.zip_error_input_stream_null, zipUri.toString())
            )
        }

        inputStream.use { input ->
            tmpZip.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    total += read
                    if (total % (1024 * 1024) < 8192) {  // 每MB更新一次
                        onLog(context.getString(R.string.zip_log_copied_mb, total / 1024 / 1024))
                    }
                }
            }
        }

        if (!tmpZip.exists() || tmpZip.length() == 0L) {
            return HtmlZipParseResult.Error(
                context.getString(R.string.zip_error_copy_failed),
                context.getString(R.string.zip_error_temp_missing, tmpZip.path)
            )
        }
        onLog(context.getString(R.string.zip_log_file_read_complete, tmpZip.length() / 1024 / 1024))

        onLog(context.getString(R.string.zip_log_check_archive))
        val zip = ZipFile(tmpZip)
        val encrypted = zip.isEncrypted
        onLog(context.getString(R.string.zip_log_check_archive_done, encrypted.toString()))

        if (encrypted) {
            if (password == null || password.isEmpty()) {
                return HtmlZipParseResult.Error(
                    context.getString(R.string.zip_error_password_required_title),
                    context.getString(R.string.zip_error_html_password_required)
                )
            }
            zip.setPassword(password)
        }

        onLog(context.getString(R.string.zip_log_extracting))
        val contentDir = File(cacheDir, "content").apply { mkdirs() }
        try {
            zip.extractAll(contentDir.path)
        } catch (e: net.lingala.zip4j.exception.ZipException) {
            val msg = e.message ?: context.getString(R.string.zip_unknown_zip_exception)
            return HtmlZipParseResult.Error(
                context.getString(R.string.zip_error_extract_failed),
                context.getString(R.string.zip_error_zip_exception, msg)
            )
        }
        tmpZip.delete()
        onLog(context.getString(R.string.zip_log_extract_complete))

        onLog(context.getString(R.string.zip_log_find_entry))
        val indexFile = findHtmlZipIndexFile(contentDir)
        if (indexFile == null) {
            val files = contentDir.listFiles()?.take(20)?.map { it.name } ?: emptyList()
            return HtmlZipParseResult.Error(
                context.getString(R.string.zip_error_entry_not_found),
                context.getString(R.string.zip_error_html_entry_missing, files.joinToString(", "))
            )
        }
        onLog(context.getString(R.string.zip_log_entry_file, indexFile.name))

        File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
        if (encrypted) File(cacheDir, ".encrypted").createNewFile()

        onLog(context.getString(R.string.zip_log_done))
        return HtmlZipParseResult.Success(HtmlZipExtractResult(cacheDir, contentDir, indexFile, encrypted))

    } catch (e: Exception) {
        Log.e(TAG, "extractHtmlZipToCache failed", e)
        cacheDir.deleteRecursively()
        return HtmlZipParseResult.Error(
            "${e.javaClass.simpleName}: ${e.message}",
            e.stackTraceToString().take(500)
        )
    } finally {
        if (tmpZip.exists()) tmpZip.delete()
    }
}

/** 清理 .html.zip 缓存。 */
fun cleanHtmlZipCache(context: Context, zipUri: Uri) {
    getHtmlZipCacheDir(context, zipUri).deleteRecursively()
}

/** 递归列出 .html.zip 内容目录中的所有文件（相对路径）。 */
fun listHtmlZipContentFiles(contentDir: File): List<String> = listMdZipContentFiles(contentDir)

// ---- .pic.zip 图片压缩包查看 ----

private val PIC_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")

/** .pic.zip 解压结果：缓存目录、内容目录、zip 内图片路径列表（已排序）、是否加密。 */
data class PicZipExtractResult(
    val cacheDir: File,
    val contentDir: File,
    val imagePaths: List<String>,
    val isEncrypted: Boolean
)

fun getPicZipCacheDir(context: Context, zipUri: Uri): File {
    val key = zipUri.toString().hashCode().toUInt().toString(16)
    return File(context.cacheDir, "pic_zip_cache/$key")
}

fun getPicZipCacheTimestamp(cacheDir: File): Long {
    val tsFile = File(cacheDir, ".cache_ts")
    return if (tsFile.exists()) tsFile.readText().trim().toLongOrNull() ?: 0L else 0L
}

fun isPicZipCacheEncrypted(cacheDir: File): Boolean = File(cacheDir, ".encrypted").exists()

/**
 * 解压 .pic.zip：仅建立目录结构并解压前 10 张图到 content。
 * @return 解压结果（imagePaths 可为空），解压失败返回 null
 */
@Suppress("UNUSED_PARAMETER")
fun extractPicZipToCache(
    context: Context,
    zipUri: Uri,
    password: CharArray?,
    zipFileName: String,
    initialCount: Int = 10
): PicZipExtractResult? {
    val cacheDir = getPicZipCacheDir(context, zipUri)
    cacheDir.deleteRecursively()
    cacheDir.mkdirs()
    val tmpZip = File(cacheDir, "__archive.zip")
    try {
        context.contentResolver.openInputStream(zipUri)?.use { input ->
            tmpZip.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        val zip = ZipFile(tmpZip)
        val encrypted = zip.isEncrypted
        if (encrypted) {
            if (password == null || password.isEmpty()) return null
            zip.setPassword(password)
        }
        val imagePaths = zip.fileHeaders.asSequence()
            .filter { !it.isDirectory }
            .map { it.fileName }
            .filter { path ->
                val ext = path.substringAfterLast('.', "").lowercase()
                ext in PIC_IMAGE_EXTENSIONS
            }
            .sorted()
            .toList()
        val contentDir = File(cacheDir, "content").apply { mkdirs() }
        val toExtract = imagePaths.take(initialCount)
        for (path in toExtract) {
            val header = zip.getFileHeader(path) ?: continue
            zip.extractFile(header, contentDir.path)
        }
        File(cacheDir, ".image_list").writeText(imagePaths.joinToString("\n"))
        File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
        if (encrypted) File(cacheDir, ".encrypted").createNewFile()
        return PicZipExtractResult(cacheDir, contentDir, imagePaths, encrypted)
    } catch (e: Exception) {
        Log.e(TAG, "extractPicZipToCache failed", e)
        cacheDir.deleteRecursively()
        return null
    }
}

/**
 * 将 .pic.zip 中指定下标范围的图片解压到 content 目录（用于按需加载前后 10 张）。
 * @param password 加密 zip 需传密码，非加密传 null
 */
@Suppress("UNUSED_PARAMETER")
fun extractPicZipImageRange(
    context: Context,
    cacheDir: File,
    contentDir: File,
    imagePaths: List<String>,
    fromIndex: Int,
    toIndex: Int,
    password: CharArray?
): Boolean {
    if (fromIndex >= toIndex || fromIndex < 0 || toIndex > imagePaths.size) return true
    val zipFile = File(cacheDir, "__archive.zip")
    if (!zipFile.exists()) return false
    return try {
        val zip = ZipFile(zipFile)
        if (zip.isEncrypted && password != null && password.isNotEmpty()) zip.setPassword(password)
        for (i in fromIndex until toIndex) {
            val path = imagePaths[i]
            val destFile = File(contentDir, path)
            if (destFile.exists()) continue
            val header = zip.getFileHeader(path) ?: continue
            zip.extractFile(header, contentDir.path)
        }
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * 验证加密 .pic.zip 的密码是否正确（用于复用已有缓存时校验）。
 * @return 密码正确且能读取到第一个图片条目时返回 true
 */
fun tryPicZipPassword(context: Context, zipUri: Uri, password: CharArray?): Boolean {
    if (password == null || password.isEmpty()) return false
    val cacheDir = getPicZipCacheDir(context, zipUri)
    val tmpZip = File(cacheDir, "__archive.zip")
    if (!tmpZip.exists()) {
        val fallback = java.io.File(context.cacheDir, "pic_zip_verify_${System.currentTimeMillis()}.zip")
        try {
            context.contentResolver.openInputStream(zipUri)?.use { fallback.outputStream().use { o -> it.copyTo(o) } }
                ?: return false
            return tryZipPassword(fallback, password)
        } finally {
            fallback.delete()
        }
    }
    return tryZipPassword(tmpZip, password)
}

fun tryArchivePassword(
    context: Context,
    zipUri: Uri,
    password: CharArray?,
    cachedArchive: File? = null
): Boolean {
    if (password == null || password.isEmpty()) return false
    val archive = cachedArchive?.takeIf { it.exists() }
    if (archive != null) return tryArchivePasswordFile(archive, password)

    val fallback = File(context.cacheDir, "archive_verify_${System.currentTimeMillis()}")
    return try {
        context.contentResolver.openInputStream(zipUri)?.use { input ->
            fallback.outputStream().use { output -> input.copyTo(output) }
        } ?: false
        tryArchivePasswordFile(fallback, password)
    } finally {
        fallback.delete()
    }
}

internal fun tryArchivePasswordFile(archiveFile: File, password: CharArray): Boolean {
    return when {
        isRarName(archiveFile.name) || hasRarSignature(archiveFile) -> tryRarPassword(archiveFile, password)
        is7zName(archiveFile.name) || has7zSignature(archiveFile) -> trySevenZPassword(archiveFile, password)
        else -> tryZipPassword(archiveFile, password)
    }
}

private fun trySevenZPassword(archiveFile: File, password: CharArray): Boolean {
    return try {
        openSevenZFile(archiveFile, password).use { sevenZ ->
            sevenZ.nextEntry ?: return true
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (sevenZ.read(buffer) > 0) {
            }
            true
        }
    } catch (e: PasswordRequiredException) {
        false
    } catch (_: Exception) {
        false
    }
}

private fun tryRarPassword(archiveFile: File, password: CharArray): Boolean {
    var archive: Archive? = null
    return try {
        archive = openRarArchive(archiveFile, password)
        if (archive == null) return false
        if (isRarArchivePasswordProtected(archive) && (password.isEmpty())) return false
        val headers = archive.fileHeaders.orEmpty()
        if (headers.isEmpty()) return true
        val firstHeader = headers.firstOrNull() ?: return true
        val output = java.io.ByteArrayOutputStream()
        archive.extractFile(firstHeader, output)
        true
    } catch (e: Exception) {
        false
    } finally {
        try {
            archive?.close()
        } catch (_: Exception) {
        }
    }
}

private fun tryZipPassword(zipFile: java.io.File, password: CharArray): Boolean {
    return try {
        val zip = ZipFile(zipFile)
        if (!zip.isEncrypted) return true
        zip.setPassword(password)
        val header = zip.fileHeaders.firstOrNull { !it.isDirectory } ?: return false
        zip.getInputStream(header).use { it.read() }
        true
    } catch (_: Exception) {
        false
    }
}

fun cleanPicZipCache(context: Context, zipUri: Uri) {
    getPicZipCacheDir(context, zipUri).deleteRecursively()
}

// ---- .vid.zip 视频压缩包查看 ----

private val VID_VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "m4v", "webm", "3gp", "3g2", "rmvb", "rm", "m4a", "mp3"
)

internal fun isVideoArchiveEntry(path: String): Boolean {
    val fileName = path.substringAfterLast('/').ifBlank { path }
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in VID_VIDEO_EXTENSIONS
}

data class VidZipExtractResult(
    val cacheDir: File,
    val contentDir: File,
    val videoPaths: List<String>,
    val isEncrypted: Boolean
)

fun getVidZipCacheDir(context: Context, zipUri: Uri): File {
    val key = zipUri.toString().hashCode().toUInt().toString(16)
    return File(context.cacheDir, "vid_zip_cache/$key")
}

/** 后台复制整包缓存（.vid.zip / .pic.zip 的 __archive.zip），避免阻塞目录列表展示。 */
private val archiveCacheCopyExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
    Thread(r, "archive-cache-copy").apply { isDaemon = true }
}

/**
 * 后台把整包复制到缓存目录的 __archive.zip（供后续 zip4j 按需解压）。
 * 先写 .part 再 rename，半成品不会被读到；失败静默（播放时会有同步回退）。
 */
private fun startArchiveCacheCopyAsync(context: Context, zipUri: Uri, cacheDir: File) {
    archiveCacheCopyExecutor.execute {
        try {
            val target = File(cacheDir, "__archive.zip")
            if (target.exists()) return@execute
            val part = File(cacheDir, "__archive.zip.part")
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                part.outputStream().use { input.copyTo(it) }
            } ?: return@execute
            if (!part.renameTo(target)) part.delete()
        } catch (_: Exception) {
            // 半成品/失败：保留旧状态，播放路径会同步回退复制
        }
    }
}

fun readVidZipVideoPaths(
    context: Context,
    zipUri: Uri,
    password: CharArray?,
    zipFileName: String
): List<String>? {
    // 快路径（非加密）：fd 读尾部目录瞬时出列表，整包缓存转后台复制。
    if (password == null || password.isEmpty()) {
        val cd = readZipCentralDirectoryFromUri(context, zipUri)
        if (cd != null && cd.none { it.isEncrypted }) {
            val paths = cd.asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter(::isVideoArchiveEntry)
                .sorted()
                .toList()
            if (paths.isNotEmpty()) {
                val cacheDir = getVidZipCacheDir(context, zipUri)
                cacheDir.mkdirs()
                File(cacheDir, "content").mkdirs()
                File(cacheDir, ".video_list").writeText(paths.joinToString("\n"))
                File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
                File(cacheDir, ".encrypted").delete()
                startArchiveCacheCopyAsync(context, zipUri, cacheDir)
            }
            return paths
        }
    }
    val cacheDir = getVidZipCacheDir(context, zipUri)
    cacheDir.mkdirs()
    val archiveFile = File(cacheDir, "__archive.zip")
    val tmpZip = if (archiveFile.exists()) archiveFile else {
        val tmp = File(context.cacheDir, "vid_zip_scan_${System.currentTimeMillis()}.zip")
        try {
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            tmp
        } catch (_: Exception) {
            tmp.delete()
            return null
        }
    }
    return try {
        val zip = ZipFile(tmpZip)
        val encrypted = zip.isEncrypted
        if (encrypted) {
            if (password == null || password.isEmpty()) return null
            zip.setPassword(password)
            if (!tryZipPassword(tmpZip, password)) return null
        }
        val paths = zip.fileHeaders.asSequence()
            .filter { !it.isDirectory }
            .map { it.fileName }
            .filter(::isVideoArchiveEntry)
            .sorted()
            .toList()
        if (paths.isNotEmpty()) {
            val listFile = File(cacheDir, ".video_list")
            if (tmpZip != archiveFile) {
                tmpZip.copyTo(archiveFile, overwrite = true)
            }
            listFile.writeText(paths.joinToString("\n"))
            File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
            if (encrypted) File(cacheDir, ".encrypted").createNewFile() else File(cacheDir, ".encrypted").delete()
        }
        paths
    } catch (_: Exception) {
        null
    } finally {
        if (tmpZip != archiveFile) tmpZip.delete()
    }
}

fun getVidZipCacheTimestamp(cacheDir: File): Long {
    val tsFile = File(cacheDir, ".cache_ts")
    return if (tsFile.exists()) tsFile.readText().trim().toLongOrNull() ?: 0L else 0L
}

fun getVidZipEntrySize(context: Context, zipUri: Uri, entryPath: String, password: CharArray?): Long {
    val cacheDir = getVidZipCacheDir(context, zipUri)
    val archiveFile = File(cacheDir, "__archive.zip")
    val zipFile = if (archiveFile.exists()) archiveFile else {
        // 同步回退（后台缓存复制未完成时触发）：复制后落盘缓存，下次直接复用
        val part = File(cacheDir, "__archive.zip.part")
        try {
            cacheDir.mkdirs()
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                part.outputStream().use { output -> input.copyTo(output) }
            }
            if (part.exists() && !part.renameTo(archiveFile)) part.delete()
            if (archiveFile.exists()) archiveFile else null
        } catch (_: Exception) {
            part.delete()
            null
        }
    }
    if (zipFile == null) return 0L
    return try {
        val zip = ZipFile(zipFile)
        if (zip.isEncrypted) {
            if (password == null || password.isEmpty()) return 0L
            zip.setPassword(password)
        }
        val header = zip.getFileHeader(entryPath) ?: return 0L
        header.uncompressedSize
    } catch (_: Exception) {
        0L
    }
}

fun isVidZipCacheEncrypted(cacheDir: File): Boolean = File(cacheDir, ".encrypted").exists()

@Suppress("UNUSED_PARAMETER")
fun extractVidZipToCache(
    context: Context,
    zipUri: Uri,
    password: CharArray?,
    zipFileName: String,
    initialCount: Int = 1
): VidZipExtractResult? {
    val cacheDir = getVidZipCacheDir(context, zipUri)
    cacheDir.deleteRecursively()
    cacheDir.mkdirs()
    val tmpZip = File(cacheDir, "__archive.zip")
    try {
        context.contentResolver.openInputStream(zipUri)?.use { input ->
            tmpZip.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        val zip = ZipFile(tmpZip)
        val encrypted = zip.isEncrypted
        if (encrypted) {
            if (password == null || password.isEmpty()) return null
            zip.setPassword(password)
            if (!tryZipPassword(tmpZip, password)) return null
        }
        val videoPaths = zip.fileHeaders.asSequence()
            .filter { !it.isDirectory }
            .map { it.fileName }
            .filter(::isVideoArchiveEntry)
            .sorted()
            .toList()
        val contentDir = File(cacheDir, "content").apply { mkdirs() }
        val toExtract = videoPaths.take(initialCount)
        for (path in toExtract) {
            val header = zip.getFileHeader(path) ?: continue
            zip.extractFile(header, contentDir.path)
        }
        File(cacheDir, ".video_list").writeText(videoPaths.joinToString("\n"))
        File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
        if (encrypted) File(cacheDir, ".encrypted").createNewFile()
        return VidZipExtractResult(cacheDir, contentDir, videoPaths, encrypted)
    } catch (e: Exception) {
        Log.e(TAG, "extractVidZipToCache failed", e)
        cacheDir.deleteRecursively()
        return null
    }
}

@Suppress("UNUSED_PARAMETER")
fun extractVidZipVideoRange(
    context: Context,
    cacheDir: File,
    contentDir: File,
    videoPaths: List<String>,
    fromIndex: Int,
    toIndex: Int,
    password: CharArray?,
    zipUri: Uri? = null,
    onProgress: ((Long, Long) -> Unit)? = null
): Boolean {
    if (fromIndex >= toIndex || fromIndex < 0 || toIndex > videoPaths.size) return true
    val zipFile = File(cacheDir, "__archive.zip")
    if (!zipFile.exists() && zipUri != null) {
        try {
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                zipFile.parentFile?.mkdirs()
                zipFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
        } catch (_: Exception) {
            return false
        }
    }
    if (!zipFile.exists()) return false
    return try {
        val zip = ZipFile(zipFile)
        if (zip.isEncrypted && password != null && password.isNotEmpty()) zip.setPassword(password)
        for (i in fromIndex until toIndex) {
            val path = videoPaths[i]
            val destFile = File(contentDir, path)
            if (destFile.exists()) continue
            val header = zip.getFileHeader(path) ?: continue
            val totalBytes = header.uncompressedSize
            if (totalBytes <= 0L) {
                zip.extractFile(header, contentDir.path)
                continue
            }
            destFile.parentFile?.mkdirs()
            val inputStream = zip.getInputStream(header)
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress?.invoke(copied, totalBytes)
                    }
                }
            }
        }
        true
    } catch (_: Exception) {
        false
    }
}

fun tryVidZipPassword(context: Context, zipUri: Uri, password: CharArray?): Boolean {
    if (password == null || password.isEmpty()) return false
    val cacheDir = getVidZipCacheDir(context, zipUri)
    val tmpZip = File(cacheDir, "__archive.zip")
    if (!tmpZip.exists()) {
        val fallback = java.io.File(context.cacheDir, "vid_zip_verify_${System.currentTimeMillis()}.zip")
        try {
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                fallback.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            return tryZipPassword(fallback, password)
        } finally {
            fallback.delete()
        }
    }
    return tryZipPassword(tmpZip, password)
}

fun cleanVidZipCache(context: Context, zipUri: Uri) {
    getVidZipCacheDir(context, zipUri).deleteRecursively()
}

// ---- EPUB 电子书查看 ----

/** EPUB 章节信息 */
data class EpubChapter(
    val id: String,
    val href: String,
    val title: String? = null  // 从NCX/NAV获取的标题
)

/** EPUB 书籍信息 */
data class EpubBookInfo(
    val title: String,
    val author: String?,
    val language: String?
)

/** EPUB 解压结果 */
data class EpubExtractResult(
    val cacheDir: File,
    val contentDir: File,
    val opfDir: File,          // OPF文件所在目录（用于解析相对路径）
    val bookInfo: EpubBookInfo,
    val chapters: List<EpubChapter>,
    val isEncrypted: Boolean
)

/** EPUB 解析结果（包含错误信息） */
sealed class EpubParseResult {
    data class Success(val result: EpubExtractResult) : EpubParseResult()
    data class Error(val message: String, val detail: String? = null) : EpubParseResult()
}

/** 获取 EPUB 的缓存目录，基于 URI 哈希。 */
fun getEpubCacheDir(context: Context, epubUri: Uri): File {
    val key = epubUri.toString().hashCode().toUInt().toString(16)
    return File(context.cacheDir, "epub_cache/$key")
}

/** 读取 EPUB 缓存时间戳（毫秒），无缓存返回 0。 */
fun getEpubCacheTimestamp(cacheDir: File): Long {
    val tsFile = File(cacheDir, ".cache_ts")
    return if (tsFile.exists()) tsFile.readText().trim().toLongOrNull() ?: 0L else 0L
}

/** 缓存是否标记为加密来源（EPUB）。 */
fun isEpubCacheEncrypted(cacheDir: File): Boolean = File(cacheDir, ".encrypted").exists()

/** 清理 EPUB 缓存。 */
fun cleanEpubCache(context: Context, epubUri: Uri) {
    getEpubCacheDir(context, epubUri).deleteRecursively()
}

private fun openEpubInputStreamWithRecovery(
    context: Context,
    epubUri: Uri,
    onLog: (String) -> Unit
): Pair<java.io.InputStream?, Uri?> {
    val cr = context.contentResolver
    try {
        val direct = cr.openInputStream(epubUri)
        if (direct != null) return direct to epubUri
    } catch (e: Exception) {
        onLog("EPUB 直读失败: ${e.javaClass.simpleName}: ${e.message}")
    }

    if (epubUri.scheme != "content") return null to null
    val authority = epubUri.authority ?: return null to null
    val docId = try { DocumentsContract.getDocumentId(epubUri) } catch (_: Exception) { return null to null }
    val slash = docId.lastIndexOf('/')
    if (slash <= 0) return null to null

    val parentId = docId.substring(0, slash)
    val originalName = docId.substring(slash + 1)
    val parentDocUri = try { DocumentsContract.buildDocumentUri(authority, parentId) } catch (_: Exception) { return null to null }
    val childrenUri = try { DocumentsContract.buildChildDocumentsUriUsingTree(parentDocUri, parentId) } catch (_: Exception) { return null to null }

    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    val candidates = mutableListOf<Pair<String, String>>()
    try {
        cr.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            if (idIdx >= 0 && nameIdx >= 0 && mimeIdx >= 0) {
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idIdx) ?: continue
                    val childName = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    if (childName.endsWith(".epub", ignoreCase = true)) {
                        candidates.add(childId to childName)
                    }
                }
            }
        }
    } catch (e: Exception) {
        onLog("EPUB 恢复扫描失败: ${e.javaClass.simpleName}: ${e.message}")
        return null to null
    }

    if (candidates.isEmpty()) return null to null

    val baseHint = originalName.substringBefore("(").substringBeforeLast('.').trim()
    val fallback =
        candidates.firstOrNull { it.second.equals(originalName, ignoreCase = true) }
            ?: candidates.firstOrNull {
                baseHint.isNotBlank() && it.second.contains(baseHint, ignoreCase = true)
            }
            ?: if (candidates.size == 1) candidates.first() else null

    if (fallback == null) return null to null

    val recoveredUri = try { DocumentsContract.buildDocumentUri(authority, fallback.first) } catch (_: Exception) { return null to null }
    return try {
        val recovered = cr.openInputStream(recoveredUri)
        if (recovered != null) {
            onLog("EPUB 已从失效 URI 自动恢复到同目录文件: ${fallback.second}")
            recovered to recoveredUri
        } else {
            null to null
        }
    } catch (e: Exception) {
        onLog("EPUB 自动恢复后仍无法读取: ${e.javaClass.simpleName}: ${e.message}")
        null to null
    }
}

/** 解析 META-INF/container.xml 获取 rootfile（OPF文件）路径。 */
private fun parseEpubContainer(contentDir: File): String? {
    val containerFile = File(contentDir, "META-INF/container.xml")
    if (!containerFile.exists()) return null
    val content = containerFile.readText()
    // 简单XML解析：查找 rootfile 的 full-path 属性
    val pathMatch = Regex("""full-path\s*=\s*"([^"]+)"""").find(content)
    return pathMatch?.groupValues?.getOrNull(1)
}

/** 解析 OPF 文件获取书籍信息和章节列表。 */
@Suppress("UNUSED_PARAMETER")
private fun parseEpubOpf(context: Context, opfFile: File, opfDir: File): Pair<EpubBookInfo, List<EpubChapter>>? {
    if (!opfFile.exists()) return null
    val content = opfFile.readText()

    // 解析 metadata
    var title = context.getString(R.string.zip_unknown_book_title)
    var author: String? = null
    var language: String? = null

    val titleMatch = Regex("""<dc:title[^>]*>([^<]*)</dc:title>""", RegexOption.IGNORE_CASE).find(content)
    if (titleMatch != null) title = titleMatch.groupValues[1].trim()

    val authorMatch = Regex("""<dc:creator[^>]*>([^<]*)</dc:creator>""", RegexOption.IGNORE_CASE).find(content)
    if (authorMatch != null) author = authorMatch.groupValues[1].trim()

    val langMatch = Regex("""<dc:language[^>]*>([^<]*)</dc:language>""", RegexOption.IGNORE_CASE).find(content)
    if (langMatch != null) language = langMatch.groupValues[1].trim()

    val bookInfo = EpubBookInfo(title, author, language)

    // 解析 manifest（ID -> href 映射）
    val manifest = mutableMapOf<String, String>()
    val itemRegex = Regex("""<item\s+[^>]+/?>""", RegexOption.IGNORE_CASE)
    val idAttrRegex = Regex("""id\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    val hrefAttrRegex = Regex("""href\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    itemRegex.findAll(content).forEach { match ->
        val itemText = match.value
        val id = idAttrRegex.find(itemText)?.groupValues?.get(1)
        val href = hrefAttrRegex.find(itemText)?.groupValues?.get(1)
        if (id != null && href != null) {
            manifest[id] = href
        }
    }

    // 解析 spine（阅读顺序）
    val chapters = mutableListOf<EpubChapter>()
    val spineRegex = Regex("""<itemref[^>]*idref\s*=\s*"([^"]+)"[^>]*/?>""", RegexOption.IGNORE_CASE)
    spineRegex.findAll(content).forEach { match ->
        val idref = match.groupValues[1]
        val href = manifest[idref]
        if (href != null) {
            chapters.add(EpubChapter(idref, href, null))
        }
    }

    return bookInfo to chapters
}

/** 尝试从 NCX 或 NAV 文件获取章节标题。 */
@Suppress("UNUSED_PARAMETER")
private fun parseEpubNavigation(
    context: Context,
    contentDir: File,
    opfDir: File,
    chapters: List<EpubChapter>,
    onLog: (String) -> Unit = {}
): List<EpubChapter> {
    // 尝试查找 NCX 文件
    val ncxFile = opfDir.listFiles()?.find { it.name.endsWith(".ncx", ignoreCase = true) }
    if (ncxFile != null && ncxFile.exists()) {
        try {
            onLog(context.getString(R.string.zip_log_read_ncx))
            val ncxContent = ncxFile.readText()

            // 使用更高效的正则：分别匹配 text 和 src
            onLog(context.getString(R.string.zip_log_parse_navpoints))
            val navMap = mutableMapOf<String, String>()

            // 先找到所有 navPoint 块
            val navPointPattern = Regex("""<navPoint[^>]*>(.*?)</navPoint>""", RegexOption.DOT_MATCHES_ALL)
            val textPattern = Regex("""<text>([^<]*)</text>""", RegexOption.IGNORE_CASE)
            val srcPattern = Regex("""src\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)

            var count = 0
            navPointPattern.findAll(ncxContent).forEach { match ->
                val block = match.groupValues[1]
                val title = textPattern.find(block)?.groupValues?.get(1)?.trim() ?: return@forEach
                val src = srcPattern.find(block)?.groupValues?.get(1)
                    ?.substringBefore("#")?.substringBefore("?") ?: return@forEach
                navMap[src] = title
                count++
                if (count % 100 == 0) {
                    onLog(context.getString(R.string.zip_log_navpoints_parsed_progress, count))
                }
            }
            onLog(context.getString(R.string.zip_log_navpoints_parsed_total, count))

            // 更新章节标题
            return chapters.map { chapter ->
                val hrefName = chapter.href.substringAfterLast("/")
                val title = navMap[hrefName] ?: navMap[chapter.href] ?: chapter.href.substringBeforeLast(".")
                chapter.copy(title = title)
            }
        } catch (e: Exception) {
            onLog(context.getString(R.string.zip_log_ncx_parse_error, e.message ?: ""))
        }
    }

    // 如果没有 NCX，使用文件名作为标题
    return chapters.map { chapter ->
        val fileName = chapter.href.substringAfterLast("/")
        val title = fileName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
        chapter.copy(title = title)
    }
}

/**
 * 解压 EPUB 到缓存目录，解析 OPF 获取章节列表。
 * @param epubFileName EPUB 文件名（用于日志，可选）
 * @param onLog 日志回调，用于显示进度
 * @return 解压结果，包含详细错误信息
 */
@Suppress("UNUSED_PARAMETER")
fun extractEpubToCache(
    context: Context,
    epubUri: Uri,
    password: CharArray?,
    epubFileName: String? = null,
    onLog: (String) -> Unit = {}
): EpubParseResult {
    val cacheDir = getEpubCacheDir(context, epubUri)
    cacheDir.deleteRecursively()
    cacheDir.mkdirs()
    val tmpZip = File(cacheDir, "__archive.zip")
    try {
        onLog(context.getString(R.string.zip_log_start_read_generic))
        val (inputStream, recoveredUri) = openEpubInputStreamWithRecovery(context, epubUri, onLog)
        if (inputStream == null) {
            return EpubParseResult.Error(
                context.getString(R.string.zip_error_open_file),
                context.getString(R.string.zip_error_input_stream_null_simple)
            )
        }
        if (recoveredUri != null && recoveredUri != epubUri) {
            onLog("提示: 最近记录 URI 可能已失效，已使用可读取文件继续加载。")
        }
        inputStream.use { input ->
            tmpZip.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    total += read
                    if (total % (1024 * 1024) < 8192) {  // 每MB更新一次
                        onLog(context.getString(R.string.zip_log_copied_mb, total / 1024 / 1024))
                    }
                }
            }
        }

        if (!tmpZip.exists() || tmpZip.length() == 0L) {
            return EpubParseResult.Error(
                context.getString(R.string.zip_error_copy_failed),
                context.getString(R.string.zip_error_temp_missing_simple)
            )
        }
        onLog(context.getString(R.string.zip_log_file_read_complete, tmpZip.length() / 1024 / 1024))

        onLog(context.getString(R.string.zip_log_check_archive))
        val zip = ZipFile(tmpZip)
        val encrypted = zip.isEncrypted
        if (encrypted) {
            if (password == null || password.isEmpty()) {
                return EpubParseResult.Error(
                    context.getString(R.string.zip_error_password_required_title),
                    context.getString(R.string.zip_error_epub_password_required)
                )
            }
            zip.setPassword(password)
        }
        onLog(context.getString(R.string.zip_log_check_archive_done, encrypted.toString()))

        onLog(context.getString(R.string.zip_log_extracting))
        val contentDir = File(cacheDir, "content").apply { mkdirs() }
        try {
            zip.extractAll(contentDir.path)
        } catch (e: net.lingala.zip4j.exception.ZipException) {
            val msg = e.message ?: context.getString(R.string.zip_unknown_zip_exception)
            return EpubParseResult.Error(
                context.getString(R.string.zip_error_extract_failed),
                context.getString(R.string.zip_error_zip_exception, msg)
            )
        }
        tmpZip.delete()
        onLog(context.getString(R.string.zip_log_extract_complete))

        onLog(context.getString(R.string.zip_log_parse_container))
        val opfRelativePath = parseEpubContainer(contentDir)
        if (opfRelativePath == null) {
            val metaInfExists = File(contentDir, "META-INF").exists()
            val containerExists = File(contentDir, "META-INF/container.xml").exists()
            val rootFiles = contentDir.listFiles()?.take(20)?.joinToString(", ") { it.name } ?: context.getString(R.string.zip_empty_placeholder)
            return EpubParseResult.Error(
                context.getString(R.string.zip_error_container_missing),
                context.getString(R.string.zip_error_container_missing_detail, metaInfExists.toString(), containerExists.toString(), rootFiles)
            )
        }
        onLog(context.getString(R.string.zip_log_opf_path, opfRelativePath))

        val opfFile = File(contentDir, opfRelativePath)
        if (!opfFile.exists()) {
            val opfDirFiles = opfFile.parentFile?.listFiles()?.take(20)?.joinToString(", ") { it.name } ?: context.getString(R.string.zip_empty_placeholder)
            return EpubParseResult.Error(
                context.getString(R.string.zip_error_opf_missing),
                context.getString(R.string.zip_error_opf_missing_detail, opfRelativePath, opfDirFiles)
            )
        }
        onLog(context.getString(R.string.zip_log_opf_found))

        val opfDir = opfFile.parentFile ?: contentDir

        onLog(context.getString(R.string.zip_log_parse_opf))
        val parseResult = parseEpubOpf(context, opfFile, opfDir)
        if (parseResult == null) {
            return EpubParseResult.Error(context.getString(R.string.zip_error_opf_parse_failed), context.getString(R.string.zip_error_file_path, opfFile.absolutePath))
        }
        val (bookInfo, rawChapters) = parseResult
        onLog(context.getString(R.string.zip_log_book_info, bookInfo.title, bookInfo.author ?: "", rawChapters.size))

        onLog(context.getString(R.string.zip_log_parse_navigation))
        val chapters = parseEpubNavigation(context, contentDir, opfDir, rawChapters) { log ->
            onLog(log)
        }
        onLog(context.getString(R.string.zip_log_final_chapter_count, chapters.size))

        if (chapters.isEmpty()) {
            val spineSample = opfFile.readText().let { text ->
                val spineMatch = Regex("<spine[^>]*>(.*?)</spine>", RegexOption.DOT_MATCHES_ALL).find(text)
                spineMatch?.groupValues?.get(1)?.take(500) ?: context.getString(R.string.zip_spine_not_found)
            }
            return EpubParseResult.Error(context.getString(R.string.zip_error_chapter_missing), context.getString(R.string.zip_error_spine_content, spineSample))
        }

        File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
        if (encrypted) File(cacheDir, ".encrypted").createNewFile()

        onLog(context.getString(R.string.zip_log_load_complete))
        return EpubParseResult.Success(EpubExtractResult(cacheDir, contentDir, opfDir, bookInfo, chapters, encrypted))
    } catch (e: Exception) {
        Log.e(TAG, "extractEpubToCache failed", e)
        cacheDir.deleteRecursively()
        return EpubParseResult.Error(
            context.getString(R.string.zip_error_parse_exception, e.javaClass.simpleName),
            e.message
        )
    } finally {
        if (tmpZip.exists()) tmpZip.delete()
    }
}

/** 从已有缓存加载 EPUB 信息（不解压）。 */
fun loadEpubFromCache(context: Context, cacheDir: File): EpubExtractResult? {
    if (!cacheDir.exists()) return null
    val contentDir = File(cacheDir, "content")
    if (!contentDir.exists()) return null

    val encrypted = isEpubCacheEncrypted(cacheDir)

    // 解析 container.xml 获取 OPF 路径
    val opfRelativePath = parseEpubContainer(contentDir) ?: return null
    val opfFile = File(contentDir, opfRelativePath)
    val opfDir = opfFile.parentFile ?: contentDir

    val parseResult = parseEpubOpf(context, opfFile, opfDir) ?: return null
    val (bookInfo, rawChapters) = parseResult
    var chapters = parseEpubNavigation(context, contentDir, opfDir, rawChapters)
    val titleMap = loadChapterTitles(cacheDir)
    chapters = applySavedChapterTitles(chapters, titleMap)
    if (titleMap.isEmpty() && File(cacheDir, ".llm_source").exists()) {
        chapters = recoverChapterTitlesFromH1(opfDir, chapters)
    }

    return EpubExtractResult(cacheDir, contentDir, opfDir, bookInfo, chapters, encrypted)
}

/** 获取 EPUB 章节的完整文件路径。 */
fun getEpubChapterFile(result: EpubExtractResult, chapter: EpubChapter): File? {
    val file = File(result.opfDir, chapter.href)
    return if (file.exists()) file else null
}

/** 判断文件名是否为 EPUB 文件。 */
fun isEpubFile(name: String): Boolean = name.endsWith(".epub", ignoreCase = true)

/** 判断文件名是否为 TXT 文件。 */
fun isTxtFile(name: String): Boolean = name.endsWith(".txt", ignoreCase = true)

/** 判断文件名是否为 LLM 对话文件。 */
fun isLlmFile(name: String): Boolean = name.endsWith(".llm", ignoreCase = true)

private const val CHAPTER_TYPE_MARKER_TXT = "<!--LM_CHAPTER_TYPE:TXT-->"
private const val CHAPTER_TYPE_MARKER_LLM = "<!--LM_CHAPTER_TYPE:LLM-->"
private const val CHAPTER_TITLES_FILE = ".chapter_titles.json"

private data class DialogBlock(
    val role: String,
    val content: String,
    val isAssistant: Boolean
)

private fun readTextWithFallback(file: File): String {
    return decodeTextWithFallback(file.readBytes())
}

private fun decodeTextWithFallback(bytes: ByteArray): String {
    return try {
        bytes.toString(Charsets.UTF_8)
    } catch (_: Exception) {
        try {
            bytes.toString(Charset.forName("GBK"))
        } catch (_: Exception) {
            bytes.toString()
        }
    }
}

private fun buildTxtChapterHtml(bookTitle: String, content: String): String {
    val paragraphs = content
        .split(Regex("\\n\\s*\\n"))
        .map { para -> para.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") }
        .filter { it.isNotEmpty() }

    val paragraphsHtml = paragraphs.joinToString("\n") { para ->
        "<p>${escapeHtml(para)}</p>"
    }

    return """
        $CHAPTER_TYPE_MARKER_TXT
        <h1>${escapeHtml(bookTitle)}</h1>
        $paragraphsHtml
    """.trimIndent()
}

private fun parseLlmDialogBlocks(content: String): List<DialogBlock> {
    val dialogBlocks = mutableListOf<DialogBlock>()
    val lines = content.lines()
    val currentBlock = StringBuilder()
    var currentRole: String? = null

    for (line in lines) {
        val trimmedLine = line.trimEnd()
        val roleMatch = Regex("^(Configure|System|User|Assistant)\\s*:\\s*(.*)$").find(trimmedLine)
        if (roleMatch != null) {
            if (currentRole != null && currentBlock.isNotEmpty()) {
                dialogBlocks.add(
                    DialogBlock(
                        role = currentRole,
                        content = currentBlock.toString().trim(),
                        isAssistant = currentRole == "Assistant"
                    )
                )
            }
            currentRole = roleMatch.groupValues[1]
            currentBlock.clear()
            val remainingContent = roleMatch.groupValues[2].trim()
            if (remainingContent.isNotEmpty()) {
                currentBlock.append(remainingContent).append("\n")
            }
        } else if (currentRole != null) {
            currentBlock.append(trimmedLine).append("\n")
        } else if (trimmedLine.isNotEmpty()) {
            if (currentBlock.isNotEmpty() || trimmedLine.startsWith("//") || trimmedLine.startsWith("#")) {
                currentBlock.append(trimmedLine).append("\n")
            }
        }
    }

    if (currentRole != null && currentBlock.isNotEmpty()) {
        dialogBlocks.add(
            DialogBlock(
                role = currentRole,
                content = currentBlock.toString().trim(),
                isAssistant = currentRole == "Assistant"
            )
        )
    } else if (currentBlock.isNotEmpty()) {
        dialogBlocks.add(DialogBlock("Comment", currentBlock.toString().trim(), false))
    }
    return dialogBlocks
}

private fun buildLlmChapterHtml(bookTitle: String, content: String): String {
    val dialogBlocks = parseLlmDialogBlocks(content)
    val blocksHtml = dialogBlocks.joinToString("\n") { block ->
        val escapedContent = escapeHtml(block.content)
        val formattedContent = escapedContent.replace("\n", "<br/>")
        if (block.isAssistant) {
            """<div class="assistant-block"><div class="role-label assistant-label">Assistant</div><div class="content assistant-content">$formattedContent</div></div>"""
        } else {
            """<details class="other-block"><summary class="role-label other-label">${block.role}</summary><div class="content other-content">$formattedContent</div></details>"""
        }
    }

    return """
        $CHAPTER_TYPE_MARKER_LLM
        <h1>${escapeHtml(bookTitle)}</h1>
        $blocksHtml
    """.trimIndent()
}

private fun saveChapterTitles(cacheDir: File, chapters: List<EpubChapter>) {
    runCatching {
        val arr = org.json.JSONArray()
        chapters.forEach { chapter ->
            arr.put(
                org.json.JSONObject().apply {
                    put("id", chapter.id)
                    put("href", chapter.href)
                    put("title", chapter.title ?: "")
                }
            )
        }
        File(cacheDir, CHAPTER_TITLES_FILE).writeText(arr.toString())
    }
}

private fun loadChapterTitles(cacheDir: File): Map<String, String> {
    val file = File(cacheDir, CHAPTER_TITLES_FILE)
    if (!file.exists()) return emptyMap()
    return runCatching {
        val arr = org.json.JSONArray(file.readText())
        buildMap {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                if (title.isBlank()) continue
                val id = obj.optString("id").trim()
                val href = obj.optString("href").trim()
                if (id.isNotBlank()) put("id:$id", title)
                if (href.isNotBlank()) put("href:$href", title)
            }
        }
    }.getOrElse { emptyMap() }
}

private fun applySavedChapterTitles(chapters: List<EpubChapter>, titleMap: Map<String, String>): List<EpubChapter> {
    if (titleMap.isEmpty()) return chapters
    return chapters.map { chapter ->
        val saved = titleMap["id:${chapter.id}"] ?: titleMap["href:${chapter.href}"]
        if (saved.isNullOrBlank()) chapter else chapter.copy(title = saved)
    }
}

private fun recoverChapterTitlesFromH1(opfDir: File, chapters: List<EpubChapter>): List<EpubChapter> {
    val h1Regex = Regex("""<h1[^>]*>(.*?)</h1>""", RegexOption.IGNORE_CASE)
    val tagRegex = Regex("<[^>]+>")
    return chapters.map { chapter ->
        val file = File(opfDir, chapter.href)
        if (!file.exists()) return@map chapter
        val title = runCatching {
            val html = file.readText()
            h1Regex.find(html)?.groupValues?.getOrNull(1)
                ?.replace(tagRegex, "")
                ?.replace("&nbsp;", " ")
                ?.trim()
        }.getOrNull()
        if (title.isNullOrBlank()) chapter else chapter.copy(title = title)
    }
}

/**
 * 将 TXT 文件转换为 EPUB 兼容格式，使用与 EPUB 相同的缓存结构
 * 按空行分隔为段落，整个文档作为一个章节
 *
 * @param context 上下文
 * @param txtFile TXT 文件
 * @param txtUri TXT 文件的 URI（用于生成缓存目录）
 * @return EpubExtractResult 或 null
 */
fun prepareTxtAsEpub(context: Context, txtFile: File, txtUri: Uri): EpubExtractResult? {
    if (!txtFile.exists()) return null
    return prepareTextBookAsEpub(
        context = context,
        sourceUri = txtUri,
        sourceFileName = txtFile.name,
        content = readTextWithFallback(txtFile),
        isLlm = false
    )
}

fun prepareTxtAsEpub(context: Context, txtUri: Uri, txtFileName: String): EpubExtractResult? {
    return try {
        val bytes = context.contentResolver.openInputStream(txtUri)?.use { it.readBytes() } ?: return null
        prepareTextBookAsEpub(
            context = context,
            sourceUri = txtUri,
            sourceFileName = txtFileName,
            content = decodeTextWithFallback(bytes),
            isLlm = false
        )
    } catch (e: Exception) {
        Log.e("TxtToEpub", "Failed to read TXT uri", e)
        null
    }
}

/**
 * 将 LLM 对话文件转换为 EPUB 兼容格式
 * Assistant 内容正常显示，其他角色（Configure/System/User）用灰色显示并可折叠
 *
 * @param context 上下文
 * @param llmFile LLM 文件
 * @param llmUri LLM 文件的 URI（用于生成缓存目录）
 * @return EpubExtractResult 或 null
 */
fun prepareLlmAsEpub(context: Context, llmFile: File, llmUri: Uri): EpubExtractResult? {
    if (!llmFile.exists()) return null
    return prepareTextBookAsEpub(
        context = context,
        sourceUri = llmUri,
        sourceFileName = llmFile.name,
        content = readTextWithFallback(llmFile),
        isLlm = true
    )
}

fun prepareLlmAsEpub(context: Context, llmUri: Uri, llmFileName: String): EpubExtractResult? {
    return try {
        val bytes = context.contentResolver.openInputStream(llmUri)?.use { it.readBytes() } ?: return null
        prepareTextBookAsEpub(
            context = context,
            sourceUri = llmUri,
            sourceFileName = llmFileName,
            content = decodeTextWithFallback(bytes),
            isLlm = true
        )
    } catch (e: Exception) {
        Log.e("LlmToEpub", "Failed to read LLM uri", e)
        null
    }
}

private fun prepareTextBookAsEpub(
    context: Context,
    sourceUri: Uri,
    sourceFileName: String,
    content: String,
    isLlm: Boolean
): EpubExtractResult? {
    return try {
        val cacheDir = getEpubCacheDir(context, sourceUri)
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()

        val contentDir = File(cacheDir, "content")
        val metaInfDir = File(contentDir, "META-INF")
        val oebpsDir = File(contentDir, "OEBPS")
        metaInfDir.mkdirs()
        oebpsDir.mkdirs()

        val bookTitle = sourceFileName.substringBeforeLast('.')

        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()
        File(metaInfDir, "container.xml").writeText(containerXml)

        val htmlFileName = "chapter_0.html"
        val htmlFile = File(oebpsDir, htmlFileName)
        val htmlContent = if (isLlm) buildLlmChapterHtml(bookTitle, content) else buildTxtChapterHtml(bookTitle, content)
        htmlFile.writeText(htmlContent)

        val identifierPrefix = if (isLlm) "llm" else "txt"
        val opfContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$bookTitle</dc:title>
                    <dc:language>zh</dc:language>
                    <dc:identifier id="uid">$identifierPrefix-${sourceFileName.hashCode()}</dc:identifier>
                </metadata>
                <manifest>
                    <item id="chapter_0" href="$htmlFileName" media-type="application/xhtml+xml"/>
                </manifest>
                <spine>
                    <itemref idref="chapter_0"/>
                </spine>
            </package>
        """.trimIndent()
        File(oebpsDir, "content.opf").writeText(opfContent)

        File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
        if (isLlm) File(cacheDir, ".llm_source").writeText(sourceFileName)
        else File(cacheDir, ".txt_source").writeText(sourceFileName)

        val chapters = listOf(
            EpubChapter(
                id = "chapter_0",
                href = htmlFileName,
                title = bookTitle
            )
        )
        saveChapterTitles(cacheDir, chapters)
        val bookInfo = EpubBookInfo(title = bookTitle, author = null, language = "zh")

        EpubExtractResult(
            cacheDir = cacheDir,
            contentDir = contentDir,
            opfDir = oebpsDir,
            bookInfo = bookInfo,
            chapters = chapters,
            isEncrypted = false
        )
    } catch (e: Exception) {
        Log.e(if (isLlm) "LlmToEpub" else "TxtToEpub", "Failed to prepare text book cache", e)
        null
    }
}

/** 解压 .llm.zip，并将包内所有 .txt/.llm 文件转换为 EPUB 多章节。 */
fun extractLlmZipToCache(
    context: Context,
    zipUri: Uri,
    password: CharArray?,
    zipFileName: String
): EpubExtractResult? {
    val cacheDir = getEpubCacheDir(context, zipUri)
    cacheDir.deleteRecursively()
    cacheDir.mkdirs()
    val tmpZip = File(cacheDir, "__archive.zip")
    try {
        context.contentResolver.openInputStream(zipUri)?.use { input ->
            tmpZip.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        val zip = ZipFile(tmpZip)
        val encrypted = zip.isEncrypted
        if (encrypted) {
            if (password == null || password.isEmpty()) return null
            zip.setPassword(password)
        }

        val rawDir = File(cacheDir, "raw").apply { mkdirs() }
        zip.extractAll(rawDir.path)
        tmpZip.delete()

        val effectiveRawDir = unwrapSingleChildDir(rawDir)
        val indexFile = sequenceOf(
            File(rawDir, ".index"),
            File(effectiveRawDir, ".index")
        ).firstOrNull { it.exists() && it.isFile }
        val sourceFiles = if (indexFile != null) {
            loadIndexedChapterFiles(context, indexFile, listOf(rawDir, effectiveRawDir).distinctBy { it.absolutePath })
        } else {
            val txtFiles = collectFilesWithExtension(effectiveRawDir, "txt")
            val llmFiles = collectFilesWithExtension(effectiveRawDir, "llm")
            toDefaultIndexedChapterEntries((txtFiles + llmFiles).sortedBy { it.first.lowercase() })
        }
        if (sourceFiles.isEmpty()) {
            Log.w(TAG, "extractLlmZipToCache: no .txt/.llm files found in $zipUri")
            cacheDir.deleteRecursively()
            return null
        }

        val contentDir = File(cacheDir, "content")
        val metaInfDir = File(contentDir, "META-INF")
        val oebpsDir = File(contentDir, "OEBPS")
        metaInfDir.mkdirs()
        oebpsDir.mkdirs()

        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()
        File(metaInfDir, "container.xml").writeText(containerXml)

        val chapters = mutableListOf<EpubChapter>()
        val manifestBuilder = StringBuilder()
        val spineBuilder = StringBuilder()

        sourceFiles.forEachIndexed { index, entry ->
            val chapterId = "chapter_$index"
            val htmlFileName = "$chapterId.html"
            val sourceFile = entry.sourceFile
            val chapterTitle = entry.title
            val chapterText = readTextWithFallback(sourceFile)
            val chapterHtml = if (sourceFile.name.endsWith(".llm", ignoreCase = true)) {
                buildLlmChapterHtml(chapterTitle, chapterText)
            } else {
                buildTxtChapterHtml(chapterTitle, chapterText)
            }
            File(oebpsDir, htmlFileName).writeText(chapterHtml)

            chapters += EpubChapter(
                id = chapterId,
                href = htmlFileName,
                title = chapterTitle
            )
            manifestBuilder.append("<item id=\"").append(chapterId).append("\" href=\"")
                .append(htmlFileName).append("\" media-type=\"application/xhtml+xml\"/>")
            spineBuilder.append("<itemref idref=\"").append(chapterId).append("\"/>")
        }

        val bookTitle = zipFileName.removeSuffix(".llm.zip").ifBlank { zipFileName.removeSuffix(".zip") }
        val opfContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>${escapeHtml(bookTitle)}</dc:title>
                    <dc:language>zh</dc:language>
                    <dc:identifier id="uid">llmzip-${zipFileName.hashCode()}</dc:identifier>
                </metadata>
                <manifest>
                    ${manifestBuilder}
                </manifest>
                <spine>
                    ${spineBuilder}
                </spine>
            </package>
        """.trimIndent()
        File(oebpsDir, "content.opf").writeText(opfContent)

        File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
        if (encrypted) File(cacheDir, ".encrypted").createNewFile()
        saveChapterTitles(cacheDir, chapters)

        return EpubExtractResult(
            cacheDir = cacheDir,
            contentDir = contentDir,
            opfDir = oebpsDir,
            bookInfo = EpubBookInfo(title = bookTitle, author = null, language = "zh"),
            chapters = chapters,
            isEncrypted = encrypted
        )
    } catch (e: Exception) {
        Log.e(TAG, "extractLlmZipToCache failed", e)
        cacheDir.deleteRecursively()
        return null
    } finally {
        if (tmpZip.exists()) tmpZip.delete()
    }
}

/** 简单的 HTML 转义 */
private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
