package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.ethereum.data.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransaction
import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.GetAllTransactionsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllTransactionsUseCaseImpl @Inject constructor(
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val usdcTransactionRepository: USDCTransactionRepository,
    private val logger: Logger
) : GetAllTransactionsUseCase {

    private val tag = "GetAllTransactionsUC"

    override suspend fun invoke(walletId: String): List<Any> = withContext(Dispatchers.IO) {
        logger.d(tag, "Fetching all transactions for wallet: $walletId")
        val startTime = System.currentTimeMillis()

        val deferred = listOf(
            async {
                bitcoinTransactionRepository.getTransactions(walletId).firstOrNull()
                    ?: emptyList<BitcoinTransaction>()
            },
            async {
                ethereumTransactionRepository.getTransactions(walletId).firstOrNull()
                    ?: emptyList<EthereumTransaction>()
            },
            async {
                solanaTransactionRepository.getTransactions(walletId).firstOrNull()
                    ?: emptyList<SolanaTransaction>()
            },
            async {
                usdcTransactionRepository.getTransactions(walletId).firstOrNull()
                    ?: emptyList<USDCTransaction>()
            }
        )

        val results = deferred.awaitAll()
        val bitcoinCount = results[0].size
        val ethereumCount = results[1].size
        val solanaCount = results[2].size
        val usdcCount = results[3].size

        logger.d(
            tag,
            "Retrieved transactions - BTC: $bitcoinCount, ETH: $ethereumCount, SOL: $solanaCount, USDC: $usdcCount"
        )

        val sortedTransactions = results
            .flatten()
            .sortedByDescending { transaction ->
                when (transaction) {
                    is BitcoinTransaction -> transaction.timestamp
                    is EthereumTransaction -> transaction.timestamp
                    is SolanaTransaction -> transaction.timestamp
                    is USDCTransaction -> transaction.timestamp
                    else -> 0L
                }
            }

        val duration = System.currentTimeMillis() - startTime
        logger.d(tag, "Returning ${sortedTransactions.size} total transactions in ${duration}ms")

        sortedTransactions
    }

    override fun observeTransactions(walletId: String): Flow<List<Any>> = combine(
        bitcoinTransactionRepository.getTransactions(walletId),
        ethereumTransactionRepository.getTransactions(walletId),
        solanaTransactionRepository.getTransactions(walletId),
        usdcTransactionRepository.getTransactions(walletId)
    ) { bitcoinTxs, ethereumTxs, solanaTxs, usdcTxs ->
        val combined = (bitcoinTxs + ethereumTxs + solanaTxs + usdcTxs)
            .sortedByDescending { transaction ->
                when (transaction) {
                    is BitcoinTransaction -> transaction.timestamp
                    is EthereumTransaction -> transaction.timestamp
                    is SolanaTransaction -> transaction.timestamp
                    is USDCTransaction -> transaction.timestamp
                    else -> 0L
                }
            }

        logger.d(
            tag,
            "Observing transactions for wallet $walletId - BTC: ${bitcoinTxs.size}, ETH: ${ethereumTxs.size}, SOL: ${solanaTxs.size}, USDC: ${usdcTxs.size}, Total: ${combined.size}"
        )

        combined
    }
}