package com.kenny.localmanager.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

private const val TAG = "FileOperations"

fun ContentResolver.openInputStreamSafe(uri: Uri): InputStream? =
    try { openInputStream(uri) } catch (_: Exception) { null }

fun ContentResolver.openOutputStreamSafe(uri: Uri): OutputStream? =
    try { openOutputStream(uri) } catch (_: Exception) { null }

@Suppress("USELESS_ELVIS")
fun DocumentFile.listFilesSafe(): Array<DocumentFile> =
    try { listFiles() ?: emptyArray() } catch (_: Exception) { emptyArray() }

/**
 * Fast directory listing using a single ContentResolver cursor query.
 * Much faster than DocumentFile.listFiles() which issues per-file queries.
 */
fun listChildrenFast(context: Context, treeUriStr: String): List<DocumentFileModel> {
    val treeUri = Uri.parse(treeUriStr)
    val docId = if (treeUriStr.contains("/document/")) {
        DocumentsContract.getDocumentId(treeUri)
    } else {
        DocumentsContract.getTreeDocumentId(treeUri)
    }
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE
    )
    val result = mutableListOf<DocumentFileModel>()
    try {
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modifiedIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx) ?: ""
                val lastModified = cursor.getLong(modifiedIdx)
                val size = cursor.getLong(sizeIdx)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                result.add(
                    DocumentFileModel(
                        name = name,
                        isDirectory = isDir,
                        uri = childUri,
                        lastModified = lastModified,
                        size = size
                    )
                )
            }
        }
    } catch (_: Exception) {
        // Fall back handled by caller
    }
    return result
}

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

private const val TRASH_DIR_NAME = ".Trash"

/**
 * 获取根目录下回收站 URI（不创建）。不存在返回 null。
 */
fun getTrashUriIfExists(context: Context, rootUri: Uri, treeUri: Uri?): Uri? {
    val parentDocUri = resolveParentDocumentUri(rootUri, treeUri) ?: rootUri
    return findChildByName(context, parentDocUri, TRASH_DIR_NAME)
}

/**
 * 回收站中的项目数量。
 */
fun getTrashItemCount(context: Context, rootUri: Uri, treeUri: Uri?): Int {
    val trashUri = getTrashUriIfExists(context, rootUri, treeUri) ?: return 0
    val parent = if (trashUri.toString().contains("/tree/")) {
        DocumentFile.fromTreeUri(context, trashUri)
    } else {
        DocumentFile.fromSingleUri(context, trashUri)
    } ?: return 0
    return parent.listFilesSafe().size
}

/**
 * 递归删除文档（目录先删子项）。
 */
private fun deleteDocumentRecursive(context: Context, uri: Uri, treeUri: Uri?): Boolean {
    val cr = context.contentResolver
    val doc = if (uri.toString().contains("/tree/")) {
        DocumentFile.fromTreeUri(context, uri)
    } else {
        DocumentFile.fromSingleUri(context, uri)
    } ?: return false
    if (doc.isDirectory) {
        doc.listFilesSafe().forEach { child ->
            deleteDocumentRecursive(context, child.uri, treeUri)
        }
    }
    return try {
        DocumentsContract.deleteDocument(cr, uri)
    } catch (_: Exception) {
        false
    }
}

/**
 * 清空回收站：递归删除回收站内所有内容。
 * @return 成功返回 true
 */
fun emptyTrash(context: Context, rootUri: Uri, treeUri: Uri? = null): Boolean {
    val trashUri = getTrashUriIfExists(context, rootUri, treeUri) ?: return true
    val parent = if (trashUri.toString().contains("/tree/")) {
        DocumentFile.fromTreeUri(context, trashUri)
    } else {
        DocumentFile.fromSingleUri(context, trashUri)
    } ?: return false
    var allOk = true
    parent.listFilesSafe().forEach { child ->
        if (!deleteDocumentRecursive(context, child.uri, treeUri)) allOk = false
    }
    return allOk
}

/**
 * 在根目录下获取或创建回收站目录。
 * @param rootUri 根目录 URI（如 OpenDocumentTree 返回的）
 * @return 回收站目录的 document URI，失败返回 null
 */
fun getOrCreateTrashUri(context: Context, rootUri: Uri, treeUri: Uri?): Uri? {
    val cr = context.contentResolver
    val parentDocUri = resolveParentDocumentUri(rootUri, treeUri) ?: rootUri
    val existing = findChildByName(context, parentDocUri, TRASH_DIR_NAME)
    if (existing != null) return existing
    return try {
        DocumentsContract.createDocument(cr, parentDocUri, DocumentsContract.Document.MIME_TYPE_DIR, TRASH_DIR_NAME)
    } catch (_: Exception) {
        null
    }
}

