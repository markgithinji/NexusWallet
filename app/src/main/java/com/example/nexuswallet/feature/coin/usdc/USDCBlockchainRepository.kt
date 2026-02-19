package com.example.nexuswallet.feature.coin.usdc

import android.util.Log
import com.example.nexuswallet.BuildConfig
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
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
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
    suspend fun getUSDCBalance(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<USDCBalance> {
        return withContext(Dispatchers.IO) {
            try {
                val usdcBalance = getUSDCBalanceViaWeb3j(address, network)

                val result = USDCBalance(
                    address = address,
                    amount = usdcBalance.balance,
                    amountDecimal = usdcBalance.balanceDecimal,
                    usdValue = usdcBalance.usdValue
                )

                Result.Success(result)

            } catch (e: Exception) {
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
                // First get Ethereum gas price
                val gasPriceResult = getEthereumGasPrice(network, feeLevel)

                when (gasPriceResult) {
                    is Result.Success -> {
                        val ethGasPrice = gasPriceResult.data

                        val (gasPriceGwei, estimatedTime) = when (feeLevel) {
                            FeeLevel.SLOW -> ethGasPrice.safe to 900
                            FeeLevel.NORMAL -> ethGasPrice.propose to 300
                            FeeLevel.FAST -> ethGasPrice.fast to 60
                        }

                        val gasPriceWei =
                            (BigDecimal(gasPriceGwei) * BigDecimal("1000000000")).toBigInteger()
                        val gasLimit = BigInteger.valueOf(DEFAULT_GAS_LIMIT)
                        val totalFeeWei = gasPriceWei.multiply(gasLimit)

                        val totalFeeEth = BigDecimal(totalFeeWei).divide(
                            BigDecimal("1000000000000000000"),
                            8,
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
    }

    private suspend fun getEthereumGasPrice(
        network: EthereumNetwork,
        feeLevel: FeeLevel
    ): Result<GasPrice> {
        return withContext(Dispatchers.IO) {
            try {
                val chainId = network.chainId
                val apiKey = BuildConfig.ETHERSCAN_API_KEY

                val response = etherscanApi.getGasPrice(
                    chainId = chainId,
                    apiKey = apiKey
                )

                if (response.status == "1") {
                    Result.Success(
                        GasPrice(
                            safe = response.result.SafeGasPrice,
                            propose = response.result.ProposeGasPrice,
                            fast = response.result.FastGasPrice,
                            lastBlock = response.result.lastBlock,
                            baseFee = response.result.suggestBaseFee
                        )
                    )
                } else {
                    Result.Error("Gas price API error: ${response.message}")
                }
            } catch (e: Exception) {
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
                val usdcAddress = network.usdcContractAddress
                val amountInUnits =
                    amount.multiply(BigDecimal(USDC_DECIMALS_DIVISOR)).toBigInteger()

                val estimatedGas = estimateGasForTokenTransfer(
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
                    return@withContext Result.Error(
                        message = "Invalid private key format",
                        throwable = e
                    )
                }

                val signedMessage =
                    TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
                val signedHex = Numeric.toHexString(signedMessage)
                val txHash = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(signedHex)))

                Result.Success(Triple(rawTransaction, signedHex, txHash))

            } catch (e: Exception) {
                Result.Error(
                    message = "Failed to create USDC transfer: ${e.message}",
                    throwable = e
                )
            }
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
                    return@withContext BigInteger.valueOf(DEFAULT_GAS_LIMIT)
                }

                val estimatedGas = response.amountUsed

                // Add 20% buffer for safety
                val buffer =
                    estimatedGas.multiply(BigInteger.valueOf(20)).divide(BigInteger.valueOf(100))
                estimatedGas.add(buffer)

            } catch (e: Exception) {
                BigInteger.valueOf(DEFAULT_GAS_LIMIT)
            }
        }
    }

    suspend fun broadcastUSDCTransaction(
        signedHex: String,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<BroadcastResult> {
        return withContext(Dispatchers.IO) {
            try {
                val chainId = network.chainId
                val apiKey = BuildConfig.ETHERSCAN_API_KEY

                val response = etherscanApi.broadcastTransaction(
                    chainId = chainId,
                    hex = signedHex,
                    apiKey = apiKey
                )

                val result = response.result

                return@withContext when {
                    result.startsWith("0x") && result.length == 66 -> {
                        Result.Success(
                            BroadcastResult(
                                success = true,
                                hash = result
                            )
                        )
                    }

                    result.contains("insufficient funds") -> {
                        Result.Error(
                            message = "Insufficient ETH for gas fees",
                            throwable = RuntimeException(result)
                        )
                    }

                    result.contains("nonce") -> {
                        Result.Error(
                            message = "Nonce error: $result",
                            throwable = RuntimeException(result)
                        )
                    }

                    else -> {
                        Result.Error(
                            message = result,
                            throwable = RuntimeException(result)
                        )
                    }
                }

            } catch (e: Exception) {
                Result.Error(
                    message = e.message ?: "Broadcast failed",
                    throwable = e
                )
            }
        }
    }

    private suspend fun broadcastViaWeb3j(
        signedHex: String,
        network: EthereumNetwork
    ): Result<BroadcastResult> {
        return withContext(Dispatchers.IO) {
            try {
                val web3j = web3jFactory.create(network)
                val response = web3j.ethSendRawTransaction(signedHex).send()

                if (response.hasError()) {
                    val error = response.error.message
                    Result.Error(
                        message = error,
                        throwable = RuntimeException(error)
                    )
                } else {
                    val txHash = response.transactionHash
                    Result.Success(
                        BroadcastResult(
                            success = true,
                            hash = txHash
                        )
                    )
                }
            } catch (e: Exception) {
                Result.Error(
                    message = e.message ?: "Broadcast failed",
                    throwable = e
                )
            }
        }
    }

    suspend fun getUSDCTransactions(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<List<Transaction>> {
        return withContext(Dispatchers.IO) {
            try {
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
                    val transactions = response.result.map { tx ->
                        Transaction(
                            hash = tx.hash,
                            from = tx.from,
                            to = tx.to,
                            value = tx.value,
                            valueDecimal = convertTokenValue(tx.value, tx.tokenDecimal),
                            gasPrice = tx.gasPrice,
                            gasUsed = tx.gasUsed,
                            timestamp = tx.timeStamp.toLong() * 1000,
                            status = TransactionStatus.SUCCESS,
                            chain = if (network.isTestnet) ChainType.ETHEREUM_SEPOLIA else ChainType.ETHEREUM
                        )
                    }
                    Result.Success(transactions)
                } else {
                    Result.Error(
                        message = response.message ?: "Failed to get transactions",
                        throwable = RuntimeException(response.message)
                    )
                }

            } catch (e: Exception) {
                Result.Error(
                    message = "Failed to get USDC transactions: ${e.message}",
                    throwable = e
                )
            }
        }
    }

    suspend fun getNonce(
        address: String,
        network: EthereumNetwork
    ): Result<BigInteger> {
        return withContext(Dispatchers.IO) {
            try {
                val web3j = web3jFactory.create(network)
                val response = web3j.ethGetTransactionCount(
                    address,
                    DefaultBlockParameterName.PENDING
                ).send()

                if (response.hasError()) {
                    Result.Error(
                        message = "Failed to get nonce: ${response.error?.message}",
                        throwable = RuntimeException(response.error?.message)
                    )
                } else {
                    Result.Success(response.transactionCount)
                }
            } catch (e: Exception) {
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

    private fun convertTokenValue(value: String, decimalsStr: String): String {
        return try {
            val decimals = decimalsStr.toIntOrNull() ?: USDC_DECIMALS
            val valueWei = BigDecimal(value)
            valueWei.divide(BigDecimal.TEN.pow(decimals), decimals, RoundingMode.HALF_UP)
                .toPlainString()
        } catch (e: Exception) {
            "0"
        }
    }

    suspend fun getUSDCTransactionHistory(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<List<TokenTransaction>> = withContext(Dispatchers.IO) {
        try {
            val chainId = network.chainId
            val usdcAddress = network.usdcContractAddress
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            Log.d("USDCAPI", "=== Fetching USDC transactions for $address on ${network.displayName} ===")

            val response = etherscanApi.getTokenTransfers(
                chainId = chainId,
                address = address,
                contractAddress = usdcAddress,
                apiKey = apiKey
            )

            if (response.status == "1") {
                val transactions = response.result
                Log.d("USDCAPI", "Found ${transactions.size} USDC transactions")

                transactions.take(3).forEachIndexed { i, tx ->
                    Log.d("USDCAPI", "Tx $i: ${tx.hash.take(8)}...")
                    Log.d("USDCAPI", "  from: ${tx.from.take(8)}...")
                    Log.d("USDCAPI", "  to: ${tx.to.take(8)}...")
                    Log.d("USDCAPI", "  value: ${tx.value}")
                    Log.d("USDCAPI", "  timestamp: ${tx.timeStamp}")
                }

                Result.Success(transactions)
            } else {
                Log.e("USDCAPI", "API error: ${response.message}")
                Result.Error(response.message ?: "Failed to get USDC transactions")
            }

        } catch (e: Exception) {
            Log.e("USDCAPI", "Failed to get USDC transactions: ${e.message}", e)
            Result.Error("Failed to get USDC transactions: ${e.message}", e)
        }
    }

    companion object {
        private const val USDC_DECIMALS = 6
        private const val USDC_DECIMALS_DIVISOR = "1000000"
        private const val DEFAULT_GAS_LIMIT = 65_000L
    }
}