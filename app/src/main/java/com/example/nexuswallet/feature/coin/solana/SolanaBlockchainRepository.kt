package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.sol4k.Connection
import org.sol4k.Keypair
import org.sol4k.PublicKey
import org.sol4k.TransactionMessage
import org.sol4k.VersionedTransaction
import org.sol4k.instruction.TransferInstruction
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SolanaBlockchainRepository @Inject constructor(
    @param:Named("solanaDevnet") private val devnetConnection: Connection,
    @param:Named("solanaMainnet") private val mainnetConnection: Connection,
    private val solanaRpcService: SolanaRpcService
) {

    private fun getConnection(network: SolanaNetwork = SolanaNetwork.DEVNET): Connection {
        return when (network) {
            SolanaNetwork.MAINNET -> mainnetConnection
            SolanaNetwork.DEVNET -> devnetConnection
        }
    }

    suspend fun getRecentBlockhash(network: SolanaNetwork = SolanaNetwork.DEVNET): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                val connection = getConnection(network)
                val blockhash = connection.getLatestBlockhash()
                Result.Success(blockhash)
            }
        } catch (e: Exception) {
            Result.Error("Failed to get recent blockhash: ${e.message}", e)
        }
    }

    suspend fun getBalance(
        address: String,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<BigDecimal> {
        return try {
            withContext(Dispatchers.IO) {
                val connection = getConnection(network)
                val publicKey = PublicKey(address)
                val balance = connection.getBalance(publicKey)

                val solBalance = BigDecimal(balance).divide(
                    BigDecimal(LAMPORTS_PER_SOL),
                    9,
                    RoundingMode.HALF_UP
                )

                Result.Success(solBalance)
            }
        } catch (e: Exception) {
            Result.Error("Failed to get balance: ${e.message}", e)
        }
    }

    /**
     * Get Solana fee estimate based on priority
     */
    fun getFeeEstimate(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<SolanaFeeEstimate> {
        return try {
            // Solana has fixed fees for now, but we could make them dynamic
            val feeLamports = SOLANA_FIXED_FEE_LAMPORTS

            val feeSol = BigDecimal(feeLamports).divide(
                BigDecimal(LAMPORTS_PER_SOL),
                9,
                RoundingMode.HALF_UP
            ).toPlainString()

            // Different priority levels might affect compute units or priority fees in the future
            val computeUnits = when (feeLevel) {
                FeeLevel.SLOW -> DEFAULT_COMPUTE_UNITS
                FeeLevel.NORMAL -> DEFAULT_COMPUTE_UNITS
                FeeLevel.FAST -> DEFAULT_COMPUTE_UNITS
            }

            Result.Success(
                SolanaFeeEstimate(
                    feeLamports = feeLamports,
                    feeSol = feeSol,
                    estimatedTime = 1, // Solana is fast! ~400ms
                    priority = feeLevel,
                    computeUnits = computeUnits
                )
            )
        } catch (e: Exception) {
            Result.Error("Failed to get fee estimate: ${e.message}", e)
        }
    }

    fun createAndSignTransaction(
        fromKeypair: Keypair,
        toAddress: String,
        lamports: Long,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<SolanaSignedTransaction> {
        return try {
            val connection = getConnection(network)
            val blockhash = connection.getLatestBlockhash()
            val receiver = PublicKey(toAddress)
            val instruction = TransferInstruction(fromKeypair.publicKey, receiver, lamports)

            // TODO: In the future, we could add priority fees based on feeLevel
            // For now, Solana has fixed fees

            val message = TransactionMessage.newMessage(
                feePayer = fromKeypair.publicKey,
                recentBlockhash = blockhash,
                instructions = listOf(instruction)
            )

            val transaction = VersionedTransaction(message)
            transaction.sign(fromKeypair)

            val serializedTx = transaction.serialize()

            // Get signature as hex string
            val signature = if (serializedTx.size >= 64) {
                serializedTx.copyOfRange(0, 64).toHexString()
            } else {
                val hash = MessageDigest.getInstance("SHA-256").digest(serializedTx)
                hash.copyOf(64).toHexString()
            }

            Result.Success(
                SolanaSignedTransaction(
                    signature = signature,
                    serialize = { serializedTx }
                )
            )

        } catch (e: Exception) {
            Result.Error("Failed to create and sign transaction: ${e.message}", e)
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    fun broadcastTransaction(
        signedTransaction: SolanaSignedTransaction,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<BroadcastResult> {
        return try {
            val connection = getConnection(network)
            val serializedTx = signedTransaction.serialize()
            val signature = connection.sendTransaction(serializedTx)

            Result.Success(
                BroadcastResult(
                    success = true,
                    hash = signature
                )
            )

        } catch (e: Exception) {
            Result.Success(
                BroadcastResult(
                    success = false,
                    error = e.message ?: "Broadcast failed"
                )
            )
        }
    }

    suspend fun getTransactionSignatures(
        address: String,
        network: SolanaNetwork = SolanaNetwork.DEVNET,
        limit: Int = 100
    ): Result<List<SolanaTransactionResponse>> = withContext(Dispatchers.IO) {
        try {
            val connection = getConnection(network)
            val publicKey = PublicKey(address)


            val signatures = connection.getSignaturesForAddress(
                publicKey,
                limit = limit
            )


            val responses = signatures.map { sigInfo ->
                SolanaTransactionResponse(
                    signature = sigInfo.signature,
                    slot = sigInfo.slot,
                    blockTime = sigInfo.blockTime,
                    confirmationStatus = sigInfo.confirmationStatus
                )
            }

            Result.Success(responses)

        } catch (e: Exception) {
            Result.Error("Failed to get signatures: ${e.message}", e)
        }
    }
    suspend fun getTransactionDetails(
        signature: String,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<SolanaTransactionDetailsResponse> = withContext(Dispatchers.IO) {
        try {
            // Create a request for getTransaction using RpcParam sealed class
            val request = RpcRequest(
                method = "getTransaction",
                params = listOf(
                    signature.toRpcParam(),
                    mapOf(
                        "commitment" to "confirmed".toRpcParam(),
                        "encoding" to "json".toRpcParam(),
                        "maxSupportedTransactionVersion" to 0.toRpcParam()
                    ).toRpcParam()
                )
            )

            // Make the RPC call
            val response = solanaRpcService.getTransaction(request)

            if (response.error != null) {
                Result.Error("RPC error: ${response.error.message}")
            } else if (response.result != null) {
                Result.Success(response.result)
            } else {
                Result.Error("Transaction not found")
            }
        } catch (e: Exception) {
            Result.Error("Failed to get transaction details: ${e.message}", e)
        }
    }

    suspend fun getFullTransactionHistory(
        address: String,
        network: SolanaNetwork = SolanaNetwork.DEVNET,
        limit: Int = 50
    ): Result<List<Pair<SolanaTransactionResponse, SolanaTransactionDetailsResponse?>>> = withContext(Dispatchers.IO) {
        try {
            // First get signatures
            val signaturesResult = getTransactionSignatures(address, network, limit)

            when (signaturesResult) {
                is Result.Success -> {
                    val signatures = signaturesResult.data

                    // Then fetch details for each signature
                    val results = mutableListOf<Pair<SolanaTransactionResponse, SolanaTransactionDetailsResponse?>>()

                    signatures.forEach { sigInfo ->
                        val details = try {
                            val detailsResult = getTransactionDetails(sigInfo.signature, network)
                            if (detailsResult is Result.Success) {
                                detailsResult.data
                            } else null
                        } catch (e: Exception) {
                            null
                        }

                        results.add(Pair(sigInfo, details))

                        // Small delay to avoid rate limiting
                        delay(50)
                    }

                    Result.Success(results)
                }

                is Result.Error -> signaturesResult
                else -> Result.Error("Unknown error")
            }
        } catch (e: Exception) {
            Result.Error("Failed to get full transaction history: ${e.message}", e)
        }
    }

    // Helper function to parse transfer info from transaction details
    fun parseTransferFromDetails(
        details: SolanaTransactionDetailsResponse,
        walletAddress: String
    ): TransferInfo? {
        try {
            val meta = details.meta ?: return null
            val accountKeys = details.transaction.message.accountKeys

            // Find our wallet's position in account keys
            val walletIndex = accountKeys.indexOfFirst { it == walletAddress }
            if (walletIndex < 0) return null

            // Check if transaction succeeded
            if (meta.err != null) return null

            // Calculate balance change for our wallet
            val preBalance = meta.preBalances.getOrNull(walletIndex) ?: 0
            val postBalance = meta.postBalances.getOrNull(walletIndex) ?: 0
            val balanceChange = postBalance - preBalance

            if (balanceChange == 0L) return null // No SOL transfer

            // Determine if incoming or outgoing
            val isIncoming = balanceChange > 0
            val amount = kotlin.math.abs(balanceChange)

            // Find counterparty (the account that had the opposite balance change)
            var counterparty = ""
            accountKeys.forEachIndexed { index, key ->
                if (index != walletIndex) {
                    val otherPre = meta.preBalances.getOrNull(index) ?: 0
                    val otherPost = meta.postBalances.getOrNull(index) ?: 0
                    val otherChange = otherPost - otherPre

                    // Counterparty should have opposite change (accounting for fees)
                    if (otherChange == -balanceChange ||
                        (otherChange < 0 && otherChange > -balanceChange - 10000)) {
                        counterparty = key
                    }
                }
            }

            return TransferInfo(
                from = if (isIncoming) counterparty else walletAddress,
                to = if (isIncoming) walletAddress else counterparty,
                amount = amount,
                isIncoming = isIncoming,
                fee = meta.fee
            )

        } catch (e: Exception) {
            return null
        }
    }

    data class TransferInfo(
        val from: String,
        val to: String,
        val amount: Long,
        val isIncoming: Boolean,
        val fee: Long
    )


    fun validateAddress(address: String): Result<Boolean> {
        return try {
            PublicKey(address)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Success(false)
        }
    }

    companion object {
        private const val LAMPORTS_PER_SOL = 1_000_000_000L
        private const val SOLANA_FIXED_FEE_LAMPORTS = 5000L
        private const val DEFAULT_COMPUTE_UNITS = 1_400_000
    }
}