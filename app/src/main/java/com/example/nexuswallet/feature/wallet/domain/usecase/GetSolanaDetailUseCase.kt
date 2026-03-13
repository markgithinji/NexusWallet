package com.example.nexuswallet.feature.wallet.domain.usecase
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.data.toStorageString
import com.example.nexuswallet.feature.solana.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.SolanaDetailResult
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSolanaDetailUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger
) {

    private val tag = "GetSolanaDetailUC"

    suspend operator fun invoke(
        walletId: String,
        network: SolanaNetwork
    ): Result<SolanaDetailResult> {
        logger.d(tag, "=== GetSolanaDetailUseCase started ===")
        logger.d(tag, "Getting Solana details for wallet: $walletId, network: ${network.name}")

        // 1. Get wallet
        val wallet = walletRepository.getWallet(walletId)
            ?: return Result.Error("Wallet not found")

        // 2. Find the specific Solana coin
        val solanaCoin = wallet.solanaCoins.find { it.network == network }
            ?: return Result.Error("Solana ${network.displayName} not enabled for this wallet")

        logger.d(tag, "Found Solana coin with address: ${solanaCoin.address.take(8)}... on ${network.displayName}")

        // 3. Delete old transactions before fetching new ones
        val networkStorage = network.toStorageString()
        logger.d(tag, "Deleting old transactions for wallet $walletId, network ${network.displayName}")
        solanaTransactionRepository.deleteForWalletAndNetwork(walletId, networkStorage)

        // 4. Fetch transactions from blockchain repository
        logger.d(tag, "Fetching transactions from blockchain repository...")
        val blockchainResult = solanaBlockchainRepository.getTransactions(
            walletId = walletId,
            address = solanaCoin.address,
            network = network,
            limit = 50
        )

        var savedCount = 0
        var tokenTransferCount = 0

        when (blockchainResult) {
            is Result.Success -> {
                val transactions = blockchainResult.data
                logger.d(tag, " Successfully fetched ${transactions.size} transactions from blockchain")

                // Save all transactions to local DB
                transactions.forEach { transaction ->
                    solanaTransactionRepository.saveTransaction(transaction)
                    savedCount++
                }

                logger.d(tag, " Saved $savedCount transactions to local DB")
            }

            is Result.Error -> {
                logger.e(tag, " Blockchain repository failed: ${blockchainResult.message}")
                // Don't return error - we'll continue with whatever we have in DB
            }

            Result.Loading -> {
                // Should not happen
            }
        }

        // 5. Get balance
        val balance = walletRepository.getWalletBalance(walletId)
        val coinBalance = balance?.solanaBalances?.get(network)
        logger.d(tag, "Balance for ${network.displayName}: ${coinBalance?.sol ?: "0"} SOL")

        // 6. Get transactions from local DB (now includes both old and new)
        logger.d(tag, "Getting transactions from local DB with network: $networkStorage...")
        val allTransactions = solanaTransactionRepository.getTransactionsSync(walletId, networkStorage)
        logger.d(tag, "Retrieved ${allTransactions.size} total transactions from DB")

        // Filter for native SOL transactions (tokenSymbol == null)
        val solTransactions = allTransactions.filter { it.tokenSymbol == null }
        logger.d(tag, "Filtered to ${solTransactions.size} native SOL transactions")

        // Log sample for debugging
        if (solTransactions.isNotEmpty()) {
            logger.d(tag, "Sample of saved transactions:")
            solTransactions.take(3).forEachIndexed { index, tx ->
                logger.d(tag, "  Tx $index: ${tx.id.take(8)}..., amount: ${tx.amountSol} SOL, " +
                        "from: ${tx.fromAddress.take(8)}..., to: ${tx.toAddress.take(8)}..., " +
                        "incoming: ${tx.isIncoming}")
            }
        }

        // 7. Prepare result
        val result = SolanaDetailResult(
            walletId = walletId,
            address = solanaCoin.address,
            balance = coinBalance?.sol ?: "0",
            balanceFormatted = "${coinBalance?.sol ?: "0"} SOL",
            usdValue = coinBalance?.usdValue ?: 0.0,
            network = network.name,
            networkDisplayName = network.displayName,
            rawTransactions = solTransactions,
            solanaCoin = solanaCoin,
            splTokens = solanaCoin.splTokens,
            availableNetworks = wallet.solanaCoins.map { it.network }
        )

        logger.d(tag, "=== GetSolanaDetailUseCase completed successfully with ${solTransactions.size} raw transactions ===")
        return Result.Success(result)
    }
}