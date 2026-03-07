package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.ethereum.EVMTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import kotlinx.coroutines.flow.Flow

interface GetAllTransactionsUseCase {
    // One-time fetch with network calls (original)
    suspend operator fun invoke(walletId: String): List<Any>

    // Get only cached transactions (no network calls)
    suspend fun getCachedTransactions(walletId: String): List<Any>

    // Force refresh transactions (network calls)
    suspend fun refreshTransactions(walletId: String)

    // Observe transactions (real-time updates)
    fun observeTransactions(walletId: String): Flow<List<Any>>
}