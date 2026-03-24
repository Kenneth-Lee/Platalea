package com.kenny.localmanager.dict

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.file.listFilesSafe
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.GZIPInputStream

private const val STAR_DICT_DIR_NAME = "stardict"
private const val META_FILE_NAME = "meta.json"
private const val INDEX_FILE_NAME = "index.tsv"
private const val DICT_FILE_NAME = "dict.bin"

data class StarDictSummary(
    val id: String,
    val name: String,
    val wordCount: Int,
    val importedAt: Long
)

data class StarDictWord(
    val word: String,
    val offset: Long,
    val size: Int
)

data class StarDictLoaded(
    val summary: StarDictSummary,
    val words: List<StarDictWord>,
    val charsetName: String?,
    val sameTypeSequence: String?
)

private data class StarDictMeta(
    val id: String,
    val name: String,
    val wordCount: Int,
    val importedAt: Long,
    val charsetName: String?,
    val sameTypeSequence: String?
)

private fun getBaseDir(context: Context): File = File(context.filesDir, STAR_DICT_DIR_NAME).apply { mkdirs() }

private fun isZipName(name: String): Boolean = name.endsWith(".zip", ignoreCase = true)

private fun isIfoName(name: String): Boolean = name.endsWith(".ifo", ignoreCase = true)

fun isStarDictImportCandidate(name: String): Boolean = isZipName(name) || isIfoName(name)

fun listImportedStarDicts(context: Context): List<StarDictSummary> {
    val baseDir = getBaseDir(context)
    return baseDir.listFiles().orEmpty()
        .asSequence()
        .filter { it.isDirectory }
        .mapNotNull { dir -> readMeta(dir) }
        .map { meta ->
            StarDictSummary(
                id = meta.id,
                name = meta.name,
                wordCount = meta.wordCount,
                importedAt = meta.importedAt
            )
        }
        .sortedByDescending { it.importedAt }
        .toList()
}

fun deleteImportedStarDict(context: Context, id: String): Boolean {
    val target = File(getBaseDir(context), id)
    if (!target.exists()) return false
    return target.deleteRecursively()
}

fun loadImportedStarDict(context: Context, id: String): StarDictLoaded? {
    val dir = File(getBaseDir(context), id)
    val meta = readMeta(dir) ?: return null
    val indexFile = File(dir, INDEX_FILE_NAME)
    if (!indexFile.exists()) return null
    val words = indexFile.useLines { lines ->
        lines.mapNotNull { line ->
            val p1 = line.indexOf('\t')
            if (p1 <= 0) return@mapNotNull null
            val p2 = line.indexOf('\t', p1 + 1)
            if (p2 <= p1) return@mapNotNull null
            val word = line.substring(0, p1)
            val offset = line.substring(p1 + 1, p2).toLongOrNull() ?: return@mapNotNull null
            val size = line.substring(p2 + 1).toIntOrNull() ?: return@mapNotNull null
            StarDictWord(word = word, offset = offset, size = size)
        }.toList()
    }
    return StarDictLoaded(
        summary = StarDictSummary(
            id = meta.id,
            name = meta.name,
            wordCount = meta.wordCount,
            importedAt = meta.importedAt
        ),
        words = words,
        charsetName = meta.charsetName,
        sameTypeSequence = meta.sameTypeSequence
    )
}

fun searchStarDictWords(loaded: StarDictLoaded, pattern: String, maxResults: Int = 500): Pair<List<StarDictWord>, String?> {
    if (pattern.isBlank()) return Pair(emptyList(), null)
    return try {
        val regex = buildRegex(pattern)
        val results = mutableListOf<StarDictWord>()
        for (item in loaded.words) {
            if (regex.containsMatchIn(item.word)) {
                results.add(item)
                if (results.size >= maxResults) break
            }
        }
        Pair(results, null)
    } catch (e: Exception) {
        Pair(emptyList(), e.message ?: "无效正则")
    }
}

/**
 * 精确查词：在已加载的词典中查找完全匹配的单词
 * @param word 要查找的单词
 * @return 找到的词条，或 null 如果未找到
 */
