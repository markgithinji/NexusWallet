package com.example.nexuswallet.feature.coin.bitcoin

import android.util.Log
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
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

    companion object {
        private const val SATOSHIS_PER_BTC = 100_000_000L
    }

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
                Log.d("BitcoinRepo", "getBalance via API for: $address")

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
                Log.e("BitcoinRepo", "Error getting balance: ${e.message}", e)
                Result.Error("Failed to get balance: ${e.message}", e)
            }
        }
    }

    /**
     * Get fee estimate based on priority
     */
    suspend fun getFeeEstimate(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<FeeEstimate> {
        return withContext(Dispatchers.IO) {
            try {
                val api =
                    getApiForNetwork(BitcoinNetwork.MAINNET) // Fee estimates are same for all networks
                val estimates = api.getFeeEstimates()

                val feePerByte = when (feeLevel) {
                    FeeLevel.SLOW -> estimates["144"] ?: 1.0
                    FeeLevel.NORMAL -> estimates["6"] ?: 10.0
                    FeeLevel.FAST -> estimates["2"] ?: 20.0
                }

                val estimatedSize = 250L
                val totalFeeSatoshis = (estimatedSize * feePerByte).toLong()

                val totalFeeDecimal = BigDecimal(totalFeeSatoshis).divide(
                    BigDecimal(SATOSHIS_PER_BTC),
                    8,
                    RoundingMode.HALF_UP
                ).toPlainString()

                Result.Success(
                    FeeEstimate(
                        feePerByte = feePerByte.toString(),
                        gasPrice = null,
                        totalFee = totalFeeSatoshis.toString(),
                        totalFeeDecimal = totalFeeDecimal,
                        estimatedTime = when (feeLevel) {
                            FeeLevel.SLOW -> 144
                            FeeLevel.NORMAL -> 6
                            FeeLevel.FAST -> 2
                        },
                        priority = feeLevel,
                        metadata = mapOf("estimatedSize" to estimatedSize.toString())
                    )
                )

            } catch (e: Exception) {
                Log.e("BitcoinRepo", "Error getting fee estimate: ${e.message}")
                Result.Success(getDefaultFeeEstimate(feeLevel))
            }
        }
    }

    private fun getDefaultFeeEstimate(feeLevel: FeeLevel): FeeEstimate {
        val feePerByte = when (feeLevel) {
            FeeLevel.SLOW -> 1.0
            FeeLevel.NORMAL -> 10.0
            FeeLevel.FAST -> 20.0
        }
        val estimatedSize = 250L
        val totalFeeSatoshis = (estimatedSize * feePerByte).toLong()

        return FeeEstimate(
            feePerByte = feePerByte.toString(),
            gasPrice = null,
            totalFee = totalFeeSatoshis.toString(),
            totalFeeDecimal = BigDecimal(totalFeeSatoshis).divide(
                BigDecimal(SATOSHIS_PER_BTC),
                8,
                RoundingMode.HALF_UP
            ).toPlainString(),
            estimatedTime = when (feeLevel) {
                FeeLevel.SLOW -> 144
                FeeLevel.NORMAL -> 6
                FeeLevel.FAST -> 2
            },
            priority = feeLevel,
            metadata = mapOf("estimatedSize" to estimatedSize.toString())
        )
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
                Log.d("BitcoinRepo", "Broadcasting transaction via Retrofit API...")
                Log.d("BitcoinRepo", "Network: $network")
                Log.d("BitcoinRepo", "Transaction hex length: ${signedHex.length}")
                Log.d("BitcoinRepo", "Hex preview: ${signedHex.take(100)}...")

                // Validate hex format
                if (!signedHex.matches(Regex("^[0-9a-fA-F]+$"))) {
                    Log.e("BitcoinRepo", "Invalid hex format - contains non-hex characters")
                    return@withContext Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Invalid transaction hex format"
                        )
                    )
                }

                val api = getApiForNetwork(network)

                // Make the API call
                val response = try {
                    api.broadcastTransaction(signedHex)
                } catch (e: HttpException) {
                    // Handle HTTP errors
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e("BitcoinRepo", "Broadcast HTTP error: ${e.code()}")
                    Log.e("BitcoinRepo", "Error body: $errorBody")

                    return@withContext Result.Success(
                        BroadcastResult(
                            success = false,
                            error = errorBody ?: "HTTP ${e.code()}"
                        )
                    )
                } catch (e: Exception) {
                    // Handle other errors
                    Log.e("BitcoinRepo", "Broadcast error: ${e.message}", e)
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
                    Log.e("BitcoinRepo", "Error reading response body: ${e.message}")
                    return@withContext Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Failed to read response"
                        )
                    )
                }

                // Blockstream returns the transaction ID as plain text on success
                if (txId.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                    Log.d("BitcoinRepo", "Transaction broadcast successful: $txId")
                    Result.Success(
                        BroadcastResult(
                            success = true,
                            hash = txId
                        )
                    )
                } else {
                    // If response doesn't look like a txid, it's probably an error
                    Log.e("BitcoinRepo", "Unexpected response: $txId")
                    Result.Success(
                        BroadcastResult(
                            success = false,
                            error = txId
                        )
                    )
                }

            } catch (e: Exception) {
                Log.e("BitcoinRepo", "Error broadcasting: ${e.message}", e)
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
                Log.d("BitcoinRepo", "Getting UTXOs via API for: $address")

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
                        Log.e("BitcoinRepo", "Error processing UTXO: ${e.message}")
                    }
                }

                return@withContext result

            } catch (e: Exception) {
                Log.e("BitcoinRepo", "Error getting UTXOs: ${e.message}")
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
                Log.e("BitcoinRepo", "Error fetching script from TX $txid: ${e.message}")
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
                Log.d("BitcoinRepo", "Creating transaction: $satoshis satoshis to $toAddress")

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

                Log.d("BitcoinRepo", "Found ${utxos.size} UTXOs")
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
                    Log.d(
                        "BitcoinRepo",
                        "Added input: ${utxo.value.value} satoshis, total: ${totalInputValue.value}"
                    )

                    if (totalInputValue.isGreaterThan(Coin.valueOf(satoshis + 1000))) {
                        Log.d("BitcoinRepo", "Collected enough inputs")
                        break
                    }
                }

                val fee = Coin.valueOf(1000) // TODO: Consider using a more dynamic fee
                Log.d("BitcoinRepo", "Fee: ${fee.value} satoshis")

                val changeValue = totalInputValue.subtract(Coin.valueOf(satoshis)).subtract(fee)
                Log.d("BitcoinRepo", "Change: ${changeValue.value} satoshis")

                if (changeValue.isPositive) {
                    tx.addOutput(changeValue, LegacyAddress.fromKey(networkParams, fromKey))
                    Log.d("BitcoinRepo", "Added change output")
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
                    Log.d("BitcoinRepo", "Signed input $i")
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
                    Log.d("BitcoinRepo", "Transaction verification passed")
                } catch (e: Exception) {
                    Log.e("BitcoinRepo", "Transaction verification failed: ${e.message}")
                    return@withContext Result.Error(
                        "Transaction verification failed: ${e.message}",
                        e
                    )
                }

                Result.Success(tx)

            } catch (e: Exception) {
                Log.e("BitcoinRepo", "Error creating transaction: ${e.message}", e)
                Result.Error("Failed to create transaction: ${e.message}", e)
            }
        }
    }
}