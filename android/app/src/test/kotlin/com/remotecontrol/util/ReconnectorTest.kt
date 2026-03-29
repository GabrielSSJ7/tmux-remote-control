package com.remotecontrol.util

import org.junit.Assert.*
import org.junit.Test

class ReconnectorTest {
    @Test
    fun firstDelayIsInitial() {
        val r = Reconnector(initialDelayMs = 1000, maxDelayMs = 30000)
        assertEquals(1000L, r.nextDelay())
    }

    @Test
    fun delayDoublesEachAttempt() {
        val r = Reconnector(initialDelayMs = 1000, maxDelayMs = 30000)
        assertEquals(1000L, r.nextDelay())
        assertEquals(2000L, r.nextDelay())
        assertEquals(4000L, r.nextDelay())
        assertEquals(8000L, r.nextDelay())
    }

    @Test
    fun delayCapAtMax() {
        val r = Reconnector(initialDelayMs = 1000, maxDelayMs = 5000)
        r.nextDelay(); r.nextDelay(); r.nextDelay(); r.nextDelay()
        assertEquals(5000L, r.nextDelay())
    }

    @Test
    fun resetRestartsSequence() {
        val r = Reconnector(initialDelayMs = 1000, maxDelayMs = 30000)
        r.nextDelay(); r.nextDelay()
        r.reset()
        assertEquals(1000L, r.nextDelay())
    }

    @Test
    fun attemptCountIncrements() {
        val r = Reconnector(initialDelayMs = 1000, maxDelayMs = 30000)
        assertEquals(0, r.attempts)
        r.nextDelay()
        assertEquals(1, r.attempts)
    }
}
