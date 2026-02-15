package com.example.nexuswallet.feature.coin.bitcoin

import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.Script
import org.bitcoinj.script.ScriptBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.Boolean
import kotlin.Exception
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.text.take
import kotlin.to
import com.example.nexuswallet.feature.coin.Result

@Singleton
class BitcoinBlockchainRepository @Inject constructor() {

    companion object {
        private const val SATOSHIS_PER_BTC = 100_000_000L
        private const val BLOCKSTREAM_API = "https://blockstream.info/api"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Get Bitcoin balance for an address
     */
    suspend fun getBalance(address: String, network: BitcoinNetwork = BitcoinNetwork.MAINNET): Result<BigDecimal> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("BitcoinRepo", " getBalance START for address: $address")

                val baseUrl = when (network) {
                    BitcoinNetwork.MAINNET -> "https://blockstream.info/api"
                    BitcoinNetwork.TESTNET -> "https://blockstream.info/testnet/api"
                    BitcoinNetwork.REGTEST -> "http://localhost:18443"
                    else -> "https://blockstream.info/api"
                }

                val url = "$baseUrl/address/$address"
                val request = Request.Builder().url(url).build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext Result.Error("Empty response")

                val json = JSONObject(responseBody)
                val chainStats = json.getJSONObject("chain_stats")
                val mempoolStats = json.getJSONObject("mempool_stats")

                val confirmed = chainStats.getLong("funded_txo_sum") - chainStats.getLong("spent_txo_sum")
                val unconfirmed = mempoolStats.getLong("funded_txo_sum") - mempoolStats.getLong("spent_txo_sum")
                val totalSatoshis = confirmed + unconfirmed

                val btcBalance = BigDecimal(totalSatoshis).divide(
                    BigDecimal(SATOSHIS_PER_BTC),
                    8,
                    RoundingMode.HALF_UP
                )

                Log.d("BitcoinRepo", " Balance for $address: $btcBalance BTC")
                Result.Success(btcBalance)

            } catch (e: Exception) {
                Log.e("BitcoinRepo", " Error getting balance: ${e.message}", e)
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
                val url = "https://blockstream.info/api/fee-estimates"
                val request = Request.Builder().url(url).build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext Result.Success(getDefaultFeeEstimate(feeLevel))

                val json = JSONObject(responseBody)

                val feePerByte = when (feeLevel) {
                    FeeLevel.SLOW -> json.getDouble("144")
                    FeeLevel.NORMAL -> json.getDouble("6")
                    FeeLevel.FAST -> json.getDouble("2")
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
                    BitcoinNetwork.REGTEST -> RegTestParams.get()
                }

                val tx = Transaction(networkParams)
                val outputValue = Coin.valueOf(satoshis)
                val outputAddress = Address.fromString(networkParams, toAddress)
                tx.addOutput(outputValue, outputAddress)

                val fromAddress = LegacyAddress.fromKey(networkParams, fromKey).toString()
                val utxos = getUnspentOutputs(fromAddress, network)

                if (utxos.isEmpty()) {
                    return@withContext Result.Error("No UTXOs found for address: $fromAddress", IllegalStateException())
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

                val fee = Coin.valueOf(1000)
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

                Log.d("BitcoinRepo", "Transaction signed successfully")
                Result.Success(tx)

            } catch (e: Exception) {
                Log.e("BitcoinRepo", "Error creating transaction: ${e.message}", e)
                Result.Error("Failed to create transaction: ${e.message}", e)
            }
        }
    }

    /**
     * Get UTXOs for an address
     */
    private suspend fun getUnspentOutputs(address: String, network: BitcoinNetwork): List<UTXO> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("BitcoinRepo", " Getting UTXOs for: $address on $network")

                val baseUrl = when (network) {
                    BitcoinNetwork.MAINNET -> "https://blockstream.info/api"
                    BitcoinNetwork.TESTNET -> "https://blockstream.info/testnet/api"
                    BitcoinNetwork.REGTEST -> "http://localhost:18443"
                    else -> "https://blockstream.info/testnet/api"
                }

                val url = "$baseUrl/address/$address/utxo"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                    return@withContext emptyList()
                }

                if (responseBody.trim() == "[]") {
                    return@withContext emptyList()
                }

                val jsonArray = JSONArray(responseBody)

                val networkParams = when (network) {
                    BitcoinNetwork.MAINNET -> MainNetParams.get()
                    BitcoinNetwork.TESTNET -> TestNet3Params.get()
                    BitcoinNetwork.REGTEST -> RegTestParams.get()
                }

                val utxos = mutableListOf<UTXO>()

                for (i in 0 until jsonArray.length()) {
                    try {
                        val utxoJson = jsonArray.getJSONObject(i)
                        val txid = utxoJson.getString("txid")
                        val vout = utxoJson.getInt("vout")
                        val value = utxoJson.getLong("value")

                        val scriptHex = getScriptPubKeyFromTransaction(txid, vout, baseUrl)

                        if (scriptHex == null) {
                            continue
                        }

                        val utxo = UTXO(
                            outPoint = TransactionOutPoint(
                                networkParams,
                                vout.toLong(),
                                Sha256Hash.wrap(txid)
                            ),
                            value = Coin.valueOf(value),
                            script = Script(Utils.HEX.decode(scriptHex))
                        )

                        utxos.add(utxo)

                    } catch (e: Exception) {
                        Log.e("BitcoinRepo", " Error processing UTXO $i: ${e.message}")
                    }
                }

                return@withContext utxos

            } catch (e: Exception) {
                Log.e("BitcoinRepo", " Error getting UTXOs: ${e.message}", e)
                emptyList()
            }
        }
    }

    private suspend fun getScriptPubKeyFromTransaction(txid: String, vout: Int, baseUrl: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                delay(100L)

                val url = "$baseUrl/tx/$txid"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                    return@withContext null
                }

                val txJson = JSONObject(responseBody)
                val voutArray = txJson.getJSONArray("vout")

                if (vout >= voutArray.length()) {
                    return@withContext null
                }

                val output = voutArray.getJSONObject(vout)

                return@withContext when {
                    output.has("scriptpubkey") && output.get("scriptpubkey") is JSONObject -> {
                        val scriptObj = output.getJSONObject("scriptpubkey")
                        scriptObj.getString("hex")
                    }
                    output.has("scriptpubkey") && output.get("scriptpubkey") is String -> {
                        output.getString("scriptpubkey")
                    }
                    output.has("scriptPubKey") -> {
                        val scriptValue = output.get("scriptPubKey")
                        if (scriptValue is String) scriptValue else null
                    }
                    output.has("script") -> {
                        val scriptValue = output.get("script")
                        if (scriptValue is String) scriptValue else null
                    }
                    else -> {
                        output.optString("scriptpubkey").takeIf { it.isNotEmpty() && it.matches(Regex("^[0-9a-fA-F]+$")) }
                    }
                }

            } catch (e: Exception) {
                Log.e("BitcoinRepo", " Error fetching script from TX $txid: ${e.message}")
                null
            }
        }
    }

    /**
     * Broadcast transaction to Bitcoin network
     */
    suspend fun broadcastTransaction(
        signedHex: String,
        network: BitcoinNetwork = BitcoinNetwork.MAINNET
    ): Result<BroadcastResult> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("BitcoinRepo", "Broadcasting Bitcoin transaction...")

                val baseUrl = when (network) {
                    BitcoinNetwork.MAINNET -> "https://blockstream.info/api"
                    BitcoinNetwork.TESTNET -> "https://blockstream.info/testnet/api"
                    BitcoinNetwork.REGTEST -> "http://localhost:18443"
                    else -> "https://blockstream.info/api"
                }

                val url = "$baseUrl/tx"
                val body = signedHex.toRequestBody("text/plain".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val txId = response.body?.string()

                if (response.isSuccessful && txId != null) {
                    Log.d("BitcoinRepo", "Transaction broadcast successful: $txId")
                    Result.Success(
                        BroadcastResult(
                            success = true,
                            hash = txId
                        )
                    )
                } else {
                    val error = response.body?.string() ?: "Broadcast failed with code: ${response.code}"
                    Log.e("BitcoinRepo", "Broadcast failed: $error")
                    Result.Success(
                        BroadcastResult(
                            success = false,
                            error = error
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

    suspend fun debugCheckUTXOsDirect(address: String, network: BitcoinNetwork = BitcoinNetwork.TESTNET) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("BitcoinRepo", "=== DEBUG CHECK UTXOs DIRECT ===")
                Log.d("BitcoinRepo", "Address: $address")
                Log.d("BitcoinRepo", "Network: ${network.name}")

                val baseUrl = when (network) {
                    BitcoinNetwork.MAINNET -> "https://blockstream.info/api"
                    BitcoinNetwork.TESTNET -> "https://blockstream.info/testnet/api"
                    BitcoinNetwork.REGTEST -> "http://localhost:18443"
                    else -> "https://blockstream.info/testnet/api"
                }

                val utxoUrl = "$baseUrl/address/$address/utxo"
                val utxoRequest = Request.Builder().url(utxoUrl).build()
                val utxoResponse = client.newCall(utxoRequest).execute()
                val utxoBody = utxoResponse.body?.string() ?: ""

                Log.d("BitcoinRepo", "UTXO Response code: ${utxoResponse.code}")
                Log.d("BitcoinRepo", "UTXO Response: $utxoBody")

                val addressUrl = "$baseUrl/address/$address"
                val addressRequest = Request.Builder().url(addressUrl).build()
                val addressResponse = client.newCall(addressRequest).execute()
                val addressBody = addressResponse.body?.string() ?: ""

                Log.d("BitcoinRepo", "Address Response code: ${addressResponse.code}")
                Log.d("BitcoinRepo", "Address Response: $addressBody")

                if (utxoResponse.isSuccessful) {
                    try {
                        val jsonArray = JSONArray(utxoBody)
                        Log.d("BitcoinRepo", "✓ Found ${jsonArray.length()} UTXOs")

                        for (i in 0 until jsonArray.length()) {
                            val utxo = jsonArray.getJSONObject(i)
                            Log.d("BitcoinRepo", "UTXO $i:")
                            Log.d("BitcoinRepo", "  txid: ${utxo.getString("txid")}")
                            Log.d("BitcoinRepo", "  vout: ${utxo.getInt("vout")}")
                            Log.d("BitcoinRepo", "  value: ${utxo.getLong("value")} satoshis")
                            Log.d("BitcoinRepo", "  status: ${utxo.optJSONObject("status")?.getString("confirmed") ?: "unconfirmed"}")
                        }
                    } catch (e: Exception) {
                        Log.e("BitcoinRepo", "Error parsing UTXOs: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                Log.e("BitcoinRepo", "Debug check failed: ${e.message}", e)
            }
        }
    }

    /**
     * Validate Bitcoin address format
     */
    fun validateAddress(address: String, network: BitcoinNetwork): Boolean {
        return try {
            val networkParams = when (network) {
                BitcoinNetwork.MAINNET -> MainNetParams.get()
                BitcoinNetwork.TESTNET -> TestNet3Params.get()
                BitcoinNetwork.REGTEST -> RegTestParams.get()
            }
            Address.fromString(networkParams, address)
            true
        } catch (e: Exception) {
            false
        }
    }

    data class UTXO(
        val outPoint: TransactionOutPoint,
        val value: Coin,
        val script: Script
    )
}