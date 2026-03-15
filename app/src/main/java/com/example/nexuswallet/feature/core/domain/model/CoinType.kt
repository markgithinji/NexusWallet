package com.example.nexuswallet.feature.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CoinType(
    val displayName: String,
    val symbol: String,
    val explorerName: String
) {
    BITCOIN("Bitcoin", "BTC", "Blockstream"),
    ETHEREUM("Ethereum", "ETH", "Etherscan"),
    SOLANA("Solana", "SOL", "Solscan"),
    USDC("USDC", "USDC", "Etherscan"),
    USDT("USDT", "USDT", "Etherscan");
}