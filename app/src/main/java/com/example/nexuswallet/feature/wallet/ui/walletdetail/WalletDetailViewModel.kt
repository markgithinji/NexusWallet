package com.example.nexuswallet.feature.wallet.ui.walletdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.ethereum.domain.model.TokenTransaction
import com.example.nexuswallet.feature.market.domain.MarketRepository
import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatBalanceUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncWalletBalancesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val syncWalletBalancesUseCase: SyncWalletBalancesUseCase,
    private val getAllTransactionsUseCase: GetAllTransactionsUseCase,
    private val marketRepository: MarketRepository,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
    private val formatBalanceUseCase: FormatBalanceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletDetailUiState(isLoading = true))
    val uiState: StateFlow<WalletDetailUiState> = _uiState.asStateFlow()

    // Cache expiration times
    private companion object {
        const val BALANCE_CACHE_TIME = 2 * 60 * 1000      // 2 minutes
        const val TRANSACTIONS_CACHE_TIME = 5 * 60 * 1000 // 5 minutes
        private val USD_FORMATTER = NumberFormat.getCurrencyInstance(Locale.US)
    }

    fun loadWallet(walletId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val currentState = _uiState.value

            // Check cache freshness
            val isBalanceFresh = now - currentState.lastBalanceSyncTime < BALANCE_CACHE_TIME
            val isTransactionsFresh =
                now - currentState.lastTransactionSyncTime < TRANSACTIONS_CACHE_TIME

            // If we already have wallet data and it's fresh, don't show loading
            if (currentState.wallet != null && isBalanceFresh && isTransactionsFresh) {
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

                // STEP 4: Load cached transactions
                launch {
                    loadCachedTransactions(walletId)
                }

                // STEP 5: Observe transactions
                observeTransactions(walletId)

                // STEP 6: Refresh if needed
                if (!isBalanceFresh) {
                    refreshBalanceInBackground(walletId, loadedWallet)
                }
                if (!isTransactionsFresh) {
                    refreshTransactionsInBackground(walletId)
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

    private suspend fun loadCachedTransactions(walletId: String) {
        _uiState.update { it.copy(isLoadingTransactions = true) }

        runCatching {
            val flow = getAllTransactionsUseCase(walletId, forceRefresh = false, observe = false)
            flow?.first() ?: emptyList()
        }.onSuccess { initialTransactions ->
            val displayTransactions = formatTransactionList(initialTransactions)
            _uiState.update {
                it.copy(
                    transactions = displayTransactions,
                    isLoadingTransactions = false,
                    lastTransactionSyncTime = System.currentTimeMillis()
                )
            }
        }.onFailure {
            _uiState.update {
                it.copy(
                    transactionsError = "Failed to load transactions",
                    isLoadingTransactions = false
                )
            }
        }
    }

    private suspend fun loadMarketPercentages() {
        runCatching {
            marketRepository.getLatestPricePercentages()
        }.onSuccess { percentagesResult ->
            when (percentagesResult) {
                is Result.Success -> {
                    _uiState.update { it.copy(pricePercentages = percentagesResult.data) }
                    updateAssets()
                }

                is Result.Error -> {
                    _uiState.update { it.copy(pricePercentages = emptyMap()) }
                    updateAssets()
                }

                Result.Loading -> {}
            }
        }.onFailure {
            // Silently fail
        }
    }

    private fun refreshBalanceInBackground(walletId: String, wallet: Wallet) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingBalance = true) }

            when (val syncResult = syncWalletBalancesUseCase(wallet)) {
                is Result.Success -> {
                    val updatedBalance = walletRepository.getWalletBalance(walletId)
                    if (updatedBalance != null) {
                        _uiState.update {
                            it.copy(
                                balance = updatedBalance,
                                hasSyncError = false,
                                syncErrorMessage = null,
                                isRefreshingBalance = false,
                                lastBalanceSyncTime = System.currentTimeMillis()
                            )
                        }
                        updateAssets()
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            hasSyncError = true,
                            syncErrorMessage = syncResult.message,
                            isRefreshingBalance = false
                        )
                    }
                    val currentBalance = walletRepository.getWalletBalance(walletId)
                    if (currentBalance != null && currentBalance != _uiState.value.balance) {
                        _uiState.update { it.copy(balance = currentBalance) }
                        updateAssets()
                    }
                }

                Result.Loading -> {}
            }
        }
    }

    private fun refreshTransactionsInBackground(walletId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingTransactions = true) }

            runCatching {
                getAllTransactionsUseCase(walletId, forceRefresh = true, observe = false)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isRefreshingTransactions = false,
                        lastTransactionSyncTime = System.currentTimeMillis()
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        transactionsError = e.message,
                        isRefreshingTransactions = false
                    )
                }
            }
        }
    }

    private fun observeTransactions(walletId: String) {
        viewModelScope.launch {
            val flow = getAllTransactionsUseCase(walletId, observe = true)
            flow?.flowOn(Dispatchers.IO)
                ?.catch { e -> /* silent */ }
                ?.collect { updatedTransactions ->
                    val displayTransactions = formatTransactionList(updatedTransactions)
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
            pricePercentages = state.pricePercentages
        )

        val totalUsd = assets.sumOf { it.usdValue }
        val totalFormatted = USD_FORMATTER.format(totalUsd)

        _uiState.update {
            it.copy(
                assets = assets,
                totalBalanceFormatted = totalFormatted
            )
        }
    }

    private fun formatTransactionList(transactions: List<Any>): List<TransactionDisplayInfo> {
        return transactions.map { transaction ->
            val coinType = determineCoinType(transaction)
            formatTransactionDisplayUseCase(transaction, coinType)
        }
    }

    private fun determineCoinType(transaction: Any): CoinType {
        return when (transaction) {
            is BitcoinTransaction -> CoinType.BITCOIN
            is SolanaTransaction -> CoinType.SOLANA
            is NativeETHTransaction -> CoinType.ETHEREUM
            is TokenTransaction -> when (transaction.tokenSymbol) {
                "USDC" -> CoinType.USDC
                else -> CoinType.ETHEREUM
            }

            else -> CoinType.BITCOIN
        }
    }

    fun getWalletName(): String = _uiState.value.wallet?.name ?: "Wallet Details"

    fun refresh() {
        _uiState.value.wallet?.let { wallet ->
            viewModelScope.launch {
                loadWallet(wallet.id)
            }
        }
    }
}