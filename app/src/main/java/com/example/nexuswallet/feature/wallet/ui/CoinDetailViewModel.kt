package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.NetworkType
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
import com.example.nexuswallet.feature.wallet.domain.BitcoinDetailResult
import com.example.nexuswallet.feature.wallet.domain.EthereumDetailResult
import com.example.nexuswallet.feature.wallet.domain.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.GetBitcoinDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.GetEthereumDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.GetSolanaDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.SolanaDetailResult
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
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin

@HiltViewModel
class CoinDetailViewModel @Inject constructor(
    private val getBitcoinDetailUseCase: GetBitcoinDetailUseCase,
    private val getEthereumDetailUseCase: GetEthereumDetailUseCase,
    private val getSolanaDetailUseCase: GetSolanaDetailUseCase
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
        val externalTokenId: String? = null,
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(CoinDetailState())
    val state: StateFlow<CoinDetailState> = _state.asStateFlow()

    fun loadCoinDetails(walletId: String, coinType: CoinType, network: String = "", forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // Set loading state
            _state.update {
                it.copy(
                    isLoading = !forceRefresh,
                    isRefreshing = forceRefresh,
                    error = null,
                    coinType = coinType,
                    coinNetwork = network
                )
            }

            Log.d("CoinDetailVM", "=== Loading $coinType details for wallet: $walletId ===")

            val result = when (coinType) {
                CoinType.BITCOIN -> getBitcoinDetailUseCase(walletId, network)
                CoinType.ETHEREUM -> getEthereumDetailUseCase.getEthDetails(walletId, network)
                CoinType.USDC -> getEthereumDetailUseCase.getUsdcDetails(walletId, network)
                CoinType.SOLANA -> getSolanaDetailUseCase(walletId, network)
            }

            // Update UI with result
            when (result) {
                is Result.Success -> {
                    when (val data = result.data) {
                        is BitcoinDetailResult -> updateStateWithBitcoinData(data)
                        is EthereumDetailResult -> updateStateWithEthereumData(data)
                        is SolanaDetailResult -> updateStateWithSolanaData(data)
                    }
                }
                is Result.Error -> {
                    _state.update {
                        it.copy(
                            error = result.message,
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                }
                Result.Loading -> {}
            }
        }
    }

    private fun updateStateWithBitcoinData(data: BitcoinDetailResult) {
        Log.d("CoinDetailVM", "Updating with ${data.transactions.size} Bitcoin transactions")
        data.transactions.forEachIndexed { index, tx ->
            Log.d("CoinDetailVM", "  Tx $index: ${tx.formattedAmount} BTC, ${tx.status}")
        }

        _state.update {
            it.copy(
                walletId = data.walletId,
                address = data.address,
                balance = data.balance,
                balanceFormatted = data.balanceFormatted,
                usdValue = data.usdValue,
                network = data.network,
                networkDisplayName = data.networkDisplayName,
                transactions = data.transactions,
                isLoading = false,
                isRefreshing = false
            )
        }
    }

    private fun updateStateWithEthereumData(data: EthereumDetailResult) {
        _state.update {
            it.copy(
                walletId = data.walletId,
                address = data.address,
                balance = data.balance,
                balanceFormatted = data.balanceFormatted,
                usdValue = data.usdValue,
                network = data.network,
                networkDisplayName = data.networkDisplayName,
                transactions = data.transactions,
                ethGasBalance = data.ethGasBalance,
                evmTokens = listOf(data.token),
                externalTokenId = data.externalTokenId,
                isLoading = false,
                isRefreshing = false
            )
        }
    }

    private fun updateStateWithSolanaData(data: SolanaDetailResult) {
        _state.update {
            it.copy(
                walletId = data.walletId,
                address = data.address,
                balance = data.balance,
                balanceFormatted = data.balanceFormatted,
                usdValue = data.usdValue,
                network = data.network,
                networkDisplayName = data.networkDisplayName,
                transactions = data.transactions,
                splTokens = data.splTokens,
                isLoading = false,
                isRefreshing = false
            )
        }
    }

    fun refresh() {
        val currentState = _state.value
        if (currentState.walletId.isNotEmpty() && currentState.coinType != null) {
            loadCoinDetails(
                currentState.walletId,
                currentState.coinType,
                currentState.coinNetwork,
                forceRefresh = true
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}