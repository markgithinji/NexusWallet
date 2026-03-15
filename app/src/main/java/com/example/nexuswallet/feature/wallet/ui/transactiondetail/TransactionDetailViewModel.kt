package com.example.nexuswallet.feature.wallet.ui.transactiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.util.Result
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
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val getTransactionDetailUseCase: GetTransactionDetailUseCase,
    private val formatTransactionDetailDisplayUseCase: FormatTransactionDetailDisplayUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionDetailState())
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TransactionDetailEffect>()
    val effects: SharedFlow<TransactionDetailEffect> = _effects.asSharedFlow()

    sealed class TransactionDetailEffect {
        data class ShowError(val message: String) : TransactionDetailEffect()
        data class CopyToClipboard(val text: String, val label: String) : TransactionDetailEffect()
        object ShareTransaction : TransactionDetailEffect()
        data class OpenExplorer(val url: String) : TransactionDetailEffect()
    }

    fun loadTransactionDetail(
        walletId: String,
        transactionId: String
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (val result = getTransactionDetailUseCase(walletId, transactionId)) {
                is Result.Success -> {
                    val transaction = result.data

                    val displayInfo = formatTransactionDetailDisplayUseCase(transaction)

                    val usdValue = calculateUSDValue(transaction)
                    val formattedUsd = NumberFormat.getCurrencyInstance(Locale.US).format(usdValue)

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

    private fun calculateUSDValue(transaction: TransactionDetail): Double {
        val amount = transaction.amount.toDoubleOrNull() ?: 0.0

        val mockPrices = mapOf(
            "bitcoin" to 65000.0,
            "ethereum" to 3500.0,
            "solana" to 150.0,
            "usd-coin" to 1.0
        )

        val priceKey = when (transaction.coinType) {
            CoinType.BITCOIN -> "bitcoin"
            CoinType.ETHEREUM -> "ethereum"
            CoinType.SOLANA -> "solana"
            CoinType.USDC -> "usd-coin"
            else -> transaction.tokenSymbol?.lowercase() ?: ""
        }

        val price = mockPrices[priceKey] ?: 0.0
        return amount * price
    }

    fun refresh() {
        val currentState = _state.value
        currentState.transaction?.let { tx ->
            loadTransactionDetail(tx.walletId, tx.id)
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
                val url = ExplorerUrlHelper.getExplorerUrl(tx.hash, tx.network)
                _effects.emit(TransactionDetailEffect.OpenExplorer(url))
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}