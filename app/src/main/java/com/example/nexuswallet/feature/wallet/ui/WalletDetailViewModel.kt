package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.market.domain.MarketRepository
import com.example.nexuswallet.feature.wallet.domain.ERC20Token
import com.example.nexuswallet.feature.wallet.domain.EVMToken
import com.example.nexuswallet.feature.wallet.domain.NativeETH
import com.example.nexuswallet.feature.wallet.domain.SPLToken
import com.example.nexuswallet.feature.wallet.domain.TokenType
import com.example.nexuswallet.feature.wallet.domain.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.USDCToken
import com.example.nexuswallet.feature.wallet.domain.USDTToken
import com.example.nexuswallet.feature.wallet.domain.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val syncWalletBalancesUseCase: SyncWalletBalancesUseCase,
    private val getAllTransactionsUseCase: GetAllTransactionsUseCase,
    private val marketRepository: MarketRepository,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase
) : ViewModel() {

    data class WalletDetailUiState(
        val wallet: Wallet? = null,
        val balance: WalletBalance? = null,
        val transactions: List<TransactionDisplayInfo> = emptyList(),
        val pricePercentages: Map<String, Double> = emptyMap(),

        // Granular loading states
        val isLoading: Boolean = false,
        val isLoadingBalance: Boolean = false,
        val isLoadingTransactions: Boolean = false,
        val isRefreshingBalance: Boolean = false,
        val isRefreshingTransactions: Boolean = false,

        // Timestamps for cache freshness
        val lastBalanceSyncTime: Long = 0,
        val lastTransactionSyncTime: Long = 0,

        // Error states
        val error: String? = null,
        val hasSyncError: Boolean = false,
        val syncErrorMessage: String? = null,
        val balanceError: String? = null,
        val transactionsError: String? = null
    )

    private val _uiState = MutableStateFlow(WalletDetailUiState(isLoading = true))
    val uiState: StateFlow<WalletDetailUiState> = _uiState.asStateFlow()

    // Cache expiration times
    private companion object {
        const val BALANCE_CACHE_TIME = 0 * 60 * 1000      // 2 minutes
        const val TRANSACTIONS_CACHE_TIME = 0 * 60 * 1000 // 5 minutes
    }

    fun loadWallet(walletId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val currentState = _uiState.value

            // Check cache freshness
            val isBalanceFresh = now - currentState.lastBalanceSyncTime < BALANCE_CACHE_TIME
            val isTransactionsFresh = now - currentState.lastTransactionSyncTime < TRANSACTIONS_CACHE_TIME

            // If we already have wallet data and it's fresh, don't show loading
            if (currentState.wallet != null && isBalanceFresh && isTransactionsFresh) {
                Log.d("WalletDetailVM", "Using fresh cached data")
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

            Log.d("WalletDetailVM", "=== Loading wallet: $walletId ===")

            try {
                // STEP 1: Load wallet from repository (fast, local DB)
                val loadedWallet = walletRepository.getWallet(walletId)

                if (loadedWallet == null) {
                    Log.e("WalletDetailVM", "Wallet not found: $walletId")
                    _uiState.update { it.copy(error = "Wallet not found", isLoading = false) }
                    return@launch
                }

                // Update wallet immediately
                _uiState.update { it.copy(wallet = loadedWallet) }

                // STEP 2: Load cached balance in parallel
                launch {
                    loadCachedBalance(walletId)
                }

                // STEP 3: Load market percentages (fast, usually cached)
                launch {
                    loadMarketPercentages()
                }

                // STEP 4: Load cached transactions in parallel
                launch {
                    loadCachedTransactions(walletId)
                }

                // STEP 5: Start observing transactions for real-time updates
                observeTransactions(walletId)

                // STEP 6: Check if we need to refresh balance
                if (!isBalanceFresh) {
                    refreshBalanceInBackground(walletId, loadedWallet)
                }

                // STEP 7: Check if we need to refresh transactions
                if (!isTransactionsFresh) {
                    refreshTransactionsInBackground(walletId)
                }

            } catch (e: Exception) {
                Log.e("WalletDetailVM", "Failed to load wallet: ${e.message}", e)
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
        try {
            _uiState.update { it.copy(isLoadingBalance = true) }

            Log.d("WalletDetailVM", "Loading cached balance for wallet: $walletId")
            val loadedBalance = walletRepository.getWalletBalance(walletId)

            _uiState.update {
                it.copy(
                    balance = loadedBalance,
                    isLoading = false, // Initial loading done once we have balance
                    isLoadingBalance = false,
                    lastBalanceSyncTime = System.currentTimeMillis()
                )
            }

            if (loadedBalance != null) {
                Log.d("WalletDetailVM", "Cached balance loaded")
            } else {
                Log.w("WalletDetailVM", "No cached balance found")
            }
        } catch (e: Exception) {
            Log.e("WalletDetailVM", "Error loading cached balance", e)
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
        try {
            _uiState.update { it.copy(isLoadingTransactions = true) }

            Log.d("WalletDetailVM", "Loading cached transactions...")
            val initialTransactions = getAllTransactionsUseCase.getCachedTransactions(walletId)

            // Format transactions using the existing use case
            val displayTransactions = formatTransactionList(initialTransactions)

            _uiState.update {
                it.copy(
                    transactions = displayTransactions,
                    isLoadingTransactions = false,
                    lastTransactionSyncTime = System.currentTimeMillis()
                )
            }

            Log.d("WalletDetailVM", "Loaded ${displayTransactions.size} cached transactions")

        } catch (e: Exception) {
            Log.e("WalletDetailVM", "Error loading cached transactions", e)
            _uiState.update {
                it.copy(
                    transactionsError = "Failed to load transactions",
                    isLoadingTransactions = false
                )
            }
        }
    }

    private suspend fun loadMarketPercentages() {
        try {
            Log.d("WalletDetailVM", "Loading market percentages...")
            when (val percentagesResult = marketRepository.getLatestPricePercentages()) {
                is Result.Success -> {
                    Log.d("WalletDetailVM", "Market percentages loaded: ${percentagesResult.data.size} entries")
                    _uiState.update { it.copy(pricePercentages = percentagesResult.data) }
                }
                is Result.Error -> {
                    Log.e("WalletDetailVM", "Failed to load market percentages: ${percentagesResult.message}")
                    _uiState.update { it.copy(pricePercentages = emptyMap()) }
                }
                Result.Loading -> {}
            }
        } catch (e: Exception) {
            Log.e("WalletDetailVM", "Error loading market percentages", e)
        }
    }

    private fun refreshBalanceInBackground(walletId: String, wallet: Wallet) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingBalance = true) }

            Log.d("WalletDetailVM", "Starting background balance sync...")

            when (val syncResult = syncWalletBalancesUseCase(wallet)) {
                is Result.Success -> {
                    Log.d("WalletDetailVM", "Balance sync completed successfully")
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
                    }
                }
                is Result.Error -> {
                    Log.e("WalletDetailVM", "Balance sync failed: ${syncResult.message}")
                    _uiState.update {
                        it.copy(
                            hasSyncError = true,
                            syncErrorMessage = syncResult.message,
                            isRefreshingBalance = false
                        )
                    }

                    // Try to get whatever balance we have
                    val currentBalance = walletRepository.getWalletBalance(walletId)
                    if (currentBalance != null && currentBalance != _uiState.value.balance) {
                        _uiState.update { it.copy(balance = currentBalance) }
                    }
                }
                Result.Loading -> {}
            }
        }
    }

    private fun refreshTransactionsInBackground(walletId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingTransactions = true) }

            Log.d("WalletDetailVM", "Starting background transaction refresh...")

            try {
                // This will fetch fresh transactions and update the flow
                getAllTransactionsUseCase.refreshTransactions(walletId)

                // The flow will update automatically via observeTransactions
                _uiState.update {
                    it.copy(
                        isRefreshingTransactions = false,
                        lastTransactionSyncTime = System.currentTimeMillis()
                    )
                }

            } catch (e: Exception) {
                Log.e("WalletDetailVM", "Transaction refresh failed: ${e.message}", e)
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
            getAllTransactionsUseCase.observeTransactions(walletId)
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    Log.e("WalletDetailVM", "Error observing transactions: ${e.message}", e)
                }
                .collect { updatedTransactions ->
                    Log.d("WalletDetailVM", "Transactions updated: ${updatedTransactions.size} transactions")

                    // Format transactions using the existing use case
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

    // Helper function to format a list of transactions using the existing use case
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

    fun getTotalUsdValue(): Double {
        val balance = _uiState.value.balance
        var total = 0.0

        balance?.bitcoinBalances?.forEach { (network, btcBalance) ->
            total += btcBalance.usdValue
        }

        balance?.solanaBalances?.forEach { (network, solBalance) ->
            total += solBalance.usdValue
        }

        balance?.evmBalances?.forEach { evmBalance ->
            total += evmBalance.usdValue
        }

        return total
    }

    fun getFullAddress(): String {
        val wallet = _uiState.value.wallet ?: return ""
        return wallet.bitcoinCoins.firstOrNull()?.address
            ?: wallet.solanaCoins.firstOrNull()?.address
            ?: wallet.evmTokens.firstOrNull()?.address
            ?: ""
    }

    fun refresh() {
        Log.d("WalletDetailVM", "Manual refresh triggered")
        _uiState.value.wallet?.let { wallet ->
            viewModelScope.launch {
                loadWallet(wallet.id)
            }
        }
    }

    // Helper functions for UI
    fun isBalanceLoading(): Boolean =
        _uiState.value.isLoadingBalance || _uiState.value.isRefreshingBalance || _uiState.value.isLoading

    fun isTransactionsLoading(): Boolean =
        _uiState.value.isLoadingTransactions || _uiState.value.isRefreshingTransactions

    fun getBalanceLoadingMessage(): String = when {
        _uiState.value.isRefreshingBalance -> "Updating balances..."
        _uiState.value.isLoadingBalance -> "Loading balances..."
        _uiState.value.isLoading -> "Loading wallet..."
        else -> ""
    }

    fun getTransactionsLoadingMessage(): String = when {
        _uiState.value.isRefreshingTransactions -> "Updating transactions..."
        _uiState.value.isLoadingTransactions -> "Loading transactions..."
        else -> ""
    }

    // Helper functions to get specific coin addresses
    fun getBitcoinAddresses(): List<String> =
        _uiState.value.wallet?.bitcoinCoins?.map { it.address } ?: emptyList()

    fun getSolanaAddresses(): List<String> =
        _uiState.value.wallet?.solanaCoins?.map { it.address } ?: emptyList()

    fun getEVMAddresses(): List<String> =
        _uiState.value.wallet?.evmTokens?.map { it.address }?.distinct() ?: emptyList()

    fun getAllSPLTokens(): List<SPLToken> =
        _uiState.value.wallet?.solanaCoins?.flatMap { it.splTokens } ?: emptyList()

    fun getTokensByType(type: TokenType): List<EVMToken> =
        _uiState.value.wallet?.evmTokens?.filter { token ->
            when (token) {
                is NativeETH -> type == TokenType.NATIVE
                is USDCToken -> type == TokenType.USDC
                is USDTToken -> type == TokenType.USDT
                is ERC20Token -> type == TokenType.ERC20
            }
        } ?: emptyList()
}