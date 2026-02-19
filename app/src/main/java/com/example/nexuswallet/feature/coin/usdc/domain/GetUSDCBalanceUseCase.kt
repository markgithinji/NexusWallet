package com.example.nexuswallet.feature.coin.usdc.domain

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.Web3jFactory
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.protocol.core.DefaultBlockParameterName
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
import java.math.BigInteger
import javax.inject.Singleton

@Singleton
class SyncUSDTransactionsUseCase @Inject constructor(
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val usdcTransactionRepository: USDCTransactionRepository,
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(walletId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("SyncUSDCUC", "=== Syncing USDC transactions for wallet: $walletId ===")

            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                Log.e("SyncUSDCUC", "Wallet not found: $walletId")
                return@withContext Result.Error("Wallet not found")
            }

            val usdcCoin = wallet.usdc
            if (usdcCoin == null) {
                Log.e("SyncUSDCUC", "USDC not enabled for wallet: ${wallet.name}")
                return@withContext Result.Error("USDC not enabled")
            }

            Log.d("SyncUSDCUC", "Wallet: ${wallet.name}, Address: ${usdcCoin.address}")

            // Fetch USDC transactions from Etherscan
            val transactionsResult = usdcBlockchainRepository.getUSDCTransactionHistory(
                address = usdcCoin.address,
                network = usdcCoin.network
            )

            when (transactionsResult) {
                is Result.Success -> {
                    val transactions = transactionsResult.data
                    Log.d("SyncUSDCUC", "Received ${transactions.size} USDC transactions from Etherscan")

                    if (transactions.isEmpty()) {
                        Log.d("SyncUSDCUC", "No transactions found")
                        return@withContext Result.Success(Unit)
                    }

                    // Delete existing transactions for this wallet
                    usdcTransactionRepository.deleteAllForWallet(walletId)
                    Log.d("SyncUSDCUC", "Deleted existing transactions")

                    // Save new transactions
                    var savedCount = 0
                    transactions.forEachIndexed { index, tx ->
                        // Determine if this is incoming (to address matches our wallet)
                        val isIncoming = tx.to.equals(usdcCoin.address, ignoreCase = true)

                        // USDC transfers are always successful if they appear in the list
                        val status = TransactionStatus.SUCCESS

                        // Parse the value
                        val decimals = tx.tokenDecimal.toIntOrNull() ?: 6
                        val amountDecimal = convertTokenValue(tx.value, decimals)

                        Log.d("SyncUSDCUC", "Transaction #$index: ${tx.hash.take(8)}...")
                        Log.d("SyncUSDCUC", "  isIncoming: $isIncoming")
                        Log.d("SyncUSDCUC", "  amount: ${tx.value} (raw)")
                        Log.d("SyncUSDCUC", "  amountDecimal: $amountDecimal USDC")
                        Log.d("SyncUSDCUC", "  from: ${tx.from.take(8)}...")
                        Log.d("SyncUSDCUC", "  to: ${tx.to.take(8)}...")

                        // Create domain transaction
                        val domainTx = createDomainTransaction(
                            tokenTx = tx,
                            walletId = walletId,
                            isIncoming = isIncoming,
                            amountDecimal = amountDecimal,
                            network = usdcCoin.network,
                            contractAddress = usdcCoin.contractAddress
                        )

                        usdcTransactionRepository.saveTransaction(domainTx)
                        savedCount++
                    }

                    Log.d("SyncUSDCUC", "Successfully saved $savedCount USDC transactions")
                    Log.d("SyncUSDCUC", "=== Sync completed successfully for wallet $walletId ===")
                    Result.Success(Unit)
                }

                is Result.Error -> {
                    Log.e("SyncUSDCUC", "Failed to fetch transactions: ${transactionsResult.message}")
                    Result.Error(transactionsResult.message)
                }

                else -> Result.Error("Unknown error")
            }
        } catch (e: Exception) {
            Log.e("SyncUSDCUC", "Error syncing: ${e.message}", e)
            Result.Error(e.message ?: "Sync failed")
        }
    }

    private fun convertTokenValue(value: String, decimals: Int): String {
        return try {
            val valueBigInt = BigInteger(value)
            BigDecimal(valueBigInt).divide(
                BigDecimal.TEN.pow(decimals),
                decimals,
                RoundingMode.HALF_UP
            ).toPlainString()
        } catch (e: Exception) {
            "0"
        }
    }

    private fun createDomainTransaction(
        tokenTx: TokenTransaction,
        walletId: String,
        isIncoming: Boolean,
        amountDecimal: String,
        network: EthereumNetwork,
        contractAddress: String
    ): USDCSendTransaction {
        val timestamp = tokenTx.timeStamp.toLongOrNull()?.times(1000) ?: System.currentTimeMillis()

        return USDCSendTransaction(
            id = "usdc_${tokenTx.hash}_${System.currentTimeMillis()}",
            walletId = walletId,
            fromAddress = tokenTx.from,
            toAddress = tokenTx.to,
            status = TransactionStatus.SUCCESS,
            timestamp = timestamp,
            note = null,
            feeLevel = FeeLevel.NORMAL,
            amount = tokenTx.value,
            amountDecimal = amountDecimal,
            contractAddress = contractAddress,
            network = network,
            gasPriceWei = tokenTx.gasPrice,
            gasPriceGwei = "0", // Will be parsed from gasPrice if needed
            gasLimit = tokenTx.gas.toLongOrNull() ?: 65000L,
            feeWei = "0", // Can calculate if needed
            feeEth = "0",
            nonce = tokenTx.nonce.toIntOrNull() ?: 0,
            chainId = network.chainId.toLongOrNull() ?: 1L,
            signedHex = null,
            txHash = tokenTx.hash,
            ethereumTransactionId = tokenTx.hash,
            isIncoming = isIncoming
        )
    }
}

