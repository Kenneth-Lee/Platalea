package com.kenny.localmanager.git

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.kenny.localmanager.R
import com.kenny.localmanager.file.copyLocalDirToTree
import com.kenny.localmanager.file.copyTreeDirToLocal
import com.kenny.localmanager.file.deleteTreeChildDirIfExists
import com.kenny.localmanager.file.findChildByName
import com.kenny.localmanager.file.listFilesSafe
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

private const val TAG = "GitHelper"
const val SYSGIT_DIR = ".sysgit"
const val PUBKEY_DIR = "pubkey"
const val SHARE_DIR = "share"

/** 校验为有效的 HTTPS 仓库地址（仅允许 https://，且可解析）。 */
fun isValidHttpsRepoUrl(repoUrl: String): Boolean {
    val s = repoUrl.trim()
    if (!s.lowercase().startsWith("https://") || s.length < 12) return false
    return try {
        val uri = Uri.parse(s)
        uri.scheme?.equals("https", ignoreCase = true) == true && !uri.host.isNullOrBlank()
    } catch (_: Exception) { false }
}

/**
 * 将远程 Git 仓库同步到文档树根下的 .sysgit 目录（仅支持 HTTPS）。
 * 若本地缓存中已有该仓库则执行 pull，否则 clone；再将结果写入文档树。
 */
fun cloneToTree(
    context: Context,
    treeRootUri: String,
    repoUrl: String,
    userName: String? = null,
    userEmail: String? = null,
    httpsPassword: String? = null,
    log: ((String) -> Unit)? = null
): Result<String> {
    if (treeRootUri.isBlank() || repoUrl.isBlank()) {
        return Result.failure(IllegalArgumentException(context.getString(R.string.git_error_missing_repo_and_root)))
    }
    if (!isValidHttpsRepoUrl(repoUrl)) {
        return Result.failure(IllegalArgumentException(context.getString(R.string.git_error_invalid_https_url)))
    }
    val uri = Uri.parse(treeRootUri)
    if (!treeRootUri.contains("/tree/")) {
        return Result.failure(IllegalArgumentException(context.getString(R.string.git_error_invalid_root)))
    }
    val appDir = context.cacheDir
    val urlHash = MessageDigest.getInstance("SHA-256").digest(repoUrl.trim().toByteArray())
    val dirName = "git_repo_" + urlHash.take(12).joinToString("") { "%02x".format(it) }
    val repoDir = File(appDir, dirName)
    val creds = UsernamePasswordCredentialsProvider(userName ?: "", httpsPassword ?: "")

    try {
        val alreadyCloned = repoDir.isDirectory && File(repoDir, ".git").exists()
        if (alreadyCloned) {
            log?.invoke(context.getString(R.string.git_log_pulling, repoUrl))
            Git.open(repoDir).use { git ->
                // 先还原工作区，防止之前残留的未提交删除影响 pull
                git.checkout().setAllPaths(true).call()
                git.pull()
                    .setCredentialsProvider(creds)
                    .call()
            }
        } else {
            log?.invoke(context.getString(R.string.git_log_cloning, repoUrl))
            if (repoDir.exists()) repoDir.deleteRecursively()
            Git.cloneRepository()
                .setURI(repoUrl.trim())
                .setDirectory(repoDir)
                .setCredentialsProvider(creds)
                .call()
                .close()
        }
        if (!userName.isNullOrBlank() || !userEmail.isNullOrBlank()) {
            Git.open(repoDir).use { git ->
                val cfg = git.repository.config
                userName?.takeIf { it.isNotBlank() }?.let { cfg.setString("user", null, "name", it) }
                userEmail?.takeIf { it.isNotBlank() }?.let { cfg.setString("user", null, "email", it) }
                cfg.save()
            }
        }
        log?.invoke(context.getString(R.string.git_log_writing_sysgit))
        deleteTreeChildDirIfExists(context, uri, SYSGIT_DIR)
        val ok = copyLocalDirToTree(context, uri, repoDir, SYSGIT_DIR, log)
        return if (ok) {
            Result.success(if (alreadyCloned) context.getString(R.string.git_success_updated, SYSGIT_DIR) else context.getString(R.string.git_success_downloaded, SYSGIT_DIR))
        } else {
            Result.failure(RuntimeException(context.getString(R.string.git_error_write_sysgit_failed)))
        }
    } catch (e: Throwable) {
        val detail = buildString {
            var t: Throwable? = e
            var depth = 0
            while (t != null && depth < 5) {
                if (depth > 0) append(" <- ")
                append(t.javaClass.simpleName)
                t.message?.let { append(": ").append(it) }
                t = t.cause
                depth++
            }
        }
        Log.e(TAG, "clone/pull failed: $detail", e)
        val errMsg = e.message ?: e.cause?.message ?: detail
        log?.invoke(context.getString(R.string.git_log_error, errMsg))
        log?.invoke(context.getString(R.string.git_log_debug_chain, detail))
        return Result.failure(e)
    }
}

