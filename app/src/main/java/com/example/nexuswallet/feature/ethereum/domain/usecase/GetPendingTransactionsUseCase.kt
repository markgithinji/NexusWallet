package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.ethereum.domain.model.EVMTransaction
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetPendingTransactionsUseCase @Inject constructor(
    private val evmTransactionRepository: EVMTransactionRepository,
    private val logger: Logger
) {

    private val tag = "GetPendingTxUC"

    suspend operator fun invoke(): Result<List<EVMTransaction>> {
        val transactions = evmTransactionRepository.getPendingTransactions()
        logger.d(tag, "Found ${transactions.size} pending transactions")
        return Result.Success(transactions)
    }
}