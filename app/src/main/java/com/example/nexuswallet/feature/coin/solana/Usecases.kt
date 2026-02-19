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
import org.sol4k.PublicKey
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.DriverManager.getConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSolanaTransactionsUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(walletId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("SyncSolanaUC", "=== Syncing Solana transactions for wallet: $walletId ===")

            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                Log.e("SyncSolanaUC", "Wallet not found: $walletId")
                return@withContext Result.Error("Wallet not found")
            }

            val solanaCoin = wallet.solana
            if (solanaCoin == null) {
                Log.e("SyncSolanaUC", "Solana not enabled for wallet: ${wallet.name}")
                return@withContext Result.Error("Solana not enabled")
            }

            Log.d("SyncSolanaUC", "Wallet: ${wallet.name}, Address: ${solanaCoin.address}")

            // Fetch full transaction history with details
            val historyResult = solanaBlockchainRepository.getFullTransactionHistory(
                address = solanaCoin.address,
                network = SolanaNetwork.DEVNET,
                limit = 50
            )

            when (historyResult) {
                is Result.Success -> {
                    val transactions = historyResult.data
                    Log.d("SyncSolanaUC", "Received ${transactions.size} transactions with details")

                    if (transactions.isEmpty()) {
                        Log.d("SyncSolanaUC", "No transactions found")
                        return@withContext Result.Success(Unit)
                    }

                    // Delete existing transactions for this wallet
                    solanaTransactionRepository.deleteAllForWallet(walletId)
                    Log.d("SyncSolanaUC", "Deleted existing transactions")

                    // Save new transactions with full details
                    var savedCount = 0
                    transactions.forEachIndexed { index, (sigInfo, details) ->
                        // Parse transfer info if we have details
                        val transfer = details?.let {
                            solanaBlockchainRepository.parseTransferFromDetails(it, solanaCoin.address)
                        }

                        // Determine status
                        val status = if (details?.meta?.err == null) {
                            when (sigInfo.confirmationStatus) {
                                "finalized" -> TransactionStatus.SUCCESS
                                "confirmed" -> TransactionStatus.SUCCESS
                                "processed" -> TransactionStatus.PENDING
                                else -> TransactionStatus.PENDING
                            }
                        } else {
                            TransactionStatus.FAILED
                        }

                        // Get amount from transfer or default to 0
                        val amountLamports = transfer?.amount ?: 0
                        val amountSol = BigDecimal(amountLamports).divide(
                            BigDecimal(1_000_000_000),
                            9,
                            RoundingMode.HALF_UP
                        ).toPlainString()

                        // Get fee from details or use default
                        val feeLamports = details?.meta?.fee ?: 5000
                        val feeSol = BigDecimal(feeLamports).divide(
                            BigDecimal(1_000_000_000),
                            9,
                            RoundingMode.HALF_UP
                        ).toPlainString()

                        // Get blockhash if available
                        val blockhash = details?.transaction?.message?.recentBlockhash ?: ""

                        // Create timestamp
                        val timestamp = (sigInfo.blockTime ?: (System.currentTimeMillis() / 1000)) * 1000

                        // Create transaction
                        val transaction = SolanaTransaction(
                            id = "sol_${sigInfo.signature}_${System.currentTimeMillis()}",
                            walletId = walletId,
                            fromAddress = transfer?.from ?: solanaCoin.address,
                            toAddress = transfer?.to ?: "",
                            status = status,
                            timestamp = timestamp,
                            note = null,
                            feeLevel = FeeLevel.NORMAL,
                            amountLamports = amountLamports,
                            amountSol = amountSol,
                            feeLamports = feeLamports,
                            feeSol = feeSol,
                            blockhash = blockhash,
                            signedData = null,
                            signature = sigInfo.signature,
                            network = when (solanaCoin.network) {
                                SolanaNetwork.MAINNET -> "mainnet"
                                SolanaNetwork.DEVNET -> "devnet"
                            },
                            isIncoming = transfer?.isIncoming ?: false,
                            slot = sigInfo.slot,
                            blockTime = sigInfo.blockTime
                        )

                        Log.d("SyncSolanaUC", "Transaction #$index: ${sigInfo.signature.take(8)}...")
                        Log.d("SyncSolanaUC", "  isIncoming: ${transaction.isIncoming}")
                        Log.d("SyncSolanaUC", "  amount: $amountLamports lamports ($amountSol SOL)")
                        Log.d("SyncSolanaUC", "  from: ${transaction.fromAddress.take(8)}...")
                        Log.d("SyncSolanaUC", "  to: ${transaction.toAddress.take(8)}...")
                        Log.d("SyncSolanaUC", "  status: $status")
                        Log.d("SyncSolanaUC", "  slot: ${transaction.slot}")

                        solanaTransactionRepository.saveTransaction(transaction)
                        savedCount++
                    }

                    Log.d("SyncSolanaUC", "Successfully saved $savedCount detailed transactions")
                    Log.d("SyncSolanaUC", "=== Sync completed successfully for wallet $walletId ===")
                    Result.Success(Unit)
                }

                is Result.Error -> {
                    Log.e("SyncSolanaUC", "Failed to fetch transactions: ${historyResult.message}")
                    Result.Error(historyResult.message)
                }

                else -> Result.Error("Unknown error")
            }
        } catch (e: Exception) {
            Log.e("SyncSolanaUC", "Error syncing: ${e.message}", e)
            Result.Error(e.message ?: "Sync failed")
        }
    }
}

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