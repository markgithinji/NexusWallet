package com.example.nexuswallet.feature.coin.bitcoin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EsploraStatusResponse(
    val confirmed: Boolean,
    val block_height: Int? = null,
    val block_hash: String? = null,
    val block_time: Long? = null
)