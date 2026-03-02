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
    val error: String? = null
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
            _uiState.update { it.copy(isLoading = true, error = null) }
            Log.d("WalletDetailVM", "=== Loading wallet: $walletId ===")

            try {
                // 1. Load wallet from repository
                val loadedWallet = walletRepository.getWallet(walletId)

                if (loadedWallet == null) {
                    Log.e("WalletDetailVM", "Wallet not found: $walletId")
                    _uiState.update { it.copy(error = "Wallet not found", isLoading = false) }
                    return@launch
                }

                // Log ALL wallet contents
                Log.d("WalletDetailVM", " Wallet loaded: ${loadedWallet.name}")
                Log.d("WalletDetailVM", "==========================================")
                Log.d("WalletDetailVM", "BITCOIN COINS: ${loadedWallet.bitcoinCoins.size}")
                loadedWallet.bitcoinCoins.forEachIndexed { index, coin ->
                    Log.d("WalletDetailVM", "  BTC $index: network=${coin.network}, address=${coin.address}")
                }

                Log.d("WalletDetailVM", "SOLANA COINS: ${loadedWallet.solanaCoins.size}")
                loadedWallet.solanaCoins.forEachIndexed { index, coin ->
                    Log.d("WalletDetailVM", "  SOL $index: network=${coin.network}, address=${coin.address}")
                    Log.d("WalletDetailVM", "    SPL Tokens: ${coin.splTokens.size}")
                }

                Log.d("WalletDetailVM", "EVM TOKENS: ${loadedWallet.evmTokens.size}")
                loadedWallet.evmTokens.forEachIndexed { index, token ->
                    Log.d("WalletDetailVM", "  Token $index: ${token.symbol} - externalId: ${token.externalId}, network: ${token.network.displayName}")
                }

                // Count by type
                val bitcoinMainnet = loadedWallet.bitcoinCoins.count { it.network == BitcoinNetwork.Mainnet }
                val bitcoinTestnet = loadedWallet.bitcoinCoins.count { it.network == BitcoinNetwork.Testnet }
                val solanaMainnet = loadedWallet.solanaCoins.count { it.network == SolanaNetwork.Mainnet }
                val solanaDevnet = loadedWallet.solanaCoins.count { it.network == SolanaNetwork.Devnet }
                val ethMainnet = loadedWallet.evmTokens.count { it is NativeETH && it.network == EthereumNetwork.Mainnet }
                val ethSepolia = loadedWallet.evmTokens.count { it is NativeETH && it.network == EthereumNetwork.Sepolia }
                val usdcMainnet = loadedWallet.evmTokens.count { it is USDCToken && it.network == EthereumNetwork.Mainnet }
                val usdcSepolia = loadedWallet.evmTokens.count { it is USDCToken && it.network == EthereumNetwork.Sepolia }

                Log.d("WalletDetailVM", "==========================================")
                Log.d("WalletDetailVM", "ASSET COUNT SUMMARY:")
                Log.d("WalletDetailVM", "  Bitcoin Mainnet: $bitcoinMainnet")
                Log.d("WalletDetailVM", "  Bitcoin Testnet: $bitcoinTestnet")
                Log.d("WalletDetailVM", "  Solana Mainnet: $solanaMainnet")
                Log.d("WalletDetailVM", "  Solana Devnet: $solanaDevnet")
                Log.d("WalletDetailVM", "  Ethereum Mainnet: $ethMainnet")
                Log.d("WalletDetailVM", "  Ethereum Sepolia: $ethSepolia")
                Log.d("WalletDetailVM", "  USDC Mainnet: $usdcMainnet")
                Log.d("WalletDetailVM", "  USDC Sepolia: $usdcSepolia")

                val totalAssets = bitcoinMainnet + bitcoinTestnet + solanaMainnet + solanaDevnet +
                        ethMainnet + ethSepolia + usdcMainnet + usdcSepolia
                Log.d("WalletDetailVM", "  TOTAL ASSETS: $totalAssets")
                Log.d("WalletDetailVM", "==========================================")

                _uiState.update { it.copy(wallet = loadedWallet) }

                // 2. Load wallet balance from repository
                Log.d("WalletDetailVM", "Loading balance for wallet: $walletId")
                val loadedBalance = walletRepository.getWalletBalance(walletId)

                if (loadedBalance != null) {
                    Log.d("WalletDetailVM", " Balance loaded:")

                    // Bitcoin balances (map of networks)
                    Log.d("WalletDetailVM", "  Bitcoin Balances: ${loadedBalance.bitcoinBalances.size}")
                    loadedBalance.bitcoinBalances.forEach { (network, btcBalance) ->
                        Log.d("WalletDetailVM", "    Bitcoin $network: ${btcBalance.btc} BTC - ${btcBalance.address.take(8)}...")
                    }

                    // Solana balances (map of networks)
                    Log.d("WalletDetailVM", "  Solana Balances: ${loadedBalance.solanaBalances.size}")
                    loadedBalance.solanaBalances.forEach { (network, solBalance) ->
                        Log.d("WalletDetailVM", "    Solana $network: ${solBalance.sol} SOL - ${solBalance.address.take(8)}...")
                    }

                    // EVM balances
                    Log.d("WalletDetailVM", "  EVM Balances: ${loadedBalance.evmBalances.size}")
                    loadedBalance.evmBalances.forEachIndexed { index, evmBalance ->
                        Log.d("WalletDetailVM", "    EVM $index: externalId=${evmBalance.externalTokenId}, balance=${evmBalance.balanceDecimal}, usdValue=${evmBalance.usdValue}")
                    }

                    // Verify we have expected balances
                    if (loadedBalance.evmBalances.size == 4) {
                        Log.d("WalletDetailVM", " All 4 EVM balances present")
                    } else {
                        Log.w("WalletDetailVM", "Expected 4 EVM balances, but got ${loadedBalance.evmBalances.size}")
                    }

                    if (loadedBalance.solanaBalances.size == 2) {
                        Log.d("WalletDetailVM", " Both Solana balances present (Mainnet and Devnet)")
                    } else {
                        Log.w("WalletDetailVM", " Expected 2 Solana balances, but got ${loadedBalance.solanaBalances.size}")
                    }

                    if (loadedBalance.bitcoinBalances.size == 2) {
                        Log.d("WalletDetailVM", " Both Bitcoin balances present (Mainnet and Testnet)")
                    } else {
                        Log.w("WalletDetailVM", "️ Expected 2 Bitcoin balances, but got ${loadedBalance.bitcoinBalances.size}")
                    }
                } else {
                    Log.w("WalletDetailVM", "⚠ No balance found for wallet")
                }

                // 3. Load market percentages
                Log.d("WalletDetailVM", "Loading market percentages...")
                when (val percentagesResult = marketRepository.getLatestPricePercentages()) {
                    is Result.Success -> {
                        Log.d("WalletDetailVM", " Market percentages loaded: ${percentagesResult.data.size} entries")
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
                Log.d("WalletDetailVM", " Loaded ${initialTransactions.size} transactions")

                // Update UI once with all initial data
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

                // 6. Sync balances in background
                Log.d("WalletDetailVM", "Starting background balance sync...")
                viewModelScope.launch {
                    when (val syncResult = syncWalletBalancesUseCase(loadedWallet)) {
                        is Result.Success -> {
                            Log.d("WalletDetailVM", " Balance sync completed successfully")
                            val updatedBalance = walletRepository.getWalletBalance(walletId)

                            if (updatedBalance != null) {
                                Log.d("WalletDetailVM", "Updated balances after sync:")

                                // Bitcoin balances
                                Log.d("WalletDetailVM", "  Bitcoin Balances: ${updatedBalance.bitcoinBalances.size}")
                                updatedBalance.bitcoinBalances.forEach { (network, btcBalance) ->
                                    Log.d("WalletDetailVM", "    Bitcoin $network: ${btcBalance.btc} BTC")
                                }

                                // Solana balances
                                Log.d("WalletDetailVM", "  Solana Balances: ${updatedBalance.solanaBalances.size}")
                                updatedBalance.solanaBalances.forEach { (network, solBalance) ->
                                    Log.d("WalletDetailVM", "    Solana $network: ${solBalance.sol} SOL")
                                }

                                // EVM balances
                                Log.d("WalletDetailVM", "  EVM Balances: ${updatedBalance.evmBalances.size}")
                                updatedBalance.evmBalances.forEachIndexed { index, evmBalance ->
                                    Log.d("WalletDetailVM", "    EVM $index: externalId=${evmBalance.externalTokenId}, balance=${evmBalance.balanceDecimal}")
                                }
                            }

                            _uiState.update { it.copy(balance = updatedBalance) }
                        }
                        is Result.Error -> {
                            Log.e("WalletDetailVM", " Balance sync failed: ${syncResult.message}")
                        }
                        Result.Loading -> {}
                    }
                }

            } catch (e: Exception) {
                Log.e("WalletDetailVM", " Failed to load wallet: ${e.message}", e)
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
                    Log.e("WalletDetailVM", " Error observing transactions: ${e.message}", e)
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