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

    suspend operator fun invoke(
        feeLevel: FeeLevel,
        inputCount: Int,
        outputCount: Int,
        network: BitcoinNetwork,
        isSegwit: Boolean = true
    ): Result<BitcoinFeeEstimate> {
        val result = bitcoinBlockchainRepository.getFeeEstimate(
            feeLevel = feeLevel,
            inputCount = inputCount,
            outputCount = outputCount,
            network = network,
            isSegwit = isSegwit
        )
        
        when (result) {
            is Result.Success -> {
                val fee = result.data
                logger.d(
                    TAG,
                    "Fee Result: Total=${fee.totalFeeBtc} BTC (${fee.totalFeeSatoshis} sats) | Rate=${fee.feePerByte} sat/vB | Size=${fee.estimatedSize} vB | Inputs=$inputCount, Outputs=$outputCount, SegWit=$isSegwit"
                )
            }
            is Result.Error -> {
                logger.e(TAG, "Fee Error: ${result.message} | Params: Inputs=$inputCount, Outputs=$outputCount")
            }
            else -> {}
        }
        
        return result
    }

    companion object {
        private const val TAG = "GetBitcoinFeeUC"
    }
}