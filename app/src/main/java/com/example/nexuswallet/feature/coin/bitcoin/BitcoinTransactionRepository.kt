package com.example.nexuswallet.feature.coin.bitcoin

import android.util.Log
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
        Log.d("BitcoinTxRepo", "=== Saving Transaction ===")
        Log.d("BitcoinTxRepo", "  id: ${transaction.id}")
        Log.d("BitcoinTxRepo", "  txHash: ${transaction.txHash}")
        Log.d("BitcoinTxRepo", "  amount: ${transaction.amountBtc} BTC")
        Log.d("BitcoinTxRepo", "  satoshis: ${transaction.amountSatoshis}")
        Log.d("BitcoinTxRepo", "  isIncoming: ${transaction.isIncoming}")
        Log.d("BitcoinTxRepo", "  from: ${transaction.fromAddress}")
        Log.d("BitcoinTxRepo", "  to: ${transaction.toAddress}")
        Log.d("BitcoinTxRepo", "  status: ${transaction.status}")
        Log.d("BitcoinTxRepo", "  timestamp: ${transaction.timestamp}")

        // Check if transaction already exists
        val existing = getTransaction(transaction.id)
        if (existing != null) {
            Log.d("BitcoinTxRepo", "Transaction already exists, updating...")
            updateTransaction(transaction)
        } else {
            Log.d("BitcoinTxRepo", "New transaction, inserting...")
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
                    .distinctBy { it.id } // Extra safety: remove any duplicates
                    .sortedByDescending { it.timestamp }
            }
    }

    suspend fun getPendingTransactions(): List<BitcoinTransaction> {
        return bitcoinTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
            .distinctBy { it.id } // Remove duplicates
            .sortedByDescending { it.timestamp }
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