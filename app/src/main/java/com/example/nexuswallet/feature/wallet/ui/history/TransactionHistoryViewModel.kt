package com.example.nexuswallet.feature.wallet.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.core.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.core.domain.model.TokenTransaction
import com.example.nexuswallet.feature.core.domain.model.Transaction
import com.example.nexuswallet.feature.wallet.domain.model.*
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.logging.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val getAllTransactionsUseCase: GetAllTransactionsUseCase,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
    private val logger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val tag = "TransactionHistoryVM"
    private val _uiState = MutableStateFlow(TransactionHistoryState())
    val uiState: StateFlow<TransactionHistoryState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadTransactions(walletId: String, coin: Coin? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { 
                it.copy(isLoading = true, error = null) 
            }

            val wallet = walletRepository.getWallet(walletId)
            if (wallet == null) {
                logger.e(tag, "Wallet not found: $walletId")
                _uiState.update { it.copy(isLoading = false, error = "Wallet not found") }
                return@launch
            }

            getAllTransactionsUseCase(walletId, forceRefresh = true)
                .flowOn(ioDispatcher)
                .conflate() // DROP rapid intermediate updates during sync
                .distinctUntilChanged() // Only proceed if the list content changed
                .catch { e ->
                    logger.e(tag, "Error in transaction flow", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { allTransactions ->
                    val filteredTransactions = if (coin != null) {
                        allTransactions.filter { transaction ->
                            isTransactionForCoin(transaction, coin)
                        }
                    } else {
                        allTransactions
                    }
                    
                    val displayTransactions = filteredTransactions.map { transaction ->
                        val txCoin = findCoinForTransaction(transaction, wallet)
                        formatTransactionDisplayUseCase(transaction, txCoin)
                    }

                    // Only update if the list is different to prevent unnecessary recompositions
                    _uiState.update { currentState ->
                        if (currentState.transactions == displayTransactions && !currentState.isLoading) {
                            currentState
                        } else {
                            currentState.copy(
                                transactions = displayTransactions,
                                isLoading = false
                            )
                        }
                    }
                }
        }
    }

    private fun isTransactionForCoin(transaction: Transaction, coin: Coin): Boolean {
        return when (transaction) {
            is BitcoinTransaction -> coin is BitcoinCoin && transaction.network == coin.network
            is SolanaTransaction -> coin is SolanaCoin && transaction.network == coin.network
            is NativeETHTransaction -> coin is NativeETH && transaction.network == coin.network
            is TokenTransaction -> coin is EVMToken && transaction.network == coin.network && transaction.evmTokenType == coin.evmTokenType
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
                wallet.evmTokens.find {
                    it.network == transaction.network && it.evmTokenType == transaction.evmTokenType
                } ?: error("No token found for ${transaction.evmTokenType} on ${transaction.network.name}")
            }
        }
    }
}
