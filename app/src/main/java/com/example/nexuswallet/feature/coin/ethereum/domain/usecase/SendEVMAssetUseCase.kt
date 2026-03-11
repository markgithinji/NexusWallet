package com.example.nexuswallet.feature.coin.ethereum.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SafeApiCall
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.SendEthereumResult
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.ERC20Token
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
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

        // 5. Broadcast transaction
        logger.d(tag, "Step 5: Broadcasting transaction...")
        val broadcastResult = evmBlockchainRepository.broadcastTransaction(
            signedHex,
            token.network
        )

        when (broadcastResult) {
            is Result.Success -> {
                val broadcastData = broadcastResult.data

                // 6. save transaction after successful broadcast
                if (broadcastData.success) {
                    logger.d(
                        tag,
                        "Step 6: Creating and saving transaction record after successful broadcast..."
                    )

                    val amountInWei =
                        amount.multiply(BigDecimal.TEN.pow(token.decimals)).toBigInteger()

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
                            txHash = broadcastData.hash ?: txHash,
                            status = TransactionStatus.SUCCESS,
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
                            txHash = broadcastData.hash ?: txHash,
                            status = TransactionStatus.SUCCESS,
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
                                    val function = Function(
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

                val sendResult = SendEthereumResult(
                    transactionId = "tx_${System.currentTimeMillis()}",
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
                logger.e(tag, "Broadcast failed: ${broadcastResult.message}, no transaction saved")
                Result.Error(broadcastResult.message, broadcastResult.throwable)
            }

            Result.Loading -> {
                logger.e(tag, "Broadcast timeout, no transaction saved")
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
                    val function = Function(
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