package com.example.nexuswallet.feature.bitcoin.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetBitcoinFeeEstimateUseCase @Inject constructor(
    private val bitcoinBlockchainRepository: BitcoinBlockchainRepository,
    private val logger: Logger
) {

    private val tag = "GetBitcoinFeeUC"

    suspend operator fun invoke(
        feeLevel: FeeLevel,
        inputCount: Int,
        outputCount: Int,
        network: BitcoinNetwork
    ): Result<BitcoinFeeEstimate> {
        logger.d(
            tag,
            "Getting fee estimate for $feeLevel ($network) with $inputCount inputs, $outputCount outputs"
        )
        return bitcoinBlockchainRepository.getFeeEstimate(feeLevel, inputCount, outputCount, network)
    }
}