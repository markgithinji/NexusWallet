package com.example.nexuswallet.feature.bitcoin.data.repository

import com.example.nexuswallet.feature.bitcoin.data.model.ParsedTransaction
import com.example.nexuswallet.feature.bitcoin.data.model.UTXO
import com.example.nexuswallet.feature.bitcoin.data.remote.api.BitcoinApi
import com.example.nexuswallet.feature.bitcoin.data.remote.model.EsploraTransactionDto
import com.example.nexuswallet.feature.bitcoin.data.toDomain
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.SafeApiCall
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
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

    /**
     * Get Bitcoin fee estimate based on priority and transaction complexity
     */
    override suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        inputCount: Int,
        outputCount: Int
    ): Result<BitcoinFeeEstimate> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val api = getApiForNetwork(BitcoinNetwork.Mainnet)
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
     * Calculate transaction size based on number of inputs and outputs
     */
    private fun calculateTransactionSize(inputCount: Int, outputCount: Int): Long {
        return BASE_TX_SIZE + (inputCount * BYTES_PER_INPUT) + (outputCount * BYTES_PER_OUTPUT)
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
     * Creates and signs a Bitcoin transaction in a single atomic operation.
     *
     * This method combines transaction creation and signing to minimize the time
     * the private key spends in memory. The key is only held during this function
     * call and is not persisted or exposed outside.
     *
     * @param fromKey The ECKey containing the private key for signing
     * @param toAddress The recipient's Bitcoin address
     * @param satoshis The amount to send in satoshis
     * @param feeLevel The fee priority level (SLOW, NORMAL, FAST)
     * @param network The Bitcoin network (Mainnet or Testnet)
     * @return Result containing the signed Transaction or error
     *
     * @implNote Security: This method is intentionally kept as a single operation
     * to avoid storing private key material in intermediate states. Do not refactor
     * into separate create/sign steps without implementing secure key handling.
     */

    override suspend fun createAndSignTransaction(
        fromKey: ECKey,
        toAddress: String,
        satoshis: Long,
        feeLevel: FeeLevel,
        network: BitcoinNetwork
    ): Result<Transaction> = withContext(ioDispatcher) {
        try {
            val networkParams = when (network) {
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
            }

            val tx = Transaction(networkParams)
            val outputValue = Coin.valueOf(satoshis)
            val outputAddress = Address.fromString(networkParams, toAddress)
            tx.addOutput(outputValue, outputAddress)

            val fromAddress = LegacyAddress.fromKey(networkParams, fromKey).toString()
            val allUtxosResult = getUnspentOutputs(fromAddress, network)
            val allUtxos = when (allUtxosResult) {
                is Result.Success -> allUtxosResult.data
                else -> return@withContext Result.Error("Failed to fetch UTXOs")
            }

            if (allUtxos.isEmpty()) {
                return@withContext Result.Error("No UTXOs found for address: $fromAddress")
            }

            val feeSatoshis = when (val feeResult = getFeeEstimate(feeLevel, allUtxos.size, 2)) {
                is Result.Success -> feeResult.data.totalFeeSatoshis
                else -> DEFAULT_FEE_SATOSHIS
            }

            val targetWithFee = satoshis + feeSatoshis
            val selectedUtxos = selectUtxos(allUtxos, targetWithFee)

            if (selectedUtxos.isEmpty()) {
                return@withContext Result.Error("Insufficient funds")
            }

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

            val targetValue = Coin.valueOf(satoshis)
            val fee = Coin.valueOf(feeSatoshis)
            val changeValue = totalInputValue.subtract(targetValue).subtract(fee)

            if (changeValue.isPositive && changeValue.value >= DUST_LIMIT) {
                tx.addOutput(changeValue, LegacyAddress.fromKey(networkParams, fromKey))
            }

            for (i in 0 until tx.inputs.size) {
                val input = tx.getInput(i.toLong())
                val utxo = selectedUtxos[i]

                val hash = tx.hashForSignature(i, utxo.script, Transaction.SigHash.ALL, false)
                val sig = fromKey.sign(hash)
                val txSig = TransactionSignature(sig, Transaction.SigHash.ALL, false)

                val script = ScriptBuilder.createInputScript(txSig, fromKey)
                input.scriptSig = script
            }

            if (tx.inputs.isEmpty()) {
                return@withContext Result.Error("Transaction has no inputs")
            }

            tx.verify()
            Result.Success(tx)
        } catch (e: Exception) {
            Result.Error("Failed to create transaction: ${e.message}")
        }
    }

    /**
     * Get UTXOs for an address
     */
    override suspend fun getUnspentOutputs(
        address: String,
        network: BitcoinNetwork
    ): Result<List<UTXO>> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val api = getApiForNetwork(network)
            val utxos = api.getUtxos(address)

            if (utxos.isEmpty()) {
                return@make emptyList<UTXO>()
            }

            val networkParams = when (network) {
                BitcoinNetwork.Mainnet -> MainNetParams.get()
                BitcoinNetwork.Testnet -> TestNet3Params.get()
            }

            val result = mutableListOf<UTXO>()

            for (utxo in utxos) {
                val scriptHex = getScriptPubKeyFromTransaction(utxo.txid, utxo.vout, network)
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
            }

            result
        }
    }

    private suspend fun getScriptPubKeyFromTransaction(
        txid: String,
        vout: Int,
        network: BitcoinNetwork
    ): String? = withContext(ioDispatcher) {
        val api = getApiForNetwork(network)
        val tx = api.getTransaction(txid)

        return@withContext if (vout < tx.vout.size) {
            tx.vout[vout].scriptpubkey
        } else {
            null
        }
    }

    /**
     * Select UTXOs for a transaction using a simple "smallest first" strategy
     */
    override fun selectUtxos(utxos: List<UTXO>, targetSatoshis: Long): List<UTXO> {
        if (utxos.isEmpty()) return emptyList()

        val sortedUtxos = utxos.sortedBy { it.value.value }
        val selected = mutableListOf<UTXO>()
        var totalSelected = 0L

        for (utxo in sortedUtxos) {
            selected.add(utxo)
            totalSelected += utxo.value.value
            if (totalSelected >= targetSatoshis) break
        }

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
        // Bitcoin constants
        private const val SATOSHIS_PER_BTC = 100_000_000L
        private const val DUST_LIMIT = 546L
        private const val DEFAULT_FEE_SATOSHIS = 1000L

        // Transaction size constants (in bytes)
        private const val BASE_TX_SIZE = 10L
        private const val BYTES_PER_INPUT = 148L
        private const val BYTES_PER_OUTPUT = 34L

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