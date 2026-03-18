package com.example.nexuswallet.feature.bitcoin.data.repository

import com.example.nexuswallet.feature.bitcoin.data.local.BitcoinTransactionDao
import com.example.nexuswallet.feature.bitcoin.data.toDomain
import com.example.nexuswallet.feature.bitcoin.data.toEntity
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitcoinTransactionRepositoryImpl @Inject constructor(
    private val bitcoinTransactionDao: BitcoinTransactionDao
) : BitcoinTransactionRepository {

    override suspend fun saveTransaction(transaction: BitcoinTransaction) {
        val existing = getTransaction(transaction.id)
        if (existing != null) {
            updateTransaction(transaction)
        } else {
            bitcoinTransactionDao.insert(transaction.toEntity())
        }
    }

    override suspend fun updateTransaction(transaction: BitcoinTransaction) {
        bitcoinTransactionDao.update(transaction.toEntity())
    }

    override suspend fun getTransaction(id: String): BitcoinTransaction? {
        return bitcoinTransactionDao.getById(id)?.toDomain()
    }

    override fun getTransactions(
        walletId: String,
        network: BitcoinNetwork
    ): Flow<List<BitcoinTransaction>> {
        return bitcoinTransactionDao.getByWalletIdAndNetwork(walletId, network)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getTransactionsSync(
        walletId: String,
        network: BitcoinNetwork
    ): List<BitcoinTransaction> {
        return bitcoinTransactionDao.getByWalletIdAndNetworkSync(walletId, network)
            .map { it.toDomain() }
    }

    override suspend fun getPendingTransactions(): List<BitcoinTransaction> {
        return bitcoinTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    override suspend fun deleteTransaction(id: String) {
        bitcoinTransactionDao.deleteById(id)
    }

    override suspend fun deleteAllForWallet(walletId: String) {
        bitcoinTransactionDao.deleteByWalletId(walletId)
    }

    override suspend fun deleteForWalletAndNetwork(
        walletId: String,
        network: BitcoinNetwork
    ) {
        bitcoinTransactionDao.deleteByWalletIdAndNetwork(walletId, network)
    }
}