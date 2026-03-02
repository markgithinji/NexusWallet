package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SafeApiCall
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.sol4k.Base58
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
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork

@Singleton
class SolanaBlockchainRepositoryImpl @Inject constructor(
    @param:Named("solanaDevnet") private val devnetConnection: Connection,
    @param:Named("solanaMainnet") private val mainnetConnection: Connection,
    private val solanaRpcService: SolanaRpcService
) : SolanaBlockchainRepository {

    private fun getConnection(network: SolanaNetwork): Connection {
        return when (network) {
            SolanaNetwork.Mainnet -> mainnetConnection
            SolanaNetwork.Devnet -> devnetConnection
        }
    }

    override suspend fun getRecentBlockhash(network: SolanaNetwork): Result<String> =
        withContext(Dispatchers.IO) {
            SafeApiCall.make {
                val connection = getConnection(network)
                connection.getLatestBlockhash()
            }
        }

    override suspend fun getBalance(
        address: String,
        network: SolanaNetwork
    ): Result<BigDecimal> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val connection = getConnection(network)
            val publicKey = PublicKey(address)
            val balance = connection.getBalance(publicKey)

            BigDecimal(balance).divide(
                BigDecimal(LAMPORTS_PER_SOL),
                SOL_DECIMALS,
                RoundingMode.HALF_UP
            )
        }
    }

    override suspend fun getTokenBalance(
        address: String,
        mintAddress: String,
        network: SolanaNetwork
    ): Result<BigDecimal> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val connection = getConnection(network)

            // Get the associated token account address
            val owner = PublicKey(address)
            val mint = PublicKey(mintAddress)

            // Find the associated token account
            val (associatedTokenAccount, _) = PublicKey.findProgramDerivedAddress(
                owner,
                mint
            )

            // Get token account balance using RPC
            val tokenBalance = connection.getTokenAccountBalance(associatedTokenAccount)

            BigDecimal(tokenBalance.amount).divide(
                BigDecimal.TEN.pow(tokenBalance.decimals),
                tokenBalance.decimals,
                RoundingMode.HALF_UP
            )
        }
    }

    private data class TokenAccountData(
        val mint: PublicKey,
        val amount: Long
    )

    override suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        network: SolanaNetwork
    ): Result<SolanaFeeEstimate> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
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

            SolanaFeeEstimate(
                feeLamports = totalFeeLamports,
                feeSol = totalFeeSol,
                estimatedTime = estimatedTime,
                priority = feeLevel,
                computeUnits = computeUnits
            )
        }
    }

    private suspend fun getRecommendedPriorityFee(
        percentile: Int,
        accounts: List<PublicKey> = emptyList(),
        network: SolanaNetwork
    ): Result<Int> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val connection = getConnection(network)

            val recentFees = if (accounts.isNotEmpty()) {
                connection.getRecentPrioritizationFees(accounts)
            } else {
                connection.getRecentPrioritizationFees(emptyList())
            }

            if (recentFees.isEmpty()) {
                return@make 0
            }

            val feeValues = recentFees.map { it.prioritizationFee }.sorted()
            val index = (feeValues.size * percentile / 100).coerceIn(0, feeValues.size - 1)
            feeValues[index].toInt()
        }
    }

    override suspend fun createAndSignTransaction(
        fromKeypair: Keypair,
        toAddress: String,
        lamports: Long,
        network: SolanaNetwork
    ): Result<SolanaSignedTransaction> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
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

            SolanaSignedTransaction(
                signature = signature,
                serialize = { serializedTx }
            )
        }
    }

    override suspend fun broadcastTransaction(
        signedTransaction: SolanaSignedTransaction,
        network: SolanaNetwork
    ): Result<BroadcastResult> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val connection = getConnection(network)
            val serializedTx = signedTransaction.serialize()
            val signature = connection.sendTransaction(serializedTx)

            BroadcastResult(
                success = true,
                hash = signature
            )
        }
    }

    override suspend fun getTransactionSignatures(
        address: String,
        network: SolanaNetwork,
        limit: Int
    ): Result<List<SolanaTransactionResponse>> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val connection = getConnection(network)
            val publicKey = PublicKey(address)

            val signatures = connection.getSignaturesForAddress(
                publicKey,
                limit = limit
            )

            signatures.map { sigInfo ->
                SolanaTransactionResponse(
                    signature = sigInfo.signature,
                    slot = sigInfo.slot,
                    blockTime = sigInfo.blockTime,
                    confirmationStatus = sigInfo.confirmationStatus
                )
            }
        }
    }

    override suspend fun getTransactionDetails(
        signature: String,
        network: SolanaNetwork
    ): Result<SolanaTransactionDetailsResponse> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
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
                throw Exception("RPC error: ${response.error.message}")
            } else if (response.result != null) {
                response.result
            } else {
                throw Exception("Transaction not found on ${network.name}")
            }
        }
    }

    override suspend fun getFullTransactionHistory(
        address: String,
        network: SolanaNetwork,
        limit: Int
    ): Result<List<Pair<SolanaTransactionResponse, SolanaTransactionDetailsResponse?>>> =
        withContext(Dispatchers.IO) {
            SafeApiCall.make {
                val signaturesResult = getTransactionSignatures(address, network, limit)

                when (signaturesResult) {
                    is Result.Success -> {
                        val signatures = signaturesResult.data
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
                            delay(RATE_LIMIT_DELAY_MS)
                        }

                        results
                    }
                    is Result.Error -> throw Exception(signaturesResult.message)
                    else -> throw Exception("Unknown error on ${network.name}")
                }
            }
        }

    override fun parseTransferFromDetails(
        details: SolanaTransactionDetailsResponse,
        walletAddress: String
    ): TransferInfo? {
        return try {
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

            TransferInfo(
                from = if (isIncoming) counterparty else walletAddress,
                to = if (isIncoming) walletAddress else counterparty,
                amount = amount,
                isIncoming = isIncoming,
                fee = meta.fee
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun validateAddress(address: String): Result<Boolean> {
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
        private const val SOL_DECIMALS = 9
        private const val RATE_LIMIT_DELAY_MS = 50L
        private const val DUST_THRESHOLD = 10_000L
    }
}

data class SolanaSignedTransaction(
    val signature: String,
    val serialize: () -> ByteArray
)