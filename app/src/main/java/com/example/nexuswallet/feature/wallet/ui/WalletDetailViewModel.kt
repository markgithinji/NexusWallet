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

                // 3. Start collecting transactions flow
                startCollectingTransactions(walletId)

                // 4. Wait for balance and update
                val loadedBalance = balanceDeferred.await()
                _balance.value = loadedBalance

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
        val addr = getWalletAddress() ?: return ""
        return if (addr.length > 12) {
            "${addr.take(8)}...${addr.takeLast(4)}"
        } else {
            addr
        }
    }

    private fun getWalletAddress(): String? {
        return when (val wallet = _wallet.value) {
            is BitcoinWallet -> wallet.address
            is EthereumWallet -> wallet.address
            is MultiChainWallet -> wallet.ethereumWallet?.address ?: wallet.bitcoinWallet?.address
            is SolanaWallet -> wallet.address
            else -> null
        }
    }

    fun getFullAddress(): String {
        return when (val wallet = _wallet.value) {
            is BitcoinWallet -> wallet.address
            is EthereumWallet -> wallet.address
            is MultiChainWallet -> wallet.ethereumWallet?.address
                ?: wallet.bitcoinWallet?.address
                ?: ""
            is SolanaWallet -> wallet.address
            else -> ""
        }
    }

    fun getWalletTypeDisplay(): String {
        return when (_wallet.value) {
            is BitcoinWallet -> "Bitcoin Wallet"
            is EthereumWallet -> "Ethereum Wallet"
            is MultiChainWallet -> "Multi-Chain Wallet"
            is SolanaWallet -> "Solana Wallet"
            else -> "Crypto Wallet"
        }
    }

    fun getNetworkDisplay(): String {
        return when (val wallet = _wallet.value) {
            is BitcoinWallet -> wallet.network.name
            is EthereumWallet -> wallet.network.name
            else -> "Mainnet"
        }
    }

    fun refresh() {
        _wallet.value?.let { wallet ->
            loadWallet(wallet.id)
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

    fun getTotalBalanceUsd(): Double {
        return _balance.value?.usdValue ?: 0.0
    }

    fun getNativeBalance(): String {
        return _balance.value?.nativeBalanceDecimal ?: "0"
    }

    fun clearError() {
        _error.value = null
    }

    fun getCreationDate(): String {
        _wallet.value?.createdAt?.let {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return sdf.format(Date(it))
        }
        return "Unknown"
    }

    fun isBackedUp(): Boolean {
        return _wallet.value?.isBackedUp ?: false
    }

    override fun onCleared() {
        super.onCleared()
        transactionsJob?.cancel()
    }
}