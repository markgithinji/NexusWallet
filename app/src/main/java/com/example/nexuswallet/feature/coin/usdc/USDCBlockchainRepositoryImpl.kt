package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SafeApiCall
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
import com.example.nexuswallet.feature.coin.ethereum.EtherscanApiService
import com.example.nexuswallet.feature.coin.ethereum.GasPrice
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.domain.USDCFeeEstimate
import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransaction
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
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
class USDCBlockchainRepositoryImpl @Inject constructor(
    private val etherscanApi: EtherscanApiService,
    private val web3jFactory: Web3jFactory
) : USDCBlockchainRepository {

    // Gas price cache - stores gas price per network with timestamp
    private val gasPriceCache = mutableMapOf<String, CachedGasPrice>()

    /**
     * Get USDC balance for an address
     */
    override suspend fun getUSDCBalance(
        address: String,
        network: EthereumNetwork
    ): Result<USDCBalance> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val usdcBalance = getUSDCBalanceViaWeb3j(address, network)
            usdcBalance
        }
    }

    private suspend fun getUSDCBalanceViaWeb3j(
        address: String,
        network: EthereumNetwork
    ): USDCBalance = withContext(Dispatchers.IO) {
        val result = SafeApiCall.make<USDCBalance> {
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
                return@make createUSDCBalance(
                    balanceRaw = "0",
                    balanceDecimal = BigDecimal.ZERO,
                    address = address,
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

            createUSDCBalance(
                balanceRaw = balanceRaw,
                balanceDecimal = balanceDecimal,
                address = address
            )
        }

        when (result) {
            is Result.Success -> result.data
            is Result.Error -> throw Exception(result.message, result.throwable)
            Result.Loading -> throw Exception("Unexpected loading state")
        }
    }

    // Helper to create USDCBalance
    private fun createUSDCBalance(
        balanceRaw: String,
        balanceDecimal: BigDecimal,
        address: String
    ): USDCBalance {
        return USDCBalance(
            address = address,
            amount = balanceRaw,
            amountDecimal = balanceDecimal.toPlainString(),
            usdValue = balanceDecimal.toDouble() // USDC is pegged 1:1 with USD
        )
    }

    /**
     * Get USDC fee estimate based on priority
     */
    override suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        network: EthereumNetwork
    ): Result<USDCFeeEstimate> = withContext(Dispatchers.IO) {
        try {
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
                    Result.Error(gasPriceResult.message, gasPriceResult.throwable)
                }

                Result.Loading -> Result.Error("Gas price request timed out")
            }
        } catch (e: Exception) {
            Result.Error("Failed to get fee estimate: ${e.message}", e)
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
                return Result.Success(cached.price)
            } else {
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
            }
        }
    }

    /**
     * Get gas price using web3j
     */
    private suspend fun getGasPriceViaWeb3j(
        network: EthereumNetwork
    ): Result<GasPrice> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val web3j = web3jFactory.create(network)
            val gasPrice = web3j.ethGasPrice().send()

            if (gasPrice.hasError()) {
                throw Exception("Gas price error: ${gasPrice.error?.message}")
            }

            val gasPriceWei = gasPrice.gasPrice

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

            GasPrice(
                safe = slowPrice,
                propose = priceStr,
                fast = fastPrice,
                lastBlock = null,
                baseFee = null
            )
        }
    }

    override suspend fun createAndSignUSDCTransfer(
        fromAddress: String,
        fromPrivateKey: String,
        toAddress: String,
        amount: BigDecimal,
        gasPriceWei: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        network: EthereumNetwork
    ): Result<Triple<RawTransaction, String, String>> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            // Input validation
            if (!validateAddress(fromAddress) || !validateAddress(toAddress)) {
                throw Exception("Invalid address format")
            }
            if (!validateAmount(amount)) {
                throw Exception("Invalid amount")
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
                throw Exception("Invalid private key format")
            }

            val signedMessage =
                TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
            val signedHex = Numeric.toHexString(signedMessage)
            val txHash = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(signedHex)))

            Triple(rawTransaction, signedHex, txHash)
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
            BigInteger.valueOf(DEFAULT_GAS_LIMIT)
        }
    }

    private suspend fun estimateGasForTokenTransfer(
        fromAddress: String,
        toAddress: String,
        amount: BigInteger,
        network: EthereumNetwork
    ): BigInteger = withContext(Dispatchers.IO) {
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
                return@withContext BigInteger.valueOf(DEFAULT_GAS_LIMIT)
            }

            val estimatedGas = response.amountUsed
            estimatedGas

        } catch (e: Exception) {
            BigInteger.valueOf(DEFAULT_GAS_LIMIT)
        }
    }

    /**
     * Broadcast USDC transaction using web3j
     */
    override suspend fun broadcastUSDCTransaction(
        signedHex: String,
        network: EthereumNetwork
    ): Result<BroadcastResult> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val web3j = web3jFactory.create(network)
            val response = web3j.ethSendRawTransaction(signedHex).send()

            if (response.hasError()) {
                val error = response.error.message

                // Parse common error messages
                when {
                    error.contains("insufficient funds") ->
                        throw Exception("Insufficient ETH for gas fees")
                    error.contains("nonce") ->
                        throw Exception("Nonce error: $error")
                    error.contains("already known") -> {
                        // Transaction already in mempool - this is actually a success
                        val hash = extractHashFromError(error) ?: "unknown"
                        return@make BroadcastResult(
                            success = true,
                            hash = hash,
                            error = null
                        )
                    }
                    else ->
                        throw Exception("Broadcast failed: $error")
                }
            }

            val txHash = response.transactionHash

            BroadcastResult(
                success = true,
                hash = txHash
            )
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

    override suspend fun getNonce(
        address: String,
        network: EthereumNetwork
    ): Result<BigInteger> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val web3j = web3jFactory.create(network)
            val response = web3j.ethGetTransactionCount(
                address,
                DefaultBlockParameterName.PENDING
            ).send()

            if (response.hasError()) {
                throw Exception(response.error?.message ?: "Failed to get nonce")
            } else {
                val nonce = response.transactionCount
                nonce
            }
        }
    }

    override suspend fun getUSDCTransactionHistory(
        walletId: String,
        address: String,
        network: EthereumNetwork
    ): Result<List<USDCTransaction>> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val chainId = network.chainId
            val usdcAddress = network.usdcContractAddress
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getTokenTransfers(
                chainId = chainId,
                address = address,
                contractAddress = usdcAddress,
                apiKey = apiKey
            )

            if (response.status == "1") {
                response.result.map { tx ->
                    tx.toDomain(
                        walletId = walletId,
                        network = network,
                        walletAddress = address
                    )
                }
            } else {
                throw Exception(response.message)
            }
        }
    }

    private fun validateAddress(address: String): Boolean {
        return address.startsWith("0x") && address.length == 42
    }

    private fun validateAmount(amount: BigDecimal): Boolean {
        return amount > BigDecimal.ZERO
    }

    private data class CachedGasPrice(
        val price: GasPrice,
        val timestamp: Long
    )

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