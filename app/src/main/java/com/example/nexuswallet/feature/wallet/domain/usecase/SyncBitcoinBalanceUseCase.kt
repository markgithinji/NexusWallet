package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.ChainSyncError
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

class SyncBitcoinBalanceUseCase @Inject constructor(
    private val balanceDataSource: BalanceDataSource,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        walletId: String,
        coin: BitcoinCoin,
        price: Double,
        saveToCache: Boolean = true
    ): Pair<BitcoinBalance?, List<ChainSyncError>> = withContext(ioDispatcher) {
        val balanceResult = bitcoinBlockchainRepository.getBalance(
            address = coin.address,
            network = coin.network
        )

        when (balanceResult) {
            is Result.Success -> {
                val btcBalance = balanceResult.data
                val satoshiBalance =
                    (btcBalance * BigDecimal("100000000")).toBigInteger().toString()
                
                val priceBigDecimal = BigDecimal.valueOf(price)
                val usdValue = btcBalance.multiply(priceBigDecimal)

                val btcBalanceDomain = BitcoinBalance(
                    address = coin.address,
                    satoshis = satoshiBalance,
                    btc = btcBalance.setScale(8, RoundingMode.HALF_UP).toPlainString(),
                    usdValue = usdValue
                )

                if (saveToCache) {
                    balanceDataSource.saveBitcoinBalance(walletId, coin.network, btcBalanceDomain)
                }
                logger.d(TAG, "Bitcoin ${coin.network.name} balance updated: $btcBalance BTC")
                Pair(btcBalanceDomain, emptyList())
            }

            is Result.Error -> {
                logger.e(TAG, "Failed to sync Bitcoin: ${balanceResult.message}")
                Pair(null, listOf(ChainSyncError(coin.network, balanceResult.message, coin.symbol)))
            }

            else -> Pair(
                null,
                listOf(ChainSyncError(coin.network, "Unknown error syncing Bitcoin", coin.symbol))
            )
        }
    }

    companion object {
        private const val TAG = "SyncBitcoinBalanceUC"
    }
}
