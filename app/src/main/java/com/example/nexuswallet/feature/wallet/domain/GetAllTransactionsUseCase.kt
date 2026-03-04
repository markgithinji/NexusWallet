package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.ethereum.EVMTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import kotlinx.coroutines.flow.Flow

interface GetAllTransactionsUseCase {
    suspend operator fun invoke(walletId: String): List<Any>
    fun observeTransactions(walletId: String): Flow<List<Any>>
}