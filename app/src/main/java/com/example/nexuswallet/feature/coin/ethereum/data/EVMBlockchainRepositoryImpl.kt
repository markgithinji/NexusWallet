package com.example.nexuswallet.feature.coin.ethereum.data

import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.coin.BroadcastResult
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SafeApiCall
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EVMFeeEstimate
import com.example.nexuswallet.feature.coin.ethereum.EtherscanApiService
import com.example.nexuswallet.feature.coin.ethereum.GasPrice
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.ethereum.toNativeETHTransactionList
import com.example.nexuswallet.feature.coin.ethereum.toTokenTransactionList
import com.example.nexuswallet.feature.coin.usdc.Web3jFactory
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TokenType
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
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class EVMBlockchainRepositoryImpl @Inject constructor(
    private val etherscanApi: EtherscanApiService,
    private val web3jFactory: Web3jFactory
) : EVMBlockchainRepository {

    // Gas price cache - stores gas price per network with timestamp
    private val gasPriceCache = mutableMapOf<String, CachedGasPrice>()
    private val confirmationTimeCache = mutableMapOf<String, CachedConfirmationTime>()

    // ============ BALANCE METHODS ============

    override suspend fun getNativeBalance(
        address: String,
        network: EthereumNetwork
    ): Result<BigDecimal> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val web3j = web3jFactory.create(network)
            val wei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().balance
            BigDecimal(wei).divide(BigDecimal(WEI_PER_ETH), ETH_DECIMALS, RoundingMode.HALF_UP)
        }
    }

    override suspend fun getTokenBalance(
        address: String,
        tokenContract: String,
        tokenDecimals: Int,
        network: EthereumNetwork
    ): Result<BigDecimal> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val web3j = web3jFactory.create(network)

            val function = Function(
                "balanceOf",
                listOf(Address(address)),
                listOf(object : TypeReference<Uint256>() {})
            )

            val encodedFunction = FunctionEncoder.encode(function)
            val response = web3j.ethCall(
                Transaction.createEthCallTransaction(address, tokenContract, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send()

            if (response.hasError()) {
                throw Exception("Web3j error: ${response.error?.message}")
            }

            if (response.result == "0x") {
                return@make BigDecimal.ZERO
            }

            val decoded = FunctionReturnDecoder.decode(
                response.result,
                function.outputParameters
            )

            if (decoded.isEmpty()) {
                throw Exception("Failed to decode Web3j response")
            }

            val balanceUint = decoded[0] as Uint256
            val balanceWei = balanceUint.value.toBigDecimal()

            balanceWei.divide(
                BigDecimal.TEN.pow(tokenDecimals),
                tokenDecimals,
                RoundingMode.HALF_UP
            )
        }
    }

    // ============ TRANSACTION METHODS ============

    override suspend fun getNativeTransactions(
        address: String,
        network: EthereumNetwork,
        walletId: String,
        tokenExternalId: String?
    ): Result<List<NativeETHTransaction>> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getEthereumTransactions(
                chainId = chainId,
                address = address,
                apiKey = apiKey
            )

            if (response.status == "1") {
                response.result.toNativeETHTransactionList(
                    walletId = walletId,
                    network = network,
                    walletAddress = address,
                    tokenExternalId = tokenExternalId
                )
            } else {
                throw Exception("API error: ${response.message}")
            }
        }
    }

    override suspend fun getTokenTransactions(
        address: String,
        tokenContract: String,
        network: EthereumNetwork,
        walletId: String,
        tokenExternalId: String
    ): Result<List<TokenTransaction>> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getTokenTransfers(
                chainId = chainId,
                address = address,
                contractAddress = tokenContract,
                apiKey = apiKey
            )

            if (response.status == "1") {
                response.result.toTokenTransactionList(
                    walletId = walletId,
                    network = network,
                    walletAddress = address,
                    tokenExternalId = tokenExternalId
                )
            } else {
                throw Exception("API error: ${response.message}")
            }
        }
    }

    // ============ TRANSACTION CREATION ============

    override suspend fun createAndSignNativeTransaction(
        fromAddress: String,
        fromPrivateKey: String,
        toAddress: String,
        amountWei: BigInteger,
        gasPriceWei: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        network: EthereumNetwork
    ): Result<Triple<RawTransaction, String, String>> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            if (!validateAddress(fromAddress) || !validateAddress(toAddress)) {
                throw Exception("Invalid address format")
            }

            val rawTransaction = RawTransaction.createEtherTransaction(
                nonce,
                gasPriceWei,
                BigInteger.valueOf(GAS_LIMIT_STANDARD),
                toAddress,
                amountWei
            )

            val credentials = Credentials.create(fromPrivateKey)
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
            val signedHex = Numeric.toHexString(signedMessage)
            val txHash = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(signedHex)))

            Triple(rawTransaction, signedHex, txHash)
        }
    }

    override suspend fun createAndSignTokenTransaction(
        fromAddress: String,
        fromPrivateKey: String,
        toAddress: String,
        amount: BigInteger,
        tokenContract: String,
        tokenDecimals: Int,
        gasPriceWei: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        network: EthereumNetwork,
        tokenType: TokenType
    ): Result<Triple<RawTransaction, String, String>> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            if (!validateAddress(fromAddress) || !validateAddress(toAddress)) {
                throw Exception("Invalid address format")
            }

            val function = Function(
                "transfer",
                listOf(Address(toAddress), Uint256(amount)),
                listOf(object : TypeReference<Bool>() {})
            )

            val encodedFunction = FunctionEncoder.encode(function)

            // Apply token-specific gas limits
            val gasLimit = when (tokenType) {
                TokenType.USDT -> BigInteger.valueOf(USDT_GAS_LIMIT)
                else -> BigInteger.valueOf(DEFAULT_TOKEN_GAS_LIMIT)
            }

            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPriceWei,
                gasLimit,
                tokenContract,
                encodedFunction
            )

            val credentials = Credentials.create(fromPrivateKey)
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
            val signedHex = Numeric.toHexString(signedMessage)
            val txHash = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(signedHex)))

            Triple(rawTransaction, signedHex, txHash)
        }
    }

    // ============ FEE METHODS ============

    override suspend fun getCurrentGasPrice(
        network: EthereumNetwork
    ): Result<GasPrice> = withContext(Dispatchers.IO) {
        val cacheKey = network.chainId

        // Check cache first
        gasPriceCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < GAS_PRICE_CACHE_TTL_MS) {
                return@withContext Result.Success(cached.price)
            } else {
                gasPriceCache.remove(cacheKey)
            }
        }

        val result = SafeApiCall.make {
            val web3j = web3jFactory.create(network)
            val gasPrice = web3j.ethGasPrice().send()

            if (gasPrice.hasError()) {
                throw Exception("Gas price error: ${gasPrice.error?.message}")
            }

            val gasPriceWei = gasPrice.gasPrice
            val gasPriceGwei = gasPriceWei.toBigDecimal().divide(
                BigDecimal(GWEI_TO_WEI),
                6,
                RoundingMode.HALF_UP
            )

            val priceStr = gasPriceGwei.toString()

            GasPrice(
                safe = (gasPriceGwei * SLOW_PRICE_MULTIPLIER).setScale(6, RoundingMode.HALF_UP).toString(),
                propose = priceStr,
                fast = (gasPriceGwei * FAST_PRICE_MULTIPLIER).setScale(6, RoundingMode.HALF_UP).toString(),
                lastBlock = null,
                baseFee = null
            )
        }

        if (result is Result.Success) {
            gasPriceCache[cacheKey] = CachedGasPrice(
                price = result.data,
                timestamp = System.currentTimeMillis()
            )
        }

        result
    }

    override suspend fun getFeeEstimate(
        feeLevel: FeeLevel,
        network: EthereumNetwork,
        isToken: Boolean
    ): Result<EVMFeeEstimate> = withContext(Dispatchers.IO) {
        val gasPriceResult = getCurrentGasPrice(network)

        when (gasPriceResult) {
            is Result.Success -> {
                val gasPrice = gasPriceResult.data

                val gasPriceGwei = when (feeLevel) {
                    FeeLevel.SLOW -> gasPrice.safe
                    FeeLevel.NORMAL -> gasPrice.propose
                    FeeLevel.FAST -> gasPrice.fast
                }

                val gasPriceWei = (BigDecimal(gasPriceGwei) * BigDecimal(GWEI_TO_WEI)).toBigInteger()
                val gasLimit = if (isToken) DEFAULT_TOKEN_GAS_LIMIT else GAS_LIMIT_STANDARD
                val totalFeeWei = gasPriceWei.multiply(BigInteger.valueOf(gasLimit))

                val totalFeeEth = BigDecimal(totalFeeWei).divide(
                    BigDecimal(WEI_PER_ETH),
                    ETH_DECIMALS,
                    RoundingMode.HALF_UP
                ).toPlainString()

                val estimatedTime = when (feeLevel) {
                    FeeLevel.SLOW -> 120
                    FeeLevel.NORMAL -> 60
                    FeeLevel.FAST -> 30
                }

                Result.Success(
                    EVMFeeEstimate(
                        gasPriceGwei = gasPriceGwei,
                        gasPriceWei = gasPriceWei.toString(),
                        gasLimit = gasLimit,
                        totalFeeWei = totalFeeWei.toString(),
                        totalFeeEth = totalFeeEth,
                        estimatedTime = estimatedTime,
                        priority = feeLevel
                    )
                )
            }

            is Result.Error -> {
                Result.Error(gasPriceResult.message, gasPriceResult.throwable)
            }

            Result.Loading -> Result.Error("Gas price request timed out")
        }
    }

    // ============ NONCE METHODS ============

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
            }

            response.transactionCount
        }
    }

    // ============ BROADCAST METHODS ============

    override suspend fun broadcastTransaction(
        signedHex: String,
        network: EthereumNetwork
    ): Result<BroadcastResult> = withContext(Dispatchers.IO) {
        SafeApiCall.make {
            val web3j = web3jFactory.create(network)
            val response = web3j.ethSendRawTransaction(signedHex).send()

            if (response.hasError()) {
                val error = response.error.message

                when {
                    error.contains("insufficient funds") ->
                        throw Exception("Insufficient ETH for gas fees")
                    error.contains("nonce") ->
                        throw Exception("Nonce error: $error")
                    error.contains("already known") -> {
                        val hash = extractHashFromError(error)
                        BroadcastResult(
                            success = true,
                            hash = hash ?: "unknown"
                        )
                    }
                    else ->
                        throw Exception("Broadcast failed: $error")
                }
            } else {
                BroadcastResult(
                    success = true,
                    hash = response.transactionHash
                )
            }
        }
    }

    // ============ HELPER METHODS ============

    private fun validateAddress(address: String): Boolean {
        return address.startsWith("0x") && address.length == 42
    }

    private fun extractHashFromError(error: String): String? {
        val hashPattern = Regex("0x[a-fA-F0-9]{64}")
        return hashPattern.find(error)?.value
    }

    private data class CachedGasPrice(
        val price: GasPrice,
        val timestamp: Long
    )

    private data class CachedConfirmationTime(
        val seconds: Int,
        val timestamp: Long
    )

    companion object {
        // Gas limits
        const val GAS_LIMIT_STANDARD = 21000L
        const val DEFAULT_TOKEN_GAS_LIMIT = 65000L
        const val USDT_GAS_LIMIT = 78000L

        // Constants
        private const val WEI_PER_GWEI = 1_000_000_000L
        private const val WEI_PER_ETH = "1000000000000000000"
        private const val ETH_DECIMALS = 18
        private const val GWEI_TO_WEI = 1_000_000_000L
        // Price multipliers
        private val SLOW_PRICE_MULTIPLIER = BigDecimal("0.9")
        private val FAST_PRICE_MULTIPLIER = BigDecimal("1.2")

        // Cache TTL (30 seconds)
        private const val GAS_PRICE_CACHE_TTL_MS = 30000L
    }
}