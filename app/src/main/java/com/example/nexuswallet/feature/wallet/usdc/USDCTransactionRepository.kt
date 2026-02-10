package com.example.nexuswallet.feature.wallet.usdc

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.repository.EthereumBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class USDCTransactionRepository @Inject constructor(
    private val localDataSource: TransactionLocalDataSource,
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val keyManager: KeyManager,
    private val walletRepository: WalletRepository
) {

    /**
     * Create REAL USDC transfer transaction
     */
    suspend fun createUSDCTransfer(
        walletId: String,
        toAddress: String,
        amount: BigDecimal, // USDC amount (e.g., 10.5)
        note: String? = null
    ): Result<SendTransaction> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDCTxRepo", " Creating REAL USDC transfer")

                // 1. Get wallet
                val wallet = walletRepository.getWallet(walletId) as? EthereumWallet
                    ?: return@withContext Result.failure(IllegalArgumentException("Ethereum wallet not found"))

                // 2. Get USDC balance to validate
                val usdcBalance = usdcBlockchainRepository.getUSDCBalance(wallet.address, wallet.network)
                val availableBalance = BigDecimal(usdcBalance.balanceDecimal)

                if (amount > availableBalance) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Insufficient USDC balance. Available: $availableBalance USDC")
                    )
                }

                // 3. Get gas price from EthereumBlockchainRepository
                val gasPrice = ethereumBlockchainRepository.getCurrentGasPrice(wallet.network)
                val gasPriceGwei = gasPrice.propose

                // 4. Create transaction object
                val transaction = SendTransaction(
                    id = "usdc_tx_${System.currentTimeMillis()}",
                    walletId = walletId,
                    walletType = WalletType.ETHEREUM,
                    fromAddress = wallet.address,
                    toAddress = toAddress,
                    amount = amount.multiply(BigDecimal("1000000")).toBigInteger().toString(),
                    amountDecimal = amount.toPlainString(),
                    fee = "0", // Will be updated after signing
                    feeDecimal = "0", // Will be updated after signing
                    total = "0", // Will be updated after signing
                    totalDecimal = "0", // Will be updated after signing
                    chain = when (wallet.network) {
                        EthereumNetwork.SEPOLIA -> ChainType.ETHEREUM_SEPOLIA
                        else -> ChainType.ETHEREUM
                    },
                    status = TransactionStatus.PENDING,
                    gasPrice = gasPriceGwei,
                    gasLimit = "65000", // Default for ERC-20
                    note = note,
                    feeLevel = FeeLevel.NORMAL,
                    metadata = mapOf(
                        "token" to "USDC",
                        "isTokenTransfer" to "true",
                        "amountUsdc" to amount.toPlainString()
                    )
                )

                // 5. Save to local storage
                localDataSource.saveSendTransaction(transaction)

                Log.d("USDCTxRepo", " USDC transfer created: ${transaction.id}")
                Result.success(transaction)

            } catch (e: Exception) {
                Log.e("USDCTxRepo", " Error creating USDC transfer: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Sign REAL USDC transaction with private key
     */
    suspend fun signUSDCTransaction(transactionId: String): Result<Pair<SendTransaction, String>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDCTxRepo", " Signing REAL USDC transaction: $transactionId")

                // 1. Get transaction
                val transaction = localDataSource.getSendTransaction(transactionId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Transaction not found"))

                // 2. Get wallet
                val wallet = walletRepository.getWallet(transaction.walletId) as? EthereumWallet
                    ?: return@withContext Result.failure(IllegalArgumentException("Wallet not found"))

                // 3. Get private key
                val privateKeyResult = keyManager.getPrivateKeyForSigning(transaction.walletId)
                if (privateKeyResult.isFailure) {
                    return@withContext Result.failure(IllegalStateException("Cannot access private key"))
                }
                val privateKey = privateKeyResult.getOrThrow()

                // 4. Convert amount to BigDecimal
                val amount = BigDecimal(transaction.amountDecimal)

                // 5. Create and sign transaction
                val (rawTransaction, signedHex) = usdcBlockchainRepository.createAndSignUSDCTransfer(
                    fromAddress = wallet.address,
                    fromPrivateKey = privateKey,
                    toAddress = transaction.toAddress,
                    amount = amount,
                    network = wallet.network
                )

                // 6. Calculate actual fees
                val gasPriceWei = rawTransaction.gasPrice
                val gasLimit = rawTransaction.gasLimit
                val feeWei = gasPriceWei.multiply(gasLimit)
                val feeEth = feeWei.toBigDecimal().divide(
                    BigDecimal("1000000000000000000"),
                    8,
                    RoundingMode.HALF_UP
                )

                // 7. Update transaction with real data
                val updatedTransaction = transaction.copy(
                    gasPrice = gasPriceWei.toString(),
                    gasLimit = gasLimit.toString(),
                    fee = feeWei.toString(),
                    feeDecimal = feeEth.toPlainString(),
                    total = feeWei.toString(),
                    totalDecimal = feeEth.toPlainString(),
                    signedHex = signedHex,
                    metadata = transaction.metadata + mapOf(
                        "gasPriceWei" to gasPriceWei.toString(),
                        "gasLimit" to gasLimit.toString(),
                        "rawTransaction" to rawTransaction.toString()
                    )
                )

                // 8. Save updated transaction
                localDataSource.saveSendTransaction(updatedTransaction)

                Log.d("USDCTxRepo", " USDC transaction signed: ${updatedTransaction.id}")
                Result.success(Pair(updatedTransaction, signedHex))

            } catch (e: Exception) {
                Log.e("USDCTxRepo", " Error signing USDC: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Broadcast REAL signed USDC transaction
     */
    suspend fun broadcastUSDCTransaction(transactionId: String): Result<BroadcastResult> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDCTxRepo", " Broadcasting REAL USDC transaction: $transactionId")

                // 1. Get transaction
                val transaction = localDataSource.getSendTransaction(transactionId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Transaction not found"))

                val signedHex = transaction.signedHex
                    ?: return@withContext Result.failure(IllegalStateException("Transaction not signed"))

                // 2. Get wallet for network info
                val wallet = walletRepository.getWallet(transaction.walletId) as? EthereumWallet
                    ?: return@withContext Result.failure(IllegalArgumentException("Wallet not found"))

                // 3. Broadcast transaction
                val broadcastResult = usdcBlockchainRepository.broadcastUSDCTransaction(
                    signedHex = signedHex,
                    network = wallet.network
                )

                // 4. Update transaction status
                val updatedTransaction = transaction.copy(
                    status = if (broadcastResult.success) TransactionStatus.PENDING else TransactionStatus.FAILED,
                    hash = broadcastResult.hash ?: transaction.hash,
                    metadata = transaction.metadata + mapOf(
                        "broadcastTime" to System.currentTimeMillis().toString(),
                        "broadcastSuccess" to broadcastResult.success.toString(),
                        "broadcastError" to (broadcastResult.error ?: "")
                    )
                )
                localDataSource.saveSendTransaction(updatedTransaction)

                Log.d("USDCTxRepo", "USDC broadcast result: success=${broadcastResult.success}, hash=${broadcastResult.hash}")
                Result.success(broadcastResult)

            } catch (e: Exception) {
                Log.e("USDCTxRepo", " Error broadcasting USDC: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Complete USDC transfer flow: Create → Sign → Broadcast
     */
    suspend fun completeUSDCTransfer(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        note: String? = null
    ): Result<BroadcastResult> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Create transaction
                val createResult = createUSDCTransfer(walletId, toAddress, amount, note)
                if (createResult.isFailure) {
                    return@withContext Result.failure(createResult.exceptionOrNull()!!)
                }
                val transaction = createResult.getOrThrow()

                // 2. Sign transaction
                val signResult = signUSDCTransaction(transaction.id)
                if (signResult.isFailure) {
                    return@withContext Result.failure(signResult.exceptionOrNull()!!)
                }

                // 3. Broadcast transaction
                val broadcastResult = broadcastUSDCTransaction(transaction.id)
                broadcastResult

            } catch (e: Exception) {
                Log.e("USDCTxRepo", " Complete USDC transfer failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Get REAL USDC transactions
     */
    suspend fun getUSDCTransactions(walletId: String): List<Transaction> {
        return withContext(Dispatchers.IO) {
            try {
                val wallet = walletRepository.getWallet(walletId) as? EthereumWallet
                    ?: return@withContext emptyList()

                usdcBlockchainRepository.getUSDCTransactions(
                    address = wallet.address,
                    network = wallet.network
                )
            } catch (e: Exception) {
                Log.e("USDCTxRepo", "Error getting USDC transactions: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Get REAL USDC balance
     */
    suspend fun getUSDCBalance(walletId: String): TokenBalance {
        return withContext(Dispatchers.IO) {
            try {
                val wallet = walletRepository.getWallet(walletId) as? EthereumWallet
                    ?: return@withContext createEmptyBalance()

                usdcBlockchainRepository.getUSDCBalance(
                    address = wallet.address,
                    network = wallet.network
                )
            } catch (e: Exception) {
                Log.e("USDCTxRepo", "Error getting USDC balance: ${e.message}")
                createEmptyBalance()
            }
        }
    }

    private fun createEmptyBalance(): TokenBalance {
        return TokenBalance(
            tokenId = "usdc_empty",
            symbol = "USDC",
            name = "USD Coin",
            contractAddress = "",
            balance = "0",
            balanceDecimal = "0",
            usdPrice = 1.0,
            usdValue = 0.0,
            decimals = 6,
            chain = ChainType.ETHEREUM_SEPOLIA
        )
    }
}