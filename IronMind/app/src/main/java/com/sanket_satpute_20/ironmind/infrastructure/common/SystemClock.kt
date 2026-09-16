package com.sanket_satpute_20.ironmind.infrastructure.common

import com.sanket_satpute_20.ironmind.domain.common.Clock

/**
 * Standard implementation of Clock that relies on the system time.
 */
class SystemClock : Clock {
    override fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}
