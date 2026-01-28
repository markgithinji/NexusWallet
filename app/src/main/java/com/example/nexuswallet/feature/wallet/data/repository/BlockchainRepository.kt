package com.example.nexuswallet.feature.wallet.data.repository

import android.content.Context
import android.util.Log
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
import com.example.nexuswallet.feature.wallet.data.repository.Config.COVALENT_API_KEY
import com.example.nexuswallet.feature.wallet.data.repository.Config.ETHERSCAN_API_KEY
import kotlin.collections.filter

class BlockchainRepository @Inject constructor(
    private val etherscanApi: EtherscanApiService,
    private val blockstreamApi: BlockstreamApiService,
    private val covalentApi: CovalentApiService
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getEthereumBalance(address: String): BigDecimal {
        return try {
            val response = etherscanApi.getEthereumBalance(
                address = address,
                apiKey = ETHERSCAN_API_KEY
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
                apiKey = COVALENT_API_KEY
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
                apiKey = ETHERSCAN_API_KEY
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

    // Helper: Get sample transactions
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
                apiKey = ETHERSCAN_API_KEY
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