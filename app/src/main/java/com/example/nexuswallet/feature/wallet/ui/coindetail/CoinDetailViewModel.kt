package com.example.nexuswallet.feature.wallet.ui.coindetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.util.Result
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
import com.example.nexuswallet.feature.wallet.util.TransactionFormatHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class CoinDetailViewModel @Inject constructor(
    private val getBitcoinDetailUseCase: GetBitcoinDetailUseCase,
    private val getEthereumDetailUseCase: GetEthereumDetailUseCase,
    private val getSolanaDetailUseCase: GetSolanaDetailUseCase,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase
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

            // Format ETH gas balance
            val formattedEthGasBalance = data.ethGasBalance?.let { ethGas ->
                try {
                    val ethAmount = if (ethGas > BigDecimal("1000000000000000")) {
                        // Convert from Wei to ETH
                        ethGas.divide(BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP)
                    } else {
                        ethGas
                    }

                    // Use the helper to format the amount
                    TransactionFormatHelper.formatAmount(ethAmount.toString())
                } catch (e: Exception) {
                    ethGas.toString()
                }
            }

            currentState.copy(
                walletId = data.walletId,
                address = data.address,
                balance = data.balance,
                balanceFormatted = data.balanceFormatted,
                usdValue = data.usdValue,
                transactions = displayTransactions,
                ethGasBalance = data.ethGasBalance,
                formattedEthGasBalance = formattedEthGasBalance ?: "0",
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
}