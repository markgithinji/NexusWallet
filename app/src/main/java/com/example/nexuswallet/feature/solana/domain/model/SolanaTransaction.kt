package com.example.nexuswallet.feature.solana.domain.model

import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.Serializable

@Serializable
data class SolanaTransaction(
    val id: String,
    val walletId: String,
    val fromAddress: String,
    val toAddress: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val note: String?,
    val feeLevel: FeeLevel,
    val amountLamports: Long,
    val amountSol: String,
    val feeLamports: Long,
    val feeSol: String,
    val signature: String?,
    val network: SolanaNetwork,
    val isIncoming: Boolean = false,
    val tokenMint: String? = null,
    val tokenSymbol: String? = null,
    val tokenDecimals: Int? = null,
    val slot: Long? = null,
    val blockTime: Long? = null
)