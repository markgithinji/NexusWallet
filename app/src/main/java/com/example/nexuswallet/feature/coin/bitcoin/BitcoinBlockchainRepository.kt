package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.core.Utils
import org.bitcoinj.crypto.TransactionSignature
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.Script
import org.bitcoinj.script.ScriptBuilder
import retrofit2.HttpException
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class BitcoinBlockchainRepository @Inject constructor(
    @param:Named("bitcoinMainnet") private val mainnetApi: BitcoinApi,
    @param:Named("bitcoinTestnet") private val testnetApi: BitcoinApi
) {

    private fun getApiForNetwork(network: BitcoinNetwork): BitcoinApi {
        return when (network) {
            BitcoinNetwork.MAINNET -> mainnetApi
            BitcoinNetwork.TESTNET -> testnetApi
        }
    }

    /**
     * Get Bitcoin balance for an address
     */
    suspend fun getBalance(
        address: String,
        network: BitcoinNetwork = BitcoinNetwork.MAINNET
    ): Result<BigDecimal> {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApiForNetwork(network)
                val response = api.getAddressInfo(address)

                val confirmed = response.chainStats.fundedTxoSum - response.chainStats.spentTxoSum
                val unconfirmed =
                    response.mempoolStats.fundedTxoSum - response.mempoolStats.spentTxoSum
                val totalSatoshis = confirmed + unconfirmed

                val btcBalance = BigDecimal(totalSatoshis).divide(
                    BigDecimal(SATOSHIS_PER_BTC),
                    8,
                    RoundingMode.HALF_UP
                )

                Result.Success(btcBalance)

            } catch (e: Exception) {
                Result.Error("Failed to get balance: ${e.message}", e)
            }
        }
    }

    /**
     * Get Bitcoin fee estimate based on priority and transaction complexity
     */
    suspend fun getFeeEstimate(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        inputCount: Int = DEFAULT_INPUT_COUNT,
        outputCount: Int = DEFAULT_OUTPUT_COUNT
    ): Result<BitcoinFeeEstimate> {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApiForNetwork(BitcoinNetwork.MAINNET)
                val estimates = api.getFeeEstimates()

                // Get fee rate based on confirmation target
                val feePerByte = when (feeLevel) {
                    FeeLevel.SLOW -> estimates[SLOW_TARGET] ?: DEFAULT_SLOW_FEE
                    FeeLevel.NORMAL -> estimates[NORMAL_TARGET] ?: DEFAULT_NORMAL_FEE
                    FeeLevel.FAST -> estimates[FAST_TARGET] ?: DEFAULT_FAST_FEE
                }

                // Calculate actual transaction size based on inputs/outputs
                val estimatedSize = calculateTransactionSize(inputCount, outputCount)
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

                Result.Success(
                    BitcoinFeeEstimate(
                        feePerByte = feePerByte,
                        totalFeeSatoshis = totalFeeSatoshis,
                        totalFeeBtc = totalFeeBtc,
                        estimatedTime = blockTarget * BLOCK_TIME_MINUTES * 60,
                        priority = feeLevel,
                        estimatedSize = estimatedSize,
                        blockTarget = blockTarget
                    )
                )

            } catch (e: Exception) {
                Result.Error("Failed to get fee estimate: ${e.message}", e)
            }
        }
    }

    /**
     * Calculate transaction size based on number of inputs and outputs
     * Formula: base (10) + inputs * 148 + outputs * 34
     */
    private fun calculateTransactionSize(inputCount: Int, outputCount: Int): Long {
        return BASE_TX_SIZE + (inputCount * BYTES_PER_INPUT) + (outputCount * BYTES_PER_OUTPUT)
    }

    /**
     * Broadcast transaction using Blockstream API
     */
    suspend fun broadcastTransaction(
        signedHex: String,
        network: BitcoinNetwork = BitcoinNetwork.MAINNET
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApiForNetwork(network)

                // Validate hex format
                if (!signedHex.matches(HEX_REGEX)) {
                    return@withContext Result.Error("Invalid transaction hex format")
                }

                // Make the API call
                val response = try {
                    api.broadcastTransaction(signedHex)
                } catch (e: HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    return@withContext Result.Error(errorBody ?: "HTTP ${e.code()}")
                } catch (e: Exception) {
                    return@withContext Result.Error(e.message ?: "Broadcast failed")
                }

                // Get the response body as string
                val txId = try {
                    response.string().trim()
                } catch (e: Exception) {
                    return@withContext Result.Error("Failed to read response")
                }

                // Blockstream returns the transaction ID as plain text on success
                if (txId.matches(TXID_REGEX)) {
                    Result.Success(txId)
                } else {
                    Result.Error(txId)
                }

            } catch (e: Exception) {
                Result.Error(e.message ?: "Broadcast failed")
            }
        }
    }

    /**
     * Get UTXOs for an address
     */
    private suspend fun getUnspentOutputs(address: String, network: BitcoinNetwork): List<UTXO> {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApiForNetwork(network)
                val utxos = api.getUtxos(address)

                if (utxos.isEmpty()) {
                    return@withContext emptyList()
                }

                val networkParams = when (network) {
                    BitcoinNetwork.MAINNET -> MainNetParams.get()
                    BitcoinNetwork.TESTNET -> TestNet3Params.get()
                }

                val result = mutableListOf<UTXO>()

                for (utxo in utxos) {
                    try {
                        // Get scriptPubKey for this UTXO
                        val scriptHex =
                            getScriptPubKeyFromTransaction(utxo.txid, utxo.vout, network)

                        if (scriptHex != null) {
                            val bitcoinjUtxo = UTXO(
                                outPoint = TransactionOutPoint(
                                    networkParams,
                                    utxo.vout.toLong(),
                                    Sha256Hash.wrap(utxo.txid)
                                ),
                                value = Coin.valueOf(utxo.value),
                                script = Script(Utils.HEX.decode(scriptHex))
                            )
                            result.add(bitcoinjUtxo)
                        }
                    } catch (e: Exception) {
                        // Skip problematic UTXO
                    }
                }

                return@withContext result

            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun getScriptPubKeyFromTransaction(
        txid: String,
        vout: Int,
        network: BitcoinNetwork
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApiForNetwork(network)
                val tx = api.getTransaction(txid)

                return@withContext if (vout < tx.vout.size) {
                    tx.vout[vout].scriptpubkey
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Select UTXOs for a transaction using a simple "smallest first" strategy
     * to minimize change and dust
     */
    private fun selectUtxos(utxos: List<UTXO>, targetSatoshis: Long): List<UTXO> {
        if (utxos.isEmpty()) return emptyList()

        // Smallest-first to minimize change output (reduces future fees)
        val sortedUtxos = utxos.sortedBy { it.value.value }
        val selected = mutableListOf<UTXO>()
        var totalSelected = 0L

        for (utxo in sortedUtxos) {
            selected.add(utxo)
            totalSelected += utxo.value.value

            if (totalSelected >= targetSatoshis) {
                break
            }
        }

        // Fallback to largest-first if smallest doesn't meet target
        if (totalSelected < targetSatoshis) {
            val largestFirst = utxos.sortedByDescending { it.value.value }
            selected.clear()
            totalSelected = 0L

            for (utxo in largestFirst) {
                selected.add(utxo)
                totalSelected += utxo.value.value
                if (totalSelected >= targetSatoshis) break
            }
        }

        return selected
    }

    /**
     * Create and sign a Bitcoin transaction using bitcoinj
     */
    suspend fun createAndSignTransaction(
        fromKey: ECKey,
        toAddress: String,
        satoshis: Long,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: BitcoinNetwork
    ): Result<Transaction> {
        return withContext(Dispatchers.IO) {
            try {
                val networkParams = when (network) {
                    BitcoinNetwork.MAINNET -> MainNetParams.get()
                    BitcoinNetwork.TESTNET -> TestNet3Params.get()
                }

                val tx = Transaction(networkParams)
                val outputValue = Coin.valueOf(satoshis)
                val outputAddress = Address.fromString(networkParams, toAddress)
                tx.addOutput(outputValue, outputAddress)

                val fromAddress = LegacyAddress.fromKey(networkParams, fromKey).toString()
                val allUtxos = getUnspentOutputs(fromAddress, network)

                if (allUtxos.isEmpty()) {
                    return@withContext Result.Error(
                        "No UTXOs found for address: $fromAddress"
                    )
                }

                // Get fee estimate first to know how much we need
                val feeResult =
                    getFeeEstimate(feeLevel, allUtxos.size, 2) // 2 outputs (recipient + change)

                val feeSatoshis = when (feeResult) {
                    is Result.Success -> feeResult.data.totalFeeSatoshis
                    is Result.Error -> DEFAULT_FEE_SATOSHIS
                    is Result.Loading -> DEFAULT_FEE_SATOSHIS
                }

                // Select UTXOs to cover amount + fee
                val targetWithFee = satoshis + feeSatoshis
                val selectedUtxos = selectUtxos(allUtxos, targetWithFee)

                if (selectedUtxos.isEmpty()) {
                    return@withContext Result.Error("Insufficient funds")
                }

                // Add selected UTXOs as inputs
                var totalInputValue = Coin.ZERO
                for (utxo in selectedUtxos) {
                    val input = TransactionInput(
                        networkParams,
                        tx,
                        utxo.script.program,
                        utxo.outPoint
                    )
                    tx.addInput(input)
                    totalInputValue = totalInputValue.add(utxo.value)
                }

                // Calculate change
                val targetValue = Coin.valueOf(satoshis)
                val fee = Coin.valueOf(feeSatoshis)
                val changeValue = totalInputValue.subtract(targetValue).subtract(fee)

                if (changeValue.isPositive) {
                    // Only add change if it's not dust (less than 546 satoshis)
                    // Dust outputs are uneconomical to spend later
                    if (changeValue.value >= DUST_LIMIT) {
                        tx.addOutput(changeValue, LegacyAddress.fromKey(networkParams, fromKey))
                    } else {
                        // Dust change is added to miner fee automatically
                    }
                }

                // Sign each input
                for (i in 0 until tx.inputs.size) {
                    val input = tx.getInput(i.toLong())
                    val utxo = selectedUtxos[i]

                    val hash = tx.hashForSignature(
                        i,
                        utxo.script,
                        Transaction.SigHash.ALL,
                        false
                    )
                    val sig = fromKey.sign(hash)
                    val txSig = TransactionSignature(sig, Transaction.SigHash.ALL, false)

                    val script = ScriptBuilder.createInputScript(txSig, fromKey)
                    input.scriptSig = script
                }

                if (tx.inputs.isEmpty()) {
                    return@withContext Result.Error("Transaction has no inputs")
                }

                // Verify the transaction
                try {
                    tx.verify()
                } catch (e: Exception) {
                    return@withContext Result.Error("Transaction verification failed: ${e.message}")
                }

                Result.Success(tx)

            } catch (e: Exception) {
                Result.Error("Failed to create transaction: ${e.message}")
            }
        }
    }

    /**
     * Get transaction status
     */
    suspend fun getTransactionStatus(
        txid: String,
        network: BitcoinNetwork
    ): Result<TransactionStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApiForNetwork(network)
                val tx = api.getTransaction(txid)

                val status = if (tx.status.confirmed) {
                    TransactionStatus.SUCCESS
                } else {
                    TransactionStatus.PENDING
                }

                Result.Success(status)

            } catch (e: Exception) {
                Result.Error("Failed to get transaction status: ${e.message}")
            }
        }
    }

    /**
     * Get all transactions for an address (both sent and received)
     */
    suspend fun getAddressTransactions(
        address: String,
        network: BitcoinNetwork = BitcoinNetwork.MAINNET
    ): Result<List<BitcoinTransactionResponse>> = withContext(Dispatchers.IO) {
        try {
            val api = getApiForNetwork(network)
            val transactions = api.getAddressTransactions(address)

            val mappedTransactions = transactions.mapNotNull { tx ->
                parseTransaction(tx, address)?.let { parsed ->
                    BitcoinTransactionResponse(
                        txid = tx.txid,
                        fromAddress = parsed.fromAddress,
                        toAddress = parsed.toAddress,
                        amount = parsed.amount,
                        fee = tx.fee,
                        status = if (tx.status.confirmed) TransactionStatus.SUCCESS else TransactionStatus.PENDING,
                        timestamp = tx.status.block_time ?: (System.currentTimeMillis() / 1000),
                        confirmations = if (tx.status.confirmed) 1 else 0,
                        blockHash = tx.status.block_hash,
                        blockHeight = tx.status.block_height,
                        isIncoming = parsed.isIncoming
                    )
                }
            }

            Result.Success(mappedTransactions)

        } catch (e: Exception) {
            Result.Error("Failed to get transactions: ${e.message}", e)
        }
    }

    /**
     * Parse a transaction to extract relevant details for our address
     */
    private fun parseTransaction(
        tx: EsploraTransaction,
        address: String
    ): ParsedTransaction? {
        val hasOutputToUs = tx.vout.any { it.scriptpubkey_address == address }
        val hasInputFromUs = tx.vin.any { vin ->
            vin.prevout?.scriptpubkey_address == address
        }

        return when {
            // Incoming transaction
            hasOutputToUs && !hasInputFromUs -> {
                val ourOutput = tx.vout.first { it.scriptpubkey_address == address }
                val sender = tx.vin.firstOrNull()?.prevout?.scriptpubkey_address ?: "unknown"
                ParsedTransaction(
                    fromAddress = sender,
                    toAddress = address,
                    amount = ourOutput.value,
                    isIncoming = true
                )
            }

            // Outgoing transaction
            hasInputFromUs -> {
                val recipientOutput = tx.vout.firstOrNull {
                    it.scriptpubkey_address != null && it.scriptpubkey_address != address
                }
                val recipient = recipientOutput?.scriptpubkey_address ?: "unknown"
                val amount = recipientOutput?.value ?: 0
                ParsedTransaction(
                    fromAddress = address,
                    toAddress = recipient,
                    amount = amount,
                    isIncoming = false
                )
            }

            // Transaction doesn't directly involve us
            else -> null
        }
    }

    /**
     * Internal data class for parsed transaction details
     */
    private data class ParsedTransaction(
        val fromAddress: String,
        val toAddress: String,
        val amount: Long,
        val isIncoming: Boolean
    )

    companion object {
        // Bitcoin constants
        private const val SATOSHIS_PER_BTC = 100_000_000L
        private const val DUST_LIMIT = 546L
        private const val DEFAULT_FEE_SATOSHIS = 1000L

        // Transaction size constants (in bytes)
        private const val BASE_TX_SIZE = 10L
        private const val BYTES_PER_INPUT = 148L
        private const val BYTES_PER_OUTPUT = 34L
        private const val DEFAULT_INPUT_COUNT = 1
        private const val DEFAULT_OUTPUT_COUNT = 2

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

data class BitcoinTransactionResponse(
    val txid: String,
    val fromAddress: String,
    val toAddress: String,
    val amount: Long,
    val fee: Long,
    val status: TransactionStatus,
    val timestamp: Long,
    val confirmations: Int,
    val blockHash: String?,
    val blockHeight: Int?,
    val isIncoming: Boolean
)