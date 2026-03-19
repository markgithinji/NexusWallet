package com.example.nexuswallet.feature.bitcoin.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddressDto(
    val address: String,
    @SerialName("chain_stats") val chainStatsRDto: ChainStatsRDto,
    @SerialName("mempool_stats") val mempoolStatsDto: MempoolStatsDto
)

@Serializable
data class ChainStatsRDto(
    @SerialName("funded_txo_sum") val fundedTxoSum: Long,
    @SerialName("spent_txo_sum") val spentTxoSum: Long,
    @SerialName("tx_count") val txCount: Int
)

@Serializable
data class MempoolStatsDto(
    @SerialName("funded_txo_sum") val fundedTxoSum: Long,
    @SerialName("spent_txo_sum") val spentTxoSum: Long,
    @SerialName("tx_count") val txCount: Int
)