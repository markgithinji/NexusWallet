package com.example.nexuswallet.feature.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency

/**
 * CompositionLocal for [CurrencyState].
 */
val LocalCurrency = compositionLocalOf { CurrencyState() }

/**
 * Provider for [LocalCurrency].
 */
@Composable
fun ProvideCurrency(
    state: CurrencyState,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalCurrency provides state) {
        content()
    }
}

/**
 * Global UI state for currency selection and exchange rates.
 */
data class CurrencyState(
    val currency: SupportedCurrency = SupportedCurrency.USD,
    val usdToRate: Double = 1.0
)
