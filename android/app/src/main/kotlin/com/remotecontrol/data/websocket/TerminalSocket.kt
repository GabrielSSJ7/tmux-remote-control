package com.remotecontrol.data.websocket

import com.remotecontrol.util.Reconnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

class TerminalSocket(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private var webSocket: WebSocket? = null
    private val reconnector = Reconnector()
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var consecutivePingFailures = 0

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _incoming = MutableSharedFlow<Frame>(extraBufferCapacity = 256)
    val incoming: SharedFlow<Frame> = _incoming

    private var currentUrl: String? = null

    fun connect(baseUrl: String, sessionId: String, token: String) {
        disconnect()
        val wsUrl = baseUrl.replace("http", "ws") + "sessions/$sessionId/terminal?token=$token"
        currentUrl = wsUrl
        reconnector.reset()
        doConnect(wsUrl)
    }

    private fun doConnect(url: String) {
        _state.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = ConnectionState.CONNECTED
                reconnector.reset()
                consecutivePingFailures = 0
                startPing()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val frame = Frame.decode(bytes.toByteArray())
                    if (frame is Frame.Pong) {
                        consecutivePingFailures = 0
                        return
                    }
                    _incoming.tryEmit(frame)
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = ConnectionState.DISCONNECTED
                stopPing()
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = ConnectionState.DISCONNECTED
                stopPing()
            }
        })
    }

    fun send(frame: Frame) {
        webSocket?.send(frame.encode().toByteString())
    }

    fun disconnect() {
        reconnectJob?.cancel()
        stopPing()
        webSocket?.close(1000, "bye")
        webSocket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    private fun startPing() {
        pingJob = scope.launch {
            while (isActive) {
                delay(15_000)
                send(Frame.Ping)
                consecutivePingFailures++
                if (consecutivePingFailures >= 3) {
                    webSocket?.cancel()
                    break
                }
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun scheduleReconnect() {
        val url = currentUrl ?: return
        reconnectJob = scope.launch {
            delay(reconnector.nextDelay())
            doConnect(url)
        }
    }
}
