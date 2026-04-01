package com.remotecontrol.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.remotecontrol.App

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: App, onBack: () -> Unit) {
    val vm = remember { SettingsViewModel(app) }
    val storedUrl by vm.serverUrl.collectAsState()
    val storedToken by vm.token.collectAsState()
    val fontSize by vm.fontSize.collectAsState()
    val darkMode by vm.darkMode.collectAsState()
    val scrollback by vm.scrollbackLines.collectAsState()

    var urlField by remember { mutableStateOf("") }
    var tokenField by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    var urlInitialized by remember { mutableStateOf(false) }
    var tokenInitialized by remember { mutableStateOf(false) }

    if (!urlInitialized && storedUrl.isNotEmpty()) { urlField = storedUrl; urlInitialized = true }
    if (!tokenInitialized && storedToken.isNotEmpty()) { tokenField = storedToken; tokenInitialized = true }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = urlField, onValueChange = { urlField = it; vm.setServerUrl(it) }, label = { Text("Server URL") }, placeholder = { Text("http://192.168.1.100:48322/") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                value = tokenField,
                onValueChange = { tokenField = it; vm.setToken(it) },
                label = { Text("Auth Token") },
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            if (tokenVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (tokenVisible) "Hide token" else "Show token"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            HorizontalDivider()
            Text("Terminal", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Font Size: $fontSize", modifier = Modifier.weight(1f))
                Slider(value = fontSize.toFloat(), onValueChange = { vm.setFontSize(it.toInt()) }, valueRange = 8f..32f, steps = 23, modifier = Modifier.weight(2f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark Mode", modifier = Modifier.weight(1f))
                Switch(checked = darkMode, onCheckedChange = { vm.setDarkMode(it) })
            }
            Text("Scrollback: $scrollback lines")
            Slider(value = scrollback.toFloat(), onValueChange = { vm.setScrollbackLines(it.toInt()) }, valueRange = 1000f..50000f, steps = 48)
        }
    }
}
