package com.example.nexuswallet.feature.bitcoin.ui.send

import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import java.math.BigDecimal

data class BtcSendUiState(
    val walletId: String = "",
    val walletName: String = "",
    val walletAddress: String = "",
    val network: BitcoinNetwork = BitcoinNetwork.Mainnet,
    val coin: BitcoinCoin? = null,
    val availableNetworks: List<BitcoinNetwork> = emptyList(),
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val balance: BigDecimal = BigDecimal.ZERO,
    val balanceFormatted: String = "",
    val fiatRate: Double = 0.0,
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: BitcoinFeeEstimate? = null,
    val validationResult: SendValidationResult = SendValidationResult(isValid = false),
    val isValid: Boolean = false,
    val isLoading: Boolean = false,
    val isFeeLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val error: String? = null
)