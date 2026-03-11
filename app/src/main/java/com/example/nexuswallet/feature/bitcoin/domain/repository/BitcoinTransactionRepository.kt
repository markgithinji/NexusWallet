package com.example.nexuswallet.feature.bitcoin.domain.repository

import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinTransaction
import kotlinx.coroutines.flow.Flow

interface BitcoinTransactionRepository {
    suspend fun saveTransaction(transaction: BitcoinTransaction)
    suspend fun updateTransaction(transaction: BitcoinTransaction)
    suspend fun getTransaction(id: String): BitcoinTransaction?

    fun getTransactions(walletId: String, network: String): Flow<List<BitcoinTransaction>>

    suspend fun getTransactionsSync(walletId: String, network: String): List<BitcoinTransaction>

    suspend fun getPendingTransactions(): List<BitcoinTransaction>

    suspend fun deleteTransaction(id: String)
    suspend fun deleteAllForWallet(walletId: String)
    suspend fun deleteForWalletAndNetwork(walletId: String, network: String)
}