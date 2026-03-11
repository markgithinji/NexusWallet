package com.example.nexuswallet.feature.solana.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
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

    suspend operator fun invoke(walletId: String, network: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            logger.d(tag, "Syncing Solana transactions for wallet: $walletId, network: $network")

            val wallet = walletRepository.getWallet(walletId) ?: run {
                logger.e(tag, "Wallet not found: $walletId")
                return@withContext Result.Error("Wallet not found")
            }

            // Find the specific Solana coin by network
            val solanaCoin = when (network.lowercase()) {
                "mainnet" -> wallet.solanaCoins.find { it.network == SolanaNetwork.Mainnet }
                "devnet" -> wallet.solanaCoins.find { it.network == SolanaNetwork.Devnet }
                else -> null
            }

            if (solanaCoin == null) {
                logger.e(tag, "Solana $network not enabled for wallet: ${wallet.name}")
                return@withContext Result.Error("Solana $network not enabled")
            }

            logger.d(
                tag,
                "Syncing for wallet: ${wallet.name}, Address: ${solanaCoin.address.take(8)}..., Network: ${solanaCoin.network}"
            )

            // Get transactions from Helius API
            val historyResult = solanaBlockchainRepository.getTransactions(
                address = solanaCoin.address,
                network = solanaCoin.network,
                limit = 50
            )

            var savedCount = 0

            when (historyResult) {
                is Result.Success -> {
                    val transactions = historyResult.data
                    logger.d(
                        tag,
                        "Received ${transactions.size} transactions on ${solanaCoin.network}"
                    )

                    if (transactions.isNotEmpty()) {
                        val networkStorage = solanaCoin.network.name

                        // Delete existing transactions for this specific wallet and network
                        solanaTransactionRepository.deleteForWalletAndNetwork(
                            walletId,
                            networkStorage
                        )
                        logger.d(tag, "Deleted existing transactions for $networkStorage")

                        transactions.forEachIndexed { index, heliusTx ->
                            // Parse transfer info
                            val transferInfo = solanaBlockchainRepository.parseTransfer(
                                transaction = heliusTx,
                                walletAddress = solanaCoin.address
                            )

                            if (transferInfo != null && heliusTx.tokenTransfers.isEmpty()) {
                                // Only save native SOL transfers (skip token transactions)
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

                                logger.d(
                                    tag,
                                    "Transaction #$index on ${solanaCoin.network}: ${
                                        transaction.signature?.take(
                                            8
                                        )
                                    }..."
                                )
                                logger.d(tag, "  isIncoming: ${transaction.isIncoming}")
                                logger.d(
                                    tag,
                                    "  amount: ${transaction.amountLamports} lamports (${transaction.amountSol} SOL)"
                                )

                                solanaTransactionRepository.saveTransaction(transaction)
                                savedCount++
                            } else if (heliusTx.tokenTransfers.isNotEmpty()) {
                                logger.d(
                                    tag,
                                    "Skipping token transaction #$index: ${heliusTx.signature.take(8)}..."
                                )
                            }
                        }
                    } else {
                        logger.d(tag, "No transactions found on ${solanaCoin.network}")
                    }

                    logger.d(
                        tag,
                        "Successfully saved $savedCount transactions on ${solanaCoin.network}"
                    )
                }

                is Result.Error -> {
                    logger.e(
                        tag,
                        "Failed to fetch transactions on ${solanaCoin.network}: ${historyResult.message}"
                    )
                    return@withContext Result.Error(historyResult.message)
                }

                else -> {}
            }

            logger.d(
                tag,
                "=== Sync completed for wallet $walletId on $network (saved: $savedCount transactions) ==="
            )
            Result.Success(Unit)
        }
}