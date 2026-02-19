package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class SolanaTransactionRepository @Inject constructor(
    private val solanaTransactionDao: SolanaTransactionDao
) {

    suspend fun saveTransaction(transaction: SolanaTransaction) {
        val entity = transaction.toEntity()
        solanaTransactionDao.insert(entity)
    }

    suspend fun updateTransaction(transaction: SolanaTransaction) {
        val entity = transaction.toEntity()
        solanaTransactionDao.update(entity)
    }

    suspend fun getTransaction(id: String): SolanaTransaction? {
        return solanaTransactionDao.getById(id)?.toDomain()
    }

    fun getTransactions(walletId: String): Flow<List<SolanaTransaction>> {
        return solanaTransactionDao.getByWalletId(walletId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getPendingTransactions(): List<SolanaTransaction> {
        return solanaTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    suspend fun deleteTransaction(id: String) {
        solanaTransactionDao.deleteById(id)
    }

    suspend fun deleteAllForWallet(walletId: String) {
        solanaTransactionDao.deleteByWalletId(walletId)
    }

    suspend fun updateSignedTransaction(
        transactionId: String,
        signedData: String,
        signature: String
    ) {
        val transaction = getTransaction(transactionId) ?: return
        val updated = transaction.copy(
            signedData = signedData,
            signature = signature,
            status = TransactionStatus.PENDING
        )
        updateTransaction(updated)
    }

    suspend fun updateTransactionStatus(
        transactionId: String,
        success: Boolean,
        signature: String? = null
    ) {
        val transaction = getTransaction(transactionId) ?: return
        val updated = transaction.copy(
            status = if (success) TransactionStatus.SUCCESS else TransactionStatus.FAILED,
            signature = signature ?: transaction.signature
        )
        updateTransaction(updated)
    }
}