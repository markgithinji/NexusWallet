package com.example.nexuswallet.feature.coin.usdc.domain

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.EthereumNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalance
import java.math.BigDecimal

interface SyncUSDTransactionsUseCase {
    suspend operator fun invoke(walletId: String): Result<Unit>
}

interface GetUSDCWalletUseCase {
    suspend operator fun invoke(walletId: String): Result<USDCWalletInfo>
}

interface SendUSDCUseCase {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel
    ): Result<SendUSDCResult>
}

interface GetUSDCBalanceUseCase {
    suspend operator fun invoke(walletId: String): Result<USDCBalance>
}

interface GetETHBalanceForGasUseCase {
    suspend operator fun invoke(walletId: String): Result<BigDecimal>
}

interface GetUSDCFeeEstimateUseCase {
    suspend operator fun invoke(
        feeLevel: FeeLevel,
        network: EthereumNetwork
    ): Result<USDCFeeEstimate>
}

interface ValidateUSDCFormUseCase {
    data class ValidationResult(
        val isValid: Boolean,
        val isValidAddress: Boolean,
        val hasSufficientBalance: Boolean,
        val hasSufficientGas: Boolean,
        val addressError: String? = null,
        val amountError: String? = null,
        val balanceError: String? = null,
        val gasError: String? = null
    )

    operator fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        usdcBalanceDecimal: BigDecimal,
        ethBalanceDecimal: BigDecimal,
        feeEstimate: USDCFeeEstimate?,
        requireGas: Boolean = true
    ): ValidationResult
}