package com.kenny.localmanager.file

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.Locale

// 外部应用打开 / URI 工具：给定 URI 即可工作，与文件浏览器无耦合，
// 可被任何界面（浏览器菜单、最近记录、待处理列表、查看器等）调用。

// ===== 外部应用打开 / URI 归一化工具（自 PlataeaApp.kt 迁入，批次 4b）=====
internal fun guessExternalOpenMimeType(context: Context, uri: Uri, fileNameHint: String?): String {
    val resolverMime = context.contentResolver.getType(uri)?.trim()?.lowercase(Locale.ROOT)
    val validResolverMime = resolverMime
        ?.takeIf { it.isNotBlank() && it != "application/octet-stream" && it != "*/*" }
    if (validResolverMime != null) return validResolverMime

    val candidateName = fileNameHint
        ?.takeIf { it.isNotBlank() }
        ?: context.contentResolver.getDisplayName(uri)
    val ext = candidateName
        ?.substringAfterLast('.', "")
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()
    if (ext.isNotEmpty()) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        externalOpenArchiveMimeByExt[ext]?.let { return it }
        if (ext in externalOpenVideoExts) return "video/*"
        if (ext in externalOpenAudioExts) return "audio/*"
        if (ext in externalOpenImageExts) return "image/*"
        if (ext in externalOpenTextExts) return "text/plain"
    }
    return resolverMime?.takeIf { it.isNotBlank() } ?: "*/*"
}

internal val externalOpenVideoExts = setOf(
    "rmvb", "rm", "rv", "ram", "mp4", "m4v", "mkv", "avi", "mov", "wmv", "flv",
    "ts", "m2ts", "mts", "webm", "3gp", "mpeg", "mpg", "vob", "asf"
)
internal val externalOpenAudioExts = setOf(
    "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "ape", "amr", "aiff", "alac"
)
internal val externalOpenImageExts = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tiff", "tif", "svg"
)
internal val externalOpenTextExts = setOf(
    "txt", "md", "rst", "log", "json", "xml", "yaml", "yml", "csv", "ini", "toml"
)
internal val externalOpenArchiveMimeByExt = mapOf(
    "zip" to "application/zip",
    "7z" to "application/x-7z-compressed",
    "rar" to "application/vnd.rar",
    "tar" to "application/x-tar",
    "gz" to "application/gzip",
    "bz2" to "application/x-bzip2",
    "xz" to "application/x-xz",
    "pdf" to "application/pdf",
    "epub" to "application/epub+zip",
    "apk" to "application/vnd.android.package-archive",
    "srt" to "application/x-subrip",
    "ass" to "text/x-ssa",
    "ssa" to "text/x-ssa"
)

internal fun toExternalLaunchUri(context: Context, uri: Uri): Uri {
    if (uri.scheme != "file") return uri
    val path = uri.path ?: return uri
    val file = java.io.File(path)
    if (!file.exists()) return uri
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrElse { uri }
}

internal fun fileExtensionKey(name: String): String? {
    val dotIndex = name.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex >= name.lastIndex) return null
    return name.substring(dotIndex + 1).trim().lowercase().ifBlank { null }
}

