package com.example.nexuswallet.feature.wallet.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransactionFormatHelper {

    fun formatAmount(amount: String): String {
        return try {
            val amountDecimal = amount.toBigDecimal()
            when {
                amountDecimal < BigDecimal(AMOUNT_THRESHOLD_ULTRA_SMALL) ->
                    amountDecimal.setScale(ULTRA_SMALL_SCALE, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()

                amountDecimal < BigDecimal(AMOUNT_THRESHOLD_VERY_SMALL) ->
                    amountDecimal.setScale(VERY_SMALL_SCALE, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()

                amountDecimal < BigDecimal(AMOUNT_THRESHOLD_SMALL) ->
                    amountDecimal.setScale(SMALL_SCALE, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()

                else ->
                    amountDecimal.setScale(NORMAL_SCALE, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()
            }
        } catch (e: Exception) {
            amount
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < JUST_NOW_THRESHOLD -> "Just now"
            diff < MINUTES_THRESHOLD -> "${diff / MINUTE_MILLIS} min ago"
            diff < HOURS_THRESHOLD -> "${diff / HOUR_MILLIS} hr ago"
            else -> {
                val date = Date(timestamp)
                DATE_FORMAT.format(date)
            }
        }
    }

    // Amount formatting thresholds
    private const val AMOUNT_THRESHOLD_ULTRA_SMALL = "0.000001"
    private const val AMOUNT_THRESHOLD_VERY_SMALL = "0.001"
    private const val AMOUNT_THRESHOLD_SMALL = "1"

    // Amount formatting scales
    private const val ULTRA_SMALL_SCALE = 8
    private const val VERY_SMALL_SCALE = 6
    private const val SMALL_SCALE = 4
    private const val NORMAL_SCALE = 2

    // Time thresholds in milliseconds
    private const val JUST_NOW_THRESHOLD = 60_000L  // 1 minute
    private const val MINUTES_THRESHOLD = 3_600_000L  // 1 hour
    private const val HOURS_THRESHOLD = 86_400_000L  // 24 hours

    // Time constants
    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 3_600_000L

    // Date formatter
    private val DATE_FORMAT = SimpleDateFormat("MMM d", Locale.getDefault())
}