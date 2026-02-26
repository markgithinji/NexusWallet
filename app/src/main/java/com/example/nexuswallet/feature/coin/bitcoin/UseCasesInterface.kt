package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import java.math.BigDecimal

interface SyncBitcoinTransactionsUseCase {
    suspend operator fun invoke(walletId: String): Result<Unit>
}

interface GetBitcoinWalletUseCase {
    suspend operator fun invoke(walletId: String): Result<BitcoinWalletInfo>
}

interface SendBitcoinUseCase {
    data class Params(
        val walletId: String,
        val toAddress: String,
        val amount: BigDecimal,
        val feeLevel: FeeLevel = FeeLevel.NORMAL,
        val note: String? = null
    )

    suspend operator fun invoke(params: Params): Result<SendBitcoinResult>
}

interface GetBitcoinFeeEstimateUseCase {
    suspend operator fun invoke(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        inputCount: Int = 1,
        outputCount: Int = 2
    ): Result<BitcoinFeeEstimate>
}

interface GetBitcoinBalanceUseCase {
    suspend operator fun invoke(
        address: String,
        network: BitcoinNetwork
    ): Result<BigDecimal>
}

interface ValidateBitcoinAddressUseCase {
    operator fun invoke(
        address: String,
        network: BitcoinNetwork
    ): Boolean
}

interface ValidateBitcoinTransactionUseCase {
    data class ValidationResult(
        val isValid: Boolean,
        val addressError: String? = null,
        val amountError: String? = null,
        val balanceError: String? = null,
        val selfSendError: String? = null
    )

    operator fun invoke(
        walletId: String,
        wallet: Wallet?,
        toAddress: String,
        amount: BigDecimal,
        network: BitcoinNetwork,
        balance: BigDecimal,
        feeEstimate: BitcoinFeeEstimate?
    ): ValidationResult
}