fun lookupExactWord(loaded: StarDictLoaded, word: String): StarDictWord? {
    if (word.isBlank()) return null
    val normalizedWord = word.trim().lowercase(Locale.getDefault())
    return loaded.words.find { it.word.lowercase(Locale.getDefault()) == normalizedWord }
}

fun readStarDictExplanation(context: Context, dictId: String, loaded: StarDictLoaded, word: StarDictWord): String {
    val dictFile = File(File(getBaseDir(context), dictId), DICT_FILE_NAME)
    if (!dictFile.exists()) return "词典数据文件不存在: ${dictFile.absolutePath}"
    if (word.size <= 0) return "词条大小无效: ${word.size}"
    if (word.offset < 0) return "词条偏移无效: ${word.offset}"

    val bytes = try {
        RandomAccessFile(dictFile, "r").use { raf ->
            raf.seek(word.offset)
            val size = word.size.coerceAtLeast(0)
            if (word.offset + size > raf.length()) {
                return "词条数据超出文件范围: offset=${word.offset}, size=${word.size}, fileSize=${raf.length()}"
            }
            val buffer = ByteArray(size)
            raf.readFully(buffer)
            buffer
        }
    } catch (e: Exception) {
        return "读取释义失败: ${e.javaClass.simpleName}: ${e.message}"
    }

    val decoded = decodeDefinition(bytes, loaded.charsetName)
    return if (decoded.isBlank()) "(无释义内容)" else decoded
}

fun importStarDict(context: Context, sourceUri: Uri, sourceName: String): Result<StarDictSummary> {
    return runCatching {
        val workDir = File(context.cacheDir, "stardict_import_${System.currentTimeMillis()}")
        workDir.mkdirs()
        try {
            val imported = if (isZipName(sourceName)) {
                importFromZip(context, sourceUri, workDir)
            } else if (isIfoName(sourceName)) {
                importFromIfo(context, sourceUri, workDir)
            } else {
                throw IllegalArgumentException("仅支持 .zip 或 .ifo 导入")
            }

            val dictId = buildDictId(imported.bookName)
            val targetDir = File(getBaseDir(context), dictId)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            val idxBytes = readIdxBytes(imported.idxFile)
            val entries = parseIdxEntries(idxBytes, imported.charsetName)
            if (entries.isEmpty()) throw IllegalStateException("词典索引为空")

            writeDictBin(imported.dictFile, File(targetDir, DICT_FILE_NAME))
            writeIndex(entries, File(targetDir, INDEX_FILE_NAME))

            val summary = StarDictSummary(
                id = dictId,
                name = imported.bookName,
                wordCount = entries.size,
                importedAt = System.currentTimeMillis()
            )
            val meta = StarDictMeta(
                id = summary.id,
                name = summary.name,
                wordCount = summary.wordCount,
                importedAt = summary.importedAt,
                charsetName = imported.charsetName,
                sameTypeSequence = imported.sameTypeSequence
            )
            writeMeta(targetDir, meta)
            summary
        } finally {
            workDir.deleteRecursively()
        }
    }
}

private data class ImportSource(
    val bookName: String,
    val idxFile: File,
    val dictFile: File,
    val charsetName: String?,
    val sameTypeSequence: String?
)

