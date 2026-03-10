package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.coin.SendValidationResult
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import java.math.BigDecimal

data class BtcSendUiState(
    val walletId: String = "",
    val walletName: String = "",
    val walletAddress: String = "",
    val network: BitcoinNetwork = BitcoinNetwork.Testnet,
    val availableNetworks: List<BitcoinNetwork> = emptyList(),
    val balance: BigDecimal = BigDecimal.ZERO,
    val balanceFormatted: String = "0 BTC",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: BitcoinFeeEstimate? = null,
    val validationResult: SendValidationResult = SendValidationResult(isValid = false),
    val isValid: Boolean = false,
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val error: String? = null
)