package com.remotecontrol.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.dataStore

    val serverUrl: Flow<String> = store.data.map { it[KEY_SERVER_URL] ?: "" }
    val token: Flow<String> = store.data.map { it[KEY_TOKEN] ?: "" }
    val fontSize: Flow<Int> = store.data.map { it[KEY_FONT_SIZE] ?: 14 }
    val darkMode: Flow<Boolean> = store.data.map { it[KEY_DARK_MODE] ?: true }
    val scrollbackLines: Flow<Int> = store.data.map { it[KEY_SCROLLBACK] ?: 10000 }

    suspend fun setServerUrl(url: String) { store.edit { it[KEY_SERVER_URL] = url } }
    suspend fun setToken(token: String) { store.edit { it[KEY_TOKEN] = token } }
    suspend fun setFontSize(size: Int) { store.edit { it[KEY_FONT_SIZE] = size } }
    suspend fun setDarkMode(dark: Boolean) { store.edit { it[KEY_DARK_MODE] = dark } }
    suspend fun setScrollbackLines(lines: Int) { store.edit { it[KEY_SCROLLBACK] = lines } }

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_FONT_SIZE = intPreferencesKey("font_size")
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        private val KEY_SCROLLBACK = intPreferencesKey("scrollback_lines")
    }
}
