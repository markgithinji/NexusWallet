package com.example.nexuswallet.feature.coin.solana

import android.util.Log
import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
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
        val wallet = walletRepository.getWallet(walletId) ?: run {
            Log.e("SyncSolanaUC", "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana ?: run {
            Log.e("SyncSolanaUC", "Solana not enabled for wallet: ${wallet.name}")
            return@withContext Result.Error("Solana not enabled")
        }

        Log.d("SyncSolanaUC", "Wallet: ${wallet.name}, Address: ${solanaCoin.address}, Network: ${solanaCoin.network}")

        val historyResult = solanaBlockchainRepository.getFullTransactionHistory(
            address = solanaCoin.address,
            network = solanaCoin.network,
            limit = 50
        )

        return@withContext when (historyResult) {
            is Result.Success -> {
                val transactions = historyResult.data
                Log.d("SyncSolanaUC", "Received ${transactions.size} transactions on ${solanaCoin.network}")

                if (transactions.isEmpty()) {
                    Log.d("SyncSolanaUC", "No transactions found")
                    return@withContext Result.Success(Unit)
                }

                solanaTransactionRepository.deleteAllForWallet(walletId)
                Log.d("SyncSolanaUC", "Deleted existing transactions")

                var savedCount = 0
                transactions.forEachIndexed { index, (sigInfo, details) ->
                    val transfer = details?.let {
                        solanaBlockchainRepository.parseTransferFromDetails(it, solanaCoin.address)
                    }

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

                    val amountLamports = transfer?.amount ?: 0
                    val amountSol = BigDecimal(amountLamports).divide(
                        BigDecimal(1_000_000_000),
                        9,
                        RoundingMode.HALF_UP
                    ).toPlainString()

                    val feeLamports = details?.meta?.fee ?: 5000
                    val feeSol = BigDecimal(feeLamports).divide(
                        BigDecimal(1_000_000_000),
                        9,
                        RoundingMode.HALF_UP
                    ).toPlainString()

                    val blockhash = details?.transaction?.message?.recentBlockhash ?: ""
                    val timestamp = (sigInfo.blockTime ?: (System.currentTimeMillis() / 1000)) * 1000

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

                    Log.d("SyncSolanaUC", "Transaction #$index on ${solanaCoin.network}: ${sigInfo.signature.take(8)}...")
                    Log.d("SyncSolanaUC", "  isIncoming: ${transaction.isIncoming}")
                    Log.d("SyncSolanaUC", "  amount: $amountLamports lamports ($amountSol SOL)")

                    solanaTransactionRepository.saveTransaction(transaction)
                    savedCount++
                }

                Log.d("SyncSolanaUC", "Successfully saved $savedCount transactions on ${solanaCoin.network}")
                Log.d("SyncSolanaUC", "=== Sync completed for wallet $walletId ===")
                Result.Success(Unit)
            }

            is Result.Error -> {
                Log.e("SyncSolanaUC", "Failed to fetch transactions on ${solanaCoin.network}: ${historyResult.message}")
                Result.Error(historyResult.message)
            }

            else -> Result.Error("Unknown error on ${solanaCoin.network}")
        }
    }
}

