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
 * 将单个文档（文件或目录）拷贝到目标父目录。目录会递归拷贝。
 * @return 新文档的 Uri，失败返回 null
 */
fun copyDocumentTo(context: Context, sourceUri: Uri, targetParentUri: Uri): Uri? {
    val cr = context.contentResolver
    val source = DocumentFile.fromSingleUri(context, sourceUri) ?: return null
    val targetDir = DocumentFile.fromTreeUri(context, targetParentUri)
        ?: DocumentFile.fromSingleUri(context, targetParentUri)
        ?: return null
    if (!targetDir.isDirectory) return null
    val name = source.name ?: return null
    return if (source.isDirectory) {
        val newDir = targetDir.createDirectory(name) ?: return null
        source.listFilesSafe().forEach { child ->
            copyDocumentTo(context, child.uri, newDir.uri)
        }
        newDir.uri
    } else {
        val mime = cr.getTypeSafe(sourceUri) ?: "application/octet-stream"
        val newFile = targetDir.createFile(mime, name) ?: return null
        cr.openInputStreamSafe(sourceUri)?.use { input ->
            cr.openOutputStream(newFile.uri)?.use { output ->
                input.copyTo(output)
            }
        }
        newFile.uri
    }
}

/**
 * 将单个文档移动到目标父目录。先尝试系统 moveDocument，不支持则拷贝后删除源。
 */
fun moveDocumentTo(context: Context, sourceUri: Uri, targetParentUri: Uri): Boolean {
    val cr = context.contentResolver
    val source = DocumentFile.fromSingleUri(context, sourceUri) ?: return false
    val sourceParent = source.parentFile?.uri ?: return false
    return try {
        val moved = DocumentsContract.moveDocument(cr, sourceUri, sourceParent, targetParentUri)
        moved != null
    } catch (_: Exception) {
        val copied = copyDocumentTo(context, sourceUri, targetParentUri)
        if (copied != null) DocumentsContract.deleteDocument(cr, sourceUri) else false
    }
}
