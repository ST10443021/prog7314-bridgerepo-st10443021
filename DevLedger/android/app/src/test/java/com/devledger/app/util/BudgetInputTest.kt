package com.devledger.app.util

import com.devledger.app.util.BudgetInput.Result
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Parsing rules for the monthly-budget field in Settings. (Google Developers, n.d.f). See REFERENCES-CODE.md. */
class BudgetInputTest {
    @Test fun `blank means no budget`() = assertThat(BudgetInput.parse("   ")).isEqualTo(Result.Valid(null))

    @Test fun `zero means no budget`() = assertThat(BudgetInput.parse("0")).isEqualTo(Result.Valid(null))

    @Test fun `whole and decimal amounts are accepted`() {
        assertThat(BudgetInput.parse("5000")).isEqualTo(Result.Valid(5000.0))
        assertThat(BudgetInput.parse("5000.50")).isEqualTo(Result.Valid(5000.5))
    }

    @Test fun `comma decimal separator is accepted`() =
        assertThat(BudgetInput.parse("1 250,75")).isEqualTo(Result.Valid(1250.75))

    @Test fun `amounts are rounded to cents`() =
        assertThat(BudgetInput.parse("10.006")).isEqualTo(Result.Valid(10.01))

    @Test fun `negative, text and absurd values are rejected`() {
        assertThat(BudgetInput.parse("-5")).isEqualTo(Result.Invalid)
        assertThat(BudgetInput.parse("abc")).isEqualTo(Result.Invalid)
        assertThat(BudgetInput.parse("1.2.3")).isEqualTo(Result.Invalid)
        assertThat(BudgetInput.parse("9999999999999")).isEqualTo(Result.Invalid)
    }

    @Test fun `format hides zero and trims whole numbers`() {
        assertThat(BudgetInput.format(null)).isEmpty()
        assertThat(BudgetInput.format(0.0)).isEmpty()
        assertThat(BudgetInput.format(2500.0)).isEqualTo("2500")
        assertThat(BudgetInput.format(2500.5)).isEqualTo("2500.5")
    }
}
