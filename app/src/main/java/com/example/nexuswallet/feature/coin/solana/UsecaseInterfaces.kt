package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import java.math.BigDecimal


interface SyncSolanaTransactionsUseCase {
    suspend operator fun invoke(walletId: String, network: String): Result<Unit>
}


interface GetSolanaWalletUseCase {
    suspend operator fun invoke(
        walletId: String,
        network: SolanaNetwork? = null
    ): Result<SolanaWalletInfo>
}

interface SendSolanaUseCase {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        network: SolanaNetwork,
        note: String? = null
    ): Result<SendSolanaResult>
}

interface GetSolanaBalanceUseCase {
    suspend operator fun invoke(
        address: String,
        network: SolanaNetwork
    ): Result<BigDecimal>
}

interface GetSolanaFeeEstimateUseCase {
    suspend operator fun invoke(
        feeLevel: FeeLevel,
        network: SolanaNetwork
    ): Result<SolanaFeeEstimate>
}

interface ValidateSolanaAddressUseCase {
    operator fun invoke(address: String): Result<Boolean>
}

interface ValidateSolanaSendUseCase {
    operator fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        walletAddress: String,
        balance: BigDecimal,
        feeEstimate: SolanaFeeEstimate? = null
    ): ValidateSolanaSendUseCase.ValidationResult

    data class ValidationResult(
        val isValid: Boolean,
        val addressError: String? = null,
        val amountError: String? = null,
        val balanceError: String? = null,
        val selfSendError: String? = null
    )
}