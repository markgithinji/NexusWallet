package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.domain.USDCSendTransaction
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
class GetAllTransactionsUseCase @Inject constructor(
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val usdcTransactionRepository: USDCTransactionRepository
) {
    suspend operator fun invoke(walletId: String): List<Any> = withContext(Dispatchers.IO) {
        val deferred = listOf(
            async {
                bitcoinTransactionRepository.getTransactions(walletId).firstOrNull() ?: emptyList<BitcoinTransaction>()
            },
            async {
                ethereumTransactionRepository.getTransactions(walletId).firstOrNull() ?: emptyList<EthereumTransaction>()
            },
            async {
                solanaTransactionRepository.getTransactions(walletId).firstOrNull() ?: emptyList<SolanaTransaction>()
            },
            async {
                usdcTransactionRepository.getTransactions(walletId).firstOrNull() ?: emptyList<USDCSendTransaction>()
            }
        )

        val results = deferred.awaitAll()

        results
            .flatten()
            .sortedByDescending { transaction ->
                when (transaction) {
                    is BitcoinTransaction -> transaction.timestamp
                    is EthereumTransaction -> transaction.timestamp
                    is SolanaTransaction -> transaction.timestamp
                    is USDCSendTransaction -> transaction.timestamp
                    else -> 0L
                }
            }
    }

    fun observeTransactions(walletId: String): Flow<List<Any>> = combine(
        bitcoinTransactionRepository.getTransactions(walletId),
        ethereumTransactionRepository.getTransactions(walletId),
        solanaTransactionRepository.getTransactions(walletId),
        usdcTransactionRepository.getTransactions(walletId)
    ) { bitcoinTxs, ethereumTxs, solanaTxs, usdcTxs ->
        (bitcoinTxs + ethereumTxs + solanaTxs + usdcTxs)
            .sortedByDescending { transaction ->
                when (transaction) {
                    is BitcoinTransaction -> transaction.timestamp
                    is EthereumTransaction -> transaction.timestamp
                    is SolanaTransaction -> transaction.timestamp
                    is USDCSendTransaction -> transaction.timestamp
                    else -> 0L
                }
            }
    }
}