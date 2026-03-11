package com.example.nexuswallet.feature.solana.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HeliusTransactionResponse(
    val description: String,
    val type: String,
    val source: String,
    val fee: Long,
    val feePayer: String,
    val signature: String,
    val slot: Long,
    val timestamp: Long,
    val tokenTransfers: List<com.example.nexuswallet.feature.solana.data.remote.HeliusTokenTransferResponse> = emptyList(),
    val nativeTransfers: List<com.example.nexuswallet.feature.solana.data.remote.HeliusNativeTransferResponse> = emptyList(),
    val accountData: List<com.example.nexuswallet.feature.solana.data.remote.HeliusAccountDataResponse> = emptyList(),
    val transactionError: String? = null,
    val instructions: List<com.example.nexuswallet.feature.solana.data.remote.HeliusInstructionResponse> = emptyList(),
    @SerialName("lighthouseData") val lighthouseData: String? = null,
    val events: com.example.nexuswallet.feature.solana.data.remote.HeliusEventsResponse? = null
)

@Serializable
data class HeliusNativeTransferResponse(
    val fromUserAccount: String,
    val toUserAccount: String,
    val amount: Long
)

@Serializable
data class HeliusTokenTransferResponse(
    val fromUserAccount: String,
    val toUserAccount: String,
    val fromTokenAccount: String,
    val toTokenAccount: String,
    val tokenAmount: Double,
    val mint: String
)

@Serializable
data class HeliusAccountDataResponse(
    val account: String,
    val nativeBalanceChange: Long,
    val tokenBalanceChanges: List<com.example.nexuswallet.feature.solana.data.remote.HeliusTokenBalanceChangeResponse> = emptyList()
)

@Serializable
data class HeliusTokenBalanceChangeResponse(
    val mint: String,
    val rawTokenAmount: com.example.nexuswallet.feature.solana.data.remote.HeliusRawTokenAmountResponse,
    val tokenAccount: String
)

@Serializable
data class HeliusRawTokenAmountResponse(
    val tokenAmount: String,
    val decimals: Int
)

@Serializable
data class HeliusInstructionResponse(
    val accounts: List<String>,
    val data: String,
    val programId: String,
    val innerInstructions: List<com.example.nexuswallet.feature.solana.data.remote.HeliusInnerInstructionResponse> = emptyList()
)

@Serializable
data class HeliusInnerInstructionResponse(
    val accounts: List<String>,
    val data: String,
    val programId: String
)

@Serializable
data class HeliusEventsResponse(
    val nft: com.example.nexuswallet.feature.solana.data.remote.HeliusNFTEventResponse? = null,
    val swap: com.example.nexuswallet.feature.solana.data.remote.HeliusSwapEventResponse? = null
)

@Serializable
data class HeliusNFTEventResponse(
    val nfts: List<com.example.nexuswallet.feature.solana.data.remote.HeliusNFTResponse>,
    val type: String,
    val seller: String? = null,
    val buyer: String? = null
)

@Serializable
data class HeliusNFTResponse(
    val mint: String,
    val amount: Int
)

@Serializable
data class HeliusSwapEventResponse(
    val nativeInput: com.example.nexuswallet.feature.solana.data.remote.HeliusNativeInputResponse? = null,
    val nativeOutput: com.example.nexuswallet.feature.solana.data.remote.HeliusNativeOutputResponse? = null,
    val tokenInputs: List<com.example.nexuswallet.feature.solana.data.remote.HeliusTokenInputResponse> = emptyList(),
    val tokenOutputs: List<com.example.nexuswallet.feature.solana.data.remote.HeliusTokenOutputResponse> = emptyList()
)

@Serializable
data class HeliusNativeInputResponse(
    val account: String,
    val amount: Long
)

@Serializable
data class HeliusNativeOutputResponse(
    val account: String,
    val amount: Long
)

@Serializable
data class HeliusTokenInputResponse(
    val fromUserAccount: String,
    val mint: String,
    val tokenAmount: Double,
    val userAccount: String
)

@Serializable
data class HeliusTokenOutputResponse(
    val toUserAccount: String,
    val mint: String,
    val tokenAmount: Double,
    val userAccount: String
)

@Serializable
data class HeliusTransactionRequest(
    val transactions: List<String>
)