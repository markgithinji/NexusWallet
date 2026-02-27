package com.example.nexuswallet.feature.coin.ethereum.data

import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import kotlinx.coroutines.flow.Flow

interface EthereumTransactionRepository {
    suspend fun saveTransaction(transaction: EthereumTransaction)
    suspend fun updateTransaction(transaction: EthereumTransaction)
    suspend fun getTransaction(id: String): EthereumTransaction?
    fun getTransactions(walletId: String): Flow<List<EthereumTransaction>>
    suspend fun getPendingTransactions(): List<EthereumTransaction>
    suspend fun deleteTransaction(id: String)
    suspend fun deleteAllForWallet(walletId: String)
}