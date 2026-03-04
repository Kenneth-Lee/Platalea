package com.kenny.localmanager.git

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kenny.localmanager.file.copyLocalDirToTree
import com.kenny.localmanager.file.deleteTreeChildDirIfExists
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.security.MessageDigest

private const val TAG = "GitHelper"
const val SYSGIT_DIR = ".sysgit"

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
        return Result.failure(IllegalArgumentException("需要填写仓库地址，并已选择根目录"))
    }
    if (!isValidHttpsRepoUrl(repoUrl)) {
        return Result.failure(IllegalArgumentException("请填写有效的 HTTPS 仓库地址，例如 https://gitcode.com/用户/仓库.git"))
    }
    val uri = Uri.parse(treeRootUri)
    if (!treeRootUri.contains("/tree/")) {
        return Result.failure(IllegalArgumentException("根目录须为文档树根（含 /tree/）"))
    }
    val appDir = context.cacheDir
    val urlHash = MessageDigest.getInstance("SHA-256").digest(repoUrl.trim().toByteArray())
    val dirName = "git_repo_" + urlHash.take(12).joinToString("") { "%02x".format(it) }
    val repoDir = File(appDir, dirName)
    val creds = UsernamePasswordCredentialsProvider(userName ?: "", httpsPassword ?: "")

    try {
        val alreadyCloned = repoDir.isDirectory && File(repoDir, ".git").exists()
        if (alreadyCloned) {
            log?.invoke("正在拉取更新: $repoUrl")
            Git.open(repoDir).use { git ->
                git.pull()
                    .setCredentialsProvider(creds)
                    .call()
            }
        } else {
            log?.invoke("正在克隆: $repoUrl")
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
        log?.invoke("正在写入 .sysgit ...")
        deleteTreeChildDirIfExists(context, uri, SYSGIT_DIR)
        val ok = copyLocalDirToTree(context, uri, repoDir, SYSGIT_DIR, log)
        return if (ok) {
            Result.success(if (alreadyCloned) "已拉取并更新到 $SYSGIT_DIR" else "已下载到根目录下的 $SYSGIT_DIR")
        } else {
            Result.failure(RuntimeException("同步成功，但写入 .sysgit 失败"))
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
        log?.invoke("错误: $errMsg")
        log?.invoke("[调试] 异常链: $detail")
        return Result.failure(e)
    }
}
