package com.sanket_satpute_20.ironmind.common

import com.sanket_satpute_20.ironmind.domain.common.Clock

/**
 * A test implementation of Clock that allows for deterministic time manipulation.
 */
class TestClock(private var currentTime: Long = 0L) : Clock {
    override fun currentTimeMillis(): Long {
        return currentTime
    }

    /**
     * Advances the clock by the specified number of milliseconds.
     */
    fun advanceBy(timeMs: Long) {
        currentTime += timeMs
    }

    /**
     * Sets the clock to an exact time.
     */
    fun setTime(timeMs: Long) {
        currentTime = timeMs
    }
}
