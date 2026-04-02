package com.remotecontrol

import android.app.Application
import com.remotecontrol.data.api.ApiClient
import com.remotecontrol.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var settings: SettingsStore
        private set

    private val _apiClient = MutableStateFlow<ApiClient?>(null)
    val apiClientFlow: StateFlow<ApiClient?> = _apiClient.asStateFlow()

    val apiClient: ApiClient? get() = _apiClient.value

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        CoroutineScope(Dispatchers.IO).launch {
            settings.serverUrl.combine(settings.token) { url, tok -> Pair(url, tok) }
                .collect { (url, tok) -> updateApiClient(url, tok) }
        }
    }

    fun updateApiClient(serverUrl: String, token: String) {
        val url = serverUrl.trim()
        val tok = token.trim()
        if (url.isNotBlank() && tok.isNotBlank()) {
            try {
                _apiClient.value = ApiClient(baseUrl = url, token = tok)
            } catch (_: Exception) {
                _apiClient.value = null
            }
        } else {
            _apiClient.value = null
        }
    }
}