/** 获取仓库对应的本地缓存目录路径 */
fun getLocalGitCacheDir(context: Context, repoUrl: String): File {
    val urlHash = MessageDigest.getInstance("SHA-256").digest(repoUrl.trim().toByteArray())
    val dirName = "git_repo_" + urlHash.take(12).joinToString("") { "%02x".format(it) }
    return File(context.cacheDir, dirName)
}

/** 检查本地是否已有仓库缓存 */
fun hasLocalGitCache(context: Context, repoUrl: String): Boolean {
    if (repoUrl.isBlank()) return false
    val repoDir = getLocalGitCacheDir(context, repoUrl)
    return repoDir.isDirectory && File(repoDir, ".git").exists()
}

/** 删除本地缓存的 git 仓库 */
fun deleteLocalGitCache(context: Context, repoUrl: String): Boolean {
    if (repoUrl.isBlank()) return true
    val repoDir = getLocalGitCacheDir(context, repoUrl)
    return if (repoDir.exists()) repoDir.deleteRecursively() else true
}

/** 删除文档树中的 .sysgit 目录 */
fun deleteSysgitFromTree(context: Context, treeRootUri: String): Boolean {
    if (treeRootUri.isBlank() || !treeRootUri.contains("/tree/")) return false
    val uri = Uri.parse(treeRootUri)
    return deleteTreeChildDirIfExists(context, uri, SYSGIT_DIR)
}

/**
 * 将文档树中 .sysgit 的内容同步回本地缓存，以便 commit/push。
 */
fun syncFromTreeToLocal(
    context: Context,
    treeRootUri: String,
    repoUrl: String,
    log: ((String) -> Unit)? = null
): Boolean {
    Log.d(TAG, "syncFromTreeToLocal: starting")
    if (treeRootUri.isBlank() || repoUrl.isBlank()) {
        Log.d(TAG, "syncFromTreeToLocal: invalid params")
        return false
    }
    val repoDir = getLocalGitCacheDir(context, repoUrl)
    if (!repoDir.isDirectory || !File(repoDir, ".git").exists()) {
        Log.d(TAG, "syncFromTreeToLocal: local repo not found at ${repoDir.absolutePath}")
        log?.invoke(context.getString(R.string.git_error_local_repo_missing))
        return false
    }
    val treeUri = Uri.parse(treeRootUri)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
    val sysgitDoc = rootDoc.listFilesSafe().find { it.name == SYSGIT_DIR && it.isDirectory } ?: run {
        Log.d(TAG, "syncFromTreeToLocal: .sysgit not found in tree")
        log?.invoke(context.getString(R.string.git_error_sysgit_missing))
        return false
    }
    log?.invoke(context.getString(R.string.git_log_syncing_from_sysgit))
    Log.d(TAG, "syncFromTreeToLocal: copying from tree to ${repoDir.absolutePath}")
    val result = copyTreeDirToLocal(context, sysgitDoc, repoDir, log)
    Log.d(TAG, "syncFromTreeToLocal: result=$result")
    return result
}

/**
 * 提交并推送本地仓库的变更。
 * 先从文档树同步到本地，然后 git add/commit/push。
 */
