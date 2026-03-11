package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.coin.bitcoin.util.BitcoinConstants.DEFAULT_INPUT_COUNT
import com.example.nexuswallet.feature.coin.bitcoin.util.BitcoinConstants.DEFAULT_OUTPUT_COUNT
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.PreparedBitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import com.example.nexuswallet.toSatoshis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrepareBitcoinTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger
) {

    private val tag = "PrepareBitcoinUC"

    suspend operator fun invoke(
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

    private fun prepareTransaction(
        bitcoinCoin: BitcoinCoin,
        toAddress: String,
        amount: BigDecimal,
        feeEstimate: BitcoinFeeEstimate,
        feeLevel: FeeLevel
    ): Result<PreparedBitcoinTransaction> {
        val satoshis = amount.toSatoshis()

        // Generate a transaction ID
        val transactionId = "btc_tx_${System.currentTimeMillis()}"

        logger.d(tag, "Transaction prepared: $transactionId")

        // Return prepared transaction info with data needed for later signing
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
}