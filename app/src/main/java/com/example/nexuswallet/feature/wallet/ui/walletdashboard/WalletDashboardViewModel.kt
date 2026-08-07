package com.example.nexuswallet.feature.wallet.ui.walletdashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.ChainSyncError
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import com.example.nexuswallet.feature.core.service.BlockchainSubscriptionService
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncBitcoinBalanceUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncEVMBalancesUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncSolanaBalanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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
    private val settingsRepository: SettingsRepository,
    private val subscriptionService: BlockchainSubscriptionService
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow<Result<List<Wallet>>>(Result.Loading)
    val uiState: StateFlow<Result<List<Wallet>>> = _uiState.asStateFlow()

    private val _syncingNetworks = MutableStateFlow<Set<Network>>(emptySet())
    val syncingNetworks: StateFlow<Set<Network>> = _syncingNetworks.asStateFlow()

    // Balances map (reactive)
    val balances: StateFlow<Map<String, WalletBalance>> = walletRepository.observeAllBalances()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // Total portfolio value (reactive)
    val totalPortfolioValue: StateFlow<BigDecimal> = balances.map { balancesMap ->
        balancesMap.values.fold(BigDecimal.ZERO) { acc, balance ->
            acc.add(balance.totalUsdValue)
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

    private val _selectedCurrency = MutableStateFlow(SupportedCurrency.USD)
    val selectedCurrency: StateFlow<SupportedCurrency> = _selectedCurrency.asStateFlow()

    // Tracking last refresh time
    private var lastRefreshTime = 0L
    private val refreshThreshold = 30_000L // 30 seconds

    private val signalFlow = MutableSharedFlow<Network>(extraBufferCapacity = 10)
    private var subscriptionJob: Job? = null
    private var priceRefreshJob: Job? = null
    private var lastPrices: Map<String, Double> = emptyMap()
    
    // racks the last time each network was auto-refreshed
    // to prevent redundant calls from high-frequency blockchains (like Ethereum blocks every 12s).
    private val lastNetworkRefreshTimes = mutableMapOf<Network, Long>()
    private val REFRESH_COOLDOWN_MS = 60_000L // 1 minute cooldown for background auto-refreshes

    init {
        observeWallets()
        observePrivacyMode()
        observeSelectedCurrency()
        observeSignals()
        startPriceTimer()
    }

    /**
     * Periodically refreshes fiat prices (USD/EUR) on a slow timer.
     * Prices are decoupled from balance updates to avoid hitting CoinGecko rate limits.
     */
    private fun startPriceTimer() {
        priceRefreshJob?.cancel()
        priceRefreshJob = viewModelScope.launch {
            while (true) {
                refreshPrices()
                delay(300_000L) // Refresh prices every 5 minutes
            }
        }
    }

    private suspend fun refreshPrices() {
        val wallets = (_uiState.value as? Result.Success)?.data ?: return
        if (wallets.isEmpty()) return

        val allSymbols = wallets.flatMap { wallet ->
            wallet.bitcoinCoins.map { it.symbol } +
                    wallet.solanaCoins.map { it.symbol } +
                    wallet.evmTokens.map { it.symbol }
        }.distinct()

        val pricesResult = getSimplePricesUseCase(allSymbols, _selectedCurrency.value)
        if (pricesResult is Result.Success) {
            lastPrices = pricesResult.data
        }
    }

    /**
     * Listens for real-time activity signals from the BlockchainSubscriptionService.
     * Signals (like "Ethereum activity detected") trigger a granular balance refresh.
     */
    private fun observeSignals() {
        viewModelScope.launch {
            signalFlow
                .debounce(5000L) // Groups multiple rapid events into a single refresh cycle.
                .collect { network ->
                    val lastRefresh = lastNetworkRefreshTimes.getOrDefault(network, 0L)
                    val currentTime = System.currentTimeMillis()
                    
                    // Cooldown logic: Only auto-refresh if at least 1 minute has passed since the last one.
                    // This prevents the app from constant fetching when blocks are fast.
                    if (currentTime - lastRefresh >= REFRESH_COOLDOWN_MS) {
                        lastNetworkRefreshTimes[network] = currentTime
                        refresh(force = true, networkFilter = network)
                    }
                }
        }
    }

    private fun observePrivacyMode() {
        viewModelScope.launch {
            settingsRepository.observePrivacyModeEnabled().collect { isEnabled ->
                _isPrivacyModeEnabled.value = isEnabled
            }
        }
    }

    private fun observeSelectedCurrency() {
        viewModelScope.launch {
            settingsRepository.observeSelectedCurrency().collect { currency ->
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

                    // Update subscriptions when wallets change
                    updateSubscriptions(walletsList)
                }
        }
    }

    private fun updateSubscriptions(wallets: List<Wallet>) {
        subscriptionJob?.cancel()
        subscriptionJob = viewModelScope.launch {
            wallets.forEach { wallet ->
                // Subscribe to Bitcoin
                wallet.bitcoinCoins.forEach { coin ->
                    launch {
                        subscriptionService.subscribeToAddressChanges(coin.address, coin.network)
                            .collect { _ -> signalFlow.emit(coin.network) }
                    }
                }
                // Subscribe to Solana
                wallet.solanaCoins.forEach { coin ->
                    launch {
                        subscriptionService.subscribeToAddressChanges(coin.address, coin.network)
                            .collect { _ -> signalFlow.emit(coin.network) }
                    }
                }
                // Subscribe to Ethereum
                wallet.evmTokens.forEach { token ->
                    launch {
                        subscriptionService.subscribeToAddressChanges(token.address, token.network)
                            .collect { _ -> signalFlow.emit(token.network) }
                    }
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

    /**
     * Primary data synchronization engine.
     * 
     * @param force If true, bypasses the internal rate-limit threshold.
     * @param networkFilter If provided, only syncs the specific blockchain network that signaled a change.
     */
    fun refresh(force: Boolean = false, networkFilter: Network? = null) {
        val currentTime = System.currentTimeMillis()
        // Threshold: Only allow one "Global" refresh every 30 seconds to save battery and data.
        if (!force && networkFilter == null) {
            if (currentTime - lastRefreshTime < refreshThreshold && _isRefreshing.value) return
            if (currentTime - lastRefreshTime < refreshThreshold && _uiState.value !is Result.Error) {
                return
            }
        }

        viewModelScope.launch {
            _isRefreshing.update { true }
            _isOperationLoading.update { false }
            if (networkFilter == null) lastRefreshTime = currentTime
            _operationError.update { null }

            val currentWallets = (_uiState.value as? Result.Success)?.data ?: emptyList()
            if (currentWallets.isNotEmpty()) {
                
                // DECISION: Should we hit the Price API?
                // Price updates are expensive and rate-limited. We only fetch on manual refresh (networkFilter == null)
                // or if we have no price data yet. Otherwise, we use the cached values.
                val prices = if (networkFilter == null || lastPrices.isEmpty()) {
                    val allSymbols = currentWallets.flatMap { wallet ->
                        wallet.bitcoinCoins.map { it.symbol } +
                                wallet.solanaCoins.map { it.symbol } +
                                wallet.evmTokens.map { it.symbol }
                    }.distinct()
                    val pricesResult = getSimplePricesUseCase(allSymbols, _selectedCurrency.value)
                    if (pricesResult is Result.Success) {
                        lastPrices = pricesResult.data
                        pricesResult.data
                    } else {
                        if (pricesResult is Result.Error) {
                            _operationError.update { "Price fetch failed: ${pricesResult.message}" }
                        }
                        lastPrices
                    }
                } else {
                    lastPrices
                }

                val allErrors = mutableListOf<ChainSyncError>()

                coroutineScope {
                    currentWallets.map { wallet ->
                        // Skip processing for entire wallets that don't match the current network signal.
                        if (networkFilter != null) {
                            val matchesFilter = when (networkFilter) {
                                is BitcoinNetwork -> wallet.bitcoinCoins.any { it.network == networkFilter }
                                is SolanaNetwork -> wallet.solanaCoins.any { it.network == networkFilter }
                                is EthereumNetwork -> wallet.evmTokens.any { it.network == networkFilter }
                            }
                            if (!matchesFilter) return@map async { emptyList<ChainSyncError>() }
                        }

                        async {
                            val btcBalances = mutableMapOf<BitcoinNetwork, BitcoinBalance>()
                            val solBalances = mutableMapOf<SolanaNetwork, SolanaBalance>()
                            val evmMap = mutableMapOf<String, EVMBalance>()
                            val walletErrors = mutableListOf<ChainSyncError>()

                            // Only fetch chains that match the filter (or all if filter is null)
                            // This ensures that an Ethereum change doesn't hit the rate-limited Bitcoin API.
                            if (networkFilter == null || networkFilter is BitcoinNetwork) {
                                wallet.bitcoinCoins
                                    .filter { networkFilter == null || it.network == networkFilter }
                                    .forEach { coin ->
                                        _syncingNetworks.update { it + coin.network }
                                        val (balance, errors) = syncBitcoinBalanceUseCase(
                                            wallet.id,
                                            coin,
                                            prices[coin.symbol] ?: 0.0,
                                            saveToCache = false
                                        )
                                        balance?.let { btcBalances[coin.network] = it }
                                        walletErrors.addAll(errors)
                                        _syncingNetworks.update { it - coin.network }
                                    }
                            }

                            if (networkFilter == null || networkFilter is SolanaNetwork) {
                                wallet.solanaCoins
                                    .filter { networkFilter == null || it.network == networkFilter }
                                    .forEach { coin ->
                                        _syncingNetworks.update { it + coin.network }
                                        val (balance, errors) = syncSolanaBalanceUseCase(
                                            wallet.id,
                                            coin,
                                            prices[coin.symbol] ?: 0.0,
                                            saveToCache = false
                                        )
                                        balance?.let { solBalances[coin.network] = it }
                                        walletErrors.addAll(errors)
                                        _syncingNetworks.update { it - coin.network }
                                    }
                            }

                            if (wallet.evmTokens.isNotEmpty() && (networkFilter == null || networkFilter is EthereumNetwork)) {
                                val evmNetworks = wallet.evmTokens.map { it.network }.toSet()
                                _syncingNetworks.update { it + evmNetworks }
                                val (balances, errors) = syncEVMBalancesUseCase(
                                    wallet.id,
                                    wallet.evmTokens,
                                    prices,
                                    saveToCache = false
                                )
                                evmMap.putAll(balances)
                                walletErrors.addAll(errors)
                                _syncingNetworks.update { it - evmNetworks }
                            }

                            // ATOMIC UPDATE: Merge the new chain balance with the existing cached balances for other chains.
                            val existing = walletRepository.getWalletBalance(wallet.id)
                            val newBalance = WalletBalance(
                                walletId = wallet.id,
                                lastUpdated = System.currentTimeMillis(),
                                bitcoinBalances = existing?.bitcoinBalances?.toMutableMap()?.apply {
                                    if (networkFilter == null || networkFilter is BitcoinNetwork) putAll(btcBalances)
                                } ?: btcBalances,
                                solanaBalances = existing?.solanaBalances?.toMutableMap()?.apply {
                                    if (networkFilter == null || networkFilter is SolanaNetwork) putAll(solBalances)
                                } ?: solBalances,
                                evmBalances = if (networkFilter == null || networkFilter is EthereumNetwork) {
                                    existing?.evmBalances?.toMutableMap()?.apply {
                                        putAll(evmMap)
                                    } ?: evmMap
                                } else {
                                    existing?.evmBalances ?: emptyMap()
                                }
                            )
                            walletRepository.saveWalletBalance(newBalance)
                            walletErrors
                        }
                    }.awaitAll().forEach { errors ->
                        allErrors.addAll(errors)
                    }
                }

                if (allErrors.isNotEmpty()) {
                    val errorString =
                        allErrors.distinctBy { "${it.network.name}-${it.assetSymbol}" }
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

    override fun onCleared() {
        super.onCleared()
        subscriptionService.clearAllSubscriptions()
    }
}