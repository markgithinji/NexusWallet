package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
import com.example.nexuswallet.feature.coin.usdc.domain.USDCFeeEstimate
import com.example.nexuswallet.feature.coin.usdc.domain.ValidateUSDCFormUseCase
import java.math.BigDecimal

data class USDCSendState(
    val walletId: String = "",
    val walletName: String = "",
    val fromAddress: String = "",
    val network: EthereumNetwork = EthereumNetwork.Sepolia,
    val contractAddress: String = "",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: USDCFeeEstimate? = null,
    val usdcBalance: String = "0",
    val usdcBalanceDecimal: BigDecimal = BigDecimal.ZERO,
    val ethBalance: String = "0",
    val ethBalanceDecimal: BigDecimal = BigDecimal.ZERO,
    val validationResult: ValidateUSDCFormUseCase.ValidationResult = ValidateUSDCFormUseCase.ValidationResult(
        isValid = false,
        isValidAddress = false,
        hasSufficientBalance = false,
        hasSufficientGas = false
    ),
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val step: String = ""
)