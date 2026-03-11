package com.example.nexuswallet.feature.bitcoin.ui.send

import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork

sealed class BitcoinSendEvent {
    data class Initialize(val walletId: String, val network: BitcoinNetwork?) : BitcoinSendEvent()
    data class UpdateAddress(val address: String) : BitcoinSendEvent()
    data class UpdateAmount(val amount: String) : BitcoinSendEvent()
    data class UpdateFeeLevel(val feeLevel: FeeLevel) : BitcoinSendEvent()
    data class SwitchNetwork(val network: BitcoinNetwork) : BitcoinSendEvent()
}