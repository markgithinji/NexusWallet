package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sol4k.Keypair
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSolanaTransactionsUseCaseImpl @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : SyncSolanaTransactionsUseCase {

    private val tag = "SyncSolanaUC"

    override suspend fun invoke(walletId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana ?: run {
            logger.e(tag, "Solana not enabled for wallet: ${wallet.name}")
            return@withContext Result.Error("Solana not enabled")
        }

        logger.d(
            tag,
            "Wallet: ${wallet.name}, Address: ${solanaCoin.address}, Network: ${solanaCoin.network}"
        )

        val historyResult = solanaBlockchainRepository.getFullTransactionHistory(
            address = solanaCoin.address,
            network = solanaCoin.network,
            limit = 50
        )

        return@withContext when (historyResult) {
            is Result.Success -> {
                val transactions = historyResult.data
                logger.d(tag, "Received ${transactions.size} transactions on ${solanaCoin.network}")

                if (transactions.isEmpty()) {
                    logger.d(tag, "No transactions found")
                    return@withContext Result.Success(Unit)
                }

                solanaTransactionRepository.deleteAllForWallet(walletId)
                logger.d(tag, "Deleted existing transactions")

                var savedCount = 0
                transactions.forEachIndexed { index, (sigInfo, details) ->
                    val transaction = (sigInfo to details).toDomainTransaction(
                        walletId = walletId,
                        walletAddress = solanaCoin.address,
                        network = solanaCoin.network
                    )

                    transaction?.let {
                        logger.d(
                            tag,
                            "Transaction #$index on ${solanaCoin.network}: ${it.signature?.take(8)}..."
                        )
                        logger.d(tag, "  isIncoming: ${it.isIncoming}")
                        logger.d(
                            tag,
                            "  amount: ${it.amountLamports} lamports (${it.amountSol} SOL)"
                        )

                        solanaTransactionRepository.saveTransaction(it)
                        savedCount++
                    }
                }

                logger.d(
                    tag,
                    "Successfully saved $savedCount transactions on ${solanaCoin.network}"
                )
                logger.d(tag, "=== Sync completed for wallet $walletId ===")
                Result.Success(Unit)
            }

            is Result.Error -> {
                logger.e(
                    tag,
                    "Failed to fetch transactions on ${solanaCoin.network}: ${historyResult.message}"
                )
                Result.Error(historyResult.message)
            }

            else -> Result.Error("Unknown error on ${solanaCoin.network}")
        }
    }
}

@Singleton
class GetSolanaWalletUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : GetSolanaWalletUseCase {

    private val tag = "GetSolanaWalletUC"

    override suspend fun invoke(walletId: String): Result<SolanaWalletInfo> {
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana ?: run {
            logger.e(tag, "Solana not enabled for wallet: ${wallet.name}")
            return Result.Error("Solana not enabled for this wallet")
        }

        logger.d(
            tag,
            "Loaded wallet: ${wallet.name}, address: ${solanaCoin.address.take(8)}..."
        )

        return Result.Success(
            SolanaWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = solanaCoin.address
            )
        )
    }
}

@Singleton
class SendSolanaUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) : SendSolanaUseCase {

    private val tag = "SendSolanaUC"

    override suspend fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SendSolanaResult> = withContext(Dispatchers.IO) {
        logger.d(tag, "Sending $amount SOL to $toAddress")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana ?: run {
            logger.e(tag, "Solana not enabled for wallet: $walletId")
            return@withContext Result.Error("Solana not enabled")
        }

        logger.d(tag, "Network: ${solanaCoin.network}")

        // Create transaction
        val transactionResult =
            createTransaction(walletId, toAddress, amount, feeLevel, note, solanaCoin.network)
        if (transactionResult is Result.Error) return@withContext transactionResult
        val transaction = (transactionResult as Result.Success).data

        // Sign transaction
        val signedTransactionResult =
            signTransaction(transaction, solanaCoin.network, solanaCoin.address)
        if (signedTransactionResult is Result.Error) return@withContext signedTransactionResult
        val signedTransaction = (signedTransactionResult as Result.Success).data

        // Broadcast transaction
        val broadcastResult = broadcastTransaction(signedTransaction, solanaCoin.network)

        val sendResult = SendSolanaResult(
            transactionId = transaction.id,
            txHash = broadcastResult.hash ?: signedTransaction.signature ?: "",
            success = broadcastResult.success,
            error = broadcastResult.error
        )

        if (sendResult.success) {
            logger.d(
                tag,
                "Send successful on ${solanaCoin.network}: tx ${sendResult.txHash.take(8)}..."
            )
        } else {
            logger.e(tag, "Send failed on ${solanaCoin.network}: ${sendResult.error}")
        }

        Result.Success(sendResult)
    }

