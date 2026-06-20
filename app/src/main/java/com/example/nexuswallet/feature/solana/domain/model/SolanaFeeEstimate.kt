package com.example.nexuswallet.feature.solana.domain.model

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import kotlinx.serialization.Serializable

@Serializable
data class SolanaFeeEstimate(
    val feeLamports: Long,
    val feeSol: String,
    val priorityFeeRate: Long, // in micro-lamports
    val estimatedTime: Int,
    val priority: FeeLevel,
    val computeUnits: Int,
    val blockhash: String? = null
)