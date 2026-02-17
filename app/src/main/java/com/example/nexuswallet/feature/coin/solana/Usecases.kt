package com.example.nexuswallet.feature.coin.solana

import android.util.Log
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sol4k.Keypair
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSolanaWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(walletId: String): Result<SolanaWalletInfo> {
        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            Log.e("GetSolanaWalletUC", "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana
        if (solanaCoin == null) {
            Log.e("GetSolanaWalletUC", "Solana not enabled for wallet: ${wallet.name}")
            return Result.Error("Solana not enabled for this wallet")
        }

        Log.d(
            "GetSolanaWalletUC",
            "Loaded wallet: ${wallet.name}, address: ${solanaCoin.address.take(8)}..."
        )

        return Result.Success(
            SolanaWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = solanaCoin.address
            )
        )
    }
}

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
            Log.d("SendSolanaUC", "Sending $amount SOL to $toAddress")

            val transaction = createTransaction(walletId, toAddress, amount, feeLevel, note)
                ?: return@withContext Result.Error("Failed to create transaction", null)

            val signedTransaction = signTransaction(transaction)
                ?: return@withContext Result.Error("Failed to sign transaction", null)

            val broadcastResult = broadcastTransaction(signedTransaction)

            val sendResult = SendSolanaResult(
                transactionId = transaction.id,
                txHash = broadcastResult.hash ?: signedTransaction.signature ?: "",
                success = broadcastResult.success,
                error = broadcastResult.error
            )

            if (sendResult.success) {
                Log.d("SendSolanaUC", "Send successful: tx ${sendResult.txHash.take(8)}...")
            } else {
                Log.e("SendSolanaUC", "Send failed: ${sendResult.error}")
            }

            Result.Success(sendResult)

        } catch (e: Exception) {
            Log.e("SendSolanaUC", "Send failed: ${e.message}")
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
            val wallet = walletRepository.getWallet(walletId) ?: run {
                Log.e("SendSolanaUC", "Wallet not found: $walletId")
                return null
            }

            val solanaCoin = wallet.solana ?: run {
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
                network = "mainnet"
            )

            solanaTransactionRepository.saveTransaction(transaction)
            Log.d("SendSolanaUC", "Transaction created: ${transaction.id}")
            return transaction

        } catch (e: Exception) {
            Log.e("SendSolanaUC", "Error creating transaction: ${e.message}")
            return null
        }
    }

    private suspend fun signTransaction(transaction: SolanaTransaction): SolanaTransaction? {
        try {
            val wallet = walletRepository.getWallet(transaction.walletId) ?: run {
                Log.e("SendSolanaUC", "Wallet not found: ${transaction.walletId}")
                return null
            }

            val solanaCoin = wallet.solana ?: run {
                Log.e("SendSolanaUC", "Solana not enabled for wallet: ${transaction.walletId}")
                return null
            }

            val blockhashResult = solanaBlockchainRepository.getRecentBlockhash()
            val currentBlockhash = when (blockhashResult) {
                is Result.Success -> blockhashResult.data
                else -> {
                    Log.e("SendSolanaUC", "Failed to get blockhash")
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

            val signedDataHex = signedTx.serialize().toHexString()

            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                signature = signedTx.signature,
                signedData = signedDataHex,
                blockhash = currentBlockhash
            )

            solanaTransactionRepository.updateTransaction(updatedTransaction)
            Log.d("SendSolanaUC", "Transaction signed: ${signedTx.signature.take(16)}...")
            return updatedTransaction

        } catch (e: Exception) {
            Log.e("SendSolanaUC", "Error signing transaction: ${e.message}")
            return null
        }
    }

    private suspend fun broadcastTransaction(transaction: SolanaTransaction): BroadcastResult {
        try {
            val signedDataHex = transaction.signedData ?: return BroadcastResult(
                success = false,
                error = "Not signed"
            )
            val signatureHex = transaction.signature ?: return BroadcastResult(
                success = false,
                error = "No signature"
            )

            val signedDataBytes = signedDataHex.hexToByteArray()

            val solanaSignedTx = SolanaSignedTransaction(
                signature = signatureHex,
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

                    if (result.success) {
                        Log.d("SendSolanaUC", "Broadcast successful: ${result.hash?.take(8)}...")
                    }
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
            Log.e("SendSolanaUC", "Error broadcasting: ${e.message}")
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
        Log.e("SendSolanaUC", "Error creating keypair: ${e.message}")
        null
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

@Singleton
class GetSolanaBalanceUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(address: String): Result<BigDecimal> {
        val result = solanaBlockchainRepository.getBalance(address)
        if (result is Result.Error) {
            Log.e("GetSolanaBalanceUC", "Failed to get balance: ${result.message}")
        }
        return result
    }
}

@Singleton
class GetSolanaFeeEstimateUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<SolanaFeeEstimate> {
        val result = solanaBlockchainRepository.getFeeEstimate(feeLevel)
        if (result is Result.Error) {
            Log.e("GetSolanaFeeUC", "Failed to get fee estimate: ${result.message}")
        }
        return result
    }
}

@Singleton
class ValidateSolanaAddressUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    operator fun invoke(address: String): Result<Boolean> {
        val result = solanaBlockchainRepository.validateAddress(address)
        if (result is Result.Success && !result.data) {
            Log.d("ValidateSolanaUC", "Invalid address: $address")
        }
        return result
    }
}