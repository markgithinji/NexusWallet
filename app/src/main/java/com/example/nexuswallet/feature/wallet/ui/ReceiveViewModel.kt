package com.example.nexuswallet.feature.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.NativeETH
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
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
        val network: String = "Mainnet",
        val networkDisplayName: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val copiedToClipboard: Boolean = false,
        val shareUrl: String = ""
    )

    private val _uiState = MutableStateFlow(ReceiveUiState())
    val uiState: StateFlow<ReceiveUiState> = _uiState.asStateFlow()

    fun initialize(walletId: String, coinType: CoinType? = null) {
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

                // If coinType is specified, try to get that specific coin
                val result = if (coinType != null) {
                    getAddressForCoinType(wallet, coinType)
                } else {
                    // Get first available address
                    getFirstAvailableAddress(wallet)
                }

                if (result == null) {
                    _uiState.update {
                        it.copy(
                            error = "No receive address available for this wallet",
                            isLoading = false
                        )
                    }
                    return@launch
                }

                val (address, resolvedCoinType, network, networkDisplayName) = result

                // Create share URL based on coin type
                val shareUrl = when (resolvedCoinType) {
                    CoinType.BITCOIN -> "bitcoin:$address"
                    CoinType.ETHEREUM, CoinType.USDC -> "ethereum:$address"
                    CoinType.SOLANA -> "solana:$address"
                }

                _uiState.update {
                    it.copy(
                        walletId = walletId,
                        walletName = wallet.name,
                        address = address,
                        coinType = resolvedCoinType,
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

    private fun getFirstAvailableAddress(wallet: Wallet): Quadruple<String, CoinType, String, String>? {
        // Check Bitcoin coins
        wallet.bitcoinCoins.firstOrNull()?.let { coin ->
            val networkName = when (coin.network) {
                BitcoinNetwork.Mainnet -> "Mainnet"
                BitcoinNetwork.Testnet -> "Testnet"
            }
            return Quadruple(
                coin.address,
                CoinType.BITCOIN,
                networkName,
                coin.network.toString()
            )
        }

        // Check EVM tokens (Native ETH)
        wallet.evmTokens.firstOrNull { it is NativeETH }?.let { token ->
            return Quadruple(
                token.address,
                CoinType.ETHEREUM,
                token.network.displayName,
                token.network.toString()
            )
        }

        // Check Solana coins
        wallet.solanaCoins.firstOrNull()?.let { coin ->
            val networkName = when (coin.network) {
                SolanaNetwork.Mainnet -> "Mainnet"
                SolanaNetwork.Devnet -> "Devnet"
            }
            return Quadruple(
                coin.address,
                CoinType.SOLANA,
                networkName,
                coin.network.toString()
            )
        }

        // Check USDC tokens
        wallet.evmTokens.firstOrNull { it is USDCToken }?.let { token ->
            return Quadruple(
                token.address,
                CoinType.USDC,
                token.network.displayName,
                token.network.toString()
            )
        }

        return null
    }

    private fun getAddressForCoinType(wallet: Wallet, coinType: CoinType): Quadruple<String, CoinType, String, String>? {
        return when (coinType) {
            CoinType.BITCOIN -> {
                wallet.bitcoinCoins.firstOrNull()?.let { coin ->
                    val networkName = when (coin.network) {
                        BitcoinNetwork.Mainnet -> "Mainnet"
                        BitcoinNetwork.Testnet -> "Testnet"
                    }
                    Quadruple(
                        coin.address,
                        CoinType.BITCOIN,
                        networkName,
                        coin.network.toString()
                    )
                }
            }
            CoinType.ETHEREUM -> {
                wallet.evmTokens.firstOrNull { it is NativeETH }?.let { token ->
                    Quadruple(
                        token.address,
                        CoinType.ETHEREUM,
                        token.network.displayName,
                        token.network.toString()
                    )
                }
            }
            CoinType.SOLANA -> {
                wallet.solanaCoins.firstOrNull()?.let { coin ->
                    val networkName = when (coin.network) {
                        SolanaNetwork.Mainnet -> "Mainnet"
                        SolanaNetwork.Devnet -> "Devnet"
                    }
                    Quadruple(
                        coin.address,
                        CoinType.SOLANA,
                        networkName,
                        coin.network.toString()
                    )
                }
            }
            CoinType.USDC -> {
                wallet.evmTokens.firstOrNull { it is USDCToken }?.let { token ->
                    Quadruple(
                        token.address,
                        CoinType.USDC,
                        token.network.displayName,
                        token.network.toString()
                    )
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