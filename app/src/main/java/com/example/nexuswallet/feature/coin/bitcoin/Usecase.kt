package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.DumpedPrivateKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Utils
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncBitcoinTransactionsUseCaseImpl @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : SyncBitcoinTransactionsUseCase {

    private val tag = "SyncBitcoinUC"

    override suspend fun invoke(walletId: String): Result<Unit> = withContext(Dispatchers.IO) {
        logger.d(tag, "Syncing Bitcoin transactions for wallet: $walletId")

        when (val walletValidation = validateWallet(walletId)) {
            is Result.Error -> return@withContext walletValidation
            is Result.Success -> {
                val bitcoinCoin = walletValidation.data

                return@withContext when (val transactionsResult =
                    bitcoinBlockchainRepository.getAddressTransactions(
                        address = bitcoinCoin.address,
                        network = bitcoinCoin.network
                    )) {
                    is Result.Success -> {
                        val transactions = transactionsResult.data

                        // Clear and save
                        bitcoinTransactionRepository.deleteAllForWallet(walletId)
                        transactions.forEach { tx ->
                            val domainTx = tx.toDomain(
                                walletId = walletId,
                                isIncoming = tx.isIncoming,
                                network = bitcoinCoin.network
                            )
                            bitcoinTransactionRepository.saveTransaction(domainTx)
                        }

                        logger.d(
                            tag,
                            "Sync completed for wallet $walletId | txCount=${transactions.size}"
                        )
                        Result.Success(Unit)
                    }

                    is Result.Error -> {
                        logger.e(tag, "Failed to sync: ${transactionsResult.message}")
                        Result.Error(transactionsResult.message)
                    }

                    else -> Result.Error("Unknown error during sync")
                }
            }

            else -> Result.Error("Unknown validation error")
        }
    }

    private suspend fun validateWallet(walletId: String): Result<BitcoinCoin> {
        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        return wallet.bitcoin?.let {
            logger.d(tag, "Wallet validated: ${wallet.name}")
            Result.Success(it)
        } ?: run {
            logger.e(tag, "Bitcoin not enabled for wallet: ${wallet.name}")
            Result.Error("Bitcoin not enabled")
        }
    }
}

@Singleton
class GetBitcoinWalletUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : GetBitcoinWalletUseCase {

    private val tag = "GetBitcoinWalletUC"

    override suspend fun invoke(walletId: String): Result<BitcoinWalletInfo> {
        logger.d(tag, "Looking up Bitcoin wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        val bitcoinCoin = wallet.bitcoin
        if (bitcoinCoin == null) {
            logger.e(tag, "Bitcoin not enabled for wallet: ${wallet.name}")
            return Result.Error("Bitcoin not enabled for this wallet")
        }

        logger.d(
            tag,
            "Found wallet: ${wallet.name} | Address: ${bitcoinCoin.address.take(8)}... | Network: ${bitcoinCoin.network}"
        )

        return Result.Success(
            BitcoinWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = bitcoinCoin.address,
                network = bitcoinCoin.network
            )
        )
    }
}

@Singleton
class SendBitcoinUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : SendBitcoinUseCase {

    private val tag = "SendBitcoinUC"

    override suspend fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SendBitcoinResult> = withContext(Dispatchers.IO) {
        logger.d(
            tag,
            "Sending ${amount.toPlainString()} BTC to ${toAddress.take(8)}... | walletId=$walletId"
        )

        // Get wallet
        val wallet = walletRepository.getWallet(walletId)
        val bitcoinCoin = wallet?.bitcoin

        if (wallet == null || bitcoinCoin == null) {
            logger.e(tag, "Invalid wallet state for walletId=$walletId")
            return@withContext Result.Error("Invalid wallet state")
        }

        // Get fee estimate
        return@withContext when (val feeResult = bitcoinBlockchainRepository.getFeeEstimate(
            feeLevel,
            DEFAULT_INPUT_COUNT,
            DEFAULT_OUTPUT_COUNT
        )) {
            is Result.Success -> {
                val feeEstimate = feeResult.data
                processTransaction(
                    walletId = walletId,
                    bitcoinCoin = bitcoinCoin,
                    toAddress = toAddress,
                    amount = amount,
                    feeEstimate = feeEstimate,
                    feeLevel = feeLevel,
                    note = note
                )
            }

            is Result.Error -> {
                logger.e(tag, "Failed to get fee estimate: ${feeResult.message}")
                Result.Error("Failed to get fee estimate: ${feeResult.message}")
            }

            else -> Result.Error("Unknown error getting fee estimate")
        }
    }

