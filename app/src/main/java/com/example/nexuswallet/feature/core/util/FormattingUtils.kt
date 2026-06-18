package com.example.nexuswallet.feature.core.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Extension to format prices based on their magnitude.
 * >= 1000: 1,234.56
 * >= 1: 1.2345
 * < 1: 0.001234
 */
fun Double.formatPrice(): String {
    return when {
        this >= 1000 -> String.format(Locale.US, "%,.2f", this)
        this >= 1 -> String.format(Locale.US, "%,.4f", this)
        else -> String.format(Locale.US, "%,.6f", this)
    }
}

fun Float.formatPrice(): String = this.toDouble().formatPrice()

/**
 * Standard two-decimal formatting for percentages or smaller balances.
 */
fun Double.formatTwoDecimals(): String = String.format(Locale.US, "%.2f", this)
fun Float.formatTwoDecimals(): String = String.format(Locale.US, "%.2f", this)

/**
 * Formats a value as USD currency (e.g., $1,234.56).
 */
fun Double.formatCurrency(): String =
    NumberFormat.getCurrencyInstance(Locale.US).format(this)

/**
 * Formats a percentage with a sign and two decimals (e.g., +5.23% or -1.10%).
 */
fun Double.formatPercent(): String {
    val sign = if (this >= 0) "+" else ""
    return "$sign${this.formatTwoDecimals()}%"
}

/**
 * Formats large market cap numbers into readable strings (T, B, M, K).
 */
fun formatLargeNumber(number: Double): String {
    return when {
        number >= 1_000_000_000_000 -> "${(number / 1_000_000_000_000.0).formatTwoDecimals()}T"
        number >= 1_000_000_000 -> "${(number / 1_000_000_000.0).formatTwoDecimals()}B"
        number >= 1_000_000 -> "${(number / 1_000_000.0).formatTwoDecimals()}M"
        number >= 1_000 -> "${(number / 1_000.0).formatTwoDecimals()}K"
        else -> number.formatTwoDecimals()
    }
}

/**
 * Formats token supply numbers.
 */
fun formatSupply(supply: Double): String {
    return when {
        supply >= 1_000_000_000 -> "${(supply / 1_000_000_000.0).formatTwoDecimals()}B"
        supply >= 1_000_000 -> "${(supply / 1_000_000.0).formatTwoDecimals()}M"
        supply >= 1_000 -> "${(supply / 1_000.0).formatTwoDecimals()}K"
        else -> supply.formatTwoDecimals()
    }
}