@Singleton
class GetSolanaWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(walletId: String): Result<SolanaWalletInfo> {
        val wallet = walletRepository.getWallet(walletId) ?: run {
            Log.e("GetSolanaWalletUC", "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana ?: run {
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
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendSolanaResult> = withContext(Dispatchers.IO) {
        Log.d("SendSolanaUC", "Sending $amount SOL to $toAddress")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            Log.e("SendSolanaUC", "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana ?: run {
            Log.e("SendSolanaUC", "Solana not enabled for wallet: $walletId")
            return@withContext Result.Error("Solana not enabled")
        }

        Log.d("SendSolanaUC", "Network: ${solanaCoin.network}")

        // Create transaction
        val transactionResult = createTransaction(walletId, toAddress, amount, feeLevel, note, solanaCoin.network)
        if (transactionResult is Result.Error) return@withContext transactionResult
        val transaction = (transactionResult as Result.Success).data

        // Sign transaction
        val signedTransactionResult = signTransaction(transaction, solanaCoin.network, solanaCoin.address)
        if (signedTransactionResult is Result.Error) return@withContext signedTransactionResult
        val signedTransaction = (signedTransactionResult as Result.Success).data

        // Broadcast transaction
        val broadcastResult = broadcastTransaction(signedTransaction, solanaCoin.network)

        val sendResult = SendSolanaResult(
            transactionId = transaction.id,
            txHash = broadcastResult.hash ?: signedTransaction.signature ?: "",
            success = broadcastResult.success,
            error = broadcastResult.error
        )

        if (sendResult.success) {
            Log.d("SendSolanaUC", "Send successful on ${solanaCoin.network}: tx ${sendResult.txHash.take(8)}...")
        } else {
            Log.e("SendSolanaUC", "Send failed on ${solanaCoin.network}: ${sendResult.error}")
        }

        Result.Success(sendResult)
    }

    private suspend fun createTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?,
        network: SolanaNetwork
    ): Result<SolanaTransaction> {
        val wallet = walletRepository.getWallet(walletId) ?: run {
            Log.e("SendSolanaUC", "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana ?: run {
            Log.e("SendSolanaUC", "Solana not enabled for wallet: $walletId")
            return Result.Error("Solana not enabled")
        }

        val blockhashResult = solanaBlockchainRepository.getRecentBlockhash(network)
        if (blockhashResult is Result.Error) {
            Log.e("SendSolanaUC", "Failed to get blockhash on ${network}")
            return Result.Error(blockhashResult.message)
        }
        val blockhash = (blockhashResult as Result.Success).data

        val feeResult = solanaBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (feeResult is Result.Error) {
            Log.e("SendSolanaUC", "Failed to get fee estimate on ${network}")
            return Result.Error(feeResult.message)
        }
        val feeEstimate = (feeResult as Result.Success).data

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
            network = when (network) {
                SolanaNetwork.MAINNET -> "mainnet"
                SolanaNetwork.DEVNET -> "devnet"
            },
            isIncoming = false,
            slot = null,
            blockTime = null
        )

        return Result.Success(transaction)
    }

    private suspend fun signTransaction(
        transaction: SolanaTransaction,
        network: SolanaNetwork,
        expectedAddress: String
    ): Result<SolanaTransaction> {
        val wallet = walletRepository.getWallet(transaction.walletId) ?: run {
            Log.e("SendSolanaUC", "Wallet not found: ${transaction.walletId}")
            return Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana ?: run {
            Log.e("SendSolanaUC", "Solana not enabled for wallet: ${transaction.walletId}")
            return Result.Error("Solana not enabled")
        }

        val blockhashResult = solanaBlockchainRepository.getRecentBlockhash(network)
        if (blockhashResult is Result.Error) {
            Log.e("SendSolanaUC", "Failed to get blockhash on ${network}")
            return Result.Error(blockhashResult.message)
        }
        val currentBlockhash = (blockhashResult as Result.Success).data

        // Get encrypted private key
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = transaction.walletId,
            keyType = "SOLANA_PRIVATE_KEY"
        ) ?: run {
            Log.e("SendSolanaUC", "No private key found for wallet: ${transaction.walletId}")
            return Result.Error("No private key found")
        }

        val (encryptedHex, iv) = encryptedData

        // Decrypt
        val privateKeyHex = try {
            keyStoreRepository.decryptString(encryptedHex, iv.toHex())
        } catch (e: Exception) {
            Log.e("SendSolanaUC", "Failed to decrypt private key: ${e.message}")
            return Result.Error("Failed to decrypt private key")
        }

        val keypair = createSolanaKeypair(privateKeyHex) ?: return Result.Error("Invalid private key format")

        val derivedAddress = keypair.publicKey.toString()
        if (derivedAddress != expectedAddress) {
            Log.e("SendSolanaUC", "Private key doesn't match wallet")
            Log.e("SendSolanaUC", "Derived: $derivedAddress, Expected: $expectedAddress")
            return Result.Error("Private key doesn't match wallet")
        }

        val signedTxResult = solanaBlockchainRepository.createAndSignTransaction(
            fromKeypair = keypair,
            toAddress = transaction.toAddress,
            lamports = transaction.amountLamports,
            network = network
        )

        if (signedTxResult is Result.Error) {
            Log.e("SendSolanaUC", "Failed to sign transaction on ${network}")
            return Result.Error(signedTxResult.message)
        }
        val signedTx = (signedTxResult as Result.Success).data

        val signedDataHex = signedTx.serialize().toHexString()

        return Result.Success(
            transaction.copy(
                status = TransactionStatus.PENDING,
                signature = signedTx.signature,
                signedData = signedDataHex,
                blockhash = currentBlockhash
            )
        )
    }

    private suspend fun broadcastTransaction(
        transaction: SolanaTransaction,
        network: SolanaNetwork
    ): BroadcastResult {
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

        val broadcastResult = solanaBlockchainRepository.broadcastTransaction(solanaSignedTx, network)

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
                Log.e("SendSolanaUC", "Broadcast failed on ${network}: ${broadcastResult.message}")
                BroadcastResult(success = false, error = broadcastResult.message)
            }
            Result.Loading -> {
                Log.e("SendSolanaUC", "Broadcast timeout on ${network}")
                BroadcastResult(success = false, error = "Broadcast timeout")
            }
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

    // Helper extension for ByteArray to Hex
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

@Singleton
class GetSolanaBalanceUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(address: String, network: SolanaNetwork): Result<BigDecimal> {
        Log.d("GetSolanaBalanceUC", "Fetching balance for $address on ${network}")
        val result = solanaBlockchainRepository.getBalance(address, network)
        if (result is Result.Error) {
            Log.e("GetSolanaBalanceUC", "Failed to get balance on ${network}: ${result.message}")
        }
        return result
    }
}

@Singleton
class GetSolanaFeeEstimateUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository
) {
    suspend operator fun invoke(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: SolanaNetwork
    ): Result<SolanaFeeEstimate> {
        Log.d("GetSolanaFeeUC", "Fetching fee estimate on ${network}")
        val result = solanaBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (result is Result.Error) {
            Log.e("GetSolanaFeeUC", "Failed to get fee estimate on ${network}: ${result.message}")
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