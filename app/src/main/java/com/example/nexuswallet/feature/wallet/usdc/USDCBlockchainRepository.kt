package com.example.nexuswallet.feature.wallet.usdc

import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.ethereum.EtherscanApiService
import com.example.nexuswallet.feature.wallet.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class USDCBlockchainRepository @Inject constructor(
    private val etherscanApi: EtherscanApiService,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository
) {

    companion object {
        // USDC Contract Addresses across chains
        private val USDC_CONTRACTS = mapOf(
            // Ethereum Mainnet
            "1" to "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            // Sepolia
            "11155111" to "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238",
            // Polygon
            "137" to "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174",
            // BSC
            "56" to "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d",
            // Arbitrum
            "42161" to "0xFF970A61A04b1cA14834A43f5dE4533eBDDB5CC8",
            // Optimism
            "10" to "0x7F5c764cBc14f9669B88837ca1490cCa17c31607",
            // Base
            "8453" to "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
            // Avalanche
            "43114" to "0xB97EF9Ef8734C71904D8002F8b6Bc66Dd9c48a6E"
        )

        private const val USDC_DECIMALS = 6
        private const val USDC_DECIMALS_DIVISOR = "1000000"
    }

    suspend fun getUSDCBalance(
        address: String,
        network: EthereumNetwork = EthereumNetwork.SEPOLIA
    ): TokenBalance {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDC_DEBUG", "====== GET USDC BALANCE (Web3j Only) ======")
                Log.d("USDC_DEBUG", "Wallet Address: $address")
                Log.d("USDC_DEBUG", "Network: $network (${network.name})")

                // Get balance via Web3j
                val usdcBalance = getUSDCBalanceViaWeb3j(address, network)

                Log.d("USDC_DEBUG", "Web3j Balance Result: ${usdcBalance.balanceDecimal} USDC")
                Log.d("USDC_DEBUG", "====== END ======")

                return@withContext usdcBalance

            } catch (e: Exception) {
                Log.e("USDC_DEBUG", "Error getting USDC balance via Web3j: ${e.message}")
                return@withContext createEmptyUSDCBalance(network)
            }
        }
    }

    /**
     * Web3j fallback for getting USDC balance
     */
    private suspend fun getUSDCBalanceViaWeb3j(
        address: String,
        network: EthereumNetwork
    ): TokenBalance {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDCRepo", "Getting USDC balance via Web3j...")

                val web3j = getWeb3j(network)
                val usdcAddress = getUSDCContractAddress(network)
                    ?: return@withContext createEmptyUSDCBalance(network)

                Log.d("USDC_DEBUG", "Web3j RPC URL: ${getRpcUrl(network)?.take(50)}...")
                Log.d("USDC_DEBUG", "USDC Contract: $usdcAddress")

                // Create balanceOf function call
                val function = Function(
                    "balanceOf",
                    listOf(Address(address)),
                    listOf(object : TypeReference<Uint256>() {})
                )

                val encodedFunction = FunctionEncoder.encode(function)

                // Create eth_call transaction
                val transaction = org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    address,
                    usdcAddress,
                    encodedFunction
                )

                Log.d("USDC_DEBUG", "Making eth_call to contract...")

                // Execute the call
                val response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()

                if (response.hasError()) {
                    Log.e("USDCRepo", "Web3j error: ${response.error?.message}")
                    return@withContext createEmptyUSDCBalance(network)
                }

                val result = response.result
                Log.d("USDC_DEBUG", "Raw response: $result")

                if (result == "0x") {
                    Log.d("USDCRepo", "Zero balance from Web3j")
                    return@withContext createTokenBalance(
                        balanceRaw = "0",
                        balanceDecimal = BigDecimal.ZERO,
                        network = network,
                        contractAddress = usdcAddress
                    )
                }

                // Decode the response
                val decoded = FunctionReturnDecoder.decode(
                    result,
                    function.outputParameters
                )

                if (decoded.isEmpty()) {
                    Log.e("USDCRepo", "Failed to decode Web3j response")
                    return@withContext createEmptyUSDCBalance(network)
                }

                val balanceUint = decoded[0] as Uint256
                val balanceRaw = balanceUint.value.toString()
                val balanceWei = balanceUint.value.toBigDecimal()

                val balanceDecimal = balanceWei.divide(
                    BigDecimal(USDC_DECIMALS_DIVISOR),
                    USDC_DECIMALS,
                    RoundingMode.HALF_UP
                )

                Log.d("USDCRepo", " Web3j USDC Balance: $balanceDecimal USDC")

                createTokenBalance(
                    balanceRaw = balanceRaw,
                    balanceDecimal = balanceDecimal,
                    network = network,
                    contractAddress = usdcAddress
                )

            } catch (e: Exception) {
                Log.e("USDCRepo", " Web3j balance check failed: ${e.message}")
                createEmptyUSDCBalance(network)
            }
        }
    }

    /**
     * Create and sign REAL USDC transfer transaction using Web3j
     */
    suspend fun createAndSignUSDCTransfer(
        fromAddress: String,
        fromPrivateKey: String,
        toAddress: String,
        amount: BigDecimal, // USDC amount (e.g., 10.5)
        network: EthereumNetwork = EthereumNetwork.SEPOLIA
    ): Triple<RawTransaction, String, String> { // Returns (rawTx, signedHex, txHash)
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDCRepo", " Creating REAL USDC transfer")

                val web3j = getWeb3j(network)
                val chainId = getChainIdLong(network)
                val usdcAddress = getUSDCContractAddress(network)
                    ?: throw IllegalArgumentException("USDC not supported on $network")

                // 1. Convert USDC amount to token units (6 decimals)
                val amountInUnits = amount.multiply(BigDecimal(USDC_DECIMALS_DIVISOR))
                    .toBigInteger()
                Log.d("USDCRepo", "Amount in token units: $amountInUnits")

                // 2. Get nonce
                val nonce = getNonce(fromAddress, web3j)
                Log.d("USDCRepo", "Nonce: $nonce")

                // 3. Get gas price (convert Gwei to Wei)
                val gasPriceResponse = ethereumBlockchainRepository.getCurrentGasPrice(network)
                val gasPriceGwei = BigDecimal(gasPriceResponse.propose)
                val gasPriceWei = gasPriceGwei.multiply(BigDecimal("1000000000")).toBigInteger()
                Log.d("USDCRepo", "Gas price: $gasPriceWei wei ($gasPriceGwei Gwei)")

                // 4. Create ERC-20 transfer function call
                val function = Function(
                    "transfer",
                    listOf(
                        Address(toAddress),
                        Uint256(amountInUnits)
                    ),
                    listOf(object : TypeReference<Bool>() {})
                )

                val encodedFunction = FunctionEncoder.encode(function)
                Log.d("USDCRepo", "Encoded function: ${encodedFunction.take(50)}...")

                // 5. Estimate gas for token transfer
                val estimatedGas = estimateGasForTokenTransfer(
                    fromAddress = fromAddress,
                    contractAddress = usdcAddress,
                    encodedData = encodedFunction,
                    network = network
                )
                Log.d("USDCRepo", "Estimated gas: $estimatedGas")

                // 6. Create raw transaction
                val rawTransaction = RawTransaction.createTransaction(
                    nonce,
                    gasPriceWei,
                    estimatedGas,
                    usdcAddress,
                    encodedFunction
                )

                Log.d("USDCRepo", "Raw transaction created")

                // 7. Sign transaction
                val credentials = Credentials.create(fromPrivateKey)
                val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
                val signedHex = Numeric.toHexString(signedMessage)

                // 8. Calculate transaction hash
                val txHash = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(signedHex)))

                Log.d("USDCRepo", " REAL USDC transaction created")
                Log.d("USDCRepo", "Transaction hash: $txHash")
                Log.d("USDCRepo", "Signed hex length: ${signedHex.length}")

                Triple(rawTransaction, signedHex, txHash)

            } catch (e: Exception) {
                Log.e("USDCRepo", " Error creating USDC transfer: ${e.message}", e)
                throw e
            }
        }
    }

    private suspend fun estimateGasForTokenTransfer(
        fromAddress: String,
        contractAddress: String,
        encodedData: String,
        network: EthereumNetwork
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            try {
                // Use fixed gas estimates for token transfers
                // ERC-20 transfer: ~65,000 gas
                val baseGas = BigInteger.valueOf(65_000L)

                // Add buffer based on network
                val buffer = when (network) {
                    EthereumNetwork.MAINNET -> BigInteger.valueOf(5_000L)
                    EthereumNetwork.POLYGON -> BigInteger.valueOf(10_000L)
                    EthereumNetwork.ARBITRUM -> BigInteger.valueOf(15_000L)
                    else -> BigInteger.valueOf(5_000L)
                }

                baseGas.add(buffer)

            } catch (e: Exception) {
                Log.w("USDCRepo", "Gas estimation error, using default: ${e.message}")
                BigInteger.valueOf(65_000L) // Default fallback
            }
        }
    }

    /**
     * Broadcast signed USDC transaction using Etherscan API
     */
    suspend fun broadcastUSDCTransaction(
        signedHex: String,
        network: EthereumNetwork = EthereumNetwork.SEPOLIA
    ): BroadcastResult {
        return withContext(Dispatchers.IO) {
            try {
                val chainId = getChainId(network)
                val apiKey = BuildConfig.ETHERSCAN_API_KEY

                val response = etherscanApi.broadcastTransaction(
                    chainId = chainId,
                    hex = signedHex,
                    apiKey = apiKey
                )

                val result = response.result

                return@withContext when {
                    result.startsWith("0x") && result.length == 66 -> {
                        BroadcastResult(
                            success = true,
                            hash = result,
                            chain = getChainTypeForNetwork(network)
                        )
                    }
                    else -> {
                        // return error
                        BroadcastResult(
                            success = false,
                            error = result,
                            chain = getChainTypeForNetwork(network)
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("USDCRepo", "Broadcast error: ${e.message}")
                BroadcastResult(
                    success = false,
                    error = e.message ?: "Broadcast failed",
                    chain = getChainTypeForNetwork(network)
                )
            }
        }
    }

    /**
     * Fallback broadcast via Web3j
     */
    private suspend fun broadcastViaWeb3j(
        signedHex: String,
        network: EthereumNetwork
    ): BroadcastResult {
        return withContext(Dispatchers.IO) {
            try {
                val web3j = getWeb3j(network)
                val response = web3j.ethSendRawTransaction(signedHex).send()

                if (response.hasError()) {
                    val error = response.error.message
                    Log.e("USDCRepo", " Web3j broadcast error: $error")
                    BroadcastResult(
                        success = false,
                        error = error,
                        chain = getChainTypeForNetwork(network)
                    )
                } else {
                    val txHash = response.transactionHash
                    Log.d("USDCRepo", " Web3j broadcast successful: $txHash")
                    BroadcastResult(
                        success = true,
                        hash = txHash,
                        chain = getChainTypeForNetwork(network)
                    )
                }
            } catch (e: Exception) {
                Log.e("USDCRepo", " Web3j broadcast failed: ${e.message}")
                BroadcastResult(
                    success = false,
                    error = e.message ?: "Broadcast failed",
                    chain = getChainTypeForNetwork(network)
                )
            }
        }
    }

    /**
     * Get USDC transaction history from Etherscan
     */
    suspend fun getUSDCTransactions(
        address: String,
        network: EthereumNetwork = EthereumNetwork.SEPOLIA
    ): List<Transaction> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("USDCRepo", "Getting USDC transactions via Etherscan (fallback)...")

                val chainId = getChainId(network)
                val usdcAddress = getUSDCContractAddress(network)
                    ?: return@withContext emptyList()

                val apiKey = BuildConfig.ETHERSCAN_API_KEY

                val response = etherscanApi.getTokenTransfers(
                    chainId = chainId,
                    address = address,
                    contractAddress = usdcAddress,
                    apiKey = apiKey
                )

                if (response.status == "1") {
                    response.result.map { tx ->
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
                            chain = getChainTypeForNetwork(network)
                        )
                    }
                } else {
                    Log.w("USDCRepo", "Cannot get transactions via Web3j, Etherscan failed: ${response.message}")
                    emptyList()
                }

            } catch (e: Exception) {
                Log.e("USDCRepo", "Error getting USDC transactions: ${e.message}")
                emptyList()
            }
        }
    }

    // =========== HELPER FUNCTIONS ===========

    private fun getChainId(network: EthereumNetwork): String {
        return when (network) {
            EthereumNetwork.MAINNET -> "1"
            EthereumNetwork.SEPOLIA -> "11155111"
            EthereumNetwork.POLYGON -> "137"
            EthereumNetwork.BSC -> "56"
            EthereumNetwork.ARBITRUM -> "42161"
            EthereumNetwork.OPTIMISM -> "10"
            else -> "11155111"
        }
    }

    private fun getChainIdLong(network: EthereumNetwork): Long {
        return when (network) {
            EthereumNetwork.MAINNET -> 1L
            EthereumNetwork.SEPOLIA -> 11155111L
            EthereumNetwork.POLYGON -> 137L
            EthereumNetwork.BSC -> 56L
            EthereumNetwork.ARBITRUM -> 42161L
            EthereumNetwork.OPTIMISM -> 10L
            else -> 11155111L
        }
    }

    private fun getUSDCContractAddress(network: EthereumNetwork): String? {
        val chainId = getChainId(network)
        return USDC_CONTRACTS[chainId]
    }

    private fun getRpcUrl(network: EthereumNetwork): String? {
        val alchemyApiKey = BuildConfig.ALCHEMY_API_KEY
        return when (network) {
            EthereumNetwork.MAINNET -> "https://eth-mainnet.g.alchemy.com/v2/$alchemyApiKey"
            EthereumNetwork.SEPOLIA -> "https://eth-sepolia.g.alchemy.com/v2/$alchemyApiKey"
            EthereumNetwork.POLYGON -> "https://polygon-mainnet.g.alchemy.com/v2/$alchemyApiKey"
            else -> "https://eth-sepolia.g.alchemy.com/v2/$alchemyApiKey"
        }
    }

    private fun getWeb3j(network: EthereumNetwork): Web3j {
        val alchemyApiKey = BuildConfig.ALCHEMY_API_KEY

        val rpcUrl = when (network) {
            EthereumNetwork.MAINNET -> "https://eth-mainnet.g.alchemy.com/v2/$alchemyApiKey"
            EthereumNetwork.SEPOLIA -> "https://eth-sepolia.g.alchemy.com/v2/$alchemyApiKey"
            EthereumNetwork.POLYGON -> "https://polygon-mainnet.g.alchemy.com/v2/$alchemyApiKey"
            else -> "https://eth-sepolia.g.alchemy.com/v2/$alchemyApiKey"
        }

        Log.d("USDCRepo", "Using Alchemy RPC: ${rpcUrl.take(50)}...")

        return Web3j.build(
            HttpService(
                rpcUrl,
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
        )
    }

    private suspend fun getNonce(address: String, web3j: Web3j): BigInteger {
        val response = web3j.ethGetTransactionCount(
            address,
            DefaultBlockParameterName.PENDING
        ).send()

        return response.transactionCount
    }

    private fun createTokenBalance(
        balanceRaw: String,
        balanceDecimal: BigDecimal,
        network: EthereumNetwork,
        contractAddress: String
    ): TokenBalance {
        return TokenBalance(
            tokenId = "usdc_${network.name.lowercase()}",
            symbol = "USDC",
            name = "USD Coin",
            contractAddress = contractAddress,
            balance = balanceRaw,
            balanceDecimal = balanceDecimal.toPlainString(),
            usdPrice = 1.0,
            usdValue = balanceDecimal.toDouble(),
            decimals = USDC_DECIMALS,
            chain = getChainTypeForNetwork(network)
        )
    }

    private fun createEmptyUSDCBalance(network: EthereumNetwork): TokenBalance {
        val contractAddress = getUSDCContractAddress(network) ?: ""
        return TokenBalance(
            tokenId = "usdc_${network.name.lowercase()}",
            symbol = "USDC",
            name = "USD Coin",
            contractAddress = contractAddress,
            balance = "0",
            balanceDecimal = "0",
            usdPrice = 1.0,
            usdValue = 0.0,
            decimals = USDC_DECIMALS,
            chain = getChainTypeForNetwork(network)
        )
    }

    private fun getChainTypeForNetwork(network: EthereumNetwork): ChainType {
        return when (network) {
            EthereumNetwork.SEPOLIA -> ChainType.ETHEREUM_SEPOLIA
            else -> ChainType.ETHEREUM
        }
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
}