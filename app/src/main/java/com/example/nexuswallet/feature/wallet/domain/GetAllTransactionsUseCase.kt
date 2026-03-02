package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.ethereum.EVMTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import kotlinx.coroutines.flow.Flow

interface GetAllTransactionsUseCase {
    // Get all transactions for a wallet (all networks, all tokens)
    suspend operator fun invoke(walletId: String): List<Any>
    fun observeTransactions(walletId: String): Flow<List<Any>>

    // Bitcoin - filtered by network
    suspend fun getBitcoinTransactions(walletId: String, network: String): List<BitcoinTransaction>
    fun observeBitcoinTransactions(walletId: String, network: String): Flow<List<BitcoinTransaction>>

    // EVM - filtered by token
    suspend fun getEVMTokenTransactions(walletId: String, tokenExternalId: String): List<EVMTransaction>
    fun observeEVMTokenTransactions(walletId: String, tokenExternalId: String): Flow<List<EVMTransaction>>

    // Solana - filtered by network
    suspend fun getSolanaTransactions(walletId: String, network: String): List<SolanaTransaction>
    fun observeSolanaTransactions(walletId: String, network: String): Flow<List<SolanaTransaction>>
}