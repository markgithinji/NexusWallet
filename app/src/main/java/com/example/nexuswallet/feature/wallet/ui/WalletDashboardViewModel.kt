package com.example.nexuswallet.feature.wallet.ui


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
@HiltViewModel
class WalletDashboardViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow<Result<List<Wallet>>>(Result.Loading)
    val uiState: StateFlow<Result<List<Wallet>>> = _uiState.asStateFlow()

    // Balances map
    private val _balances = MutableStateFlow<Map<String, WalletBalance>>(emptyMap())
    val balances: StateFlow<Map<String, WalletBalance>> = _balances.asStateFlow()

    // Total portfolio value
    private val _totalPortfolioValue = MutableStateFlow(BigDecimal.ZERO)
    val totalPortfolioValue: StateFlow<BigDecimal> = _totalPortfolioValue.asStateFlow()

    // Loading state for specific operations (delete, refresh)
    private val _isOperationLoading = MutableStateFlow(false)
    val isOperationLoading: StateFlow<Boolean> = _isOperationLoading.asStateFlow()

    // Operation error state
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    init {
        Log.d("WalletVM", "ViewModel initialized")
        observeWallets()
    }

    private fun observeWallets() {
        Log.d("WalletVM", "Starting to observe wallets")
        viewModelScope.launch {
            walletRepository.observeWallets()
                .catch { e ->
                    Log.e("WalletVM", "Error observing wallets: ${e.message}", e)
                    _uiState.value = Result.Error("Failed to load wallets: ${e.message}")
                }
                .collectLatest { walletsList ->
                    Log.d("WalletVM", "Collected wallets: ${walletsList.size} wallets found")

                    // Log each wallet's details
                    walletsList.forEachIndexed { index, wallet ->
                        Log.d("WalletVM", "Wallet $index: id=${wallet.id}, name=${wallet.name}, " +
                                "bitcoinCoins=${wallet.bitcoinCoins.size}, " +
                                "solanaCoins=${wallet.solanaCoins.size}, " +
                                "evmTokens=${wallet.evmTokens.size}")

                        // Log Bitcoin coins
                        wallet.bitcoinCoins.forEachIndexed { coinIdx, coin ->
                            Log.d("WalletVM", "  BTC Coin $coinIdx: network=${coin.network}, address=${coin.address.take(8)}...")
                        }

                        // Log Solana coins
                        wallet.solanaCoins.forEachIndexed { coinIdx, coin ->
                            Log.d("WalletVM", "  SOL Coin $coinIdx: network=${coin.network}, address=${coin.address.take(8)}...")
                        }

                        // Log EVM token externalIds
                        wallet.evmTokens.forEachIndexed { tokenIdx, token ->
                            Log.d("WalletVM", "  Token $tokenIdx: ${token.symbol} - externalId: ${token.externalId}")
                        }
                    }

                    if (walletsList.isEmpty()) {
                        Log.d("WalletVM", "No wallets found, setting empty success state")
                        _uiState.value = Result.Success(emptyList())
                    } else {
                        Log.d("WalletVM", "Wallets found, setting success state and loading balances")
                        _uiState.value = Result.Success(walletsList)
                        loadBalances(walletsList)
                    }
                }
        }
    }

    private fun loadBalances(wallets: List<Wallet>) {
        Log.d("WalletVM", "Loading balances for ${wallets.size} wallets")
        viewModelScope.launch {
            try {
                val balancesMap = mutableMapOf<String, WalletBalance>()
                wallets.forEach { wallet ->
                    Log.d("WalletVM", "Fetching balance for wallet: ${wallet.id}")
                    val balance = walletRepository.getWalletBalance(wallet.id)
                    if (balance != null) {
                        Log.d("WalletVM", "Balance found for wallet ${wallet.id}: " +
                                "bitcoinBalances=${balance.bitcoinBalances.size}, " +
                                "solanaBalances=${balance.solanaBalances.size}, " +
                                "evmBalances=${balance.evmBalances.size}")

                        // Log Bitcoin balances
                        balance.bitcoinBalances.forEach { (network, btcBalance) ->
                            Log.d("WalletVM", "  Bitcoin $network: ${btcBalance.btc} BTC, value=${btcBalance.usdValue}")
                        }

                        // Log Solana balances
                        balance.solanaBalances.forEach { (network, solBalance) ->
                            Log.d("WalletVM", "  Solana $network: ${solBalance.sol} SOL, value=${solBalance.usdValue}")
                        }

                        // Log EVM balance details
                        balance.evmBalances.forEachIndexed { idx, evmBalance ->
                            Log.d("WalletVM", "  EVM Balance $idx: externalTokenId=${evmBalance.externalTokenId}, " +
                                    "value=${evmBalance.usdValue}, address=${evmBalance.address.take(8)}...")
                        }

                        balancesMap[wallet.id] = balance
                    } else {
                        Log.d("WalletVM", "No balance found for wallet: ${wallet.id}")
                    }
                }
                Log.d("WalletVM", "Loaded balances for ${balancesMap.size} wallets")
                _balances.value = balancesMap

                calculateTotalPortfolio(wallets)

            } catch (e: Exception) {
                Log.e("WalletVM", "Failed to load balances: ${e.message}", e)
            }
        }
    }

    private fun calculateTotalPortfolio(wallets: List<Wallet>) {
        viewModelScope.launch {
            Log.d("WalletVM", "Calculating total portfolio")
            var total = BigDecimal.ZERO

            wallets.forEach { wallet ->
                val balance = _balances.value[wallet.id]
                val walletValue = balance?.let {
                    // Calculate total including all balances
                    var walletTotal = BigDecimal.ZERO

                    // Add all Bitcoin balances
                    it.bitcoinBalances.forEach { (_, btcBalance) ->
                        walletTotal = walletTotal.add(BigDecimal(btcBalance.usdValue))
                    }

                    // Add all Solana balances
                    it.solanaBalances.forEach { (_, solBalance) ->
                        walletTotal = walletTotal.add(BigDecimal(solBalance.usdValue))
                    }

                    // Add all EVM token balances
                    it.evmBalances.forEach { evmBalance ->
                        walletTotal = walletTotal.add(BigDecimal(evmBalance.usdValue))
                    }

                    Log.d("WalletVM", "Wallet ${wallet.id} value: $walletTotal")
                    walletTotal
                } ?: BigDecimal.ZERO

                total = total.add(walletValue)
            }

            _totalPortfolioValue.value = total
            Log.d("WalletVM", "Total portfolio calculated: $total")
        }
    }

    fun getWalletBalance(walletId: String): WalletBalance? {
        val balance = _balances.value[walletId]
        Log.d("WalletVM", "getWalletBalance for $walletId: ${balance != null}")
        return balance
    }

    fun getBitcoinBalance(walletId: String, network: String): BitcoinBalance? {
        return _balances.value[walletId]?.bitcoinBalances?.get(network)
    }

    fun getSolanaBalance(walletId: String, network: String): SolanaBalance? {
        return _balances.value[walletId]?.solanaBalances?.get(network)
    }

    fun getTokenBalance(walletId: String, externalTokenId: String): EVMBalance? {
        return _balances.value[walletId]?.evmBalances?.find { it.externalTokenId == externalTokenId }
    }

    fun deleteWallet(walletId: String) {
        Log.d("WalletVM", "Deleting wallet: $walletId")
        viewModelScope.launch {
            _isOperationLoading.value = true
            _operationError.value = null
            try {
                walletRepository.deleteWallet(walletId)
                Log.d("WalletVM", "Wallet deleted successfully: $walletId")
            } catch (e: Exception) {
                Log.e("WalletVM", "Failed to delete wallet: ${e.message}", e)
                _operationError.value = "Failed to delete wallet: ${e.message}"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    fun refresh() {
        Log.d("WalletVM", "Manual refresh triggered")
        viewModelScope.launch {
            _isOperationLoading.value = true
            _operationError.value = null
            try {
                val currentWallets = when (val state = _uiState.value) {
                    is Result.Success -> {
                        Log.d("WalletVM", "Current state has ${state.data.size} wallets")
                        state.data
                    }
                    else -> {
                        Log.d("WalletVM", "Current state is not Success: ${_uiState.value}")
                        emptyList()
                    }
                }
                if (currentWallets.isNotEmpty()) {
                    Log.d("WalletVM", "Refreshing balances for ${currentWallets.size} wallets")
                    loadBalances(currentWallets)
                } else {
                    Log.d("WalletVM", "No wallets to refresh")
                }
            } catch (e: Exception) {
                Log.e("WalletVM", "Failed to refresh: ${e.message}", e)
                _operationError.value = "Failed to refresh: ${e.message}"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    fun clearOperationError() {
        _operationError.value = null
    }

    fun hasWallets(): Boolean {
        val hasWallets = when (val state = _uiState.value) {
            is Result.Success -> {
                val result = state.data.isNotEmpty()
                Log.d("WalletVM", "hasWallets check: $result, size=${state.data.size}")
                result
            }
            else -> {
                Log.d("WalletVM", "hasWallets check: false, state=${_uiState.value}")
                false
            }
        }
        return hasWallets
    }

    fun getWalletCount(): Int {
        val count = when (val state = _uiState.value) {
            is Result.Success -> state.data.size
            else -> 0
        }
        Log.d("WalletVM", "getWalletCount: $count")
        return count
    }

    fun getWallets(): List<Wallet> {
        val wallets = when (val state = _uiState.value) {
            is Result.Success -> state.data
            else -> emptyList()
        }
        Log.d("WalletVM", "getWallets: ${wallets.size} wallets")
        return wallets
    }

    // Helper to get formatted portfolio value
    fun getFormattedPortfolioValue(): String {
        return NumberFormat.getCurrencyInstance(Locale.US).format(_totalPortfolioValue.value)
    }

    // Helper to get all tokens across all wallets for portfolio tracking
    fun getAllTokensWithBalances(): List<TokenWithBalance> {
        val result = mutableListOf<TokenWithBalance>()

        val wallets = when (val state = _uiState.value) {
            is Result.Success -> state.data
            else -> emptyList()
        }

        wallets.forEach { wallet ->
            val balance = _balances.value[wallet.id]

            // Add Bitcoin balances (all networks)
            wallet.bitcoinCoins.forEach { coin ->
                val networkKey = when (coin.network) {
                    BitcoinNetwork.Mainnet -> "mainnet"
                    BitcoinNetwork.Testnet -> "testnet"
                }
                val btcBalance = balance?.bitcoinBalances?.get(networkKey)
                btcBalance?.let {
                    result.add(
                        TokenWithBalance(
                            walletId = wallet.id,
                            walletName = wallet.name,
                            tokenName = "Bitcoin",
                            tokenSymbol = "BTC",
                            balance = it.btc,
                            usdValue = it.usdValue,
                            network = coin.network.displayName,
                            networkKey = networkKey
                        )
                    )
                }
            }

            // Add Solana balances (all networks)
            wallet.solanaCoins.forEach { coin ->
                val networkKey = when (coin.network) {
                    SolanaNetwork.Mainnet -> "mainnet"
                    SolanaNetwork.Devnet -> "devnet"
                }
                val solBalance = balance?.solanaBalances?.get(networkKey)
                solBalance?.let {
                    result.add(
                        TokenWithBalance(
                            walletId = wallet.id,
                            walletName = wallet.name,
                            tokenName = "Solana",
                            tokenSymbol = "SOL",
                            balance = it.sol,
                            usdValue = it.usdValue,
                            network = coin.network.name,
                            networkKey = networkKey
                        )
                    )
                }
            }

            // Add EVM tokens
            wallet.evmTokens.forEach { token ->
                val tokenBalance = balance?.evmBalances?.find { it.externalTokenId == token.externalId }
                tokenBalance?.let {
                    result.add(
                        TokenWithBalance(
                            walletId = wallet.id,
                            walletName = wallet.name,
                            tokenName = token.name,
                            tokenSymbol = token.symbol,
                            balance = it.balanceDecimal,
                            usdValue = it.usdValue,
                            network = token.network.displayName,
                            externalTokenId = token.externalId
                        )
                    )
                }
            }
        }

        return result
    }
}

// Data class for portfolio items
data class TokenWithBalance(
    val walletId: String,
    val walletName: String,
    val tokenName: String,
    val tokenSymbol: String,
    val balance: String,
    val usdValue: Double,
    val network: String,
    val networkKey: String? = null,
    val externalTokenId: String? = null
)

// Extension property for WalletBalance with polymorphic support
val WalletBalance.totalUsdValue: Double
    get() {
        var total = 0.0
        Log.d("WalletVM", "Calculating totalUsdValue for wallet $walletId")

        // Add all Bitcoin balances
        bitcoinBalances.forEach { (network, btcBalance) ->
            total += btcBalance.usdValue
            Log.d("WalletVM", "  + Bitcoin $network: ${btcBalance.usdValue}")
        }

        // Add all Solana balances
        solanaBalances.forEach { (network, solBalance) ->
            total += solBalance.usdValue
            Log.d("WalletVM", "  + Solana $network: ${solBalance.usdValue}")
        }

        // Add all EVM token balances
        evmBalances.forEachIndexed { index, evmBalance ->
            total += evmBalance.usdValue
            Log.d("WalletVM", "  + EVM Balance $index: ${evmBalance.symbol ?: "Unknown"} - $${evmBalance.usdValue}")
        }

        Log.d("WalletVM", "  Total = $total")
        return total
    }

// Add helper extension to EVMBalance for better logging
val EVMBalance.symbol: String?
    get() {
        return when {
            externalTokenId.contains("eth") -> "ETH"
            externalTokenId.contains("usdc") -> "USDC"
            externalTokenId.contains("usdt") -> "USDT"
            else -> null
        }
    }