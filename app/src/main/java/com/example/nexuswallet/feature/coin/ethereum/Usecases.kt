package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SafeApiCall
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.ERC20Token
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.generated.Uint256
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
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val walletRepository: WalletRepository,
    private val logger: Logger
) : SyncEthereumTransactionsUseCase {

    private val tag = "SyncEthUC"

    override suspend fun invoke(walletId: String, tokenExternalId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        logger.d(tag, "=== Syncing EVM transactions for wallet: $walletId, token: $tokenExternalId ===")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Get all EVM tokens or filter by specific token
        val evmTokens = if (tokenExternalId != null) {
            wallet.evmTokens.filter { it.externalId == tokenExternalId }
        } else {
            wallet.evmTokens
        }

        if (evmTokens.isEmpty()) {
            logger.d(tag, "No EVM tokens found for wallet: $walletId")
            return@withContext Result.Success(Unit)
        }

        var totalTransactions = 0

        // Sync transactions for each token
        for (token in evmTokens) {
            // Delete existing transactions for this specific token
            evmTransactionRepository.deleteForWalletAndToken(walletId, token.externalId)

            val result = when (token) {
                is NativeETH -> evmBlockchainRepository.getNativeTransactions(
                    address = token.address,
                    network = token.network,
                    walletId = walletId,
                    tokenExternalId = token.externalId
                )
                is USDCToken, is USDTToken, is ERC20Token -> evmBlockchainRepository.getTokenTransactions(
                    address = token.address,
                    tokenContract = token.contractAddress,
                    network = token.network,
                    walletId = walletId,
                    tokenExternalId = token.externalId
                )
            }

            when (result) {
                is Result.Success -> {
                    val transactions = result.data
                    transactions.forEach { transaction ->
                        evmTransactionRepository.saveTransaction(transaction)
                    }
                    totalTransactions += transactions.size
                    logger.d(tag, "Synced ${transactions.size} ${token.symbol} transactions on ${token.network.displayName}")
                }
                is Result.Error -> {
                    logger.w(tag, "Failed to sync ${token.symbol}: ${result.message}")
                }
                Result.Loading -> {}
            }
        }

        logger.d(tag, "Successfully saved $totalTransactions total transactions")
        logger.d(tag, "=== Sync completed successfully for wallet $walletId ===")
        Result.Success(Unit)
    }
}

