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
        val walletType: WalletType = WalletType.BITCOIN,
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
                    val address = when (wallet) {
                        is BitcoinWallet -> wallet.address
                        is EthereumWallet -> wallet.address
                        is MultiChainWallet -> wallet.ethereumWallet?.address
                            ?: wallet.bitcoinWallet?.address
                            ?: ""
                        else -> ""
                    }

                    val network = when (wallet) {
                        is BitcoinWallet -> wallet.network.name
                        is EthereumWallet -> wallet.network.name
                        else -> "Mainnet"
                    }

                    // Create share URL
                    val shareUrl = when (wallet.walletType) {
                        WalletType.BITCOIN -> "bitcoin:$address"
                        WalletType.ETHEREUM -> "ethereum:$address"
                        else -> address
                    }

                    _uiState.update {
                        it.copy(
                            walletId = walletId,
                            walletName = wallet.name,
                            address = address,
                            walletType = wallet.walletType,
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
            // Reset after 2 seconds
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(copiedToClipboard = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}