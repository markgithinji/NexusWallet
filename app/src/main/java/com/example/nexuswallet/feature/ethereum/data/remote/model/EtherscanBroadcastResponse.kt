package com.example.nexuswallet.feature.ethereum.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EtherscanBroadcastResponse(
    @SerialName("jsonrpc") val jsonrpc: String? = null,
    @SerialName("result") val result: String? = null,
    @SerialName("id") val id: Int? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("error") val error: BroadcastErrorResponse? = null
)

@Serializable
data class BroadcastErrorResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String
)