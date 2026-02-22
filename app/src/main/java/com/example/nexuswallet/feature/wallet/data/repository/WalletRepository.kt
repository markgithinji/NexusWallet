package com.example.nexuswallet.feature.wallet.data.repository

import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor(
    private val localDataSource: WalletLocalDataSource
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _walletsFlow = MutableStateFlow<List<Wallet>>(emptyList())
    val walletsFlow: StateFlow<List<Wallet>> = _walletsFlow.asStateFlow()

    init {
        scope.launch {
            localDataSource.loadAllWallets().collect { wallets ->
                _walletsFlow.value = wallets
            }
        }
    }

    // === WALLET CRUD ===
    suspend fun getWallet(walletId: String): Wallet? {
        return localDataSource.loadWallet(walletId)
    }

    suspend fun deleteWallet(walletId: String) {
        localDataSource.deleteWallet(walletId)
        removeWalletFromFlow(walletId)
    }

    // === BALANCE OPERATIONS ===
    suspend fun getWalletBalance(walletId: String): WalletBalance? {
        return localDataSource.loadWalletBalance(walletId)
    }

    private fun removeWalletFromFlow(walletId: String) {
        val currentWallets = _walletsFlow.value.toMutableList()
        currentWallets.removeAll { it.id == walletId }
        _walletsFlow.value = currentWallets
    }
}