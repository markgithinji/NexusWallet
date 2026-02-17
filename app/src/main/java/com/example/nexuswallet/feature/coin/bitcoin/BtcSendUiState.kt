package com.example.nexuswallet.feature.coin.bitcoin

import java.math.BigDecimal

data class BtcSendUiState(
    val walletId: String = "",
    val walletName: String = "",
    val walletAddress: String = "",
    val balance: BigDecimal = BigDecimal.ZERO,
    val balanceFormatted: String = "0 BTC",
    val toAddress: String = "",
    val isAddressValid: Boolean = false,
    val addressError: String? = null,
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val network: BitcoinNetwork = BitcoinNetwork.TESTNET,
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: BitcoinFeeEstimate? = null,
    val isLoading: Boolean = false,
    val step: String = "",
    val error: String? = null,
    val info: String? = null
)