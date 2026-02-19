package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.feature.coin.usdc.domain.USDCSendTransaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class USDCTransactionRepository @Inject constructor(
    private val usdcTransactionDao: USDCTransactionDao
) {

    suspend fun saveTransaction(transaction: USDCSendTransaction) {
        val entity = transaction.toEntity()
        usdcTransactionDao.insert(entity)
    }

    suspend fun updateTransaction(transaction: USDCSendTransaction) {
        val entity = transaction.toEntity()
        usdcTransactionDao.update(entity)
    }

    suspend fun getTransaction(id: String): USDCSendTransaction? {
        return usdcTransactionDao.getById(id)?.toDomain()
    }

    fun getTransactions(walletId: String): Flow<List<USDCSendTransaction>> {
        return usdcTransactionDao.getByWalletId(walletId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getPendingTransactions(): List<USDCSendTransaction> {
        return usdcTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    suspend fun deleteTransaction(id: String) {
        usdcTransactionDao.deleteById(id)
    }

    suspend fun deleteAllForWallet(walletId: String) {
        usdcTransactionDao.deleteByWalletId(walletId)
    }
}