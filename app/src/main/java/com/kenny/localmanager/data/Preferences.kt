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
private val LOCAL_NETWORK_SERVICE_ENABLED = booleanPreferencesKey("local_network_service_enabled")
private val FAMILY_NETWORK_USER_NAME = stringPreferencesKey("family_network_user_name")
private val FAMILY_NETWORK_HOST_PASSWORD = stringPreferencesKey("family_network_host_password")
private val FTP_TIMEOUT_MINUTES = intPreferencesKey("ftp_timeout_minutes")
private val FILTER_VISIBLE = booleanPreferencesKey("filter_visible")
private val GIT_REPO_URL = stringPreferencesKey("git_repo_url")
private val GIT_USER_NAME = stringPreferencesKey("git_user_name")
private val GIT_USER_EMAIL = stringPreferencesKey("git_user_email")
private val GIT_HTTPS_PASSWORD = stringPreferencesKey("git_https_password")
private val GIT_CONFIG_APPLIED = booleanPreferencesKey("git_config_applied")
private val GIT_MANAGED_PROJECTS_JSON = stringPreferencesKey("git_managed_projects_json")
private val PLAYER_LAST_DIR_URI = stringPreferencesKey("player_last_dir_uri")
private val PLAYER_LAST_INDEX = intPreferencesKey("player_last_index")
private val PLAYER_LAST_POSITION_MS = longPreferencesKey("player_last_position_ms")
private val PLAYER_LAST_PLAYLIST_ID = stringPreferencesKey("player_last_playlist_id")
private val PLAYER_PLAYLISTS_JSON = stringPreferencesKey("player_playlists_json")
private val PLAYER_PLAYLIST_RESUME_JSON = stringPreferencesKey("player_playlist_resume_json")
private val PLAYER_LIST_BOOKMARKS_JSON = stringPreferencesKey("player_list_bookmarks_json")
private val PLAYER_AUDIO_ENGINE = stringPreferencesKey("player_audio_engine")
private val PLAYER_KEEP_AWAKE = booleanPreferencesKey("player_keep_awake")
private val PLAYER_AUDIO_EFFECTS_ENABLED = booleanPreferencesKey("player_audio_effects_enabled")
private val PLAYER_AUDIO_EFFECT_PRESET = stringPreferencesKey("player_audio_effect_preset")
private val PLAYER_HIGH_QUALITY_OUTPUT = booleanPreferencesKey("player_high_quality_output")
private val LAST_MAIN_TAB = stringPreferencesKey("last_main_tab")
private val RECENT_OPEN_ITEMS_JSON = stringPreferencesKey("recent_open_items_json")
private val STARTUP_DECRYPT_KEY = booleanPreferencesKey("startup_decrypt_key")
private val EXTERNAL_OPEN_BY_EXTENSION_JSON = stringPreferencesKey("external_open_by_extension_json")
private val EPUB_DICT_AREA_EXPANDED = booleanPreferencesKey("epub_dict_area_expanded")
private val EPUB_DICT_LOOKUP_WORDS_JSON = stringPreferencesKey("epub_dict_lookup_words_json")
private val EPUB_ZOOM_PERCENT_BY_URI_JSON = stringPreferencesKey("epub_zoom_percent_by_uri_json")
private val PDF_LAST_PAGE_BY_URI_JSON = stringPreferencesKey("pdf_last_page_by_uri_json")
private val PDF_ZOOM_PERCENT_BY_URI_JSON = stringPreferencesKey("pdf_zoom_percent_by_uri_json")
private val EPUB_TTS_ENGINE_PACKAGE = stringPreferencesKey("epub_tts_engine_package")
private val EPUB_TTS_VOICE_NAME = stringPreferencesKey("epub_tts_voice_name")
private val EPUB_TTS_SPEED_PERCENT = intPreferencesKey("epub_tts_speed_percent")
private val EPUB_TTS_AUTO_NEXT_CHAPTER = booleanPreferencesKey("epub_tts_auto_next_chapter")
private val HIDE_READER_FLOATING_NEXT_BUTTON = booleanPreferencesKey("hide_reader_floating_next_button")
private val READER_FLOATING_NEXT_BUTTON_X_PERCENT = intPreferencesKey("reader_floating_next_button_x_percent")
private val READER_FLOATING_NEXT_BUTTON_Y_PERCENT = intPreferencesKey("reader_floating_next_button_y_percent")
private val DICT_QUERY_HISTORY_JSON = stringPreferencesKey("dict_query_history_json")
private val QUICK_NOTE_LAST_CATEGORY = stringPreferencesKey("quick_note_last_category")
private val RECENT_ROOT_URIS_JSON = stringPreferencesKey("recent_root_uris_json")

data class PlaylistAppendResult(
    val found: Boolean,
    val appendedCount: Int,
    val skippedCount: Int
)

