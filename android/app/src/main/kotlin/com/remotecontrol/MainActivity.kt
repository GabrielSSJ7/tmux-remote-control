package com.remotecontrol

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.remotecontrol.navigation.NavGraph
import com.remotecontrol.ui.theme.RemoteControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        val app = application as App
        setContent {
            val darkMode by app.settings.darkMode.collectAsState(initial = true)
            RemoteControlTheme(darkTheme = darkMode) {
                NavGraph(app = app)
            }
        }
    }
}
