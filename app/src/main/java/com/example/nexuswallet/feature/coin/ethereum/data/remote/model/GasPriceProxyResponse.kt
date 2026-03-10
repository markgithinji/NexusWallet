package com.example.nexuswallet.feature.coin.ethereum.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GasPriceProxyResponse(
    @SerialName("jsonrpc") val jsonrpc: String,
    @SerialName("id") val id: Int,
    @SerialName("result") val result: String
)