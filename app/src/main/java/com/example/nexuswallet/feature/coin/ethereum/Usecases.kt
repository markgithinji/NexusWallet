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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.RoundingMode

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
            val wallet = walletRepository.getWallet(walletId) as? EthereumWallet
                ?: return Result.Error("Ethereum wallet not found", IllegalArgumentException("Wallet not found"))

            createEthereumTransaction(wallet, toAddress, amount, feeLevel, note)
        } catch (e: Exception) {
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }

    private suspend fun createEthereumTransaction(
        wallet: EthereumWallet,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<EthereumTransaction> = withContext(Dispatchers.IO) {
        try {
            Log.d("CreateTxUseCase", "Creating Ethereum transaction")

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(wallet.address, wallet.network)
            val nonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Error -> return@withContext Result.Error(
                    "Failed to get nonce: ${nonceResult.message}",
                    nonceResult.throwable
                )
                Result.Loading -> return@withContext Result.Error("Timeout getting nonce", null)
            }

            val gasPriceResult = ethereumBlockchainRepository.getEthereumGasPrice(wallet.network)
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
                walletId = wallet.id,
                fromAddress = wallet.address,
                toAddress = toAddress,
                amountWei = amountWei,
                amountEth = amount,
                gasPriceWei = gasPriceWei,
                gasPriceGwei = BigDecimal(selectedFee.gasPrice),
                gasLimit = gasLimit,
                feeWei = feeWei,
                feeEth = feeEth,
                nonce = nonce,
                chainId = if (wallet.network == EthereumNetwork.SEPOLIA) 11155111L else 1L,
                signedHex = null,
                txHash = null,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = wallet.network.name,
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
            val wallet = walletRepository.getWallet(transaction.walletId) as? EthereumWallet
                ?: return@withContext Result.Error("Ethereum wallet not found", IllegalArgumentException("Ethereum wallet not found"))

            Log.d("SignTxUseCase", "Signing transaction: ${transaction.id}")

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(wallet.address, wallet.network)
            val currentNonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Error -> return@withContext Result.Error("Failed to get nonce: ${nonceResult.message}", nonceResult.throwable)
                Result.Loading -> return@withContext Result.Error("Failed to get nonce", null)
            }

            val gasPriceResult = ethereumBlockchainRepository.getCurrentGasPrice(wallet.network)
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
            if (credentials.address.lowercase() != wallet.address.lowercase()) {
                return@withContext Result.Error("Private key doesn't match wallet", IllegalStateException("Private key doesn't match wallet"))
            }

            val rawTransaction = RawTransaction.createTransaction(
                BigInteger.valueOf(currentNonce.toLong()),
                gasPriceWei,
                BigInteger.valueOf(transaction.gasLimit),
                transaction.toAddress,
                transaction.amountWei,
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
                gasPriceGwei = BigDecimal(selectedGasPrice),
                gasPriceWei = gasPriceWei
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

        val wallet = walletRepository.getWallet(transaction.walletId) as? EthereumWallet
            ?: return@withContext Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

        val broadcastResult = ethereumBlockchainRepository.broadcastEthereumTransaction(
            signedHex,
            wallet.network
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