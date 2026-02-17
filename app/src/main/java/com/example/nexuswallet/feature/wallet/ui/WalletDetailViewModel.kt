package com.example.nexuswallet.feature.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.domain.GetETHBalanceForGasUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.USDCSendTransaction
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.SyncWalletBalancesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val usdcTransactionRepository: USDCTransactionRepository,
    private val getETHBalanceForGasUseCase: GetETHBalanceForGasUseCase,
    private val syncWalletBalancesUseCase: SyncWalletBalancesUseCase
) : ViewModel() {

    // Current wallet
    private val _wallet = MutableStateFlow<Wallet?>(null)
    val wallet: StateFlow<Wallet?> = _wallet

    // Wallet balance
    private val _balance = MutableStateFlow<WalletBalance?>(null)
    val balance: StateFlow<WalletBalance?> = _balance

    // ETH balance for USDC wallet (for gas)
    private val _ethBalanceForGas = MutableStateFlow<BigDecimal?>(null)
    val ethBalanceForGas: StateFlow<BigDecimal?> = _ethBalanceForGas

    // Transactions by type
    private val _bitcoinTransactions = MutableStateFlow<List<BitcoinTransaction>>(emptyList())
    private val _ethereumTransactions = MutableStateFlow<List<EthereumTransaction>>(emptyList())
    private val _solanaTransactions = MutableStateFlow<List<SolanaTransaction>>(emptyList())
    private val _usdcTransactions = MutableStateFlow<List<USDCSendTransaction>>(emptyList())

    // Combined transactions for UI
    private val _transactions = MutableStateFlow<List<Any>>(emptyList())
    val transactions: StateFlow<List<Any>> = _transactions

    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Load wallet details
    fun loadWallet(walletId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // 1. Load wallet from repository
                val loadedWallet = walletRepository.getWallet(walletId)

                if (loadedWallet == null) {
                    _error.value = "Wallet not found"
                    _isLoading.value = false
                    return@launch
                }

                _wallet.value = loadedWallet

                // 2. Load wallet balance from repository
                val loadedBalance = walletRepository.getWalletBalance(walletId)
                _balance.value = loadedBalance

                // 3. Load transactions for each coin type
                loadAllTransactions(walletId)

                // 4. Load ETH balance for gas if USDC is enabled
                if (loadedWallet.usdc != null) {
                    loadETHGasBalance(walletId)
                }

                // 5. Sync balances in background using use case
                viewModelScope.launch {
                    syncWalletBalancesUseCase(loadedWallet)
                    // Refresh balance after sync
                    _balance.value = walletRepository.getWalletBalance(walletId)
                }

            } catch (e: Exception) {
                _error.value = "Failed to load wallet: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadAllTransactions(walletId: String) {
        viewModelScope.launch {
            // Load Bitcoin transactions
            bitcoinTransactionRepository.getTransactions(walletId).collect { txs ->
                _bitcoinTransactions.value = txs
                combineTransactions()
            }

            // Load Ethereum transactions
            ethereumTransactionRepository.getTransactions(walletId).collect { txs ->
                _ethereumTransactions.value = txs
                combineTransactions()
            }

            // Load Solana transactions
            solanaTransactionRepository.getTransactions(walletId).collect { txs ->
                _solanaTransactions.value = txs
                combineTransactions()
            }

            // Load USDC transactions
            usdcTransactionRepository.getTransactions(walletId).collect { txs ->
                _usdcTransactions.value = txs
                combineTransactions()
            }
        }
    }

    private fun combineTransactions() {
        val all = mutableListOf<Any>()
        all.addAll(_bitcoinTransactions.value)
        all.addAll(_ethereumTransactions.value)
        all.addAll(_solanaTransactions.value)
        all.addAll(_usdcTransactions.value)

        // Sort by timestamp descending (newest first)
        _transactions.value = all.sortedByDescending {
            when(it) {
                is BitcoinTransaction -> it.timestamp
                is EthereumTransaction -> it.timestamp
                is SolanaTransaction -> it.timestamp
                is USDCSendTransaction -> it.timestamp
                else -> 0L
            }
        }
    }

    private suspend fun loadETHGasBalance(walletId: String) {
        val result = getETHBalanceForGasUseCase(walletId)
        when (result) {
            is Result.Success -> _ethBalanceForGas.value = result.data
            is Result.Error -> {}
            Result.Loading -> {}
        }
    }

    fun getTotalUsdValue(): Double {
        val balance = _balance.value
        var total = 0.0

        balance?.bitcoin?.let { total += it.usdValue }
        balance?.ethereum?.let { total += it.usdValue }
        balance?.solana?.let { total += it.usdValue }
        balance?.usdc?.let { total += it.usdValue }

        return total
    }

    fun getWalletName(): String {
        return _wallet.value?.name ?: "Wallet Details"
    }

    fun getFullAddress(): String {
        val wallet = _wallet.value ?: return ""
        return wallet.ethereum?.address
            ?: wallet.bitcoin?.address
            ?: wallet.solana?.address
            ?: wallet.usdc?.address
            ?: ""
    }

    fun refresh() {
        _wallet.value?.let { wallet ->
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    // Use the use case to sync balances
                    syncWalletBalancesUseCase(wallet)
                    loadWallet(wallet.id)
                } catch (e: Exception) {
                    _error.value = "Refresh failed: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }
}