@Singleton
class GetTransactionUseCaseImpl @Inject constructor(
    private val evmTransactionRepository: EVMTransactionRepository,
    private val logger: Logger
) : GetTransactionUseCase {

    private val tag = "GetTransactionUC"

    override suspend fun invoke(transactionId: String): Result<EVMTransaction> {
        val transaction = evmTransactionRepository.getTransaction(transactionId)
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
    private val evmTransactionRepository: EVMTransactionRepository,
    private val logger: Logger
) : GetWalletTransactionsUseCase {

    private val tag = "GetWalletTxUC"

    override fun invoke(walletId: String): Flow<Result<List<EVMTransaction>>> {
        logger.d(tag, "Subscribing to transactions flow for wallet: $walletId")

        return evmTransactionRepository.getTransactions(walletId)
            .map { transactions ->
                logger.d(tag, "Emitting ${transactions.size} transactions for wallet: $walletId")
                Result.Success(transactions) as Result<List<EVMTransaction>>
            }
            .catch { e ->
                logger.e(tag, "Error loading transactions for wallet $walletId: ${e.message}")
                emit(Result.Error("Failed to load transactions: ${e.message}"))
            }
    }
}

@Singleton
class GetPendingTransactionsUseCaseImpl @Inject constructor(
    private val evmTransactionRepository: EVMTransactionRepository,
    private val logger: Logger
) : GetPendingTransactionsUseCase {

    private val tag = "GetPendingTxUC"

    override suspend fun invoke(): Result<List<EVMTransaction>> {
        val transactions = evmTransactionRepository.getPendingTransactions()
        logger.d(tag, "Found ${transactions.size} pending transactions")
        return Result.Success(transactions)
    }
}

@Singleton
class ValidateEVMSendUseCaseImpl @Inject constructor(
    private val getFeeEstimateUseCase: GetFeeEstimateUseCase,
    private val logger: Logger
) : ValidateEVMSendUseCase {

    private val tag = "ValidateEVMSendUC"

    override suspend fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        fromAddress: String,
        tokenBalance: BigDecimal,
        ethBalance: BigDecimal,
        feeLevel: FeeLevel,
        token: EVMToken
    ): ValidateEVMSendUseCase.ValidationResult {

        var addressError: String? = null
        var amountError: String? = null
        var balanceError: String? = null
        var selfSendError: String? = null
        var gasError: String? = null
        var isValid = true
        var feeEstimate: EVMFeeEstimate? = null

        // Validate address is not empty
        if (toAddress.isBlank()) {
            addressError = "Please enter a recipient address"
            isValid = false
            logger.d(tag, "Address is empty")
        }
        // Validate address format
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
            val feeResult = getFeeEstimateUseCase(
                feeLevel = feeLevel,
                network = token.network,
                isToken = token !is NativeETH
            )

            when (feeResult) {
                is Result.Success -> {
                    feeEstimate = feeResult.data
                    val feeEth = BigDecimal(feeEstimate.totalFeeEth)

                    when (token) {
                        // For native ETH: Need enough ETH for amount + gas
                        is NativeETH -> {
                            val totalRequired = amountValue + feeEth
                            if (totalRequired > tokenBalance) {
                                balanceError = "Insufficient ETH balance. You have ${tokenBalance.setScale(4)} ETH but need ${totalRequired.setScale(4)} ETH (including gas)"
                                isValid = false
                                logger.d(tag, "Insufficient ETH balance: have $tokenBalance, need $totalRequired")
                            }
                        }
                        // For tokens (USDC, USDT, ERC20): Need enough tokens AND enough ETH for gas
                        else -> {
                            // Check token balance (USDC balance)
                            if (amountValue > tokenBalance) {
                                balanceError = "Insufficient ${token.symbol} balance. You have ${tokenBalance.setScale(2)} ${token.symbol} but need ${amountValue.setScale(2)} ${token.symbol}"
                                isValid = false
                                logger.d(tag, "Insufficient ${token.symbol} balance: have $tokenBalance, need $amountValue")
                            }

                            // Check ETH balance for gas
                            if (ethBalance < feeEth) {
                                gasError = "Insufficient ETH for gas. You have ${ethBalance.setScale(6)} ETH but need ${feeEth.setScale(6)} ETH"
                                isValid = false
                                logger.d(tag, "Insufficient ETH for gas: have $ethBalance, need $feeEth")
                            }
                        }
                    }
                }
                is Result.Error -> {
                    logger.w(tag, "Failed to get fee estimate: ${feeResult.message}")
                }
                Result.Loading -> {}
            }
        }

        return ValidateEVMSendUseCase.ValidationResult(
            isValid = isValid,
            addressError = addressError,
            amountError = amountError,
            balanceError = balanceError,
            selfSendError = selfSendError,
            gasError = gasError,
            feeEstimate = feeEstimate
        )
    }

    private fun isValidEthereumAddress(address: String): Boolean {
        return address.startsWith("0x") &&
                address.length == 42 &&
                address.substring(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val addressError: String? = null,
        val amountError: String? = null,
        val balanceError: String? = null,
        val selfSendError: String? = null,
        val gasError: String? = null,
        val feeEstimate: EVMFeeEstimate? = null
    )
}

@Singleton
class GetFeeEstimateUseCaseImpl @Inject constructor(
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val logger: Logger
) : GetFeeEstimateUseCase {

    private val tag = "GetFeeEstimateUC"

    override suspend fun invoke(
        feeLevel: FeeLevel,
        network: EthereumNetwork,
        isToken: Boolean
    ): Result<EVMFeeEstimate> {
        logger.d(tag, "Getting fee estimate for $feeLevel on ${network.displayName} (isToken=$isToken)")
        return evmBlockchainRepository.getFeeEstimate(feeLevel, network, isToken)
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

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return Result.Error("Wallet not found")
        }

        // Find the first NativeETH token
        val nativeEth = wallet.evmTokens.filterIsInstance<NativeETH>().firstOrNull()
        if (nativeEth == null) {
            logger.e(tag, "Ethereum not enabled for wallet: ${wallet.name}")
            return Result.Error("Ethereum not enabled for this wallet")
        }

        logger.d(
            tag,
            "Found wallet: ${wallet.name}, Address: ${nativeEth.address.take(8)}..., Network: ${nativeEth.network.displayName}"
        )

        return Result.Success(
            EthereumWalletInfo(
                walletId = wallet.id,
                walletName = wallet.name,
                walletAddress = nativeEth.address,
                network = nativeEth.network
            )
        )
    }
}
@Singleton
class SendEVMAssetUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) : SendEVMAssetUseCase {

    private val tag = "SendEVMAssetUC"

