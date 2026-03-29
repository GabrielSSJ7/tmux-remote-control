package com.remotecontrol

import android.app.Application
import com.remotecontrol.data.api.ApiClient
import com.remotecontrol.data.settings.SettingsStore

class App : Application() {
    lateinit var settings: SettingsStore
        private set
    var apiClient: ApiClient? = null
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
    }

    fun updateApiClient(serverUrl: String, token: String) {
        if (serverUrl.isNotBlank() && token.isNotBlank()) {
            apiClient = ApiClient(baseUrl = serverUrl, token = token)
        }
    }
}
