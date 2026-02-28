package com.kenny.localmanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(name = "settings")

private val ROOT_URI = stringPreferencesKey("root_uri")

class Preferences(private val context: Context) {
    val rootUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ROOT_URI]
    }

    suspend fun setRootUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(ROOT_URI)
            else prefs[ROOT_URI] = uri
        }
    }
}
