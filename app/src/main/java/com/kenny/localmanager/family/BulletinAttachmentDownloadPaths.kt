package com.kenny.localmanager.family

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

enum class BulletinAttachmentDownloadConflict {
    OVERWRITE,
    RENAME
}

object BulletinAttachmentDownloadPaths {
    const val ROOT_DIR_NAME = "留言板附件"

    fun sanitizeSegment(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "unknown" }

    fun downloadRoot(context: Context, rootUri: String): DocumentFile? {
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(rootUri)) ?: return null
        return rootDoc.findFile(ROOT_DIR_NAME)?.takeIf { it.isDirectory }
            ?: rootDoc.createDirectory(ROOT_DIR_NAME)
    }

    fun targetExists(context: Context, rootUri: String, ref: BulletinAttachmentRef): Boolean {
        val downloadDir = downloadRoot(context, rootUri) ?: return false
        val name = sanitizeSegment(ref.name)
        return downloadDir.findFile(name) != null
    }

    fun resolveTargetName(
        downloadDir: DocumentFile,
        ref: BulletinAttachmentRef,
        conflict: BulletinAttachmentDownloadConflict
    ): String {
        val baseName = sanitizeSegment(ref.name)
        if (conflict == BulletinAttachmentDownloadConflict.OVERWRITE) {
            return baseName
        }
        return uniqueNameIn(downloadDir, baseName, ref.kind == BulletinAttachmentKind.DIRECTORY)
    }

    private fun uniqueNameIn(dir: DocumentFile, baseName: String, isDirectory: Boolean): String {
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
        throw IllegalStateException("无法生成不冲突的文件名：$baseName")
    }
}

data class BulletinAttachmentDownloadResult(
    val uri: Uri,
    val savedPath: String
)
