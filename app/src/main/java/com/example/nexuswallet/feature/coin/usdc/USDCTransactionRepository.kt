package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransaction
import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransactionRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class USDCTransactionRepositoryImpl @Inject constructor(
    private val usdcTransactionDao: USDCTransactionDao
) : USDCTransactionRepository {

    override suspend fun saveTransaction(transaction: USDCTransaction) {
        val entity = transaction.toEntity()
        usdcTransactionDao.insert(entity)
    }

    override suspend fun updateTransaction(transaction: USDCTransaction) {
        val entity = transaction.toEntity()
        usdcTransactionDao.update(entity)
    }

    override suspend fun getTransaction(id: String): USDCTransaction? {
        return usdcTransactionDao.getById(id)?.toDomain()
    }

    override fun getTransactions(walletId: String): Flow<List<USDCTransaction>> {
        return usdcTransactionDao.getByWalletId(walletId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getPendingTransactions(): List<USDCTransaction> {
        return usdcTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    override suspend fun deleteTransaction(id: String) {
        usdcTransactionDao.deleteById(id)
    }

    override suspend fun deleteAllForWallet(walletId: String) {
        usdcTransactionDao.deleteByWalletId(walletId)
    }
}