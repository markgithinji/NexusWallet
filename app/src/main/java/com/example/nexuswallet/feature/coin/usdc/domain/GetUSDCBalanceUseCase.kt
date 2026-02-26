package com.example.nexuswallet.feature.coin.usdc.domain

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUSDTransactionsUseCaseImpl @Inject constructor(
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val usdcTransactionRepository: USDCTransactionRepository,
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : SyncUSDTransactionsUseCase {

    private val tag = "SyncUSDCUC"

    override suspend fun invoke(walletId: String): Result<Unit> = withContext(Dispatchers.IO) {
        logger.d(tag, "=== Syncing USDC transactions for wallet: $walletId ===")

        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        val usdcCoin = wallet.usdc
        if (usdcCoin == null) {
            logger.e(tag, "USDC not enabled for wallet: ${wallet.name}")
            return@withContext Result.Error("USDC not enabled")
        }

        logger.d(tag, "Wallet: ${wallet.name}, Address: ${usdcCoin.address}")

        // Fetch USDC transactions
        val transactionsResult = usdcBlockchainRepository.getUSDCTransactionHistory(
            walletId = walletId,
            address = usdcCoin.address,
            network = usdcCoin.network
        )

        return@withContext when (transactionsResult) {
            is Result.Success -> {
                val transactions = transactionsResult.data
                logger.d(tag, "Received ${transactions.size} USDC transactions from Etherscan")

                if (transactions.isEmpty()) {
                    logger.d(tag, "No transactions found")
                    return@withContext Result.Success(Unit)
                }

                // Delete existing transactions for this wallet
                usdcTransactionRepository.deleteAllForWallet(walletId)
                logger.d(tag, "Deleted existing transactions")

                // Save new transactions
                var savedCount = 0
                transactions.forEachIndexed { index, transaction ->
                    logger.d(
                        tag,
                        "Transaction #$index: ${transaction.txHash?.take(8) ?: "unknown"}..."
                    )
                    logger.d(tag, "  isIncoming: ${transaction.isIncoming}")
                    logger.d(tag, "  amount: ${transaction.amountDecimal} USDC")
                    logger.d(tag, "  fee: ${transaction.feeEth} ETH")
                    logger.d(tag, "  gasPrice: ${transaction.gasPriceGwei} Gwei")
                    logger.d(tag, "  from: ${transaction.fromAddress.take(8)}...")
                    logger.d(tag, "  to: ${transaction.toAddress.take(8)}...")

                    usdcTransactionRepository.saveTransaction(transaction)
                    savedCount++
                }

                logger.d(tag, "Successfully saved $savedCount USDC transactions")
                logger.d(tag, "=== Sync completed successfully for wallet $walletId ===")
                Result.Success(Unit)
            }

            is Result.Error -> {
                logger.e(tag, "Failed to fetch transactions: ${transactionsResult.message}")
                Result.Error(transactionsResult.message, transactionsResult.throwable)
            }

            else -> Result.Error("Unknown error")
        }
    }
}

@Singleton
class GetUSDCWalletUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : GetUSDCWalletUseCase {

    private val tag = "GetUSDCWalletUC"

    override suspend fun invoke(walletId: String): Result<USDCWalletInfo> {
        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val usdcCoin = wallet.usdc
        if (usdcCoin == null) {
            logger.e(tag, "USDC not enabled for wallet: ${wallet.name}")
            return Result.Error("USDC not enabled for this wallet")
        }

        logger.d(tag, "Loaded wallet: ${wallet.name}, address: ${usdcCoin.address.take(8)}...")

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
class SendUSDCUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val usdcTransactionRepository: USDCTransactionRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) : SendUSDCUseCase {

    private val tag = "SendUSDCUC"

    override suspend fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel
    ): Result<SendUSDCResult> = withContext(Dispatchers.IO) {
        logger.d(tag, "Sending $amount USDC to $toAddress")

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
            logger.e(tag, "No private key found for wallet: $walletId")
            return@withContext Result.Error("Private key not found for wallet")
        }

        val (encryptedHex, iv) = encryptedData

        // Decrypt
        val privateKey = try {
            keyStoreRepository.decryptString(encryptedHex, iv.toHex())
        } catch (e: Exception) {
            logger.e(tag, "Failed to decrypt private key: ${e.message}")
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
        val broadcastResult =
            usdcBlockchainRepository.broadcastUSDCTransaction(signedHex, usdcCoin.network)
        if (broadcastResult is Result.Error) {
            return@withContext Result.Error(broadcastResult.message, broadcastResult.throwable)
        }
        val broadcastData = (broadcastResult as Result.Success).data

        // Update transaction status
        val updatedTransaction = if (broadcastData.success) {
            transaction.copy(
                status = TransactionStatus.SUCCESS,
                txHash = broadcastData.hash ?: txHash
            )
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
            logger.d(tag, "Send successful: tx ${result.txHash.take(8)}...")
        } else {
            logger.e(tag, "Send failed: ${result.error}")
        }

        Result.Success(result)
    }

    // Helper extension for ByteArray to Hex
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

@Singleton
class GetUSDCBalanceUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val logger: Logger
) : GetUSDCBalanceUseCase {

    private val tag = "GetUSDCBalanceUC"

    override suspend fun invoke(walletId: String): Result<USDCBalance> {
        val wallet = walletRepository.getWallet(walletId)
            ?: return Result.Error("Wallet not found").also {
                logger.e(tag, "Wallet not found: $walletId")
            }

        val usdcCoin = wallet.usdc
            ?: return Result.Error("USDC not enabled for this wallet").also {
                logger.e(tag, "USDC not enabled for wallet: ${wallet.name}")
            }

        val result = usdcBlockchainRepository.getUSDCBalance(usdcCoin.address, usdcCoin.network)
        if (result is Result.Success) {
            logger.d(tag, "Balance: ${result.data.amountDecimal} USDC")
        }
        return result
    }
}

@Singleton
class GetETHBalanceForGasUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val logger: Logger
) : GetETHBalanceForGasUseCase {

    private val tag = "GetETHGasUC"

    override suspend fun invoke(walletId: String): Result<BigDecimal> {
        val wallet = walletRepository.getWallet(walletId)
            ?: return Result.Error("Wallet not found").also {
                logger.e(tag, "Wallet not found: $walletId")
            }

        // Check for Ethereum coin first
        val ethereumCoin = wallet.ethereum
        if (ethereumCoin != null) {
            val result = ethereumBlockchainRepository.getEthereumBalance(
                address = ethereumCoin.address,
                network = ethereumCoin.network
            )
            if (result is Result.Success) {
                logger.d(tag, "ETH balance: ${result.data} ETH")
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
                logger.d(tag, "ETH balance (from USDC address): ${result.data} ETH")
            }
            return result
        }

        logger.d(tag, "No ETH or USDC coin found, returning 0")
        return Result.Success(BigDecimal.ZERO)
    }
}

