package com.example.nexuswallet.feature.coin.bitcoin.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EsploraVoutResponse(
    val scriptpubkey: String?,
    @SerialName("scriptpubkey_asm") val scriptpubkeyAsm: String?,
    @SerialName("scriptpubkey_type") val scriptpubkeyType: String?,
    @SerialName("scriptpubkey_address") val scriptpubkeyAddress: String?,
    val value: Long
)