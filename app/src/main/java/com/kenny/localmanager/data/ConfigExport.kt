package com.kenny.localmanager.data

import android.content.Context
import android.util.Base64
import com.kenny.localmanager.gpg.getGpgKeyDir
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val KEY_FILTER_VISIBLE = "filter_visible"
private const val KEY_HIDE_DOT_FILES = "hide_dot_files"
private const val KEY_VIEWER_PREVIEW_BYTES = "viewer_preview_bytes"
private const val KEY_FTP_PASSWORD = "ftp_password"
private const val KEY_FTP_TIMEOUT_MINUTES = "ftp_timeout_minutes"
private const val KEY_GIT_REPO_URL = "git_repo_url"
private const val KEY_GIT_USER_NAME = "git_user_name"
private const val KEY_GIT_USER_EMAIL = "git_user_email"
private const val KEY_GIT_HTTPS_PASSWORD = "git_https_password"
private const val KEY_GIT_CONFIG_APPLIED = "git_config_applied"
private const val KEY_GPG_PUBLIC_KEYS_BASE64 = "gpg_public_keys_base64"
private const val KEY_GPG_SECRET_KEYS_BASE64 = "gpg_secret_keys_base64"
private const val KEY_PLAYER_PLAYLISTS = "player_playlists"

enum class ConfigPlaylistImportMode {
    OVERWRITE,
    APPEND
}

/**
 * 导出当前配置为 JSON 字符串。包含：过滤条件、隐藏点文件、查看器预览长度、
 * FTP 密码与倒计时、Git 配置、公钥与私钥（base64）。
 */
