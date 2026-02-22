package com.example.nexuswallet.feature.coin.solana

import android.util.Log
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.BroadcastResult
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

    private fun getConnection(network: SolanaNetwork): Connection {
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
                Log.d("SolanaRepo", "Recent blockhash for ${network.name}: $blockhash")
                Result.Success(blockhash)
            }
        } catch (e: Exception) {
            Result.Error("Failed to get recent blockhash on ${network.name}: ${e.message}", e)
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
                    SOL_DECIMALS,
                    RoundingMode.HALF_UP
                )

                Log.d("SolanaRepo", "Balance for $address on ${network.name}: $solBalance SOL")
                Result.Success(solBalance)
            }
        } catch (e: Exception) {
            Result.Error("Failed to get balance on ${network.name}: ${e.message}", e)
        }
    }

    /**
     * Get Solana fee estimate with dynamic priority fees based on network conditions.
     * Base fee is fixed at 5,000 lamports. Priority fees are calculated from recent network activity.
     */
    suspend fun getFeeEstimate(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<SolanaFeeEstimate> {
        return try {
            val baseFeeLamports = SOLANA_FIXED_FEE_LAMPORTS

            val percentile = when (feeLevel) {
                FeeLevel.SLOW -> 0
                FeeLevel.NORMAL -> 50
                FeeLevel.FAST -> 95
            }

            val priorityFeeRate = if (percentile > 0) {
                val result = getRecommendedPriorityFee(percentile, emptyList(), network)
                if (result is Result.Success) result.data else 0
            } else 0

            // Match compute units with transaction
            val computeUnits = when (feeLevel) {
                FeeLevel.SLOW -> 200_000
                FeeLevel.NORMAL -> 400_000
                FeeLevel.FAST -> 800_000
            }

            val priorityFeeLamports = (priorityFeeRate.toLong() * computeUnits) / 1_000_000
            val totalFeeLamports = baseFeeLamports + priorityFeeLamports

            val totalFeeSol = BigDecimal(totalFeeLamports).divide(
                BigDecimal(LAMPORTS_PER_SOL),
                SOL_DECIMALS,
                RoundingMode.HALF_UP
            ).toPlainString()

            val estimatedTime = when (feeLevel) {
                FeeLevel.SLOW -> 2
                FeeLevel.NORMAL -> 1
                FeeLevel.FAST -> 1
            }

            Result.Success(
                SolanaFeeEstimate(
                    feeLamports = totalFeeLamports,
                    feeSol = totalFeeSol,
                    estimatedTime = estimatedTime,
                    priority = feeLevel,
                    computeUnits = computeUnits
                )
            )
        } catch (e: Exception) {
            Result.Error("Failed to get fee estimate on ${network.name}: ${e.message}", e)
        }
    }


    /**
     * Get recommended priority fee
     */
    private suspend fun getRecommendedPriorityFee(
        percentile: Int,
        accounts: List<PublicKey> = emptyList(),
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val connection = getConnection(network)

            // Fetch recent priority fees
            val recentFees = if (accounts.isNotEmpty()) {
                connection.getRecentPrioritizationFees(accounts)
            } else {
                connection.getRecentPrioritizationFees(emptyList())
            }

            if (recentFees.isEmpty()) {
                return@withContext Result.Success(0)
            }

            // Extract fee values and sort
            val feeValues = recentFees.map { it.prioritizationFee }.sorted()

            // Calculate percentile
            val index = (feeValues.size * percentile / 100).coerceIn(0, feeValues.size - 1)
            val recommendedFee = feeValues[index]

            Log.d(
                "SolanaRepo",
                "Recommended priority fee ($percentile percentile): $recommendedFee microLamports"
            )
            Result.Success(recommendedFee.toInt())

        } catch (e: Exception) {
            Log.e("SolanaRepo", "Failed to get priority fee: ${e.message}")
            Result.Error("Failed to get priority fee: ${e.message}", e)
        }
    }

    fun createAndSignTransaction(
        fromKeypair: Keypair,
        toAddress: String,
        lamports: Long,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<SolanaSignedTransaction> {
        return try {
            val connection = getConnection(network)
            val blockhash = connection.getLatestBlockhash()
            val receiver = PublicKey(toAddress)

            val instructions = listOf(
                TransferInstruction(fromKeypair.publicKey, receiver, lamports)
            )

            val message = TransactionMessage.newMessage(
                feePayer = fromKeypair.publicKey,
                recentBlockhash = blockhash,
                instructions = instructions
            )

            val transaction = VersionedTransaction(message)
            transaction.sign(fromKeypair)

            val serializedTx = transaction.serialize()
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
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }

    fun broadcastTransaction(
        signedTransaction: SolanaSignedTransaction,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<BroadcastResult> {
        return try {
            val connection = getConnection(network)
            val serializedTx = signedTransaction.serialize()
            val signature = connection.sendTransaction(serializedTx)

            Log.d("SolanaRepo", "Broadcast successful on ${network.name}: $signature")
            Result.Success(
                BroadcastResult(
                    success = true,
                    hash = signature
                )
            )

        } catch (e: Exception) {
            Log.e("SolanaRepo", "Broadcast failed on ${network.name}: ${e.message}")
            Result.Success(
                BroadcastResult(
                    success = false,
                    error = e.message ?: "Broadcast failed on ${network.name}"
                )
            )
        }
    }

    suspend fun getTransactionSignatures(
        address: String,
        network: SolanaNetwork = SolanaNetwork.DEVNET,
        limit: Int = DEFAULT_SIGNATURE_LIMIT
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

            Log.d(
                "SolanaRepo",
                "Found ${responses.size} signatures for $address on ${network.name}"
            )
            Result.Success(responses)

        } catch (e: Exception) {
            Result.Error("Failed to get signatures on ${network.name}: ${e.message}", e)
        }
    }

    suspend fun getTransactionDetails(
        signature: String,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<SolanaTransactionDetailsResponse> = withContext(Dispatchers.IO) {
        try {
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

            val response = solanaRpcService.getTransaction(request)

            if (response.error != null) {
                Log.e("SolanaRepo", "RPC error for tx $signature: ${response.error.message}")
                Result.Error("RPC error: ${response.error.message}")
            } else if (response.result != null) {
                Log.d(
                    "SolanaRepo",
                    "Got transaction details for ${signature.take(8)}... on ${network.name}"
                )
                Result.Success(response.result)
            } else {
                Log.w("SolanaRepo", "Transaction $signature not found")
                Result.Error("Transaction not found on ${network.name}")
            }
        } catch (e: Exception) {
            Log.e("SolanaRepo", "Failed to get transaction details: ${e.message}", e)
            Result.Error("Failed to get transaction details on ${network.name}: ${e.message}", e)
        }
    }

    suspend fun getFullTransactionHistory(
        address: String,
        network: SolanaNetwork = SolanaNetwork.DEVNET,
        limit: Int = DEFAULT_HISTORY_LIMIT
    ): Result<List<Pair<SolanaTransactionResponse, SolanaTransactionDetailsResponse?>>> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(
                    "SolanaRepo",
                    "Fetching full transaction history for $address on ${network.name}"
                )

                val signaturesResult = getTransactionSignatures(address, network, limit)

                when (signaturesResult) {
                    is Result.Success -> {
                        val signatures = signaturesResult.data
                        val results =
                            mutableListOf<Pair<SolanaTransactionResponse, SolanaTransactionDetailsResponse?>>()

                        signatures.forEach { sigInfo ->
                            val details = try {
                                val detailsResult =
                                    getTransactionDetails(sigInfo.signature, network)
                                if (detailsResult is Result.Success) {
                                    detailsResult.data
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                            results.add(Pair(sigInfo, details))
                            delay(RATE_LIMIT_DELAY_MS)
                        }

                        Result.Success(results)
                    }

                    is Result.Error -> signaturesResult
                    else -> Result.Error("Unknown error on ${network.name}")
                }
            } catch (e: Exception) {
                Result.Error(
                    "Failed to get full transaction history on ${network.name}: ${e.message}",
                    e
                )
            }
        }

    fun parseTransferFromDetails(
        details: SolanaTransactionDetailsResponse,
        walletAddress: String
    ): TransferInfo? {
        try {
            val meta = details.meta ?: return null
            val accountKeys = details.transaction.message.accountKeys

            val walletIndex = accountKeys.indexOfFirst { it == walletAddress }
            if (walletIndex < 0) return null

            if (meta.err != null) return null

            val preBalance = meta.preBalances.getOrNull(walletIndex) ?: 0
            val postBalance = meta.postBalances.getOrNull(walletIndex) ?: 0
            val balanceChange = postBalance - preBalance

            if (balanceChange == 0L) return null

            val isIncoming = balanceChange > 0
            val amount = kotlin.math.abs(balanceChange)

            var counterparty = ""
            accountKeys.forEachIndexed { index, key ->
                if (index != walletIndex) {
                    val otherPre = meta.preBalances.getOrNull(index) ?: 0
                    val otherPost = meta.postBalances.getOrNull(index) ?: 0
                    val otherChange = otherPost - otherPre

                    if (otherChange == -balanceChange ||
                        (otherChange < 0 && otherChange > -balanceChange - DUST_THRESHOLD)
                    ) {
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

    fun validateAddress(address: String): Result<Boolean> {
        return try {
            PublicKey(address)
            Result.Success(true)
        } catch (e: Exception) {
            Result.Success(false)
        }
    }

    companion object {
        // Solana constants
        private const val LAMPORTS_PER_SOL = 1_000_000_000L
        private const val SOLANA_FIXED_FEE_LAMPORTS = 5000L
        private const val DEFAULT_COMPUTE_UNITS = 1_400_000
        private const val SOL_DECIMALS = 9

        // Rate limiting
        private const val RATE_LIMIT_DELAY_MS = 50L

        // Dust threshold for counterparty detection (10,000 lamports = 0.00001 SOL)
        private const val DUST_THRESHOLD = 10_000L

        // Default limits
        private const val DEFAULT_SIGNATURE_LIMIT = 100
        private const val DEFAULT_HISTORY_LIMIT = 50
    }
}