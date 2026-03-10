package com.example.nexuswallet.feature.coin.ethereum.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EtherscanTransactionCountResponse(
    @SerialName("jsonrpc") val jsonrpc: String? = null,
    @SerialName("result") val result: String,
    @SerialName("id") val id: Int? = null
)
