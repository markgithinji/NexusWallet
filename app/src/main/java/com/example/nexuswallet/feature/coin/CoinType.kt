package com.example.nexuswallet.feature.coin

import kotlinx.serialization.Serializable

@Serializable
enum class CoinType {
    BITCOIN,
    ETHEREUM,
    SOLANA,
    USDC
}