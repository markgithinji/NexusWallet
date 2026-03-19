package com.example.nexuswallet.feature.wallet.ui.walletcreation

import com.example.nexuswallet.feature.wallet.domain.model.Wallet

sealed class WalletCreationUiState {
    object Idle : WalletCreationUiState()
    object Loading : WalletCreationUiState()
    object MnemonicGenerated : WalletCreationUiState()
    data class WalletCreated(val wallet: Wallet) : WalletCreationUiState()
    data class Error(val message: String) : WalletCreationUiState()
}