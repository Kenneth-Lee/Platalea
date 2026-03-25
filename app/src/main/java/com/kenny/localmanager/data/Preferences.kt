package com.kenny.localmanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(name = "settings")

private val ROOT_URI = stringPreferencesKey("root_uri")
private val HIDE_DOT_FILES = booleanPreferencesKey("hide_dot_files")
private val VIEWER_PREVIEW_BYTES = intPreferencesKey("viewer_preview_bytes")
private val FTP_PORT = intPreferencesKey("ftp_port")
private val FTP_PASSWORD = stringPreferencesKey("ftp_password")
private val FTP_TIMEOUT_MINUTES = intPreferencesKey("ftp_timeout_minutes")
private val FILTER_VISIBLE = booleanPreferencesKey("filter_visible")
private val GIT_REPO_URL = stringPreferencesKey("git_repo_url")
private val GIT_USER_NAME = stringPreferencesKey("git_user_name")
private val GIT_USER_EMAIL = stringPreferencesKey("git_user_email")
private val GIT_HTTPS_PASSWORD = stringPreferencesKey("git_https_password")
private val GIT_CONFIG_APPLIED = booleanPreferencesKey("git_config_applied")
private val PLAYER_LAST_DIR_URI = stringPreferencesKey("player_last_dir_uri")
private val PLAYER_LAST_INDEX = intPreferencesKey("player_last_index")
private val PLAYER_LAST_POSITION_MS = longPreferencesKey("player_last_position_ms")
private val PLAYER_LAST_PLAYLIST_ID = stringPreferencesKey("player_last_playlist_id")
private val PLAYER_PLAYLISTS_JSON = stringPreferencesKey("player_playlists_json")
private val PLAYER_PLAYLIST_RESUME_JSON = stringPreferencesKey("player_playlist_resume_json")
private val PLAYER_LIST_BOOKMARK_JSON = stringPreferencesKey("player_list_bookmark_json")
private val STARTUP_DECRYPT_KEY = booleanPreferencesKey("startup_decrypt_key")
private val EXTERNAL_OPEN_BY_EXTENSION_JSON = stringPreferencesKey("external_open_by_extension_json")

data class PlaylistAppendResult(
    val found: Boolean,
    val appendedCount: Int,
    val skippedCount: Int
)

data class PlayerListBookmark(
    val playlistId: String?,
    val dirUri: String?,
    val trackIndex: Int,
    val positionMs: Long,
    val trackName: String,
    val savedAt: Long
)

