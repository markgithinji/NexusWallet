package com.example.nexuswallet.feature.wallet.data.local

import com.example.nexuswallet.feature.wallet.data.model.SendTransaction
import com.example.nexuswallet.feature.wallet.data.model.SendTransactionDao
import com.example.nexuswallet.feature.wallet.data.model.SendTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

class TransactionLocalDataSource(
    private val transactionDao: TransactionDao,
    private val sendTransactionDao: SendTransactionDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveSendTransaction(transaction: SendTransaction) {
        val jsonStr = json.encodeToString(transaction)
        val entity = SendTransactionEntity(
            id = transaction.id,
            walletId = transaction.walletId,
            transactionJson = jsonStr,
            timestamp = transaction.timestamp,
            status = transaction.status.name
        )
        sendTransactionDao.insert(entity)
    }

    suspend fun getSendTransaction(transactionId: String): SendTransaction? {
        val entity = sendTransactionDao.get(transactionId) ?: return null
        return try {
            json.decodeFromString<SendTransaction>(entity.transactionJson)
        } catch (e: Exception) {
            null
        }
    }

    fun getSendTransactions(walletId: String): Flow<List<SendTransaction>> {
        return sendTransactionDao.getByWallet(walletId).map { entities ->
            entities.mapNotNull { entity ->
                try {
                    json.decodeFromString<SendTransaction>(entity.transactionJson)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun deleteSendTransaction(transactionId: String) {
        sendTransactionDao.delete(transactionId)
    }

    suspend fun getPendingTransactions(): List<SendTransaction> {
        val entities = sendTransactionDao.getByStatus("PENDING")
        return entities.mapNotNull { entity ->
            try {
                json.decodeFromString<SendTransaction>(entity.transactionJson)
            } catch (e: Exception) {
                null
            }
        }
    }
}