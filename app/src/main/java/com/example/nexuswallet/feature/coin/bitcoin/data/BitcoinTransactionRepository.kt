package com.example.nexuswallet.feature.coin.bitcoin.data

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import kotlinx.coroutines.flow.Flow

interface BitcoinTransactionRepository {
    suspend fun deleteAllForWallet(walletId: String)
    suspend fun saveTransaction(transaction: BitcoinTransaction)
    suspend fun updateTransaction(transaction: BitcoinTransaction)
    suspend fun getTransaction(id: String): BitcoinTransaction?
    fun getTransactions(walletId: String): Flow<List<BitcoinTransaction>>
    suspend fun getPendingTransactions(): List<BitcoinTransaction>
}