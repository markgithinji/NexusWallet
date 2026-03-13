package com.example.nexuswallet.feature.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CoinType(val displayName: String) {
    BITCOIN("Bitcoin"),
    ETHEREUM("Ethereum"),
    SOLANA("Solana"),
    USDC("USDC")
}