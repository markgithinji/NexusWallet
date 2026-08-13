package com.example.nexuswallet.feature.wallet.ui.walletdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.domain.model.Transaction
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.formatCurrency
import com.example.nexuswallet.feature.market.domain.repository.MarketRepository
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.ChainSyncError
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatBalanceUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncBitcoinBalanceUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncEVMBalancesUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncSolanaBalanceUseCase
import com.example.nexuswallet.feature.core.service.BlockchainSubscriptionService
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.Network
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
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
    private val settingsRepository: SettingsRepository,
    private val subscriptionService: BlockchainSubscriptionService,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val TAG = "WalletDetailVM"
    private val _uiState = MutableStateFlow(WalletDetailUiState(isLoading = true))
    val uiState: StateFlow<WalletDetailUiState> = _uiState.asStateFlow()

    private var transactionsJob: Job? = null
    private var balanceWatchdogJob: Job? = null

    init {
        observeSelectedCurrency()
        observeSignals()
        startBalanceWatchdog()
    }

    /**
     * Periodically refreshes all balances to ensure data is fresh even if WSS signals are missed
     * or for networks like Bitcoin where WSS is currently disabled.
     */
    private fun startBalanceWatchdog() {
        balanceWatchdogJob?.cancel()
        balanceWatchdogJob = viewModelScope.launch {
            while (true) {
                delay(300_000L) // Refresh every 5 minutes
                _uiState.value.wallet?.let {
                    logger.d(TAG, "Watchdog: Periodic balance refresh triggered.")
                    refresh() // Full refresh (no networkFilter)
                }
            }
        }
    }

    /**
     * Listens for real-time blockchain activity signals.
     * Only refreshes the specific network that signaled a change to save API quota.
     */
    private fun observeSignals() {
        viewModelScope.launch {
            subscriptionService.addressChanges
                .debounce(2000L) // Prevent rapid redundant refreshes
                .collect { network ->
                    if (_uiState.value.wallet != null && !_uiState.value.isRefreshingBalance) {
                        logger.d(TAG, "Reactive Signal: ${network.name} activity. Performing surgical refresh.")
                        refresh(networkFilter = network)
                    }
                }
        }
    }

    private fun observeSelectedCurrency() {
        viewModelScope.launch {
            settingsRepository.observeSelectedCurrency().collect { currency ->
                // If we have a wallet, refresh to get new prices in USD
                if (_uiState.value.wallet != null) {
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

                // STEP 2: Register subscriptions for real-time updates
                registerSubscriptions(loadedWallet)

                // STEP 3: Observe balance reactively from local DB
                observeWalletBalance(walletId)

                // STEP 4: Load market percentages
                launch {
                    loadMarketPercentages()
                }

                // STEP 5: Observe transactions
                observeTransactions(walletId, loadedWallet, forceRefresh = false)

                // STEP 6: Refresh balance if needed
                if (!isBalanceFresh) {
                    refreshBalanceInBackground(loadedWallet, syncTransactions = true)
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

    private fun registerSubscriptions(wallet: Wallet) {
        logger.d(TAG, "Registering WebSocket subscriptions for wallet: ${wallet.id}")
        wallet.bitcoinCoins.forEach { subscriptionService.subscribeToAddressChanges(it.address, it.network) }
        wallet.solanaCoins.forEach { subscriptionService.subscribeToAddressChanges(it.address, it.network) }
        wallet.evmTokens.forEach { subscriptionService.subscribeToAddressChanges(it.address, it.network) }
    }

    private fun observeWalletBalance(walletId: String) {
        _uiState.update { it.copy(isLoadingBalance = true) }

        viewModelScope.launch {
            walletRepository.observeWalletBalance(walletId)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            balanceError = "Failed to load balance: ${e.message}",
                            isLoadingBalance = false
                        )
                    }
                }
                .collect { loadedBalance ->
                    _uiState.update {
                        it.copy(
                            balance = loadedBalance,
                            isLoadingBalance = false,
                            lastBalanceSyncTime = System.currentTimeMillis()
                        )
                    }
                    updateAssets()
                }
        }
    }

    private suspend fun loadMarketPercentages() {
        // ALWAYS fetch market percentages in USD for consistency in the database base values
        when (val percentagesResult =
            marketRepository.getLatestPricePercentages(SupportedCurrency.USD)) {
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

    private fun refreshBalanceInBackground(
        wallet: Wallet,
        syncTransactions: Boolean = false,
        networkFilter: Network? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingBalance = true) }

            // 1. Fetch prices in USD as the base currency for all display conversions
            val symbols = (wallet.bitcoinCoins.map { it.symbol } +
                    wallet.solanaCoins.map { it.symbol } +
                    wallet.evmTokens.map { it.symbol }).distinct()

            val pricesResult = getSimplePricesUseCase(symbols, SupportedCurrency.USD)
            val prices = if (pricesResult is Result.Success) {
                pricesResult.data
            } else {
                emptyMap()
            }

            // 2. Sync balances in parallel, respecting the network filter
            val allErrors = mutableListOf<ChainSyncError>()
            val btcBalances = mutableMapOf<BitcoinNetwork, BitcoinBalance>()
            val solBalances = mutableMapOf<SolanaNetwork, SolanaBalance>()
            val evmBalances = mutableMapOf<String, EVMBalance>()

            coroutineScope {
                // Bitcoin: Only if no filter or Bitcoin signal (currently disabled in WSS)
                val btcDeferred = if (networkFilter == null || networkFilter is BitcoinNetwork) {
                    wallet.bitcoinCoins.map { coin ->
                        async {
                            _uiState.update { it.copy(syncingNetworks = it.syncingNetworks + coin.network) }
                            val result = syncBitcoinBalanceUseCase(
                                wallet.id,
                                coin,
                                prices[coin.symbol] ?: 0.0,
                                saveToCache = false
                            )
                            _uiState.update { it.copy(syncingNetworks = it.syncingNetworks - coin.network) }
                            coin.network to result
                        }
                    }
                } else emptyList()

                // Solana: Only if no filter or Solana signal
                val solDeferred = if (networkFilter == null || networkFilter is SolanaNetwork) {
                    wallet.solanaCoins.map { coin ->
                        async {
                            _uiState.update { it.copy(syncingNetworks = it.syncingNetworks + coin.network) }
                            val result = syncSolanaBalanceUseCase(
                                wallet.id,
                                coin,
                                prices[coin.symbol] ?: 0.0,
                                saveToCache = false
                            )
                            _uiState.update { it.copy(syncingNetworks = it.syncingNetworks - coin.network) }
                            coin.network to result
                        }
                    }
                } else emptyList()

                // EVM: Only if no filter or Ethereum signal
                val evmDeferred = if (wallet.evmTokens.isNotEmpty() && (networkFilter == null || networkFilter is EthereumNetwork)) {
                    async {
                        val evmNetworks = wallet.evmTokens.map { it.network }.toSet()
                        _uiState.update { it.copy(syncingNetworks = it.syncingNetworks + evmNetworks) }
                        val result = syncEVMBalancesUseCase(
                            wallet.id,
                            wallet.evmTokens,
                            prices,
                            saveToCache = false
                        )
                        _uiState.update { it.copy(syncingNetworks = it.syncingNetworks - evmNetworks) }
                        result
                    }
                } else null

                // Collect BTC results
                btcDeferred.awaitAll().forEach { (network, result) ->
                    when (result) {
                        is Result.Success -> result.data?.let { btcBalances[network] = it }
                        is Result.Error -> allErrors.add(ChainSyncError(network, result.message, "BTC"))
                        else -> {}
                    }
                }

                // Collect Solana results
                solDeferred.awaitAll().forEach { (network, result) ->
                    when (result) {
                        is Result.Success -> result.data?.let { solBalances[network] = it }
                        is Result.Error -> allErrors.add(ChainSyncError(network, result.message, "SOL"))
                        else -> {}
                    }
                }

                // Collect EVM results
                evmDeferred?.await()?.let { result ->
                    when (result) {
                        is Result.Success -> evmBalances.putAll(result.data)
                        is Result.Error -> {
                            wallet.evmTokens.map { it.network }.distinct().forEach { network ->
                                allErrors.add(ChainSyncError(network, result.message, "EVM"))
                            }
                        }
                        else -> {}
                    }
                }
            }

            // ATOMIC MERGE: Merge the new chain balance with the existing cached balances for other chains.
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
                        putAll(evmBalances)
                    } ?: evmBalances
                } else {
                    existing?.evmBalances ?: emptyMap()
                }
            )
            walletRepository.saveWalletBalance(newBalance)

            // After balances are updated in local DB, trigger transaction sync if requested
            if (syncTransactions) {
                observeTransactions(wallet.id, wallet, forceRefresh = true)
            }

            if (allErrors.isEmpty()) {
                _uiState.update {
                    it.copy(
                        hasSyncError = false,
                        syncErrorMessage = null,
                        syncErrors = emptyList(),
                        isRefreshingBalance = false,
                        lastBalanceSyncTime = System.currentTimeMillis()
                    )
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

        viewModelScope.launch {
            val currency = settingsRepository.getSelectedCurrency()
            val rate = settingsRepository.getUsdToRate()

            val assets = formatBalanceUseCase(
                walletId = wallet.id,
                wallet = wallet,
                balance = state.balance,
                pricePercentages = state.pricePercentages,
                currency = currency,
                usdToRate = rate
            )

            val totalUsd = assets.fold(BigDecimal.ZERO) { acc, asset -> acc.add(asset.usdValue) }

            _uiState.update {
                it.copy(
                    assets = assets,
                    totalBalance = totalUsd
                )
            }
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

    fun refresh(networkFilter: Network? = null) {
        _uiState.value.wallet?.let { wallet ->
            refreshBalanceInBackground(wallet, syncTransactions = true, networkFilter = networkFilter)
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
                _uiState.update {
                    it.copy(
                        error = "Failed to rename wallet: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // Cache expiration times
    private companion object {
        const val BALANCE_CACHE_TIME = 2 * 60 * 1000      // 2 minutes
    }
}
