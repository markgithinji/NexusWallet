package com.example.nexuswallet.feature.solana.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SolanaNetwork {
    abstract val name: String

    @Serializable
    @SerialName("SolanaMainnet")
    data object Mainnet : SolanaNetwork() {
        override val name = "SolanaMainnet"
    }

    @Serializable
    @SerialName("SolanaDevnet")
    data object Devnet : SolanaNetwork() {
        override val name = "SolanaDevnet"
    }
}