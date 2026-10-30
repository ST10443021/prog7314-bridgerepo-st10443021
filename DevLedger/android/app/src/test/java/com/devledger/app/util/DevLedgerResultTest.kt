package com.devledger.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

// Reference scope: JVM unit testing for Android with JUnit, MockK and Truth. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

class DevLedgerResultTest {

    @Test
    fun `safeCall returns Success when the block completes normally`() {
        val result = DevLedgerResult.safeCall { 42 }
        assertThat(result).isInstanceOf(DevLedgerResult.Success::class.java)
        assertThat((result as DevLedgerResult.Success).data).isEqualTo(42)
    }

    @Test
    fun `safeCall marks IOException as an offline error`() {
        val result = DevLedgerResult.safeCall<Int> { throw IOException("no network") }
        assertThat(result).isInstanceOf(DevLedgerResult.Error::class.java)
        assertThat((result as DevLedgerResult.Error).isOffline).isTrue()
    }

    @Test
    fun `safeCall marks a generic exception as a non-offline error`() {
        val result = DevLedgerResult.safeCall<Int> { error("server said no") }
        assertThat(result).isInstanceOf(DevLedgerResult.Error::class.java)
        assertThat((result as DevLedgerResult.Error).isOffline).isFalse()
        assertThat(result.message).contains("server said no")
    }

    @Test
    fun `onSuccess only runs for a Success result`() {
        var invoked = false
        DevLedgerResult.Success(1).onSuccess { invoked = true }
        assertThat(invoked).isTrue()

        var wronglyInvoked = false
        DevLedgerResult.Error("failed").onSuccess { wronglyInvoked = true }
        assertThat(wronglyInvoked).isFalse()
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun `safeCall propagates coroutine cancellation`() {
        DevLedgerResult.safeCall<Unit> { throw kotlinx.coroutines.CancellationException("Cancelled") }
    }
}
