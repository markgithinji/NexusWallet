package com.example.nexuswallet.feature.wallet.ui.recive

import android.graphics.Bitmap
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.wallet.domain.model.Network

data class ReceiveUiState(
    val walletId: String = "",
    val walletName: String = "",
    val address: String = "",
    val network: Network? = null,
    val networkDisplayName: String = "",
    val qrCodeBitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val copiedToClipboard: Boolean = false,
    val shareUrl: String = ""
) {
    val coinType: CoinType? get() = network?.coinType
}