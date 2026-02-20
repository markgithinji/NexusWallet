package com.example.nexuswallet.feature.coin.ethereum

import android.util.Log
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.Transaction
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
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEthereumTransactionsUseCase @Inject constructor(
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(walletId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("SyncEthUC", "=== Syncing Ethereum transactions for wallet: $walletId ===")

            // Get wallet
            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                Log.e("SyncEthUC", "Wallet not found: $walletId")
                return@withContext Result.Error("Wallet not found")
            }

            // Get Ethereum coin
            val ethereumCoin = wallet.ethereum
            if (ethereumCoin == null) {
                Log.e("SyncEthUC", "Ethereum not enabled for wallet: ${wallet.name}")
                return@withContext Result.Error("Ethereum not enabled")
            }

            Log.d("SyncEthUC", "Wallet: ${wallet.name}, Address: ${ethereumCoin.address}, Network: ${ethereumCoin.network.displayName}")

            // Fetch transactions directly from Etherscan
            val transactionsResult = ethereumBlockchainRepository.getEthereumTransactions(
                address = ethereumCoin.address,
                network = ethereumCoin.network
            )

            when (transactionsResult) {
                is Result.Success -> {
                    val transactions = transactionsResult.data
                    Log.d("SyncEthUC", "Received ${transactions.size} transactions from Etherscan")

                    if (transactions.isEmpty()) {
                        Log.d("SyncEthUC", "No transactions found")
                        return@withContext Result.Success(Unit)
                    }

                    // Delete existing transactions for this wallet
                    ethereumTransactionRepository.deleteAllForWallet(walletId)
                    Log.d("SyncEthUC", "Deleted existing transactions")

                    // Save new transactions
                    var savedCount = 0
                    transactions.forEachIndexed { index, tx ->
                        // Determine if this is incoming (to address matches our wallet)
                        val isIncoming = tx.to.equals(ethereumCoin.address, ignoreCase = true)

                        // Determine status from Etherscan fields
                        val status = when {
                            tx.isError == "1" -> TransactionStatus.FAILED
                            tx.receiptStatus == "1" -> TransactionStatus.SUCCESS
                            else -> TransactionStatus.PENDING
                        }

                        // Convert block number
                        val blockNumber = tx.blockNumber.toLongOrNull() ?: 0

                        Log.d("SyncEthUC", "Transaction #$index: ${tx.hash}")
                        Log.d("SyncEthUC", "  isIncoming: $isIncoming")
                        Log.d("SyncEthUC", "  amount: ${tx.value} wei")
                        Log.d("SyncEthUC", "  from: ${tx.from}")
                        Log.d("SyncEthUC", "  to: ${tx.to}")
                        Log.d("SyncEthUC", "  block: $blockNumber")
                        Log.d("SyncEthUC", "  isError: ${tx.isError}")
                        Log.d("SyncEthUC", "  receiptStatus: ${tx.receiptStatus}")
                        Log.d("SyncEthUC", "  status: $status")

                        // Convert to domain model and save
                        val domainTx = tx.toDomain(
                            walletId = walletId,
                            isIncoming = isIncoming,
                            status = status,
                            network = ethereumCoin.network.displayName
                        )
                        ethereumTransactionRepository.saveTransaction(domainTx)
                        savedCount++
                    }

                    Log.d("SyncEthUC", "Successfully saved $savedCount transactions")
                    Log.d("SyncEthUC", "=== Sync completed successfully for wallet $walletId ===")
                    Result.Success(Unit)
                }

                is Result.Error -> {
                    Log.e("SyncEthUC", "Failed to fetch transactions: ${transactionsResult.message}")
                    Result.Error(transactionsResult.message)
                }

                else -> {
                    Log.e("SyncEthUC", "Unknown error fetching transactions")
                    Result.Error("Unknown error")
                }
            }
        } catch (e: Exception) {
            Log.e("SyncEthUC", "Error syncing Ethereum transactions: ${e.message}", e)
            Result.Error(e.message ?: "Sync failed")
        }
    }
}

/**
 * Extension function to convert EtherscanTransaction to domain EthereumTransaction
 */
