package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel

sealed class SendEvent {
    data class ToAddressChanged(val address: String) : SendEvent()
    data class AmountChanged(val amount: String) : SendEvent()
    data class NoteChanged(val note: String) : SendEvent()
    data class FeeLevelChanged(val feeLevel: FeeLevel) : SendEvent()
    object Validate : SendEvent()
    object ClearError : SendEvent()
}