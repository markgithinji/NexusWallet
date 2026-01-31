package com.example.nexuswallet.feature.wallet.data.repository

import android.content.Context
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
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.collections.filter

class BlockchainRepository @Inject constructor(
    private val etherscanApi: EtherscanApiService,
    private val blockstreamApi: BlockstreamApiService,
    private val covalentApi: CovalentApiService,
    private val bitcoinBroadcastApi: BitcoinBroadcastApiService
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getEthereumBalance(address: String): BigDecimal {
        return try {
            val response = etherscanApi.getEthereumBalance(
                address = address,
                apiKey = BuildConfig.ETHERSCAN_API_KEY
            )

            if (response.status == "1") {
                val wei = BigDecimal(response.result)
                wei.divide(BigDecimal("1000000000000000000"), 8, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting ETH balance: ${e.message}")
            getSimulatedBalance(address) // Fallback for demo
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

    // Ethereum Transactions
    suspend fun getEthereumTransactions(address: String): List<Transaction> {
        return try {
            val response = etherscanApi.getEthereumTransactions(
                address = address,
                apiKey = BuildConfig.ETHERSCAN_API_KEY
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
                        chain = ChainType.ETHEREUM
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting ETH transactions: ${e.message}")
            getSampleTransactions(address, ChainType.ETHEREUM)
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

    // Network utilities
    suspend fun getCurrentGasPrice(): GasPrice {
        return try {
            val response = etherscanApi.getGasPrice(
                apiKey = BuildConfig.ETHERSCAN_API_KEY
            )

            GasPrice(
                safe = response.result.SafeGasPrice,
                propose = response.result.ProposeGasPrice,
                fast = response.result.FastGasPrice
            )
        } catch (e: Exception) {
            GasPrice(
                safe = "30",
                propose = "35",
                fast = "40"
            )
        }
    }

    // Address validation
    fun isValidEthereumAddress(address: String): Boolean {
        return address.matches(Regex("^0x[a-fA-F0-9]{40}\$"))
    }

    fun isValidBitcoinAddress(address: String): Boolean {
        return address.matches(Regex("^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,39}\$"))
    }

    suspend fun getBitcoinFeeEstimates(): TransactionFee {
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

    // Ethereum Gas Price
    suspend fun getEthereumGasPrice(): TransactionFee {
        return try {
            val gasPrice = getCurrentGasPrice()
            TransactionFee(
                chain = ChainType.ETHEREUM,
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
            Log.e("BlockchainRepo", "Error getting ETH gas price: ${e.message}")
            // Fallback to demo fees
            getDemoEthereumFees()
        }
    }

    suspend fun broadcastEthereumTransaction(rawTx: String): BroadcastResult {
        return try {
            val response = etherscanApi.broadcastTransaction(
                hex = rawTx,
                apiKey = BuildConfig.ETHERSCAN_API_KEY
            )

            if (response.result.isNotEmpty() && !response.result.startsWith("Error")) {
                BroadcastResult(
                    success = true,
                    hash = response.result,
                    chain = ChainType.ETHEREUM
                )
            } else {
                BroadcastResult(
                    success = false,
                    error = response.result,
                    chain = ChainType.ETHEREUM
                )
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error broadcasting ETH transaction: ${e.message}")
            BroadcastResult(
                success = false,
                error = e.message,
                chain = ChainType.ETHEREUM
            )
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

    // Get Bitcoin UTXOs (for transaction building)
    suspend fun getBitcoinUTXOs(address: String): List<UTXO> {
        return try {
            val utxos = blockstreamApi.getBitcoinUtxos(address)
            utxos.map { utxo ->
                UTXO(
                    txid = utxo.txId,
                    vout = utxo.vout,
                    amount = utxo.value,
                    scriptPubKey = utxo.scriptPubKey ?: "",
                    confirmations = calculateConfirmations(utxo.status)
                )
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting BTC UTXOs: ${e.message}")
            // Return mock UTXOs for demo
            getMockUTXOs(address)
        }
    }

    private fun calculateConfirmations(status: BlockstreamStatus?): Int {
        if (status == null || !status.confirmed) return 0

        // For demo purposes, we'll return a fixed number
        // In production, we'll get current block height from API
        return 3
    }
    // Get Ethereum Nonce
    suspend fun getEthereumNonce(address: String): Int {
        return try {
            val response = etherscanApi.getTransactionCount(
                address = address,
                apiKey = BuildConfig.ETHERSCAN_API_KEY
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
            Log.e("BlockchainRepo", "Error getting ETH nonce: ${e.message}")
            0 // Return 0 for demo
        }
    }

    // Validation with chain parameter
    suspend fun validateAddress(address: String, chain: ChainType): Boolean {
        return when (chain) {
            ChainType.BITCOIN -> isValidBitcoinAddress(address)
            ChainType.ETHEREUM -> isValidEthereumAddress(address)
            else -> true // Accept all other chains for demo
        }
    }

    // === HELPER METHODS ===

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

fun getSampleTransactions(address: String, chain: ChainType): List<Transaction> {
    return listOf(
        Transaction(
            hash = when (chain) {
                ChainType.BITCOIN -> "btc_${System.currentTimeMillis().toString(16)}"
                ChainType.SOLANA -> "sol_${System.currentTimeMillis().toString(16)}"
                else -> "0x${System.currentTimeMillis().toString(16)}"
            },
            from = when (chain) {
                ChainType.BITCOIN -> "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"
                ChainType.SOLANA -> "SolanaSender123456789012345678901234567890"
                else -> "0x742d35Cc6634C0532925a3b844Bc9e"
            },
            to = address,
            value = when (chain) {
                ChainType.BITCOIN -> "150000000"
                ChainType.SOLANA -> "5000000000"
                else -> "1000000000000000000"
            },
            valueDecimal = when (chain) {
                ChainType.BITCOIN -> "1.5"
                ChainType.SOLANA -> "5.0"
                else -> "1.0"
            },
            gasPrice = when (chain) {
                ChainType.BITCOIN, ChainType.SOLANA -> null
                else -> "50000000000"
            },
            gasUsed = when (chain) {
                ChainType.BITCOIN, ChainType.SOLANA -> null
                else -> "21000"
            },
            timestamp = System.currentTimeMillis() - 86400000,
            status = TransactionStatus.SUCCESS,
            chain = chain
        )
    )
}

// Helper extension
private fun ChainId.toChainType(): ChainType {
    return when (this) {
        ChainId.ETHEREUM_MAINNET -> ChainType.ETHEREUM
        ChainId.POLYGON -> ChainType.POLYGON
        ChainId.BINANCE_SMART_CHAIN -> ChainType.BINANCE_SMART_CHAIN
        ChainId.ARBITRUM -> ChainType.ARBITRUM
        ChainId.OPTIMISM -> ChainType.OPTIMISM
    }
}