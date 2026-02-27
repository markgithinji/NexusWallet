package com.example.nexuswallet.feature.coin.solana.domain

import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import kotlinx.coroutines.flow.Flow

interface SolanaTransactionRepository {
    suspend fun saveTransaction(transaction: SolanaTransaction)
    suspend fun updateTransaction(transaction: SolanaTransaction)
    suspend fun getTransaction(id: String): SolanaTransaction?
    fun getTransactions(walletId: String): Flow<List<SolanaTransaction>>
    suspend fun getPendingTransactions(): List<SolanaTransaction>
    suspend fun deleteTransaction(id: String)
    suspend fun deleteAllForWallet(walletId: String)
    suspend fun updateSignedTransaction(
        transactionId: String,
        signedData: String,
        signature: String
    )

    suspend fun updateTransactionStatus(
        transactionId: String,
        success: Boolean,
        signature: String?
    )
}