fun commitAndPush(
    context: Context,
    treeRootUri: String,
    repoUrl: String,
    commitMessage: String,
    userName: String?,
    httpsPassword: String?,
    log: ((String) -> Unit)? = null
): Result<String> {
    Log.d(TAG, "commitAndPush: starting with message=$commitMessage")
    if (treeRootUri.isBlank() || repoUrl.isBlank()) {
        return Result.failure(IllegalArgumentException(context.getString(R.string.git_error_missing_repo_and_root)))
    }
    val repoDir = getLocalGitCacheDir(context, repoUrl)
    if (!repoDir.isDirectory || !File(repoDir, ".git").exists()) {
        return Result.failure(IllegalStateException(context.getString(R.string.git_error_local_repo_missing)))
    }

    // 从文档树同步回本地
    if (!syncFromTreeToLocal(context, treeRootUri, repoUrl, log)) {
        return Result.failure(RuntimeException(context.getString(R.string.git_error_sync_to_local_failed)))
    }

    val creds = UsernamePasswordCredentialsProvider(userName ?: "", httpsPassword ?: "")
    try {
        Git.open(repoDir).use { git ->
            // 检查当前状态
            val status = git.status().call()
            Log.d(TAG, "commitAndPush: status before add - added=${status.added}, changed=${status.changed}, removed=${status.removed}, missing=${status.missing}, untracked=${status.untracked}")

            log?.invoke(context.getString(R.string.git_log_adding_changes))
            // 添加新文件和修改的文件
            git.add().addFilepattern(".").call()
            // 暂存已删除的文件
            git.add().addFilepattern(".").setUpdate(true).call()

            // 再次检查状态
            val statusAfter = git.status().call()
            Log.d(TAG, "commitAndPush: status after add - added=${statusAfter.added}, changed=${statusAfter.changed}, removed=${statusAfter.removed}")

            log?.invoke(context.getString(R.string.git_log_committing))
            git.commit()
                .setMessage(commitMessage)
                .setAllowEmpty(false)
                .call()
            Log.d(TAG, "commitAndPush: commit done")

            log?.invoke(context.getString(R.string.git_log_pushing))
            git.push()
                .setCredentialsProvider(creds)
                .call()
            Log.d(TAG, "commitAndPush: push done")
        }
        log?.invoke(context.getString(R.string.git_log_push_success))
        return Result.success(context.getString(R.string.git_success_commit_push))
    } catch (e: Throwable) {
        val errMsg = e.message ?: e.cause?.message ?: e.javaClass.simpleName
        Log.e(TAG, "commit/push failed: $errMsg", e)
        log?.invoke(context.getString(R.string.git_log_error, errMsg))
        return Result.failure(e)
    }
}

/** 公钥信息 */
data class RemotePubkeyInfo(
    val filename: String,
    val keyId: Long,
    val userId: String,
    val ring: PGPPublicKeyRing
)

/**
 * 读取 .sysgit/pubkey/ 下的公钥列表。
 * 会尝试解析目录下所有文件，不仅限于 .asc/.gpg 后缀。
 */
fun listRemotePubkeys(context: Context, treeRootUri: String): List<RemotePubkeyInfo> {
    if (treeRootUri.isBlank() || !treeRootUri.contains("/tree/")) return emptyList()
    val treeUri = Uri.parse(treeRootUri)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    val sysgitDoc = rootDoc.listFilesSafe().find { it.name == SYSGIT_DIR && it.isDirectory } ?: return emptyList()
    val pubkeyDir = sysgitDoc.listFilesSafe().find { it.name == PUBKEY_DIR && it.isDirectory } ?: return emptyList()

    val result = mutableListOf<RemotePubkeyInfo>()
    pubkeyDir.listFilesSafe().filter { it.isFile }.forEach { file ->
        try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                val decoder = PGPUtil.getDecoderStream(input)
                val factory = BcPGPObjectFactory(decoder)
                var obj = factory.nextObject()
                while (obj != null) {
                    if (obj is PGPPublicKeyRing) {
                        val pk = obj.publicKey
                        val userId = pk?.userIDs?.asSequence()?.firstOrNull() ?: "Unknown"
                        result.add(RemotePubkeyInfo(
                            filename = file.name ?: "",
                            keyId = pk?.keyID ?: 0L,
                            userId = userId,
                            ring = obj
                        ))
                        break
                    }
                    obj = factory.nextObject()
                }
            }
        } catch (_: Exception) {
            // 跳过无法解析的文件
        }
    }
    return result
}

