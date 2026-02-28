package com.kenny.localmanager.file

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class DocumentFileModel(
    val documentFile: DocumentFile,
    val name: String,
    val isDirectory: Boolean,
    val uri: Uri,
    val lastModified: Long,
    val size: Long
) {
    val displaySize: String
        get() = when {
            isDirectory -> ""
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
            size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024))
            else -> "%.1f GB".format(size / (1024.0 * 1024 * 1024))
        }
}

fun DocumentFile.toModel(): DocumentFileModel? = takeIf { it.exists() }?.let { doc ->
    DocumentFileModel(
        documentFile = doc,
        name = doc.name ?: "",
        isDirectory = doc.isDirectory,
        uri = doc.uri,
        lastModified = doc.lastModified(),
        size = doc.length()
    )
}
