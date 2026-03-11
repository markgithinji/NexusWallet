package com.example.nexuswallet.feature.coin.ethereum.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EtherscanTransactionsResponse(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("result") val result: List<EtherscanTransactionResponse>
)