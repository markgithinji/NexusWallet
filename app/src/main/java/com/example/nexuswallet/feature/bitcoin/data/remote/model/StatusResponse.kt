package com.example.nexuswallet.feature.bitcoin.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(
    val confirmed: Boolean,
    @SerialName("block_height") val blockHeight: Int? = null,
    @SerialName("block_hash") val blockHash: String? = null,
    @SerialName("block_time") val blockTime: Long? = null
)