package com.example.nexuswallet.feature.bitcoin.domain.model

import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork

data class BitcoinWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String,
    val network: BitcoinNetwork
)