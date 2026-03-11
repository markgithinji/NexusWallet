package com.example.nexuswallet.feature.solana.domain.repository

import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

interface SolanaTransactionRepository {
    suspend fun saveTransaction(transaction: com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction)
    suspend fun updateTransaction(transaction: com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction)
    suspend fun getTransaction(id: String): com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction?

    fun getTransactions(walletId: String, network: String): Flow<List<com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction>>
    fun getTransactionsByToken(walletId: String, tokenMint: String?, network: String): Flow<List<com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction>>
    fun getNativeTransactions(walletId: String, network: String): Flow<List<com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction>>
    fun observePendingTransactions(): Flow<List<com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction>>

    suspend fun getTransactionsSync(walletId: String, network: String): List<com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction>
    suspend fun getNativeTransactionsSync(walletId: String, network: String): List<com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction>

    suspend fun getPendingTransactions(): List<com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction>
    suspend fun deleteTransaction(id: String)
    suspend fun deleteAllForWallet(walletId: String)
    suspend fun deleteForWalletAndNetwork(walletId: String, network: String)
    suspend fun updateTransactionStatus(transactionId: String, status: TransactionStatus)
    suspend fun updateTransactionSignature(transactionId: String, signature: String)
    suspend fun confirmTransaction(transactionId: String, signature: String, slot: Long, blockTime: Long)
}