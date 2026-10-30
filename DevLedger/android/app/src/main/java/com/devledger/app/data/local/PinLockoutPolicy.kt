package com.devledger.app.data.local

/**
 * Escalating lock-out for wrong PIN entries. A 4-digit PIN has only 10,000 combinations, so
 * without throttling it could be guessed quickly (OWASP Foundation, n.d.a). Pure Kotlin so it is
 * unit tested on the JVM. See REFERENCES-CODE.md for full entries.
 */
object PinLockoutPolicy {
    /** Wrong attempts allowed before the first temporary lock. */
    const val FREE_ATTEMPTS = 5

    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND

    /** How long the PIN pad stays locked after [failures] consecutive wrong entries. */
    fun lockoutMillisFor(failures: Int): Long = when {
        failures < FREE_ATTEMPTS -> 0L
        failures == FREE_ATTEMPTS -> 30 * SECOND
        failures == FREE_ATTEMPTS + 1 -> MINUTE
        failures == FREE_ATTEMPTS + 2 -> 5 * MINUTE
        failures == FREE_ATTEMPTS + 3 -> 15 * MINUTE
        else -> 30 * MINUTE
    }

    /** Wrong attempts left before the next lock; never negative. */
    fun attemptsLeft(failures: Int): Int = (FREE_ATTEMPTS - failures).coerceAtLeast(0)
}
