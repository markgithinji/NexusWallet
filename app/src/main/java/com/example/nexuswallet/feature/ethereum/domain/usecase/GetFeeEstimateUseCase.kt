package com.example.nexuswallet.feature.ethereum.domain.usecase

import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.ethereum.domain.model.EVMFeeEstimate
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result

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