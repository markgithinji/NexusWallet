package com.example.nexuswallet.feature.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CoinType {
    BITCOIN,
    ETHEREUM,
    SOLANA,
    USDC
}