internal fun buildExternalOpenIntent(
    context: Context,
    uri: Uri,
    packageName: String? = null,
    fileNameHint: String? = null,
    forcedMimeType: String? = null
): Intent {
    val externalUri = toExternalLaunchUri(context, uri)
    val mimeType = forcedMimeType?.takeIf { it.isNotBlank() }
        ?: guessExternalOpenMimeType(context, externalUri, fileNameHint)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(externalUri, mimeType)
        if (!packageName.isNullOrBlank()) setPackage(packageName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal fun launchExternalOpen(
    context: Context,
    uriString: String,
    packageName: String?,
    fileNameHint: String? = null,
    forcedMimeType: String? = null
): Boolean {
    return runCatching {
        context.startActivity(buildExternalOpenIntent(context, Uri.parse(uriString), packageName, fileNameHint, forcedMimeType))
        true
    }.getOrElse { false }
}

internal fun pathFromRoot(context: Context, rootUri: String?, currentUri: String): String {
    if (rootUri == null) return currentUri
    // 首选 documentId 前缀法：ExternalStorageProvider 的 id 形如 "volume:rel/path"，
    // 与根 id（"volume:rel"）做前缀匹配即可得到从应用根算起的相对路径。
    // 单文档 URI（如 zip 编辑目标）的 parentFile 为 null，上溯法对其无效。
    val rootId = runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(normalizeContentUriString(rootUri)))
    }.getOrNull()
    val currentId = runCatching {
        val uri = Uri.parse(normalizeContentUriString(currentUri))
        if (uri.pathSegments.firstOrNull() == "tree") DocumentsContract.getDocumentId(uri)
        else uri.lastPathSegment
    }.getOrNull()
    if (rootId != null && currentId != null && rootId.isNotEmpty()) {
        // 根 id 两种形态：带目录（"primary:Documents"）后跟"/"；卷根（"home:"）后直接跟路径
        if (currentId == rootId) return "/"
        if (currentId.startsWith(rootId)) {
            val rel = currentId.removePrefix(rootId).removePrefix("/")
            if (rel.isNotEmpty()) return rel
        }
    }
    // 兜底：parentFile 上溯（其他 provider 的 id 不带路径语义时仍可用）
    val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(normalizeContentUriString(rootUri))) ?: return currentUri
    val currentDoc = if (currentUri.contains("/tree/")) DocumentFile.fromTreeUri(context, Uri.parse(currentUri))
        else DocumentFile.fromSingleUri(context, Uri.parse(currentUri))
    val current = currentDoc ?: return currentUri
    val parts = mutableListOf<String>()
    var cursor: DocumentFile? = current
    while (cursor != null) {
        if (cursor.uri == rootDoc.uri) break
        parts.add(0, cursor.name ?: "?")
        cursor = cursor.parentFile
    }
    return parts.joinToString("/").ifEmpty { "/" }
}

/**
 * 把（[pathFromRoot] 得到的、从应用根算起的）目标路径换算成相对当前目录的显示形式：
 * 同目录给纯文件名；跨目录用标准 ".." 段回退。目标为当前目录自身时给 "."。
 */
internal fun relativePathFromCurrentDir(targetPathFromRoot: String, currentDirPathFromRoot: String): String {
    val target = targetPathFromRoot.trim('/').split('/').filter { it.isNotEmpty() }
    val base = currentDirPathFromRoot.trim('/').split('/').filter { it.isNotEmpty() }
    if (target == base) return "."
    var common = 0
    // 目标最后一段是文件名，不参与目录段匹配，避免与同名目录误消
    while (common < base.size && common < target.size - 1 && base[common] == target[common]) common++
    val ups = List(base.size - common) { ".." }
    val rest = target.drop(common)
    return (ups + rest).joinToString("/").ifEmpty { "." }
}

internal fun humanReadableRootUri(uriString: String): String {
    val normalized = normalizeContentUriString(uriString)
    return runCatching {
        val uri = Uri.parse(normalized)
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val decoded = Uri.decode(treeId)
        val parts = decoded.split(':', limit = 2)
        val volume = parts.getOrNull(0)?.trim().orEmpty()
        val relative = parts.getOrNull(1)?.trim().orEmpty()
        when {
            volume.equals("home", ignoreCase = true) -> {
                if (relative.isNotBlank()) "/sdcard/Documents/$relative" else "/sdcard/Documents"
            }
            volume.equals("primary", ignoreCase = true) -> {
                if (relative.isNotBlank()) "/sdcard/$relative" else "/sdcard"
            }
            relative.isNotBlank() -> "/$relative"
            volume.isNotBlank() -> volume
            else -> decoded.ifBlank { normalized }
        }
    }.getOrElse {
        normalized.substringAfterLast('/').substringAfterLast('%').ifBlank { normalized }
    }
}