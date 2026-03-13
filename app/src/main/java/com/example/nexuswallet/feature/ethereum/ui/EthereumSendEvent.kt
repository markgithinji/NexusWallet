package com.example.nexuswallet.feature.ethereum.ui

import com.example.nexuswallet.feature.core.domain.model.FeeLevel

sealed class EthereumSendEvent {
    data class ToAddressChanged(val address: String) : EthereumSendEvent()
    data class AmountChanged(val amount: String) : EthereumSendEvent()
    data class NoteChanged(val note: String) : EthereumSendEvent()
    data class FeeLevelChanged(val feeLevel: FeeLevel) : EthereumSendEvent()
    object Validate : EthereumSendEvent()
    object ClearError : EthereumSendEvent()
}