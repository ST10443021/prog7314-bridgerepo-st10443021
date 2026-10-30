package com.devledger.app.util

// Reference scope: Utility helpers kept free of UI code. (Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.

/**
 * Converts between currencies using a small in-memory rate table so the Financial
 * Tracker's "real-time conversion rates" requirement (Part 1 app concept) works even
 * fully offline in the prototype. In production this table is refreshed periodically
 * from a currency-rate REST endpoint on the DevLedger API.
 */
object CurrencyConverter {

    // Approximate ZAR-based rates, refreshed manually for the prototype.
    private val ratesToZar = mapOf(
        "ZAR" to 1.0,
        "USD" to 18.10,
        "EUR" to 19.60,
        "GBP" to 22.90,
    )

    fun convert(amount: Double, from: String, to: String): Double {
        val fromRate = ratesToZar[from] ?: 1.0
        val toRate = ratesToZar[to] ?: 1.0
        val amountInZar = amount * fromRate
        return amountInZar / toRate
    }

    fun supportedCurrencies(): List<String> = ratesToZar.keys.toList()
}
