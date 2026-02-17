package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
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
     * Get Bitcoin fee estimate based on priority
     */
    suspend fun getFeeEstimate(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<BitcoinFeeEstimate> {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApiForNetwork(BitcoinNetwork.MAINNET)
                val estimates = api.getFeeEstimates()

                // Block targets from Blockstream API:
                // "2" -> next block (fast)
                // "6" -> within 6 blocks (normal)
                // "144" -> within 144 blocks (slow)
                val feePerByte = when (feeLevel) {
                    FeeLevel.SLOW -> estimates["144"] ?: 1.0
                    FeeLevel.NORMAL -> estimates["6"] ?: 10.0
                    FeeLevel.FAST -> estimates["2"] ?: 20.0
                }

                val blockTarget = when (feeLevel) {
                    FeeLevel.SLOW -> 144
                    FeeLevel.NORMAL -> 6
                    FeeLevel.FAST -> 2
                }

                val estimatedSize = 250L // Average transaction size
                val totalFeeSatoshis = (estimatedSize * feePerByte).toLong()

                val totalFeeBtc = BigDecimal(totalFeeSatoshis).divide(
                    BigDecimal(SATOSHIS_PER_BTC),
                    8,
                    RoundingMode.HALF_UP
                ).toPlainString()

                Result.Success(
                    BitcoinFeeEstimate(
                        feePerByte = feePerByte,
                        totalFeeSatoshis = totalFeeSatoshis,
                        totalFeeBtc = totalFeeBtc,
                        estimatedTime = when (feeLevel) {
                            FeeLevel.SLOW -> 144 * 10 * 60 // 144 blocks * ~10 minutes
                            FeeLevel.NORMAL -> 6 * 10 * 60 // 6 blocks * ~10 minutes
                            FeeLevel.FAST -> 2 * 10 * 60 // 2 blocks * ~10 minutes
                        },
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
     * Broadcast transaction using Blockstream API
     */
    suspend fun broadcastTransaction(
        signedHex: String,
        network: BitcoinNetwork = BitcoinNetwork.MAINNET
    ): Result<BroadcastResult> {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApiForNetwork(network)

                // Validate hex format
                if (!signedHex.matches(Regex("^[0-9a-fA-F]+$"))) {
                    return@withContext Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Invalid transaction hex format"
                        )
                    )
                }

                // Make the API call
                val response = try {
                    api.broadcastTransaction(signedHex)
                } catch (e: HttpException) {
                    // Handle HTTP errors
                    val errorBody = e.response()?.errorBody()?.string()
                    return@withContext Result.Success(
                        BroadcastResult(
                            success = false,
                            error = errorBody ?: "HTTP ${e.code()}"
                        )
                    )
                } catch (e: Exception) {
                    // Handle other errors
                    return@withContext Result.Success(
                        BroadcastResult(
                            success = false,
                            error = e.message ?: "Broadcast failed"
                        )
                    )
                }

                // Get the response body as string
                val txId = try {
                    response.string().trim()
                } catch (e: Exception) {
                    return@withContext Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Failed to read response"
                        )
                    )
                }

                // Blockstream returns the transaction ID as plain text on success
                if (txId.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                    Result.Success(
                        BroadcastResult(
                            success = true,
                            hash = txId
                        )
                    )
                } else {
                    // If response doesn't look like a txid, it's probably an error
                    Result.Success(
                        BroadcastResult(
                            success = false,
                            error = txId
                        )
                    )
                }

            } catch (e: Exception) {
                Result.Success(
                    BroadcastResult(
                        success = false,
                        error = e.message ?: "Broadcast failed"
                    )
                )
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
                    tx.vout[vout].scriptPubKey
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Create and sign a Bitcoin transaction using bitcoinj
     */
    suspend fun createAndSignTransaction(
        fromKey: ECKey,
        toAddress: String,
        satoshis: Long,
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
                val utxos = getUnspentOutputs(fromAddress, network)

                if (utxos.isEmpty()) {
                    return@withContext Result.Error(
                        "No UTXOs found for address: $fromAddress",
                        IllegalStateException()
                    )
                }

                var totalInputValue = Coin.ZERO
                for (utxo in utxos) {
                    val input = TransactionInput(
                        networkParams,
                        tx,
                        utxo.script.program,
                        utxo.outPoint
                    )
                    tx.addInput(input)
                    totalInputValue = totalInputValue.add(utxo.value)

                    if (totalInputValue.isGreaterThan(Coin.valueOf(satoshis + 1000))) {
                        break
                    }
                }

                val fee = Coin.valueOf(1000) // TODO: Consider using a more dynamic fee

                val changeValue = totalInputValue.subtract(Coin.valueOf(satoshis)).subtract(fee)

                if (changeValue.isPositive) {
                    tx.addOutput(changeValue, LegacyAddress.fromKey(networkParams, fromKey))
                }

                for (i in 0 until tx.inputs.size) {
                    val input = tx.getInput(i.toLong())
                    val utxo = utxos[i]

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
                    return@withContext Result.Error(
                        "Transaction has no inputs",
                        IllegalStateException()
                    )
                }

                // Verify the transaction
                try {
                    tx.verify()
                } catch (e: Exception) {
                    return@withContext Result.Error(
                        "Transaction verification failed: ${e.message}",
                        e
                    )
                }

                Result.Success(tx)

            } catch (e: Exception) {
                Result.Error("Failed to create transaction: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val SATOSHIS_PER_BTC = 100_000_000L
    }
}