package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Clock

class FakeClock(
    private var currentTime: Long = 0L
) : Clock {
    override fun currentTimeMillis(): Long = currentTime

    fun advanceTimeBy(millis: Long) {
        currentTime += millis
    }

    fun setTime(millis: Long) {
        currentTime = millis
    }
}
