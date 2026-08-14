package com.example.nexuswallet.feature.bitcoin.data.repository

import com.example.nexuswallet.feature.bitcoin.data.model.ParsedTransaction
import com.example.nexuswallet.feature.bitcoin.data.model.UTXO
import com.example.nexuswallet.feature.bitcoin.data.remote.api.BitcoinApi
import com.example.nexuswallet.feature.bitcoin.data.remote.model.EsploraTransactionDto
import com.example.nexuswallet.feature.bitcoin.data.toDomain
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants.SATOSHIS_PER_BTC
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.SafeApiCall
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.ScriptBuilder
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class BitcoinBlockchainRepositoryImpl @Inject constructor(
    @param:Named("bitcoinMainnet") private val mainnetApi: BitcoinApi,
    @param:Named("bitcoinTestnet") private val testnetApi: BitcoinApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BitcoinBlockchainRepository {

    private fun getApiForNetwork(network: BitcoinNetwork): BitcoinApi {
        return when (network) {
            BitcoinNetwork.Mainnet -> mainnetApi
            BitcoinNetwork.Testnet -> testnetApi
        }
    }

    /**
     * Get Bitcoin balance for an address
     */
    override suspend fun getBalance(
        address: String,
        network: BitcoinNetwork
    ): Result<BigDecimal> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val api = getApiForNetwork(network)
            val response = api.getAddressInfo(address)

            val confirmed =
                response.chainStatsRDto.fundedTxoSum - response.chainStatsRDto.spentTxoSum
            val unconfirmed =
                response.mempoolStatsDto.fundedTxoSum - response.mempoolStatsDto.spentTxoSum
            val totalSatoshis = confirmed + unconfirmed

            BigDecimal(totalSatoshis).divide(
                BigDecimal(SATOSHIS_PER_BTC),
                8,
                RoundingMode.HALF_UP
            )
        }
    }

    override suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        inputCount: Int,
        outputCount: Int,
        network: BitcoinNetwork,
        isSegwit: Boolean
    ): Result<BitcoinFeeEstimate> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val api = getApiForNetwork(network)
            val estimates = api.getFeeEstimates()

            // Get fee rate based on confirmation target
            val feePerByte = estimates[when (feeLevel) {
                FeeLevel.SLOW -> SLOW_TARGET
                FeeLevel.NORMAL -> NORMAL_TARGET
                FeeLevel.FAST -> FAST_TARGET
            }] ?: run {
                // Fallback to finding the closest available target if the exact one isn't provided by the API
                val desiredTarget = when (feeLevel) {
                    FeeLevel.SLOW -> SLOW_TARGET.toInt()
                    FeeLevel.NORMAL -> NORMAL_TARGET.toInt()
                    FeeLevel.FAST -> FAST_TARGET.toInt()
                }

                val closestKey = estimates.keys
                    .mapNotNull { it.toIntOrNull() }
                    .minByOrNull { Math.abs(it - desiredTarget) }
                    ?.toString()

                closestKey?.let { estimates[it] } ?: when (feeLevel) {
                    FeeLevel.SLOW -> DEFAULT_SLOW_FEE
                    FeeLevel.NORMAL -> DEFAULT_NORMAL_FEE
                    FeeLevel.FAST -> DEFAULT_FAST_FEE
                }
            }

            // Calculate actual transaction size based on inputs/outputs
            val estimatedSize = calculateTransactionSize(inputCount, outputCount, isSegwit)
            val totalFeeSatoshis = (estimatedSize * feePerByte).toLong()

            val totalFeeBtc = BigDecimal(totalFeeSatoshis).divide(
                BigDecimal(SATOSHIS_PER_BTC),
                8,
                RoundingMode.HALF_UP
            ).toPlainString()

            val blockTarget = when (feeLevel) {
                FeeLevel.SLOW -> SLOW_TARGET.toInt()
                FeeLevel.NORMAL -> NORMAL_TARGET.toInt()
                FeeLevel.FAST -> FAST_TARGET.toInt()
            }

            BitcoinFeeEstimate(
                feePerByte = feePerByte,
                totalFeeSatoshis = totalFeeSatoshis,
                totalFeeBtc = totalFeeBtc,
                estimatedTime = blockTarget * BLOCK_TIME_MINUTES * 60,
                priority = feeLevel,
                estimatedSize = estimatedSize,
                blockTarget = blockTarget
            )
        }
    }

    /**
     * Calculate transaction size based on number of inputs and outputs.
     * Uses virtual bytes (vBytes) for SegWit.
     */
    private fun calculateTransactionSize(
        inputCount: Int,
        outputCount: Int,
        isSegwit: Boolean
    ): Long {
        return if (isSegwit) {
            // SegWit vByte estimation
            // Header (11) + Inputs (68 * count) + Outputs (31 * count)
            (11 + (inputCount * 68) + (outputCount * 31)).toLong()
        } else {
            BitcoinConstants.BASE_TX_SIZE + (inputCount * BitcoinConstants.BYTES_PER_INPUT) + (outputCount * BitcoinConstants.BYTES_PER_OUTPUT)
        }
    }

    /**
     * Broadcast transaction using API
     */
    override suspend fun broadcastTransaction(
        signedHex: String,
        network: BitcoinNetwork
    ): Result<String> = withContext(ioDispatcher) {
        // Business logic validation first
        if (!signedHex.matches(HEX_REGEX)) {
            return@withContext Result.Error("Invalid transaction hex format")
        }

        SafeApiCall.make {
            val api = getApiForNetwork(network)
            val response = api.broadcastTransaction(signedHex)
            val txId = response.string().trim()

            if (txId.matches(TXID_REGEX)) {
                txId
            } else {
                throw Exception("Invalid response from network")
            }
        }
    }

    /**
     * Get transaction status
     */
    override suspend fun getTransactionStatus(
        txid: String,
        network: BitcoinNetwork
    ): Result<TransactionStatus> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val api = getApiForNetwork(network)
            val tx = api.getTransaction(txid)

            if (tx.status.confirmed) {
                TransactionStatus.SUCCESS
            } else {
                TransactionStatus.PENDING
            }
        }
    }

    /**
     * Get all transactions for an address
     */
    override suspend fun getAddressTransactions(
        walletId: String,
        address: String,
        network: BitcoinNetwork
    ): Result<List<BitcoinTransaction>> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val api = getApiForNetwork(network)
            val transactions = api.getAddressTransactions(address)

            transactions.mapNotNull { tx ->
                parseTransaction(tx, address)?.let { parsed ->
                    tx.toDomain(
                        walletId = walletId,
                        fromAddress = parsed.fromAddress,
                        toAddress = parsed.toAddress,
                        amount = parsed.amount,
                        isIncoming = parsed.isIncoming,
                        network = network
                    )
                }
            }
        }
    }

    /**
     * Get UTXOs for an address.
     * Optimized to derive scriptPubKey locally from address, avoiding O(N) network requests.
     */
    override suspend fun getUnspentOutputs(
        address: String,
        network: BitcoinNetwork
    ): Result<List<UTXO>> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val api = getApiForNetwork(network)
            val utxos = api.getUtxos(address)

            if (utxos.isEmpty()) {
                return@make emptyList()
            }

            val networkParams = when (network) {
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
            }

            // Derive scriptPubKey from the address directly
            val btcjAddress = Address.fromString(networkParams, address)
            val scriptPubKey = ScriptBuilder.createOutputScript(btcjAddress)

            utxos.map { utxo ->
                UTXO(
                    outPoint = TransactionOutPoint(
                        networkParams,
                        utxo.vout.toLong(),
                        Sha256Hash.wrap(utxo.txid)
                    ),
                    value = Coin.valueOf(utxo.value),
                    script = scriptPubKey
                )
            }
        }
    }

    /**
     * Select UTXOs for a transaction.
     *
     * 1. Attempts Branch and Bound to find a combination that matches the target closely,
     *    potentially avoiding a change output and reducing transaction size/fees.
     * 2. Falls back to "Largest First" to minimize the number of inputs and current fees.
     */
    override fun selectUtxos(utxos: List<UTXO>, targetSatoshis: Long): List<UTXO> {
        if (utxos.isEmpty()) return emptyList()

        // 1. Try Branch and Bound to find an exact (or near-exact) match to avoid change
        val bnbResult = branchAndBound(utxos, targetSatoshis)
        if (bnbResult.isNotEmpty()) return bnbResult

        // 2. Fallback to Largest First (minimizes input count and thus current fee)
        val selected = mutableListOf<UTXO>()
        var totalSelected = 0L
        val sortedUtxos = utxos.sortedByDescending { it.value.value }

        for (utxo in sortedUtxos) {
            selected.add(utxo)
            totalSelected += utxo.value.value
            if (totalSelected >= targetSatoshis) break
        }

        return if (totalSelected >= targetSatoshis) selected else emptyList()
    }

    /**
     * Simplified Branch and Bound implementation for coin selection.
     * Searches for a combination of UTXOs that avoids change.
     */
    private fun branchAndBound(utxos: List<UTXO>, target: Long): List<UTXO> {
        // Sort descending to optimize search
        val sorted = utxos.sortedByDescending { it.value.value }
        var bestSelection = emptyList<UTXO>()
        val currentSelection = mutableListOf<UTXO>()

        // Limit iterations to prevent UI hang or excessive CPU usage
        var iterations = 0
        val maxIterations = 50_000

        fun search(index: Int, currentSum: Long) {
            if (iterations++ > maxIterations || bestSelection.isNotEmpty()) return

            if (currentSum >= target) {
                // If we found a match that doesn't require change (sum is target or slightly over but below dust)
                // we treat it as an exact match to save on change output size.
                if (currentSum == target || (currentSum - target) < BitcoinConstants.DUST_LIMIT) {
                    bestSelection = ArrayList(currentSelection)
                }
                return
            }

            if (index >= sorted.size) return

            // Including this UTXO
            currentSelection.add(sorted[index])
            search(index + 1, currentSum + sorted[index].value.value)

            // Excluding this UTXO
            currentSelection.removeAt(currentSelection.size - 1)
            search(index + 1, currentSum)
        }

        search(0, 0L)
        return bestSelection
    }

    /**
     * Parse a transaction to extract relevant details for our address
     */
    private fun parseTransaction(
        tx: EsploraTransactionDto,
        address: String
    ): ParsedTransaction? {
        val hasOutputToUs = tx.vout.any { it.scriptpubkeyAddress == address }
        val hasInputFromUs = tx.vin.any { vin ->
            vin.prevout?.scriptpubkeyAddress == address
        }

        return when {
            hasOutputToUs && !hasInputFromUs -> {
                val ourOutput = tx.vout.first { it.scriptpubkeyAddress == address }
                val sender = tx.vin.firstOrNull()?.prevout?.scriptpubkeyAddress ?: "unknown"
                ParsedTransaction(
                    fromAddress = sender,
                    toAddress = address,
                    amount = ourOutput.value,
                    isIncoming = true
                )
            }

            hasInputFromUs -> {
                val recipientOutput = tx.vout.firstOrNull {
                    it.scriptpubkeyAddress != null && it.scriptpubkeyAddress != address
                }
                val recipient = recipientOutput?.scriptpubkeyAddress ?: "unknown"
                val amount = recipientOutput?.value ?: 0
                ParsedTransaction(
                    fromAddress = address,
                    toAddress = recipient,
                    amount = amount,
                    isIncoming = false
                )
            }

            else -> null
        }
    }

    companion object {
        // Fee estimate targets (in blocks)
        // 144 blocks = ~24 hours, 6 blocks = ~1 hour, 2 blocks = ~20 minutes
        private const val SLOW_TARGET = "144"
        private const val NORMAL_TARGET = "6"
        private const val FAST_TARGET = "2"

        // Default fee rates (sat/vB) as fallbacks
        private const val DEFAULT_SLOW_FEE = 1.0
        private const val DEFAULT_NORMAL_FEE = 10.0
        private const val DEFAULT_FAST_FEE = 20.0

        // Block time constant (in minutes)
        private const val BLOCK_TIME_MINUTES = 10

        // Validation regexes
        private val HEX_REGEX = Regex("^[0-9a-fA-F]+$")
        private val TXID_REGEX = Regex("^[0-9a-fA-F]{64}$")
    }
}
