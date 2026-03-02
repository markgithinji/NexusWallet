package com.example.nexuswallet.feature.coin.ethereum.data

//
//@Singleton
//class EthereumBlockchainRepositoryImpl @Inject constructor(
//    private val etherscanApi: EtherscanApiService
//) : EthereumBlockchainRepository {
//
//    private val gasPriceCache = mutableMapOf<String, CachedGasPrice>()
//    private val confirmationTimeCache = mutableMapOf<String, CachedConfirmationTime>()
//
//    /**
//     * Get Ethereum balance for an address
//     */
//    override suspend fun getEthereumBalance(
//        address: String,
//        network: EthereumNetwork
//    ): Result<BigDecimal> = SafeApiCall.make {
//        val chainId = network.chainId
//        val apiKey = BuildConfig.ETHERSCAN_API_KEY
//
//        val response = etherscanApi.getEthereumBalance(
//            chainId = chainId,
//            address = address,
//            apiKey = apiKey
//        )
//
//        if (response.status == "1") {
//            val wei = BigDecimal(response.result)
//            val eth = wei.divide(BigDecimal(WEI_PER_ETH), ETH_DECIMALS, RoundingMode.HALF_UP)
//            eth
//        } else {
//            throw Exception("API error: ${response.message}")
//        }
//    }
//
//    /**
//     * Get Ethereum transactions for an address
//     */
//    override suspend fun getEthereumTransactions(
//        address: String,
//        network: EthereumNetwork,
//        walletId: String
//    ): Result<List<EthereumTransaction>> = SafeApiCall.make {
//        val chainId = network.chainId
//        val apiKey = BuildConfig.ETHERSCAN_API_KEY
//
//        val response = etherscanApi.getEthereumTransactions(
//            chainId = chainId,
//            address = address,
//            apiKey = apiKey
//        )
//
//        if (response.status == "1") {
//            val transactions = response.result.toDomain(
//                walletId = walletId,
//                network = network,
//                walletAddress = address
//            )
//            transactions
//        } else {
//            throw Exception("API error: ${response.message}")
//        }
//    }
//
//    /**
//     * Get current gas price from Etherscan - works for all supported chains
//     * Uses gasoracle for Mainnet, proxy eth_gasPrice for other networks
//     */
//    override suspend fun getCurrentGasPrice(
//        network: EthereumNetwork
//    ): Result<GasPrice> = SafeApiCall.make {
//        // Check cache for this specific network
//        val cacheKey = network.chainId
//        gasPriceCache[cacheKey]?.let { cached ->
//            if (System.currentTimeMillis() - cached.timestamp < GAS_PRICE_CACHE_TTL_MS) {
//                return@make cached.price
//            } else {
//                gasPriceCache.remove(cacheKey)
//            }
//        }
//
//        val chainId = network.chainId
//        val apiKey = BuildConfig.ETHERSCAN_API_KEY
//
//        val gasPrice = when (network) {
//            is EthereumNetwork.Mainnet -> {
//                // Mainnet uses gasoracle for Safe/Propose/Fast prices
//                val response = etherscanApi.getGasPrice(
//                    chainId = chainId,
//                    apiKey = apiKey
//                )
//
//                if (response.status == "1") {
//                    GasPrice(
//                        safe = response.result.SafeGasPrice,
//                        propose = response.result.ProposeGasPrice,
//                        fast = response.result.FastGasPrice,
//                        lastBlock = response.result.lastBlock,
//                        baseFee = response.result.suggestBaseFee
//                    )
//                } else {
//                    throw Exception("Gas price API error: ${response.message}")
//                }
//            }
//
//            is EthereumNetwork.Sepolia -> {
//                // Sepolia uses proxy eth_gasPrice endpoint
//                val response = etherscanApi.getGasPriceProxy(
//                    chainId = chainId,
//                    apiKey = apiKey
//                )
//
//                // Parse hex result to wei
//                val gasPriceWei = org.web3j.utils.Numeric.toBigInt(response.result)
//
//                // Convert wei to Gwei with HIGHER PRECISION (6 decimal places)
//                val gasPriceGwei = gasPriceWei.toBigDecimal().divide(
//                    BigDecimal(WEI_PER_GWEI),
//                    6,
//                    RoundingMode.HALF_UP
//                )
//
//                val priceStr = gasPriceGwei.toString()
//
//                // For Sepolia, we use the same price for all levels with multipliers
//                GasPrice(
//                    safe = (gasPriceGwei * SLOW_PRICE_MULTIPLIER).setScale(6, RoundingMode.HALF_UP).toString(),
//                    propose = priceStr,
//                    fast = (gasPriceGwei * FAST_PRICE_MULTIPLIER).setScale(6, RoundingMode.HALF_UP).toString(),
//                    lastBlock = null,
//                    baseFee = null
//                )
//            }
//        }
//
//        // Update cache for this network
//        gasPriceCache[cacheKey] = CachedGasPrice(gasPrice, System.currentTimeMillis())
//
//        gasPrice
//    }
//
//    /**
//     * Get dynamic fee estimate based on current gas prices
//     */
//    override suspend fun getDynamicFeeEstimate(
//        feeLevel: FeeLevel,
//        network: EthereumNetwork
//    ): Result<EthereumFeeEstimate> {
//        // Get current gas price
//        val gasPriceResult = getCurrentGasPrice(network)
//
//        return when (gasPriceResult) {
//            is Result.Success -> {
//                val gasPrice = gasPriceResult.data
//
//                // Parse gas prices as BigDecimals
//                val safeGwei = gasPrice.safe.toBigDecimal()
//                val proposeGwei = gasPrice.propose.toBigDecimal()
//                val fastGwei = gasPrice.fast.toBigDecimal()
//
//                // Get final gas price based on fee level
//                val selectedGwei = when (feeLevel) {
//                    FeeLevel.SLOW -> safeGwei
//                    FeeLevel.NORMAL -> proposeGwei
//                    FeeLevel.FAST -> fastGwei
//                }
//
//                val gasPriceGwei = formatGasPrice(selectedGwei)
//
//                // Calculate wei values
//                val gasPriceWei = (BigDecimal(gasPriceGwei) * BigDecimal(WEI_PER_GWEI)).toBigInteger()
//                val totalFeeWei = gasPriceWei.multiply(BigInteger.valueOf(GAS_LIMIT_STANDARD))
//                val totalFeeEth = BigDecimal(totalFeeWei).divide(
//                    BigDecimal(WEI_PER_ETH),
//                    ETH_DECIMALS,
//                    RoundingMode.HALF_UP
//                ).toPlainString()
//
//                // Get real confirmation time estimate from Etherscan (Mainnet only)
//                val estimatedTimeResult = if (network is EthereumNetwork.Mainnet) {
//                    getConfirmationTimeEstimate(gasPriceWei.toString(), network)
//                } else {
//                    // For Sepolia, use reasonable estimates based on fee level
//                    val time = when (feeLevel) {
//                        FeeLevel.SLOW -> 120
//                        FeeLevel.NORMAL -> 60
//                        FeeLevel.FAST -> 30
//                    }
//                    Result.Success(time)
//                }
//
//                when (estimatedTimeResult) {
//                    is Result.Success -> {
//                        Result.Success(
//                            EthereumFeeEstimate(
//                                gasPriceGwei = gasPriceGwei,
//                                gasPriceWei = gasPriceWei.toString(),
//                                gasLimit = GAS_LIMIT_STANDARD,
//                                totalFeeWei = totalFeeWei.toString(),
//                                totalFeeEth = totalFeeEth,
//                                estimatedTime = estimatedTimeResult.data,
//                                priority = feeLevel,
//                                baseFee = gasPrice.baseFee,
//                                isEIP1559 = gasPrice.baseFee != null
//                            )
//                        )
//                    }
//
//                    is Result.Error -> {
//                        Result.Error("Failed to get confirmation time: ${estimatedTimeResult.message}")
//                    }
//
//                    Result.Loading -> {
//                        Result.Error("Confirmation time request timed out")
//                    }
//                }
//            }
//
//            is Result.Error -> {
//                Result.Error("Failed to get gas price: ${gasPriceResult.message}")
//            }
//
//            Result.Loading -> {
//                Result.Error("Gas price request timed out")
//            }
//        }
//    }
//
//    /**
//     * Get standard fee estimate
//     */
//    override suspend fun getFeeEstimate(
//        feeLevel: FeeLevel,
//        network: EthereumNetwork
//    ): Result<EthereumFeeEstimate> {
//        return getDynamicFeeEstimate(feeLevel, network)
//    }
//
//    /**
//     * Get real confirmation time estimate from Etherscan with caching
//     * Returns Result.Error if API fails
//     */
//    private suspend fun getConfirmationTimeEstimate(
//        gasPriceWei: String,
//        network: EthereumNetwork
//    ): Result<Int> = SafeApiCall.make {
//        // Create cache key that includes both gas price and network
//        val cacheKey = "${network.chainId}_$gasPriceWei"
//
//        // Check cache first
//        confirmationTimeCache[cacheKey]?.let { cached ->
//            if (System.currentTimeMillis() - cached.timestamp < CONFIRMATION_TIME_CACHE_TTL_MS) {
//                return@make cached.seconds
//            } else {
//                confirmationTimeCache.remove(cacheKey)
//            }
//        }
//
//        val chainId = network.chainId
//        val apiKey = BuildConfig.ETHERSCAN_API_KEY
//
//        val response = etherscanApi.getConfirmationTimeEstimate(
//            chainId = chainId,
//            gasPriceWei = gasPriceWei,
//            apiKey = apiKey
//        )
//
//        if (response.status == "1") {
//            val seconds = response.result.toIntOrNull()
//            if (seconds != null) {
//                // Cache the result
//                confirmationTimeCache[cacheKey] = CachedConfirmationTime(seconds, System.currentTimeMillis())
//
//                seconds
//            } else {
//                throw Exception("Invalid confirmation time format: ${response.result}")
//            }
//        } else {
//            throw Exception("Confirmation time API error: ${response.message}")
//        }
//    }
//
//    /**
//     * Get Ethereum nonce for an address
//     */
//    override suspend fun getEthereumNonce(
//        address: String,
//        network: EthereumNetwork
//    ): Result<Int> = SafeApiCall.make {
//        val chainId = network.chainId
//        val apiKey = BuildConfig.ETHERSCAN_API_KEY
//
//        val response = etherscanApi.getTransactionCount(
//            chainId = chainId,
//            address = address,
//            tag = "pending",
//            apiKey = apiKey
//        )
//
//        // Handle the response based on network
//        val nonce = when (network) {
//            is EthereumNetwork.Mainnet -> {
//                if (response.result.isNotEmpty() && response.result != "0x") {
//                    val cleanHex = if (response.result.startsWith("0x")) {
//                        response.result
//                    } else {
//                        "0x${response.result}"
//                    }
//                    org.web3j.utils.Numeric.toBigInt(cleanHex).toInt()
//                } else {
//                    0
//                }
//            }
//
//            is EthereumNetwork.Sepolia -> {
//                org.web3j.utils.Numeric.toBigInt(response.result).toInt()
//            }
//        }
//
//        nonce
//    }
//
//    /**
//     * Broadcast Ethereum transaction
//     */
//    override suspend fun broadcastEthereumTransaction(
//        rawTx: String,
//        network: EthereumNetwork
//    ): Result<BroadcastResult> = SafeApiCall.make {
//        val chainId = network.chainId
//        val apiKey = BuildConfig.ETHERSCAN_API_KEY
//
//        delay(RETRY_DELAY_MS)
//
//        val response = etherscanApi.broadcastTransaction(
//            chainId = chainId,
//            hex = rawTx,
//            apiKey = apiKey
//        )
//
//        when (network) {
//            is EthereumNetwork.Mainnet -> handleMainnetBroadcast(response)
//            is EthereumNetwork.Sepolia -> handleSepoliaBroadcast(response)
//        }
//    }
//
//    private fun handleMainnetBroadcast(response: EtherscanBroadcastResponse): BroadcastResult {
//        // First check if there's an error object
//        response.error?.let { error ->
//            throw Exception(error.message)
//        }
//
//        // Check if result is present and looks like a transaction hash
//        response.result?.let { result ->
//            when {
//                result.startsWith("0x") && result.length == TX_HASH_LENGTH -> {
//                    return BroadcastResult(
//                        success = true,
//                        hash = result
//                    )
//                }
//
//                result.contains("nonce") -> {
//                    throw Exception("Nonce error: $result")
//                }
//
//                result.contains("insufficient funds") || result.contains("balance") -> {
//                    throw Exception("Insufficient balance: $result")
//                }
//
//                result.contains("already known") -> {
//                    val hashPattern = TX_HASH_REGEX
//                    val hash = hashPattern.find(result)?.value
//                    if (hash != null) {
//                        return BroadcastResult(
//                            success = true,
//                            hash = hash
//                        )
//                    } else {
//                        throw Exception(result)
//                    }
//                }
//            }
//        }
//
//        // If we get here, something unexpected happened
//        throw Exception(response.message ?: "Unknown error")
//    }
//
//    private fun handleSepoliaBroadcast(response: EtherscanBroadcastResponse): BroadcastResult {
//        // First check if there's an error object
//        response.error?.let { error ->
//            throw Exception(error.message)
//        }
//
//        // Check if result is present and looks like a transaction hash
//        response.result?.let { result ->
//            if (result.startsWith("0x") && result.length == TX_HASH_LENGTH) {
//                return BroadcastResult(
//                    success = true,
//                    hash = result
//                )
//            } else {
//                throw Exception("Invalid response format")
//            }
//        }
//
//        // If we get here, something unexpected happened
//        throw Exception(response.message ?: "Unknown error")
//    }
//
//    /**
//     * Format gas price for display - keep decimals for small values
//     */
//    private fun formatGasPrice(price: BigDecimal): String {
//        return if (price < BigDecimal.ONE) {
//            String.format("%.6f", price)
//        } else {
//            String.format("%.2f", price)
//        }
//    }
//
//    private data class CachedGasPrice(
//        val price: GasPrice,
//        val timestamp: Long
//    )
//
//    private data class CachedConfirmationTime(
//        val seconds: Int,
//        val timestamp: Long
//    )
//
//    companion object {
//        // Ethereum constants
//        private const val GAS_LIMIT_STANDARD = 21000L
//        private const val WEI_PER_GWEI = 1_000_000_000L
//        private const val WEI_PER_ETH = "1000000000000000000"
//        private const val ETH_DECIMALS = 18
//
//        // Price multipliers for different fee levels
//        private val SLOW_PRICE_MULTIPLIER = BigDecimal("0.9")
//        private val FAST_PRICE_MULTIPLIER = BigDecimal("1.2")
//
//        // Gas price cache TTL (30 seconds)
//        private const val GAS_PRICE_CACHE_TTL_MS = 30000L
//
//        // Retry delay
//        private const val RETRY_DELAY_MS = 500L
//
//        // Transaction hash validation
//        private const val TX_HASH_LENGTH = 66
//        private val TX_HASH_REGEX = Regex("^0x[a-fA-F0-9]{64}$")
//
//        // Cache TTL for confirmation times (5 minutes)
//        private const val CONFIRMATION_TIME_CACHE_TTL_MS = 300000L
//    }
//}