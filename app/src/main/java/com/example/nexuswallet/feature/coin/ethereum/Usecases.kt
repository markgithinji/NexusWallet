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
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
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
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumCoin
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.RoundingMode

@Singleton
class SendEthereumUseCase @Inject constructor(
    private val createSendTransactionUseCase: CreateSendTransactionUseCase,
    private val signEthereumTransactionUseCase: SignEthereumTransactionUseCase,
    private val broadcastTransactionUseCase: BroadcastTransactionUseCase
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
            val createResult = createSendTransactionUseCase(
                walletId = walletId,
                toAddress = toAddress,
                amount = amount,
                feeLevel = feeLevel,
                note = note
            )

            if (createResult is Result.Error) {
                Log.e("SendEthereumUC", " Create failed: ${createResult.message}")
                return@withContext createResult
            }

            val transaction = (createResult as Result.Success).data
            Log.d("SendEthereumUC", " Transaction created: ${transaction.id}")

            // 2. Sign transaction
            Log.d("SendEthereumUC", "Step 2: Signing transaction...")
            val signResult = signEthereumTransactionUseCase(transaction.id)

            if (signResult is Result.Error) {
                Log.e("SendEthereumUC", " Sign failed: ${signResult.message}")
                return@withContext signResult
            }

            val signedTransaction = (signResult as Result.Success).data
            Log.d("SendEthereumUC", " Transaction signed: ${signedTransaction.txHash}")

            // 3. Broadcast transaction
            Log.d("SendEthereumUC", "Step 3: Broadcasting transaction...")
            val broadcastResult = broadcastTransactionUseCase(transaction.id)

            when (broadcastResult) {
                is Result.Success -> {
                    val result = broadcastResult.data
                    Log.d("SendEthereumUC", " Broadcast result: success=${result.success}, hash=${result.hash}")

                    val sendResult = SendEthereumResult(
                        transactionId = transaction.id,
                        txHash = result.hash ?: signedTransaction.txHash ?: "",
                        success = result.success,
                        error = result.error
                    )

                    Log.d("SendEthereumUC", "========== SEND COMPLETE ==========")
                    Result.Success(sendResult)
                }
                is Result.Error -> {
                    Log.e("SendEthereumUC", " Broadcast failed: ${broadcastResult.message}")
                    broadcastResult
                }
                Result.Loading -> {
                    Log.d("SendEthereumUC", " Broadcast loading...")
                    Result.Error("Broadcast timeout", null)
                }
            }

        } catch (e: Exception) {
            Log.e("SendEthereumUC", " Exception: ${e.message}", e)
            Result.Error("Send failed: ${e.message}", e)
        }
    }
}

data class SendEthereumResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)

@Singleton
class CreateSendTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<EthereumTransaction> {
        return try {
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

            val ethereumCoin = wallet.ethereum
                ?: return Result.Error("Ethereum not enabled for this wallet", IllegalArgumentException("Ethereum not enabled"))

            createEthereumTransaction(wallet.id, ethereumCoin, toAddress, amount, feeLevel, note)
        } catch (e: Exception) {
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }

