package com.example.nexuswallet.feature.coin.solana

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HeliusTransaction(
    val description: String,
    val type: String,
    val source: String,
    val fee: Long,
    val feePayer: String,
    val signature: String,
    val slot: Long,
    val timestamp: Long,
    val tokenTransfers: List<HeliusTokenTransfer> = emptyList(),
    val nativeTransfers: List<HeliusNativeTransfer> = emptyList(),
    val accountData: List<HeliusAccountData> = emptyList(),
    val transactionError: String? = null,
    val instructions: List<HeliusInstruction> = emptyList(),
    @SerialName("lighthouseData") val lighthouseData: String? = null,
    val events: HeliusEvents? = null
)

@Serializable
data class HeliusNativeTransfer(
    val fromUserAccount: String,
    val toUserAccount: String,
    val amount: Long
)

@Serializable
data class HeliusTokenTransfer(
    val fromUserAccount: String,
    val toUserAccount: String,
    val fromTokenAccount: String,
    val toTokenAccount: String,
    val tokenAmount: Double,
    val mint: String
)

@Serializable
data class HeliusAccountData(
    val account: String,
    val nativeBalanceChange: Long,
    val tokenBalanceChanges: List<HeliusTokenBalanceChange> = emptyList()
)

@Serializable
data class HeliusTokenBalanceChange(
    val mint: String,
    val rawTokenAmount: HeliusRawTokenAmount,
    val tokenAccount: String
)

@Serializable
data class HeliusRawTokenAmount(
    val tokenAmount: String,
    val decimals: Int
)

@Serializable
data class HeliusInstruction(
    val accounts: List<String>,
    val data: String,
    val programId: String,
    val innerInstructions: List<HeliusInnerInstruction> = emptyList()
)

@Serializable
data class HeliusInnerInstruction(
    val accounts: List<String>,
    val data: String,
    val programId: String
)

@Serializable
data class HeliusEvents(
    val nft: HeliusNFTEvent? = null,
    val swap: HeliusSwapEvent? = null
)

@Serializable
data class HeliusNFTEvent(
    val nfts: List<HeliusNFT>,
    val type: String,
    val seller: String? = null,
    val buyer: String? = null
)

@Serializable
data class HeliusNFT(
    val mint: String,
    val amount: Int
)

@Serializable
data class HeliusSwapEvent(
    val nativeInput: HeliusNativeInput? = null,
    val nativeOutput: HeliusNativeOutput? = null,
    val tokenInputs: List<HeliusTokenInput> = emptyList(),
    val tokenOutputs: List<HeliusTokenOutput> = emptyList()
)

@Serializable
data class HeliusNativeInput(
    val account: String,
    val amount: Long
)

@Serializable
data class HeliusNativeOutput(
    val account: String,
    val amount: Long
)

@Serializable
data class HeliusTokenInput(
    val fromUserAccount: String,
    val mint: String,
    val tokenAmount: Double,
    val userAccount: String
)

@Serializable
data class HeliusTokenOutput(
    val toUserAccount: String,
    val mint: String,
    val tokenAmount: Double,
    val userAccount: String
)

@Serializable
data class HeliusTransactionRequest(
    val transactions: List<String>
)