fun EtherscanTransaction.toDomain(
    walletId: String,
    isIncoming: Boolean,
    status: TransactionStatus,
    network: String
): EthereumTransaction {
    // Convert wei to ETH (1 ETH = 10^18 wei)
    val valueEth = try {
        BigDecimal(value).divide(
            BigDecimal("1000000000000000000"),
            18,
            RoundingMode.HALF_UP
        ).toPlainString()
    } catch (e: Exception) {
        "0"
    }

    // Convert gas price to Gwei (1 Gwei = 10^9 wei)
    val gasPriceGwei = try {
        BigDecimal(gasPrice).divide(
            BigDecimal(1_000_000_000),
            2,
            RoundingMode.HALF_UP
        ).toPlainString()
    } catch (e: Exception) {
        "0"
    }

    // Calculate fee = gas * gasPrice
    val feeWei = try {
        BigInteger(gas).multiply(BigInteger(gasPrice))
    } catch (e: Exception) {
        BigInteger.ZERO
    }

    val feeEth = try {
        BigDecimal(feeWei).divide(
            BigDecimal("1000000000000000000"),
            18,
            RoundingMode.HALF_UP
        ).toPlainString()
    } catch (e: Exception) {
        "0"
    }

    return EthereumTransaction(
        id = "eth_${hash}_${System.currentTimeMillis()}",
        walletId = walletId,
        fromAddress = from,
        toAddress = to,
        status = status,
        timestamp = timestamp.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
        note = null,
        feeLevel = FeeLevel.NORMAL,
        amountWei = value,
        amountEth = valueEth,
        gasPriceWei = gasPrice,
        gasPriceGwei = gasPriceGwei,
        gasLimit = gas.toLongOrNull() ?: 21000L,
        feeWei = feeWei.toString(),
        feeEth = feeEth,
        nonce = 0,
        chainId = 1,
        signedHex = null,
        txHash = hash,
        network = network,
        data = "",
        isIncoming = isIncoming
    )
}


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

            val wallet = walletRepository.getWallet(walletId) ?: run {
                Log.e("SendEthereumUC", "Wallet not found: $walletId")
                return@withContext Result.Error("Wallet not found")
            }

            val ethereumCoin = wallet.ethereum ?: run {
                Log.e("SendEthereumUC", "Ethereum not enabled for wallet: $walletId")
                return@withContext Result.Error("Ethereum not enabled")
            }

            Log.d("SendEthereumUC", "Network: ${ethereumCoin.network.displayName}")

            // 1. Create transaction with network context
            Log.d("SendEthereumUC", "Step 1: Creating transaction...")
            val transaction = createTransaction(walletId, toAddress, amount, feeLevel, note, ethereumCoin.network)
                ?: return@withContext Result.Error("Failed to create transaction")

            Log.d("SendEthereumUC", "Transaction created: ${transaction.id}")

            // 2. Sign transaction
            Log.d("SendEthereumUC", "Step 2: Signing transaction...")
            val signedTransaction = signTransaction(transaction, ethereumCoin.network)
                ?: return@withContext Result.Error("Failed to sign transaction")

            Log.d("SendEthereumUC", "Transaction signed: ${signedTransaction.txHash}")

            // 3. Broadcast transaction
            Log.d("SendEthereumUC", "Step 3: Broadcasting transaction...")
            val broadcastResult = broadcastTransaction(signedTransaction, ethereumCoin.network)

            Log.d("SendEthereumUC", "Broadcast result: success=${broadcastResult.success}, hash=${broadcastResult.hash}")

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
            Result.Error("Send failed: ${e.message}")
        }
    }

    private suspend fun createTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?,
        network: EthereumNetwork
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

            Log.d("SendEthereumUC", "Creating transaction for address: ${ethereumCoin.address} on ${network.displayName}")

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(
                ethereumCoin.address,
                network
            )
            val nonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                else -> {
                    Log.e("SendEthereumUC", "Failed to get nonce")
                    return null
                }
            }

            val feeResult = ethereumBlockchainRepository.getFeeEstimate(feeLevel, network)
            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                else -> {
                    Log.e("SendEthereumUC", "Failed to get fee estimate")
                    return null
                }
            }

            val amountWei = amount.multiply(BigDecimal("1000000000000000000")).toBigInteger()

            return EthereumTransaction(
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
                chainId = network.chainId.toLong(),
                signedHex = null,
                txHash = null,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = network.displayName,
                data = "",
                isIncoming = false
            )
        } catch (e: Exception) {
            Log.e("SendEthereumUC", "Error creating transaction: ${e.message}", e)
            return null
        }
    }

    private suspend fun signTransaction(
        transaction: EthereumTransaction,
        network: EthereumNetwork
    ): EthereumTransaction? {
        try {
            Log.d("SendEthereumUC", "Signing transaction: ${transaction.id}")

            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: run {
                    Log.e("SendEthereumUC", "Wallet not found: ${transaction.walletId}")
                    return null
                }

            val ethereumCoin = wallet.ethereum
                ?: run {
                    Log.e("SendEthereumUC", "Ethereum not enabled for wallet: ${transaction.walletId}")
                    return null
                }

            // Verify network matches
            if (ethereumCoin.network.chainId != network.chainId) {
                Log.e("SendEthereumUC", "Network mismatch: wallet=${ethereumCoin.network.displayName}, transaction=$network")
                return null
            }

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(
                ethereumCoin.address,
                network
            )
            val currentNonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                else -> {
                    Log.e("SendEthereumUC", "Failed to get nonce")
                    return null
                }
            }

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

            val chainId = network.chainId.toLong()
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
            val signedHex = Numeric.toHexString(signedMessage)

            val txHashBytes = Hash.sha3(Numeric.hexStringToByteArray(signedHex))
            val calculatedHash = Numeric.toHexString(txHashBytes)

            return transaction.copy(
                status = TransactionStatus.PENDING,
                txHash = calculatedHash,
                signedHex = signedHex,
                nonce = currentNonce
            )
        } catch (e: Exception) {
            Log.e("SendEthereumUC", "Error signing transaction: ${e.message}", e)
            return null
        }
    }

    private suspend fun broadcastTransaction(
        transaction: EthereumTransaction,
        network: EthereumNetwork
    ): BroadcastResult {
        try {
            if (transaction.signedHex == null) {
                Log.e("SendEthereumUC", "Transaction not signed")
                return BroadcastResult(success = false, error = "Transaction not signed")
            }

            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: return BroadcastResult(success = false, error = "Wallet not found")

            val ethereumCoin = wallet.ethereum
                ?: return BroadcastResult(success = false, error = "Ethereum not enabled")

            // Verify network matches
            if (ethereumCoin.network.chainId != network.chainId) {
                Log.e("SendEthereumUC", "Network mismatch during broadcast")
                return BroadcastResult(success = false, error = "Network mismatch")
            }

            val broadcastResult = ethereumBlockchainRepository.broadcastEthereumTransaction(
                transaction.signedHex,
                network
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
                        transaction.copy(status = TransactionStatus.FAILED)
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
    suspend operator fun invoke(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<EthereumFeeEstimate> {
        return ethereumBlockchainRepository.getFeeEstimate(feeLevel, network)
    }
}