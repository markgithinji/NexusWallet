package com.example.nexuswallet.feature.coin.ethereum

import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.coin.CachedGasPrice
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.SafeApiCall
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EthereumBlockchainRepository @Inject constructor(
    private val etherscanApi: EtherscanApiService
) {
    private val gasPriceCache = mutableMapOf<String, CachedGasPrice>()
    private val confirmationTimeCache = mutableMapOf<String, CachedConfirmationTime>()

    suspend fun getEthereumBalance(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<BigDecimal> = SafeApiCall.make {
        Log.d("EthereumRepo", "Fetching balance for $address on ${network.displayName}")

        val chainId = network.chainId
        val apiKey = BuildConfig.ETHERSCAN_API_KEY

        val response = etherscanApi.getEthereumBalance(
            chainId = chainId,
            address = address,
            apiKey = apiKey
        )

        if (response.status == "1") {
            val wei = BigDecimal(response.result)
            val eth = wei.divide(BigDecimal(WEI_PER_ETH), ETH_DECIMALS, RoundingMode.HALF_UP)
            Log.d(
                "EthereumRepo",
                "Balance fetched for $address on ${network.displayName}: $eth ETH"
            )
            eth
        } else {
            throw Exception("API error: ${response.message}")
        }
    }

    suspend fun getEthereumTransactions(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<List<EtherscanTransaction>> = SafeApiCall.make {
        Log.d("EthereumRepo", "Fetching transactions for $address on ${network.displayName}")

        val chainId = network.chainId
        val apiKey = BuildConfig.ETHERSCAN_API_KEY

        val response = etherscanApi.getEthereumTransactions(
            chainId = chainId,
            address = address,
            apiKey = apiKey
        )

        if (response.status == "1") {
            Log.d(
                "EthereumRepo",
                "Fetched ${response.result.size} transactions for $address on ${network.displayName}"
            )
            response.result
        } else {
            throw Exception("API error: ${response.message}")
        }
    }

    /**
     * Get current gas price from Etherscan - works for all supported chains
     * Uses gasoracle for Mainnet, proxy eth_gasPrice for other networks
     */
    suspend fun getCurrentGasPrice(network: EthereumNetwork = EthereumNetwork.Mainnet): Result<GasPrice> = SafeApiCall.make {
        Log.d("EthereumRepo", "Fetching gas price for network: ${network.displayName} from Etherscan")

        // Check cache for this specific network
        val cacheKey = network.chainId
        gasPriceCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < GAS_PRICE_CACHE_TTL_MS) {
                Log.d("EthereumRepo", "Using cached gas price for ${network.displayName}")
                return@make cached.price
            } else {
                gasPriceCache.remove(cacheKey)
            }
        }

        val chainId = network.chainId
        val apiKey = BuildConfig.ETHERSCAN_API_KEY

        val gasPrice = when (network) {
            is EthereumNetwork.Mainnet -> {
                // Mainnet uses gasoracle for Safe/Propose/Fast prices
                val response = etherscanApi.getGasPrice(
                    chainId = chainId,
                    apiKey = apiKey
                )

                if (response.status == "1") {
                    GasPrice(
                        safe = response.result.SafeGasPrice,
                        propose = response.result.ProposeGasPrice,
                        fast = response.result.FastGasPrice,
                        lastBlock = response.result.lastBlock,
                        baseFee = response.result.suggestBaseFee
                    )
                } else {
                    throw Exception("Gas price API error: ${response.message}")
                }
            }

            is EthereumNetwork.Sepolia -> {
                // Sepolia uses proxy eth_gasPrice endpoint
                val response = etherscanApi.getGasPriceProxy(
                    chainId = chainId,
                    apiKey = apiKey
                )

                // Parse hex result to wei
                val gasPriceWei = org.web3j.utils.Numeric.toBigInt(response.result)
                Log.d("EthereumRepo", "Raw gas price wei for Sepolia: $gasPriceWei")

                // Convert wei to Gwei with HIGHER PRECISION (6 decimal places)
                val gasPriceGwei = gasPriceWei.toBigDecimal().divide(
                    BigDecimal(WEI_PER_GWEI),
                    6,
                    RoundingMode.HALF_UP
                )

                val priceStr = gasPriceGwei.toString()
                Log.d("EthereumRepo", "Gas price in Gwei for Sepolia: $priceStr")

                // For Sepolia, we use the same price for all levels with multipliers
                GasPrice(
                    safe = (gasPriceGwei * SLOW_PRICE_MULTIPLIER).setScale(6, RoundingMode.HALF_UP).toString(),
                    propose = priceStr,
                    fast = (gasPriceGwei * FAST_PRICE_MULTIPLIER).setScale(6, RoundingMode.HALF_UP).toString(),
                    lastBlock = null,
                    baseFee = null
                )
            }
        }

        // Update cache for this network
        gasPriceCache[cacheKey] = CachedGasPrice(gasPrice, System.currentTimeMillis())

        Log.d(
            "EthereumRepo",
            "Gas price response for ${network.displayName} - Safe: ${gasPrice.safe}, Propose: ${gasPrice.propose}, Fast: ${gasPrice.fast}"
        )
        gasPrice
    }

    /**
     * Get dynamic fee estimate based on current gas prices
     */
    suspend fun getDynamicFeeEstimate(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<EthereumFeeEstimate> {
        Log.d(
            "EthereumRepo",
            "=== getDynamicFeeEstimate called with feeLevel: $feeLevel on ${network.displayName} ==="
        )

        // Get current gas price
        val gasPriceResult = getCurrentGasPrice(network)

        when (gasPriceResult) {
            is Result.Success -> {
                val gasPrice = gasPriceResult.data

                Log.d(
                    "EthereumRepo",
                    "Raw gas prices for ${network.displayName} - Safe: ${gasPrice.safe}, Propose: ${gasPrice.propose}, Fast: ${gasPrice.fast}"
                )

                // Parse gas prices as BigDecimals
                val safeGwei = gasPrice.safe.toBigDecimal()
                val proposeGwei = gasPrice.propose.toBigDecimal()
                val fastGwei = gasPrice.fast.toBigDecimal()

                Log.d(
                    "EthereumRepo",
                    "Parsed gas prices - Safe: $safeGwei, Propose: $proposeGwei, Fast: $fastGwei"
                )

                // Get final gas price based on fee level
                val (selectedGwei, priceLabel) = when (feeLevel) {
                    FeeLevel.SLOW -> safeGwei to "Safe"
                    FeeLevel.NORMAL -> proposeGwei to "Propose"
                    FeeLevel.FAST -> fastGwei to "Fast"
                }

                val gasPriceGwei = formatGasPrice(selectedGwei)
                Log.d(
                    "EthereumRepo",
                    "Selected $priceLabel price for $feeLevel on ${network.displayName}: $gasPriceGwei Gwei"
                )

                // Calculate wei values
                val gasPriceWei = (BigDecimal(gasPriceGwei) * BigDecimal(WEI_PER_GWEI)).toBigInteger()
                val totalFeeWei = gasPriceWei.multiply(BigInteger.valueOf(GAS_LIMIT_STANDARD))
                val totalFeeEth = BigDecimal(totalFeeWei).divide(
                    BigDecimal(WEI_PER_ETH),
                    ETH_DECIMALS,
                    RoundingMode.HALF_UP
                ).toPlainString()

                // Get real confirmation time estimate from Etherscan (Mainnet only)
                val estimatedTimeResult = if (network is EthereumNetwork.Mainnet) {
                    getConfirmationTimeEstimate(gasPriceWei.toString(), network)
                } else {
                    // For Sepolia, use reasonable estimates based on fee level
                    val time = when (feeLevel) {
                        FeeLevel.SLOW -> 120
                        FeeLevel.NORMAL -> 60
                        FeeLevel.FAST -> 30
                    }
                    Result.Success(time)
                }

                when (estimatedTimeResult) {
                    is Result.Success -> {
                        Log.d(
                            "EthereumRepo",
                            "Fee in ETH: $totalFeeEth ETH, Estimated time: ${estimatedTimeResult.data}s"
                        )

                        return Result.Success(
                            EthereumFeeEstimate(
                                gasPriceGwei = gasPriceGwei,
                                gasPriceWei = gasPriceWei.toString(),
                                gasLimit = GAS_LIMIT_STANDARD,
                                totalFeeWei = totalFeeWei.toString(),
                                totalFeeEth = totalFeeEth,
                                estimatedTime = estimatedTimeResult.data,
                                priority = feeLevel,
                                baseFee = gasPrice.baseFee,
                                isEIP1559 = gasPrice.baseFee != null
                            )
                        )
                    }

                    is Result.Error -> {
                        Log.e(
                            "EthereumRepo",
                            "Failed to get confirmation time: ${estimatedTimeResult.message}"
                        )
                        return Result.Error("Failed to get confirmation time: ${estimatedTimeResult.message}")
                    }

                    Result.Loading -> {
                        return Result.Error("Confirmation time request timed out")
                    }
                }
            }

            is Result.Error -> {
                Log.e(
                    "EthereumRepo",
                    "Failed to get gas price for ${network.displayName}: ${gasPriceResult.message}"
                )
                return Result.Error("Failed to get gas price: ${gasPriceResult.message}")
            }

            Result.Loading -> {
                Log.w("EthereumRepo", "Gas price request timed out for ${network.displayName}")
                return Result.Error("Gas price request timed out")
            }
        }
    }

    /**
     * Get real confirmation time estimate from Etherscan with caching
     * Returns Result.Error if API fails - NO FALLBACKS
     */
    private suspend fun getConfirmationTimeEstimate(
        gasPriceWei: String,
        network: EthereumNetwork
    ): Result<Int> = SafeApiCall.make {
        // Create cache key that includes both gas price and network
        val cacheKey = "${network.chainId}_$gasPriceWei"

        // Check cache first
        confirmationTimeCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CONFIRMATION_TIME_CACHE_TTL_MS) {
                Log.d("EthereumRepo", "Using cached confirmation time for ${network.displayName}: ${cached.seconds} seconds")
                return@make cached.seconds
            } else {
                confirmationTimeCache.remove(cacheKey)
            }
        }

        val chainId = network.chainId
        val apiKey = BuildConfig.ETHERSCAN_API_KEY

        Log.d("EthereumRepo", "Fetching confirmation time for ${network.displayName} with gas price: $gasPriceWei wei")

        val response = etherscanApi.getConfirmationTimeEstimate(
            chainId = chainId,
            gasPriceWei = gasPriceWei,
            apiKey = apiKey
        )

        if (response.status == "1") {
            val seconds = response.result.toIntOrNull()
            if (seconds != null) {
                Log.d("EthereumRepo", "Confirmation time estimate for ${network.displayName}: $seconds seconds")

                // Cache the result
                confirmationTimeCache[cacheKey] = CachedConfirmationTime(seconds, System.currentTimeMillis())

                seconds
            } else {
                throw Exception("Invalid confirmation time format: ${response.result}")
            }
        } else {
            throw Exception("Confirmation time API error: ${response.message}")
        }
    }

    /**
     * Get standard fee estimate
     */
    suspend fun getFeeEstimate(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<EthereumFeeEstimate> {
        return getDynamicFeeEstimate(feeLevel, network)
    }

    /**
     * Format gas price for display - keep decimals for small values
     */
    private fun formatGasPrice(price: BigDecimal): String {
        return if (price < BigDecimal.ONE) {
            String.format("%.6f", price)
        } else {
            String.format("%.2f", price)
        }
    }

    suspend fun getEthereumNonce(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<Int> = SafeApiCall.make {
        Log.d(
            "EthereumRepo",
            "Fetching nonce for $address on ${network.displayName} with tag=pending"
        )

        val chainId = network.chainId
        val apiKey = BuildConfig.ETHERSCAN_API_KEY

        val response = etherscanApi.getTransactionCount(
            chainId = chainId,
            address = address,
            tag = "pending",
            apiKey = apiKey
        )

        Log.d("EthereumRepo", "Raw nonce response: $response")

        // Handle the response based on network
        val nonce = when (network) {
            is EthereumNetwork.Mainnet -> {
                if (response.result.isNotEmpty() && response.result != "0x") {
                    val cleanHex = if (response.result.startsWith("0x")) {
                        response.result
                    } else {
                        "0x${response.result}"
                    }
                    org.web3j.utils.Numeric.toBigInt(cleanHex).toInt()
                } else {
                    0
                }
            }

            is EthereumNetwork.Sepolia -> {
                org.web3j.utils.Numeric.toBigInt(response.result).toInt()
            }
        }

        Log.d("EthereumRepo", "Final nonce for $address on ${network.displayName}: $nonce")
        nonce
    }

    suspend fun broadcastEthereumTransaction(
        rawTx: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<BroadcastResult> = SafeApiCall.make {
        Log.d("EthereumRepo", "Broadcasting transaction on ${network.displayName}")

        val chainId = network.chainId
        val apiKey = BuildConfig.ETHERSCAN_API_KEY

        delay(RETRY_DELAY_MS)

        val response = etherscanApi.broadcastTransaction(
            chainId = chainId,
            hex = rawTx,
            apiKey = apiKey
        )

        Log.d("EthereumRepo", "Broadcast raw response on ${network.displayName}: $response")

        when (network) {
            is EthereumNetwork.Mainnet -> handleMainnetBroadcast(response)
            is EthereumNetwork.Sepolia -> handleSepoliaBroadcast(response)
        }
    }

    private fun handleMainnetBroadcast(response: EtherscanBroadcastResponse): BroadcastResult {
        // First check if there's an error object
        response.error?.let { error ->
            Log.e("EthereumRepo", "Broadcast error on Mainnet: ${error.message}")
            throw Exception(error.message)
        }

        // Check if result is present and looks like a transaction hash
        response.result?.let { result ->
            when {
                result.startsWith("0x") && result.length == TX_HASH_LENGTH -> {
                    Log.d("EthereumRepo", "Broadcast successful on Mainnet: $result")
                    return BroadcastResult(
                        success = true,
                        hash = result
                    )
                }

                result.contains("nonce") -> {
                    Log.e("EthereumRepo", "Nonce error: $result")
                    throw Exception("Nonce error: $result")
                }

                result.contains("insufficient funds") || result.contains("balance") -> {
                    Log.e("EthereumRepo", "Insufficient funds: $result")
                    throw Exception("Insufficient balance: $result")
                }

                result.contains("already known") -> {
                    Log.w("EthereumRepo", "Transaction already known")
                    val hashPattern = TX_HASH_REGEX
                    val hash = hashPattern.find(result)?.value
                    if (hash != null) {
                        return BroadcastResult(
                            success = true,
                            hash = hash
                        )
                    } else {
                        throw Exception(result)
                    }
                }
            }
        }

        // If we get here, something unexpected happened
        Log.e("EthereumRepo", "Broadcast failed with unexpected response")
        throw Exception(response.message ?: "Unknown error")
    }

    private fun handleSepoliaBroadcast(response: EtherscanBroadcastResponse): BroadcastResult {
        Log.d("EthereumRepo", "Handling Sepolia broadcast response: $response")

        // First check if there's an error object
        response.error?.let { error ->
            Log.e("EthereumRepo", "Broadcast error on Sepolia: ${error.message}")
            throw Exception(error.message)
        }

        // Check if result is present and looks like a transaction hash
        response.result?.let { result ->
            if (result.startsWith("0x") && result.length == TX_HASH_LENGTH) {
                Log.d("EthereumRepo", "Broadcast successful on Sepolia: $result")
                return BroadcastResult(
                    success = true,
                    hash = result
                )
            } else {
                Log.e("EthereumRepo", "Invalid transaction hash format on Sepolia: $result")
                throw Exception("Invalid response format")
            }
        }

        // If we get here, something unexpected happened
        Log.e("EthereumRepo", "Unexpected broadcast response on Sepolia: $response")
        throw Exception(response.message ?: "Unknown error")
    }

    companion object {
        // Ethereum constants
        private const val GAS_LIMIT_STANDARD = 21000L
        private const val WEI_PER_GWEI = 1_000_000_000L
        private const val WEI_PER_ETH = "1000000000000000000"
        private const val ETH_DECIMALS = 18

        // Price multipliers for different fee levels
        private val SLOW_PRICE_MULTIPLIER = BigDecimal("0.9")
        private val FAST_PRICE_MULTIPLIER = BigDecimal("1.2")

        // Gas price cache TTL (30 seconds)
        private const val GAS_PRICE_CACHE_TTL_MS = 30000L

        // Retry delay
        private const val RETRY_DELAY_MS = 500L

        // Transaction hash validation
        private const val TX_HASH_LENGTH = 66
        private val TX_HASH_REGEX = Regex("^0x[a-fA-F0-9]{64}$")

        // Cache TTL for confirmation times (5 minutes)
        private const val CONFIRMATION_TIME_CACHE_TTL_MS = 300000L
    }
}