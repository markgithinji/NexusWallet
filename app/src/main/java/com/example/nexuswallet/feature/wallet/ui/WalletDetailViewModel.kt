package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.market.data.repository.MarketRepository
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.SyncWalletBalancesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletDetailUiState(
    val wallet: Wallet? = null,
    val balance: WalletBalance? = null,
    val transactions: List<Any> = emptyList(),
    val pricePercentages: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val syncWalletBalancesUseCase: SyncWalletBalancesUseCase,
    private val getAllTransactionsUseCase: GetAllTransactionsUseCase,
    private val marketRepository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletDetailUiState(isLoading = true))
    val uiState: StateFlow<WalletDetailUiState> = _uiState.asStateFlow()

    fun loadWallet(walletId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // 1. Load wallet from repository
                val loadedWallet = walletRepository.getWallet(walletId)

                if (loadedWallet == null) {
                    _uiState.update { it.copy(error = "Wallet not found", isLoading = false) }
                    return@launch
                }

                _uiState.update { it.copy(wallet = loadedWallet) }

                // 2. Load wallet balance from repository
                val loadedBalance = walletRepository.getWalletBalance(walletId)

                // 3. Load market percentages
                val percentages = marketRepository.getLatestPricePercentages()

                // 4. Load initial transactions
                val initialTransactions = getAllTransactionsUseCase(walletId)

                // Update UI once with all initial data
                _uiState.update {
                    it.copy(
                        wallet = loadedWallet,
                        balance = loadedBalance,
                        pricePercentages = percentages,
                        transactions = initialTransactions,
                        isLoading = false
                    )
                }

                // 5. Start observing transactions in a separate coroutine (doesn't block)
                observeTransactions(walletId)

                // 6. Sync balances in background (also separate)
                viewModelScope.launch {
                    syncWalletBalancesUseCase(loadedWallet)
                    val updatedBalance = walletRepository.getWalletBalance(walletId)
                    _uiState.update { it.copy(balance = updatedBalance) }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Failed to load wallet: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeTransactions(walletId: String) {
        viewModelScope.launch {
            getAllTransactionsUseCase.observeTransactions(walletId)
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    Log.e("WalletDetailVM", "Error observing transactions", e)
                }
                .collect { updatedTransactions ->
                    _uiState.update { it.copy(transactions = updatedTransactions) }
                }
        }
    }

    private suspend fun loadMarketPercentages() {
        val percentages = marketRepository.getLatestPricePercentages()
        _uiState.update { it.copy(pricePercentages = percentages) }
    }

    fun getWalletName(): String = _uiState.value.wallet?.name ?: "Wallet Details"

    fun getTotalUsdValue(): Double {
        val balance = _uiState.value.balance
        var total = 0.0
        balance?.bitcoin?.let { total += it.usdValue }
        balance?.ethereum?.let { total += it.usdValue }
        balance?.solana?.let { total += it.usdValue }
        balance?.usdc?.let { total += it.usdValue }
        return total
    }

    fun getFullAddress(): String {
        val wallet = _uiState.value.wallet ?: return ""
        return wallet.ethereum?.address
            ?: wallet.bitcoin?.address
            ?: wallet.solana?.address
            ?: wallet.usdc?.address
            ?: ""
    }

    fun refresh() {
        _uiState.value.wallet?.let { wallet ->
            viewModelScope.launch {
                loadWallet(wallet.id)
            }
        }
    }
}