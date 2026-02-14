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

    suspend fun saveTransaction(transaction: BitcoinTransaction) {
        val entity = transaction.toEntity()
        bitcoinTransactionDao.insert(entity)
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
            .map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getPendingTransactions(): List<BitcoinTransaction> {
        return bitcoinTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    suspend fun deleteTransaction(id: String) {
        bitcoinTransactionDao.deleteById(id)
    }

    suspend fun updateSignedTransaction(
        transactionId: String,
        signedHex: String,
        txHash: String
    ) {
        val transaction = getTransaction(transactionId) ?: return
        val updated = transaction.copy(
            signedHex = signedHex,
            txHash = txHash,
            status = TransactionStatus.PENDING
        )
        updateTransaction(updated)
    }

    suspend fun updateTransactionStatus(
        transactionId: String,
        success: Boolean,
        txHash: String? = null
    ) {
        val transaction = getTransaction(transactionId) ?: return
        val updated = transaction.copy(
            status = if (success) TransactionStatus.SUCCESS else TransactionStatus.FAILED,
            txHash = txHash ?: transaction.txHash
        )
        updateTransaction(updated)
    }
}