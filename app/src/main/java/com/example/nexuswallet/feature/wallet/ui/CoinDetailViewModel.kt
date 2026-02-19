package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.bitcoin.SyncBitcoinTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.SyncEthereumTransactionsUseCase
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SyncSolanaTransactionsUseCase
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.domain.GetETHBalanceForGasUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.SyncUSDTransactionsUseCase
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
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

    data class CoinDetailState(
        val walletId: String = "",
        val address: String = "",
        val balance: String = "0",
        val balanceFormatted: String = "0",
        val usdValue: Double = 0.0,
        val network: String = "",
        val ethGasBalance: BigDecimal? = null
    )

    private val _coinDetailState = MutableStateFlow<CoinDetailState?>(null)
    val coinDetailState: StateFlow<CoinDetailState?> = _coinDetailState

    private val _transactions = MutableStateFlow<List<Any>>(emptyList())
    val transactions: StateFlow<List<Any>> = _transactions

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadCoinDetails(walletId: String, coinType: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d("CoinDetailVM", "=== Loading $coinType details for wallet: $walletId ===")

                // Sync transactions before loading based on coin type
                when (coinType) {
                    "BTC" -> {
                        Log.d("CoinDetailVM", "Syncing Bitcoin transactions...")
                        syncBitcoinTransactionsUseCase(walletId)
                    }
                    "ETH" -> {
                        Log.d("CoinDetailVM", "Syncing Ethereum transactions...")
                        syncEthereumTransactionsUseCase(walletId)
                    }
                    "SOL" -> {
                        Log.d("CoinDetailVM", "Syncing Solana transactions...")
                        syncSolanaTransactionsUseCase(walletId)
                    }
                    "USDC" -> {
                        Log.d("CoinDetailVM", "Syncing USDC transactions...")
                        syncUSDTransactionsUseCase(walletId)
                    }
                }

                val wallet = walletRepository.getWallet(walletId) ?: throw Exception("Wallet not found")
                Log.d("CoinDetailVM", "Wallet loaded: ${wallet.name}")

                val balance = walletRepository.getWalletBalance(walletId)
                Log.d("CoinDetailVM", "Balance loaded: $balance")

                val state = when (coinType) {
                    "BTC" -> {
                        val coin = wallet.bitcoin ?: throw Exception("Bitcoin not enabled")
                        val coinBalance = balance?.bitcoin

                        Log.d("CoinDetailVM", "BTC Coin Address: ${coin.address}")
                        Log.d("CoinDetailVM", "BTC Balance from repo: ${coinBalance?.btc}")
                        Log.d("CoinDetailVM", "BTC satoshis: ${coinBalance?.satoshis}")

                        CoinDetailState(
                            walletId = walletId,
                            address = coin.address,
                            balance = coinBalance?.btc ?: "0",
                            balanceFormatted = "${coinBalance?.btc ?: "0"} BTC",
                            usdValue = coinBalance?.usdValue ?: 0.0,
                            network = coin.network.name
                        )
                    }
                    "ETH" -> {
                        val coin = wallet.ethereum ?: throw Exception("Ethereum not enabled")
                        val coinBalance = balance?.ethereum

                        Log.d("CoinDetailVM", "ETH Address: ${coin.address}")
                        Log.d("CoinDetailVM", "ETH Balance from repo: ${coinBalance?.eth}")

                        CoinDetailState(
                            walletId = walletId,
                            address = coin.address,
                            balance = coinBalance?.eth ?: "0",
                            balanceFormatted = "${coinBalance?.eth ?: "0"} ETH",
                            usdValue = coinBalance?.usdValue ?: 0.0,
                            network = coin.network.displayName
                        )
                    }
                    "SOL" -> {
                        val coin = wallet.solana ?: throw Exception("Solana not enabled")
                        val coinBalance = balance?.solana

                        Log.d("CoinDetailVM", "SOL Address: ${coin.address}")
                        Log.d("CoinDetailVM", "SOL Balance from repo: ${coinBalance?.sol}")

                        CoinDetailState(
                            walletId = walletId,
                            address = coin.address,
                            balance = coinBalance?.sol ?: "0",
                            balanceFormatted = "${coinBalance?.sol ?: "0"} SOL",
                            usdValue = coinBalance?.usdValue ?: 0.0,
                            network = "Mainnet"
                        )
                    }
                    "USDC" -> {
                        val coin = wallet.usdc ?: throw Exception("USDC not enabled")
                        val coinBalance = balance?.usdc

                        val ethBalanceResult = getETHBalanceForGasUseCase(walletId)
                        val ethBalance = when (ethBalanceResult) {
                            is Result.Success -> ethBalanceResult.data
                            else -> null
                        }

                        Log.d("CoinDetailVM", "USDC Address: ${coin.address}")
                        Log.d("CoinDetailVM", "USDC Balance from repo: ${coinBalance?.amountDecimal}")

                        CoinDetailState(
                            walletId = walletId,
                            address = coin.address,
                            balance = coinBalance?.amountDecimal ?: "0",
                            balanceFormatted = "${coinBalance?.amountDecimal ?: "0"} USDC",
                            usdValue = coinBalance?.usdValue ?: 0.0,
                            network = coin.network.displayName,
                            ethGasBalance = ethBalance
                        )
                    }
                    else -> throw Exception("Unknown coin type")
                }

                Log.d("CoinDetailVM", "Final state balance: ${state.balance}")
                Log.d("CoinDetailVM", "Final state formatted: ${state.balanceFormatted}")

                _coinDetailState.value = state
                loadTransactions(walletId, coinType)

            } catch (e: Exception) {
                Log.e("CoinDetailVM", "Error: ${e.message}")
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadTransactions(walletId: String, coinType: String) {
        viewModelScope.launch {
            when (coinType) {
                "BTC" -> {
                    bitcoinTransactionRepository.getTransactions(walletId).collect { txs ->
                        Log.d("CoinDetailVM", "BTC Transactions loaded: ${txs.size}")
                        txs.take(3).forEachIndexed { i, tx ->
                            Log.d("CoinDetailVM", "  BTC Tx $i: amount=${tx.amountBtc}, incoming=${(tx as? BitcoinTransaction)?.isIncoming}")
                        }
                        _transactions.value = txs
                    }
                }
                "ETH" -> {
                    ethereumTransactionRepository.getTransactions(walletId).collect { txs ->
                        Log.d("CoinDetailVM", "ETH Transactions loaded: ${txs.size}")
                        txs.take(3).forEachIndexed { i, tx ->
                            Log.d("CoinDetailVM", "  ETH Tx $i: amount=${tx.amountEth}, incoming=${tx.isIncoming}")
                        }
                        _transactions.value = txs
                    }
                }
                "SOL" -> {
                    solanaTransactionRepository.getTransactions(walletId).collect { txs ->
                        Log.d("CoinDetailVM", "SOL Transactions loaded: ${txs.size}")
                        txs.take(3).forEachIndexed { i, tx ->
                            Log.d("CoinDetailVM", "  SOL Tx $i: amount=${tx.amountSol}, incoming=${tx.isIncoming}")
                        }
                        _transactions.value = txs
                    }
                }
                "USDC" -> {
                    usdcTransactionRepository.getTransactions(walletId).collect { txs ->
                        Log.d("CoinDetailVM", "USDC Transactions loaded: ${txs.size}")
                        _transactions.value = txs
                    }
                }
            }
        }
    }

    fun refresh() {
        val currentState = _coinDetailState.value ?: return
        Log.d("CoinDetailVM", "Refreshing...")
        loadCoinDetails(currentState.walletId,
            when {
                currentState.balanceFormatted.contains("BTC") -> "BTC"
                currentState.balanceFormatted.contains("ETH") -> "ETH"
                currentState.balanceFormatted.contains("SOL") -> "SOL"
                currentState.balanceFormatted.contains("USDC") -> "USDC"
                else -> ""
            }
        )
    }
}