/**
 * 清理 .sysgit/pubkey/ 中的重复公钥文件。
 * 按 keyId 分组，每个 keyId 只保留一个文件（优先保留标准命名的）。
 * @return 删除的文件数量
 */
fun deduplicateRemotePubkeys(context: Context, treeRootUri: String, log: ((String) -> Unit)? = null): Int {
    if (treeRootUri.isBlank() || !treeRootUri.contains("/tree/")) {
        Log.d(TAG, "deduplicateRemotePubkeys: invalid treeRootUri")
        return 0
    }
    val treeUri = Uri.parse(treeRootUri)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
    if (rootDoc == null) {
        Log.d(TAG, "deduplicateRemotePubkeys: rootDoc is null")
        return 0
    }
    val sysgitDoc = rootDoc.listFilesSafe().find { it.name == SYSGIT_DIR && it.isDirectory }
    if (sysgitDoc == null) {
        Log.d(TAG, "deduplicateRemotePubkeys: .sysgit not found")
        return 0
    }
    val pubkeyDir = sysgitDoc.listFilesSafe().find { it.name == PUBKEY_DIR && it.isDirectory }
    if (pubkeyDir == null) {
        Log.d(TAG, "deduplicateRemotePubkeys: pubkey dir not found")
        return 0
    }

    // 解析所有公钥文件，按 keyId 分组
    val keyIdToFiles = mutableMapOf<Long, MutableList<DocumentFile>>()
    val allFiles = pubkeyDir.listFilesSafe().filter { it.isFile }
    Log.d(TAG, "deduplicateRemotePubkeys: found ${allFiles.size} files in pubkey dir")
    log?.invoke(context.getString(R.string.git_log_pubkey_file_count, allFiles.size))

    allFiles.forEach { file ->
        try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                val decoder = PGPUtil.getDecoderStream(input)
                val factory = BcPGPObjectFactory(decoder)
                var obj = factory.nextObject()
                while (obj != null) {
                    if (obj is PGPPublicKeyRing) {
                        val keyId = obj.publicKey?.keyID ?: 0L
                        if (keyId != 0L) {
                            keyIdToFiles.getOrPut(keyId) { mutableListOf() }.add(file)
                            Log.d(TAG, "  ${file.name} -> keyId=${keyId.toString(16)}")
                        }
                        break
                    }
                    obj = factory.nextObject()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "  ${file.name} parse failed: ${e.message}")
        }
    }

    // 删除重复文件
    var deletedCount = 0
    for ((keyId, files) in keyIdToFiles) {
        if (files.size > 1) {
            Log.d(TAG, "keyId ${keyId.toString(16)} has ${files.size} duplicates")
            // 优先保留标准命名的文件（0x开头，不含括号）
            val standardName = "0x${keyId.toString(16).lowercase()}.asc"
            val sorted = files.sortedBy { f ->
                when {
                    f.name == standardName -> 0
                    f.name?.startsWith("0x") == true && f.name?.contains("(") == false && f.name?.contains("（") == false -> 1
                    else -> 2
                }
            }
            // 保留第一个，删除其余
            Log.d(TAG, "  keep: ${sorted[0].name}")
            for (i in 1 until sorted.size) {
                val toDelete = sorted[i]
                val ok = toDelete.delete()
                Log.d(TAG, "  delete: ${toDelete.name} -> ${if (ok) "OK" else "FAILED"}")
                if (ok) deletedCount++
            }
        }
    }
    Log.d(TAG, "deduplicateRemotePubkeys: deleted $deletedCount files")
    return deletedCount
}

