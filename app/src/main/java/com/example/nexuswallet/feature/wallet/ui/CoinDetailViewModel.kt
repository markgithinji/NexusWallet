package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.bitcoin.SyncBitcoinTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransaction
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.SyncEthereumTransactionsUseCase
import com.example.nexuswallet.feature.coin.solana.SolanaTransaction
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SyncSolanaTransactionsUseCase
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.domain.GetETHBalanceForGasUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.SyncUSDTransactionsUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransaction
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.emptyList

@HiltViewModel
class CoinDetailViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val bitcoinTransactionRepository: BitcoinTransactionRepository,
    private val ethereumTransactionRepository: EthereumTransactionRepository,
    private val solanaTransactionRepository: SolanaTransactionRepository,
    private val usdcTransactionRepository: USDCTransactionRepository,
    private val getETHBalanceForGasUseCase: GetETHBalanceForGasUseCase,
    private val syncBitcoinTransactionsUseCase: SyncBitcoinTransactionsUseCase,
    private val syncEthereumTransactionsUseCase: SyncEthereumTransactionsUseCase,
    private val syncSolanaTransactionsUseCase: SyncSolanaTransactionsUseCase,
    private val syncUSDTransactionsUseCase: SyncUSDTransactionsUseCase,
    private val formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase
) : ViewModel() {

    data class CoinDetailState(
        val walletId: String = "",
        val address: String = "",
        val balance: String = "0",
        val balanceFormatted: String = "0",
        val usdValue: Double = 0.0,
        val network: String = "",
        val coinType: CoinType? = null,
        val ethGasBalance: BigDecimal? = null,
        val transactions: List<TransactionDisplayInfo> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(CoinDetailState())
    val state: StateFlow<CoinDetailState> = _state.asStateFlow()

    fun loadCoinDetails(walletId: String, coinType: CoinType) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    coinType = coinType
                )
            }

            try {
                Log.d("CoinDetailVM", "=== Loading $coinType details for wallet: $walletId ===")

                // Sync transactions based on coin type
                when (coinType) {
                    CoinType.BITCOIN -> {
                        Log.d("CoinDetailVM", "Syncing Bitcoin transactions...")
                        syncBitcoinTransactionsUseCase(walletId)
                    }
                    CoinType.ETHEREUM -> {
                        Log.d("CoinDetailVM", "Syncing Ethereum transactions...")
                        syncEthereumTransactionsUseCase(walletId)
                    }
                    CoinType.SOLANA -> {
                        Log.d("CoinDetailVM", "Syncing Solana transactions...")
                        syncSolanaTransactionsUseCase(walletId)
                    }
                    CoinType.USDC -> {
                        Log.d("CoinDetailVM", "Syncing USDC transactions...")
                        syncUSDTransactionsUseCase(walletId)
                    }
                }

                val wallet = walletRepository.getWallet(walletId) ?: throw Exception("Wallet not found")
                Log.d("CoinDetailVM", "Wallet loaded: ${wallet.name}")

                val balance = walletRepository.getWalletBalance(walletId)
                Log.d("CoinDetailVM", "Balance loaded: $balance")

                // Update state with coin details
                when (coinType) {
                    CoinType.BITCOIN -> {
                        val coin = wallet.bitcoin ?: throw Exception("Bitcoin not enabled")
                        val coinBalance = balance?.bitcoin

                        _state.update {
                            it.copy(
                                walletId = walletId,
                                address = coin.address,
                                balance = coinBalance?.btc ?: "0",
                                balanceFormatted = "${coinBalance?.btc ?: "0"} BTC",
                                usdValue = coinBalance?.usdValue ?: 0.0,
                                network = coin.network.name
                            )
                        }
                    }
                    CoinType.ETHEREUM -> {
                        val coin = wallet.ethereum ?: throw Exception("Ethereum not enabled")
                        val coinBalance = balance?.ethereum

                        _state.update {
                            it.copy(
                                walletId = walletId,
                                address = coin.address,
                                balance = coinBalance?.eth ?: "0",
                                balanceFormatted = "${coinBalance?.eth ?: "0"} ETH",
                                usdValue = coinBalance?.usdValue ?: 0.0,
                                network = coin.network.displayName
                            )
                        }
                    }
                    CoinType.SOLANA -> {
                        val coin = wallet.solana ?: throw Exception("Solana not enabled")
                        val coinBalance = balance?.solana

                        _state.update {
                            it.copy(
                                walletId = walletId,
                                address = coin.address,
                                balance = coinBalance?.sol ?: "0",
                                balanceFormatted = "${coinBalance?.sol ?: "0"} SOL",
                                usdValue = coinBalance?.usdValue ?: 0.0,
                                network = "Mainnet"
                            )
                        }
                    }
                    CoinType.USDC -> {
                        val coin = wallet.usdc ?: throw Exception("USDC not enabled")
                        val coinBalance = balance?.usdc

                        val ethBalanceResult = getETHBalanceForGasUseCase(walletId)
                        val ethBalance = when (ethBalanceResult) {
                            is Result.Success -> ethBalanceResult.data
                            else -> null
                        }

                        _state.update {
                            it.copy(
                                walletId = walletId,
                                address = coin.address,
                                balance = coinBalance?.amountDecimal ?: "0",
                                balanceFormatted = "${coinBalance?.amountDecimal ?: "0"} USDC",
                                usdValue = coinBalance?.usdValue ?: 0.0,
                                network = coin.network.displayName,
                                ethGasBalance = ethBalance
                            )
                        }
                    }
                }

                // Load transactions
                loadTransactions(walletId, coinType)

            } catch (e: Exception) {
                Log.e("CoinDetailVM", "Error: ${e.message}")
                _state.update {
                    it.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadTransactions(walletId: String, coinType: CoinType) {
        viewModelScope.launch {
            when (coinType) {
                CoinType.BITCOIN -> {
                    bitcoinTransactionRepository.getTransactions(walletId).collect { txs ->
                        val displayTransactions = formatTransactionDisplayUseCase.formatTransactionList(txs, coinType)
                        _state.update {
                            it.copy(
                                transactions = displayTransactions,
                                isLoading = false
                            )
                        }
                    }
                }
                CoinType.ETHEREUM -> {
                    ethereumTransactionRepository.getTransactions(walletId).collect { txs ->
                        val displayTransactions = formatTransactionDisplayUseCase.formatTransactionList(txs, coinType)
                        _state.update {
                            it.copy(
                                transactions = displayTransactions,
                                isLoading = false
                            )
                        }
                    }
                }
                CoinType.SOLANA -> {
                    solanaTransactionRepository.getTransactions(walletId).collect { txs ->
                        val displayTransactions = formatTransactionDisplayUseCase.formatTransactionList(txs, coinType)
                        _state.update {
                            it.copy(
                                transactions = displayTransactions,
                                isLoading = false
                            )
                        }
                    }
                }
                CoinType.USDC -> {
                    usdcTransactionRepository.getTransactions(walletId).collect { txs ->
                        val displayTransactions = formatTransactionDisplayUseCase.formatTransactionList(txs, coinType)
                        _state.update {
                            it.copy(
                                transactions = displayTransactions,
                                isLoading = false
                            )
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        val currentState = _state.value
        if (currentState.walletId.isNotEmpty() && currentState.coinType != null) {
            loadCoinDetails(currentState.walletId, currentState.coinType)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}