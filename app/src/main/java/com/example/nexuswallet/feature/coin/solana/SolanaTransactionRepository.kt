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


    override fun getTransactions(walletId: String, network: String): Flow<List<SolanaTransaction>> {
        return solanaTransactionDao.getByWalletIdAndNetwork(walletId, network)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTransactionsByToken(
        walletId: String,
        tokenMint: String?,
        network: String
    ): Flow<List<SolanaTransaction>> {
        return solanaTransactionDao.getByWalletIdTokenAndNetwork(walletId, tokenMint, network)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getNativeTransactions(walletId: String, network: String): Flow<List<SolanaTransaction>> {
        return solanaTransactionDao.getNativeTransactions(walletId, network)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getPendingTransactions(): List<SolanaTransaction> {
        return solanaTransactionDao.getPendingTransactions()
            .map { it.toDomain() }
    }

    override fun observePendingTransactions(): Flow<List<SolanaTransaction>> {
        return solanaTransactionDao.observePendingTransactions()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun deleteTransaction(id: String) {
        solanaTransactionDao.deleteById(id)
    }

    override suspend fun deleteAllForWallet(walletId: String) {
        solanaTransactionDao.deleteByWalletId(walletId)
    }

    override suspend fun deleteForWalletAndNetwork(walletId: String, network: String) {
        solanaTransactionDao.deleteByWalletIdAndNetwork(walletId, network)
    }

    override suspend fun updateTransactionStatus(
        transactionId: String,
        status: TransactionStatus
    ) {
        solanaTransactionDao.updateStatus(transactionId, status.name)
    }

    override suspend fun updateTransactionSignature(
        transactionId: String,
        signature: String
    ) {
        val transaction = getTransaction(transactionId) ?: return
        val updated = transaction.copy(
            signature = signature,
            status = TransactionStatus.PENDING
        )
        updateTransaction(updated)
    }

    override suspend fun confirmTransaction(
        transactionId: String,
        signature: String,
        slot: Long,
        blockTime: Long
    ) {
        val transaction = getTransaction(transactionId) ?: return
        val updated = transaction.copy(
            signature = signature,
            status = TransactionStatus.SUCCESS,
            slot = slot,
            blockTime = blockTime
        )
        updateTransaction(updated)
    }
}