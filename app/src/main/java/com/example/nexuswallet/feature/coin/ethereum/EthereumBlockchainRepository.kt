package com.example.nexuswallet.feature.coin.ethereum

import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.wallet.data.model.BroadcastResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
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
import java.math.BigInteger
@Singleton
class EthereumBlockchainRepository @Inject constructor(
    private val etherscanApi: EtherscanApiService
) {

    companion object {
        private const val GAS_LIMIT_STANDARD = 21000L
        private const val WEI_PER_GWEI = 1_000_000_000L
    }

    suspend fun getEthereumBalance(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<BigDecimal> {
        return try {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getEthereumBalance(
                chainId = chainId,
                address = address,
                apiKey = apiKey
            )

            if (response.status == "1") {
                val wei = BigDecimal(response.result)
                val eth = wei.divide(BigDecimal("1000000000000000000"), 8, RoundingMode.HALF_UP)
                Result.Success(eth)
            } else {
                val simulated = getSimulatedBalance(address)
                Result.Success(simulated)
            }
        } catch (e: Exception) {
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
                Result.Error("API error: ${response.message}")
            }
        } catch (e: Exception) {
            Result.Error("Failed to get transactions: ${e.message}", e)
        }
    }

    suspend fun getCurrentGasPrice(network: EthereumNetwork = EthereumNetwork.Mainnet): Result<GasPrice> {
        return try {
            if (network is EthereumNetwork.Sepolia) {
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
                val fallback = when (network) {
                    is EthereumNetwork.Mainnet -> GasPrice(safe = "30", propose = "35", fast = "40")
                    is EthereumNetwork.Sepolia -> GasPrice(safe = "100", propose = "120", fast = "150")
                }
                Result.Success(fallback)
            }
        } catch (e: Exception) {
            val fallback = when (network) {
                is EthereumNetwork.Sepolia -> GasPrice(safe = "0.1", propose = "0.15", fast = "0.2")
                else -> GasPrice(safe = "30", propose = "35", fast = "40")
            }
            Result.Success(fallback)
        }
    }

    /**
     * Get Ethereum fee estimate based on priority
     */
    suspend fun getFeeEstimate(feeLevel: FeeLevel = FeeLevel.NORMAL): Result<EthereumFeeEstimate> {
        return try {
            val gasPriceResult = getCurrentGasPrice(EthereumNetwork.Mainnet)

            when (gasPriceResult) {
                is Result.Success -> {
                    val gasPrice = gasPriceResult.data

                    val (gasPriceGwei, estimatedTime) = when (feeLevel) {
                        FeeLevel.SLOW -> gasPrice.safe to 900
                        FeeLevel.NORMAL -> gasPrice.propose to 300
                        FeeLevel.FAST -> gasPrice.fast to 60
                    }

                    val gasPriceWei = (BigDecimal(gasPriceGwei) * BigDecimal(WEI_PER_GWEI)).toBigInteger()
                    val totalFeeWei = gasPriceWei.multiply(BigInteger.valueOf(GAS_LIMIT_STANDARD))

                    val totalFeeEth = BigDecimal(totalFeeWei).divide(
                        BigDecimal("1000000000000000000"),
                        8,
                        RoundingMode.HALF_UP
                    ).toPlainString()

                    Result.Success(
                        EthereumFeeEstimate(
                            gasPriceGwei = gasPriceGwei,
                            gasPriceWei = gasPriceWei.toString(),
                            gasLimit = GAS_LIMIT_STANDARD,
                            totalFeeWei = totalFeeWei.toString(),
                            totalFeeEth = totalFeeEth,
                            estimatedTime = estimatedTime,
                            priority = feeLevel,
                            baseFee = gasPrice.baseFee,
                            isEIP1559 = gasPrice.baseFee != null
                        )
                    )
                }
                is Result.Error -> {
                    Result.Success(getDefaultFeeEstimate(feeLevel))
                }
                Result.Loading -> Result.Success(getDefaultFeeEstimate(feeLevel))
            }
        } catch (e: Exception) {
            Result.Success(getDefaultFeeEstimate(feeLevel))
        }
    }

    private fun getDefaultFeeEstimate(feeLevel: FeeLevel): EthereumFeeEstimate {
        val (gasPriceGwei, estimatedTime) = when (feeLevel) {
            FeeLevel.SLOW -> "20" to 900
            FeeLevel.NORMAL -> "30" to 300
            FeeLevel.FAST -> "50" to 60
        }

        val gasPriceWei = (BigDecimal(gasPriceGwei) * BigDecimal(WEI_PER_GWEI)).toBigInteger()
        val totalFeeWei = gasPriceWei.multiply(BigInteger.valueOf(GAS_LIMIT_STANDARD))

        val totalFeeEth = BigDecimal(totalFeeWei).divide(
            BigDecimal("1000000000000000000"),
            8,
            RoundingMode.HALF_UP
        ).toPlainString()

        return EthereumFeeEstimate(
            gasPriceGwei = gasPriceGwei,
            gasPriceWei = gasPriceWei.toString(),
            gasLimit = GAS_LIMIT_STANDARD,
            totalFeeWei = totalFeeWei.toString(),
            totalFeeEth = totalFeeEth,
            estimatedTime = estimatedTime,
            priority = feeLevel,
            baseFee = null,
            isEIP1559 = false
        )
    }

    suspend fun getEthereumNonce(
        address: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<Int> {
        return try {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            val response = etherscanApi.getTransactionCount(
                chainId = chainId,
                address = address,
                apiKey = apiKey
            )

            if (response.result.isNotEmpty() && response.result != "0x") {
                val hexResult = if (response.result.startsWith("0x")) {
                    response.result.substring(2)
                } else {
                    response.result
                }

                if (hexResult.isNotEmpty()) {
                    val nonce = hexResult.toInt(16)
                    Result.Success(nonce)
                } else {
                    Result.Success(0)
                }
            } else {
                Result.Success(0)
            }
        } catch (e: Exception) {
            Result.Error("Failed to get nonce: ${e.message}", e)
        }
    }

    suspend fun broadcastEthereumTransaction(
        rawTx: String,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<BroadcastResult> {
        return try {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY

            delay(500)

            val response = etherscanApi.broadcastTransaction(
                chainId = chainId,
                hex = rawTx,
                apiKey = apiKey
            )

            val result = response.result

            when {
                result.startsWith("0x") && result.length == 66 -> {
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
                    Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Nonce error: $result"
                        )
                    )
                }
                result.contains("insufficient funds") || result.contains("balance") -> {
                    Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Insufficient balance: $result"
                        )
                    )
                }
                result.contains("already known") -> {
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
                    Result.Success(
                        BroadcastResult(
                            success = false,
                            error = "Broadcast failed: $result"
                        )
                    )
                }
            }
        } catch (e: Exception) {
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
        try {
            val chainId = network.chainId
            val apiKey = BuildConfig.ETHERSCAN_API_KEY
            etherscanApi.getEthereumTransactions(
                chainId = chainId,
                address = "",
                apiKey = apiKey
            )
        } catch (e: Exception) {
            // Silently ignore
        }
    }

    private fun getSimulatedBalance(address: String): BigDecimal {
        val hash = address.hashCode().toLong() and 0xFFFFFFFFL
        val simulatedBalance = (hash % 1000L + 500L).toDouble() / 100.0
        return BigDecimal.valueOf(simulatedBalance)
    }
}