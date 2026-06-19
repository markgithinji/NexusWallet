package com.example.nexuswallet.feature.solana.domain.repository

import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

interface SolanaTransactionRepository {
    suspend fun saveTransaction(transaction: SolanaTransaction)
    suspend fun updateTransaction(transaction: SolanaTransaction)
    suspend fun getTransaction(id: String): SolanaTransaction?

    fun getTransactions(
        walletId: String,
        network: SolanaNetwork
    ): Flow<List<SolanaTransaction>>

    fun getTransactionsByToken(
        walletId: String,
        tokenMint: String?,
        network: SolanaNetwork
    ): Flow<List<SolanaTransaction>>

    fun getNativeTransactions(
        walletId: String,
        network: SolanaNetwork
    ): Flow<List<SolanaTransaction>>

    fun observePendingTransactions(): Flow<List<SolanaTransaction>>

    suspend fun getTransactionsSync(
        walletId: String,
        network: SolanaNetwork
    ): List<SolanaTransaction>

    suspend fun getNativeTransactionsSync(
        walletId: String,
        network: SolanaNetwork
    ): List<SolanaTransaction>

    suspend fun getPendingTransactions(): List<SolanaTransaction>
    suspend fun deleteTransaction(id: String)
    suspend fun deleteAllForWallet(walletId: String)

    suspend fun deleteForWalletAndNetwork(
        walletId: String,
        network: SolanaNetwork
    )

    suspend fun replaceTransactions(
        walletId: String,
        network: SolanaNetwork,
        transactions: List<SolanaTransaction>
    )

    suspend fun updateTransactionStatus(transactionId: String, status: TransactionStatus)
    suspend fun updateTransactionSignature(transactionId: String, signature: String)
    suspend fun confirmTransaction(transactionId: String, signature: String, slot: Long, blockTime: Long)
}