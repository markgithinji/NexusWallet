package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
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

    suspend operator fun invoke(
        walletId: String,
        network: BitcoinNetwork? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        logger.d(tag, "Syncing Bitcoin transactions for wallet: $walletId, network: ${network?.displayName}")

        val wallet = walletRepository.getWallet(walletId) ?: run {
            logger.e(tag, "Wallet not found: $walletId")
            return@withContext Result.Error("Wallet not found")
        }

        // Filter Bitcoin coins by network if specified
        val bitcoinCoins = if (network != null) {
            wallet.bitcoinCoins.filter { it.network == network }
        } else {
            wallet.bitcoinCoins
        }

        if (bitcoinCoins.isEmpty()) {
            val msg = if (network != null) "Bitcoin ${network.displayName} not enabled" else "Bitcoin not enabled"
            logger.e(tag, "$msg for wallet: ${wallet.name}")
            return@withContext Result.Error(msg)
        }

        var totalTransactions = 0
        val errors = mutableListOf<String>()

        // Sync transactions for each Bitcoin coin
        bitcoinCoins.forEach { bitcoinCoin ->
            logger.d(tag, "Syncing for ${bitcoinCoin.network.displayName}")

            when (val result = bitcoinBlockchainRepository.getAddressTransactions(
                walletId = walletId,
                address = bitcoinCoin.address,
                network = bitcoinCoin.network
            )) {
                is Result.Success -> {
                    val transactions = result.data

                    // Delete only this network's transactions
                    bitcoinTransactionRepository.deleteForWalletAndNetwork(
                        walletId,
                        bitcoinCoin.network
                    )

                    // Save transactions
                    transactions.forEach { tx ->
                        bitcoinTransactionRepository.saveTransaction(tx)
                        totalTransactions++
                    }

                    logger.d(tag, "Synced ${transactions.size} transactions for ${bitcoinCoin.network.displayName}")
                }
                is Result.Error -> {
                    val errorMsg = "Failed to sync ${bitcoinCoin.network.displayName}: ${result.message}"
                    logger.e(tag, errorMsg)
                    errors.add(errorMsg)
                }
                else -> {}
            }
        }

        if (errors.isNotEmpty()) {
            Result.Error(errors.first())
        } else {
            logger.d(tag, "Sync completed | total txCount=$totalTransactions")
            Result.Success(Unit)
        }
    }
}