package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.SyncBitcoinTransactionsUseCase
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.NativeETHTransaction
import com.example.nexuswallet.feature.coin.ethereum.SyncEthereumTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.TokenTransaction
import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SyncSolanaTransactionsUseCase
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CoinDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val evmTransactionRepository: EVMTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val syncBitcoinTransactionsUseCase: SyncBitcoinTransactionsUseCase,
    private val syncEthereumTransactionsUseCase: SyncEthereumTransactionsUseCase,
    private val syncSolanaTransactionsUseCase: SyncSolanaTransactionsUseCase,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase
) : ViewModel() {

    data class CoinDetailState(
        val walletId: String = "",
        val address: String = "",
        val balance: String = "0",
        val balanceFormatted: String = "0",
        val usdValue: Double = 0.0,
        val network: String = "",
        val networkDisplayName: String = "",
        val coinType: CoinType? = null,
        val coinNetwork: String = "",
        val ethGasBalance: BigDecimal? = null,
        val splTokens: List<SPLToken> = emptyList(),
        val evmTokens: List<EVMToken> = emptyList(),
        val transactions: List<TransactionDisplayInfo> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        val externalTokenId: String? = null
    )

    private val _state = MutableStateFlow(CoinDetailState())
    val state: StateFlow<CoinDetailState> = _state.asStateFlow()

    fun loadCoinDetails(walletId: String, coinType: CoinType, network: String = "") {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    coinType = coinType,
                    coinNetwork = network
                )
            }

            try {
                Log.d("CoinDetailVM", "=== Loading $coinType details for wallet: $walletId with network: $network ===")

                // Sync transactions based on coin type
                when (coinType) {
                    CoinType.BITCOIN -> {
                        Log.d("CoinDetailVM", "Syncing Bitcoin transactions for network: $network...")
                        syncBitcoinTransactionsUseCase(walletId, network)
                    }
                    CoinType.ETHEREUM, CoinType.USDC -> {
                        Log.d("CoinDetailVM", "Syncing EVM transactions...")
                        syncEthereumTransactionsUseCase(walletId)
                    }
                    CoinType.SOLANA -> {
                        Log.d("CoinDetailVM", "Syncing Solana transactions for network: $network...")
                        syncSolanaTransactionsUseCase(walletId, network)
                    }
                }

                val wallet = walletRepository.getWallet(walletId) ?: throw Exception("Wallet not found")
                Log.d("CoinDetailVM", "Wallet loaded: ${wallet.name}")

                val balance = walletRepository.getWalletBalance(walletId)
                Log.d("CoinDetailVM", "Balance loaded: ${balance?.evmBalances?.size} EVM balances")

                // Create a map of externalTokenId -> EVMBalance for quick lookup
                val balanceMap = balance?.evmBalances?.associateBy { it.externalTokenId } ?: emptyMap()

                // Update state with coin details based on coin type and network
                when (coinType) {
                    CoinType.BITCOIN -> {
                        // Find the specific Bitcoin coin by network
                        val bitcoinCoin = wallet.bitcoinCoins.find {
                            when (network.lowercase()) {
                                "mainnet" -> it.network == BitcoinNetwork.Mainnet
                                "testnet" -> it.network == BitcoinNetwork.Testnet
                                else -> true
                            }
                        } ?: wallet.bitcoinCoins.firstOrNull()
                        ?: throw Exception("Bitcoin not enabled")

                        // Get the specific balance for this network
                        val networkKey = when (bitcoinCoin.network) {
                            BitcoinNetwork.Mainnet -> "mainnet"
                            BitcoinNetwork.Testnet -> "testnet"
                        }
                        val coinBalance = balance?.bitcoinBalances?.get(networkKey)

                        _state.update {
                            it.copy(
                                walletId = walletId,
                                address = bitcoinCoin.address,
                                balance = coinBalance?.btc ?: "0",
                                balanceFormatted = "${coinBalance?.btc ?: "0"} BTC",
                                usdValue = coinBalance?.usdValue ?: 0.0,
                                network = bitcoinCoin.network.name,
                                networkDisplayName = if (bitcoinCoin.network == BitcoinNetwork.Mainnet) "Mainnet" else "Testnet"
                            )
                        }
                    }

                    CoinType.ETHEREUM -> {
                        // Find the specific ETH token by network
                        val nativeEth = wallet.evmTokens.filterIsInstance<NativeETH>().find {
                            when (network.lowercase()) {
                                "mainnet" -> it.network == EthereumNetwork.Mainnet
                                "sepolia" -> it.network == EthereumNetwork.Sepolia
                                else -> true
                            }
                        } ?: wallet.evmTokens.filterIsInstance<NativeETH>().firstOrNull()
                        ?: throw Exception("Ethereum not enabled")

                        val ethBalance = balanceMap[nativeEth.externalId]

                        _state.update {
                            it.copy(
                                walletId = walletId,
                                address = nativeEth.address,
                                balance = ethBalance?.balanceDecimal ?: "0",
                                balanceFormatted = "${ethBalance?.balanceDecimal ?: "0"} ETH",
                                usdValue = ethBalance?.usdValue ?: 0.0,
                                network = nativeEth.network.displayName,
                                networkDisplayName = nativeEth.network.displayName,
                                evmTokens = listOf(nativeEth),
                                externalTokenId = nativeEth.externalId
                            )
                        }
                    }

                    CoinType.SOLANA -> {
                        // Find the specific Solana coin by network
                        val solanaCoin = wallet.solanaCoins.find {
                            when (network.lowercase()) {
                                "mainnet" -> it.network == SolanaNetwork.Mainnet
                                "devnet" -> it.network == SolanaNetwork.Devnet
                                else -> true
                            }
                        } ?: wallet.solanaCoins.firstOrNull()
                        ?: throw Exception("Solana not enabled")

                        // Get the specific balance for this network
                        val networkKey = when (solanaCoin.network) {
                            SolanaNetwork.Mainnet -> "mainnet"
                            SolanaNetwork.Devnet -> "devnet"
                        }
                        val coinBalance = balance?.solanaBalances?.get(networkKey)

                        _state.update {
                            it.copy(
                                walletId = walletId,
                                address = solanaCoin.address,
                                balance = coinBalance?.sol ?: "0",
                                balanceFormatted = "${coinBalance?.sol ?: "0"} SOL",
                                usdValue = coinBalance?.usdValue ?: 0.0,
                                network = solanaCoin.network.name,
                                networkDisplayName = when (solanaCoin.network) {
                                    SolanaNetwork.Mainnet -> "Mainnet"
                                    SolanaNetwork.Devnet -> "Devnet"
                                },
                                splTokens = solanaCoin.splTokens
                            )
                        }
                    }

                    CoinType.USDC -> {
                        // Find the specific USDC token by network
                        val usdcToken = wallet.evmTokens.filterIsInstance<USDCToken>().find {
                            when (network.lowercase()) {
                                "mainnet" -> it.network == EthereumNetwork.Mainnet
                                "sepolia" -> it.network == EthereumNetwork.Sepolia
                                else -> true
                            }
                        } ?: wallet.evmTokens.filterIsInstance<USDCToken>().firstOrNull()
                        ?: throw Exception("USDC not enabled")

                        val usdcBalance = balanceMap[usdcToken.externalId]

                        // Get ETH balance for gas
                        val nativeEth = wallet.evmTokens.filterIsInstance<NativeETH>().find {
                            it.network == usdcToken.network
                        }
                        val ethGasBalance = nativeEth?.let {
                            balanceMap[it.externalId]?.balanceDecimal?.toBigDecimalOrNull()
                        }

                        _state.update {
                            it.copy(
                                walletId = walletId,
                                address = usdcToken.address,
                                balance = usdcBalance?.balanceDecimal ?: "0",
                                balanceFormatted = "${usdcBalance?.balanceDecimal ?: "0"} USDC",
                                usdValue = usdcBalance?.usdValue ?: 0.0,
                                network = usdcToken.network.displayName,
                                networkDisplayName = usdcToken.network.displayName,
                                ethGasBalance = ethGasBalance,
                                evmTokens = listOf(usdcToken),
                                externalTokenId = usdcToken.externalId
                            )
                        }
                    }
                }

                // Load transactions with network filter
                loadTransactions(walletId, coinType, network)

            } catch (e: Exception) {
                Log.e("CoinDetailVM", "Error loading $coinType details", e)
                _state.update {
                    it.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadTransactions(walletId: String, coinType: CoinType, network: String = "") {
        viewModelScope.launch {
            try {
                when (coinType) {
                    CoinType.BITCOIN -> {
                        bitcoinTransactionRepository.getTransactions(walletId, network).collect { txs ->
                            val displayTransactions = formatTransactionDisplayUseCase.formatTransactionList(txs, coinType)
                            _state.update {
                                it.copy(
                                    transactions = displayTransactions,
                                    isLoading = false
                                )
                            }
                        }
                    }

                    CoinType.ETHEREUM, CoinType.USDC -> {
                        evmTransactionRepository.getTransactions(walletId).collect { txs ->
                            val currentExternalTokenId = _state.value.externalTokenId

                            val filteredTxs = when (coinType) {
                                CoinType.ETHEREUM -> {
                                    txs.filterIsInstance<NativeETHTransaction>()
                                }
                                CoinType.USDC -> {
                                    txs.filterIsInstance<TokenTransaction>()
                                        .filter { tx ->
                                            tx.tokenSymbol == "USDC" &&
                                                    tx.tokenExternalId == currentExternalTokenId
                                        }
                                }
                                else -> emptyList()
                            }

                            val displayTransactions = formatTransactionDisplayUseCase.formatTransactionList(filteredTxs, coinType)
                            _state.update {
                                it.copy(
                                    transactions = displayTransactions,
                                    isLoading = false
                                )
                            }
                        }
                    }

                    CoinType.SOLANA -> {
                        solanaTransactionRepository.getTransactions(walletId, network).collect { txs ->
                            val solTxs = txs.filter { it.tokenSymbol == null }
                            val displayTransactions = formatTransactionDisplayUseCase.formatTransactionList(solTxs, coinType)
                            _state.update {
                                it.copy(
                                    transactions = displayTransactions,
                                    isLoading = false
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CoinDetailVM", "Error loading transactions", e)
                _state.update {
                    it.copy(
                        transactions = emptyList(),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun refresh() {
        val currentState = _state.value
        if (currentState.walletId.isNotEmpty() && currentState.coinType != null) {
            loadCoinDetails(currentState.walletId, currentState.coinType, currentState.coinNetwork)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // Helper to get formatted gas balance
    fun getFormattedGasBalance(): String {
        return state.value.ethGasBalance?.let {
            NumberFormat.getNumberInstance(Locale.US).format(it)
        } ?: "0"
    }

    // Helper to check if user has enough gas for transactions
    fun hasEnoughGas(requiredGas: BigDecimal = BigDecimal("0.001")): Boolean {
        return (state.value.ethGasBalance ?: BigDecimal.ZERO) >= requiredGas
    }

    // Helper to get the current token for display
    fun getCurrentToken(): EVMToken? {
        return state.value.evmTokens.firstOrNull()
    }
}