package com.example.nexuswallet.feature.wallet.ui.walletdashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.service.BlockchainSubscriptionService
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.ChainSyncError
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncBitcoinBalanceUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncEVMBalancesUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncSolanaBalanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    private val subscriptionService: BlockchainSubscriptionService,
    private val logger: com.example.nexuswallet.feature.logging.Logger
) : ViewModel() {

    private val TAG = "WalletDashboardVM"

    private val _uiState = MutableStateFlow<Result<List<Wallet>>>(Result.Loading)
    val uiState: StateFlow<Result<List<Wallet>>> = _uiState.asStateFlow()

    private val _syncingNetworks = MutableStateFlow<Set<Network>>(emptySet())
    val syncingNetworks: StateFlow<Set<Network>> = _syncingNetworks.asStateFlow()

    val balances: StateFlow<Map<String, WalletBalance>> = walletRepository.observeAllBalances()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val totalPortfolioValue: StateFlow<BigDecimal> = balances.map { balancesMap ->
        balancesMap.values.fold(BigDecimal.ZERO) { acc, balance ->
            acc.add(balance.totalUsdValue)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BigDecimal.ZERO
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isOperationLoading = MutableStateFlow(false)
    val isOperationLoading: StateFlow<Boolean> = _isOperationLoading.asStateFlow()

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    private val _isPrivacyModeEnabled = MutableStateFlow(false)
    val isPrivacyModeEnabled: StateFlow<Boolean> = _isPrivacyModeEnabled.asStateFlow()

    private var lastRefreshTime = 0L
    private val refreshThreshold = 30_000L

    private var subscriptionJob: Job? = null
    private var priceRefreshJob: Job? = null
    private var lastPrices: Map<String, Double> = emptyMap()

    init {
        observeWallets()
        observePrivacyMode()
        observeSelectedCurrency()
        observeSignals()
        startPriceTimer()
    }

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
        val pricesResult = getSimplePricesUseCase(allSymbols, SupportedCurrency.USD)
        if (pricesResult is Result.Success) {
            lastPrices = pricesResult.data
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSignals() {
        viewModelScope.launch {
            subscriptionService.addressChanges
                .debounce(1500L)
                .collect { network ->
                    logger.d(TAG, "Surgical Reactive Signal: ${network.name}. Syncing...")
                    refresh(force = true, networkFilter = network)
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
            settingsRepository.observeSelectedCurrency().collect {
                refresh(force = true)
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
                    val previousWallets = (previousState as? Result.Success)?.data ?: emptyList()
                    if (walletsList.isNotEmpty() && walletsList.size > previousWallets.size) {
                        refresh(force = true)
                    }
                    updateSubscriptions(walletsList)
                }
        }
    }

    private fun updateSubscriptions(wallets: List<Wallet>) {
        subscriptionJob?.cancel()
        subscriptionJob = viewModelScope.launch {
            wallets.forEach { wallet ->
                wallet.bitcoinCoins.forEach {
                    subscriptionService.subscribeToAddressChanges(
                        it.address,
                        it.network
                    )
                }
                wallet.solanaCoins.forEach {
                    subscriptionService.subscribeToAddressChanges(
                        it.address,
                        it.network
                    )
                }
                wallet.evmTokens.forEach {
                    subscriptionService.subscribeToAddressChanges(
                        it.address,
                        it.network
                    )
                }
            }
        }
    }

    fun refresh(force: Boolean = false, networkFilter: Network? = null) {
        val currentTime = System.currentTimeMillis()
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
                val prices = if (lastPrices.isEmpty() || force) {
                    val allSymbols = currentWallets.flatMap { wallet ->
                        wallet.bitcoinCoins.map { it.symbol } +
                                wallet.solanaCoins.map { it.symbol } +
                                wallet.evmTokens.map { it.symbol }
                    }.distinct()
                    val pricesResult = getSimplePricesUseCase(allSymbols, SupportedCurrency.USD)
                    if (pricesResult is Result.Success) {
                        lastPrices = pricesResult.data
                        pricesResult.data
                    } else {
                        lastPrices
                    }
                } else {
                    lastPrices
                }

                val allErrors = mutableListOf<ChainSyncError>()

                coroutineScope {
                    currentWallets.map { wallet ->
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

                            if (networkFilter == null || networkFilter is BitcoinNetwork) {
                                wallet.bitcoinCoins
                                    .filter { networkFilter == null || it.network == networkFilter }
                                    .forEach { coin ->
                                        _syncingNetworks.update { it + coin.network }
                                        val result = syncBitcoinBalanceUseCase(
                                            wallet.id,
                                            coin,
                                            prices[coin.symbol] ?: 0.0,
                                            saveToCache = false
                                        )
                                        if (result is Result.Success) {
                                            btcBalances[coin.network] = result.data!!
                                        } else if (result is Result.Error) {
                                            walletErrors.add(
                                                ChainSyncError(
                                                    coin.network,
                                                    result.message,
                                                    coin.symbol
                                                )
                                            )
                                        }
                                        _syncingNetworks.update { it - coin.network }
                                    }
                            }

                            if (networkFilter == null || networkFilter is SolanaNetwork) {
                                wallet.solanaCoins
                                    .filter { networkFilter == null || it.network == networkFilter }
                                    .forEach { coin ->
                                        _syncingNetworks.update { it + coin.network }
                                        val result = syncSolanaBalanceUseCase(
                                            wallet.id,
                                            coin,
                                            prices[coin.symbol] ?: 0.0,
                                            saveToCache = false
                                        )
                                        if (result is Result.Success) {
                                            solBalances[coin.network] = result.data!!
                                        } else if (result is Result.Error) {
                                            walletErrors.add(
                                                ChainSyncError(
                                                    coin.network,
                                                    result.message,
                                                    coin.symbol
                                                )
                                            )
                                        }
                                        _syncingNetworks.update { it - coin.network }
                                    }
                            }

                            if (wallet.evmTokens.isNotEmpty() && (networkFilter == null || networkFilter is EthereumNetwork)) {
                                val evmNetworks = wallet.evmTokens.map { it.network }.toSet()
                                _syncingNetworks.update { it + evmNetworks }
                                val result = syncEVMBalancesUseCase(
                                    wallet.id,
                                    wallet.evmTokens,
                                    prices,
                                    saveToCache = false
                                )
                                if (result is Result.Success) {
                                    evmMap.putAll(result.data)
                                } else if (result is Result.Error) {
                                    evmNetworks.forEach { network ->
                                        walletErrors.add(
                                            ChainSyncError(
                                                network,
                                                result.message,
                                                "EVM"
                                            )
                                        )
                                    }
                                }
                                _syncingNetworks.update { it - evmNetworks }
                            }

                            val existing = walletRepository.getWalletBalance(wallet.id)
                            val newBalance = WalletBalance(
                                walletId = wallet.id,
                                lastUpdated = System.currentTimeMillis(),
                                bitcoinBalances = existing?.bitcoinBalances?.toMutableMap()?.apply {
                                    if (networkFilter == null || networkFilter is BitcoinNetwork) putAll(
                                        btcBalances
                                    )
                                } ?: btcBalances,
                                solanaBalances = existing?.solanaBalances?.toMutableMap()?.apply {
                                    if (networkFilter == null || networkFilter is SolanaNetwork) putAll(
                                        solBalances
                                    )
                                } ?: solBalances,
                                evmBalances = if (networkFilter == null || networkFilter is EthereumNetwork) {
                                    existing?.evmBalances?.toMutableMap()?.apply { putAll(evmMap) }
                                        ?: evmMap
                                } else {
                                    existing?.evmBalances ?: emptyMap()
                                }
                            )
                            walletRepository.saveWalletBalance(newBalance)
                            walletErrors
                        }
                    }.awaitAll().forEach { errors -> allErrors.addAll(errors) }
                }

                if (allErrors.isNotEmpty()) {
                    val errorString =
                        allErrors.distinctBy { "${it.network.name}-${it.assetSymbol}" }
                            .joinToString("\n") { error -> "• ${error.assetSymbol?.let { "$it on " } ?: ""}${error.network.name}: ${error.message}" }
                    _operationError.update { if (it != null) "$it\n\n$errorString" else "Partial sync failure:\n$errorString" }
                }
            }
            _isRefreshing.update { false }
        }
    }

    fun deleteWallet(walletId: String) {
        viewModelScope.launch {
            _isOperationLoading.update { true }
            runCatching { walletRepository.deleteWallet(walletId) }
            _isOperationLoading.update { false }
        }
    }

    fun renameWallet(walletId: String, newName: String) {
        viewModelScope.launch {
            _isOperationLoading.update { true }
            runCatching { walletRepository.updateWalletName(walletId, newName) }
            _isOperationLoading.update { false }
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
