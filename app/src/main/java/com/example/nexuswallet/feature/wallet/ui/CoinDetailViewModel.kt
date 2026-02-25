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
    private val syncUSDTransactionsUseCase: SyncUSDTransactionsUseCase
) : ViewModel() {

    data class TransactionDisplayInfo(
        val id: String,
        val isIncoming: Boolean,
        val amount: String,
        val formattedAmount: String,
        val status: TransactionStatus,
        val timestamp: Long,
        val formattedTime: String,
        val hash: String?
    )

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

                        Log.d("CoinDetailVM", "BTC Coin Address: ${coin.address}")
                        Log.d("CoinDetailVM", "BTC Balance from repo: ${coinBalance?.btc}")

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

                        Log.d("CoinDetailVM", "ETH Address: ${coin.address}")
                        Log.d("CoinDetailVM", "ETH Balance from repo: ${coinBalance?.eth}")

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

                        Log.d("CoinDetailVM", "SOL Address: ${coin.address}")
                        Log.d("CoinDetailVM", "SOL Balance from repo: ${coinBalance?.sol}")

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

                        Log.d("CoinDetailVM", "USDC Address: ${coin.address}")
                        Log.d("CoinDetailVM", "USDC Balance from repo: ${coinBalance?.amountDecimal}")

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

                Log.d("CoinDetailVM", "Final state balance: ${_state.value.balance}")
                Log.d("CoinDetailVM", "Final state formatted: ${_state.value.balanceFormatted}")

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
                        Log.d("CoinDetailVM", "BTC Transactions loaded: ${txs.size}")
                        val displayTransactions = txs.map { tx ->
                            tx.toTransactionDisplayInfo(coinType)
                        }
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
                        Log.d("CoinDetailVM", "ETH Transactions loaded: ${txs.size}")
                        val displayTransactions = txs.map { tx ->
                            tx.toTransactionDisplayInfo(coinType)
                        }
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
                        Log.d("CoinDetailVM", "SOL Transactions loaded: ${txs.size}")
                        val displayTransactions = txs.map { tx ->
                            tx.toTransactionDisplayInfo(coinType)
                        }
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
                        Log.d("CoinDetailVM", "USDC Transactions loaded: ${txs.size}")
                        val displayTransactions = txs.map { tx ->
                            tx.toTransactionDisplayInfo(coinType)
                        }
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

    private fun formatTransactionAmount(amount: String): String {
        return try {
            val amountDecimal = amount.toBigDecimal()
            when {
                amountDecimal < BigDecimal("0.000001") ->
                    amountDecimal.setScale(8, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()
                amountDecimal < BigDecimal("0.001") ->
                    amountDecimal.setScale(6, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()
                amountDecimal < BigDecimal("1") ->
                    amountDecimal.setScale(4, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()
                else ->
                    amountDecimal.setScale(2, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()
            }
        } catch (e: Exception) {
            amount
        }
    }

    private fun BitcoinTransaction.toTransactionDisplayInfo(coinType: CoinType): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = id,
            isIncoming = isIncoming,
            amount = amountBtc,
            formattedAmount = formatTransactionAmount(amountBtc),
            status = status,
            timestamp = timestamp,
            formattedTime = formatTimestamp(timestamp),
            hash = txHash
        )
    }

    private fun EthereumTransaction.toTransactionDisplayInfo(coinType: CoinType): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = id,
            isIncoming = isIncoming,
            amount = amountEth,
            formattedAmount = formatTransactionAmount(amountEth),
            status = status,
            timestamp = timestamp,
            formattedTime = formatTimestamp(timestamp),
            hash = txHash
        )
    }

    private fun SolanaTransaction.toTransactionDisplayInfo(coinType: CoinType): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = id,
            isIncoming = isIncoming,
            amount = amountSol,
            formattedAmount = formatTransactionAmount(amountSol),
            status = status,
            timestamp = timestamp,
            formattedTime = formatTimestamp(timestamp),
            hash = signature
        )
    }

    private fun USDCTransaction.toTransactionDisplayInfo(coinType: CoinType): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = id,
            isIncoming = isIncoming,
            amount = amountDecimal,
            formattedAmount = formatTransactionAmount(amountDecimal),
            status = status,
            timestamp = timestamp,
            formattedTime = formatTimestamp(timestamp),
            hash = txHash
        )
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} hr ago"
            else -> {
                val date = Date(timestamp)
                SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
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