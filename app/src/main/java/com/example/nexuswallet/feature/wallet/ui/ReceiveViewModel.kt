package com.example.nexuswallet.feature.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : ViewModel() {

    data class ReceiveUiState(
        val walletId: String = "",
        val walletName: String = "",
        val address: String = "",
        val network: Network? = null,
        val networkDisplayName: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val copiedToClipboard: Boolean = false,
        val shareUrl: String = ""
    ) {
        val coinType: CoinType? get() = network?.coinType
    }

    private val _uiState = MutableStateFlow(ReceiveUiState())
    val uiState: StateFlow<ReceiveUiState> = _uiState.asStateFlow()

    fun initialize(walletId: String, network: Network) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val wallet = walletRepository.getWallet(walletId)
                if (wallet == null) {
                    _uiState.update {
                        it.copy(
                            error = "Wallet not found",
                            isLoading = false
                        )
                    }
                    return@launch
                }

                val addressResult = getAddressForNetwork(wallet, network)

                if (addressResult == null) {
                    _uiState.update {
                        it.copy(
                            error = "No receive address available for ${network.displayName}",
                            isLoading = false
                        )
                    }
                    return@launch
                }

                val (address, networkDisplayName) = addressResult

                val shareUrl = when (network.coinType) {
                    CoinType.BITCOIN -> "bitcoin:$address"
                    CoinType.ETHEREUM, CoinType.USDC -> "ethereum:$address"
                    CoinType.SOLANA -> "solana:$address"
                }

                _uiState.update {
                    it.copy(
                        walletId = walletId,
                        walletName = wallet.name,
                        address = address,
                        network = network,
                        networkDisplayName = networkDisplayName,
                        shareUrl = shareUrl,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Failed to load wallet: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun getAddressForNetwork(
        wallet: Wallet,
        network: Network
    ): Pair<String, String>? {
        return when (network) {
            is BitcoinNetwork -> {
                wallet.bitcoinCoins.find { it.network == network }?.let { coin ->
                    Pair(coin.address, network.displayName)
                }
            }

            is EthereumNetwork -> {
                when (network.coinType) {
                    CoinType.ETHEREUM -> {
                        wallet.evmTokens.filterIsInstance<NativeETH>()
                            .find { it.network == network }
                            ?.let { token ->
                                Pair(token.address, network.displayName)
                            }
                    }

                    CoinType.USDC -> {
                        wallet.evmTokens.filterIsInstance<USDCToken>()
                            .find { it.network == network }
                            ?.let { token ->
                                Pair(token.address, "${token.symbol} on ${network.displayName}")
                            }
                    }

                    else -> null
                }
            }

            is SolanaNetwork -> {
                wallet.solanaCoins.find { it.network == network }?.let { coin ->
                    Pair(coin.address, network.displayName)
                }
            }
        }
    }

    fun onCopyClicked() {
        _uiState.update { it.copy(copiedToClipboard = true) }
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(copiedToClipboard = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}