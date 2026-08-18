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
import com.example.nexuswallet.feature.core.util.toSatoshis
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.ScriptPattern
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prepares a Bitcoin transaction by selecting UTXOs and estimating fees.
 */
@Singleton
class PrepareBitcoinTransactionUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val selectBitcoinUtxosUseCase: SelectBitcoinUtxosUseCase,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        network: BitcoinNetwork
    ): Result<PreparedBitcoinTransaction> = withContext(ioDispatcher) {
        logger.d(
            TAG,
            "Preparing transaction: ${amount.toPlainString()} BTC to ${toAddress.take(8)}... | walletId=$walletId | network=$network"
        )

        // Get wallet
        val wallet = walletRepository.getWallet(walletId)
        if (wallet == null) {
            logger.e(TAG, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Get the specific Bitcoin coin for this network
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
        if (bitcoinCoin == null) {
            logger.e(TAG, "Bitcoin not enabled for network $network in wallet: $walletId")
            return@withContext Result.Error("Bitcoin not enabled for $network")
        }

        val networkParams = when (network) {
            BitcoinNetwork.Mainnet -> MainNetParams.get()
            BitcoinNetwork.Testnet -> TestNet3Params.get()
        }

        // Derive SegWit address from xpub to check for modern UTXOs
        val segwitAddressString = try {
            val masterKey = DeterministicKey.deserializeB58(bitcoinCoin.xpub, networkParams)
            SegwitAddress.fromKey(networkParams, masterKey).toString()
        } catch (_: Exception) {
            null
        }

        // 1. Fetch actual UTXOs from both address types
        val targetAddresses = listOfNotNull(bitcoinCoin.address, segwitAddressString)
        val allUtxos = mutableListOf<UTXO>()
        
        for (addr in targetAddresses) {
            val utxosResult = bitcoinBlockchainRepository.getUnspentOutputs(addr, network)
            if (utxosResult is Result.Success) {
                allUtxos.addAll(utxosResult.data)
            }
        }

        if (allUtxos.isEmpty()) {
            logger.e(TAG, "No UTXOs found for wallet: $walletId")
            return@withContext Result.Error("No UTXOs found. Your balance might be zero.")
        }

        // 2. Initial fee estimate to get a ballpark for UTXO selection
        // Detect if we have any SegWit UTXOs to determine calculation base
        val containsSegwit = allUtxos.any { ScriptPattern.isP2WPKH(it.script) }
        
        val initialFeeResult = bitcoinBlockchainRepository.getFeeEstimate(
            feeLevel = feeLevel,
            inputCount = DEFAULT_INPUT_COUNT,
            outputCount = DEFAULT_OUTPUT_COUNT,
            network = network,
            isSegwit = containsSegwit
        )

        val feePerByte = if (initialFeeResult is Result.Success) initialFeeResult.data.feePerByte else 10.0

        // 3. Select UTXOs
        val targetSats = amount.toSatoshis()
        logger.d(TAG, "Selecting UTXOs for $targetSats sats with feePerByte=$feePerByte from ${allUtxos.size} UTXOs")
        
        // Strategy: First try with 2 outputs (recipient + change)
        var selectedUtxos = selectBitcoinUtxosUseCase(
            utxos = allUtxos,
            targetSatoshis = targetSats,
            feePerByte = feePerByte,
            outputCount = 2
        )
        
        var finalOutputCount = 2

        if (selectedUtxos.isEmpty()) {
            // If it failed, try with 1 output (Sweep/Max case)
            logger.d(TAG, "Selection with 2 outputs failed, trying with 1 output (Sweep)")
            selectedUtxos = selectBitcoinUtxosUseCase(
                utxos = allUtxos,
                targetSatoshis = targetSats,
                feePerByte = feePerByte,
                outputCount = 1
            )
            
            if (selectedUtxos.isNotEmpty()) {
                finalOutputCount = 1
            }
        }

        if (selectedUtxos.isEmpty()) {
            val totalAvailable = allUtxos.map { it.value.value }.sum()
            logger.e(TAG, "Insufficient funds: targetSats=$targetSats, totalAvailable=$totalAvailable, feePerByte=$feePerByte")
            return@withContext Result.Error("Insufficient funds to cover amount and network fees")
        }

        val inputCount = selectedUtxos.size

        // 4. Get accurate fee estimate based on actual required inputs and script types
        val isSegwitTx = selectedUtxos.any { ScriptPattern.isP2WPKH(it.script) }
        
        val apiFeeResult = bitcoinBlockchainRepository.getFeeEstimate(
            feeLevel = feeLevel,
            inputCount = inputCount,
            outputCount = finalOutputCount,
            network = network,
            isSegwit = isSegwitTx
        )

        val result = when (apiFeeResult) {
            is Result.Success -> {
                val feeEstimate = apiFeeResult.data
                
                // Final safety check: Total Required vs Total Available
                val totalRequiredSats = targetSats + feeEstimate.totalFeeSatoshis
                val totalAvailableSats = selectedUtxos.sumOf { it.value.value }
                
                logger.d(TAG, "Final Check: Target=$targetSats, Fee=${feeEstimate.totalFeeSatoshis}, TotalRequired=$totalRequiredSats, Available=$totalAvailableSats")
                
                if (totalRequiredSats > totalAvailableSats) {
                    val shortfall = totalRequiredSats - totalAvailableSats
                    logger.e(TAG, "Insufficient funds after final estimation: short by $shortfall sats")
                    Result.Error("Insufficient funds to cover fees ($shortfall sats short)")
                } else {
                    prepareTransaction(
                        bitcoinCoin = bitcoinCoin,
                        toAddress = toAddress,
                        amount = amount,
                        feeEstimate = feeEstimate,
                        feeLevel = feeLevel,
                        inputCount = inputCount,
                        selectedUtxos = selectedUtxos
                    )
                }
            }

            is Result.Error -> {
                logger.e(TAG, "Failed to get fee estimate: ${apiFeeResult.message}")
                Result.Error("Failed to get fee estimate: ${apiFeeResult.message}")
            }

            else -> Result.Error("Unknown error getting fee estimate")
        }

        return@withContext result
    }

    private fun prepareTransaction(
        bitcoinCoin: BitcoinCoin,
        toAddress: String,
        amount: BigDecimal,
        feeEstimate: BitcoinFeeEstimate,
        feeLevel: FeeLevel,
        inputCount: Int,
        selectedUtxos: List<UTXO>
    ): Result<PreparedBitcoinTransaction> {
        val satoshis = amount.toSatoshis()
        val transactionId = "btc_tx_${System.currentTimeMillis()}"

        logger.d(TAG, "Transaction prepared with $inputCount inputs: $transactionId")

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
                utxoCount = inputCount,
                selectedUtxos = selectedUtxos
            )
        )
    }

    companion object {
        private const val TAG = "PrepareBitcoinUC"
    }
}