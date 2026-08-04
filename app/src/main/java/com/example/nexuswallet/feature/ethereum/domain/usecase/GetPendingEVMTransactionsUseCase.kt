package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.domain.model.EVMTransaction
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetPendingEVMTransactionsUseCase @Inject constructor(
    private val evmTransactionRepository: EVMTransactionRepository,
    private val logger: Logger
) {

    suspend operator fun invoke(): Result<List<EVMTransaction>> {
        val transactions = evmTransactionRepository.getPendingTransactions()
        logger.d(TAG, "Found ${transactions.size} pending transactions")
        return Result.Success(transactions)
    }

    companion object {
        private const val TAG = "GetPendingTxUC"
    }
}