package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import kotlinx.coroutines.Dispatchers
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
    @param:Named("solanaMainnet") private val mainnetConnection: Connection
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