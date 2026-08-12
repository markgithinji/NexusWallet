package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinDetailResult
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.forEachIndexed

@Singleton
class GetBitcoinDetailUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(
        walletId: String,
        network: BitcoinNetwork
    ): Result<BitcoinDetailResult> = withContext(ioDispatcher) {
        logger.d(TAG, "Getting Bitcoin details for wallet: $walletId, network: ${network.name}")

        // 1. Get wallet
        val wallet = walletRepository.getWallet(walletId)
            ?: return@withContext Result.Error("Wallet not found")

        // 2. Find the specific Bitcoin coin
        val bitcoinCoin = wallet.bitcoinCoins.find { it.network == network }
            ?: wallet.bitcoinCoins.firstOrNull()
            ?: return@withContext Result.Error("Bitcoin ${network.name} not enabled for this wallet")

        logger.d(TAG, "Found Bitcoin coin with address: ${bitcoinCoin.address.take(8)}... on ${network.name}")

        // 3. Fetch fresh transactions from blockchain
        logger.d(TAG, "Fetching transactions from blockchain for ${network.name}...")

        val txResult = bitcoinBlockchainRepository.getAddressTransactions(
            walletId = walletId,
            address = bitcoinCoin.address,
            network = bitcoinCoin.network
        )

        when (txResult) {
            is Result.Success -> {
                logger.d(TAG, "Fetched ${txResult.data.size} transactions from blockchain")

                // Delete old transactions and save new ones
                bitcoinTransactionRepository.deleteForWalletAndNetwork(walletId, bitcoinCoin.network)
                
                // Save transactions directly
                txResult.data.forEach { tx ->
                    bitcoinTransactionRepository.saveTransaction(tx)
                }
                logger.d(TAG, "Synced ${txResult.data.size} transactions")
            }
            is Result.Error -> {
                logger.e(TAG, "Failed to fetch transactions: ${txResult.message}")
            }
            Result.Loading -> {}
        }

        // 4. Get cached balance
        val balance = walletRepository.getWalletBalance(walletId)
        val coinBalance = balance?.bitcoinBalances?.get(bitcoinCoin.network)
        logger.d(TAG, "Balance for ${network.name}: ${coinBalance?.btc ?: "0"} BTC")

        // 5. Get transactions from local DB
        val rawTransactions = bitcoinTransactionRepository.getTransactionsSync(walletId, bitcoinCoin.network)

        val result = BitcoinDetailResult(
            walletId = walletId,
            address = bitcoinCoin.address,
            balance = coinBalance?.btc ?: "0",
            balanceFormatted = "${coinBalance?.btc ?: "0"} BTC",
            usdValue = coinBalance?.usdValue ?: BigDecimal.ZERO,
            network = bitcoinCoin.network,
            networkDisplayName = bitcoinCoin.network.name,
            rawTransactions = rawTransactions,
            bitcoinCoin = bitcoinCoin,
            availableNetworks = wallet.bitcoinCoins.map { it.network }
        )

        logger.d(TAG, "=== GetBitcoinDetailUseCase completed successfully ===")
        Result.Success(result)
    }

    companion object {
        private const val TAG = "GetBitcoinDetailUC"
    }
}