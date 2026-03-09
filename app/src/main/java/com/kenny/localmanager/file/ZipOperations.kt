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
    val targetFile: File,
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

/** 在已有缓存目录中查找可渲染的 md/rst 文件，未找到返回 null。 */
fun findMdZipCacheTarget(cacheDir: File): File? {
    val contentDir = File(cacheDir, "content")
    if (!contentDir.exists() || !contentDir.isDirectory) return null
    return findRenderableFile(contentDir)
}

private val MD_ZIP_CANDIDATES = listOf("index.md", "index.rst", "README.md", "README.rst")

/** 在目录树中查找第一个可渲染的 md/rst 文件（按 index.md > index.rst > README.md > README.rst 优先级）。 */
private fun findRenderableFile(dir: File): File? {
    for (name in MD_ZIP_CANDIDATES) {
        findFileByName(dir, name)?.let { return it }
    }
    return null
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
 * 解压 .md.zip 到缓存目录，查找可渲染的 md/rst 文件。
 * @return 解压结果，失败返回 null
 */
fun extractMdZipToCache(
    context: Context,
    zipUri: Uri,
    password: CharArray?
): MdZipExtractResult? {
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
        val target = findRenderableFile(contentDir) ?: run {
            Log.w(TAG, "extractMdZipToCache: no renderable file found in $zipUri")
            cacheDir.deleteRecursively()
            return null
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
