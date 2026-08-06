package com.example.nexuswallet.feature.solana.ui

import com.example.nexuswallet.feature.core.domain.model.FeeLevel

sealed class SolanaSendEvent {
    data class ToAddressChanged(val address: String) : SolanaSendEvent()
    data class AmountChanged(val amount: String) : SolanaSendEvent()
    data class FeeLevelChanged(val feeLevel: FeeLevel) : SolanaSendEvent()
    data class SelectToken(val token: com.example.nexuswallet.feature.wallet.domain.model.SPLToken?) : SolanaSendEvent()
    object Validate : SolanaSendEvent()
    object ClearError : SolanaSendEvent()
    data class ToggleFiatMode(val isFiatMode: Boolean) : SolanaSendEvent()
}