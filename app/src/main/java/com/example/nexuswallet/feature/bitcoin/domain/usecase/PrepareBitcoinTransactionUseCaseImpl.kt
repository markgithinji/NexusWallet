package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.bitcoin.data.model.UTXO
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.bitcoin.domain.model.PreparedBitcoinTransaction
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants.DEFAULT_INPUT_COUNT
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants.DEFAULT_OUTPUT_COUNT
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.core.util.toSatoshis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrepareBitcoinTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val tag = "PrepareBitcoinUC"

    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        network: BitcoinNetwork
    ): Result<PreparedBitcoinTransaction> = withContext(ioDispatcher) {
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

        // 1. Fetch actual UTXOs to determine input count
        val utxosResult = bitcoinBlockchainRepository.getUnspentOutputs(bitcoinCoin.address, network)
        val allUtxos = when (utxosResult) {
            is Result.Success -> utxosResult.data
            is Result.Error -> {
                logger.e(tag, "Failed to fetch UTXOs: ${utxosResult.message}")
                return@withContext Result.Error("Failed to fetch UTXOs: ${utxosResult.message}")
            }
            else -> return@withContext Result.Error("Unknown error fetching UTXOs")
        }

        if (allUtxos.isEmpty()) {
            logger.e(tag, "No UTXOs found for address: ${bitcoinCoin.address}")
            return@withContext Result.Error("No UTXOs found. Your balance might be zero.")
        }

        // 2. Initial fee estimate to get a ballpark for UTXO selection
        val initialFeeResult = bitcoinBlockchainRepository.getFeeEstimate(
            feeLevel,
            DEFAULT_INPUT_COUNT,
            DEFAULT_OUTPUT_COUNT,
            network
        )

        val initialFeeSatoshis = when (initialFeeResult) {
            is Result.Success -> initialFeeResult.data.totalFeeSatoshis
            else -> 1000L // Small fallback if initial estimate fails
        }

        // 3. Select UTXOs to cover amount + estimated fee
        val targetSatoshis = amount.toSatoshis() + initialFeeSatoshis
        val selectedUtxos = bitcoinBlockchainRepository.selectUtxos(allUtxos, targetSatoshis)

        val inputCount = if (selectedUtxos.isNotEmpty()) selectedUtxos.size else DEFAULT_INPUT_COUNT
        val outputCount = DEFAULT_OUTPUT_COUNT // Recipient + Change

        // 4. Get accurate fee estimate based on actual required inputs
        val feeResult = bitcoinBlockchainRepository.getFeeEstimate(
            feeLevel,
            inputCount,
            outputCount,
            network
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
                    feeLevel = feeLevel,
                    inputCount = inputCount
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
        feeLevel: FeeLevel,
        inputCount: Int
    ): Result<PreparedBitcoinTransaction> {
        val satoshis = amount.toSatoshis()

        // Generate a transaction ID
        val transactionId = "btc_tx_${System.currentTimeMillis()}"

        logger.d(tag, "Transaction prepared with $inputCount inputs: $transactionId")

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
                utxoCount = inputCount
            )
        )
    }
}