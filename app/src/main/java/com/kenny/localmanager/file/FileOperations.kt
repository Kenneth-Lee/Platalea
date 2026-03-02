package com.kenny.localmanager.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

fun ContentResolver.openInputStreamSafe(uri: Uri): InputStream? =
    try { openInputStream(uri) } catch (_: Exception) { null }

fun ContentResolver.openOutputStreamSafe(uri: Uri): OutputStream? =
    try { openOutputStream(uri) } catch (_: Exception) { null }

fun DocumentFile.listFilesSafe(): Array<DocumentFile> =
    try { listFiles() ?: emptyArray() } catch (_: Exception) { emptyArray() }

fun ContentResolver.getDisplayName(uri: Uri): String? =
    try {
        query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }

fun ContentResolver.getTypeSafe(uri: Uri): String? =
    try { getType(uri) } catch (_: Exception) { null }

/**
 * 从文件或目录的 URI 获取要打开的目录 URI。
 * 若为文件则返回其父目录；若为目录则返回自身。
 */
fun getDirectoryToOpen(context: Context, uri: Uri): Uri? {
    return try {
        when (uri.scheme) {
            "content" -> {
                val doc = DocumentFile.fromSingleUri(context, uri) ?: return null
                if (doc.isDirectory) uri
                else if (DocumentsContract.isDocumentUri(context, uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val lastSlash = docId.lastIndexOf('/')
                    val parentId = when {
                        lastSlash > 0 -> docId.substring(0, lastSlash)
                        docId.contains(":") -> docId.substringBefore(":")  // e.g. "primary:file" -> "primary"
                        else -> return doc.parentFile?.uri
                    }
                    DocumentsContract.buildDocumentUri(uri.authority ?: "com.android.externalstorage.documents", parentId)
                } else doc.parentFile?.uri
            }
            "file" -> {
                val path = uri.path ?: return null
                val file = java.io.File(path)
                if (file.isDirectory) uri
                else file.parentFile?.let { Uri.fromFile(it) }
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

fun ContentResolver.deleteDocument(uri: Uri): Boolean =
    try { DocumentsContract.deleteDocument(this, uri) } catch (_: Exception) { false }

fun ContentResolver.renameDocument(uri: Uri, newName: String): Uri? =
    try {
        DocumentsContract.renameDocument(this, uri, newName)
    } catch (_: Exception) { null }

/**
 * 从指定偏移读取最多 maxLen 字节。
 * @return 读到的字节数组，失败或到达末尾返回 null 或不足 maxLen 的数组
 */
fun ContentResolver.readBytesFromOffset(uri: Uri, offset: Long, maxLen: Int): ByteArray? =
    try {
        openInputStream(uri)?.use { input ->
            var remaining = offset
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped <= 0) break
                remaining -= skipped
            }
            val buf = ByteArray(maxLen)
            val n = input.read(buf)
            if (n <= 0) byteArrayOf() else buf.copyOf(n)
        }
    } catch (_: Exception) { null }

/**
 * 在指定偏移处写入字节（覆盖该位置起的内容）。需要 provider 支持 "rw" 模式。
 * @return 是否成功
 */
fun writeBytesAtOffset(context: Context, uri: Uri, offset: Long, bytes: ByteArray): Boolean =
    try {
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).use { out ->
                out.channel.use { ch ->
                    ch.position(offset)
                    ch.write(ByteBuffer.wrap(bytes))
                }
            }
            true
        } ?: false
    } catch (_: Exception) { false }

/**
 * 覆盖写入整个文件内容。
 * @return 是否成功
 */
fun ContentResolver.writeBytesFull(uri: Uri, bytes: ByteArray): Boolean =
    try {
        openOutputStream(uri)?.use { it.write(bytes) } != null
    } catch (_: Exception) { false }

/**
 * 在目标目录下创建新文件并写入字节内容。
 * @return 成功返回 true
 */
fun createFileWithBytes(
    context: Context,
    targetParentUri: Uri,
    treeUri: Uri?,
    fileName: String,
    mimeType: String,
    bytes: ByteArray
): Boolean {
    val cr = context.contentResolver
    val parentCandidates = if (treeUri != null && !DocumentsContract.isTreeUri(targetParentUri)) {
        val treeBuilt = resolveParentDocumentUri(targetParentUri, treeUri)
        listOf(targetParentUri, treeBuilt).filterNotNull().distinct()
    } else {
        listOf(resolveParentDocumentUri(targetParentUri, treeUri) ?: return false)
    }
    for (parentDocUri in parentCandidates) {
        try {
            val newUri = DocumentsContract.createDocument(cr, parentDocUri, mimeType, fileName)
                ?: continue
            cr.openOutputStream(newUri)?.use { it.write(bytes) }
            return true
        } catch (_: Exception) { }
    }
    return false
}

/**
 * 解析为目标目录的 document URI，供 createDocument 使用。
 * 重要：若 URI 同时含 /tree/ 与 /document/（树下某文档），须用 getDocumentId 取当前目录 ID，
 * 否则 isTreeUri 为 true 时用 getTreeDocumentId 会误取树根，导致拷贝/移动到上级目录。
 */
private fun resolveParentDocumentUri(targetParentUri: Uri, treeUri: Uri?): Uri? {
    val s = targetParentUri.toString()
    return when {
        s.contains("/tree/") && s.contains("/document/") -> try {
            val treeUriParsed = Uri.parse(s.substringBefore("/document/"))
            val docId = DocumentsContract.getDocumentId(targetParentUri)
            DocumentsContract.buildDocumentUriUsingTree(treeUriParsed, docId)
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
 * 在 parentDocUri 下创建文件并写入内容；若失败返回 null。
 * 若无法打开源或写入目标则返回 null（避免产生空文件）。
 */
private fun createFileUnder(
    context: Context,
    parentDocUri: Uri,
    mime: String,
    name: String,
    sourceUri: Uri,
    log: ((String) -> Unit)?
): Uri? {
    val cr = context.contentResolver
    val newFileUri = try {
        DocumentsContract.createDocument(cr, parentDocUri, mime, name)
    } catch (e: Exception) {
        log?.invoke("  createDocument 异常: ${e.message}")
        null
    } ?: return null
    val input = cr.openInputStreamSafe(sourceUri)
    if (input == null) {
        log?.invoke("  无法打开源文件(空): $name")
        try { DocumentsContract.deleteDocument(cr, newFileUri) } catch (_: Exception) { }
        return null
    }
    var written = false
    input.use { inp ->
        cr.openOutputStreamSafe(newFileUri)?.use { out ->
            inp.copyTo(out)
            written = true
        }
    }
    if (!written) {
        log?.invoke("  无法写入目标: $name")
        try { DocumentsContract.deleteDocument(cr, newFileUri) } catch (_: Exception) { }
        return null
    }
    return newFileUri
}

/**
 * 将单个文档（文件或目录）拷贝到目标父目录。目录会递归拷贝。
 * @param log 调试日志回调，非 null 时输出关键步骤
 * @return 新文档的 Uri，失败返回 null
 */
fun copyDocumentTo(
    context: Context,
    sourceUri: Uri,
    targetParentUri: Uri,
    treeUri: Uri? = null,
    log: ((String) -> Unit)? = null
): Uri? {
    val cr = context.contentResolver
    val source = DocumentFile.fromSingleUri(context, sourceUri) ?: return null
    val name = source.name ?: return null
    val parentCandidates = if (treeUri != null && !DocumentsContract.isTreeUri(targetParentUri)) {
        val treeBuilt = resolveParentDocumentUri(targetParentUri, treeUri)
        listOf(targetParentUri, treeBuilt).filterNotNull().distinct()
    } else {
        listOf(resolveParentDocumentUri(targetParentUri, treeUri) ?: return null)
    }
    for (parentDocUri in parentCandidates) {
        if (source.isDirectory) {
            log?.invoke("  [拷贝目录] $name 尝试 parent=$parentDocUri")
            val newDirUri = try {
                DocumentsContract.createDocument(
                    cr,
                    parentDocUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name
                )
            } catch (e: Exception) {
                log?.invoke("  $name 建目录异常: ${e.message}")
                null
            }
            if (newDirUri != null) {
                var children = source.listFilesSafe().toList()
                if (children.isEmpty() && treeUri != null) {
                    // 部分 provider 对 document URI 的 listFiles() 返回空，用树查询列举子项
                    val parentId = try { DocumentsContract.getDocumentId(sourceUri) } catch (_: Exception) { null }
                    if (parentId != null) {
                        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                        val list = mutableListOf<Triple<Uri, String, String>>() // uri, displayName, mimeType
                        try {
                            cr.query(
                                childUri,
                                arrayOf(
                                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                    DocumentsContract.Document.COLUMN_MIME_TYPE
                                ),
                                null, null, null
                            )?.use { c ->
                                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                                if (idIdx >= 0 && nameIdx >= 0 && mimeIdx >= 0) {
                                    while (c.moveToNext()) {
                                        val id = c.getString(idIdx) ?: continue
                                        list.add(
                                            Triple(
                                                DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                                                c.getString(nameIdx) ?: "",
                                                c.getString(mimeIdx) ?: "application/octet-stream"
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (_: Exception) { }
                        if (list.isNotEmpty()) {
                            log?.invoke("  [拷贝目录] $name 通过树查询子项数=${list.size}")
                            list.forEach { (childUri, cName, mimeType) ->
                                log?.invoke("    子项: $cName isDir=${mimeType == DocumentsContract.Document.MIME_TYPE_DIR}")
                                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                                    val res = copyDocumentTo(context, childUri, newDirUri, treeUri, log)
                                    if (res == null) log?.invoke("    子项失败: $cName")
                                } else {
                                    val created = createFileUnder(context, newDirUri, mimeType, cName.ifEmpty { "<无名>" }, childUri, log)
                                    if (created == null) log?.invoke("    子项失败: $cName")
                                }
                            }
                            return newDirUri
                        }
                    }
                    log?.invoke("  [拷贝目录] $name 子项数=0 (listFiles 空且树查询无结果)")
                } else {
                    log?.invoke("  [拷贝目录] $name 已建目录，子项数=${children.size}")
                    children.forEach { child ->
                        val cName = child.name ?: "<无名>"
                        log?.invoke("    子项: $cName isDir=${child.isDirectory}")
                        val res = copyDocumentTo(context, child.uri, newDirUri, treeUri, log)
                        if (res == null) log?.invoke("    子项失败: $cName")
                    }
                }
                return newDirUri
            }
        } else {
            val mime = cr.getTypeSafe(sourceUri) ?: "application/octet-stream"
            val newFileUri = createFileUnder(context, parentDocUri, mime, name, sourceUri, log)
            if (newFileUri != null) {
                log?.invoke("  $name -> 成功 parent=$parentDocUri")
                return newFileUri
            }
        }
    }
    log?.invoke("  $name -> 失败")
    return null
}

/**
 * 将单个文档移动到目标父目录。目录始终用「递归拷贝后删除」以保留子内容；
 * 文件先尝试系统 moveDocument，不支持则拷贝后删除源。
 * @param log 调试日志回调
 */
fun moveDocumentTo(
    context: Context,
    sourceUri: Uri,
    targetParentUri: Uri,
    treeUri: Uri? = null,
    log: ((String) -> Unit)? = null
): Boolean {
    val cr = context.contentResolver
    val source = DocumentFile.fromSingleUri(context, sourceUri) ?: return false
    // 目录：系统 moveDocument 可能不迁移子节点导致内容丢失，统一用拷贝后删除
    if (source.isDirectory) {
        val dirName = source.name ?: sourceUri.lastPathSegment ?: "?"
        log?.invoke("[移动-目录] $dirName 开始递归拷贝 -> $targetParentUri")
        val copied = copyDocumentTo(context, sourceUri, targetParentUri, treeUri, log)
        if (copied == null) {
            log?.invoke("[移动-目录] $dirName 拷贝失败，不删除源")
            return false
        }
        log?.invoke("[移动-目录] $dirName 拷贝完成，正在删除源...")
        var deleted = false
        try {
            DocumentsContract.deleteDocument(cr, sourceUri)
            deleted = true
        } catch (e: Exception) {
            log?.invoke("  删除源目录异常: ${e.message}")
        }
        if (deleted) log?.invoke("[移动-目录] $dirName 完成(已删除源)")
        else log?.invoke("[移动-目录] $dirName 拷贝成功但删除源失败")
        return deleted
    }
    val sourceParent = source.parentFile?.uri
    if (sourceParent != null) {
        val targetCandidates = if (treeUri != null && !DocumentsContract.isTreeUri(targetParentUri)) {
            listOf(targetParentUri, resolveParentDocumentUri(targetParentUri, treeUri)).filterNotNull().distinct()
        } else {
            listOf(resolveParentDocumentUri(targetParentUri, treeUri) ?: return false)
        }
        for (targetDocUri in targetCandidates) {
            try {
                val moved = DocumentsContract.moveDocument(cr, sourceUri, sourceParent, targetDocUri)
                if (moved != null) {
                    log?.invoke("  moveDocument 成功 target=$targetDocUri")
                    return true
                }
            } catch (e: Exception) {
                log?.invoke("  moveDocument 异常: ${e.message}")
            }
        }
    }
    val copied = copyDocumentTo(context, sourceUri, targetParentUri, treeUri, log)
    return if (copied != null) {
        try {
            DocumentsContract.deleteDocument(cr, sourceUri)
        } catch (_: Exception) {
            false
        }
    } else false
}
