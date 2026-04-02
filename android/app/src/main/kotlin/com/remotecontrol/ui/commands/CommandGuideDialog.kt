package com.remotecontrol.ui.commands

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun CommandGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How Commands Work") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GuideItem("Create", "Tap the + button to save a command with a name and the shell command.")
                GuideItem("Use", "In the terminal, tap the play button to open your commands. Tap one to send it immediately.")
                GuideItem("Edit / Delete", "Long-press any command to edit or delete it.")
                HorizontalDivider()
                Text("Shell Examples", style = MaterialTheme.typography.titleSmall)
                CommandExample("Disk Usage", "df -h")
                CommandExample("Running Processes", "ps aux | head -20")
                CommandExample("Git Log", "git log --oneline -10")
                CommandExample("Restart Service", "sudo systemctl restart nginx")
                CommandExample("Tail Logs", "tail -f /var/log/syslog")
                HorizontalDivider()
                Text("Tmux / Key Sequences", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Use <C-x> for Ctrl+X or \\xHH for hex codes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CommandExample("Tmux Window 1", "<C-s>1")
                CommandExample("Tmux Window 2", "<C-s>2")
                CommandExample("Tmux Next Window", "<C-s>n")
                CommandExample("Tmux Prev Window", "<C-s>p")
                CommandExample("Tmux Split Horizontal", "<C-s>%")
                CommandExample("Tmux Split Vertical", "<C-s>\"")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

@Composable
private fun GuideItem(title: String, description: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CommandExample(name: String, command: String) {
    Column {
        Text(name, style = MaterialTheme.typography.bodySmall)
        Text(command, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
    }
}
