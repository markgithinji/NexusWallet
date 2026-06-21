package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_ETHEREUM_MAIN
import com.example.nexuswallet.feature.core.util.decodeHex
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendEVMAssetUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val getFeeEstimateUseCase: GetFeeEstimateUseCase,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val tag = "SendEVMAssetUC"

    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        token: EVMToken,
        note: String?
    ): Result<SendEVMResult> = withContext(ioDispatcher) {
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
                    it.network == token.network &&
                    it.evmTokenType == token.evmTokenType
        }
        if (!hasToken) {
            logger.e(tag, "Token ${token.symbol} not enabled for wallet: $walletId")
            return@withContext Result.Error("${token.symbol} not enabled for this wallet")
        }

        logger.d(tag, "Network: ${token.network.name}")

        // 1. Get encrypted private key
        logger.d(
            tag,
            "Step 1: Retrieving private key for wallet: $walletId with type: $KEY_ETHEREUM_MAIN"
        )
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = KEY_ETHEREUM_MAIN
        ) ?: run {
            logger.e(
                tag,
                "No private key found for wallet: $walletId with keyType: $KEY_ETHEREUM_MAIN"
            )
            return@withContext Result.Error("No private key found")
        }

        val (encryptedHex, iv) = encryptedData

        // 1b. Decrypt private key
        val privateKeyBytes = try {
            keyStoreRepository.decrypt(
                encryptedHex.decodeHex(),
                iv
            )
        } catch (e: Exception) {
            logger.e(tag, "Failed to decrypt private key: ${e.message}")
            return@withContext Result.Error("Failed to decrypt private key")
        }

        try {
            // 2. Get nonce
            logger.d(tag, "Step 2: Getting nonce...")
            val nonceResult = evmBlockchainRepository.getNonce(
                token.address,
                token.network
            )

            if (nonceResult !is Result.Success) {
                val message = (nonceResult as? Result.Error)?.message ?: "Failed to get nonce"
                logger.e(tag, message)
                return@withContext Result.Error(message)
            }
            val nonce = nonceResult.data

            // 4. Get fee estimate (includes EIP-1559 logic)
            logger.d(tag, "Step 4: Getting fee estimate...")
            val amountInWei = amount.multiply(BigDecimal.TEN.pow(token.decimals)).toBigInteger()

            val feeEstimateResult = getFeeEstimateUseCase(
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
                logger.e(tag, message)
                return@withContext Result.Error(message)
            }
            val feeEstimate = feeEstimateResult.data

            val gasLimit = BigInteger.valueOf(feeEstimate.gasLimit)
            val totalFeeWei = BigInteger(feeEstimate.totalFeeWei)
            val totalFeeEth = feeEstimate.totalFeeEth

            logger.d(
                tag,
                "Fee Estimate - EIP1559: ${feeEstimate.isEIP1559}, Limit: $gasLimit, Fee: $totalFeeEth ETH"
            )

            // 6. Create and sign transaction
            logger.d(tag, "Step 6: Creating and signing transaction...")

            val createResult = when (token.evmTokenType) {
                EVMTokenType.NATIVE -> if (feeEstimate.isEIP1559) {
                    evmBlockchainRepository.createAndSignNative1559Transaction(
                        fromAddress = token.address,
                        fromPrivateKey = privateKeyBytes,
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
                        fromPrivateKey = privateKeyBytes,
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
                        fromPrivateKey = privateKeyBytes,
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
                        fromPrivateKey = privateKeyBytes,
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

            if (createResult !is Result.Success) {
                val message =
                    (createResult as? Result.Error)?.message ?: "Failed to create transaction"
                logger.e(tag, message)
                return@withContext Result.Error(message)
            }
            val (_, signedHex, txHash) = createResult.data

            val gasPriceGwei = feeEstimate.gasPriceGwei
            val gasPriceWei = feeEstimate.gasPriceWei

            // 6. Broadcast transaction
            logger.d(tag, "Step 6: Broadcasting transaction...")
            val broadcastResult = evmBlockchainRepository.broadcastTransaction(
                signedHex,
                token.network
            )

            when (broadcastResult) {
                is Result.Success -> {
                    val broadcastData = broadcastResult.data
                    val finalTxHash = broadcastData.hash ?: txHash

                    // 7. Save transaction after successful broadcast
                    if (broadcastData.success) {
                        logger.d(
                            tag,
                            "Step 7: Creating and saving transaction record after successful broadcast..."
                        )

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
                        logger.d(
                            tag,
                            "Transaction saved after successful broadcast: ${transaction.id} with hash: ${
                                transaction.txHash?.take(8)
                            }..."
                        )
                    } else {
                        logger.e(
                            tag,
                            "Broadcast returned success=false, no transaction saved: ${broadcastData.error}"
                        )
                    }

                    val sendResult = SendEVMResult(
                        transactionId = finalTxHash,
                        txHash = finalTxHash,
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
                    logger.e(
                        tag,
                        "Broadcast failed: ${broadcastResult.message}, no transaction saved"
                    )
                    Result.Error(broadcastResult.message, broadcastResult.throwable)
                }

                Result.Loading -> {
                    logger.e(tag, "Broadcast timeout, no transaction saved")
                    Result.Error("Broadcast timeout")
                }
            }
        } finally {
            privateKeyBytes.fill(0)
        }
    }
}