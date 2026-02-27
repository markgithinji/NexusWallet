package com.example.nexuswallet.feature.wallet.domain

import kotlinx.coroutines.flow.Flow

interface GetAllTransactionsUseCase{
    suspend operator fun invoke(walletId: String): List<Any>
    fun observeTransactions(walletId: String): Flow<List<Any>>
}