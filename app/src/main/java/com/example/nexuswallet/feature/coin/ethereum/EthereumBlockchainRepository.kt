package com.example.nexuswallet.feature.coin.ethereum

import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
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
    // Simple cache for gas price to avoid too many calls (per network)
    private val gasPriceCache = mutableMapOf<String, GasPriceCache>()

    private val confirmationTimeCache = mutableMapOf<String, ConfirmationTimeCache>()

    suspend fun getEthereumBalance(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<BigDecimal> {
        return try {
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
                Result.Success(eth)
            } else {
                Log.e(
                    "EthereumRepo",
                    "Balance API error for ${network.displayName}: ${response.message}"
                )
                Result.Error("API error: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e(
                "EthereumRepo",
                "Failed to get balance on ${network.displayName}: ${e.message}",
                e
            )
            Result.Error("Failed to get balance: ${e.message}", e)
        }
    }

    suspend fun getEthereumTransactions(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<List<EtherscanTransaction>> {
        return try {
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
                Result.Success(response.result)
            } else {
                Log.e(
                    "EthereumRepo",
                    "Transactions API error for ${network.displayName}: ${response.message}"
                )
                Result.Error("API error: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e(
                "EthereumRepo",
                "Failed to get transactions on ${network.displayName}: ${e.message}",
                e
            )
            Result.Error("Failed to get transactions: ${e.message}", e)
        }
    }

    suspend fun getCurrentGasPrice(network: EthereumNetwork = EthereumNetwork.Mainnet): Result<GasPrice> {
        return try {
            Log.d("EthereumRepo", "Fetching gas price for network: ${network.displayName}")

            // For Sepolia, we need to use a different approach or return appropriate values
            if (network is EthereumNetwork.Sepolia) {
                // Sepolia has predictable low gas prices for testing
                // TODO: Check if we might want to use a different API
                return Result.Success(
                    GasPrice(
                        safe = SEPOLIA_GAS_PRICE,
                        propose = SEPOLIA_GAS_PRICE,
                        fast = SEPOLIA_GAS_PRICE,
                        lastBlock = null,
                        baseFee = null
                    )
                )
            }

            // Check cache for this specific network
            val cache = gasPriceCache.getOrPut(network.chainId) { GasPriceCache() }
            cache.get()?.let { cachedPrice ->
                Log.d("EthereumRepo", "Using cached gas price for ${network.displayName}")
                return Result.Success(cachedPrice)
            }

            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getGasPrice(
                chainId = chainId,
                apiKey = apiKey
            )

            if (response.status == "1") {
                val gasPrice = GasPrice(
                    safe = response.result.SafeGasPrice,
                    propose = response.result.ProposeGasPrice,
                    fast = response.result.FastGasPrice,
                    lastBlock = response.result.lastBlock,
                    baseFee = response.result.suggestBaseFee
                )

                // Update cache for this network
                cache.update(gasPrice)

                Log.d(
                    "EthereumRepo",
                    "Gas price response for ${network.displayName} - Safe: ${gasPrice.safe}, Propose: ${gasPrice.propose}, Fast: ${gasPrice.fast}"
                )
                Result.Success(gasPrice)
            } else {
                Log.e(
                    "EthereumRepo",
                    "Gas price API error for ${network.displayName}: ${response.message}"
                )
                Result.Error("Gas price API error: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e("EthereumRepo", "Exception getting gas price for ${network.displayName}", e)
            Result.Error("Failed to get gas price: ${e.message}")
        }
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

        // For Sepolia, return a fixed low fee estimate
        if (network is EthereumNetwork.Sepolia) {
            val gasPriceGwei = SEPOLIA_GAS_PRICE
            val gasPriceWei = (BigDecimal(gasPriceGwei) * BigDecimal(WEI_PER_GWEI)).toBigInteger()
            val totalFeeWei = gasPriceWei.multiply(BigInteger.valueOf(GAS_LIMIT_STANDARD))
            val totalFeeEth = BigDecimal(totalFeeWei).divide(
                BigDecimal(WEI_PER_ETH),
                ETH_DECIMALS,
                RoundingMode.HALF_UP
            ).toPlainString()

            return Result.Success(
                EthereumFeeEstimate(
                    gasPriceGwei = gasPriceGwei,
                    gasPriceWei = gasPriceWei.toString(),
                    gasLimit = GAS_LIMIT_STANDARD,
                    totalFeeWei = totalFeeWei.toString(),
                    totalFeeEth = totalFeeEth,
                    estimatedTime = SEPOLIA_ESTIMATED_TIME,
                    priority = feeLevel,
                    baseFee = null,
                    isEIP1559 = false
                )
            )
        }

        // Get current gas price
        val gasPriceResult = getCurrentGasPrice(network)

        when (gasPriceResult) {
            is Result.Success -> {
                val gasPrice = gasPriceResult.data

                Log.d(
                    "EthereumRepo",
                    "Raw gas prices for ${network.displayName} - Safe: ${gasPrice.safe}, Propose: ${gasPrice.propose}, Fast: ${gasPrice.fast}"
                )

                // Parse gas prices as BigDecimals - will throw if parsing fails
                val safeGwei = gasPrice.safe.toBigDecimal()
                val proposeGwei = gasPrice.propose.toBigDecimal()
                val fastGwei = gasPrice.fast.toBigDecimal()

                Log.d(
                    "EthereumRepo",
                    "Parsed gas prices - Safe: $safeGwei, Propose: $proposeGwei, Fast: $fastGwei"
                )

                // Get final gas price based on fee level
                val (selectedGwei, priceLabel) = when (feeLevel) {
                    FeeLevel.SLOW -> (safeGwei * SLOW_PRICE_MULTIPLIER) to "Safe"
                    FeeLevel.NORMAL -> proposeGwei to "Propose"
                    FeeLevel.FAST -> (fastGwei * FAST_PRICE_MULTIPLIER) to "Fast"
                }

                val gasPriceGwei = formatGasPrice(selectedGwei)
                Log.d(
                    "EthereumRepo",
                    "Selected $priceLabel price for $feeLevel on ${network.displayName}: $gasPriceGwei Gwei"
                )

                // Calculate wei values
                val gasPriceWei =
                    (BigDecimal(gasPriceGwei) * BigDecimal(WEI_PER_GWEI)).toBigInteger()
                val totalFeeWei = gasPriceWei.multiply(BigInteger.valueOf(GAS_LIMIT_STANDARD))
                val totalFeeEth = BigDecimal(totalFeeWei).divide(
                    BigDecimal(WEI_PER_ETH),
                    ETH_DECIMALS,
                    RoundingMode.HALF_UP
                ).toPlainString()

                // Get real confirmation time estimate from Etherscan (Mainnet only)
                val estimatedTime = if (network is EthereumNetwork.Mainnet) {
                    getConfirmationTimeEstimate(gasPriceWei.toString())
                } else {
                    DEFAULT_ESTIMATED_TIME
                }

                Log.d(
                    "EthereumRepo",
                    "Fee in ETH: $totalFeeEth ETH, Estimated time: ${estimatedTime}s"
                )

                return Result.Success(
                    EthereumFeeEstimate(
                        gasPriceGwei = gasPriceGwei,
                        gasPriceWei = gasPriceWei.toString(),
                        gasLimit = GAS_LIMIT_STANDARD,
                        totalFeeWei = totalFeeWei.toString(),
                        totalFeeEth = totalFeeEth,
                        estimatedTime = estimatedTime,
                        priority = feeLevel,
                        baseFee = gasPrice.baseFee,
                        isEIP1559 = gasPrice.baseFee != null
                    )
                )
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
     */
    private suspend fun getConfirmationTimeEstimate(gasPriceWei: String): Int {
        // Check cache first
        val cacheKey = gasPriceWei
        confirmationTimeCache[cacheKey]?.let { cache ->
            if (System.currentTimeMillis() - cache.timestamp < CONFIRMATION_TIME_CACHE_TTL_MS) {
                Log.d("EthereumRepo", "Using cached confirmation time: ${cache.seconds} seconds")
                return cache.seconds
            }
        }

        return try {
            val chainId = EthereumNetwork.Mainnet.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            Log.d("EthereumRepo", "Fetching confirmation time for gas price: $gasPriceWei wei")

            val response = etherscanApi.getConfirmationTimeEstimate(
                chainId = chainId,
                gasPriceWei = gasPriceWei,
                apiKey = apiKey
            )

            if (response.status == "1") {
                val seconds = response.result.toIntOrNull() ?: DEFAULT_ESTIMATED_TIME
                Log.d("EthereumRepo", "Confirmation time estimate: $seconds seconds")

                // Cache the result
                confirmationTimeCache[cacheKey] =
                    ConfirmationTimeCache(seconds, System.currentTimeMillis())

                seconds
            } else {
                Log.w(
                    "EthereumRepo",
                    "Failed to get confirmation time: ${response.message}, using estimate"
                )
                // Return a reasonable estimate based on gas price
                val gasPrice = BigDecimal(gasPriceWei).divide(BigDecimal(WEI_PER_GWEI))
                estimateConfirmationTimeFromGasPrice(gasPrice)
            }
        } catch (e: Exception) {
            Log.e("EthereumRepo", "Error getting confirmation time", e)
            DEFAULT_ESTIMATED_TIME
        }
    }

    /**
     * Fallback estimation when API fails
     */
    private fun estimateConfirmationTimeFromGasPrice(gasPriceGwei: BigDecimal): Int {
        return when {
            gasPriceGwei > BigDecimal("50") -> 15      // Very fast
            gasPriceGwei > BigDecimal("20") -> 30      // Fast
            gasPriceGwei > BigDecimal("10") -> 60      // Normal
            gasPriceGwei > BigDecimal("5") -> 120      // Slow
            else -> 300                                 // Very slow
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
     * Format gas price for display - keep decimals for small values, round to integer for large
     */
    private fun formatGasPrice(price: BigDecimal): String {
        return if (price < BigDecimal.ONE) {
            // For prices less than 1 Gwei, show with 2 decimal places
            String.format("%.2f", price)
        } else {
            // For prices 1 Gwei or more, round to nearest integer
            price.setScale(0, RoundingMode.HALF_UP).toString()
        }
    }

    suspend fun getEthereumNonce(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<Int> {
        return try {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            Log.d(
                "EthereumRepo",
                "Fetching nonce for $address on ${network.displayName} with tag=pending"
            )

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
                    // Mainnet returns JSON-RPC format with hex result
                    if (response.result.isNotEmpty() && response.result != "0x") {
                        val cleanHex = if (response.result.startsWith("0x")) {
                            response.result
                        } else {
                            "0x${response.result}"
                        }
                        val nonceValue = org.web3j.utils.Numeric.toBigInt(cleanHex).toInt()
                        Log.d(
                            "EthereumRepo",
                            "Parsed Mainnet nonce: $nonceValue from hex: $cleanHex"
                        )
                        nonceValue
                    } else {
                        0
                    }
                }

                is EthereumNetwork.Sepolia -> {
                    // It's hex - convert it
                    val nonceValue = org.web3j.utils.Numeric.toBigInt(response.result).toInt()
                    Log.d("EthereumRepo", "Parsed Sepolia hex nonce: $nonceValue")
                    nonceValue
                }
            }

            Log.d("EthereumRepo", "Final nonce for $address on ${network.displayName}: $nonce")
            Result.Success(nonce)

        } catch (e: Exception) {
            Log.e("EthereumRepo", "Failed to get nonce on ${network.displayName}: ${e.message}", e)
            Result.Error("Failed to get nonce: ${e.message}")
        }
    }

    suspend fun broadcastEthereumTransaction(
        rawTx: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<BroadcastResult> {
        Log.d("EthereumRepo", "Broadcasting transaction on ${network.displayName}")

        return try {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            delay(RETRY_DELAY_MS)

            val response = etherscanApi.broadcastTransaction(
                chainId = chainId,
                hex = rawTx,
                apiKey = apiKey
            )

            Log.d("EthereumRepo", "Broadcast raw response on ${network.displayName}: $response")

            return when (network) {
                is EthereumNetwork.Mainnet -> handleMainnetBroadcast(response)
                is EthereumNetwork.Sepolia -> handleSepoliaBroadcast(response)
            }

        } catch (e: Exception) {
            Log.e("EthereumRepo", "Network error broadcasting on ${network.displayName}", e)
            Result.Success(
                BroadcastResult(
                    success = false,
                    error = "Network error: ${e.message}"
                )
            )
        }
    }

    private fun handleMainnetBroadcast(response: EtherscanBroadcastResponse): Result<BroadcastResult> {
        // First check if there's an error object
        response.error?.let { error ->
            Log.e("EthereumRepo", "Broadcast error on Mainnet: ${error.message}")
            return Result.Success(
                BroadcastResult(
                    success = false,
                    error = error.message
                )
            )
        }

        // Check if result is present and looks like a transaction hash
        response.result?.let { result ->
            when {
                result.startsWith("0x") && result.length == TX_HASH_LENGTH -> {
                    Log.d("EthereumRepo", "Broadcast successful on Mainnet: $result")
                    return Result.Success(
                        BroadcastResult(
                            success = true,
                            hash = result
                        )
                    )
                }

                result.contains("nonce") -> {
                    Log.e("EthereumRepo", "Nonce error: $result")
                    return Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Nonce error: $result"
                        )
                    )
                }

                result.contains("insufficient funds") || result.contains("balance") -> {
                    Log.e("EthereumRepo", "Insufficient funds: $result")
                    return Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Insufficient balance: $result"
                        )
                    )
                }

                result.contains("already known") -> {
                    Log.w("EthereumRepo", "Transaction already known")
                    val hashPattern = TX_HASH_REGEX
                    val hash = hashPattern.find(result)?.value
                    return Result.Success(
                        BroadcastResult(
                            success = hash != null,
                            hash = hash,
                            error = if (hash == null) result else null
                        )
                    )
                }
            }
        }

        // If we get here, something unexpected happened
        Log.e("EthereumRepo", "Broadcast failed with unexpected response")
        return Result.Success(
            BroadcastResult(
                success = false,
                error = response.message ?: "Unknown error"
            )
        )
    }

    private fun handleSepoliaBroadcast(response: EtherscanBroadcastResponse): Result<BroadcastResult> {
        Log.d("EthereumRepo", "Handling Sepolia broadcast response: $response")

        // First check if there's an error object (this is the actual error case)
        response.error?.let { error ->
            Log.e("EthereumRepo", "Broadcast error on Sepolia: ${error.message}")
            return Result.Success(
                BroadcastResult(
                    success = false,
                    error = error.message
                )
            )
        }

        // Check if result is present and looks like a transaction hash (success case)
        response.result?.let { result ->
            if (result.startsWith("0x") && result.length == TX_HASH_LENGTH) {
                Log.d("EthereumRepo", "Broadcast successful on Sepolia: $result")
                return Result.Success(
                    BroadcastResult(
                        success = true,
                        hash = result
                    )
                )
            } else {
                Log.e("EthereumRepo", "Invalid transaction hash format on Sepolia: $result")
                return Result.Success(
                    BroadcastResult(
                        success = false,
                        error = "Invalid response format"
                    )
                )
            }
        }

        // If we get here, something unexpected happened
        Log.e("EthereumRepo", "Unexpected broadcast response on Sepolia: $response")
        return Result.Success(
            BroadcastResult(
                success = false,
                error = response.message ?: "Unknown error"
            )
        )
    }

    /**
     * Simple cache for gas price to reduce API calls (per network)
     */
    class GasPriceCache {
        private var cachedPrice: GasPrice? = null
        private var cacheTime: Long = 0

        fun get(): GasPrice? {
            return if (System.currentTimeMillis() - cacheTime < GAS_PRICE_CACHE_TTL_MS) {
                cachedPrice
            } else {
                null
            }
        }

        fun update(price: GasPrice) {
            cachedPrice = price
            cacheTime = System.currentTimeMillis()
        }
    }

    /**
     * Simple cache for confirmation time to reduce API calls (per gas price)
     */
    class ConfirmationTimeCache(
        val seconds: Int,
        val timestamp: Long
    )

    companion object {
        // Ethereum constants
        private const val GAS_LIMIT_STANDARD = 21000L
        private const val WEI_PER_GWEI = 1_000_000_000L
        private const val WEI_PER_ETH = "1000000000000000000"
        private const val ETH_DECIMALS = 18

        // Price multipliers for different fee levels
        private val SLOW_PRICE_MULTIPLIER = BigDecimal("0.9")   // 10% cheaper than safe
        private val FAST_PRICE_MULTIPLIER = BigDecimal("1.2")   // 20% more expensive than fast

        // Sepolia specific values
        private const val SEPOLIA_GAS_PRICE = "0.1"  // 0.1 Gwei for Sepolia
        private const val SEPOLIA_ESTIMATED_TIME = 15 // 15 seconds for Sepolia

        // Default estimated time (fallback if API fails)
        private const val DEFAULT_ESTIMATED_TIME = 300 // 5 minutes

        // Gas price cache TTL (30 seconds)
        private const val GAS_PRICE_CACHE_TTL_MS = 30000L

        // Retry delay
        private const val RETRY_DELAY_MS = 500L

        // Transaction hash validation
        private const val TX_HASH_LENGTH = 66
        private val TX_HASH_REGEX = Regex("^0x[a-fA-F0-9]{64}$")

        // Cache TTL for confirmation times (5 minutes since gas prices change slowly)
        private const val CONFIRMATION_TIME_CACHE_TTL_MS = 300000L // 5 minutes
    }
}