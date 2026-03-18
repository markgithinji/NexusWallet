package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.toHex
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTransactionType
import com.example.nexuswallet.feature.ethereum.domain.model.SendEVMResult
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.Dispatchers
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
            isToken = token.evmTokenType != EVMTokenType.NATIVE
        )

        if (feeResult is Result.Error) {
            logger.e(tag, "Failed to get fee estimate: ${feeResult.message}")
            return@withContext Result.Error(feeResult.message)
        }
        val feeEstimate = (feeResult as Result.Success).data
        val gasPriceWei = BigInteger(feeEstimate.gasPriceWei)

        // 4. Create and sign transaction
        logger.d(tag, "Step 4: Creating and signing transaction...")
        val amountInWei = amount.multiply(BigDecimal.TEN.pow(token.decimals)).toBigInteger()

        val createResult = when (token.evmTokenType) {
            EVMTokenType.NATIVE -> evmBlockchainRepository.createAndSignNativeTransaction(
                fromAddress = token.address,
                fromPrivateKey = privateKey,
                toAddress = toAddress,
                amountWei = amountInWei,
                gasPriceWei = gasPriceWei,
                nonce = nonce,
                chainId = token.network.chainId.toLong(),
                network = token.network
            )
            EVMTokenType.USDC, EVMTokenType.USDT -> evmBlockchainRepository.createAndSignTokenTransaction(
                fromAddress = token.address,
                fromPrivateKey = privateKey,
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

        // 5. Broadcast transaction
        logger.d(tag, "Step 5: Broadcasting transaction...")
        val broadcastResult = evmBlockchainRepository.broadcastTransaction(
            signedHex,
            token.network
        )

        when (broadcastResult) {
            is Result.Success -> {
                val broadcastData = broadcastResult.data
                val finalTxHash = broadcastData.hash ?: txHash

                // 6. save transaction after successful broadcast
                if (broadcastData.success) {
                    logger.d(
                        tag,
                        "Step 6: Creating and saving transaction record after successful broadcast..."
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
                            fee = feeEstimate.totalFeeEth,
                            symbol = token.symbol,
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
                            fee = feeEstimate.totalFeeEth,
                            symbol = token.symbol,
                            amountWei = amountInWei.toString(),
                            gasPriceWei = feeEstimate.gasPriceWei,
                            gasPriceGwei = feeEstimate.gasPriceGwei,
                            gasLimit = feeEstimate.gasLimit,
                            feeWei = feeEstimate.totalFeeWei,
                            feeEth = feeEstimate.totalFeeEth,
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
                logger.e(tag, "Broadcast failed: ${broadcastResult.message}, no transaction saved")
                Result.Error(broadcastResult.message, broadcastResult.throwable)
            }

            Result.Loading -> {
                logger.e(tag, "Broadcast timeout, no transaction saved")
                Result.Error("Broadcast timeout")
            }
        }
    }

    companion object {
        private const val KEY_ETHEREUM_MAIN = "ethereum_main"
    }
}