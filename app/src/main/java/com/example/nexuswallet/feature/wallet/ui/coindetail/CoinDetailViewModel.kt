package com.example.nexuswallet.feature.wallet.ui.coindetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinDetailResult
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumDetailResult
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.SolanaDetailResult
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetBitcoinDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetEthereumDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetSolanaDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncBitcoinBalanceUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncEVMBalancesUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncSolanaBalanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CoinDetailViewModel @Inject constructor(
    private val getBitcoinDetailUseCase: GetBitcoinDetailUseCase,
    private val getEthereumDetailUseCase: GetEthereumDetailUseCase,
    private val getSolanaDetailUseCase: GetSolanaDetailUseCase,
    private val syncBitcoinBalanceUseCase: SyncBitcoinBalanceUseCase,
    private val syncSolanaBalanceUseCase: SyncSolanaBalanceUseCase,
    private val syncEVMBalancesUseCase: SyncEVMBalancesUseCase,
    private val getSimplePricesUseCase: GetSimplePricesUseCase,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CoinDetailState())
    val state: StateFlow<CoinDetailState> = _state.asStateFlow()

    fun loadCoinDetails(
        walletId: String,
        coin: Coin,
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            // Set loading state with the coin
            _state.update {
                it.copy(
                    isLoading = !forceRefresh,
                    isRefreshing = forceRefresh,
                    error = null,
                    coin = coin,
                    walletId = walletId
                )
            }

            // Sync balance and prices in parallel if refreshing
            if (forceRefresh) {
                syncData(walletId, coin)
            }

            val result = when (coin) {
                is BitcoinCoin -> {
                    getBitcoinDetailUseCase(walletId, coin.network)
                }

                is EVMToken -> {
                    getEthereumDetailUseCase(walletId, coin)
                }

                is SolanaCoin -> {
                    getSolanaDetailUseCase(walletId, coin.network)
                }
            }

            // Update UI with result
            when (result) {
                is Result.Success -> {
                    when (val data = result.data) {
                        is BitcoinDetailResult -> updateStateWithBitcoinData(data)
                        is EthereumDetailResult -> updateStateWithEthereumData(data)
                        is SolanaDetailResult -> updateStateWithSolanaData(data)
                    }
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            error = result.message,
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                }

                Result.Loading -> {}
            }
        }
    }

    private fun updateStateWithBitcoinData(data: BitcoinDetailResult) {
        _state.update { currentState ->
            val coin = currentState.coin ?: return@update currentState

            val displayTransactions = data.rawTransactions.map { transaction ->
                formatTransactionDisplayUseCase(transaction, coin)
            }

            currentState.copy(
                walletId = data.walletId,
                address = data.address,
                balance = data.balance,
                balanceFormatted = data.balanceFormatted,
                usdValue = data.usdValue,
                transactions = displayTransactions,
                isLoading = false,
                isRefreshing = false
            )
        }
    }

    private fun updateStateWithEthereumData(data: EthereumDetailResult) {
        _state.update { currentState ->
            val coin = data.token

            val displayTransactions = data.rawTransactions.map { transaction ->
                formatTransactionDisplayUseCase(transaction, coin)
            }

            currentState.copy(
                walletId = data.walletId,
                address = data.address,
                balance = data.balance,
                balanceFormatted = data.balanceFormatted,
                usdValue = data.usdValue,
                transactions = displayTransactions,
                ethGasBalance = data.ethGasBalance,
                evmTokens = data.availableTokens,
                isLoading = false,
                isRefreshing = false,
                coin = coin
            )
        }
    }

    private fun updateStateWithSolanaData(data: SolanaDetailResult) {
        _state.update { currentState ->
            val coin = currentState.coin ?: return@update currentState

            val displayTransactions = data.rawTransactions.map { transaction ->
                formatTransactionDisplayUseCase(transaction, coin)
            }

            currentState.copy(
                walletId = data.walletId,
                address = data.address,
                balance = data.balance,
                balanceFormatted = data.balanceFormatted,
                usdValue = data.usdValue,
                transactions = displayTransactions,
                splTokens = data.splTokens,
                isLoading = false,
                isRefreshing = false
            )
        }
    }

    fun refresh() {
        val currentState = _state.value
        if (currentState.walletId.isNotEmpty() && currentState.coin != null) {
            loadCoinDetails(
                currentState.walletId,
                currentState.coin,
                forceRefresh = true
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun syncData(walletId: String, coin: Coin) {
        // ALWAYS fetch prices in USD for the database "usdValue" fields
        val pricesResult = getSimplePricesUseCase(listOf(coin.symbol), SupportedCurrency.USD)
        val prices = if (pricesResult is Result.Success) pricesResult.data else emptyMap()
        val currentPrice = prices[coin.symbol] ?: 0.0

        when (coin) {
            is BitcoinCoin -> {
                syncBitcoinBalanceUseCase(walletId, coin, currentPrice)
            }

            is SolanaCoin -> {
                syncSolanaBalanceUseCase(walletId, coin, currentPrice)
            }

            is EVMToken -> {
                syncEVMBalancesUseCase(walletId, listOf(coin), prices)
            }
        }
    }
}