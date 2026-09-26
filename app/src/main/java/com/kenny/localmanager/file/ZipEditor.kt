package com.kenny.localmanager.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.lingala.zip4j.model.ZipParameters
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val ZIP_EDITOR_TAG = "ZipEditor"

/** zip 条目的只读描述，列表展示用 */
data class ZipEntryInfo(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val lastModifiedTime: Long,
    val isDirectory: Boolean,
    val isEncrypted: Boolean = false
)

/** 目标 zip 的加密方式（取第一条加密条目）：加入时新条目按此保持一致。 */
internal data class ZipEncryptionInfo(
    val method: EncryptionMethod,
    val aesKeyStrength: AesKeyStrength?
)

/** 一次编辑的结果：同名改名明细需要反馈给用户；写回走临时文档替换时目标 URI 会变化，需回写配置。 */
data class ZipEditResult(
    val ok: Boolean,
    val renamed: List<Pair<String, String>> = emptyList(),
    val error: String? = null,
    val newTargetUri: Uri? = null,
    /** 目标加密且未提供密码：需要宿主收集密码后重试（未做任何修改）。 */
    val needsPassword: Boolean = false,
    /** 提供的密码验证失败（未做任何修改）。 */
    val wrongPassword: Boolean = false,
    /** 成功且目标加密：新条目已按原加密方式写入。 */
    val addedEncrypted: Boolean = false
)

/** 进度阶段 */
enum class ZipEditStage { COPY_TARGET, MODIFY, WRITE_BACK }

/**
 * 使打包阅读器（md/html/pic/vid zip 及 epub/llm zip 共用的解压缓存）针对指定 zip URI 全部失效。
 * zip 编辑写回后调用：写回走临时文档替换时 URI 可能变化，新旧 URI 都清；
 * 失效后查看器重新解压，不依赖列表快照的 lastModified 判定（编辑后列表未刷新时快照是旧值）。
 */
fun invalidateArchiveViewerCaches(context: Context, vararg zipUris: Uri) {
    for (uri in zipUris) {
        getMdZipCacheDir(context, uri).deleteRecursively()
        getHtmlZipCacheDir(context, uri).deleteRecursively()
        getPicZipCacheDir(context, uri).deleteRecursively()
        getVidZipCacheDir(context, uri).deleteRecursively()
        getEpubCacheDir(context, uri).deleteRecursively()
    }
}

// ---------------------------------------------------------------------------
// 本地 File 纯函数层：全部 zip 语义在此，供单测覆盖
// ---------------------------------------------------------------------------

/** 同名改名策略：a.txt 与已有条目冲突时生成 a (1).txt、a (2).txt … */
internal fun uniqueEntryName(desired: String, existing: Set<String>): String {
    if (desired !in existing) return desired
    val dot = desired.lastIndexOf('.')
    val base = if (dot > 0) desired.substring(0, dot) else desired
    val ext = if (dot > 0) desired.substring(dot) else ""
    var i = 1
    while (true) {
        val candidate = "$base ($i)$ext"
        if (candidate !in existing) return candidate
        i++
    }
}

internal fun listEntriesOfFile(zipFile: File): List<ZipEntryInfo> =
    ZipFile(zipFile).use { zip ->
        zip.fileHeaders.map { h ->
            ZipEntryInfo(
                name = h.fileName,
                size = h.uncompressedSize,
                compressedSize = h.compressedSize,
                lastModifiedTime = h.lastModifiedTimeEpoch * 1000L,
                isDirectory = h.isDirectory,
                isEncrypted = h.isEncrypted
            )
        }
    }

/** 探测 zip 加密方式：无加密条目返回 null；取第一条加密条目的方式（ZipCrypto / AES+强度）。 */
internal fun probeEncryption(zipFile: File): ZipEncryptionInfo? {
    ZipFile(zipFile).use { zip ->
        val enc = zip.fileHeaders.firstOrNull { it.isEncrypted } ?: return null
        return ZipEncryptionInfo(
            method = enc.encryptionMethod ?: EncryptionMethod.ZIP_STANDARD,
            aesKeyStrength = enc.aesExtraDataRecord?.aesKeyStrength
        )
    }
}

