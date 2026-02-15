package com.example.nexuswallet.feature.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.WalletType
import dagger.hilt.android.lifecycle.HiltViewModel
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
        val coinType: String = "",
        val network: String = "Mainnet",
        val isLoading: Boolean = false,
        val error: String? = null,
        val copiedToClipboard: Boolean = false,
        val shareUrl: String = ""
    )

    private val _uiState = MutableStateFlow(ReceiveUiState())
    val uiState: StateFlow<ReceiveUiState> = _uiState.asStateFlow()

    fun initialize(walletId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val wallet = walletRepository.getWallet(walletId)
                if (wallet != null) {
                    // Get the first available address (prioritize BTC, ETH, SOL, USDC in that order)
                    val address = wallet.bitcoin?.address
                        ?: wallet.ethereum?.address
                        ?: wallet.solana?.address
                        ?: wallet.usdc?.address
                        ?: ""

                    // Determine coin type and network
                    val (coinType, network) = when {
                        wallet.bitcoin != null -> {
                            "BTC" to wallet.bitcoin.network.name
                        }
                        wallet.ethereum != null -> {
                            "ETH" to wallet.ethereum.network.name
                        }
                        wallet.solana != null -> {
                            "SOL" to "Mainnet" // TODO: use network enum
                        }
                        wallet.usdc != null -> {
                            "USDC" to wallet.usdc.network.name
                        }
                        else -> "Unknown" to "Unknown"
                    }

                    // Create share URL based on coin type
                    val shareUrl = when {
                        wallet.bitcoin != null -> "bitcoin:$address"
                        wallet.ethereum != null -> "ethereum:$address"
                        wallet.solana != null -> "solana:$address"
                        wallet.usdc != null -> "ethereum:$address" // USDC uses ETH addresses
                        else -> address
                    }

                    _uiState.update {
                        it.copy(
                            walletId = walletId,
                            walletName = wallet.name,
                            address = address,
                            coinType = coinType,
                            network = network,
                            shareUrl = shareUrl,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            error = "Wallet not found",
                            isLoading = false
                        )
                    }
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

    fun onCopyClicked() {
        _uiState.update { it.copy(copiedToClipboard = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(copiedToClipboard = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}