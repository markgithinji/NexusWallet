package com.example.nexuswallet.feature.solana.domain.model

import com.example.nexuswallet.feature.solana.domain.model.SolanaNetwork

data class SolanaWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String,
    val network: SolanaNetwork
)