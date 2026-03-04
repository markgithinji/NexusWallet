package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.toDomain
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton@Singleton
class EVMTransactionRepositoryImpl @Inject constructor(
    private val evmTransactionDao: EVMTransactionDao
) : EVMTransactionRepository {

    override suspend fun saveTransaction(transaction: EVMTransaction) {
        val entity = transaction.toEntity()
        evmTransactionDao.insert(entity)
    }

    override suspend fun updateTransaction(transaction: EVMTransaction) {
        val entity = transaction.toEntity()
        evmTransactionDao.update(entity)
    }

    override suspend fun getTransaction(id: String): EVMTransaction? {
        return evmTransactionDao.getById(id)?.toDomain()
    }

    override fun getTransactions(walletId: String): Flow<List<EVMTransaction>> {
        return evmTransactionDao.getByWalletId(walletId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTransactionsByToken(
        walletId: String,
        tokenContract: String?
    ): Flow<List<EVMTransaction>> {
        return evmTransactionDao.getByWalletIdAndToken(walletId, tokenContract)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTransactionsByTokenExternalId(
        walletId: String,
        tokenExternalId: String
    ): Flow<List<EVMTransaction>> {
        return evmTransactionDao.getByWalletIdAndTokenExternalId(walletId, tokenExternalId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getNativeTransactions(walletId: String): Flow<List<EVMTransaction>> {
        return evmTransactionDao.getNativeTransactions(walletId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun observePendingTransactions(): Flow<List<EVMTransaction>> {
        return evmTransactionDao.observePendingTransactions()
            .map { entities -> entities.map { it.toDomain() } }
    }


    override suspend fun getTransactionsSync(walletId: String): List<EVMTransaction> {
        return evmTransactionDao.getByWalletIdSync(walletId)
            .map { it.toDomain() }
    }

    override suspend fun getNativeTransactionsSync(walletId: String): List<EVMTransaction> {
        return evmTransactionDao.getNativeTransactionsSync(walletId)
            .map { it.toDomain() }
    }

    override suspend fun getTransactionsForToken(
        walletId: String,
        tokenExternalId: String
    ): List<EVMTransaction> {
        return evmTransactionDao.getTransactionsForToken(walletId, tokenExternalId)
            .map { it.toDomain() }
    }

    override suspend fun getPendingTransactions(): List<EVMTransaction> {
        return evmTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    override suspend fun deleteTransaction(id: String) {
        evmTransactionDao.deleteById(id)
    }

    override suspend fun deleteAllForWallet(walletId: String) {
        evmTransactionDao.deleteByWalletId(walletId)
    }

    override suspend fun deleteForWalletAndToken(walletId: String, tokenExternalId: String) {
        evmTransactionDao.deleteByWalletIdAndTokenExternalId(walletId, tokenExternalId)
    }

    override suspend fun updateTransactionStatus(transactionId: String, status: TransactionStatus) {
        evmTransactionDao.updateStatus(transactionId, status.name)
    }
}