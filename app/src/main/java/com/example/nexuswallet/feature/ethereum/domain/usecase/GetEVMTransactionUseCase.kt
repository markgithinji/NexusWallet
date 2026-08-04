package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.domain.model.EVMTransaction
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetEVMTransactionUseCase @Inject constructor(
    private val evmTransactionRepository: EVMTransactionRepository,
    private val logger: Logger
) {

    suspend operator fun invoke(transactionId: String): Result<EVMTransaction> {
        val transaction = evmTransactionRepository.getTransaction(transactionId)
        return if (transaction != null) {
            logger.d(TAG, "Transaction found: $transactionId")
            Result.Success(transaction)
        } else {
            logger.w(TAG, "Transaction not found: $transactionId")
            Result.Error("Transaction not found")
        }
    }

    companion object {
        private const val TAG = "GetTransactionUC"
    }
}