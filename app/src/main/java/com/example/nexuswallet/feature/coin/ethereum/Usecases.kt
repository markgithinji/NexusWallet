package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEthereumTransactionsUseCaseImpl @Inject constructor(
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : SyncEthereumTransactionsUseCase {

    private val tag = "SyncEthUC"

    override suspend fun invoke(walletId: String): Result<Unit> = withContext(Dispatchers.IO) {
        logger.d(tag, "=== Syncing Ethereum transactions for wallet: $walletId ===")

        // Business logic validation
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        val ethereumCoin = wallet.ethereum ?: run {
            logger.e(tag, "Ethereum not enabled for wallet: ${wallet.name}")
            return@withContext Result.Error("Ethereum not enabled")
        }

        logger.d(tag, "Wallet: ${wallet.name}, Address: ${ethereumCoin.address}, Network: ${ethereumCoin.network.displayName}")

        return@withContext when (val transactionsResult = ethereumBlockchainRepository.getEthereumTransactions(
            address = ethereumCoin.address,
            network = ethereumCoin.network,
            walletId = walletId
        )) {
            is Result.Success -> {
                val transactions = transactionsResult.data
                logger.d(tag, "Received ${transactions.size} transactions from Etherscan")

                // Delete existing transactions
                ethereumTransactionRepository.deleteAllForWallet(walletId)
                logger.d(tag, "Deleted existing transactions")

                // Save new transactions
                transactions.forEach { transaction ->
                    ethereumTransactionRepository.saveTransaction(transaction)
                }

                logger.d(tag, "Successfully saved ${transactions.size} transactions")
                logger.d(tag, "=== Sync completed successfully for wallet $walletId ===")
                Result.Success(Unit)
            }

            is Result.Error -> {
                logger.e(tag, "Failed to fetch transactions: ${transactionsResult.message}")
                Result.Error(transactionsResult.message)
            }

            else -> Result.Error("Unknown error")
        }
    }
}

@Singleton
class GetTransactionUseCaseImpl @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val logger: Logger
) : GetTransactionUseCase {

    private val tag = "GetTransactionUC"

    override suspend fun invoke(transactionId: String): Result<EthereumTransaction> {
        val transaction = ethereumTransactionRepository.getTransaction(transactionId)
        return if (transaction != null) {
            logger.d(tag, "Transaction found: $transactionId")
            Result.Success(transaction)
        } else {
            logger.w(tag, "Transaction not found: $transactionId")
            Result.Error("Transaction not found")
        }
    }
}

@Singleton
class GetWalletTransactionsUseCaseImpl @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val logger: Logger
) : GetWalletTransactionsUseCase {

    private val tag = "GetWalletTxUC"

    override fun invoke(walletId: String): Flow<Result<List<EthereumTransaction>>> {
        logger.d(tag, "Subscribing to transactions flow for wallet: $walletId")

        return ethereumTransactionRepository.getTransactions(walletId)
            .map { transactions ->
                logger.d(tag, "Emitting ${transactions.size} transactions for wallet: $walletId")
                Result.Success(transactions) as Result<List<EthereumTransaction>>
            }
            .catch { e ->
                logger.e(tag, "Error loading transactions for wallet $walletId: ${e.message}")
                emit(Result.Error("Failed to load transactions: ${e.message}"))
            }
    }
}

@Singleton
class GetPendingTransactionsUseCaseImpl @Inject constructor(
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val logger: Logger
) : GetPendingTransactionsUseCase {

    private val tag = "GetPendingTxUC"

    override suspend fun invoke(): Result<List<EthereumTransaction>> {
        val transactions = ethereumTransactionRepository.getPendingTransactions()
        logger.d(tag, "Found ${transactions.size} pending transactions")
        return Result.Success(transactions)
    }
}

