package com.example.nexuswallet.feature.wallet.domain.usecase
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.wallet.domain.model.SolanaDetailResult
import com.example.nexuswallet.feature.solana.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository

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
        network: SolanaNetwork  // Changed from String to SolanaNetwork
    ): Result<SolanaDetailResult> {
        logger.d(tag, "=== GetSolanaDetailUseCase started ===")
        logger.d(tag, "Getting Solana details for wallet: $walletId, network: $network")

        // 1. Get wallet
        val wallet = walletRepository.getWallet(walletId)
            ?: return Result.Error("Wallet not found")

        // 2. Find the specific Solana coin
        val solanaCoin = wallet.solanaCoins.find { it.network == network }
            ?: wallet.solanaCoins.firstOrNull()
            ?: return Result.Error("Solana not enabled for $network")

        val networkStorage = solanaCoin.network.name

        logger.d(tag, "Found Solana coin with address: ${solanaCoin.address.take(8)}... on ${solanaCoin.network}")

        // 3. Delete old transactions before fetching new ones
        logger.d(tag, "Deleting old transactions for wallet $walletId, network $networkStorage")
        solanaTransactionRepository.deleteForWalletAndNetwork(walletId, networkStorage)

        // 4. Fetch transactions using Helius API
        logger.d(tag, "Fetching transactions from Helius API...")
        val heliusResult = solanaBlockchainRepository.getTransactions(
            walletId = walletId,
            address = solanaCoin.address,
            network = solanaCoin.network,
            limit = 50
        )

        var savedCount = 0
        var tokenTransferCount = 0

        when (heliusResult) {
            is Result.Success -> {
                val transactions = heliusResult.data
                logger.d(tag, " Successfully fetched ${transactions.size} transactions from Helius")

                transactions.forEachIndexed { index, heliusTx ->
                    logger.d(tag, "Processing Helius transaction ${index + 1}: ${heliusTx.signature.take(8)}...")

                    // Check if this is a token transfer (has token transfers)
                    val isTokenTransfer = heliusTx.tokenTransfers.isNotEmpty()

                    if (!isTokenTransfer) {
                        // Native SOL transfer
                        solanaTransactionRepository.saveTransaction(heliusTx)
                        savedCount++
                        logger.d(tag, "  Saved SOL transfer: ${heliusTx.signature.take(8)}..., amount: ${heliusTx.amountSol} SOL")
                    } else {
                        // This is a token transfer (already handled in repository)
                        tokenTransferCount++
                        logger.d(tag, "  Token transaction: ${heliusTx.signature.take(8)}...")
                    }
                }

                logger.d(tag, " Helius sync complete - SOL transfers: $savedCount, Token transactions: $tokenTransferCount")
            }

            is Result.Error -> {
                logger.e(tag, " Helius API failed: ${heliusResult.message}")
                return Result.Error("Failed to fetch transactions: ${heliusResult.message}")
            }

            Result.Loading -> {
                // Should not happen
            }
        }

        // 5. Get balance
        val balance = walletRepository.getWalletBalance(walletId)
        val coinBalance = balance?.solanaBalances?.get(solanaCoin.network)  // Direct network lookup
        logger.d(tag, "Balance for ${solanaCoin.network}: ${coinBalance?.sol ?: "0"} SOL")

        // 6. Get raw transactions from local DB
        logger.d(tag, "Getting transactions from local DB with network: $networkStorage...")
        val transactions = solanaTransactionRepository.getTransactionsSync(walletId, networkStorage)
        logger.d(tag, "Retrieved ${transactions.size} transactions from DB")

        val solTxs = transactions.filter { it.tokenSymbol == null }
        logger.d(tag, "Filtered to ${solTxs.size} native SOL transactions")

        if (solTxs.isNotEmpty()) {
            logger.d(tag, "Sample of saved transactions:")
            solTxs.take(3).forEachIndexed { index, tx ->
                logger.d(tag, "  Tx $index: ${tx.id.take(8)}..., amount: ${tx.amountSol} SOL, " +
                        "from: ${tx.fromAddress.take(8)}..., to: ${tx.toAddress.take(8)}..., " +
                        "incoming: ${tx.isIncoming}")
            }
        }

        // 7. Return raw transactions
        val result = SolanaDetailResult(
            walletId = walletId,
            address = solanaCoin.address,
            balance = coinBalance?.sol ?: "0",
            balanceFormatted = "${coinBalance?.sol ?: "0"} SOL",
            usdValue = coinBalance?.usdValue ?: 0.0,
            network = solanaCoin.network.name,
            networkDisplayName = when (solanaCoin.network) {
                SolanaNetwork.Mainnet -> "Mainnet"
                SolanaNetwork.Devnet -> "Devnet"
            },
            rawTransactions = solTxs,
            solanaCoin = solanaCoin,
            splTokens = solanaCoin.splTokens,
            availableNetworks = wallet.solanaCoins.map { it.network }
        )

        logger.d(tag, "=== GetSolanaDetailUseCase completed successfully with ${solTxs.size} raw transactions ===")
        return Result.Success(result)
    }
}