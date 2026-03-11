package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncBitcoinTransactionsUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val walletRepository: WalletRepository,
    private val logger: Logger
) {

    private val tag = "SyncBitcoinUC"

    suspend operator fun invoke(walletId: String, network: String?): Result<Unit> = withContext(Dispatchers.IO) {
        logger.d(tag, "Syncing Bitcoin transactions for wallet: $walletId, network: $network")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Filter Bitcoin coins by network if specified
        val bitcoinCoins = if (network != null) {
            wallet.bitcoinCoins.filter { coin ->
                when (network.lowercase()) {
                    "mainnet" -> coin.network == BitcoinNetwork.Mainnet
                    "testnet" -> coin.network == BitcoinNetwork.Testnet
                    else -> false
                }
            }
        } else {
            wallet.bitcoinCoins
        }

        if (bitcoinCoins.isEmpty()) {
            val msg = if (network != null) "Bitcoin $network not enabled" else "Bitcoin not enabled"
            logger.e(tag, "$msg for wallet: ${wallet.name}")
            return@withContext Result.Error(msg)
        }

        var totalTransactions = 0

        // Sync transactions for each Bitcoin coin
        bitcoinCoins.forEach { bitcoinCoin ->
            logger.d(tag, "Syncing for ${bitcoinCoin.network}")

            when (val result = bitcoinBlockchainRepository.getAddressTransactions(
                walletId = walletId,
                address = bitcoinCoin.address,
                network = bitcoinCoin.network
            )) {
                is Result.Success -> {
                    val transactions = result.data

                    // Delete only this network's transactions
                    val networkParam = when (bitcoinCoin.network) {
                        BitcoinNetwork.Mainnet -> "mainnet"
                        BitcoinNetwork.Testnet -> "testnet"
                    }
                    bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, networkParam)

                    // Save transactions
                    transactions.forEach { tx ->
                        bitcoinTransactionRepository.saveTransaction(tx)
                        totalTransactions++
                    }

                    logger.d(tag, "Synced ${transactions.size} transactions for ${bitcoinCoin.network}")
                }
                is Result.Error -> {
                    logger.e(tag, "Failed to sync ${bitcoinCoin.network}: ${result.message}")
                }
                else -> {}
            }
        }

        logger.d(tag, "Sync completed | total txCount=$totalTransactions")
        Result.Success(Unit)
    }
}