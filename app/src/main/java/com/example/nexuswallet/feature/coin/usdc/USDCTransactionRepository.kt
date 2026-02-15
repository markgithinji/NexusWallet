package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.feature.coin.usdc.domain.USDCSendTransaction
import com.example.nexuswallet.feature.coin.usdc.domain.toDomain
import com.example.nexuswallet.feature.coin.usdc.domain.toEntity
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

    suspend fun updateSignedTransaction(
        transactionId: String,
        signedHex: String,
        txHash: String,
        gasPriceWei: String,
        gasPriceGwei: String,
        feeWei: String,
        feeEth: String
    ) {
        val transaction = getTransaction(transactionId) ?: return
        val updated = transaction.copy(
            signedHex = signedHex,
            txHash = txHash,
            gasPriceWei = gasPriceWei,
            gasPriceGwei = gasPriceGwei,
            feeWei = feeWei,
            feeEth = feeEth,
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