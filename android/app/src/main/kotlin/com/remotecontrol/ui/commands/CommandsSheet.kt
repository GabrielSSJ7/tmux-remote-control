package com.remotecontrol.ui.commands

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotecontrol.App
import com.remotecontrol.data.model.Command
import com.remotecontrol.ui.commands.CommandUtils.resolveCommand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsSheet(app: App, onDismiss: () -> Unit, onCommandSelected: (Command) -> Unit) {
    val client by app.apiClientFlow.collectAsState()
    val api = client?.commands ?: return
    val vm = remember(api) { CommandsViewModel(api) }
    val commands by vm.filteredCommands.collectAsState()
    LaunchedEffect(Unit) { vm.loadCommands() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Commands", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            var search by remember { mutableStateOf("") }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; vm.setSearch(it) },
                placeholder = { Text("Search...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            if (commands.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("No commands saved yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(commands, key = { it.id }) { cmd ->
                        ListItem(
                            modifier = Modifier.clickable { onCommandSelected(cmd.copy(command = resolveCommand(cmd.command))) },
                            headlineContent = { Text(cmd.name) },
                            supportingContent = { Text(cmd.command, maxLines = 1, color = MaterialTheme.colorScheme.outline) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
