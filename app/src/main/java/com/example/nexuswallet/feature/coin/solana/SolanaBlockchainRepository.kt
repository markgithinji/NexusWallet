package com.example.nexuswallet.feature.coin.solana

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.sol4k.*
import org.sol4k.Connection
import org.sol4k.instruction.TransferInstruction
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.Any
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Double
import kotlin.Exception
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.collections.map
import kotlin.let
import kotlin.text.take
import kotlin.to
import com.example.nexuswallet.feature.coin.Result
import javax.inject.Named
@Singleton
class SolanaBlockchainRepository @Inject constructor(
    @param:Named("solanaDevnet") private val devnetConnection: Connection,
    @param:Named("solanaMainnet") private val mainnetConnection: Connection
) {

    companion object {
        private const val LAMPORTS_PER_SOL = 1_000_000_000L
        private const val SOLANA_FIXED_FEE_LAMPORTS = 5000L
        private const val DEFAULT_COMPUTE_UNITS = 1_400_000
    }

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
                Log.d("SolanaRepo", "Got blockhash: ${blockhash.take(16)}...")
                Result.Success(blockhash)
            }
        } catch (e: Exception) {
            Log.e("SolanaRepo", "Error getting blockhash: ${e.message}", e)
            Result.Error("Failed to get recent blockhash: ${e.message}", e)
        }
    }

    suspend fun getBalance(
        address: String,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<BigDecimal> {
        return try {
            Log.d("SolanaRepo", "getBalance START for address: $address on $network")

            withContext(Dispatchers.IO) {
                val connection = getConnection(network)
                val publicKey = PublicKey(address)
                val balance = connection.getBalance(publicKey)

                val solBalance = BigDecimal(balance).divide(
                    BigDecimal(LAMPORTS_PER_SOL),
                    9,
                    RoundingMode.HALF_UP
                )

                Log.d("SolanaRepo", "Balance for $address: $solBalance SOL")
                Result.Success(solBalance)
            }
        } catch (e: Exception) {
            Log.e("SolanaRepo", "Error getting balance: ${e.message}", e)
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
            // Solana has fixed fees for now, but you could make them dynamic
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
            Log.e("SolanaRepo", "Error getting fee estimate: ${e.message}", e)
            Result.Error("Failed to get fee estimate: ${e.message}", e)
        }
    }

    suspend fun createAndSignTransaction(
        fromKeypair: Keypair,
        toAddress: String,
        lamports: Long,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<SolanaSignedTransaction> {
        return try {
            Log.d("SolanaRepo", "Creating transaction: $lamports lamports to $toAddress")

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

            val signature = if (serializedTx.size >= 64) {
                serializedTx.copyOfRange(0, 64)
            } else {
                val hash = MessageDigest.getInstance("SHA-256").digest(serializedTx)
                hash.copyOf(64)
            }

            Log.d("SolanaRepo", "Transaction signed successfully")

            Result.Success(
                SolanaSignedTransaction(
                    signature = signature,
                    serialize = { serializedTx }
                )
            )

        } catch (e: Exception) {
            Log.e("SolanaRepo", "Error creating transaction: ${e.message}", e)
            Result.Error("Failed to create and sign transaction: ${e.message}", e)
        }
    }

    suspend fun broadcastTransaction(
        signedTransaction: SolanaSignedTransaction,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<BroadcastResult> {
        return try {
            Log.d("SolanaRepo", "Broadcasting transaction...")

            val connection = getConnection(network)
            val serializedTx = signedTransaction.serialize()
            val signature = connection.sendTransaction(serializedTx)

            Log.d("SolanaRepo", "Transaction broadcast successful: $signature")

            Result.Success(
                BroadcastResult(
                    success = true,
                    hash = signature
                )
            )

        } catch (e: Exception) {
            Log.e("SolanaRepo", "Error broadcasting: ${e.message}", e)
            Result.Success(
                BroadcastResult(
                    success = false,
                    error = e.message ?: "Broadcast failed"
                )
            )
        }
    }

    suspend fun requestAirdrop(
        address: String,
        amountSol: Double = 1.0,
        network: SolanaNetwork = SolanaNetwork.DEVNET
    ): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                val connection = getConnection(network)
                val publicKey = PublicKey(address)
                val lamports = (amountSol * LAMPORTS_PER_SOL).toLong()
                val signature = connection.requestAirdrop(publicKey, lamports)
                Log.d("SolanaRepo", "Airdrop successful: $signature")
                Result.Success(signature)
            }
        } catch (e: Exception) {
            Result.Error("Airdrop failed: ${e.message}", e)
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

    data class SolanaTransaction(
        val signature: String,
        val slot: Long,
        val timestamp: Long,
        val status: TransactionStatus,
        val memo: String? = null,
        val error: String? = null
    )

    data class SolanaSignedTransaction(
        val signature: ByteArray,
        val serialize: () -> ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SolanaSignedTransaction
            return signature.contentEquals(other.signature)
        }

        override fun hashCode(): Int {
            return signature.contentHashCode()
        }
    }
}