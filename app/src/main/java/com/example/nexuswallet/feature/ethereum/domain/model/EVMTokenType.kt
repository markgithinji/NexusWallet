package com.example.nexuswallet.feature.ethereum.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class EVMTokenType(
    val symbol: String,
    val displayName: String,
    val decimals: Int,
) {
    NATIVE("ETH", "Ethereum", 18),
    USDC("USDC", "USD Coin", 6),
    USDT("USDT", "Tether USD", 6)
}