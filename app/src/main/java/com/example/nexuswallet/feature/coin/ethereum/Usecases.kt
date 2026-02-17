package com.example.nexuswallet.feature.coin.ethereum


import android.util.Log
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetEthereumWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(walletId: String): Result<EthereumWalletInfo> {
        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            Log.e("GetEthereumWalletUC", "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val ethereumCoin = wallet.ethereum
        if (ethereumCoin == null) {
            Log.e("GetEthereumWalletUC", "Ethereum not enabled for wallet: ${wallet.name}")
            return Result.Error("Ethereum not enabled for this wallet")
        }

        Log.d(
            "GetEthereumWalletUC",
            "Loaded wallet: ${wallet.name}, address: ${ethereumCoin.address.take(8)}..."
        )

        return Result.Success(
            EthereumWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = ethereumCoin.address,
                network = ethereumCoin.network
            )
        )
    }
}

@Singleton
class SendEthereumUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val keyManager: KeyManager
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendEthereumResult> = withContext(Dispatchers.IO) {
        try {
            Log.d("SendEthereumUC", "WalletId: $walletId, To: $toAddress, Amount: $amount ETH")

            // 1. Create transaction
            Log.d("SendEthereumUC", "Step 1: Creating transaction...")
            val transaction = createTransaction(walletId, toAddress, amount, feeLevel, note)
                ?: return@withContext Result.Error("Failed to create transaction", null)

            Log.d("SendEthereumUC", "Transaction created: ${transaction.id}")

            // 2. Sign transaction
            Log.d("SendEthereumUC", "Step 2: Signing transaction...")
            val signedTransaction = signTransaction(transaction)
                ?: return@withContext Result.Error("Failed to sign transaction", null)

            Log.d("SendEthereumUC", "Transaction signed: ${signedTransaction.txHash}")

            // 3. Broadcast transaction
            Log.d("SendEthereumUC", "Step 3: Broadcasting transaction...")
            val broadcastResult = broadcastTransaction(signedTransaction)

            Log.d(
                "SendEthereumUC",
                "Broadcast result: success=${broadcastResult.success}, hash=${broadcastResult.hash}"
            )

            val sendResult = SendEthereumResult(
                transactionId = transaction.id,
                txHash = broadcastResult.hash ?: signedTransaction.txHash ?: "",
                success = broadcastResult.success,
                error = broadcastResult.error
            )

            Log.d("SendEthereumUC", "========== SEND COMPLETE ==========")
            Result.Success(sendResult)

        } catch (e: Exception) {
            Log.e("SendEthereumUC", "Exception: ${e.message}", e)
            Result.Error("Send failed: ${e.message}", e)
        }
    }

    private suspend fun createTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): EthereumTransaction? {
        try {
            val wallet = walletRepository.getWallet(walletId)
                ?: run {
                    Log.e("SendEthereumUC", "Wallet not found: $walletId")
                    return null
                }

            val ethereumCoin = wallet.ethereum
                ?: run {
                    Log.e("SendEthereumUC", "Ethereum not enabled for wallet: $walletId")
                    return null
                }

            Log.d("SendEthereumUC", "Creating transaction for address: ${ethereumCoin.address}")

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(
                ethereumCoin.address,
                ethereumCoin.network
            )
            val nonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                else -> {
                    Log.e("SendEthereumUC", "Failed to get nonce")
                    return null
                }
            }

            val feeResult = ethereumBlockchainRepository.getFeeEstimate(feeLevel)
            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                else -> {
                    Log.e("SendEthereumUC", "Failed to get fee estimate")
                    return null
                }
            }

            val amountWei = amount.multiply(BigDecimal("1000000000000000000")).toBigInteger()

            val transaction = EthereumTransaction(
                id = "tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = ethereumCoin.address,
                toAddress = toAddress,
                amountWei = amountWei.toString(),
                amountEth = amount.toPlainString(),
                gasPriceWei = feeEstimate.gasPriceWei,
                gasPriceGwei = feeEstimate.gasPriceGwei,
                gasLimit = feeEstimate.gasLimit,
                feeWei = feeEstimate.totalFeeWei,
                feeEth = feeEstimate.totalFeeEth,
                nonce = nonce,
                chainId = ethereumCoin.network.chainId.toLong(),
                signedHex = null,
                txHash = null,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = ethereumCoin.network.displayName,
                data = ""
            )

            ethereumTransactionRepository.saveTransaction(transaction)
            Log.d("SendEthereumUC", "Transaction saved locally: ${transaction.id}")
            return transaction

        } catch (e: Exception) {
            Log.e("SendEthereumUC", "Error creating transaction: ${e.message}", e)
            return null
        }
    }

    private suspend fun signTransaction(transaction: EthereumTransaction): EthereumTransaction? {
        try {
            Log.d("SendEthereumUC", "Signing transaction: ${transaction.id}")

            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: run {
                    Log.e("SendEthereumUC", "Wallet not found: ${transaction.walletId}")
                    return null
                }

            val ethereumCoin = wallet.ethereum
                ?: run {
                    Log.e(
                        "SendEthereumUC",
                        "Ethereum not enabled for wallet: ${transaction.walletId}"
                    )
                    return null
                }

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(
                ethereumCoin.address,
                ethereumCoin.network
            )
            val currentNonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                else -> {
                    Log.e("SendEthereumUC", "Failed to get nonce")
                    return null
                }
            }

            // Use the fee from the transaction
            val gasPriceWei = BigInteger(transaction.gasPriceWei)

            val privateKeyResult = keyManager.getPrivateKeyForSigning(transaction.walletId)
            if (privateKeyResult.isFailure) {
                Log.e("SendEthereumUC", "Failed to get private key")
                return null
            }
            val privateKey = privateKeyResult.getOrThrow()

            val credentials = Credentials.create(privateKey)
            if (credentials.address.lowercase() != ethereumCoin.address.lowercase()) {
                Log.e("SendEthereumUC", "Private key doesn't match wallet")
                return null
            }

            val rawTransaction = RawTransaction.createTransaction(
                BigInteger.valueOf(currentNonce.toLong()),
                gasPriceWei,
                BigInteger.valueOf(transaction.gasLimit),
                transaction.toAddress,
                BigInteger(transaction.amountWei),
                transaction.data
            )

            val chainId = transaction.chainId
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
            val signedHex = Numeric.toHexString(signedMessage)

            val txHashBytes = Hash.sha3(Numeric.hexStringToByteArray(signedHex))
            val calculatedHash = Numeric.toHexString(txHashBytes)

            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                txHash = calculatedHash,
                signedHex = signedHex,
                nonce = currentNonce
            )

            ethereumTransactionRepository.updateTransaction(updatedTransaction)
            Log.d("SendEthereumUC", "Transaction signed: $calculatedHash")
            return updatedTransaction

        } catch (e: Exception) {
            Log.e("SendEthereumUC", "Error signing transaction: ${e.message}", e)
            return null
        }
    }

    private suspend fun broadcastTransaction(transaction: EthereumTransaction): BroadcastResult {
        try {
            if (transaction.signedHex == null) {
                Log.e("SendEthereumUC", "Transaction not signed")
                return BroadcastResult(success = false, error = "Transaction not signed")
            }

            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: return BroadcastResult(success = false, error = "Wallet not found")

            val ethereumCoin = wallet.ethereum
                ?: return BroadcastResult(success = false, error = "Ethereum not enabled")

            val broadcastResult = ethereumBlockchainRepository.broadcastEthereumTransaction(
                transaction.signedHex,
                ethereumCoin.network
            )

            return when (broadcastResult) {
                is Result.Success -> {
                    val result = broadcastResult.data
                    val updatedTransaction = if (result.success) {
                        transaction.copy(
                            status = TransactionStatus.SUCCESS,
                            txHash = result.hash ?: transaction.txHash
                        )
                    } else {
                        transaction.copy(
                            status = TransactionStatus.FAILED
                        )
                    }
                    ethereumTransactionRepository.updateTransaction(updatedTransaction)

                    Log.d("SendEthereumUC", "Broadcast result: ${result.success}")
                    result
                }

                is Result.Error -> {
                    Log.e("SendEthereumUC", "Broadcast failed: ${broadcastResult.message}")
                    BroadcastResult(success = false, error = broadcastResult.message)
                }

                Result.Loading -> {
                    Log.e("SendEthereumUC", "Broadcast timeout")
                    BroadcastResult(success = false, error = "Broadcast timeout")
                }
            }

        } catch (e: Exception) {
            Log.e("SendEthereumUC", "Error broadcasting: ${e.message}", e)
            return BroadcastResult(success = false, error = e.message ?: "Broadcast failed")
        }
    }
}

