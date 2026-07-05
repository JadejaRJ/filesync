package com.syncbridge.core.sync.engine

/**
 * Exponential backoff for retrying a failed transfer. Pure math so it's trivially unit-testable;
 * the executor is responsible for actually delaying (`kotlinx.coroutines.delay`) between attempts.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMillis: Long = 1_000,
    val maxDelayMillis: Long = 30_000,
) {
    /** Delay before attempt number [attempt] (1-indexed: the retry *after* the first failure is attempt 1). */
    fun delayForAttempt(attempt: Int): Long {
        val exponential = baseDelayMillis * (1L shl (attempt - 1).coerceAtMost(20))
        return exponential.coerceAtMost(maxDelayMillis)
    }

    fun shouldRetry(attempt: Int, retryable: Boolean): Boolean = retryable && attempt < maxAttempts
}
