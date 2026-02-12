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


@Singleton
class CreateSendTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendTransaction> {
        return try {
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

            val transaction = when (wallet) {
                is BitcoinWallet -> createBitcoinTransaction(
                    wallet = wallet,
                    toAddress = toAddress,
                    amount = amount,
                    feeLevel = feeLevel,
                    note = note
                )
                is EthereumWallet -> createEthereumTransaction(
                    wallet = wallet,
                    toAddress = toAddress,
                    amount = amount,
                    feeLevel = feeLevel,
                    note = note
                )
                else -> return Result.Error(
                    "Unsupported wallet type: ${wallet.walletType}",
                    IllegalArgumentException("Unsupported wallet type")
                )
            }

            transactionLocalDataSource.saveSendTransaction(transaction)
            Result.Success(transaction)
        } catch (e: Exception) {
            Log.e("CreateTxUseCase", "Failed to create transaction", e)
            Result.Error("Failed to create transaction: ${e.message}", e)
        }
    }

    private suspend fun createBitcoinTransaction(
        wallet: BitcoinWallet,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): SendTransaction {
        val amountSat = amount.multiply(BigDecimal("100000000")).toLong()
        val feeEstimate = ethereumBlockchainRepository.getBitcoinFeeEstimates()
        val selectedFee = when (feeLevel) {
            FeeLevel.SLOW -> feeEstimate.slow
            FeeLevel.FAST -> feeEstimate.fast
            else -> feeEstimate.normal
        }
        val feeSat = selectedFee.totalFee.toLong()

        return SendTransaction(
            id = "tx_${System.currentTimeMillis()}",
            walletId = wallet.id,
            walletType = WalletType.BITCOIN,
            fromAddress = wallet.address,
            toAddress = toAddress,
            amount = amountSat.toString(),
            amountDecimal = amount.toPlainString(),
            fee = feeSat.toString(),
            feeDecimal = selectedFee.totalFeeDecimal,
            total = (amountSat + feeSat).toString(),
            totalDecimal = (amount + BigDecimal(selectedFee.totalFeeDecimal)).toPlainString(),
            chain = ChainType.BITCOIN,
            status = TransactionStatus.PENDING,
            note = note,
            timestamp = System.currentTimeMillis(),
            feeLevel = feeLevel
        )
    }

    private suspend fun createEthereumTransaction(
        wallet: EthereumWallet,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): SendTransaction {
        Log.d("CreateTxUseCase", "Creating Ethereum transaction (local only)")

        val nonceResult = ethereumBlockchainRepository.getEthereumNonce(wallet.address, wallet.network)
        val nonce = when (nonceResult) {
            is Result.Success -> nonceResult.data
            else -> 0
        }

        val gasPriceResult = ethereumBlockchainRepository.getEthereumGasPrice(wallet.network)
        val gasPrice = when (gasPriceResult) {
            is Result.Success -> gasPriceResult.data
            else -> ethereumBlockchainRepository.getDemoEthereumFees()
        }

        val selectedFee = when (feeLevel) {
            FeeLevel.SLOW -> gasPrice.slow
            FeeLevel.FAST -> gasPrice.fast
            else -> gasPrice.normal
        }

        val amountWei = amount.multiply(BigDecimal("1000000000000000000")).toBigInteger()

        return SendTransaction(
            id = "tx_${System.currentTimeMillis()}",
            walletId = wallet.id,
            walletType = WalletType.ETHEREUM,
            fromAddress = wallet.address,
            toAddress = toAddress,
            amount = amountWei.toString(),
            amountDecimal = amount.toPlainString(),
            fee = selectedFee.totalFee,
            feeDecimal = selectedFee.totalFeeDecimal,
            total = (amountWei + BigDecimal(selectedFee.totalFee).toBigInteger()).toString(),
            totalDecimal = (amount + BigDecimal(selectedFee.totalFeeDecimal)).toPlainString(),
            chain = if (wallet.network == EthereumNetwork.SEPOLIA) ChainType.ETHEREUM_SEPOLIA else ChainType.ETHEREUM,
            status = TransactionStatus.PENDING,
            note = note,
            gasPrice = selectedFee.gasPrice,
            gasLimit = "21000",
            signedHex = null,
            nonce = nonce,
            hash = null,
            timestamp = System.currentTimeMillis(),
            feeLevel = feeLevel
        )
    }
}
@Singleton
class SignEthereumTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val keyManager: KeyManager,
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(transactionId: String): Result<SignedTransaction> {
        return try {
            val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                ?: return Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            if (transaction.walletType != WalletType.ETHEREUM) {
                return Result.Error(
                    "Only Ethereum signing supported",
                    IllegalArgumentException("Only Ethereum signing supported")
                )
            }

            signEthereumTransaction(transactionId)
        } catch (e: Exception) {
            Log.e("SignTxUseCase", "Signing failed", e)
            Result.Error("Signing failed: ${e.message}", e)
        }
    }

    private suspend fun signEthereumTransaction(
        transactionId: String
    ): Result<SignedTransaction> {
        return try {
            val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                ?: return Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            val wallet = walletRepository.getWallet(transaction.walletId) as? EthereumWallet
                ?: return Result.Error("Ethereum wallet not found", IllegalArgumentException("Ethereum wallet not found"))

            Log.d("SignTxUseCase", "Signing transaction: ${transaction.id}")

            val nonceResult = ethereumBlockchainRepository.getEthereumNonce(wallet.address, wallet.network)
            val currentNonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Error -> return Result.Error("Failed to get nonce: ${nonceResult.message}", nonceResult.throwable)
                Result.Loading -> return Result.Error("Failed to get nonce", null)
            }

            val gasPriceResult = ethereumBlockchainRepository.getCurrentGasPrice(wallet.network)
            val gasPrice = when (gasPriceResult) {
                is Result.Success -> gasPriceResult.data
                is Result.Error -> return Result.Error("Failed to get gas price: ${gasPriceResult.message}", gasPriceResult.throwable)
                Result.Loading -> return Result.Error("Failed to get gas price", null)
            }

            val selectedGasPrice = when (transaction.feeLevel ?: FeeLevel.NORMAL) {
                FeeLevel.SLOW -> gasPrice.safe
                FeeLevel.FAST -> gasPrice.fast
                else -> gasPrice.propose
            }

            val gasPriceWei = (BigDecimal(selectedGasPrice) * BigDecimal("1000000000")).toBigInteger()

            val privateKeyResult = keyManager.getPrivateKeyForSigning(transaction.walletId)
            if (privateKeyResult.isFailure) {
                return Result.Error(
                    privateKeyResult.exceptionOrNull()?.message ?: "No private key",
                    privateKeyResult.exceptionOrNull()
                )
            }
            val privateKey = privateKeyResult.getOrThrow()

            val credentials = Credentials.create(privateKey)
            if (credentials.address.lowercase() != wallet.address.lowercase()) {
                return Result.Error("Private key doesn't match wallet", IllegalStateException("Private key doesn't match wallet"))
            }

            val amountWei = try {
                BigDecimal(transaction.amount).toBigInteger()
            } catch (e: Exception) {
                return Result.Error("Invalid amount format", e)
            }

            val rawTransaction = RawTransaction.createTransaction(
                BigInteger.valueOf(currentNonce.toLong()),
                gasPriceWei,
                BigInteger("21000"),
                transaction.toAddress,
                amountWei,
                ""
            )

            val chainId = if (wallet.network == EthereumNetwork.SEPOLIA) 11155111L else 1L
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
            val signedHex = Numeric.toHexString(signedMessage)

            val txHashBytes = Hash.sha3(Numeric.hexStringToByteArray(signedHex))
            val calculatedHash = Numeric.toHexString(txHashBytes)

            val signedTransaction = SignedTransaction(
                rawHex = signedHex,
                hash = calculatedHash,
                chain = transaction.chain
            )

            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                hash = calculatedHash,
                signedHex = signedHex,
                nonce = currentNonce,
                gasPrice = selectedGasPrice,
                gasLimit = "21000"
            )

            transactionLocalDataSource.saveSendTransaction(updatedTransaction)
            Result.Success(signedTransaction)

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
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(transactionId: String): Result<BroadcastResult> {
        Log.d("BroadcastUseCase", "Broadcasting transaction: $transactionId")

        return try {
            val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                ?: return Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

            when (transaction.chain) {
                ChainType.ETHEREUM_SEPOLIA -> broadcastEthereumTransaction(transactionId)
                else -> Result.Success(
                    BroadcastResult(
                        success = false,
                        error = "Chain ${transaction.chain} broadcasting not implemented",
                        chain = transaction.chain
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("BroadcastUseCase", "Broadcast failed: ${e.message}", e)
            Result.Error("Broadcast failed: ${e.message}", e)
        }
    }

    private suspend fun broadcastEthereumTransaction(
        transactionId: String
    ): Result<BroadcastResult> {
        val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
            ?: return Result.Error("Transaction not found", IllegalArgumentException("Transaction not found"))

        val signedHex = transaction.signedHex
            ?: return Result.Error("Transaction not signed", IllegalStateException("Transaction not signed"))

        val wallet = walletRepository.getWallet(transaction.walletId) as? EthereumWallet
            ?: return Result.Error("Wallet not found", IllegalArgumentException("Wallet not found"))

        val broadcastResult = ethereumBlockchainRepository.broadcastEthereumTransaction(
            signedHex,
            wallet.network
        )

        return when (broadcastResult) {
            is Result.Success -> {
                val result = broadcastResult.data
                val updatedTransaction = if (result.success) {
                    transaction.copy(
                        status = TransactionStatus.SUCCESS,
                        hash = result.hash ?: transaction.hash
                    )
                } else {
                    transaction.copy(
                        status = TransactionStatus.FAILED
                    )
                }
                transactionLocalDataSource.saveSendTransaction(updatedTransaction)
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
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(transactionId: String): Result<SendTransaction> {
        return try {
            val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
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
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    fun invoke(walletId: String): Flow<Result<List<SendTransaction>>> {
        return transactionLocalDataSource.getSendTransactions(walletId)
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
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(): Result<List<SendTransaction>> {
        return try {
            val transactions = transactionLocalDataSource.getPendingTransactions()
            Result.Success(transactions)
        } catch (e: Exception) {
            Result.Error("Failed to get pending transactions: ${e.message}", e)
        }
    }
}

@Singleton
class ValidateAddressUseCase @Inject constructor() {
    operator fun invoke(address: String, chain: ChainType): Result<Boolean> {
        return try {
            val isValid = when (chain) {
                ChainType.BITCOIN -> address.startsWith("1") || address.startsWith("3") || address.startsWith("bc1")
                ChainType.ETHEREUM, ChainType.ETHEREUM_SEPOLIA -> address.startsWith("0x") && address.length == 42
                else -> true
            }
            Result.Success(isValid)
        } catch (e: Exception) {
            Result.Error("Address validation failed: ${e.message}", e)
        }
    }
}

@Singleton
class GetFeeEstimateUseCase @Inject constructor() {
    operator fun invoke(chain: ChainType, feeLevel: FeeLevel): Result<FeeEstimate> {
        return try {
            val estimate = when (chain) {
                ChainType.BITCOIN -> when (feeLevel) {
                    FeeLevel.SLOW -> FeeEstimate(
                        feePerByte = "10",
                        gasPrice = null,
                        totalFee = "2000",
                        totalFeeDecimal = "0.00002",
                        estimatedTime = 3600,
                        priority = FeeLevel.SLOW
                    )
                    FeeLevel.FAST -> FeeEstimate(
                        feePerByte = "50",
                        gasPrice = null,
                        totalFee = "10000",
                        totalFeeDecimal = "0.0001",
                        estimatedTime = 120,
                        priority = FeeLevel.FAST
                    )
                    else -> FeeEstimate(
                        feePerByte = "25",
                        gasPrice = null,
                        totalFee = "5000",
                        totalFeeDecimal = "0.00005",
                        estimatedTime = 600,
                        priority = FeeLevel.NORMAL
                    )
                }
                ChainType.ETHEREUM, ChainType.ETHEREUM_SEPOLIA -> when (feeLevel) {
                    FeeLevel.SLOW -> FeeEstimate(
                        feePerByte = null,
                        gasPrice = "20",
                        totalFee = "420000000000000",
                        totalFeeDecimal = "0.00042",
                        estimatedTime = 900,
                        priority = FeeLevel.SLOW
                    )
                    FeeLevel.FAST -> FeeEstimate(
                        feePerByte = null,
                        gasPrice = "50",
                        totalFee = "1050000000000000",
                        totalFeeDecimal = "0.00105",
                        estimatedTime = 60,
                        priority = FeeLevel.FAST
                    )
                    else -> FeeEstimate(
                        feePerByte = null,
                        gasPrice = "30",
                        totalFee = "630000000000000",
                        totalFeeDecimal = "0.00063",
                        estimatedTime = 300,
                        priority = FeeLevel.NORMAL
                    )
                }
                else -> FeeEstimate(
                    feePerByte = null,
                    gasPrice = null,
                    totalFee = "0",
                    totalFeeDecimal = "0",
                    estimatedTime = 60,
                    priority = FeeLevel.NORMAL
                )
            }
            Result.Success(estimate)
        } catch (e: Exception) {
            Result.Error("Failed to get fee estimate: ${e.message}", e)
        }
    }
}