@Singleton
class GetUSDCWalletUseCase @Inject constructor(
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(walletId: String): Result<USDCWalletInfo> {
        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            Log.e("GetUSDCWalletUC", "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val usdcCoin = wallet.usdc
        if (usdcCoin == null) {
            Log.e("GetUSDCWalletUC", "USDC not enabled for wallet: ${wallet.name}")
            return Result.Error("USDC not enabled for this wallet")
        }

        Log.d("GetUSDCWalletUC", "Loaded wallet: ${wallet.name}, address: ${usdcCoin.address.take(8)}...")

        return Result.Success(
            USDCWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = usdcCoin.address,
                network = usdcCoin.network,
                contractAddress = usdcCoin.contractAddress
            )
        )
    }
}

@Singleton
class SendUSDCUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val keyManager: KeyManager,
    private val usdcTransactionRepository: USDCTransactionRepository
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL
    ): Result<SendUSDCResult> = withContext(Dispatchers.IO) {
        try {
            Log.d("SendUSDCUC", "Sending $amount USDC to $toAddress")

            val wallet = walletRepository.getWallet(walletId)
                ?: return@withContext Result.Error("Wallet not found")

            val usdcCoin = wallet.usdc
                ?: return@withContext Result.Error("USDC not enabled for this wallet")

            val feeResult = usdcBlockchainRepository.getFeeEstimate(feeLevel, usdcCoin.network)
            val feeEstimate = when (feeResult) {
                is Result.Success -> feeResult.data
                is Result.Error -> return@withContext Result.Error(feeResult.message, feeResult.throwable)
                Result.Loading -> return@withContext Result.Error("Failed to get fee estimate: timeout")
            }

            val nonceResult = usdcBlockchainRepository.getNonce(usdcCoin.address, usdcCoin.network)
            val nonce = when (nonceResult) {
                is Result.Success -> nonceResult.data
                is Result.Error -> return@withContext Result.Error(nonceResult.message, nonceResult.throwable)
                Result.Loading -> return@withContext Result.Error("Failed to get nonce: timeout")
            }

            val privateKeyResult = keyManager.getPrivateKeyForSigning(walletId)
            if (privateKeyResult.isFailure) {
                Log.e("SendUSDCUC", "Failed to access private key")
                return@withContext Result.Error("Cannot access private key", privateKeyResult.exceptionOrNull())
            }

            val createResult = usdcBlockchainRepository.createAndSignUSDCTransfer(
                fromAddress = usdcCoin.address,
                fromPrivateKey = privateKeyResult.getOrThrow(),
                toAddress = toAddress,
                amount = amount,
                gasPriceWei = BigInteger(feeEstimate.gasPriceWei),
                nonce = nonce,
                chainId = usdcCoin.network.chainId.toLong(),
                network = usdcCoin.network
            )

            val (_, signedHex, txHash) = when (createResult) {
                is Result.Success -> createResult.data
                is Result.Error -> return@withContext Result.Error(createResult.message, createResult.throwable)
                Result.Loading -> return@withContext Result.Error("Failed to create USDC transfer: timeout")
            }

            val amountUnits = amount.multiply(BigDecimal("1000000")).toBigInteger()
            val transaction = USDCSendTransaction(
                id = "usdc_tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = usdcCoin.address,
                toAddress = toAddress,
                status = TransactionStatus.PENDING,
                timestamp = System.currentTimeMillis(),
                note = null,
                feeLevel = feeLevel,
                amount = amountUnits.toString(),
                amountDecimal = amount.toPlainString(),
                contractAddress = usdcCoin.contractAddress,
                network = usdcCoin.network,
                gasPriceWei = feeEstimate.gasPriceWei,
                gasPriceGwei = feeEstimate.gasPriceGwei,
                gasLimit = feeEstimate.gasLimit,
                feeWei = feeEstimate.totalFeeWei,
                feeEth = feeEstimate.totalFeeEth,
                nonce = nonce.toInt(),
                chainId = usdcCoin.network.chainId.toLong(),
                signedHex = signedHex,
                txHash = txHash
            )

            usdcTransactionRepository.saveTransaction(transaction)

            val broadcastResult = usdcBlockchainRepository.broadcastUSDCTransaction(signedHex, usdcCoin.network)
            val broadcastData = when (broadcastResult) {
                is Result.Success -> broadcastResult.data
                is Result.Error -> return@withContext Result.Error(broadcastResult.message, broadcastResult.throwable)
                Result.Loading -> return@withContext Result.Error("Broadcast timeout")
            }

            val updatedTransaction = if (broadcastData.success) {
                transaction.copy(status = TransactionStatus.SUCCESS, txHash = broadcastData.hash ?: txHash)
            } else {
                transaction.copy(status = TransactionStatus.FAILED)
            }
            usdcTransactionRepository.updateTransaction(updatedTransaction)

            val result = SendUSDCResult(
                transactionId = transaction.id,
                txHash = broadcastData.hash ?: txHash,
                success = broadcastData.success,
                error = broadcastData.error
            )

            if (result.success) {
                Log.d("SendUSDCUC", "Send successful: tx ${result.txHash.take(8)}...")
            } else {
                Log.e("SendUSDCUC", "Send failed: ${result.error}")
            }

            Result.Success(result)

        } catch (e: Exception) {
            Log.e("SendUSDCUC", "Send failed: ${e.message}")
            Result.Error("Send failed: ${e.message}", e)
        }
    }
}

