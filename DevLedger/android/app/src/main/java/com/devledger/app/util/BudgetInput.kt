package com.devledger.app.util

/**
 * Parsing and formatting for the monthly-budget field in Settings. Kept free of Android classes
 * so it can be unit tested on the JVM.
 *
 * The budget is expressed in the user's base currency and is also enforced server-side by the
 * budget-alert service that sends the 80% and 100% push notifications.
 * Reference scope: input validation. (Google Developers, n.d.f). See REFERENCES-CODE.md.
 */
object BudgetInput {
    /** Largest accepted budget; protects the decimal column and the progress maths. */
    const val MAX_BUDGET = 1_000_000_000.0

    sealed interface Result {
        /** [amount] is null when the field was left empty or 0, meaning "no budget". */
        data class Valid(val amount: Double?) : Result
        data object Invalid : Result
    }

    fun parse(text: String): Result {
        val cleaned = text.trim().replace(" ", "").replace(',', '.')
        if (cleaned.isEmpty()) return Result.Valid(null)
        val value = cleaned.toDoubleOrNull() ?: return Result.Invalid
        if (value.isNaN() || value.isInfinite() || value < 0.0 || value > MAX_BUDGET) return Result.Invalid
        return Result.Valid(if (value == 0.0) null else Math.round(value * 100.0) / 100.0)
    }

    /** Text shown in the field for a stored budget; blank means no budget is set. */
    fun format(amount: Double?): String = when {
        amount == null || amount <= 0.0 -> ""
        amount == Math.floor(amount) -> amount.toLong().toString()
        else -> amount.toString()
    }
}
