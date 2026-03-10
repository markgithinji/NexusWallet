package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.ERC20Token
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SyncWalletBalancesUseCase
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TokenType
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class SyncWalletBalancesUseCaseImpl @Inject constructor(
    private val localDataSource: WalletLocalDataSource,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger
) : SyncWalletBalancesUseCase {

    private val tag = "SyncBalancesUC"

    override suspend operator fun invoke(wallet: Wallet): Result<Unit> {
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
        return try {
            val balanceResult = bitcoinBlockchainRepository.getBalance(
                address = coin.address,
                network = coin.network
            )

            when (balanceResult) {
                is Result.Success -> {
                    val btcBalance = balanceResult.data
                    val satoshiBalance =
                        (btcBalance * BigDecimal("100000000")).toBigInteger().toString()
                    val usdValue = calculateUsdValue(btcBalance, "BTC")

                    val currentBalance = localDataSource.loadWalletBalance(walletId)
                        ?: WalletBalance(
                            walletId = walletId,
                            lastUpdated = System.currentTimeMillis()
                        )

                    val networkKey = when (coin.network) {
                        BitcoinNetwork.Mainnet -> "mainnet"
                        BitcoinNetwork.Testnet -> "testnet"
                    }

                    val updatedBitcoinBalances = currentBalance.bitcoinBalances.toMutableMap()
                    updatedBitcoinBalances[networkKey] = BitcoinBalance(
                        address = coin.address,
                        satoshis = satoshiBalance,
                        btc = btcBalance.setScale(8, RoundingMode.HALF_UP).toPlainString(),
                        usdValue = usdValue
                    )

                    val updatedBalance = currentBalance.copy(
                        bitcoinBalances = updatedBitcoinBalances,
                        lastUpdated = System.currentTimeMillis()
                    )

                    localDataSource.saveWalletBalance(updatedBalance)
                    logger.d(tag, "Bitcoin ${coin.network} balance updated: ${btcBalance} BTC")
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    logger.e(tag, "Failed to sync Bitcoin: ${balanceResult.message}")
                    Result.Error("Bitcoin (${coin.network}): ${balanceResult.message}")
                }
                else -> Result.Error("Unknown error syncing Bitcoin")
            }
        } catch (e: Exception) {
            logger.e(tag, "Exception syncing Bitcoin", e)
            Result.Error("Bitcoin (${coin.network}): ${e.message}")
        }
    }

    private suspend fun syncSolanaBalance(walletId: String, coin: SolanaCoin): Result<Unit> {
        return try {
            val solBalanceResult = solanaBlockchainRepository.getBalance(
                address = coin.address,
                network = coin.network
            )

            when (solBalanceResult) {
                is Result.Success -> {
                    val solBalance = solBalanceResult.data
                    val lamportsBalance =
                        (solBalance * BigDecimal("1000000000")).toBigInteger().toString()
                    val usdValue = calculateUsdValue(solBalance, "SOL")

                    val currentBalance = localDataSource.loadWalletBalance(walletId)
                        ?: WalletBalance(
                            walletId = walletId,
                            lastUpdated = System.currentTimeMillis()
                        )

                    val networkKey = when (coin.network) {
                        SolanaNetwork.Mainnet -> "mainnet"
                        SolanaNetwork.Devnet -> "devnet"
                    }

                    val updatedSolanaBalances = currentBalance.solanaBalances.toMutableMap()
                    updatedSolanaBalances[networkKey] = SolanaBalance(
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
                        evmBalances = currentBalance.evmBalances,
                        splBalances = currentBalance.splBalances
                    )

                    localDataSource.saveWalletBalance(updatedBalance)
                    logger.d(tag, "Solana ${coin.network} balance updated: ${solBalance} SOL")
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    logger.e(tag, "Failed to sync Solana: ${solBalanceResult.message}")
                    Result.Error("Solana (${coin.network}): ${solBalanceResult.message}")
                }
                else -> Result.Error("Unknown error syncing Solana")
            }
        } catch (e: Exception) {
            logger.e(tag, "Exception syncing Solana", e)
            Result.Error("Solana (${coin.network}): ${e.message}")
        }
    }

    private suspend fun syncEVMBalances(walletId: String, tokens: List<EVMToken>): Result<Unit> {
        val evmBalances = mutableListOf<EVMBalance>()
        val errors = mutableListOf<String>()

        tokens.forEach { token ->
            try {
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
                            is NativeETH -> (balance * BigDecimal("1000000000000000000")).toBigInteger().toString()
                            else -> (balance * BigDecimal.TEN.pow(token.decimals)).toBigInteger().toString()
                        }

                        val usdValue = calculateTokenUsdValue(balance, token.symbol)

                        evmBalances.add(
                            EVMBalance(
                                externalTokenId = token.externalId,
                                address = token.address,
                                balanceWei = balanceWei,
                                balanceDecimal = balance.toPlainString(),
                                usdValue = usdValue
                            )
                        )

                        logger.d(tag, "${token.symbol} balance updated: $balance (externalId: ${token.externalId})")
                    }

                    is Result.Error -> {
                        logger.e(tag, "Failed to sync ${token.symbol} (${token.externalId}): ${balanceResult.message}")
                        errors.add("${token.symbol}: ${balanceResult.message}")
                    }

                    else -> {
                        errors.add("${token.symbol}: Unknown error")
                    }
                }
            } catch (e: Exception) {
                logger.e(tag, "Exception syncing ${token.symbol}", e)
                errors.add("${token.symbol}: ${e.message}")
            }
        }

        // Save all EVM balances that succeeded
        if (evmBalances.isNotEmpty()) {
            val currentBalance = localDataSource.loadWalletBalance(walletId)
                ?: WalletBalance(walletId, System.currentTimeMillis())

            val updatedBalance = currentBalance.copy(
                evmBalances = evmBalances,
                lastUpdated = System.currentTimeMillis()
            )

            localDataSource.saveWalletBalance(updatedBalance)
            logger.d(tag, "Saved ${evmBalances.size} EVM balances for wallet $walletId")
        }

        return if (errors.isEmpty()) {
            Result.Success(Unit)
        } else {
            Result.Error("EVM sync errors: ${errors.joinToString(", ")}")
        }
    }

    private fun calculateUsdValue(amount: BigDecimal, symbol: String): Double {
        val price = when (symbol) {
            "BTC" -> 45000.0
            "ETH" -> 3000.0
            "SOL" -> 30.0
            else -> 1.0
        }
        return amount.toDouble() * price
    }

    private fun calculateTokenUsdValue(amount: BigDecimal, symbol: String): Double {
        return when (symbol) {
            "USDC", "USDT" -> amount.toDouble()  // Stablecoins are 1:1
            else -> amount.toDouble() * getTokenPrice(symbol)
        }
    }

    private fun getTokenPrice(symbol: String): Double {
        // TODO: fetch from price API
        return when (symbol) {
            "ETH" -> 3000.0
            "SOL" -> 30.0
            else -> 1.0
        }
    }

    // method to sync a single token by its externalId
    suspend fun syncTokenBalance(walletId: String, token: EVMToken): Result<EVMBalance?> {
        return try {
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
                        is NativeETH -> (balance * BigDecimal("1000000000000000000")).toBigInteger().toString()
                        else -> (balance * BigDecimal.TEN.pow(token.decimals)).toBigInteger().toString()
                    }

                    val usdValue = calculateTokenUsdValue(balance, token.symbol)

                    val evmBalance = EVMBalance(
                        externalTokenId = token.externalId,
                        address = token.address,
                        balanceWei = balanceWei,
                        balanceDecimal = balance.toPlainString(),
                        usdValue = usdValue
                    )

                    // Update just this token's balance
                    val currentBalance = localDataSource.loadWalletBalance(walletId)
                    val updatedEvmBalances = currentBalance?.evmBalances?.toMutableList() ?: mutableListOf()

                    // Remove old balance for this token if exists
                    updatedEvmBalances.removeAll { it.externalTokenId == token.externalId }
                    updatedEvmBalances.add(evmBalance)

                    val updatedBalance = (currentBalance ?: WalletBalance(walletId, System.currentTimeMillis()))
                        .copy(
                            evmBalances = updatedEvmBalances,
                            lastUpdated = System.currentTimeMillis()
                        )

                    localDataSource.saveWalletBalance(updatedBalance)
                    logger.d(tag, "Updated ${token.symbol} balance: $balance")

                    Result.Success(evmBalance)
                }
                is Result.Error -> {
                    logger.e(tag, "Failed to sync ${token.symbol}: ${balanceResult.message}")
                    Result.Error(balanceResult.message)
                }
                else -> Result.Error("Unknown error")
            }
        } catch (e: Exception) {
            logger.e(tag, "Error syncing token balance", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }
}