data class PlayerListBookmark(
    val id: String,
    val playlistId: String?,
    val dirUri: String?,
    val trackIndex: Int,
    val positionMs: Long,
    val trackName: String,
    val note: String,
    val savedAt: Long
)

data class PlayerResumeState(
    val trackIndex: Int,
    val positionMs: Long
)

const val PLAYER_AUDIO_ENGINE_MEDIA_PLAYER = "media_player"
const val PLAYER_AUDIO_ENGINE_EXO_PLAYER = "exo_player"
const val PLAYER_AUDIO_PRESET_FLAT = "flat"
const val PLAYER_AUDIO_PRESET_VOCAL = "vocal"
const val PLAYER_AUDIO_PRESET_BASS = "bass"
const val PLAYER_AUDIO_PRESET_CAR = "car"
const val PLAYER_AUDIO_PRESET_HEADPHONE = "headphone"

data class PlayerAudioSettings(
    val engine: String = PLAYER_AUDIO_ENGINE_MEDIA_PLAYER,
    val keepAwake: Boolean = false,
    val audioEffectsEnabled: Boolean = false,
    val effectPreset: String = PLAYER_AUDIO_PRESET_FLAT,
    val highQualityOutput: Boolean = false
)

data class RecentOpenItem(
    val type: String,
    val key: String,
    val title: String,
    val uri: String?,
    val playlistId: String?,
    val openedAt: Long
)

