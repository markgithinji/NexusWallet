package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EthereumTransactionRepository @Inject constructor(
    private val ethereumTransactionDao: EthereumTransactionDao
) {

    suspend fun saveTransaction(transaction: EthereumTransaction) {
        val entity = transaction.toEntity()
        ethereumTransactionDao.insert(entity)
    }

    suspend fun updateTransaction(transaction: EthereumTransaction) {
        val entity = transaction.toEntity()
        ethereumTransactionDao.update(entity)
    }

    suspend fun getTransaction(id: String): EthereumTransaction? {
        return ethereumTransactionDao.getById(id)?.toDomain()
    }

    fun getTransactions(walletId: String): Flow<List<EthereumTransaction>> {
        return ethereumTransactionDao.getByWalletId(walletId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getPendingTransactions(): List<EthereumTransaction> {
        return ethereumTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    suspend fun deleteTransaction(id: String) {
        ethereumTransactionDao.deleteById(id)
    }

    suspend fun updateSignedTransaction(
        transactionId: String,
        signedHex: String,
        txHash: String,
        nonce: Int,
        gasPrice: String
    ) {
        val transaction = getTransaction(transactionId) ?: return
        val updated = transaction.copy(
            signedHex = signedHex,
            txHash = txHash,
            nonce = nonce,
            gasPriceGwei = BigDecimal(gasPrice),
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