    private suspend fun processTransaction(
        walletId: String,
        bitcoinCoin: BitcoinCoin,
        toAddress: String,
        amount: BigDecimal,
        feeEstimate: BitcoinFeeEstimate,
        feeLevel: FeeLevel,
        note: String?
    ): Result<SendBitcoinResult> {
        val satoshis = amount.toSatoshis()

        // Create transaction
        val transaction = BitcoinTransaction(
            id = "btc_tx_${System.currentTimeMillis()}",
            walletId = walletId,
            fromAddress = bitcoinCoin.address,
            toAddress = toAddress,
            amountSatoshis = satoshis,
            amountBtc = amount.toPlainString(),
            feeSatoshis = feeEstimate.totalFeeSatoshis,
            feeBtc = feeEstimate.totalFeeBtc,
            feePerByte = feeEstimate.feePerByte,
            estimatedSize = feeEstimate.estimatedSize,
            signedHex = null,
            txHash = null,
            status = TransactionStatus.PENDING,
            note = note,
            timestamp = System.currentTimeMillis(),
            feeLevel = feeLevel,
            network = bitcoinCoin.network,
            isIncoming = false
        )

        bitcoinTransactionRepository.saveTransaction(transaction)
        logger.d(tag, "Transaction created: ${transaction.id}")

        // Sign transaction
        return when (val signResult = signTransaction(transaction, bitcoinCoin)) {
            is Result.Success -> {
                val signedTransaction = signResult.data
                broadcastTransaction(signedTransaction, bitcoinCoin.network)
            }

            is Result.Error -> {
                val failedTx = transaction.copy(status = TransactionStatus.FAILED)
                bitcoinTransactionRepository.updateTransaction(failedTx)
                logger.e(tag, "Failed to sign transaction: ${signResult.message}")
                Result.Error("Failed to sign transaction: ${signResult.message}")
            }

            else -> Result.Error("Unknown signing error")
        }
    }

    private suspend fun signTransaction(
        transaction: BitcoinTransaction,
        bitcoinCoin: BitcoinCoin
    ): Result<BitcoinTransaction> {
        // Get private key
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = transaction.walletId,
            keyType = BTC_PRIVATE_KEY_TYPE
        ) ?: return Result.Error("No private key found")

        return try {
            val privateKeyWIF = keyStoreRepository.decryptString(
                encryptedData.first,
                encryptedData.second.toHex()
            )

            val networkParams = when (bitcoinCoin.network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
            }

            val ecKey = DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key

            // Verify key matches address
            if (LegacyAddress.fromKey(networkParams, ecKey).toString() != bitcoinCoin.address) {
                logger.e(tag, "Private key does not match wallet address")
                return Result.Error("Private key does not match wallet address")
            }

            // Sign
            when (val signResult = bitcoinBlockchainRepository.createAndSignTransaction(
                fromKey = ecKey,
                toAddress = transaction.toAddress,
                satoshis = transaction.amountSatoshis,
                feeLevel = transaction.feeLevel,
                network = bitcoinCoin.network
            )) {
                is Result.Success -> {
                    val signedTx = signResult.data
                    val updatedTx = transaction.copy(
                        status = TransactionStatus.PENDING,
                        txHash = signedTx.txId.toString(),
                        signedHex = Utils.HEX.encode(signedTx.bitcoinSerialize())
                    ).also { bitcoinTransactionRepository.updateTransaction(it) }
                    logger.d(tag, "Transaction signed successfully: ${updatedTx.id}")
                    Result.Success(updatedTx)
                }

                is Result.Error -> {
                    logger.e(tag, "Failed to create signed transaction: ${signResult.message}")
                    Result.Error("Failed to create signed transaction: ${signResult.message}")
                }

                else -> Result.Error("Unknown signing error")
            }
        } catch (e: Exception) {
            logger.e(tag, "Error signing transaction", e)
            Result.Error("Signing failed: ${e.message}")
        }
    }

    private suspend fun broadcastTransaction(
        transaction: BitcoinTransaction,
        network: BitcoinNetwork
    ): Result<SendBitcoinResult> {
        return when (val broadcastResult = bitcoinBlockchainRepository.broadcastTransaction(
            signedHex = transaction.signedHex!!,
            network = network
        )) {
            is Result.Success -> {
                val updatedTx = transaction.copy(
                    status = TransactionStatus.SUCCESS,
                    txHash = broadcastResult.data
                )
                bitcoinTransactionRepository.updateTransaction(updatedTx)

                logger.d(
                    tag,
                    "Transaction broadcast successfully: ${updatedTx.id} | txHash=${
                        broadcastResult.data.take(8)
                    }..."
                )

                Result.Success(
                    SendBitcoinResult(
                        transactionId = transaction.id,
                        txHash = broadcastResult.data,
                        success = true,
                        error = null
                    )
                )
            }

            is Result.Error -> {
                val failedTx = transaction.copy(status = TransactionStatus.FAILED)
                bitcoinTransactionRepository.updateTransaction(failedTx)
                logger.e(tag, "Failed to broadcast transaction: ${broadcastResult.message}")
                Result.Error(broadcastResult.message)
            }

            else -> Result.Error("Unknown broadcast error")
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val BTC_PRIVATE_KEY_TYPE = "BTC_PRIVATE_KEY"
        private const val DEFAULT_INPUT_COUNT = 1
        private const val DEFAULT_OUTPUT_COUNT = 2
    }
}