/**
 * 将文件或目录移到回收站（根目录下的 .Trash）。若目标已存在同名则附加时间戳避免覆盖。
 * @return 成功返回 true
 */
fun moveToTrash(
    context: Context,
    sourceUri: Uri,
    rootUri: Uri,
    treeUri: Uri? = null,
    log: ((String) -> Unit)? = null
): Boolean {
    val cr = context.contentResolver
    val trashUri = getOrCreateTrashUri(context, rootUri, treeUri) ?: run {
        log?.invoke("  moveToTrash: 无法创建回收站目录")
        return false
    }
    val source = DocumentFile.fromSingleUri(context, sourceUri) ?: return false
    if (source.name == TRASH_DIR_NAME) {
        log?.invoke("  moveToTrash: 不能将回收站移入自身")
        return false
    }
    val name = source.name ?: "unknown"
    val baseName = name.substringBeforeLast(".")
    val ext = if (name.contains(".")) ".${name.substringAfterLast(".")}" else ""
    var destName = name
    while (findChildByName(context, trashUri, destName) != null) {
        destName = "${baseName}_${System.currentTimeMillis()}$ext"
    }
    return when {
        source.isDirectory -> {
            val newDirUri = try {
                DocumentsContract.createDocument(cr, trashUri, DocumentsContract.Document.MIME_TYPE_DIR, destName)
            } catch (_: Exception) {
                log?.invoke("  moveToTrash: 无法创建目录 $destName")
                return false
            } ?: return false
            var children = source.listFilesSafe().toList()
            if (children.isEmpty() && treeUri != null) {
                val parentId = try { DocumentsContract.getDocumentId(sourceUri) } catch (_: Exception) { null }
                if (parentId != null) {
                    val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                    val list = mutableListOf<DocumentFile>()
                    try {
                        cr.query(childUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
                            ?.use { c ->
                                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                                if (idIdx >= 0 && nameIdx >= 0 && mimeIdx >= 0) {
                                    while (c.moveToNext()) {
                                        val id = c.getString(idIdx) ?: continue
                                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                                        val doc = DocumentFile.fromSingleUri(context, docUri) ?: continue
                                        list.add(doc)
                                    }
                                }
                            }
                    } catch (_: Exception) { }
                    if (list.isNotEmpty()) children = list
                }
            }
            var allOk = true
            children.forEach { child ->
                if (copyDocumentTo(context, child.uri, newDirUri, treeUri, log) == null) {
                    allOk = false
                    log?.invoke("  moveToTrash: 子项拷贝失败 ${child.name}")
                }
            }
            if (allOk) {
                try {
                    DocumentsContract.deleteDocument(cr, sourceUri)
                    true
                } catch (_: Exception) {
                    log?.invoke("  moveToTrash: 拷贝成功但删除源目录失败")
                    false
                }
            } else false
        }
        else -> {
            val mime = cr.getTypeSafe(sourceUri) ?: "application/octet-stream"
            val created = createFileUnder(context, trashUri, mime, destName, sourceUri, log)
            if (created != null) {
                try {
                    DocumentsContract.deleteDocument(cr, sourceUri)
                    true
                } catch (_: Exception) {
                    try { DocumentsContract.deleteDocument(cr, created) } catch (_: Exception) { }
                    log?.invoke("  moveToTrash: 拷贝成功但删除源失败，已回滚")
                    false
                }
            } else false
        }
    }
}

/**
 * 判断 uri 是否在 parentUri 目录下（或就是 parentUri 自身）。
 */
fun isInsideDirectory(uri: Uri, parentUri: Uri): Boolean {
    return try {
        val id = DocumentsContract.getDocumentId(uri)
        val parentId = DocumentsContract.getDocumentId(parentUri)
        id == parentId || id.startsWith("$parentId/")
    } catch (_: Exception) {
        false
    }
}

/**
 * 从回收站恢复到根目录（.Trash 的父目录）。若有同名则附加时间戳。
 * @return 成功返回 true
 */
fun restoreFromTrash(
    context: Context,
    sourceUri: Uri,
    rootUri: Uri,
    treeUri: Uri? = null,
    log: ((String) -> Unit)? = null
): Boolean {
    val trashUri = getTrashUriIfExists(context, rootUri, treeUri) ?: return false
    if (!isInsideDirectory(sourceUri, trashUri)) return false
    val source = DocumentFile.fromSingleUri(context, sourceUri) ?: return false
    if (source.name == TRASH_DIR_NAME) return false
    val parentDocUri = resolveParentDocumentUri(rootUri, treeUri) ?: rootUri
    val name = source.name ?: "unknown"
    val baseName = name.substringBeforeLast(".")
    val ext = if (name.contains(".")) ".${name.substringAfterLast(".")}" else ""
    var destName = name
    while (findChildByName(context, parentDocUri, destName) != null) {
        destName = "${baseName}_${System.currentTimeMillis()}$ext"
    }
    return if (source.isDirectory) {
        val newDirUri = try {
            DocumentsContract.createDocument(context.contentResolver, parentDocUri, DocumentsContract.Document.MIME_TYPE_DIR, destName)
        } catch (_: Exception) { null } ?: return false
        var children = source.listFilesSafe().toList()
        if (children.isEmpty() && treeUri != null) {
            val parentId = try { DocumentsContract.getDocumentId(sourceUri) } catch (_: Exception) { null }
            if (parentId != null) {
                val cr = context.contentResolver
                val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                val list = mutableListOf<DocumentFile>()
                try {
                    cr.query(childUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { c ->
                        val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        if (idIdx >= 0) {
                            while (c.moveToNext()) {
                                val id = c.getString(idIdx) ?: continue
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                                DocumentFile.fromSingleUri(context, docUri)?.let { list.add(it) }
                            }
                        }
                    }
                } catch (_: Exception) { }
                if (list.isNotEmpty()) children = list
            }
        }
        var allOk = true
        children.forEach { child ->
            if (copyDocumentTo(context, child.uri, newDirUri, treeUri, log) == null) allOk = false
        }
        if (allOk) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, sourceUri)
                true
            } catch (_: Exception) { false }
        } else false
    } else {
        val mime = context.contentResolver.getTypeSafe(sourceUri) ?: "application/octet-stream"
        val created = createFileUnder(context, parentDocUri, mime, destName, sourceUri, log)
        if (created != null) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, sourceUri)
                true
            } catch (_: Exception) {
                try { DocumentsContract.deleteDocument(context.contentResolver, created) } catch (_: Exception) { }
                false
            }
        } else false
    }
}

