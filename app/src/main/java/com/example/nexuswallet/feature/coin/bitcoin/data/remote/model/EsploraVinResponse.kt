package com.example.nexuswallet.feature.coin.bitcoin.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class EsploraVinResponse(
    val txid: String,
    val vout: Int,
    val is_coinbase: Boolean,
    val scriptsig: String?,
    val scriptsig_asm: String?,
    val sequence: Long,
    val witness: List<String>?= null,
    val prevout: EsploraVoutResponse?
)