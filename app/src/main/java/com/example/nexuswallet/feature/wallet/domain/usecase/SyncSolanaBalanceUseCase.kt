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
    private val tag = "SyncSolanaBalanceUC"

    suspend operator fun invoke(
        walletId: String,
        coin: SolanaCoin,
        price: Double
    ): List<ChainSyncError> = withContext(ioDispatcher) {
        val solBalanceResult = solanaBlockchainRepository.getBalance(
            address = coin.address,
            network = coin.network
        )

        when (solBalanceResult) {
            is Result.Success -> {
                val solBalance = solBalanceResult.data
                val lamportsBalance =
                    (solBalance * BigDecimal("1000000000")).toBigInteger().toString()
                val usdValue = solBalance.toDouble() * price

                val solanaBalanceDomain = SolanaBalance(
                    address = coin.address,
                    lamports = lamportsBalance,
                    sol = solBalance.setScale(9, RoundingMode.HALF_UP).toPlainString(),
                    usdValue = usdValue
                )

                balanceDataSource.saveSolanaBalance(walletId, coin.network, solanaBalanceDomain)
                logger.d(tag, "Solana ${coin.network.name} balance updated: $solBalance SOL")
                emptyList()
            }

            is Result.Error -> {
                logger.e(tag, "Failed to sync Solana: ${solBalanceResult.message}")
                listOf(ChainSyncError(coin.network, solBalanceResult.message, coin.symbol))
            }

            else -> listOf(
                ChainSyncError(coin.network, "Unknown error syncing Solana", coin.symbol)
            )
        }
    }
}
