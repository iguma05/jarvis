package com.jarvis.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "jarvis_settings")

class SettingsStore(private val context: Context) {
    private val baseUrlKey = stringPreferencesKey("base_url")

    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[baseUrlKey] ?: DEFAULT_BASE_URL
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = url.trim().trimEnd('/')
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://192.168.1.100:5050"
    }
}
