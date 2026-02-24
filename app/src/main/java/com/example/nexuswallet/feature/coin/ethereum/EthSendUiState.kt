package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import java.math.BigDecimal

data class EthSendUiState(
    val walletId: String = "",
    val walletName: String = "",
    val fromAddress: String = "",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val note: String = "",
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val isLoading: Boolean = false,
    val error: String? = null,
    val feeEstimate: EthereumFeeEstimate? = null,
    val balance: BigDecimal = BigDecimal.ZERO,
    val isValid: Boolean = false,
    val validationError: String? = null,
    val network: String = "",
    val step: String = ""
)