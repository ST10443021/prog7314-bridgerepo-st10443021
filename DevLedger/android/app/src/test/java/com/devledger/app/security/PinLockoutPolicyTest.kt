package com.devledger.app.security

import com.devledger.app.data.local.PinLockoutPolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The PIN pad must lock progressively so a 4-digit PIN cannot be guessed quickly. (OWASP Foundation, n.d.a). See REFERENCES-CODE.md. */
class PinLockoutPolicyTest {
    @Test fun `the first five wrong entries are free`() {
        for (failures in 0 until PinLockoutPolicy.FREE_ATTEMPTS) {
            assertThat(PinLockoutPolicy.lockoutMillisFor(failures)).isEqualTo(0L)
        }
    }

    @Test fun `lock-out grows with every further failure`() {
        val steps = (5..9).map { PinLockoutPolicy.lockoutMillisFor(it) }
        assertThat(steps).isInOrder()
        assertThat(steps.first()).isEqualTo(30_000L)
        assertThat(steps.last()).isEqualTo(30 * 60_000L)
    }

    @Test fun `lock-out is capped`() =
        assertThat(PinLockoutPolicy.lockoutMillisFor(500)).isEqualTo(30 * 60_000L)

    @Test fun `attempts left never goes negative`() {
        assertThat(PinLockoutPolicy.attemptsLeft(0)).isEqualTo(5)
        assertThat(PinLockoutPolicy.attemptsLeft(4)).isEqualTo(1)
        assertThat(PinLockoutPolicy.attemptsLeft(12)).isEqualTo(0)
    }
}
