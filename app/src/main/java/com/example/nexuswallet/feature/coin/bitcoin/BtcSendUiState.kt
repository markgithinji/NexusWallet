package com.example.nexuswallet.feature.coin.bitcoin

import java.math.BigDecimal

data class BtcSendUiState(
    val walletId: String = "",
    val walletName: String = "",
    val walletAddress: String = "",
    val network: BitcoinNetwork = BitcoinNetwork.TESTNET,
    val balance: BigDecimal = BigDecimal.ZERO,
    val balanceFormatted: String = "0 BTC",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: BitcoinFeeEstimate? = null,
    val validationResult: ValidateBitcoinTransactionUseCase.ValidationResult = ValidateBitcoinTransactionUseCase.ValidationResult(
        isValid = false
    ),
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val step: String = ""
)