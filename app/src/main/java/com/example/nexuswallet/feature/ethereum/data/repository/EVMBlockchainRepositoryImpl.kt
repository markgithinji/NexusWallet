package com.example.nexuswallet.feature.ethereum.data.repository

import com.example.nexuswallet.feature.core.domain.model.BroadcastResult
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.SafeApiCall
import com.example.nexuswallet.feature.ethereum.data.model.CachedGasPrice
import com.example.nexuswallet.feature.ethereum.data.model.GasPrice
import com.example.nexuswallet.feature.ethereum.data.remote.EtherscanApiService
import com.example.nexuswallet.feature.ethereum.data.toNativeETHTransactionList
import com.example.nexuswallet.feature.ethereum.data.toTokenTransactionList
import com.example.nexuswallet.feature.ethereum.domain.model.EVMFeeEstimate
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.DEFAULT_TOKEN_GAS_LIMIT
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.GAS_LIMIT_STANDARD
import com.example.nexuswallet.feature.ethereum.util.EVMConstants.USDT_GAS_LIMIT
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.usdc.Web3jFactory
import kotlinx.coroutines.CoroutineDispatcher
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EVMBlockchainRepositoryImpl @Inject constructor(
    private val etherscanApi: EtherscanApiService,
    private val web3jFactory: Web3jFactory,
    private val ioDispatcher: CoroutineDispatcher
) : EVMBlockchainRepository {

    // Gas price cache - stores gas price per network with timestamp
    private val gasPriceCache = ConcurrentHashMap<String, CachedGasPrice>()

    // ============ BALANCE METHODS ============

    override suspend fun getNativeBalance(
        address: String,
        network: EthereumNetwork
    ): Result<BigDecimal> = withContext(ioDispatcher) {
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
    ): Result<BigDecimal> = withContext(ioDispatcher) {
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
        evmTokenType: EVMTokenType?
    ): Result<List<NativeETHTransaction>> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val chainId = network.chainId

            val response = etherscanApi.getEthereumTransactions(
                chainId = chainId,
                address = address,
            )

            if (response.status == "1") {
                response.result.toNativeETHTransactionList(
                    walletId = walletId,
                    network = network,
                    walletAddress = address,
                    evmTokenType = evmTokenType ?: EVMTokenType.NATIVE
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
        evmTokenType: EVMTokenType
    ): Result<List<TokenTransaction>> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val chainId = network.chainId

            val response = etherscanApi.getTokenTransfers(
                chainId = chainId,
                address = address,
                contractAddress = tokenContract,
            )

            if (response.status == "1") {
                response.result.toTokenTransactionList(
                    walletId = walletId,
                    network = network,
                    walletAddress = address,
                    evmTokenType = evmTokenType
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
    ): Result<Triple<RawTransaction, String, String>> = withContext(ioDispatcher) {
        SafeApiCall.make {
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
        evmTokenType: EVMTokenType
    ): Result<Triple<RawTransaction, String, String>> = withContext(ioDispatcher) {
        SafeApiCall.make {
            val function = Function(
                "transfer",
                listOf(Address(toAddress), Uint256(amount)),
                listOf(object : TypeReference<Bool>() {})
            )

            val encodedFunction = FunctionEncoder.encode(function)

            // Apply token-specific gas limits
            val gasLimit = when (evmTokenType) {
                EVMTokenType.USDT -> BigInteger.valueOf(USDT_GAS_LIMIT)
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
    ): Result<GasPrice> = withContext(ioDispatcher) {
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
                safe = (gasPriceGwei * SLOW_PRICE_MULTIPLIER).setScale(6, RoundingMode.HALF_UP)
                    .toString(),
                propose = priceStr,
                fast = (gasPriceGwei * FAST_PRICE_MULTIPLIER).setScale(6, RoundingMode.HALF_UP)
                    .toString(),
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

    // ============ NONCE METHODS ============

    override suspend fun getNonce(
        address: String,
        network: EthereumNetwork
    ): Result<BigInteger> = withContext(ioDispatcher) {
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
    ): Result<BroadcastResult> = withContext(ioDispatcher) {
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

    private fun extractHashFromError(error: String): String? {
        val hashPattern = Regex("0x[a-fA-F0-9]{64}")
        return hashPattern.find(error)?.value
    }

    companion object {
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