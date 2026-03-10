package com.example.nexuswallet.feature.coin.bitcoin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UtxoResponse(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: StatusResponse
)