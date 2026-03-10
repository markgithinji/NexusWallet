package com.example.nexuswallet.feature.coin.ethereum.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GasPrice(
    val safe: String,
    val propose: String,
    val fast: String,
    val lastBlock: String? = null,
    val baseFee: String? = null
)