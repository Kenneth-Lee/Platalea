package com.kenny.localmanager.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

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

fun ContentResolver.deleteDocument(uri: Uri): Boolean =
    try { DocumentsContract.deleteDocument(this, uri) } catch (_: Exception) { false }

fun ContentResolver.renameDocument(uri: Uri, newName: String): Uri? =
    try {
        DocumentsContract.renameDocument(this, uri, newName)
    } catch (_: Exception) { null }

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
    cr.openInputStreamSafe(sourceUri)?.use { input ->
        cr.openOutputStream(newFileUri)?.use { output ->
            input.copyTo(output)
        }
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
                source.listFilesSafe().forEach { child ->
                    copyDocumentTo(context, child.uri, newDirUri, treeUri, log)
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
 * 将单个文档移动到目标父目录。先尝试系统 moveDocument，不支持则拷贝后删除源。
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
        DocumentsContract.deleteDocument(cr, sourceUri)
    } else false
}
