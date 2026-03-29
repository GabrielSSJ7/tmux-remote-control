package com.remotecontrol.ui.terminal

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.remotecontrol.App

@Composable
fun TerminalScreen(app: App, sessionId: String, onBack: () -> Unit) {
    Text("Terminal: $sessionId")
}
