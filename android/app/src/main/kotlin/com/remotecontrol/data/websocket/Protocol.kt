package com.remotecontrol.data.websocket

import com.google.gson.Gson
import java.nio.ByteBuffer

sealed class Frame {
    data class Data(val payload: ByteArray) : Frame() {
        override fun equals(other: Any?) = other is Data && payload.contentEquals(other.payload)
        override fun hashCode() = payload.contentHashCode()
    }
    data class Resize(val cols: Int, val rows: Int) : Frame()
    data object Ping : Frame()
    data object Pong : Frame()
    data class SessionEvent(val eventType: String, val sessionId: String) : Frame()
    data class Auth(val token: ByteArray) : Frame() {
        override fun equals(other: Any?) = other is Auth && token.contentEquals(other.token)
        override fun hashCode() = token.contentHashCode()
    }

    fun encode(): ByteArray = when (this) {
        is Data -> byteArrayOf(TYPE_DATA) + payload
        is Resize -> {
            val buf = ByteBuffer.allocate(5)
            buf.put(TYPE_RESIZE)
            buf.putShort(cols.toShort())
            buf.putShort(rows.toShort())
            buf.array()
        }
        is Ping -> byteArrayOf(TYPE_PING)
        is Pong -> byteArrayOf(TYPE_PONG)
        is SessionEvent -> {
            val json = gson.toJson(mapOf("event_type" to eventType, "session_id" to sessionId))
            byteArrayOf(TYPE_SESSION_EVENT) + json.toByteArray()
        }
        is Auth -> byteArrayOf(TYPE_AUTH) + token
    }

    companion object {
        private const val TYPE_DATA: Byte = 0x00
        private const val TYPE_RESIZE: Byte = 0x01
        private const val TYPE_PING: Byte = 0x02
        private const val TYPE_PONG: Byte = 0x03
        private const val TYPE_SESSION_EVENT: Byte = 0x04
        private const val TYPE_AUTH: Byte = 0x05
        private val gson = Gson()

        fun decode(data: ByteArray): Frame {
            require(data.isNotEmpty()) { "Empty frame" }
            return when (data[0]) {
                TYPE_DATA -> Data(data.copyOfRange(1, data.size))
                TYPE_RESIZE -> {
                    require(data.size >= 5) { "Resize frame too short" }
                    val buf = ByteBuffer.wrap(data, 1, 4)
                    Resize(cols = buf.short.toInt() and 0xFFFF, rows = buf.short.toInt() and 0xFFFF)
                }
                TYPE_PING -> Ping
                TYPE_PONG -> Pong
                TYPE_SESSION_EVENT -> {
                    val json = String(data, 1, data.size - 1)
                    @Suppress("UNCHECKED_CAST")
                    val map = gson.fromJson(json, Map::class.java) as Map<String, String>
                    SessionEvent(eventType = map["event_type"] ?: "", sessionId = map["session_id"] ?: "")
                }
                TYPE_AUTH -> Auth(data.copyOfRange(1, data.size))
                else -> throw IllegalArgumentException("Unknown frame type: 0x${String.format("%02x", data[0])}")
            }
        }
    }
}