data class ManagedGitProject(
    val id: String,
    val projectName: String,
    val repoUrl: String,
    val localDirName: String,
    val branchName: String?,
    val createdAt: Long,
    val lastSyncAt: Long,
    val lastPushAt: Long
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

    val epubDictAreaExpanded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[EPUB_DICT_AREA_EXPANDED] ?: false
    }

    val epubDictLookupWords: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseStringListJson(prefs[EPUB_DICT_LOOKUP_WORDS_JSON])
    }

    val epubTtsEnginePackage: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[EPUB_TTS_ENGINE_PACKAGE]?.trim()?.takeIf { it.isNotEmpty() }
    }

    val epubTtsVoiceName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[EPUB_TTS_VOICE_NAME]?.trim()?.takeIf { it.isNotEmpty() }
    }

    val epubTtsSpeedPercent: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[EPUB_TTS_SPEED_PERCENT] ?: 100).coerceIn(50, 300)
    }

    val epubTtsAutoNextChapter: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[EPUB_TTS_AUTO_NEXT_CHAPTER] ?: true
    }

    val hideReaderFloatingNextButton: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HIDE_READER_FLOATING_NEXT_BUTTON] ?: false
    }

    val readerFloatingNextButtonXPercent: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[READER_FLOATING_NEXT_BUTTON_X_PERCENT] ?: 100).coerceIn(0, 100)
    }

    val readerFloatingNextButtonYPercent: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[READER_FLOATING_NEXT_BUTTON_Y_PERCENT] ?: 82).coerceIn(0, 100)
    }

    val dictQueryHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseStringListJson(prefs[DICT_QUERY_HISTORY_JSON])
    }

    val quickNoteLastCategory: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[QUICK_NOTE_LAST_CATEGORY]?.trim()?.takeIf { it.isNotEmpty() }
    }

    val recentRootUris: Flow<List<String>> = context.dataStore.data.map { prefs ->
        parseStringListJson(prefs[RECENT_ROOT_URIS_JSON])
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

    val networkServiceTimeoutMinutes: Flow<Int> = ftpTimeoutMinutes

    val localNetworkServiceEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LOCAL_NETWORK_SERVICE_ENABLED] ?: true
    }

    val familyNetworkUserName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[FAMILY_NETWORK_USER_NAME]
    }

    val familyNetworkHostPassword: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[FAMILY_NETWORK_HOST_PASSWORD]
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

    val managedGitProjects: Flow<List<ManagedGitProject>> = context.dataStore.data.map { prefs ->
        parseManagedGitProjects(prefs[GIT_MANAGED_PROJECTS_JSON])
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
        parseExternalOpenByExtensionMap(prefs[EXTERNAL_OPEN_BY_EXTENSION_JSON])
    }

    /** 主界面上次停留 tab。默认目录页。 */
    val lastMainTab: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LAST_MAIN_TAB] ?: "directory"
    }

    val recentOpenItems: Flow<List<RecentOpenItem>> = context.dataStore.data.map { prefs ->
        parseRecentOpenItems(prefs[RECENT_OPEN_ITEMS_JSON])
    }

    val playerPlaylistResumeStates: Flow<Map<String, PlayerResumeState>> = context.dataStore.data.map { prefs ->
        parsePlayerResumeStates(prefs[PLAYER_PLAYLIST_RESUME_JSON])
    }

    val playerAudioSettings: Flow<PlayerAudioSettings> = context.dataStore.data.map { prefs ->
        PlayerAudioSettings(
            engine = normalizePlayerAudioEngine(prefs[PLAYER_AUDIO_ENGINE]),
            keepAwake = prefs[PLAYER_KEEP_AWAKE] ?: false,
            audioEffectsEnabled = prefs[PLAYER_AUDIO_EFFECTS_ENABLED] ?: false,
            effectPreset = normalizePlayerAudioPreset(prefs[PLAYER_AUDIO_EFFECT_PRESET]),
            highQualityOutput = prefs[PLAYER_HIGH_QUALITY_OUTPUT] ?: false
        )
    }

    /** 最后一次播放的列表 ID（用于停止后「恢复播放」）。 */
    val playerLastPlaylistId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PLAYER_LAST_PLAYLIST_ID]
    }

    /** 手动保存的播放书签（支持一个播放列表多个书签，可备注）。 */
    val playerListBookmarks: Flow<List<PlayerListBookmark>> = context.dataStore.data.map { prefs ->
        // 兼容历史版本：旧版本是单对象字段 player_list_bookmark_json
        val legacyJson = prefs[stringPreferencesKey("player_list_bookmark_json")]
        val json = prefs[PLAYER_LIST_BOOKMARKS_JSON]
        when {
            !json.isNullOrBlank() -> parsePlayerListBookmarks(json)
            !legacyJson.isNullOrBlank() -> parseLegacyPlayerListBookmark(legacyJson)?.let { listOf(it) } ?: emptyList()
            else -> emptyList()
        }
    }

    suspend fun setRootUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(ROOT_URI)
            else prefs[ROOT_URI] = uri
        }
    }

    suspend fun recordRecentRootSwitch(fromUri: String?, toUri: String?) {
        context.dataStore.edit { prefs ->
            val normalizedFrom = fromUri?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedTo = toUri?.trim()?.takeIf { it.isNotEmpty() }
            val updated = buildList {
                normalizedFrom?.let { add(it) }
                addAll(parseStringListJson(prefs[RECENT_ROOT_URIS_JSON]))
            }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { it == normalizedTo }
                .distinct()
                .take(2)
            if (updated.isEmpty()) {
                prefs.remove(RECENT_ROOT_URIS_JSON)
            } else {
                prefs[RECENT_ROOT_URIS_JSON] = stringListToJson(updated)
            }
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

    suspend fun setEpubDictAreaExpanded(expanded: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[EPUB_DICT_AREA_EXPANDED] = expanded
        }
    }

    suspend fun setEpubDictLookupWords(words: List<String>) {
        context.dataStore.edit { prefs ->
            val normalized = words.map { it.trim() }.filter { it.isNotBlank() }.takeLast(50)
            if (normalized.isEmpty()) {
                prefs.remove(EPUB_DICT_LOOKUP_WORDS_JSON)
            } else {
                prefs[EPUB_DICT_LOOKUP_WORDS_JSON] = stringListToJson(normalized)
            }
        }
    }

    suspend fun setEpubTtsEnginePackage(packageName: String?) {
        context.dataStore.edit { prefs ->
            val normalized = packageName?.trim()?.takeIf { it.isNotEmpty() }
            if (normalized == null) {
                prefs.remove(EPUB_TTS_ENGINE_PACKAGE)
            } else {
                prefs[EPUB_TTS_ENGINE_PACKAGE] = normalized
            }
        }
    }

    suspend fun setEpubTtsVoiceName(voiceName: String?) {
        context.dataStore.edit { prefs ->
            val normalized = voiceName?.trim()?.takeIf { it.isNotEmpty() }
            if (normalized == null) {
                prefs.remove(EPUB_TTS_VOICE_NAME)
            } else {
                prefs[EPUB_TTS_VOICE_NAME] = normalized
            }
        }
    }

    suspend fun setEpubTtsSpeedPercent(speedPercent: Int) {
        context.dataStore.edit { prefs ->
            prefs[EPUB_TTS_SPEED_PERCENT] = speedPercent.coerceIn(50, 300)
        }
    }

    suspend fun setEpubTtsAutoNextChapter(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[EPUB_TTS_AUTO_NEXT_CHAPTER] = enabled
        }
    }

    suspend fun setHideReaderFloatingNextButton(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HIDE_READER_FLOATING_NEXT_BUTTON] = enabled
        }
    }

    suspend fun setReaderFloatingNextButtonPositionPercent(xPercent: Int, yPercent: Int) {
        context.dataStore.edit { prefs ->
            prefs[READER_FLOATING_NEXT_BUTTON_X_PERCENT] = xPercent.coerceIn(0, 100)
            prefs[READER_FLOATING_NEXT_BUTTON_Y_PERCENT] = yPercent.coerceIn(0, 100)
        }
    }

    suspend fun recordDictQuery(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return
        context.dataStore.edit { prefs ->
            val history = buildList {
                add(normalizedQuery)
                addAll(parseStringListJson(prefs[DICT_QUERY_HISTORY_JSON]))
            }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(10)
            prefs[DICT_QUERY_HISTORY_JSON] = stringListToJson(history)
        }
    }

    suspend fun clearDictQueryHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(DICT_QUERY_HISTORY_JSON)
        }
    }

    suspend fun getEpubZoomPercentForUri(uri: String): Int? {
        val normalizedUri = uri.trim()
        if (normalizedUri.isEmpty()) return null
        val json = context.dataStore.data.first()[EPUB_ZOOM_PERCENT_BY_URI_JSON] ?: return null
        return try {
            val obj = org.json.JSONObject(json)
            val value = obj.optInt(normalizedUri, -1)
            if (value in 50..200) value else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setEpubZoomPercentForUri(uri: String, percent: Int) {
        val normalizedUri = uri.trim()
        if (normalizedUri.isEmpty()) return
        val clamped = percent.coerceIn(50, 200)
        context.dataStore.edit { prefs ->
            val obj = try {
                org.json.JSONObject(prefs[EPUB_ZOOM_PERCENT_BY_URI_JSON] ?: "{}")
            } catch (_: Exception) {
                org.json.JSONObject()
            }
            obj.put(normalizedUri, clamped)
            // 控制体积：仅保留最近写入的 200 个条目（超出时删除先迭代到的旧键）。
            while (obj.length() > 200) {
                val key = obj.keys().asSequence().firstOrNull() ?: break
                obj.remove(key)
            }
            prefs[EPUB_ZOOM_PERCENT_BY_URI_JSON] = obj.toString()
        }
    }

    suspend fun getPdfLastPageForUri(uri: String): Int? {
        val normalizedUri = uri.trim()
        if (normalizedUri.isEmpty()) return null
        val json = context.dataStore.data.first()[PDF_LAST_PAGE_BY_URI_JSON] ?: return null
        return try {
            val obj = org.json.JSONObject(json)
            val value = obj.optInt(normalizedUri, -1)
            if (value >= 0) value else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setPdfLastPageForUri(uri: String, pageIndex: Int) {
        val normalizedUri = uri.trim()
        if (normalizedUri.isEmpty()) return
        context.dataStore.edit { prefs ->
            val obj = try {
                org.json.JSONObject(prefs[PDF_LAST_PAGE_BY_URI_JSON] ?: "{}")
            } catch (_: Exception) {
                org.json.JSONObject()
            }
            obj.put(normalizedUri, pageIndex.coerceAtLeast(0))
            while (obj.length() > 200) {
                val key = obj.keys().asSequence().firstOrNull() ?: break
                obj.remove(key)
            }
            prefs[PDF_LAST_PAGE_BY_URI_JSON] = obj.toString()
        }
    }

    suspend fun getPdfZoomPercentForUri(uri: String): Int? {
        val normalizedUri = uri.trim()
        if (normalizedUri.isEmpty()) return null
        val json = context.dataStore.data.first()[PDF_ZOOM_PERCENT_BY_URI_JSON] ?: return null
        return try {
            val obj = org.json.JSONObject(json)
            val value = obj.optInt(normalizedUri, -1)
            if (value in 50..300) value else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun setPdfZoomPercentForUri(uri: String, percent: Int) {
        val normalizedUri = uri.trim()
        if (normalizedUri.isEmpty()) return
        val clamped = percent.coerceIn(50, 300)
        context.dataStore.edit { prefs ->
            val obj = try {
                org.json.JSONObject(prefs[PDF_ZOOM_PERCENT_BY_URI_JSON] ?: "{}")
            } catch (_: Exception) {
                org.json.JSONObject()
            }
            obj.put(normalizedUri, clamped)
            while (obj.length() > 200) {
                val key = obj.keys().asSequence().firstOrNull() ?: break
                obj.remove(key)
            }
            prefs[PDF_ZOOM_PERCENT_BY_URI_JSON] = obj.toString()
        }
    }

    suspend fun setQuickNoteLastCategory(category: String?) {
        context.dataStore.edit { prefs ->
            val normalized = category?.trim()?.takeIf { it.isNotEmpty() }
            if (normalized == null) {
                prefs.remove(QUICK_NOTE_LAST_CATEGORY)
            } else {
                prefs[QUICK_NOTE_LAST_CATEGORY] = normalized
            }
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

    suspend fun replaceExternalOpenPackages(map: Map<String, String>) {
        context.dataStore.edit { prefs ->
            if (map.isEmpty()) {
                prefs.remove(EXTERNAL_OPEN_BY_EXTENSION_JSON)
            } else {
                prefs[EXTERNAL_OPEN_BY_EXTENSION_JSON] = externalOpenByExtensionMapToJson(map)
            }
        }
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

    suspend fun setNetworkServiceTimeoutMinutes(minutes: Int) {
        setFtpTimeoutMinutes(minutes)
    }

    suspend fun setLocalNetworkServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LOCAL_NETWORK_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setFamilyNetworkUserName(name: String?) {
        context.dataStore.edit { prefs ->
            val trimmed = name?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                prefs.remove(FAMILY_NETWORK_USER_NAME)
            } else {
                prefs[FAMILY_NETWORK_USER_NAME] = trimmed
            }
        }
    }

    suspend fun setFamilyNetworkHostPassword(password: String?) {
        context.dataStore.edit { prefs ->
            val trimmed = password?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                prefs.remove(FAMILY_NETWORK_HOST_PASSWORD)
            } else {
                prefs[FAMILY_NETWORK_HOST_PASSWORD] = trimmed
            }
        }
    }

    suspend fun setFilterVisible(visible: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[FILTER_VISIBLE] = visible
        }
    }

    suspend fun setLastMainTab(tab: String) {
        context.dataStore.edit { prefs ->
            prefs[LAST_MAIN_TAB] = tab.ifBlank { "directory" }
        }
    }

    suspend fun addRecentOpenItem(
        type: String,
        key: String,
        title: String,
        uri: String? = null,
        playlistId: String? = null
    ) {
        if (type.isBlank() || key.isBlank() || title.isBlank()) return
        context.dataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val existing = parseRecentOpenItems(prefs[RECENT_OPEN_ITEMS_JSON]).toMutableList()
            val index = existing.indexOfFirst { it.type == type && it.key == key }
            val updated = RecentOpenItem(
                type = type,
                key = key,
                title = title,
                uri = uri,
                playlistId = playlistId,
                openedAt = now
            )
            if (index >= 0) {
                existing[index] = updated
            } else {
                existing.add(updated)
            }
            val trimmed = existing
                .sortedByDescending { it.openedAt }
                .take(30)
            prefs[RECENT_OPEN_ITEMS_JSON] = recentOpenItemsToJson(trimmed)
        }
    }

    suspend fun removeRecentOpenItem(type: String, key: String) {
        if (type.isBlank() || key.isBlank()) return
        context.dataStore.edit { prefs ->
            val filtered = parseRecentOpenItems(prefs[RECENT_OPEN_ITEMS_JSON])
                .filterNot { it.type == type && it.key == key }
            if (filtered.isEmpty()) {
                prefs.remove(RECENT_OPEN_ITEMS_JSON)
            } else {
                prefs[RECENT_OPEN_ITEMS_JSON] = recentOpenItemsToJson(filtered)
            }
        }
    }

    suspend fun clearRecentOpenItems() {
        context.dataStore.edit { prefs ->
            prefs.remove(RECENT_OPEN_ITEMS_JSON)
        }
    }

    suspend fun replaceRecentOpenItems(items: List<RecentOpenItem>) {
        context.dataStore.edit { prefs ->
            val normalized = items
                .filter { it.type.isNotBlank() && it.key.isNotBlank() && it.title.isNotBlank() }
                .sortedByDescending { it.openedAt }
                .take(30)
            if (normalized.isEmpty()) {
                prefs.remove(RECENT_OPEN_ITEMS_JSON)
            } else {
                prefs[RECENT_OPEN_ITEMS_JSON] = recentOpenItemsToJson(normalized)
            }
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

    suspend fun addManagedGitProject(project: ManagedGitProject) {
        context.dataStore.edit { prefs ->
            val existing = parseManagedGitProjects(prefs[GIT_MANAGED_PROJECTS_JSON])
            val updated = (existing.filterNot { it.id == project.id || it.localDirName == project.localDirName || it.repoUrl == project.repoUrl } + project)
                .sortedBy { it.createdAt }
            prefs[GIT_MANAGED_PROJECTS_JSON] = managedGitProjectsToJson(updated)
        }
    }

    suspend fun updateManagedGitProject(project: ManagedGitProject) {
        context.dataStore.edit { prefs ->
            val existing = parseManagedGitProjects(prefs[GIT_MANAGED_PROJECTS_JSON])
            val updated = existing.map { if (it.id == project.id) project else it }
            prefs[GIT_MANAGED_PROJECTS_JSON] = managedGitProjectsToJson(updated)
        }
    }

    suspend fun removeManagedGitProject(id: String) {
        context.dataStore.edit { prefs ->
            val updated = parseManagedGitProjects(prefs[GIT_MANAGED_PROJECTS_JSON]).filterNot { it.id == id }
            if (updated.isEmpty()) prefs.remove(GIT_MANAGED_PROJECTS_JSON)
            else prefs[GIT_MANAGED_PROJECTS_JSON] = managedGitProjectsToJson(updated)
        }
    }

    suspend fun markManagedGitProjectSynced(id: String, syncedAt: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            val updated = parseManagedGitProjects(prefs[GIT_MANAGED_PROJECTS_JSON]).map {
                if (it.id == id) it.copy(lastSyncAt = syncedAt) else it
            }
            prefs[GIT_MANAGED_PROJECTS_JSON] = managedGitProjectsToJson(updated)
        }
    }

    suspend fun markManagedGitProjectPushed(id: String, pushedAt: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            val updated = parseManagedGitProjects(prefs[GIT_MANAGED_PROJECTS_JSON]).map {
                if (it.id == id) it.copy(lastPushAt = pushedAt, lastSyncAt = maxOf(it.lastSyncAt, pushedAt)) else it
            }
            prefs[GIT_MANAGED_PROJECTS_JSON] = managedGitProjectsToJson(updated)
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

    suspend fun setPlayerAudioSettings(settings: PlayerAudioSettings) {
        context.dataStore.edit { prefs ->
            prefs[PLAYER_AUDIO_ENGINE] = normalizePlayerAudioEngine(settings.engine)
            prefs[PLAYER_KEEP_AWAKE] = settings.keepAwake
            prefs[PLAYER_AUDIO_EFFECTS_ENABLED] = settings.audioEffectsEnabled
            prefs[PLAYER_AUDIO_EFFECT_PRESET] = normalizePlayerAudioPreset(settings.effectPreset)
            prefs[PLAYER_HIGH_QUALITY_OUTPUT] = settings.highQualityOutput
        }
    }

    suspend fun updatePlayerAudioSettings(transform: (PlayerAudioSettings) -> PlayerAudioSettings) {
        context.dataStore.edit { prefs ->
            val current = PlayerAudioSettings(
                engine = normalizePlayerAudioEngine(prefs[PLAYER_AUDIO_ENGINE]),
                keepAwake = prefs[PLAYER_KEEP_AWAKE] ?: false,
                audioEffectsEnabled = prefs[PLAYER_AUDIO_EFFECTS_ENABLED] ?: false,
                effectPreset = normalizePlayerAudioPreset(prefs[PLAYER_AUDIO_EFFECT_PRESET]),
                highQualityOutput = prefs[PLAYER_HIGH_QUALITY_OUTPUT] ?: false
            )
            val updated = transform(current)
            prefs[PLAYER_AUDIO_ENGINE] = normalizePlayerAudioEngine(updated.engine)
            prefs[PLAYER_KEEP_AWAKE] = updated.keepAwake
            prefs[PLAYER_AUDIO_EFFECTS_ENABLED] = updated.audioEffectsEnabled
            prefs[PLAYER_AUDIO_EFFECT_PRESET] = normalizePlayerAudioPreset(updated.effectPreset)
            prefs[PLAYER_HIGH_QUALITY_OUTPUT] = updated.highQualityOutput
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

    suspend fun appendPlaylists(playlists: List<Playlist>): Map<String, String> {
        if (playlists.isEmpty()) return emptyMap()
        var idMapping = emptyMap<String, String>()
        context.dataStore.edit { prefs ->
            val existing = Playlist.listFromJson(prefs[PLAYER_PLAYLISTS_JSON] ?: "")
            val usedIds = existing.map { it.id }.toMutableSet()
            val mapping = mutableMapOf<String, String>()
            val normalized = playlists.map { playlist ->
                if (playlist.id !in usedIds) {
                    usedIds += playlist.id
                    mapping[playlist.id] = playlist.id
                    playlist
                } else {
                    var newId = UUID.randomUUID().toString()
                    while (newId in usedIds) newId = UUID.randomUUID().toString()
                    usedIds += newId
                    mapping[playlist.id] = newId
                    playlist.copy(id = newId)
                }
            }
            prefs[PLAYER_PLAYLISTS_JSON] = Playlist.listToJson(existing + normalized)
            idMapping = mapping.toMap()
        }
        return idMapping
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

    suspend fun addPlayerListBookmark(
        playlistId: String?,
        dirUri: String?,
        trackIndex: Int,
        positionMs: Long,
        trackName: String,
        note: String
    ) {
        context.dataStore.edit { prefs ->
            val existing = parsePlayerListBookmarks(prefs[PLAYER_LIST_BOOKMARKS_JSON])
            val now = System.currentTimeMillis()
            val added = PlayerListBookmark(
                id = UUID.randomUUID().toString(),
                playlistId = playlistId,
                dirUri = dirUri,
                trackIndex = trackIndex.coerceAtLeast(0),
                positionMs = positionMs.coerceAtLeast(0L),
                trackName = trackName,
                note = note.trim(),
                savedAt = now
            )
            prefs[PLAYER_LIST_BOOKMARKS_JSON] = playerListBookmarksToJson(existing + added)
        }
    }

    suspend fun updatePlayerListBookmarkNote(bookmarkId: String, note: String) {
        context.dataStore.edit { prefs ->
            val existing = parsePlayerListBookmarks(prefs[PLAYER_LIST_BOOKMARKS_JSON])
            val updated = existing.map {
                if (it.id == bookmarkId) it.copy(note = note.trim()) else it
            }
            prefs[PLAYER_LIST_BOOKMARKS_JSON] = playerListBookmarksToJson(updated)
        }
    }

    suspend fun deletePlayerListBookmark(bookmarkId: String) {
        context.dataStore.edit { prefs ->
            val existing = parsePlayerListBookmarks(prefs[PLAYER_LIST_BOOKMARKS_JSON])
            val updated = existing.filterNot { it.id == bookmarkId }
            if (updated.isEmpty()) prefs.remove(PLAYER_LIST_BOOKMARKS_JSON)
            else prefs[PLAYER_LIST_BOOKMARKS_JSON] = playerListBookmarksToJson(updated)
        }
    }

    suspend fun clearPlayerListBookmarks() {
        context.dataStore.edit { prefs ->
            prefs.remove(PLAYER_LIST_BOOKMARKS_JSON)
        }
    }

    suspend fun replacePlayerListBookmarks(bookmarks: List<PlayerListBookmark>) {
        context.dataStore.edit { prefs ->
            if (bookmarks.isEmpty()) prefs.remove(PLAYER_LIST_BOOKMARKS_JSON)
            else prefs[PLAYER_LIST_BOOKMARKS_JSON] = playerListBookmarksToJson(bookmarks)
        }
    }

    suspend fun appendPlayerListBookmarks(bookmarks: List<PlayerListBookmark>) {
        if (bookmarks.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = parsePlayerListBookmarks(prefs[PLAYER_LIST_BOOKMARKS_JSON])
            val usedIds = existing.map { it.id }.toMutableSet()
            val normalized = bookmarks.map { bookmark ->
                if (bookmark.id !in usedIds) {
                    usedIds += bookmark.id
                    bookmark
                } else {
                    var newId = UUID.randomUUID().toString()
                    while (newId in usedIds) newId = UUID.randomUUID().toString()
                    usedIds += newId
                    bookmark.copy(id = newId)
                }
            }
            prefs[PLAYER_LIST_BOOKMARKS_JSON] = playerListBookmarksToJson(existing + normalized)
        }
    }

    suspend fun replacePlayerResumeStates(states: Map<String, PlayerResumeState>) {
        context.dataStore.edit { prefs ->
            if (states.isEmpty()) prefs.remove(PLAYER_PLAYLIST_RESUME_JSON)
            else prefs[PLAYER_PLAYLIST_RESUME_JSON] = playerResumeStatesToJson(states)
        }
    }

    suspend fun mergePlayerResumeStates(states: Map<String, PlayerResumeState>) {
        if (states.isEmpty()) return
        context.dataStore.edit { prefs ->
            val merged = parsePlayerResumeStates(prefs[PLAYER_PLAYLIST_RESUME_JSON]).toMutableMap()
            merged.putAll(states)
            prefs[PLAYER_PLAYLIST_RESUME_JSON] = playerResumeStatesToJson(merged)
        }
    }

    private fun parsePlayerListBookmarks(json: String?): List<PlayerListBookmark> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                    add(
                        PlayerListBookmark(
                            id = id,
                            playlistId = obj.optString("playlistId").ifBlank { null },
                            dirUri = obj.optString("dirUri").ifBlank { null },
                            trackIndex = obj.optInt("trackIndex", 0).coerceAtLeast(0),
                            positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
                            trackName = obj.optString("trackName", ""),
                            note = obj.optString("note", ""),
                            savedAt = obj.optLong("savedAt", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseLegacyPlayerListBookmark(json: String): PlayerListBookmark? {
        return try {
            val obj = org.json.JSONObject(json)
            PlayerListBookmark(
                id = UUID.randomUUID().toString(),
                playlistId = obj.optString("playlistId").ifBlank { null },
                dirUri = obj.optString("dirUri").ifBlank { null },
                trackIndex = obj.optInt("trackIndex", 0).coerceAtLeast(0),
                positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
                trackName = obj.optString("trackName", ""),
                note = "",
                savedAt = obj.optLong("savedAt", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePlayerResumeStates(json: String?): Map<String, PlayerResumeState> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val playlistId = keys.next()
                    val value = obj.optJSONObject(playlistId) ?: continue
                    put(
                        playlistId,
                        PlayerResumeState(
                            trackIndex = value.optInt("i", 0).coerceAtLeast(0),
                            positionMs = value.optLong("p", 0L).coerceAtLeast(0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun normalizePlayerAudioPreset(value: String?): String = when (value) {
        PLAYER_AUDIO_PRESET_VOCAL,
        PLAYER_AUDIO_PRESET_BASS,
        PLAYER_AUDIO_PRESET_CAR,
        PLAYER_AUDIO_PRESET_HEADPHONE -> value
        else -> PLAYER_AUDIO_PRESET_FLAT
    }

    private fun normalizePlayerAudioEngine(value: String?): String = when (value) {
        PLAYER_AUDIO_ENGINE_EXO_PLAYER -> PLAYER_AUDIO_ENGINE_EXO_PLAYER
        else -> PLAYER_AUDIO_ENGINE_MEDIA_PLAYER
    }

    private fun playerResumeStatesToJson(states: Map<String, PlayerResumeState>): String {
        val obj = org.json.JSONObject()
        states.forEach { (playlistId, state) ->
            obj.put(
                playlistId,
                org.json.JSONObject().apply {
                    put("i", state.trackIndex.coerceAtLeast(0))
                    put("p", state.positionMs.coerceAtLeast(0L))
                }
            )
        }
        return obj.toString()
    }

    private fun parseExternalOpenByExtensionMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
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

    private fun externalOpenByExtensionMapToJson(map: Map<String, String>): String {
        val obj = org.json.JSONObject()
        map.forEach { (extension, packageName) ->
            val normalizedExtension = extension.trim().lowercase().removePrefix(".")
            val normalizedPackage = packageName.trim()
            if (normalizedExtension.isNotBlank() && normalizedPackage.isNotBlank()) {
                obj.put(normalizedExtension, normalizedPackage)
            }
        }
        return obj.toString()
    }

    private fun playerListBookmarksToJson(list: List<PlayerListBookmark>): String {
        val arr = org.json.JSONArray()
        list.forEach { bm ->
            arr.put(
                org.json.JSONObject().apply {
                    put("id", bm.id)
                    put("playlistId", bm.playlistId ?: "")
                    put("dirUri", bm.dirUri ?: "")
                    put("trackIndex", bm.trackIndex.coerceAtLeast(0))
                    put("positionMs", bm.positionMs.coerceAtLeast(0L))
                    put("trackName", bm.trackName)
                    put("note", bm.note)
                    put("savedAt", bm.savedAt)
                }
            )
        }
        return arr.toString()
    }

    private fun parseRecentOpenItems(json: String?): List<RecentOpenItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val type = obj.optString("type").trim()
                    val key = obj.optString("key").trim()
                    val title = obj.optString("title").trim()
                    if (type.isBlank() || key.isBlank() || title.isBlank()) continue
                    add(
                        RecentOpenItem(
                            type = type,
                            key = key,
                            title = title,
                            uri = obj.optString("uri").ifBlank { null },
                            playlistId = obj.optString("playlistId").ifBlank { null },
                            openedAt = obj.optLong("openedAt", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.openedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun recentOpenItemsToJson(list: List<RecentOpenItem>): String {
        val arr = org.json.JSONArray()
        list.forEach { item ->
            arr.put(
                org.json.JSONObject().apply {
                    put("type", item.type)
                    put("key", item.key)
                    put("title", item.title)
                    put("uri", item.uri ?: "")
                    put("playlistId", item.playlistId ?: "")
                    put("openedAt", item.openedAt)
                }
            )
        }
        return arr.toString()
    }

    private fun parseManagedGitProjects(json: String?): List<ManagedGitProject> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim().ifBlank { UUID.randomUUID().toString() }
                    val projectName = obj.optString("projectName").trim()
                    val repoUrl = obj.optString("repoUrl").trim()
                    val localDirName = obj.optString("localDirName").trim()
                    if (projectName.isBlank() || repoUrl.isBlank() || localDirName.isBlank()) continue
                    add(
                        ManagedGitProject(
                            id = id,
                            projectName = projectName,
                            repoUrl = repoUrl,
                            localDirName = localDirName,
                            branchName = obj.optString("branchName").trim().ifBlank { null },
                            createdAt = obj.optLong("createdAt", 0L),
                            lastSyncAt = obj.optLong("lastSyncAt", 0L),
                            lastPushAt = obj.optLong("lastPushAt", 0L)
                        )
                    )
                }
            }.sortedBy { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun managedGitProjectsToJson(list: List<ManagedGitProject>): String {
        val arr = org.json.JSONArray()
        list.forEach { item ->
            arr.put(
                org.json.JSONObject().apply {
                    put("id", item.id)
                    put("projectName", item.projectName)
                    put("repoUrl", item.repoUrl)
                    put("localDirName", item.localDirName)
                    item.branchName?.takeIf { it.isNotBlank() }?.let { put("branchName", it) }
                    put("createdAt", item.createdAt)
                    put("lastSyncAt", item.lastSyncAt)
                    put("lastPushAt", item.lastPushAt)
                }
            )
        }
        return arr.toString()
    }

    private fun parseStringListJson(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun stringListToJson(list: List<String>): String {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
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
