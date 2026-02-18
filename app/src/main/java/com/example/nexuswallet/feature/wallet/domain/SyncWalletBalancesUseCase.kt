package com.example.nexuswallet.feature.wallet.domain

import android.util.Log
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result

class SyncWalletBalancesUseCase @Inject constructor(
    private val localDataSource: WalletLocalDataSource,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val ethereumBlockchainRepository: EthereumBlockchainRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val usdcBlockchainRepository: USDCBlockchainRepository
) {

    suspend operator fun invoke(wallet: Wallet) {
        Log.d("SyncBalancesUC", "Syncing balances for wallet: ${wallet.name}")
        syncWalletBalances(wallet)
    }

    private suspend fun syncWalletBalances(wallet: Wallet) {
        wallet.bitcoin?.let { syncBitcoinBalance(wallet.id, it) }
        wallet.ethereum?.let { syncEthereumBalance(wallet.id, it) }
        wallet.solana?.let { syncSolanaBalance(wallet.id, it) }
        wallet.usdc?.let { syncUSDCBalance(wallet.id, it) }
    }

    private suspend fun syncBitcoinBalance(walletId: String, coin: BitcoinCoin) {
        val balanceResult = bitcoinBlockchainRepository.getBalance(
            address = coin.address,
            network = coin.network
        )

        when (balanceResult) {
            is Result.Success -> {
                val btcBalance = balanceResult.data
                val satoshiBalance =
                    (btcBalance * BigDecimal("100000000")).toBigInteger().toString()
                val usdValue = calculateUsdValue(btcBalance, CoinType.BITCOIN.name)

                val currentBalance = localDataSource.loadWalletBalance(walletId) ?: WalletBalance(
                    walletId,
                    System.currentTimeMillis()
                )

                val updatedBalance = currentBalance.copy(
                    bitcoin = BitcoinBalance(
                        address = coin.address,
                        satoshis = satoshiBalance,
                        btc = btcBalance.setScale(8, RoundingMode.HALF_UP).toPlainString(),
                        usdValue = usdValue
                    ),
                    lastUpdated = System.currentTimeMillis()
                )

                localDataSource.saveWalletBalance(updatedBalance)
                Log.d("SyncBalancesUC", "Bitcoin balance updated: ${btcBalance} BTC")
            }

            is Result.Error -> {
                Log.e("SyncBalancesUC", "Failed to sync Bitcoin: ${balanceResult.message}")
            }
            else -> {}
        }
    }

    private suspend fun syncEthereumBalance(walletId: String, coin: EthereumCoin) {
        val balanceResult = ethereumBlockchainRepository.getEthereumBalance(
            address = coin.address,
            network = coin.network
        )

        when (balanceResult) {
            is Result.Success -> {
                val ethBalance = balanceResult.data
                val weiBalance =
                    (ethBalance * BigDecimal("1000000000000000000")).toBigInteger().toString()
                val usdValue = calculateUsdValue(ethBalance, CoinType.ETHEREUM.name)

                val currentBalance = localDataSource.loadWalletBalance(walletId) ?: WalletBalance(
                    walletId,
                    System.currentTimeMillis()
                )

                val updatedBalance = currentBalance.copy(
                    ethereum = EthereumBalance(
                        address = coin.address,
                        wei = weiBalance,
                        eth = ethBalance.setScale(6, RoundingMode.HALF_UP).toPlainString(),
                        usdValue = usdValue
                    ),
                    lastUpdated = System.currentTimeMillis()
                )

                localDataSource.saveWalletBalance(updatedBalance)
                Log.d("SyncBalancesUC", "Ethereum balance updated: ${ethBalance} ETH")
            }

            is Result.Error -> {
                Log.e("SyncBalancesUC", "Failed to sync Ethereum: ${balanceResult.message}")
            }
            else -> {}
        }
    }

    private suspend fun syncSolanaBalance(walletId: String, coin: SolanaCoin) {
        val balanceResult = solanaBlockchainRepository.getBalance(coin.address)

        when (balanceResult) {
            is Result.Success -> {
                val solBalance = balanceResult.data
                val lamportsBalance =
                    (solBalance * BigDecimal("1000000000")).toBigInteger().toString()
                val usdValue = calculateUsdValue(solBalance, CoinType.SOLANA.name)

                val currentBalance = localDataSource.loadWalletBalance(walletId) ?: WalletBalance(
                    walletId,
                    System.currentTimeMillis()
                )

                val updatedBalance = currentBalance.copy(
                    solana = SolanaBalance(
                        address = coin.address,
                        lamports = lamportsBalance,
                        sol = solBalance.setScale(9, RoundingMode.HALF_UP).toPlainString(),
                        usdValue = usdValue
                    ),
                    lastUpdated = System.currentTimeMillis()
                )

                localDataSource.saveWalletBalance(updatedBalance)
                Log.d("SyncBalancesUC", "Solana balance updated: ${solBalance} SOL")
            }

            is Result.Error -> {
                Log.e("SyncBalancesUC", "Failed to sync Solana: ${balanceResult.message}")
            }
            else -> {}
        }
    }

    private suspend fun syncUSDCBalance(walletId: String, coin: USDCCoin) {
        val result = usdcBlockchainRepository.getUSDCBalance(
            address = coin.address,
            network = coin.network
        )

        when (result) {
            is Result.Success -> {
                val usdcBalance = result.data

                val currentBalance = localDataSource.loadWalletBalance(walletId)
                    ?: WalletBalance(walletId, System.currentTimeMillis())

                val updatedBalance = currentBalance.copy(
                    usdc = usdcBalance,
                    lastUpdated = System.currentTimeMillis()
                )

                localDataSource.saveWalletBalance(updatedBalance)
                Log.d("SyncBalancesUC", "USDC balance updated: ${usdcBalance.amountDecimal} USDC")
            }

            is Result.Error -> {
                Log.e("SyncBalancesUC", "Failed to sync USDC: ${result.message}")
            }
            else -> {}
        }
    }

    private fun calculateUsdValue(amount: BigDecimal, symbol: String): Double {
        val price = when (symbol) {
            CoinType.BITCOIN.name -> 45000.0
            CoinType.ETHEREUM.name -> 3000.0
            CoinType.SOLANA.name-> 30.0
            else -> 1.0
        }
        return amount.toDouble() * price
    }
}