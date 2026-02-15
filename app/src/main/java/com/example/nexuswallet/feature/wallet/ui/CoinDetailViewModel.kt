package com.example.nexuswallet.feature.wallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SolanaTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.domain.GetETHBalanceForGasUseCase
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
    private val getETHBalanceForGasUseCase: GetETHBalanceForGasUseCase
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
                val wallet = walletRepository.getWallet(walletId) ?: throw Exception("Wallet not found")
                val balance = walletRepository.getWalletBalance(walletId)

                val state = when (coinType) {
                    "BTC" -> {
                        val coin = wallet.bitcoin ?: throw Exception("Bitcoin not enabled")
                        val coinBalance = balance?.bitcoin
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
                        CoinDetailState(
                            walletId = walletId,
                            address = coin.address,
                            balance = coinBalance?.eth ?: "0",
                            balanceFormatted = "${coinBalance?.eth ?: "0"} ETH",
                            usdValue = coinBalance?.usdValue ?: 0.0,
                            network = coin.network.name
                        )
                    }
                    "SOL" -> {
                        val coin = wallet.solana ?: throw Exception("Solana not enabled")
                        val coinBalance = balance?.solana
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

                        CoinDetailState(
                            walletId = walletId,
                            address = coin.address,
                            balance = coinBalance?.amountDecimal ?: "0",
                            balanceFormatted = "${coinBalance?.amountDecimal ?: "0"} USDC",
                            usdValue = coinBalance?.usdValue ?: 0.0,
                            network = coin.network.name,
                            ethGasBalance = ethBalance
                        )
                    }
                    else -> throw Exception("Unknown coin type")
                }

                _coinDetailState.value = state
                loadTransactions(walletId, coinType)

            } catch (e: Exception) {
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
                        _transactions.value = txs
                    }
                }
                "ETH" -> {
                    ethereumTransactionRepository.getTransactions(walletId).collect { txs ->
                        _transactions.value = txs
                    }
                }
                "SOL" -> {
                    solanaTransactionRepository.getTransactions(walletId).collect { txs ->
                        _transactions.value = txs
                    }
                }
                "USDC" -> {
                    usdcTransactionRepository.getTransactions(walletId).collect { txs ->
                        _transactions.value = txs
                    }
                }
            }
        }
    }

    fun refresh() {
        val currentState = _coinDetailState.value ?: return
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