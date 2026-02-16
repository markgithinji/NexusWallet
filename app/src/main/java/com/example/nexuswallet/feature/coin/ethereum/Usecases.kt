package com.example.nexuswallet.feature.coin.ethereum


import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumCoin
import com.example.nexuswallet.feature.wallet.ui.SendResult
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.RoundingMode

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
            Log.d("SendEthereumUC", "========== SEND ETHEREUM START ==========")
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

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(ethereumCoin.address, ethereumCoin.network)
            val nonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Error -> {
                    Log.e("SendEthereumUC", "Failed to get nonce: ${nonceResult.message}")
                    return null
                }
                Result.Loading -> {
                    Log.e("SendEthereumUC", "Timeout getting nonce")
                    return null
                }
            }

            val gasPriceResult = ethereumBlockchainRepository.getEthereumGasPrice(ethereumCoin.network)
            val gasPrice = when (gasPriceResult) {
                is Result.Success -> gasPriceResult.data
                is Result.Error -> {
                    Log.e("SendEthereumUC", "Failed to get gas price: ${gasPriceResult.message}")
                    return null
                }
                Result.Loading -> {
                    Log.e("SendEthereumUC", "Timeout getting gas price")
                    return null
                }
            }

            val selectedFee = when (feeLevel) {
                FeeLevel.SLOW -> gasPrice.slow
                FeeLevel.FAST -> gasPrice.fast
                else -> gasPrice.normal
            }

            val amountWei = amount.multiply(BigDecimal("1000000000000000000")).toBigInteger()
            val gasPriceWei = (BigDecimal(selectedFee.gasPrice) * BigDecimal("1000000000")).toBigInteger()
            val gasLimit = 21000L
            val feeWei = gasPriceWei.multiply(BigInteger.valueOf(gasLimit))
            val feeEth = BigDecimal(feeWei).divide(BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP)

            val transaction = EthereumTransaction(
                id = "tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = ethereumCoin.address,
                toAddress = toAddress,
                amountWei = amountWei.toString(),
                amountEth = amount.toPlainString(),
                gasPriceWei = gasPriceWei.toString(),
                gasPriceGwei = BigDecimal(selectedFee.gasPrice).toPlainString(),
                gasLimit = gasLimit,
                feeWei = feeWei.toString(),
                feeEth = feeEth.toPlainString(),
                nonce = nonce,
                chainId = if (ethereumCoin.network == EthereumNetwork.Sepolia) 11155111L else 1L,
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
                    Log.e("SendEthereumUC", "Ethereum not enabled for wallet: ${transaction.walletId}")
                    return null
                }

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(ethereumCoin.address, ethereumCoin.network)
            val currentNonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Error -> {
                    Log.e("SendEthereumUC", "Failed to get nonce: ${nonceResult.message}")
                    return null
                }
                Result.Loading -> {
                    Log.e("SendEthereumUC", "Failed to get nonce")
                    return null
                }
            }

            val gasPriceResult = ethereumBlockchainRepository.getCurrentGasPrice(ethereumCoin.network)
            val gasPrice = when (gasPriceResult) {
                is Result.Success -> gasPriceResult.data
                is Result.Error -> {
                    Log.e("SendEthereumUC", "Failed to get gas price: ${gasPriceResult.message}")
                    return null
                }
                Result.Loading -> {
                    Log.e("SendEthereumUC", "Failed to get gas price")
                    return null
                }
            }

            val selectedGasPrice = when (transaction.feeLevel) {
                FeeLevel.SLOW -> gasPrice.safe
                FeeLevel.FAST -> gasPrice.fast
                else -> gasPrice.propose
            }

            val gasPriceWei = (BigDecimal(selectedGasPrice) * BigDecimal("1000000000")).toBigInteger()

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
                nonce = currentNonce,
                gasPriceGwei = BigDecimal(selectedGasPrice).toPlainString(),
                gasPriceWei = gasPriceWei.toString()
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

data class SendEthereumResult(
    override val transactionId: String,
    override val txHash: String,
    override val success: Boolean,
    override val error: String? = null
) : SendResult

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
                Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))
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
    suspend operator fun invoke(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<FeeEstimate> {
        return try {

            val gasPriceResult = ethereumBlockchainRepository.getEthereumGasPrice(EthereumNetwork.Sepolia)

            when (gasPriceResult) {
                is Result.Success -> {
                    val gasPrice = gasPriceResult.data

                    // Get the appropriate fee based on level
                    val selectedFee = when (feeLevel) {
                        FeeLevel.SLOW -> gasPrice.slow
                        FeeLevel.FAST -> gasPrice.fast
                        else -> gasPrice.normal
                    }

                    // Convert to FeeEstimate format
                    val estimate = FeeEstimate(
                        feePerByte = null,
                        gasPrice = selectedFee.gasPrice,
                        totalFee = selectedFee.totalFee,
                        totalFeeDecimal = selectedFee.totalFeeDecimal,
                        estimatedTime = when (feeLevel) {
                            FeeLevel.SLOW -> 900
                            FeeLevel.FAST -> 60
                            else -> 300
                        },
                        priority = feeLevel
                    )

                    Result.Success(estimate)
                }
                is Result.Error -> Result.Error(gasPriceResult.message, gasPriceResult.throwable)
                Result.Loading -> Result.Error("Timeout getting gas price", null)
            }
        } catch (e: Exception) {
            Result.Error("Failed to get fee estimate: ${e.message}", e)
        }
    }
}