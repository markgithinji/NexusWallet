package com.example.nexuswallet.feature.wallet.ui


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.TokenBalance
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.USDCWallet
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
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
import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.model.SendTransaction

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val transactionLocalDataSource: TransactionLocalDataSource,
    private val getUSDCBalanceUseCase: GetUSDCBalanceUseCase,
    private val getETHBalanceForGasUseCase: GetETHBalanceForGasUseCase
) : ViewModel() {

    // Current wallet
    private val _wallet = MutableStateFlow<CryptoWallet?>(null)
    val wallet: StateFlow<CryptoWallet?> = _wallet

    // Wallet balance
    private val _balance = MutableStateFlow<WalletBalance?>(null)
    val balance: StateFlow<WalletBalance?> = _balance

    // Token balances (for USDC and other ERC-20 tokens)
    private val _tokenBalances = MutableStateFlow<List<TokenBalance>>(emptyList())
    val tokenBalances: StateFlow<List<TokenBalance>> = _tokenBalances

    // Transactions
    private val _transactions = MutableStateFlow<List<SendTransaction>>(emptyList())
    val transactions: StateFlow<List<SendTransaction>> = _transactions

    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingTokenBalances = MutableStateFlow(false)
    val isLoadingTokenBalances: StateFlow<Boolean> = _isLoadingTokenBalances

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Job for collecting transactions flow
    private var transactionsJob: Job? = null

    // Live balance
    private val _liveBalance = MutableStateFlow<String?>(null)
    val liveBalance: StateFlow<String?> = _liveBalance

    // ETH balance for USDC wallet (for gas)
    private val _ethBalanceForGas = MutableStateFlow<WalletBalance?>(null)
    val ethBalanceForGas: StateFlow<WalletBalance?> = _ethBalanceForGas

    // Load wallet details
    fun loadWallet(walletId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // 1. Load wallet from repository
                val loadedWallet = walletRepository.getWallet(walletId)
                _wallet.value = loadedWallet
                Log.d("WalletDetailVM", "Loaded wallet: ${loadedWallet?.name}")

                // 2. Load wallet balance from repository
                val loadedBalance = walletRepository.getWalletBalance(walletId)
                _balance.value = loadedBalance
                Log.d("WalletDetailVM", "Loaded balance: ${loadedBalance?.nativeBalanceDecimal}")

                // 3. Load transactions
                loadTransactions(walletId)

                // 4. Load token balances if applicable
                if (loadedWallet is USDCWallet || loadedWallet is EthereumWallet) {
                    loadTokenBalances(walletId)
                }

            } catch (e: Exception) {
                _error.value = "Failed to load wallet: ${e.message}"
                Log.e("WalletDetailVM", "Error loading wallet", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Load transactions from TransactionLocalDataSource
    private fun loadTransactions(walletId: String) {
        transactionsJob?.cancel()

        transactionsJob = viewModelScope.launch {
            transactionLocalDataSource.getSendTransactions(walletId)
                .collect { txList ->
                    // Sort by timestamp descending (newest first)
                    _transactions.value = txList.sortedByDescending { it.timestamp }
                    Log.d("WalletDetailVM", "Loaded ${txList.size} transactions")
                }
        }
    }

    fun refreshBalance() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                _wallet.value?.let { wallet ->
                    walletRepository.syncWalletBalance(wallet)

                    val updatedBalance = walletRepository.getWalletBalance(wallet.id)
                    _balance.value = updatedBalance

                    if (wallet is USDCWallet || wallet is EthereumWallet) {
                        loadTokenBalances(wallet.id)
                    }

                    // Refresh transactions too
                    loadTransactions(wallet.id)

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

    fun loadTokenBalances(walletId: String) {
        viewModelScope.launch {
            _isLoadingTokenBalances.value = true
            try {
                val wallet = walletRepository.getWallet(walletId)
                val existingBalance = walletRepository.getWalletBalance(walletId)

                when (wallet) {
                    is USDCWallet -> {
                        val usdcBalanceResult = getUSDCBalanceUseCase(walletId)
                        val usdcBalance = when (usdcBalanceResult) {
                            is Result.Success -> usdcBalanceResult.data
                            is Result.Error -> {
                                Log.e("WalletDetailVM", "Error getting USDC balance: ${usdcBalanceResult.message}")
                                createEmptyUSDCBalance(wallet.network)
                            }
                            Result.Loading -> createEmptyUSDCBalance(wallet.network)
                        }

                        val tokenList = if (usdcBalance.balanceDecimal != "0") {
                            listOf(usdcBalance)
                        } else {
                            emptyList()
                        }
                        _tokenBalances.value = tokenList

                        if (existingBalance != null) {
                            val updatedBalance = existingBalance.copy(
                                tokens = tokenList,
                                nativeBalance = existingBalance.nativeBalance,
                                nativeBalanceDecimal = existingBalance.nativeBalanceDecimal,
                                lastUpdated = System.currentTimeMillis()
                            )
                            walletRepository.saveWalletBalance(updatedBalance)
                            _balance.value = updatedBalance
                        }
                    }

                    is EthereumWallet -> {
                        if (wallet.network == EthereumNetwork.SEPOLIA || wallet.network == EthereumNetwork.MAINNET) {
                            val usdcBalanceResult = getUSDCBalanceUseCase(walletId)
                            val usdcBalance = when (usdcBalanceResult) {
                                is Result.Success -> usdcBalanceResult.data
                                is Result.Error -> createEmptyUSDCBalance(wallet.network)
                                Result.Loading -> createEmptyUSDCBalance(wallet.network)
                            }

                            val tokenList = if (usdcBalance.balanceDecimal != "0") {
                                listOf(usdcBalance)
                            } else {
                                emptyList()
                            }

                            _tokenBalances.value = tokenList

                            if (existingBalance != null) {
                                val updatedBalance = existingBalance.copy(
                                    tokens = tokenList,
                                    lastUpdated = System.currentTimeMillis()
                                )
                                walletRepository.saveWalletBalance(updatedBalance)
                                _balance.value = updatedBalance
                            }
                        } else {
                            _tokenBalances.value = emptyList()
                        }
                    }

                    else -> {
                        _tokenBalances.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e("WalletDetailVM", "Error loading token balances: ${e.message}", e)
                _tokenBalances.value = emptyList()
            } finally {
                _isLoadingTokenBalances.value = false
            }
        }
    }

    private fun createEmptyUSDCBalance(network: EthereumNetwork): TokenBalance {
        val chainType = when (network) {
            EthereumNetwork.SEPOLIA -> ChainType.ETHEREUM_SEPOLIA
            else -> ChainType.ETHEREUM
        }

        return TokenBalance(
            tokenId = "usdc_empty_${System.currentTimeMillis()}",
            symbol = "USDC",
            name = "USD Coin",
            contractAddress = "",
            balance = "0",
            balanceDecimal = "0",
            usdPrice = 1.0,
            usdValue = 0.0,
            decimals = 6,
            chain = chainType
        )
    }

    fun getDisplayBalance(): String {
        val balance = _balance.value

        return when (val wallet = _wallet.value) {
            is USDCWallet -> {
                val native = balance?.nativeBalanceDecimal ?: "0"
                val hasTokens = balance?.tokens?.isNotEmpty() == true

                if (hasTokens) {
                    val usdcBalance = balance.tokens.firstOrNull()
                    "${usdcBalance?.balanceDecimal ?: "0"} USDC (${native} native)"
                } else {
                    "$native USDC"
                }
            }
            is EthereumWallet -> {
                val ethBalance = balance?.nativeBalanceDecimal ?: "0"
                val hasTokens = balance?.tokens?.isNotEmpty() == true

                if (hasTokens) {
                    val tokenList = balance.tokens.joinToString(", ") { "${it.balanceDecimal} ${it.symbol}" }
                    "$ethBalance ETH + $tokenList"
                } else {
                    "$ethBalance ETH"
                }
            }
            else -> {
                balance?.nativeBalanceDecimal ?: "0"
            }
        }
    }

    fun getTotalUsdValue(): Double {
        val balance = _balance.value
        var total = balance?.usdValue ?: 0.0

        balance?.tokens?.forEach { token ->
            total += token.usdValue
        }

        return total
    }

    fun refreshTokenBalances() {
        _wallet.value?.let { wallet ->
            loadTokenBalances(wallet.id)
        }
    }

    fun getTokenBalance(symbol: String = "USDC"): TokenBalance? {
        return _balance.value?.tokens?.find { it.symbol == symbol } ?: _tokenBalances.value.find { it.symbol == symbol }
    }

    fun hasTokens(): Boolean {
        return _balance.value?.tokens?.isNotEmpty() == true || _tokenBalances.value.isNotEmpty()
    }

    fun getAllTokens(): List<TokenBalance> {
        return _balance.value?.tokens ?: _tokenBalances.value
    }

    suspend fun getETHGasBalance(): BigDecimal? {
        val wallet = _wallet.value
        return if (wallet is USDCWallet) {
            val result = getETHBalanceForGasUseCase(wallet.id)
            when (result) {
                is Result.Success -> {
                    Log.d("WalletDetailVM", "ETH gas balance loaded: ${result.data}")
                    result.data
                }
                is Result.Error -> {
                    Log.e("WalletDetailVM", "Error getting ETH gas balance: ${result.message}")
                    null
                }
                Result.Loading -> {
                    Log.d("WalletDetailVM", "Loading ETH gas balance...")
                    null
                }
            }
        } else {
            null
        }
    }

    suspend fun hasSufficientGasForUSDC(minRequired: BigDecimal = BigDecimal("0.001")): Boolean {
        return if (_wallet.value is USDCWallet) {
            val ethBalance = getETHGasBalance()
            ethBalance?.let { it >= minRequired } ?: false
        } else {
            true
        }
    }

    fun getWalletName(): String {
        return _wallet.value?.name ?: "Wallet Details"
    }

    fun getDisplayAddress(): String {
        val addr = _wallet.value?.address ?: return "Loading..."
        return if (addr.length > 12) {
            "${addr.take(8)}...${addr.takeLast(4)}"
        } else {
            addr
        }
    }

    fun getFullAddress(): String {
        return _wallet.value?.address ?: ""
    }

    fun refresh() {
        _wallet.value?.let { wallet ->
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    walletRepository.syncWalletBalance(wallet)
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
        transactionsJob?.cancel()
    }
}