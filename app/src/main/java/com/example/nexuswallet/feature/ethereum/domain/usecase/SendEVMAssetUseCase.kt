package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_ETHEREUM_MAIN
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.use
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTransactionType
import com.example.nexuswallet.feature.ethereum.domain.model.SendEVMResult
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.GWEI_TO_WEI
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendEVMAssetUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val getEVMFeeEstimateUseCase: GetEVMFeeEstimateUseCase,
    private val vaultRepository: VaultRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        token: EVMToken,
        note: String?,
        cipher: Cipher? = null
    ): Result<SendEVMResult> = withContext(ioDispatcher) {
        logger.d(TAG, "Starting EVM send | walletId=$walletId, token=${token.symbol}, target=${toAddress.take(8)}...")

        // Validate wallet exists
        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(TAG, "Wallet not found | walletId=$walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Verify the token belongs to this wallet
        val hasToken = wallet.evmTokens.any {
            it.address == token.address &&
                    it.contractAddress == token.contractAddress &&
                    it.network == token.network &&
                    it.evmTokenType == token.evmTokenType
        }
        if (!hasToken) {
            logger.e(TAG, "Token mismatch | ${token.symbol} not enabled for this wallet")
            return@withContext Result.Error("${token.symbol} not enabled for this wallet")
        }

        logger.d(TAG, "Network: ${token.network.name}")

        // 1. Get encrypted private key
        val encryptedData = vaultRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = KEY_ETHEREUM_MAIN
        ) ?: run {
            logger.e(TAG, "No private key found in vault | keyType=$KEY_ETHEREUM_MAIN")
            return@withContext Result.Error("No private key found")
        }

        val (encryptedHex, iv) = encryptedData

        // 2. Get nonce
        logger.d(TAG, "Fetching nonce for address=${token.address}")
        val nonceResult = evmBlockchainRepository.getNonce(
            token.address,
            token.network
        )

        if (nonceResult !is Result.Success) {
            val message = (nonceResult as? Result.Error)?.message ?: "Failed to get nonce"
            logger.e(TAG, "Failed to get nonce | error=$message")
            return@withContext Result.Error(message)
        }
        val nonce = nonceResult.data
        logger.d(TAG, "Nonce retrieved: $nonce")

        // 4. Get fee estimate (includes EIP-1559 logic)
        val amountInWei = amount.multiply(BigDecimal.TEN.pow(token.decimals)).toBigInteger()

        logger.d(TAG, "Requesting fee estimate | network=${token.network.name}")
        val feeEstimateResult = getEVMFeeEstimateUseCase(
            feeLevel = feeLevel,
            network = token.network,
            isToken = token.evmTokenType != EVMTokenType.NATIVE,
            fromAddress = token.address,
            toAddress = toAddress,
            amount = amountInWei,
            tokenContract = if (token.evmTokenType == EVMTokenType.NATIVE) null else token.contractAddress
        )

        if (feeEstimateResult !is Result.Success) {
            val message =
                (feeEstimateResult as? Result.Error)?.message ?: "Failed to get fee estimate"
            logger.e(TAG, "Fee estimation failed | error=$message")
            return@withContext Result.Error(message)
        }
        val feeEstimate = feeEstimateResult.data

        val gasLimit = BigInteger.valueOf(feeEstimate.gasLimit)
        val totalFeeWei = BigInteger(feeEstimate.totalFeeWei)
        val totalFeeEth = feeEstimate.totalFeeEth
        logger.d(TAG, "Fee estimate received | totalFee=$totalFeeEth ETH, isEIP1559=${feeEstimate.isEIP1559}")

        // 5. Decrypt private key
        val decryptionResult = if (cipher != null) {
            logger.d(TAG, "Decrypting private key using provided cipher")
            keyStoreRepository.decryptWithCipher(cipher, encryptedHex.decodeHex())
        } else {
            logger.d(TAG, "Decrypting private key using stored IV")
            keyStoreRepository.decrypt(encryptedHex.decodeHex(), iv)
        }

        if (decryptionResult is Result.Error) {
            logger.e(TAG, "Decryption failed | error=${decryptionResult.message}")
            return@withContext decryptionResult
        }

        val privateKeyBytes = (decryptionResult as Result.Success).data

        // 6. Create and sign transaction
        logger.d(TAG, "Creating and signing transaction")
        val createResult = privateKeyBytes.use { keyBytes ->
            when (token.evmTokenType) {
                EVMTokenType.NATIVE -> if (feeEstimate.isEIP1559) {
                    evmBlockchainRepository.createAndSignNative1559Transaction(
                        fromAddress = token.address,
                        fromPrivateKey = keyBytes,
                        toAddress = toAddress,
                        amountWei = amountInWei,
                        maxPriorityFeePerGas = BigDecimal(feeEstimate.maxPriorityFeeGwei!!)
                            .multiply(BigDecimal(GWEI_TO_WEI.toString())).toBigInteger(),
                        maxFeePerGas = BigInteger(feeEstimate.gasPriceWei),
                        gasLimit = gasLimit,
                        nonce = nonce,
                        chainId = token.network.chainId.toLong(),
                        network = token.network
                    )
                } else {
                    evmBlockchainRepository.createAndSignNativeTransaction(
                        fromAddress = token.address,
                        fromPrivateKey = keyBytes,
                        toAddress = toAddress,
                        amountWei = amountInWei,
                        gasPriceWei = BigInteger(feeEstimate.gasPriceWei),
                        gasLimit = gasLimit,
                        nonce = nonce,
                        chainId = token.network.chainId.toLong(),
                        network = token.network
                    )
                }

                EVMTokenType.USDC, EVMTokenType.USDT -> if (feeEstimate.isEIP1559) {
                    evmBlockchainRepository.createAndSignToken1559Transaction(
                        fromAddress = token.address,
                        fromPrivateKey = keyBytes,
                        toAddress = toAddress,
                        amount = amountInWei,
                        tokenContract = token.contractAddress,
                        tokenDecimals = token.decimals,
                        maxPriorityFeePerGas = BigDecimal(feeEstimate.maxPriorityFeeGwei!!)
                            .multiply(BigDecimal(GWEI_TO_WEI.toString())).toBigInteger(),
                        maxFeePerGas = BigInteger(feeEstimate.gasPriceWei),
                        gasLimit = gasLimit,
                        nonce = nonce,
                        chainId = token.network.chainId.toLong(),
                        network = token.network,
                        evmTokenType = token.evmTokenType
                    )
                } else {
                    evmBlockchainRepository.createAndSignTokenTransaction(
                        fromAddress = token.address,
                        fromPrivateKey = keyBytes,
                        toAddress = toAddress,
                        amount = amountInWei,
                        tokenContract = token.contractAddress,
                        tokenDecimals = token.decimals,
                        gasPriceWei = BigInteger(feeEstimate.gasPriceWei),
                        gasLimit = gasLimit,
                        nonce = nonce,
                        chainId = token.network.chainId.toLong(),
                        network = token.network,
                        evmTokenType = token.evmTokenType
                    )
                }
            }
        }

        if (createResult !is Result.Success) {
            val message =
                (createResult as? Result.Error)?.message ?: "Failed to create transaction"
            logger.e(TAG, "Transaction creation failed | error=$message")
            return@withContext Result.Error(message)
        }
        val (_, signedHex, txHash) = createResult.data
        logger.d(TAG, "Transaction created and signed locally | offlineHash=$txHash")

        val gasPriceGwei = feeEstimate.gasPriceGwei
        val gasPriceWei = feeEstimate.gasPriceWei

        // 6. Broadcast transaction
        logger.d(TAG, "Broadcasting transaction to ${token.network.name}")
        val broadcastResult = evmBlockchainRepository.broadcastTransaction(
            signedHex,
            token.network
        )

        when (broadcastResult) {
            is Result.Success -> {
                val broadcastData = broadcastResult.data
                val finalTxHash = broadcastData.hash ?: txHash
                logger.d(TAG, "Broadcast completed | success=${broadcastData.success}, hash=$finalTxHash")

                // 7. Save transaction after successful broadcast
                if (broadcastData.success) {
                    val transaction = when (token.evmTokenType) {
                        EVMTokenType.NATIVE -> NativeETHTransaction(
                            id = finalTxHash,
                            walletId = walletId,
                            fromAddress = token.address,
                            toAddress = toAddress,
                            status = TransactionStatus.PENDING,
                            timestamp = System.currentTimeMillis(),
                            note = note,
                            feeLevel = feeLevel,
                            network = token.network,
                            isIncoming = false,
                            txHash = finalTxHash,
                            amount = amount.toPlainString(),
                            fee = totalFeeEth,
                            symbol = token.symbol,
                            amountWei = amountInWei.toString(),
                            amountEth = amount.toPlainString(),
                            gasPriceWei = gasPriceWei,
                            gasPriceGwei = gasPriceGwei,
                            gasLimit = gasLimit.toLong(),
                            feeWei = totalFeeWei.toString(),
                            feeEth = totalFeeEth,
                            nonce = nonce.toInt(),
                            chainId = token.network.chainId.toLong(),
                            signedHex = signedHex,
                            transactionType = EVMTransactionType.NATIVE_ETH,
                            evmTokenType = token.evmTokenType,
                            data = ""
                        )

                        EVMTokenType.USDC, EVMTokenType.USDT -> TokenTransaction(
                            id = finalTxHash,
                            walletId = walletId,
                            fromAddress = token.address,
                            toAddress = toAddress,
                            status = TransactionStatus.PENDING,
                            timestamp = System.currentTimeMillis(),
                            note = note,
                            feeLevel = feeLevel,
                            network = token.network,
                            isIncoming = false,
                            txHash = finalTxHash,
                            amount = amount.toPlainString(),
                            fee = totalFeeEth,
                            symbol = token.symbol,
                            amountWei = amountInWei.toString(),
                            gasPriceWei = gasPriceWei,
                            gasPriceGwei = gasPriceGwei,
                            gasLimit = gasLimit.toLong(),
                            feeWei = totalFeeWei.toString(),
                            feeEth = totalFeeEth,
                            nonce = nonce.toInt(),
                            chainId = token.network.chainId.toLong(),
                            signedHex = signedHex,
                            transactionType = EVMTransactionType.TOKEN,
                            evmTokenType = token.evmTokenType,
                            tokenContract = token.contractAddress,
                            data = ""
                        )
                    }

                    evmTransactionRepository.saveTransaction(transaction)
                    logger.d(TAG, "Transaction record saved to database")
                }

                val sendResult = SendEVMResult(
                    transactionId = finalTxHash,
                    txHash = finalTxHash,
                    success = broadcastData.success,
                    error = broadcastData.error
                )

                Result.Success(sendResult)
            }

            is Result.Error -> {
                logger.e(TAG, "Broadcast failed | error=${broadcastResult.message}")
                Result.Error(broadcastResult.message, broadcastResult.throwable)
            }

            Result.Loading -> {
                logger.e(TAG, "Broadcast timed out")
                Result.Error("Broadcast timeout")
            }
        }
    }

    companion object {
        private const val TAG = "SendEVMAssetUC"
    }
}
