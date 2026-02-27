package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitcoinTransactionRepositoryImpl @Inject constructor(
    private val bitcoinTransactionDao: BitcoinTransactionDao
) : BitcoinTransactionRepository {

    override suspend fun deleteAllForWallet(walletId: String) {
        bitcoinTransactionDao.deleteByWalletId(walletId)
    }

    override suspend fun saveTransaction(transaction: BitcoinTransaction) {
        // Check if transaction already exists
        val existing = getTransaction(transaction.id)
        if (existing != null) {
            updateTransaction(transaction)
        } else {
            val entity = transaction.toEntity()
            bitcoinTransactionDao.insert(entity)
        }
    }

    override suspend fun updateTransaction(transaction: BitcoinTransaction) {
        val entity = transaction.toEntity()
        bitcoinTransactionDao.update(entity)
    }

    override suspend fun getTransaction(id: String): BitcoinTransaction? {
        return bitcoinTransactionDao.getById(id)?.toDomain()
    }

    override fun getTransactions(walletId: String): Flow<List<BitcoinTransaction>> {
        return bitcoinTransactionDao.getByWalletId(walletId)
            .map { entities ->
                entities.map { it.toDomain() }
                    .distinctBy { it.id }
                    .sortedByDescending { it.timestamp }
            }
    }

    override suspend fun getPendingTransactions(): List<BitcoinTransaction> {
        return bitcoinTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    }
}