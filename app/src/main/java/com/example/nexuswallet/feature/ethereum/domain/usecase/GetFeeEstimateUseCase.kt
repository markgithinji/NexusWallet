package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.domain.model.EVMFeeEstimate
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.ethereum.domain.model.EthereumNetwork
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.core.util.Result

@Singleton
class GetFeeEstimateUseCase @Inject constructor(
    private val evmBlockchainRepository: EVMBlockchainRepository,
    private val logger: Logger
) {

    private val tag = "GetFeeEstimateUC"

    suspend operator fun invoke(
        feeLevel: FeeLevel,
        network: EthereumNetwork,
        isToken: Boolean
    ): Result<EVMFeeEstimate> {
        logger.d(tag, "Getting fee estimate for $feeLevel on ${network.displayName} (isToken=$isToken)")
        return evmBlockchainRepository.getFeeEstimate(feeLevel, network, isToken)
    }
}