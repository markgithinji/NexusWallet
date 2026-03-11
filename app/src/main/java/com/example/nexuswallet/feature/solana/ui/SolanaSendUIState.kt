package com.example.nexuswallet.feature.solana.ui

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.coin.SendValidationResult
import com.example.nexuswallet.feature.coin.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import java.math.BigDecimal

data class SolanaSendUIState(
    val walletId: String = "",
    val walletName: String = "",
    val walletAddress: String = "",
    val network: SolanaNetwork = SolanaNetwork.Devnet,
    val availableNetworks: List<SolanaNetwork> = emptyList(),
    val availableSplTokens: List<SPLToken> = emptyList(),
    val selectedSplToken: SPLToken? = null,
    val isNativeSol: Boolean = true,
    val balance: BigDecimal = BigDecimal.ZERO,
    val balanceFormatted: String = "0 SOL",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: SolanaFeeEstimate? = null,
    val isFeeLoading: Boolean = false,
    val validationResult: SendValidationResult = SendValidationResult(isValid = false),
    val isLoading: Boolean = false,
    val error: String? = null,
    val step: String = "",
    val isValid: Boolean = false
)