private fun importFromZip(context: Context, sourceUri: Uri, workDir: File): ImportSource {
    val zipFile = File(workDir, "source.zip")
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        zipFile.outputStream().use { output -> input.copyTo(output) }
    } ?: throw IllegalStateException("无法读取压缩包")

    val zip = ZipFile(zipFile)
    val headers = zip.fileHeaders.orEmpty()
    val ifoHeader = findHeader(headers, ".ifo") ?: throw IllegalStateException("压缩包中未找到 .ifo")
    val idxHeader = findHeader(headers, ".idx") ?: findHeader(headers, ".idx.gz")
    val dictHeader = findHeader(headers, ".dict") ?: findHeader(headers, ".dict.dz")
    if (idxHeader == null || dictHeader == null) {
        throw IllegalStateException("压缩包缺少 .idx/.dict 词典文件")
    }

    val extractDir = File(workDir, "zip_extract").apply { mkdirs() }

    // 保留原始扩展名，以便后续判断是否需要解压
    val ifoFile = extractHeaderWithOriginalExt(zip, ifoHeader, extractDir, "source")
    val idxFile = extractHeaderWithOriginalExt(zip, idxHeader, extractDir, "source")
    val dictFile = extractHeaderWithOriginalExt(zip, dictHeader, extractDir, "source")

    val ifoMap = parseIfo(ifoFile)
    val bookName = ifoMap["bookname"].orEmpty().ifBlank { stripExt(ifoHeader.fileName.substringAfterLast('/')) }
    val charsetName = ifoMap["charset"]?.trim()?.ifBlank { null }
    val sameTypeSequence = ifoMap["sametypesequence"]?.trim()?.ifBlank { null }
    return ImportSource(bookName = bookName, idxFile = idxFile, dictFile = dictFile, charsetName = charsetName, sameTypeSequence = sameTypeSequence)
}

private fun extractHeaderWithOriginalExt(zip: ZipFile, header: FileHeader, extractDir: File, baseName: String): File {
    val originalName = header.fileName.substringAfterLast('/')
    val ext = if (originalName.contains('.')) originalName.substringAfterLast('.') else ""
    val targetName = if (ext.isNotEmpty()) "$baseName.$ext" else baseName
    zip.extractFile(header, extractDir.absolutePath, targetName)
    return File(extractDir, targetName)
}

private fun importFromIfo(context: Context, sourceUri: Uri, workDir: File): ImportSource {
    val ifoDoc = DocumentFile.fromSingleUri(context, sourceUri) ?: throw IllegalStateException("无法读取 .ifo 文件")
    val ifoName = ifoDoc.name ?: throw IllegalStateException("无法获取 .ifo 文件名")
    val baseName = stripExt(ifoName)
    val parent = ifoDoc.parentFile ?: throw IllegalStateException("无法定位 .ifo 所在目录")
    val siblings = parent.listFilesSafe().toList()

    val idxDoc = siblings.firstOrNull {
        val n = it.name.orEmpty()
        n.equals("$baseName.idx", ignoreCase = true) || n.equals("$baseName.idx.gz", ignoreCase = true)
    } ?: throw IllegalStateException("同目录未找到 $baseName.idx")

    val dictDoc = siblings.firstOrNull {
        val n = it.name.orEmpty()
        n.equals("$baseName.dict", ignoreCase = true) || n.equals("$baseName.dict.dz", ignoreCase = true)
    } ?: throw IllegalStateException("同目录未找到 $baseName.dict")

    // 保留原始扩展名
    val ifoFile = copyDocToFileWithOriginalExt(context, ifoDoc.uri, workDir, "source")
    val idxFile = copyDocToFileWithOriginalExt(context, idxDoc.uri, workDir, "source")
    val dictFile = copyDocToFileWithOriginalExt(context, dictDoc.uri, workDir, "source")

    val ifoMap = parseIfo(ifoFile)
    val bookName = ifoMap["bookname"].orEmpty().ifBlank { baseName }
    val charsetName = ifoMap["charset"]?.trim()?.ifBlank { null }
    val sameTypeSequence = ifoMap["sametypesequence"]?.trim()?.ifBlank { null }
    return ImportSource(bookName = bookName, idxFile = idxFile, dictFile = dictFile, charsetName = charsetName, sameTypeSequence = sameTypeSequence)
}

private fun copyDocToFileWithOriginalExt(context: Context, sourceUri: Uri, workDir: File, baseName: String): File {
    val doc = DocumentFile.fromSingleUri(context, sourceUri) ?: throw IllegalStateException("无法读取文件")
    val name = doc.name ?: throw IllegalStateException("无法获取文件名")
    val ext = if (name.contains('.')) name.substringAfterLast('.') else ""
    val targetName = if (ext.isNotEmpty()) "$baseName.$ext" else baseName
    val target = File(workDir, targetName)
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: throw IllegalStateException("无法读取源文件")
    return target
}

