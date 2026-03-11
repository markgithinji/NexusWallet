package com.example.nexuswallet.feature.coin.solana.domain.usecase

import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.solana.domain.model.SolanaFeeEstimate
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSolanaFeeEstimateUseCase @Inject constructor(
    private val solanaBlockchainRepository: SolanaBlockchainRepository,
    private val logger: Logger
) {

    private val tag = "GetSolanaFeeUC"

    suspend operator fun invoke(
        feeLevel: FeeLevel,
        network: SolanaNetwork
    ): Result<SolanaFeeEstimate> {
        logger.d(tag, "Fetching fee estimate on $network")
        val result = solanaBlockchainRepository.getFeeEstimate(feeLevel, network)
        if (result is Result.Error) {
            logger.e(tag, "Failed to get fee estimate on $network: ${result.message}")
        }
        return result
    }
}