package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.SolanaDetailResult
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSolanaDetailUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val syncSolanaBalanceUseCase: SyncSolanaBalanceUseCase,
    private val getSimplePricesUseCase: GetSimplePricesUseCase,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(
        walletId: String,
        network: SolanaNetwork
    ): Result<SolanaDetailResult> = withContext(ioDispatcher) {
        logger.d(TAG, "=== GetSolanaDetailUseCase started ===")
        logger.d(TAG, "Getting Solana details for wallet: $walletId, network: ${network.name}")

        // 1. Get wallet
        val wallet = walletRepository.getWallet(walletId)
            ?: return@withContext Result.Error("Wallet not found")

        // 2. Find the specific Solana coin
        val solanaCoin = wallet.solanaCoins.find { it.network == network }
            ?: return@withContext Result.Error("Solana ${network.name} not enabled for this wallet")

        logger.d(
            TAG,
            "Found Solana coin with address: ${solanaCoin.address.take(8)}... on ${network.name}"
        )

        // 3. Delete old transactions before fetching new ones
        logger.d(TAG, "Deleting old transactions for wallet $walletId, network ${network.name}")
        solanaTransactionRepository.deleteForWalletAndNetwork(walletId, network)

        // 4. Fetch transactions from blockchain repository
        logger.d(TAG, "Fetching transactions from blockchain repository for ${network.name}...")
        val blockchainResult = solanaBlockchainRepository.getTransactions(
            walletId = walletId,
            address = solanaCoin.address,
            network = network,
            limit = 50
        )

        var savedCount = 0

        when (blockchainResult) {
            is Result.Success -> {
                val transactions = blockchainResult.data
                logger.d(
                    TAG,
                    " Successfully fetched ${transactions.size} transactions from blockchain"
                )

                // Save all transactions to local DB
                transactions.forEach { transaction ->
                    solanaTransactionRepository.saveTransaction(transaction)
                    savedCount++
                }

                logger.d(TAG, " Saved $savedCount transactions to local DB")
            }

            is Result.Error -> {
                logger.e(TAG, " Blockchain repository failed: ${blockchainResult.message}")
                // Don't return error, we'll continue with whatever we have in DB
            }

            Result.Loading -> {
                // Should not happen
            }
        }

        // 4.5. Sync fresh balance
        try {
            val currency = settingsRepository.getSelectedCurrency()
            val pricesResult = getSimplePricesUseCase(listOf(solanaCoin.symbol), currency)
            val price = if (pricesResult is Result.Success) pricesResult.data[solanaCoin.symbol] ?: 0.0 else 0.0

            syncSolanaBalanceUseCase(walletId, solanaCoin, price)
            logger.d(TAG, "Synced balance for $walletId, network ${network.name}")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to sync Solana balance", e)
        }

        // 5. Get balance (Already synced)
        val balance = walletRepository.getWalletBalance(walletId)
        val coinBalance = balance?.solanaBalances?.get(network)
        logger.d(TAG, "Balance for ${network.name}: ${coinBalance?.sol ?: "0"} SOL")

        // 6. Get transactions from local DB
        logger.d(TAG, "Getting transactions from local DB with network: ${network.name}...")
        val allTransactions = solanaTransactionRepository.getTransactionsSync(walletId, network)
        logger.d(TAG, "Retrieved ${allTransactions.size} total transactions from DB")

        // Filter for native SOL transactions (tokenSymbol == null)
        val solTransactions = allTransactions.filter { it.tokenSymbol == null }
        logger.d(TAG, "Filtered to ${solTransactions.size} native SOL transactions")

        // 7. Prepare result with network object
        val result = SolanaDetailResult(
            walletId = walletId,
            address = solanaCoin.address,
            balance = coinBalance?.sol ?: "0",
            balanceFormatted = "${coinBalance?.sol ?: "0"} SOL",
            usdValue = coinBalance?.usdValue ?: BigDecimal.ZERO,
            network = network,
            networkDisplayName = network.name,
            rawTransactions = solTransactions,
            solanaCoin = solanaCoin,
            splTokens = solanaCoin.splTokens,
            availableNetworks = wallet.solanaCoins.map { it.network }
        )

        logger.d(
            TAG,
            "=== GetSolanaDetailUseCase completed successfully with ${solTransactions.size} raw transactions on ${network.name} ==="
        )
        Result.Success(result)
    }

    companion object {
        private const val TAG = "GetSolanaDetailUC"
    }
}