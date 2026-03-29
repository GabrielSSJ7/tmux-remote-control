package com.remotecontrol.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.remotecontrol.App
import com.remotecontrol.data.websocket.ConnectionState
import com.remotecontrol.terminal.TerminalRenderer
import com.remotecontrol.ui.commands.CommandsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(app: App, sessionId: String, onBack: () -> Unit) {
    val apiClient = app.apiClient ?: return
    val serverUrl by app.settings.serverUrl.collectAsState(initial = "")
    val token by app.settings.token.collectAsState(initial = "")
    val fontSize by app.settings.fontSize.collectAsState(initial = 14)
    val vm = remember(sessionId) { TerminalViewModel(serverUrl = serverUrl, token = token, sessionId = sessionId, okHttpClient = apiClient.okHttpClient()) }
    val connectionState by vm.connectionState.collectAsState()
    val version by vm.version.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var showCommands by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.connect(); focusRequester.requestFocus(); keyboard?.show() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sessionId); Spacer(Modifier.width(8.dp))
                        val color = when (connectionState) { ConnectionState.CONNECTED -> Color.Green; ConnectionState.CONNECTING -> Color.Yellow; ConnectionState.DISCONNECTED -> Color.Red }
                        Box(Modifier.size(8.dp).background(color, shape = CircleShape))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showCommands = true }, modifier = Modifier.padding(bottom = 48.dp)) { Icon(Icons.Default.PlayArrow, contentDescription = "Commands") } },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .focusRequester(focusRequester).focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val char = event.utf16CodePoint.toChar()
                        if (char.code in 32..126) { vm.sendInput(char.toString()); true }
                        else when (event.key) {
                            Key.Enter -> { vm.sendKey("Enter"); true }; Key.Backspace -> { vm.sendInput("\u007f"); true }
                            Key.Tab -> { vm.sendKey("Tab"); true }; Key.DirectionUp -> { vm.sendKey("Up"); true }
                            Key.DirectionDown -> { vm.sendKey("Down"); true }; Key.DirectionLeft -> { vm.sendKey("Left"); true }
                            Key.DirectionRight -> { vm.sendKey("Right"); true }; Key.Escape -> { vm.sendKey("Esc"); true }
                            else -> false
                        }
                    } else false
                },
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1E1E1E))) {
                TerminalRenderer(emulator = vm.emulator, version = version, fontSize = fontSize.toFloat(), modifier = Modifier.fillMaxSize(), onSizeChanged = { cols, rows -> vm.resize(cols, rows) })
                if (connectionState == ConnectionState.DISCONNECTED) {
                    Surface(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp), color = MaterialTheme.colorScheme.error, shape = MaterialTheme.shapes.small) {
                        Text("Disconnected - reconnecting...", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
            ExtraKeysBar(onKey = { vm.sendKey(it) }, onCtrl = { vm.sendCtrl(it) })
        }
    }
    if (showCommands) { CommandsSheet(app = app, onDismiss = { showCommands = false }, onCommandSelected = { command -> vm.sendInput(command.command + "\n"); showCommands = false }) }
}