@Singleton
class ValidateEthereumSendUseCaseImpl @Inject constructor(
    private val getFeeEstimateUseCase: GetFeeEstimateUseCase,
    private val logger: Logger
) : ValidateEthereumSendUseCase {

    private val tag = "ValidateEthSendUC"

    override suspend fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        fromAddress: String,
        balance: BigDecimal,
        feeLevel: FeeLevel
    ): ValidateEthereumSendUseCase.ValidationResult {

        var addressError: String? = null
        var amountError: String? = null
        var balanceError: String? = null
        var selfSendError: String? = null
        var isValid = true
        var feeEstimate: EthereumFeeEstimate? = null

        // Validate address is not empty
        if (toAddress.isBlank()) {
            addressError = "Please enter a recipient address"
            isValid = false
            logger.d(tag, "Address is empty")
        }
        // Validate address format (Ethereum addresses are 0x + 40 hex chars)
        else if (!isValidEthereumAddress(toAddress)) {
            addressError = "Invalid Ethereum address format"
            isValid = false
            logger.d(tag, "Invalid address format: $toAddress")
        }

        // Validate not sending to self
        if (toAddress.isNotBlank() && toAddress.equals(fromAddress, ignoreCase = true)) {
            selfSendError = "Cannot send to yourself"
            isValid = false
            logger.d(tag, "Self-send attempt detected")
        }

        // Validate amount
        if (amountValue <= BigDecimal.ZERO) {
            amountError = "Amount must be greater than 0"
            isValid = false
            logger.d(tag, "Invalid amount: $amountValue")
        }

        // Get fee estimate and validate balance
        if (isValid || amountValue > BigDecimal.ZERO) {
            val feeResult = getFeeEstimateUseCase(feeLevel)
            if (feeResult is Result.Success) {
                feeEstimate = feeResult.data

                val totalRequired = amountValue + BigDecimal(feeEstimate.totalFeeEth)
                if (totalRequired > balance) {
                    balanceError = "Insufficient balance (need ${totalRequired.setScale(4, RoundingMode.HALF_UP)} ETH including fees)"
                    isValid = false
                    logger.d(tag, "Insufficient balance: required $totalRequired, available $balance")
                }
            }
        }

        return ValidateEthereumSendUseCase.ValidationResult(
            isValid = isValid,
            addressError = addressError,
            amountError = amountError,
            balanceError = balanceError,
            selfSendError = selfSendError,
            feeEstimate = feeEstimate
        )
    }

    private fun isValidEthereumAddress(address: String): Boolean {
        return address.startsWith("0x") &&
                address.length == 42 &&
                address.substring(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    }
}

@Singleton
class GetFeeEstimateUseCaseImpl @Inject constructor(
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val logger: Logger
) : GetFeeEstimateUseCase {

    private val tag = "GetFeeEstimateUC"

    override suspend fun invoke(
        feeLevel: FeeLevel,
        network: EthereumNetwork
    ): Result<EthereumFeeEstimate> {
        logger.d(tag, "Getting fee estimate for $feeLevel on ${network.displayName}")
        return ethereumBlockchainRepository.getFeeEstimate(feeLevel, network)
    }
}

@Singleton
class GetEthereumWalletUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : GetEthereumWalletUseCase {

    private val tag = "GetEthereumWalletUC"

    override suspend fun invoke(walletId: String): Result<EthereumWalletInfo> {
        logger.d(tag, "Looking up Ethereum wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val ethereumCoin = wallet.ethereum
        if (ethereumCoin == null) {
            logger.e(tag, "Ethereum not enabled for wallet: ${wallet.name}")
            return Result.Error("Ethereum not enabled for this wallet")
        }

        logger.d(tag, "Found wallet: ${wallet.name}, Address: ${ethereumCoin.address.take(8)}..., Network: ${ethereumCoin.network.displayName}")

        return Result.Success(
            EthereumWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = ethereumCoin.address,
                network = ethereumCoin.network
            )
        )
    }
}

@Singleton
class SendEthereumUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) : SendEthereumUseCase {

    private val tag = "SendEthereumUC"

