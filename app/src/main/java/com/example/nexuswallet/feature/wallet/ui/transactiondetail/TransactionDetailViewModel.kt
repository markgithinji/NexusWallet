package com.example.nexuswallet.feature.wallet.ui.transactiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.core.util.formatCurrency
import com.example.nexuswallet.feature.market.domain.usecase.GetSimplePricesUseCase
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDetail
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatTransactionDetailDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetTransactionDetailUseCase
import com.example.nexuswallet.feature.wallet.util.ExplorerUrlHelper
import com.example.nexuswallet.feature.wallet.util.TransactionFormatHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val getTransactionDetailUseCase: GetTransactionDetailUseCase,
    private val formatTransactionDetailDisplayUseCase: FormatTransactionDetailDisplayUseCase,
    private val getSimplePricesUseCase: GetSimplePricesUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionDetailState())
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TransactionDetailEffect>()
    val effects: SharedFlow<TransactionDetailEffect> = _effects.asSharedFlow()

    fun loadTransactionDetail(
        walletId: String,
        transactionId: String,
        coin: Coin
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (val result = getTransactionDetailUseCase(walletId, transactionId, coin)) {
                is Result.Success -> {
                    val transaction = result.data

                    val displayInfo = formatTransactionDetailDisplayUseCase(transaction)

                    // Get real price
                    val currency = settingsRepository.getSelectedCurrency()
                    val priceResult = getSimplePricesUseCase(listOf(transaction.coin.symbol), currency)
                    val price = if (priceResult is Result.Success) {
                        priceResult.data[transaction.coin.symbol] ?: 0.0
                    } else 0.0

                    val amount = transaction.amount.toDoubleOrNull() ?: 0.0
                    val usdValue = amount * price
                    val formattedUsd = usdValue.formatCurrency(currency)

                    _state.update {
                        it.copy(
                            transaction = transaction,
                            formattedAmount = displayInfo.formattedAmount,
                            formattedFee = TransactionFormatHelper.formatAmount(transaction.fee),
                            formattedTime = displayInfo.formattedTime,
                            usdValue = usdValue,
                            formattedUsd = formattedUsd,
                            isLoading = false
                        )
                    }
                }

                is Result.Error -> {
                    _state.update {
                        it.copy(
                            error = result.message,
                            isLoading = false
                        )
                    }
                    _effects.emit(TransactionDetailEffect.ShowError(result.message))
                }

                Result.Loading -> {}
            }
        }
    }

    fun refresh() {
        val currentState = _state.value
        currentState.transaction?.let { tx ->
            loadTransactionDetail(tx.walletId, tx.id, tx.coin)
        }
    }

    fun copyToClipboard(text: String, label: String) {
        viewModelScope.launch {
            _effects.emit(TransactionDetailEffect.CopyToClipboard(text, label))
        }
    }

    fun shareTransaction() {
        viewModelScope.launch {
            _effects.emit(TransactionDetailEffect.ShareTransaction)
        }
    }

    fun openInExplorer() {
        viewModelScope.launch {
            _state.value.transaction?.let { tx ->
                val url = ExplorerUrlHelper.getExplorerUrl(tx.hash, tx.coin.network)
                _effects.emit(TransactionDetailEffect.OpenExplorer(url))
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}