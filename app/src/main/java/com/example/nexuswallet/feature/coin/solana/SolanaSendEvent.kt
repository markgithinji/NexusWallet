package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel


sealed class SolanaSendEvent {
    data class ToAddressChanged(val address: String) : SolanaSendEvent()
    data class AmountChanged(val amount: String) : SolanaSendEvent()
    data class FeeLevelChanged(val feeLevel: FeeLevel) : SolanaSendEvent()
    object Validate : SolanaSendEvent()
    object ClearError : SolanaSendEvent()
}