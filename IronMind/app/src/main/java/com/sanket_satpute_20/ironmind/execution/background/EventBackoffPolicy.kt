package com.sanket_satpute_20.ironmind.execution.background

/**
 * Deterministic backoff policy for event processing retries:
 * - Base delay: 30 seconds (30,000 ms)
 * - Exponential doubling by failure count: baseDelay * 2^(failureCount - 1)
 * - Maximum delay: 1 hour (3,600,000 ms)
 * - Overflow-safe calculations
 */
object EventBackoffPolicy {
    const val BASE_DELAY_MS: Long = 30_000L
    const val MAX_DELAY_MS: Long = 3_600_000L // 1 hour

    fun calculateDelayMs(failureCount: Int): Long {
        if (failureCount <= 1) return BASE_DELAY_MS
        // 2^20 already exceeds MAX_DELAY_MS; prevent bit-shift overflow
        if (failureCount > 20) return MAX_DELAY_MS

        val multiplier = 1L shl (failureCount - 1)
        val calculated = saturatingMultiply(BASE_DELAY_MS, multiplier)
        return calculated.coerceAtMost(MAX_DELAY_MS)
    }

    fun calculateNextAttemptAt(failureCount: Int, now: Long): Long {
        val delay = calculateDelayMs(failureCount)
        return if (Long.MAX_VALUE - delay < now) {
            Long.MAX_VALUE
        } else {
            now + delay
        }
    }

    private fun saturatingMultiply(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        if (b > 0 && a > Long.MAX_VALUE / b) return Long.MAX_VALUE
        return a * b
    }
}
