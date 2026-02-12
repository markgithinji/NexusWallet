package com.example.nexuswallet.feature.coin.solana

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
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
import kotlinx.coroutines.withContext
import org.sol4k.Keypair
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateSolanaTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(
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
        Log.d("CreateSolanaTxUseCase", " createSolanaTransaction START")
        Log.d("CreateSolanaTxUseCase", "Wallet: ${wallet.address}")
        Log.d("CreateSolanaTxUseCase", "Amount: $amount SOL to $toAddress")

        return try {
            val blockhash = solanaBlockchainRepository.getRecentBlockhash()
            Log.d("CreateSolanaTxUseCase", "Blockhash: ${blockhash.take(16)}...")

            val feeEstimate = solanaBlockchainRepository.getFeeEstimate()
            Log.d("CreateSolanaTxUseCase", "Fee estimate: ${feeEstimate.totalFee} lamports")

            val lamports = amount.multiply(BigDecimal("1000000000")).toLong()
            Log.d("CreateSolanaTxUseCase", "Amount in lamports: $lamports")

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

            transactionLocalDataSource.saveSendTransaction(transaction)
            Log.d("CreateSolanaTxUseCase", " Saved LOCAL transaction")

            Result.success(transaction)

        } catch (e: Exception) {
            Log.e("CreateSolanaTxUseCase", " Error: ${e.message}", e)
            Result.failure(e)
        }
    }
}

