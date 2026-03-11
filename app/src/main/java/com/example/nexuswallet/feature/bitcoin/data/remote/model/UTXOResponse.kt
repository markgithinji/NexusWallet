package com.example.nexuswallet.feature.bitcoin.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class UTXOResponse(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: StatusResponse
)