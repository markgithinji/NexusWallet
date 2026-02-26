package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import java.math.BigDecimal

data class EthSendUiState(
    val walletId: String = "",
    val walletName: String = "",
    val fromAddress: String = "",
    val network: String = "",
    val balance: BigDecimal = BigDecimal.ZERO,
    val balanceFormatted: String = "0 ETH",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val note: String = "",
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: EthereumFeeEstimate? = null,
    val validationResult: ValidateEthereumSendUseCase.ValidationResult = ValidateEthereumSendUseCase.ValidationResult(
        isValid = false
    ),
    val isLoading: Boolean = false,
    val error: String? = null,
    val step: String = ""
)