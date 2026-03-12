package com.example.nexuswallet.feature.solana.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SolanaNetwork {
    abstract val name: String
    abstract val displayName: String

    @Serializable
    @SerialName("SolanaMainnet")
    data object Mainnet : SolanaNetwork() {
        override val name = "SolanaMainnet"
        override val displayName = "Mainnet"
    }

    @Serializable
    @SerialName("SolanaDevnet")
    data object Devnet : SolanaNetwork() {
        override val name = "SolanaDevnet"
        override val displayName = "Devnet"
    }
}