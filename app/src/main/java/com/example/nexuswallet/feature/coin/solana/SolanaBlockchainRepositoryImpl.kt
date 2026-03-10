package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SafeApiCall
import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.logging.Logger
import kotlinx.coroutines.Dispatchers
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
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

@Singleton
class SolanaBlockchainRepositoryImpl @Inject constructor(
    @param:Named("heliusRpcDevnet") private val rpcDevnetConnection: Connection,
    @param:Named("heliusRpcMainnet") private val rpcMainnetConnection: Connection,
    private val heliusApi: HeliusApi,
    private val apiKey: String,
    private val logger: Logger
) : SolanaBlockchainRepository {

    private val tag = "SolanaBlockchainRepo"

    private fun getRpcConnection(network: SolanaNetwork): Connection {
        return when (network) {
            SolanaNetwork.Mainnet -> rpcMainnetConnection
            SolanaNetwork.Devnet -> rpcDevnetConnection
        }
    }

    override suspend fun getRecentBlockhash(network: SolanaNetwork): Result<String> =
        withContext(Dispatchers.IO) {
            logger.d(tag, "getRecentBlockhash called for network: $network")
            SafeApiCall.make {
                val connection = getRpcConnection(network)
                val blockhash = connection.getLatestBlockhash()
                logger.d(tag, "getRecentBlockhash success: $blockhash")
                blockhash
            }
        }

    override suspend fun getBalance(
        address: String,
        network: SolanaNetwork
    ): Result<BigDecimal> = withContext(Dispatchers.IO) {
        logger.d(tag, "getBalance called for address: ${address.take(8)}..., network: $network")
        SafeApiCall.make {
            val connection = getRpcConnection(network)
            val publicKey = PublicKey(address)
            val balance = connection.getBalance(publicKey)

            val balanceSol = BigDecimal(balance).divide(
                BigDecimal(LAMPORTS_PER_SOL),
                SOL_DECIMALS,
                RoundingMode.HALF_UP
            )

            logger.d(tag, "getBalance success: ${balance} lamports (${balanceSol} SOL)")
            balanceSol
        }
    }

    override suspend fun getTokenBalance(
        address: String,
        mintAddress: String,
        network: SolanaNetwork
    ): Result<BigDecimal> = withContext(Dispatchers.IO) {
        logger.d(tag, "getTokenBalance called for address: ${address.take(8)}..., mint: ${mintAddress.take(8)}..., network: $network")
        SafeApiCall.make {
            val connection = getRpcConnection(network)

            val owner = PublicKey(address)
            val mint = PublicKey(mintAddress)

            val (associatedTokenAccount, _) = PublicKey.findProgramDerivedAddress(
                holderAddress = owner,
                tokenMintAddress = mint
            )

            logger.d(tag, "Associated token account: ${associatedTokenAccount.toBase58()}")

            val tokenBalance = connection.getTokenAccountBalance(associatedTokenAccount)

            val balance = BigDecimal(tokenBalance.amount).divide(
                BigDecimal.TEN.pow(tokenBalance.decimals),
                tokenBalance.decimals,
                RoundingMode.HALF_UP
            )

            logger.d(tag, "getTokenBalance success: ${tokenBalance.amount} (${balance} tokens)")
            balance
        }
    }

    override suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        network: SolanaNetwork
    ): Result<SolanaFeeEstimate> = withContext(Dispatchers.IO) {
        logger.d(tag, "getFeeEstimate called for feeLevel: $feeLevel, network: $network")
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

            logger.d(tag, "getFeeEstimate success - base: $baseFeeLamports, priority: $priorityFeeRate, total: $totalFeeLamports lamports ($totalFeeSol SOL)")

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
        logger.d(tag, "getRecommendedPriorityFee called for percentile: $percentile, accounts: ${accounts.size}")
        SafeApiCall.make {
            val connection = getRpcConnection(network)

            val recentFees = if (accounts.isNotEmpty()) {
                connection.getRecentPrioritizationFees(accounts)
            } else {
                connection.getRecentPrioritizationFees(emptyList())
            }

            logger.d(tag, "getRecentPrioritizationFees returned ${recentFees.size} fees")

            if (recentFees.isEmpty()) {
                logger.d(tag, "No recent fees, returning 0")
                return@make 0
            }

            val feeValues = recentFees.map { it.prioritizationFee }.sorted()
            val index = (feeValues.size * percentile / 100).coerceIn(0, feeValues.size - 1)
            val recommendedFee = feeValues[index].toInt()

            logger.d(tag, "Recommended priority fee: $recommendedFee (index $index of ${feeValues.size})")
            recommendedFee
        }
    }

    override suspend fun createAndSignTransaction(
        fromKeypair: Keypair,
        toAddress: String,
        lamports: Long,
        network: SolanaNetwork
    ): Result<SolanaSignedTransaction> = withContext(Dispatchers.IO) {
        logger.d(tag, "createAndSignTransaction called - from: ${fromKeypair.publicKey.toBase58().take(8)}..., to: ${toAddress.take(8)}..., lamports: $lamports")
        SafeApiCall.make {
            val connection = getRpcConnection(network)
            val blockhash = connection.getLatestBlockhash()
            val receiver = PublicKey(toAddress)

            logger.d(tag, "Got blockhash: $blockhash")

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
                Base58.encode(serializedTx.copyOfRange(0, 64))
            } else {
                val hash = MessageDigest.getInstance("SHA-256").digest(serializedTx)
                Base58.encode(hash)
            }

            logger.d(tag, "Transaction created with signature: ${signature.take(8)}...")

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
        logger.d(tag, "broadcastTransaction called - signature: ${signedTransaction.signature.take(8)}...")
        SafeApiCall.make {
            val connection = getRpcConnection(network)
            val serializedTx = signedTransaction.serialize()
            val signature = connection.sendTransaction(serializedTx)

            logger.d(tag, "Transaction broadcast successful - signature: $signature")

            BroadcastResult(
                success = true,
                hash = signature
            )
        }
    }

    // ============= HELIUS API (RETROFIT) =============

    override suspend fun getTransactions(
        address: String,
        network: SolanaNetwork,
        limit: Int
    ): Result<List<HeliusTransaction>> = withContext(Dispatchers.IO) {
        logger.d(tag, "=== getTransactions called ===")
        logger.d(tag, "Address: ${address.take(8)}..., Network: $network, Limit: $limit")

        SafeApiCall.make {
            val transactions = heliusApi.getTransactions(
                address = address,
                limit = limit,
                apiKey = apiKey
            )

            logger.d(tag, "Found ${transactions.size} transactions from Helius")
            transactions
        }
    }

    override suspend fun getTransaction(
        signature: String,
        network: SolanaNetwork
    ): Result<HeliusTransaction> = withContext(Dispatchers.IO) {
        logger.d(tag, "=== getTransaction called ===")
        logger.d(tag, "Signature: ${signature.take(8)}..., Network: $network")

        SafeApiCall.make {
            val request = HeliusTransactionRequest(transactions = listOf(signature))
            val transactions = heliusApi.getTransaction(
                request = request,
                apiKey = apiKey
            )

            transactions.firstOrNull() ?: throw Exception("Transaction not found")
        }
    }

    override fun parseTransfer(
        transaction: HeliusTransaction,
        walletAddress: String
    ): TransferInfo? {
        logger.d(tag, "=== parseTransfer called ===")
        logger.d(tag, "Wallet address: ${walletAddress.take(8)}...")
        logger.d(tag, "Transaction type: ${transaction.type}")
        logger.d(tag, "Description: ${transaction.description}")

        return try {
            val nativeTransfer = transaction.nativeTransfers.find {
                it.fromUserAccount == walletAddress || it.toUserAccount == walletAddress
            }

            if (nativeTransfer != null) {
                val isIncoming = nativeTransfer.toUserAccount == walletAddress
                val amount = nativeTransfer.amount
                val amountSol = amount.toDouble() / LAMPORTS_PER_SOL

                logger.d(tag, " Found native transfer:")
                logger.d(tag, "  isIncoming: $isIncoming")
                logger.d(tag, "  amount: $amount lamports ($amountSol SOL)")
                logger.d(tag, "  from: ${nativeTransfer.fromUserAccount.take(8)}...")
                logger.d(tag, "  to: ${nativeTransfer.toUserAccount.take(8)}...")

                return TransferInfo(
                    from = nativeTransfer.fromUserAccount,
                    to = nativeTransfer.toUserAccount,
                    amount = amount,
                    isIncoming = isIncoming,
                    fee = transaction.fee
                )
            }

            logger.d(tag, "️ No native transfer found for wallet")
            null

        } catch (e: Exception) {
            logger.e(tag, " Error parsing transfer", e)
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
    }
}

data class SolanaSignedTransaction(
    val signature: String,
    val serialize: () -> ByteArray
)