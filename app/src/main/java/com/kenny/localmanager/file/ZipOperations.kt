package com.kenny.localmanager.file

import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import java.io.File

private const val TAG = "ZipOperations"
private const val MIME_ZIP = "application/zip"

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
    val cache = File(context.cacheDir, "zip_check_${System.currentTimeMillis()}.zip")
    return try {
        context.contentResolver.openInputStream(zipUri)?.use { input ->
            cache.outputStream().use { output -> input.copyTo(output) }
        } ?: return false
        ZipFile(cache).isEncrypted
    } catch (_: Exception) { false }
    finally { cache.delete() }
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
