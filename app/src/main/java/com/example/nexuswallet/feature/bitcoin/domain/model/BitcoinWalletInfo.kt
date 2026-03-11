package com.example.nexuswallet.feature.bitcoin.domain.model

import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork

data class BitcoinWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String,
    val network: BitcoinNetwork
)