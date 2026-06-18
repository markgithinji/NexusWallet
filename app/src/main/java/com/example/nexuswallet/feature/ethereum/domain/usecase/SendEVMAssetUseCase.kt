package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.WalletConstants.KEY_ETHEREUM_MAIN
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTransactionType
import com.example.nexuswallet.feature.ethereum.domain.model.SendEVMResult
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.DEFAULT_TOKEN_GAS_LIMIT
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.GAS_LIMIT_STANDARD
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.GWEI_TO_WEI
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.USDT_GAS_LIMIT
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.WEI_PER_ETH
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendEVMAssetUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val logger: Logger
) {

    private val tag = "SendEVMAssetUC"

    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        token: EVMToken,
        note: String?
    ): Result<SendEVMResult> = withContext(Dispatchers.IO) {
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
        logger.d(tag, "Step 1: Retrieving private key...")
        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = KEY_ETHEREUM_MAIN
        ) ?: run {
            logger.e(tag, "No private key found for wallet: $walletId")
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

            if (nonceResult is Result.Error) {
                logger.e(tag, "Failed to get nonce: ${nonceResult.message}")
                return@withContext Result.Error(nonceResult.message)
            }
            val nonce = (nonceResult as Result.Success).data

            // 3. Get current gas price directly
            logger.d(tag, "Step 3: Getting current gas price...")
            val gasPriceResult = evmBlockchainRepository.getCurrentGasPrice(token.network)

            if (gasPriceResult is Result.Error) {
                logger.e(tag, "Failed to get gas price: ${gasPriceResult.message}")
                return@withContext Result.Error(gasPriceResult.message)
            }
            val gasPrice = (gasPriceResult as Result.Success).data

            // 4. Calculate gas price based on fee level
            val gasPriceGwei = when (feeLevel) {
                FeeLevel.SLOW -> gasPrice.safe
                FeeLevel.NORMAL -> gasPrice.propose
                FeeLevel.FAST -> gasPrice.fast
            }

            // Convert Gwei to Wei
            val gasPriceWei = (BigDecimal(gasPriceGwei) * BigDecimal(GWEI_TO_WEI)).toBigInteger()

            // Gas limit based on token type
            val gasLimit = if (token.evmTokenType != EVMTokenType.NATIVE) {
                when (token.evmTokenType) {
                    EVMTokenType.USDT -> USDT_GAS_LIMIT
                    else -> DEFAULT_TOKEN_GAS_LIMIT
                }
            } else {
                GAS_LIMIT_STANDARD
            }

            val totalFeeWei = gasPriceWei.multiply(BigInteger.valueOf(gasLimit))
            val totalFeeEth = BigDecimal(totalFeeWei).divide(
                BigDecimal(WEI_PER_ETH),
                18,
                RoundingMode.HALF_UP
            ).toPlainString()

            logger.d(tag, "Gas price: $gasPriceGwei Gwei, Fee: $totalFeeEth ETH")

            // 5. Create and sign transaction
            logger.d(tag, "Step 5: Creating and signing transaction...")
            val amountInWei = amount.multiply(BigDecimal.TEN.pow(token.decimals)).toBigInteger()

            val createResult = when (token.evmTokenType) {
                EVMTokenType.NATIVE -> evmBlockchainRepository.createAndSignNativeTransaction(
                    fromAddress = token.address,
                    fromPrivateKey = privateKeyBytes,
                    toAddress = toAddress,
                    amountWei = amountInWei,
                    gasPriceWei = gasPriceWei,
                    nonce = nonce,
                    chainId = token.network.chainId.toLong(),
                    network = token.network
                )

                EVMTokenType.USDC, EVMTokenType.USDT -> evmBlockchainRepository.createAndSignTokenTransaction(
                    fromAddress = token.address,
                    fromPrivateKey = privateKeyBytes,
                    toAddress = toAddress,
                    amount = amountInWei,
                    tokenContract = token.contractAddress,
                    tokenDecimals = token.decimals,
                    gasPriceWei = gasPriceWei,
                    nonce = nonce,
                    chainId = token.network.chainId.toLong(),
                    network = token.network,
                    evmTokenType = token.evmTokenType
                )
            }

            if (createResult is Result.Error) {
                logger.e(tag, "Failed to create transaction: ${createResult.message}")
                return@withContext Result.Error(createResult.message)
            }
            val (_, signedHex, txHash) = (createResult as Result.Success).data

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
                                status = TransactionStatus.SUCCESS,
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
                                gasPriceWei = gasPriceWei.toString(),
                                gasPriceGwei = gasPriceGwei,
                                gasLimit = gasLimit,
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
                                status = TransactionStatus.SUCCESS,
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
                                gasPriceWei = gasPriceWei.toString(),
                                gasPriceGwei = gasPriceGwei,
                                gasLimit = gasLimit,
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