package com.remotecontrol

import android.app.Application
import com.remotecontrol.data.api.ApiClient
import com.remotecontrol.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var settings: SettingsStore
        private set
    var apiClient: ApiClient? = null
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        CoroutineScope(Dispatchers.IO).launch {
            val url = settings.serverUrl.first()
            val tok = settings.token.first()
            updateApiClient(url, tok)
        }
    }

    fun updateApiClient(serverUrl: String, token: String) {
        if (serverUrl.isNotBlank() && token.isNotBlank()) {
            apiClient = ApiClient(baseUrl = serverUrl, token = token)
        }
    }
}
