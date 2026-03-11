package com.example.nexuswallet.feature.bitcoin.ui.send

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinNetwork

sealed class BitcoinSendEvent {
    data class Initialize(val walletId: String, val network: BitcoinNetwork?) : BitcoinSendEvent()
    data class UpdateAddress(val address: String) : BitcoinSendEvent()
    data class UpdateAmount(val amount: String) : BitcoinSendEvent()
    data class UpdateFeeLevel(val feeLevel: FeeLevel) : BitcoinSendEvent()
    data class SwitchNetwork(val network: BitcoinNetwork) : BitcoinSendEvent()
}