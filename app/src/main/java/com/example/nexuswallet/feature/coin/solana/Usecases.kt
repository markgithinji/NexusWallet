package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
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

    override suspend fun invoke(walletId: String, network: String): Result<Unit> = withContext(Dispatchers.IO) {
        logger.d(tag, "Syncing Solana transactions for wallet: $walletId, network: $network")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Find the specific Solana coin by network
        val solanaCoin = when (network.lowercase()) {
            "mainnet" -> wallet.solanaCoins.find { it.network == SolanaNetwork.Mainnet }
            "devnet" -> wallet.solanaCoins.find { it.network == SolanaNetwork.Devnet }
            else -> null
        }

        if (solanaCoin == null) {
            logger.e(tag, "Solana $network not enabled for wallet: ${wallet.name}")
            return@withContext Result.Error("Solana $network not enabled")
        }

        logger.d(
            tag,
            "Syncing for wallet: ${wallet.name}, Address: ${solanaCoin.address.take(8)}..., Network: ${solanaCoin.network}"
        )

        val historyResult = solanaBlockchainRepository.getFullTransactionHistory(
            address = solanaCoin.address,
            network = solanaCoin.network,
            limit = 50
        )

        var savedCount = 0

        when (historyResult) {
            is Result.Success -> {
                val transactions = historyResult.data
                logger.d(tag, "Received ${transactions.size} transactions on ${solanaCoin.network}")

                if (transactions.isNotEmpty()) {
                    // Delete existing transactions for this specific wallet and network
                    solanaTransactionRepository.deleteForWalletAndNetwork(walletId, network)
                    logger.d(tag, "Deleted existing transactions for $network")

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
                } else {
                    logger.d(tag, "No transactions found on ${solanaCoin.network}")
                }

                logger.d(
                    tag,
                    "Successfully saved $savedCount transactions on ${solanaCoin.network}"
                )
            }

            is Result.Error -> {
                logger.e(
                    tag,
                    "Failed to fetch transactions on ${solanaCoin.network}: ${historyResult.message}"
                )
                return@withContext Result.Error(historyResult.message)
            }

            else -> {}
        }

        logger.d(tag, "=== Sync completed for wallet $walletId on $network (saved: $savedCount transactions) ===")
        Result.Success(Unit)
    }
}

@Singleton
class GetSolanaWalletUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : GetSolanaWalletUseCase {

    private val tag = "GetSolanaWalletUC"

    override suspend fun invoke(walletId: String, network: SolanaNetwork?): Result<SolanaWalletInfo> {
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        // If network specified, get that specific Solana coin
        val solanaCoin = if (network != null) {
            wallet.solanaCoins.find { it.network == network }
        } else {
            // Otherwise get the first one (usually Mainnet)
            wallet.solanaCoins.firstOrNull()
        }

        if (solanaCoin == null) {
            val networkMsg = network?.let { " for $it" } ?: ""
            logger.e(tag, "Solana not enabled$networkMsg for wallet: ${wallet.name}")
            return Result.Error("Solana not enabled${networkMsg} for this wallet")
        }

        logger.d(
            tag,
            "Loaded wallet: ${wallet.name}, address: ${solanaCoin.address.take(8)}..., network: ${solanaCoin.network}"
        )

        return Result.Success(
            SolanaWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = solanaCoin.address,
                network = solanaCoin.network
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
        network: SolanaNetwork,
        note: String?
    ): Result<SendSolanaResult> = withContext(Dispatchers.IO) {
        logger.d(tag, "Sending $amount SOL to $toAddress on $network")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Get the specific Solana coin for this network
        val solanaCoin = wallet.solanaCoins.find { it.network == network }
        if (solanaCoin == null) {
            logger.e(tag, "Solana not enabled for network $network in wallet: $walletId")
            return@withContext Result.Error("Solana not enabled for $network")
        }

        logger.d(tag, "Network: ${solanaCoin.network}")

        // 1. Get fee estimate
        val feeResult = solanaBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (feeResult is Result.Error) {
            logger.e(tag, "Failed to get fee estimate on $network")
            return@withContext Result.Error(feeResult.message)
        }
        val feeEstimate = (feeResult as Result.Success).data

        val lamports = amount.multiply(BigDecimal(LAMPORTS_PER_SOL)).toLong()

        // 2. Get private key
        val keyType = when (network) {
            SolanaNetwork.Mainnet -> "SOL_MAINNET_PRIVATE_KEY"
            SolanaNetwork.Devnet -> "SOL_DEVNET_PRIVATE_KEY"
        }

        logger.d(tag, "Looking for key with type: $keyType")

        var encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = keyType
        )

        // Try fallback key types if not found
        if (encryptedData == null) {
            val fallbackKeys = listOf(
                "SOLANA_PRIVATE_KEY",
                "SOLANA_MAINNET_PRIVATE_KEY",
                "SOLANA_DEVNET_PRIVATE_KEY"
            )

            for (fallbackKey in fallbackKeys) {
                logger.d(tag, "Trying fallback key type: $fallbackKey")
                encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
                    walletId = walletId,
                    keyType = fallbackKey
                )
                if (encryptedData != null) {
                    logger.d(tag, "Found key with fallback type: $fallbackKey")
                    break
                }
            }
        }

