package com.example.nexuswallet.feature.coin.ethereum.ui

import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.SendValidationResult
import com.example.nexuswallet.feature.coin.ethereum.domain.model.EVMFeeEstimate
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import java.math.BigDecimal

data class EthSendUiState(
    val walletId: String = "",
    val walletName: String = "",
    val fromAddress: String = "",
    val network: EthereumNetwork = EthereumNetwork.Sepolia,
    val availableNetworks: List<EthereumNetwork> = emptyList(),
    val availableTokens: List<EVMToken> = emptyList(),
    val selectedToken: EVMToken? = null,
    val ethBalance: BigDecimal = BigDecimal.ZERO,
    val tokenBalance: BigDecimal = BigDecimal.ZERO,
    val balanceFormatted: String = "0 ETH",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val note: String = "",
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: EVMFeeEstimate? = null,
    val isFeeLoading: Boolean = false,
    val validationResult: SendValidationResult = SendValidationResult(isValid = false),
    val isLoading: Boolean = false,
    val error: String? = null,
    val step: String = "",
    val isInitialized: Boolean = false,
    val balancesLoaded: Boolean = false
)
