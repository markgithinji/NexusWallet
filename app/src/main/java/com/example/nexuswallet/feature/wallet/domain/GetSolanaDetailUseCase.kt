package com.example.nexuswallet.feature.wallet.domain
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.domain.model.SolanaTransaction


interface GetSolanaDetailUseCase {
    suspend operator fun invoke(
        walletId: String,
        network: String = ""
    ): Result<SolanaDetailResult>
}

@Singleton
class GetSolanaDetailUseCaseImpl @Inject constructor(
    private val walletRepository: WalletRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger
) : GetSolanaDetailUseCase {

    private val tag = "GetSolanaDetailUC"

    override suspend operator fun invoke(
        walletId: String,
        network: String
    ): Result<SolanaDetailResult> {
        return try {
            logger.d(tag, "=== GetSolanaDetailUseCase started ===")
            logger.d(tag, "Getting Solana details for wallet: $walletId, network: $network")

            // 1. Get wallet
            val wallet = walletRepository.getWallet(walletId)
                ?: return Result.Error("Wallet not found")

            // 2. Find the specific Solana coin
            val solanaCoin = wallet.solanaCoins.find {
                when (network.lowercase()) {
                    "mainnet" -> it.network == SolanaNetwork.Mainnet
                    "devnet" -> it.network == SolanaNetwork.Devnet
                    else -> true
                }
            } ?: wallet.solanaCoins.firstOrNull()
            ?: return Result.Error("Solana not enabled")

            val networkStorage = solanaCoin.network.name

            logger.d(tag, "Found Solana coin with address: ${solanaCoin.address.take(8)}... on ${solanaCoin.network}")

            // 3. Delete old transactions before fetching new ones
            logger.d(tag, "Deleting old transactions for wallet $walletId, network $networkStorage")
            solanaTransactionRepository.deleteForWalletAndNetwork(walletId, networkStorage)

            // 4. Fetch transactions using Helius API
            logger.d(tag, "Fetching transactions from Helius API...")
            val heliusResult = solanaBlockchainRepository.getTransactions(
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

                        // Use the parser to get transfer info
                        val transferInfo = solanaBlockchainRepository.parseTransfer(
                            transaction = heliusTx,
                            walletAddress = solanaCoin.address
                        )

                        if (transferInfo != null) {
                            // This is a SOL transfer transaction
                            val transaction = SolanaTransaction(
                                id = heliusTx.signature,
                                walletId = walletId,
                                fromAddress = transferInfo.from,
                                toAddress = transferInfo.to,
                                status = if (heliusTx.transactionError == null)
                                    TransactionStatus.SUCCESS
                                else
                                    TransactionStatus.FAILED,
                                timestamp = heliusTx.timestamp * 1000, // Convert to milliseconds
                                note = heliusTx.description,
                                feeLevel = FeeLevel.NORMAL,
                                amountLamports = transferInfo.amount,
                                amountSol = (transferInfo.amount.toDouble() / 1_000_000_000).toString(),
                                feeLamports = heliusTx.fee,
                                feeSol = (heliusTx.fee.toDouble() / 1_000_000_000).toString(),
                                signature = heliusTx.signature,
                                network = solanaCoin.network,
                                isIncoming = transferInfo.isIncoming,
                                tokenMint = null,
                                tokenSymbol = null,
                                tokenDecimals = null,
                                slot = heliusTx.slot,
                                blockTime = heliusTx.timestamp
                            )
                            solanaTransactionRepository.saveTransaction(transaction)
                            savedCount++
                            logger.d(tag, "  Saved SOL transfer: ${heliusTx.signature.take(8)}..., amount: ${transferInfo.amount} lamports (${transferInfo.amount.toDouble() / 1_000_000_000} SOL)")
                        } else {
                            // This is not a SOL transfer (token transfer, NFT sale, etc.)
                            tokenTransferCount++
                            logger.d(tag, "  Non-SOL transaction (token/NFT/etc): ${heliusTx.signature.take(8)}...")
                        }
                    }

                    logger.d(tag, " Helius sync complete - SOL transfers: $savedCount, Other transactions: $tokenTransferCount")
                }

                is Result.Error -> {
                    logger.e(tag, " Helius API failed: ${heliusResult.message}")
                    return Result.Error("Failed to fetch transactions: ${heliusResult.message}")
                }

                Result.Loading -> {
                    // Should not happen
                    logger.d(tag, "Unexpected Loading state")
                }
            }

            // 5. Get balance
            val balance = walletRepository.getWalletBalance(walletId)
            val networkKey = when (solanaCoin.network) {
                SolanaNetwork.Mainnet -> "mainnet"
                SolanaNetwork.Devnet -> "devnet"
            }
            val coinBalance = balance?.solanaBalances?.get(networkKey)
            logger.d(tag, "Balance for $networkKey: ${coinBalance?.sol ?: "0"} SOL")

            // 6. Get raw transactions from local DB using the correct storage string
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
            Result.Success(result)

        } catch (e: Exception) {
            logger.e(tag, "Error getting Solana details", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }
}