        if (encryptedData == null) {
            logger.e(tag, "No private key found for wallet: $walletId")
            return@withContext Result.Error("No private key found. Make sure Solana is enabled in your wallet.")
        }

        val (encryptedHex, iv) = encryptedData

        val privateKeyHex = try {
            keyStoreRepository.decryptString(encryptedHex, iv.toHex())
        } catch (e: Exception) {
            logger.e(tag, "Failed to decrypt private key: ${e.message}")
            return@withContext Result.Error("Failed to decrypt private key")
        }

        val keypair = createSolanaKeypair(privateKeyHex) ?: return@withContext Result.Error("Invalid private key format")

        val derivedAddress = keypair.publicKey.toString()
        if (derivedAddress != solanaCoin.address) {
            logger.e(tag, "Private key doesn't match wallet")
            logger.e(tag, "Derived: $derivedAddress, Expected: ${solanaCoin.address}")
            return@withContext Result.Error("Private key doesn't match wallet")
        }

        // 3. Create and sign transaction
        val signedTxResult = solanaBlockchainRepository.createAndSignTransaction(
            fromKeypair = keypair,
            toAddress = toAddress,
            lamports = lamports,
            network = network
        )

        if (signedTxResult is Result.Error) {
            logger.e(tag, "Failed to sign transaction on $network: ${signedTxResult.message}")
            return@withContext Result.Error(signedTxResult.message)
        }
        val signedTx = (signedTxResult as Result.Success).data

        // 4. Broadcast transaction
        val broadcastResult = broadcastTransaction(signedTx, network)

        // 5. save transaction after successful broadcast
        if (broadcastResult.success) {
            val transaction = createTransactionRecord(
                walletId = walletId,
                toAddress = toAddress,
                amount = amount,
                feeLevel = feeLevel,
                note = note,
                network = network,
                solanaCoin = solanaCoin,
                feeEstimate = feeEstimate,
                signature = signedTx.signature
            )

            solanaTransactionRepository.saveTransaction(transaction)
            logger.d(tag, "Transaction saved after successful broadcast: ${transaction.id} with signature ${signedTx.signature?.take(8)}...")
        } else {
            logger.e(tag, "Broadcast failed, no transaction saved: ${broadcastResult.error}")
        }

        val sendResult = SendSolanaResult(
            transactionId = "sol_tx_${System.currentTimeMillis()}", // Generate ID even if failed for UI feedback
            txHash = signedTx.signature ?: "",
            success = broadcastResult.success,
            error = broadcastResult.error
        )

        if (sendResult.success) {
            logger.d(
                tag,
                "Send successful on $network: tx ${sendResult.txHash.take(8)}..."
            )
        } else {
            logger.e(tag, "Send failed on $network: ${sendResult.error}")
        }

        Result.Success(sendResult)
    }

    private fun createTransactionRecord(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?,
        network: SolanaNetwork,
        solanaCoin: SolanaCoin,
        feeEstimate: SolanaFeeEstimate,
        signature: String?
    ): SolanaTransaction {
        val lamports = amount.multiply(BigDecimal(LAMPORTS_PER_SOL)).toLong()

        return SolanaTransaction(
            id = "sol_tx_${System.currentTimeMillis()}",
            walletId = walletId,
            fromAddress = solanaCoin.address,
            toAddress = toAddress,
            amountLamports = lamports,
            amountSol = amount.toPlainString(),
            feeLamports = feeEstimate.feeLamports,
            feeSol = feeEstimate.feeSol,
            signature = signature,
            status = TransactionStatus.SUCCESS,
            note = note,
            timestamp = System.currentTimeMillis(),
            feeLevel = feeLevel,
            network = network,
            isIncoming = false,
            tokenMint = null,
            tokenSymbol = null,
            tokenDecimals = null,
            slot = null,
            blockTime = null
        )
    }

    private suspend fun broadcastTransaction(
        signedTx: SolanaSignedTransaction,
        network: SolanaNetwork
    ): BroadcastResult {
        val broadcastResult = solanaBlockchainRepository.broadcastTransaction(signedTx, network)

        return when (broadcastResult) {
            is Result.Success -> {
                broadcastResult.data
            }
            is Result.Error -> {
                logger.e(tag, "Broadcast failed on $network: ${broadcastResult.message}")
                BroadcastResult(success = false, error = broadcastResult.message)
            }
            Result.Loading -> {
                logger.e(tag, "Broadcast timeout on $network")
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

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val LAMPORTS_PER_SOL = 1_000_000_000L
    }
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
        logger.d(tag, "Fetching balance for $address on $network")
        val result = solanaBlockchainRepository.getBalance(address, network)
        if (result is Result.Error) {
            logger.e(tag, "Failed to get balance on $network: ${result.message}")
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
        logger.d(tag, "Fetching fee estimate on $network")
        val result = solanaBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (result is Result.Error) {
            logger.e(tag, "Failed to get fee estimate on $network: ${result.message}")
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