@Singleton
class GetBitcoinFeeEstimateUseCaseImpl @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger
) : GetBitcoinFeeEstimateUseCase {

    private val tag = "GetBitcoinFeeUC"

    override suspend fun invoke(
        feeLevel: FeeLevel,
        inputCount: Int,
        outputCount: Int
    ): Result<BitcoinFeeEstimate> {
        logger.d(
            tag,
            "Getting fee estimate for $feeLevel with $inputCount inputs, $outputCount outputs"
        )
        return bitcoinBlockchainRepository.getFeeEstimate(feeLevel, inputCount, outputCount)
    }
}

@Singleton
class GetBitcoinBalanceUseCaseImpl @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger
) : GetBitcoinBalanceUseCase {

    private val tag = "GetBitcoinBalanceUC"

    override suspend fun invoke(
        address: String,
        network: BitcoinNetwork
    ): Result<BigDecimal> {
        logger.d(tag, "Fetching balance for ${address.take(8)}... on $network")

        return when (val result = bitcoinBlockchainRepository.getBalance(address, network)) {
            is Result.Success -> {
                logger.d(tag, "Balance: ${result.data} BTC")
                Result.Success(result.data)
            }

            is Result.Error -> {
                logger.e(tag, "Failed to get balance: ${result.message}")
                Result.Error(result.message)
            }

            else -> Result.Error("Unknown error getting balance")
        }
    }
}

@Singleton
class ValidateBitcoinAddressUseCaseImpl @Inject constructor(
    private val logger: Logger
) : ValidateBitcoinAddressUseCase {

    private val tag = "ValidateBitcoinUC"

    override fun invoke(address: String, network: BitcoinNetwork): Boolean {
        return try {
            val params = when (network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
            }
            Address.fromString(params, address)
            logger.d(tag, "Valid $network address: ${address.take(8)}...")
            true
        } catch (e: Exception) {
            logger.e(tag, "Invalid $network address: ${address.take(8)}...")
            false
        }
    }
}

@Singleton
class ValidateBitcoinTransactionUseCaseImpl @Inject constructor(
    private val logger: Logger
) : ValidateBitcoinTransactionUseCase {

    private val tag = "ValidateBitcoinTxUC"

    override fun invoke(
        walletId: String,
        wallet: Wallet?,
        toAddress: String,
        amount: BigDecimal,
        network: BitcoinNetwork,
        balance: BigDecimal,
        feeEstimate: BitcoinFeeEstimate?
    ): ValidateBitcoinTransactionUseCase.ValidationResult {

        var addressError: String? = null
        var amountError: String? = null
        var balanceError: String? = null
        var selfSendError: String? = null
        var isValid = true

        // Validate wallet exists
        if (wallet == null) {
            logger.w(tag, "Wallet not found: $walletId")
            return ValidateBitcoinTransactionUseCase.ValidationResult(
                isValid = false,
                addressError = "Wallet not found"
            )
        }

        // Validate Bitcoin is enabled
        val bitcoinCoin = wallet.bitcoin
        if (bitcoinCoin == null) {
            logger.w(tag, "Bitcoin not enabled for wallet: ${wallet.name}")
            return ValidateBitcoinTransactionUseCase.ValidationResult(
                isValid = false,
                addressError = "Bitcoin not enabled for this wallet"
            )
        }

        // Validate address is not empty
        if (toAddress.isBlank()) {
            addressError = "Please enter a recipient address"
            isValid = false
            logger.w(tag, "Address is empty")
        }
        // Validate address format
        else if (!isValidBitcoinAddress(toAddress, network)) {
            addressError = "Invalid Bitcoin address for ${network.name.lowercase()}"
            isValid = false
            logger.w(tag, "Invalid address format: ${toAddress.take(8)}...")
        }

        // Validate not sending to self
        if (toAddress.isNotBlank() && toAddress == bitcoinCoin.address) {
            selfSendError = "Cannot send to yourself"
            isValid = false
            logger.w(tag, "Attempted self-send")
        }

        // Validate amount > 0
        if (amount <= BigDecimal.ZERO) {
            amountError = "Amount must be greater than zero"
            isValid = false
            logger.w(tag, "Invalid amount: $amount")
        }

        // Calculate total required including fees
        val feeBtc = if (feeEstimate != null) {
            BigDecimal(feeEstimate.totalFeeBtc)
        } else {
            BigDecimal("0.00001") // Default fallback for estimation
        }

        val totalRequired = amount + feeBtc

        // Check against user's actual balance
        if (amount > BigDecimal.ZERO && totalRequired > balance) {
            balanceError = "Insufficient balance. You have ${balance.setScale(8)} BTC but need ${
                totalRequired.setScale(8)
            } BTC (including fees)"
            isValid = false
            logger.w(tag, "Insufficient balance: have $balance BTC, need $totalRequired BTC")
        }

        return ValidateBitcoinTransactionUseCase.ValidationResult(
            isValid = isValid,
            addressError = addressError,
            amountError = amountError,
            balanceError = balanceError,
            selfSendError = selfSendError
        )
    }

    private fun isValidBitcoinAddress(address: String, network: BitcoinNetwork): Boolean {
        return try {
            val params = when (network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
            }
            Address.fromString(params, address)
            true
        } catch (e: Exception) {
            false
        }
    }
}