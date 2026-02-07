package com.example.nexuswallet.feature.wallet.data.solana

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sol4k.Keypair
import org.sol4k.PublicKey
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolanaTransactionRepository @Inject constructor(
    private val localDataSource: TransactionLocalDataSource,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val walletRepository: WalletRepository,
    private val keyManager: KeyManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun createSendTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendTransaction> {
        return try {
            val wallet = walletRepository.getWallet(walletId) as? SolanaWallet
                ?: return Result.failure(IllegalArgumentException("Solana wallet not found"))

            createSolanaTransaction(
                wallet = wallet,
                toAddress = toAddress,
                amount = amount,
                feeLevel = feeLevel,
                note = note
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createSolanaTransaction(
        wallet: SolanaWallet,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SendTransaction> {
        Log.d("SolanaTxRepo", " createSolanaTransaction START")
        Log.d("SolanaTxRepo", "Wallet: ${wallet.address}")
        Log.d("SolanaTxRepo", "Amount: $amount SOL to $toAddress")

        return try {
            // 1. Get recent blockhash for display
            Log.d("SolanaTxRepo", "Getting recent blockhash...")
            val blockhash = solanaBlockchainRepository.getRecentBlockhash()
            Log.d("SolanaTxRepo", "Blockhash: ${blockhash.take(16)}...")

            // 2. Get fee estimate (Solana fees are fixed, ~5000 lamports)
            val feeEstimate = solanaBlockchainRepository.getFeeEstimate()
            Log.d("SolanaTxRepo", "Fee estimate: ${feeEstimate.totalFee} lamports")

            // 3. Convert amount to lamports (1 SOL = 1,000,000,000 lamports)
            val lamports = amount.multiply(BigDecimal("1000000000")).toLong()
            Log.d("SolanaTxRepo", "Amount in lamports: $lamports")

            // 4. Create LOCAL transaction record
            val transaction = SendTransaction(
                id = "sol_tx_${System.currentTimeMillis()}",
                walletId = wallet.id,
                walletType = WalletType.SOLANA,
                fromAddress = wallet.address,
                toAddress = toAddress,
                amount = lamports.toString(),
                amountDecimal = amount.toPlainString(),
                fee = feeEstimate.totalFee,
                feeDecimal = feeEstimate.totalFeeDecimal,
                total = (lamports + feeEstimate.totalFee.toLong()).toString(),
                totalDecimal = (amount + BigDecimal(feeEstimate.totalFeeDecimal)).toPlainString(),
                chain = ChainType.SOLANA,
                status = TransactionStatus.PENDING,
                note = note,
                gasPrice = null,
                gasLimit = null,
                signedHex = null,
                nonce = 0,
                hash = null,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                metadata = mapOf(
                    "blockhash" to blockhash,
                    "feePayer" to wallet.address
                )
            )

            // 5. Save to local storage only
            localDataSource.saveSendTransaction(transaction)
            Log.d("SolanaTxRepo", " Saved LOCAL transaction (not signed/broadcasted)")

            Result.success(transaction)

        } catch (e: Exception) {
            Log.e("SolanaTxRepo", " Error in createSolanaTransaction: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signTransaction(transactionId: String): Result<SignedTransaction> {
        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            // Only support Solana
            if (transaction.walletType != WalletType.SOLANA) {
                return Result.failure(IllegalArgumentException("Only Solana signing supported"))
            }

            signSolanaTransactionReal(transactionId)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real Solana signing implementation using sol4k
     */
    private suspend fun signSolanaTransactionReal(
        transactionId: String
    ): Result<SignedTransaction> {
        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            // Get wallet
            val wallet = walletRepository.getWallet(transaction.walletId) as? SolanaWallet
                ?: return Result.failure(IllegalArgumentException("Solana wallet not found"))

            Log.d("SolanaTxRepo", " Signing transaction: ${transaction.id}")

            // 1. Get CURRENT blockhash
            Log.d("SolanaTxRepo", "Getting fresh blockhash from API...")
            val currentBlockhash = solanaBlockchainRepository.getRecentBlockhash()
            Log.d("SolanaTxRepo", "Current blockhash: ${currentBlockhash.take(16)}...")

            // 2. Get fee estimate
            val fee = solanaBlockchainRepository.getFeeEstimate()
            Log.d("SolanaTxRepo", "Fee: ${fee.totalFee} lamports")

            // 3. Get private key
            Log.d("SolanaTxRepo", "Requesting private key...")
            val privateKeyResult = keyManager.getPrivateKeyForSigning(transaction.walletId)

            if (privateKeyResult.isFailure) {
                Log.e("SolanaTxRepo", "Failed to get private key")
                return Result.failure(
                    privateKeyResult.exceptionOrNull() ?: IllegalStateException("No private key")
                )
            }

            val privateKeyHex = privateKeyResult.getOrThrow()
            Log.d("SolanaTxRepo", "✓ Got private key: ${privateKeyHex.take(8)}...")

            // 4. Create sol4k Keypair from private key
            val keypair = createSolanaKeypair(privateKeyHex)
                ?: return Result.failure(IllegalArgumentException("Invalid private key format"))

            // 5. Verify address matches
            val derivedAddress = keypair.publicKey.toString()
            if (derivedAddress != wallet.address) {
                Log.e(
                    "SolanaTxRepo",
                    "Address mismatch! Expected: ${wallet.address}, Got: $derivedAddress"
                )
                return Result.failure(IllegalStateException("Private key doesn't match wallet"))
            }

            // 6. Create and sign transaction with sol4k
            val lamports = transaction.amount.toLongOrNull() ?: 0L

            val signedTx = solanaBlockchainRepository.createAndSignTransaction(
                fromKeypair = keypair,
                toAddress = transaction.toAddress,
                lamports = lamports
            )

            // 7. Get transaction hash (signature in Solana)
            val signatureBytes = signedTx.signature
            val txHash = signatureBytes.toHexString()

            Log.d("SolanaTxRepo", "Signed! Hash: ${txHash.take(16)}...")

            // 8. Create signed transaction
            val signedTransaction = SignedTransaction(
                rawHex = signedTx.serialize().toHexString(),
                hash = txHash,
                chain = transaction.chain
            )

            // 9. Update transaction with signed data
            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                hash = txHash,
                signedHex = signedTransaction.rawHex,
                metadata = transaction.metadata + mapOf(
                    "blockhash" to currentBlockhash,
                    "signature" to txHash,
                    "feePayer" to wallet.address
                )
            )
            localDataSource.saveSendTransaction(updatedTransaction)

            Result.success(signedTransaction)

        } catch (e: Exception) {
            Log.e("SolanaTxRepo", " Signing failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun broadcastTransaction(transactionId: String): Result<BroadcastResult> {
        Log.d("SolanaTxRepo", " ENTERING broadcastTransaction for: $transactionId")

        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: run {
                    Log.e("SolanaTxRepo", " Transaction not found")
                    return Result.failure(IllegalArgumentException("Transaction not found"))
                }

            Log.d(
                "SolanaTxRepo",
                "Transaction found: ${transaction.id}, chain: ${transaction.chain}"
            )

            // Only handle Solana
            when (transaction.chain) {
                ChainType.SOLANA -> {
                    Log.d("SolanaTxRepo", " Using real broadcast for Solana")
                    broadcastTransactionReal(transactionId)
                }

                else -> {
                    Log.w("SolanaTxRepo", "⚠ Chain ${transaction.chain} not supported")
                    Result.success(
                        BroadcastResult(
                            success = false,
                            error = "Chain ${transaction.chain} not implemented",
                            chain = transaction.chain
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaTxRepo", " Broadcast failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun broadcastTransactionReal(transactionId: String): Result<BroadcastResult> {
        Log.d("SolanaBroadcast", "🎬 START broadcastTransactionReal")
        Log.d("SolanaBroadcast", "Transaction ID: $transactionId")

        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: run {
                    Log.e("SolanaBroadcast", " Transaction not found")
                    return Result.failure(IllegalArgumentException("Transaction not found"))
                }

            Log.d("SolanaBroadcast", "Found transaction: ${transaction.id}")
            Log.d("SolanaBroadcast", "Signed hex available: ${transaction.signedHex != null}")

            // 1. Check if transaction is signed
            val signedHex = transaction.signedHex
                ?: run {
                    Log.e("SolanaBroadcast", " Transaction not signed")
                    return Result.failure(IllegalStateException("Transaction not signed"))
                }

            // 2. Create SolanaSignedTransaction object from hex
            val signatureBytes = signedHex.hexToByteArray()
            val solanaSignedTx = SolanaBlockchainRepository.SolanaSignedTransaction(
                signature = signatureBytes.take(64).toByteArray(), // First 64 bytes are signature
                serialize = { signatureBytes }
            )

            // 3. Broadcast to Solana devnet using sol4k
            Log.d("SolanaBroadcast", " Broadcasting transaction to Solana devnet...")
            val broadcastResult = solanaBlockchainRepository.broadcastTransaction(
                solanaSignedTx
            )

            Log.d("SolanaBroadcast", "Broadcast result: success=${broadcastResult.success}")
            Log.d("SolanaBroadcast", "Broadcast hash: ${broadcastResult.hash}")

            // 4. Update transaction status
            val updatedTransaction = if (broadcastResult.success) {
                Log.d("SolanaBroadcast", " Transaction broadcast successful!")
                transaction.copy(
                    status = TransactionStatus.SUCCESS,
                    hash = broadcastResult.hash ?: transaction.hash
                )
            } else {
                Log.e("SolanaBroadcast", " Transaction broadcast failed: ${broadcastResult.error}")
                transaction.copy(
                    status = TransactionStatus.FAILED
                )
            }
            localDataSource.saveSendTransaction(updatedTransaction)

            Result.success(broadcastResult)

        } catch (e: Exception) {
            Log.e("SolanaBroadcast", " Broadcast error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun createSolanaKeypair(privateKeyHex: String): Keypair? {
        return try {
            // Remove 0x prefix if present
            val cleanPrivateKeyHex = if (privateKeyHex.startsWith("0x")) {
                privateKeyHex.substring(2)
            } else {
                privateKeyHex
            }

            Log.d("SolanaTxRepo", "Creating keypair from hex, length: ${cleanPrivateKeyHex.length}")

            // Decode hex to bytes
            val privateKeyBytes = cleanPrivateKeyHex.hexToByteArray()

            // sol4k expects 64-byte keypair (seed + public key)
            if (privateKeyBytes.size == 64) {
                Keypair.fromSecretKey(privateKeyBytes)
            } else if (privateKeyBytes.size == 32) {
                // If we only have seed (32 bytes), generate keypair from seed
                Keypair.fromSecretKey(privateKeyBytes + ByteArray(32))
            } else {
                Log.e("SolanaTxRepo", "Invalid private key size: ${privateKeyBytes.size} bytes")
                null
            }
        } catch (e: Exception) {
            Log.e("SolanaTxRepo", "Error creating keypair: ${e.message}", e)
            null
        }
    }

    // Helper extension functions
    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}