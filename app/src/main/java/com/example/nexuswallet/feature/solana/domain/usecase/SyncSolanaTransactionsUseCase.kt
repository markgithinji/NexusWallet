package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSolanaTransactionsUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val walletRepository: WalletRepository,
    private val logger: Logger
) {

    private val tag = "SyncSolanaUC"

    suspend operator fun invoke(walletId: String, network: SolanaNetwork): Result<Unit> =
        withContext(Dispatchers.IO) {
            logger.d(
                tag,
                "Syncing Solana transactions for wallet: $walletId, network: ${network.displayName}"
            )

            val wallet = walletRepository.getWallet(walletId) ?: run {
                logger.e(tag, "Wallet not found: $walletId")
                return@withContext Result.Error("Wallet not found")
            }

            // Find the specific Solana coin by network
            val solanaCoin = wallet.solanaCoins.find { it.network == network }

            if (solanaCoin == null) {
                logger.e(
                    tag,
                    "Solana ${network.displayName} not enabled for wallet: ${wallet.name}"
                )
                return@withContext Result.Error("Solana ${network.displayName} not enabled")
            }

            logger.d(
                tag,
                "Syncing for wallet: ${wallet.name}, Address: ${solanaCoin.address.take(8)}..., Network: ${solanaCoin.network.displayName}"
            )

            // Get transactions from Helius API
            val historyResult = solanaBlockchainRepository.getTransactions(
                walletId = walletId,
                address = solanaCoin.address,
                network = network,
                limit = 50
            )

            var savedCount = 0

            when (historyResult) {
                is Result.Success -> {
                    val transactions = historyResult.data
                    logger.d(
                        tag,
                        "Received ${transactions.size} transactions on ${network.displayName}"
                    )

                    if (transactions.isNotEmpty()) {
                        solanaTransactionRepository.deleteForWalletAndNetwork(
                            walletId,
                            network
                        )
                        logger.d(tag, "Deleted existing transactions for ${network.displayName}")

                        // Save the new transactions
                        transactions.forEach { transaction ->
                            solanaTransactionRepository.saveTransaction(transaction)
                            savedCount++
                        }

                        logger.d(tag, "Saved $savedCount transactions to database")
                    } else {
                        logger.d(tag, "No transactions found on ${network.displayName}")
                    }

                    logger.d(
                        tag,
                        "Successfully synced $savedCount transactions on ${network.displayName}"
                    )
                }

                is Result.Error -> {
                    logger.e(
                        tag,
                        "Failed to fetch transactions on ${network.displayName}: ${historyResult.message}"
                    )
                    return@withContext Result.Error(historyResult.message)
                }

                Result.Loading -> {
                    // Do nothing
                }
            }

            logger.d(
                tag,
                "=== Sync completed for wallet $walletId on ${network.displayName} (saved: $savedCount transactions) ==="
            )
            Result.Success(Unit)
        }
}