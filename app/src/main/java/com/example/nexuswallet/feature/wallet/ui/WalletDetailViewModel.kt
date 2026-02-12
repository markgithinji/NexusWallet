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

@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
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
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions

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

                    // Also refresh token balances if it's a token wallet
                    if (wallet is USDCWallet || wallet is EthereumWallet) {
                        loadTokenBalances(wallet.id)
                    }

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
                // 1. Load wallet from repository
                val loadedWallet = walletRepository.getWallet(walletId)
                _wallet.value = loadedWallet

                // 2. Load wallet balance from repository
                val balanceDeferred = async { walletRepository.getWalletBalance(walletId) }

                // 3. Load token balances using use cases
                if (loadedWallet is USDCWallet || loadedWallet is EthereumWallet) {
                    loadTokenBalances(walletId)
                }

                // 4. If USDC wallet, load ETH balance for gas using use case
                if (loadedWallet is USDCWallet) {
                    val ethBalance = getETHBalanceForGasUseCase(walletId)
                    // Store in metadata or separate state
                }

                // 5. Wait for balance and update
                val loadedBalance = balanceDeferred.await()
                _balance.value = loadedBalance

            } catch (e: Exception) {
                _error.value = "Failed to load wallet: ${e.message}"
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

                // Get existing wallet balance first
                val existingBalance = walletRepository.getWalletBalance(walletId)

                when (wallet) {
                    is USDCWallet -> {
                        // Load USDC balance
                        val usdcBalance = try {
                            getUSDCBalanceUseCase(walletId)
                        } catch (e: Exception) {
                            Log.e("WalletDetailVM", "Error getting USDC balance: ${e.message}")
                            createEmptyUSDCBalance(wallet.network)
                        }

                        // Also get ETH balance for gas (for USDC wallets)
                        val ethBalance = try {
                            getETHBalanceForGasUseCase(walletId)
                        } catch (e: Exception) {
                            Log.e("WalletDetailVM", "Error getting ETH balance for gas: ${e.message}")
                            BigDecimal.ZERO
                        }

                        // Update token balances state
                        val tokenList = if (usdcBalance.balanceDecimal != "0") {
                            listOf(usdcBalance)
                        } else {
                            emptyList()
                        }
                        _tokenBalances.value = tokenList

                        // Update the main WalletBalance with tokens in the tokens field
                        if (existingBalance != null) {
                            val updatedBalance = existingBalance.copy(
                                tokens = tokenList,
                                nativeBalance = existingBalance.nativeBalance,
                                nativeBalanceDecimal = existingBalance.nativeBalanceDecimal,
                                lastUpdated = System.currentTimeMillis()
                            )

                            // Save updated balance with tokens
                            walletRepository.saveWalletBalance(updatedBalance)
                            _balance.value = updatedBalance
                        }

                        Log.d("WalletDetailVM", "Loaded USDC: ${usdcBalance.balanceDecimal}, ETH for gas: $ethBalance")
                    }

                    is EthereumWallet -> {
                        // For Ethereum wallets, check if they have USDC
                        if (wallet.network == EthereumNetwork.SEPOLIA || wallet.network == EthereumNetwork.MAINNET) {
                            val usdcBalance = try {
                                getUSDCBalanceUseCase(walletId)
                            } catch (e: Exception) {
                                Log.e("WalletDetailVM", "Error getting USDC for Ethereum wallet: ${e.message}")
                                createEmptyUSDCBalance(wallet.network)
                            }

                            val tokenList = if (usdcBalance.balanceDecimal != "0") {
                                listOf(usdcBalance)
                            } else {
                                emptyList()
                            }

                            _tokenBalances.value = tokenList

                            // Update main balance with tokens
                            if (existingBalance != null) {
                                val updatedBalance = existingBalance.copy(
                                    tokens = tokenList,
                                    lastUpdated = System.currentTimeMillis()
                                )

                                walletRepository.saveWalletBalance(updatedBalance)
                                _balance.value = updatedBalance
                            }

                            Log.d("WalletDetailVM", "Found USDC on Ethereum wallet: ${usdcBalance.balanceDecimal}")
                        } else {
                            _tokenBalances.value = emptyList()
                            Log.d("WalletDetailVM", "Not checking USDC for non-Ethereum network: ${wallet.network}")
                        }
                    }

                    else -> {
                        _tokenBalances.value = emptyList()
                        Log.d("WalletDetailVM", "Wallet type ${wallet?.walletType} doesn't support token balances")
                    }
                }
            } catch (e: Exception) {
                Log.e("WalletDetailVM", "Error loading token balances: ${e.message}")
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

    // Helper to get display balance with tokens
    fun getDisplayBalance(): String {
        val balance = _balance.value

        return when (val wallet = _wallet.value) {
            is USDCWallet -> {
                // For USDC wallets, show both native balance and token balance
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
                // For Ethereum wallets, show ETH balance and any tokens
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

    // Get total USD value including tokens
    fun getTotalUsdValue(): Double {
        val balance = _balance.value
        var total = balance?.usdValue ?: 0.0

        // Add token values
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

    // Get specific token balance
    fun getTokenBalance(symbol: String = "USDC"): TokenBalance? {
        return _balance.value?.tokens?.find { it.symbol == symbol } ?: _tokenBalances.value.find { it.symbol == symbol }
    }

    // Check if wallet has any tokens
    fun hasTokens(): Boolean {
        return _balance.value?.tokens?.isNotEmpty() == true || _tokenBalances.value.isNotEmpty()
    }

    fun getAllTokens(): List<TokenBalance> {
        return _balance.value?.tokens ?: _tokenBalances.value
    }

    // Get ETH balance for gas (specifically for USDC wallets)
    suspend fun getETHGasBalance(): BigDecimal? {
        val wallet = _wallet.value
        return if (wallet is USDCWallet) {
            try {
                getETHBalanceForGasUseCase(wallet.id)
            } catch (e: Exception) {
                Log.e("WalletDetailVM", "Error getting ETH gas balance: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    // Check if wallet has sufficient ETH for USDC transactions
    suspend fun hasSufficientGasForUSDC(minRequired: BigDecimal = BigDecimal("0.001")): Boolean {
        return if (_wallet.value is USDCWallet) {
            val ethBalance = getETHGasBalance()
            ethBalance?.let { it >= minRequired } ?: false
        } else {
            true // Non-USDC wallets don't need this check
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

    override fun onCleared() {
        super.onCleared()
        transactionsJob?.cancel()
    }
}