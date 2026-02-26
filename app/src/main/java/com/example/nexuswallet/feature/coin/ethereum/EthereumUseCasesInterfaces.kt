package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface SyncEthereumTransactionsUseCase {
    suspend operator fun invoke(walletId: String): Result<Unit>
}

interface GetTransactionUseCase {
    suspend operator fun invoke(transactionId: String): Result<EthereumTransaction>
}

interface GetWalletTransactionsUseCase {
    fun invoke(walletId: String): Flow<Result<List<EthereumTransaction>>>
}

interface GetPendingTransactionsUseCase {
    suspend operator fun invoke(): Result<List<EthereumTransaction>>
}

interface ValidateEthereumSendUseCase {
    data class ValidationResult(
        val isValid: Boolean,
        val addressError: String? = null,
        val amountError: String? = null,
        val balanceError: String? = null,
        val selfSendError: String? = null,
        val feeEstimate: EthereumFeeEstimate? = null
    )

    suspend operator fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        fromAddress: String,
        balance: BigDecimal,
        feeLevel: FeeLevel
    ): ValidationResult
}

interface GetFeeEstimateUseCase {
    suspend operator fun invoke(
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        network: EthereumNetwork = EthereumNetwork.Mainnet
    ): Result<EthereumFeeEstimate>
}

interface GetEthereumWalletUseCase {
    suspend operator fun invoke(walletId: String): Result<EthereumWalletInfo>
}

interface SendEthereumUseCase {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel = FeeLevel.NORMAL,
        note: String? = null
    ): Result<SendEthereumResult>
}