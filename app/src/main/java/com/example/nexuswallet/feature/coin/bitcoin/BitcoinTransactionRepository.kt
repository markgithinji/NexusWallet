package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitcoinTransactionRepository @Inject constructor(
    private val bitcoinTransactionDao: BitcoinTransactionDao
) {

    suspend fun deleteAllForWallet(walletId: String) {
        bitcoinTransactionDao.deleteByWalletId(walletId)
    }

    suspend fun saveTransaction(transaction: BitcoinTransaction) {
        // Check if transaction already exists
        val existing = getTransaction(transaction.id)
        if (existing != null) {
            updateTransaction(transaction)
        } else {
            val entity = transaction.toEntity()
            bitcoinTransactionDao.insert(entity)
        }
    }

    suspend fun updateTransaction(transaction: BitcoinTransaction) {
        val entity = transaction.toEntity()
        bitcoinTransactionDao.update(entity)
    }

    suspend fun getTransaction(id: String): BitcoinTransaction? {
        return bitcoinTransactionDao.getById(id)?.toDomain()
    }

    fun getTransactions(walletId: String): Flow<List<BitcoinTransaction>> {
        return bitcoinTransactionDao.getByWalletId(walletId)
            .map { entities ->
                entities.map { it.toDomain() }
                    .distinctBy { it.id }
                    .sortedByDescending { it.timestamp }
            }
    }

    suspend fun getPendingTransactions(): List<BitcoinTransaction> {
        return bitcoinTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    }
}