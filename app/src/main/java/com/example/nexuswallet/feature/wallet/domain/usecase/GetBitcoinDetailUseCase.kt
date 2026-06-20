package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinDetailResult
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncWalletBalancesUseCase
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.forEachIndexed

@Singleton
class GetBitcoinDetailUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val tag = "GetBitcoinDetailUC"

    suspend operator fun invoke(
        walletId: String,
        network: BitcoinNetwork
    ): Result<BitcoinDetailResult> = withContext(ioDispatcher) {
        logger.d(tag, "Getting Bitcoin details for wallet: $walletId, network: ${network.name}")

        // 1. Get wallet
        val wallet = walletRepository.getWallet(walletId)
            ?: return@withContext Result.Error("Wallet not found")

        // 2. Find the specific Bitcoin coin
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
            ?: wallet.bitcoinCoins.firstOrNull()
            ?: return@withContext Result.Error("Bitcoin ${network.name} not enabled for this wallet")

        logger.d(tag, "Found Bitcoin coin with address: ${bitcoinCoin.address.take(8)}... on ${network.name}")

        // 3. Fetch fresh transactions from blockchain
        logger.d(tag, "Fetching transactions from blockchain for ${network.name}...")

        val txResult = bitcoinBlockchainRepository.getAddressTransactions(
            walletId = walletId,
            address = bitcoinCoin.address,
            network = bitcoinCoin.network
        )

        when (txResult) {
            is Result.Success -> {
                logger.d(tag, "Fetched ${txResult.data.size} transactions from blockchain")

                // Delete old transactions and save new ones
                bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, bitcoinCoin.network)
                logger.d(tag, "Deleted old transactions for wallet $walletId, network ${network.name}")

                // Save transactions directly
                txResult.data.forEachIndexed { index, tx ->
                    bitcoinTransactionRepository.saveTransaction(tx)
                    logger.d(tag, "Saved transaction $index: ${tx.txHash?.take(8) ?: "unknown"} on ${network.name}")
                }
                logger.d(tag, "Synced ${txResult.data.size} transactions")
            }
            is Result.Error -> {
                logger.e(tag, "Failed to fetch transactions: ${txResult.message}")
                // Continue with existing transactions in DB
            }
            Result.Loading -> {}
        }

        // 4. Get balance (Already synced by ViewModel)
        val balance = walletRepository.getWalletBalance(walletId)
        val coinBalance = balance?.bitcoinBalances?.get(bitcoinCoin.network)
        logger.d(tag, "Balance for ${network.name}: ${coinBalance?.btc ?: "0"} BTC")

        // 5. Get raw transactions from local DB
        logger.d(tag, "Querying transactions with walletId=$walletId, network=${network.name}")
        val rawTransactions = bitcoinTransactionRepository.getTransactionsSync(walletId, bitcoinCoin.network)
        logger.d(tag, "Retrieved ${rawTransactions.size} raw transactions from DB for ${network.name}")

        val result = BitcoinDetailResult(
            walletId = walletId,
            address = bitcoinCoin.address,
            balance = coinBalance?.btc ?: "0",
            balanceFormatted = "${coinBalance?.btc ?: "0"} BTC",
            usdValue = coinBalance?.usdValue ?: 0.0,
            network = bitcoinCoin.network,
            networkDisplayName = bitcoinCoin.network.name,
            rawTransactions = rawTransactions,
            bitcoinCoin = bitcoinCoin,
            availableNetworks = wallet.bitcoinCoins.map { it.network }
        )

        logger.d(tag, "=== GetBitcoinDetailUseCase completed successfully with ${rawTransactions.size} raw transactions on ${network.name} ===")
        Result.Success(result)
    }
}