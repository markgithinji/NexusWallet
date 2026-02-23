package com.example.nexuswallet.feature.coin.ethereum

import android.util.Log
import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
        Log.d("SyncEthUC", "=== Syncing Ethereum transactions for wallet: $walletId ===")

        // Business logic validation
        val wallet = walletRepository.getWallet(walletId) ?: run {
            Log.e("SyncEthUC", "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        val ethereumCoin = wallet.ethereum ?: run {
            Log.e("SyncEthUC", "Ethereum not enabled for wallet: ${wallet.name}")
            return@withContext Result.Error("Ethereum not enabled")
        }

        Log.d("SyncEthUC", "Wallet: ${wallet.name}, Address: ${ethereumCoin.address}, Network: ${ethereumCoin.network.displayName}")

        return@withContext when (val transactionsResult = ethereumBlockchainRepository.getEthereumTransactions(
            address = ethereumCoin.address,
            network = ethereumCoin.network
        )) {
            is Result.Success -> {
                val transactions = transactionsResult.data
                Log.d("SyncEthUC", "Received ${transactions.size} transactions from Etherscan")

                if (transactions.isEmpty()) {
                    Log.d("SyncEthUC", "No transactions found")
                    return@withContext Result.Success(Unit)
                }

                // Delete existing transactions
                ethereumTransactionRepository.deleteAllForWallet(walletId)
                Log.d("SyncEthUC", "Deleted existing transactions")

                // Save new transactions
                var savedCount = 0
                transactions.forEach { tx ->
                    val isIncoming = tx.to.equals(ethereumCoin.address, ignoreCase = true)
                    val status = when {
                        tx.isError == "1" -> TransactionStatus.FAILED
                        tx.receiptStatus == "1" -> TransactionStatus.SUCCESS
                        else -> TransactionStatus.PENDING
                    }

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

            else -> Result.Error("Unknown error")
        }
    }
}

@Singleton
class GetTransactionUseCase @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository
) {
    suspend operator fun invoke(transactionId: String): Result<EthereumTransaction> {
        // Room operation
        val transaction = ethereumTransactionRepository.getTransaction(transactionId)
        return if (transaction != null) {
            Result.Success(transaction)
        } else {
            Result.Error("Transaction not found")
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
                Result.Success(transactions) as Result<List<EthereumTransaction>>
            }
            .catch { e ->
                // Only catch flow errors
                emit(Result.Error("Failed to load transactions: ${e.message}"))
            }
    }
}

@Singleton
class GetPendingTransactionsUseCase @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository
) {
    suspend operator fun invoke(): Result<List<EthereumTransaction>> {
        val transactions = ethereumTransactionRepository.getPendingTransactions()
        return Result.Success(transactions)
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
class SendEthereumUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendEthereumResult> = withContext(Dispatchers.IO) {
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
        val transactionResult = createTransaction(walletId, toAddress, amount, feeLevel, note, ethereumCoin.network)
        if (transactionResult is Result.Error) return@withContext transactionResult
        val transaction = (transactionResult as Result.Success).data

        Log.d("SendEthereumUC", "Transaction created: ${transaction.id}")

        // 2. Sign transaction
        Log.d("SendEthereumUC", "Step 2: Signing transaction...")
        val signedTransactionResult = signTransaction(transaction, ethereumCoin.network, ethereumCoin.address)
        if (signedTransactionResult is Result.Error) return@withContext signedTransactionResult
        val signedTransaction = (signedTransactionResult as Result.Success).data

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
    }

    private suspend fun createTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?,
        network: EthereumNetwork
    ): Result<EthereumTransaction> {
        val wallet = walletRepository.getWallet(walletId)
            ?: run {
                Log.e("SendEthereumUC", "Wallet not found: $walletId")
                return Result.Error("Wallet not found")
            }

        val ethereumCoin = wallet.ethereum
            ?: run {
                Log.e("SendEthereumUC", "Ethereum not enabled for wallet: $walletId")
                return Result.Error("Ethereum not enabled")
            }

        Log.d("SendEthereumUC", "Creating transaction for address: ${ethereumCoin.address} on ${network.displayName}")

        val feeResult = ethereumBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (feeResult is Result.Error) {
            Log.e("SendEthereumUC", "Failed to get fee estimate")
            return Result.Error(feeResult.message)
        }
        val feeEstimate = (feeResult as Result.Success).data

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
            nonce = 0, // Temporary value, will be updated at signing
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

        return Result.Success(transaction)
    }

    private suspend fun signTransaction(
        transaction: EthereumTransaction,
        network: EthereumNetwork,
        expectedAddress: String
    ): Result<EthereumTransaction> {
        Log.d("SendEthereumUC", "Signing transaction: ${transaction.id}")

        val wallet = walletRepository.getWallet(transaction.walletId)
            ?: run {
                Log.e("SendEthereumUC", "Wallet not found: ${transaction.walletId}")
                return Result.Error("Wallet not found")
            }

        val ethereumCoin = wallet.ethereum
            ?: run {
                Log.e("SendEthereumUC", "Ethereum not enabled for wallet: ${transaction.walletId}")
                return Result.Error("Ethereum not enabled")
            }

        // Get encrypted private key
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = transaction.walletId,
            keyType = "ETH_PRIVATE_KEY"
        ) ?: run {
            Log.e("SendEthereumUC", "No private key found for wallet: ${transaction.walletId}")
            return Result.Error("No private key found")
        }

        val (encryptedHex, iv) = encryptedData

        // Decrypt
        val privateKey = try {
            keyStoreRepository.decryptString(encryptedHex, iv.toHex())
        } catch (e: Exception) {
            Log.e("SendEthereumUC", "Failed to decrypt private key: ${e.message}")
            return Result.Error("Failed to decrypt private key")
        }

        // Get the nonce
        val nonceResult = ethereumBlockchainRepository.getEthereumNonce(
            ethereumCoin.address,
            network
        )

        if (nonceResult is Result.Error) {
            Log.e("SendEthereumUC", "Failed to get nonce")
            return Result.Error(nonceResult.message)
        }
        val currentNonce = (nonceResult as Result.Success).data

        // Log the transaction's stored nonce for debugging
        Log.d("SendEthereumUC", "Transaction stored nonce: ${transaction.nonce}")

        val gasPriceWei = BigInteger(transaction.gasPriceWei)

        val credentials = try {
            Credentials.create(privateKey)
        } catch (e: Exception) {
            Log.e("SendEthereumUC", "Failed to create credentials: ${e.message}")
            return Result.Error("Failed to create credentials")
        }

        // Verify private key matches wallet address
        if (credentials.address.lowercase() != ethereumCoin.address.lowercase()) {
            Log.e("SendEthereumUC", "Private key doesn't match wallet")
            Log.e("SendEthereumUC", "Derived: ${credentials.address}, Expected: ${ethereumCoin.address}")
            return Result.Error("Private key doesn't match wallet")
        }

        // Use currentNonce
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

        // Update the transaction with the correct nonce and save
        val updatedTransaction = transaction.copy(
            status = TransactionStatus.PENDING,
            txHash = calculatedHash,
            signedHex = signedHex,
            nonce = currentNonce
        )

        // Save the updated transaction
        ethereumTransactionRepository.updateTransaction(updatedTransaction)

        Log.d("SendEthereumUC", "Transaction signed successfully with nonce: $currentNonce")
        return Result.Success(updatedTransaction)
    }

    private suspend fun broadcastTransaction(
        transaction: EthereumTransaction,
        network: EthereumNetwork
    ): BroadcastResult {
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
    }

    // Helper extension for ByteArray to Hex
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}