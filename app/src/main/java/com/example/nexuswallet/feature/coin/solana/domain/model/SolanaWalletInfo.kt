package com.example.nexuswallet.feature.coin.solana.domain.model

import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork

data class SolanaWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String,
    val network: SolanaNetwork
)