package com.sanket_satpute_20.ironmind.testutil.fake

import org.junit.Assert.assertEquals
import org.junit.Test

class FakeClockTest {

    @Test
    fun `test initial time is zero by default`() {
        val clock = FakeClock()
        assertEquals(0L, clock.currentTimeMillis())
    }

    @Test
    fun `test advancing time`() {
        val clock = FakeClock(100L)
        clock.advanceTimeBy(50L)
        assertEquals(150L, clock.currentTimeMillis())
    }

    @Test
    fun `test setting time directly`() {
        val clock = FakeClock()
        clock.setTime(1000L)
        assertEquals(1000L, clock.currentTimeMillis())
    }
}
