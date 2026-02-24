package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel

sealed class EthereumSendEvent {
    data class ToAddressChanged(val address: String) : EthereumSendEvent()
    data class AmountChanged(val amount: String) : EthereumSendEvent()
    data class NoteChanged(val note: String) : EthereumSendEvent()
    data class FeeLevelChanged(val feeLevel: FeeLevel) : EthereumSendEvent()
    object Validate : EthereumSendEvent()
    object ClearError : EthereumSendEvent()
}