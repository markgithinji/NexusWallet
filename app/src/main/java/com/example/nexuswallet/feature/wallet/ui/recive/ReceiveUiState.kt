package com.example.nexuswallet.feature.wallet.ui.recive

import android.graphics.Bitmap
import com.example.nexuswallet.feature.wallet.domain.model.Coin

data class ReceiveUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val walletId: String = "",
    val walletName: String = "",
    val address: String = "",
    val coin: Coin? = null,
    val networkDisplayName: String = "",
    val shareUrl: String = "",
    val qrCodeBitmap: Bitmap? = null,
    val copiedToClipboard: Boolean = false
)