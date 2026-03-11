package com.example.nexuswallet.feature.bitcoin.ui.review

import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.PreparedBitcoinTransaction
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import java.math.BigDecimal

data class BitcoinReviewUiState(
    val walletId: String = "",
    val walletName: String = "",
    val fromAddress: String = "",
    val toAddress: String = "",
    val amount: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val feeLevel: FeeLevel = FeeLevel.NORMAL,
    val network: BitcoinNetwork = BitcoinNetwork.Testnet,
    val balance: BigDecimal = BigDecimal.ZERO,
    val balanceFormatted: String = "0 BTC",
    val feeEstimate: BitcoinFeeEstimate? = null,
    val isFeeLoading: Boolean = false,
    val preparedTransaction: PreparedBitcoinTransaction? = null,
    val transactionPrepared: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val step: String = ""
)