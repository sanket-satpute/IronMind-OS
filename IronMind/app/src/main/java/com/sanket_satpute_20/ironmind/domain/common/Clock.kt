package com.sanket_satpute_20.ironmind.domain.common

/**
 * Interface to provide the current time.
 * This abstraction allows the domain layer to be tested deterministically.
 */
interface Clock {
    /**
     * Returns the current time in milliseconds since the epoch.
     */
    fun currentTimeMillis(): Long
}
