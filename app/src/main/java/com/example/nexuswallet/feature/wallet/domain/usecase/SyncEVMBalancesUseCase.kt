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
    ): Pair<List<EVMBalance>, List<ChainSyncError>> = withContext(ioDispatcher) {
        val evmBalances = mutableListOf<EVMBalance>()
        val chainErrors = mutableListOf<ChainSyncError>()

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

                            val price = prices[token.symbol] ?: 0.0
                            val usdValue = balance.toDouble() * price

                            logger.d(TAG, "${token.symbol} on ${token.network.name} balance updated: $balance")
                            
                            Pair(
                                EVMBalance(
                                    evmTokenType = token.evmTokenType,
                                    network = token.network,
                                    address = token.address,
                                    balanceWei = balanceWei,
                                    balanceDecimal = balance.toPlainString(),
                                    usdValue = usdValue
                                ),
                                null
                            )
                        }

                        is Result.Error -> {
                            logger.e(TAG, "Failed to sync ${token.symbol} on ${token.network.name}: ${balanceResult.message}")
                            Pair(null, ChainSyncError(token.network, balanceResult.message, token.symbol))
                        }

                        else -> {
                            Pair(null, ChainSyncError(token.network, "Unknown error", token.symbol))
                        }
                    }
                }
            }

            deferredBalances.awaitAll().forEach { (balance, error) ->
                balance?.let { evmBalances.add(it) }
                error?.let { chainErrors.add(it) }
            }
        }

        // Save all EVM balances that succeeded
        if (saveToCache && evmBalances.isNotEmpty()) {
            balanceDataSource.saveEVMBalances(walletId, evmBalances)
            logger.d(TAG, "Saved ${evmBalances.size} EVM balances for wallet $walletId")
        }

        Pair(evmBalances, chainErrors)
    }

    companion object {
        private const val TAG = "SyncEVMBalancesUC"
    }
}
