package com.example.nexuswallet.feature.ethereum.ui

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.ethereum.domain.model.EVMFeeEstimate
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import java.math.BigDecimal

data class EVMSendUiState(
    val walletId: String = "",
    val walletName: String = "",
    val fromAddress: String = "",
    val network: EthereumNetwork = EthereumNetwork.Mainnet,
    val coin: EVMToken? = null,
    val availableNetworks: List<EthereumNetwork> = emptyList(),
    val availableTokens: List<EVMToken> = emptyList(),
    val selectedToken: EVMToken? = null,
    val ethBalance: BigDecimal = BigDecimal.ZERO,
    val tokenBalance: BigDecimal = BigDecimal.ZERO,
    val balanceFormatted: String = "",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: EVMFeeEstimate? = null,
    val note: String = "",
    val validationResult: SendValidationResult = SendValidationResult(isValid = false),
    val isLoading: Boolean = false,
    val isFeeLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val balancesLoaded: Boolean = false,
    val error: String? = null,
    val step: String = ""
)