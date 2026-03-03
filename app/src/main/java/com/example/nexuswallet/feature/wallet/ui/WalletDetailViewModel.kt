package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.market.domain.MarketRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.ERC20Token
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SyncWalletBalancesUseCase
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TokenType
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDTToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.domain.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletDetailUiState(
    val wallet: Wallet? = null,
    val balance: WalletBalance? = null,
    val transactions: List<Any> = emptyList(),
    val pricePercentages: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSyncError: Boolean = false,
    val syncErrorMessage: String? = null
)
@HiltViewModel
class WalletDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val syncWalletBalancesUseCase: SyncWalletBalancesUseCase,
    private val getAllTransactionsUseCase: GetAllTransactionsUseCase,
    private val marketRepository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletDetailUiState(isLoading = true))
    val uiState: StateFlow<WalletDetailUiState> = _uiState.asStateFlow()

    fun loadWallet(walletId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, hasSyncError = false) }
            Log.d("WalletDetailVM", "=== Loading wallet: $walletId ===")

            try {
                // 1. Load wallet from repository
                val loadedWallet = walletRepository.getWallet(walletId)

                if (loadedWallet == null) {
                    Log.e("WalletDetailVM", "Wallet not found: $walletId")
                    _uiState.update { it.copy(error = "Wallet not found", isLoading = false) }
                    return@launch
                }

                _uiState.update { it.copy(wallet = loadedWallet) }

                // 2. Load wallet balance from repository (cached data)
                Log.d("WalletDetailVM", "Loading balance for wallet: $walletId")
                val loadedBalance = walletRepository.getWalletBalance(walletId)

                if (loadedBalance != null) {
                    Log.d("WalletDetailVM", "Balance loaded (cached)")
                } else {
                    Log.w("WalletDetailVM", "⚠ No balance found for wallet")
                }

                // 3. Load market percentages
                Log.d("WalletDetailVM", "Loading market percentages...")
                when (val percentagesResult = marketRepository.getLatestPricePercentages()) {
                    is Result.Success -> {
                        Log.d("WalletDetailVM", "Market percentages loaded: ${percentagesResult.data.size} entries")
                        _uiState.update { it.copy(pricePercentages = percentagesResult.data) }
                    }
                    is Result.Error -> {
                        Log.e("WalletDetailVM", "Failed to load market percentages: ${percentagesResult.message}")
                        _uiState.update { it.copy(pricePercentages = emptyMap()) }
                    }
                    Result.Loading -> {}
                }

                // 4. Load initial transactions
                Log.d("WalletDetailVM", "Loading initial transactions...")
                val initialTransactions = getAllTransactionsUseCase(walletId)
                Log.d("WalletDetailVM", "Loaded ${initialTransactions.size} transactions")

                // Update UI once with all initial data (using cached balance)
                _uiState.update {
                    it.copy(
                        wallet = loadedWallet,
                        balance = loadedBalance,
                        transactions = initialTransactions,
                        isLoading = false
                    )
                }

                // 5. Start observing transactions
                Log.d("WalletDetailVM", "Starting transaction observation...")
                observeTransactions(walletId)

                // 6. Sync balances in background (this is where network calls happen)
                Log.d("WalletDetailVM", "Starting background balance sync...")
                viewModelScope.launch {
                    when (val syncResult = syncWalletBalancesUseCase(loadedWallet)) {
                        is Result.Success -> {
                            Log.d("WalletDetailVM", "Balance sync completed successfully")
                            val updatedBalance = walletRepository.getWalletBalance(walletId)

                            if (updatedBalance != null) {
                                Log.d("WalletDetailVM", "Updated balances after sync (fresh data)")
                                // Update UI with fresh data - this clears any sync error
                                _uiState.update {
                                    it.copy(
                                        balance = updatedBalance,
                                        hasSyncError = false,
                                        syncErrorMessage = null
                                    )
                                }
                            }
                        }
                        is Result.Error -> {
                            Log.e("WalletDetailVM", "Balance sync failed: ${syncResult.message}")
                            // Show warning but KEEP the cached balance
                            _uiState.update {
                                it.copy(
                                    hasSyncError = true,
                                    syncErrorMessage = syncResult.message
                                )
                            }

                            // Even though sync failed, try to get whatever balance we have
                            val currentBalance = walletRepository.getWalletBalance(walletId)
                            if (currentBalance != null && currentBalance != _uiState.value.balance) {
                                _uiState.update { it.copy(balance = currentBalance) }
                            }
                        }
                        Result.Loading -> {}
                    }
                }

            } catch (e: Exception) {
                Log.e("WalletDetailVM", "Failed to load wallet: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        error = "Failed to load wallet: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeTransactions(walletId: String) {
        viewModelScope.launch {
            getAllTransactionsUseCase.observeTransactions(walletId)
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    Log.e("WalletDetailVM", "Error observing transactions: ${e.message}", e)
                }
                .collect { updatedTransactions ->
                    Log.d("WalletDetailVM", "Transactions updated: ${updatedTransactions.size} transactions")
                    _uiState.update { it.copy(transactions = updatedTransactions) }
                }
        }
    }

    fun getWalletName(): String = _uiState.value.wallet?.name ?: "Wallet Details"

    fun getTotalUsdValue(): Double {
        val balance = _uiState.value.balance
        var total = 0.0

        // Bitcoin balances (all networks)
        balance?.bitcoinBalances?.forEach { (network, btcBalance) ->
            total += btcBalance.usdValue
            Log.d("WalletDetailVM", "Total USD - Bitcoin $network: ${btcBalance.usdValue}")
        }

        // Solana balances (all networks)
        balance?.solanaBalances?.forEach { (network, solBalance) ->
            total += solBalance.usdValue
            Log.d("WalletDetailVM", "Total USD - Solana $network: ${solBalance.usdValue}")
        }

        // EVM balances (all tokens)
        balance?.evmBalances?.forEach { evmBalance ->
            total += evmBalance.usdValue
            Log.d("WalletDetailVM", "Total USD - EVM (${evmBalance.externalTokenId}): ${evmBalance.usdValue}")
        }

        Log.d("WalletDetailVM", "Total USD Value: $total")
        return total
    }

    fun getFullAddress(): String {
        val wallet = _uiState.value.wallet ?: return ""

        // Try to get the first available address
        return wallet.bitcoinCoins.firstOrNull()?.address
            ?: wallet.solanaCoins.firstOrNull()?.address
            ?: wallet.evmTokens.firstOrNull()?.address
            ?: ""
    }

    fun refresh() {
        Log.d("WalletDetailVM", "Manual refresh triggered")
        _uiState.value.wallet?.let { wallet ->
            viewModelScope.launch {
                loadWallet(wallet.id)
            }
        }
    }

    // Helper functions to get specific coin addresses
    fun getBitcoinAddresses(): List<String> =
        _uiState.value.wallet?.bitcoinCoins?.map { it.address } ?: emptyList()

    fun getSolanaAddresses(): List<String> =
        _uiState.value.wallet?.solanaCoins?.map { it.address } ?: emptyList()

    fun getEVMAddresses(): List<String> =
        _uiState.value.wallet?.evmTokens?.map { it.address }?.distinct() ?: emptyList()

    // Helper to get all SPL tokens
    fun getAllSPLTokens(): List<SPLToken> =
        _uiState.value.wallet?.solanaCoins?.flatMap { it.splTokens } ?: emptyList()

    // Helper to get all EVM tokens by type
    fun getTokensByType(type: TokenType): List<EVMToken> =
        _uiState.value.wallet?.evmTokens?.filter { token ->
            when (token) {
                is NativeETH -> type == TokenType.NATIVE
                is USDCToken -> type == TokenType.USDC
                is USDTToken -> type == TokenType.USDT
                is ERC20Token -> type == TokenType.ERC20
            }
        } ?: emptyList()
}