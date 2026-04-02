package com.remotecontrol.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotecontrol.App
import com.remotecontrol.data.model.Session

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(app: App, onSessionClick: (String) -> Unit, onCommandsClick: () -> Unit, onSettingsClick: () -> Unit) {
    val client by app.apiClientFlow.collectAsState()
    val api = client?.sessions
    val vm = remember(api) { api?.let { SessionsViewModel(it) } }
    val sessions by vm?.sessions?.collectAsState() ?: remember { mutableStateOf(emptyList<Session>()) }
    val isLoading by vm?.isLoading?.collectAsState() ?: remember { mutableStateOf(false) }
    val error by vm?.error?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm) { vm?.loadSessions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                actions = {
                    IconButton(onClick = onCommandsClick) { Icon(Icons.Default.PlayArrow, contentDescription = "Commands") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            if (vm != null) {
                FloatingActionButton(onClick = { vm.createSession() }) { Icon(Icons.Default.Add, contentDescription = "New session") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (api == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Server not configured")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onSettingsClick) { Text("Open Settings") }
                }
            } else if (isLoading && sessions.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (sessions.isEmpty()) {
                Text("No active sessions", Modifier.align(Alignment.Center))
            } else {
                LazyColumn {
                    items(sessions, key = { it.name }) { session ->
                        SessionItem(session = session, onClick = { onSessionClick(session.name) }, onDelete = { vm!!.deleteSession(session.name) })
                    }
                }
            }
            error?.let {
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), action = { TextButton(onClick = { vm?.loadSessions() }) { Text("Retry") } }) { Text(it) }
            }
        }
    }
}

@Composable
private fun SessionItem(session: Session, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(session.name) },
        supportingContent = { Text(if (session.attached) "attached" else "detached") },
        leadingContent = {
            val color = if (session.attached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Box(Modifier.size(12.dp).background(color, shape = CircleShape))
        },
        trailingContent = { IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") } },
    )
}
