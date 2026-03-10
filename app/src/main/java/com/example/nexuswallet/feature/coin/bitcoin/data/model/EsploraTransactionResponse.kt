package com.example.nexuswallet.feature.coin.bitcoin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EsploraTransactionResponse(
    val txid: String,
    val version: Int,
    val locktime: Int,
    val size: Int,
    val weight: Int,
    val fee: Long,
    val vin: List<EsploraVinResponse>,
    val vout: List<EsploraVoutResponse>,
    val status: EsploraStatusResponse
)
