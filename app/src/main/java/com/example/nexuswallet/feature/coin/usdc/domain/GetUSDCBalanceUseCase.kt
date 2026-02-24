package com.example.nexuswallet.feature.coin.usdc.domain

import android.util.Log
import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUSDTransactionsUseCase @Inject constructor(
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val usdcTransactionRepository: USDCTransactionRepository,
    private val walletRepository: WalletRepository
) {
    suspend operator fun invoke(walletId: String): Result<Unit> = withContext(Dispatchers.IO) {
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

        // Fetch USDC transactions
        val transactionsResult = usdcBlockchainRepository.getUSDCTransactionHistory(
            walletId = walletId,
            address = usdcCoin.address,
            network = usdcCoin.network
        )

        return@withContext when (transactionsResult) {
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
                transactions.forEachIndexed { index, transaction ->
                    Log.d("SyncUSDCUC", "Transaction #$index: ${transaction.txHash?.take(8) ?: "unknown"}...")
                    Log.d("SyncUSDCUC", "  isIncoming: ${transaction.isIncoming}")
                    Log.d("SyncUSDCUC", "  amount: ${transaction.amountDecimal} USDC")
                    Log.d("SyncUSDCUC", "  fee: ${transaction.feeEth} ETH")
                    Log.d("SyncUSDCUC", "  gasPrice: ${transaction.gasPriceGwei} Gwei")
                    Log.d("SyncUSDCUC", "  from: ${transaction.fromAddress.take(8)}...")
                    Log.d("SyncUSDCUC", "  to: ${transaction.toAddress.take(8)}...")

                    usdcTransactionRepository.saveTransaction(transaction)
                    savedCount++
                }

                Log.d("SyncUSDCUC", "Successfully saved $savedCount USDC transactions")
                Log.d("SyncUSDCUC", "=== Sync completed successfully for wallet $walletId ===")
                Result.Success(Unit)
            }

            is Result.Error -> {
                Log.e("SyncUSDCUC", "Failed to fetch transactions: ${transactionsResult.message}")
                Result.Error(transactionsResult.message, transactionsResult.throwable)
            }

            else -> Result.Error("Unknown error")
        }
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
    private val usdcTransactionRepository: USDCTransactionRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository
) {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL
    ): Result<SendUSDCResult> = withContext(Dispatchers.IO) {
        Log.d("SendUSDCUC", "Sending $amount USDC to $toAddress")

        val wallet = walletRepository.getWallet(walletId)
            ?: return@withContext Result.Error("Wallet not found")

        val usdcCoin = wallet.usdc
            ?: return@withContext Result.Error("USDC not enabled for this wallet")

        // Get fee estimate
        val feeResult = usdcBlockchainRepository.getFeeEstimate(feeLevel, usdcCoin.network)
        if (feeResult is Result.Error) {
            return@withContext Result.Error(feeResult.message, feeResult.throwable)
        }
        val feeEstimate = (feeResult as Result.Success).data

        // Get nonce
        val nonceResult = usdcBlockchainRepository.getNonce(usdcCoin.address, usdcCoin.network)
        if (nonceResult is Result.Error) {
            return@withContext Result.Error(nonceResult.message, nonceResult.throwable)
        }
        val nonce = (nonceResult as Result.Success).data

        // Get encrypted private key directly from SecurityPreferencesRepository
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = "ETH_PRIVATE_KEY" // USDC uses Ethereum private key
        ) ?: run {
            Log.e("SendUSDCUC", "No private key found for wallet: $walletId")
            return@withContext Result.Error("Private key not found for wallet")
        }

        val (encryptedHex, iv) = encryptedData

        // Decrypt
        val privateKey = try {
            keyStoreRepository.decryptString(encryptedHex, iv.toHex())
        } catch (e: Exception) {
            Log.e("SendUSDCUC", "Failed to decrypt private key: ${e.message}")
            return@withContext Result.Error("Failed to decrypt private key: ${e.message}")
        }

        // Create and sign USDC transfer
        val createResult = usdcBlockchainRepository.createAndSignUSDCTransfer(
            fromAddress = usdcCoin.address,
            fromPrivateKey = privateKey,
            toAddress = toAddress,
            amount = amount,
            gasPriceWei = BigInteger(feeEstimate.gasPriceWei),
            nonce = nonce,
            chainId = usdcCoin.network.chainId.toLong(),
            network = usdcCoin.network
        )

        if (createResult is Result.Error) {
            return@withContext Result.Error(createResult.message, createResult.throwable)
        }
        val (_, signedHex, txHash) = (createResult as Result.Success).data

        // Create transaction record
        val amountUnits = amount.multiply(BigDecimal("1000000")).toBigInteger()
        val transaction = USDCTransaction(
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

        // Broadcast transaction
        val broadcastResult = usdcBlockchainRepository.broadcastUSDCTransaction(signedHex, usdcCoin.network)
        if (broadcastResult is Result.Error) {
            return@withContext Result.Error(broadcastResult.message, broadcastResult.throwable)
        }
        val broadcastData = (broadcastResult as Result.Success).data

        // Update transaction status
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
    }

    // Helper extension for ByteArray to Hex
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
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

@Singleton
class ValidateUSDCFormUseCase @Inject constructor() {

    data class ValidationResult(
        val isValid: Boolean,
        val isValidAddress: Boolean,
        val hasSufficientBalance: Boolean,
        val hasSufficientGas: Boolean,
        val addressError: String? = null,
        val amountError: String? = null,
        val balanceError: String? = null,
        val gasError: String? = null
    )

    operator fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        usdcBalanceDecimal: BigDecimal,
        ethBalanceDecimal: BigDecimal,
        feeEstimate: USDCFeeEstimate?,
        requireGas: Boolean = true
    ): ValidationResult {

        // Validate address format (Ethereum address)
        val isValidAddress = validateAddress(toAddress)
        val addressError = if (!isValidAddress && toAddress.isNotEmpty())
            "Invalid Ethereum address format" else null

        // Validate amount > 0
        val isAmountValid = amountValue > BigDecimal.ZERO
        val amountError = if (!isAmountValid && amountValue <= BigDecimal.ZERO)
            "Amount must be greater than 0" else null

        // Check sufficient USDC balance
        val hasSufficientBalance = amountValue <= usdcBalanceDecimal && isAmountValid
        val balanceError = if (!hasSufficientBalance && isAmountValid)
            "Insufficient USDC balance" else null

        // Check sufficient ETH for gas
        val requiredEth = if (feeEstimate != null) {
            BigDecimal(feeEstimate.totalFeeEth)
        } else {
            BigDecimal("0.0005") // fallback estimate
        }

        val hasSufficientGas = ethBalanceDecimal >= requiredEth
        val gasError = if (!hasSufficientGas)
            "Insufficient ETH for gas fees (need ${requiredEth.toPlainString()} ETH)"
        else null

        // Overall validation
        val isValid = (toAddress.isEmpty() || isValidAddress) &&
                isAmountValid &&
                hasSufficientBalance &&
                (if (requireGas) hasSufficientGas else true)

        return ValidationResult(
            isValid = isValid,
            isValidAddress = isValidAddress,
            hasSufficientBalance = hasSufficientBalance,
            hasSufficientGas = hasSufficientGas,
            addressError = addressError,
            amountError = amountError,
            balanceError = balanceError,
            gasError = gasError
        )
    }

    private fun validateAddress(address: String): Boolean {
        return address.startsWith("0x") && address.length == 42
    }
}