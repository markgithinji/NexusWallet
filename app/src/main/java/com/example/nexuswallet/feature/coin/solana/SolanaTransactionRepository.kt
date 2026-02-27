package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolanaTransactionRepositoryImpl @Inject constructor(
    private val solanaTransactionDao: SolanaTransactionDao
) : SolanaTransactionRepository {

    override suspend fun saveTransaction(transaction: SolanaTransaction) {
        val entity = transaction.toEntity()
        solanaTransactionDao.insert(entity)
    }

    override suspend fun updateTransaction(transaction: SolanaTransaction) {
        val entity = transaction.toEntity()
        solanaTransactionDao.update(entity)
    }

    override suspend fun getTransaction(id: String): SolanaTransaction? {
        return solanaTransactionDao.getById(id)?.toDomain()
    }

    override fun getTransactions(walletId: String): Flow<List<SolanaTransaction>> {
        return solanaTransactionDao.getByWalletId(walletId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getPendingTransactions(): List<SolanaTransaction> {
        return solanaTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    override suspend fun deleteTransaction(id: String) {
        solanaTransactionDao.deleteById(id)
    }

    override suspend fun deleteAllForWallet(walletId: String) {
        solanaTransactionDao.deleteByWalletId(walletId)
    }

    override suspend fun updateSignedTransaction(
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

    override suspend fun updateTransactionStatus(
        transactionId: String,
        success: Boolean,
        signature: String?
    ) {
        val transaction = getTransaction(transactionId) ?: return
        val updated = transaction.copy(
            status = if (success) TransactionStatus.SUCCESS else TransactionStatus.FAILED,
            signature = signature ?: transaction.signature
        )
        updateTransaction(updated)
    }
}