private fun parseIfo(ifoFile: File): Map<String, String> {
    val map = linkedMapOf<String, String>()
    ifoFile.forEachLine(Charsets.UTF_8) { line ->
        val idx = line.indexOf('=')
        if (idx <= 0) return@forEachLine
        val key = line.substring(0, idx).trim().lowercase(Locale.getDefault())
        val value = line.substring(idx + 1).trim()
        if (key.isNotBlank()) map[key] = value
    }
    return map
}

private fun readIdxBytes(idxFile: File): ByteArray {
    val lower = idxFile.name.lowercase(Locale.getDefault())
    return if (lower.endsWith(".gz")) {
        GZIPInputStream(idxFile.inputStream()).use { it.readBytes() }
    } else {
        idxFile.readBytes()
    }
}

private fun parseIdxEntries(data: ByteArray, charsetName: String?): List<StarDictWord> {
    val charset = resolveCharset(charsetName) ?: Charsets.UTF_8
    val result = ArrayList<StarDictWord>()
    var index = 0
    while (index < data.size) {
        val zero = findZeroByte(data, index)
        if (zero < 0) break
        if (zero + 8 >= data.size) break
        val wordBytes = data.copyOfRange(index, zero)
        val word = wordBytes.toString(charset).trim().ifBlank { "(空词条)" }
        val offset = readUint32(data, zero + 1)
        val size = readUint32(data, zero + 5).toInt()
        result.add(StarDictWord(word = word, offset = offset, size = size))
        index = zero + 9
    }
    return result
}

private fun findZeroByte(data: ByteArray, startIndex: Int): Int {
    var i = startIndex.coerceAtLeast(0)
    while (i < data.size) {
        if (data[i] == 0.toByte()) return i
        i++
    }
    return -1
}