    private suspend fun createTransaction(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?,
        network: SolanaNetwork
    ): Result<SolanaTransaction> {
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana ?: run {
            logger.e(tag, "Solana not enabled for wallet: $walletId")
            return Result.Error("Solana not enabled")
        }

        val blockhashResult = solanaBlockchainRepository.getRecentBlockhash(network)
        if (blockhashResult is Result.Error) {
            logger.e(tag, "Failed to get blockhash on ${network}")
            return Result.Error(blockhashResult.message)
        }
        val blockhash = (blockhashResult as Result.Success).data

        val feeResult = solanaBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (feeResult is Result.Error) {
            logger.e(tag, "Failed to get fee estimate on ${network}")
            return Result.Error(feeResult.message)
        }
        val feeEstimate = (feeResult as Result.Success).data

        val lamports = amount.multiply(BigDecimal("1000000000")).toLong()

        val transaction = SolanaTransaction(
            id = "sol_tx_${System.currentTimeMillis()}",
            walletId = walletId,
            fromAddress = solanaCoin.address,
            toAddress = toAddress,
            amountLamports = lamports,
            amountSol = amount.toPlainString(),
            feeLamports = feeEstimate.feeLamports,
            feeSol = feeEstimate.feeSol,
            blockhash = blockhash,
            signedData = null,
            signature = null,
            status = TransactionStatus.PENDING,
            note = note,
            timestamp = System.currentTimeMillis(),
            feeLevel = feeLevel,
            network = network,
            isIncoming = false,
            slot = null,
            blockTime = null
        )

        return Result.Success(transaction)
    }

    private suspend fun signTransaction(
        transaction: SolanaTransaction,
        network: SolanaNetwork,
        expectedAddress: String
    ): Result<SolanaTransaction> {
        val wallet = walletRepository.getWallet(transaction.walletId) ?: run {
            logger.e(tag, "Wallet not found: ${transaction.walletId}")
            return Result.Error("Wallet not found")
        }

        val solanaCoin = wallet.solana ?: run {
            logger.e(tag, "Solana not enabled for wallet: ${transaction.walletId}")
            return Result.Error("Solana not enabled")
        }

        val blockhashResult = solanaBlockchainRepository.getRecentBlockhash(network)
        if (blockhashResult is Result.Error) {
            logger.e(tag, "Failed to get blockhash on ${network}")
            return Result.Error(blockhashResult.message)
        }
        val currentBlockhash = (blockhashResult as Result.Success).data

        // Get encrypted private key
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = transaction.walletId,
            keyType = "SOLANA_PRIVATE_KEY"
        ) ?: run {
            logger.e(tag, "No private key found for wallet: ${transaction.walletId}")
            return Result.Error("No private key found")
        }

        val (encryptedHex, iv) = encryptedData

        // Decrypt
        val privateKeyHex = try {
            keyStoreRepository.decryptString(encryptedHex, iv.toHex())
        } catch (e: Exception) {
            logger.e(tag, "Failed to decrypt private key: ${e.message}")
            return Result.Error("Failed to decrypt private key")
        }

        val keypair =
            createSolanaKeypair(privateKeyHex) ?: return Result.Error("Invalid private key format")

        val derivedAddress = keypair.publicKey.toString()
        if (derivedAddress != expectedAddress) {
            logger.e(tag, "Private key doesn't match wallet")
            logger.e(tag, "Derived: $derivedAddress, Expected: $expectedAddress")
            return Result.Error("Private key doesn't match wallet")
        }

        val signedTxResult = solanaBlockchainRepository.createAndSignTransaction(
            fromKeypair = keypair,
            toAddress = transaction.toAddress,
            lamports = transaction.amountLamports,
            network = network
        )

        if (signedTxResult is Result.Error) {
            logger.e(tag, "Failed to sign transaction on ${network}")
            return Result.Error(signedTxResult.message)
        }
        val signedTx = (signedTxResult as Result.Success).data

        val signedDataHex = signedTx.serialize().toHexString()

