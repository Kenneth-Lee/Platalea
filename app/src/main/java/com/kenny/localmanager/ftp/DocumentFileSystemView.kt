package com.kenny.localmanager.ftp

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.file.listFilesSafe
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.FtpFile

/**
 * 基于 DocumentFile 根目录的 FileSystemView。
 * 路径分隔符为 '/'，根目录为 "/"。所有目录均来自树下（TreeDocumentFile），保证 listFiles() 可用。
 */
class DocumentFileSystemView(
    private val context: Context,
    private val rootDoc: DocumentFile,
    initialWorkingDoc: DocumentFile? = null,
    initialWorkingPath: String = "/",
    private val onLog: (String) -> Unit = {}
) : FileSystemView {

    private var workingDir: FtpFile = if (initialWorkingDoc != null && initialWorkingPath != "/")
        DocumentFileFtpFile(context, initialWorkingDoc, initialWorkingPath, onLog = onLog)
    else
        DocumentFileFtpFile(context, rootDoc, "/", onLog = onLog)

    override fun getHomeDirectory(): FtpFile = DocumentFileFtpFile(context, rootDoc, "/", onLog = onLog)

    override fun getWorkingDirectory(): FtpFile = workingDir

    @Throws(FtpException::class)
    override fun changeWorkingDirectory(dir: String): Boolean {
        val target = getFile(dir) ?: return false
        if (!target.isDirectory || !target.doesExist()) return false
        workingDir = target
        return true
    }

    @Throws(FtpException::class)
    override fun getFile(file: String?): FtpFile? {
        val raw = file?.replace("\\", "/")?.trim()?.trimEnd('/') ?: ""
        if (raw.isEmpty() || raw == ".") return workingDir
        val path = if (raw.startsWith("/")) normalizePath(raw)
        else normalizePath((workingDir as DocumentFileFtpFile).getAbsolutePath().trimEnd('/') + "/" + raw)
        if (path == "/") return DocumentFileFtpFile(context, rootDoc, "/", onLog = onLog)
        val parts = path.removePrefix("/").split("/").filter { it.isNotEmpty() }
        var current: DocumentFile? = rootDoc
        for (i in parts.indices) {
            val name = parts[i]
            if (current == null || !current.isDirectory) return null
            var child = current.listFilesSafe().find { (it.name ?: "") == name }
            if (child == null && i < parts.lastIndex) {
                child = current.createDirectory(name)
            }
            if (i == parts.lastIndex) {
                return if (child != null) DocumentFileFtpFile(context, child, path, onLog = onLog)
                else DocumentFileFtpFile(context, null, path, current, name, onLog = onLog)
            }
            current = child
        }
        return if (current != null) DocumentFileFtpFile(context, current, path, onLog = onLog) else null
    }

    override fun isRandomAccessible(): Boolean = false

    override fun dispose() {}
}

private fun normalizePath(path: String): String {
    var p = path.replace("\\", "/").trim()
    while (p.contains("//")) p = p.replace("//", "/")
    if (p.endsWith("/") && p.length > 1) p = p.dropLast(1)
    if (p.isEmpty() || p != "/" && !p.startsWith("/")) p = "/$p"
    return p
}
