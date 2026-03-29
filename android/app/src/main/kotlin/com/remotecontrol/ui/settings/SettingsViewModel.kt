package com.remotecontrol.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotecontrol.App
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: App) : ViewModel() {
    val serverUrl = app.settings.serverUrl.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val token = app.settings.token.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val fontSize = app.settings.fontSize.stateIn(viewModelScope, SharingStarted.Lazily, 14)
    val darkMode = app.settings.darkMode.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val scrollbackLines = app.settings.scrollbackLines.stateIn(viewModelScope, SharingStarted.Lazily, 10000)

    fun setServerUrl(url: String) { viewModelScope.launch { app.settings.setServerUrl(url); app.updateApiClient(url, token.value) } }
    fun setToken(token: String) { viewModelScope.launch { app.settings.setToken(token); app.updateApiClient(serverUrl.value, token) } }
    fun setFontSize(size: Int) { viewModelScope.launch { app.settings.setFontSize(size) } }
    fun setDarkMode(dark: Boolean) { viewModelScope.launch { app.settings.setDarkMode(dark) } }
    fun setScrollbackLines(lines: Int) { viewModelScope.launch { app.settings.setScrollbackLines(lines) } }
}
