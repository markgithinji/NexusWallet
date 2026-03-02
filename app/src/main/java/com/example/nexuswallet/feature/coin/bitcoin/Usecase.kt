package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.DumpedPrivateKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Transaction
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

    override suspend fun invoke(walletId: String, network: String?): Result<Unit> = withContext(Dispatchers.IO) {
        logger.d(tag, "Syncing Bitcoin transactions for wallet: $walletId, network: $network")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Filter Bitcoin coins by network if specified
        val bitcoinCoins = if (network != null) {
            wallet.bitcoinCoins.filter { coin ->
                when (network.lowercase()) {
                    "mainnet" -> coin.network == BitcoinNetwork.Mainnet
                    "testnet" -> coin.network == BitcoinNetwork.Testnet
                    else -> false
                }
            }
        } else {
            wallet.bitcoinCoins
        }

        if (bitcoinCoins.isEmpty()) {
            val msg = if (network != null) "Bitcoin $network not enabled" else "Bitcoin not enabled"
            logger.e(tag, "$msg for wallet: ${wallet.name}")
            return@withContext Result.Error(msg)
        }

        var totalTransactions = 0

        // Sync transactions for each Bitcoin coin
        bitcoinCoins.forEach { bitcoinCoin ->
            logger.d(tag, "Syncing for ${bitcoinCoin.network}")

            when (val result = bitcoinBlockchainRepository.getAddressTransactions(
                address = bitcoinCoin.address,
                network = bitcoinCoin.network
            )) {
                is Result.Success -> {
                    val transactions = result.data

                    // Delete only this network's transactions
                    val networkParam = when (bitcoinCoin.network) {
                        BitcoinNetwork.Mainnet -> "mainnet"
                        BitcoinNetwork.Testnet -> "testnet"
                    }
                    bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, networkParam)

                    transactions.forEach { tx ->
                        val domainTx = tx.toDomain(
                            walletId = walletId,
                            isIncoming = tx.isIncoming,
                            network = bitcoinCoin.network
                        )
                        bitcoinTransactionRepository.saveTransaction(domainTx)
                        totalTransactions++
                    }

                    logger.d(tag, "Synced ${transactions.size} transactions for ${bitcoinCoin.network}")
                }
                is Result.Error -> {
                    logger.e(tag, "Failed to sync ${bitcoinCoin.network}: ${result.message}")
                }
                else -> {}
            }
        }

        logger.d(tag, "Sync completed | total txCount=$totalTransactions")
        Result.Success(Unit)
    }
}

@Singleton
class GetBitcoinWalletUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : GetBitcoinWalletUseCase {

    private val tag = "GetBitcoinWalletUC"

