package com.example.nexuswallet.feature.coin.solana

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolanaTransactionResponse(
    val signature: String,
    val slot: Long,
    val blockTime: Long?,
    val confirmationStatus: String?,
)

@Serializable
data class SolanaTransactionDetailsResponse(
    val blockTime: Long?,
    val meta: SolanaTransactionMetaResponse?,
    val slot: Long,
    val transaction: SolanaTransactionDataResponse,
    val version: Int? = null
)

@Serializable
sealed class TransactionError {
    @Serializable
    @SerialName("Ok")
    data object Ok : TransactionError()

    @Serializable
    @SerialName("Err")
    data class Err(val message: String) : TransactionError()

    @Serializable
    @SerialName("InstructionError")
    data class InstructionError(val index: Int, val error: String) : TransactionError()

    @Serializable
    @SerialName("AccountNotFound")
    data object AccountNotFound : TransactionError()

    @Serializable
    @SerialName("BlockhashNotFound")
    data object BlockhashNotFound : TransactionError()

    @Serializable
    @SerialName("Custom")
    data class Custom(val code: Int) : TransactionError()
}

@Serializable
data class SolanaTransactionMetaResponse(
    val fee: Long,
    val err: TransactionError? = null,
    val preBalances: List<Long>,
    val postBalances: List<Long>,
    val preTokenBalances: List<SolanaTokenBalanceResponse>? = null,
    val postTokenBalances: List<SolanaTokenBalanceResponse>? = null,
    val logMessages: List<String>? = null,
    val innerInstructions: List<SolanaInnerInstructionResponse>? = null
)

@Serializable
data class SolanaTokenBalanceResponse(
    val accountIndex: Int,
    val mint: String,
    val owner: String? = null,
    val uiTokenAmount: SolanaUiTokenAmountResponse
)

@Serializable
data class SolanaUiTokenAmountResponse(
    val amount: String,
    val decimals: Int,
    val uiAmount: Double? = null,
    val uiAmountString: String
)

@Serializable
data class SolanaInnerInstructionResponse(
    val index: Int,
    val instructions: List<SolanaInstructionDataResponse>
)

@Serializable
data class SolanaTransactionDataResponse(
    val message: SolanaTransactionMessageDataResponse,
    val signatures: List<String>
)

@Serializable
data class SolanaTransactionMessageDataResponse(
    val accountKeys: List<String>,
    val instructions: List<SolanaInstructionDataResponse>,
    val recentBlockhash: String
)

@Serializable
data class SolanaInstructionDataResponse(
    val programIdIndex: Int,
    val accounts: List<Int>? = null,
    val data: String
)
