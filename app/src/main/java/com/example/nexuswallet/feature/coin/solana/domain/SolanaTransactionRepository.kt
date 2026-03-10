package com.example.nexuswallet.feature.coin.solana.domain

import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.flow.Flow

interface SolanaTransactionRepository {
    suspend fun saveTransaction(transaction: SolanaTransaction)
    suspend fun updateTransaction(transaction: SolanaTransaction)
    suspend fun getTransaction(id: String): SolanaTransaction?

    fun getTransactions(walletId: String, network: String): Flow<List<SolanaTransaction>>
    fun getTransactionsByToken(walletId: String, tokenMint: String?, network: String): Flow<List<SolanaTransaction>>
    fun getNativeTransactions(walletId: String, network: String): Flow<List<SolanaTransaction>>
    fun observePendingTransactions(): Flow<List<SolanaTransaction>>

    suspend fun getTransactionsSync(walletId: String, network: String): List<SolanaTransaction>
    suspend fun getNativeTransactionsSync(walletId: String, network: String): List<SolanaTransaction>

    suspend fun getPendingTransactions(): List<SolanaTransaction>
    suspend fun deleteTransaction(id: String)
    suspend fun deleteAllForWallet(walletId: String)
    suspend fun deleteForWalletAndNetwork(walletId: String, network: String)
    suspend fun updateTransactionStatus(transactionId: String, status: TransactionStatus)
    suspend fun updateTransactionSignature(transactionId: String, signature: String)
    suspend fun confirmTransaction(transactionId: String, signature: String, slot: Long, blockTime: Long)
}