    override suspend fun invoke(
        walletId: String,
        network: BitcoinNetwork?
    ): Result<BitcoinWalletInfo> {
        logger.d(tag, "Looking up Bitcoin wallet: $walletId")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        // If network specified, get that specific Bitcoin coin
        val bitcoinCoin = if (network != null) {
            wallet.bitcoinCoins.find { it.network == network }
        } else {
            // Otherwise get the first one (usually Mainnet)
            wallet.bitcoinCoins.firstOrNull()
        }

        if (bitcoinCoin == null) {
            val networkMsg = network?.let { " for $it" } ?: ""
            logger.e(tag, "Bitcoin not enabled$networkMsg for wallet: ${wallet.name}")
            return Result.Error("Bitcoin not enabled${networkMsg} for this wallet")
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
        preparedTransaction: PreparedBitcoinTransaction,
        walletId: String,
        network: BitcoinNetwork
    ): Result<SendBitcoinResult> = withContext(Dispatchers.IO) {
        logger.d(tag, "Sending prepared transaction: ${preparedTransaction.transactionId} | walletId=$walletId | network=$network")

        // Get wallet
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Get the specific Bitcoin coin for this network
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
        if (bitcoinCoin == null) {
            logger.e(tag, "Bitcoin not enabled for network $network in wallet: $walletId")
            return@withContext Result.Error("Bitcoin not enabled for $network")
        }

        // Get private key
        val keyType = when (bitcoinCoin.network) {
            BitcoinNetwork.Mainnet -> "BTC_MAINNET_PRIVATE_KEY"
            BitcoinNetwork.Testnet -> "BTC_TESTNET_PRIVATE_KEY"
        }

        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = keyType
        ) ?: run {
            securityPreferencesRepository.getEncryptedPrivateKey(
                walletId = walletId,
                keyType = BTC_PRIVATE_KEY_TYPE
            )
        }

        if (encryptedData == null) {
            logger.e(tag, "No private key found for wallet: $walletId")
            return@withContext Result.Error("No private key found")
        }

        return@withContext try {
            val privateKeyWIF = keyStoreRepository.decryptString(
                encryptedData.first,
                encryptedData.second.toHex()
            )

            val networkParams = when (bitcoinCoin.network) {
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
            }

            val ecKey = DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key

            // Verify key matches address
            if (LegacyAddress.fromKey(networkParams, ecKey).toString() != bitcoinCoin.address) {
                logger.e(tag, "Private key does not match wallet address")
                return@withContext Result.Error("Private key does not match wallet address")
            }

            // Create and sign transaction using prepared data
            when (val signResult = bitcoinBlockchainRepository.createAndSignTransaction(
                fromKey = ecKey,
                toAddress = preparedTransaction.toAddress,
                satoshis = preparedTransaction.amountSatoshis,
                feeLevel = preparedTransaction.feeLevel,
                network = bitcoinCoin.network
            )) {
                is Result.Success -> {
                    val signedTx = signResult.data

                    // Broadcast and save after successful broadcast
                    broadcastAndSaveTransaction(
                        signedTx = signedTx,
                        preparedTx = preparedTransaction,
                        walletId = walletId,
                        network = bitcoinCoin.network
                    )
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

    private suspend fun broadcastAndSaveTransaction(
        signedTx: Transaction,
        preparedTx: PreparedBitcoinTransaction,
        walletId: String,
        network: BitcoinNetwork
    ): Result<SendBitcoinResult> {
        val signedHex = Utils.HEX.encode(signedTx.bitcoinSerialize())
        val txId = signedTx.txId.toString()

        return when (val broadcastResult = bitcoinBlockchainRepository.broadcastTransaction(
            signedHex = signedHex,
            network = network
        )) {
            is Result.Success -> {
                // Create and save transaction
                val transaction = BitcoinTransaction(
                    id = preparedTx.transactionId,
                    walletId = walletId,
                    fromAddress = preparedTx.fromAddress,
                    toAddress = preparedTx.toAddress,
                    amountSatoshis = preparedTx.amountSatoshis,
                    amountBtc = preparedTx.amountBtc.toPlainString(),
                    feeSatoshis = preparedTx.feeSatoshis,
                    feeBtc = preparedTx.feeBtc.toPlainString(),
                    feePerByte = preparedTx.feePerByte,
                    estimatedSize = preparedTx.estimatedSize.toLong(),
                    signedHex = signedHex,
                    txHash = broadcastResult.data,
                    status = TransactionStatus.SUCCESS,
                    note = null,
                    timestamp = System.currentTimeMillis(),
                    feeLevel = preparedTx.feeLevel,
                    network = network,
                    isIncoming = false
                )

                bitcoinTransactionRepository.saveTransaction(transaction)
                logger.d(tag, "Transaction saved after successful broadcast: ${transaction.id}")

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
                logger.e(tag, "Failed to broadcast transaction: ${broadcastResult.message}")
                Result.Error(broadcastResult.message)
            }

            else -> Result.Error("Unknown broadcast error")
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val BTC_PRIVATE_KEY_TYPE = "BTC_PRIVATE_KEY"
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
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
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
    private val validateBitcoinAddressUseCase: ValidateBitcoinAddressUseCase,
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

        // Validate Bitcoin is enabled for this network
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
        if (bitcoinCoin == null) {
            logger.w(tag, "Bitcoin not enabled for $network in wallet: ${wallet.name}")
            return ValidateBitcoinTransactionUseCase.ValidationResult(
                isValid = false,
                addressError = "Bitcoin not enabled for $network"
            )
        }

        // Validate address is not empty
        if (toAddress.isBlank()) {
            addressError = "Please enter a recipient address"
            isValid = false
            logger.w(tag, "Address is empty")
        }
        // Validate address format
        else if (!validateBitcoinAddressUseCase(toAddress, network)) {
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

        // If no specific errors but isValid is false, set a generic error
        if (!isValid && addressError == null && amountError == null && balanceError == null && selfSendError == null) {
            // This should not happen, but just in case
            addressError = "Invalid transaction. Please check your inputs."
        }

        return ValidateBitcoinTransactionUseCase.ValidationResult(
            isValid = isValid,
            addressError = addressError,
            amountError = amountError,
            balanceError = balanceError,
            selfSendError = selfSendError
        )
    }
}