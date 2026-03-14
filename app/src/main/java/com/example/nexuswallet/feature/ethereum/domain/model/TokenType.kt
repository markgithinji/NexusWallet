package com.example.nexuswallet.feature.ethereum.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TokenType {
    NATIVE,     // Native ETH
    ERC20,      // Generic ERC20 token
    USDC,       // USD Coin (special handling for 1:1 USD peg)
    USDT        // Tether
}