package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.ChainSyncError
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.SyncReport
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncWalletBalancesUseCase @Inject constructor(
    private val balanceDataSource: BalanceDataSource,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val tag = "SyncBalancesUC"

    suspend operator fun invoke(
        wallet: Wallet,
        prices: Map<String, Double> = emptyMap()
    ): Result<SyncReport> = withContext(ioDispatcher) {
        logger.d(tag, "Syncing balances for wallet: ${wallet.name}")

        val errors = mutableListOf<ChainSyncError>()

        // Sync Bitcoin balances
        wallet.bitcoinCoins.forEach { coin ->
            errors.addAll(syncBitcoinBalance(wallet.id, coin, prices[coin.symbol] ?: 0.0))
        }

        // Sync Solana balances (including SPL tokens)
        wallet.solanaCoins.forEach { coin ->
            errors.addAll(syncSolanaBalance(wallet.id, coin, prices[coin.symbol] ?: 0.0))
        }

        // Sync EVM balances (Native ETH + all tokens)
        if (wallet.evmTokens.isNotEmpty()) {
            errors.addAll(syncEVMBalances(wallet.id, wallet.evmTokens, prices))
        }

        val report = SyncReport(walletId = wallet.id, errors = errors)

        if (report.isSuccessful) {
            logger.d(tag, "Successfully synced all balances for wallet: ${wallet.name}")
        } else {
            logger.e(tag, "Sync completed with ${errors.size} errors for wallet: ${wallet.name}")
        }

        Result.Success(report)
    }

    private suspend fun syncBitcoinBalance(
        walletId: String,
        coin: BitcoinCoin,
        price: Double
    ): List<ChainSyncError> {
        val balanceResult = bitcoinBlockchainRepository.getBalance(
            address = coin.address,
            network = coin.network
        )

        return when (balanceResult) {
            is Result.Success -> {
                val btcBalance = balanceResult.data
                val satoshiBalance =
                    (btcBalance * BigDecimal("100000000")).toBigInteger().toString()
                val usdValue = btcBalance.toDouble() * price

                val currentBalance = balanceDataSource.loadWalletBalance(walletId)
                    ?: WalletBalance(
                        walletId = walletId,
                        lastUpdated = System.currentTimeMillis()
                    )

                val updatedBitcoinBalances = currentBalance.bitcoinBalances.toMutableMap()
                updatedBitcoinBalances[coin.network] = BitcoinBalance(
                    address = coin.address,
                    satoshis = satoshiBalance,
                    btc = btcBalance.setScale(8, RoundingMode.HALF_UP).toPlainString(),
                    usdValue = usdValue
                )

                val updatedBalance = currentBalance.copy(
                    bitcoinBalances = updatedBitcoinBalances,
                    lastUpdated = System.currentTimeMillis()
                )

                balanceDataSource.saveWalletBalance(updatedBalance)
                logger.d(
                    tag,
                    "Bitcoin ${coin.network.name} balance updated: $btcBalance BTC"
                )
                emptyList()
            }

            is Result.Error -> {
                logger.e(tag, "Failed to sync Bitcoin: ${balanceResult.message}")
                listOf(ChainSyncError(coin.network, balanceResult.message, coin.symbol))
            }

            else -> listOf(
                ChainSyncError(
                    coin.network,
                    "Unknown error syncing Bitcoin",
                    coin.symbol
                )
            )
        }
    }

    private suspend fun syncSolanaBalance(
        walletId: String,
        coin: SolanaCoin,
        price: Double
    ): List<ChainSyncError> {
        val solBalanceResult = solanaBlockchainRepository.getBalance(
            address = coin.address,
            network = coin.network
        )

        return when (solBalanceResult) {
            is Result.Success -> {
                val solBalance = solBalanceResult.data
                val lamportsBalance =
                    (solBalance * BigDecimal("1000000000")).toBigInteger().toString()
                val usdValue = solBalance.toDouble() * price

                val currentBalance = balanceDataSource.loadWalletBalance(walletId)
                    ?: WalletBalance(
                        walletId = walletId,
                        lastUpdated = System.currentTimeMillis()
                    )

                val updatedSolanaBalances = currentBalance.solanaBalances.toMutableMap()
                updatedSolanaBalances[coin.network] = SolanaBalance(
                    address = coin.address,
                    lamports = lamportsBalance,
                    sol = solBalance.setScale(9, RoundingMode.HALF_UP).toPlainString(),
                    usdValue = usdValue
                )

                val updatedBalance = WalletBalance(
                    walletId = walletId,
                    lastUpdated = System.currentTimeMillis(),
                    bitcoinBalances = currentBalance.bitcoinBalances,
                    solanaBalances = updatedSolanaBalances,
                    evmBalances = currentBalance.evmBalances
                )

                balanceDataSource.saveWalletBalance(updatedBalance)
                logger.d(tag, "Solana ${coin.network.name} balance updated: $solBalance SOL")
                emptyList()
            }

            is Result.Error -> {
                logger.e(tag, "Failed to sync Solana: ${solBalanceResult.message}")
                listOf(ChainSyncError(coin.network, solBalanceResult.message, coin.symbol))
            }

            else -> listOf(
                ChainSyncError(
                    coin.network,
                    "Unknown error syncing Solana",
                    coin.symbol
                )
            )
        }
    }

    private suspend fun syncEVMBalances(
        walletId: String,
        tokens: List<EVMToken>,
        prices: Map<String, Double>
    ): List<ChainSyncError> {
        val evmBalances = mutableListOf<EVMBalance>()
        val chainErrors = mutableListOf<ChainSyncError>()

        tokens.forEach { token ->
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

                    evmBalances.add(
                        EVMBalance(
                            evmTokenType = token.evmTokenType,
                            network = token.network,
                            address = token.address,
                            balanceWei = balanceWei,
                            balanceDecimal = balance.toPlainString(),
                            usdValue = usdValue
                        )
                    )

                    logger.d(
                        tag,
                        "${token.symbol} on ${token.network.name} balance updated: $balance"
                    )
                }

                is Result.Error -> {
                    logger.e(
                        tag,
                        "Failed to sync ${token.symbol} on ${token.network.name}: ${balanceResult.message}"
                    )
                    chainErrors.add(
                        ChainSyncError(
                            token.network,
                            balanceResult.message,
                            token.symbol
                        )
                    )
                }

                else -> {
                    chainErrors.add(
                        ChainSyncError(
                            token.network,
                            "Unknown error",
                            token.symbol
                        )
                    )
                }
            }
        }

        // Save all EVM balances that succeeded
        if (evmBalances.isNotEmpty()) {
            val currentBalance = balanceDataSource.loadWalletBalance(walletId)
                ?: WalletBalance(walletId, System.currentTimeMillis())

            val updatedBalance = currentBalance.copy(
                evmBalances = evmBalances,
                lastUpdated = System.currentTimeMillis()
            )

            balanceDataSource.saveWalletBalance(updatedBalance)
            logger.d(tag, "Saved ${evmBalances.size} EVM balances for wallet $walletId")
        }

        return chainErrors
    }
}
