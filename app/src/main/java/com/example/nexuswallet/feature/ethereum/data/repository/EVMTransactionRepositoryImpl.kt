package com.example.nexuswallet.feature.ethereum.data.repository

import com.example.nexuswallet.feature.core.domain.model.EVMTransaction
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.ethereum.data.local.EVMTransactionDao
import com.example.nexuswallet.feature.ethereum.data.toDomain
import com.example.nexuswallet.feature.ethereum.data.toEntity
import com.example.nexuswallet.feature.ethereum.domain.model.TokenType
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EVMTransactionRepositoryImpl @Inject constructor(
    private val evmTransactionDao: EVMTransactionDao
) : EVMTransactionRepository {

    override suspend fun saveTransaction(transaction: EVMTransaction) {
        val entity = transaction.toEntity()
        evmTransactionDao.insert(entity)
    }

    override suspend fun updateTransaction(transaction: EVMTransaction) {
        val entity = transaction.toEntity()
        evmTransactionDao.update(entity)
    }

    override suspend fun getTransaction(id: String): EVMTransaction? {
        return evmTransactionDao.getById(id)?.toDomain()
    }

    override fun getTransactions(walletId: String): Flow<List<EVMTransaction>> {
        return evmTransactionDao.getByWalletId(walletId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTransactionsByToken(
        walletId: String,
        tokenContract: String?
    ): Flow<List<EVMTransaction>> {
        return evmTransactionDao.getByWalletIdAndToken(walletId, tokenContract)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTransactionsByTokenType(
        walletId: String,
        tokenType: TokenType
    ): Flow<List<EVMTransaction>> {
        return evmTransactionDao.getByWalletIdAndTokenType(walletId, tokenType)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getNativeTransactions(walletId: String): Flow<List<EVMTransaction>> {
        return evmTransactionDao.getNativeTransactions(walletId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun observePendingTransactions(): Flow<List<EVMTransaction>> {
        return evmTransactionDao.observePendingTransactions()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getTransactionsSync(walletId: String): List<EVMTransaction> {
        return evmTransactionDao.getByWalletIdSync(walletId)
            .map { it.toDomain() }
    }

    override suspend fun getNativeTransactionsSync(walletId: String): List<EVMTransaction> {
        return evmTransactionDao.getNativeTransactionsSync(walletId)
            .map { it.toDomain() }
    }

    override suspend fun getTransactionsForTokenType(
        walletId: String,
        tokenType: TokenType
    ): List<EVMTransaction> {
        return evmTransactionDao.getTransactionsForTokenType(walletId, tokenType)
            .map { it.toDomain() }
    }

    override suspend fun getPendingTransactions(): List<EVMTransaction> {
        return evmTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    override suspend fun deleteTransaction(id: String) {
        evmTransactionDao.deleteById(id)
    }

    override suspend fun deleteAllForWallet(walletId: String) {
        evmTransactionDao.deleteByWalletId(walletId)
    }

    override suspend fun deleteForWalletAndTokenType(walletId: String, tokenType: TokenType) {
        evmTransactionDao.deleteByWalletIdAndTokenType(walletId, tokenType)
    }

    override suspend fun updateTransactionStatus(transactionId: String, status: TransactionStatus) {
        evmTransactionDao.updateStatus(transactionId, status.name)
    }

    override suspend fun getNativeTransaction(id: String): NativeETHTransaction? {
        val entity = evmTransactionDao.getById(id)
        return if (entity?.tokenType == TokenType.NATIVE) {
            entity.toDomain() as? NativeETHTransaction
        } else null
    }

    override suspend fun getTokenTransaction(id: String, tokenType: TokenType): TokenTransaction? {
        val entity = evmTransactionDao.getById(id)
        return if (entity?.tokenType == tokenType) {
            entity.toDomain() as? TokenTransaction
        } else null
    }
}