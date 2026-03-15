package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.ERC20Token
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
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
    private val logger: Logger
) {

    private val tag = "SyncBalancesUC"

    suspend operator fun invoke(wallet: Wallet): Result<Unit> {
        logger.d(tag, "Syncing balances for wallet: ${wallet.name}")

        val errors = mutableListOf<String>()

        // Sync Bitcoin balances
        wallet.bitcoinCoins.forEach { coin ->
            val result = syncBitcoinBalance(wallet.id, coin)
            if (result is Result.Error) {
                errors.add(result.message)
            }
        }

        // Sync Solana balances (including SPL tokens)
        wallet.solanaCoins.forEach { coin ->
            val result = syncSolanaBalance(wallet.id, coin)
            if (result is Result.Error) {
                errors.add(result.message)
            }
        }

        // Sync EVM balances (Native ETH + all tokens)
        if (wallet.evmTokens.isNotEmpty()) {
            val result = syncEVMBalances(wallet.id, wallet.evmTokens)
            if (result is Result.Error) {
                errors.add(result.message)
            }
        }

        return if (errors.isEmpty()) {
            logger.d(tag, "Successfully synced all balances for wallet: ${wallet.name}")
            Result.Success(Unit)
        } else {
            val errorMessage = "Sync completed with errors: ${errors.joinToString(", ")}"
            logger.e(tag, errorMessage)
            Result.Error(errorMessage)
        }
    }

    private suspend fun syncBitcoinBalance(walletId: String, coin: BitcoinCoin): Result<Unit> {
        val balanceResult = bitcoinBlockchainRepository.getBalance(
            address = coin.address,
            network = coin.network
        )

        return when (balanceResult) {
            is Result.Success -> {
                val btcBalance = balanceResult.data
                val satoshiBalance =
                    (btcBalance * BigDecimal("100000000")).toBigInteger().toString()
                val usdValue = calculateUsdValue(btcBalance, CoinType.BITCOIN)

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
                    "Bitcoin ${coin.network.displayName} balance updated: $btcBalance BTC"
                )
                Result.Success(Unit)
            }

            is Result.Error -> {
                logger.e(tag, "Failed to sync Bitcoin: ${balanceResult.message}")
                Result.Error("Bitcoin (${coin.network.displayName}): ${balanceResult.message}")
            }

            else -> Result.Error("Unknown error syncing Bitcoin")
        }
    }

    private suspend fun syncSolanaBalance(walletId: String, coin: SolanaCoin): Result<Unit> {
        val solBalanceResult = solanaBlockchainRepository.getBalance(
            address = coin.address,
            network = coin.network
        )

        return when (solBalanceResult) {
            is Result.Success -> {
                val solBalance = solBalanceResult.data
                val lamportsBalance =
                    (solBalance * BigDecimal("1000000000")).toBigInteger().toString()
                val usdValue = calculateUsdValue(solBalance, CoinType.SOLANA)

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
                logger.d(tag, "Solana ${coin.network.displayName} balance updated: $solBalance SOL")
                Result.Success(Unit)
            }

            is Result.Error -> {
                logger.e(tag, "Failed to sync Solana: ${solBalanceResult.message}")
                Result.Error("Solana (${coin.network.displayName}): ${solBalanceResult.message}")
            }

            else -> Result.Error("Unknown error syncing Solana")
        }
    }

    private suspend fun syncEVMBalances(walletId: String, tokens: List<EVMToken>): Result<Unit> {
        val evmBalances = mutableListOf<EVMBalance>()
        val errors = mutableListOf<String>()

        tokens.forEach { token ->
            val balanceResult = when (token) {
                is NativeETH -> evmBlockchainRepository.getNativeBalance(
                    address = token.address,
                    network = token.network
                )

                is USDCToken, is USDTToken, is ERC20Token -> evmBlockchainRepository.getTokenBalance(
                    address = token.address,
                    tokenContract = token.contractAddress,
                    tokenDecimals = token.decimals,
                    network = token.network
                )
            }

            when (balanceResult) {
                is Result.Success -> {
                    val balance = balanceResult.data
                    val balanceWei = when (token) {
                        is NativeETH -> (balance * BigDecimal("1000000000000000000")).toBigInteger()
                            .toString()

                        else -> (balance * BigDecimal.TEN.pow(token.decimals)).toBigInteger()
                            .toString()
                    }

                    val coinType = when (token) {
                        is NativeETH -> CoinType.ETHEREUM
                        is USDCToken -> CoinType.USDC
                        is USDTToken -> CoinType.USDC  // Treat USDT as USDC for USD value
                        else -> CoinType.ETHEREUM  // Default for other ERC20 tokens
                    }

                    val usdValue = calculateTokenUsdValue(balance, coinType)

                    evmBalances.add(
                        EVMBalance(
                            externalTokenId = token.externalId,
                            address = token.address,
                            balanceWei = balanceWei,
                            balanceDecimal = balance.toPlainString(),
                            usdValue = usdValue
                        )
                    )

                    logger.d(
                        tag,
                        "${token.symbol} on ${token.network.displayName} balance updated: $balance"
                    )
                }

                is Result.Error -> {
                    logger.e(
                        tag,
                        "Failed to sync ${token.symbol} on ${token.network.displayName}: ${balanceResult.message}"
                    )
                    errors.add("${token.symbol}: ${balanceResult.message}")
                }

                else -> {
                    errors.add("${token.symbol}: Unknown error")
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

        return if (errors.isEmpty()) {
            Result.Success(Unit)
        } else {
            Result.Error("EVM sync errors: ${errors.joinToString(", ")}")
        }
    }

    // TODO: fetch from price API
    private fun calculateUsdValue(amount: BigDecimal, coinType: CoinType): Double {
        val price = when (coinType) {
            CoinType.BITCOIN -> 45000.0
            CoinType.ETHEREUM -> 3000.0
            CoinType.SOLANA -> 30.0
            CoinType.USDC -> 1.0
        }
        return amount.toDouble() * price
    }

    // TODO: fetch from price API
    private fun calculateTokenUsdValue(amount: BigDecimal, coinType: CoinType): Double {
        return when (coinType) {
            CoinType.USDC -> amount.toDouble()
            CoinType.ETHEREUM -> amount.toDouble() * 3000.0
            else -> amount.toDouble()
        }
    }
}