/**
 * 导出公钥到 .sysgit/pubkey/ 目录。
 * 会先删除所有包含相同 keyId 的现有文件。
 * @param keyIdHex 用作文件名（不含扩展名）
 * @param armoredBytes ASCII 装甲格式的公钥数据
 * @param targetKeyId 要导出的公钥 keyId，用于删除重复文件
 */
fun exportPubkeyToSysgit(
    context: Context,
    treeRootUri: String,
    keyIdHex: String,
    armoredBytes: ByteArray,
    targetKeyId: Long = 0L
): Boolean {
    if (treeRootUri.isBlank() || !treeRootUri.contains("/tree/")) return false
    val treeUri = Uri.parse(treeRootUri)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
    var sysgitDoc = rootDoc.listFilesSafe().find { it.name == SYSGIT_DIR && it.isDirectory }
    if (sysgitDoc == null) {
        sysgitDoc = rootDoc.createDirectory(SYSGIT_DIR) ?: return false
    }
    var pubkeyDir = sysgitDoc.listFilesSafe().find { it.name == PUBKEY_DIR && it.isDirectory }
    if (pubkeyDir == null) {
        pubkeyDir = sysgitDoc.createDirectory(PUBKEY_DIR) ?: return false
    }

    // 删除所有包含相同 keyId 的现有文件
    if (targetKeyId != 0L) {
        pubkeyDir.listFilesSafe().filter { it.isFile }.forEach { file ->
            try {
                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    val decoder = PGPUtil.getDecoderStream(input)
                    val factory = BcPGPObjectFactory(decoder)
                    var obj = factory.nextObject()
                    while (obj != null) {
                        if (obj is PGPPublicKeyRing) {
                            if (obj.publicKey?.keyID == targetKeyId) {
                                file.delete()
                            }
                            break
                        }
                        obj = factory.nextObject()
                    }
                }
            } catch (_: Exception) {
                // 忽略解析错误
            }
        }
    }

    val filename = "$keyIdHex.asc"
    val newFile = pubkeyDir.createFile("application/pgp-keys", filename) ?: return false
    return try {
        context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
            out.write(armoredBytes)
        }
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * 删除 .sysgit/pubkey/ 中的公钥文件。
 */
fun deleteRemotePubkey(context: Context, treeRootUri: String, filename: String): Boolean {
    if (treeRootUri.isBlank() || !treeRootUri.contains("/tree/")) return false
    val treeUri = Uri.parse(treeRootUri)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
    val sysgitDoc = rootDoc.listFilesSafe().find { it.name == SYSGIT_DIR && it.isDirectory } ?: return false
    val pubkeyDir = sysgitDoc.listFilesSafe().find { it.name == PUBKEY_DIR && it.isDirectory } ?: return false
    val file = pubkeyDir.listFilesSafe().find { it.name == filename } ?: return true // 已不存在
    return file.delete()
}

/** 将 PGPPublicKeyRing 编码为 ASCII 装甲格式 */
fun encodePublicKeyRingToArmored(ring: PGPPublicKeyRing): ByteArray {
    return ByteArrayOutputStream().use { out ->
        ArmoredOutputStream(out).use { armored ->
            ring.encode(armored)
        }
        out.toByteArray()
    }
}

// ── 文件共享 ──────────────────────────────────────────────────

/** 共享文件信息 */
data class SharedFileInfo(
    val filename: String,
    val size: Long,
    val lastModified: Long,
    val uri: Uri
)

/**
 * 获取 .sysgit/share/ 目录的 DocumentFile，不存在则创建。
 */
private fun getOrCreateShareDir(context: Context, treeRootUri: String): DocumentFile? {
    if (treeRootUri.isBlank() || !treeRootUri.contains("/tree/")) return null
    val treeUri = Uri.parse(treeRootUri)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
    var sysgitDoc = rootDoc.listFilesSafe().find { it.name == SYSGIT_DIR && it.isDirectory }
    if (sysgitDoc == null) {
        sysgitDoc = rootDoc.createDirectory(SYSGIT_DIR) ?: return null
    }
    var shareDir = sysgitDoc.listFilesSafe().find { it.name == SHARE_DIR && it.isDirectory }
    if (shareDir == null) {
        shareDir = sysgitDoc.createDirectory(SHARE_DIR) ?: return null
    }
    return shareDir
}

