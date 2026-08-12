package com.example.nexuswallet.feature.core.util

import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

/**
 * Extension to format crypto amounts with appropriate precision based on magnitude.
 */
fun String.formatCryptoAmount(): String {
    return try {
        val amountDecimal = this.toBigDecimal()
        when {
            amountDecimal < BigDecimal("0.000001") ->
                amountDecimal.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
                    .toPlainString()

            else ->
                // Use up to 6 decimal places for regular balances to ensure precision 
                // for both small fractions and stablecoin "cents".
                amountDecimal.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
                    .toPlainString()
        }
    } catch (e: Exception) {
        this
    }
}

/**
 * Formats a timestamp into a human-readable relative or absolute time.
 */
fun Long.formatTimestamp(): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L} min ago"
        diff < 86_400_000L -> "${diff / 3_600_000L} hr ago"
        else -> {
            val locale = Locale.getDefault()
            val formatter = SimpleDateFormat("MMM d", locale)
            formatter.format(Date(this))
        }
    }
}

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
        this > 0.0001 -> String.format(Locale.US, "%,.6f", this)
        this > 0 -> {
            // For very small prices (meme coins), show enough decimals to see the value
            val s = String.format(Locale.US, "%.10f", this)
            s.trimEnd('0').trimEnd('.')
        }
        else -> "0.00"
    }
}

fun Float.formatPrice(): String = this.toDouble().formatPrice()

/**
 * Standard two-decimal formatting for percentages or smaller balances.
 */
fun Double.formatTwoDecimals(): String = String.format(Locale.US, "%.2f", this)
fun Float.formatTwoDecimals(): String = String.format(Locale.US, "%.2f", this)

/**
 * Formats a value as currency (e.g., $1,234.56, €1.234,56).
 */
fun Double.formatCurrency(currencyCode: String = "USD"): String {
    return try {
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        format.currency = Currency.getInstance(currencyCode.uppercase())
        format.format(this)
    } catch (e: Exception) {
        // Fallback to USD if currency code is invalid
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        format.format(this)
    }
}

fun Double.formatCurrency(currency: SupportedCurrency): String = formatCurrency(currency.code)

/**
 * Converts a USD value to the target currency and formats it.
 */
fun Double.formatAsCurrency(rate: Double, currency: SupportedCurrency): String {
    val rateSafe = if (rate <= 0.0) 1.0 else rate
    return (this * rateSafe).formatCurrency(currency)
}

fun BigDecimal.formatAsCurrency(rate: Double, currency: SupportedCurrency): String {
    val rateSafe = if (rate <= 0.0) 1.0 else rate
    return (this.toDouble() * rateSafe).formatCurrency(currency)
}

fun BigDecimal.formatCurrency(currency: SupportedCurrency): String = this.toDouble().formatCurrency(currency.code)

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