suspend fun exportConfig(context: Context, prefs: Preferences): String {
    val obj = JSONObject()
    obj.put(KEY_FILTER_VISIBLE, prefs.filterVisible.first())
    obj.put(KEY_HIDE_DOT_FILES, prefs.hideDotFiles.first())
    obj.put(KEY_VIEWER_PREVIEW_BYTES, prefs.viewerPreviewBytes.first())
    prefs.ftpPassword.first()?.let { obj.put(KEY_FTP_PASSWORD, it) }
    obj.put(KEY_FTP_TIMEOUT_MINUTES, prefs.ftpTimeoutMinutes.first())
    prefs.gitRepoUrl.first()?.let { obj.put(KEY_GIT_REPO_URL, it) }
    prefs.gitUserName.first()?.let { obj.put(KEY_GIT_USER_NAME, it) }
    prefs.gitUserEmail.first()?.let { obj.put(KEY_GIT_USER_EMAIL, it) }
    prefs.gitHttpsPassword.first()?.let { obj.put(KEY_GIT_HTTPS_PASSWORD, it) }
    obj.put(KEY_GIT_CONFIG_APPLIED, prefs.gitConfigApplied.first())
    obj.put(KEY_PLAYER_PLAYLISTS, JSONArray(prefs.playlists.first().map { it.toJson() }))

    val keyDir = getGpgKeyDir(context)
    File(keyDir, "pubring.gpg").takeIf { it.exists() }?.readBytes()?.let { bytes ->
        obj.put(KEY_GPG_PUBLIC_KEYS_BASE64, Base64.encodeToString(bytes, Base64.NO_WRAP))
    }
    File(keyDir, "secring.gpg").takeIf { it.exists() }?.readBytes()?.let { bytes ->
        obj.put(KEY_GPG_SECRET_KEYS_BASE64, Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    return obj.toString(2)
}

/**
 * 检查 JSON 配置字符串是否包含公钥或私钥数据（用于导入前提示用户）。
 */
fun configJsonContainsKeys(jsonString: String): Boolean {
    val obj = try {
        JSONObject(jsonString)
    } catch (_: Exception) {
        return false
    }
    return obj.has(KEY_GPG_PUBLIC_KEYS_BASE64) || obj.has(KEY_GPG_SECRET_KEYS_BASE64)
}

fun configJsonPlaylistCount(jsonString: String): Int {
    val obj = try {
        JSONObject(jsonString)
    } catch (_: Exception) {
        return 0
    }
    return obj.optJSONArray(KEY_PLAYER_PLAYLISTS)?.length() ?: 0
}

/**
 * 从 JSON 字符串导入配置。仅对存在的键写入，缺失的键不修改。
 * @param importKeys 是否导入并覆盖公钥/私钥；为 false 时跳过密钥，保留本机现有密钥
 * @return 成功为 true，解析失败为 false
 */
suspend fun importConfig(
    context: Context,
    prefs: Preferences,
    jsonString: String,
    importKeys: Boolean = true,
    playlistImportMode: ConfigPlaylistImportMode = ConfigPlaylistImportMode.OVERWRITE
): Boolean {
    val obj = try {
        JSONObject(jsonString)
    } catch (_: Exception) {
        return false
    }

    if (obj.has(KEY_FILTER_VISIBLE)) prefs.setFilterVisible(obj.getBoolean(KEY_FILTER_VISIBLE))
    if (obj.has(KEY_HIDE_DOT_FILES)) prefs.setHideDotFiles(obj.getBoolean(KEY_HIDE_DOT_FILES))
    if (obj.has(KEY_VIEWER_PREVIEW_BYTES)) prefs.setViewerPreviewBytes(obj.getInt(KEY_VIEWER_PREVIEW_BYTES).coerceIn(1024, 10 * 1024 * 1024))
    if (obj.has(KEY_FTP_PASSWORD)) prefs.setFtpPassword(obj.optString(KEY_FTP_PASSWORD).ifBlank { null })
    if (obj.has(KEY_FTP_TIMEOUT_MINUTES)) prefs.setFtpTimeoutMinutes(obj.getInt(KEY_FTP_TIMEOUT_MINUTES).coerceIn(0, 1440))
    if (obj.has(KEY_GIT_REPO_URL)) prefs.setGitRepoUrl(obj.optString(KEY_GIT_REPO_URL).ifBlank { null })
    if (obj.has(KEY_GIT_USER_NAME)) prefs.setGitUserName(obj.optString(KEY_GIT_USER_NAME).ifBlank { null })
    if (obj.has(KEY_GIT_USER_EMAIL)) prefs.setGitUserEmail(obj.optString(KEY_GIT_USER_EMAIL).ifBlank { null })
    if (obj.has(KEY_GIT_HTTPS_PASSWORD)) prefs.setGitHttpsPassword(obj.optString(KEY_GIT_HTTPS_PASSWORD).ifBlank { null })
    if (obj.has(KEY_GIT_CONFIG_APPLIED)) prefs.setGitConfigApplied(obj.getBoolean(KEY_GIT_CONFIG_APPLIED))
    if (obj.has(KEY_PLAYER_PLAYLISTS)) {
        val playlists = runCatching {
            val arr = obj.optJSONArray(KEY_PLAYER_PLAYLISTS) ?: JSONArray()
            (0 until arr.length()).map { Playlist.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        when (playlistImportMode) {
            ConfigPlaylistImportMode.OVERWRITE -> prefs.replacePlaylists(playlists)
            ConfigPlaylistImportMode.APPEND -> prefs.appendPlaylists(playlists)
        }
    }

    if (importKeys) {
        val keyDir = getGpgKeyDir(context)
        if (obj.has(KEY_GPG_PUBLIC_KEYS_BASE64)) {
            try {
                val bytes = Base64.decode(obj.getString(KEY_GPG_PUBLIC_KEYS_BASE64), Base64.NO_WRAP)
                File(keyDir, "pubring.gpg").writeBytes(bytes)
            } catch (_: Exception) { }
        }
        if (obj.has(KEY_GPG_SECRET_KEYS_BASE64)) {
            try {
                val bytes = Base64.decode(obj.getString(KEY_GPG_SECRET_KEYS_BASE64), Base64.NO_WRAP)
                File(keyDir, "secring.gpg").writeBytes(bytes)
            } catch (_: Exception) { }
        }
    }

    return true
}
