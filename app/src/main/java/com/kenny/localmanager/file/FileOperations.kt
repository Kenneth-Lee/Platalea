package com.kenny.localmanager.file

import android.content.ContentResolver
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

fun ContentResolver.deleteDocument(uri: Uri): Boolean =
    try { DocumentsContract.deleteDocument(this, uri) } catch (_: Exception) { false }

fun ContentResolver.renameDocument(uri: Uri, newName: String): Uri? =
    try {
        DocumentsContract.renameDocument(this, uri, newName)
    } catch (_: Exception) { null }
