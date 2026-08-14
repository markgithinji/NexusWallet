package com.example.nexuswallet.feature.wallet.ui.walletdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.domain.model.Transaction
import com.example.nexuswallet.feature.core.service.BlockchainSubscriptionService
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.market.domain.repository.MarketRepository
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.ChainSyncError
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.Network
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    init {
        observeSelectedCurrency()
        observeSignals()
    }

    private fun observeSignals() {
        viewModelScope.launch {
            subscriptionService.addressChanges
                .debounce(2000L)
                .collect { network ->
                    val currentState = _uiState.value
                    if (currentState.wallet != null && !currentState.isRefreshingBalance) {
                        logger.d(
                            TAG,
                            "Surgical Reactive Signal: ${network.name}. Syncing detail..."
                        )
                        refresh(networkFilter = network)
                    }
                }
        }
    }

    private fun observeSelectedCurrency() {
        viewModelScope.launch {
            settingsRepository.observeSelectedCurrency().collect {
                if (_uiState.value.wallet != null) refresh()
            }
        }
    }

    fun loadWallet(walletId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.wallet == null,
                    error = null,
                    hasSyncError = false
                )
            }
            runCatching {
                val loadedWallet = walletRepository.getWallet(walletId)
                    ?: throw IllegalStateException("Wallet not found")
                _uiState.update { it.copy(wallet = loadedWallet) }
                registerSubscriptions(loadedWallet)
                observeWalletBalance(walletId)
                launch { loadMarketPercentages() }
                observeTransactions(walletId, loadedWallet, forceRefresh = false)
                refreshBalanceInBackground(loadedWallet, syncTransactions = true)
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
                    _uiState.update { it.copy(balance = loadedBalance, isLoadingBalance = false) }
                    updateAssets()
                }
        }
    }

    private suspend fun loadMarketPercentages() {
        when (val res = marketRepository.getLatestPricePercentages(SupportedCurrency.USD)) {
            is Result.Success -> _uiState.update { it.copy(pricePercentages = res.data) }
            else -> _uiState.update { it.copy(pricePercentages = emptyMap()) }
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
            val symbols =
                (wallet.bitcoinCoins.map { it.symbol } + wallet.solanaCoins.map { it.symbol } + wallet.evmTokens.map { it.symbol }).distinct()
            val pricesRes = getSimplePricesUseCase(symbols, SupportedCurrency.USD)
            val prices = if (pricesRes is Result.Success) pricesRes.data else emptyMap()

            val allErrors = mutableListOf<ChainSyncError>()
            val btcBalances = mutableMapOf<BitcoinNetwork, BitcoinBalance>()
            val solBalances = mutableMapOf<SolanaNetwork, SolanaBalance>()
            val evmBalances = mutableMapOf<String, EVMBalance>()

            coroutineScope {
                val btcDef = if (networkFilter == null || networkFilter is BitcoinNetwork) {
                    wallet.bitcoinCoins.map { coin ->
                        async {
                            _uiState.update { it.copy(syncingNetworks = it.syncingNetworks + coin.network) }
                            val res = syncBitcoinBalanceUseCase(
                                wallet.id,
                                coin,
                                prices[coin.symbol] ?: 0.0,
                                saveToCache = false
                            )
                            _uiState.update { it.copy(syncingNetworks = it.syncingNetworks - coin.network) }
                            coin.network to res
                        }
                    }
                } else emptyList()

                val solDef = if (networkFilter == null || networkFilter is SolanaNetwork) {
                    wallet.solanaCoins.map { coin ->
                        async {
                            _uiState.update { it.copy(syncingNetworks = it.syncingNetworks + coin.network) }
                            val res = syncSolanaBalanceUseCase(
                                wallet.id,
                                coin,
                                prices[coin.symbol] ?: 0.0,
                                saveToCache = false
                            )
                            _uiState.update { it.copy(syncingNetworks = it.syncingNetworks - coin.network) }
                            coin.network to res
                        }
                    }
                } else emptyList()

                val evmDef =
                    if (wallet.evmTokens.isNotEmpty() && (networkFilter == null || networkFilter is EthereumNetwork)) {
                        async {
                            val evmNets = wallet.evmTokens.map { it.network }.toSet()
                            _uiState.update { it.copy(syncingNetworks = it.syncingNetworks + evmNets) }
                            val res = syncEVMBalancesUseCase(
                                wallet.id,
                                wallet.evmTokens,
                                prices,
                                saveToCache = false
                            )
                            _uiState.update { it.copy(syncingNetworks = it.syncingNetworks - evmNets) }
                            res
                        }
                    } else null

                btcDef.awaitAll().forEach { (net, res) ->
                    if (res is Result.Success) res.data?.let { btcBalances[net] = it }
                    else if (res is Result.Error) allErrors.add(
                        ChainSyncError(
                            net,
                            res.message,
                            "BTC"
                        )
                    )
                }
                solDef.awaitAll().forEach { (net, res) ->
                    if (res is Result.Success) res.data?.let { solBalances[net] = it }
                    else if (res is Result.Error) allErrors.add(
                        ChainSyncError(
                            net,
                            res.message,
                            "SOL"
                        )
                    )
                }
                evmDef?.await()?.let { res ->
                    if (res is Result.Success) evmBalances.putAll(res.data)
                    else if (res is Result.Error) wallet.evmTokens.map { it.network }.distinct()
                        .forEach { allErrors.add(ChainSyncError(it, res.message, "EVM")) }
                }
            }

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
                evmBalances = if (networkFilter == null || networkFilter is EthereumNetwork) existing?.evmBalances?.toMutableMap()
                    ?.apply { putAll(evmBalances) } ?: evmBalances else existing?.evmBalances
                    ?: emptyMap()
            )
            walletRepository.saveWalletBalance(newBalance)

            if (syncTransactions) observeTransactions(
                wallet.id,
                wallet,
                forceRefresh = true,
                networkFilter = networkFilter
            )
            _uiState.update {
                it.copy(
                    hasSyncError = allErrors.isNotEmpty(),
                    syncErrorMessage = if (allErrors.isNotEmpty()) allErrors.joinToString { e -> "${e.assetSymbol}: ${e.message}" } else null,
                    syncErrors = allErrors,
                    isRefreshingBalance = false,
                    lastBalanceSyncTime = System.currentTimeMillis())
            }
        }
    }

    private fun observeTransactions(
        walletId: String,
        wallet: Wallet,
        forceRefresh: Boolean = false,
        networkFilter: Network? = null
    ) {
        transactionsJob?.cancel()
        transactionsJob = viewModelScope.launch {
            if (forceRefresh) _uiState.update { it.copy(isRefreshingTransactions = true) }
            else if (_uiState.value.transactions.isEmpty()) _uiState.update {
                it.copy(
                    isLoadingTransactions = true
                )
            }

            getAllTransactionsUseCase(walletId, forceRefresh, networkFilter)
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
                .collect { updated ->
                    val display = formatTransactionList(updated, wallet)
                    _uiState.update {
                        it.copy(
                            transactions = display,
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
            val assets = formatBalanceUseCase(
                wallet.id,
                wallet,
                state.balance,
                state.pricePercentages,
                settingsRepository.getSelectedCurrency(),
                settingsRepository.getUsdToRate()
            )
            _uiState.update {
                it.copy(
                    assets = assets,
                    totalBalance = assets.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.usdValue) })
            }
        }
    }

    private fun formatTransactionList(
        txs: List<Transaction>,
        wallet: Wallet
    ): List<TransactionDisplayInfo> =
        txs.map { formatTransactionDisplayUseCase(it, findCoinForTransaction(it, wallet)) }

    private fun findCoinForTransaction(tx: Transaction, wallet: Wallet): Coin = when (tx) {
        is BitcoinTransaction -> wallet.bitcoinCoins.find { it.network == tx.network }
            ?: error("No Bitcoin coin")

        is SolanaTransaction -> wallet.solanaCoins.find { it.network == tx.network }
            ?: error("No Solana coin")

        is NativeETHTransaction -> wallet.evmTokens.find { it is NativeETH && it.network == tx.network }
            ?: error("No NativeETH")

        is TokenTransaction -> wallet.evmTokens.find { it.network == tx.network && it.evmTokenType == tx.evmTokenType }
            ?: error("No token")
    }

    fun getWalletName(): String = _uiState.value.wallet?.name ?: "Wallet Details"
    fun refresh(networkFilter: Network? = null) {
        _uiState.value.wallet?.let {
            refreshBalanceInBackground(
                it,
                syncTransactions = true,
                networkFilter = networkFilter
            )
        }
    }

    fun renameWallet(newName: String) {
        val cur = _uiState.value.wallet ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                walletRepository.updateWalletName(cur.id, newName)
                _uiState.update {
                    it.copy(
                        wallet = walletRepository.getWallet(cur.id),
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        error = "Failed: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private companion object {
        const val BALANCE_CACHE_TIME = 2 * 60 * 1000
    }
}
