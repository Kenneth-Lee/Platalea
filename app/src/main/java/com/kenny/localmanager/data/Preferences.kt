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
}