class Preferences(private val context: Context) {
    val rootUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ROOT_URI]
    }

    val hideDotFiles: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HIDE_DOT_FILES] ?: false
    }

    val viewerPreviewBytes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[VIEWER_PREVIEW_BYTES] ?: 4096
    }

    val ftpPort: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[FTP_PORT] ?: 2121
    }

    val ftpPassword: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[FTP_PASSWORD]
    }

    val ftpTimeoutMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[FTP_TIMEOUT_MINUTES] ?: 0
    }

    val filterVisible: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[FILTER_VISIBLE] ?: true
    }

    val gitRepoUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[GIT_REPO_URL]
    }

    val gitUserName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[GIT_USER_NAME]
    }

    val gitUserEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[GIT_USER_EMAIL]
    }

    val gitHttpsPassword: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[GIT_HTTPS_PASSWORD]
    }

    val gitConfigApplied: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[GIT_CONFIG_APPLIED] ?: false
    }

    val playerLastDirUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PLAYER_LAST_DIR_URI]
    }

    val playerLastIndex: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PLAYER_LAST_INDEX] ?: 0
    }

    val playerLastPositionMs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[PLAYER_LAST_POSITION_MS] ?: 0L
    }

    val externalOpenByExtension: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val json = prefs[EXTERNAL_OPEN_BY_EXTENSION_JSON] ?: return@map emptyMap()
        try {
            val obj = org.json.JSONObject(json)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next().trim().lowercase()
                    val value = obj.optString(key).trim()
                    if (key.isNotBlank() && value.isNotBlank()) put(key, value)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** 最后一次播放的列表 ID（用于停止后「恢复播放」）。 */
    val playerLastPlaylistId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PLAYER_LAST_PLAYLIST_ID]
    }

    /** 手动保存的播放书签（用于随时跳转到列表中的特定曲目和进度）。 */
    val playerListBookmark: Flow<PlayerListBookmark?> = context.dataStore.data.map { prefs ->
        val json = prefs[PLAYER_LIST_BOOKMARK_JSON] ?: return@map null
        try {
            val obj = org.json.JSONObject(json)
            PlayerListBookmark(
                playlistId = obj.optString("playlistId").ifBlank { null },
                dirUri = obj.optString("dirUri").ifBlank { null },
                trackIndex = obj.optInt("trackIndex", 0).coerceAtLeast(0),
                positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
                trackName = obj.optString("trackName", ""),
                savedAt = obj.optLong("savedAt", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setRootUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(ROOT_URI)
            else prefs[ROOT_URI] = uri
        }
    }

    suspend fun setHideDotFiles(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HIDE_DOT_FILES] = enabled
        }
    }

    suspend fun setViewerPreviewBytes(bytes: Int) {
        context.dataStore.edit { prefs ->
            prefs[VIEWER_PREVIEW_BYTES] = bytes
        }
    }

    suspend fun setExternalOpenPackageForExtension(extension: String, packageName: String?) {
        val normalizedExtension = extension.trim().lowercase().removePrefix(".")
        if (normalizedExtension.isBlank()) return
        context.dataStore.edit { prefs ->
            val obj = try {
                org.json.JSONObject(prefs[EXTERNAL_OPEN_BY_EXTENSION_JSON] ?: "{}")
            } catch (_: Exception) {
                org.json.JSONObject()
            }
            if (packageName.isNullOrBlank()) {
                obj.remove(normalizedExtension)
            } else {
                obj.put(normalizedExtension, packageName.trim())
            }
            if (obj.length() == 0) prefs.remove(EXTERNAL_OPEN_BY_EXTENSION_JSON)
            else prefs[EXTERNAL_OPEN_BY_EXTENSION_JSON] = obj.toString()
        }
    }

    suspend fun clearExternalOpenPackageForExtension(extension: String) {
        setExternalOpenPackageForExtension(extension, null)
    }

    suspend fun setFtpPort(port: Int) {
        context.dataStore.edit { prefs ->
            prefs[FTP_PORT] = port.coerceIn(1024, 65535)
        }
    }

    suspend fun setFtpPassword(password: String?) {
        context.dataStore.edit { prefs ->
            if (password.isNullOrBlank()) prefs.remove(FTP_PASSWORD)
            else prefs[FTP_PASSWORD] = password
        }
    }

    suspend fun setFtpTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[FTP_TIMEOUT_MINUTES] = minutes.coerceIn(0, 1440)
        }
    }

    suspend fun setFilterVisible(visible: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[FILTER_VISIBLE] = visible
        }
    }

    suspend fun setGitRepoUrl(url: String?) {
        context.dataStore.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(GIT_REPO_URL)
            else prefs[GIT_REPO_URL] = url.trim()
        }
    }

    suspend fun setGitUserName(name: String?) {
        context.dataStore.edit { prefs ->
            if (name.isNullOrBlank()) prefs.remove(GIT_USER_NAME)
            else prefs[GIT_USER_NAME] = name.trim()
        }
    }

    suspend fun setGitUserEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email.isNullOrBlank()) prefs.remove(GIT_USER_EMAIL)
            else prefs[GIT_USER_EMAIL] = email.trim()
        }
    }

    suspend fun setGitHttpsPassword(password: String?) {
        context.dataStore.edit { prefs ->
            if (password.isNullOrBlank()) prefs.remove(GIT_HTTPS_PASSWORD)
            else prefs[GIT_HTTPS_PASSWORD] = password
        }
    }

    suspend fun setGitConfigApplied(applied: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[GIT_CONFIG_APPLIED] = applied
        }
    }

    suspend fun setPlayerLastState(dirUri: String?, index: Int, positionMs: Long) {
        context.dataStore.edit { prefs ->
            if (dirUri == null) {
                prefs.remove(PLAYER_LAST_DIR_URI)
                prefs.remove(PLAYER_LAST_INDEX)
                prefs.remove(PLAYER_LAST_POSITION_MS)
            } else {
                prefs[PLAYER_LAST_DIR_URI] = dirUri
                prefs[PLAYER_LAST_INDEX] = index.coerceAtLeast(0)
                prefs[PLAYER_LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
            }
        }
    }

    val playlists: Flow<List<Playlist>> = context.dataStore.data.map { prefs ->
        Playlist.listFromJson(prefs[PLAYER_PLAYLISTS_JSON] ?: "")
    }

    suspend fun getPlaylistById(id: String): Playlist? =
        playlists.first().find { it.id == id }

    suspend fun addPlaylist(playlist: Playlist) {
        context.dataStore.edit { prefs ->
            val list = Playlist.listFromJson(prefs[PLAYER_PLAYLISTS_JSON] ?: "") + playlist
            prefs[PLAYER_PLAYLISTS_JSON] = Playlist.listToJson(list)
        }
    }

    suspend fun appendTracksToPlaylist(playlistId: String, uris: List<String>, names: List<String>): PlaylistAppendResult {
        val trackCount = minOf(uris.size, names.size)
        if (trackCount <= 0) return PlaylistAppendResult(found = false, appendedCount = 0, skippedCount = 0)
        var found = false
        var appendedCount = 0
        var skippedCount = 0
        context.dataStore.edit { prefs ->
            val list = Playlist.listFromJson(prefs[PLAYER_PLAYLISTS_JSON] ?: "").map { playlist ->
                if (playlist.id != playlistId) {
                    playlist
                } else {
                    found = true
                    val existingUris = playlist.uris.toMutableSet()
                    val newUris = mutableListOf<String>()
                    val newNames = mutableListOf<String>()
                    repeat(trackCount) { index ->
                        val uri = uris[index]
                        if (existingUris.add(uri)) {
                            newUris += uri
                            newNames += names[index]
                        } else {
                            skippedCount++
                        }
                    }
                    appendedCount = newUris.size
                    playlist.copy(
                        uris = playlist.uris + newUris,
                        names = playlist.names + newNames
                    )
                }
            }
            prefs[PLAYER_PLAYLISTS_JSON] = Playlist.listToJson(list)
        }
        return PlaylistAppendResult(found = found, appendedCount = appendedCount, skippedCount = skippedCount)
    }

    suspend fun appendPlaylists(playlists: List<Playlist>) {
        if (playlists.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = Playlist.listFromJson(prefs[PLAYER_PLAYLISTS_JSON] ?: "")
            val usedIds = existing.map { it.id }.toMutableSet()
            val normalized = playlists.map { playlist ->
                if (playlist.id !in usedIds) {
                    usedIds += playlist.id
                    playlist
                } else {
                    var newId = UUID.randomUUID().toString()
                    while (newId in usedIds) newId = UUID.randomUUID().toString()
                    usedIds += newId
                    playlist.copy(id = newId)
                }
            }
            prefs[PLAYER_PLAYLISTS_JSON] = Playlist.listToJson(existing + normalized)
        }
    }

    suspend fun replacePlaylists(playlists: List<Playlist>) {
        context.dataStore.edit { prefs ->
            if (playlists.isEmpty()) prefs.remove(PLAYER_PLAYLISTS_JSON)
            else prefs[PLAYER_PLAYLISTS_JSON] = Playlist.listToJson(playlists)
        }
    }

    suspend fun removePlaylist(id: String) {
        context.dataStore.edit { prefs ->
            val list = Playlist.listFromJson(prefs[PLAYER_PLAYLISTS_JSON] ?: "").filter { it.id != id }
            prefs[PLAYER_PLAYLISTS_JSON] = Playlist.listToJson(list)
        }
    }

    suspend fun updatePlaylistOrder(orderedIds: List<String>) {
        context.dataStore.edit { prefs ->
            val list = Playlist.listFromJson(prefs[PLAYER_PLAYLISTS_JSON] ?: "")
            val byId = list.associateBy { it.id }
            val ordered = orderedIds.mapNotNull { byId[it] }
            val rest = list.filter { it.id !in orderedIds.toSet() }
            prefs[PLAYER_PLAYLISTS_JSON] = Playlist.listToJson(ordered + rest)
        }
    }

    /** 更新播放列表内容（曲目顺序/删除等），按 id 替换同 id 的列表。 */
    suspend fun updatePlaylist(playlist: Playlist) {
        context.dataStore.edit { prefs ->
            val list = Playlist.listFromJson(prefs[PLAYER_PLAYLISTS_JSON] ?: "").map {
                if (it.id == playlist.id) playlist else it
            }
            prefs[PLAYER_PLAYLISTS_JSON] = Playlist.listToJson(list)
        }
    }

    /** 按播放列表 ID 保存/读取进度（每个列表单独记，切换回时能恢复）。 */
    suspend fun setPlayerLastStateForPlaylist(playlistId: String, index: Int, positionMs: Long) {
        context.dataStore.edit { prefs ->
            val json = prefs[PLAYER_PLAYLIST_RESUME_JSON] ?: "{}"
            val map = try {
                org.json.JSONObject(json)
            } catch (_: Exception) {
                org.json.JSONObject()
            }
            map.put(playlistId, org.json.JSONObject().apply {
                put("i", index.coerceAtLeast(0))
                put("p", positionMs.coerceAtLeast(0L))
            })
            prefs[PLAYER_PLAYLIST_RESUME_JSON] = map.toString()
            prefs[PLAYER_LAST_PLAYLIST_ID] = playlistId
            prefs[PLAYER_LAST_INDEX] = index.coerceAtLeast(0)
            prefs[PLAYER_LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }

    suspend fun getPlayerResumeStateForPlaylist(playlistId: String): Pair<Int, Long>? {
        val json = context.dataStore.data.first()[PLAYER_PLAYLIST_RESUME_JSON] ?: return null
        return try {
            val map = org.json.JSONObject(json)
            val obj = map.optJSONObject(playlistId) ?: return null
            val idx = obj.optInt("i", 0).coerceAtLeast(0)
            val pos = obj.optLong("p", 0L).coerceAtLeast(0L)
            idx to pos
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setPlayerListBookmark(
        playlistId: String?,
        dirUri: String?,
        trackIndex: Int,
        positionMs: Long,
        trackName: String
    ) {
        context.dataStore.edit { prefs ->
            val obj = org.json.JSONObject().apply {
                put("playlistId", playlistId ?: "")
                put("dirUri", dirUri ?: "")
                put("trackIndex", trackIndex.coerceAtLeast(0))
                put("positionMs", positionMs.coerceAtLeast(0L))
                put("trackName", trackName)
                put("savedAt", System.currentTimeMillis())
            }
            prefs[PLAYER_LIST_BOOKMARK_JSON] = obj.toString()
        }
    }

    suspend fun clearPlayerListBookmark() {
        context.dataStore.edit { prefs ->
            prefs.remove(PLAYER_LIST_BOOKMARK_JSON)
        }
    }

    /** 启动时解密密钥：开启后启动需输入私钥密码解锁，解密成功则缓存在内存，后续使用密钥不再询问。不参与导出。默认关闭。 */
    val startupDecryptKey: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[STARTUP_DECRYPT_KEY] ?: false
    }

    suspend fun setStartupDecryptKey(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[STARTUP_DECRYPT_KEY] = enabled
        }
    }
}
