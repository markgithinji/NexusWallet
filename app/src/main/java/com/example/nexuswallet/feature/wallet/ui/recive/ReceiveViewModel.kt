package com.example.nexuswallet.feature.wallet.ui.recive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.NativeETH
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.USDCToken
import com.example.nexuswallet.feature.wallet.domain.model.USDTToken
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.GenerateQrCodeUseCase
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
    private val walletRepository: WalletRepository,
    private val generateQrCodeUseCase: GenerateQrCodeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiveUiState())
    val uiState: StateFlow<ReceiveUiState> = _uiState.asStateFlow()

    fun initialize(walletId: String, coin: Coin) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            runCatching {
                walletRepository.getWallet(walletId)
            }.fold(
                onSuccess = { wallet ->
                    if (wallet == null) {
                        _uiState.update {
                            it.copy(
                                error = "Wallet not found",
                                isLoading = false
                            )
                        }
                        return@launch
                    }

                    val addressResult = getAddressForCoin(wallet, coin)

                    if (addressResult == null) {
                        _uiState.update {
                            it.copy(
                                error = "No receive address available for ${coin.symbol} on ${coin.network.name}",
                                isLoading = false
                            )
                        }
                        return@launch
                    }

                    val (address, networkDisplayName) = addressResult

                    val shareUrl = when (coin) {
                        is BitcoinCoin -> "bitcoin:$address"
                        is NativeETH -> "ethereum:$address"
                        is USDCToken, is USDTToken -> "ethereum:$address"
                        is SolanaCoin -> "solana:$address"
                    }

                    // Generate QR code
                    val qrCode = generateQrCodeUseCase(address)

                    _uiState.update {
                        it.copy(
                            walletId = walletId,
                            walletName = wallet.name,
                            address = address,
                            coin = coin,
                            networkDisplayName = networkDisplayName,
                            shareUrl = shareUrl,
                            qrCode = qrCode,
                            isLoading = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            error = "Failed to load wallet: ${e.message}",
                            isLoading = false
                        )
                    }
                }
            )
        }
    }

    private fun getAddressForCoin(
        wallet: Wallet,
        coin: Coin
    ): Pair<String, String>? {
        return when (coin) {
            is BitcoinCoin -> {
                wallet.bitcoinCoins.find { it.network == coin.network && it.address == coin.address }
                    ?.let { foundCoin ->
                        Pair(foundCoin.address, "${foundCoin.symbol} on ${foundCoin.network.name}")
                    }
            }

            is EVMToken -> {
                wallet.evmTokens.find {
                    it.network == coin.network &&
                            it.address == coin.address &&
                            it.evmTokenType == coin.evmTokenType
                }?.let { foundToken ->
                    Pair(foundToken.address, "${foundToken.symbol} on ${foundToken.network.name}")
                }
            }

            is SolanaCoin -> {
                wallet.solanaCoins.find { it.network == coin.network && it.address == coin.address }
                    ?.let { foundCoin ->
                        Pair(foundCoin.address, "${foundCoin.symbol} on ${foundCoin.network.name}")
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