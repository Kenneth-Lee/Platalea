package com.kenny.localmanager.ftp

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import com.kenny.localmanager.file.listFilesSafe
import org.apache.ftpserver.ftplet.FtpFile
import java.io.InputStream
import java.io.OutputStream

/**
 * FtpFile 实现，基于 DocumentFile（如 SAF 根目录）。
 * @param doc 已存在的文件/目录；为 null 时表示尚不存在的路径（用于 STOR 创建新文件），此时需提供 parentDoc 与 newFileName。
 * @param parentDoc 仅当 doc==null 且为创建新文件时使用，表示父目录
 * @param newFileName 仅当 doc==null 时使用，表示待创建的文件名
 * @param onLog 日志回调，仅用于记录错误等对用户有用的信息
 */
class DocumentFileFtpFile(
    private val context: Context,
    private val doc: DocumentFile?,
    private val absolutePath: String,
    private val parentDoc: DocumentFile? = null,
    private val newFileName: String? = null,
    private val onLog: (String) -> Unit = {}
) : FtpFile {

    override fun getAbsolutePath(): String = absolutePath

    override fun getName(): String = when {
        absolutePath == "/" -> "/"
        newFileName != null -> newFileName
        doc != null -> doc.name ?: ""
        else -> absolutePath.substringAfterLast('/').ifEmpty { absolutePath }
    }

    override fun isHidden(): Boolean = doc?.name?.startsWith(".") == true

    override fun isDirectory(): Boolean = doc?.isDirectory == true

    override fun isFile(): Boolean = doc?.isFile == true

    override fun doesExist(): Boolean = doc?.exists() == true

    override fun isReadable(): Boolean = doc != null && doc.exists()

    override fun isWritable(): Boolean = when {
        doc != null && doc.exists() -> doc.canWrite()
        parentDoc != null && newFileName != null -> parentDoc.canWrite()
        else -> false
    }

    override fun isRemovable(): Boolean = doc?.canWrite() == true

    override fun getOwnerName(): String = "lm"

    override fun getGroupName(): String = "lm"

    override fun getLinkCount(): Int = if (doc?.isFile == true) 1 else (doc?.listFilesSafe()?.size ?: 0).coerceAtLeast(1)

    override fun getLastModified(): Long = doc?.lastModified() ?: 0L

    override fun setLastModified(time: Long): Boolean = false

    override fun getSize(): Long = doc?.length() ?: 0L

    override fun getPhysicalFile(): Any? = doc?.uri

    override fun mkdir(): Boolean {
        val parent = parentDoc ?: doc?.parentFile
        val name = newFileName ?: doc?.name
        if (parent == null || !parent.isDirectory || name.isNullOrBlank()) return false
        if (doc != null && doc.exists()) return doc.isDirectory
        return parent.createDirectory(name) != null
    }

    override fun delete(): Boolean = doc?.delete() == true

    override fun move(destination: FtpFile): Boolean {
        val destDoc = (destination as? DocumentFileFtpFile)?.doc
        if (doc == null || !doc.exists() || destDoc == null || !destDoc.isDirectory) return false
        if (!doc.isFile) return false
        val created = destDoc.createFile("*/*", doc.name ?: "file") ?: return false
        context.contentResolver.openInputStream(doc.uri)?.use { input ->
            context.contentResolver.openOutputStream(created.uri)?.use { input.copyTo(it) }
        } ?: return false
        return doc.delete()
    }

    override fun listFiles(): List<FtpFile>? {
        if (doc == null || !doc.isDirectory) return null
        return try {
            val arr = doc.listFilesSafe()
            java.util.Collections.unmodifiableList(
                arr.mapNotNull { child ->
                    val name = child.name ?: ""
                    val path = if (absolutePath == "/") "/$name" else "$absolutePath/$name"
                    DocumentFileFtpFile(context, child, path, onLog = onLog)
                }
                    .sortedBy { it.name.lowercase() }
            )
        } catch (e: Exception) {
            onLog(context.getString(R.string.ftp_error_list_dir_failed, e.message ?: e.javaClass.simpleName))
            null
        }
    }

    override fun createOutputStream(offset: Long): OutputStream {
        if (offset != 0L) throw UnsupportedOperationException("Random write not supported")
        val targetDoc = when {
            doc != null && doc.exists() && doc.isFile -> doc
            parentDoc != null && newFileName != null -> parentDoc.createFile("*/*", newFileName) ?: throw IllegalArgumentException("Cannot create file")
            else -> throw IllegalArgumentException("Not a file or cannot create")
        }
        return context.contentResolver.openOutputStream(targetDoc.uri, "wt")
            ?: throw IllegalArgumentException("Cannot open output stream")
    }

    override fun createInputStream(offset: Long): InputStream {
        if (offset != 0L) throw UnsupportedOperationException("Random read not supported")
        if (doc == null || !doc.isFile) throw IllegalArgumentException("Not a file or does not exist")
        return context.contentResolver.openInputStream(doc.uri)
            ?: throw IllegalArgumentException("Cannot open input stream")
    }
}
