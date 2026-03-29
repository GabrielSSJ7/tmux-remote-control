package com.remotecontrol.ui.commands

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotecontrol.App
import com.remotecontrol.data.model.Command
import com.remotecontrol.data.model.CreateCommand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsSheet(app: App, onDismiss: () -> Unit, onCommandSelected: (Command) -> Unit) {
    val api = app.apiClient?.commands ?: return
    val vm = remember { CommandsViewModel(api) }
    val grouped by vm.groupedCommands.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadCommands() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Commands", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = "Add command") }
            }
            var search by remember { mutableStateOf("") }
            OutlinedTextField(value = search, onValueChange = { search = it; vm.setSearch(it) }, placeholder = { Text("Search commands...") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true)
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                grouped.forEach { (category, commands) ->
                    item { Text(category.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                    items(commands) { cmd -> ListItem(modifier = Modifier.clickable { onCommandSelected(cmd) }, headlineContent = { Text(cmd.name) }, supportingContent = { Text(cmd.command, maxLines = 1, color = MaterialTheme.colorScheme.outline) }) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showAdd) {
        AddCommandDialog(onDismiss = { showAdd = false }, onConfirm = { name, command, category -> vm.createCommand(CreateCommand(name, command, null, category)); showAdd = false })
    }
}

@Composable
private fun AddCommandDialog(onDismiss: () -> Unit, onConfirm: (name: String, command: String, category: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Command") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text("Command") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, command, category) }, enabled = name.isNotBlank() && command.isNotBlank() && category.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