        return Result.Success(
            transaction.copy(
                status = TransactionStatus.PENDING,
                signature = signedTx.signature,
                signedData = signedDataHex,
                blockhash = currentBlockhash
            )
        )
    }

    private suspend fun broadcastTransaction(
        transaction: SolanaTransaction,
        network: SolanaNetwork
    ): BroadcastResult {
        val signedDataHex = transaction.signedData ?: return BroadcastResult(
            success = false,
            error = "Not signed"
        )
        val signatureHex = transaction.signature ?: return BroadcastResult(
            success = false,
            error = "No signature"
        )

        val signedDataBytes = signedDataHex.hexToByteArray()

        val solanaSignedTx = SolanaSignedTransaction(
            signature = signatureHex,
            serialize = { signedDataBytes }
        )

        val broadcastResult =
            solanaBlockchainRepository.broadcastTransaction(solanaSignedTx, network)

        return when (broadcastResult) {
            is Result.Success -> {
                val result = broadcastResult.data
                val updatedTransaction = if (result.success) {
                    transaction.copy(status = TransactionStatus.SUCCESS)
                } else {
                    transaction.copy(status = TransactionStatus.FAILED)
                }
                solanaTransactionRepository.updateTransaction(updatedTransaction)
                result
            }

            is Result.Error -> {
                logger.e(tag, "Broadcast failed on ${network}: ${broadcastResult.message}")
                BroadcastResult(success = false, error = broadcastResult.message)
            }

            Result.Loading -> {
                logger.e(tag, "Broadcast timeout on ${network}")
                BroadcastResult(success = false, error = "Broadcast timeout")
            }
        }
    }

    private fun createSolanaKeypair(privateKeyHex: String): Keypair? = try {
        val cleanPrivateKeyHex = if (privateKeyHex.startsWith("0x")) {
            privateKeyHex.substring(2)
        } else {
            privateKeyHex
        }
        val privateKeyBytes = cleanPrivateKeyHex.hexToByteArray()
        when (privateKeyBytes.size) {
            64 -> Keypair.fromSecretKey(privateKeyBytes)
            32 -> Keypair.fromSecretKey(privateKeyBytes + ByteArray(32))
            else -> null
        }
    } catch (e: Exception) {
        logger.e(tag, "Error creating keypair: ${e.message}")
        null
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

@Singleton
class GetSolanaBalanceUseCaseImpl @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger
) : GetSolanaBalanceUseCase {

    private val tag = "GetSolanaBalanceUC"

    override suspend fun invoke(
        address: String,
        network: SolanaNetwork
    ): Result<BigDecimal> {
        logger.d(tag, "Fetching balance for $address on ${network}")
        val result = solanaBlockchainRepository.getBalance(address, network)
        if (result is Result.Error) {
            logger.e(tag, "Failed to get balance on ${network}: ${result.message}")
        }
        return result
    }
}

@Singleton
class GetSolanaFeeEstimateUseCaseImpl @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger
) : GetSolanaFeeEstimateUseCase {

    private val tag = "GetSolanaFeeUC"

    override suspend fun invoke(
        feeLevel: FeeLevel,
        network: SolanaNetwork
    ): Result<SolanaFeeEstimate> {
        logger.d(tag, "Fetching fee estimate on ${network}")
        val result = solanaBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (result is Result.Error) {
            logger.e(tag, "Failed to get fee estimate on ${network}: ${result.message}")
        }
        return result
    }
}

@Singleton
class ValidateSolanaAddressUseCaseImpl @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger
) : ValidateSolanaAddressUseCase {

    private val tag = "ValidateSolanaUC"

    override fun invoke(address: String): Result<Boolean> {
        val result = solanaBlockchainRepository.validateAddress(address)
        if (result is Result.Success && !result.data) {
            logger.d(tag, "Invalid address: $address")
        }
        return result
    }
}

@Singleton
class ValidateSolanaSendUseCaseImpl @Inject constructor(
    private val validateSolanaAddressUseCase: ValidateSolanaAddressUseCase,
    private val logger: Logger
) : ValidateSolanaSendUseCase {

    private val tag = "ValidateSolanaSendUC"

    override fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        walletAddress: String,
        balance: BigDecimal,
        feeEstimate: SolanaFeeEstimate?
    ): ValidateSolanaSendUseCase.ValidationResult {

        var addressError: String? = null
        var amountError: String? = null
        var balanceError: String? = null
        var selfSendError: String? = null
        var isValid = true

        // Validate address is not empty
        if (toAddress.isBlank()) {
            addressError = "Please enter a recipient address"
            isValid = false
            logger.d(tag, "Address is empty")
        }
        // Validate address format
        else {
            val validationResult = validateSolanaAddressUseCase(toAddress)
            when (validationResult) {
                is Result.Success -> {
                    if (!validationResult.data) {
                        addressError = "Invalid Solana address format"
                        isValid = false
                        logger.d(tag, "Invalid address format: $toAddress")
                    }
                }

                is Result.Error -> {
                    addressError = "Address validation failed"
                    isValid = false
                    logger.d(tag, "Address validation error: ${validationResult.message}")
                }

                Result.Loading -> {}
            }
        }

        // Validate amount
        if (amountValue <= BigDecimal.ZERO) {
            amountError = "Amount must be greater than 0"
            isValid = false
            logger.d(tag, "Invalid amount: $amountValue")
        }

        // Validate not sending to self
        if (toAddress.isNotBlank() && toAddress == walletAddress) {
            selfSendError = "Cannot send to yourself"
            isValid = false
            logger.d(tag, "Attempted self-send")
        }

        // Validate balance including fees
        if (amountValue > BigDecimal.ZERO) {
            val fee = feeEstimate?.feeSol?.toBigDecimalOrNull() ?: BigDecimal("0.000005")
            val totalRequired = amountValue + fee
            if (totalRequired > balance) {
                balanceError = "Insufficient balance (need ${
                    totalRequired.setScale(
                        4,
                        RoundingMode.HALF_UP
                    )
                } SOL including fees)"
                isValid = false
                logger.d(tag, "Insufficient balance: have $balance, need $totalRequired")
            }
        }

        return ValidateSolanaSendUseCase.ValidationResult(
            isValid = isValid,
            addressError = addressError,
            amountError = amountError,
            balanceError = balanceError,
            selfSendError = selfSendError
        )
    }
}