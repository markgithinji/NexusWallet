package com.example.nexuswallet.feature.coin.bitcoin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EsploraVoutResponse(
    val scriptpubkey: String?,
    val scriptpubkey_asm: String?,
    val scriptpubkey_type: String?,
    val scriptpubkey_address: String?,
    val value: Long
)