package com.example.nexuswallet.feature.wallet.data.repository

import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.wallet.data.model.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.TransactionFee
import com.example.nexuswallet.feature.wallet.data.model.UTXO
import com.example.nexuswallet.feature.wallet.data.remote.BitcoinBroadcastApiService
import com.example.nexuswallet.feature.wallet.data.remote.BlockstreamApiService
import com.example.nexuswallet.feature.wallet.data.remote.ChainId
import com.example.nexuswallet.feature.wallet.data.remote.CovalentApiService
import com.example.nexuswallet.feature.wallet.data.remote.EtherscanApiService
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

class BlockchainRepository @Inject constructor(
    @Named("mainnetEtherscanApi") private val mainnetEtherscanApi: EtherscanApiService,
    @Named("sepoliaEtherscanApi") private val sepoliaEtherscanApi: EtherscanApiService,
    private val etherscanApi: EtherscanApiService,
    private val blockstreamApi: BlockstreamApiService,
    private val covalentApi: CovalentApiService,
    private val bitcoinBroadcastApi: BitcoinBroadcastApiService
) {

    companion object {
        // Chain IDs for Etherscan V2
        const val CHAIN_ID_ETHEREUM = "1"
        const val CHAIN_ID_SEPOLIA = "11155111"
        const val CHAIN_ID_HOLESKY = "17000"
        const val CHAIN_ID_POLYGON = "137"
        const val CHAIN_ID_BSC = "56"
        const val CHAIN_ID_ARBITRUM = "42161"
        const val CHAIN_ID_OPTIMISM = "10"
    }

    suspend fun getEthereumBalance(
        address: String,
        network: EthereumNetwork = EthereumNetwork.MAINNET
    ): BigDecimal {
        Log.d("BlockchainRepo", "=".repeat(50))
        Log.d("BlockchainRepo", " START: getEthereumBalance() - V2")
        Log.d("BlockchainRepo", " Wallet Address: $address")
        Log.d("BlockchainRepo", " Network: $network")
        Log.d("BlockchainRepo", " API Key exists: ${BuildConfig.ETHERSCAN_API_KEY.isNotEmpty()}")

        return try {
            // Get chain ID for V2 API
            val chainId = when (network) {
                EthereumNetwork.SEPOLIA -> CHAIN_ID_SEPOLIA
//                EthereumNetwork.HOLESKY -> CHAIN_ID_HOLESKY
                EthereumNetwork.POLYGON -> CHAIN_ID_POLYGON
                EthereumNetwork.BSC -> CHAIN_ID_BSC
                EthereumNetwork.ARBITRUM -> CHAIN_ID_ARBITRUM
                EthereumNetwork.OPTIMISM -> CHAIN_ID_OPTIMISM
                else -> CHAIN_ID_ETHEREUM
            }

            // Get appropriate API service
            val etherscanApi = when (network) {
                EthereumNetwork.SEPOLIA -> sepoliaEtherscanApi
                else -> mainnetEtherscanApi
            }

            val apiKey = BuildConfig.ETHERSCAN_API_KEY
            Log.d("BlockchainRepo", " Using Etherscan API V2")
            Log.d("BlockchainRepo", " Chain ID: $chainId")
            Log.d("BlockchainRepo", " Base URL: https://api.etherscan.io/v2/api")
            Log.d("BlockchainRepo", " Parameters: chainid=$chainId, module=account, action=balance, address=$address")

            val response = etherscanApi.getEthereumBalance(
                chainId = chainId,
                address = address,
                apiKey = apiKey
            )

            Log.d("BlockchainRepo", " API CALL SUCCESSFUL")
            Log.d("BlockchainRepo", " Response Status: ${response.status}")
            Log.d("BlockchainRepo", " Response Message: ${response.message}")
            Log.d("BlockchainRepo", " Raw Result: ${response.result}")
            Log.d("BlockchainRepo", " Result Length: ${response.result.length} chars")

            if (response.status == "1") {
                val wei = BigDecimal(response.result)
                val eth = wei.divide(BigDecimal("1000000000000000000"), 8, RoundingMode.HALF_UP)

                Log.d("BlockchainRepo", " BALANCE CALCULATION:")
                Log.d("BlockchainRepo", "   - Wei: $wei")
                Log.d("BlockchainRepo", "   - ETH: $eth")
                Log.d("BlockchainRepo", "   - Conversion: $wei / 1000000000000000000 = $eth")

                Log.d("BlockchainRepo", " FINAL BALANCE: $eth ETH")
                Log.d("BlockchainRepo", "=".repeat(50))

                eth
            } else {
                Log.w("BlockchainRepo", "⚠ Etherscan API V2 Error:")
                Log.w("BlockchainRepo", "  - Status: ${response.status}")
                Log.w("BlockchainRepo", "  - Message: ${response.message}")
                Log.w("BlockchainRepo", "  - Result: ${response.result}")

                if (response.message.contains("deprecated") || response.message.contains("V1")) {
                    Log.e("BlockchainRepo", " V1 API DEPRECATED! Using RPC fallback...")
                }

                // Fallback to RPC for Sepolia
                if (network == EthereumNetwork.SEPOLIA) {
                    Log.d("BlockchainRepo", " Falling back to RPC for Sepolia...")
                    return getSepoliaBalanceViaRPC(address)
                }

                // Fallback to simulated for other networks
                val simulated = getSimulatedBalance(address)
                Log.d("BlockchainRepo", " Using simulated balance: $simulated ETH")
                Log.d("BlockchainRepo", "=".repeat(50))
                simulated
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", " Etherscan API V2 call failed:")
            Log.e("BlockchainRepo", "   - Error: ${e.javaClass.simpleName}")
            Log.e("BlockchainRepo", "   - Message: ${e.message}")

            // Fallback to RPC for Sepolia
            if (network == EthereumNetwork.SEPOLIA) {
                Log.d("BlockchainRepo", " Falling back to RPC for Sepolia...")
                return getSepoliaBalanceViaRPC(address)
            }

            val simulated = getSimulatedBalance(address)
            Log.d("BlockchainRepo", " Using simulated balance: $simulated ETH")
            Log.d("BlockchainRepo", "=".repeat(50))
            simulated
        }
    }

    private suspend fun getSepoliaBalanceViaRPC(address: String): BigDecimal {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("BlockchainRepo", " Getting Sepolia balance via RPC...")

                // Use public Sepolia RPC
                val rpcUrl = "https://rpc.sepolia.org"
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val jsonRequest = """
                {
                    "jsonrpc": "2.0",
                    "method": "eth_getBalance",
                    "params": ["$address", "latest"],
                    "id": 1
                }
                """.trimIndent()

                val request = Request.Builder()
                    .url(rpcUrl)
                    .post(RequestBody.create("application/json".toMediaType(), jsonRequest))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    // Parse JSON response
                    val json = JSONObject(responseBody)
                    if (json.has("result")) {
                        val hexResult = json.getString("result")

                        if (hexResult != null && hexResult.startsWith("0x")) {
                            val hexBalance = hexResult.removePrefix("0x")
                            val wei = BigInteger(hexBalance, 16)
                            val eth = BigDecimal(wei).divide(
                                BigDecimal("1000000000000000000"),
                                8,
                                RoundingMode.HALF_UP
                            )

                            Log.d("BlockchainRepo", " RPC Balance: $eth ETH")
                            return@withContext eth
                        }
                    }
                }

                Log.w("BlockchainRepo", "RPC failed, using simulated")
                getSimulatedBalance(address)

            } catch (e: Exception) {
                Log.e("BlockchainRepo", "RPC error: ${e.message}")
                getSimulatedBalance(address)
            }
        }
    }

    // Token Balances (ERC20, BEP20, etc.)
    suspend fun getTokenBalances(address: String, chainId: ChainId): List<TokenBalance> {
        return try {
            val response = covalentApi.getTokenBalances(
                chainId = chainId.id,
                address = address,
                apiKey = BuildConfig.COVALENT_API_KEY
            )

            response.data.items.mapNotNull { token ->
                if (token.rawBalance == "0") return@mapNotNull null

                val balanceBigInt = BigInteger(token.rawBalance)
                val divisor = BigInteger.TEN.pow(token.decimals)

                val integerPart = balanceBigInt.divide(divisor)
                val fractionalPart = balanceBigInt.mod(divisor)

                val balanceDecimal = if (fractionalPart == BigInteger.ZERO) {
                    integerPart.toString()
                } else {
                    val fractionalStr = fractionalPart.toString()
                        .padStart(token.decimals, '0')
                        .trimEnd('0')
                    "$integerPart.$fractionalStr"
                }

                TokenBalance(
                    tokenId = token.contractAddress,
                    symbol = token.symbol,
                    name = token.contractName,
                    contractAddress = token.contractAddress,
                    balance = token.rawBalance,
                    balanceDecimal = balanceDecimal,
                    usdPrice = token.quoteRate ?: 0.0,
                    usdValue = token.quote ?: 0.0,
                    decimals = token.decimals,
                    chain = chainId.toChainType()
                )
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting token balances: ${e.message}")
            emptyList()
        }
    }

    suspend fun getEthereumTransactions(
        address: String,
        network: EthereumNetwork = EthereumNetwork.MAINNET
    ): List<Transaction> {
        return try {
            val chainId = when (network) {
                EthereumNetwork.SEPOLIA -> CHAIN_ID_SEPOLIA
//                EthereumNetwork.HOLESKY -> CHAIN_ID_HOLESKY
                EthereumNetwork.POLYGON -> CHAIN_ID_POLYGON
                EthereumNetwork.BSC -> CHAIN_ID_BSC
                EthereumNetwork.ARBITRUM -> CHAIN_ID_ARBITRUM
                EthereumNetwork.OPTIMISM -> CHAIN_ID_OPTIMISM
                else -> CHAIN_ID_ETHEREUM
            }

            val etherscanApi = when (network) {
                EthereumNetwork.SEPOLIA -> sepoliaEtherscanApi
                else -> mainnetEtherscanApi
            }

            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getEthereumTransactions(
                chainId = chainId,
                address = address,
                apiKey = apiKey
            )

            if (response.status == "1") {
                response.result.map { tx ->
                    val valueWei = BigDecimal(tx.value)
                    val valueEth = valueWei.divide(
                        BigDecimal("1000000000000000000"),
                        18,
                        RoundingMode.HALF_UP
                    )

                    Transaction(
                        hash = tx.hash,
                        from = tx.from,
                        to = tx.to,
                        value = tx.value,
                        valueDecimal = valueEth.toPlainString(),
                        gasPrice = tx.gasPrice,
                        gasUsed = tx.gas,
                        timestamp = tx.timestamp.toLong() * 1000,
                        status = when {
                            tx.isError == "1" -> TransactionStatus.FAILED
                            tx.receiptStatus == "1" -> TransactionStatus.SUCCESS
                            else -> TransactionStatus.PENDING
                        },
                        chain = when (network) {
                            EthereumNetwork.SEPOLIA -> ChainType.ETHEREUM_SEPOLIA
                            else -> ChainType.ETHEREUM
                        }
                    )
                }
            } else {
                Log.w("BlockchainRepo", "V2 API error for transactions: ${response.message}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting $network transactions: ${e.message}")
            getSampleTransactions(address, getChainTypeForNetwork(network))
        }
    }

    // Helper: Get simulated balance for demo
    private fun getSimulatedBalance(address: String): BigDecimal {
        // For demo: generate deterministic "balance" based on address
        val hash = address.hashCode().toLong() and 0xFFFFFFFFL
        val simulatedBalance = (hash % 1000L + 500L).toDouble() / 100.0
        return BigDecimal.valueOf(simulatedBalance)
    }

    fun getSampleTransactions(address: String, chain: ChainType): List<Transaction> {
        return listOf(
            Transaction(
                hash = "0x${System.currentTimeMillis().toString(16)}",
                from = "0x742d35Cc6634C0532925a3b844Bc9e",
                to = address,
                value = "1000000000000000000",
                valueDecimal = "1.0",
                gasPrice = "50000000000",
                gasUsed = "21000",
                timestamp = System.currentTimeMillis() - 86400000,
                status = TransactionStatus.SUCCESS,
                chain = chain
            ),
            Transaction(
                hash = "0x${(System.currentTimeMillis() - 1000).toString(16)}",
                from = address,
                to = "0x742d35Cc6634C0532925a3b844Bc9e",
                value = "500000000000000000",
                valueDecimal = "0.5",
                gasPrice = "60000000000",
                gasUsed = "21000",
                timestamp = System.currentTimeMillis() - 172800000,
                status = TransactionStatus.SUCCESS,
                chain = chain
            )
        )
    }

    suspend fun getCurrentGasPrice(network: EthereumNetwork = EthereumNetwork.MAINNET): GasPrice {
        return try {
            val chainId = when (network) {
                EthereumNetwork.SEPOLIA -> CHAIN_ID_SEPOLIA
//                EthereumNetwork.HOLESKY -> CHAIN_ID_HOLESKY
                EthereumNetwork.POLYGON -> CHAIN_ID_POLYGON
                EthereumNetwork.BSC -> CHAIN_ID_BSC
                EthereumNetwork.ARBITRUM -> CHAIN_ID_ARBITRUM
                EthereumNetwork.OPTIMISM -> CHAIN_ID_OPTIMISM
                else -> CHAIN_ID_ETHEREUM
            }

            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getGasPrice(
                chainId = chainId,
                apiKey = apiKey
            )

            GasPrice(
                safe = response.result.SafeGasPrice,
                propose = response.result.ProposeGasPrice,
                fast = response.result.FastGasPrice
            )
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting gas price for $network: ${e.message}")
            GasPrice(
                safe = "30",
                propose = "35",
                fast = "40"
            )
        }
    }

    suspend fun getBitcoinBalance(address: String): BigDecimal {
        return try {
            val utxos = blockstreamApi.getBitcoinUtxos(address)
            val totalSats = utxos.filter { it.status.confirmed }
                .sumOf { it.value }

            BigDecimal(totalSats).divide(BigDecimal("100000000"), 8, RoundingMode.HALF_UP)
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting BTC balance: ${e.message}")
            getSimulatedBalance(address)
        }
    }

    // Address validation
    fun isValidEthereumAddress(address: String): Boolean {
        return address.matches(Regex("^0x[a-fA-F0-9]{40}\$"))
    }

    fun isValidBitcoinAddress(address: String): Boolean {
        return address.matches(Regex("^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,39}\$"))
    }

    fun getBitcoinFeeEstimates(): TransactionFee {
        return try {
            // Using blockstream.info API for fee estimates
            // For demo, we'll return mock data
            TransactionFee(
                chain = ChainType.BITCOIN,
                slow = FeeEstimate(
                    feePerByte = "10",
                    gasPrice = null,
                    totalFee = "2000",
                    totalFeeDecimal = "0.00002",
                    estimatedTime = 3600,
                    priority = FeeLevel.SLOW
                ),
                normal = FeeEstimate(
                    feePerByte = "25",
                    gasPrice = null,
                    totalFee = "5000",
                    totalFeeDecimal = "0.00005",
                    estimatedTime = 600,
                    priority = FeeLevel.NORMAL
                ),
                fast = FeeEstimate(
                    feePerByte = "50",
                    gasPrice = null,
                    totalFee = "10000",
                    totalFeeDecimal = "0.0001",
                    estimatedTime = 120,
                    priority = FeeLevel.FAST
                )
            )
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting BTC fee estimates: ${e.message}")
            // Fallback to demo fees
            getDemoBitcoinFees()
        }
    }

    suspend fun getEthereumGasPrice(network: EthereumNetwork = EthereumNetwork.MAINNET): TransactionFee {
        return try {
            val gasPrice = getCurrentGasPrice(network)
            TransactionFee(
                chain = when (network) {
                    EthereumNetwork.SEPOLIA -> ChainType.ETHEREUM_SEPOLIA
                    else -> ChainType.ETHEREUM
                },
                slow = FeeEstimate(
                    feePerByte = null,
                    gasPrice = gasPrice.safe,
                    totalFee = calculateEthFee(gasPrice.safe, 21000),
                    totalFeeDecimal = calculateEthFeeDecimal(gasPrice.safe, 21000),
                    estimatedTime = 900,
                    priority = FeeLevel.SLOW
                ),
                normal = FeeEstimate(
                    feePerByte = null,
                    gasPrice = gasPrice.propose,
                    totalFee = calculateEthFee(gasPrice.propose, 21000),
                    totalFeeDecimal = calculateEthFeeDecimal(gasPrice.propose, 21000),
                    estimatedTime = 300,
                    priority = FeeLevel.NORMAL
                ),
                fast = FeeEstimate(
                    feePerByte = null,
                    gasPrice = gasPrice.fast,
                    totalFee = calculateEthFee(gasPrice.fast, 21000),
                    totalFeeDecimal = calculateEthFeeDecimal(gasPrice.fast, 21000),
                    estimatedTime = 60,
                    priority = FeeLevel.FAST
                )
            )
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting ETH gas price for $network: ${e.message}")
            // Fallback to demo fees
            getDemoEthereumFees()
        }
    }

    suspend fun broadcastEthereumTransaction(
        rawTx: String,
        network: EthereumNetwork = EthereumNetwork.MAINNET
    ): BroadcastResult {
        return try {
            val chainId = when (network) {
                EthereumNetwork.SEPOLIA -> CHAIN_ID_SEPOLIA
//                EthereumNetwork.HOLESKY -> CHAIN_ID_HOLESKY
                EthereumNetwork.POLYGON -> CHAIN_ID_POLYGON
                EthereumNetwork.BSC -> CHAIN_ID_BSC
                EthereumNetwork.ARBITRUM -> CHAIN_ID_ARBITRUM
                EthereumNetwork.OPTIMISM -> CHAIN_ID_OPTIMISM
                else -> CHAIN_ID_ETHEREUM
            }

            val etherscanApi = when (network) {
                EthereumNetwork.SEPOLIA -> sepoliaEtherscanApi
                else -> mainnetEtherscanApi
            }

            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.broadcastTransaction(
                chainId = chainId,
                hex = rawTx,
                apiKey = apiKey
            )

            if (response.result.isNotEmpty() && !response.result.startsWith("Error")) {
                BroadcastResult(
                    success = true,
                    hash = response.result,
                    chain = getChainTypeForNetwork(network)
                )
            } else {
                BroadcastResult(
                    success = false,
                    error = response.result,
                    chain = getChainTypeForNetwork(network)
                )
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error broadcasting to $network: ${e.message}")
            BroadcastResult(
                success = false,
                error = e.message,
                chain = getChainTypeForNetwork(network)
            )
        }
    }

    private fun getChainTypeForNetwork(network: EthereumNetwork): ChainType {
        return when (network) {
            EthereumNetwork.SEPOLIA -> ChainType.ETHEREUM_SEPOLIA
//            EthereumNetwork.HOLESKY -> ChainType.ETHEREUM_HOLESKY
            EthereumNetwork.POLYGON -> ChainType.POLYGON
            EthereumNetwork.BSC -> ChainType.BINANCE_SMART_CHAIN
            EthereumNetwork.ARBITRUM -> ChainType.ARBITRUM
            EthereumNetwork.OPTIMISM -> ChainType.OPTIMISM
            else -> ChainType.ETHEREUM
        }
    }

    suspend fun broadcastBitcoinTransactionReal(rawTx: String): BroadcastResult {
        return try {
            Log.d("BlockchainRepo", "Broadcasting Bitcoin transaction...")
            Log.d("BlockchainRepo", "Raw TX (first 100 chars): ${rawTx.take(100)}...")

            val response = bitcoinBroadcastApi.broadcastBitcoinTransaction(rawTx)

            if (response.isSuccessful) {
                val txHash = response.body() ?: "unknown"
                Log.d("BlockchainRepo", " Bitcoin broadcast successful! Hash: $txHash")

                BroadcastResult(
                    success = true,
                    hash = txHash,
                    chain = ChainType.BITCOIN
                )
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e("BlockchainRepo", " Bitcoin broadcast failed: $errorBody")

                BroadcastResult(
                    success = false,
                    error = "HTTP ${response.code()}: $errorBody",
                    chain = ChainType.BITCOIN
                )
            }

        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Bitcoin broadcast error: ${e.message}", e)
            BroadcastResult(
                success = false,
                error = e.message,
                chain = ChainType.BITCOIN
            )
        }
    }

    /**
     * Get real Bitcoin fee estimates from Mempool.space
     */
    suspend fun getRealBitcoinFeeEstimates(): TransactionFee {
        return try {
            Log.d("BlockchainRepo", "Getting real Bitcoin fees from Mempool.space...")
            val fees = bitcoinBroadcastApi.getBitcoinFees()

            TransactionFee(
                chain = ChainType.BITCOIN,
                slow = FeeEstimate(
                    feePerByte = fees.hourFee.toString(),
                    gasPrice = null,
                    totalFee = calculateBitcoinFee(fees.hourFee, 250), // Assuming 250 vbytes
                    totalFeeDecimal = calculateBitcoinFeeDecimal(fees.hourFee, 250),
                    estimatedTime = 3600, // 1 hour
                    priority = FeeLevel.SLOW
                ),
                normal = FeeEstimate(
                    feePerByte = fees.halfHourFee.toString(),
                    gasPrice = null,
                    totalFee = calculateBitcoinFee(fees.halfHourFee, 250),
                    totalFeeDecimal = calculateBitcoinFeeDecimal(fees.halfHourFee, 250),
                    estimatedTime = 1800, // 30 minutes
                    priority = FeeLevel.NORMAL
                ),
                fast = FeeEstimate(
                    feePerByte = fees.fastestFee.toString(),
                    gasPrice = null,
                    totalFee = calculateBitcoinFee(fees.fastestFee, 250),
                    totalFeeDecimal = calculateBitcoinFeeDecimal(fees.fastestFee, 250),
                    estimatedTime = 300, // 5 minutes
                    priority = FeeLevel.FAST
                )
            )

        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Failed to get real Bitcoin fees: ${e.message}")
            // Fallback to demo fees
            getDemoBitcoinFees()
        }
    }

    private fun calculateBitcoinFee(feePerByte: Int, sizeBytes: Int): String {
        return (feePerByte * sizeBytes).toString()
    }

    private fun calculateBitcoinFeeDecimal(feePerByte: Int, sizeBytes: Int): String {
        val feeSats = feePerByte * sizeBytes
        return BigDecimal(feeSats).divide(BigDecimal("100000000"), 8, RoundingMode.HALF_UP).toPlainString()
    }

    private fun calculateConfirmations(status: BlockstreamStatus?): Int {
        if (status == null || !status.confirmed) return 0
        return 3 // For demo
    }

    // Get Ethereum Nonce
    suspend fun getEthereumNonce(
        address: String,
        network: EthereumNetwork = EthereumNetwork.MAINNET
    ): Int {
        return try {
            val chainId = when (network) {
                EthereumNetwork.SEPOLIA -> CHAIN_ID_SEPOLIA
                else -> CHAIN_ID_ETHEREUM
            }

            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getTransactionCount(
                chainId = chainId,
                address = address,
                apiKey = apiKey
            )

            // JSON-RPC response: just check if result is valid
            if (response.result.isNotEmpty() && response.result != "0x") {
                // Convert hex to decimal (remove "0x" prefix)
                val hexResult = if (response.result.startsWith("0x")) {
                    response.result.substring(2)
                } else {
                    response.result
                }

                if (hexResult.isNotEmpty()) {
                    hexResult.toInt(16)
                } else {
                    0
                }
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting ETH nonce for $network: ${e.message}")
            0 // Return 0 for demo
        }
    }

    private fun getDemoBitcoinFees(): TransactionFee {
        return TransactionFee(
            chain = ChainType.BITCOIN,
            slow = FeeEstimate(
                feePerByte = "10",
                gasPrice = null,
                totalFee = "2000",
                totalFeeDecimal = "0.00002",
                estimatedTime = 3600,
                priority = FeeLevel.SLOW
            ),
            normal = FeeEstimate(
                feePerByte = "25",
                gasPrice = null,
                totalFee = "5000",
                totalFeeDecimal = "0.00005",
                estimatedTime = 600,
                priority = FeeLevel.NORMAL
            ),
            fast = FeeEstimate(
                feePerByte = "50",
                gasPrice = null,
                totalFee = "10000",
                totalFeeDecimal = "0.0001",
                estimatedTime = 120,
                priority = FeeLevel.FAST
            )
        )
    }

    private fun getDemoEthereumFees(): TransactionFee {
        return TransactionFee(
            chain = ChainType.ETHEREUM,
            slow = FeeEstimate(
                feePerByte = null,
                gasPrice = "20",
                totalFee = "420000000000000",
                totalFeeDecimal = "0.00042",
                estimatedTime = 900,
                priority = FeeLevel.SLOW
            ),
            normal = FeeEstimate(
                feePerByte = null,
                gasPrice = "30",
                totalFee = "630000000000000",
                totalFeeDecimal = "0.00063",
                estimatedTime = 300,
                priority = FeeLevel.NORMAL
            ),
            fast = FeeEstimate(
                feePerByte = null,
                gasPrice = "50",
                totalFee = "1050000000000000",
                totalFeeDecimal = "0.00105",
                estimatedTime = 60,
                priority = FeeLevel.FAST
            )
        )
    }

    private fun getMockUTXOs(address: String): List<UTXO> {
        return listOf(
            UTXO(
                txid = "mock_txid_${System.currentTimeMillis()}",
                vout = 0,
                amount = 100000000, // 1 BTC in satoshis
                scriptPubKey = "0014${address.takeLast(40)}",
                confirmations = 3
            )
        )
    }

    private fun calculateEthFee(gasPrice: String, gasLimit: Int): String {
        val gasPriceWei = BigDecimal(gasPrice).multiply(BigDecimal("1000000000"))
        return gasPriceWei.multiply(BigDecimal(gasLimit)).toPlainString()
    }

    private fun calculateEthFeeDecimal(gasPrice: String, gasLimit: Int): String {
        val feeWei = calculateEthFee(gasPrice, gasLimit).toBigDecimal()
        return feeWei.divide(BigDecimal("1000000000000000000"), 8, RoundingMode.HALF_UP).toPlainString()
    }
}

private fun ChainId.toChainType(): ChainType {
    return when (this) {
        ChainId.ETHEREUM_MAINNET -> ChainType.ETHEREUM
        ChainId.POLYGON -> ChainType.POLYGON
        ChainId.BINANCE_SMART_CHAIN -> ChainType.BINANCE_SMART_CHAIN
        ChainId.ARBITRUM -> ChainType.ARBITRUM
        ChainId.OPTIMISM -> ChainType.OPTIMISM
    }
}