private fun writeDictBin(sourceDictFile: File, targetBinFile: File) {
    val lower = sourceDictFile.name.lowercase(Locale.getDefault())
    if (lower.endsWith(".dz")) {
        GZIPInputStream(sourceDictFile.inputStream()).use { input ->
            targetBinFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } else {
        sourceDictFile.inputStream().use { input ->
            targetBinFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

private fun writeIndex(entries: List<StarDictWord>, target: File) {
    target.bufferedWriter(Charsets.UTF_8).use { writer ->
        entries.forEach { entry ->
            val safeWord = entry.word.replace('\t', ' ')
            writer.append(safeWord)
            writer.append('\t')
            writer.append(entry.offset.toString())
            writer.append('\t')
            writer.append(entry.size.toString())
            writer.append('\n')
        }
    }
}

private fun buildRegex(pattern: String): Regex {
    val slashForm = Regex("^/((?:\\\\.|[^/])*)/([a-zA-Z]*)$").matchEntire(pattern)
    if (slashForm != null) {
        val source = slashForm.groupValues[1]
        val flags = slashForm.groupValues[2]
        val options = buildSet {
            flags.forEach { flag ->
                when (flag.lowercaseChar()) {
                    'i' -> add(RegexOption.IGNORE_CASE)
                    'm' -> add(RegexOption.MULTILINE)
                    's' -> add(RegexOption.DOT_MATCHES_ALL)
                    'u', 'g' -> Unit
                    else -> throw IllegalArgumentException("不支持的正则标志: $flag")
                }
            }
        }
        return Regex(source, options)
    }
    return Regex(pattern)
}

private fun decodeDefinition(bytes: ByteArray, charsetName: String?): String {
    // 1. 优先使用词典指定的编码
    charsetName?.let { name ->
        resolveCharset(name)?.let { cs ->
            try {
                val text = bytes.toString(cs)
                if (isValidText(text)) {
                    return formatDefinition(text)
                }
            } catch (_: Exception) {}
        }
    }

    // 2. 尝试 UTF-8
    try {
        val text = bytes.toString(Charsets.UTF_8)
        if (isValidText(text)) {
            return formatDefinition(text)
        }
    } catch (_: Exception) {}

    // 3. 尝试 GB18030（常见于中文词典）
    try {
        val gb18030 = Charset.forName("GB18030")
        val text = bytes.toString(gb18030)
        if (isValidText(text)) {
            return formatDefinition(text)
        }
    } catch (_: Exception) {}

    // 4. 尝试 GBK
    try {
        val gbk = Charset.forName("GBK")
        val text = bytes.toString(gbk)
        if (isValidText(text)) {
            return formatDefinition(text)
        }
    } catch (_: Exception) {}

    // 5. 尝试 Big5（繁体中文）
    try {
        val big5 = Charset.forName("Big5")
        val text = bytes.toString(big5)
        if (isValidText(text)) {
            return formatDefinition(text)
        }
    } catch (_: Exception) {}

    // 6. 最后使用 UTF-8，即使可能有乱码
    return formatDefinition(bytes.toString(Charsets.UTF_8))
}

private fun isValidText(text: String): Boolean {
    if (text.isBlank()) return false
    // 检测常见的乱码特征
    val replacementCharCount = text.count { it == '\uFFFD' }
    if (replacementCharCount > text.length / 10) return false // 替换字符超过10%

    // 检测连续的高位控制字符（常见于错误编码）
    var controlCount = 0
    for (c in text) {
        val code = c.code
        if (code in 0x80..0x9F || code == 0xFFFD) {
            controlCount++
        }
    }
    if (controlCount > text.length / 5) return false

    return true
}

private fun formatDefinition(text: String): String {
    return text
        .replace("\u0000", "\n")
        .trim()
}

private fun resolveCharset(name: String?): Charset? {
    val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Charset.forName(n) }.getOrNull()
}

private fun readUint32(data: ByteArray, start: Int): Long {
    if (start + 4 > data.size) return 0L
    return ((data[start].toLong() and 0xff) shl 24) or
        ((data[start + 1].toLong() and 0xff) shl 16) or
        ((data[start + 2].toLong() and 0xff) shl 8) or
        (data[start + 3].toLong() and 0xff)
}

private fun findHeader(headers: List<FileHeader>, suffix: String): FileHeader? {
    return headers.firstOrNull { header ->
        !header.isDirectory && header.fileName.lowercase(Locale.getDefault()).endsWith(suffix.lowercase(Locale.getDefault()))
    }
}

private fun stripExt(name: String): String {
    val idx = name.lastIndexOf('.')
    if (idx <= 0) return name
    return name.substring(0, idx)
}

private fun buildDictId(bookName: String): String {
    val normalized = bookName.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]+"), "_").trim('_')
    val base = normalized.ifBlank { "dict" }
    return "${base}_${System.currentTimeMillis()}"
}

private fun readMeta(dir: File): StarDictMeta? {
    val metaFile = File(dir, META_FILE_NAME)
    if (!metaFile.exists()) return null
    return runCatching {
        val obj = JSONObject(metaFile.readText(Charsets.UTF_8))
        StarDictMeta(
            id = obj.optString("id").ifBlank { dir.name },
            name = obj.optString("name").ifBlank { dir.name },
            wordCount = obj.optInt("wordCount", 0),
            importedAt = obj.optLong("importedAt", 0L),
            charsetName = obj.optString("charsetName").ifBlank { null },
            sameTypeSequence = obj.optString("sameTypeSequence").ifBlank { null }
        )
    }.getOrNull()
}

private fun writeMeta(dir: File, meta: StarDictMeta) {
    val obj = JSONObject()
        .put("id", meta.id)
        .put("name", meta.name)
        .put("wordCount", meta.wordCount)
        .put("importedAt", meta.importedAt)
    meta.charsetName?.let { obj.put("charsetName", it) }
    meta.sameTypeSequence?.let { obj.put("sameTypeSequence", it) }
    File(dir, META_FILE_NAME).writeText(obj.toString(), Charsets.UTF_8)
}
