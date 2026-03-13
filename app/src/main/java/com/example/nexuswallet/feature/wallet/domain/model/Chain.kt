package com.example.nexuswallet.feature.wallet.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Chain {
    @Serializable
    @SerialName("Bitcoin")
    data object Bitcoin : Chain()

    @Serializable
    @SerialName("Ethereum")
    data object Ethereum : Chain()

    @Serializable
    @SerialName("Solana")
    data object Solana : Chain()
}