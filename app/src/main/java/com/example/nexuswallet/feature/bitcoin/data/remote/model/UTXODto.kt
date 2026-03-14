package com.example.nexuswallet.feature.bitcoin.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class UTXODto(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: StatusDto
)