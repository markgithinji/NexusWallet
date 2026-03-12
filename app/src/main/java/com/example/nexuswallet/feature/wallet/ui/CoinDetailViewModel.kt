package com.example.nexuswallet.feature.wallet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.domain.model.NetworkType
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.ethereum.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.solana.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinDetailResult
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumDetailResult
import com.example.nexuswallet.feature.wallet.domain.model.SPLToken
import com.example.nexuswallet.feature.wallet.domain.model.SolanaDetailResult
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetBitcoinDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetEthereumDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetSolanaDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class CoinDetailViewModel @Inject constructor(
    private val getBitcoinDetailUseCase: GetBitcoinDetailUseCase,
    private val getEthereumDetailUseCase: GetEthereumDetailUseCase,
    private val getSolanaDetailUseCase: GetSolanaDetailUseCase,
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
        val networkType: NetworkType? = null,
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

    fun loadCoinDetails(walletId: String, coinType: CoinType, networkType: NetworkType? = null, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // Set loading state
            _state.update {
                it.copy(
                    isLoading = !forceRefresh,
                    isRefreshing = forceRefresh,
                    error = null,
                    coinType = coinType,
                    networkType = networkType,
                )
            }

            Log.d("CoinDetailVM", "=== Loading $coinType details for wallet: $walletId with network: ${networkType?.name} ===")

            // Convert NetworkType to the appropriate domain network object
            val result = when (coinType) {
                CoinType.BITCOIN -> {
                    val bitcoinNetwork = when (networkType) {
                        NetworkType.BITCOIN_TESTNET -> BitcoinNetwork.Testnet
                        else -> BitcoinNetwork.Mainnet
                    }
                    getBitcoinDetailUseCase(walletId, bitcoinNetwork)
                }
                CoinType.ETHEREUM -> {
                    val ethereumNetwork = when (networkType) {
                        NetworkType.ETHEREUM_SEPOLIA -> EthereumNetwork.Sepolia
                        else -> EthereumNetwork.Mainnet
                    }
                    getEthereumDetailUseCase.getEthDetails(walletId, ethereumNetwork)
                }
                CoinType.USDC -> {
                    val ethereumNetwork = when (networkType) {
                        NetworkType.ETHEREUM_SEPOLIA -> EthereumNetwork.Sepolia
                        else -> EthereumNetwork.Mainnet
                    }
                    getEthereumDetailUseCase.getUsdcDetails(walletId, ethereumNetwork)
                }
                CoinType.SOLANA -> {
                    val solanaNetwork = when (networkType) {
                        NetworkType.SOLANA_DEVNET -> SolanaNetwork.Devnet
                        else -> SolanaNetwork.Mainnet
                    }
                    getSolanaDetailUseCase(walletId, solanaNetwork)
                }
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
        val displayTransactions = data.rawTransactions.map { transaction ->
            formatTransactionDisplayUseCase(transaction, CoinType.BITCOIN)
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
                transactions = displayTransactions,
                isLoading = false,
                isRefreshing = false
            )
        }
    }

    private fun updateStateWithEthereumData(data: EthereumDetailResult) {
        val coinType = if (data.token is USDCToken) CoinType.USDC else CoinType.ETHEREUM
        val displayTransactions = data.rawTransactions.map { transaction ->
            formatTransactionDisplayUseCase(transaction, coinType)
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
                transactions = displayTransactions,
                ethGasBalance = data.ethGasBalance,
                evmTokens = listOf(data.token),
                externalTokenId = data.externalTokenId,
                isLoading = false,
                isRefreshing = false,
                coinType = coinType // Update coinType in case it changed (USDC vs ETH)
            )
        }
    }

    private fun updateStateWithSolanaData(data: SolanaDetailResult) {
        val displayTransactions = data.rawTransactions.map { transaction ->
            formatTransactionDisplayUseCase(transaction, CoinType.SOLANA)
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
                transactions = displayTransactions,
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
                currentState.networkType,
                forceRefresh = true
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}