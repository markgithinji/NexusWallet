package com.example.nexuswallet.feature.solana.data.repository

import com.example.nexuswallet.feature.core.domain.model.BroadcastResult
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.SafeApiCall
import com.example.nexuswallet.feature.solana.data.model.SolanaSignedTransaction
import com.example.nexuswallet.feature.solana.data.remote.HeliusTransactionRequest
import com.example.nexuswallet.feature.solana.data.remote.HeliusTransactionResponse
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.solana.util.SolanaConstants.LAMPORTS_PER_SOL
import com.example.nexuswallet.feature.wallet.domain.SolanaNetwork
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

@Singleton
class SolanaBlockchainRepositoryImpl @Inject constructor(
    @param:Named("heliusRpcDevnet") private val rpcDevnetConnection: Connection,
    @param:Named("heliusRpcMainnet") private val rpcMainnetConnection: Connection,
    private val heliusApi: com.example.nexuswallet.feature.solana.data.remote.HeliusApi
) : com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository {

    private fun getRpcConnection(network: SolanaNetwork): Connection {
        return when (network) {
            SolanaNetwork.Mainnet -> rpcMainnetConnection
            SolanaNetwork.Devnet -> rpcDevnetConnection
        }
    }

    override suspend fun getRecentBlockhash(network: SolanaNetwork): Result<String> =
        withContext(Dispatchers.IO) {
            SafeApiCall.make {
                val connection = getRpcConnection(network)
                val blockhash = connection.getLatestBlockhash()
                blockhash
            }
        }

    override suspend fun getBalance(
        address: String,
        network: SolanaNetwork
    ): Result<BigDecimal> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val connection = getRpcConnection(network)
            val publicKey = PublicKey(address)
            val balance = connection.getBalance(publicKey)

            val balanceSol = BigDecimal(balance).divide(
                BigDecimal(LAMPORTS_PER_SOL),
                SOL_DECIMALS,
                RoundingMode.HALF_UP
            )
            balanceSol
        }
    }

    override suspend fun getTokenBalance(
        address: String,
        mintAddress: String,
        network: SolanaNetwork
    ): Result<BigDecimal> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val connection = getRpcConnection(network)

            val owner = PublicKey(address)
            val mint = PublicKey(mintAddress)

            val (associatedTokenAccount, _) = PublicKey.findProgramDerivedAddress(
                holderAddress = owner,
                tokenMintAddress = mint
            )

            val tokenBalance = connection.getTokenAccountBalance(associatedTokenAccount)

            val balance = BigDecimal(tokenBalance.amount).divide(
                BigDecimal.TEN.pow(tokenBalance.decimals),
                tokenBalance.decimals,
                RoundingMode.HALF_UP
            )
            balance
        }
    }

    override suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        network: SolanaNetwork
    ): Result<SolanaFeeEstimate> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val baseFeeLamports = SOLANA_FIXED_FEE_LAMPORTS

            val percentile = when (feeLevel) {
                FeeLevel.SLOW -> SLOW_PERCENTILE
                FeeLevel.NORMAL -> NORMAL_PERCENTILE
                FeeLevel.FAST -> FAST_PERCENTILE
            }

            val priorityFeeRate = if (percentile > 0) {
                val result = getRecommendedPriorityFee(percentile, emptyList(), network)
                if (result is Result.Success) result.data else DEFAULT_PRIORITY_FEE
            } else DEFAULT_PRIORITY_FEE

            val computeUnits = when (feeLevel) {
                FeeLevel.SLOW -> SLOW_COMPUTE_UNITS
                FeeLevel.NORMAL -> NORMAL_COMPUTE_UNITS
                FeeLevel.FAST -> FAST_COMPUTE_UNITS
            }

            val priorityFeeLamports =
                (priorityFeeRate.toLong() * computeUnits) / MICRO_LAMPORTS_PER_LAMPORT
            val totalFeeLamports = baseFeeLamports + priorityFeeLamports

            val totalFeeSol = BigDecimal(totalFeeLamports).divide(
                BigDecimal(LAMPORTS_PER_SOL),
                SOL_DECIMALS,
                RoundingMode.HALF_UP
            ).toPlainString()

            val estimatedTime = when (feeLevel) {
                FeeLevel.SLOW -> SLOW_ESTIMATED_TIME_SECONDS
                FeeLevel.NORMAL -> NORMAL_ESTIMATED_TIME_SECONDS
                FeeLevel.FAST -> FAST_ESTIMATED_TIME_SECONDS
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
            val connection = getRpcConnection(network)

            val recentFees = if (accounts.isNotEmpty()) {
                connection.getRecentPrioritizationFees(accounts)
            } else {
                connection.getRecentPrioritizationFees(emptyList())
            }

            if (recentFees.isEmpty()) {
                return@make DEFAULT_PRIORITY_FEE
            }

            val feeValues = recentFees.map { it.prioritizationFee }.sorted()
            val index = (feeValues.size * percentile / PERCENTILE_DIVISOR).coerceIn(
                COERCE_MIN,
                feeValues.size - 1
            )
            val recommendedFee = feeValues[index].toInt()

            recommendedFee
        }
    }

    override suspend fun createAndSignTransaction(
        fromKeypair: Keypair,
        toAddress: String,
        lamports: Long,
        network: SolanaNetwork
    ): Result<SolanaSignedTransaction> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val connection = getRpcConnection(network)
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

            val signature = if (serializedTx.size >= SIGNATURE_SIZE) {
                Base58.encode(serializedTx.copyOfRange(0, SIGNATURE_SIZE))
            } else {
                val hash = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(serializedTx)
                Base58.encode(hash)
            }

            SolanaSignedTransaction(
                signature = signature,
                serialize = { serializedTx }
            )
        }
    }

    override suspend fun broadcastTransaction(
        signedTransaction: com.example.nexuswallet.feature.solana.data.model.SolanaSignedTransaction,
        network: SolanaNetwork
    ): Result<BroadcastResult> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val connection = getRpcConnection(network)
            val serializedTx = signedTransaction.serialize()
            val signature = connection.sendTransaction(serializedTx)

            BroadcastResult(
                success = true,
                hash = signature
            )
        }
    }

    override suspend fun getTransactions(
        address: String,
        network: SolanaNetwork,
        limit: Int
    ): Result<List<HeliusTransactionResponse>> = withContext(Dispatchers.IO) {

        SafeApiCall.make {
            val transactions = heliusApi.getTransactions(
                address = address,
                limit = limit
            )
            transactions
        }
    }

    override suspend fun getTransaction(
        signature: String,
        network: SolanaNetwork
    ): Result<HeliusTransactionResponse> = withContext(Dispatchers.IO) {

        SafeApiCall.make {
            val request =
                HeliusTransactionRequest(
                    transactions = listOf(signature)
                )
            val transactions = heliusApi.getTransaction(
                request = request
            )

            transactions.firstOrNull() ?: throw Exception("Transaction not found")
        }
    }

    override fun parseTransfer(
        transaction: com.example.nexuswallet.feature.solana.data.remote.HeliusTransactionResponse,
        walletAddress: String
    ): com.example.nexuswallet.feature.solana.domain.model.TransferInfo? {

        return try {
            val nativeTransfer = transaction.nativeTransfers.find {
                it.fromUserAccount == walletAddress || it.toUserAccount == walletAddress
            }

            if (nativeTransfer != null) {
                val isIncoming = nativeTransfer.toUserAccount == walletAddress
                val amount = nativeTransfer.amount

                return _root_ide_package_.com.example.nexuswallet.feature.solana.domain.model.TransferInfo(
                    from = nativeTransfer.fromUserAccount,
                    to = nativeTransfer.toUserAccount,
                    amount = amount,
                    isIncoming = isIncoming,
                    fee = transaction.fee
                )
            }
            null

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
        private const val SOLANA_FIXED_FEE_LAMPORTS = 5000L
        private const val SOL_DECIMALS = 9
        private const val MICRO_LAMPORTS_PER_LAMPORT = 1_000_000L

        // Fee estimate constants
        private const val SLOW_PERCENTILE = 0
        private const val NORMAL_PERCENTILE = 50
        private const val FAST_PERCENTILE = 95
        private const val DEFAULT_PRIORITY_FEE = 0
        private const val PERCENTILE_DIVISOR = 100
        private const val COERCE_MIN = 0

        // Compute unit constants
        private const val SLOW_COMPUTE_UNITS = 200_000
        private const val NORMAL_COMPUTE_UNITS = 400_000
        private const val FAST_COMPUTE_UNITS = 800_000

        // Estimated time constants (in seconds)
        private const val SLOW_ESTIMATED_TIME_SECONDS = 2
        private const val NORMAL_ESTIMATED_TIME_SECONDS = 1
        private const val FAST_ESTIMATED_TIME_SECONDS = 1

        // Signature constants
        private const val SIGNATURE_SIZE = 64
        private const val SHA_256_ALGORITHM = "SHA-256"
    }
}