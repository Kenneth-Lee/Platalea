package com.kenny.localmanager.family

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BulletinBoardExporter {
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val fileTimeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun snapshotToMarkdown(snapshot: BulletinBoardSnapshot): String {
        val exportedAt = timeFormat.format(Date())
        val sb = StringBuilder()
        sb.appendLine("# ${snapshot.boardName}")
        sb.appendLine()
        sb.appendLine("> 留言板 ID：`${snapshot.boardId}`")
        sb.appendLine("> 导出时间：$exportedAt")
        sb.appendLine("> 消息数：${snapshot.messages.size}")
        sb.appendLine()
        if (snapshot.messages.isEmpty()) {
            sb.appendLine("（暂无留言）")
            return sb.toString()
        }
        snapshot.messages.forEach { message ->
            sb.appendLine("---")
            sb.appendLine()
            val timeLabel = timeFormat.format(Date(message.updatedAt))
            sb.appendLine("**${message.authorLabel}** · $timeLabel")
            message.authorDevice?.takeIf { it.isNotBlank() }?.let { device ->
                sb.appendLine()
                sb.appendLine("> 设备：$device")
            }
            sb.appendLine()
            if (message.content.isNotBlank()) {
                sb.appendLine(message.content)
                sb.appendLine()
            }
            if (message.attachments.isNotEmpty()) {
                sb.appendLine("附件：")
                message.attachments.forEach { attachment ->
                    sb.appendLine("- ${formatAttachmentLine(attachment)}")
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
            ?: throw IllegalStateException("无法访问根目录")
        val exportDir = rootDoc.findFile(EXPORT_DIR_NAME)?.takeIf { it.isDirectory }
            ?: rootDoc.createDirectory(EXPORT_DIR_NAME)
            ?: throw IllegalStateException("无法创建导出目录")
        val safeName = sanitizeFileName(fileName)
        exportDir.findFile(safeName)?.let { existing ->
            if (!existing.isDirectory) {
                existing.delete()
            }
        }
        val created = exportDir.createFile("text/markdown", safeName)
            ?: throw IllegalStateException("无法创建 Markdown 文件")
        context.contentResolver.openOutputStream(created.uri)?.use { output ->
            output.write(markdown.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("无法写入 Markdown 文件")
        "$EXPORT_DIR_NAME/$safeName"
    }

    fun defaultExportFileName(boardName: String): String {
        val safeBoard = sanitizeFileName(boardName).removeSuffix(".md")
        val ts = fileTimeFormat.format(Date())
        return "${safeBoard}_$ts.md"
    }

    private fun formatAttachmentLine(attachment: BulletinAttachmentRef): String {
        val size = when (attachment.kind) {
            BulletinAttachmentKind.FILE -> attachment.size
            BulletinAttachmentKind.DIRECTORY -> attachment.totalSize.coerceAtLeast(attachment.size)
        }
        val sizeLabel = formatByteCount(size)
        val kindLabel = if (attachment.kind == BulletinAttachmentKind.DIRECTORY) "目录" else "文件"
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

    const val EXPORT_DIR_NAME = "留言板导出"
}
