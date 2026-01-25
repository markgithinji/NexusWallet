package com.example.nexuswallet.feature.wallet.ui

import com.example.nexuswallet.data.model.CryptoWallet
import com.example.nexuswallet.data.model.WalletBalance


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.NexusWalletApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal

class WalletDashboardViewModel() : ViewModel() {
    private val walletDataManager = NexusWalletApplication.Companion.instance.walletDataManager
    // Wallets list
    private val _wallets = MutableStateFlow<List<CryptoWallet>>(emptyList())
    val wallets: StateFlow<List<CryptoWallet>> = _wallets

    // Balances map
    private val _balances = MutableStateFlow<Map<String, WalletBalance>>(emptyMap())
    val balances: StateFlow<Map<String, WalletBalance>> = _balances

    // Total portfolio value
    private val _totalPortfolioValue = MutableStateFlow(BigDecimal.ZERO)
    val totalPortfolioValue: StateFlow<BigDecimal> = _totalPortfolioValue

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Selected wallet
    private val _selectedWallet = MutableStateFlow<CryptoWallet?>(null)
    val selectedWallet: StateFlow<CryptoWallet?> = _selectedWallet

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Observe wallets from data manager
        viewModelScope.launch {
            walletDataManager.getWalletsFlow().collectLatest { walletsList ->
                _wallets.value = walletsList
                calculateTotalPortfolio()
            }
        }

        // Load initial data
        loadWallets()
    }

    fun loadWallets() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val walletsList = walletDataManager.getAllWallets()
                _wallets.value = walletsList

                val balancesMap = mutableMapOf<String, WalletBalance>()
                walletsList.forEach { wallet ->
                    val balance = walletDataManager.getWalletBalance(wallet.id)
                    if (balance != null) {
                        balancesMap[wallet.id] = balance
                    }
                }
                _balances.value = balancesMap

                calculateTotalPortfolio()
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to load wallets: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun calculateTotalPortfolio() {
        var total = BigDecimal.ZERO
        _balances.value.values.forEach { balance ->
            total = total.add(BigDecimal(balance.usdValue.toString()))
        }
        _totalPortfolioValue.value = total
    }

    fun getWalletBalance(walletId: String): WalletBalance? {
        return _balances.value[walletId]
    }

    fun selectWallet(wallet: CryptoWallet) {
        _selectedWallet.value = wallet
    }

    fun deleteWallet(walletId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                walletDataManager.deleteWallet(walletId)
                loadWallets()
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to delete wallet: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadWallets()
    }

    fun clearError() {
        _error.value = null
    }

    fun hasWallets(): Boolean {
        return _wallets.value.isNotEmpty()
    }

    fun getWalletCount(): Int {
        return _wallets.value.size
    }
}