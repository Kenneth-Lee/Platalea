package com.kenny.localmanager.data

import android.content.Context
import android.util.Base64
import com.kenny.localmanager.gpg.getGpgKeyDir
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val KEY_CONFIG_VERSION = "config_version"
private const val KEY_FILTER_VISIBLE = "filter_visible"
private const val KEY_HIDE_DOT_FILES = "hide_dot_files"
private const val KEY_VIEWER_PREVIEW_BYTES = "viewer_preview_bytes"
private const val KEY_FTP_PORT = "ftp_port"
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
private const val KEY_PLAYER_LIST_BOOKMARKS = "player_list_bookmarks"
private const val KEY_PLAYER_PLAYLIST_RESUMES = "player_playlist_resumes"
private const val KEY_EXTERNAL_OPEN_BY_EXTENSION = "external_open_by_extension"
private const val KEY_ROOT_BOOKMARKS = "root_bookmarks"
private const val KEY_LEGACY_PLAYER_LIST_BOOKMARK = "player_list_bookmark"

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
    obj.put(KEY_CONFIG_VERSION, 2)
    obj.put(KEY_FILTER_VISIBLE, prefs.filterVisible.first())
    obj.put(KEY_HIDE_DOT_FILES, prefs.hideDotFiles.first())
    obj.put(KEY_VIEWER_PREVIEW_BYTES, prefs.viewerPreviewBytes.first())
    obj.put(KEY_FTP_PORT, prefs.ftpPort.first())
    prefs.ftpPassword.first()?.let { obj.put(KEY_FTP_PASSWORD, it) }
    obj.put(KEY_FTP_TIMEOUT_MINUTES, prefs.ftpTimeoutMinutes.first())
    prefs.gitRepoUrl.first()?.let { obj.put(KEY_GIT_REPO_URL, it) }
    prefs.gitUserName.first()?.let { obj.put(KEY_GIT_USER_NAME, it) }
    prefs.gitUserEmail.first()?.let { obj.put(KEY_GIT_USER_EMAIL, it) }
    prefs.gitHttpsPassword.first()?.let { obj.put(KEY_GIT_HTTPS_PASSWORD, it) }
    obj.put(KEY_GIT_CONFIG_APPLIED, prefs.gitConfigApplied.first())
    obj.put(KEY_PLAYER_PLAYLISTS, JSONArray(prefs.playlists.first().map { it.toJson() }))
    obj.put(KEY_PLAYER_LIST_BOOKMARKS, JSONArray(prefs.playerListBookmarks.first().map { it.toJson() }))
    obj.put(KEY_PLAYER_PLAYLIST_RESUMES, prefs.playerPlaylistResumeStates.first().toJson())
    obj.put(KEY_EXTERNAL_OPEN_BY_EXTENSION, JSONObject(prefs.externalOpenByExtension.first()))
    obj.put(KEY_ROOT_BOOKMARKS, RootBookmarkManager(context).toJson())

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
    if (obj.has(KEY_FTP_PORT)) prefs.setFtpPort(obj.getInt(KEY_FTP_PORT).coerceIn(1024, 65535))
    if (obj.has(KEY_FTP_PASSWORD)) prefs.setFtpPassword(obj.optString(KEY_FTP_PASSWORD).ifBlank { null })
    if (obj.has(KEY_FTP_TIMEOUT_MINUTES)) prefs.setFtpTimeoutMinutes(obj.getInt(KEY_FTP_TIMEOUT_MINUTES).coerceIn(0, 1440))
    if (obj.has(KEY_GIT_REPO_URL)) prefs.setGitRepoUrl(obj.optString(KEY_GIT_REPO_URL).ifBlank { null })
    if (obj.has(KEY_GIT_USER_NAME)) prefs.setGitUserName(obj.optString(KEY_GIT_USER_NAME).ifBlank { null })
    if (obj.has(KEY_GIT_USER_EMAIL)) prefs.setGitUserEmail(obj.optString(KEY_GIT_USER_EMAIL).ifBlank { null })
    if (obj.has(KEY_GIT_HTTPS_PASSWORD)) prefs.setGitHttpsPassword(obj.optString(KEY_GIT_HTTPS_PASSWORD).ifBlank { null })
    if (obj.has(KEY_GIT_CONFIG_APPLIED)) prefs.setGitConfigApplied(obj.getBoolean(KEY_GIT_CONFIG_APPLIED))
    var playlistIdMapping = emptyMap<String, String>()
    if (obj.has(KEY_PLAYER_PLAYLISTS)) {
        val playlists = runCatching {
            val arr = obj.optJSONArray(KEY_PLAYER_PLAYLISTS) ?: JSONArray()
            (0 until arr.length()).map { Playlist.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        when (playlistImportMode) {
            ConfigPlaylistImportMode.OVERWRITE -> {
                prefs.replacePlaylists(playlists)
                playlistIdMapping = playlists.associate { it.id to it.id }
            }
            ConfigPlaylistImportMode.APPEND -> {
                playlistIdMapping = prefs.appendPlaylists(playlists)
            }
        }
    }
    if (obj.has(KEY_PLAYER_LIST_BOOKMARKS) || obj.has(KEY_LEGACY_PLAYER_LIST_BOOKMARK)) {
        val bookmarks = parseImportedPlayerBookmarks(obj)
            .map { bookmark ->
                val mappedPlaylistId = bookmark.playlistId?.let { playlistIdMapping[it] ?: it }
                bookmark.copy(playlistId = mappedPlaylistId)
            }
        when (playlistImportMode) {
            ConfigPlaylistImportMode.OVERWRITE -> prefs.replacePlayerListBookmarks(bookmarks)
            ConfigPlaylistImportMode.APPEND -> prefs.appendPlayerListBookmarks(bookmarks)
        }
    }
    if (obj.has(KEY_PLAYER_PLAYLIST_RESUMES)) {
        val resumes = parseImportedPlayerResumes(obj.opt(KEY_PLAYER_PLAYLIST_RESUMES))
            .mapKeys { (playlistId, _) -> playlistIdMapping[playlistId] ?: playlistId }
        when (playlistImportMode) {
            ConfigPlaylistImportMode.OVERWRITE -> prefs.replacePlayerResumeStates(resumes)
            ConfigPlaylistImportMode.APPEND -> prefs.mergePlayerResumeStates(resumes)
        }
    }
    if (obj.has(KEY_EXTERNAL_OPEN_BY_EXTENSION)) {
        prefs.replaceExternalOpenPackages(parseExternalOpenMapping(obj.opt(KEY_EXTERNAL_OPEN_BY_EXTENSION)))
    }
    if (obj.has(KEY_ROOT_BOOKMARKS)) {
        RootBookmarkManager(context).importAll(parseRootBookmarks(obj.opt(KEY_ROOT_BOOKMARKS)))
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

private fun PlayerListBookmark.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("playlistId", playlistId ?: "")
    put("dirUri", dirUri ?: "")
    put("trackIndex", trackIndex)
    put("positionMs", positionMs)
    put("trackName", trackName)
    put("note", note)
    put("savedAt", savedAt)
}

private fun Map<String, PlayerResumeState>.toJson(): JSONObject = JSONObject().apply {
    forEach { (playlistId, state) ->
        put(
            playlistId,
            JSONObject().apply {
                put("i", state.trackIndex)
                put("p", state.positionMs)
            }
        )
    }
}

private fun RootBookmarkManager.toJson(): JSONArray {
    val arr = JSONArray()
    exportAll().forEach { (root, paths) ->
        arr.put(
            JSONObject().apply {
                put("root", root)
                put("paths", JSONArray(paths))
            }
        )
    }
    return arr
}

private fun parseImportedPlayerBookmarks(obj: JSONObject): List<PlayerListBookmark> {
    val newArr = obj.optJSONArray(KEY_PLAYER_LIST_BOOKMARKS)
    if (newArr != null) {
        return (0 until newArr.length()).mapNotNull { index ->
            newArr.optJSONObject(index)?.toPlayerListBookmark()
        }
    }
    val legacy = obj.opt(KEY_LEGACY_PLAYER_LIST_BOOKMARK)
    return when (legacy) {
        is JSONObject -> legacy.toPlayerListBookmark()?.let { listOf(it) } ?: emptyList()
        is JSONArray -> (0 until legacy.length()).mapNotNull { index -> legacy.optJSONObject(index)?.toPlayerListBookmark() }
        else -> emptyList()
    }
}

private fun JSONObject.toPlayerListBookmark(): PlayerListBookmark? {
    return runCatching {
        PlayerListBookmark(
            id = optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            playlistId = optString("playlistId").ifBlank { null },
            dirUri = optString("dirUri").ifBlank { null },
            trackIndex = optInt("trackIndex", 0).coerceAtLeast(0),
            positionMs = optLong("positionMs", 0L).coerceAtLeast(0L),
            trackName = optString("trackName", ""),
            note = optString("note", ""),
            savedAt = optLong("savedAt", 0L)
        )
    }.getOrNull()
}

private fun parseImportedPlayerResumes(value: Any?): Map<String, PlayerResumeState> {
    val obj = value as? JSONObject ?: return emptyMap()
    return buildMap {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val playlistId = keys.next()
            val state = obj.optJSONObject(playlistId) ?: continue
            put(
                playlistId,
                PlayerResumeState(
                    trackIndex = state.optInt("i", 0).coerceAtLeast(0),
                    positionMs = state.optLong("p", 0L).coerceAtLeast(0L)
                )
            )
        }
    }
}

private fun parseExternalOpenMapping(value: Any?): Map<String, String> {
    val obj = value as? JSONObject ?: return emptyMap()
    return buildMap {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val extension = keys.next().trim().lowercase()
            val packageName = obj.optString(extension).trim()
            if (extension.isNotBlank() && packageName.isNotBlank()) {
                put(extension, packageName)
            }
        }
    }
}

private fun parseRootBookmarks(value: Any?): Map<String, List<String>> {
    val arr = value as? JSONArray ?: return emptyMap()
    return buildMap {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val root = item.optString("root").trim()
            if (root.isBlank()) continue
            val pathsJson = item.optJSONArray("paths") ?: JSONArray()
            val paths = buildList {
                for (j in 0 until pathsJson.length()) {
                    val path = pathsJson.optString(j).trim()
                    if (path.isNotBlank()) add(path)
                }
            }
            put(root, paths)
        }
    }
}
