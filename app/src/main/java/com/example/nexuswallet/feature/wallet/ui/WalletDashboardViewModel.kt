package com.example.nexuswallet.feature.wallet.ui

import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.WalletBalance


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class WalletDashboardViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : ViewModel() {

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

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Observe wallets from repository
        viewModelScope.launch {
            walletRepository.walletsFlow.collectLatest { walletsList ->
                _wallets.value = walletsList
                loadBalances(walletsList)
            }
        }
    }

    private fun loadBalances(wallets: List<CryptoWallet>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val balancesMap = mutableMapOf<String, WalletBalance>()
                wallets.forEach { wallet ->
                    val balance = walletRepository.getWalletBalance(wallet.id)
                    if (balance != null) {
                        balancesMap[wallet.id] = balance
                    }
                }
                _balances.value = balancesMap

                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to load balances: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getWalletBalance(walletId: String): WalletBalance? {
        return _balances.value[walletId]
    }

    fun deleteWallet(walletId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                walletRepository.deleteWallet(walletId)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to delete wallet: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Force refresh by reloading balances
                loadBalances(_wallets.value)
            } catch (e: Exception) {
                _error.value = "Failed to refresh: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
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