/**
 * 若文档树根下存在指定名称的子目录则递归删除。
 * @param treeRootUri 文档树根 URI（含 /tree/）
 * @param childDirName 子目录名（如 ".sysgit"）
 * @return 若存在并已删除或本不存在返回 true，失败返回 false
 */
fun deleteTreeChildDirIfExists(context: Context, treeRootUri: Uri, childDirName: String): Boolean {
    val rootDocId = try { DocumentsContract.getTreeDocumentId(treeRootUri) } catch (_: Exception) { return true }
    val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeRootUri, rootDocId) ?: return true
    val childUri = findChildByName(context, rootDocUri, childDirName) ?: return true
    return deleteDocumentRecursive(context, childUri, treeRootUri)
}

/**
 * 在目录中查找指定名称的子文档。
 * @return 子文档的 Uri，不存在返回 null
 */
fun findChildByName(context: Context, parentUri: Uri, childName: String): Uri? {
    val parent = if (parentUri.toString().contains("/tree/")) {
        DocumentFile.fromTreeUri(context, parentUri)
    } else {
        DocumentFile.fromSingleUri(context, parentUri)
    } ?: return null
    return parent.listFilesSafe().find { it.name == childName }?.uri
}

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
        openFileDescriptor(uri, "rw")?.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).use { out ->
                out.channel.use { ch ->
                    ch.truncate(0)
                    ch.position(0)
                    ch.write(ByteBuffer.wrap(bytes))
                    ch.force(true)
                }
            }
            true
        } ?: run {
            openOutputStream(uri)?.use { it.write(bytes) } != null
        }
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

/**
 * 将本地目录（如 JGit clone 的临时目录）递归复制到文档树根下的指定子目录。
 * @param treeRootUri 文档树根 URI（含 /tree/）
 * @param sourceDir 本地源目录
 * @param destDirName 目标子目录名（如 ".sysgit"）
 * @return 成功返回 true
 */
