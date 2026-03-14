package com.example.nexuswallet.feature.solana.data.repository

import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.data.local.SolanaTransactionDao
import com.example.nexuswallet.feature.solana.data.toDomain
import com.example.nexuswallet.feature.solana.data.toEntity
import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolanaTransactionRepositoryImpl @Inject constructor(
    private val solanaTransactionDao: SolanaTransactionDao,
    private val logger: Logger
) : SolanaTransactionRepository {

    override suspend fun saveTransaction(transaction: SolanaTransaction) {
        logger.d("SolanaTxRepo", "Saving transaction: ${transaction.id.take(8)}...")
        logger.d("SolanaTxRepo", "  walletId: ${transaction.walletId}")
        logger.d("SolanaTxRepo", "  network: ${transaction.network.displayName}")
        logger.d("SolanaTxRepo", "  tokenSymbol: ${transaction.tokenSymbol}")

        val entity = transaction.toEntity()
        solanaTransactionDao.insert(entity)

        val saved = solanaTransactionDao.getById(transaction.id)
        logger.d("SolanaTxRepo", "Verification - transaction exists after save: ${saved != null}")
    }

    override suspend fun getTransactionsSync(
        walletId: String,
        network: SolanaNetwork
    ): List<SolanaTransaction> {
        logger.d(
            "SolanaTxRepo",
            "getTransactionsSync called for wallet: $walletId, network: ${network.displayName}"
        )
        val entities = solanaTransactionDao.getByWalletIdAndNetworkSync(walletId, network)
        logger.d(
            "SolanaTxRepo",
            "Found ${entities.size} transactions for wallet: $walletId, network: ${network.displayName}"
        )
        return entities.map { it.toDomain() }
    }

    override suspend fun updateTransaction(transaction: SolanaTransaction) {
        val entity = transaction.toEntity()
        solanaTransactionDao.update(entity)
    }

    override suspend fun getTransaction(id: String): SolanaTransaction? {
        return solanaTransactionDao.getById(id)?.toDomain()
    }

    override fun getTransactions(
        walletId: String,
        network: SolanaNetwork
    ): Flow<List<SolanaTransaction>> {
        return solanaTransactionDao.getByWalletIdAndNetwork(walletId, network)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTransactionsByToken(
        walletId: String,
        tokenMint: String?,
        network: SolanaNetwork
    ): Flow<List<SolanaTransaction>> {
        return solanaTransactionDao.getByWalletIdTokenAndNetwork(walletId, tokenMint, network)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getNativeTransactions(
        walletId: String,
        network: SolanaNetwork
    ): Flow<List<SolanaTransaction>> {
        return solanaTransactionDao.getNativeTransactions(walletId, network)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun observePendingTransactions(): Flow<List<SolanaTransaction>> {
        return solanaTransactionDao.observePendingTransactions()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getNativeTransactionsSync(
        walletId: String,
        network: SolanaNetwork
    ): List<SolanaTransaction> {
        return solanaTransactionDao.getNativeTransactionsSync(walletId, network)
            .map { it.toDomain() }
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

    override suspend fun deleteForWalletAndNetwork(
        walletId: String,
        network: SolanaNetwork
    ) {
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