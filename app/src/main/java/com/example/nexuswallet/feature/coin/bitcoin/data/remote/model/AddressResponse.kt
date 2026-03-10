package com.example.nexuswallet.feature.coin.bitcoin.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddressResponse(
    val address: String,
    @SerialName("chain_stats") val chainStatsResponse: ChainStatsResponse,
    @SerialName("mempool_stats") val mempoolStatsResponse: MempoolStatsResponse
)