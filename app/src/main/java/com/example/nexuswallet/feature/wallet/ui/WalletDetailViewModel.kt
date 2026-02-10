package com.example.nexuswallet.feature.wallet.ui


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.NexusWalletApplication
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.ChainType
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.USDCWallet
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : ViewModel() {

    // Current wallet
    private val _wallet = MutableStateFlow<CryptoWallet?>(null)
    val wallet: StateFlow<CryptoWallet?> = _wallet

    // Wallet balance
    private val _balance = MutableStateFlow<WalletBalance?>(null)
    val balance: StateFlow<WalletBalance?> = _balance

    // Transactions
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Job for collecting transactions flow
    private var transactionsJob: Job? = null

    private val _liveBalance = MutableStateFlow<String?>(null)
    val liveBalance: StateFlow<String?> = _liveBalance


    // ETH balance for USDC wallet (for gas)
    private val _ethBalanceForGas = MutableStateFlow<WalletBalance?>(null)
    val ethBalanceForGas: StateFlow<WalletBalance?> = _ethBalanceForGas


    fun refreshBalance() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Force sync the wallet balance
                _wallet.value?.let { wallet ->
                    walletRepository.syncWalletBalance(wallet)

                    // Reload balance from repository
                    val updatedBalance = walletRepository.getWalletBalance(wallet.id)
                    _balance.value = updatedBalance

                    Log.d("WalletDetailVM", " Balance refreshed")
                }
            } catch (e: Exception) {
                _error.value = "Failed to refresh balance: ${e.message}"
                Log.e("WalletDetailVM", "Error refreshing balance", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Load wallet details
    fun loadWallet(walletId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // 1. Load wallet
                val loadedWallet = walletRepository.getWallet(walletId)
                _wallet.value = loadedWallet

                // 2. Load balance (async)
                val balanceDeferred = async { walletRepository.getWalletBalance(walletId) }

                // 3. If USDC wallet, also load ETH balance for gas
                if (loadedWallet is USDCWallet) {
                    val ethBalanceDeferred = async {
                        walletRepository.getWalletBalance(loadedWallet.id)
                    }
                    _ethBalanceForGas.value = ethBalanceDeferred.await()
                } else{
                    Log.d("WalletDetailVM", "Not an USDC wallet, not loading ETH balance for gas")
                }

                // 4. Start collecting transactions
                startCollectingTransactions(walletId)

                // 5. Wait for balance and update
                val loadedBalance = balanceDeferred.await()
                _balance.value = loadedBalance

                Log.d("WalletDetailVM", "Loaded ${loadedWallet?.walletType} wallet")

            } catch (e: Exception) {
                _error.value = "Failed to load wallet: ${e.message}"
                Log.e("WalletDetailVM", "Error loading wallet: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startCollectingTransactions(walletId: String) {
        // Cancel previous job if exists
        transactionsJob?.cancel()

        transactionsJob = viewModelScope.launch {
            walletRepository.getTransactions(walletId).collect { txList ->
                _transactions.value = txList
            }
        }
    }

    fun getWalletName(): String {
        return _wallet.value?.name ?: "Wallet Details"
    }

    fun getDisplayAddress(): String {
        val addr = _wallet.value?.address ?: return "Loading..."
        Log.d("WalletDetailVM", "getDisplayAddress - Original: $addr")

        return if (addr.length > 12) {
            val display = "${addr.take(8)}...${addr.takeLast(4)}"
            Log.d("WalletDetailVM", "getDisplayAddress - Display: $display")
            display
        } else {
            addr
        }
    }

    fun getFullAddress(): String {
        return _wallet.value?.address ?: ""
    }

    private fun getWalletAddress(): String? {
        return _wallet.value?.address
    }

    fun refresh() {
        _wallet.value?.let { wallet ->
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    // Refresh balance
                    walletRepository.syncWalletBalance(wallet)

                    // Reload everything
                    loadWallet(wallet.id)

                    Log.d("WalletDetailVM", " Wallet refreshed")
                } catch (e: Exception) {
                    _error.value = "Refresh failed: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    fun createSampleTransaction(): Transaction {
        val fromAddress = getWalletAddress() ?: ""

        return Transaction(
            hash = "0x${System.currentTimeMillis().toString(16)}",
            from = fromAddress,
            to = "0x742d35Cc6634C0532925a3b844Bc9e",
            value = "1000000000000000000", // 1 ETH
            valueDecimal = "1.0",
            gasPrice = "50000000000", // 50 Gwei
            gasUsed = "21000",
            timestamp = System.currentTimeMillis() - 3600000, // 1 hour ago
            status = TransactionStatus.SUCCESS,
            chain = ChainType.ETHEREUM,
            tokenTransfers = emptyList()
        )
    }

    fun addSampleTransaction() {
        viewModelScope.launch {
            val sampleTx = createSampleTransaction()
            val currentTx = _transactions.value.toMutableList()
            currentTx.add(0, sampleTx)
            _transactions.value = currentTx

            // Save to data layer
            _wallet.value?.id?.let { walletId ->
                walletRepository.saveTransactions(walletId, currentTx)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        transactionsJob?.cancel()
    }
}