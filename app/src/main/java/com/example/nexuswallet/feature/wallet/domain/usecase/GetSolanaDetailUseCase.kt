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
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
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
                }

                logger.d(TAG, " Saved ${transactions.size} transactions to local DB")
            }

            is Result.Error -> {
                logger.e(TAG, " Blockchain repository failed: ${blockchainResult.message}")
            }

            Result.Loading -> {}
        }

        // 5. Get cached balance
        val balance = walletRepository.getWalletBalance(walletId)
        val coinBalance = balance?.solanaBalances?.get(network)
        logger.d(TAG, "Balance for ${network.name}: ${coinBalance?.sol ?: "0"} SOL")

        // 6. Get transactions from local DB
        logger.d(TAG, "Getting transactions from local DB with network: ${network.name}...")
        val allTransactions = solanaTransactionRepository.getTransactionsSync(walletId, network)
        
        // Filter for native SOL transactions (tokenSymbol == null)
        val solTransactions = allTransactions.filter { it.tokenSymbol == null }

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
            "=== GetSolanaDetailUseCase completed successfully ==="
        )
        Result.Success(result)
    }

    companion object {
        private const val TAG = "GetSolanaDetailUC"
    }
}