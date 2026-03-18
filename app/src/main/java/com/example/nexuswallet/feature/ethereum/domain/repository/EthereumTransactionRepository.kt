package com.example.nexuswallet.feature.ethereum.domain.repository

import com.example.nexuswallet.feature.core.domain.model.EVMTransaction
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.ethereum.domain.model.TokenType
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

interface EVMTransactionRepository {
    suspend fun saveTransaction(transaction: EVMTransaction)
    suspend fun updateTransaction(transaction: EVMTransaction)
    suspend fun getTransaction(id: String): EVMTransaction?
    fun getTransactions(walletId: String): Flow<List<EVMTransaction>>
    fun getTransactionsByToken(walletId: String, tokenContract: String?): Flow<List<EVMTransaction>>
    fun getTransactionsByTokenType(walletId: String, tokenType: TokenType): Flow<List<EVMTransaction>>
    fun getNativeTransactions(walletId: String): Flow<List<EVMTransaction>>
    fun observePendingTransactions(): Flow<List<EVMTransaction>>
    suspend fun getTransactionsSync(walletId: String): List<EVMTransaction>
    suspend fun getNativeTransactionsSync(walletId: String): List<EVMTransaction>
    suspend fun getTransactionsForTokenType(walletId: String, tokenType: TokenType): List<EVMTransaction>
    suspend fun getPendingTransactions(): List<EVMTransaction>
    suspend fun deleteTransaction(id: String)
    suspend fun deleteAllForWallet(walletId: String)
    suspend fun deleteForWalletAndTokenType(walletId: String, tokenType: TokenType)
    suspend fun updateTransactionStatus(transactionId: String, status: TransactionStatus)
    suspend fun getNativeTransaction(id: String): NativeETHTransaction?
    suspend fun getTokenTransaction(id: String, tokenType: TokenType): TokenTransaction?
}