@Singleton
class GetUSDCBalanceUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository
) {
    suspend operator fun invoke(walletId: String): Result<USDCBalance> {
        val wallet = walletRepository.getWallet(walletId)
            ?: return Result.Error("Wallet not found").also {
                Log.e("GetUSDCBalanceUC", "Wallet not found: $walletId")
            }

        val usdcCoin = wallet.usdc
            ?: return Result.Error("USDC not enabled for this wallet").also {
                Log.e("GetUSDCBalanceUC", "USDC not enabled for wallet: ${wallet.name}")
            }

        val result = usdcBlockchainRepository.getUSDCBalance(usdcCoin.address, usdcCoin.network)
        if (result is Result.Success) {
            Log.d("GetUSDCBalanceUC", "Balance: ${result.data.amountDecimal} USDC")
        }
        return result
    }
}

@Singleton
class GetETHBalanceForGasUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) {
    suspend operator fun invoke(walletId: String): Result<BigDecimal> {
        val wallet = walletRepository.getWallet(walletId)
            ?: return Result.Error("Wallet not found").also {
                Log.e("GetETHGasUC", "Wallet not found: $walletId")
            }

        // Check for Ethereum coin first
        val ethereumCoin = wallet.ethereum
        if (ethereumCoin != null) {
            val result = ethereumBlockchainRepository.getEthereumBalance(
                address = ethereumCoin.address,
                network = ethereumCoin.network
            )
            if (result is Result.Success) {
                Log.d("GetETHGasUC", "ETH balance: ${result.data} ETH")
            }
            return result
        }

        // Fallback to USDC coin
        val usdcCoin = wallet.usdc
        if (usdcCoin != null) {
            val result = ethereumBlockchainRepository.getEthereumBalance(
                address = usdcCoin.address,
                network = usdcCoin.network
            )
            if (result is Result.Success) {
                Log.d("GetETHGasUC", "ETH balance (from USDC address): ${result.data} ETH")
            }
            return result
        }

        Log.d("GetETHGasUC", "No ETH or USDC coin found, returning 0")
        return Result.Success(BigDecimal.ZERO)
    }
}

@Singleton
class GetUSDCFeeEstimateUseCase @Inject constructor(
    private val usdcBlockchainRepository: USDCBlockchainRepository
) {
    suspend operator fun invoke(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<USDCFeeEstimate> {
        val result = usdcBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (result is Result.Error) {
            Log.e("GetUSDCFeeUC", "Failed to get fee estimate: ${result.message}")
        }
        return result
    }
}