package com.remotecontrol.data.websocket

import org.junit.Ignore
import org.junit.Test

class TerminalSocketTest {

    @Ignore("Stub: implement after TerminalSocket rewrite")
    @Test
    fun connectUrlDoesNotContainToken() {
        TODO("Build TerminalSocket, call connect(), capture the URL passed to OkHttp, assert no ?token=")
    }

    @Ignore("Stub: implement after TerminalSocket rewrite")
    @Test
    fun authFrameSentAsFirstMessageInOnOpen() {
        TODO("Mock WebSocket, trigger onOpen, assert first send() call is Frame.Auth encoded bytes")
    }

    @Ignore("Stub: implement after TerminalSocket rewrite")
    @Test
    fun reconnectUsesStoredToken() {
        TODO("Trigger reconnect, assert doConnect receives the original token")
    }
}
