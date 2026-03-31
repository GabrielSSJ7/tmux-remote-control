package com.remotecontrol.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val fontSize by app.settings.fontSize.collectAsState(initial = 8)

    if (serverUrl.isBlank() || token.isBlank()) return

    val vm = remember(sessionId, serverUrl, token) { TerminalViewModel(serverUrl = serverUrl, token = token, sessionId = sessionId, okHttpClient = apiClient.okHttpClient()) }
    val connectionState by vm.connectionState.collectAsState()
    val version by vm.version.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var showCommands by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(vm) { vm.connect(); focusRequester.requestFocus(); keyboard?.show() }

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
                actions = { IconButton(onClick = { showCommands = true }) { Icon(Icons.Default.PlayArrow, contentDescription = "Commands") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1E1E1E))) {
                TerminalRenderer(emulator = vm.emulator, version = version, fontSize = fontSize.toFloat(), modifier = Modifier.fillMaxSize(), onSizeChanged = { cols, rows -> vm.resize(cols, rows) }, onTap = { focusRequester.requestFocus(); keyboard?.show() })
                if (connectionState == ConnectionState.DISCONNECTED) {
                    Surface(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp), color = MaterialTheme.colorScheme.error, shape = MaterialTheme.shapes.small) {
                        Text("Disconnected - reconnecting...", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
            ExtraKeysBar(onKey = { vm.sendKey(it) }, onCtrl = { vm.sendCtrl(it) })
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4,
                        decorationBox = { inner ->
                            if (inputText.isEmpty()) Text("Type command...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            inner()
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotEmpty()) {
                                vm.sendInput(inputText)
                                vm.sendKey("Enter")
                                inputText = ""
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
    if (showCommands) { CommandsSheet(app = app, onDismiss = { showCommands = false }, onCommandSelected = { command -> vm.sendInput(command.command + "\n"); showCommands = false }) }
}
