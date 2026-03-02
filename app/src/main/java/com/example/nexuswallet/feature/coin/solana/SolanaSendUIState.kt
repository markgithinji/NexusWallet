package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import java.math.BigDecimal


data class SolanaSendUIState(
    val walletId: String = "",
    val walletName: String = "",
    val walletAddress: String = "",
    val network: SolanaNetwork = SolanaNetwork.Devnet,
    val balance: BigDecimal = BigDecimal.ZERO,
    val balanceFormatted: String = "0 SOL",
    val toAddress: String = "",
    val isAddressValid: Boolean = false,
    val addressError: String? = null,
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val feeEstimate: SolanaFeeEstimate? = null,
    val isLoading: Boolean = false,
    val step: String = "",
    val error: String? = null
)