@Singleton
class SignSolanaTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val keyManager: KeyManager,
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(transactionId: String): Result<SignedTransaction> {
        return try {
            val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            if (transaction.walletType != WalletType.SOLANA) {
                return Result.failure(IllegalArgumentException("Only Solana signing supported"))
            }

            signSolanaTransaction(transactionId)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun signSolanaTransaction(
        transactionId: String
    ): Result<SignedTransaction> {
        return withContext(Dispatchers.IO) {
            try {
                val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Transaction not found"))

                val wallet = walletRepository.getWallet(transaction.walletId) as? SolanaWallet
                    ?: return@withContext Result.failure(IllegalArgumentException("Solana wallet not found"))

                Log.d("SignSolanaTxUseCase", " Signing transaction: ${transaction.id}")

                val currentBlockhash = solanaBlockchainRepository.getRecentBlockhash()
                Log.d("SignSolanaTxUseCase", "Current blockhash: ${currentBlockhash.take(16)}...")

                val fee = solanaBlockchainRepository.getFeeEstimate()
                Log.d("SignSolanaTxUseCase", "Fee: ${fee.totalFee} lamports")

                Log.d("SignSolanaTxUseCase", "Requesting private key...")
                val privateKeyResult = keyManager.getPrivateKeyForSigning(
                    transaction.walletId,
                    keyType = "SOLANA_PRIVATE_KEY"
                )

                if (privateKeyResult.isFailure) {
                    Log.e("SignSolanaTxUseCase", "Failed to get private key")
                    return@withContext Result.failure(
                        privateKeyResult.exceptionOrNull() ?: IllegalStateException("No private key")
                    )
                }

                val privateKeyHex = privateKeyResult.getOrThrow()
                Log.d("SignSolanaTxUseCase", "✓ Got private key: ${privateKeyHex.take(8)}...")

                val keypair = createSolanaKeypair(privateKeyHex)
                    ?: return@withContext Result.failure(IllegalArgumentException("Invalid private key format"))

                val derivedAddress = keypair.publicKey.toString()
                if (derivedAddress != wallet.address) {
                    Log.e("SignSolanaTxUseCase", "Address mismatch!")
                    return@withContext Result.failure(IllegalStateException("Private key doesn't match wallet"))
                }

                val lamports = transaction.amount.toLongOrNull() ?: 0L

                val signedTx = solanaBlockchainRepository.createAndSignTransaction(
                    fromKeypair = keypair,
                    toAddress = transaction.toAddress,
                    lamports = lamports
                )

                val signatureBytes = signedTx.signature
                val txHash = signatureBytes.toHexString()

                Log.d("SignSolanaTxUseCase", "Signed! Hash: ${txHash.take(16)}...")

                val signedTransaction = SignedTransaction(
                    rawHex = signedTx.serialize().toHexString(),
                    hash = txHash,
                    chain = transaction.chain
                )

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
                transactionLocalDataSource.saveSendTransaction(updatedTransaction)

                Result.success(signedTransaction)

            } catch (e: Exception) {
                Log.e("SignSolanaTxUseCase", " Signing failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private fun createSolanaKeypair(privateKeyHex: String): Keypair? {
        return try {
            val cleanPrivateKeyHex = if (privateKeyHex.startsWith("0x")) {
                privateKeyHex.substring(2)
            } else {
                privateKeyHex
            }

            Log.d("SignSolanaTxUseCase", "Creating keypair from hex, length: ${cleanPrivateKeyHex.length}")

            val privateKeyBytes = cleanPrivateKeyHex.hexToByteArray()

            when (privateKeyBytes.size) {
                64 -> Keypair.fromSecretKey(privateKeyBytes)
                32 -> Keypair.fromSecretKey(privateKeyBytes + ByteArray(32))
                else -> {
                    Log.e("SignSolanaTxUseCase", "Invalid private key size: ${privateKeyBytes.size} bytes")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("SignSolanaTxUseCase", "Error creating keypair: ${e.message}", e)
            null
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

@Singleton
class BroadcastSolanaTransactionUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(transactionId: String): Result<BroadcastResult> {
        Log.d("BroadcastSolanaUseCase", " Broadcasting transaction: $transactionId")

        return try {
            val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            if (transaction.chain != ChainType.SOLANA) {
                return Result.failure(IllegalArgumentException("Not a Solana transaction"))
            }

            broadcastSolanaTransaction(transactionId)

        } catch (e: Exception) {
            Log.e("BroadcastSolanaUseCase", " Broadcast failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun broadcastSolanaTransaction(
        transactionId: String
    ): Result<BroadcastResult> {
        return withContext(Dispatchers.IO) {
            try {
                val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Transaction not found"))

                val signedHex = transaction.signedHex
                    ?: return@withContext Result.failure(IllegalStateException("Transaction not signed"))

                val signatureBytes = signedHex.hexToByteArray()
                val solanaSignedTx = SolanaBlockchainRepository.SolanaSignedTransaction(
                    signature = signatureBytes.take(64).toByteArray(),
                    serialize = { signatureBytes }
                )

                Log.d("BroadcastSolanaUseCase", " Broadcasting to Solana devnet...")
                val broadcastResult = solanaBlockchainRepository.broadcastTransaction(solanaSignedTx)

                val updatedTransaction = if (broadcastResult.success) {
                    Log.d("BroadcastSolanaUseCase", " Broadcast successful!")
                    transaction.copy(
                        status = TransactionStatus.SUCCESS,
                        hash = broadcastResult.hash ?: transaction.hash
                    )
                } else {
                    Log.e("BroadcastSolanaUseCase", " Broadcast failed: ${broadcastResult.error}")
                    transaction.copy(
                        status = TransactionStatus.FAILED
                    )
                }
                transactionLocalDataSource.saveSendTransaction(updatedTransaction)

                Result.success(broadcastResult)

            } catch (e: Exception) {
                Log.e("BroadcastSolanaUseCase", " Broadcast error: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

@Singleton
class GetSolanaFeeEstimateUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(): FeeEstimate {
        return solanaBlockchainRepository.getFeeEstimate()
    }
}

@Singleton
class GetSolanaRecentBlockhashUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(): String {
        return solanaBlockchainRepository.getRecentBlockhash()
    }
}

@Singleton
class ValidateSolanaAddressUseCase @Inject constructor() {
    operator fun invoke(address: String): Boolean {
        // Solana addresses are base58 encoded and typically 32-44 characters
        return address.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"))
    }
}