package com.example.nexuswallet.feature.bitcoin.data.repository

import com.example.nexuswallet.feature.bitcoin.data.model.ParsedTransaction
import com.example.nexuswallet.feature.bitcoin.data.model.UTXO
import com.example.nexuswallet.feature.bitcoin.data.remote.api.BitcoinApi
import com.example.nexuswallet.feature.bitcoin.data.remote.model.EsploraTransactionDto
import com.example.nexuswallet.feature.bitcoin.data.toDomain
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants
import com.example.nexuswallet.feature.bitcoin.util.BitcoinConstants.DUST_LIMIT
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
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.core.TransactionWitness
import org.bitcoinj.crypto.TransactionSignature
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptPattern
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
            // Header (10.5) + Inputs (27 * count) + Outputs (31 * count) + Witness (107 / 4 * count)
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
     * Creates and signs a Bitcoin transaction using modern SegWit standards and BIP-69.
     */
    override suspend fun createAndSignTransaction(
        fromKey: ECKey,
        toAddress: String,
        satoshis: Long,
        feeLevel: FeeLevel,
        network: BitcoinNetwork,
        utxos: List<UTXO>?
    ): Result<Transaction> = withContext(ioDispatcher) {
        try {
            val networkParams = when (network) {
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
            }

            // 1. Determine address types (favor SegWit)
            val segwitAddress = SegwitAddress.fromKey(networkParams, fromKey)
            val legacyAddress = LegacyAddress.fromKey(networkParams, fromKey)

            // 2. Get UTXOs if not provided
            val selected: List<UTXO> = if (utxos != null) {
                utxos
            } else {
                // Fetch UTXOs for both to be safe, but we favor spending SegWit
                val fromAddresses = listOf(segwitAddress.toString(), legacyAddress.toString())
                val allUtxos = mutableListOf<UTXO>()
                for (addr in fromAddresses) {
                    when (val result = getUnspentOutputs(addr, network)) {
                        is Result.Success -> allUtxos.addAll(result.data)
                        else -> {}
                    }
                }

                if (allUtxos.isEmpty()) {
                    return@withContext Result.Error("No UTXOs found")
                }

                // Get current fee rates for selection
                val api = getApiForNetwork(network)
                val estimates = api.getFeeEstimates()
                val feePerByte = estimates[when (feeLevel) {
                    FeeLevel.SLOW -> SLOW_TARGET
                    FeeLevel.NORMAL -> NORMAL_TARGET
                    FeeLevel.FAST -> FAST_TARGET
                }] ?: DEFAULT_NORMAL_FEE

                // Selection (Iterative)
                val sel = mutableListOf<UTXO>()
                var totalSelectedSatoshis = 0L
                val sortedUtxos = allUtxos.sortedByDescending { it.value.value }
                val outputCount = 2

                for (utxo in sortedUtxos) {
                    sel.add(utxo)
                    totalSelectedSatoshis += utxo.value.value
                    val isSegwitTx = sel.any { ScriptPattern.isP2WPKH(it.script) }
                    val vSize = calculateTransactionSize(sel.size, outputCount, isSegwitTx)
                    val currentFee = (vSize * feePerByte).toLong()
                    if (totalSelectedSatoshis >= (satoshis + currentFee)) break
                }
                if (totalSelectedSatoshis < (satoshis + 0)) { // Simple check first
                    return@withContext Result.Error("Insufficient funds")
                }
                sel
            }

            val totalSelectedSatoshis = selected.sumOf { it.value.value }

            // 3. Get current fee rates for final calculation
            val api = getApiForNetwork(network)
            val estimates = api.getFeeEstimates()
            val feePerByte = estimates[when (feeLevel) {
                FeeLevel.SLOW -> SLOW_TARGET
                FeeLevel.NORMAL -> NORMAL_TARGET
                FeeLevel.FAST -> FAST_TARGET
            }] ?: DEFAULT_NORMAL_FEE

            val isSegwitTx = selected.any { ScriptPattern.isP2WPKH(it.script) }
            val vSize = calculateTransactionSize(selected.size, 2, isSegwitTx)
            val currentFee = (vSize * feePerByte).toLong()

            if (totalSelectedSatoshis < (satoshis + currentFee)) {
                return@withContext Result.Error("Insufficient funds for amount and fees")
            }

            // 4. Construct Transaction with BIP-69 Sorting
            val tx = Transaction(networkParams)

            // Add Outputs
            val outputs = mutableListOf<TransactionOutputData>()
            outputs.add(
                TransactionOutputData(
                    Coin.valueOf(satoshis),
                    Address.fromString(networkParams, toAddress)
                )
            )

            val changeValue = totalSelectedSatoshis - satoshis - currentFee
            if (changeValue >= DUST_LIMIT) {
                // Change goes back to SegWit address for future fee savings
                outputs.add(TransactionOutputData(Coin.valueOf(changeValue), segwitAddress))
            }

            // Sort Outputs (BIP-69)
            outputs.sortWith(OutputComparator())
            for (out in outputs) {
                tx.addOutput(out.value, out.address)
            }

            // Add Inputs
            val inputs = selected.map { utxo ->
                TransactionInput(networkParams, tx, utxo.script.program, utxo.outPoint, utxo.value)
            }.toMutableList()

            // Sort Inputs (BIP-69)
            inputs.sortWith(InputComparator())
            for (input in inputs) {
                tx.addInput(input)
            }

            // 5. Signing
            for (i in 0 until tx.inputs.size) {
                val input = tx.getInput(i.toLong())
                val utxo = selected.find { it.outPoint == input.outpoint }!!

                if (ScriptPattern.isP2WPKH(utxo.script)) {
                    // Native SegWit Signing
                    val hash = tx.hashForWitnessSignature(
                        i,
                        utxo.script,
                        utxo.value,
                        Transaction.SigHash.ALL,
                        false
                    )
                    val sig = fromKey.sign(hash)
                    val txSig = TransactionSignature(sig, Transaction.SigHash.ALL, false)

                    input.setWitness(TransactionWitness.redeemP2WPKH(txSig, fromKey))
                    input.scriptSig = ScriptBuilder.createEmpty()
                } else {
                    // Legacy Signing
                    val hash = tx.hashForSignature(i, utxo.script, Transaction.SigHash.ALL, false)
                    val sig = fromKey.sign(hash)
                    val txSig = TransactionSignature(sig, Transaction.SigHash.ALL, false)
                    input.scriptSig = ScriptBuilder.createInputScript(txSig, fromKey)
                }
            }

            tx.verify()
            Result.Success(tx)
        } catch (e: Exception) {
            Result.Error("Failed to sign transaction: ${e.message}")
        }
    }

    private data class TransactionOutputData(val value: Coin, val address: Address)

    private class OutputComparator : Comparator<TransactionOutputData> {
        override fun compare(o1: TransactionOutputData, o2: TransactionOutputData): Int {
            val v1 = o1.value.value
            val v2 = o2.value.value
            if (v1 != v2) return v1.compareTo(v2)
            return o1.address.toString().compareTo(o2.address.toString())
        }
    }

    private class InputComparator : Comparator<TransactionInput> {
        override fun compare(o1: TransactionInput, o2: TransactionInput): Int {
            val hash1 = o1.outpoint.hash.toString()
            val hash2 = o2.outpoint.hash.toString()
            if (hash1 != hash2) return hash1.compareTo(hash2)
            return o1.outpoint.index.compareTo(o2.outpoint.index)
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
                if (currentSum == target || (currentSum - target) < DUST_LIMIT) {
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