    private suspend fun createEthereumTransaction(
        walletId: String,
        ethereumCoin: EthereumCoin,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<EthereumTransaction> = withContext(Dispatchers.IO) {
        try {
            Log.d("CreateTxUseCase", "Creating Ethereum transaction")

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(ethereumCoin.address, ethereumCoin.network)
            val nonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Error -> return@withContext Result.Error(
                    "Failed to get nonce: ${nonceResult.message}",
                    nonceResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Timeout getting nonce", null)
            }

            val gasPriceResult = ethereumBlockchainRepository.getEthereumGasPrice(ethereumCoin.network)
            val gasPrice = when (gasPriceResult) {
                is Result.Success -> gasPriceResult.data
                is Result.Error -> return@withContext Result.Error(
                    "Failed to get gas price: ${gasPriceResult.message}",
                    gasPriceResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Timeout getting gas price", null)
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
                chainId = if (ethereumCoin.network == EthereumNetwork.SEPOLIA) 11155111L else 1L,
                signedHex = null,
                txHash = null,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = ethereumCoin.network.name,
                data = ""
            )

            ethereumTransactionRepository.saveTransaction(transaction)
            Result.Success(transaction)

        } catch (e: Exception) {
            Log.e("CreateTxUseCase", "Failed to create transaction", e)
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }
}

@Singleton
class SignEthereumTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val keyManager: KeyManager,
    private val ethereumTransactionRepository: EthereumTransactionRepository
) {
    suspend operator fun invoke(transactionId: String): Result<EthereumTransaction> {
        return try {
            val transaction = ethereumTransactionRepository.getTransaction(transactionId)
                ?: return Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            signEthereumTransaction(transaction)
        } catch (e: Exception) {
            Log.e("SignTxUseCase", "Signing failed", e)
            Result.Error("Signing failed: ${e.message}", e)
        }
    }

    private suspend fun signEthereumTransaction(
        transaction: EthereumTransaction
    ): Result<EthereumTransaction> = withContext(Dispatchers.IO) {
        try {
            val wallet = walletRepository.getWallet(transaction.walletId)
                ?: return@withContext Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

            val ethereumCoin = wallet.ethereum
                ?: return@withContext Result.Error("Ethereum not enabled for this wallet", IllegalArgumentException("Ethereum not enabled"))

            Log.d("SignTxUseCase", "Signing transaction: ${transaction.id}")

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(ethereumCoin.address, ethereumCoin.network)
            val currentNonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Error -> return@withContext Result.Error("Failed to get nonce: ${nonceResult.message}", nonceResult.throwable)
                Result.Loading -> return@withContext Result.Error("Failed to get nonce", null)
            }

            val gasPriceResult = ethereumBlockchainRepository.getCurrentGasPrice(ethereumCoin.network)
            val gasPrice = when (gasPriceResult) {
                is Result.Success -> gasPriceResult.data
                is Result.Error -> return@withContext Result.Error("Failed to get gas price: ${gasPriceResult.message}", gasPriceResult.throwable)
                Result.Loading -> return@withContext Result.Error("Failed to get gas price", null)
            }

            val selectedGasPrice = when (transaction.feeLevel) {
                FeeLevel.SLOW -> gasPrice.safe
                FeeLevel.FAST -> gasPrice.fast
                else -> gasPrice.propose
            }

            val gasPriceWei = (BigDecimal(selectedGasPrice) * BigDecimal("1000000000")).toBigInteger()

            val privateKeyResult = keyManager.getPrivateKeyForSigning(transaction.walletId)
            if (privateKeyResult.isFailure) {
                return@withContext Result.Error(
                    privateKeyResult.exceptionOrNull()?.message ?: "No private key",
                    privateKeyResult.exceptionOrNull()
                )
            }
            val privateKey = privateKeyResult.getOrThrow()

            val credentials = Credentials.create(privateKey)
            if (credentials.address.lowercase() != ethereumCoin.address.lowercase()) {
                return@withContext Result.Error("Private key doesn't match wallet", IllegalStateException("Private key doesn't match wallet"))
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
            Result.Success(updatedTransaction)

        } catch (e: Exception) {
            Log.e("SignTxUseCase", "Signing failed: ${e.message}", e)
            Result.Error("Signing failed: ${e.message}", e)
        }
    }
}

@Singleton
class BroadcastTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository
) {
    suspend operator fun invoke(transactionId: String): Result<BroadcastResult> {
        Log.d("BroadcastUseCase", "Broadcasting transaction: $transactionId")

        return try {
            val transaction = ethereumTransactionRepository.getTransaction(transactionId)
                ?: return Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            broadcastEthereumTransaction(transaction)
        } catch (e: Exception) {
            Log.e("BroadcastUseCase", "Broadcast failed: ${e.message}", e)
            Result.Error("Broadcast failed: ${e.message}", e)
        }
    }

    private suspend fun broadcastEthereumTransaction(
        transaction: EthereumTransaction
    ): Result<BroadcastResult> = withContext(Dispatchers.IO) {
        val signedHex = transaction.signedHex
            ?: return@withContext Result.Error("Transaction not signed", IllegalStateException("Transaction not signed"))

        val wallet = walletRepository.getWallet(transaction.walletId)
            ?: return@withContext Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

        val ethereumCoin = wallet.ethereum
            ?: return@withContext Result.Error("Ethereum not enabled for this wallet", IllegalArgumentException("Ethereum not enabled"))

        val broadcastResult = ethereumBlockchainRepository.broadcastEthereumTransaction(
            signedHex,
            ethereumCoin.network
        )

        return@withContext when (broadcastResult) {
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
                Result.Success(result)
            }
            is Result.Error -> {
                Result.Error(broadcastResult.message, broadcastResult.throwable)
            }
            Result.Loading -> {
                Result.Error("Broadcast timeout", null)
            }
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

            val gasPriceResult = ethereumBlockchainRepository.getEthereumGasPrice(EthereumNetwork.SEPOLIA)

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