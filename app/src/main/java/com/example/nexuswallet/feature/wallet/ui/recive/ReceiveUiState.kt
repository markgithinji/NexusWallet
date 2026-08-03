package com.example.nexuswallet.feature.wallet.ui.recive

import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.QrCodeData

data class ReceiveUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val walletId: String = "",
    val walletName: String = "",
    val address: String = "",
    val coin: Coin? = null,
    val networkDisplayName: String = "",
    val shareUrl: String = "",
    val qrCode: QrCodeData? = null,
    val copiedToClipboard: Boolean = false
)
