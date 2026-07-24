package com.example.nexuswallet.feature.wallet.ui.walletdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.domain.model.Transaction
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.repository.MarketRepository
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatBalanceUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncBitcoinBalanceUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncEVMBalancesUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncSolanaBalanceUseCase
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.nexuswallet.feature.core.util.formatCurrency
import javax.inject.Inject

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val syncBitcoinBalanceUseCase: SyncBitcoinBalanceUseCase,
    private val syncSolanaBalanceUseCase: SyncSolanaBalanceUseCase,
    private val syncEVMBalancesUseCase: SyncEVMBalancesUseCase,
    private val getAllTransactionsUseCase: GetAllTransactionsUseCase,
    private val marketRepository: MarketRepository,
    private val getSimplePricesUseCase: GetSimplePricesUseCase,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
    private val formatBalanceUseCase: FormatBalanceUseCase,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletDetailUiState(isLoading = true))
    val uiState: StateFlow<WalletDetailUiState> = _uiState.asStateFlow()

    private var transactionsJob: Job? = null

    // Cache expiration times
    private companion object {
        const val BALANCE_CACHE_TIME = 2 * 60 * 1000      // 2 minutes
    }

    init {
        observeSelectedCurrency()
    }

    private fun observeSelectedCurrency() {
        viewModelScope.launch {
            securityPreferencesRepository.observeSelectedCurrency().collect { currency ->
                val previousCurrency = _uiState.value.selectedCurrency
                _uiState.update { it.copy(selectedCurrency = currency) }
                
                // If currency changed and we have a wallet, refresh to get new prices
                if (previousCurrency != currency && _uiState.value.wallet != null) {
                    refresh()
                }
            }
        }
    }

    fun loadWallet(walletId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val currentState = _uiState.value

            // Check balance cache freshness
            val isBalanceFresh = now - currentState.lastBalanceSyncTime < BALANCE_CACHE_TIME

            // If we already have wallet data and balance is fresh, don't show loading
            if (currentState.wallet != null && isBalanceFresh) {
                return@launch
            }

            // Set initial loading state only if we don't have wallet yet
            _uiState.update {
                it.copy(
                    isLoading = it.wallet == null,
                    error = null,
                    hasSyncError = false
                )
            }

            runCatching {
                // STEP 1: Load wallet from repository
                val loadedWallet = walletRepository.getWallet(walletId)
                    ?: throw IllegalStateException("Wallet not found")

                // Update wallet immediately
                _uiState.update { it.copy(wallet = loadedWallet) }

                // STEP 2: Load cached balance in parallel
                launch {
                    loadCachedBalance(walletId)
                }

                // STEP 3: Load market percentages
                launch {
                    loadMarketPercentages()
                }

                // STEP 4: Observe transactions - initial load without force refresh
                observeTransactions(walletId, loadedWallet, forceRefresh = false)

                // STEP 5: Refresh balance if needed
                if (!isBalanceFresh) {
                    refreshBalanceInBackground(walletId, loadedWallet)
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        error = "Failed to load wallet: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    private suspend fun loadCachedBalance(walletId: String) {
        _uiState.update { it.copy(isLoadingBalance = true) }

        runCatching {
            walletRepository.getWalletBalance(walletId)
        }.onSuccess { loadedBalance ->
            _uiState.update {
                it.copy(
                    balance = loadedBalance,
                    isLoading = false,
                    isLoadingBalance = false,
                    lastBalanceSyncTime = System.currentTimeMillis()
                )
            }
            updateAssets()
        }.onFailure {
            _uiState.update {
                it.copy(
                    balanceError = "Failed to load balance",
                    isLoading = false,
                    isLoadingBalance = false
                )
            }
        }
    }

    private suspend fun loadMarketPercentages() {
        when (val percentagesResult = marketRepository.getLatestPricePercentages()) {
            is Result.Success -> {
                _uiState.update { it.copy(pricePercentages = percentagesResult.data) }
            }

            is Result.Error -> {
                _uiState.update { it.copy(pricePercentages = emptyMap()) }
            }

            Result.Loading -> { /* Ignored since this will be omitted */
            }
        }
        updateAssets()
    }

    private fun refreshBalanceInBackground(walletId: String, wallet: Wallet) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingBalance = true) }

            // 1. Fetch prices
            val symbols = (wallet.bitcoinCoins.map { it.symbol } +
                    wallet.solanaCoins.map { it.symbol } +
                    wallet.evmTokens.map { it.symbol }).distinct()

            val pricesResult = getSimplePricesUseCase(symbols, _uiState.value.selectedCurrency)
            val prices = if (pricesResult is Result.Success) pricesResult.data else emptyMap()

            // 2. Sync balances with prices
            val allErrors = mutableListOf<com.example.nexuswallet.feature.wallet.domain.model.ChainSyncError>()

            wallet.bitcoinCoins.forEach { coin ->
                allErrors.addAll(syncBitcoinBalanceUseCase(wallet.id, coin, prices[coin.symbol] ?: 0.0))
            }

            wallet.solanaCoins.forEach { coin ->
                allErrors.addAll(syncSolanaBalanceUseCase(wallet.id, coin, prices[coin.symbol] ?: 0.0))
            }

            if (wallet.evmTokens.isNotEmpty()) {
                allErrors.addAll(syncEVMBalancesUseCase(wallet.id, wallet.evmTokens, prices))
            }

            if (allErrors.isEmpty()) {
                val updatedBalance = walletRepository.getWalletBalance(walletId)
                if (updatedBalance != null) {
                    _uiState.update {
                        it.copy(
                            balance = updatedBalance,
                            hasSyncError = false,
                            syncErrorMessage = null,
                            syncErrors = emptyList(),
                            isRefreshingBalance = false,
                            lastBalanceSyncTime = System.currentTimeMillis()
                        )
                    }
                    updateAssets()
                }
            } else {
                _uiState.update {
                    it.copy(
                        hasSyncError = true,
                        syncErrorMessage = allErrors.joinToString { error -> "${error.assetSymbol}: ${error.message}" },
                        syncErrors = allErrors,
                        isRefreshingBalance = false
                    )
                }
                val currentBalance = walletRepository.getWalletBalance(walletId)
                if (currentBalance != null && currentBalance != _uiState.value.balance) {
                    _uiState.update { it.copy(balance = currentBalance) }
                    updateAssets()
                }
            }
        }
    }

    private fun observeTransactions(
        walletId: String,
        wallet: Wallet,
        forceRefresh: Boolean = false
    ) {
        transactionsJob?.cancel()
        transactionsJob = viewModelScope.launch {
            // Set loading state only if it's a manual refresh
            if (forceRefresh) {
                _uiState.update { it.copy(isRefreshingTransactions = true) }
            } else if (_uiState.value.transactions.isEmpty()) {
                _uiState.update { it.copy(isLoadingTransactions = true) }
            }

            getAllTransactionsUseCase(walletId, forceRefresh)
                .flowOn(ioDispatcher)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            transactionsError = "Failed to load transactions: ${e.message}",
                            isLoadingTransactions = false,
                            isRefreshingTransactions = false
                        )
                    }
                }
                .collect { updatedTransactions ->
                    val displayTransactions = formatTransactionList(updatedTransactions, wallet)
                    _uiState.update {
                        it.copy(
                            transactions = displayTransactions,
                            isLoadingTransactions = false,
                            isRefreshingTransactions = false
                        )
                    }
                }
        }
    }

    private fun updateAssets() {
        val state = _uiState.value
        val wallet = state.wallet ?: return

        val assets = formatBalanceUseCase(
            walletId = wallet.id,
            wallet = wallet,
            balance = state.balance,
            pricePercentages = state.pricePercentages,
            currencyCode = state.selectedCurrency
        )

        val totalUsd = assets.sumOf { it.usdValue }
        val totalFormatted = totalUsd.formatCurrency(state.selectedCurrency)

        _uiState.update {
            it.copy(
                assets = assets,
                totalBalanceFormatted = totalFormatted
            )
        }
    }

    private fun formatTransactionList(
        transactions: List<Transaction>,
        wallet: Wallet
    ): List<TransactionDisplayInfo> {
        return transactions.map { transaction ->
            val coin = findCoinForTransaction(transaction, wallet)
            formatTransactionDisplayUseCase(transaction, coin)
        }
    }

    private fun findCoinForTransaction(transaction: Transaction, wallet: Wallet): Coin {
        return when (transaction) {
            is BitcoinTransaction -> {
                wallet.bitcoinCoins.find { it.network == transaction.network }
                    ?: error("No Bitcoin coin found for network ${transaction.network.name}")
            }

            is SolanaTransaction -> {
                wallet.solanaCoins.find { it.network == transaction.network }
                    ?: error("No Solana coin found for network ${transaction.network.name}")
            }

            is NativeETHTransaction -> {
                wallet.evmTokens.find {
                    it is NativeETH && it.network == transaction.network
                } ?: error("No NativeETH found for network ${transaction.network.name}")
            }

            is TokenTransaction -> {
                findTokenForTransaction(transaction, wallet)
            }
        }
    }

    private fun findTokenForTransaction(transaction: TokenTransaction, wallet: Wallet): EVMToken {
        return wallet.evmTokens.find {
            it.network == transaction.network && it.evmTokenType == transaction.evmTokenType
        } ?: error("No token found for ${transaction.evmTokenType} on ${transaction.network.name}")
    }

    fun getWalletName(): String = _uiState.value.wallet?.name ?: "Wallet Details"

    fun refresh() {
        _uiState.value.wallet?.let { wallet ->
            viewModelScope.launch {

                // Refresh balance
                refreshBalanceInBackground(wallet.id, wallet)

                // Refresh transactions with forceRefresh = true
                // This will trigger a new sync and update the UI when complete
                observeTransactions(wallet.id, wallet, forceRefresh = true)
            }
        }
    }

    fun renameWallet(newName: String) {
        val currentWallet = _uiState.value.wallet ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                walletRepository.updateWalletName(currentWallet.id, newName)
                // Reload wallet to get updated name
                val updatedWallet = walletRepository.getWallet(currentWallet.id)
                _uiState.update { it.copy(wallet = updatedWallet, isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Failed to rename wallet: ${e.message}", isLoading = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}