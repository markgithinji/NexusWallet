package com.example.nexuswallet.feature.coin.bitcoin.domain.usecase

import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinFeeEstimate
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.logging.Logger
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
        outputCount: Int
    ): Result<BitcoinFeeEstimate> {
        logger.d(
            tag,
            "Getting fee estimate for $feeLevel with $inputCount inputs, $outputCount outputs"
        )
        return bitcoinBlockchainRepository.getFeeEstimate(feeLevel, inputCount, outputCount)
    }
}