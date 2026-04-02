package com.remotecontrol.ui.commands

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.remotecontrol.App
import com.remotecontrol.data.model.Command

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CommandsScreen(app: App, onBack: () -> Unit) {
    val client by app.apiClientFlow.collectAsState()
    val api = client?.commands ?: return
    val vm = remember(api) { CommandsViewModel(api) }
    val commands by vm.filteredCommands.collectAsState()
    val error by vm.error.collectAsState()

    var showGuide by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCommand by remember { mutableStateOf<Command?>(null) }
    var menuCommand by remember { mutableStateOf<Command?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Command?>(null) }

    LaunchedEffect(vm) { vm.loadCommands() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Commands") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = { showGuide = true }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "New command") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (commands.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("No commands yet", style = MaterialTheme.typography.titleMedium)
                    Text("Save commands you use often for quick access.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showGuide = true }) { Text("Learn how it works") }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(commands, key = { it.id }) { cmd ->
                        Box {
                            ListItem(
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { menuCommand = cmd },
                                ),
                                headlineContent = { Text(cmd.name) },
                                supportingContent = { Text(cmd.command, maxLines = 1, color = MaterialTheme.colorScheme.outline) },
                            )
                            DropdownMenu(
                                expanded = menuCommand?.id == cmd.id,
                                onDismissRequest = { menuCommand = null },
                                offset = DpOffset(16.dp, 0.dp),
                            ) {
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { editingCommand = menuCommand; menuCommand = null })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { showDeleteConfirm = menuCommand; menuCommand = null })
                            }
                        }
                    }
                }
            }

            error?.let {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { vm.clearError() }) { Text("Dismiss") } },
                ) { Text(it) }
            }
        }
    }

    if (showGuide) { CommandGuideDialog(onDismiss = { showGuide = false }) }

    if (showAddDialog) {
        CommandFormDialog(
            title = "New Command",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, command -> vm.createCommand(name, command); showAddDialog = false },
        )
    }

    editingCommand?.let { cmd ->
        CommandFormDialog(
            title = "Edit Command",
            initialName = cmd.name,
            initialCommand = cmd.command,
            onDismiss = { editingCommand = null },
            onConfirm = { name, command -> vm.updateCommand(cmd.id, name, command); editingCommand = null },
        )
    }

    showDeleteConfirm?.let { cmd ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete command?") },
            text = { Text("\"${cmd.name}\" will be permanently removed.") },
            confirmButton = { TextButton(onClick = { vm.deleteCommand(cmd.id); showDeleteConfirm = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun CommandFormDialog(
    title: String,
    initialName: String = "",
    initialCommand: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, command: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var command by remember { mutableStateOf(initialCommand) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text("Command") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, command) }, enabled = name.isNotBlank() && command.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
