package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.model.ChainSyncError
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject

class SyncEVMBalancesUseCase @Inject constructor(
    private val balanceDataSource: BalanceDataSource,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        walletId: String,
        tokens: List<EVMToken>,
        prices: Map<String, Double>,
        saveToCache: Boolean = true
    ): Result<Map<String, EVMBalance>> = withContext(ioDispatcher) {
        val evmBalances = mutableMapOf<String, EVMBalance>()

        try {
            coroutineScope {
                val deferredBalances = tokens.map { token ->
                    async {
                        val balanceResult = when (token.evmTokenType) {
                            EVMTokenType.NATIVE -> evmBlockchainRepository.getNativeBalance(
                                address = token.address,
                                network = token.network
                            )

                            EVMTokenType.USDC, EVMTokenType.USDT -> evmBlockchainRepository.getTokenBalance(
                                address = token.address,
                                tokenContract = token.contractAddress,
                                tokenDecimals = token.decimals,
                                network = token.network
                            )
                        }

                        when (balanceResult) {
                            is Result.Success -> {
                                val balance = balanceResult.data
                                val balanceWei = when (token.evmTokenType) {
                                    EVMTokenType.NATIVE -> (balance * BigDecimal("1000000000000000000")).toBigInteger()
                                        .toString()

                                    else -> (balance * BigDecimal.TEN.pow(token.decimals)).toBigInteger()
                                        .toString()
                                }

                                val price = BigDecimal.valueOf(prices[token.symbol] ?: 0.0)
                                val usdValue = balance.multiply(price)

                                logger.d(TAG, "${token.symbol} on ${token.network.name} balance updated: $balance")

                                val balanceDomain = EVMBalance(
                                    evmTokenType = token.evmTokenType,
                                    network = token.network,
                                    address = token.address,
                                    contractAddress = token.contractAddress,
                                    balanceWei = balanceWei,
                                    balanceDecimal = balance.toPlainString(),
                                    usdValue = usdValue
                                )

                                // lookup key: chainId + contractAddress
                                val key = "${token.network.chainId}_${token.contractAddress}"
                                key to balanceDomain
                            }

                            else -> null
                        }
                    }
                }

                deferredBalances.awaitAll().forEach { entry ->
                    entry?.let { (key, balance) -> evmBalances[key] = balance }
                }
            }

            // Save all EVM balances that succeeded
            if (saveToCache && evmBalances.isNotEmpty()) {
                balanceDataSource.saveEVMBalances(walletId, evmBalances.values.toList())
                logger.d(TAG, "Saved ${evmBalances.size} EVM balances for wallet $walletId")
            }

            Result.Success(evmBalances)
        } catch (e: Exception) {
            logger.e(TAG, "Exception syncing EVM balances", e)
            Result.Error(e.message ?: "Sync failed")
        }
    }

    companion object {
        private const val TAG = "SyncEVMBalancesUC"
    }
}
