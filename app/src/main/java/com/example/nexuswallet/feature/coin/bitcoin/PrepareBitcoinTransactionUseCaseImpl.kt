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
import org.bitcoinj.core.Utils
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrepareBitcoinTransactionUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val keyStoreRepository: KeyStoreRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : PrepareBitcoinTransactionUseCase {

    private val tag = "PrepareBitcoinUC"

    override suspend fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        network: BitcoinNetwork
    ): Result<PreparedBitcoinTransaction> = withContext(Dispatchers.IO) {
        logger.d(
            tag,
            "Preparing transaction: ${amount.toPlainString()} BTC to ${toAddress.take(8)}... | walletId=$walletId | network=$network"
        )

        // Get wallet
        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Get the specific Bitcoin coin for this network
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
        if (bitcoinCoin == null) {
            logger.e(tag, "Bitcoin not enabled for network $network in wallet: $walletId")
            return@withContext Result.Error("Bitcoin not enabled for $network")
        }

        // Get fee estimate
        val feeResult = bitcoinBlockchainRepository.getFeeEstimate(
            feeLevel,
            DEFAULT_INPUT_COUNT,
            DEFAULT_OUTPUT_COUNT
        )

        // Process based on fee result
        val result = when (feeResult) {
            is Result.Success -> {
                val feeEstimate = feeResult.data
                prepareTransaction(
                    walletId = walletId,
                    bitcoinCoin = bitcoinCoin,
                    toAddress = toAddress,
                    amount = amount,
                    feeEstimate = feeEstimate,
                    feeLevel = feeLevel
                )
            }
            is Result.Error -> {
                logger.e(tag, "Failed to get fee estimate: ${feeResult.message}")
                Result.Error("Failed to get fee estimate: ${feeResult.message}")
            }
            else -> {
                Result.Error("Unknown error getting fee estimate")
            }
        }

        return@withContext result
    }

    private suspend fun prepareTransaction(
        walletId: String,
        bitcoinCoin: BitcoinCoin,
        toAddress: String,
        amount: BigDecimal,
        feeEstimate: BitcoinFeeEstimate,
        feeLevel: FeeLevel
    ): Result<PreparedBitcoinTransaction> {
        val satoshis = amount.toSatoshis()

        // Generate a transaction ID
        val transactionId = "btc_tx_${System.currentTimeMillis()}"

        logger.d(tag, "Transaction prepared (not saved): $transactionId")

        // Check if private key exists (but don't use it yet)
        val keyType = when (bitcoinCoin.network) {
            BitcoinNetwork.Mainnet -> "BTC_MAINNET_PRIVATE_KEY"
            BitcoinNetwork.Testnet -> "BTC_TESTNET_PRIVATE_KEY"
        }

        val encryptedData = securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = keyType
        ) ?: securityPreferencesRepository.getEncryptedPrivateKey(
            walletId = walletId,
            keyType = BTC_PRIVATE_KEY_TYPE
        )

        if (encryptedData == null) {
            logger.e(tag, "No private key found for wallet: $walletId")
            return Result.Error("No private key found")
        }

        // Return prepared transaction info with ALL data needed for later signing
        return Result.Success(
            PreparedBitcoinTransaction(
                transactionId = transactionId,
                fromAddress = bitcoinCoin.address,
                toAddress = toAddress,
                amountBtc = amount,
                amountSatoshis = satoshis,
                feeBtc = feeEstimate.totalFeeBtc.toBigDecimal(),
                feeSatoshis = feeEstimate.totalFeeSatoshis,
                feePerByte = feeEstimate.feePerByte,
                feeLevel = feeLevel,
                network = bitcoinCoin.network,
                hasPrivateKey = true,
                estimatedSize = feeEstimate.estimatedSize.toInt(),
                utxoCount = DEFAULT_INPUT_COUNT
            )
        )
    }

    companion object {
        private const val BTC_PRIVATE_KEY_TYPE = "BTC_PRIVATE_KEY"
        private const val DEFAULT_INPUT_COUNT = 1
        private const val DEFAULT_OUTPUT_COUNT = 2
    }
}

interface PrepareBitcoinTransactionUseCase {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        network: BitcoinNetwork
    ): Result<PreparedBitcoinTransaction>
}

data class PreparedBitcoinTransaction(
    val transactionId: String,
    val fromAddress: String,
    val toAddress: String,
    val amountBtc: BigDecimal,
    val amountSatoshis: Long,
    val feeBtc: BigDecimal,
    val feeSatoshis: Long,
    val feePerByte: Double,
    val feeLevel: FeeLevel,
    val network: BitcoinNetwork,
    val hasPrivateKey: Boolean,
    val estimatedSize: Int,
    val utxoCount: Int
)