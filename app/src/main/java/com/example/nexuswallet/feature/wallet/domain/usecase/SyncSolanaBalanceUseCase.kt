package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.model.ChainSyncError
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

class SyncSolanaBalanceUseCase @Inject constructor(
    private val balanceDataSource: BalanceDataSource,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        walletId: String,
        coin: SolanaCoin,
        price: Double,
        saveToCache: Boolean = true
    ): Result<SolanaBalance?> = withContext(ioDispatcher) {
        try {
            val solBalanceResult = solanaBlockchainRepository.getBalance(
                address = coin.address,
                network = coin.network
            )

            when (solBalanceResult) {
                is Result.Success -> {
                    val solBalance = solBalanceResult.data
                    val lamportsBalance =
                        (solBalance * BigDecimal("1000000000")).toBigInteger().toString()

                    val priceBigDecimal = BigDecimal.valueOf(price)
                    val usdValue = solBalance.multiply(priceBigDecimal)

                    val solanaBalanceDomain = SolanaBalance(
                        address = coin.address,
                        lamports = lamportsBalance,
                        sol = solBalance.setScale(9, RoundingMode.HALF_UP).toPlainString(),
                        usdValue = usdValue
                    )

                    if (saveToCache) {
                        balanceDataSource.saveSolanaBalance(walletId, coin.network, solanaBalanceDomain)
                    }
                    logger.d(TAG, "Solana ${coin.network.name} balance updated: $solBalance SOL")
                    Result.Success(solanaBalanceDomain)
                }

                is Result.Error -> {
                    logger.e(TAG, "Failed to sync Solana: ${solBalanceResult.message}")
                    Result.Error(solBalanceResult.message)
                }

                else -> Result.Error("Unknown error syncing Solana")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Exception syncing Solana balance", e)
            Result.Error(e.message ?: "Sync failed")
        }
    }

    companion object {
        private const val TAG = "SyncSolanaBalanceUC"
    }
}
