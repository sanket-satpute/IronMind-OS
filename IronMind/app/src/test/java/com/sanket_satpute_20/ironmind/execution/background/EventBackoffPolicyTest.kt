package com.sanket_satpute_20.ironmind.execution.background

import org.junit.Assert.assertEquals
import org.junit.Test

class EventBackoffPolicyTest {

    @Test
    fun `calculateDelayMs returns base interval for failureCount 0 or 1`() {
        assertEquals(30_000L, EventBackoffPolicy.calculateDelayMs(0))
        assertEquals(30_000L, EventBackoffPolicy.calculateDelayMs(1))
        assertEquals(30_000L, EventBackoffPolicy.calculateDelayMs(-5))
    }

    @Test
    fun `calculateDelayMs doubles exponentially with failureCount`() {
        assertEquals(30_000L, EventBackoffPolicy.calculateDelayMs(1))     // 30s
        assertEquals(60_000L, EventBackoffPolicy.calculateDelayMs(2))     // 60s
        assertEquals(120_000L, EventBackoffPolicy.calculateDelayMs(3))    // 120s
        assertEquals(240_000L, EventBackoffPolicy.calculateDelayMs(4))    // 240s
        assertEquals(480_000L, EventBackoffPolicy.calculateDelayMs(5))    // 480s
        assertEquals(960_000L, EventBackoffPolicy.calculateDelayMs(6))    // 960s
        assertEquals(1_920_000L, EventBackoffPolicy.calculateDelayMs(7))  // 1920s
    }

    @Test
    fun `calculateDelayMs caps at one hour for higher failure counts`() {
        val oneHourMs = 3_600_000L
        assertEquals(oneHourMs, EventBackoffPolicy.calculateDelayMs(8))
        assertEquals(oneHourMs, EventBackoffPolicy.calculateDelayMs(15))
        assertEquals(oneHourMs, EventBackoffPolicy.calculateDelayMs(50))
        assertEquals(oneHourMs, EventBackoffPolicy.calculateDelayMs(1000))
        assertEquals(oneHourMs, EventBackoffPolicy.calculateDelayMs(Int.MAX_VALUE))
    }

    @Test
    fun `calculateNextAttemptAt calculates correct future timestamp deterministically`() {
        val now = 1_000_000L
        assertEquals(now + 30_000L, EventBackoffPolicy.calculateNextAttemptAt(1, now))
        assertEquals(now + 60_000L, EventBackoffPolicy.calculateNextAttemptAt(2, now))
        assertEquals(now + 120_000L, EventBackoffPolicy.calculateNextAttemptAt(3, now))
    }

    @Test
    fun `calculateNextAttemptAt protects against Long overflow safely`() {
        val nearMax = Long.MAX_VALUE - 10_000L
        val next = EventBackoffPolicy.calculateNextAttemptAt(1, nearMax)
        assertEquals(Long.MAX_VALUE, next)
    }
}
