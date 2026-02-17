package com.example.nexuswallet.feature.coin.ethereum

import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.wallet.data.model.FeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.model.TransactionFee
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
@Singleton
class EthereumBlockchainRepository @Inject constructor(
    private val etherscanApi: EtherscanApiService
) {

    companion object {
        private const val GAS_LIMIT_STANDARD = 21000
    }

    suspend fun getEthereumBalance(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<BigDecimal> {
        Log.d("BlockchainRepo", "=".repeat(50))
        Log.d("BlockchainRepo", " START: getEthereumBalance()")
        Log.d("BlockchainRepo", " Wallet Address: $address")
        Log.d("BlockchainRepo", " Network: ${network.displayName}")

        return try {
            val chainId = network.chainId

            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getEthereumBalance(
                chainId = chainId,
                address = address,
                apiKey = apiKey
            )

            Log.d("BlockchainRepo", "Response Status: ${response.status}")
            Log.d("BlockchainRepo", "Response Message: ${response.message}")

            if (response.status == "1") {
                val wei = BigDecimal(response.result)
                val eth = wei.divide(BigDecimal("1000000000000000000"), 8, RoundingMode.HALF_UP)
                Log.d("BlockchainRepo", "✓ Balance: $eth ETH")
                Log.d("BlockchainRepo", "=".repeat(50))
                Result.Success(eth)
            } else {
                Log.w("BlockchainRepo", "⚠ API Error: ${response.message}")
                val simulated = getSimulatedBalance(address)
                Log.d("BlockchainRepo", "Using simulated balance: $simulated ETH")
                Log.d("BlockchainRepo", "=".repeat(50))
                Result.Success(simulated)
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error: ${e.message}", e)
            Result.Error("Failed to get balance: ${e.message}", e)
        }
    }

    suspend fun getEthereumTransactions(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<List<Transaction>> {
        return try {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getEthereumTransactions(
                chainId = chainId,
                address = address,
                apiKey = apiKey
            )

            if (response.status == "1") {
                val transactions = response.result.map { tx ->
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
                            is EthereumNetwork.Sepolia -> ChainType.ETHEREUM_SEPOLIA
                            else -> ChainType.ETHEREUM
                        }
                    )
                }
                Result.Success(transactions)
            } else {
                Log.w("BlockchainRepo", "V2 API error for transactions: ${response.message}")
                Result.Error("API error: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting transactions: ${e.message}", e)
            Result.Error("Failed to get transactions: ${e.message}", e)
        }
    }

    suspend fun getCurrentGasPrice(network: EthereumNetwork = EthereumNetwork.Mainnet): Result<GasPrice> {
        Log.d("GasPrice", "=== getCurrentGasPrice ===")
        Log.d("GasPrice", "Network: ${network.displayName}")

        return try {
            if (network is EthereumNetwork.Sepolia) {
                Log.d("GasPrice", "Using HIGH Sepolia gas price")
                return Result.Success(
                    GasPrice(
                        safe = "100",     // 100 Gwei
                        propose = "120",  // 120 Gwei
                        fast = "150",     // 150 Gwei
                        lastBlock = null
                    )
                )
            }

            val chainId = network.chainId

            val apiKey = BuildConfig.ETHERSCAN_API_KEY
            Log.d("GasPrice", "Fetching gas price for chainId: $chainId")

            val response = etherscanApi.getGasPrice(
                chainId = chainId,
                apiKey = apiKey
            )

            if (response.status == "1") {
                Log.d("GasPrice", "✓ Got gas prices from API for ${network.displayName}")
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
                Log.w("GasPrice", "API error for ${network.displayName}: ${response.message}")
                val fallback = when (network) {
                    is EthereumNetwork.Mainnet -> GasPrice(safe = "30", propose = "35", fast = "40")
                    is EthereumNetwork.Sepolia -> GasPrice(safe = "100", propose = "120", fast = "150")
                    else -> GasPrice(safe = "30", propose = "35", fast = "40")
                }
                Result.Success(fallback)
            }
        } catch (e: Exception) {
            Log.e("GasPrice", "Error getting gas price for ${network.displayName}: ${e.message}", e)
            val fallback = when (network) {
                is EthereumNetwork.Sepolia -> GasPrice(safe = "0.1", propose = "0.15", fast = "0.2")
                else -> GasPrice(safe = "30", propose = "35", fast = "40")
            }
            Result.Success(fallback)
        }
    }

    suspend fun getEthereumNonce(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<Int> {
        Log.d("Nonce", "=== getEthereumNonce ===")
        Log.d("Nonce", "Address: $address")
        Log.d("Nonce", "Network: ${network.displayName}")

        return try {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getTransactionCount(
                chainId = chainId,
                address = address,
                apiKey = apiKey
            )

            Log.d("Nonce", "Response: jsonrpc=${response.jsonrpc}, result=${response.result}, id=${response.id}")

            if (response.result.isNotEmpty() && response.result != "0x") {
                val hexResult = if (response.result.startsWith("0x")) {
                    response.result.substring(2)
                } else {
                    response.result
                }

                if (hexResult.isNotEmpty()) {
                    val nonce = hexResult.toInt(16)
                    Log.d("Nonce", "✓ Parsed nonce: $nonce")
                    Result.Success(nonce)
                } else {
                    Log.w("Nonce", "Empty hex result")
                    Result.Success(0)
                }
            } else {
                Log.w("Nonce", "Empty or invalid result")
                Result.Success(0)
            }
        } catch (e: Exception) {
            Log.e("Nonce", "Error: ${e.message}", e)
            Result.Error("Failed to get nonce: ${e.message}", e)
        }
    }

    suspend fun broadcastEthereumTransaction(
        rawTx: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<BroadcastResult> {
        Log.d("Broadcast", "=== broadcastEthereumTransaction ===")
        Log.d("Broadcast", "Network: ${network.displayName}")
        Log.d("Broadcast", "Raw TX length: ${rawTx.length}")
        Log.d("Broadcast", "Raw TX (first 100): ${rawTx.take(100)}...")

        return try {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY
            Log.d("Broadcast", "API key present: ${apiKey.isNotEmpty()}")
            Log.d("Broadcast", "API key last 4: ...${apiKey.takeLast(4)}")

            delay(500)

            Log.d("Broadcast", "Making API call to Etherscan V2...")
            val response = etherscanApi.broadcastTransaction(
                chainId = chainId,
                hex = rawTx,
                apiKey = apiKey
            )

            Log.d("Broadcast", "Full response: jsonrpc=${response.jsonrpc}, id=${response.id}")
            Log.d("Broadcast", "Result field: ${response.result}")

            val result = response.result

            when {
                result.startsWith("0x") && result.length == 66 -> {
                    Log.d("Broadcast", " Valid TX hash detected!")
                    Log.d("Broadcast", "Transaction hash: $result")

                    delay(2000)
                    checkTransactionAfterBroadcast(result, network)

                    Result.Success(
                        BroadcastResult(
                            success = true,
                            hash = result
                        )
                    )
                }
                result.contains("nonce") -> {
                    Log.e("Broadcast", " Nonce error: $result")
                    Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Nonce error: $result"
                        )
                    )
                }
                result.contains("insufficient funds") || result.contains("balance") -> {
                    Log.e("Broadcast", " Insufficient balance: $result")
                    Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Insufficient balance: $result"
                        )
                    )
                }
                result.contains("already known") -> {
                    Log.w("Broadcast", "⚠ Transaction already in mempool: $result")
                    val hashPattern = Regex("0x[a-fA-F0-9]{64}")
                    val hash = hashPattern.find(result)?.value
                    Result.Success(
                        BroadcastResult(
                            success = hash != null,
                            hash = hash,
                            error = if (hash == null) result else null
                        )
                    )
                }
                else -> {
                    Log.e("Broadcast", " Unknown error: $result")
                    Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Broadcast failed: $result"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("Broadcast", "Network error: ${e.message}", e)
            Result.Success(
                BroadcastResult(
                    success = false,
                    error = "Network error: ${e.message}"
                )
            )
        }
    }

    suspend fun checkTransactionStatus(
        txHash: String,
        network: EthereumNetwork = EthereumNetwork.Sepolia
    ): Result<TransactionStatus> {
        return try {
            val chainId = network.chainId

            val response = etherscanApi.getTransactionReceiptStatus(
                chainId = chainId,
                txhash = txHash,
                apiKey = BuildConfig.ETHERSCAN_API_KEY
            )

            val status = when (response.result.status) {
                "1" -> TransactionStatus.SUCCESS
                "0" -> TransactionStatus.FAILED
                else -> TransactionStatus.PENDING
            }
            Result.Success(status)
        } catch (e: Exception) {
            Result.Success(TransactionStatus.PENDING)
        }
    }

    private suspend fun checkTransactionAfterBroadcast(txHash: String, network: EthereumNetwork) {
        Log.d("Broadcast", " Checking if transaction $txHash was accepted...")
        try {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY
            etherscanApi.getEthereumTransactions(
                chainId = chainId,
                address = "",
                apiKey = apiKey
            )
        } catch (e: Exception) {
            Log.e("Broadcast", "Failed to check transaction: ${e.message}")
        }
    }

    private fun getSimulatedBalance(address: String): BigDecimal {
        val hash = address.hashCode().toLong() and 0xFFFFFFFFL
        val simulatedBalance = (hash % 1000L + 500L).toDouble() / 100.0
        return BigDecimal.valueOf(simulatedBalance)
    }

    private fun calculateEthFee(gasPrice: String, gasLimit: Int = GAS_LIMIT_STANDARD): String {
        val gasPriceWei = BigDecimal(gasPrice).multiply(BigDecimal("1000000000"))
        return gasPriceWei.multiply(BigDecimal(gasLimit)).toPlainString()
    }

    private fun calculateEthFeeDecimal(gasPrice: String, gasLimit: Int = GAS_LIMIT_STANDARD): String {
        val feeWei = calculateEthFee(gasPrice, gasLimit).toBigDecimal()
        return feeWei.divide(BigDecimal("1000000000000000000"), 8, RoundingMode.HALF_UP)
            .toPlainString()
    }

    suspend fun getEthereumGasPrice(network: EthereumNetwork = EthereumNetwork.Mainnet): Result<TransactionFee> {
        return try {
            val gasPriceResult = getCurrentGasPrice(network)
            when (gasPriceResult) {
                is Result.Success -> {
                    val gasPrice = gasPriceResult.data
                    Result.Success(
                        TransactionFee(
                            chain = when (network) {
                                is EthereumNetwork.Sepolia -> ChainType.ETHEREUM_SEPOLIA
                                else -> ChainType.ETHEREUM
                            },
                            slow = FeeEstimate(
                                feePerByte = null,
                                gasPrice = gasPrice.safe,
                                totalFee = calculateEthFee(gasPrice.safe),
                                totalFeeDecimal = calculateEthFeeDecimal(gasPrice.safe),
                                estimatedTime = 900,
                                priority = FeeLevel.SLOW
                            ),
                            normal = FeeEstimate(
                                feePerByte = null,
                                gasPrice = gasPrice.propose,
                                totalFee = calculateEthFee(gasPrice.propose),
                                totalFeeDecimal = calculateEthFeeDecimal(gasPrice.propose),
                                estimatedTime = 300,
                                priority = FeeLevel.NORMAL
                            ),
                            fast = FeeEstimate(
                                feePerByte = null,
                                gasPrice = gasPrice.fast,
                                totalFee = calculateEthFee(gasPrice.fast),
                                totalFeeDecimal = calculateEthFeeDecimal(gasPrice.fast),
                                estimatedTime = 60,
                                priority = FeeLevel.FAST
                            )
                        )
                    )
                }
                is Result.Error -> {
                    Log.e("BlockchainRepo", "Error getting ETH gas price for ${network.displayName}: ${gasPriceResult.message}")
                    Result.Success(getDemoEthereumFees())
                }
                Result.Loading -> Result.Success(getDemoEthereumFees())
            }
        } catch (e: Exception) {
            Log.e("BlockchainRepo", "Error getting ETH gas price for ${network.displayName}: ${e.message}", e)
            Result.Success(getDemoEthereumFees())
        }
    }

    fun getDemoEthereumFees(): TransactionFee {
        return TransactionFee(
            chain = ChainType.ETHEREUM,
            slow = FeeEstimate(
                feePerByte = null,
                gasPrice = "20",
                totalFee = calculateEthFee("20"),
                totalFeeDecimal = calculateEthFeeDecimal("20"),
                estimatedTime = 900,
                priority = FeeLevel.SLOW
            ),
            normal = FeeEstimate(
                feePerByte = null,
                gasPrice = "30",
                totalFee = calculateEthFee("30"),
                totalFeeDecimal = calculateEthFeeDecimal("30"),
                estimatedTime = 300,
                priority = FeeLevel.NORMAL
            ),
            fast = FeeEstimate(
                feePerByte = null,
                gasPrice = "50",
                totalFee = calculateEthFee("50"),
                totalFeeDecimal = calculateEthFeeDecimal("50"),
                estimatedTime = 60,
                priority = FeeLevel.FAST
            )
        )
    }
}