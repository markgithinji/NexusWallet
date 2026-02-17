package com.example.nexuswallet.feature.coin.solana

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sol4k.Keypair
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.ui.SendResult

@Singleton
class SendSolanaUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val keyManager: KeyManager
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendSolanaResult> = withContext(Dispatchers.IO) {
        try {
            Log.d("SendSolanaUC", "========== SEND SOLANA START ==========")
            Log.d("SendSolanaUC", "WalletId: $walletId, To: $toAddress, Amount: $amount SOL")

            // 1. Create transaction
            Log.d("SendSolanaUC", "Step 1: Creating transaction...")
            val transaction = createTransaction(walletId, toAddress, amount, feeLevel, note)
                ?: return@withContext Result.Error("Failed to create transaction", null)

            Log.d("SendSolanaUC", "Transaction created: ${transaction.id}")

            // 2. Sign transaction
            Log.d("SendSolanaUC", "Step 2: Signing transaction...")
            val signedTransaction = signTransaction(transaction)
                ?: return@withContext Result.Error("Failed to sign transaction", null)

            Log.d("SendSolanaUC", "Transaction signed: ${signedTransaction.signature?.take(16)}...")

            // 3. Broadcast transaction
            Log.d("SendSolanaUC", "Step 3: Broadcasting transaction...")
            val broadcastResult = broadcastTransaction(signedTransaction)

            val txHash = broadcastResult.hash ?: signedTransaction.signature ?: ""

            Log.d("SendSolanaUC", "Broadcast result: success=${broadcastResult.success}, hash=${txHash}")

            val sendResult = SendSolanaResult(
                transactionId = transaction.id,
                txHash = txHash,
                success = broadcastResult.success,
                error = broadcastResult.error
            )

            Log.d("SendSolanaUC", "========== SEND COMPLETE ==========")
            Result.Success(sendResult)

        } catch (e: Exception) {
            Log.e("SendSolanaUC", "Exception: ${e.message}", e)
            Result.Error("Send failed: ${e.message}", e)
        }
    }

    private suspend fun createTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): SolanaTransaction? {
        try {
            val wallet = walletRepository.getWallet(walletId)
                ?: run {
                    Log.e("SendSolanaUC", "Wallet not found: $walletId")
                    return null
                }

            val solanaCoin = wallet.solana
                ?: run {
                    Log.e("SendSolanaUC", "Solana not enabled for wallet: $walletId")
                    return null
                }

            val blockhashResult = solanaBlockchainRepository.getRecentBlockhash()
            val blockhash = when (blockhashResult) {
                is Result.Success -> blockhashResult.data
                else -> {
                    Log.e("SendSolanaUC", "Failed to get blockhash")
                    return null
                }
            }

            val feeResult = solanaBlockchainRepository.getFeeEstimate(feeLevel)
            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                else -> {
                    Log.e("SendSolanaUC", "Failed to get fee estimate")
                    return null
                }
            }

            val lamports = amount.multiply(BigDecimal("1000000000")).toLong()

            val transaction = SolanaTransaction(
                id = "sol_tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = solanaCoin.address,
                toAddress = toAddress,
                amountLamports = lamports,
                amountSol = amount.toPlainString(),
                feeLamports = feeEstimate.feeLamports,
                feeSol = feeEstimate.feeSol,
                blockhash = blockhash,
                signedData = null,
                signature = null,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = "mainnet" // TDDO: add network to SolanaCoin
            )

            solanaTransactionRepository.saveTransaction(transaction)
            Log.d("SendSolanaUC", "Transaction saved locally: ${transaction.id}")
            return transaction

        } catch (e: Exception) {
            Log.e("SendSolanaUC", "Error creating transaction: ${e.message}", e)
            return null
        }
    }

    private suspend fun signTransaction(transaction: SolanaTransaction): SolanaTransaction? {
        try {
            Log.d("SendSolanaUC", "Signing transaction: ${transaction.id}")

            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: run {
                    Log.e("SendSolanaUC", "Wallet not found: ${transaction.walletId}")
                    return null
                }

            val solanaCoin = wallet.solana
                ?: run {
                    Log.e("SendSolanaUC", "Solana not enabled for wallet: ${transaction.walletId}")
                    return null
                }

            val blockhashResult = solanaBlockchainRepository.getRecentBlockhash()
            val currentBlockhash = when (blockhashResult) {
                is Result.Success -> blockhashResult.data
                else -> {
                    Log.e("SendSolanaUC", "Failed to get blockhash for signing")
                    return null
                }
            }

            val privateKeyResult = keyManager.getPrivateKeyForSigning(
                transaction.walletId,
                keyType = "SOLANA_PRIVATE_KEY"
            )

            if (privateKeyResult.isFailure) {
                Log.e("SendSolanaUC", "Failed to get private key")
                return null
            }

            val privateKeyHex = privateKeyResult.getOrThrow()
            val keypair = createSolanaKeypair(privateKeyHex) ?: return null

            val derivedAddress = keypair.publicKey.toString()
            if (derivedAddress != solanaCoin.address) {
                Log.e("SendSolanaUC", "Private key doesn't match wallet")
                return null
            }

            val signedTxResult = solanaBlockchainRepository.createAndSignTransaction(
                fromKeypair = keypair,
                toAddress = transaction.toAddress,
                lamports = transaction.amountLamports,
                feeLevel = transaction.feeLevel
            )

            val signedTx = when (signedTxResult) {
                is Result.Success -> signedTxResult.data
                else -> {
                    Log.e("SendSolanaUC", "Failed to sign transaction")
                    return null
                }
            }

            val signatureHex = signedTx.signature.toHexString()
            val signedDataHex = signedTx.serialize().toHexString()

            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                signature = signatureHex,
                signedData = signedDataHex,
                blockhash = currentBlockhash
            )

            solanaTransactionRepository.updateTransaction(updatedTransaction)
            Log.d("SendSolanaUC", "Transaction signed: ${signatureHex.take(16)}...")
            return updatedTransaction

        } catch (e: Exception) {
            Log.e("SendSolanaUC", "Error signing transaction: ${e.message}", e)
            return null
        }
    }

    private suspend fun broadcastTransaction(transaction: SolanaTransaction): BroadcastResult {
        try {
            val signedDataHex = transaction.signedData ?: return BroadcastResult(success = false, error = "Not signed")
            val signatureHex = transaction.signature ?: return BroadcastResult(success = false, error = "No signature")

            val signedDataBytes = signedDataHex.hexToByteArray()
            val signatureBytes = signatureHex.hexToByteArray()

            val solanaSignedTx = SolanaBlockchainRepository.SolanaSignedTransaction(
                signature = signatureBytes,
                serialize = { signedDataBytes }
            )

            val broadcastResult = solanaBlockchainRepository.broadcastTransaction(solanaSignedTx)

            return when (broadcastResult) {
                is Result.Success -> {
                    val result = broadcastResult.data
                    val updatedTransaction = if (result.success) {
                        transaction.copy(status = TransactionStatus.SUCCESS)
                    } else {
                        transaction.copy(status = TransactionStatus.FAILED)
                    }
                    solanaTransactionRepository.updateTransaction(updatedTransaction)
                    result
                }
                is Result.Error -> {
                    Log.e("SendSolanaUC", "Broadcast failed: ${broadcastResult.message}")
                    BroadcastResult(success = false, error = broadcastResult.message)
                }
                Result.Loading -> {
                    Log.e("SendSolanaUC", "Broadcast timeout")
                    BroadcastResult(success = false, error = "Broadcast timeout")
                }
            }

        } catch (e: Exception) {
            Log.e("SendSolanaUC", "Error broadcasting: ${e.message}", e)
            return BroadcastResult(success = false, error = e.message ?: "Broadcast failed")
        }
    }

    private fun createSolanaKeypair(privateKeyHex: String): Keypair? = try {
        val cleanPrivateKeyHex = if (privateKeyHex.startsWith("0x")) {
            privateKeyHex.substring(2)
        } else {
            privateKeyHex
        }
        val privateKeyBytes = cleanPrivateKeyHex.hexToByteArray()
        when (privateKeyBytes.size) {
            64 -> Keypair.fromSecretKey(privateKeyBytes)
            32 -> Keypair.fromSecretKey(privateKeyBytes + ByteArray(32))
            else -> null
        }
    } catch (e: Exception) {
        Log.e("SendSolanaUC", "Error creating keypair: ${e.message}", e)
        null
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

data class SendSolanaResult(
    override val transactionId: String,
    override val txHash: String,
    override val success: Boolean,
    override val error: String? = null
) : SendResult

@Singleton
class GetSolanaBalanceUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(address: String): Result<BigDecimal> {
        return solanaBlockchainRepository.getBalance(address)
    }
}

@Singleton
class GetSolanaFeeEstimateUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<SolanaFeeEstimate> {
        return solanaBlockchainRepository.getFeeEstimate(feeLevel)
    }
}

@Singleton
class GetSolanaRecentBlockhashUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(): Result<String> {
        return solanaBlockchainRepository.getRecentBlockhash()
    }
}

@Singleton
class RequestSolanaAirdropUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(address: String, amountSol: Double = 1.0): Result<String> {
        return solanaBlockchainRepository.requestAirdrop(address, amountSol)
    }
}

//@Singleton
//class GetSolanaTransactionHistoryUseCase @Inject constructor(
//    private val solanaBlockchainRepository: SolanaBlockchainRepository
//) {
//    suspend operator fun invoke(address: String, limit: Int = 10): Result<List<SolanaBlockchainRepository.SolanaTransaction>> {
//        return solanaBlockchainRepository.getTransactionHistory(address, limit)
//    }
//}

@Singleton
class ValidateSolanaAddressUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    operator fun invoke(address: String): Result<Boolean> {
        return solanaBlockchainRepository.validateAddress(address)
    }
}