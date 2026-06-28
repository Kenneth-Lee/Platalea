package com.kenny.localmanager.family

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R

enum class BulletinAttachmentDownloadConflict {
    OVERWRITE,
    RENAME
}

object BulletinAttachmentDownloadPaths {
    private val legacyRootDirNames = listOf("留言板附件", "Bulletin attachments")

    fun rootDirName(context: Context): String =
        context.getString(R.string.family_attachment_folder_name)

    fun downloadRoot(context: Context, rootUri: String): DocumentFile? {
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(rootUri)) ?: return null
        val localized = rootDirName(context)
        (listOf(localized) + legacyRootDirNames).distinct().forEach { name ->
            rootDoc.findFile(name)?.takeIf { it.isDirectory }?.let { return it }
        }
        return rootDoc.createDirectory(localized)
    }

    fun targetExists(context: Context, rootUri: String, ref: BulletinAttachmentRef): Boolean {
        val downloadDir = downloadRoot(context, rootUri) ?: return false
        val name = sanitizeSegment(ref.name)
        return downloadDir.findFile(name) != null
    }

    fun resolveTargetName(
        context: Context,
        downloadDir: DocumentFile,
        ref: BulletinAttachmentRef,
        conflict: BulletinAttachmentDownloadConflict
    ): String {
        val baseName = sanitizeSegment(ref.name)
        if (conflict == BulletinAttachmentDownloadConflict.OVERWRITE) {
            return baseName
        }
        return uniqueNameIn(context, downloadDir, baseName, ref.kind == BulletinAttachmentKind.DIRECTORY)
    }

    fun sanitizeSegment(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "unknown" }

    private fun uniqueNameIn(
        context: Context,
        dir: DocumentFile,
        baseName: String,
        isDirectory: Boolean
    ): String {
        if (dir.findFile(baseName) == null) return baseName
        var index = 1
        while (index < 10_000) {
            val candidate = if (isDirectory) {
                "$baseName ($index)"
            } else {
                val dot = baseName.lastIndexOf('.')
                if (dot > 0) {
                    "${baseName.substring(0, dot)} ($index)${baseName.substring(dot)}"
                } else {
                    "$baseName ($index)"
                }
            }
            if (dir.findFile(candidate) == null) return candidate
            index++
        }
        throw IllegalStateException(context.getString(R.string.family_msg_81577, baseName))
    }
}

data class BulletinAttachmentDownloadResult(
    val uri: Uri,
    val savedPath: String
)
