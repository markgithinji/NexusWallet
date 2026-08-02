package com.example.nexuswallet.feature.settings.ui.util

import androidx.annotation.StringRes
import com.example.nexuswallet.R
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency

/**
 * Maps [SupportedCurrency] to its corresponding string resource ID for display names.
 */
@StringRes
fun SupportedCurrency.getDisplayNameRes(): Int {
    return when (this) {
        SupportedCurrency.USD -> R.string.currency_usd
        SupportedCurrency.EUR -> R.string.currency_eur
        SupportedCurrency.GBP -> R.string.currency_gbp
        SupportedCurrency.JPY -> R.string.currency_jpy
        SupportedCurrency.AUD -> R.string.currency_aud
        SupportedCurrency.CAD -> R.string.currency_cad
        SupportedCurrency.KES -> R.string.currency_kes
    }
}