/**
 * 列出 .sysgit/share/ 下的所有文件。
 */
fun listSharedFiles(context: Context, treeRootUri: String): List<SharedFileInfo> {
    if (treeRootUri.isBlank() || !treeRootUri.contains("/tree/")) return emptyList()
    val treeUri = Uri.parse(treeRootUri)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
    val sysgitDoc = rootDoc.listFilesSafe().find { it.name == SYSGIT_DIR && it.isDirectory }
        ?: return emptyList()
    val shareDir = sysgitDoc.listFilesSafe().find { it.name == SHARE_DIR && it.isDirectory }
        ?: return emptyList()
    return shareDir.listFilesSafe().filter { it.isFile }.mapNotNull { file ->
        SharedFileInfo(
            filename = file.name ?: return@mapNotNull null,
            size = file.length(),
            lastModified = file.lastModified(),
            uri = file.uri
        )
    }
}

/**
 * 将文件复制到 .sysgit/share/，如果同名文件存在则覆盖。
 */
fun copyFileToShare(context: Context, treeRootUri: String, sourceUri: Uri, fileName: String): Boolean {
    val shareDir = getOrCreateShareDir(context, treeRootUri) ?: return false
    val cr = context.contentResolver
    // 删除已存在的同名文件
    shareDir.listFilesSafe().find { it.name == fileName }?.delete()
    val newFile = shareDir.createFile("application/octet-stream", fileName) ?: return false
    return try {
        cr.openInputStream(sourceUri)?.use { inp ->
            cr.openOutputStream(newFile.uri)?.use { out ->
                inp.copyTo(out)
            }
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "copyFileToShare failed: ${e.message}")
        false
    }
}

/**
 * 从 .sysgit/share/ 复制文件到根目录。
 * @return true 成功, false 失败
 */
fun copySharedFileToRoot(context: Context, treeRootUri: String, fileName: String, overwrite: Boolean): Boolean {
    if (treeRootUri.isBlank() || !treeRootUri.contains("/tree/")) return false
    val treeUri = Uri.parse(treeRootUri)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
    val sysgitDoc = rootDoc.listFilesSafe().find { it.name == SYSGIT_DIR && it.isDirectory }
        ?: return false
    val shareDir = sysgitDoc.listFilesSafe().find { it.name == SHARE_DIR && it.isDirectory }
        ?: return false
    val srcFile = shareDir.listFilesSafe().find { it.name == fileName } ?: return false
    val cr = context.contentResolver
    if (overwrite) {
        rootDoc.listFilesSafe().find { it.name == fileName && !it.isDirectory }?.delete()
    }
    val destFile = rootDoc.createFile("application/octet-stream", fileName) ?: return false
    return try {
        cr.openInputStream(srcFile.uri)?.use { inp ->
            cr.openOutputStream(destFile.uri)?.use { out ->
                inp.copyTo(out)
            }
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "copySharedFileToRoot failed: ${e.message}")
        false
    }
}

/**
 * 检查根目录下是否存在同名文件。
 */
fun rootHasFile(context: Context, treeRootUri: String, fileName: String): Boolean {
    if (treeRootUri.isBlank() || !treeRootUri.contains("/tree/")) return false
    val treeUri = Uri.parse(treeRootUri)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
    return rootDoc.listFilesSafe().any { it.name == fileName && !it.isDirectory }
}

/**
 * 删除 .sysgit/share/ 中的文件。
 */
fun deleteSharedFile(context: Context, treeRootUri: String, filename: String): Boolean {
    if (treeRootUri.isBlank() || !treeRootUri.contains("/tree/")) return false
    val treeUri = Uri.parse(treeRootUri)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
    val sysgitDoc = rootDoc.listFilesSafe().find { it.name == SYSGIT_DIR && it.isDirectory }
        ?: return false
    val shareDir = sysgitDoc.listFilesSafe().find { it.name == SHARE_DIR && it.isDirectory }
        ?: return false
    val file = shareDir.listFilesSafe().find { it.name == filename } ?: return true
    return file.delete()
}
