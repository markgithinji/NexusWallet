package com.example.nexuswallet.feature.coin.bitcoin


import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.ChainType
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
    suspend fun getBalance(address: String, network: BitcoinNetwork = BitcoinNetwork.MAINNET): BigDecimal {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("BitcoinRepo", " getBalance START for address: $address")

                // Choose API based on network
                val baseUrl = when (network) {
                    BitcoinNetwork.MAINNET -> "https://blockstream.info/api"
                    BitcoinNetwork.TESTNET -> "https://blockstream.info/testnet/api"
                    BitcoinNetwork.REGTEST -> "http://localhost:18443"
                    else -> "https://blockstream.info/api"
                }

                val url = "$baseUrl/address/$address"
                val request = Request.Builder().url(url).build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext BigDecimal.ZERO

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
                btcBalance

            } catch (e: Exception) {
                Log.e("BitcoinRepo", " Error getting balance: ${e.message}", e)
                BigDecimal.ZERO
            }
        }
    }

    /**
     * Get fee estimate based on priority
     */
    suspend fun getFeeEstimate(feeLevel: FeeLevel = FeeLevel.NORMAL): FeeEstimate {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://blockstream.info/api/fee-estimates"
                val request = Request.Builder().url(url).build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext getDefaultFeeEstimate(feeLevel)

                val json = JSONObject(responseBody)

                // Blockstream API returns fees for different confirmation targets
                val feePerByte = when (feeLevel) {
                    FeeLevel.SLOW -> json.getDouble("144") // 24 hours
                    FeeLevel.NORMAL -> json.getDouble("6") // 6 blocks (~1 hour)
                    FeeLevel.FAST -> json.getDouble("2") // 2 blocks (~20 minutes)
                }

                // Estimate typical transaction size (in bytes)
                val estimatedSize = 250L // Conservative estimate for 2-input, 2-output transaction
                val totalFeeSatoshis = (estimatedSize * feePerByte).toLong()

                val totalFeeDecimal = BigDecimal(totalFeeSatoshis).divide(
                    BigDecimal(SATOSHIS_PER_BTC),
                    8,
                    RoundingMode.HALF_UP
                ).toPlainString()

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

            } catch (e: Exception) {
                Log.e("BitcoinRepo", "Error getting fee estimate: ${e.message}")
                getDefaultFeeEstimate(feeLevel)
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
    /**
     * Create and sign a Bitcoin transaction using bitcoinj
     */
    suspend fun createAndSignTransaction(
        fromKey: ECKey,
        toAddress: String,
        satoshis: Long,
        network: BitcoinNetwork
    ): Transaction {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("BitcoinRepo", "Creating transaction: $satoshis satoshis to $toAddress")

                val networkParams = when (network) {
                    BitcoinNetwork.MAINNET -> MainNetParams.get()
                    BitcoinNetwork.TESTNET -> TestNet3Params.get()
                    BitcoinNetwork.REGTEST -> RegTestParams.get()
                }

                // 1. Create transaction
                val tx = Transaction(networkParams)

                // 2. Add output (sending to recipient)
                val outputValue = Coin.valueOf(satoshis)
                val outputAddress = Address.fromString(networkParams, toAddress)
                tx.addOutput(outputValue, outputAddress)

                // 3. Get UTXOs (Unspent Transaction Outputs) for the sender
                val fromAddress = LegacyAddress.fromKey(networkParams, fromKey).toString()
                val utxos = getUnspentOutputs(fromAddress, network)

                if (utxos.isEmpty()) {
                    throw IllegalStateException("No UTXOs found for address: $fromAddress")
                }

                // 4. Add inputs from UTXOs
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

                    // Stop if we have enough inputs
                    if (totalInputValue.isGreaterThan(Coin.valueOf(satoshis + 1000))) {
                        break
                    }
                }

                // 5. Add change output if needed
                val fee = Coin.valueOf(1000) // Fixed fee for simplicity
                val changeValue = totalInputValue.subtract(Coin.valueOf(satoshis)).subtract(fee)
                if (changeValue.isPositive) {
                    tx.addOutput(changeValue, LegacyAddress.fromKey(networkParams, fromKey))
                }

                // 6. Sign all inputs using the proper bitcoinj signing method
                for (i in 0 until tx.inputs.size) {
                    val input = tx.getInput(i.toLong())
                    val utxo = utxos[i]

                    // Sign the input
                    val hash = tx.hashForSignature(
                        i,
                        utxo.script,
                        Transaction.SigHash.ALL,
                        false
                    )
                    val sig = fromKey.sign(hash)
                    val txSig = TransactionSignature(sig, Transaction.SigHash.ALL, false)

                    // Create the input script
                    val script = ScriptBuilder.createInputScript(txSig, fromKey)
                    input.scriptSig = script
                }

                Log.d("BitcoinRepo", "Transaction signed successfully")
                tx

            } catch (e: Exception) {
                Log.e("BitcoinRepo", "Error creating transaction: ${e.message}", e)
                throw e
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
                Log.d("BitcoinRepo", "📡 Calling: $url")

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d("BitcoinRepo", "Response code: ${response.code}")

                if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                    Log.e("BitcoinRepo", " Failed to get UTXOs. Code: ${response.code}")
                    return@withContext emptyList()
                }

                if (responseBody.trim() == "[]") {
                    Log.d("BitcoinRepo", "ℹ No UTXOs found (empty array)")
                    return@withContext emptyList()
                }

                val jsonArray = JSONArray(responseBody)
                Log.d("BitcoinRepo", " Found ${jsonArray.length()} UTXOs in API response")

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

                        Log.d("BitcoinRepo", "Processing UTXO $i: txid=$txid, vout=$vout, value=$value")

                        // Fetch transaction to get the scriptpubkey
                        val scriptHex = getScriptPubKeyFromTransaction(txid, vout, baseUrl)

                        if (scriptHex == null) {
                            Log.e("BitcoinRepo", " Failed to get script for UTXO $i")
                            continue
                        }

                        Log.d("BitcoinRepo", "Got script: ${scriptHex.take(20)}...")

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
                        Log.d("BitcoinRepo", "✓ Added UTXO $i successfully")

                    } catch (e: Exception) {
                        Log.e("BitcoinRepo", " Error processing UTXO $i: ${e.message}")
                    }
                }

                Log.d("BitcoinRepo", " Total UTXOs processed: ${utxos.size}")
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
                // Add delay to avoid rate limiting
                delay(100L)

                val url = "$baseUrl/tx/$txid"
                Log.d("BitcoinRepo", " Fetching TX: $url")

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                    Log.e("BitcoinRepo", " Failed to fetch TX $txid: ${response.code}")
                    return@withContext null
                }

                Log.d("BitcoinRepo", " TX Response: ${responseBody.take(500)}...")

                val txJson = JSONObject(responseBody)
                val voutArray = txJson.getJSONArray("vout")

                if (vout >= voutArray.length()) {
                    Log.e("BitcoinRepo", " Invalid vout $vout for TX $txid")
                    return@withContext null
                }

                val output = voutArray.getJSONObject(vout)

                // Try different ways to get scriptpubkey
                return@withContext when {
                    // 1. Check if scriptpubkey is a JSON object with "hex" field
                    output.has("scriptpubkey") && output.get("scriptpubkey") is JSONObject -> {
                        val scriptObj = output.getJSONObject("scriptpubkey")
                        scriptObj.getString("hex")
                    }
                    // 2. Check if scriptpubkey is a direct string (hex)
                    output.has("scriptpubkey") && output.get("scriptpubkey") is String -> {
                        output.getString("scriptpubkey")
                    }
                    // 3. Check for alternative fields
                    output.has("scriptPubKey") -> {
                        val scriptValue = output.get("scriptPubKey")
                        if (scriptValue is String) scriptValue else null
                    }
                    output.has("script") -> {
                        val scriptValue = output.get("script")
                        if (scriptValue is String) scriptValue else null
                    }
                    // 4. Last resort: try to get it as a string anyway
                    else -> {
                        output.optString("scriptpubkey").takeIf { it.isNotEmpty() && it.matches(Regex("^[0-9a-fA-F]+$")) }
                    }
                }

            } catch (e: Exception) {
                Log.e("BitcoinRepo", " Error fetching script from TX $txid: ${e.message}")
                e.printStackTrace()
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
    ): BroadcastResult {
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
                    BroadcastResult(
                        success = true,
                        hash = txId,
                        chain = ChainType.BITCOIN
                    )
                } else {
                    val error = response.body?.string() ?: "Broadcast failed with code: ${response.code}"
                    Log.e("BitcoinRepo", "Broadcast failed: $error")
                    BroadcastResult(
                        success = false,
                        error = error,
                        chain = ChainType.BITCOIN
                    )
                }

            } catch (e: Exception) {
                Log.e("BitcoinRepo", "Error broadcasting: ${e.message}", e)
                BroadcastResult(
                    success = false,
                    error = e.message ?: "Broadcast failed",
                    chain = ChainType.BITCOIN
                )
            }
        }
    }

    suspend fun debugCheckUTXOsDirect(address: String, network: BitcoinNetwork = BitcoinNetwork.TESTNET) {
        return withContext(Dispatchers.IO) {
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

                // Check 1: UTXOs endpoint
                val utxoUrl = "$baseUrl/address/$address/utxo"
                Log.d("BitcoinRepo", "UTXO URL: $utxoUrl")

                val utxoRequest = Request.Builder().url(utxoUrl).build()
                val utxoResponse = client.newCall(utxoRequest).execute()
                val utxoBody = utxoResponse.body?.string() ?: ""

                Log.d("BitcoinRepo", "UTXO Response code: ${utxoResponse.code}")
                Log.d("BitcoinRepo", "UTXO Response: $utxoBody")

                // Check 2: Address info endpoint
                val addressUrl = "$baseUrl/address/$address"
                Log.d("BitcoinRepo", "Address URL: $addressUrl")

                val addressRequest = Request.Builder().url(addressUrl).build()
                val addressResponse = client.newCall(addressRequest).execute()
                val addressBody = addressResponse.body?.string() ?: ""

                Log.d("BitcoinRepo", "Address Response code: ${addressResponse.code}")
                Log.d("BitcoinRepo", "Address Response: $addressBody")

                // Parse and display
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

    // Bitcoin-specific models
    data class UTXO(
        val outPoint: TransactionOutPoint,
        val value: Coin,
        val script: Script
    )
}