/** 验证密码：读第一条加密条目流探一字节（zip4j 在此处校验密码，AES 与 ZipCrypto 均抛 Wrong Password）。 */
internal fun verifyZipPassword(zipFile: File, password: CharArray): Boolean {
    return try {
        ZipFile(zipFile, password).use { zip ->
            val header = zip.fileHeaders.firstOrNull { it.isEncrypted } ?: return true
            zip.getInputStream(header).use { it.read() }
        }
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * 把源文件/目录加入本地 zip。目录源以目录条目形式整体加入。
 * 目标加密时传 encryption + password：新条目按原方式（算法+AES强度）加密，
 * 并在写入前校验密码（zip4j 的 add 不校验旧密码，错密码会产出多密码混合包）。
 * 返回 (源显示名, 最终条目名) 列表，同名时最终条目名经过改名。
 */
internal fun addFilesToZipFile(
    zipFile: File,
    sources: List<File>,
    encryption: ZipEncryptionInfo? = null,
    password: CharArray? = null,
    onBytes: ((Long) -> Unit)? = null
): List<Pair<String, String>> {
    if (encryption != null) {
        require(password != null && password.isNotEmpty()) { "encrypted target requires password" }
        if (!verifyZipPassword(zipFile, password)) {
            throw IllegalArgumentException("wrong password")
        }
    }
    ZipFile(zipFile, password).use { zip ->
        val existing = zip.fileHeaders.map { it.fileName }.toHashSet()
        val applied = mutableListOf<Pair<String, String>>()
        val onChunk: (Int) -> Unit = { n -> if (n > 0) onBytes?.invoke(n.toLong()) }
        for (src in sources) {
            val entryName = uniqueEntryName(src.name, existing)
            if (src.isDirectory) {
                addLocalDirEntryStream(zip, src, entryName, encryption, onChunk)
                existing.add("$entryName/")
                collectLocalEntryNames(src, entryName).forEach { existing.add(it) }
            } else {
                addLocalFileEntryStream(zip, src, entryName, encryption, onChunk)
                existing.add(entryName)
            }
            applied.add(src.name to entryName)
        }
        return applied
    }
}

/** 构造 addStream 条目参数：加密目标时新条目按原方式（算法+AES强度）加密；目录条目 STORE。 */
private fun buildEntryParams(encryption: ZipEncryptionInfo?, fileNameInZip: String, size: Long, store: Boolean = false): ZipParameters {
    return if (encryption != null) {
        ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = encryption.method
            aesKeyStrength = encryption.aesKeyStrength ?: AesKeyStrength.KEY_STRENGTH_256
            this.fileNameInZip = fileNameInZip
            entrySize = size
            if (store) compressionMethod = CompressionMethod.STORE
        }
    } else {
        ZipParameters().apply {
            this.fileNameInZip = fileNameInZip
            entrySize = size
            if (store) compressionMethod = CompressionMethod.STORE
        }
    }
}

/** 单文件源流式写入（探针实证 addStream 支持 AES/ZipCrypto 与计数流）。 */
private fun addLocalFileEntryStream(zip: ZipFile, file: File, entryName: String, encryption: ZipEncryptionInfo?, onChunk: (Int) -> Unit) {
    val params = buildEntryParams(encryption, entryName, file.length())
    file.inputStream().use { input ->
        zip.addStream(CountingInputStream(input, onChunk), params)
    }
}

/** 目录源：目录条目 STORE 空流 + 子项按名排序递归（与压缩侧 addLocalDirToZipStream 同构）。 */
private fun addLocalDirEntryStream(zip: ZipFile, dir: File, entryPrefix: String, encryption: ZipEncryptionInfo?, onChunk: (Int) -> Unit) {
    zip.addStream(ByteArrayInputStream(ByteArray(0)), buildEntryParams(encryption, "$entryPrefix/", 0L, store = true))
    dir.listFiles().orEmpty().sortedBy { it.name.lowercase() }.forEach { child ->
        val childName = "$entryPrefix/${child.name}"
        if (child.isDirectory) addLocalDirEntryStream(zip, child, childName, encryption, onChunk)
        else addLocalFileEntryStream(zip, child, childName, encryption, onChunk)
    }
}

/** 本地目录下将写出的全部条目名（含各级目录条目，精确集合，后续同名改名检测用） */
private fun collectLocalEntryNames(dir: File, prefix: String): List<String> {
    val names = mutableListOf<String>()
    dir.walkTopDown().forEach { f ->
        val rel = f.relativeTo(dir).invariantSeparatorsPath
        if (rel.isEmpty()) return@forEach
        names.add(if (f.isDirectory) "$prefix/$rel/" else "$prefix/$rel")
    }
    return names
}

/** 从本地 zip 删除指定条目（目录条目按名字精确删除） */
internal fun removeEntriesFromZipFile(zipFile: File, entryNames: List<String>): Boolean {
    ZipFile(zipFile).use { zip ->
        val existing = zip.fileHeaders.map { it.fileName }.toHashSet()
        var removed = 0
        for (name in entryNames) {
            if (name !in existing) continue
            zip.removeFile(name)
            removed++
        }
        return removed > 0
    }
}

// ---------------------------------------------------------------------------
// SAF 编排层：中转 + 临时文档替换写回
// ---------------------------------------------------------------------------

private fun buildEditCacheDir(context: Context): File =
    File(context.cacheDir, "zip_edit_${System.currentTimeMillis()}").apply { mkdirs() }

/** 本地文件/目录树总字节数（MODIFY 阶段 total 用，目录源需递归）。 */
private fun localTreeBytes(f: File): Long =
    if (f.isDirectory) f.walkTopDown().filter { !it.isDirectory }.sumOf { it.length() } else f.length()

/** 带 64KB 循环+取消检查的字节流拷贝，每个 chunk 回调累计字节数。 */
private fun copyStreamCounting(input: java.io.InputStream, output: java.io.OutputStream, shouldCancel: () -> Boolean, onBytes: (Long) -> Unit) {
    val buf = ByteArray(64 * 1024)
    while (true) {
        if (shouldCancel()) throw ArchiveOperationCancelledException("zip edit cancelled")
        val n = input.read(buf)
        if (n < 0) break
        output.write(buf, 0, n)
        if (n > 0) onBytes(n.toLong())
    }
}

/**
 * 写回策略：同目录临时文档 -> 完整写入 -> 删原文档 -> 临时文档改回原名。
 * 无法在原目录创建临时文档（provider 限制）时退化为直接覆写原 URI。
 * 成功返回新的目标 URI（rename 路径下 URI 已变），失败返回 null。
 */
private fun writeBackWithReplace(context: Context, targetUri: Uri, treeUri: Uri?, newZipFile: File, onBytes: (Long) -> Unit = {}): Uri? {
    val cr = context.contentResolver
    val doc = DocumentFile.fromSingleUri(context, targetUri)
    val docName = doc?.name ?: "target.zip"
    var tempUri: Uri? = null
    return try {
        val parent = findParentDocument(context, targetUri, treeUri)
        if (parent != null && parent.canWrite()) {
            tempUri = try {
                DocumentsContract.createDocument(cr, parent.uri, "application/zip", "$docName.lmtmp")
            } catch (e: Exception) {
                Log.w(ZIP_EDITOR_TAG, "writeBack: create temp document failed", e)
                null
            }
        }
        if (tempUri != null) {
            cr.openOutputStream(tempUri, "w")?.use { out ->
                FileInputStream(newZipFile).use { inp -> copyStreamCounting(inp, out, { false }, onBytes) }
            } ?: return null
            if (doc != null && !doc.delete()) {
                Log.w(ZIP_EDITOR_TAG, "writeBack: delete original failed, fallback to overwrite")
                runCatching { cr.deleteDocument(tempUri) }
                return overwriteTarget(context, targetUri, newZipFile, onBytes)
            }
            val renamedUri = try {
                DocumentsContract.renameDocument(cr, tempUri, docName)
            } catch (e: Exception) {
                Log.w(ZIP_EDITOR_TAG, "writeBack: rename failed", e)
                null
            }
            if (renamedUri != null) {
                return renamedUri
            }
            // rename 失败：临时文档数据完整，回退覆写并清理
            Log.w(ZIP_EDITOR_TAG, "writeBack: rename returned null, fallback to overwrite")
            val overwritten = overwriteTarget(context, targetUri, newZipFile, onBytes)
            runCatching { cr.deleteDocument(tempUri) }
            return overwritten
        } else {
            return overwriteTarget(context, targetUri, newZipFile)
        }
    } finally {
        tempUri?.let { uri ->
            runCatching { cr.deleteDocument(uri) }
        }
    }
}

/** 从 target URI 推导其父文档（同目录放临时文档用），失败返回 null */
private fun findParentDocument(context: Context, targetUri: Uri, treeUri: Uri?): DocumentFile? {
    return try {
        val docId = DocumentsContract.getDocumentId(targetUri) ?: return null
        val parentDocId = docId.substringBeforeLast('/', "").ifEmpty { return null }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(targetUri, parentDocId)
        DocumentFile.fromTreeUri(context, parentUri)
    } catch (e: Exception) {
        Log.w(ZIP_EDITOR_TAG, "findParentDocument failed", e)
        null
    }
}

private fun overwriteTarget(context: Context, targetUri: Uri, newZipFile: File, onBytes: (Long) -> Unit = {}): Uri? =
    context.contentResolver.openOutputStream(targetUri, "w")?.use { out ->
        FileInputStream(newZipFile).use { inp -> copyStreamCounting(inp, out, { false }, onBytes) }
        targetUri
    }

private fun copyUriToLocal(context: Context, uri: Uri, localDir: File, treeUri: Uri?, shouldCancel: () -> Boolean, onBytes: (Long) -> Unit = {}): File? {
    val resolved = resolveDocUriWithTree(uri, treeUri)
    val doc = DocumentFile.fromSingleUri(context, resolved) ?: return null
    val name = doc.name ?: return null
    val localEntry = File(localDir, name)
    return try {
        if (doc.isDirectory) {
            localEntry.mkdirs()
            copyDocumentToLocal(context, resolved, localEntry, treeUri, shouldCancel)
            localEntry
        } else {
            context.contentResolver.openInputStream(resolved)?.use { input ->
                localEntry.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (shouldCancel()) throw ArchiveOperationCancelledException("zip edit cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        if (n > 0) onBytes(n.toLong())
                    }
                }
            } ?: return null
            localEntry
        }
    } catch (e: ArchiveOperationCancelledException) {
        throw e
    } catch (e: Exception) {
        Log.e(ZIP_EDITOR_TAG, "copyUriToLocal failed for $resolved", e)
        null
    }
}

