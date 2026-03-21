package com.kenny.localmanager.file

import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import java.io.File
import java.io.FileOutputStream

private const val TAG = "ZipOperations"
private const val MIME_ZIP = "application/zip"

private fun isRarName(name: String?): Boolean =
    name?.endsWith(".rar", ignoreCase = true) == true

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
private fun resolveDocUriWithTree(uri: Uri, treeUri: Uri?): Uri = when {
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
 * @return 成功 true，失败 false
 */
fun unzipToParent(
    context: Context,
    zipUri: Uri,
    parentDirUri: Uri,
    treeUri: Uri?,
    archiveName: String?,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit
): Boolean {
    val cr = context.contentResolver
    val cacheDir = File(context.cacheDir, "zip_unzip_${System.currentTimeMillis()}").apply { mkdirs() }
    val zipFile = File(cacheDir, "archive.zip")
    try {
        cr.openInputStream(zipUri)?.use { input ->
            zipFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return false
        if (isRarName(archiveName) || hasRarSignature(zipFile)) {
            return unrarToParent(context, zipFile, parentDirUri, treeUri, password, setProgress)
        }

        val zip = ZipFile(zipFile)
        if (zip.isEncrypted) {
            if (password == null || password.isEmpty()) return false
            zip.setPassword(password)
        }
        val total = zip.fileHeaders.size.coerceAtLeast(1)
        setProgress(0, total)
        val extractDir = File(cacheDir, "extract").apply { mkdirs() }
        zip.extractAll(extractDir.path)
        var current = 0
        // 解压到目标目录时，只复制 zip 顶层内容，不要多出一层 extract 目录。
        // 进度按顶层条目数更新，不传 onEach 避免递归时重复计数导致 current > total。
        extractDir.listFiles()?.orEmpty()?.forEach { child ->
            copyLocalDirToDocument(context, child, parentDirUri, treeUri) { }
            current++
            setProgress(current, total)
        }
        return true
    } catch (e: Exception) {
        return false
    } finally {
        zipFile.delete()
        cacheDir.deleteRecursively()
    }
}

private fun unrarToParent(
    context: Context,
    rarFile: File,
    parentDirUri: Uri,
    treeUri: Uri?,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit
): Boolean {
    val tempExtractDir = File(rarFile.parentFile, "extract").apply {
        deleteRecursively()
        mkdirs()
    }
    var archive: Archive? = null
    return try {
        archive = openRarArchive(rarFile, password)
        if (archive == null) return false
        if (isRarArchivePasswordProtected(archive) && (password == null || password.isEmpty())) return false

        val headers = archive.fileHeaders.orEmpty()
        val total = headers.size.coerceAtLeast(1)
        setProgress(0, total)
        var current = 0
        headers.forEach { header ->
            val path = sanitizeArchivePath(rarEntryName(header))
            if (path.isEmpty()) {
                current++
                setProgress(current, total)
                return@forEach
            }
            val outFile = File(tempExtractDir, path)
            if (header.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { output ->
                    archive.extractFile(header, output)
                }
            }
            current++
            setProgress(current, total)
        }

        // RAR 与 ZIP 保持一致：只复制归档顶层内容到目标目录。
        tempExtractDir.listFiles()?.orEmpty()?.forEach { child ->
            copyLocalDirToDocument(context, child, parentDirUri, treeUri) { }
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "unrarToParent failed", e)
        false
    } finally {
        try {
            archive?.close()
        } catch (_: Exception) {}
        tempExtractDir.deleteRecursively()
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
    onEach: () -> Unit
): Uri? {
    val cr = context.contentResolver
    val parentResolved = resolveParentForZip(context, parentDocUri, treeUri) ?: return null
    return if (local.isDirectory) {
        val newDirUri = try {
            DocumentsContract.createDocument(cr, parentResolved, DocumentsContract.Document.MIME_TYPE_DIR, local.name)
        } catch (_: Exception) { null } ?: return null
        local.listFiles()?.orEmpty()?.forEach { child ->
            copyLocalDirToDocument(context, child, newDirUri, treeUri, onEach)
            onEach()
        }
        newDirUri
    } else {
        val mime = "application/octet-stream"
        val newUri = try {
            DocumentsContract.createDocument(cr, parentResolved, mime, local.name)
        } catch (_: Exception) { null } ?: return null
        local.inputStream().use { inp ->
            cr.openOutputStream(newUri)?.use { inp.copyTo(it) }
        }
        onEach()
        newUri
    }
}

private fun resolveParentForZip(context: Context, targetParentUri: Uri, treeUri: Uri?): Uri? {
    val s = targetParentUri.toString()
    return when {
        s.contains("/tree/") && s.contains("/document/") -> try {
            val treeParsed = Uri.parse(s.substringBefore("/document/"))
            val docId = DocumentsContract.getDocumentId(targetParentUri)
            DocumentsContract.buildDocumentUriUsingTree(treeParsed, docId)
        } catch (_: Exception) { targetParentUri }
        DocumentsContract.isTreeUri(targetParentUri) -> DocumentsContract.buildDocumentUriUsingTree(
            targetParentUri,
            DocumentsContract.getTreeDocumentId(targetParentUri)
        )
        treeUri != null -> try {
            val docId = DocumentsContract.getDocumentId(targetParentUri)
            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        } catch (_: Exception) { targetParentUri }
        else -> targetParentUri
    }
}

/**
 * 将若干文件/目录压缩为一个 zip，保存到指定目录。
 * @param sourceUris 要压缩的项（文件或目录）的 content URI
 * @param parentDirUri zip 输出所在目录
 * @param zipFileName 生成的 zip 文件名（含 .zip）
 * @param password 加密密码，无密码传 null
 * @param setProgress (current, total) 当前项/总项数
 * @return 成功 true
 */
fun compressToZip(
    context: Context,
    sourceUris: List<Uri>,
    parentDirUri: Uri,
    treeUri: Uri?,
    zipFileName: String,
    password: CharArray?,
    setProgress: (Int, Int) -> Unit
): Boolean {
    val cr = context.contentResolver
    val cacheDir = File(context.cacheDir, "zip_compress_${System.currentTimeMillis()}").apply { mkdirs() }
    val zipPath = File(cacheDir, zipFileName)
    try {
        val total = sourceUris.size
        val zip = ZipFile(zipPath).apply {
            if (password != null && password.isNotEmpty()) setPassword(password)
        }
        val params = ZipParameters().apply { compressionLevel = CompressionLevel.NORMAL }
        sourceUris.forEachIndexed { index, uri ->
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
                copyDocumentToLocal(context, resolvedUri, localEntry, treeUri)
                zip.addFolder(localEntry, params)
                localEntry.deleteRecursively()
            } else {
                cr.openInputStream(resolvedUri)?.use { input ->
                    localEntry.outputStream().use { input.copyTo(it) }
                } ?: run {
                    Log.e(TAG, "compressToZip: openInputStream failed for $resolvedUri")
                    return false
                }
                zip.addFile(localEntry, params)
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
        val outUri = try {
            DocumentsContract.createDocument(cr, parentResolved, MIME_ZIP, zipFileName)
        } catch (e: Exception) {
            Log.e(TAG, "compressToZip: createDocument failed", e)
            null
        } ?: return false
        zipPath.inputStream().use { inp ->
            cr.openOutputStream(outUri)?.use { inp.copyTo(it) }
        }
        return true
    } catch (e: Exception) {
        Log.e(TAG, "compressToZip failed", e)
        return false
    } finally {
        zipPath.delete()
        cacheDir.deleteRecursively()
    }
}

/** 将 DocumentFile（目录）内容递归复制到已存在的本地目录 localDir。 */
private fun copyDocumentToLocal(context: Context, docUri: Uri, localDir: File, treeUri: Uri?) {
    // 必须用树遍历得到的 DocumentFile，否则 SingleDocumentFile.listFiles() 会抛 UnsupportedOperationException
    val doc = getDirectoryViaTree(context, docUri, treeUri) ?: return
    if (!doc.isDirectory) return
    doc.listFiles()?.orEmpty()?.forEach { child ->
        val name = child.name ?: return@forEach
        val childUri = resolveDocUriWithTree(child.uri, treeUri)
        if (child.isDirectory) {
            File(localDir, name).apply { mkdirs() }.let { copyDocumentToLocal(context, childUri, it, treeUri) }
        } else {
            context.contentResolver.openInputStream(childUri)?.use { input ->
                File(localDir, name).outputStream().use { input.copyTo(it) }
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
    val cache = File(context.cacheDir, "zip_check_${System.currentTimeMillis()}.zip")
    return try {
        context.contentResolver.openInputStream(archiveUri)?.use { input ->
            cache.outputStream().use { output -> input.copyTo(output) }
        } ?: return false
        if (isRarName(archiveName) || hasRarSignature(cache)) {
            isRarEncrypted(cache)
        } else {
            ZipFile(cache).isEncrypted
        }
    } catch (_: Exception) { false }
    finally { cache.delete() }
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

/** 读取归档第一层内容（zip/rar）。 */
fun getArchiveFirstLevelEntries(
    context: Context,
    archiveUri: Uri,
    archiveName: String?,
    password: CharArray?
): ZipFirstLevelResult {
    val cache = File(context.cacheDir, "zip_list_${System.currentTimeMillis()}.zip")
    return try {
        context.contentResolver.openInputStream(archiveUri)?.use { input ->
            cache.outputStream().use { output -> input.copyTo(output) }
        } ?: return ZipFirstLevelResult.Error
        if (isRarName(archiveName) || hasRarSignature(cache)) {
            return getRarFirstLevelEntries(cache, password)
        }
        val zip = ZipFile(cache)
        if (zip.isEncrypted) {
            if (password == null || password.isEmpty()) return ZipFirstLevelResult.Encrypted
            zip.setPassword(password)
        }
        val firstLevel = mutableSetOf<String>()
        val firstLevelDirs = mutableSetOf<String>()
        for (header in zip.fileHeaders) {
            val path = header.fileName
            val top = path.substringBefore("/").ifEmpty { path }
            if (top.isNotEmpty()) {
                firstLevel.add(top)
                if (path.startsWith("$top/")) firstLevelDirs.add(top)
            }
        }
        val sortedFirst = firstLevel.sorted().map { if (it in firstLevelDirs) "$it/" else it }
        if (firstLevel.size == 1) {
            val rootName = firstLevel.single()
            val prefix = "$rootName/"
            val hasChildrenUnderRoot = zip.fileHeaders.any { it.fileName.startsWith(prefix) }
            if (hasChildrenUnderRoot) {
                val childToDir = mutableMapOf<String, Boolean>()
                for (header in zip.fileHeaders) {
                    if (!header.fileName.startsWith(prefix)) continue
                    val rest = header.fileName.removePrefix(prefix)
                    val childName = rest.substringBefore("/")
                    if (childName.isEmpty()) continue
                    val isDir = rest == "$childName/" || rest.startsWith("$childName/")
                    childToDir[childName] = childToDir[childName] == true || isDir
                }
                val children = childToDir.entries.sortedBy { it.key }.map { (name, isDir) -> if (isDir) "$name/" else name }
                return ZipFirstLevelResult.OkSingleDir(rootName, children)
            }
        }
        ZipFirstLevelResult.Ok(sortedFirst)
    } catch (_: Exception) {
        ZipFirstLevelResult.Error
    } finally {
        cache.delete()
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
fun findMdZipCacheTarget(cacheDir: File, isRstZip: Boolean): File? {
    val contentDir = File(cacheDir, "content")
    if (!contentDir.exists() || !contentDir.isDirectory) return null
    return findOrCreateRenderableFile(contentDir, isRstZip)
}

/** 根据 zip 类型确定搜索候选文件名。 */
private fun candidatesForZipType(isRstZip: Boolean): List<String> =
    if (isRstZip) listOf("index.rst", "README.rst") else listOf("index.md", "README.md")

/**
 * 在目录树中查找 index/README 文件。若未找到，自动在有效目录中生成一个包含
 * 同类文件链接列表的 index 文件。
 */
private fun findOrCreateRenderableFile(dir: File, isRstZip: Boolean): File? {
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
    indexFile.writeText(generateIndexContent(files, effectiveDir, isRstZip))
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

/** 生成 index 文件内容，包含所有同类文件的链接。 */
private fun generateIndexContent(
    files: List<Pair<String, File>>,
    baseDir: File,
    isRstZip: Boolean
): String {
    val sb = StringBuilder()
    if (isRstZip) {
        sb.appendLine("目录")
        sb.appendLine("====")
        sb.appendLine()
        for ((rel, _) in files) {
            val label = rel.substringBeforeLast('.').replace("/", " / ")
            sb.appendLine("- `$label <$rel>`_")
        }
    } else {
        sb.appendLine("# 目录")
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
        val target = findOrCreateRenderableFile(contentDir, isRstZip)
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

/** 解压 .html.zip 到缓存目录，查找 index.html。
 * @return 解压结果（indexFile 可能为 null），解压失败返回 null */
fun extractHtmlZipToCache(
    context: Context,
    zipUri: Uri,
    password: CharArray?,
    zipFileName: String
): HtmlZipExtractResult? {
    val cacheDir = getHtmlZipCacheDir(context, zipUri)
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
        val indexFile = findHtmlZipIndexFile(contentDir)
        if (indexFile == null) {
            Log.w(TAG, "extractHtmlZipToCache: no index.html found in $zipUri")
        }
        File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
        if (encrypted) File(cacheDir, ".encrypted").createNewFile()
        return HtmlZipExtractResult(cacheDir, contentDir, indexFile, encrypted)
    } catch (e: Exception) {
        Log.e(TAG, "extractHtmlZipToCache failed", e)
        cacheDir.deleteRecursively()
        return null
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

private val PIC_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

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

private fun tryZipPassword(zipFile: java.io.File, password: CharArray): Boolean {
    return try {
        val zip = ZipFile(zipFile)
        if (!zip.isEncrypted) return true
        zip.setPassword(password)
        val imagePaths = zip.fileHeaders.asSequence()
            .filter { !it.isDirectory }
            .map { it.fileName }
            .filter { path ->
                val ext = path.substringAfterLast('.', "").lowercase()
                ext in PIC_IMAGE_EXTENSIONS
            }
            .sorted()
            .toList()
        val first = imagePaths.firstOrNull() ?: return false
        val header = zip.getFileHeader(first) ?: return false
        zip.getInputStream(header).use { it.read() }
        true
    } catch (_: Exception) {
        false
    }
}

fun cleanPicZipCache(context: Context, zipUri: Uri) {
    getPicZipCacheDir(context, zipUri).deleteRecursively()
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
private fun parseEpubOpf(opfFile: File, opfDir: File): Pair<EpubBookInfo, List<EpubChapter>>? {
    if (!opfFile.exists()) return null
    val content = opfFile.readText()

    // 解析 metadata
    var title = "未知书名"
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
    val manifestRegex = Regex("""<item[^>]*id\s*=\s*"([^"]+)"[^>]*href\s*=\s*"([^"]+)"[^>]*/?>""", RegexOption.IGNORE_CASE)
    manifestRegex.findAll(content).forEach { match ->
        val id = match.groupValues[1]
        val href = match.groupValues[2]
        manifest[id] = href
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
private fun parseEpubNavigation(contentDir: File, opfDir: File, chapters: List<EpubChapter>): List<EpubChapter> {
    // 尝试查找 NCX 文件
    val ncxFile = opfDir.listFiles()?.find { it.name.endsWith(".ncx", ignoreCase = true) }
    if (ncxFile != null && ncxFile.exists()) {
        try {
            val ncxContent = ncxFile.readText()
            val navPointRegex = Regex("""<navPoint[^>]*>.*?<navLabel>.*?<text>([^<]*)</text>.*?</navLabel>.*?<content[^>]*src\s*=\s*"([^"]+)""""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val navMap = mutableMapOf<String, String>()
            navPointRegex.findAll(ncxContent).iterator().forEach { match ->
                val title = match.groupValues[1].trim()
                val src = match.groupValues[2].substringBefore("#").substringBefore("?")
                navMap[src] = title
            }
            // 更新章节标题
            return chapters.map { chapter ->
                val hrefName = chapter.href.substringAfterLast("/")
                val title = navMap[hrefName] ?: navMap[chapter.href] ?: chapter.href.substringBeforeLast(".")
                chapter.copy(title = title)
            }
        } catch (_: Exception) { }
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
 * @return 解压结果，解压失败返回 null
 */
fun extractEpubToCache(
    context: Context,
    epubUri: Uri,
    password: CharArray?,
    epubFileName: String? = null
): EpubExtractResult? {
    val cacheDir = getEpubCacheDir(context, epubUri)
    cacheDir.deleteRecursively()
    cacheDir.mkdirs()
    val tmpZip = File(cacheDir, "__archive.zip")
    try {
        context.contentResolver.openInputStream(epubUri)?.use { input ->
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

        // 解析 container.xml 获取 OPF 路径
        val opfRelativePath = parseEpubContainer(contentDir)
        if (opfRelativePath == null) {
            Log.w(TAG, "extractEpubToCache: META-INF/container.xml not found or invalid")
            return null
        }

        val opfFile = File(contentDir, opfRelativePath)
        val opfDir = opfFile.parentFile ?: contentDir

        // 解析 OPF
        val parseResult = parseEpubOpf(opfFile, opfDir)
        if (parseResult == null) {
            Log.w(TAG, "extractEpubToCache: Failed to parse OPF file")
            return null
        }
        val (bookInfo, rawChapters) = parseResult

        // 尝试获取章节标题
        val chapters = parseEpubNavigation(contentDir, opfDir, rawChapters)

        if (chapters.isEmpty()) {
            Log.w(TAG, "extractEpubToCache: No chapters found in EPUB")
            return null
        }

        File(cacheDir, ".cache_ts").writeText(System.currentTimeMillis().toString())
        if (encrypted) File(cacheDir, ".encrypted").createNewFile()

        return EpubExtractResult(cacheDir, contentDir, opfDir, bookInfo, chapters, encrypted)
    } catch (e: Exception) {
        Log.e(TAG, "extractEpubToCache failed", e)
        cacheDir.deleteRecursively()
        return null
    } finally {
        if (tmpZip.exists()) tmpZip.delete()
    }
}

/** 从已有缓存加载 EPUB 信息（不解压）。 */
fun loadEpubFromCache(cacheDir: File): EpubExtractResult? {
    if (!cacheDir.exists()) return null
    val contentDir = File(cacheDir, "content")
    if (!contentDir.exists()) return null

    val encrypted = isEpubCacheEncrypted(cacheDir)

    // 解析 container.xml 获取 OPF 路径
    val opfRelativePath = parseEpubContainer(contentDir) ?: return null
    val opfFile = File(contentDir, opfRelativePath)
    val opfDir = opfFile.parentFile ?: contentDir

    val parseResult = parseEpubOpf(opfFile, opfDir) ?: return null
    val (bookInfo, rawChapters) = parseResult
    val chapters = parseEpubNavigation(contentDir, opfDir, rawChapters)

    return EpubExtractResult(cacheDir, contentDir, opfDir, bookInfo, chapters, encrypted)
}

/** 获取 EPUB 章节的完整文件路径。 */
fun getEpubChapterFile(result: EpubExtractResult, chapter: EpubChapter): File? {
    val file = File(result.opfDir, chapter.href)
    return if (file.exists()) file else null
}

/** 判断文件名是否为 EPUB 文件。 */
fun isEpubFile(name: String): Boolean = name.endsWith(".epub", ignoreCase = true)