fun copyLocalDirToTree(
    context: Context,
    treeRootUri: Uri,
    sourceDir: File,
    destDirName: String,
    log: ((String) -> Unit)? = null
): Boolean {
    if (!sourceDir.isDirectory) return false
    val cr = context.contentResolver
    val rootDocId = try { DocumentsContract.getTreeDocumentId(treeRootUri) } catch (_: Exception) { null } ?: return false
    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeRootUri, rootDocId) ?: return false
    val destUri = try {
        DocumentsContract.createDocument(cr, parentDocUri, DocumentsContract.Document.MIME_TYPE_DIR, destDirName)
    } catch (e: Exception) {
        log?.invoke("创建目录 $destDirName 失败: ${e.message}")
        return false
    } ?: return false
    return copyLocalDirRecursive(context, destUri, sourceDir, log)
}

private fun copyLocalDirRecursive(context: Context, parentDocUri: Uri, sourceDir: File, log: ((String) -> Unit)?): Boolean {
    val cr = context.contentResolver
    val list = sourceDir.listFiles() ?: return true
    Log.d(TAG, "copyLocalDirRecursive: ${sourceDir.name} has ${list.size} items: ${list.map { it.name }}")
    for (f in list) {
        val name = f.name ?: continue
        if (name == "." || name == "..") continue
        try {
            if (f.isDirectory) {
                val childUri = DocumentsContract.createDocument(cr, parentDocUri, DocumentsContract.Document.MIME_TYPE_DIR, name)
                if (childUri == null) {
                    Log.e(TAG, "copyLocalDirRecursive: failed to create dir: $name")
                    continue
                }
                if (!copyLocalDirRecursive(context, childUri, f, log)) return false
            } else {
                val mime = "application/octet-stream"
                val newUri = DocumentsContract.createDocument(cr, parentDocUri, mime, name)
                if (newUri == null) {
                    Log.e(TAG, "copyLocalDirRecursive: failed to create file: $name")
                    continue
                }
                Log.d(TAG, "copyLocalDirRecursive: created file $name")
                f.inputStream().use { inp ->
                    cr.openOutputStream(newUri)?.use { out ->
                        inp.copyTo(out)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "copyLocalDirRecursive: exception copying $name: ${e.message}")
            log?.invoke("复制 $name 失败: ${e.message}")
            return false
        }
    }
    return true
}

/**
 * 将文档树目录复制到本地文件系统目录（覆盖式，用于 git 同步回本地）。
 * 会清空目标目录中与源目录同名的文件/子目录，但保留 .git 目录。
 * @param sourceDoc 源文档目录
 * @param destDir 目标本地目录（必须已存在）
 */
fun copyTreeDirToLocal(
    context: Context,
    sourceDoc: DocumentFile,
    destDir: File,
    log: ((String) -> Unit)? = null
): Boolean {
    if (!sourceDoc.isDirectory || !destDir.isDirectory) {
        Log.d(TAG, "copyTreeDirToLocal: invalid dirs - source.isDir=${sourceDoc.isDirectory}, dest.isDir=${destDir.isDirectory}")
        return false
    }
    val cr = context.contentResolver
    val children = sourceDoc.listFilesSafe()
    Log.d(TAG, "copyTreeDirToLocal: ${sourceDoc.name} -> ${destDir.name}, ${children.size} children")
    for (child in children) {
        val name = child.name ?: continue
        if (name == "." || name == ".." || name == ".git") continue
        val destFile = File(destDir, name)
        try {
            if (child.isDirectory) {
                if (destFile.exists() && !destFile.isDirectory) destFile.delete()
                if (!destFile.exists()) destFile.mkdirs()
                if (!copyTreeDirToLocal(context, child, destFile, log)) return false
            } else {
                if (destFile.exists() && destFile.isDirectory) destFile.deleteRecursively()
                cr.openInputStream(child.uri)?.use { inp ->
                    destFile.outputStream().use { out ->
                        inp.copyTo(out)
                    }
                }
            }
        } catch (e: Exception) {
            log?.invoke("复制 $name 失败: ${e.message}")
            return false
        }
    }
    // 删除本地多余的文件（源目录中不存在的）
    val sourceNames = children.mapNotNull { it.name }.toSet()
    Log.d(TAG, "copyTreeDirToLocal: sourceNames in ${sourceDoc.name} = $sourceNames")
    destDir.listFiles()?.forEach { localFile ->
        val n = localFile.name
        if (n != ".git" && n !in sourceNames) {
            Log.d(TAG, "copyTreeDirToLocal: deleting extra local file: $n")
            localFile.deleteRecursively()
        }
    }
    return true
}
