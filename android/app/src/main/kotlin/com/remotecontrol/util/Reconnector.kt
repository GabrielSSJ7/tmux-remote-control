package com.remotecontrol.util

import kotlin.math.min

class Reconnector(
    private val initialDelayMs: Long = 1000,
    private val maxDelayMs: Long = 30000,
) {
    var attempts: Int = 0
        private set

    fun nextDelay(): Long {
        val delay = min(initialDelayMs * (1L shl attempts), maxDelayMs)
        attempts++
        return delay
    }

    fun reset() {
        attempts = 0
    }
}
