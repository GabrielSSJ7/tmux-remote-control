package com.remotecontrol.data.websocket

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test
    fun dataFrameRoundtrip() {
        val frame = Frame.Data("hello terminal".toByteArray())
        val encoded = frame.encode()
        assertEquals(0x00.toByte(), encoded[0])
        val decoded = Frame.decode(encoded)
        assertTrue(decoded is Frame.Data)
        assertArrayEquals("hello terminal".toByteArray(), (decoded as Frame.Data).payload)
    }

    @Test
    fun resizeFrameRoundtrip() {
        val frame = Frame.Resize(cols = 120, rows = 40)
        val encoded = frame.encode()
        assertEquals(0x01.toByte(), encoded[0])
        assertEquals(5, encoded.size)
        val decoded = Frame.decode(encoded)
        assertTrue(decoded is Frame.Resize)
        assertEquals(120, (decoded as Frame.Resize).cols)
        assertEquals(40, decoded.rows)
    }

    @Test
    fun pingPongRoundtrip() {
        assertEquals(Frame.Ping, Frame.decode(Frame.Ping.encode()))
        assertEquals(Frame.Pong, Frame.decode(Frame.Pong.encode()))
    }

    @Test
    fun sessionEventRoundtrip() {
        val frame = Frame.SessionEvent(eventType = "ended", sessionId = "rc-abc")
        val encoded = frame.encode()
        assertEquals(0x04.toByte(), encoded[0])
        val decoded = Frame.decode(encoded) as Frame.SessionEvent
        assertEquals("ended", decoded.eventType)
        assertEquals("rc-abc", decoded.sessionId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyFrameThrows() {
        Frame.decode(byteArrayOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownTypeThrows() {
        Frame.decode(byteArrayOf(0xFF.toByte()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun resizeTooShortThrows() {
        Frame.decode(byteArrayOf(0x01, 0x00))
    }
}