@Singleton
class GetUSDCFeeEstimateUseCaseImpl @Inject constructor(
    private val usdcBlockchainRepository: USDCBlockchainRepository,
    private val logger: Logger
) : GetUSDCFeeEstimateUseCase {

    private val tag = "GetUSDCFeeUC"

    override suspend fun invoke(
        feeLevel: FeeLevel,
        network: EthereumNetwork
    ): Result<USDCFeeEstimate> {
        val result = usdcBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (result is Result.Error) {
            logger.e(tag, "Failed to get fee estimate: ${result.message}")
        }
        return result
    }
}

@Singleton
class ValidateUSDCFormUseCaseImpl @Inject constructor(
    private val logger: Logger
) : ValidateUSDCFormUseCase {

    private val tag = "ValidateUSDCFormUC"

    override fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        usdcBalanceDecimal: BigDecimal,
        ethBalanceDecimal: BigDecimal,
        feeEstimate: USDCFeeEstimate?,
        requireGas: Boolean
    ): ValidateUSDCFormUseCase.ValidationResult {

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

        if (!isValid) {
            logger.d(
                tag,
                "Validation failed - addressValid: $isValidAddress, amountValid: $isAmountValid, sufficientBalance: $hasSufficientBalance, sufficientGas: $hasSufficientGas"
            )
        }

        return ValidateUSDCFormUseCase.ValidationResult(
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
        return address.startsWith("0x") &&
                address.length == 42 &&
                address.substring(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    }
}