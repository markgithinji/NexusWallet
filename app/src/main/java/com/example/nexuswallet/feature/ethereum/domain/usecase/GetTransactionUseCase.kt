package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.coin.ethereum.domain.model.EVMTransaction
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetTransactionUseCase @Inject constructor(
    private val evmTransactionRepository: EVMTransactionRepository,
    private val logger: Logger
) {

    private val tag = "GetTransactionUC"

    suspend operator fun invoke(transactionId: String): Result<EVMTransaction> {
        val transaction = evmTransactionRepository.getTransaction(transactionId)
        return if (transaction != null) {
            logger.d(tag, "Transaction found: $transactionId")
            Result.Success(transaction)
        } else {
            logger.w(tag, "Transaction not found: $transactionId")
            Result.Error("Transaction not found")
        }
    }
}