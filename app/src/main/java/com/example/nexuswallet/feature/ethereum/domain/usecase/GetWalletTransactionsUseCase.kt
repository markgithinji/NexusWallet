package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.domain.model.EVMTransaction
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetWalletTransactionsUseCase @Inject constructor(
    private val evmTransactionRepository: EVMTransactionRepository,
    private val logger: Logger
) {

    private val tag = "GetWalletTxUC"

    operator fun invoke(walletId: String): Flow<Result<List<EVMTransaction>>> {
        logger.d(tag, "Subscribing to transactions flow for wallet: $walletId")

        return evmTransactionRepository.getTransactions(walletId)
            .map { transactions ->
                logger.d(tag, "Emitting ${transactions.size} transactions for wallet: $walletId")
                Result.Success(transactions) as Result<List<EVMTransaction>>
            }
            .catch { e ->
                logger.e(tag, "Error loading transactions for wallet $walletId: ${e.message}")
                emit(Result.Error("Failed to load transactions: ${e.message}"))
            }
    }
}