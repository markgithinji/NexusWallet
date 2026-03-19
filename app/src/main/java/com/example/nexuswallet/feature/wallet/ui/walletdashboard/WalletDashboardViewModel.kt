package com.example.nexuswallet.feature.wallet.ui.walletdashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

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
                    _uiState.value = Result.Success(walletsList)
                    if (walletsList.isNotEmpty()) {
                        loadBalances(walletsList)
                    }
                }
        }
    }

    private fun loadBalances(wallets: List<Wallet>) {
        viewModelScope.launch {
            val balancesMap = wallets.mapNotNull { wallet ->
                runCatching { // TODO: Move catching to repo safeApicall
                    walletRepository.getWalletBalance(wallet.id)
                }.getOrNull()?.let { balance ->
                    wallet.id to balance
                }
            }.toMap()

            _balances.value = balancesMap
            calculateTotalPortfolio(wallets)
        }
    }

    private fun calculateTotalPortfolio(wallets: List<Wallet>) {
        val total = wallets.sumOf { wallet ->
            _balances.value[wallet.id]?.let { balance ->
                balance.bitcoinBalances.values.sumOf { BigDecimal(it.usdValue) } +
                        balance.solanaBalances.values.sumOf { BigDecimal(it.usdValue) } +
                        balance.evmBalances.sumOf { BigDecimal(it.usdValue) }
            } ?: BigDecimal.ZERO
        }

        _totalPortfolioValue.value = total
    }

    fun deleteWallet(walletId: String) {
        viewModelScope.launch {
            _isOperationLoading.update { true }
            _operationError.update { null }

            runCatching {
                walletRepository.deleteWallet(walletId)
            }.onFailure { e ->
                _operationError.update { "Failed to delete wallet: ${e.message}" }
            }

            _isOperationLoading.update { false }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isOperationLoading.update { true }
            _operationError.update { null }

            runCatching {
                val currentWallets = (_uiState.value as? Result.Success)?.data ?: emptyList()
                if (currentWallets.isNotEmpty()) {
                    loadBalances(currentWallets)
                }
            }.onFailure { e ->
                _operationError.update { "Failed to refresh: ${e.message}" }
            }

            _isOperationLoading.update { false }
        }
    }

    fun clearOperationError() {
        _operationError.update { null }
    }
}