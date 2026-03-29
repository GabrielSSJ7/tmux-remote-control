package com.remotecontrol.ui.sessions

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.remotecontrol.App

@Composable
fun SessionsScreen(app: App, onSessionClick: (String) -> Unit, onSettingsClick: () -> Unit) {
    Text("Sessions")
}
