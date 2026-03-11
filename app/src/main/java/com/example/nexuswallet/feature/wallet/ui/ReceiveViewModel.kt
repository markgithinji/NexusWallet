package com.example.nexuswallet.feature.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.core.domain.model.NetworkType
import com.example.nexuswallet.feature.wallet.domain.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.NativeETH
import com.example.nexuswallet.feature.wallet.domain.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.USDCToken
import com.example.nexuswallet.feature.wallet.domain.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
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
        val coinType: CoinType = CoinType.BITCOIN,
        val network: NetworkType? = null,
        val networkDisplayName: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val copiedToClipboard: Boolean = false,
        val shareUrl: String = ""
    )

    private val _uiState = MutableStateFlow(ReceiveUiState())
    val uiState: StateFlow<ReceiveUiState> = _uiState.asStateFlow()

    fun initialize(walletId: String, coinType: CoinType, network: NetworkType?) {
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

                // Get address for the specific coin type and network
                val addressResult = getAddressForCoinTypeAndNetwork(wallet, coinType, network)

                if (addressResult == null) {
                    val networkDisplay = network?.displayName ?: "default"
                    _uiState.update {
                        it.copy(
                            error = "No receive address available for $coinType on $networkDisplay",
                            isLoading = false
                        )
                    }
                    return@launch
                }

                val (address, networkDisplayName) = addressResult

                // Create share URL based on coin type
                val shareUrl = when (coinType) {
                    CoinType.BITCOIN -> "bitcoin:$address"
                    CoinType.ETHEREUM, CoinType.USDC -> "ethereum:$address"
                    CoinType.SOLANA -> "solana:$address"
                }

                _uiState.update {
                    it.copy(
                        walletId = walletId,
                        walletName = wallet.name,
                        address = address,
                        coinType = coinType,
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

    private fun getAddressForCoinTypeAndNetwork(
        wallet: Wallet,
        coinType: CoinType,
        network: NetworkType?
    ): Pair<String, String>? {
        return when (coinType) {
            CoinType.BITCOIN -> {
                val bitcoinNetwork = when (network) {
                    NetworkType.BITCOIN_MAINNET -> BitcoinNetwork.Mainnet
                    NetworkType.BITCOIN_TESTNET -> BitcoinNetwork.Testnet
                    else -> BitcoinNetwork.Mainnet // Default to Mainnet
                }
                wallet.bitcoinCoins.find { it.network == bitcoinNetwork }?.let { coin ->
                    val displayName = when (coin.network) {
                        BitcoinNetwork.Mainnet -> "Bitcoin Mainnet"
                        BitcoinNetwork.Testnet -> "Bitcoin Testnet"
                    }
                    Pair(coin.address, displayName)
                }
            }
            CoinType.ETHEREUM -> {
                val ethNetwork = when (network) {
                    NetworkType.ETHEREUM_MAINNET -> EthereumNetwork.Mainnet
                    NetworkType.ETHEREUM_SEPOLIA -> EthereumNetwork.Sepolia
                    else -> EthereumNetwork.Mainnet // Default to Mainnet
                }
                wallet.evmTokens.filterIsInstance<NativeETH>()
                    .find { it.network == ethNetwork }
                    ?.let { token ->
                        Pair(token.address, token.network.displayName)
                    }
            }
            CoinType.SOLANA -> {
                val solanaNetwork = when (network) {
                    NetworkType.SOLANA_MAINNET -> SolanaNetwork.Mainnet
                    NetworkType.SOLANA_DEVNET -> SolanaNetwork.Devnet
                    else -> SolanaNetwork.Mainnet // Default to Mainnet
                }
                wallet.solanaCoins.find { it.network == solanaNetwork }?.let { coin ->
                    val displayName = when (coin.network) {
                        SolanaNetwork.Mainnet -> "Solana Mainnet"
                        SolanaNetwork.Devnet -> "Solana Devnet"
                    }
                    Pair(coin.address, displayName)
                }
            }
            CoinType.USDC -> {
                val ethNetwork = when (network) {
                    NetworkType.ETHEREUM_MAINNET -> EthereumNetwork.Mainnet
                    NetworkType.ETHEREUM_SEPOLIA -> EthereumNetwork.Sepolia
                    else -> EthereumNetwork.Mainnet // Default to Mainnet
                }
                wallet.evmTokens.filterIsInstance<USDCToken>()
                    .find { it.network == ethNetwork }
                    ?.let { token ->
                        Pair(token.address, "${token.symbol} on ${token.network.displayName}")
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

// Helper data class for returning multiple values
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)