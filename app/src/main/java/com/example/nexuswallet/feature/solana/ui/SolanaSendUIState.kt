package com.example.nexuswallet.feature.solana.ui

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.domain.model.SendValidationResult
import com.example.nexuswallet.feature.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.wallet.domain.model.AddressBookEntry
import com.example.nexuswallet.feature.wallet.domain.model.SPLToken
import com.example.nexuswallet.feature.wallet.domain.model.SolanaCoin
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import java.math.BigDecimal

data class SolanaSendUIState(
    val walletId: String = "",
    val walletName: String = "",
    val walletAddress: String = "",
    val network: SolanaNetwork = SolanaNetwork.Devnet,
    val coin: SolanaCoin? = null,
    val availableNetworks: List<SolanaNetwork> = emptyList(),
    val availableCoins: List<SolanaCoin> = emptyList(),
    val availableSplTokens: List<SPLToken> = emptyList(),
    val selectedSplToken: SPLToken? = null,
    val isNativeSol: Boolean = true,
    val balance: BigDecimal = BigDecimal.ZERO, // Selected asset balance
    val solBalance: BigDecimal = BigDecimal.ZERO, // Native SOL balance for fees
    val balanceFormatted: String = "0 SOL",
    val fiatRate: Double = 0.0,
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: SolanaFeeEstimate? = null,
    val isFeeLoading: Boolean = false,
    val validationResult: SendValidationResult = SendValidationResult(isValid = false),
    val isLoading: Boolean = false,
    val addressBookEntries: List<AddressBookEntry> = emptyList(),
    val error: String? = null,
    val step: String = "",
    val isValid: Boolean = false,
    val isFiatMode: Boolean = false,
    val maxAmountSuggestion: BigDecimal? = null,
    val maxFeeSuggestion: BigDecimal? = null
)