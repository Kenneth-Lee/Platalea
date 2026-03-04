package com.kenny.localmanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(name = "settings")

private val ROOT_URI = stringPreferencesKey("root_uri")
private val DEBUG_ENABLED = booleanPreferencesKey("debug_enabled")
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

class Preferences(private val context: Context) {
    val rootUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ROOT_URI]
    }

    val debugEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DEBUG_ENABLED] ?: false
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

    suspend fun setRootUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(ROOT_URI)
            else prefs[ROOT_URI] = uri
        }
    }

    suspend fun setDebugEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DEBUG_ENABLED] = enabled
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
}
