package com.example.nexuswallet.feature.wallet.ui


import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository

import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction


import com.example.nexuswallet.feature.coin.usdc.domain.GetETHBalanceForGasUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCBalanceUseCase
import com.example.nexuswallet.feature.wallet.domain.ChainType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.domain.USDCSendTransaction
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import androidx.compose.runtime.collectAsState
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import kotlinx.coroutines.flow.firstOrNull

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val usdcTransactionRepository: USDCTransactionRepository,
    private val getUSDCBalanceUseCase: GetUSDCBalanceUseCase,
    private val getETHBalanceForGasUseCase: GetETHBalanceForGasUseCase
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

            Log.d("WalletDetailVM", "========== LOAD WALLET START ==========")
            Log.d("WalletDetailVM", "Attempting to load wallet with ID: '$walletId'")

            try {
                // 1. Load wallet from repository
                Log.d("WalletDetailVM", "Calling walletRepository.getWallet($walletId)")
                val loadedWallet = walletRepository.getWallet(walletId)

                if (loadedWallet == null) {
                    Log.e("WalletDetailVM", " walletRepository.getWallet returned NULL for ID: $walletId")

                    // List all available wallets to see what's in the database
                    try {
                        val allWallets = walletRepository.getAllWallets().firstOrNull()
                        if (allWallets.isNullOrEmpty()) {
                            Log.e("WalletDetailVM", "No wallets found in repository at all")
                        } else {
                            Log.d("WalletDetailVM", "Available wallet IDs in repository: ${allWallets.map { it.id }}")
                            Log.d("WalletDetailVM", "Available wallet names: ${allWallets.map { it.name }}")
                        }
                    } catch (e: Exception) {
                        Log.e("WalletDetailVM", "Error getting all wallets: ${e.message}")
                    }

                    _error.value = "Wallet not found"
                    _isLoading.value = false
                    return@launch
                }

                Log.d("WalletDetailVM", " Wallet loaded successfully:")
                Log.d("WalletDetailVM", "   - ID: ${loadedWallet.id}")
                Log.d("WalletDetailVM", "   - Name: ${loadedWallet.name}")
                Log.d("WalletDetailVM", "   - Created: ${loadedWallet.createdAt}")
                Log.d("WalletDetailVM", "   - Has Bitcoin: ${loadedWallet.bitcoin != null}")
                Log.d("WalletDetailVM", "   - Has Ethereum: ${loadedWallet.ethereum != null}")
                Log.d("WalletDetailVM", "   - Has Solana: ${loadedWallet.solana != null}")
                Log.d("WalletDetailVM", "   - Has USDC: ${loadedWallet.usdc != null}")

                _wallet.value = loadedWallet
                Log.d("WalletDetailVM", "Wallet assigned to StateFlow")

                // 2. Load wallet balance from repository
                Log.d("WalletDetailVM", "Loading balance for wallet: $walletId")
                val loadedBalance = walletRepository.getWalletBalance(walletId)

                if (loadedBalance == null) {
                    Log.w("WalletDetailVM", " No balance found for wallet (this is normal for new wallets)")
                } else {
                    Log.d("WalletDetailVM", " Balance loaded:")
                    Log.d("WalletDetailVM", "   - Last Updated: ${loadedBalance.lastUpdated}")
                    Log.d("WalletDetailVM", "   - BTC: ${loadedBalance.bitcoin?.btc ?: "N/A"}")
                    Log.d("WalletDetailVM", "   - ETH: ${loadedBalance.ethereum?.eth ?: "N/A"}")
                    Log.d("WalletDetailVM", "   - SOL: ${loadedBalance.solana?.sol ?: "N/A"}")
                    Log.d("WalletDetailVM", "   - USDC: ${loadedBalance.usdc?.amountDecimal ?: "N/A"}")
                }

                _balance.value = loadedBalance
                Log.d("WalletDetailVM", "Balance assigned to StateFlow")

                // 3. Load transactions for each coin type
                Log.d("WalletDetailVM", "Starting transaction loads for wallet: $walletId")
                loadAllTransactions(walletId)

                // 4. Load ETH balance for gas if USDC is enabled
                if (loadedWallet.usdc != null) {
                    Log.d("WalletDetailVM", "USDC enabled, loading ETH gas balance")
                    loadETHGasBalance(walletId)
                }

                Log.d("WalletDetailVM", "========== LOAD WALLET SUCCESS ==========")

            } catch (e: Exception) {
                Log.e("WalletDetailVM", " Exception loading wallet: ${e.message}")
                Log.e("WalletDetailVM", "Stack trace: ${e.stackTraceToString()}")
                _error.value = "Failed to load wallet: ${e.message}"
            } finally {
                _isLoading.value = false
                Log.d("WalletDetailVM", "Load wallet completed, isLoading set to false")
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
            is Result.Error -> Log.e("WalletDetailVM", "Error loading ETH gas balance: ${result.message}")
            Result.Loading -> {}
        }
    }

    fun refreshBalance() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                _wallet.value?.let { wallet ->
                    walletRepository.syncWalletBalances(wallet)

                    val updatedBalance = walletRepository.getWalletBalance(wallet.id)
                    _balance.value = updatedBalance

                    // Refresh transactions
                    loadAllTransactions(wallet.id)

                    // Refresh ETH gas balance if needed
                    if (wallet.usdc != null) {
                        loadETHGasBalance(wallet.id)
                    }

                    Log.d("WalletDetailVM", "Balance refreshed")
                }
            } catch (e: Exception) {
                _error.value = "Failed to refresh balance: ${e.message}"
                Log.e("WalletDetailVM", "Error refreshing balance", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getDisplayBalance(): String {
        val wallet = _wallet.value ?: return "0"
        val balance = _balance.value

        return when {
            wallet.usdc != null -> {
                val usdcBalance = balance?.usdc
                usdcBalance?.let { "${it.amountDecimal} USDC" } ?: "0 USDC"
            }
            wallet.ethereum != null -> {
                val ethBalance = balance?.ethereum
                ethBalance?.let { "${it.eth} ETH" } ?: "0 ETH"
            }
            wallet.bitcoin != null -> {
                val btcBalance = balance?.bitcoin
                btcBalance?.let { "${it.btc} BTC" } ?: "0 BTC"
            }
            wallet.solana != null -> {
                val solBalance = balance?.solana
                solBalance?.let { "${it.sol} SOL" } ?: "0 SOL"
            }
            else -> "0"
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

    fun getDisplayAddress(): String {
        val wallet = _wallet.value ?: return "Loading..."
        val address = wallet.ethereum?.address
            ?: wallet.bitcoin?.address
            ?: wallet.solana?.address
            ?: wallet.usdc?.address
            ?: return "Loading..."

        return if (address.length > 12) {
            "${address.take(8)}...${address.takeLast(4)}"
        } else {
            address
        }
    }

    fun getFullAddress(): String {
        val wallet = _wallet.value ?: return ""
        return wallet.ethereum?.address
            ?: wallet.bitcoin?.address
            ?: wallet.solana?.address
            ?: wallet.usdc?.address
            ?: ""
    }

    fun getCoinSymbol(): String {
        val wallet = _wallet.value ?: return ""
        return when {
            wallet.usdc != null -> "USDC"
            wallet.ethereum != null -> "ETH"
            wallet.bitcoin != null -> "BTC"
            wallet.solana != null -> "SOL"
            else -> ""
        }
    }



    @Composable
    fun getCoinColor(): Color {
        return when {
            _wallet.collectAsState().value?.bitcoin != null -> Color(0xFFF7931A)
            _wallet.collectAsState().value?.ethereum != null -> Color(0xFF627EEA)
            _wallet.collectAsState().value?.solana != null -> Color(0xFF00FFA3)
            _wallet.collectAsState().value?.usdc != null -> Color(0xFF2775CA)
            else -> MaterialTheme.colorScheme.primary
        }
    }

    fun getCoinIcon(): ImageVector {
        return when {
            _wallet.value?.bitcoin != null -> Icons.Default.CurrencyBitcoin
            _wallet.value?.ethereum != null -> Icons.Default.Diamond
            _wallet.value?.solana != null -> Icons.Default.FlashOn
            _wallet.value?.usdc != null -> Icons.Default.AttachMoney
            else -> Icons.Default.AccountBalanceWallet
        }
    }

    fun getSendRoute(): String {
        val wallet = _wallet.value ?: return "send"
        return when {
            wallet.solana != null -> "send/${wallet.id}/SOL"
            wallet.bitcoin != null -> "send/${wallet.id}/BTC"
            wallet.usdc != null -> "send/${wallet.id}/USDC"
            wallet.ethereum != null -> {
                // ETH for both mainnet and sepolia - network is handled in the send screen
                "send/${wallet.id}/ETH"
            }
            else -> "send"
        }
    }

    fun refresh() {
        _wallet.value?.let { wallet ->
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    walletRepository.syncWalletBalances(wallet)
                    loadWallet(wallet.id)
                    Log.d("WalletDetailVM", "Wallet refreshed")
                } catch (e: Exception) {
                    _error.value = "Refresh failed: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}