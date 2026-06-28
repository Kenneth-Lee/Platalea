package com.kenny.localmanager.family

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BulletinBoardExporter {
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val fileTimeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val legacyExportDirNames = listOf("留言板导出", "Bulletin exports")

    fun exportDirName(context: Context): String =
        context.getString(R.string.family_board_export_folder_name)

    fun findOrCreateExportDir(context: Context, rootDoc: DocumentFile): DocumentFile? {
        val localized = exportDirName(context)
        (listOf(localized) + legacyExportDirNames).distinct().forEach { name ->
            rootDoc.findFile(name)?.takeIf { it.isDirectory }?.let { return it }
        }
        return rootDoc.createDirectory(localized)
    }

    fun snapshotToMarkdown(context: Context, snapshot: BulletinBoardSnapshot): String {
        val exportedAt = timeFormat.format(Date())
        val sb = StringBuilder()
        sb.appendLine("# ${snapshot.boardName}")
        sb.appendLine()
        sb.appendLine(context.getString(R.string.family_msg_58092, snapshot.boardId))
        sb.appendLine(context.getString(R.string.family_msg_48632, exportedAt))
        sb.appendLine(context.getString(R.string.family_msg_97534, snapshot.messages.size))
        sb.appendLine()
        if (snapshot.messages.isEmpty()) {
            sb.appendLine(context.getString(R.string.family_msg_06816))
            return sb.toString()
        }
        snapshot.messages.forEach { message ->
            sb.appendLine("---")
            sb.appendLine()
            val timeLabel = timeFormat.format(Date(message.updatedAt))
            sb.appendLine("**${message.authorLabel}** · $timeLabel")
            message.authorDevice?.takeIf { it.isNotBlank() }?.let { device ->
                sb.appendLine()
                sb.appendLine(context.getString(R.string.family_msg_33585, device))
            }
            sb.appendLine()
            if (message.content.isNotBlank()) {
                sb.appendLine(message.content)
                sb.appendLine()
            }
            if (message.attachments.isNotEmpty()) {
                sb.appendLine(context.getString(R.string.family_msg_90847))
                message.attachments.forEach { attachment ->
                    sb.appendLine("- ${formatAttachmentLine(context, attachment)}")
                }
                sb.appendLine()
            }
        }
        return sb.toString().trimEnd() + "\n"
    }

    fun saveMarkdownToRoot(
        context: Context,
        rootUri: String,
        fileName: String,
        markdown: String
    ): Result<String> = runCatching {
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
            ?: throw IllegalStateException(context.getString(R.string.family_msg_54649))
        val exportDir = findOrCreateExportDir(context, rootDoc)
            ?: throw IllegalStateException(context.getString(R.string.family_msg_54837))
        val safeName = sanitizeFileName(fileName)
        exportDir.findFile(safeName)?.let { existing ->
            if (!existing.isDirectory) {
                existing.delete()
            }
        }
        val created = exportDir.createFile("text/markdown", safeName)
            ?: throw IllegalStateException(context.getString(R.string.family_msg_02480))
        context.contentResolver.openOutputStream(created.uri)?.use { output ->
            output.write(markdown.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException(context.getString(R.string.family_msg_40893))
        "${exportDirName(context)}/$safeName"
    }

    fun defaultExportFileName(boardName: String): String {
        val safeBoard = sanitizeFileName(boardName).removeSuffix(".md")
        val ts = fileTimeFormat.format(Date())
        return "${safeBoard}_$ts.md"
    }

    private fun formatAttachmentLine(context: Context, attachment: BulletinAttachmentRef): String {
        val size = when (attachment.kind) {
            BulletinAttachmentKind.FILE -> attachment.size
            BulletinAttachmentKind.DIRECTORY -> attachment.totalSize.coerceAtLeast(attachment.size)
        }
        val sizeLabel = formatByteCount(size)
        val kindLabel = if (attachment.kind == BulletinAttachmentKind.DIRECTORY) {
            context.getString(R.string.family_msg_11649)
        } else {
            context.getString(R.string.family_msg_33306)
        }
        return "${attachment.name}（$kindLabel，$sizeLabel）"
    }

    private fun formatByteCount(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    private fun sanitizeFileName(name: String): String {
        val base = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "board" }
        return if (base.endsWith(".md", ignoreCase = true)) base else "$base.md"
    }
}
