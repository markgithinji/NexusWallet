package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.SerialName
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
@Serializable
data class SolanaFeeEstimate(
    val feeLamports: Long,
    val feeSol: String,
    val estimatedTime: Int,
    val priority: FeeLevel,
    val computeUnits: Int,
    val blockhash: String? = null
)

data class SendSolanaResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)

data class SolanaWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String,
    val network: SolanaNetwork
)

data class TransferInfo(
    val from: String,
    val to: String,
    val amount: Long,
    val isIncoming: Boolean,
    val fee: Long
)