    override suspend fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SendEthereumResult> = withContext(Dispatchers.IO) {
        logger.d(tag, "WalletId: $walletId, To: $toAddress, Amount: $amount ETH")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        val ethereumCoin = wallet.ethereum ?: run {
            logger.e(tag, "Ethereum not enabled for wallet: $walletId")
            return@withContext Result.Error("Ethereum not enabled")
        }

        logger.d(tag, "Network: ${ethereumCoin.network.displayName}")

        // 1. Create transaction with network context
        logger.d(tag, "Step 1: Creating transaction...")
        val transactionResult = createTransaction(walletId, toAddress, amount, feeLevel, note, ethereumCoin.network)
        if (transactionResult is Result.Error) return@withContext transactionResult
        val transaction = (transactionResult as Result.Success).data

        logger.d(tag, "Transaction created: ${transaction.id}")

        // 2. Sign transaction
        logger.d(tag, "Step 2: Signing transaction...")
        val signedTransactionResult = signTransaction(transaction, ethereumCoin.network)
        if (signedTransactionResult is Result.Error) return@withContext signedTransactionResult
        val signedTransaction = (signedTransactionResult as Result.Success).data

        logger.d(tag, "Transaction signed: ${signedTransaction.txHash}")

        // 3. Broadcast transaction
        logger.d(tag, "Step 3: Broadcasting transaction...")
        val broadcastResult = broadcastTransaction(signedTransaction, ethereumCoin.network)

        logger.d(tag, "Broadcast result: success=${broadcastResult.success}, hash=${broadcastResult.hash}")

        val sendResult = SendEthereumResult(
            transactionId = transaction.id,
            txHash = broadcastResult.hash ?: signedTransaction.txHash ?: "",
            success = broadcastResult.success,
            error = broadcastResult.error
        )

        logger.d(tag, "========== SEND COMPLETE ==========")
        Result.Success(sendResult)
    }

