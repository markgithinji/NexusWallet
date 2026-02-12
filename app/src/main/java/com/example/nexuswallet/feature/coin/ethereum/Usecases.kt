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
            val wallet = walletRepository.getWallet(walletId) ?: return Result.failure(
                IllegalArgumentException("Wallet not found")
            )

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
                else -> return Result.failure(IllegalArgumentException("Unsupported wallet type"))
            }

            // Save directly to local data source
            transactionLocalDataSource.saveSendTransaction(transaction)
            Result.success(transaction)
        } catch (e: Exception) {
            Result.failure(e)
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

        val nonce = ethereumBlockchainRepository.getEthereumNonce(wallet.address, wallet.network)
        val gasPrice = ethereumBlockchainRepository.getEthereumGasPrice(wallet.network)
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
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            if (transaction.walletType != WalletType.ETHEREUM) {
                return Result.failure(IllegalArgumentException("Only Ethereum signing supported"))
            }

            signEthereumTransaction(transactionId)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun signEthereumTransaction(
        transactionId: String
    ): Result<SignedTransaction> {
        return try {
            val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            val wallet = walletRepository.getWallet(transaction.walletId) as? EthereumWallet
                ?: return Result.failure(IllegalArgumentException("Ethereum wallet not found"))

            Log.d("SignTxUseCase", "Signing transaction: ${transaction.id}")

            // 1. Get CURRENT nonce
            val currentNonce = ethereumBlockchainRepository.getEthereumNonce(wallet.address, wallet.network)

            // 2. Get CURRENT gas price
            val gasPrice = ethereumBlockchainRepository.getCurrentGasPrice(wallet.network)
            val selectedGasPrice = when (transaction.feeLevel ?: FeeLevel.NORMAL) {
                FeeLevel.SLOW -> gasPrice.safe
                FeeLevel.FAST -> gasPrice.fast
                else -> gasPrice.propose
            }

            // 3. Convert gas price to wei
            val gasPriceWei = (BigDecimal(selectedGasPrice) * BigDecimal("1000000000")).toBigInteger()

            // 4. Get private key
            val privateKeyResult = keyManager.getPrivateKeyForSigning(transaction.walletId)
            if (privateKeyResult.isFailure) {
                return Result.failure(
                    privateKeyResult.exceptionOrNull() ?: IllegalStateException("No private key")
                )
            }
            val privateKey = privateKeyResult.getOrThrow()

            // 5. Create credentials
            val credentials = Credentials.create(privateKey)
            if (credentials.address.lowercase() != wallet.address.lowercase()) {
                return Result.failure(IllegalStateException("Private key doesn't match wallet"))
            }

            // 6. Prepare transaction
            val amountWei = try {
                BigDecimal(transaction.amount).toBigInteger()
            } catch (e: Exception) {
                return Result.failure(IllegalArgumentException("Invalid amount format"))
            }

            val rawTransaction = RawTransaction.createTransaction(
                BigInteger.valueOf(currentNonce.toLong()),
                gasPriceWei,
                BigInteger("21000"),
                transaction.toAddress,
                amountWei,
                ""
            )

            // 7. Sign with chain ID
            val chainId = if (wallet.network == EthereumNetwork.SEPOLIA) 11155111L else 1L
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
            val signedHex = Numeric.toHexString(signedMessage)

            // 8. Calculate hash
            val txHashBytes = Hash.sha3(Numeric.hexStringToByteArray(signedHex))
            val calculatedHash = Numeric.toHexString(txHashBytes)

            // 9. Create signed transaction
            val signedTransaction = SignedTransaction(
                rawHex = signedHex,
                hash = calculatedHash,
                chain = transaction.chain
            )

            // 10. Update transaction with signed data
            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                hash = calculatedHash,
                signedHex = signedHex,
                nonce = currentNonce,
                gasPrice = selectedGasPrice,
                gasLimit = "21000"
            )

            // Save directly to local data source
            transactionLocalDataSource.saveSendTransaction(updatedTransaction)

            Result.success(signedTransaction)

        } catch (e: Exception) {
            Log.e("SignTxUseCase", "Signing failed: ${e.message}", e)
            Result.failure(e)
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
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            when (transaction.chain) {
                ChainType.ETHEREUM_SEPOLIA -> broadcastEthereumTransaction(transactionId)
                else -> Result.success(
                    BroadcastResult(
                        success = false,
                        error = "Chain ${transaction.chain} broadcasting not implemented",
                        chain = transaction.chain
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("BroadcastUseCase", "Broadcast failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun broadcastEthereumTransaction(
        transactionId: String
    ): Result<BroadcastResult> {
        val transaction = transactionLocalDataSource.getSendTransaction(transactionId)
            ?: return Result.failure(IllegalArgumentException("Transaction not found"))

        val signedHex = transaction.signedHex
            ?: return Result.failure(IllegalStateException("Transaction not signed"))

        val wallet = walletRepository.getWallet(transaction.walletId) as? EthereumWallet
            ?: return Result.failure(IllegalArgumentException("Wallet not found"))

        val broadcastResult = ethereumBlockchainRepository.broadcastEthereumTransaction(
            signedHex,
            wallet.network
        )

        val updatedTransaction = if (broadcastResult.success) {
            transaction.copy(
                status = TransactionStatus.SUCCESS,
                hash = broadcastResult.hash ?: transaction.hash
            )
        } else {
            transaction.copy(
                status = TransactionStatus.FAILED
            )
        }

        transactionLocalDataSource.saveSendTransaction(updatedTransaction)

        return Result.success(broadcastResult)
    }
}

@Singleton
class GetTransactionUseCase @Inject constructor(
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(transactionId: String): SendTransaction? {
        return transactionLocalDataSource.getSendTransaction(transactionId)
    }
}

@Singleton
class GetWalletTransactionsUseCase @Inject constructor(
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    fun invoke(walletId: String): Flow<List<SendTransaction>> {
        return transactionLocalDataSource.getSendTransactions(walletId)
    }
}

@Singleton
class GetPendingTransactionsUseCase @Inject constructor(
    private val transactionLocalDataSource: TransactionLocalDataSource
) {
    suspend operator fun invoke(): List<SendTransaction> {
        return transactionLocalDataSource.getPendingTransactions()
    }
}

@Singleton
class ValidateAddressUseCase @Inject constructor() {
    operator fun invoke(address: String, chain: ChainType): Boolean {
        return when (chain) {
            ChainType.BITCOIN -> address.startsWith("1") || address.startsWith("3") || address.startsWith("bc1")
            ChainType.ETHEREUM, ChainType.ETHEREUM_SEPOLIA -> address.startsWith("0x") && address.length == 42
            else -> true
        }
    }
}

@Singleton
class GetFeeEstimateUseCase @Inject constructor() {
    operator fun invoke(chain: ChainType, feeLevel: FeeLevel): FeeEstimate {
        return when (chain) {
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
    }
}