    override suspend fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        token: EVMToken,
        note: String?
    ): Result<SendEthereumResult> = withContext(Dispatchers.IO) {
        logger.d(tag, "WalletId: $walletId, To: $toAddress, Amount: $amount ${token.symbol}")

        // Validate wallet exists
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Verify the token belongs to this wallet
        val hasToken = wallet.evmTokens.any {
            it.address == token.address &&
                    it.contractAddress == token.contractAddress &&
                    it.network.chainId == token.network.chainId
        }
        if (!hasToken) {
            logger.e(tag, "Token ${token.symbol} not enabled for wallet: $walletId")
            return@withContext Result.Error("${token.symbol} not enabled for this wallet")
        }

        logger.d(tag, "Network: ${token.network.displayName}")

        // 1. Get encrypted private key
        logger.d(tag, "Step 1: Retrieving private key...")
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = "ETH_MAIN_PRIVATE_KEY"
        ) ?: run {
            logger.e(tag, "No private key found for wallet: $walletId")
            return@withContext Result.Error("No private key found")
        }

        val (encryptedHex, iv) = encryptedData

        val privateKey = try {
            keyStoreRepository.decryptString(encryptedHex, iv.toHex())
        } catch (e: Exception) {
            logger.e(tag, "Failed to decrypt private key: ${e.message}")
            return@withContext Result.Error("Failed to decrypt private key")
        }

        // 2. Get nonce
        logger.d(tag, "Step 2: Getting nonce...")
        val nonceResult = evmBlockchainRepository.getNonce(
            token.address,
            token.network
        )

        if (nonceResult is Result.Error) {
            logger.e(tag, "Failed to get nonce: ${nonceResult.message}")
            return@withContext Result.Error(nonceResult.message)
        }
        val nonce = (nonceResult as Result.Success).data

        // 3. Get fee estimate
        logger.d(tag, "Step 3: Getting fee estimate...")
        val feeResult = evmBlockchainRepository.getFeeEstimate(
            feeLevel = feeLevel,
            network = token.network,
            isToken = token !is NativeETH
        )

        if (feeResult is Result.Error) {
            logger.e(tag, "Failed to get fee estimate: ${feeResult.message}")
            return@withContext Result.Error(feeResult.message)
        }
        val feeEstimate = (feeResult as Result.Success).data

        // 4. Create and sign transaction
        logger.d(tag, "Step 4: Creating and signing transaction...")
        val createResult = createAndSignTransaction(
            token = token,
            toAddress = toAddress,
            amount = amount,
            privateKey = privateKey,
            gasPriceWei = BigInteger(feeEstimate.gasPriceWei),
            nonce = nonce,
            chainId = token.network.chainId.toLong()
        )

        if (createResult is Result.Error) {
            logger.e(tag, "Failed to create transaction: ${createResult.message}")
            return@withContext Result.Error(createResult.message)
        }
        val (_, signedHex, txHash) = (createResult as Result.Success).data

        // 5. Create transaction record with tokenExternalId
        logger.d(tag, "Step 5: Creating transaction record...")
        val amountInWei = amount.multiply(BigDecimal.TEN.pow(token.decimals)).toBigInteger()

        val transaction = when (token) {
            is NativeETH -> NativeETHTransaction(
                id = "tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = token.address,
                toAddress = toAddress,
                amountWei = amountInWei.toString(),
                amountEth = amount.toPlainString(),
                gasPriceWei = feeEstimate.gasPriceWei,
                gasPriceGwei = feeEstimate.gasPriceGwei,
                gasLimit = feeEstimate.gasLimit,
                feeWei = feeEstimate.totalFeeWei,
                feeEth = feeEstimate.totalFeeEth,
                nonce = nonce.toInt(),
                chainId = token.network.chainId.toLong(),
                signedHex = signedHex,
                txHash = txHash,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = token.network.displayName,
                isIncoming = false,
                data = "",
                tokenExternalId = token.externalId
            )
            else -> TokenTransaction(
                id = "tx_${System.currentTimeMillis()}",
                walletId = walletId,
                fromAddress = token.address,
                toAddress = toAddress,
                amountWei = amountInWei.toString(),
                amountDecimal = amount.toPlainString(),
                gasPriceWei = feeEstimate.gasPriceWei,
                gasPriceGwei = feeEstimate.gasPriceGwei,
                gasLimit = feeEstimate.gasLimit,
                feeWei = feeEstimate.totalFeeWei,
                feeEth = feeEstimate.totalFeeEth,
                nonce = nonce.toInt(),
                chainId = token.network.chainId.toLong(),
                signedHex = signedHex,
                txHash = txHash,
                status = TransactionStatus.PENDING,
                note = note,
                timestamp = System.currentTimeMillis(),
                feeLevel = feeLevel,
                network = token.network.displayName,
                isIncoming = false,
                tokenContract = token.contractAddress,
                tokenSymbol = token.symbol,
                tokenDecimals = token.decimals,
                data = when (token) {
                    is USDCToken, is USDTToken, is ERC20Token -> {
                        val function = org.web3j.abi.datatypes.Function(
                            "transfer",
                            listOf(Address(toAddress), Uint256(amountInWei)),
                            listOf(object : TypeReference<Bool>() {})
                        )
                        FunctionEncoder.encode(function)
                    }
                    else -> ""
                },
                tokenExternalId = token.externalId
            )
        }

        evmTransactionRepository.saveTransaction(transaction)
        logger.d(tag, "Transaction record saved: ${transaction.id} with tokenExternalId: ${token.externalId}")

        // 6. Broadcast transaction
        logger.d(tag, "Step 6: Broadcasting transaction...")
        val broadcastResult = evmBlockchainRepository.broadcastTransaction(
            signedHex,
            token.network
        )

        when (broadcastResult) {
            is Result.Success -> {
                val broadcastData = broadcastResult.data
                val updatedTransaction = when (transaction) {
                    is NativeETHTransaction -> transaction.copy(
                        status = if (broadcastData.success) TransactionStatus.SUCCESS else TransactionStatus.FAILED,
                        txHash = broadcastData.hash ?: txHash
                    )
                    is TokenTransaction -> transaction.copy(
                        status = if (broadcastData.success) TransactionStatus.SUCCESS else TransactionStatus.FAILED,
                        txHash = broadcastData.hash ?: txHash
                    )
                }
                evmTransactionRepository.updateTransaction(updatedTransaction)

                val sendResult = SendEthereumResult(
                    transactionId = transaction.id,
                    txHash = broadcastData.hash ?: txHash,
                    success = broadcastData.success,
                    error = broadcastData.error
                )

                if (sendResult.success) {
                    logger.d(tag, "Send successful: tx ${sendResult.txHash.take(8)}...")
                } else {
                    logger.e(tag, "Send failed: ${sendResult.error}")
                }

                Result.Success(sendResult)
            }

            is Result.Error -> {
                val failedTransaction = when (transaction) {
                    is NativeETHTransaction -> transaction.copy(status = TransactionStatus.FAILED)
                    is TokenTransaction -> transaction.copy(status = TransactionStatus.FAILED)
                }
                evmTransactionRepository.updateTransaction(failedTransaction)
                logger.e(tag, "Broadcast failed: ${broadcastResult.message}")
                Result.Error(broadcastResult.message, broadcastResult.throwable)
            }

            Result.Loading -> {
                logger.e(tag, "Broadcast timeout")
                Result.Error("Broadcast timeout")
            }
        }
    }

    private suspend fun createAndSignTransaction(
        token: EVMToken,
        toAddress: String,
        amount: BigDecimal,
        privateKey: String,
        gasPriceWei: BigInteger,
        nonce: BigInteger,
        chainId: Long
    ): Result<Triple<RawTransaction, String, String>> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val amountInWei = amount.multiply(
                BigDecimal.TEN.pow(token.decimals)
            ).toBigInteger()

            val (to, data, gasLimit) = when (token) {
                is NativeETH -> {
                    Triple(
                        toAddress,
                        "",
                        BigInteger.valueOf(GAS_LIMIT_STANDARD)
                    )
                }
                is USDCToken, is USDTToken, is ERC20Token -> {
                    val function = org.web3j.abi.datatypes.Function(
                        "transfer",
                        listOf(Address(toAddress), Uint256(amountInWei)),
                        listOf(object : TypeReference<Bool>() {})
                    )
                    val encodedFunction = FunctionEncoder.encode(function)

                    val gasLimit = when (token) {
                        is USDTToken -> BigInteger.valueOf(USDT_GAS_LIMIT)
                        else -> BigInteger.valueOf(DEFAULT_TOKEN_GAS_LIMIT)
                    }

                    Triple(
                        token.contractAddress,
                        encodedFunction,
                        gasLimit
                    )
                }
            }

            val rawTransaction = if (token is NativeETH) {
                RawTransaction.createEtherTransaction(
                    nonce,
                    gasPriceWei,
                    gasLimit,
                    to,
                    amountInWei
                )
            } else {
                RawTransaction.createTransaction(
                    nonce,
                    gasPriceWei,
                    gasLimit,
                    to,
                    data
                )
            }

            val credentials = Credentials.create(privateKey)
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
            val signedHex = Numeric.toHexString(signedMessage)
            val txHash = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(signedHex)))

            Triple(rawTransaction, signedHex, txHash)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val GAS_LIMIT_STANDARD = 21000L
        const val DEFAULT_TOKEN_GAS_LIMIT = 65000L
        const val USDT_GAS_LIMIT = 78000L
    }
}