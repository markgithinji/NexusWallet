package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.ethereum.data.EthereumTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EthereumTransactionRepositoryImpl @Inject constructor(
    private val ethereumTransactionDao: EthereumTransactionDao
) : EthereumTransactionRepository {

    override suspend fun saveTransaction(transaction: EthereumTransaction) {
        val entity = transaction.toEntity()
        ethereumTransactionDao.insert(entity)
    }

    override suspend fun updateTransaction(transaction: EthereumTransaction) {
        val entity = transaction.toEntity()
        ethereumTransactionDao.update(entity)
    }

    override suspend fun getTransaction(id: String): EthereumTransaction? {
        return ethereumTransactionDao.getById(id)?.toDomain()
    }

    override fun getTransactions(walletId: String): Flow<List<EthereumTransaction>> {
        return ethereumTransactionDao.getByWalletId(walletId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getPendingTransactions(): List<EthereumTransaction> {
        return ethereumTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    override suspend fun deleteTransaction(id: String) {
        ethereumTransactionDao.deleteById(id)
    }

    override suspend fun deleteAllForWallet(walletId: String) {
        ethereumTransactionDao.deleteByWalletId(walletId)
    }
}