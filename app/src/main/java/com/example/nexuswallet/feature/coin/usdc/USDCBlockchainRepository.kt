package com.example.nexuswallet.feature.coin.usdc

import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.coin.CachedGasPrice
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EtherscanApiService
import com.example.nexuswallet.feature.coin.ethereum.GasPrice
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.coin.usdc.domain.USDCFeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class USDCBlockchainRepository @Inject constructor(
    private val etherscanApi: EtherscanApiService,
    private val web3jFactory: Web3jFactory
) {
    // Gas price cache - stores gas price per network with timestamp
    private val gasPriceCache = mutableMapOf<String, CachedGasPrice>()

    suspend fun getUSDCBalance(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<USDCBalance> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDCRepo", "Fetching USDC balance for $address on ${network.displayName}")

                val usdcBalance = getUSDCBalanceViaWeb3j(address, network)

                val result = USDCBalance(
                    address = address,
                    amount = usdcBalance.balance,
                    amountDecimal = usdcBalance.balanceDecimal,
                    usdValue = usdcBalance.usdValue
                )

                Log.d("USDCRepo", "USDC balance for $address: ${result.amountDecimal} USDC")
                Result.Success(result)

            } catch (e: Exception) {
                Log.e("USDCRepo", "Failed to get USDC balance: ${e.message}", e)
                Result.Error("Failed to get USDC balance: ${e.message}", e)
            }
        }
    }

    private suspend fun getUSDCBalanceViaWeb3j(
        address: String,
        network: EthereumNetwork
    ): TokenBalance {
        return withContext(Dispatchers.IO) {
            try {
                val web3j = web3jFactory.create(network)
                val usdcAddress = network.usdcContractAddress

                val function = Function(
                    "balanceOf",
                    listOf(Address(address)),
                    listOf(object : TypeReference<Uint256>() {})
                )

                val encodedFunction = FunctionEncoder.encode(function)

                val transaction =
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                        address,
                        usdcAddress,
                        encodedFunction
                    )

                val response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()

                if (response.hasError()) {
                    throw Exception("Web3j error: ${response.error?.message}")
                }

                val result = response.result

                if (result == "0x") {
                    return@withContext createTokenBalance(
                        balanceRaw = "0",
                        balanceDecimal = BigDecimal.ZERO,
                        network = network
                    )
                }

                val decoded = FunctionReturnDecoder.decode(
                    result,
                    function.outputParameters
                )

                if (decoded.isEmpty()) {
                    throw Exception("Failed to decode Web3j response")
                }

                val balanceUint = decoded[0] as Uint256
                val balanceRaw = balanceUint.value.toString()
                val balanceWei = balanceUint.value.toBigDecimal()

                val balanceDecimal = balanceWei.divide(
                    BigDecimal(USDC_DECIMALS_DIVISOR),
                    USDC_DECIMALS,
                    RoundingMode.HALF_UP
                )

                createTokenBalance(
                    balanceRaw = balanceRaw,
                    balanceDecimal = balanceDecimal,
                    network = network
                )

            } catch (e: Exception) {
                Log.e("USDCRepo", "Web3j balance fetch failed: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Get USDC fee estimate based on priority
     */
    suspend fun getFeeEstimate(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<USDCFeeEstimate> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(
                    "USDCRepo",
                    "=== getFeeEstimate called with feeLevel: $feeLevel on ${network.displayName} ==="
                )

                val gasPriceResult = getEthereumGasPrice(network)

                when (gasPriceResult) {
                    is Result.Success -> {
                        val ethGasPrice = gasPriceResult.data

                        val (gasPriceGwei, estimatedTime) = when (feeLevel) {
                            FeeLevel.SLOW -> ethGasPrice.safe to SLOW_ESTIMATE_TIME
                            FeeLevel.NORMAL -> ethGasPrice.propose to NORMAL_ESTIMATE_TIME
                            FeeLevel.FAST -> ethGasPrice.fast to FAST_ESTIMATE_TIME
                        }

                        val gasPriceWei =
                            (BigDecimal(gasPriceGwei) * BigDecimal(GWEI_TO_WEI)).toBigInteger()
                        val gasLimit = BigInteger.valueOf(DEFAULT_GAS_LIMIT)
                        val totalFeeWei = gasPriceWei.multiply(gasLimit)

                        val totalFeeEth = BigDecimal(totalFeeWei).divide(
                            BigDecimal(WEI_TO_ETH),
                            ETH_DECIMALS,
                            RoundingMode.HALF_UP
                        ).toPlainString()

                        Log.d(
                            "USDCRepo",
                            "Fee in ETH: $totalFeeEth ETH, Estimated time: ${estimatedTime}s"
                        )

                        Result.Success(
                            USDCFeeEstimate(
                                gasPriceGwei = gasPriceGwei,
                                gasPriceWei = gasPriceWei.toString(),
                                gasLimit = DEFAULT_GAS_LIMIT,
                                totalFeeWei = totalFeeWei.toString(),
                                totalFeeEth = totalFeeEth,
                                estimatedTime = estimatedTime,
                                priority = feeLevel,
                                contractAddress = network.usdcContractAddress,
                                tokenDecimals = USDC_DECIMALS
                            )
                        )
                    }

                    is Result.Error -> {
                        Log.e("USDCRepo", "Failed to get gas price: ${gasPriceResult.message}")
                        Result.Error(gasPriceResult.message, gasPriceResult.throwable)
                    }

                    Result.Loading -> Result.Error("Gas price request timed out")
                }
            } catch (e: Exception) {
                Log.e("USDCRepo", "Failed to get fee estimate: ${e.message}", e)
                Result.Error("Failed to get fee estimate: ${e.message}", e)
            }
        }
    }

    /**
     * Get Ethereum gas price using web3j with caching
     */
    private suspend fun getEthereumGasPrice(
        network: EthereumNetwork
    ): Result<GasPrice> {
        val cacheKey = network.chainId

        // Check cache first
        gasPriceCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < GAS_PRICE_CACHE_TTL_MS) {
                Log.d("USDCRepo", "Using cached gas price for ${network.displayName}")
                return Result.Success(cached.price)
            } else {
                Log.d("USDCRepo", "Cached gas price expired for ${network.displayName}")
                gasPriceCache.remove(cacheKey)
            }
        }

        // Fetch fresh gas price
        return getGasPriceViaWeb3j(network).also { result ->
            if (result is Result.Success) {
                gasPriceCache[cacheKey] = CachedGasPrice(
                    price = result.data,
                    timestamp = System.currentTimeMillis()
                )
                Log.d("USDCRepo", "Cached fresh gas price for ${network.displayName}")
            }
        }
    }

    /**
     * Get gas price using web3j
     */
    private suspend fun getGasPriceViaWeb3j(
        network: EthereumNetwork
    ): Result<GasPrice> {
        return withContext(Dispatchers.IO) {
            try {
                val web3j = web3jFactory.create(network)
                val gasPrice = web3j.ethGasPrice().send()

                if (gasPrice.hasError()) {
                    return@withContext Result.Error("Gas price error: ${gasPrice.error?.message}")
                }

                val gasPriceWei = gasPrice.gasPrice
                Log.d("USDCRepo", "Raw gas price wei: $gasPriceWei")

                // Convert wei to Gwei with 6 decimal precision
                val gasPriceGwei = gasPriceWei.toBigDecimal().divide(
                    BigDecimal(GWEI_TO_WEI),
                    6,
                    RoundingMode.HALF_UP
                )

                val priceStr = gasPriceGwei.toString()

                // Apply multipliers with same precision
                val slowPrice = (gasPriceGwei * SLOW_PRICE_MULTIPLIER).setScale(6, RoundingMode.HALF_UP).toString()
                val fastPrice = (gasPriceGwei * FAST_PRICE_MULTIPLIER).setScale(6, RoundingMode.HALF_UP).toString()

                Log.d("USDCRepo", "Web3j gas price on ${network.displayName}: $priceStr Gwei")
                Log.d("USDCRepo", "Using gas prices - Safe: $slowPrice, Propose: $priceStr, Fast: $fastPrice")

                Result.Success(
                    GasPrice(
                        safe = slowPrice,
                        propose = priceStr,
                        fast = fastPrice,
                        lastBlock = null,
                        baseFee = null
                    )
                )

            } catch (e: Exception) {
                Log.e("USDCRepo", "Failed to get gas price via web3j: ${e.message}", e)
                Result.Error("Failed to get gas price: ${e.message}", e)
            }
        }
    }

    suspend fun createAndSignUSDCTransfer(
        fromAddress: String,
        fromPrivateKey: String,
        toAddress: String,
        amount: BigDecimal,
        gasPriceWei: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<Triple<RawTransaction, String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDCRepo", "Creating USDC transfer on ${network.displayName}")
                Log.d("USDCRepo", "From: $fromAddress, To: $toAddress, Amount: $amount USDC")

                // Input validation
                if (!validateAddress(fromAddress) || !validateAddress(toAddress)) {
                    return@withContext Result.Error("Invalid address format")
                }
                if (!validateAmount(amount)) {
                    return@withContext Result.Error("Invalid amount")
                }

                val usdcAddress = network.usdcContractAddress
                val amountInUnits =
                    amount.multiply(BigDecimal(USDC_DECIMALS_DIVISOR)).toBigInteger()

                val estimatedGas = getDynamicGasLimit(
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    amount = amountInUnits,
                    network = network
                )

                val function = Function(
                    "transfer",
                    listOf(
                        Address(toAddress),
                        Uint256(amountInUnits)
                    ),
                    listOf(object : TypeReference<Bool>() {})
                )

                val encodedFunction = FunctionEncoder.encode(function)

                val rawTransaction = RawTransaction.createTransaction(
                    nonce,
                    gasPriceWei,
                    estimatedGas,
                    usdcAddress,
                    encodedFunction
                )

                val credentials = try {
                    Credentials.create(fromPrivateKey)
                } catch (e: Exception) {
                    Log.e("USDCRepo", "Invalid private key format", e)
                    return@withContext Result.Error(
                        message = "Invalid private key format",
                        throwable = e
                    )
                }

                val signedMessage =
                    TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
                val signedHex = Numeric.toHexString(signedMessage)
                val txHash = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(signedHex)))

                Log.d("USDCRepo", "USDC transfer created, txHash: $txHash")
                Result.Success(Triple(rawTransaction, signedHex, txHash))

            } catch (e: Exception) {
                Log.e("USDCRepo", "Failed to create USDC transfer: ${e.message}", e)
                Result.Error(
                    message = "Failed to create USDC transfer: ${e.message}",
                    throwable = e
                )
            }
        }
    }

    private suspend fun getDynamicGasLimit(
        fromAddress: String,
        toAddress: String,
        amount: BigInteger,
        network: EthereumNetwork
    ): BigInteger {
        return try {
            val estimated = estimateGasForTokenTransfer(fromAddress, toAddress, amount, network)
            // Add buffer for safety
            estimated + (estimated * BigInteger.valueOf(GAS_LIMIT_BUFFER_PERCENT) / BigInteger.valueOf(
                100
            ))
        } catch (e: Exception) {
            Log.w("USDCRepo", "Gas estimation failed, using default: ${e.message}")
            BigInteger.valueOf(DEFAULT_GAS_LIMIT)
        }
    }

    private suspend fun estimateGasForTokenTransfer(
        fromAddress: String,
        toAddress: String,
        amount: BigInteger,
        network: EthereumNetwork
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            try {
                val web3j = web3jFactory.create(network)
                val usdcAddress = network.usdcContractAddress

                val function = Function(
                    "transfer",
                    listOf(Address(toAddress), Uint256(amount)),
                    emptyList()
                )

                val encodedFunction = FunctionEncoder.encode(function)

                val transaction =
                    org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                        fromAddress,
                        null, null, null,
                        usdcAddress,
                        encodedFunction
                    )

                val response = web3j.ethEstimateGas(transaction).send()

                if (response.hasError()) {
                    Log.w(
                        "USDCRepo",
                        "Gas estimation failed, using default: ${response.error?.message}"
                    )
                    return@withContext BigInteger.valueOf(DEFAULT_GAS_LIMIT)
                }

                val estimatedGas = response.amountUsed
                Log.d("USDCRepo", "Estimated gas: $estimatedGas")
                estimatedGas

            } catch (e: Exception) {
                Log.w("USDCRepo", "Gas estimation error, using default: ${e.message}")
                BigInteger.valueOf(DEFAULT_GAS_LIMIT)
            }
        }
    }

    /**
     * Broadcast USDC transaction using web3j
     */
    suspend fun broadcastUSDCTransaction(
        signedHex: String,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<BroadcastResult> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDCRepo", "Broadcasting USDC transaction on ${network.displayName} via web3j")

                val web3j = web3jFactory.create(network)
                val response = web3j.ethSendRawTransaction(signedHex).send()

                if (response.hasError()) {
                    val error = response.error.message
                    Log.e("USDCRepo", "Web3j broadcast error: $error")

                    // Parse common error messages
                    return@withContext when {
                        error.contains("insufficient funds") ->
                            Result.Error("Insufficient ETH for gas fees", RuntimeException(error))
                        error.contains("nonce") ->
                            Result.Error("Nonce error: $error", RuntimeException(error))
                        error.contains("already known") ->
                            // Transaction already in mempool - this is actually a success
                            Result.Success(
                                BroadcastResult(
                                    success = true,
                                    hash = extractHashFromError(error) ?: "unknown",
                                    error = null
                                )
                            )
                        else ->
                            Result.Error("Broadcast failed: $error", RuntimeException(error))
                    }
                }

                val txHash = response.transactionHash
                Log.d("USDCRepo", "Web3j broadcast successful: $txHash")

                Result.Success(
                    BroadcastResult(
                        success = true,
                        hash = txHash
                    )
                )

            } catch (e: Exception) {
                Log.e("USDCRepo", "Web3j broadcast error: ${e.message}", e)
                Result.Error(
                    message = e.message ?: "Broadcast failed",
                    throwable = e
                )
            }
        }
    }

    /**
     * Helper to extract transaction hash from error message
     * Sometimes "already known" errors contain the hash
     */
    private fun extractHashFromError(error: String): String? {
        val hashPattern = Regex("0x[a-fA-F0-9]{64}")
        return hashPattern.find(error)?.value
    }

    suspend fun getNonce(
        address: String,
        network: EthereumNetwork
    ): Result<BigInteger> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDCRepo", "Fetching nonce for $address on ${network.displayName}")

                val web3j = web3jFactory.create(network)
                val response = web3j.ethGetTransactionCount(
                    address,
                    DefaultBlockParameterName.PENDING
                ).send()

                if (response.hasError()) {
                    Log.e("USDCRepo", "Failed to get nonce: ${response.error?.message}")
                    Result.Error(
                        message = "Failed to get nonce: ${response.error?.message}",
                        throwable = RuntimeException(response.error?.message)
                    )
                } else {
                    val nonce = response.transactionCount
                    Log.d("USDCRepo", "Nonce for $address: $nonce")
                    Result.Success(nonce)
                }
            } catch (e: Exception) {
                Log.e("USDCRepo", "Network error getting nonce: ${e.message}", e)
                Result.Error(
                    message = "Network error getting nonce: ${e.message}",
                    throwable = e
                )
            }
        }
    }

    private fun createTokenBalance(
        balanceRaw: String,
        balanceDecimal: BigDecimal,
        network: EthereumNetwork
    ): TokenBalance {
        return TokenBalance(
            tokenId = "usdc_${network::class.simpleName?.lowercase() ?: "unknown"}",
            symbol = "USDC",
            name = "USD Coin",
            contractAddress = network.usdcContractAddress,
            balance = balanceRaw,
            balanceDecimal = balanceDecimal.toPlainString(),
            usdPrice = 1.0,
            usdValue = balanceDecimal.toDouble(),
            decimals = USDC_DECIMALS,
            chain = if (network.isTestnet) ChainType.ETHEREUM_SEPOLIA else ChainType.ETHEREUM
        )
    }

    suspend fun getUSDCTransactionHistory(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<List<TokenTransaction>> = withContext(Dispatchers.IO) {
        try {
            val chainId = network.chainId
            val usdcAddress = network.usdcContractAddress
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            Log.d(
                "USDCRepo",
                "=== Fetching USDC transaction history for $address on ${network.displayName} ==="
            )

            val response = etherscanApi.getTokenTransfers(
                chainId = chainId,
                address = address,
                contractAddress = usdcAddress,
                apiKey = apiKey
            )

            if (response.status == "1") {
                val transactions = response.result
                Log.d("USDCRepo", "Found ${transactions.size} USDC transactions")

                transactions.take(3).forEachIndexed { i, tx ->
                    Log.d("USDCRepo", "Tx $i: ${tx.hash.take(8)}...")
                    Log.d("USDCRepo", "  from: ${tx.from.take(8)}...")
                    Log.d("USDCRepo", "  to: ${tx.to.take(8)}...")
                    Log.d("USDCRepo", "  value: ${tx.value}")
                    Log.d("USDCRepo", "  timestamp: ${tx.timeStamp}")
                }

                Result.Success(transactions)
            } else {
                Log.e("USDCRepo", "API error: ${response.message}")
                Result.Error(response.message ?: "Failed to get USDC transactions")
            }

        } catch (e: Exception) {
            Log.e("USDCRepo", "Failed to get USDC transactions: ${e.message}", e)
            Result.Error("Failed to get USDC transactions: ${e.message}", e)
        }
    }

    private fun validateAddress(address: String): Boolean {
        return address.startsWith("0x") && address.length == 42
    }

    private fun validateAmount(amount: BigDecimal): Boolean {
        return amount > BigDecimal.ZERO
    }

    companion object {
        // USDC constants
        private const val USDC_DECIMALS = 6
        private const val USDC_DECIMALS_DIVISOR = "1000000"
        private const val DEFAULT_GAS_LIMIT = 65_000L

        // Fee estimate times (seconds)
        private const val SLOW_ESTIMATE_TIME = 900
        private const val NORMAL_ESTIMATE_TIME = 300
        private const val FAST_ESTIMATE_TIME = 60

        // Gas price constants
        private const val GWEI_TO_WEI = 1_000_000_000L
        private const val WEI_TO_ETH = "1000000000000000000"
        private const val ETH_DECIMALS = 18
        private const val GAS_LIMIT_BUFFER_PERCENT = 20L

        // Price multipliers for web3j gas prices
        private val SLOW_PRICE_MULTIPLIER = BigDecimal("0.9")
        private val FAST_PRICE_MULTIPLIER = BigDecimal("1.2")

        // Cache TTL (30 seconds)
        private const val GAS_PRICE_CACHE_TTL_MS = 30000L
    }
}