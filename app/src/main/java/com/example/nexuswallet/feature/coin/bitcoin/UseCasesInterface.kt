package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import java.math.BigDecimal

interface SyncBitcoinTransactionsUseCase {
    suspend operator fun invoke(
        walletId: String,
        network: String? = null  // "mainnet", "testnet", or null for all
    ): Result<Unit>
}

interface GetBitcoinWalletUseCase {
    suspend operator fun invoke(
        walletId: String,
        network: BitcoinNetwork? = null
    ): Result<BitcoinWalletInfo>
}

interface SendBitcoinUseCase {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        network: BitcoinNetwork,
        note: String? = null
    ): Result<SendBitcoinResult>
}

interface GetBitcoinBalanceUseCase {
    suspend operator fun invoke(
        address: String,
        network: BitcoinNetwork
    ): Result<BigDecimal>
}

interface GetBitcoinFeeEstimateUseCase {
    suspend operator fun invoke(
        feeLevel: FeeLevel,
        inputCount: Int = 1,
        outputCount: Int = 2
    ): Result<BitcoinFeeEstimate>
}

interface ValidateBitcoinAddressUseCase {
    operator fun invoke(
        address: String,
        network: BitcoinNetwork
    ): Boolean
}

interface ValidateBitcoinTransactionUseCase {
    operator fun invoke(
        walletId: String,
        wallet: Wallet?,
        toAddress: String,
        amount: BigDecimal,
        network: BitcoinNetwork,
        balance: BigDecimal,
        feeEstimate: BitcoinFeeEstimate? = null
    ): ValidateBitcoinTransactionUseCase.ValidationResult

    data class ValidationResult(
        val isValid: Boolean,
        val addressError: String? = null,
        val amountError: String? = null,
        val balanceError: String? = null,
        val selfSendError: String? = null
    )
}