@Singleton
class GetTransactionUseCase @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository
) {
    suspend operator fun invoke(transactionId: String): Result<EthereumTransaction> {
        return try {
            val transaction = ethereumTransactionRepository.getTransaction(transactionId)
            if (transaction != null) {
                Result.Success(transaction)
            } else {
                Result.Error(
                    "Transaction not found",
                    IllegalArgumentException("Transaction not found")
                )
            }
        } catch (e: Exception) {
            Result.Error("Failed to get transaction: ${e.message}", e)
        }
    }
}

@Singleton
class GetWalletTransactionsUseCase @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository
) {
    fun invoke(walletId: String): Flow<Result<List<EthereumTransaction>>> {
        return ethereumTransactionRepository.getTransactions(walletId)
            .map { transactions ->
                try {
                    Result.Success(transactions)
                } catch (e: Exception) {
                    Result.Error("Failed to load transactions: ${e.message}", e)
                }
            }
    }
}

@Singleton
class GetPendingTransactionsUseCase @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository
) {
    suspend operator fun invoke(): Result<List<EthereumTransaction>> {
        return try {
            val transactions = ethereumTransactionRepository.getPendingTransactions()
            Result.Success(transactions)
        } catch (e: Exception) {
            Result.Error("Failed to get pending transactions: ${e.message}", e)
        }
    }
}

@Singleton
class ValidateAddressUseCase @Inject constructor() {
    operator fun invoke(address: String): Boolean {
        return address.startsWith("0x") && address.length == 42
    }
}

@Singleton
class GetFeeEstimateUseCase @Inject constructor(
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) {
    suspend operator fun invoke(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<EthereumFeeEstimate> {
        return ethereumBlockchainRepository.getFeeEstimate(feeLevel)
    }
}