    private suspend fun createTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?,
        network: EthereumNetwork
    ): Result<EthereumTransaction> {
        val wallet = walletRepository.getWallet(walletId)
            ?: run {
                logger.e(tag, "Wallet not found: $walletId")
                return Result.Error("Wallet not found")
            }

        val ethereumCoin = wallet.ethereum
            ?: run {
                logger.e(tag, "Ethereum not enabled for wallet: $walletId")
                return Result.Error("Ethereum not enabled")
            }

        logger.d(tag, "Creating transaction for address: ${ethereumCoin.address.take(8)}... on ${network.displayName}")

        val feeResult = ethereumBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (feeResult is Result.Error) {
            logger.e(tag, "Failed to get fee estimate")
            return Result.Error(feeResult.message)
        }
        val feeEstimate = (feeResult as Result.Success).data

        val amountWei = amount.multiply(BigDecimal("1000000000000000000")).toBigInteger()

        val transaction = EthereumTransaction(
            id = "tx_${System.currentTimeMillis()}",
            walletId = walletId,
            fromAddress = ethereumCoin.address,
            toAddress = toAddress,
            amountWei = amountWei.toString(),
            amountEth = amount.toPlainString(),
            gasPriceWei = feeEstimate.gasPriceWei,
            gasPriceGwei = feeEstimate.gasPriceGwei,
            gasLimit = feeEstimate.gasLimit,
            feeWei = feeEstimate.totalFeeWei,
            feeEth = feeEstimate.totalFeeEth,
            nonce = 0, // Temporary value, will be updated at signing
            chainId = network.chainId.toLong(),
            signedHex = null,
            txHash = null,
            status = TransactionStatus.PENDING,
            note = note,
            timestamp = System.currentTimeMillis(),
            feeLevel = feeLevel,
            network = network.displayName,
            data = "",
            isIncoming = false
        )

        return Result.Success(transaction)
    }

    private suspend fun signTransaction(
        transaction: EthereumTransaction,
        network: EthereumNetwork
    ): Result<EthereumTransaction> {
        logger.d(tag, "Signing transaction: ${transaction.id}")

        val wallet = walletRepository.getWallet(transaction.walletId)
            ?: run {
                logger.e(tag, "Wallet not found: ${transaction.walletId}")
                return Result.Error("Wallet not found")
            }

        val ethereumCoin = wallet.ethereum
            ?: run {
                logger.e(tag, "Ethereum not enabled for wallet: ${transaction.walletId}")
                return Result.Error("Ethereum not enabled")
            }

        // Get encrypted private key
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = transaction.walletId,
            keyType = "ETH_PRIVATE_KEY"
        ) ?: run {
            logger.e(tag, "No private key found for wallet: ${transaction.walletId}")
            return Result.Error("No private key found")
        }

        val (encryptedHex, iv) = encryptedData

        // Decrypt
        val privateKey = try {
            keyStoreRepository.decryptString(encryptedHex, iv.toHex())
        } catch (e: Exception) {
            logger.e(tag, "Failed to decrypt private key: ${e.message}")
            return Result.Error("Failed to decrypt private key")
        }

        // Get the nonce
        val nonceResult = ethereumBlockchainRepository.getEthereumNonce(
            ethereumCoin.address,
            network
        )

        if (nonceResult is Result.Error) {
            logger.e(tag, "Failed to get nonce")
            return Result.Error(nonceResult.message)
        }
        val currentNonce = (nonceResult as Result.Success).data

        // Log the transaction's stored nonce for debugging
        logger.d(tag, "Transaction stored nonce: ${transaction.nonce}")

        val gasPriceWei = BigInteger(transaction.gasPriceWei)

        val credentials = try {
            Credentials.create(privateKey)
        } catch (e: Exception) {
            logger.e(tag, "Failed to create credentials: ${e.message}")
            return Result.Error("Failed to create credentials")
        }

        // Verify private key matches wallet address
        if (credentials.address.lowercase() != ethereumCoin.address.lowercase()) {
            logger.e(tag, "Private key doesn't match wallet")
            logger.e(tag, "Derived: ${credentials.address}, Expected: ${ethereumCoin.address}")
            return Result.Error("Private key doesn't match wallet")
        }

        // Use currentNonce
        val rawTransaction = RawTransaction.createTransaction(
            BigInteger.valueOf(currentNonce.toLong()),
            gasPriceWei,
            BigInteger.valueOf(transaction.gasLimit),
            transaction.toAddress,
            BigInteger(transaction.amountWei),
            transaction.data
        )

        val chainId = network.chainId.toLong()
        val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
        val signedHex = Numeric.toHexString(signedMessage)

        val txHashBytes = Hash.sha3(Numeric.hexStringToByteArray(signedHex))
        val calculatedHash = Numeric.toHexString(txHashBytes)

        // Update the transaction with the correct nonce and save
        val updatedTransaction = transaction.copy(
            status = TransactionStatus.PENDING,
            txHash = calculatedHash,
            signedHex = signedHex,
            nonce = currentNonce
        )

        // Save the updated transaction
        ethereumTransactionRepository.updateTransaction(updatedTransaction)

        logger.d(tag, "Transaction signed successfully with nonce: $currentNonce")
        return Result.Success(updatedTransaction)
    }

    private suspend fun broadcastTransaction(
        transaction: EthereumTransaction,
        network: EthereumNetwork
    ): BroadcastResult {
        if (transaction.signedHex == null) {
            logger.e(tag, "Transaction not signed")
            return BroadcastResult(success = false, error = "Transaction not signed")
        }

        val wallet = walletRepository.getWallet(transaction.walletId)
            ?: return BroadcastResult(success = false, error = "Wallet not found")

        val ethereumCoin = wallet.ethereum
            ?: return BroadcastResult(success = false, error = "Ethereum not enabled")

        // Verify network matches
        if (ethereumCoin.network.chainId != network.chainId) {
            logger.e(tag, "Network mismatch during broadcast")
            return BroadcastResult(success = false, error = "Network mismatch")
        }

        val broadcastResult = ethereumBlockchainRepository.broadcastEthereumTransaction(
            transaction.signedHex,
            network
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
                    transaction.copy(status = TransactionStatus.FAILED)
                }
                ethereumTransactionRepository.updateTransaction(updatedTransaction)

                logger.d(tag, "Broadcast result: ${result.success}")
                result
            }

            is Result.Error -> {
                logger.e(tag, "Broadcast failed: ${broadcastResult.message}")
                BroadcastResult(success = false, error = broadcastResult.message)
            }

            Result.Loading -> {
                logger.e(tag, "Broadcast timeout")
                BroadcastResult(success = false, error = "Broadcast timeout")
            }
        }
    }

    // Helper extension for ByteArray to Hex
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}