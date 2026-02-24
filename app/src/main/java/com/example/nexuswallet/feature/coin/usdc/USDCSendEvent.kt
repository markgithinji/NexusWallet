package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel

sealed class USDCSendEvent {
    data class ToAddressChanged(val address: String) : USDCSendEvent()
    data class AmountChanged(val amount: String) : USDCSendEvent()
    data class FeeLevelChanged(val feeLevel: FeeLevel) : USDCSendEvent()
    object Validate : USDCSendEvent()
    object ClearError : USDCSendEvent()
    object ClearInfo : USDCSendEvent()
}