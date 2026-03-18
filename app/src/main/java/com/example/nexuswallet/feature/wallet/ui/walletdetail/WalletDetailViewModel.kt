package com.example.nexuswallet.feature.wallet.ui.walletdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.domain.model.Transaction
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.MarketRepository
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
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
        private val USD_FORMATTER = NumberFormat.getCurrencyInstance(Locale.US)
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

                // STEP 4: Observe transactions (this will also trigger initial load via onStart)
                observeTransactions(walletId, loadedWallet)

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
            Result.Loading -> { /* Ignored since this will be omitted */ }
        }
        updateAssets()
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

    private fun observeTransactions(walletId: String, wallet: Wallet) {
        viewModelScope.launch {
            getAllTransactionsUseCase(walletId)
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    _uiState.update {
                        it.copy(transactionsError = e.message)
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

    private fun formatTransactionList(transactions: List<Transaction>, wallet: Wallet): List<TransactionDisplayInfo> {
        return transactions.map { transaction ->
            val coin = findCoinForTransaction(transaction, wallet)
            formatTransactionDisplayUseCase(transaction, coin)
        }
    }

    private fun findCoinForTransaction(transaction: Transaction, wallet: Wallet): Coin {
        return when (transaction) {
            is BitcoinTransaction -> {
                //  Find the Bitcoin coin that matches the transaction's network
                wallet.bitcoinCoins.find { it.network == transaction.network }
                    ?: error("No Bitcoin coin found for network ${transaction.network.name}")
            }
            is SolanaTransaction -> {
                //  Find the Solana coin that matches the transaction's network
                wallet.solanaCoins.find { it.network == transaction.network }
                    ?: error("No Solana coin found for network ${transaction.network.name}")
            }
            is NativeETHTransaction -> {
                //  Find the Native ETH token that matches the transaction's network
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
        //  Find the token that matches both network AND token type
        return wallet.evmTokens.find {
            it.network == transaction.network && it.tokenType == transaction.tokenType
        } ?: error("No token found for ${transaction.tokenType} on ${transaction.network.name}")
    }

    fun getWalletName(): String = _uiState.value.wallet?.name ?: "Wallet Details"

    fun refresh() {
        _uiState.value.wallet?.let { wallet ->
            viewModelScope.launch {
                refreshBalanceInBackground(wallet.id, wallet)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}