/** 列出目标 zip 全部条目；目标不可读/非 zip 返回 null */
fun listZipEntries(context: Context, zipUri: Uri, treeUri: Uri?): List<ZipEntryInfo>? {
    val cacheDir = buildEditCacheDir(context)
    return try {
        val localZip = File(cacheDir, "target.zip")
        val resolved = resolveDocUriWithTree(zipUri, treeUri)
        context.contentResolver.openInputStream(resolved)?.use { input ->
            localZip.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        listEntriesOfFile(localZip)
    } catch (e: Exception) {
        Log.e(ZIP_EDITOR_TAG, "listZipEntries failed", e)
        null
    } finally {
        cacheDir.deleteRecursively()
    }
}

/** 把 sources 追加进目标 zip（目录源按目录条目加入），同名自动改名；目标加密时需密码 */
fun addToZip(
    context: Context,
    targetUri: Uri,
    treeUri: Uri?,
    sourceUris: List<Uri>,
    setProgress: (ZipEditStage, Int, Int, Long, Long) -> Unit,
    shouldCancel: () -> Boolean = { false },
    password: CharArray? = null
): ZipEditResult {
    val cacheDir = buildEditCacheDir(context)
    try {
        // 字节进度：COPY_TARGET 覆盖目标快照+源拷贝（连续计数），MODIFY 覆盖压入源字节，WRITE_BACK 覆盖新包写回
        val targetBytes = docTreeSize(context, targetUri, treeUri)
        val sourceBytes = sourceUris.sumOf { docTreeSize(context, it, treeUri) }
        var copyDone = 0L
        val reportCopy: (Long) -> Unit = { copyDone += it; setProgress(ZipEditStage.COPY_TARGET, 0, sourceUris.size + 1, copyDone, targetBytes + sourceBytes) }

        setProgress(ZipEditStage.COPY_TARGET, 0, 1, 0, targetBytes + sourceBytes)
        val localZip = File(cacheDir, "target.zip")
        val resolvedTarget = resolveDocUriWithTree(targetUri, treeUri)
        context.contentResolver.openInputStream(resolvedTarget)?.use { input ->
            localZip.outputStream().use { output -> copyStreamCounting(input, output, shouldCancel, reportCopy) }
        } ?: return ZipEditResult(false, error = "open target failed")

        // 加密目标：先探测再校验，任一不满足直接返回（未做任何修改）
        val encryption = probeEncryption(localZip)
        if (encryption != null) {
            if (password == null || password.isEmpty()) {
                return ZipEditResult(false, needsPassword = true)
            }
            if (!verifyZipPassword(localZip, password)) {
                return ZipEditResult(false, wrongPassword = true)
            }
        }

        val sourceDir = File(cacheDir, "sources").apply { mkdirs() }
        val localSources = mutableListOf<File>()
        sourceUris.forEachIndexed { index, uri ->
            if (shouldCancel()) return ZipEditResult(false, error = "cancelled")
            val local = copyUriToLocal(context, uri, sourceDir, treeUri, shouldCancel, reportCopy)
                ?: return ZipEditResult(false, error = "copy source failed: $uri")
            localSources.add(local)
            setProgress(ZipEditStage.COPY_TARGET, index + 1, sourceUris.size + 1, copyDone, targetBytes + sourceBytes)
        }

        val modifyTotal = localSources.sumOf { localTreeBytes(it) }
        var modifyDone = 0L
        val reportModify: (Long) -> Unit = { modifyDone += it; setProgress(ZipEditStage.MODIFY, 0, localSources.size, modifyDone, modifyTotal) }
        setProgress(ZipEditStage.MODIFY, 0, localSources.size, 0, modifyTotal)
        val applied = addFilesToZipFile(localZip, localSources, encryption, password, reportModify)
        setProgress(ZipEditStage.MODIFY, localSources.size, localSources.size, modifyTotal, modifyTotal)

        setProgress(ZipEditStage.WRITE_BACK, 0, 1, 0, localZip.length())
        val newUri = writeBackWithReplace(context, resolvedTarget, treeUri, localZip) { done ->
            setProgress(ZipEditStage.WRITE_BACK, 0, 1, done, localZip.length())
        }
        setProgress(ZipEditStage.WRITE_BACK, 1, 1, localZip.length(), localZip.length())
        return if (newUri != null) {
            ZipEditResult(
                ok = true,
                renamed = applied.filter { it.first != it.second },
                newTargetUri = newUri,
                addedEncrypted = encryption != null
            )
        } else {
            ZipEditResult(false, error = "write back failed")
        }
    } catch (e: ArchiveOperationCancelledException) {
        return ZipEditResult(false, error = "cancelled")
    } catch (e: Exception) {
        Log.e(ZIP_EDITOR_TAG, "addToZip failed", e)
        return ZipEditResult(false, error = e.message ?: e.javaClass.simpleName)
    } finally {
        cacheDir.deleteRecursively()
    }
}

/** 从目标 zip 删除指定条目 */
fun removeZipEntries(
    context: Context,
    targetUri: Uri,
    treeUri: Uri?,
    entryNames: List<String>,
    setProgress: (ZipEditStage, Int, Int, Long, Long) -> Unit,
    shouldCancel: () -> Boolean = { false }
): ZipEditResult {
    val cacheDir = buildEditCacheDir(context)
    try {
        val targetBytes = docTreeSize(context, targetUri, treeUri)
        setProgress(ZipEditStage.COPY_TARGET, 0, 1, 0, targetBytes)
        val localZip = File(cacheDir, "target.zip")
        val resolvedTarget = resolveDocUriWithTree(targetUri, treeUri)
        context.contentResolver.openInputStream(resolvedTarget)?.use { input ->
            localZip.outputStream().use { output ->
                var done = 0L
                copyStreamCounting(input, output, shouldCancel) { done += it; setProgress(ZipEditStage.COPY_TARGET, 0, 1, done, targetBytes) }
            }
        } ?: return ZipEditResult(false, error = "open target failed")

        setProgress(ZipEditStage.MODIFY, 0, 1, 0, 0)
        val changed = removeEntriesFromZipFile(localZip, entryNames)
        setProgress(ZipEditStage.MODIFY, 1, 1, 0, 0)
        if (!changed) {
            return ZipEditResult(true)
        }

        setProgress(ZipEditStage.WRITE_BACK, 0, 1, 0, localZip.length())
        val newUri = writeBackWithReplace(context, resolvedTarget, treeUri, localZip) { done ->
            setProgress(ZipEditStage.WRITE_BACK, 0, 1, done, localZip.length())
        }
        setProgress(ZipEditStage.WRITE_BACK, 1, 1, localZip.length(), localZip.length())
        return if (newUri != null) ZipEditResult(true, newTargetUri = newUri) else ZipEditResult(false, error = "write back failed")
    } catch (e: ArchiveOperationCancelledException) {
        return ZipEditResult(false, error = "cancelled")
    } catch (e: Exception) {
        Log.e(ZIP_EDITOR_TAG, "removeZipEntries failed", e)
        return ZipEditResult(false, error = e.message ?: e.javaClass.simpleName)
    } finally {
        cacheDir.deleteRecursively()
    }
}
