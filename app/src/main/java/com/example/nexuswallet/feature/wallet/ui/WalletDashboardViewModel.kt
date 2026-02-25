package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import  com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import com.example.nexuswallet.feature.coin.Result
import kotlinx.coroutines.flow.catch

@HiltViewModel
class WalletDashboardViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow<Result<List<Wallet>>>(Result.Loading)
    val uiState: StateFlow<Result<List<Wallet>>> = _uiState.asStateFlow()

    // Balances map
    private val _balances = MutableStateFlow<Map<String, WalletBalance>>(emptyMap())
    val balances: StateFlow<Map<String, WalletBalance>> = _balances.asStateFlow()

    // Total portfolio value
    private val _totalPortfolioValue = MutableStateFlow(BigDecimal.ZERO)
    val totalPortfolioValue: StateFlow<BigDecimal> = _totalPortfolioValue.asStateFlow()

    // Loading state for specific operations (delete, refresh)
    private val _isOperationLoading = MutableStateFlow(false)
    val isOperationLoading: StateFlow<Boolean> = _isOperationLoading.asStateFlow()

    // Operation error state
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    init {
        observeWallets()
    }

    private fun observeWallets() {
        viewModelScope.launch {
            walletRepository.observeWallets()
                .catch { e ->
                    _uiState.value = Result.Error("Failed to load wallets: ${e.message}")
                }
                .collectLatest { walletsList ->
                    if (walletsList.isEmpty()) {
                        _uiState.value = Result.Success(emptyList())
                    } else {
                        _uiState.value = Result.Success(walletsList)
                        loadBalances(walletsList)
                    }
                }
        }
    }

    private fun loadBalances(wallets: List<Wallet>) {
        viewModelScope.launch {
            try {
                val balancesMap = mutableMapOf<String, WalletBalance>()
                wallets.forEach { wallet ->
                    val balance = walletRepository.getWalletBalance(wallet.id)
                    if (balance != null) {
                        balancesMap[wallet.id] = balance
                    }
                }
                _balances.value = balancesMap

                calculateTotalPortfolio(wallets)

            } catch (e: Exception) {
                Log.e("WalletVM", "Failed to load balances: ${e.message}")
            }
        }
    }

    private fun calculateTotalPortfolio(wallets: List<Wallet>) {
        viewModelScope.launch {
            val total = wallets.sumOf { wallet ->
                val balance = _balances.value[wallet.id]
                BigDecimal(balance?.totalUsdValue ?: 0.0)
            }
            _totalPortfolioValue.value = total
            Log.d("WalletVM", "Total portfolio calculated: $total")
        }
    }

    fun getWalletBalance(walletId: String): WalletBalance? {
        return _balances.value[walletId]
    }

    fun deleteWallet(walletId: String) {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _operationError.value = null
            try {
                walletRepository.deleteWallet(walletId)
            } catch (e: Exception) {
                _operationError.value = "Failed to delete wallet: ${e.message}"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _operationError.value = null
            try {
                val currentWallets = when (val state = _uiState.value) {
                    is Result.Success -> state.data
                    else -> emptyList()
                }
                if (currentWallets.isNotEmpty()) {
                    loadBalances(currentWallets)
                }
            } catch (e: Exception) {
                _operationError.value = "Failed to refresh: ${e.message}"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    fun clearOperationError() {
        _operationError.value = null
    }

    fun hasWallets(): Boolean {
        return when (val state = _uiState.value) {
            is Result.Success -> state.data.isNotEmpty()
            else -> false
        }
    }

    fun getWalletCount(): Int {
        return when (val state = _uiState.value) {
            is Result.Success -> state.data.size
            else -> 0
        }
    }

    fun getWallets(): List<Wallet> {
        return when (val state = _uiState.value) {
            is Result.Success -> state.data
            else -> emptyList()
        }
    }
}

val WalletBalance.totalUsdValue: Double
    get() = (bitcoin?.usdValue ?: 0.0) +
            (ethereum?.usdValue ?: 0.0) +
            (solana?.usdValue ?: 0.0) +
            (usdc?.usdValue ?: 0.0)