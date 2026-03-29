package com.remotecontrol.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotecontrol.data.websocket.ConnectionState
import com.remotecontrol.data.websocket.Frame
import com.remotecontrol.data.websocket.TerminalSocket
import com.remotecontrol.terminal.TerminalEmulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class TerminalViewModel(
    private val serverUrl: String,
    private val token: String,
    private val sessionId: String,
    okHttpClient: OkHttpClient,
) : ViewModel() {
    val emulator = TerminalEmulator()
    private val socket = TerminalSocket(client = okHttpClient, scope = viewModelScope)
    val connectionState: StateFlow<ConnectionState> = socket.state
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    init {
        viewModelScope.launch {
            socket.incoming.collect { frame ->
                when (frame) {
                    is Frame.Data -> { emulator.process(frame.payload); _version.value = emulator.version }
                    is Frame.SessionEvent -> { if (frame.eventType == "ended") socket.disconnect() }
                    else -> {}
                }
            }
        }
    }

    fun connect() { socket.connect(serverUrl, sessionId, token) }
    fun sendInput(text: String) { socket.send(Frame.Data(text.toByteArray())) }
    fun sendKey(key: String) {
        val bytes = when (key) {
            "Tab" -> byteArrayOf(0x09); "Esc" -> byteArrayOf(0x1b); "Enter" -> byteArrayOf(0x0d)
            "Up" -> "\u001b[A".toByteArray(); "Down" -> "\u001b[B".toByteArray()
            "Right" -> "\u001b[C".toByteArray(); "Left" -> "\u001b[D".toByteArray()
            else -> key.toByteArray()
        }
        socket.send(Frame.Data(bytes))
    }
    fun sendCtrl(c: Char) {
        val code = c.uppercaseChar().code - 64
        if (code in 0..31) socket.send(Frame.Data(byteArrayOf(code.toByte())))
    }
    fun resize(cols: Int, rows: Int) { emulator.resize(cols, rows); socket.send(Frame.Resize(cols = cols, rows = rows)); _version.value = emulator.version }
    override fun onCleared() { socket.disconnect() }
}
