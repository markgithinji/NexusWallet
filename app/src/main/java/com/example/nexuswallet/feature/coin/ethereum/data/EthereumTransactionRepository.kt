package com.example.nexuswallet.feature.coin.ethereum.data

import com.example.nexuswallet.feature.coin.ethereum.EVMTransaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.flow.Flow

interface EVMTransactionRepository {
    suspend fun saveTransaction(transaction: EVMTransaction)
    suspend fun updateTransaction(transaction: EVMTransaction)
    suspend fun getTransaction(id: String): EVMTransaction?

    fun getTransactions(walletId: String): Flow<List<EVMTransaction>>
    fun getTransactionsByToken(walletId: String, tokenContract: String?): Flow<List<EVMTransaction>>
    fun getTransactionsByTokenExternalId(walletId: String, tokenExternalId: String): Flow<List<EVMTransaction>>
    fun getNativeTransactions(walletId: String): Flow<List<EVMTransaction>>
    fun observePendingTransactions(): Flow<List<EVMTransaction>>

    suspend fun getTransactionsSync(walletId: String): List<EVMTransaction>
    suspend fun getNativeTransactionsSync(walletId: String): List<EVMTransaction>
    suspend fun getTransactionsForToken(walletId: String, tokenExternalId: String): List<EVMTransaction>
    suspend fun getPendingTransactions(): List<EVMTransaction>

    suspend fun deleteTransaction(id: String)
    suspend fun deleteAllForWallet(walletId: String)
    suspend fun deleteForWalletAndToken(walletId: String, tokenExternalId: String)
    suspend fun updateTransactionStatus(transactionId: String, status: TransactionStatus)
}