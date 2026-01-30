package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.feature.authentication.domain.SecurityManager
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SignedTransaction
import com.example.nexuswallet.feature.wallet.data.model.ValidationResult
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.math.BigDecimal

class TransactionRepository(
    private val localDataSource: TransactionLocalDataSource,
    private val blockchainRepository: BlockchainRepository,
    private val walletRepository: WalletRepository
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun createSendTransaction(
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

            when (wallet) {
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

                else -> Result.failure(IllegalArgumentException("Unsupported wallet type"))
            }
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
    ): Result<SendTransaction> {
        return try {
            // Convert amount to satoshis
            val amountSat = amount.multiply(BigDecimal("100000000")).toLong()

            // Get fee estimate
            val feeEstimate = blockchainRepository.getBitcoinFeeEstimates()
            val selectedFee = when (feeLevel) {
                FeeLevel.SLOW -> feeEstimate.slow
                FeeLevel.FAST -> feeEstimate.fast
                else -> feeEstimate.normal
            }

            // For demo, use simple fee calculation
            val feeSat = selectedFee.totalFee.toLong()

            // Create transaction
            val transaction = SendTransaction(
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
                note = note
            )

            // Save to local storage
            localDataSource.saveSendTransaction(transaction)

            Result.success(transaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createEthereumTransaction(
        wallet: EthereumWallet,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SendTransaction> {
        return try {
            // Convert amount to wei
            val amountWei = amount.multiply(BigDecimal("1000000000000000000"))

            // Get gas price
            val gasPrice = blockchainRepository.getEthereumGasPrice()
            val selectedGasPrice = when (feeLevel) {
                FeeLevel.SLOW -> gasPrice.slow
                FeeLevel.FAST -> gasPrice.fast
                else -> gasPrice.normal
            }

            // Standard gas limit for ETH transfer
            val gasLimit = "21000"
            val gasPriceGwei = BigDecimal(selectedGasPrice.gasPrice ?: "30")
            val gasPriceWei = gasPriceGwei.multiply(BigDecimal("1000000000"))

            // Calculate fee in wei
            val feeWei = gasPriceWei.multiply(BigDecimal(gasLimit))

            // Create transaction
            val transaction = SendTransaction(
                id = "tx_${System.currentTimeMillis()}",
                walletId = wallet.id,
                walletType = WalletType.ETHEREUM,
                fromAddress = wallet.address,
                toAddress = toAddress,
                amount = amountWei.toPlainString(),
                amountDecimal = amount.toPlainString(),
                fee = feeWei.toPlainString(),
                feeDecimal = feeWei.divide(BigDecimal("1000000000000000000")).toPlainString(),
                total = (amountWei + feeWei).toPlainString(),
                totalDecimal = (amount + feeWei.divide(BigDecimal("1000000000000000000"))).toPlainString(),
                chain = ChainType.ETHEREUM,
                status = TransactionStatus.PENDING,
                note = note,
                gasPrice = selectedGasPrice.gasPrice,
                gasLimit = gasLimit
            )

            // Save to local storage
            localDataSource.saveSendTransaction(transaction)

            Result.success(transaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // SIGN TRANSACTION (DEMO - No real signing)
    suspend fun signTransaction(transactionId: String): Result<SignedTransaction> {
        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            // Create mock signed transaction for demo
            val mockHash = when (transaction.chain) {
                ChainType.BITCOIN -> "btc_mock_${System.currentTimeMillis()}"
                ChainType.ETHEREUM -> "eth_mock_${System.currentTimeMillis()}"
                else -> "mock_${System.currentTimeMillis()}"
            }

            val mockRawTx = "mock_raw_transaction_${System.currentTimeMillis()}"

            val signedTransaction = SignedTransaction(
                rawHex = mockRawTx,
                hash = mockHash,
                chain = transaction.chain
            )

            // Update transaction with mock hash
            val updatedTransaction = transaction.copy(
                status = TransactionStatus.PENDING,
                hash = mockHash,
                signedHex = mockRawTx
            )
            localDataSource.saveSendTransaction(updatedTransaction)

            Result.success(signedTransaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // BROADCAST TRANSACTION (DEMO - No real broadcast)
    suspend fun broadcastTransaction(transactionId: String): Result<BroadcastResult> {
        return try {
            val transaction = localDataSource.getSendTransaction(transactionId)
                ?: return Result.failure(IllegalArgumentException("Transaction not found"))

            // Simulate network delay
            delay(1000)

            // Mock successful broadcast
            val broadcastResult = BroadcastResult(
                success = true,
                hash = transaction.hash ?: "broadcast_${System.currentTimeMillis()}",
                chain = transaction.chain
            )

            // Update transaction status
            val updatedTransaction = transaction.copy(
                status = TransactionStatus.SUCCESS
            )
            localDataSource.saveSendTransaction(updatedTransaction)

            Result.success(broadcastResult)
        } catch (e: Exception) {
            // Simulate failure
            val transaction = localDataSource.getSendTransaction(transactionId)
            if (transaction != null) {
                val failedTransaction = transaction.copy(
                    status = TransactionStatus.FAILED
                )
                localDataSource.saveSendTransaction(failedTransaction)
            }

            Result.failure(e)
        }
    }

    // VALIDATION
    suspend fun validateAddress(address: String, chain: ChainType): Boolean {
        return when (chain) {
            ChainType.BITCOIN -> address.startsWith("1") || address.startsWith("3") || address.startsWith("bc1")
            ChainType.ETHEREUM -> address.startsWith("0x") && address.length == 42
            else -> true
        }
    }

    suspend fun validateAmount(
        walletId: String,
        amount: BigDecimal,
        chain: ChainType
    ): ValidationResult {
        return try {
            // Get the REAL wallet balance from wallet repository
            val wallet = walletRepository.getWallet(walletId) ?: return ValidationResult.Error("Wallet not found")

            // Get the balance from database/blockchain
            val balance = when (wallet) {
                is BitcoinWallet -> {
                    // Try to get balance from blockchain
                    blockchainRepository.getBitcoinBalance(wallet.address)
                }
                is EthereumWallet -> {
                    blockchainRepository.getEthereumBalance(wallet.address)
                }
                else -> {
                    // For other wallets, get from database or use demo
                    val storedBalance = walletRepository.getWalletBalance(walletId)
                    storedBalance?.nativeBalanceDecimal?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                }
            }

            // Get fee estimate
            val feeEstimate = getFeeEstimate(chain, FeeLevel.NORMAL)
            val estimatedFee = feeEstimate.totalFeeDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO

            ValidationResult.Valid(
                balance = balance,
                estimatedFee = estimatedFee
            )
        } catch (e: Exception) {
            ValidationResult.Error("Failed to validate: ${e.message}")
        }
    }

    // FEE ESTIMATES
    suspend fun getFeeEstimate(chain: ChainType, feeLevel: FeeLevel): FeeEstimate {
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
            ChainType.ETHEREUM -> when (feeLevel) {
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

    // LOCAL DATA OPERATIONS
    suspend fun getSendTransaction(transactionId: String): SendTransaction? {
        return localDataSource.getSendTransaction(transactionId)
    }

    fun getSendTransactions(walletId: String): Flow<List<SendTransaction>> {
        return localDataSource.getSendTransactions(walletId)
    }

    suspend fun updateTransactionStatus(
        transactionId: String,
        status: TransactionStatus,
        hash: String? = null
    ) {
        val transaction = localDataSource.getSendTransaction(transactionId) ?: return
        val updated = transaction.copy(status = status, hash = hash)
        localDataSource.saveSendTransaction(updated)
    }

    suspend fun deleteTransaction(transactionId: String) {
        localDataSource.deleteSendTransaction(transactionId)
    }
}