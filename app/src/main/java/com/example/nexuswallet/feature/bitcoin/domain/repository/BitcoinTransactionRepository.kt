package com.example.nexuswallet.feature.bitcoin.domain.repository

import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import kotlinx.coroutines.flow.Flow

interface BitcoinTransactionRepository {
    suspend fun saveTransaction(transaction: BitcoinTransaction)
    suspend fun updateTransaction(transaction: BitcoinTransaction)
    suspend fun getTransaction(id: String): BitcoinTransaction?

    fun getTransactions(
        walletId: String,
        network: BitcoinNetwork
    ): Flow<List<BitcoinTransaction>>

    suspend fun getTransactionsSync(
        walletId: String,
        network: BitcoinNetwork
    ): List<BitcoinTransaction>

    suspend fun getPendingTransactions(): List<BitcoinTransaction>
    suspend fun deleteTransaction(id: String)
    suspend fun deleteAllForWallet(walletId: String)

    suspend fun deleteForWalletAndNetwork(
        walletId: String,
        network: BitcoinNetwork
    )
}