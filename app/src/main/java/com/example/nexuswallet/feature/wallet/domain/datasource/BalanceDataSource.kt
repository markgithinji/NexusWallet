package com.example.nexuswallet.feature.wallet.domain.datasource

import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import kotlinx.coroutines.flow.Flow

interface BalanceDataSource {
    suspend fun saveWalletBalance(balance: WalletBalance)
    suspend fun saveBitcoinBalance(walletId: String, network: BitcoinNetwork, balance: BitcoinBalance)
    suspend fun saveSolanaBalance(walletId: String, network: SolanaNetwork, balance: SolanaBalance)
    suspend fun saveEVMBalances(walletId: String, balances: List<EVMBalance>)
    suspend fun loadWalletBalance(walletId: String): WalletBalance?
    fun observeWalletBalance(walletId: String): Flow<WalletBalance?>
    fun observeAllBalances(): Flow<Map<String, WalletBalance>>
}