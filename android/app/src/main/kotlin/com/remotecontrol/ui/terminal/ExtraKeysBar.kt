package com.remotecontrol.ui.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExtraKeysBar(onKey: (String) -> Unit, onCtrl: (Char) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyButton("Esc") { onKey("Esc") }; KeyButton("Tab") { onKey("Tab") }
            KeyButton("Ctrl") { onCtrl('C') }; KeyButton("^D") { onCtrl('D') }
            KeyButton("^Z") { onCtrl('Z') }; KeyButton("^L") { onCtrl('L') }
            KeyButton("|") { onKey("|") }; KeyButton("~") { onKey("~") }
            KeyButton("/") { onKey("/") }; KeyButton("-") { onKey("-") }
            KeyButton("Up") { onKey("Up") }; KeyButton("Down") { onKey("Down") }
            KeyButton("Left") { onKey("Left") }; KeyButton("Right") { onKey("Right") }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(36.dp)) {
        Text(label, fontSize = 12.sp)
    }
}
