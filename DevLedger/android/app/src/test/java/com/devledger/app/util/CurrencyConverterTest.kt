package com.devledger.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Reference scope: JVM unit testing for Android with JUnit, MockK and Truth. (Google Developers, n.d.s). See REFERENCES-CODE.md for full entries.

/** Unit tests for [CurrencyConverter] — verifies the Dev Expense & Income Tracker's
 *  "real-time conversion rates" custom feature converts correctly in both directions. */
class CurrencyConverterTest {

    @Test
    fun `converting ZAR to ZAR returns the same amount`() {
        val result = CurrencyConverter.convert(100.0, "ZAR", "ZAR")
        assertThat(result).isEqualTo(100.0)
    }

    @Test
    fun `converting USD to ZAR scales up using the ZAR rate`() {
        val result = CurrencyConverter.convert(10.0, "USD", "ZAR")
        // 10 USD * 18.10 ZAR/USD = 181.0 ZAR
        assertThat(result).isWithin(0.01).of(181.0)
    }

    @Test
    fun `converting ZAR to USD scales down using the ZAR rate`() {
        val result = CurrencyConverter.convert(181.0, "ZAR", "USD")
        assertThat(result).isWithin(0.01).of(10.0)
    }

    @Test
    fun `unsupported currency code falls back to a 1-to-1 rate rather than crashing`() {
        val result = CurrencyConverter.convert(50.0, "XYZ", "ZAR")
        assertThat(result).isEqualTo(50.0)
    }

    @Test
    fun `supportedCurrencies includes ZAR as the base currency`() {
        assertThat(CurrencyConverter.supportedCurrencies()).contains("ZAR")
    }
}
