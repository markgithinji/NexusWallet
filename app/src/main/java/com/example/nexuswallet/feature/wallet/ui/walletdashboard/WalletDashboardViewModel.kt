package com.example.nexuswallet.feature.wallet.ui.walletdashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.wallet.domain.model.ChainSyncError
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncBitcoinBalanceUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncEVMBalancesUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncSolanaBalanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class WalletDashboardViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val syncBitcoinBalanceUseCase: SyncBitcoinBalanceUseCase,
    private val syncSolanaBalanceUseCase: SyncSolanaBalanceUseCase,
    private val syncEVMBalancesUseCase: SyncEVMBalancesUseCase,
    private val getSimplePricesUseCase: GetSimplePricesUseCase,
    private val securityPreferencesRepository: SecurityPreferencesRepository
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow<Result<List<Wallet>>>(Result.Loading)
    val uiState: StateFlow<Result<List<Wallet>>> = _uiState.asStateFlow()

    // Balances map - REACTIVE
    val balances: StateFlow<Map<String, WalletBalance>> = walletRepository.observeAllBalances()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // Total portfolio value - REACTIVE
    val totalPortfolioValue: StateFlow<BigDecimal> = balances.map { balancesMap ->
        balancesMap.values.sumOf { balance ->
            balance.bitcoinBalances.values.sumOf { BigDecimal(it.usdValue) } +
                    balance.solanaBalances.values.sumOf { BigDecimal(it.usdValue) } +
                    balance.evmBalances.sumOf { BigDecimal(it.usdValue) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BigDecimal.ZERO
    )

    // Loading state for background sync/refresh
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Loading state for specific critical operations (delete)
    private val _isOperationLoading = MutableStateFlow(false)
    val isOperationLoading: StateFlow<Boolean> = _isOperationLoading.asStateFlow()

    // Operation error state
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _isPrivacyModeEnabled = MutableStateFlow(false)
    val isPrivacyModeEnabled: StateFlow<Boolean> = _isPrivacyModeEnabled.asStateFlow()

    private val _selectedCurrency = MutableStateFlow("USD")
    val selectedCurrency: StateFlow<String> = _selectedCurrency.asStateFlow()

    // Tracking last refresh time
    private var lastRefreshTime = 0L
    private val refreshThreshold = 30_000L // 30 seconds

    init {
        observeWallets()
        observePrivacyMode()
        observeSelectedCurrency()
    }

    private fun observePrivacyMode() {
        viewModelScope.launch {
            securityPreferencesRepository.observePrivacyModeEnabled().collect { isEnabled ->
                _isPrivacyModeEnabled.value = isEnabled
            }
        }
    }

    private fun observeSelectedCurrency() {
        viewModelScope.launch {
            securityPreferencesRepository.observeSelectedCurrency().collect { currency ->
                val previousCurrency = _selectedCurrency.value
                _selectedCurrency.value = currency
                if (previousCurrency != currency) {
                    refresh()
                }
            }
        }
    }

    private fun observeWallets() {
        viewModelScope.launch {
            walletRepository.observeWallets()
                .catch { e ->
                    _uiState.value = Result.Error("Failed to load wallets: ${e.message}")
                }
                .collectLatest { walletsList ->
                    val previousState = _uiState.value
                    _uiState.value = Result.Success(walletsList)

                    // Automatically trigger refresh if a new wallet was added
                    val previousWallets = (previousState as? Result.Success)?.data ?: emptyList()
                    if (walletsList.isNotEmpty() && walletsList.size > previousWallets.size) {
                        refresh(force = true)
                    }
                }
        }
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

    fun renameWallet(walletId: String, newName: String) {
        viewModelScope.launch {
            _isOperationLoading.update { true }
            _operationError.update { null }

            runCatching {
                walletRepository.updateWalletName(walletId, newName)
            }.onFailure { e ->
                _operationError.update { "Failed to rename wallet: ${e.message}" }
            }

            _isOperationLoading.update { false }
        }
    }

    fun refresh(force: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (!force) {
            if (currentTime - lastRefreshTime < refreshThreshold && _isRefreshing.value) return
            if (currentTime - lastRefreshTime < refreshThreshold && _uiState.value !is Result.Error) {
                // If we refreshed recently and have data, don't block or restart sync
                return
            }
        }

        viewModelScope.launch {
            _isRefreshing.update { true }
            _isOperationLoading.update { false } // Safety reset
            lastRefreshTime = currentTime
            _operationError.update { null }

            val currentWallets = (_uiState.value as? Result.Success)?.data ?: emptyList()
            if (currentWallets.isNotEmpty()) {
                val allErrors = mutableListOf<ChainSyncError>()

                // Fetch prices once for all wallets
                val allSymbols = currentWallets.flatMap { wallet ->
                    wallet.bitcoinCoins.map { it.symbol } +
                            wallet.solanaCoins.map { it.symbol } +
                            wallet.evmTokens.map { it.symbol }
                }.distinct()

                val pricesResult = getSimplePricesUseCase(allSymbols, _selectedCurrency.value)
                val prices = if (pricesResult is Result.Success) pricesResult.data else emptyMap()

                if (pricesResult is Result.Error) {
                    _operationError.update { "Price fetch failed: ${pricesResult.message}. Using last known balances." }
                }

                currentWallets.forEach { wallet ->
                    val btcBalances = mutableMapOf<com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork, com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance>()
                    val solBalances = mutableMapOf<com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork, com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance>()
                    val evmList = mutableListOf<com.example.nexuswallet.feature.wallet.domain.model.EVMBalance>()

                    // Sync Bitcoin
                    wallet.bitcoinCoins.forEach { coin ->
                        val (balance, errors) = syncBitcoinBalanceUseCase(wallet.id, coin, prices[coin.symbol] ?: 0.0, saveToCache = false)
                        balance?.let { btcBalances[coin.network] = it }
                        allErrors.addAll(errors)
                    }

                    // Sync Solana
                    wallet.solanaCoins.forEach { coin ->
                        val (balance, errors) = syncSolanaBalanceUseCase(wallet.id, coin, prices[coin.symbol] ?: 0.0, saveToCache = false)
                        balance?.let { solBalances[coin.network] = it }
                        allErrors.addAll(errors)
                    }

                    // Sync EVM
                    if (wallet.evmTokens.isNotEmpty()) {
                        val (balances, errors) = syncEVMBalancesUseCase(wallet.id, wallet.evmTokens, prices, saveToCache = false)
                        evmList.addAll(balances)
                        allErrors.addAll(errors)
                    }

                    // ATOMIC UPDATE: Save the full wallet balance at once
                    if (btcBalances.isNotEmpty() || solBalances.isNotEmpty() || evmList.isNotEmpty()) {
                        val newBalance = WalletBalance(
                            walletId = wallet.id,
                            lastUpdated = System.currentTimeMillis(),
                            bitcoinBalances = btcBalances,
                            solanaBalances = solBalances,
                            evmBalances = evmList
                        )
                        walletRepository.saveWalletBalance(newBalance)
                    }
                }

                if (allErrors.isNotEmpty()) {
                    val errorString = allErrors.distinctBy { "${it.network.name}-${it.assetSymbol}" }
                        .joinToString("\n") { error ->
                            val assetPrefix = error.assetSymbol?.let { "$it on " } ?: ""
                            "• $assetPrefix${error.network.name}: ${error.message}"
                        }
                    val existingError = _operationError.value
                    val newError = "Partial sync failure:\n$errorString"
                    _operationError.update { if (existingError != null) "$existingError\n\n$newError" else newError }
                }
            }

            _isRefreshing.update { false }
        }
    }

    fun clearOperationError() {
        _operationError.update { null }
    }
}