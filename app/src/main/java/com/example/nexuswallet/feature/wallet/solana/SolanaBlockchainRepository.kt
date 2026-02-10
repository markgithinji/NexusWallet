package com.example.nexuswallet.feature.wallet.solana

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
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
import kotlin.Result
import kotlin.String
import kotlin.collections.map
import kotlin.let
import kotlin.text.take
import kotlin.to

@Singleton
class SolanaBlockchainRepository @Inject constructor() {

    companion object {
        private const val LAMPORTS_PER_SOL = 1_000_000_000L
        private const val SOLANA_FIXED_FEE_LAMPORTS = 5000L // ~0.000005 SOL
    }

    // Use sol4k Connection for RPC calls
    private val connection = Connection("https://api.devnet.solana.com")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get recent blockhash for transaction creation
     */
    suspend fun getRecentBlockhash(): String {
        return try {
            withContext(Dispatchers.IO) {
                val blockhashObj = connection.getLatestBlockhash()
                Log.d("SolanaRepo", "Got blockhash object")

                val blockhash = blockhashObj
                Log.d("SolanaRepo", "Got blockhash: ${blockhash.take(16)}...")
                blockhash
            }
        } catch (e: Exception) {
            Log.e("SolanaRepo", "Error getting blockhash: ${e.message}")
            throw e
        }
    }
    /**
     * Get SOL balance
     */
    suspend fun getBalance(address: String): BigDecimal {
        return try {
            Log.d("SolanaRepo", " getBalance START for address: $address")

            withContext(Dispatchers.IO) {
                val publicKey = PublicKey(address)
                Log.d("SolanaRepo", "Created PublicKey object")

                val balance = connection.getBalance(publicKey)
                Log.d("SolanaRepo", "Raw balance from RPC: ${balance} lamports")

                val solBalance = BigDecimal(balance).divide(
                    BigDecimal(LAMPORTS_PER_SOL),
                    9,
                    RoundingMode.HALF_UP
                )

                Log.d("SolanaRepo", " Balance for $address: $solBalance SOL")
                solBalance
            }
        } catch (e: Exception) {
            Log.e("SolanaRepo", " Error getting balance: ${e.message}", e)
            BigDecimal.ZERO
        }
    }

    /**
     * Get fee estimate (Solana has fixed fees)
     */
    suspend fun getFeeEstimate(feeLevel: FeeLevel = FeeLevel.NORMAL): FeeEstimate {
        // Solana has fixed fees, ignore feeLevel for now
        val feeDecimal = BigDecimal(SOLANA_FIXED_FEE_LAMPORTS).divide(
            BigDecimal(LAMPORTS_PER_SOL),
            9,
            RoundingMode.HALF_UP
        )

        return FeeEstimate(
            feePerByte = null,
            gasPrice = null,
            totalFee = SOLANA_FIXED_FEE_LAMPORTS.toString(),
            totalFeeDecimal = feeDecimal.toPlainString(),
            estimatedTime = 1, // Solana confirms in ~400ms
            priority = feeLevel,
            metadata = mapOf("computeUnits" to "1400000") // Default compute units
        )
    }

    /**
     * Create and sign a SOL transfer transaction using sol4k
     */
    suspend fun createAndSignTransaction(
        fromKeypair: Keypair,
        toAddress: String,
        lamports: Long
    ): SolanaSignedTransaction {
        return try {
            Log.d("SolanaRepo", "Creating transaction: $lamports lamports to $toAddress")

            // 1. Get recent blockhash
            val blockhash = connection.getLatestBlockhash()

            // 2. Create transfer instruction
            val receiver = PublicKey(toAddress)
            val instruction = TransferInstruction(fromKeypair.publicKey, receiver, lamports)

            // 3. Create transaction message
            val message = TransactionMessage.newMessage(
                feePayer = fromKeypair.publicKey,
                recentBlockhash = blockhash,
                instructions = listOf(instruction)
            )

            // 4. Create and sign versioned transaction
            val transaction = VersionedTransaction(message)
            transaction.sign(fromKeypair)

            // 5. Serialize the transaction
            val serializedTx = transaction.serialize()

            // 6. Extract signature from serialized transaction
            // In Solana serialized format, the signature is the first 64 bytes
            val signature = if (serializedTx.size >= 64) {
                serializedTx.copyOfRange(0, 64)
            } else {
                // Fallback: create a mock signature from transaction hash
                val hash = MessageDigest.getInstance("SHA-256").digest(serializedTx)
                hash.copyOf(64)
            }

            Log.d("SolanaRepo", "Transaction signed successfully")

            SolanaSignedTransaction(
                signature = signature,
                serialize = { serializedTx }
            )

        } catch (e: Exception) {
            Log.e("SolanaRepo", "Error creating transaction: ${e.message}", e)
            throw e
        }
    }

    /**
     * Broadcast transaction to devnet using sol4k
     */
    suspend fun broadcastTransaction(signedTransaction: SolanaSignedTransaction): BroadcastResult {
        return try {
            Log.d("SolanaRepo", "Broadcasting transaction...")

            // Get the serialized transaction
            val serializedTx = signedTransaction.serialize()

            // Send transaction using sol4k
            val signature = connection.sendTransaction(serializedTx)

            Log.d("SolanaRepo", "Transaction broadcast successful: $signature")

            BroadcastResult(
                success = true,
                hash = signature,
                chain = ChainType.SOLANA
            )

        } catch (e: Exception) {
            Log.e("SolanaRepo", "Error broadcasting: ${e.message}", e)

            BroadcastResult(
                success = false,
                error = e.message ?: "Broadcast failed",
                chain = ChainType.SOLANA
            )
        }
    }

    /**
     * Request airdrop (free test SOL) from devnet faucet
     */
    suspend fun requestAirdrop(address: String, amountSol: Double = 1.0): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                val publicKey = PublicKey(address)
                val lamports = (amountSol * LAMPORTS_PER_SOL).toLong()
                val signature = connection.requestAirdrop(publicKey, lamports)
                Log.d("SolanaRepo", "Airdrop successful: $signature")
                Result.success(signature)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get transaction history for an address
     */
    suspend fun getTransactionHistory(address: String, limit: Int = 10): List<SolanaTransaction> {
        return try {
            withContext(Dispatchers.IO) {
                val publicKey = PublicKey(address)
                val signatures = connection.getSignaturesForAddress(publicKey, limit)

                signatures.map { signatureInfo ->
                    SolanaTransaction(
                        signature = signatureInfo.signature,
                        slot = signatureInfo.slot,
                        timestamp = signatureInfo.blockTime?.let { it * 1000L } ?: 0L,
                        status = if (signatureInfo.isError) TransactionStatus.SUCCESS
                        else TransactionStatus.FAILED,
                        memo = null,
                        error = signatureInfo.toString()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaRepo", "Error fetching history: ${e.message}")
            emptyList()
        }
    }

    /**
     * Validate Solana address format
     */
    fun validateAddress(address: String): Boolean {
        return try {
            PublicKey(address)
            true
        } catch (e: Exception) {
            false
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