package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.SendValidationResult
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal


interface SyncEthereumTransactionsUseCase {
    suspend operator fun invoke(
        walletId: String,
        tokenExternalId: String? = null
    ): Result<Unit>
}

interface GetTransactionUseCase {
    suspend operator fun invoke(transactionId: String): Result<EVMTransaction>
}

interface GetWalletTransactionsUseCase {
    operator fun invoke(walletId: String): Flow<Result<List<EVMTransaction>>>
}

interface GetPendingTransactionsUseCase {
    suspend operator fun invoke(): Result<List<EVMTransaction>>
}

interface ValidateEVMSendUseCase {
    suspend operator fun invoke(
        toAddress: String,
        amountValue: BigDecimal,
        fromAddress: String,
        tokenBalance: BigDecimal,
        ethBalance: BigDecimal,
        feeLevel: FeeLevel,
        token: EVMToken
    ): SendValidationResult
}

interface GetFeeEstimateUseCase {
    suspend operator fun invoke(
        feeLevel: FeeLevel,
        network: EthereumNetwork,
        isToken: Boolean
    ): Result<EVMFeeEstimate>
}

interface GetEthereumWalletUseCase {
    suspend operator fun invoke(walletId: String): Result<EthereumWalletInfo>
}

interface SendEVMAssetUseCase {
    suspend operator fun invoke(
        walletId: String,
        toAddress: String,
        amount: BigDecimal,
        feeLevel: FeeLevel,
        token: EVMToken,
        note: String? = null
    ): Result<SendEthereumResult>
}
