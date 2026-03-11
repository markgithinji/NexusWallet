package com.example.nexuswallet.feature.usdc

import com.example.nexuswallet.feature.core.domain.model.FeeLevel

sealed class USDCSendEvent {
    data class ToAddressChanged(val address: String) : com.example.nexuswallet.feature.usdc.USDCSendEvent()
    data class AmountChanged(val amount: String) : com.example.nexuswallet.feature.usdc.USDCSendEvent()
    data class FeeLevelChanged(val feeLevel: FeeLevel) : com.example.nexuswallet.feature.usdc.USDCSendEvent()
    object Validate : com.example.nexuswallet.feature.usdc.USDCSendEvent()
    object ClearError : com.example.nexuswallet.feature.usdc.USDCSendEvent()
    object ClearInfo : com.example.nexuswallet.feature.usdc.USDCSendEvent()
}