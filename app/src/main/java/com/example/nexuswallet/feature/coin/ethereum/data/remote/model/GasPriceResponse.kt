package com.example.nexuswallet.feature.coin.ethereum.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GasPriceResponse(
    @SerialName("status") val status: String,
    @SerialName("message") val message: String,
    @SerialName("result") val result: GasPriceResultResponse
)

@Serializable
data class GasPriceResultResponse(
    @SerialName("LastBlock") val lastBlock: String,
    @SerialName("SafeGasPrice") val SafeGasPrice: String,
    @SerialName("ProposeGasPrice") val ProposeGasPrice: String,
    @SerialName("FastGasPrice") val FastGasPrice: String,
    @SerialName("suggestBaseFee") val suggestBaseFee: String? = null,
    @SerialName("gasUsedRatio") val gasUsedRatio: String? = null
)